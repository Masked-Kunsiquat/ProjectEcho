package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.rules.getBorderTiles
import com.github.maskedkunisquat.projectecho.domain.rules.getNeighbors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileNeighborsTest {

    // Grid is 16 cols × 6 rows = 96 hex tiles.
    // id = row * 16 + col; neighbors computed by flat-top even-q offset.
    // Interior tile: 6 neighbors. Edge/corner tiles: 2–5 depending on position and col parity.

    @Test
    fun `interior tile has 6 neighbors`() {
        val tileId = 3 * 16 + 8  // row=3, col=8 (even)
        assertEquals(6, getNeighbors(tileId).size)
    }

    @Test
    fun `interior odd-col tile has 6 neighbors`() {
        val tileId = 3 * 16 + 7  // row=3, col=7 (odd)
        assertEquals(6, getNeighbors(tileId).size)
    }

    @Test
    fun `left edge tile has 4 neighbors`() {
        val tileId = 3 * 16 + 0  // row=3, col=0 (even; SW and NW cut)
        assertEquals(4, getNeighbors(tileId).size)
    }

    @Test
    fun `right edge tile has 4 neighbors`() {
        val tileId = 3 * 16 + 15  // row=3, col=15 (odd; NE and SE cut)
        assertEquals(4, getNeighbors(tileId).size)
    }

    @Test
    fun `top edge even-col tile has 3 neighbors`() {
        val tileId = 0 * 16 + 8  // row=0, col=8 (even; N, NE, NW cut)
        assertEquals(3, getNeighbors(tileId).size)
    }

    @Test
    fun `top edge odd-col tile has 5 neighbors`() {
        val tileId = 0 * 16 + 7  // row=0, col=7 (odd; only N cut)
        assertEquals(5, getNeighbors(tileId).size)
    }

    @Test
    fun `bottom edge even-col tile has 5 neighbors`() {
        val tileId = 5 * 16 + 8  // row=5, col=8 (even; only S cut)
        assertEquals(5, getNeighbors(tileId).size)
    }

    @Test
    fun `bottom edge odd-col tile has 3 neighbors`() {
        val tileId = 5 * 16 + 7  // row=5, col=7 (odd; SE, S, SW cut)
        assertEquals(3, getNeighbors(tileId).size)
    }

    @Test
    fun `corner top-left has 2 neighbors`() {
        // col=0 (even), row=0: SE=(1,0), S=(0,1) only valid
        assertEquals(2, getNeighbors(0 * 16 + 0).size)
    }

    @Test
    fun `corner top-right has 3 neighbors`() {
        // col=15 (odd), row=0: S=(15,1), SW=(14,1), NW=(14,0) valid
        assertEquals(3, getNeighbors(0 * 16 + 15).size)
    }

    @Test
    fun `corner bottom-left has 3 neighbors`() {
        // col=0 (even), row=5: N=(0,4), NE=(1,4), SE=(1,5) valid
        assertEquals(3, getNeighbors(5 * 16 + 0).size)
    }

    @Test
    fun `corner bottom-right has 2 neighbors`() {
        // col=15 (odd), row=5: N=(15,4) and NW=(14,5) only valid
        assertEquals(2, getNeighbors(5 * 16 + 15).size)
    }

    @Test
    fun `tile is never its own neighbor`() {
        listOf(0, 1, 50, 80, 95).forEach { tileId ->
            assertFalse("tile $tileId should not be its own neighbor", tileId in getNeighbors(tileId))
        }
    }

    @Test
    fun `neighbors are symmetric`() {
        // If B is a neighbor of A, then A is a neighbor of B
        val tileId = 3 * 16 + 8
        for (neighborId in getNeighbors(tileId)) {
            assertTrue(
                "tile $tileId should be in neighbors of $neighborId",
                tileId in getNeighbors(neighborId),
            )
        }
    }

    // --- getBorderTiles ---

    private fun tile(id: Int, col: Int, row: Int, owner: String? = null) =
        MapTile(id = id, col = col, row = row, occupantTribeId = owner)

    @Test
    fun `getBorderTiles returns empty when tile has no occupant`() {
        val tiles = listOf(
            tile(0, col = 0, row = 0),                        // unowned
            tile(1, col = 1, row = 0, owner = "beta"),        // neighbor, different tribe
        )
        assertEquals(emptyList<Int>(), getBorderTiles(0, tiles))
    }

    @Test
    fun `getBorderTiles returns empty when no adjacent different-tribe tiles`() {
        // tile 0 (col=0,row=0) and tile 1 (col=1,row=0) are hex neighbors; both own by alpha
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "alpha"),
        )
        assertEquals(emptyList<Int>(), getBorderTiles(0, tiles))
    }

    @Test
    fun `getBorderTiles returns ids of adjacent different-tribe tiles`() {
        // tile 0 (col=0,row=0, alpha) and tile 1 (col=1,row=0, beta) are hex neighbors
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
        )
        val borders = getBorderTiles(0, tiles)
        assertTrue(1 in borders)
        assertFalse(0 in borders)
    }

    @Test
    fun `getBorderTiles excludes unoccupied neighbors`() {
        val tiles = listOf(
            tile(0,  col = 0, row = 0, owner = "alpha"),  // owned by alpha
            tile(1,  col = 1, row = 0),                   // adjacent, unoccupied
            tile(16, col = 0, row = 1, owner = "beta"),   // adjacent S neighbor, different tribe
        )
        val borders = getBorderTiles(0, tiles)
        assertTrue(16 in borders)
        assertFalse(1 in borders)
        assertFalse(0 in borders)
    }
}
