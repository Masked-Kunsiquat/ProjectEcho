package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.rules.EventParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventParserTest {

    @Test
    fun `parse single event round-trip`() {
        val json = """
            [
              {
                "id": "famine_warning",
                "trigger": { "stat": "foodSupply", "operator": "lt", "threshold": 50 },
                "texts": ["The granaries run thin."]
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("famine_warning", event.id)
        assertEquals("foodSupply", event.trigger.stat)
        assertEquals("lt", event.trigger.operator)
        assertEquals(50, event.trigger.threshold)
        assertEquals("The granaries run thin.", event.texts[0])
        assertNull(event.effect)
        assertNull(event.cooldownTicks)
    }

    @Test
    fun `parse event with cooldownTicks`() {
        val json = """
            [
              {
                "id": "recurring",
                "cooldownTicks": 30,
                "trigger": { "stat": "foodSupply", "operator": "lt", "threshold": 50 },
                "texts": ["Stores run low again."]
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)

        assertEquals(30, events[0].cooldownTicks)
    }

    @Test
    fun `parse event with multiple text variants`() {
        val json = """
            [
              {
                "id": "multi_text",
                "trigger": { "stat": "population", "operator": "gte", "threshold": 200 },
                "texts": ["Variant A.", "Variant B.", "Variant C."]
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)

        assertEquals(3, events[0].texts.size)
        assertEquals("Variant A.", events[0].texts[0])
        assertEquals("Variant C.", events[0].texts[2])
    }

    @Test
    fun `parse event with effect`() {
        val json = """
            [
              {
                "id": "divine_boost",
                "trigger": { "stat": "divineFavor", "operator": "gte", "threshold": 90 },
                "texts": ["The heavens stir."],
                "effect": { "stat": "devotion", "delta": 10 }
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)

        val effect = requireNotNull(events[0].effect) { "effect field should be present in parsed event" }
        assertEquals("devotion", effect.stat)
        assertEquals(10, effect.delta)
    }

    @Test
    fun `parse multi-condition AND trigger`() {
        val json = """
            [
              {
                "id": "drought_starving",
                "trigger": {
                  "logic": "AND",
                  "conditions": [
                    { "stat": "soilMoisture", "operator": "lte", "threshold": 20 },
                    { "stat": "foodSupply", "operator": "lt", "threshold": 50 }
                  ]
                },
                "texts": ["The soil cracks and the stores fail."]
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)

        val trigger = events[0].trigger
        assertEquals("AND", trigger.logic)
        assertEquals(2, trigger.conditions?.size)
        assertEquals("soilMoisture", trigger.conditions!![0].stat)
        assertEquals("foodSupply", trigger.conditions[1].stat)
    }

    @Test
    fun `parse multi-condition OR trigger`() {
        val json = """
            [
              {
                "id": "or_event",
                "trigger": {
                  "logic": "OR",
                  "conditions": [
                    { "stat": "devotion", "operator": "lt", "threshold": 5 },
                    { "stat": "divineFavor", "operator": "lt", "threshold": 5 }
                  ]
                },
                "texts": ["The bond grows fragile."]
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)

        assertEquals("OR", events[0].trigger.logic)
        assertEquals(2, events[0].trigger.conditions?.size)
    }

    @Test
    fun `parse multiple events`() {
        val json = """
            [
              {
                "id": "event_a",
                "trigger": { "stat": "population", "operator": "gt", "threshold": 100 },
                "texts": ["The tribe grows."]
              },
              {
                "id": "event_b",
                "trigger": { "stat": "devotion", "operator": "lt", "threshold": 20 },
                "texts": ["Faith wavers."]
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)

        assertEquals(2, events.size)
        assertEquals("event_a", events[0].id)
        assertEquals("event_b", events[1].id)
    }

    @Test
    fun `parse empty array`() {
        val events = EventParser.parse("[]")
        assertEquals(0, events.size)
    }

    @Test
    fun `ignores unknown keys`() {
        val json = """
            [
              {
                "id": "future_event",
                "trigger": { "stat": "population", "operator": "gte", "threshold": 500 },
                "texts": ["A great nation."],
                "unknownField": "ignored"
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)
        assertEquals(1, events.size)
        assertEquals("future_event", events[0].id)
    }
}
