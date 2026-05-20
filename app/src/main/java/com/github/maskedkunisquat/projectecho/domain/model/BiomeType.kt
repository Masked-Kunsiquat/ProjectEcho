package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BiomeType(
    val moistureBaseline: Int,
    val weatherResistance: Float,
    val volatilityGain: Int,
) {
    Grassland(moistureBaseline = 35, weatherResistance = 1.00f, volatilityGain = 5),
    Forest   (moistureBaseline = 45, weatherResistance = 0.75f, volatilityGain = 3),
    Desert   (moistureBaseline = 15, weatherResistance = 0.50f, volatilityGain = 8),
    Coast    (moistureBaseline = 35, weatherResistance = 1.00f, volatilityGain = 6),
    Water    (moistureBaseline = 50, weatherResistance = 0.00f, volatilityGain = 0),
}
