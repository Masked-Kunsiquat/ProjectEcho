package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_ROWS

fun getNeighbors(
    tileId: Int,
    cols: Int = GRID_COLS,
    rows: Int = GRID_ROWS,
): List<Int> {
    val cellIdx = tileId / 2
    val row = cellIdx / cols
    val col = cellIdx % cols

    return buildList {
        add(tileId xor 1)  // partner triangle in the same cell
        if (row > 0)        { val c = (row - 1) * cols + col; add(c * 2); add(c * 2 + 1) }
        if (row < rows - 1) { val c = (row + 1) * cols + col; add(c * 2); add(c * 2 + 1) }
        if (col > 0)        { val c = row * cols + (col - 1); add(c * 2); add(c * 2 + 1) }
        if (col < cols - 1) { val c = row * cols + (col + 1); add(c * 2); add(c * 2 + 1) }
    }
}
