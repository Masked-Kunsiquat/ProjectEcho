"""
Trace the world generation RNG calls to see exactly what Python produces.
This helps verify that Kotlin would produce the same world.
"""
from simulation import JavaRandom, java_hashcode, GRID_COLS, GRID_ROWS, BiomeType
import math

tribe_id = "iron-wrought"
seed = java_hashcode(tribe_id)
print(f"Seed = {seed} (hex: {hex(seed & 0xFFFFFFFF)})")

rng = JavaRandom(seed)
print(f"Initial internal seed: {rng._seed} (hex: {hex(rng._seed)})")
print()

cell_count = GRID_COLS * GRID_ROWS

# Step 1: Water blobs
blob_count = rng.next_int_range(1, 3)
print(f"Blob count: {blob_count}")

water_cells = set()
for b in range(blob_count):
    centre = rng.next_int(cell_count)
    crow, ccol = centre // GRID_COLS, centre % GRID_COLS
    blob_size = rng.next_int_range(8, 13)
    print(f"  Blob {b}: centre={centre} (row={crow},col={ccol}), size={blob_size}")
    scores = []
    for ci in range(cell_count):
        r, c = ci // GRID_COLS, ci % GRID_COLS
        score = math.sqrt(float(r-crow)**2 + float(c-ccol)**2) + rng.next_float() * 0.8
        scores.append((ci, score))
    scores.sort(key=lambda x: x[1])
    for ci, _ in scores[:blob_size]:
        water_cells.add(ci)
    print(f"    Water cells added: {sorted(scores[:blob_size], key=lambda x: x[0])[:5]}...")

print(f"Total water cells: {len(water_cells)}")

# Step 2: Coast cells (no RNG)
coast_cells = set()
for ci in range(cell_count):
    if ci in water_cells: continue
    c, r = ci % GRID_COLS, ci // GRID_COLS
    if c % 2 == 0: nbrs = [(c,r-1),(c+1,r-1),(c+1,r),(c,r+1),(c-1,r),(c-1,r-1)]
    else:           nbrs = [(c,r-1),(c+1,r),(c+1,r+1),(c,r+1),(c-1,r+1),(c-1,r)]
    if any(0<=nc<GRID_COLS and 0<=nr<GRID_ROWS and nr*GRID_COLS+nc in water_cells for nc,nr in nbrs):
        coast_cells.add(ci)
print(f"Coast cells: {len(coast_cells)}")

# Step 3: Biomes
biome_map = {}
for ci in range(cell_count):
    if ci in water_cells: biome_map[ci] = BiomeType.Water
    elif ci in coast_cells: biome_map[ci] = BiomeType.Coast
    else:
        v = rng.next_int(10)
        biome_map[ci] = BiomeType.Grassland if v <= 4 else (BiomeType.Forest if v <= 7 else BiomeType.Desert)

desert_count = sum(1 for b in biome_map.values() if b == BiomeType.Desert)
print(f"Desert tiles: {desert_count}")
print(f"Biome distribution: Water={sum(1 for b in biome_map.values() if b==BiomeType.Water)}, "
      f"Coast={sum(1 for b in biome_map.values() if b==BiomeType.Coast)}, "
      f"Grassland={sum(1 for b in biome_map.values() if b==BiomeType.Grassland)}, "
      f"Forest={sum(1 for b in biome_map.values() if b==BiomeType.Forest)}, "
      f"Desert={desert_count}")

# Step 4: Start position
start_row = rng.next_int(GRID_ROWS)
start_col = rng.next_int(GRID_COLS)
claimed_count = 100 * cell_count // 500  # population=100
print(f"\nStart position: row={start_row}, col={start_col}")
print(f"Claimed count: {claimed_count}")

# Step 5: Territory scoring
scores = []
for id_ in range(cell_count):
    row, col = id_ // GRID_COLS, id_ % GRID_COLS
    if biome_map.get(id_) == BiomeType.Water:
        scores.append((id_, float('inf')))
    else:
        dr, dc = float(row-start_row), float(col-start_col)
        s = math.sqrt(dr*dr+dc*dc) + rng.next_float() * 1.5
        scores.append((id_, s))
scores.sort(key=lambda x: x[1])
occupied_ids = {id_ for id_,_ in scores[:claimed_count]}

print(f"\nOwned tiles ({len(occupied_ids)}):")
owned = [(id_, biome_map[id_]) for id_ in sorted(occupied_ids)]
for id_, biome in owned:
    print(f"  id={id_:3d} col={id_%GRID_COLS:2d} row={id_//GRID_COLS} biome={biome.value}")

desert_owned = [(id_, biome_map[id_]) for id_, b in owned if b == BiomeType.Desert]
print(f"\nDesert tiles in territory: {len(desert_owned)}")

# Step 6: Personality
from simulation import ALL_ARCHETYPES
pers_idx = rng.next_int(len(ALL_ARCHETYPES))
print(f"\nPersonality index: {pers_idx} ({ALL_ARCHETYPES[pers_idx]().archetype_id})")
print(f"Desert affinity: {ALL_ARCHETYPES[pers_idx]().affinity_for(BiomeType.Desert)}")
