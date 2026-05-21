from simulation import initial, BiomeType, GRID_COLS

state = initial()
tribe = list(state.tribes.values())[0]
tid = tribe.tribe_id

occ = [t for t in state.tiles if t.occupant_tribe_id == tid]
print(f"Starting territory: {len(occ)} tiles")
print(f"Personality: {tribe.personality.archetype_id}")

print("\nOwned tile details:")
for t in sorted(occ, key=lambda x: (x.col, x.row)):
    print(f"  id={t.id:3d} col={t.col:2d} row={t.row} biome={t.biome.value:10s} moisture={t.soil_moisture}")

print("\nAll Desert tiles in world:")
for t in sorted(state.tiles, key=lambda x: (x.col, x.row)):
    if t.biome == BiomeType.Desert:
        owned = "(OWNED)" if t.occupant_tribe_id == tid else ""
        print(f"  id={t.id:3d} col={t.col:2d} row={t.row} {owned}")

print(f"\nStarting cols: {sorted(set(t.col for t in occ))}")
print(f"Desert owned cols: {sorted(set(t.col for t in occ if t.biome == BiomeType.Desert))}")
