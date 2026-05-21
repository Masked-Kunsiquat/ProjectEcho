from simulation import initial, JavaRandom, HeuristicPolicy, tick, BiomeType, _env_food_mult as env_phase_food_multiplier

state = initial()
rng   = JavaRandom(42)
pol   = HeuristicPolicy(rng)

for i in range(50):
    state = tick(state, rng, pol)
    tribe = list(state.tribes.values())[0] if state.tribes else None
    if not tribe:
        print(f"T{i+1}: EXTINCT"); break
    occ = [t for t in state.tiles if t.occupant_tribe_id == tribe.tribe_id]
    avg_m = sum(t.soil_moisture for t in occ) // len(occ) if occ else 0

    # compute what farming should produce
    soph_b = tribe.personality.sophistication * 0.02
    mults  = [env_phase_food_multiplier(t.soil_moisture) * (1.0 + soph_b * tribe.personality.affinity_for(t.biome))
              for t in occ]
    eff_m  = sum(mults)/len(mults) if mults else 1.0
    farmers= min(tribe.population, len(occ)*10) if occ else tribe.population
    import math
    farmed = math.floor(farmers * 0.70 * eff_m + 0.5)

    # Count parched tiles
    parched = sum(1 for t in occ if t.soil_moisture < 21)

    print(f"T{i+1:3d}: pop={tribe.population:4d} food={tribe.food_supply:5d} tiles={len(occ):3d} "
          f"avg_m={avg_m:3d} parched={parched} eff_m={eff_m:.3f} farmed~{farmed}")
