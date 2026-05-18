package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme
import kotlin.random.Random

private const val MAX_POPULATION = 500
private const val GRID_COLS = 10
private const val GRID_ROWS = 10
private const val GRID_SIZE = GRID_COLS * GRID_ROWS  // 100 total cells

@Composable
internal fun TribalGridMap(
    tribeName: String,
    population: Int,
    modifier: Modifier = Modifier,
) {
    val occupiedColor = MaterialTheme.colorScheme.primary
    val emptyColor    = MaterialTheme.colorScheme.surfaceVariant

    // Seeded from tribe name so each tribe has a stable, unique spread pattern.
    // Re-computed only when the tribe name changes.
    val cellOrder = remember(tribeName) {
        (0 until GRID_SIZE).shuffled(Random(tribeName.hashCode()))
    }

    val claimedCells = (population.coerceIn(0, MAX_POPULATION) * GRID_SIZE) / MAX_POPULATION
    val occupiedIndices = remember(claimedCells, cellOrder) {
        cellOrder.take(claimedCells).toHashSet()
    }

    Canvas(modifier = modifier) {
        val gap   = 2.dp.toPx()
        val cellW = (size.width  - gap * (GRID_COLS + 1)) / GRID_COLS
        val cellH = (size.height - gap * (GRID_ROWS + 1)) / GRID_ROWS

        var cellIndex = 0
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                drawRect(
                    color   = if (cellIndex in occupiedIndices) occupiedColor else emptyColor,
                    topLeft = Offset(gap + col * (cellW + gap), gap + row * (cellH + gap)),
                    size    = Size(cellW, cellH),
                )
                cellIndex++
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "TribalGridMap - Early (pop 50)")
@Composable
private fun TribalGridMapEarlyPreview() {
    ProjectEchoTheme {
        TribalGridMap(tribeName = "The Iron-Wrought", population = 50, modifier = Modifier.size(200.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "TribalGridMap - Mid (pop 250)")
@Composable
private fun TribalGridMapMidPreview() {
    ProjectEchoTheme {
        TribalGridMap(tribeName = "The Iron-Wrought", population = 250, modifier = Modifier.size(200.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "TribalGridMap - Late (pop 450)")
@Composable
private fun TribalGridMapLatePreview() {
    ProjectEchoTheme {
        TribalGridMap(tribeName = "The Iron-Wrought", population = 450, modifier = Modifier.size(200.dp))
    }
}
