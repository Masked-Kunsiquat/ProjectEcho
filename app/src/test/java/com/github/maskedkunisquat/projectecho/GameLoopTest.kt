package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Assert.assertEquals
import org.junit.Test

class GameLoopTest {

    @Test
    fun `thriving tick - food is plentiful`() {
        val state = WorldState(
            worldTimeTick = 0L,
            divineFavor = 10,
            tribe = Tribe(
                name = "The Echosi",
                population = 100,
                devotion = 50,
                foodSupply = 200, // 200 - 100 = 100 remaining (> 0)
            ),
        )

        val result = tick(state)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(100, result.tribe.foodSupply)   // 200 - 100
        assertEquals(102, result.tribe.population)   // (100 * 1.02).roundToInt()
        assertEquals(51, result.tribe.devotion)      // 50 + 1
    }

    @Test
    fun `starving tick - food runs out`() {
        val state = WorldState(
            worldTimeTick = 0L,
            divineFavor = 10,
            tribe = Tribe(
                name = "The Echosi",
                population = 100,
                devotion = 20,
                foodSupply = 50, // 50 - 100 = -50 (< 0)
            ),
        )

        val result = tick(state)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(0, result.tribe.foodSupply)     // clamped to 0
        assertEquals(95, result.tribe.population)    // (100 * 0.95).roundToInt()
        assertEquals(15, result.tribe.devotion)      // 20 - 5
    }
}
