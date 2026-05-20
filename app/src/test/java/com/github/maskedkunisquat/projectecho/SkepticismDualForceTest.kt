package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.PRAYER_PRESSURE_CAP
import com.github.maskedkunisquat.projectecho.domain.rules.PRAYER_THRESHOLD
import com.github.maskedkunisquat.projectecho.domain.rules.SKEPTICISM_DECAY_BASE
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkepticismDualForceTest {

    // foodSupply=10 with pop=50 and no tiles gives farmed=40, newFood=0 (exact equilibrium)
    // so devotion does not change during survival — important for deterministic prayer checks.
    private fun devoutTribe(
        devotion: Int = 80,
        skepticism: Int = 0,
        prayerPressure: Float = 0f,
        skepticismDecayBuffer: Float = 0f,
        skepticismRate: Float = 1.0f,
        traditionalism: Float = 0.5f,
        foodSupply: Int = 10,
        population: Int = 50,
        generationDeaths: Int = 0,
    ) = Tribe(
        tribeId = "t1", name = "The Test",
        population = population, devotion = devotion, foodSupply = foodSupply,
        personality = TribePersonality(
            archetypeId = "test",
            aggression = 0.5f, caution = 0.5f,
            skepticismRate = skepticismRate,
            traditionalism = traditionalism,
            biomeAffinity = mapOf("Grassland" to 1.0f),
            sophistication = 0, skepticism = skepticism,
        ),
        prayerPressure = prayerPressure,
        skepticismDecayBuffer = skepticismDecayBuffer,
        generationDeaths = generationDeaths,
    )

    private fun stateWith(tribe: Tribe, divineFavor: Int = 100) = WorldState(
        worldTimeTick = 0L,
        divineFavor = divineFavor,
        tiles = emptyList(),
        tribes = mapOf("t1" to tribe),
    )

    // --- Prayer pressure accumulation ---

    @Test fun `prayerPressure accumulates when devout and no action applied`() {
        val tribe = devoutTribe(devotion = 80)
        val after = tick(stateWith(tribe), action = null)
        // devotion stays at 80 (equilibrium food); pressure += 80 - PRAYER_THRESHOLD = 20
        assertEquals((80 - PRAYER_THRESHOLD).toFloat(), after.tribes["t1"]!!.prayerPressure, 0.01f)
    }

    @Test fun `prayerPressure does not accumulate when devotion equals threshold`() {
        val tribe = devoutTribe(devotion = PRAYER_THRESHOLD, prayerPressure = 10f)
        val after = tick(stateWith(tribe), action = null)
        // devotion == threshold; condition is > not >=; pressure stays frozen
        assertEquals(10f, after.tribes["t1"]!!.prayerPressure, 0.01f)
    }

    @Test fun `prayerPressure freezes when devotion below threshold`() {
        val tribe = devoutTribe(devotion = PRAYER_THRESHOLD - 1, prayerPressure = 50f)
        val after = tick(stateWith(tribe), action = null)
        assertEquals(50f, after.tribes["t1"]!!.prayerPressure, 0.01f)
    }

    @Test fun `prayerPressure halves when divine action applied`() {
        val tribe = devoutTribe(prayerPressure = 100f)
        val after = tick(stateWith(tribe, divineFavor = 100), action = DivineAction.InspireDevout)
        assertEquals(50f, after.tribes["t1"]!!.prayerPressure, 0.01f)
    }

    @Test fun `prayerPressure does not accumulate on same tick action is applied`() {
        // Action halves pressure; no accumulation when actionApplied
        val tribe = devoutTribe(devotion = 80, prayerPressure = 0f)
        val after = tick(stateWith(tribe, divineFavor = 100), action = DivineAction.InspireDevout)
        assertEquals(0f, after.tribes["t1"]!!.prayerPressure, 0.01f)
    }

    // --- Cap conversion ---

    @Test fun `prayerPressure converts to skepticism at cap with Chronicle entry`() {
        // pressure + (100 - 60) = 161 + 40 = 201 >= 200 → convert
        val tribe = devoutTribe(devotion = 100, skepticism = 5, prayerPressure = 161f)
        val after = tick(stateWith(tribe), action = null)
        val result = after.tribes["t1"]!!
        assertEquals(0f, result.prayerPressure, 0.01f)
        assertEquals(6, result.personality.skepticism)
        assertTrue(after.eventHistory.any { "go unanswered" in it })
    }

    @Test fun `skepticism is clamped at 100 on cap conversion`() {
        val tribe = devoutTribe(devotion = 100, skepticism = 100, prayerPressure = 161f)
        val after = tick(stateWith(tribe), action = null)
        assertEquals(100, after.tribes["t1"]!!.personality.skepticism)
    }

    // --- Passive decay ---

    @Test fun `skepticism decays over time`() {
        // rate=1.0 → buffer increments 1f/tick → decay fires every SKEPTICISM_DECAY_BASE ticks.
        // Large foodSupply prevents starvation/deaths/generational-turnover contamination.
        // devotion=0 stays below PRAYER_THRESHOLD throughout so no prayer accumulation.
        var state = stateWith(devoutTribe(devotion = 0, skepticism = 10, skepticismRate = 1.0f, foodSupply = 10_000))
        repeat(SKEPTICISM_DECAY_BASE.toInt() + 2) { state = tick(state) }
        assertTrue("skepticism should have dropped at least once", state.tribes["t1"]!!.personality.skepticism < 10)
    }

    @Test fun `skepticism decays faster for low skepticismRate archetypes`() {
        // Large foodSupply prevents starvation deaths which would trigger generational turnover
        // and contaminate the skepticism measurement. devotion=0 stays below PRAYER_THRESHOLD
        // throughout 50 ticks (rises by 1/tick to 50), so no prayer pressure interference.
        // Low rate=0.6 → buffer fills at 1/0.6≈1.67/tick → decay every ~6 ticks → ~8 fires in 50 ticks.
        // High rate=1.4 → buffer fills at 1/1.4≈0.71/tick → decay every ~14 ticks → ~3 fires in 50 ticks.
        fun runTicks(rate: Float, n: Int): Int {
            var state = WorldState(
                worldTimeTick = 0L, divineFavor = 0,
                tiles = emptyList(),
                tribes = mapOf(
                    "t1" to devoutTribe(devotion = 0, skepticism = 20, skepticismRate = rate, foodSupply = 10_000)
                ),
            )
            repeat(n) { state = tick(state) }
            return state.tribes["t1"]!!.personality.skepticism
        }
        val agrarianSkepticism = runTicks(0.6f, 50)
        val warlikeSkepticism  = runTicks(1.4f, 50)
        assertTrue(
            "low-skepticismRate tribe should decay faster (lower skepticism after same ticks)\n" +
                "agrarian=$agrarianSkepticism warlike=$warlikeSkepticism",
            agrarianSkepticism < warlikeSkepticism
        )
    }

    // --- Generational turnover ---

    @Test fun `prayerPressure survives generational turnover unchanged`() {
        // Set generationDeaths just below the trigger (halfPop = pop/2).
        // Send a plague to kill 20% and tip over the threshold.
        // prayerPressure is halved by the action, then preserved through turnover.
        val tribe = devoutTribe(
            devotion = 30,          // below PRAYER_THRESHOLD, no accumulation
            skepticism = 20,
            prayerPressure = 75f,
            foodSupply = 0,
            population = 100,
            generationDeaths = 49,  // halfPop = ~80/2 = 40; one death tips it over
        )
        val after = tick(stateWith(tribe, divineFavor = 100), action = DivineAction.SendPlague)
        val result = after.tribes["t1"]!!
        assertTrue("Generational turnover should have fired", after.eventHistory.any { "new generation" in it })
        // prayerPressure halved by action (75 * 0.5 = 37.5), then preserved through turnover
        assertEquals(37.5f, result.prayerPressure, 0.1f)
    }
}
