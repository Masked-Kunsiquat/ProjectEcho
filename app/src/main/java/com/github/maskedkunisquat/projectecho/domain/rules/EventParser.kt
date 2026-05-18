package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import kotlinx.serialization.json.Json

object EventParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): List<SimEvent> = json.decodeFromString(rawJson)
}
