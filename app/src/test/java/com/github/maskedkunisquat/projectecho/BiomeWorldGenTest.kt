package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.GRID_SIZE
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BiomeWorldGenTest {

    private val world = WorldState.initial()

    @Test
    fun `all tiles have a biome field`() {
        assertEquals(GRID_SIZE, world.tiles.size)
    }

    @Test
    fun `Water tiles always have null occupantTribeId`() {
        world.tiles.filter { it.biome == BiomeType.Water }.forEach { tile ->
            assertNull("Water tile id=${tile.id} should have no occupant", tile.occupantTribeId)
        }
    }

    @Test
    fun `every land tile adjacent to Water is Coast`() {
        val waterPositions = world.tiles
            .filter { it.biome == BiomeType.Water }
            .map { it.col to it.row }
            .toSet()

        world.tiles
            .filter { tile ->
                tile.biome != BiomeType.Water &&
                waterPositions.any { (wc, wr) ->
                    (abs(tile.col - wc) == 1 && tile.row == wr) ||
                    (tile.col == wc && abs(tile.row - wr) == 1)
                }
            }
            .forEach { tile ->
                assertEquals(
                    "Tile at col=${tile.col} row=${tile.row} is adjacent to Water but biome=${tile.biome}",
                    BiomeType.Coast,
                    tile.biome,
                )
            }
    }

    @Test
    fun `world generation is deterministic - same seed produces same biomes`() {
        val world2 = WorldState.initial()
        assertEquals(world.tiles.map { it.biome }, world2.tiles.map { it.biome })
    }

    @Test
    fun `world contains at least one Water tile`() {
        assertTrue("Expected at least one Water tile", world.tiles.any { it.biome == BiomeType.Water })
    }

    @Test
    fun `world contains at least 3 distinct biome types`() {
        val biomes = world.tiles.map { it.biome }.toSet()
        assertTrue("Expected ≥3 biome types, got: $biomes", biomes.size >= 3)
    }
}
