package com.github.maskedkunisquat.projectecho.domain.headless

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.rules.EventParser
import java.io.File

/**
 * Headless simulation entry point — no Android imports.
 *
 * Usage (via JVM test or standalone Kotlin runner):
 *   --ticks  N       Number of ticks to simulate (default 1000)
 *   --seed   N       RNG seed (default 42)
 *   --output path    Write post-tick states to a JSONL file (optional)
 *   --events path    Load SimEvents from a JSON file (optional; defaults to no events)
 */
fun main(args: Array<String>) {
    val argMap = buildArgMap(args)
    val ticks = parseLongArg(argMap, "ticks", default = 1000L)
    val seed  = parseLongArg(argMap, "seed",  default = 42L)
    val outputPath = argMap["output"]
    val eventsPath = argMap["events"]

    val events: List<SimEvent> = if (eventsPath != null) {
        EventParser.parse(File(eventsPath).readText())
    } else emptyList()

    val startMs = System.currentTimeMillis()
    val finalState = runHeadless(ticks = ticks, seed = seed, events = events, outputPath = outputPath)
    val elapsedMs = System.currentTimeMillis() - startMs

    println("Completed $ticks ticks in ${elapsedMs}ms")
    println("worldTimeTick  : ${finalState.worldTimeTick}")
    println("tribes         : ${finalState.tribes.size}")
    println("totalPopulation: ${finalState.tribes.values.sumOf { it.population }}")
    println("occupiedTiles  : ${finalState.tiles.count { it.occupantTribeId != null }}")
}

private fun parseLongArg(argMap: Map<String, String>, key: String, default: Long): Long {
    if (!argMap.containsKey(key)) return default
    val raw = argMap[key]!!
    return raw.toLongOrNull()
        ?: throw IllegalArgumentException("--$key requires a numeric value, got '$raw'")
}

private fun buildArgMap(args: Array<String>): Map<String, String> {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        if (key.startsWith("--") && i + 1 < args.size) {
            map[key.removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    return map
}
