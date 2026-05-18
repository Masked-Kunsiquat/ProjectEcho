package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WorldState

/**
 * Evaluates a list of candidate events against the current world state and returns those whose triggers are met.
 *
 * This object only performs condition matching; deduplication of already-fired events
 * must be done by the caller before passing [events] in.
 */
object EventEngine {
    /**
     * Returns every event in [events] whose trigger condition is satisfied by [state].
     *
     * @param state Current simulation snapshot to test conditions against.
     * @param events Candidate events (caller should exclude previously fired IDs).
     * @return Subset of [events] that matched.
     */
    fun evaluate(state: WorldState, events: List<SimEvent>): List<SimEvent> =
        events.filter { matches(state, it.trigger) }

    /**
     * Returns true if [trigger]'s condition holds for the given [state].
     *
     * Supported [SimEvent.Trigger.stat] values: `"population"`, `"devotion"`, `"foodSupply"`, `"divineFavor"`.
     * Supported [SimEvent.Trigger.operator] values: `"lt"`, `"gt"`, `"lte"`, `"gte"`, `"eq"`.
     * Any unrecognised key returns false rather than throwing, so malformed JSON events are silently skipped.
     */
    private fun matches(state: WorldState, trigger: SimEvent.Trigger): Boolean {
        val value = when (trigger.stat) {
            "population" -> state.tribe.population
            "devotion" -> state.tribe.devotion
            "foodSupply" -> state.tribe.foodSupply
            "divineFavor" -> state.divineFavor
            else -> return false
        }
        return when (trigger.operator) {
            "lt" -> value < trigger.threshold
            "gt" -> value > trigger.threshold
            "lte" -> value <= trigger.threshold
            "gte" -> value >= trigger.threshold
            "eq" -> value == trigger.threshold
            else -> false
        }
    }
}
