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
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme
import kotlin.math.sqrt
import kotlin.random.Random

private const val MAX_POPULATION = 500
private const val GRID_COLS = 16
private const val GRID_ROWS = 6
private const val GRID_SIZE = GRID_COLS * GRID_ROWS * 2  // 192 triangles

@Composable
internal fun TribalGridMap(
    tribeName: String,
    population: Int,
    modifier: Modifier = Modifier,
) {
    val occupiedColor  = MaterialTheme.colorScheme.primary
    val emptyColor     = MaterialTheme.colorScheme.surfaceVariant
    val separatorColor = MaterialTheme.colorScheme.background

    // Sort triangles by jittered distance from a tribe-seeded start cell so territory
    // grows as a connected blob from a homeland rather than scattering randomly.
    val cellOrder = remember(tribeName) {
        val rng = Random(tribeName.hashCode())
        val startRow = rng.nextInt(GRID_ROWS)
        val startCol = rng.nextInt(GRID_COLS)
        (0 until GRID_SIZE).sortedBy { idx ->
            val cellIdx = idx / 2
            val row = cellIdx / GRID_COLS
            val col = cellIdx % GRID_COLS
            val dRow = (row - startRow).toFloat()
            val dCol = (col - startCol).toFloat()
            sqrt((dRow * dRow + dCol * dCol).toDouble()).toFloat() + rng.nextFloat() * 1.5f
        }
    }

    val claimedCells = (population.coerceIn(0, MAX_POPULATION) * GRID_SIZE) / MAX_POPULATION
    val occupiedIndices = remember(claimedCells, cellOrder) {
        cellOrder.take(claimedCells).toHashSet()
    }

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

                drawPath(pathA, if (idxA in occupiedIndices) occupiedColor else emptyColor)
                drawPath(pathB, if (idxB in occupiedIndices) occupiedColor else emptyColor)
                drawPath(pathA, separatorColor, style = stroke)
                drawPath(pathB, separatorColor, style = stroke)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "TribalGridMap - Early (pop 50)")
@Composable
private fun TribalGridMapEarlyPreview() {
    ProjectEchoTheme {
        TribalGridMap(tribeName = "The Iron-Wrought", population = 50,
            modifier = Modifier.fillMaxWidth().height(120.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "TribalGridMap - Mid (pop 250)")
@Composable
private fun TribalGridMapMidPreview() {
    ProjectEchoTheme {
        TribalGridMap(tribeName = "The Iron-Wrought", population = 250,
            modifier = Modifier.fillMaxWidth().height(120.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "TribalGridMap - Late (pop 450)")
@Composable
private fun TribalGridMapLatePreview() {
    ProjectEchoTheme {
        TribalGridMap(tribeName = "The Iron-Wrought", population = 450,
            modifier = Modifier.fillMaxWidth().height(120.dp))
    }
}
