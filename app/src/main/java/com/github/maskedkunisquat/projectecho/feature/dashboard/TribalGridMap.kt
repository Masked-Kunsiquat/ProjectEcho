package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_SIZE
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.getNeighbors
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme
import kotlin.math.sqrt

internal val biomeColorWater   = Color(0xFF1A4D8F)
internal val biomeColorDesert  = Color(0xFFD4A96A)
internal val biomeColorForest  = Color(0xFF1A5C2A)
internal val biomeColorCoast   = Color(0xFF1A7080)

internal val climateParched  = Color(0xFFCC3333)
internal val climateFertile  = Color(0xFF33BB55)
internal val climateDeluge   = Color(0xFF3355BB)

@Composable
internal fun TribalGridMap(
    tiles: List<MapTile>,
    activeFront: WeatherFront? = null,
    hoveredTileId: Int? = null,
    overlay: MapOverlay = MapOverlay.Default,
    tribeColors: Map<String, Color> = emptyMap(),
    onTilePressed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val emptyColor       = MaterialTheme.colorScheme.surfaceVariant
    val occupiedFallback = MaterialTheme.colorScheme.primary
    val separatorColor   = MaterialTheme.colorScheme.background
    val haloFill    = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
    val haloOutline = Color.White.copy(alpha = 0.85f)
    val rainFill    = Color(0x554499FF)
    val heatFill    = Color(0x55FF6600)

    val tileMap = remember(tiles) { tiles.associateBy { it.id } }
    val hoveredCluster = remember(hoveredTileId) {
        hoveredTileId?.let { id -> (listOf(id) + getNeighbors(id)).toHashSet() } ?: emptySet<Int>()
    }

    val frontCol = activeFront?.column?.toFloat() ?: -2f
    val animatedFrontCol by animateFloatAsState(targetValue = frontCol, label = "weatherFront")

    Canvas(
        modifier = modifier.pointerInput(onTilePressed) {
            detectTapGestures { offset ->
                onTilePressed(
                    hitTestHex(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                )
            }
        }
    ) {
        val R    = size.width / (2f + 1.5f * (GRID_COLS - 1))
        val hexH = R * sqrt(3f)
        val stroke = Stroke(width = 1.dp.toPx())

        // Pass 1: draw all hex tiles
        for (tile in tiles) {
            val (cx, cy) = hexCenter(tile.col, tile.row, R, hexH)
            val path  = hexPath(cx, cy, R, hexH)
            val color = tileDisplayColor(tile, overlay, tribeColors, emptyColor, occupiedFallback)
            drawPath(path, color)
            drawPath(path, separatorColor, style = stroke)
        }

        // Pass 2: halo overlay for touched cluster
        if (hoveredCluster.isNotEmpty()) {
            val haloStroke = Stroke(width = 1.5.dp.toPx())
            for (tileId in hoveredCluster) {
                val t = tileMap[tileId] ?: continue
                val (cx, cy) = hexCenter(t.col, t.row, R, hexH)
                val path = hexPath(cx, cy, R, hexH)
                drawPath(path, haloFill)
                drawPath(path, haloOutline, style = haloStroke)
            }
        }

        // Pass 3: animated weather front glow column
        if (activeFront != null && animatedFrontCol >= -1f) {
            val fillColor = if (activeFront.type == WeatherType.RainCloud) rainFill else heatFill
            val cx       = R + animatedFrontCol * 1.5f * R
            val bandHalf = R * 1.5f
            val left     = (cx - bandHalf).coerceAtLeast(0f)
            val right    = (cx + bandHalf).coerceAtMost(size.width)
            drawRect(
                color    = fillColor,
                topLeft  = Offset(left, 0f),
                size     = Size(right - left, size.height),
            )
        }
    }
}

private fun hexCenter(col: Int, row: Int, R: Float, hexH: Float): Pair<Float, Float> {
    val cx = R + col * 1.5f * R
    val cy = if (col % 2 == 0) hexH * 0.5f + row * hexH else hexH + row * hexH
    return cx to cy
}

private fun hexPath(cx: Float, cy: Float, R: Float, hexH: Float): Path = Path().apply {
    val hR = R * 0.5f
    val hH = hexH * 0.5f
    moveTo(cx + hR, cy - hH)  // top-right
    lineTo(cx + R,  cy)        // right
    lineTo(cx + hR, cy + hH)  // bottom-right
    lineTo(cx - hR, cy + hH)  // bottom-left
    lineTo(cx - R,  cy)        // left
    lineTo(cx - hR, cy - hH)  // top-left
    close()
}

private fun tileDisplayColor(
    tile: MapTile,
    overlay: MapOverlay,
    tribeColors: Map<String, Color>,
    emptyColor: Color,
    occupiedFallback: Color,
): Color = when (overlay) {
    MapOverlay.Default -> when (val owner = tile.occupantTribeId) {
        null -> emptyColor
        else -> tribeColors[owner] ?: occupiedFallback
    }
    MapOverlay.Biome      -> biomeColor(tile.biome, emptyColor)
    MapOverlay.Climate    -> climateColor(tile.soilMoisture)
    MapOverlay.Volatility -> volatilityColor(tile.volatility)
}

private fun biomeTerrainTint(biome: BiomeType): Color? = when (biome) {
    BiomeType.Grassland -> null
    BiomeType.Forest    -> biomeColorForest
    BiomeType.Desert    -> biomeColorDesert
    BiomeType.Coast     -> biomeColorCoast
    BiomeType.Water     -> biomeColorWater
}

private fun biomeColor(biome: BiomeType, emptyColor: Color): Color = when (biome) {
    BiomeType.Grassland -> emptyColor
    BiomeType.Forest    -> biomeColorForest
    BiomeType.Desert    -> biomeColorDesert
    BiomeType.Coast     -> biomeColorCoast
    BiomeType.Water     -> biomeColorWater
}

private fun climateColor(moisture: Int): Color {
    val t = moisture / 100f
    return when {
        t <= 0.5f -> lerp(climateParched, climateFertile, t * 2f)
        else      -> lerp(climateFertile, climateDeluge, (t - 0.5f) * 2f)
    }
}

private fun volatilityColor(volatility: Int): Color {
    val brightness = 0.25f + (volatility / 100f) * 0.70f
    return Color(brightness, brightness, brightness)
}

private fun hitTestHex(x: Float, y: Float, canvasWidth: Float, canvasHeight: Float): Int {
    val R    = canvasWidth / (2f + 1.5f * (GRID_COLS - 1))
    val hexH = R * sqrt(3f)
    var bestId     = 0
    var bestDistSq = Float.MAX_VALUE
    for (id in 0 until GRID_SIZE) {
        val col = id % GRID_COLS
        val row = id / GRID_COLS
        val cx = R + col * 1.5f * R
        val cy = if (col % 2 == 0) hexH * 0.5f + row * hexH else hexH + row * hexH
        val dx = x - cx
        val dy = y - cy
        val distSq = dx * dx + dy * dy
        if (distSq < bestDistSq) {
            bestDistSq = distSq
            bestId = id
        }
    }
    return bestId
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
