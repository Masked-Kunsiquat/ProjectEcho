package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import kotlinx.serialization.json.Json

/**
 * Deserialises a JSON string into a list of [SimEvent]s.
 *
 * Unknown JSON keys are silently ignored, so `events.json` can carry extra fields
 * (e.g. editor metadata) without breaking the parser.
 */
object EventParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses [rawJson] (a JSON array of event objects) into a typed list.
     *
     * @param rawJson Raw JSON string, typically the full contents of `assets/events.json`.
     * @return Parsed list of [SimEvent]s; empty list if the JSON array is empty.
     */
    fun parse(rawJson: String): List<SimEvent> = json.decodeFromString(rawJson)
}
