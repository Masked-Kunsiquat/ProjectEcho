package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.sqrt
import kotlin.random.Random

@Serializable
data class WorldState(
    val worldTimeTick: Long,
    val divineFavor: Int,
    val tiles: List<MapTile>,
    val tribes: Map<String, Tribe>,
    val eventHistory: List<String> = emptyList(),
    val firedEventIds: Set<String> = emptySet(),
    val activeFront: WeatherFront? = null,
) {
    companion object {
        fun initial(): WorldState {
            val tribeId = "iron-wrought"
            val tribeName = "The Iron-Wrought"
            val population = 100

            val rng = Random(tribeId.hashCode())
            val startRow = rng.nextInt(GRID_ROWS)
            val startCol = rng.nextInt(GRID_COLS)
            val claimedCount = (population * GRID_SIZE) / 500

            // Precompute one score per triangle so sortedBy reads each score exactly once.
            val orderedIds = (0 until GRID_SIZE).map { idx ->
                val cellIdx = idx / 2
                val row = cellIdx / GRID_COLS
                val col = cellIdx % GRID_COLS
                val dRow = (row - startRow).toFloat()
                val dCol = (col - startCol).toFloat()
                val score = sqrt((dRow * dRow + dCol * dCol).toDouble()).toFloat() + rng.nextFloat() * 1.5f
                idx to score
            }.sortedBy { (_, score) -> score }.map { (idx, _) -> idx }
            val occupiedIds = orderedIds.take(claimedCount).toHashSet()

            val tiles = (0 until GRID_SIZE).map { id ->
                val cellIdx = id / 2
                MapTile(
                    id = id,
                    col = cellIdx % GRID_COLS,
                    row = cellIdx / GRID_COLS,
                    occupantTribeId = if (id in occupiedIds) tribeId else null,
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
