package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.repository.WorldStateRepository
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages live game state and drives the simulation tick loop for the Dashboard.
 *
 * On init, restores any persisted [WorldState] from [repository] before starting the
 * auto-tick loop. Each tick saves the new state to [repository] as a fire-and-forget
 * IO coroutine.
 */
class GameViewModel(
    private val repository: WorldStateRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

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
            // Restore saved state before the first tick fires.
            withContext(ioDispatcher) { repository.load() }
                ?.let { saved -> _worldState.value = saved }
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
     * Immediately advances the simulation by one tick and persists the result.
     *
     * Called automatically every 2 seconds by the init loop and also by the Manual Tick button.
     */
    fun triggerTick() {
        val action = pendingAction
        pendingAction = null
        val newState = tick(_worldState.value, action, simEvents)
        _worldState.value = newState
        viewModelScope.launch(ioDispatcher) {
            repository.save(newState)
        }
    }

    override fun onCleared() {
        super.onCleared() // cancels viewModelScope, ending the tick loop and any in-flight saves
    }
}
