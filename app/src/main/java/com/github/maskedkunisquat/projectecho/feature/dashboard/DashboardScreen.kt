package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.WorldState

@Composable
fun DashboardScreen(
    worldState: WorldState,
    onTickPressed: () -> Unit,
    onActionPressed: (DivineAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Project Echo", style = MaterialTheme.typography.headlineLarge)
        HorizontalDivider()

        StatRow(label = "Tick", value = worldState.worldTimeTick.toString())
        StatRow(label = "Divine Favor", value = worldState.divineFavor.toString())

        Text(text = worldState.tribe.name, style = MaterialTheme.typography.headlineMedium)
        StatRow(label = "Population", value = worldState.tribe.population.toString())
        StatRow(label = "Devotion", value = worldState.tribe.devotion.toString())
        StatRow(label = "Food Supply", value = worldState.tribe.foodSupply.toString())

        HorizontalDivider()

        ActionPanel(
            divineFavor = worldState.divineFavor,
            onActionPressed = onActionPressed,
        )

        HorizontalDivider()

        Text(text = "Chronicle", style = MaterialTheme.typography.titleMedium)

        HistoryLedger(
            entries = worldState.eventHistory,
            modifier = Modifier.weight(1f),
        )

        Button(
            onClick = onTickPressed,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Manual Tick")
        }
    }
}

@Composable
private fun HistoryLedger(
    entries: List<String>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries.asReversed()) { entry ->
            Text(
                text = entry,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ActionPanel(
    divineFavor: Int,
    onActionPressed: (DivineAction) -> Unit,
) {
    val actions = listOf(
        DivineAction.CastRain to "Cast Rain",
        DivineAction.BlessHarvest to "Bless Harvest",
        DivineAction.InspireDevout to "Inspire Devout",
        DivineAction.CauseFamine to "Cause Famine",
        DivineAction.SendPlague to "Send Plague",
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Divine Interventions", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
