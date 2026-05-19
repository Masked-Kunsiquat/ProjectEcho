package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WeatherType(val moistureDelta: Int) {
    RainCloud(15),
    HeatWave(-12),
}
