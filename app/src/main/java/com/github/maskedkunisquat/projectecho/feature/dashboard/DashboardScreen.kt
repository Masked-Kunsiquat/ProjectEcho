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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
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
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribeNeed
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.ui.theme.TRIBE_COLORS
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme

// U+26A1 + U+FE0E forces text presentation so the glyph inherits Compose color styling
private const val FAVOR_ICON = "⚡︎"

// Need indicator glyphs — U+FE0E forces text presentation where needed
private const val ICON_PARCHED     = "☀︎"  // U+2600 + U+FE0E
private const val ICON_WATERLOGGED = "〰"   // U+3030 wavy dash
private const val ICON_HUNGRY      = "⊙"   // U+2299
private const val ICON_STARVING    = "☠︎"  // U+2620 + U+FE0E
private const val ICON_ENDANGERED  = "⚠︎"  // U+26A0 + U+FE0E
private const val ICON_THREAT      = "⚔︎"  // U+2694 + U+FE0E
private const val ICON_SPIRITUAL   = "✦"   // U+2726
private const val ICON_CROWDED     = "⊕"   // U+2295

private fun TribeNeed.icon(): String? = when (this) {
    TribeNeed.Parched             -> ICON_PARCHED
    TribeNeed.Waterlogged         -> ICON_WATERLOGGED
    TribeNeed.Hungry              -> ICON_HUNGRY
    TribeNeed.Starving            -> ICON_STARVING
    TribeNeed.Endangered          -> ICON_ENDANGERED
    TribeNeed.UnderThreat         -> ICON_THREAT
    TribeNeed.SpirituallyDepleted -> ICON_SPIRITUAL
    TribeNeed.Overcrowded         -> ICON_CROWDED
    TribeNeed.Thriving            -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    worldState: WorldState,
    onTickPressed: () -> Unit,
    onActionPressed: (DivineAction, Int?, String?) -> Unit,
    snackbarHostState: SnackbarHostState,
    isChronicleVisible: Boolean,
    onShowChronicle: () -> Unit,
    onDismissChronicle: () -> Unit,
    rlDebugLog: List<String> = emptyList(),
    isDebugLogVisible: Boolean = false,
    onShowDebugLog: () -> Unit = {},
    onDismissDebugLog: () -> Unit = {},
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

    if (isDebugLogVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismissDebugLog,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = "Policy Log",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HistoryLedger(
                entries = rlDebugLog.ifEmpty { listOf("No data yet — waiting for first RL tick.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .padding(horizontal = 16.dp),
            )
        }
    }

    // Stable color assignment: existing IDs keep their slot; stale IDs are pruned; new IDs get the next slot.
    val colorAssignments = remember { mutableMapOf<String, Color>() }
    colorAssignments.keys.retainAll(worldState.tribes.keys)
    worldState.tribes.keys.forEach { id ->
        if (id !in colorAssignments) {
            val usedColors = colorAssignments.values.toSet()
            colorAssignments[id] = TRIBE_COLORS.firstOrNull { it !in usedColors } ?: TRIBE_COLORS.last()
        }
    }
    val tribeColorMap: Map<String, Color> = colorAssignments

    var hoveredTileId by remember { mutableStateOf<Int?>(null) }
    var detailTribeId by remember { mutableStateOf<String?>(null) }
    var selectedTribeId by remember { mutableStateOf<String?>(null) }
    detailTribeId?.let { tribeId ->
        worldState.tribes[tribeId]?.let { tribe ->
            val occupiedTiles = worldState.tiles.filter { it.occupantTribeId == tribeId }
            val avgMoisture = if (occupiedTiles.isEmpty()) 35
                              else occupiedTiles.sumOf { it.soilMoisture } / occupiedTiles.size
            TribeDetailSheet(
                tribe = tribe,
                tilesOccupied = occupiedTiles.size,
                environmentalPhase = EnvironmentalPhase.from(avgMoisture),
                tribeAge = worldState.worldTimeTick - tribe.foundedTick,
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

        // Aspect-ratio for flat-top hex grid: (2 + 1.5*(cols-1)) / (sqrt(3) * (rows+0.5)) ≈ 2.18
        TribalGridMap(
            tiles = worldState.tiles,
            activeFront = worldState.activeFront,
            hoveredTileId = hoveredTileId,
            overlay = mapOverlay,
            tribeColors = tribeColorMap,
            onTilePressed = { hoveredTileId = it },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.18f),
        )

        // Tribe legend strip — scrollable for future multi-tribe support
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(worldState.tribes.values.toList(), key = { it.tribeId }) { tribe ->
                val ownedTiles = worldState.tiles.filter { it.occupantTribeId == tribe.tribeId }
                val needs = tribe.needs(ownedTiles, worldState.worldTimeTick)
                TribeLegendChip(
                    tribe = tribe,
                    tribeColor = tribeColorMap[tribe.tribeId] ?: TRIBE_COLORS[0],
                    needs = needs,
                    selected = selectedTribeId == tribe.tribeId,
                    onSelect = {
                        selectedTribeId = if (selectedTribeId == tribe.tribeId) null else tribe.tribeId
                    },
                    onOpenDetail = { detailTribeId = tribe.tribeId },
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
                onActionPressed(action, hoveredTileId, selectedTribeId)
                hoveredTileId = null
                selectedTribeId = null
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
            OutlinedButton(
                onClick = onShowDebugLog,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text("Policy Log")
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
    tribeAge: Long,
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
            StatRow(label = "Age",            value = "$tribeAge ticks")
            StatRow(label = "Archetype",      value = tribe.personality.archetypeId.replaceFirstChar { it.uppercase() })
            ProgressStatRow(label = "Devotion",       value = tribe.devotion,                   maxValue = 100)
            ProgressStatRow(label = "Sophistication", value = tribe.personality.sophistication, maxValue = 10)
            ProgressStatRow(label = "Skepticism",     value = tribe.personality.skepticism,     maxValue = 100)
        }
    }
}

@Composable
private fun TribeLegendChip(
    tribe: Tribe,
    tribeColor: Color,
    needs: Set<TribeNeed>,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val borderWidth = if (selected) 2.dp else 1.dp
    val needIcons = needs.mapNotNull { it.icon() }.joinToString("")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(tribeColor, CircleShape),
        )
        Text(
            text = tribe.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (needIcons.isNotEmpty()) {
            Text(
                text = needIcons,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "▸",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onOpenDetail),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPanel(
    divineFavor: Int,
    onActionPressed: (DivineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        Triple(DivineAction.CastRain,      "Rain",       "+25 moisture on target tiles"),
        Triple(DivineAction.BlessHarvest,  "Harvest",    "+200 food, +8 moisture on cluster"),
        Triple(DivineAction.InspireDevout, "Inspire",    "+15 devotion, no skepticism penalty"),
        Triple(DivineAction.CauseFamine,   "Famine",     "-80 food, -15 moisture on cluster"),
        Triple(DivineAction.SendPlague,    "Plague",     "-20% population"),
        Triple(DivineAction.Fortify,       "Fortify",    "5-tick raid immunity"),
        Triple(DivineAction.Blight,        "Blight",     "-30 moisture, triggers natural famine"),
        Triple(DivineAction.Revelation,    "Revelation", "-20 skepticism, +10 devotion"),
        Triple(DivineAction.Smite,         "Smite",      "Clears tiles, kills 15% of occupants"),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (action, label, description) ->
                    ActionChip(
                        label = label,
                        cost = action.favorCost,
                        enabled = divineFavor >= action.favorCost,
                        onClick = { onActionPressed(action) },
                        tooltip = description,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionChip(
    label: String,
    cost: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    tooltip: String,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
                         else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (enabled) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant

    // Outer Box owns the layout — TooltipBox sits inside as a pure interaction layer.
    Box(
        modifier = modifier
            .aspectRatio(1.6f),
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
            onActionPressed = { _, _, _ -> },  // preview stub
            snackbarHostState = remember { SnackbarHostState() },
            isChronicleVisible = false,
            onShowChronicle = {},
            onDismissChronicle = {},
            onOverlaySelected = {},
        )
    }
}
