package com.github.maskedkunisquat.projectecho.domain.model

/**
 * A god-level intervention the player can spend divine favor to activate.
 *
 * Each action is silently ignored if the player's current [WorldState.divineFavor]
 * is below [favorCost].
 *
 * @property favorCost Amount of divine favor required to execute this action.
 */
sealed class DivineAction(val favorCost: Int) {
    /** Adds 50 food units to the tribe's supply. */
    object CastRain : DivineAction(favorCost = 10)
    /** Reduces the tribe's population by 20%. */
    object SendPlague : DivineAction(favorCost = 15)
    /** Raises devotion by 15 points (capped at 100). */
    object InspireDevout : DivineAction(favorCost = 8)
    /** Destroys 80 food units (floor is 0). */
    object CauseFamine : DivineAction(favorCost = 5)
    /** Adds 200 food units to the tribe's supply. */
    object BlessHarvest : DivineAction(favorCost = 20)
}
