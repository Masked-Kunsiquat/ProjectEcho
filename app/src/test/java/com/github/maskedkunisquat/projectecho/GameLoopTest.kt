package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Assert.assertEquals
import org.junit.Test

class GameLoopTest {

    private fun stableState(
        population: Int = 50,
        foodSupply: Int = 300,
        devotion: Int = 50,
        divineFavor: Int = 50,
    ) = WorldState(
        worldTimeTick = 0L,
        divineFavor = divineFavor,
        tribe = Tribe(
            name = "The Echosi",
            population = population,
            devotion = devotion,
            foodSupply = foodSupply,
        ),
    )

    // --- Phase 1 ---

    @Test
    fun `thriving tick - food is plentiful`() {
        val state = stableState(population = 100, foodSupply = 200, divineFavor = 10)

        val result = tick(state)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(100, result.tribe.foodSupply)   // 200 - 100
        assertEquals(102, result.tribe.population)   // (100 * 1.02).roundToInt()
        assertEquals(51, result.tribe.devotion)      // 50 + 1
    }

    @Test
    fun `starving tick - food runs out`() {
        val state = stableState(population = 100, foodSupply = 50, devotion = 20, divineFavor = 10)

        val result = tick(state)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(0, result.tribe.foodSupply)     // clamped to 0
        assertEquals(95, result.tribe.population)    // (100 * 0.95).roundToInt()
        assertEquals(15, result.tribe.devotion)      // 20 - 5
    }

    // --- Phase 2: Divine Interventions ---

    @Test
    fun `CastRain - replenishes food and deducts favor`() {
        // population=50, foodSupply=100, favor=20
        // action: +50 food, -10 favor → foodSupply=150, favor=10
        // normal tick: 150-50=100 food remaining (thriving)
        val state = stableState(population = 50, foodSupply = 100, divineFavor = 20)

        val result = tick(state, DivineAction.CastRain)

        assertEquals(10, result.divineFavor)         // 20 - 10
        assertEquals(100, result.tribe.foodSupply)   // (100+50) - 50
        assertEquals(51, result.tribe.population)    // (50 * 1.02).roundToInt()
    }

    @Test
    fun `SendPlague - reduces population and deducts favor`() {
        // population=100, foodSupply=300, favor=20
        // action: pop * 0.8 = 80, favor=5
        // normal tick: 300-80=220 food (thriving), pop = (80*1.02)=82
        val state = stableState(population = 100, foodSupply = 300, divineFavor = 20)

        val result = tick(state, DivineAction.SendPlague)

        assertEquals(5, result.divineFavor)          // 20 - 15
        assertEquals(82, result.tribe.population)    // (80 * 1.02).roundToInt()
        assertEquals(220, result.tribe.foodSupply)   // 300 - 80
    }

    @Test
    fun `InspireDevout - boosts devotion and deducts favor`() {
        // devotion=50, favor=20
        // action: devotion=65, favor=12
        // normal tick: thriving → devotion=66
        val state = stableState(devotion = 50, divineFavor = 20)

        val result = tick(state, DivineAction.InspireDevout)

        assertEquals(12, result.divineFavor)         // 20 - 8
        assertEquals(66, result.tribe.devotion)      // 50 + 15 (action) + 1 (thriving tick)
    }

    @Test
    fun `InspireDevout - devotion clamped at 100`() {
        val state = stableState(devotion = 90, divineFavor = 20)

        val result = tick(state, DivineAction.InspireDevout)

        assertEquals(100, result.tribe.devotion)     // min(100, 90+15)=100, then +1 clamped to 100
    }

    @Test
    fun `CauseFamine - drains food and deducts favor`() {
        // foodSupply=200, favor=20
        // action: food=120, favor=15
        // normal tick: 120-50=70 (thriving)
        val state = stableState(population = 50, foodSupply = 200, divineFavor = 20)

        val result = tick(state, DivineAction.CauseFamine)

        assertEquals(15, result.divineFavor)         // 20 - 5
        assertEquals(70, result.tribe.foodSupply)    // (200-80) - 50
    }

    @Test
    fun `CauseFamine - food clamped at zero`() {
        val state = stableState(population = 50, foodSupply = 30, divineFavor = 20)

        val result = tick(state, DivineAction.CauseFamine)

        // action: max(0, 30-80)=0; normal tick: 0-50<0 → starvation
        assertEquals(0, result.tribe.foodSupply)
    }

    @Test
    fun `BlessHarvest - large food bonus and deducts favor`() {
        // foodSupply=100, favor=30
        // action: food=300, favor=10
        // normal tick: 300-50=250 (thriving)
        val state = stableState(population = 50, foodSupply = 100, divineFavor = 30)

        val result = tick(state, DivineAction.BlessHarvest)

        assertEquals(10, result.divineFavor)         // 30 - 20
        assertEquals(250, result.tribe.foodSupply)   // (100+200) - 50
    }

    @Test
    fun `favor clamped to zero when cost exceeds current favor`() {
        val state = stableState(divineFavor = 3)

        val result = tick(state, DivineAction.CastRain) // costs 10

        assertEquals(0, result.divineFavor)          // coerceIn(0, 100)
    }
}
