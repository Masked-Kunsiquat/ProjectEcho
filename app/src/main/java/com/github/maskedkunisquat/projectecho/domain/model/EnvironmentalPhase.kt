package com.github.maskedkunisquat.projectecho.domain.model

sealed class EnvironmentalPhase {
    object Deluge : EnvironmentalPhase()    // moisture 81–100
    object Saturated : EnvironmentalPhase() // moisture 51–80
    object Fertile : EnvironmentalPhase()   // moisture 21–50
    object Parched : EnvironmentalPhase()   // moisture  0–20

    val foodMultiplier: Double
        get() = when (this) {
            is Deluge    -> 0.0
            is Saturated -> 0.5
            is Fertile   -> 1.5
            is Parched   -> 0.1
        }

    companion object {
        fun from(soilMoisture: Int): EnvironmentalPhase = when {
            soilMoisture >= 81 -> Deluge
            soilMoisture >= 51 -> Saturated
            soilMoisture >= 21 -> Fertile
            else               -> Parched
        }
    }
}
