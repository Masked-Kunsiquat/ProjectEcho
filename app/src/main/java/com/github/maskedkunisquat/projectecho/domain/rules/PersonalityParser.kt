package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import kotlinx.serialization.json.Json

object PersonalityParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(rawJson: String): List<TribePersonality> = json.decodeFromString(rawJson)
}
