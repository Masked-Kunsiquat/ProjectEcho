from simulation import initial, JavaRandom, HeuristicPolicy, tick, BiomeType, _env_food_mult

state = initial()
rng   = JavaRandom(42)
pol   = HeuristicPolicy(rng)

for i in range(100):
    state = tick(state, rng, pol)
    tribe = next(iter(state.tribes.values()), None)
    if not tribe:
        print(f"T{i+1}: EXTINCT")
        break
    occ = [t for t in state.tiles if t.occupant_tribe_id == tribe.tribe_id]
    avg_m = sum(t.soil_moisture for t in occ) // len(occ) if occ else 0
    parched = sum(1 for t in occ if t.soil_moisture < 21)
    # show weather front if active
    front = f" front={state.active_front.type.value}@col{state.active_front.column}" if state.active_front else ""
    print(f"T{i+1:3d}: pop={tribe.population:4d} food={tribe.food_supply:5d} tiles={len(occ):3d} "
          f"avg_m={avg_m:3d} parched={parched}{front}")
