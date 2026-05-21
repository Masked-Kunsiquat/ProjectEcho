package com.github.maskedkunisquat.projectecho.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.repository.WorldStateRepository
import com.github.maskedkunisquat.projectecho.domain.rules.HeuristicPolicy
import com.github.maskedkunisquat.projectecho.domain.rules.RLPolicy
import com.github.maskedkunisquat.projectecho.domain.rules.getNeighbors
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

    private val _worldState = MutableStateFlow(WorldState.initialGame())
    /** Current game state; observed by the UI layer. */
    val worldState: StateFlow<WorldState> = _worldState.asStateFlow()

    private val _mapOverlay = MutableStateFlow(MapOverlay.Default)
    /** UI-only overlay mode for the map; not persisted to WorldState. */
    val mapOverlay: StateFlow<MapOverlay> = _mapOverlay.asStateFlow()

    private var pendingAction: DivineAction? = null
    private var pendingCluster: List<Int> = emptyList()
    private var pendingTribeTarget: String? = null

    @Volatile
    private var simEvents: List<SimEvent> = emptyList()

    private var rlPolicy: RLPolicy? = null

    private val _useRlPolicy = MutableStateFlow(false)
    /** When true, tribes use the trained RL policy instead of the heuristic. */
    val useRlPolicy: StateFlow<Boolean> = _useRlPolicy.asStateFlow()

    private val _rlDebugLog = MutableStateFlow(listOf("tick,name,pop,tiles,food,dev,soph,action"))
    /** Rolling CSV log: header + last ~30 ticks of per-tribe state+action rows. */
    val rlDebugLog: StateFlow<List<String>> = _rlDebugLog.asStateFlow()

    fun setRLPolicy(jsonString: String) {
        rlPolicy = RLPolicy.fromJson(jsonString)
        _useRlPolicy.value = true
    }

    fun toggleRLPolicy() {
        _useRlPolicy.value = !_useRlPolicy.value
    }

    init {
        viewModelScope.launch {
            // Restore saved state before the first tick fires.
            runCatching { withContext(ioDispatcher) { repository.load() } }
                .getOrNull()
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

    /** Switches the map overlay mode. */
    fun setMapOverlay(overlay: MapOverlay) { _mapOverlay.value = overlay }

    /**
     * Queues a divine action to be applied on the next tick.
     *
     * Only one action can be pending at a time; a second call before the next tick
     * replaces the previous one.
     */
    fun applyDivineAction(action: DivineAction, targetTileId: Int? = null, targetTribeId: String? = null) {
        pendingAction = action
        pendingCluster = targetTileId?.let { listOf(it) + getNeighbors(it) } ?: emptyList()
        pendingTribeTarget = targetTribeId
    }

    /**
     * Immediately advances the simulation by one tick and persists the result.
     *
     * Called automatically every 2 seconds by the init loop and also by the Manual Tick button.
     */
    fun triggerTick() {
        val action = pendingAction
        val cluster = pendingCluster
        val tribeTarget = pendingTribeTarget
        pendingAction = null
        pendingCluster = emptyList()
        pendingTribeTarget = null
        val currentState = _worldState.value
        val activeRl = rlPolicy?.takeIf { _useRlPolicy.value }
        activeRl?.setWorldState(currentState)
        if (activeRl != null) {
            val newRows = buildDebugRows(currentState, activeRl.lastActions)
            val current = _rlDebugLog.value
            val header = current.firstOrNull() ?: "tick,name,pop,tiles,food,dev,soph,action"
            val data = (current.drop(1) + newRows).takeLast(150)
            _rlDebugLog.value = listOf(header) + data
        }
        val policy = activeRl ?: HeuristicPolicy()
        val newState = tick(currentState, action, simEvents, cluster, targetTribeId = tribeTarget, policy = policy)
        _worldState.value = newState
        viewModelScope.launch(ioDispatcher) {
            runCatching { repository.save(newState) }
        }
    }

    private fun buildDebugRows(state: WorldState, actions: Map<String, Int>): List<String> {
        return state.tribes.entries.sortedBy { it.key }.map { (tribeId, tribe) ->
            val sortedOthers = state.tribes.keys.filter { it != tribeId }.sorted()
            val action = when (val idx = actions[tribeId] ?: 11) {
                0    -> "ExpN"
                1    -> "ExpS"
                2    -> "ExpE"
                3    -> "ExpW"
                11   -> "Rest"
                else -> "Raid:${sortedOthers.getOrNull(idx - 4)?.let { state.tribes[it]?.name } ?: "?"}"
            }
            val tiles = state.tiles.count { it.occupantTribeId == tribeId }
            "${state.worldTimeTick},${tribe.name},${tribe.population},$tiles,${tribe.foodSupply},${tribe.devotion},${tribe.personality.sophistication},$action"
        }
    }

    override fun onCleared() {
        super.onCleared() // cancels viewModelScope, ending the tick loop and any in-flight saves
    }
}
