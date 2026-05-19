package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherFront(
    val type: WeatherType,
    val column: Int,
    val direction: Int,
) {
    init {
        require(direction == -1 || direction == 1) { "direction must be -1 or 1, got $direction" }
    }
}
