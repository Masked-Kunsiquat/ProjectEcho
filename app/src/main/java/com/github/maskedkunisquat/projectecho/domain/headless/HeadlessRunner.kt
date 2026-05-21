package com.github.maskedkunisquat.projectecho.domain.headless

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

/**
 * Runs [ticks] simulation steps from [WorldState.initial()] using a seeded [Random].
 *
 * No Android imports — safe to call from JVM unit tests and the headless entry point.
 *
 * @param outputPath If non-null, each post-tick state is appended as a JSON line to this file (JSONL).
 */
fun runHeadless(
    ticks: Long,
    seed: Long,
    events: List<SimEvent> = emptyList(),
    outputPath: String? = null,
): WorldState {
    val random = Random(seed)
    var state = WorldState.initial()
    val json = Json { prettyPrint = false }
    val writer = outputPath?.let { File(it).bufferedWriter() }

    try {
        for (i in 0 until ticks) {
            state = tick(state, events = events, random = random)
            writer?.appendLine(json.encodeToString(state))
        }
    } finally {
        writer?.close()
    }

    return state
}
