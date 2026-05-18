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
                "text": "The granaries run thin."
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
        assertEquals("The granaries run thin.", event.text)
        assertNull(event.effect)
    }

    @Test
    fun `parse event with effect`() {
        val json = """
            [
              {
                "id": "divine_boost",
                "trigger": { "stat": "divineFavor", "operator": "gte", "threshold": 90 },
                "text": "The heavens stir.",
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
    fun `parse multiple events`() {
        val json = """
            [
              {
                "id": "event_a",
                "trigger": { "stat": "population", "operator": "gt", "threshold": 100 },
                "text": "The tribe grows."
              },
              {
                "id": "event_b",
                "trigger": { "stat": "devotion", "operator": "lt", "threshold": 20 },
                "text": "Faith wavers."
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
                "text": "A great nation.",
                "unknownField": "ignored"
              }
            ]
        """.trimIndent()

        val events = EventParser.parse(json)
        assertEquals(1, events.size)
        assertEquals("future_event", events[0].id)
    }
}
