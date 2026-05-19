package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.rules.territoryStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerritoryStepTest {

    private fun tile(id: Int, col: Int, row: Int, owner: String? = null) =
        MapTile(id = id, col = col, row = row, occupantTribeId = owner)

    private fun tribe(id: String, population: Int) =
        Tribe(tribeId = id, name = id, population = population, devotion = 50, foodSupply = 100)

    // GRID_SIZE=192: expected = population * 192 / 500
    // pop=1  → expected=0
    // pop=10 → expected=3

    @Test
    fun `tribe with fewer tiles than expected claims adjacent unclaimed tiles`() {
        // pop=10 → expected=3; tribe owns 1 tile, deficit=2
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),  // owned
            tile(1, col = 1, row = 0),                   // unclaimed, adjacent (dCol=1)
            tile(2, col = 0, row = 1),                   // unclaimed, adjacent (dRow=1)
            tile(3, col = 2, row = 0),                   // unclaimed, NOT adjacent (dCol=2)
        )
        val result = territoryStep(tiles, mapOf("alpha" to tribe("alpha", 10)))
        assertEquals(3, result.count { it.occupantTribeId == "alpha" })
        assertNull(result[3].occupantTribeId)
    }

    @Test
    fun `expansion does not claim another tribe's tile`() {
        // pop=10 → expected=3; "alpha" owns tile 0, "beta" blocks tile 1
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
            tile(2, col = 0, row = 1),
        )
        val result = territoryStep(tiles, mapOf(
            "alpha" to tribe("alpha", 10),
            "beta"  to tribe("beta",  10),
        ))
        assertEquals("alpha", result[2].occupantTribeId)
        assertEquals("beta",  result[1].occupantTribeId)
    }

    @Test
    fun `expansion stops gracefully when no adjacent unclaimed tiles remain`() {
        // pop=10 → expected=3; all neighbors belong to another tribe
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
            tile(2, col = 0, row = 1, owner = "beta"),
        )
        val result = territoryStep(tiles, mapOf("alpha" to tribe("alpha", 10)))
        assertEquals(1, result.count { it.occupantTribeId == "alpha" })
    }

    @Test
    fun `other triangle in same cell is treated as adjacent`() {
        // Two triangles share the same col/row — owning one should allow claiming the other
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),  // triangle A
            tile(1, col = 0, row = 0),                   // triangle B, same cell
        )
        val result = territoryStep(tiles, mapOf("alpha" to tribe("alpha", 10)))
        assertEquals("alpha", result[1].occupantTribeId)
    }

    @Test
    fun `shrink releases excess tiles`() {
        // pop=1 → expected=0; tribe owns 3 tiles, should release all
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "alpha"),
            tile(2, col = 2, row = 0, owner = "alpha"),
        )
        val result = territoryStep(tiles, mapOf("alpha" to tribe("alpha", 1)))
        assertEquals(0, result.count { it.occupantTribeId == "alpha" })
    }
}
