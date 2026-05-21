"""
ProjectEcho Phase 20 — League env wrapper for single-learner PPO training.

LeagueEnv presents one tribe ("training-tribe-0") as the learner and fills
the remaining opponent slots with Hall-of-Fame frozen snapshots.  When the
HoF is still empty (early training) opponents pick random valid actions.

Set heuristic_opponents=True to route all non-learner decisions through the
sim's built-in HeuristicPolicy — used by HeuristicEvalCallback to measure
whether the learner has beaten the heuristic baseline.
"""
from __future__ import annotations

import random
from typing import Optional

import numpy as np
import gymnasium as gym

from game_env import (
    ProjectEchoEnv,
    NUM_ACTIONS,
    ACTION_REST,
)
from simulation import STATE_VECTOR_SIZE


# ---------------------------------------------------------------------------
# HallOfFame — stores frozen actor-MLP snapshots for opponent play
# ---------------------------------------------------------------------------

class HallOfFame:
    """
    Holds up to max_size frozen policy weight snapshots.

    Each snapshot is a list of (W, b) float32 numpy pairs extracted from
    the actor MLP; inference is pure numpy so no torch import is needed
    inside subprocess workers.
    """

    def __init__(self, max_size: int = 50):
        self._snapshots: list = []
        self._max_size  = max_size
        self._rng       = random.Random()

    @property
    def has_snapshots(self) -> bool:
        return bool(self._snapshots)

    def add(self, layers: list) -> None:
        if len(self._snapshots) >= self._max_size:
            self._snapshots.pop(0)
        self._snapshots.append(layers)

    def get_action(self, obs: np.ndarray, mask: np.ndarray) -> int:
        """Forward-pass a randomly sampled snapshot; fall back to random valid."""
        valid = np.where(mask)[0]
        if not self._snapshots:
            return int(self._rng.choice(valid.tolist())) if len(valid) else ACTION_REST

        layers = self._rng.choice(self._snapshots)
        x = obs.astype(np.float32)
        for W, b in layers[:-1]:
            x = np.tanh(W @ x + b)
        W, b = layers[-1]
        logits = W @ x + b
        logits = np.where(mask, logits, -1e9)
        return int(np.argmax(logits))

    # Picklable state for env_method() transfer across subprocess boundary
    def get_snapshots(self) -> list:
        return list(self._snapshots)

    def set_snapshots(self, snapshots: list) -> None:
        self._snapshots = list(snapshots)


def extract_actor_weights(model) -> list:
    """
    Extract actor MLP weights from an SB3 MaskablePPO model as (W, b) pairs.

    Covers policy.mlp_extractor.policy_net (shared feature layers) and
    policy.action_net (the final logit projection).
    """
    layers = []
    for module in model.policy.mlp_extractor.policy_net:
        if hasattr(module, "weight"):
            layers.append((
                module.weight.detach().cpu().numpy().copy(),
                module.bias.detach().cpu().numpy().copy(),
            ))
    an = model.policy.action_net
    layers.append((
        an.weight.detach().cpu().numpy().copy(),
        an.bias.detach().cpu().numpy().copy(),
    ))
    return layers


# ---------------------------------------------------------------------------
# LeagueEnv — single-agent Gymnasium wrapper around ProjectEchoEnv
# ---------------------------------------------------------------------------

class LeagueEnv(gym.Env):
    """
    Single-learner Gymnasium env for PPO training.

    "training-tribe-0" is the RL learner; remaining tribes are controlled
    by the Hall of Fame (random valid action when HoF is still empty).

    heuristic_opponents=True routes all non-learner decisions through
    ProjectEchoEnv's built-in HeuristicPolicy (used for eval).
    """

    metadata = {"render_modes": []}

    def __init__(
        self,
        num_tribes:           int  = 4,
        max_ticks:            int  = 50,
        domain_randomize:     bool = True,
        heuristic_opponents:  bool = False,
    ):
        super().__init__()
        self._inner = ProjectEchoEnv(
            num_tribes=num_tribes,
            max_ticks=max_ticks,
            domain_randomize=domain_randomize,
        )
        self._learner_id        = self._inner._agent_ids[0]
        self._hof_ids           = self._inner._agent_ids[1:]
        self._heuristic_mode    = heuristic_opponents
        self._hof               = HallOfFame()
        self._last_obs: dict    = {}
        self._last_masks: dict  = {}

        self.observation_space = self._inner.observation_space[self._learner_id]
        self.action_space      = self._inner.action_space[self._learner_id]

    # ------------------------------------------------------------------
    # Gymnasium API
    # ------------------------------------------------------------------

    def reset(self, seed=None, options=None):
        obs_dict, info       = self._inner.reset(seed=seed, options=options)
        self._last_obs       = dict(obs_dict)
        self._last_masks     = self._inner.action_masks()
        return obs_dict[self._learner_id], info

    def step(self, action):
        if self._heuristic_mode:
            # Pass only the learner's action — the inner env's _MixedPolicy
            # will route all other tribes through HeuristicPolicy automatically.
            action_dict = {self._learner_id: int(action)}
        else:
            action_dict = {self._learner_id: int(action)}
            for opp_id in self._hof_ids:
                obs  = self._last_obs.get(
                    opp_id, np.zeros(STATE_VECTOR_SIZE, dtype=np.float32))
                mask = self._last_masks.get(
                    opp_id, np.ones(NUM_ACTIONS, dtype=bool)).astype(bool)
                mask[ACTION_REST] = True
                action_dict[opp_id] = self._hof.get_action(obs, mask)

        obs_dict, rew_dict, term_dict, trunc_dict, info = self._inner.step(action_dict)
        self._last_obs   = dict(obs_dict)
        self._last_masks = self._inner.action_masks()

        return (
            obs_dict[self._learner_id],
            rew_dict[self._learner_id],
            term_dict[self._learner_id],
            trunc_dict[self._learner_id],
            info,
        )

    def action_masks(self) -> np.ndarray:
        m = self._last_masks.get(self._learner_id)
        return (m if m is not None else np.ones(NUM_ACTIONS, dtype=bool)).astype(bool)

    # ------------------------------------------------------------------
    # env_method() hooks (called from main process via SubprocVecEnv)
    # ------------------------------------------------------------------

    def update_hof(self, snapshots: list) -> None:
        self._hof.set_snapshots(snapshots)

    def get_hof_snapshots(self) -> list:
        return self._hof.get_snapshots()

    def set_max_ticks(self, max_ticks: int) -> None:
        self._inner.max_ticks = max_ticks

    # ------------------------------------------------------------------
    # Evaluation helper
    # ------------------------------------------------------------------

    def get_final_info(self) -> dict:
        """
        Snapshot of final territory counts for win-rate computation.
        Called after episode ends; safe to call on a reset env (returns {}).
        """
        state = self._inner._state
        if state is None:
            return {}
        learner_tiles = sum(
            1 for t in state.tiles if t.occupant_tribe_id == self._learner_id)
        opp_tiles = [
            sum(1 for t in state.tiles if t.occupant_tribe_id == oid)
            for oid in self._hof_ids
            if oid in state.tribes
        ]
        return {
            "learner_tiles": learner_tiles,
            "opponent_tiles": opp_tiles,
            "learner_alive":  self._learner_id in state.tribes,
        }
