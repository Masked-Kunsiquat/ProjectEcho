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
    /** Raises soil moisture +25 and volatility +10 on target cluster tiles. */
    object CastRain : DivineAction(favorCost = 10)
    /** Reduces the tribe's population by 20%. */
    object SendPlague : DivineAction(favorCost = 15)
    /** Raises devotion by 15 points (capped at 100). */
    object InspireDevout : DivineAction(favorCost = 8)
    /** Destroys 80 food units and drains tile moisture −15 on cluster. */
    object CauseFamine : DivineAction(favorCost = 5)
    /** Adds 200 food units and raises tile moisture +8 on cluster. */
    object BlessHarvest : DivineAction(favorCost = 20)
    /** Grants divine protection: target tribe cannot be raided for 5 ticks. */
    object Fortify : DivineAction(favorCost = 15)
    /** Drains tile moisture −30 on cluster; no direct food hit — famine comes naturally. */
    object Blight : DivineAction(favorCost = 8)
    /** Reduces skepticism −20 and raises devotion +10; does not itself cause skepticism. */
    object Revelation : DivineAction(favorCost = 12)
    /** Frees tiles in cluster (occupants cleared), drops moisture to 0, kills 15% of occupying population. */
    object Smite : DivineAction(favorCost = 25)
}
