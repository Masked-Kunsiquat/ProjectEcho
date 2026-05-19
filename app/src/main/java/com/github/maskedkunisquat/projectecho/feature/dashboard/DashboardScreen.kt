package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    worldState: WorldState,
    onTickPressed: () -> Unit,
    onActionPressed: (DivineAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    isChronicleVisible: Boolean,
    onShowChronicle: () -> Unit,
    onDismissChronicle: () -> Unit,
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

    val tribe = worldState.tribes.values.firstOrNull() ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "PROJECT ECHO", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Tick ${worldState.worldTimeTick}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(text = tribe.name, style = MaterialTheme.typography.displayLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow(label = "Population", value = tribe.population.toString())
                StatRow(label = "Food Supply", value = tribe.foodSupply.toString())
                ProgressStatRow(label = "Devotion",     value = tribe.devotion,       maxValue = 100)
                ProgressStatRow(label = "Divine Favor", value = worldState.divineFavor, maxValue = 100)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                ActionPanel(
                    divineFavor = worldState.divineFavor,
                    onActionPressed = onActionPressed,
                    modifier = Modifier.padding(12.dp),
                )
            }

            TribalGridMap(
                tiles = worldState.tiles,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
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
private fun ActionPanel(
    divineFavor: Int,
    onActionPressed: (DivineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        DivineAction.CastRain      to "Cast Rain",
        DivineAction.BlessHarvest  to "Bless Harvest",
        DivineAction.InspireDevout to "Inspire Devout",
        DivineAction.CauseFamine   to "Cause Famine",
        DivineAction.SendPlague    to "Send Plague",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Divine Interventions", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.take(3).forEach { (action, label) ->
                ActionButton(
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
                ActionButton(
                    label = label,
                    cost = action.favorCost,
                    enabled = divineFavor >= action.favorCost,
                    onClick = { onActionPressed(action) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    cost: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = "($cost favor)", style = MaterialTheme.typography.labelSmall)
        }
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

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "Dashboard - Full")
@Composable
private fun DashboardScreenPreview() {
    ProjectEchoTheme {
        DashboardScreen(
            worldState = WorldState.initial(),
            onTickPressed = {},
            onActionPressed = {},
            snackbarHostState = remember { SnackbarHostState() },
            isChronicleVisible = false,
            onShowChronicle = {},
            onDismissChronicle = {},
        )
    }
}
