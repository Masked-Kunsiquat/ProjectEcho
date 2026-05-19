package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WorldState

object EventEngine {
    fun evaluate(state: WorldState, events: List<SimEvent>): List<SimEvent> =
        events.filter { matches(state, it.trigger) }

    private fun matches(state: WorldState, trigger: SimEvent.Trigger): Boolean {
        // divineFavor is world-level and independent of tribe presence.
        if (trigger.stat == "divineFavor") {
            return compare(state.divineFavor, trigger.operator, trigger.threshold)
        }
        // Tribe-level stats: fire if any tribe meets the condition.
        return state.tribes.values.any { tribe ->
            val value = when (trigger.stat) {
                "population" -> tribe.population
                "devotion"   -> tribe.devotion
                "foodSupply" -> tribe.foodSupply
                else         -> return false  // unknown stat — skip silently
            }
            compare(value, trigger.operator, trigger.threshold)
        }
    }

    private fun compare(value: Int, operator: String, threshold: Int): Boolean = when (operator) {
        "lt"  -> value < threshold
        "gt"  -> value > threshold
        "lte" -> value <= threshold
        "gte" -> value >= threshold
        "eq"  -> value == threshold
        else  -> false
    }
}
