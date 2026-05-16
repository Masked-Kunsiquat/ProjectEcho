package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import kotlin.math.roundToInt

fun tick(currentState: WorldState): WorldState {
    val tribe = currentState.tribe
    val newFoodSupply = tribe.foodSupply - tribe.population

    val updatedTribe = when {
        newFoodSupply < 0 -> tribe.copy(
            foodSupply = 0,
            population = (tribe.population * 0.95).roundToInt(),
            devotion = maxOf(0, tribe.devotion - 5),
        )
        newFoodSupply > 0 -> tribe.copy(
            foodSupply = newFoodSupply,
            population = (tribe.population * 1.02).roundToInt(),
            devotion = minOf(100, tribe.devotion + 1),
        )
        else -> tribe.copy(foodSupply = 0)
    }

    return currentState.copy(
        worldTimeTick = currentState.worldTimeTick + 1,
        tribe = updatedTribe,
    )
}
