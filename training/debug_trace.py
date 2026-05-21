from simulation import initial, JavaRandom, HeuristicPolicy, tick, java_hashcode
from collections import Counter

# Verify XorWow: JavaRandom(42), first nextInt() should be 972016666
rng = JavaRandom(42)
print("XorWow check - JavaRandom(42) first nextInt():", rng.next_int())

print("\nhashcode('iron-wrought') =", java_hashcode("iron-wrought"))

state = initial()
rng2  = JavaRandom(42)
pol   = HeuristicPolicy(rng2)

tribe = next(iter(state.tribes.values()))
occ0  = [t for t in state.tiles if t.occupant_tribe_id == tribe.tribe_id]
print(f"T0: pop={tribe.population}, food={tribe.food_supply}, tiles={len(occ0)}")
print(f"  personality={tribe.personality.archetype_id}")
print(f"  world biomes: {dict(Counter(t.biome.value for t in state.tiles))}")
print(f"  owned biomes: {dict(Counter(t.biome.value for t in occ0))}")
print(f"  owned moistures: {sorted(t.soil_moisture for t in occ0)}")

for i in range(10):
    state = tick(state, rng2, pol)
    tribe = list(state.tribes.values())[0] if state.tribes else None
    if tribe:
        occ = [t for t in state.tiles if t.occupant_tribe_id == tribe.tribe_id]
        avg_m = sum(t.soil_moisture for t in occ) // len(occ) if occ else 0
        print(f"T{i+1}: pop={tribe.population}, food={tribe.food_supply:5d}, tiles={len(occ)}, avgMoisture={avg_m}")
