package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.rules.getNeighbors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileNeighborsTest {

    // Grid is 16 cols × 6 rows = 96 cells, 192 triangles.
    // cellIdx = row*16+col; tileA = cellIdx*2, tileB = cellIdx*2+1
    // Neighbor counts (partner=1, each adjacent cell=2):
    //   interior      = 1 + 2+2+2+2 = 9
    //   edge (non-corner) = 1 + 2+2+2 = 7
    //   corner        = 1 + 2+2 = 5

    @Test
    fun `interior tile has 9 neighbors`() {
        val tileId = (3 * 16 + 8) * 2  // row=3, col=8, triangle A
        assertEquals(9, getNeighbors(tileId).size)
    }

    @Test
    fun `left edge tile has 7 neighbors`() {
        val tileId = (3 * 16 + 0) * 2  // row=3, col=0
        assertEquals(7, getNeighbors(tileId).size)
    }

    @Test
    fun `right edge tile has 7 neighbors`() {
        val tileId = (3 * 16 + 15) * 2  // row=3, col=15
        assertEquals(7, getNeighbors(tileId).size)
    }

    @Test
    fun `top edge tile has 7 neighbors`() {
        val tileId = (0 * 16 + 8) * 2  // row=0, col=8
        assertEquals(7, getNeighbors(tileId).size)
    }

    @Test
    fun `bottom edge tile has 7 neighbors`() {
        val tileId = (5 * 16 + 8) * 2  // row=5, col=8
        assertEquals(7, getNeighbors(tileId).size)
    }

    @Test
    fun `corner tile has 5 neighbors`() {
        assertEquals(5, getNeighbors(0).size)                     // top-left, tile A
        assertEquals(5, getNeighbors((0 * 16 + 15) * 2).size)    // top-right
        assertEquals(5, getNeighbors((5 * 16 + 0) * 2).size)     // bottom-left
        assertEquals(5, getNeighbors((5 * 16 + 15) * 2).size)    // bottom-right
    }

    @Test
    fun `partner triangle in same cell is always a neighbor`() {
        listOf(0, 50, 100, 191).forEach { tileId ->
            assertTrue("partner of $tileId should be a neighbor", (tileId xor 1) in getNeighbors(tileId))
        }
    }

    @Test
    fun `tile is never its own neighbor`() {
        listOf(0, 1, 50, 100, 191).forEach { tileId ->
            assertFalse("tile $tileId should not be its own neighbor", tileId in getNeighbors(tileId))
        }
    }

    @Test
    fun `triangle B of a corner cell has same count as triangle A`() {
        val tileA = 0  // top-left corner, triangle A
        val tileB = 1  // top-left corner, triangle B
        assertEquals(getNeighbors(tileA).size, getNeighbors(tileB).size)
    }
}
