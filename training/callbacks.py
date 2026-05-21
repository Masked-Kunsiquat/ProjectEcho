"""
ProjectEcho Phase 20 — SB3 training callbacks.

HallOfFameCallback   — snapshots actor weights, propagates to all envs
CurriculumCallback   — grows max_ticks from 50 → 100 → 200 over training
HeuristicEvalCallback — measures win rate vs HeuristicPolicy; stops at threshold
"""
from __future__ import annotations

import os
from typing import Optional

import numpy as np
from stable_baselines3.common.callbacks import BaseCallback


# ---------------------------------------------------------------------------
# HallOfFameCallback
# ---------------------------------------------------------------------------

class HallOfFameCallback(BaseCallback):
    """
    Every `snapshot_every` timesteps, extract the current actor weights and
    push the updated snapshot list to all SubprocVecEnv workers.

    Workers sample randomly from the HoF list when choosing opponent actions,
    so opponents are always lagged versions of the training policy — the
    canonical self-play trick that prevents strategy collapse.
    """

    def __init__(self, snapshot_every: int = 5_000, verbose: int = 0):
        super().__init__(verbose)
        self._snapshot_every = snapshot_every
        self._last_snapshot  = 0

    def _on_step(self) -> bool:
        if self.num_timesteps - self._last_snapshot < self._snapshot_every:
            return True
        self._last_snapshot = self.num_timesteps

        from env_wrappers import extract_actor_weights
        new_layer = extract_actor_weights(self.model)

        # Pull current snapshot list from worker 0 (all workers stay in sync)
        current: list = self.training_env.env_method("get_hof_snapshots")[0]
        current = list(current) + [new_layer]
        if len(current) > 50:
            current = current[-50:]

        self.training_env.env_method("update_hof", current)

        if self.verbose:
            print(f"[HoF] snapshot #{len(current)} @ step {self.num_timesteps:,}")
        return True


# ---------------------------------------------------------------------------
# CurriculumCallback
# ---------------------------------------------------------------------------

class CurriculumCallback(BaseCallback):
    """
    Grows episode length (max_ticks) as the policy matures.

    schedule is a list of (start_step, max_ticks) pairs; the highest
    start_step that has been passed determines the current max_ticks.

    Default: 50 ticks until 200 k steps → 100 ticks until 500 k → 200 ticks.
    """

    _DEFAULT_SCHEDULE = [
        (0,       50),
        (200_000, 100),
        (500_000, 200),
    ]

    def __init__(
        self,
        schedule: Optional[list] = None,
        verbose: int = 0,
    ):
        super().__init__(verbose)
        self._schedule        = sorted(schedule or self._DEFAULT_SCHEDULE, key=lambda x: x[0])
        self._current_ticks   = self._schedule[0][1]

    def _on_step(self) -> bool:
        target = self._current_ticks
        for step, max_ticks in reversed(self._schedule):
            if self.num_timesteps >= step:
                target = max_ticks
                break

        if target != self._current_ticks:
            self._current_ticks = target
            self.training_env.env_method("set_max_ticks", target)
            if self.verbose:
                print(f"[Curriculum] max_ticks → {target} @ step {self.num_timesteps:,}")
        return True


# ---------------------------------------------------------------------------
# HeuristicEvalCallback
# ---------------------------------------------------------------------------

class HeuristicEvalCallback(BaseCallback):
    """
    Every `eval_freq` timesteps, run `n_eval_episodes` episodes where the
    learner faces HeuristicPolicy opponents.

    Win = learner survives AND finishes with territory >= median opponent territory.
    Saves best model and stops training when win_rate >= win_rate_threshold.
    """

    def __init__(
        self,
        eval_freq:            int   = 25_000,
        n_eval_episodes:      int   = 20,
        win_rate_threshold:   float = 0.60,
        output_dir:           str   = "./checkpoints",
        verbose:              int   = 0,
    ):
        super().__init__(verbose)
        self._eval_freq          = eval_freq
        self._n_episodes         = n_eval_episodes
        self._threshold          = win_rate_threshold
        self._output_dir         = output_dir
        self._last_eval          = 0
        self._best_win_rate      = 0.0
        self._eval_env           = None

    def _on_training_start(self) -> None:
        os.makedirs(self._output_dir, exist_ok=True)
        from env_wrappers import LeagueEnv
        # max_ticks=200 for eval regardless of curriculum stage
        self._eval_env = LeagueEnv(
            num_tribes=4,
            max_ticks=200,
            domain_randomize=False,
            heuristic_opponents=True,
        )

    def _on_step(self) -> bool:
        if self.num_timesteps - self._last_eval < self._eval_freq:
            return True
        self._last_eval = self.num_timesteps

        win_rate = self._run_eval()
        self.logger.record("eval/heuristic_win_rate", win_rate)

        if self.verbose:
            print(
                f"[Eval] step {self.num_timesteps:,} — "
                f"heuristic win rate: {win_rate:.1%}"
                f"  (best: {self._best_win_rate:.1%})"
            )

        if win_rate > self._best_win_rate:
            self._best_win_rate = win_rate
            path = os.path.join(self._output_dir, "best_model")
            self.model.save(path)
            if self.verbose:
                print(f"[Eval] New best — saved to {path}")

        if win_rate >= self._threshold:
            print(
                f"[Eval] Win rate {win_rate:.1%} ≥ threshold {self._threshold:.1%}. "
                "Training complete."
            )
            return False  # signals model.learn() to stop

        return True

    def _run_eval(self) -> float:
        wins = 0
        env  = self._eval_env
        for _ in range(self._n_episodes):
            obs, _ = env.reset()
            done   = False
            while not done:
                masks          = env.action_masks()
                action, _      = self.model.predict(
                    obs, action_masks=masks, deterministic=True)
                obs, _, terminated, truncated, _ = env.step(action)
                done           = terminated or truncated
            if self._is_win(env.get_final_info()):
                wins += 1
        return wins / self._n_episodes

    @staticmethod
    def _is_win(info: dict) -> bool:
        if not info.get("learner_alive", False):
            return False
        learner_tiles = info.get("learner_tiles", 0)
        opp_tiles     = info.get("opponent_tiles", [])
        if not opp_tiles:
            return True  # every opponent is dead
        return learner_tiles >= float(np.median(opp_tiles))
