package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

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
) {
    companion object {
        fun initial(): WorldState {
            val tribeId = "iron-wrought"
            val tribeName = TribeNameGenerator.generate(tribeId.hashCode())
            val population = 100

            val rng = Random(tribeId.hashCode())
            val cellCount = GRID_COLS * GRID_ROWS  // 96 cells, each with 2 triangle tiles

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

            // Step 2: Coast — land cells with at least one Water neighbour (N/S/E/W)
            val coastCells = (0 until cellCount).filter { ci ->
                if (ci in waterCells) return@filter false
                val c = ci % GRID_COLS
                val r = ci / GRID_COLS
                waterCells.any { wci ->
                    val wc = wci % GRID_COLS
                    val wr = wci / GRID_COLS
                    (abs(c - wc) == 1 && r == wr) || (c == wc && abs(r - wr) == 1)
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

            // Precompute one score per triangle; Water tiles are pushed to the end.
            val orderedIds = (0 until GRID_SIZE).map { idx ->
                val cellIdx = idx / 2
                val row = cellIdx / GRID_COLS
                val col = cellIdx % GRID_COLS
                val score = if (biomeMap[cellIdx] == BiomeType.Water) {
                    Float.MAX_VALUE
                } else {
                    val dRow = (row - startRow).toFloat()
                    val dCol = (col - startCol).toFloat()
                    sqrt((dRow * dRow + dCol * dCol).toDouble()).toFloat() + rng.nextFloat() * 1.5f
                }
                idx to score
            }.sortedBy { (_, score) -> score }.map { (idx, _) -> idx }
            val occupiedIds = orderedIds.take(claimedCount).toHashSet()

            val tiles = (0 until GRID_SIZE).map { id ->
                val cellIdx = id / 2
                val biome = biomeMap[cellIdx] ?: BiomeType.Grassland
                MapTile(
                    id = id,
                    col = cellIdx % GRID_COLS,
                    row = cellIdx / GRID_COLS,
                    biome = biome,
                    occupantTribeId = if (id in occupiedIds && biome != BiomeType.Water) tribeId else null,
                )
            }

            val tribe = Tribe(
                tribeId = tribeId,
                name = tribeName,
                population = population,
                devotion = 50,
                foodSupply = 500,
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
