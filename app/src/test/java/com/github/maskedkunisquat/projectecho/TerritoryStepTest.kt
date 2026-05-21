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

    // GRID_SIZE=96: expected = population * 96 / 500
    // pop=1  → expected=0
    // pop=16 → expected=3

    @Test
    fun `tribe with fewer tiles than expected claims adjacent unclaimed tiles`() {
        // pop=16 → expected=3; tribe owns 1 tile, deficit=2
        // In hex (even-q flat-top), neighbors of tile 0 (col=0,row=0, even col) are:
        //   SE=(1,0)=id=1 and S=(0,1)=id=16. id=2 (col=2,row=0) is NOT adjacent to id=0.
        val tiles = listOf(
            tile(0,  col = 0, row = 0, owner = "alpha"),  // owned
            tile(1,  col = 1, row = 0),                   // unclaimed, adjacent SE
            tile(16, col = 0, row = 1),                   // unclaimed, adjacent S
            tile(2,  col = 2, row = 0),                   // unclaimed, NOT adjacent to tile 0
        )
        val result = territoryStep(tiles, mapOf("alpha" to tribe("alpha", 16)))
        assertEquals(3, result.count { it.occupantTribeId == "alpha" })
        assertNull(result[3].occupantTribeId)
    }

    @Test
    fun `expansion does not claim another tribe's tile`() {
        // pop=16 → expected=3; "alpha" owns tile 0, "beta" occupies adjacent tile 1
        val tiles = listOf(
            tile(0,  col = 0, row = 0, owner = "alpha"),
            tile(1,  col = 1, row = 0, owner = "beta"),
            tile(16, col = 0, row = 1),
        )
        val result = territoryStep(tiles, mapOf(
            "alpha" to tribe("alpha", 16),
            "beta"  to tribe("beta",  16),
        ))
        assertEquals("alpha", result[2].occupantTribeId)  // tile 16 claimed
        assertEquals("beta",  result[1].occupantTribeId)  // tile 1 untouched
    }

    @Test
    fun `expansion stops gracefully when no adjacent unclaimed tiles remain`() {
        // pop=16 → expected=3; both adjacent tiles owned by beta → frontier empty, stays at 1
        val tiles = listOf(
            tile(0,  col = 0, row = 0, owner = "alpha"),
            tile(1,  col = 1, row = 0, owner = "beta"),
            tile(16, col = 0, row = 1, owner = "beta"),
        )
        val result = territoryStep(tiles, mapOf("alpha" to tribe("alpha", 16)))
        assertEquals(1, result.count { it.occupantTribeId == "alpha" })
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
