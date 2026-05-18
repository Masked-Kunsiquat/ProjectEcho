package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages live game state and drives the simulation tick loop for the Dashboard.
 *
 * Exposes [worldState] as a read-only [StateFlow] the UI observes. The simulation
 * auto-advances every 2 seconds; the player can also queue a [DivineAction] via
 * [applyDivineAction] and manually advance via [triggerTick].
 */
class GameViewModel : ViewModel() {

    private val _worldState = MutableStateFlow(
        WorldState(
            worldTimeTick = 0L,
            divineFavor = 50,
            tribe = Tribe(
                name = "The Iron-Wrought",
                population = 100,
                devotion = 50,
                foodSupply = 500
            )
        )
    )
    /** Current game state; observed by the UI layer. */
    val worldState: StateFlow<WorldState> = _worldState.asStateFlow()

    private var pendingAction: DivineAction? = null

    @Volatile
    private var simEvents: List<SimEvent> = emptyList()

    init {
        viewModelScope.launch {
            while (true) {
                delay(2_000L)
                triggerTick()
            }
        }
    }

    /**
     * Loads the event definitions used to evaluate triggers each tick.
     *
     * Call once at startup after parsing `assets/events.json`; takes effect on the next tick.
     */
    fun setSimEvents(events: List<SimEvent>) {
        simEvents = events
    }

    /**
     * Queues a divine action to be applied on the next tick.
     *
     * Only one action can be pending at a time; a second call before the next tick
     * replaces the previous one.
     */
    fun applyDivineAction(action: DivineAction) {
        pendingAction = action
    }

    /**
     * Immediately advances the simulation by one tick.
     *
     * Consumes any pending [DivineAction], computes the next [WorldState], and emits it.
     * Called automatically every 2 seconds by the init loop and also by the Manual Tick button.
     */
    fun triggerTick() {
        val action = pendingAction
        pendingAction = null
        _worldState.value = tick(_worldState.value, action, simEvents)
    }
}
