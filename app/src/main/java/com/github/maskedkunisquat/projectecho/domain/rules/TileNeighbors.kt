package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_ROWS
import com.github.maskedkunisquat.projectecho.domain.model.MapTile

fun getBorderTiles(tileId: Int, tiles: List<MapTile>): List<Int> {
    val tileMap = tiles.associateBy { it.id }
    val ownerId = tileMap[tileId]?.occupantTribeId ?: return emptyList()
    return getNeighbors(tileId).filter { neighborId ->
        val neighbor = tileMap[neighborId]
        neighbor?.occupantTribeId != null && neighbor.occupantTribeId != ownerId
    }
}

// Flat-top hexagons, even-q offset (even columns are not shifted vertically).
fun getNeighbors(
    tileId: Int,
    cols: Int = GRID_COLS,
    rows: Int = GRID_ROWS,
): List<Int> {
    require(cols > 0) { "cols must be positive, was $cols" }
    require(rows > 0) { "rows must be positive, was $rows" }
    require(tileId in 0 until cols * rows) { "tileId $tileId out of range [0, ${cols * rows})" }
    val col = tileId % cols
    val row = tileId / cols

    val dirs = if (col % 2 == 0) listOf(
        col     to row - 1,  // N
        col + 1 to row - 1,  // NE
        col + 1 to row,      // SE
        col     to row + 1,  // S
        col - 1 to row,      // SW
        col - 1 to row - 1,  // NW
    ) else listOf(
        col     to row - 1,  // N
        col + 1 to row,      // NE
        col + 1 to row + 1,  // SE
        col     to row + 1,  // S
        col - 1 to row + 1,  // SW
        col - 1 to row,      // NW
    )

    return dirs
        .filter { (c, r) -> c in 0 until cols && r in 0 until rows }
        .map { (c, r) -> r * cols + c }
}
