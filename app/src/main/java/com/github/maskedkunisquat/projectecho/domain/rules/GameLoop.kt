package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import kotlin.math.roundToInt

fun tick(
    currentState: WorldState,
    action: DivineAction? = null,
    events: List<SimEvent> = emptyList(),
): WorldState {
    var state = currentState

    if (action != null && state.divineFavor >= action.favorCost) {
        val updatedTribes = state.tribes.mapValues { (_, tribe) ->
            when (action) {
                DivineAction.CastRain -> tribe.copy(foodSupply = tribe.foodSupply + 50)
                DivineAction.SendPlague -> tribe.copy(population = (tribe.population * 0.8).roundToInt())
                DivineAction.InspireDevout -> tribe.copy(devotion = minOf(100, tribe.devotion + 15))
                DivineAction.CauseFamine -> tribe.copy(foodSupply = maxOf(0, tribe.foodSupply - 80))
                DivineAction.BlessHarvest -> tribe.copy(foodSupply = tribe.foodSupply + 200)
            }
        }
        state = state.copy(
            tribes = updatedTribes,
            divineFavor = (state.divineFavor - action.favorCost).coerceIn(0, 100),
        )
    }

    val survivedTribes = state.tribes.mapValues { (_, tribe) ->
        val farmed = (tribe.population * 0.8).roundToInt()
        val newFoodSupply = tribe.foodSupply + farmed - tribe.population
        when {
            newFoodSupply < 0 -> tribe.copy(
                foodSupply = 0,
                population = (tribe.population * 0.95).roundToInt(),
                devotion = maxOf(0, tribe.devotion - 3),
            )
            newFoodSupply > 0 -> tribe.copy(
                foodSupply = newFoodSupply,
                population = (tribe.population * 1.02).roundToInt(),
                devotion = minOf(100, tribe.devotion + 1),
            )
            else -> tribe.copy(foodSupply = 0)
        }
    }

    val regenAmount = (survivedTribes.values.maxOfOrNull { it.devotion } ?: 0) * 3 / 100
    val regenedFavor = minOf(100, state.divineFavor + regenAmount)

    val postTickState = state.copy(
        worldTimeTick = state.worldTimeTick + 1,
        divineFavor = regenedFavor,
        tribes = survivedTribes,
    )

    val unfiredEvents = events.filter { it.id !in postTickState.firedEventIds }
    val triggered = EventEngine.evaluate(postTickState, unfiredEvents)

    return if (triggered.isEmpty()) {
        postTickState
    } else {
        postTickState.copy(
            eventHistory = postTickState.eventHistory + triggered.map { it.text },
            firedEventIds = postTickState.firedEventIds + triggered.map { it.id },
        )
    }
}
