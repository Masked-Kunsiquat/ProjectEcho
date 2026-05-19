package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BiomeType(
    val moistureBaseline: Int,
    val weatherResistance: Float,
) {
    Grassland(moistureBaseline = 35, weatherResistance = 1.00f),
    Forest   (moistureBaseline = 45, weatherResistance = 0.75f),
    Desert   (moistureBaseline = 15, weatherResistance = 0.50f),
    Coast    (moistureBaseline = 35, weatherResistance = 1.00f),
    Water    (moistureBaseline = 50, weatherResistance = 0.00f),
}
