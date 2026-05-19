package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_ROWS
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme

@Composable
internal fun TribalGridMap(
    tiles: List<MapTile>,
    modifier: Modifier = Modifier,
) {
    val occupiedColor  = MaterialTheme.colorScheme.primary
    val emptyColor     = MaterialTheme.colorScheme.surfaceVariant
    val separatorColor = MaterialTheme.colorScheme.background

    val tileMap = remember(tiles) { tiles.associateBy { it.id } }

    Canvas(modifier = modifier) {
        val cellW  = size.width  / GRID_COLS
        val cellH  = size.height / GRID_ROWS
        val stroke = Stroke(width = 1.dp.toPx())

        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                val x0 = col * cellW
                val x1 = (col + 1) * cellW
                val y0 = row * cellH
                val y1 = (row + 1) * cellH

                val idxA = 2 * (row * GRID_COLS + col)
                val idxB = idxA + 1

                val pathA = Path()
                val pathB = Path()

                if ((row + col) % 2 == 0) {
                    // ╲ split
                    pathA.apply { moveTo(x0, y0); lineTo(x1, y0); lineTo(x0, y1); close() }
                    pathB.apply { moveTo(x1, y0); lineTo(x1, y1); lineTo(x0, y1); close() }
                } else {
                    // ╱ split
                    pathA.apply { moveTo(x0, y0); lineTo(x1, y0); lineTo(x1, y1); close() }
                    pathB.apply { moveTo(x0, y0); lineTo(x1, y1); lineTo(x0, y1); close() }
                }

                val colorA = if (tileMap[idxA]?.occupantTribeId != null) occupiedColor else emptyColor
                val colorB = if (tileMap[idxB]?.occupantTribeId != null) occupiedColor else emptyColor

                drawPath(pathA, colorA)
                drawPath(pathB, colorB)
                drawPath(pathA, separatorColor, style = stroke)
                drawPath(pathB, separatorColor, style = stroke)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "TribalGridMap - Initial State")
@Composable
private fun TribalGridMapPreview() {
    ProjectEchoTheme {
        TribalGridMap(
            tiles = WorldState.initial().tiles,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
    }
}
