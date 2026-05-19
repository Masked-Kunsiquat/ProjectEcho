package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_ROWS
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.getNeighbors
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme

@Composable
internal fun TribalGridMap(
    tiles: List<MapTile>,
    activeFront: WeatherFront? = null,
    hoveredTileId: Int? = null,
    onTilePressed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val occupiedColor  = MaterialTheme.colorScheme.primary
    val emptyColor     = MaterialTheme.colorScheme.surfaceVariant
    val separatorColor = MaterialTheme.colorScheme.background
    val rainOutline    = Color(0xFF4499FF)
    val heatOutline    = Color(0xFFFF6600)
    val haloFill       = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
    val haloOutline    = Color.White.copy(alpha = 0.85f)

    val tileMap = remember(tiles) { tiles.associateBy { it.id } }
    val hoveredCluster = remember(hoveredTileId) {
        hoveredTileId?.let { id -> (listOf(id) + getNeighbors(id)).toHashSet() } ?: emptySet()
    }

    Canvas(
        modifier = modifier.pointerInput(onTilePressed) {
            detectTapGestures { offset ->
                onTilePressed(
                    hitTestTile(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                )
            }
        }
    ) {
        val cellW  = size.width  / GRID_COLS
        val cellH  = size.height / GRID_ROWS
        val stroke = Stroke(width = 1.dp.toPx())

        // Pass 1: draw all tiles
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                val x0 = col * cellW
                val x1 = (col + 1) * cellW
                val y0 = row * cellH
                val y1 = (row + 1) * cellH

                val idxA = 2 * (row * GRID_COLS + col)
                val idxB = idxA + 1

                val pathA = trianglePath(idxA, x0, x1, y0, y1, row, col)
                val pathB = trianglePath(idxB, x0, x1, y0, y1, row, col)

                val colorA = if (tileMap[idxA]?.occupantTribeId != null) occupiedColor else emptyColor
                val colorB = if (tileMap[idxB]?.occupantTribeId != null) occupiedColor else emptyColor

                drawPath(pathA, colorA)
                drawPath(pathB, colorB)
                drawPath(pathA, separatorColor, style = stroke)
                drawPath(pathB, separatorColor, style = stroke)
            }
        }

        // Pass 2: amber halo overlay for touched cluster
        if (hoveredCluster.isNotEmpty()) {
            val haloStroke = Stroke(width = 1.5.dp.toPx())
            for (tileId in hoveredCluster) {
                val cellIdx = tileId / 2
                val tileRow = cellIdx / GRID_COLS
                val tileCol = cellIdx % GRID_COLS
                val x0 = tileCol * cellW
                val x1 = (tileCol + 1) * cellW
                val y0 = tileRow * cellH
                val y1 = (tileRow + 1) * cellH
                val path = trianglePath(tileId, x0, x1, y0, y1, tileRow, tileCol)
                drawPath(path, haloFill)
                drawPath(path, haloOutline, style = haloStroke)
            }
        }

        // Pass 3: weather front column outline
        if (activeFront != null) {
            val outlineColor = if (activeFront.type == WeatherType.RainCloud) rainOutline else heatOutline
            drawRect(
                color = outlineColor,
                topLeft = Offset(activeFront.column * cellW, 0f),
                size = Size(cellW, size.height),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private fun trianglePath(
    tileId: Int,
    x0: Float, x1: Float,
    y0: Float, y1: Float,
    row: Int, col: Int,
): Path = Path().apply {
    val isA = tileId % 2 == 0
    if ((row + col) % 2 == 0) {
        if (isA) { moveTo(x0, y0); lineTo(x1, y0); lineTo(x0, y1) }
        else     { moveTo(x1, y0); lineTo(x1, y1); lineTo(x0, y1) }
    } else {
        if (isA) { moveTo(x0, y0); lineTo(x1, y0); lineTo(x1, y1) }
        else     { moveTo(x0, y0); lineTo(x1, y1); lineTo(x0, y1) }
    }
    close()
}

private fun hitTestTile(x: Float, y: Float, canvasWidth: Float, canvasHeight: Float): Int {
    val cellW  = canvasWidth  / GRID_COLS
    val cellH  = canvasHeight / GRID_ROWS
    val col    = (x / cellW).toInt().coerceIn(0, GRID_COLS - 1)
    val row    = (y / cellH).toInt().coerceIn(0, GRID_ROWS - 1)
    val localX = (x - col * cellW) / cellW
    val localY = (y - row * cellH) / cellH
    val idxA   = 2 * (row * GRID_COLS + col)
    val idxB   = idxA + 1
    return if ((row + col) % 2 == 0) {
        if (localX + localY <= 1f) idxA else idxB
    } else {
        if (localX >= localY) idxA else idxB
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
