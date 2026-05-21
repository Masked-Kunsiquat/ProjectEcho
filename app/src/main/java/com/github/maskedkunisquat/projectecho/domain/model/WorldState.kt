package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.sqrt
import kotlin.random.Random

private const val TRAINING_EXTINCTION_PENALTY = -10f

@Serializable
data class WorldState(
    val worldTimeTick: Long,
    val divineFavor: Int,
    val tiles: List<MapTile>,
    val tribes: Map<String, Tribe>,
    val eventHistory: List<String> = emptyList(),
    val eventCooldowns: Map<String, Long> = emptyMap(),
    val activeFront: WeatherFront? = null,
    val nextSpawnTick: Long = 10L,
    val lastSplitTick: Long = 0L,
) {
    companion object {
        fun initialForTraining(numTribes: Int, seed: Long): WorldState {
            require(numTribes in 1..Tribe.MAX_TRIBES) {
                "numTribes must be in [1, ${Tribe.MAX_TRIBES}]"
            }
            val rng = Random(seed)
            val cellCount = GRID_COLS * GRID_ROWS

            val waterCells = mutableSetOf<Int>()
            val blobCount = rng.nextInt(1, 3)
            repeat(blobCount) {
                val centre = rng.nextInt(cellCount)
                val centreRow = centre / GRID_COLS
                val centreCol = centre % GRID_COLS
                val blobSize = rng.nextInt(8, 13)
                val sorted = (0 until cellCount).map { ci ->
                    val r = ci / GRID_COLS; val c = ci % GRID_COLS
                    val dR = (r - centreRow).toFloat(); val dC = (c - centreCol).toFloat()
                    val score = sqrt((dR * dR + dC * dC).toDouble()).toFloat() + rng.nextFloat() * 0.8f
                    ci to score
                }.sortedBy { (_, s) -> s }.take(blobSize).map { (ci, _) -> ci }
                waterCells += sorted
            }

            val coastCells = (0 until cellCount).filter { ci ->
                if (ci in waterCells) return@filter false
                val c = ci % GRID_COLS; val r = ci / GRID_COLS
                val hexNeighbors = if (c % 2 == 0) listOf(
                    c to r-1, c+1 to r-1, c+1 to r,
                    c to r+1, c-1 to r,   c-1 to r-1,
                ) else listOf(
                    c to r-1, c+1 to r,   c+1 to r+1,
                    c to r+1, c-1 to r+1, c-1 to r,
                )
                hexNeighbors.any { (nc, nr) ->
                    nc in 0 until GRID_COLS && nr in 0 until GRID_ROWS &&
                    (nr * GRID_COLS + nc) in waterCells
                }
            }.toSet()

            val biomeMap: Map<Int, BiomeType> = (0 until cellCount).associate { ci ->
                ci to when {
                    ci in waterCells -> BiomeType.Water
                    ci in coastCells -> BiomeType.Coast
                    else -> when (rng.nextInt(10)) {
                        in 0..4 -> BiomeType.Grassland
                        in 5..7 -> BiomeType.Forest
                        else    -> BiomeType.Desert
                    }
                }
            }

            val tilesWorking = (0 until cellCount).map { id ->
                MapTile(id = id, col = id % GRID_COLS, row = id / GRID_COLS,
                        biome = biomeMap[id] ?: BiomeType.Grassland)
            }.toMutableList()

            // Pick numTribes start cells spread across the grid
            val landCells = (0 until cellCount).filter { biomeMap[it] != BiomeType.Water }
            val startCells = mutableListOf<Int>()
            val minDistSq = 9
            repeat(numTribes) {
                val candidates = if (startCells.isEmpty()) landCells else landCells.filter { ci ->
                    val r = ci / GRID_COLS; val c = ci % GRID_COLS
                    startCells.all { sc ->
                        val sr = sc / GRID_COLS; val sCol = sc % GRID_COLS
                        val dr = r - sr; val dc = c - sCol
                        dr * dr + dc * dc >= minDistSq
                    }
                }
                startCells += if (candidates.isNotEmpty()) candidates.random(rng) else landCells.random(rng)
            }

            val tribes = mutableMapOf<String, Tribe>()
            for ((idx, startCell) in startCells.withIndex()) {
                val tribeId = "training-tribe-$idx"
                val tribeName = TribeNameGenerator.generate(tribeId.hashCode())
                val personality = TribePersonality.ALL_ARCHETYPES[rng.nextInt(TribePersonality.ALL_ARCHETYPES.size)]
                val population = 100
                val claimCount = population * GRID_SIZE / 500

                val startRow = startCell / GRID_COLS; val startCol = startCell % GRID_COLS
                val available = tilesWorking.indices.filter {
                    tilesWorking[it].biome != BiomeType.Water && tilesWorking[it].occupantTribeId == null
                }
                val claimed = available.sortedBy { ci ->
                    val r = ci / GRID_COLS; val c = ci % GRID_COLS
                    val dr = (r - startRow).toFloat(); val dc = (c - startCol).toFloat()
                    dr * dr + dc * dc
                }.take(claimCount)

                for (tileIdx in claimed) {
                    tilesWorking[tileIdx] = tilesWorking[tileIdx].copy(occupantTribeId = tribeId)
                }
                tribes[tribeId] = Tribe(
                    tribeId = tribeId,
                    name = tribeName,
                    population = population,
                    devotion = 50,
                    foodSupply = 500,
                    personality = personality,
                )
            }

            return WorldState(
                worldTimeTick = 0L,
                divineFavor = 50,
                tiles = tilesWorking,
                tribes = tribes,
            )
        }

        fun initial(): WorldState {
            val tribeId = "iron-wrought"
            val tribeName = TribeNameGenerator.generate(tribeId.hashCode())
            val population = 100

            val rng = Random(tribeId.hashCode())
            val cellCount = GRID_COLS * GRID_ROWS  // 96 hex cells

            // --- Biome generation (cell-level, shared by both triangles in each cell) ---

            // Step 1: Water blobs — stamp 1–2 blob centres, mark nearest cells as Water
            val waterCells = mutableSetOf<Int>()
            val blobCount = rng.nextInt(1, 3)
            repeat(blobCount) {
                val centre = rng.nextInt(cellCount)
                val centreRow = centre / GRID_COLS
                val centreCol = centre % GRID_COLS
                val blobSize = rng.nextInt(8, 13)
                val sorted = (0 until cellCount).map { ci ->
                    val r = ci / GRID_COLS
                    val c = ci % GRID_COLS
                    val dR = (r - centreRow).toFloat()
                    val dC = (c - centreCol).toFloat()
                    val score = sqrt((dR * dR + dC * dC).toDouble()).toFloat() + rng.nextFloat() * 0.8f
                    ci to score
                }.sortedBy { (_, s) -> s }.take(blobSize).map { (ci, _) -> ci }
                waterCells += sorted
            }

            // Step 2: Coast — land cells with at least one Water hex-neighbour (6 directions)
            val coastCells = (0 until cellCount).filter { ci ->
                if (ci in waterCells) return@filter false
                val c = ci % GRID_COLS
                val r = ci / GRID_COLS
                val hexNeighbors = if (c % 2 == 0) listOf(
                    c to r-1, c+1 to r-1, c+1 to r,
                    c to r+1, c-1 to r,   c-1 to r-1,
                ) else listOf(
                    c to r-1, c+1 to r,   c+1 to r+1,
                    c to r+1, c-1 to r+1, c-1 to r,
                )
                hexNeighbors.any { (nc, nr) ->
                    nc in 0 until GRID_COLS && nr in 0 until GRID_ROWS &&
                    (nr * GRID_COLS + nc) in waterCells
                }
            }.toSet()

            // Step 3: Distribute Grassland / Forest / Desert among remaining land cells
            val biomeMap: Map<Int, BiomeType> = (0 until cellCount).associate { ci ->
                ci to when {
                    ci in waterCells -> BiomeType.Water
                    ci in coastCells -> BiomeType.Coast
                    else -> when (rng.nextInt(10)) {
                        in 0..4 -> BiomeType.Grassland
                        in 5..7 -> BiomeType.Forest
                        else    -> BiomeType.Desert
                    }
                }
            }

            // --- Territory generation — Water cells are impassable ---
            val startRow = rng.nextInt(GRID_ROWS)
            val startCol = rng.nextInt(GRID_COLS)
            val claimedCount = (population * GRID_SIZE) / 500

            // Score each hex tile; Water tiles are pushed to the end.
            val orderedIds = (0 until GRID_SIZE).map { id ->
                val row = id / GRID_COLS
                val col = id % GRID_COLS
                val score = if (biomeMap[id] == BiomeType.Water) {
                    Float.MAX_VALUE
                } else {
                    val dRow = (row - startRow).toFloat()
                    val dCol = (col - startCol).toFloat()
                    sqrt((dRow * dRow + dCol * dCol).toDouble()).toFloat() + rng.nextFloat() * 1.5f
                }
                id to score
            }.sortedBy { (_, score) -> score }.map { (id, _) -> id }
            val occupiedIds = orderedIds.take(claimedCount).toHashSet()

            val tiles = (0 until GRID_SIZE).map { id ->
                val biome = biomeMap[id] ?: BiomeType.Grassland
                MapTile(
                    id = id,
                    col = id % GRID_COLS,
                    row = id / GRID_COLS,
                    biome = biome,
                    occupantTribeId = if (id in occupiedIds && biome != BiomeType.Water) tribeId else null,
                )
            }

            val personality = TribePersonality.ALL_ARCHETYPES[rng.nextInt(TribePersonality.ALL_ARCHETYPES.size)]
            val tribe = Tribe(
                tribeId = tribeId,
                name = tribeName,
                population = population,
                devotion = 50,
                foodSupply = 500,
                personality = personality,
            )

            return WorldState(
                worldTimeTick = 0L,
                divineFavor = 50,
                tiles = tiles,
                tribes = mapOf(tribeId to tribe),
            )
        }
    }
}

/**
 * Per-tick reward for [tribeId]: population delta + territory delta.
 * Returns [TRAINING_EXTINCTION_PENALTY] if the tribe is absent from this state (went extinct).
 */
fun WorldState.reward(prev: WorldState, tribeId: String): Float {
    if (tribeId !in tribes) return TRAINING_EXTINCTION_PENALTY
    val prevTribe = prev.tribes[tribeId] ?: return TRAINING_EXTINCTION_PENALTY
    val thisTribe = tribes.getValue(tribeId)
    val newPop = thisTribe.population
    val prevPop = prevTribe.population
    val newTiles = tiles.count { it.occupantTribeId == tribeId }
    val prevTiles = prev.tiles.count { it.occupantTribeId == tribeId }
    return (newPop - prevPop + newTiles - prevTiles).toFloat()
}
