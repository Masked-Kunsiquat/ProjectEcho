package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.EventEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEngineTest {

    private fun state(
        population: Int = 100,
        devotion: Int = 50,
        foodSupply: Int = 300,
        divineFavor: Int = 50,
    ) = WorldState(
        worldTimeTick = 1L,
        divineFavor = divineFavor,
        tribe = Tribe("Test Tribe", population, devotion, foodSupply),
    )

    private fun event(
        id: String,
        stat: String,
        operator: String,
        threshold: Int,
        text: String = "Event: $id",
    ) = SimEvent(
        id = id,
        trigger = SimEvent.Trigger(stat, operator, threshold),
        text = text,
    )

    @Test
    fun `lt operator fires when value is below threshold`() {
        val events = listOf(event("food_low", "foodSupply", "lt", 50))
        val result = EventEngine.evaluate(state(foodSupply = 30), events)
        assertEquals(1, result.size)
        assertEquals("food_low", result[0].id)
    }

    @Test
    fun `lt operator does not fire when value equals threshold`() {
        val events = listOf(event("food_low", "foodSupply", "lt", 50))
        val result = EventEngine.evaluate(state(foodSupply = 50), events)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `gt operator fires when value is above threshold`() {
        val events = listOf(event("pop_high", "population", "gt", 100))
        val result = EventEngine.evaluate(state(population = 200), events)
        assertEquals(1, result.size)
    }

    @Test
    fun `gte operator fires when value equals threshold`() {
        val events = listOf(event("dev_surge", "devotion", "gte", 80))
        val result = EventEngine.evaluate(state(devotion = 80), events)
        assertEquals(1, result.size)
    }

    @Test
    fun `lte operator fires when value is below threshold`() {
        val events = listOf(event("favor_low", "divineFavor", "lte", 10))
        val result = EventEngine.evaluate(state(divineFavor = 5), events)
        assertEquals(1, result.size)
    }

    @Test
    fun `eq operator fires only on exact match`() {
        val events = listOf(event("exact", "devotion", "eq", 50))
        assertEquals(1, EventEngine.evaluate(state(devotion = 50), events).size)
        assertTrue(EventEngine.evaluate(state(devotion = 51), events).isEmpty())
    }

    @Test
    fun `unknown stat returns no match`() {
        val events = listOf(event("unknown", "happiness", "gt", 0))
        val result = EventEngine.evaluate(state(), events)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown operator returns no match`() {
        val events = listOf(event("bad_op", "devotion", "between", 40))
        val result = EventEngine.evaluate(state(), events)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple matching events all returned`() {
        val events = listOf(
            event("food_low", "foodSupply", "lt", 50),
            event("pop_ok", "population", "gte", 100),
            event("dev_high", "devotion", "gt", 90),
        )
        val result = EventEngine.evaluate(state(foodSupply = 20, population = 100, devotion = 50), events)
        assertEquals(2, result.size)
        assertEquals("food_low", result[0].id)
        assertEquals("pop_ok", result[1].id)
    }

    @Test
    fun `empty events list returns empty result`() {
        val result = EventEngine.evaluate(state(), emptyList())
        assertTrue(result.isEmpty())
    }
}
