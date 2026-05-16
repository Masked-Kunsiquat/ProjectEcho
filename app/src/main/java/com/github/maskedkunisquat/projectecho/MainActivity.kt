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
import com.github.maskedkunisquat.projectecho.feature.dashboard.DashboardScreen
import com.github.maskedkunisquat.projectecho.feature.dashboard.GameViewModel
import com.github.maskedkunisquat.projectecho.ui.theme.ProjectEchoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjectEchoTheme {
                val worldState by viewModel.worldState.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        worldState = worldState,
                        onTickPressed = { viewModel.triggerTick() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}