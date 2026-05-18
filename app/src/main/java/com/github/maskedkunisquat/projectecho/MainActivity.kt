package com.github.maskedkunisquat.projectecho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.github.maskedkunisquat.projectecho.domain.rules.EventParser
import com.github.maskedkunisquat.projectecho.feature.dashboard.DashboardScreen
import com.github.maskedkunisquat.projectecho.feature.dashboard.GameViewModel
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open("events.json").bufferedReader().use { reader ->
                        EventParser.parse(reader.readText())
                    }
                }.getOrDefault(emptyList())
            }
            viewModel.setSimEvents(events)
        }

        setContent {
            ProjectEchoTheme {
                val worldState by viewModel.worldState.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        worldState = worldState,
                        onTickPressed = { viewModel.triggerTick() },
                        onActionPressed = { viewModel.applyDivineAction(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
