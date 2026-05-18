package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme

@Composable
internal fun HistoryLedger(
    entries: List<String>,
    modifier: Modifier = Modifier,
) {
    val reversed = remember(entries) { entries.asReversed() }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(reversed, key = { _, entry -> entry }) { index, entry ->
            LedgerEntry(text = entry, isNew = index == 0)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun LedgerEntry(text: String, isNew: Boolean) {
    var visible by remember { mutableStateOf(!isNew) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "entry_fade",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .padding(vertical = 4.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F, name = "HistoryLedger")
@Composable
private fun HistoryLedgerPreview() {
    ProjectEchoTheme {
        HistoryLedger(
            entries = listOf(
                "The tribe has grown to 120 souls.",
                "A great famine begins.",
                "The gods have spoken.",
            ),
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
    }
}
