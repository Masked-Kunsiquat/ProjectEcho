package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.HUNGRY_FOOD_TICKS
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.MIN_VIABLE_POPULATION
import com.github.maskedkunisquat.projectecho.domain.model.NEEDS_DENSITY_THRESHOLD
import com.github.maskedkunisquat.projectecho.domain.model.PARCHED_MOISTURE_THRESHOLD
import com.github.maskedkunisquat.projectecho.domain.model.SPIRITUALLY_DEPLETED_DEVOTION
import com.github.maskedkunisquat.projectecho.domain.model.SPIRITUALLY_DEPLETED_SKEPTICISM
import com.github.maskedkunisquat.projectecho.domain.model.UNDER_THREAT_TICKS
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribeNeed
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_MIN_FOOD_TICKS
import com.github.maskedkunisquat.projectecho.domain.rules.conflictStep
import com.github.maskedkunisquat.projectecho.domain.rules.splitStep
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TribeNeedTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun tile(id: Int, moisture: Int = 50, owner: String? = null) = MapTile(
        id = id, col = id, row = 0,
        soilMoisture = moisture,
        occupantTribeId = owner,
        biome = BiomeType.Grassland,
    )

    private fun tribe(
        population: Int = 100,
        foodSupply: Int = 500,
        devotion: Int = 50,
        skepticism: Int = 0,
        lastRaidTick: Long = -1L,
    ) = Tribe(
        tribeId = "t",
        name = "Test",
        population = population,
        devotion = devotion,
        foodSupply = foodSupply,
        personality = TribePersonality(
            archetypeId = "test",
            aggression = 0.5f, caution = 0.5f,
            skepticismRate = 1.0f, traditionalism = 0.5f,
            biomeAffinity = emptyMap(),
            skepticism = skepticism,
        ),
        lastRaidTick = lastRaidTick,
    )

    // ── Parched ───────────────────────────────────────────────────────────────

    @Test fun `Parched fires when avg moisture is below threshold`() {
        val t = tribe()
        val tiles = listOf(tile(0, moisture = PARCHED_MOISTURE_THRESHOLD - 1))
        assertTrue(TribeNeed.Parched in t.needs(tiles, 0L))
    }

    @Test fun `Parched does not fire at exact threshold`() {
        val t = tribe()
        val tiles = listOf(tile(0, moisture = PARCHED_MOISTURE_THRESHOLD))
        assertFalse(TribeNeed.Parched in t.needs(tiles, 0L))
    }

    @Test fun `Parched uses average moisture across all tiles`() {
        // one dry tile + one wet tile; average = (10+80)/2 = 45 → not Parched
        val t = tribe()
        val tiles = listOf(tile(0, moisture = 10), tile(1, moisture = 80))
        assertFalse(TribeNeed.Parched in t.needs(tiles, 0L))
    }

    // ── Hungry ────────────────────────────────────────────────────────────────

    @Test fun `Hungry fires when foodSupply is below population times threshold`() {
        // pop=100, threshold=3 → boundary at 300; food=299 → Hungry
        val t = tribe(population = 100, foodSupply = 100 * HUNGRY_FOOD_TICKS - 1)
        assertTrue(TribeNeed.Hungry in t.needs(emptyList(), 0L))
    }

    @Test fun `Hungry does not fire at exactly the threshold`() {
        val t = tribe(population = 100, foodSupply = 100 * HUNGRY_FOOD_TICKS)
        assertFalse(TribeNeed.Hungry in t.needs(emptyList(), 0L))
    }

    // ── Starving ──────────────────────────────────────────────────────────────

    @Test fun `Starving fires when foodSupply is zero`() {
        val t = tribe(population = 50, foodSupply = 0)
        assertTrue(TribeNeed.Starving in t.needs(emptyList(), 0L))
    }

    @Test fun `Starving and Hungry both fire when foodSupply is zero`() {
        val t = tribe(population = 50, foodSupply = 0)
        val needs = t.needs(emptyList(), 0L)
        assertTrue(TribeNeed.Starving in needs)
        assertTrue(TribeNeed.Hungry in needs)
    }

    @Test fun `Starving does not fire when foodSupply is above zero`() {
        val t = tribe(population = 50, foodSupply = 1)
        assertFalse(TribeNeed.Starving in t.needs(emptyList(), 0L))
    }

    // ── Endangered ────────────────────────────────────────────────────────────

    @Test fun `Endangered fires when population is below minimum`() {
        val t = tribe(population = MIN_VIABLE_POPULATION - 1)
        assertTrue(TribeNeed.Endangered in t.needs(emptyList(), 0L))
    }

    @Test fun `Endangered does not fire at exact minimum population`() {
        val t = tribe(population = MIN_VIABLE_POPULATION)
        assertFalse(TribeNeed.Endangered in t.needs(emptyList(), 0L))
    }

    // ── UnderThreat ───────────────────────────────────────────────────────────

    @Test fun `UnderThreat fires when raided within window`() {
        val t = tribe(lastRaidTick = 10L)
        assertTrue(TribeNeed.UnderThreat in t.needs(emptyList(), 10L + UNDER_THREAT_TICKS))
    }

    @Test fun `UnderThreat does not fire after window expires`() {
        val t = tribe(lastRaidTick = 10L)
        assertFalse(TribeNeed.UnderThreat in t.needs(emptyList(), 10L + UNDER_THREAT_TICKS + 1L))
    }

    @Test fun `UnderThreat does not fire for never-raided tribe`() {
        val t = tribe(lastRaidTick = -1L)
        assertFalse(TribeNeed.UnderThreat in t.needs(emptyList(), 100L))
    }

    // ── SpirituallyDepleted ───────────────────────────────────────────────────

    @Test fun `SpirituallyDepleted fires when skepticism high and devotion low`() {
        val t = tribe(skepticism = SPIRITUALLY_DEPLETED_SKEPTICISM + 1, devotion = SPIRITUALLY_DEPLETED_DEVOTION - 1)
        assertTrue(TribeNeed.SpirituallyDepleted in t.needs(emptyList(), 0L))
    }

    @Test fun `SpirituallyDepleted does not fire when skepticism is at boundary`() {
        val t = tribe(skepticism = SPIRITUALLY_DEPLETED_SKEPTICISM, devotion = SPIRITUALLY_DEPLETED_DEVOTION - 1)
        assertFalse(TribeNeed.SpirituallyDepleted in t.needs(emptyList(), 0L))
    }

    @Test fun `SpirituallyDepleted does not fire when devotion is at boundary`() {
        val t = tribe(skepticism = SPIRITUALLY_DEPLETED_SKEPTICISM + 1, devotion = SPIRITUALLY_DEPLETED_DEVOTION)
        assertFalse(TribeNeed.SpirituallyDepleted in t.needs(emptyList(), 0L))
    }

    // ── Overcrowded ───────────────────────────────────────────────────────────

    @Test fun `Overcrowded fires when pop-per-tile exceeds density threshold`() {
        // pop=90, tiles=9 → density=10 > NEEDS_DENSITY_THRESHOLD(8)
        val t = tribe(population = 90)
        val tiles = (0 until 9).map { tile(it, owner = "t") }
        assertTrue(TribeNeed.Overcrowded in t.needs(tiles, 0L))
    }

    @Test fun `Overcrowded does not fire at exactly threshold`() {
        // pop=80, tiles=10 → density=8 == threshold → NOT overcrowded
        val t = tribe(population = 80)
        val tiles = (0 until 10).map { tile(it, owner = "t") }
        assertFalse(TribeNeed.Overcrowded in t.needs(tiles, 0L))
    }

    @Test fun `Overcrowded does not fire for tribe with no tiles`() {
        val t = tribe(population = 500)
        assertFalse(TribeNeed.Overcrowded in t.needs(emptyList(), 0L))
    }

    // ── Thriving ──────────────────────────────────────────────────────────────

    @Test fun `Thriving fires when no other needs are active`() {
        // pop=50 on 7 tiles → density=7.1 ≤ 8, moisture=60 ≥ 25, food=1000 ≥ 50*3=150
        val t = tribe(population = 50, foodSupply = 1000, devotion = 50, skepticism = 0)
        val tiles = (0 until 7).map { tile(it, moisture = 60) }
        assertEquals(setOf(TribeNeed.Thriving), t.needs(tiles, 0L))
    }

    @Test fun `Thriving does not fire when any need is active`() {
        val t = tribe(population = 100, foodSupply = 0)
        assertFalse(TribeNeed.Thriving in t.needs(emptyList(), 0L))
    }

    // ── Relevance scoring ─────────────────────────────────────────────────────

    private fun baseState(t: Tribe, divineFavor: Int = 100) = WorldState(
        worldTimeTick = 0L,
        divineFavor = divineFavor,
        tiles = emptyList(),
        tribes = mapOf("t" to t),
    )

    @Test fun `relevance 1·0 - action matches primary need (full reset)`() {
        // SpirituallyDepleted tribe + InspireDevout.primaryNeed=SpirituallyDepleted
        // pressureReset = 80f × (0.3 + 0.5 × 1.0) = 64f → newPressure = 16f
        val t = tribe(devotion = 20, skepticism = 70, foodSupply = 10_000)
            .copy(prayerPressure = 80f)
        val after = tick(baseState(t), action = DivineAction.InspireDevout)
        assertEquals(16f, after.tribes["t"]!!.prayerPressure, 0.01f)
    }

    @Test fun `relevance 0·6 - action matches secondary need (partial reset)`() {
        // CastRain.secondaryNeed = Hungry; tribe is Hungry but not Parched
        // pressureReset = 80f × (0.3 + 0.5 × 0.6) = 48f → newPressure = 32f
        val t = tribe(population = 100, foodSupply = 1, devotion = 50) // foodSupply=1 < 100*3 → Hungry
            .copy(prayerPressure = 80f)
        val after = tick(baseState(t), action = DivineAction.CastRain)
        assertEquals(32f, after.tribes["t"]!!.prayerPressure, 0.01f)
    }

    @Test fun `relevance 0·2 - action matches no need (minimal reset)`() {
        // Thriving tribe + CauseFamine (no primaryNeed, no secondaryNeed)
        // pressureReset = 80f × (0.3 + 0.5 × 0.2) = 32f → newPressure = 48f
        val t = tribe(population = 50, foodSupply = 10_000, devotion = 50, skepticism = 0)
            .copy(prayerPressure = 80f)
        val after = tick(baseState(t), action = DivineAction.CauseFamine)
        assertEquals(48f, after.tribes["t"]!!.prayerPressure, 0.01f)
    }

    // ── conflictStep lastRaidTick ─────────────────────────────────────────────

    @Test fun `conflictStep sets lastRaidTick on defender when raid succeeds`() {
        val tiles = listOf(
            MapTile(id = 0, col = 0, row = 0, occupantTribeId = "alpha", biome = BiomeType.Grassland),
            MapTile(id = 1, col = 0, row = 0, occupantTribeId = "alpha", biome = BiomeType.Grassland),
            MapTile(id = 2, col = 1, row = 0, occupantTribeId = "beta",  biome = BiomeType.Grassland),
            MapTile(id = 3, col = 1, row = 0, occupantTribeId = "beta",  biome = BiomeType.Grassland),
        )
        val aggressor = tribe(population = 200).copy(
            tribeId = "alpha",
            personality = tribe().personality.copy(aggression = 1.0f, caution = 0.5f),
            devotion = 0,
        )
        val defender = tribe(population = 200).copy(
            tribeId = "beta",
            personality = tribe().personality.copy(aggression = 0.0f, caution = 0.0f),
            devotion = 0,
        )
        val state = WorldState(
            worldTimeTick = 42L,
            divineFavor = 50,
            tiles = tiles,
            tribes = mapOf("alpha" to aggressor, "beta" to defender),
        )

        val result = conflictStep(state, Random(seed = 0L))

        // Assert the raid happened, then validate lastRaidTick unconditionally
        assertEquals(1, result.tiles.count { it.occupantTribeId == "beta" })
        assertEquals(42L, result.tribes["beta"]!!.lastRaidTick)
    }

    @Test fun `conflictStep does not set lastRaidTick when no raid occurs`() {
        // aggression=0 → threshold=0 → never raids
        val tiles = listOf(
            MapTile(id = 0, col = 0, row = 0, occupantTribeId = "alpha", biome = BiomeType.Grassland),
            MapTile(id = 2, col = 1, row = 0, occupantTribeId = "beta",  biome = BiomeType.Grassland),
        )
        val state = WorldState(
            worldTimeTick = 10L,
            divineFavor = 50,
            tiles = tiles,
            tribes = mapOf(
                "alpha" to tribe().copy(tribeId = "alpha", personality = tribe().personality.copy(aggression = 0.0f)),
                "beta"  to tribe().copy(tribeId = "beta"),
            ),
        )

        val result = conflictStep(state, Random.Default)

        assertEquals(-1L, result.tribes["beta"]!!.lastRaidTick)
    }

    // ── Split viability guard ─────────────────────────────────────────────────

    private fun splitState(
        population: Int = 500,
        foodSupply: Int,
        tileCount: Int = 50,
    ): WorldState {
        val tiles = (0 until tileCount).map { i ->
            MapTile(id = i, col = i % 16, row = i / 16, occupantTribeId = "alpha", biome = BiomeType.Grassland)
        }
        return WorldState(
            worldTimeTick = 200L,
            divineFavor = 50,
            tiles = tiles,
            tribes = mapOf(
                "alpha" to Tribe(
                    tribeId = "alpha", name = "Alpha",
                    population = population, devotion = 50, foodSupply = foodSupply,
                )
            ),
            lastSplitTick = 0L,
        )
    }

    @Test fun `splitStep is suppressed when child food falls below viability threshold`() {
        // pop=500 → parentPop=300, childPop=200
        // childFood must be >= 200 × SPLIT_MIN_FOOD_TICKS=1 = 200 to be viable
        // with foodSupply=400: parentFood=240, childFood=160 < 200 → blocked
        val state = splitState(population = 500, foodSupply = 400)

        val result = splitStep(state)

        assertEquals(1, result.tribes.size)
    }

    @Test fun `splitStep proceeds when child food meets viability threshold`() {
        // childFood = 3000 * 0.4 = 1200 >= 200 × 1 = 200 → viable
        val state = splitState(population = 500, foodSupply = 3000)

        val result = splitStep(state)

        assertEquals(2, result.tribes.size)
    }

    @Test fun `splitStep blocked exactly at boundary (childFood equals threshold)`() {
        // Need childFood == childPop × SPLIT_MIN_FOOD_TICKS to be exactly at the boundary (just passes)
        // childPop = pop - (pop * 0.6).roundToInt() = 500 - 300 = 200
        // Need childFood >= 200 × 1 = 200
        // foodSupply=500: parentFood=300, childFood=200 → exactly 200 >= 200 → VIABLE
        val state = splitState(population = 500, foodSupply = 500)

        val result = splitStep(state)

        assertEquals(2, result.tribes.size)
    }
}
