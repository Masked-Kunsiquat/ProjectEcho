package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.EnvironmentalPhase
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_ROWS
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme

// U+26A1 + U+FE0E forces text presentation so the glyph inherits Compose color styling
private const val FAVOR_ICON = "⚡︎"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    worldState: WorldState,
    onTickPressed: () -> Unit,
    onActionPressed: (DivineAction, Int?) -> Unit,
    snackbarHostState: SnackbarHostState,
    isChronicleVisible: Boolean,
    onShowChronicle: () -> Unit,
    onDismissChronicle: () -> Unit,
    mapOverlay: MapOverlay = MapOverlay.Default,
    onOverlaySelected: (MapOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(worldState.eventHistory.size) {
        if (worldState.eventHistory.isNotEmpty()) {
            snackbarHostState.showSnackbar(
                message = worldState.eventHistory.last(),
                duration = SnackbarDuration.Short,
            )
        }
    }

    if (isChronicleVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismissChronicle,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "Chronicle",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HistoryLedger(
                entries = worldState.eventHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    var hoveredTileId by remember { mutableStateOf<Int?>(null) }
    var detailTribeId by remember { mutableStateOf<String?>(null) }
    detailTribeId?.let { tribeId ->
        worldState.tribes[tribeId]?.let { tribe ->
            val occupiedTiles = worldState.tiles.filter { it.occupantTribeId == tribeId }
            val avgMoisture = if (occupiedTiles.isEmpty()) 35
                              else occupiedTiles.sumOf { it.soilMoisture } / occupiedTiles.size
            TribeDetailSheet(
                tribe = tribe,
                tilesOccupied = occupiedTiles.size,
                environmentalPhase = EnvironmentalPhase.from(avgMoisture),
                onDismiss = { detailTribeId = null },
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Global top bar: title left, ⚡favor + tick right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PROJECT ECHO",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$FAVOR_ICON${worldState.divineFavor}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "T:${worldState.worldTimeTick}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Overlay toggle row
        OverlayToggleRow(
            selected = mapOverlay,
            onSelect = onOverlaySelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // Aspect-ratio constrained so cells stay square (16×6 grid)
        TribalGridMap(
            tiles = worldState.tiles,
            activeFront = worldState.activeFront,
            hoveredTileId = hoveredTileId,
            overlay = mapOverlay,
            onTilePressed = { hoveredTileId = it },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(GRID_COLS.toFloat() / GRID_ROWS.toFloat()),
        )

        // Overlay colour legend
        MapOverlayLegend(
            overlay = mapOverlay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // Tribe legend strip — scrollable for future multi-tribe support
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(worldState.tribes.values.toList(), key = { it.tribeId }) { tribe ->
                TribeLegendChip(
                    tribe = tribe,
                    onClick = { detailTribeId = tribe.tribeId },
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        )

        // Action panel — uniform square chips with ⚡cost badge
        ActionPanel(
            divineFavor = worldState.divineFavor,
            onActionPressed = { action ->
                onActionPressed(action, hoveredTileId)
                hoveredTileId = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onShowChronicle,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text("Chronicle")
            }
            Button(
                onClick = onTickPressed,
                modifier = Modifier.weight(1f),
            ) {
                Text("Manual Tick")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TribeDetailSheet(
    tribe: Tribe,
    tilesOccupied: Int,
    environmentalPhase: EnvironmentalPhase,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = tribe.name, style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            StatRow(label = "Population",     value = tribe.population.toString())
            StatRow(label = "Food Supply",    value = tribe.foodSupply.toString())
            StatRow(label = "Tiles Occupied", value = tilesOccupied.toString())
            StatRow(label = "Environment",    value = environmentalPhase.displayName())
            ProgressStatRow(label = "Devotion", value = tribe.devotion, maxValue = 100)
        }
    }
}

@Composable
private fun TribeLegendChip(
    tribe: Tribe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column {
            Text(
                text = tribe.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Pop ${tribe.population} · Food ${tribe.foodSupply}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "▸",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionPanel(
    divineFavor: Int,
    onActionPressed: (DivineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        DivineAction.CastRain      to "Rain",
        DivineAction.BlessHarvest  to "Harvest",
        DivineAction.InspireDevout to "Inspire",
        DivineAction.CauseFamine   to "Famine",
        DivineAction.SendPlague    to "Plague",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.take(3).forEach { (action, label) ->
                ActionChip(
                    label = label,
                    cost = action.favorCost,
                    enabled = divineFavor >= action.favorCost,
                    onClick = { onActionPressed(action) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.drop(3).forEach { (action, label) ->
                ActionChip(
                    label = label,
                    cost = action.favorCost,
                    enabled = divineFavor >= action.favorCost,
                    onClick = { onActionPressed(action) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    cost: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (enabled) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "$FAVOR_ICON$cost", style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

@Composable
private fun ProgressStatRow(label: String, value: Int, maxValue: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = "$value / $maxValue", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { value.toFloat() / maxValue.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun EnvironmentalPhase.displayName(): String = when (this) {
    is EnvironmentalPhase.Deluge    -> "Deluge"
    is EnvironmentalPhase.Saturated -> "Saturated"
    is EnvironmentalPhase.Fertile   -> "Fertile"
    is EnvironmentalPhase.Parched   -> "Parched"
}

@Composable
private fun MapOverlayLegend(
    overlay: MapOverlay,
    modifier: Modifier = Modifier,
) {
    val occupied = MaterialTheme.colorScheme.primary
    val empty    = MaterialTheme.colorScheme.surfaceVariant

    val items: List<Pair<Color, String>> = when (overlay) {
        MapOverlay.Default    -> listOf(occupied to "Occupied", empty to "Empty")
        MapOverlay.Biome      -> listOf(
            empty            to "Grassland",
            biomeColorForest to "Forest",
            biomeColorDesert to "Desert",
            biomeColorCoast  to "Coast",
            biomeColorWater  to "Water",
        )
        MapOverlay.Climate    -> listOf(
            climateParched to "Parched",
            climateFertile to "Fertile",
            climateDeluge  to "Deluge",
        )
        MapOverlay.Volatility -> listOf(
            Color(0.25f, 0.25f, 0.25f) to "Low",
            Color(0.60f, 0.60f, 0.60f) to "Mid",
            Color(0.95f, 0.95f, 0.95f) to "High",
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (color, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverlayToggleRow(
    selected: MapOverlay,
    onSelect: (MapOverlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlays = listOf(
        MapOverlay.Default    to "Default",
        MapOverlay.Biome      to "Biome",
        MapOverlay.Climate    to "Climate",
        MapOverlay.Volatility to "Volatile",
    )
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        overlays.forEach { (overlay, label) ->
            val isSelected = selected == overlay
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                 else MaterialTheme.colorScheme.surface
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(containerColor)
                    .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(overlay) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "Dashboard - Full")
@Composable
private fun DashboardScreenPreview() {
    ProjectEchoTheme {
        DashboardScreen(
            worldState = WorldState.initial(),
            onTickPressed = {},
            onActionPressed = { _, _ -> },  // preview stub
            snackbarHostState = remember { SnackbarHostState() },
            isChronicleVisible = false,
            onShowChronicle = {},
            onDismissChronicle = {},
            onOverlaySelected = {},
        )
    }
}
