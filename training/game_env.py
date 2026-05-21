"""
ProjectEcho — Gymnasium environment for RL tribe policy training.

Each agent controls one tribe's expansion and raid decisions. Child tribes
spawned by splits continue under HeuristicPolicy. Episodes end when all
initial tribes are extinct or max_ticks is reached.

Action space per agent  (Discrete 12):
  0–3  expand_N / expand_S / expand_E / expand_W
  4–10 raid_tribe_0 … raid_tribe_6  (sorted by tribeId, self excluded)
  11   rest
"""
from __future__ import annotations

import random as _random
from typing import Optional

import numpy as np

try:
    import gymnasium as gym
    from gymnasium import spaces
except ImportError:
    raise ImportError("Install 'gymnasium' first:  pip install gymnasium")

from simulation import (
    JavaRandom, HeuristicPolicy, TribePolicy, RaidCandidate,
    Tribe, MapTile, WorldState,
    initial_for_training, tick, get_neighbors,
    BiomeType, TribeNeed,
    GRID_SIZE, MAX_TRIBES, STATE_VECTOR_SIZE,
)

# ---------------------------------------------------------------------------
# Action constants
# ---------------------------------------------------------------------------

NUM_ACTIONS    = 12
ACTION_EXPAND_N = 0
ACTION_EXPAND_S = 1
ACTION_EXPAND_E = 2
ACTION_EXPAND_W = 3
ACTION_RAID_BASE = 4   # actions 4–10 = raid tribe at sorted index 0–6
ACTION_REST      = 11

_EXPAND_ACTIONS = {ACTION_EXPAND_N, ACTION_EXPAND_S, ACTION_EXPAND_E, ACTION_EXPAND_W}
_RAID_ACTIONS   = set(range(ACTION_RAID_BASE, ACTION_REST))


# ---------------------------------------------------------------------------
# RLPolicy — maps integer actions to simulation policy decisions
# ---------------------------------------------------------------------------

class RLPolicy(TribePolicy):
    def __init__(self, actions: dict, sorted_other_ids: list, rng: JavaRandom):
        self._actions  = actions           # {tribe_id: int}
        self._others   = sorted_other_ids  # sorted tribeIds of all living tribes
        self._rng      = rng

    def choose_expansion(self, tribe: Tribe, candidates: list) -> Optional[MapTile]:
        if not candidates:
            return None
        action = self._actions.get(tribe.tribe_id, ACTION_REST)
        if action not in _EXPAND_ACTIONS:
            return None

        if action == ACTION_EXPAND_N:
            def key(t): return (t.row, t.col)
        elif action == ACTION_EXPAND_S:
            def key(t): return (-t.row, t.col)
        elif action == ACTION_EXPAND_E:
            def key(t): return (-t.col, t.row)
        else:  # EXPAND_W
            def key(t): return (t.col, t.row)
        return min(candidates, key=key)

    def choose_raid(self, tribe: Tribe, targets: list) -> Optional[RaidCandidate]:
        if not targets: return None
        action = self._actions.get(tribe.tribe_id, ACTION_REST)
        if action not in _RAID_ACTIONS: return None

        raid_idx = action - ACTION_RAID_BASE
        others   = [tid for tid in self._others if tid != tribe.tribe_id]
        if raid_idx >= len(others): return None

        target_id = others[raid_idx]
        matching  = [t for t in targets if t.defender_tribe_id == target_id]
        if not matching: return None
        return matching[self._rng.next_int(len(matching))]


# ---------------------------------------------------------------------------
# Reward shaping (Phase 19 spec)
# ---------------------------------------------------------------------------

def _shaped_reward(prev: WorldState, nxt: WorldState, tribe_id: str, was_successful_raider: bool) -> float:
    if tribe_id not in nxt.tribes:
        return -1.0   # extinction (clipped; base is -10 before clip)

    tribe      = nxt.tribes[tribe_id]
    prev_tribe = prev.tribes.get(tribe_id)
    r = 0.0
    r += 0.01                                          # alive bonus
    r += 0.02 * max(0, len(nxt.tribes) - 1)           # coexistence: reward world with multiple tribes
    # hoarding guard: food bonus only when pop is stable or growing
    if tribe.food_supply > 0 and (prev_tribe is None or tribe.population >= prev_tribe.population):
        r += 0.1                                       # food surplus tick
    if tribe.food_supply == 0 and prev_tribe and tribe.population < prev_tribe.population:
        r -= 0.5                                       # starvation tick
    if was_successful_raider:    r += 0.3             # successful raid (v4: bumped from 0.2; starvation mask handles over-raiding)
    # overextension: penalise holding >40% of all tiles (v4: quadratic, much steeper above 60%)
    tribe_tiles   = sum(1 for t in nxt.tiles if t.occupant_tribe_id == tribe_id)
    tile_fraction = tribe_tiles / max(1, len(nxt.tiles))
    if tile_fraction > 0.4:
        excess = tile_fraction - 0.4
        r -= 0.3 * excess + 0.5 * (excess ** 2)       # ~0.06 at 60%, ~0.28 at 80%, ~0.60 at 100%
    return max(-1.0, min(1.0, r))


# ---------------------------------------------------------------------------
# ProjectEchoEnv
# ---------------------------------------------------------------------------

class ProjectEchoEnv(gym.Env):
    """
    Multi-agent Gymnasium env wrapping the ProjectEcho tribe simulation.

    Observation and action spaces are keyed by the initial tribe IDs
    (dict spaces). Dead agents receive zero observations; their actions
    are ignored. Child tribes from splits are controlled by HeuristicPolicy.

    For single-agent PPO training, wrap with a SingleAgentWrapper or iterate
    over agents in the training loop — see README.
    """

    metadata = {"render_modes": []}

    def __init__(
        self,
        num_tribes: int    = 4,
        max_ticks:  int    = 200,
        domain_randomize: bool = True,
    ):
        super().__init__()
        if not (1 <= num_tribes <= MAX_TRIBES):
            raise ValueError(f"num_tribes must be in [1, {MAX_TRIBES}], got {num_tribes}")
        self.num_tribes        = num_tribes
        self.max_ticks         = max_ticks
        self.domain_randomize  = domain_randomize

        # Spaces are keyed by "training-tribe-{i}" for i in [0, num_tribes)
        self._agent_ids = [f"training-tribe-{i}" for i in range(num_tribes)]

        _box = spaces.Box(low=0.0, high=1.0, shape=(STATE_VECTOR_SIZE,), dtype=np.float32)
        self.observation_space = spaces.Dict({tid: _box for tid in self._agent_ids})
        self.action_space      = spaces.Dict({tid: spaces.Discrete(NUM_ACTIONS) for tid in self._agent_ids})

        # Internal state (initialised in reset())
        self._state:        Optional[WorldState] = None
        self._sim_rng:      Optional[JavaRandom] = None
        self._episode_seed: int = 42
        self._env_rng       = _random.Random()    # for domain-randomisation shocks (Python RNG)

    # ------------------------------------------------------------------
    # Gymnasium API
    # ------------------------------------------------------------------

    def reset(self, seed=None, options=None):
        super().reset(seed=seed)
        if seed is not None:
            self._episode_seed = seed
        else:
            self._episode_seed = _random.randint(0, 2**31 - 1)

        self._sim_rng = JavaRandom(self._episode_seed)
        self._env_rng = _random.Random(self._episode_seed ^ 0xDEADBEEF)
        self._state   = initial_for_training(self.num_tribes, self._episode_seed)

        return self._observations(), {}

    def step(self, action_dict: dict):
        if self._state is None:
            raise RuntimeError("Call reset() first")

        # Build sorted other-tribe list (for RLPolicy raid indexing)
        living_ids      = sorted(self._state.tribes.keys())
        policy          = RLPolicy(action_dict, living_ids, self._sim_rng)
        heuristic       = HeuristicPolicy(self._sim_rng)

        # For child tribes not in action_dict, fall back to heuristic
        class _MixedPolicy(TribePolicy):
            def __init__(self, rl: RLPolicy, h: HeuristicPolicy, rl_ids: set):
                self._rl, self._h, self._ids = rl, h, rl_ids
            def choose_expansion(self, tribe, candidates):
                if tribe.tribe_id in self._ids:
                    return self._rl.choose_expansion(tribe, candidates)
                return self._h.choose_expansion(tribe, candidates)
            def choose_raid(self, tribe, targets):
                if tribe.tribe_id in self._ids:
                    return self._rl.choose_raid(tribe, targets)
                return self._h.choose_raid(tribe, targets)

        rl_ids       = set(action_dict.keys())
        mixed_policy = _MixedPolicy(policy, heuristic, rl_ids)

        prev_state   = self._state

        # Domain randomisation: 5% chance of a shock to a random living tribe
        if self.domain_randomize and self._env_rng.random() < 0.05:
            self._state = self._apply_shock(self._state)

        self._state  = tick(self._state, self._sim_rng, mixed_policy)

        # Compute raid successes (tiles transferred in this tick)
        prev_tiles   = {tid: {t.id for t in prev_state.tiles if t.occupant_tribe_id == tid}
                        for tid in self._agent_ids}
        curr_tiles   = {tid: {t.id for t in self._state.tiles if t.occupant_tribe_id == tid}
                        for tid in self._agent_ids}
        raiders      = {tid: bool(curr_tiles[tid] - prev_tiles[tid] - {
                            t.id for t in prev_state.tiles if t.occupant_tribe_id is None})
                        for tid in self._agent_ids}

        observations = self._observations()
        rewards      = {}
        terminated   = {}
        truncated    = {}

        for tid in self._agent_ids:
            dead           = tid not in self._state.tribes
            timed_out      = self._state.world_time_tick >= self.max_ticks
            rewards[tid]   = _shaped_reward(prev_state, self._state, tid, raiders.get(tid, False))
            terminated[tid] = dead
            truncated[tid]  = timed_out and not dead

        info = {
            "world_time_tick": self._state.world_time_tick,
            "living_tribes":   list(self._state.tribes.keys()),
        }
        return observations, rewards, terminated, truncated, info

    def action_masks(self) -> dict:
        """
        Returns {tribe_id: np.ndarray(bool, shape=(NUM_ACTIONS,))} for valid actions.
        Hook into MaskablePPO / CleanRL action-masking utilities.
        """
        if self._state is None:
            raise RuntimeError("Call reset() first")
        masks = {}
        for tid in self._agent_ids:
            m = np.zeros(NUM_ACTIONS, dtype=bool)
            m[ACTION_REST] = True   # rest is always legal
            if tid not in self._state.tribes:
                masks[tid] = m
                continue

            tribe     = self._state.tribes[tid]
            occ_ids   = {t.id for t in self._state.tiles if t.occupant_tribe_id == tid}
            nbr_ids   = set()
            for oid in occ_ids: nbr_ids.update(get_neighbors(oid))
            frontier  = [t for t in self._state.tiles
                         if t.occupant_tribe_id is None and t.biome != BiomeType.Water
                         and t.id in nbr_ids]

            if frontier:
                m[ACTION_EXPAND_N] = True
                m[ACTION_EXPAND_S] = True
                m[ACTION_EXPAND_E] = True
                m[ACTION_EXPAND_W] = True

            # Starvation guard: a tribe with no food cannot sustain a raid
            if tribe.food_supply > 0:
                others = sorted(ot for ot in self._state.tribes if ot != tid)
                for i, other_id in enumerate(others[:MAX_TRIBES - 1]):
                    other        = self._state.tribes[other_id]
                    other_occ    = {t.id for t in self._state.tiles if t.occupant_tribe_id == other_id}
                    is_adjacent  = any(any(n in occ_ids for n in get_neighbors(oid)) for oid in other_occ)
                    if is_adjacent and other.divine_shield_ticks == 0:
                        m[ACTION_RAID_BASE + i] = True

            masks[tid] = m
        return masks

    # ------------------------------------------------------------------
    # Observation helpers
    # ------------------------------------------------------------------

    def _observations(self) -> dict:
        obs = {}
        for tid in self._agent_ids:
            if tid not in self._state.tribes:
                obs[tid] = np.zeros(STATE_VECTOR_SIZE, dtype=np.float32)
                continue
            tribe      = self._state.tribes[tid]
            owned      = [t for t in self._state.tiles if t.occupant_tribe_id == tid]
            neighbors  = [t for ot,t in self._state.tribes.items() if ot != tid]
            vec        = tribe.to_float_array(owned, neighbors, self._state.tiles, self._state.world_time_tick)
            obs[tid]   = np.array(vec, dtype=np.float32)
        return obs

    # ------------------------------------------------------------------
    # Domain randomisation shock
    # ------------------------------------------------------------------

    def _apply_shock(self, state: WorldState) -> WorldState:
        """5% chance per tick: inject a divine-action-magnitude shock to a random tribe."""
        living = list(state.tribes.keys())
        if not living: return state
        target_id = self._env_rng.choice(living)
        tribe     = state.tribes[target_id]
        shock     = self._env_rng.randint(0, 3)  # 0=plague 1=harvest 2=inspire 3=famine
        if   shock == 0: new_tribe = tribe.copy(population=max(0, int(tribe.population * 0.8)))
        elif shock == 1: new_tribe = tribe.copy(food_supply=tribe.food_supply + 200)
        elif shock == 2: new_tribe = tribe.copy(devotion=min(100, tribe.devotion + 15))
        else:            new_tribe = tribe.copy(food_supply=max(0, tribe.food_supply - 80))
        new_tribes = dict(state.tribes)
        new_tribes[target_id] = new_tribe
        return state.copy(tribes=new_tribes)
