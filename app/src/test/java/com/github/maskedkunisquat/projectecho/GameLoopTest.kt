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
        assertEquals(11, result.divineFavor)             // 10 + 1 regen (devotion 51 ≥ 40)
        assertEquals(100, result.tribe.foodSupply)       // 200 - 100
        assertEquals(102, result.tribe.population)       // (100 * 1.02).roundToInt()
        assertEquals(51, result.tribe.devotion)          // 50 + 1
    }

    @Test
    fun `starving tick - food runs out`() {
        val state = stableState(population = 100, foodSupply = 50, devotion = 20, divineFavor = 10)

        val result = tick(state)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(10, result.divineFavor)             // no regen: devotion 17 < 40
        assertEquals(0, result.tribe.foodSupply)         // clamped to 0
        assertEquals(95, result.tribe.population)        // (100 * 0.95).roundToInt()
        assertEquals(17, result.tribe.devotion)          // 20 - 3
    }

    // --- Phase 2: Divine Interventions ---

    @Test
    fun `CastRain - replenishes food and deducts favor`() {
        val state = stableState(population = 50, foodSupply = 100, divineFavor = 20)

        val result = tick(state, DivineAction.CastRain)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(11, result.divineFavor)             // 20 - 10 (CastRain) + 1 regen (devotion 51 ≥ 40)
        assertEquals(100, result.tribe.foodSupply)       // (100+50) - 50
        assertEquals(51, result.tribe.population)        // (50 * 1.02).roundToInt()
    }

    @Test
    fun `SendPlague - reduces population and deducts favor`() {
        val state = stableState(population = 100, foodSupply = 300, divineFavor = 20)

        val result = tick(state, DivineAction.SendPlague)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(6, result.divineFavor)              // 20 - 15 (SendPlague) + 1 regen (devotion 51 ≥ 40)
        assertEquals(82, result.tribe.population)        // (80 * 1.02).roundToInt()
        assertEquals(220, result.tribe.foodSupply)       // 300 - 80
    }

    @Test
    fun `InspireDevout - boosts devotion and deducts favor`() {
        val state = stableState(devotion = 50, divineFavor = 20)

        val result = tick(state, DivineAction.InspireDevout)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(13, result.divineFavor)             // 20 - 8 (InspireDevout) + 1 regen (devotion 66 ≥ 40)
        assertEquals(66, result.tribe.devotion)          // 50 + 15 (action) + 1 (thriving tick)
    }

    @Test
    fun `InspireDevout - devotion clamped at 100`() {
        val state = stableState(devotion = 90, divineFavor = 20)

        val result = tick(state, DivineAction.InspireDevout)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(100, result.tribe.devotion)         // min(100, 90+15)=100, then +1 clamped to 100
    }

    @Test
    fun `CauseFamine - drains food and deducts favor`() {
        val state = stableState(population = 50, foodSupply = 200, divineFavor = 20)

        val result = tick(state, DivineAction.CauseFamine)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(16, result.divineFavor)             // 20 - 5 (CauseFamine) + 1 regen (devotion 51 ≥ 40)
        assertEquals(70, result.tribe.foodSupply)        // (200-80) - 50
    }

    @Test
    fun `CauseFamine - food clamped at zero`() {
        val state = stableState(population = 50, foodSupply = 30, divineFavor = 20)

        val result = tick(state, DivineAction.CauseFamine)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(0, result.tribe.foodSupply)         // max(0, 30-80)=0; starvation tick keeps it 0
    }

    @Test
    fun `BlessHarvest - large food bonus and deducts favor`() {
        val state = stableState(population = 50, foodSupply = 100, divineFavor = 30)

        val result = tick(state, DivineAction.BlessHarvest)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(11, result.divineFavor)             // 30 - 20 (BlessHarvest) + 1 regen (devotion 51 ≥ 40)
        assertEquals(250, result.tribe.foodSupply)       // (100+200) - 50
    }

    @Test
    fun `action skipped when favor is insufficient`() {
        val state = stableState(population = 50, foodSupply = 300, divineFavor = 3)

        val result = tick(state, DivineAction.CastRain)  // costs 10, player has 3

        assertEquals(1L, result.worldTimeTick)
        assertEquals(4, result.divineFavor)              // action skipped + 1 regen (devotion 51 ≥ 40)
        assertEquals(250, result.tribe.foodSupply)       // 300 - 50, no +50 from CastRain
    }

    @Test
    fun `favor clamped to hundred when above max`() {
        val state = stableState(divineFavor = 150)       // above the [0,100] range

        val result = tick(state, DivineAction.CastRain)  // costs 10 → (150-10)=140 → coerceIn → 100

        assertEquals(1L, result.worldTimeTick)
        assertEquals(100, result.divineFavor)
    }
}
