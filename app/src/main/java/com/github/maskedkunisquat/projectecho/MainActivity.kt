package com.github.maskedkunisquat.projectecho

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.github.maskedkunisquat.projectecho.data.db.AppDatabase
import com.github.maskedkunisquat.projectecho.data.repository.RoomWorldStateRepository
import com.github.maskedkunisquat.projectecho.domain.rules.EventParser
import com.github.maskedkunisquat.projectecho.domain.rules.PersonalityParser
import com.github.maskedkunisquat.projectecho.feature.dashboard.DashboardScreen
import com.github.maskedkunisquat.projectecho.feature.dashboard.GameViewModel
import com.github.maskedkunisquat.projectecho.feature.dashboard.GameViewModelFactory
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory(
            RoomWorldStateRepository(
                AppDatabase.getInstance(applicationContext).worldStateDao()
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open("events.json").bufferedReader().use { EventParser.parse(it.readText()) }
                }.getOrElse { e -> Log.e("MainActivity", "Failed to load events.json", e); emptyList() }
            }
            viewModel.setSimEvents(events)

            val personalityList = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open("personalities.json").bufferedReader().use { PersonalityParser.parse(it.readText()) }
                }.getOrElse { e -> Log.e("MainActivity", "Failed to load personalities.json", e); emptyList() }
            }
            viewModel.setPersonalities(personalityList)
        }

        setContent {
            ProjectEchoTheme {
                val worldState by viewModel.worldState.collectAsState()
                val mapOverlay by viewModel.mapOverlay.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                var isChronicleVisible by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    DashboardScreen(
                        worldState = worldState,
                        onTickPressed = { viewModel.triggerTick() },
                        onActionPressed = { action, tileId, tribeId -> viewModel.applyDivineAction(action, tileId, tribeId) },
                        snackbarHostState = snackbarHostState,
                        isChronicleVisible = isChronicleVisible,
                        onShowChronicle = { isChronicleVisible = true },
                        onDismissChronicle = { isChronicleVisible = false },
                        mapOverlay = mapOverlay,
                        onOverlaySelected = { viewModel.setMapOverlay(it) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
