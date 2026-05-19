package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.EventEngine
import com.github.maskedkunisquat.projectecho.domain.rules.NarrativeResolver
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase10ChronicleTest {

    private val testTribe = Tribe(
        tribeId = "iron-wrought",
        name = "The Iron-Wrought",
        population = 100,
        devotion = 50,
        foodSupply = 300,
    )

    private fun baseState(
        population: Int = 100,
        devotion: Int = 50,
        foodSupply: Int = 300,
        divineFavor: Int = 50,
        tick: Long = 1L,
        cooldowns: Map<String, Long> = emptyMap(),
    ) = WorldState(
        worldTimeTick = tick,
        divineFavor = divineFavor,
        tiles = emptyList(),
        tribes = mapOf(
            "iron-wrought" to testTribe.copy(
                population = population,
                devotion = devotion,
                foodSupply = foodSupply,
            )
        ),
        eventCooldowns = cooldowns,
    )

    private fun event(
        id: String,
        stat: String,
        operator: String,
        threshold: Int,
        texts: List<String> = listOf("Event: $id"),
        cooldownTicks: Int? = null,
    ) = SimEvent(
        id = id,
        trigger = SimEvent.Trigger(stat = stat, operator = operator, threshold = threshold),
        texts = texts,
        cooldownTicks = cooldownTicks,
    )

    // --- NarrativeResolver ---

    @Test
    fun `resolve replaces tribeName token`() {
        val state = baseState()
        val result = NarrativeResolver.resolve("Hello, {{tribeName}}!", state)
        assertEquals("Hello, The Iron-Wrought!", result)
    }

    @Test
    fun `resolve replaces population token`() {
        val state = baseState(population = 247)
        val result = NarrativeResolver.resolve("Pop: {{population}}", state)
        assertEquals("Pop: 247", result)
    }

    @Test
    fun `resolve replaces foodSupply token`() {
        val state = baseState(foodSupply = 812)
        val result = NarrativeResolver.resolve("Food: {{foodSupply}}", state)
        assertEquals("Food: 812", result)
    }

    @Test
    fun `resolve replaces tick token`() {
        val state = baseState(tick = 42L)
        val result = NarrativeResolver.resolve("Tick {{tick}} has passed.", state)
        assertEquals("Tick 42 has passed.", result)
    }

    @Test
    fun `resolve replaces multiple tokens in one string`() {
        val state = baseState(population = 99, foodSupply = 200, tick = 7L)
        val result = NarrativeResolver.resolve(
            "{{tribeName}} (pop {{population}}, food {{foodSupply}}) at tick {{tick}}.",
            state,
        )
        assertEquals("The Iron-Wrought (pop 99, food 200) at tick 7.", result)
    }

    @Test
    fun `unknown tokens pass through unchanged`() {
        val state = baseState()
        val result = NarrativeResolver.resolve("Status: {{unknownStat}}", state)
        assertEquals("Status: {{unknownStat}}", result)
    }

    @Test
    fun `resolve with no tribes uses fallback for tribeName`() {
        val emptyState = WorldState(
            worldTimeTick = 1L,
            divineFavor = 50,
            tiles = emptyList(),
            tribes = emptyMap(),
        )
        val result = NarrativeResolver.resolve("{{tribeName}} endures.", emptyState)
        assertEquals("the tribe endures.", result)
    }

    // --- Variant selection ---

    @Test
    fun `fired event text is always one of the defined variants`() {
        val variants = listOf("Alpha text.", "Beta text.", "Gamma text.")
        val singleEvent = event("multi", "foodSupply", "lt", 50, texts = variants)
        // nextSpawnTick=Long.MAX_VALUE prevents weather messages from polluting eventHistory
        val state = baseState(foodSupply = 10).copy(nextSpawnTick = Long.MAX_VALUE)

        // Run 20 ticks; every resolved entry must be one of the variants
        repeat(20) { i ->
            val tickedState = tick(
                state.copy(worldTimeTick = i.toLong()),
                events = listOf(singleEvent),
                random = Random(i),
            )
            tickedState.eventHistory.forEach { entry ->
                assertTrue("'$entry' must be a variant of $variants", entry in variants)
            }
        }
    }

    // --- Cooldown system ---

    @Test
    fun `one-and-done event fires exactly once`() {
        val oneShotEvent = event("one_shot", "foodSupply", "lt", 500, cooldownTicks = null)
        var state = baseState(foodSupply = 10, tick = 0L)

        state = tick(state, events = listOf(oneShotEvent), random = Random(0))
        assertEquals(1, state.eventHistory.size)

        state = tick(state, events = listOf(oneShotEvent), random = Random(1))
        assertEquals(1, state.eventHistory.size)  // still 1 — did not re-fire
    }

    @Test
    fun `cooldown blocks re-fire before expiry`() {
        val cooldownEvent = event("recurring", "foodSupply", "lt", 500, cooldownTicks = 10)
        var state = baseState(foodSupply = 10, tick = 0L)

        // First fire
        state = tick(state, events = listOf(cooldownEvent), random = Random(0))
        val firstCount = state.eventHistory.size
        assertTrue(firstCount >= 1)

        // Tick 5 — too early (cooldown = 10, lastFired = 1)
        state = state.copy(worldTimeTick = 5L)
        state = tick(state, events = listOf(cooldownEvent), random = Random(2))
        assertEquals(firstCount, state.eventHistory.size)  // no new entry
    }

    @Test
    fun `cooldown allows re-fire after expiry`() {
        val cooldownEvent = event("recurring", "foodSupply", "lt", 500, cooldownTicks = 10)
        var state = baseState(foodSupply = 10, tick = 0L)

        // First fire at tick 1
        state = tick(state, events = listOf(cooldownEvent), random = Random(0))
        val firstCount = state.eventHistory.size
        val lastFiredTick = state.eventCooldowns["recurring"]!!

        // Jump past cooldown — lastFired + cooldownTicks = 1 + 10 = 11; set tick to 10 so next tick is 11
        state = state.copy(worldTimeTick = lastFiredTick + cooldownEvent.cooldownTicks!! - 1)
        state = tick(state, events = listOf(cooldownEvent), random = Random(3))
        assertTrue(state.eventHistory.size > firstCount)  // re-fired
    }

    @Test
    fun `eventCooldowns records the tick the event last fired`() {
        val e = event("tracked", "foodSupply", "lt", 500, cooldownTicks = 5)
        val state = baseState(foodSupply = 10, tick = 7L)
        val result = tick(state, events = listOf(e), random = Random(0))
        assertEquals(8L, result.eventCooldowns["tracked"])
    }

    // --- Multi-condition triggers ---

    @Test
    fun `AND trigger fires only when both conditions are true`() {
        val andEvent = SimEvent(
            id = "and_test",
            trigger = SimEvent.Trigger(
                logic = "AND",
                conditions = listOf(
                    SimEvent.Trigger(stat = "foodSupply", operator = "lt", threshold = 50),
                    SimEvent.Trigger(stat = "devotion", operator = "lt", threshold = 20),
                ),
            ),
            texts = listOf("Both conditions met."),
        )

        // Both true
        assertTrue(
            EventEngine.evaluate(baseState(foodSupply = 10, devotion = 10), listOf(andEvent)).isNotEmpty()
        )
        // Only first true
        assertTrue(
            EventEngine.evaluate(baseState(foodSupply = 10, devotion = 80), listOf(andEvent)).isEmpty()
        )
        // Only second true
        assertTrue(
            EventEngine.evaluate(baseState(foodSupply = 300, devotion = 10), listOf(andEvent)).isEmpty()
        )
        // Neither true
        assertTrue(
            EventEngine.evaluate(baseState(foodSupply = 300, devotion = 80), listOf(andEvent)).isEmpty()
        )
    }

    @Test
    fun `OR trigger fires when at least one condition is true`() {
        val orEvent = SimEvent(
            id = "or_test",
            trigger = SimEvent.Trigger(
                logic = "OR",
                conditions = listOf(
                    SimEvent.Trigger(stat = "devotion", operator = "lt", threshold = 5),
                    SimEvent.Trigger(stat = "divineFavor", operator = "lt", threshold = 5),
                ),
            ),
            texts = listOf("At least one condition met."),
        )

        // Both true
        assertTrue(
            EventEngine.evaluate(baseState(devotion = 2, divineFavor = 2), listOf(orEvent)).isNotEmpty()
        )
        // First only
        assertTrue(
            EventEngine.evaluate(baseState(devotion = 2, divineFavor = 50), listOf(orEvent)).isNotEmpty()
        )
        // Second only
        assertTrue(
            EventEngine.evaluate(baseState(devotion = 50, divineFavor = 2), listOf(orEvent)).isNotEmpty()
        )
        // Neither
        assertTrue(
            EventEngine.evaluate(baseState(devotion = 50, divineFavor = 50), listOf(orEvent)).isEmpty()
        )
    }

    @Test
    fun `unknown logic string returns no match`() {
        val badLogicEvent = SimEvent(
            id = "bad_logic",
            trigger = SimEvent.Trigger(
                logic = "XOR",
                conditions = listOf(
                    SimEvent.Trigger(stat = "devotion", operator = "lt", threshold = 50),
                ),
            ),
            texts = listOf("Should not fire."),
        )
        assertTrue(EventEngine.evaluate(baseState(), listOf(badLogicEvent)).isEmpty())
    }

    @Test
    fun `unknown stat key returns no match without crashing`() {
        val unknownStatEvent = event("unknown", "happiness", "gt", 0)
        val result = EventEngine.evaluate(baseState(), listOf(unknownStatEvent))
        assertTrue(result.isEmpty())
    }

    // --- Integration: template substitution in Chronicle ---

    @Test
    fun `Chronicle entry contains tribe name after tick`() {
        val namedEvent = event(
            "herald",
            "foodSupply",
            "gte",
            100,
            texts = listOf("{{tribeName}} prospers at tick {{tick}}."),
        )
        val state = baseState(foodSupply = 200, tick = 4L)
        val result = tick(state, events = listOf(namedEvent), random = Random(0))
        assertTrue(result.eventHistory.any { it.startsWith("The Iron-Wrought prospers") })
    }
}
