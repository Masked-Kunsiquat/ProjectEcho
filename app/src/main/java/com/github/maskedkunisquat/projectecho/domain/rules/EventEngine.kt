package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WorldState

object EventEngine {
    fun evaluate(state: WorldState, events: List<SimEvent>): List<SimEvent> =
        events.filter { matches(state, it.trigger) }

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
