package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WorldState

object EventEngine {

    private val statResolvers: Map<String, (WorldState) -> List<Double>?> = mapOf(
        "divineFavor" to { state ->
            listOf(state.divineFavor.toDouble())
        },
        "soilMoisture" to { state ->
            val occupied = state.tiles.filter { it.occupantTribeId != null }
            if (occupied.isEmpty()) null else listOf(occupied.map { it.soilMoisture }.average())
        },
        "population" to { state ->
            state.tribes.values.map { it.population.toDouble() }.takeIf { it.isNotEmpty() }
        },
        "devotion" to { state ->
            state.tribes.values.map { it.devotion.toDouble() }.takeIf { it.isNotEmpty() }
        },
        "foodSupply" to { state ->
            state.tribes.values.map { it.foodSupply.toDouble() }.takeIf { it.isNotEmpty() }
        },
    )

    fun evaluate(state: WorldState, events: List<SimEvent>): List<SimEvent> =
        events.filter { matches(state, it.trigger) }

    internal fun matches(state: WorldState, trigger: SimEvent.Trigger): Boolean {
        if (trigger.conditions != null) {
            return when (trigger.logic) {
                "AND" -> trigger.conditions.all { matches(state, it) }
                "OR"  -> trigger.conditions.any { matches(state, it) }
                else  -> false
            }
        }
        val values = statResolvers[trigger.stat]?.invoke(state) ?: return false
        return values.any { compare(it, trigger.operator, trigger.threshold) }
    }

    private fun compare(value: Double, operator: String, threshold: Int): Boolean = when (operator) {
        "lt"  -> value < threshold
        "gt"  -> value > threshold
        "lte" -> value <= threshold
        "gte" -> value >= threshold
        "eq"  -> value == threshold.toDouble()
        else  -> false
    }
}
