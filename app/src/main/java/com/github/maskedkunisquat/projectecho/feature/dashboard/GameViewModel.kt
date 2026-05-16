package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private val _worldState = MutableStateFlow(
        WorldState(
            worldTimeTick = 0L,
            divineFavor = 10,
            tribe = Tribe(
                name = "The Iron-Wrought",
                population = 100,
                devotion = 50,
                foodSupply = 500
            )
        )
    )
    val worldState: StateFlow<WorldState> = _worldState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(2_000L)
                triggerTick()
            }
        }
    }

    fun triggerTick() {
        _worldState.value = tick(_worldState.value)
    }
}
