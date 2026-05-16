package com.github.maskedkunisquat.projectecho.domain.model

sealed class DivineAction(val favorCost: Int) {
    object CastRain : DivineAction(favorCost = 10)
    object SendPlague : DivineAction(favorCost = 15)
    object InspireDevout : DivineAction(favorCost = 8)
    object CauseFamine : DivineAction(favorCost = 5)
    object BlessHarvest : DivineAction(favorCost = 20)
}
