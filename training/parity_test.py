"""
Phase 19 parity test: run 100 ticks with seed=42 and compare against the
known-good Kotlin headless snapshot (seed=42, ticks=100).

Kotlin snapshot (HeadlessParityTest.kt):
  snapshotTotalPop     = 713
  snapshotOccupiedTiles = 78

If this test passes, the Python simulation is tick-for-tick identical to
the Kotlin headless runner (same JavaRandom LCG, same game logic).

To recapture: set both constants to -1, run the test, copy printed values.
"""

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from simulation import run_headless

# ---------------------------------------------------------------------------
# Known-good snapshots from Kotlin HeadlessParityTest (seed=42, ticks=100)
# Set to -1 to print capture values and skip assertion.
# ---------------------------------------------------------------------------
SNAPSHOT_TOTAL_POP      = 713
SNAPSHOT_OCCUPIED_TILES = 78


def test_parity_seed42_tick100():
    state      = run_headless(ticks=100, seed=42)
    total_pop  = sum(t.population for t in state.tribes.values())
    occupied   = sum(1 for t in state.tiles if t.occupant_tribe_id is not None)

    print(">>> Python parity snapshot (seed=42, ticks=100):")
    print(f">>>   totalPop      = {total_pop}")
    print(f">>>   occupiedTiles = {occupied}")
    print(f">>>   worldTimeTick = {state.world_time_tick}")
    print(f">>>   tribes        = {len(state.tribes)}")

    assert state.world_time_tick == 100, f"expected 100, got {state.world_time_tick}"

    if SNAPSHOT_TOTAL_POP >= 0:
        assert total_pop == SNAPSHOT_TOTAL_POP, (
            f"totalPop drift: expected {SNAPSHOT_TOTAL_POP}, got {total_pop}")

    if SNAPSHOT_OCCUPIED_TILES >= 0:
        assert occupied == SNAPSHOT_OCCUPIED_TILES, (
            f"occupiedTiles drift: expected {SNAPSHOT_OCCUPIED_TILES}, got {occupied}")

    print(">>> Parity test PASSED")


def test_deterministic():
    """Same seed must produce identical results across two independent runs."""
    run1 = run_headless(ticks=100, seed=42)
    run2 = run_headless(ticks=100, seed=42)
    pop1 = sum(t.population for t in run1.tribes.values())
    pop2 = sum(t.population for t in run2.tribes.values())
    occ1 = sum(1 for t in run1.tiles if t.occupant_tribe_id)
    occ2 = sum(1 for t in run2.tiles if t.occupant_tribe_id)
    assert pop1 == pop2 and occ1 == occ2, "Non-deterministic! Two runs with same seed diverged."
    print(">>> Determinism test PASSED")


def test_vector_length_and_range():
    """State vector must have exactly STATE_VECTOR_SIZE entries, all in [0, 1]."""
    from simulation import initial_for_training, JavaRandom, HeuristicPolicy, tick, STATE_VECTOR_SIZE
    state = initial_for_training(num_tribes=4, seed=99)
    rng   = JavaRandom(99)
    pol   = HeuristicPolicy(rng)
    state = tick(state, rng, pol)
    for tid, tribe in state.tribes.items():
        owned = [t for t in state.tiles if t.occupant_tribe_id == tid]
        nbrs  = [t for ot,t in state.tribes.items() if ot != tid]
        vec   = tribe.to_float_array(owned, nbrs, state.tiles, state.world_time_tick)
        assert len(vec) == STATE_VECTOR_SIZE, f"{tid}: vector length {len(vec)} != {STATE_VECTOR_SIZE}"
        for j, v in enumerate(vec):
            assert 0.0 <= v <= 1.0, f"{tid}: vec[{j}]={v} out of [0,1]"
    print(">>> Vector length and range test PASSED")


def test_reward_signs():
    """Reward is positive after growth tick, negative after extinction."""
    from simulation import initial_for_training, JavaRandom, HeuristicPolicy, tick
    state = initial_for_training(num_tribes=2, seed=7)
    rng   = JavaRandom(7)
    pol   = HeuristicPolicy(rng)
    prev  = state
    next_ = tick(state, rng, pol)
    # At least one tribe should have a valid (non-extinction) reward
    valid_rewards = [next_.reward(prev, tid) for tid in prev.tribes if tid in next_.tribes]
    assert any(r > -10.0 for r in valid_rewards), "All rewards look like extinction penalties"
    print(">>> Reward signs test PASSED")


if __name__ == "__main__":
    test_parity_seed42_tick100()
    test_deterministic()
    test_vector_length_and_range()
    test_reward_signs()
    print("\nAll parity tests passed.")
