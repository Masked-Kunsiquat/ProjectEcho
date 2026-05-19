package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherFront(
    val type: WeatherType,
    val column: Int,
    val direction: Int,
)
