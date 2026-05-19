package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.EnvironmentalPhase
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.DECAY_DELTA
import com.github.maskedkunisquat.projectecho.domain.rules.MOISTURE_BASELINE
import com.github.maskedkunisquat.projectecho.domain.rules.decayStep
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentalPhaseTest {

    // --- Phase classification (boundary conditions) ---

    @Test fun `from 0 is Parched`() = assertEquals(EnvironmentalPhase.Parched, EnvironmentalPhase.from(0))
    @Test fun `from 20 is Parched - upper boundary`() = assertEquals(EnvironmentalPhase.Parched, EnvironmentalPhase.from(20))
    @Test fun `from 21 is Fertile - lower boundary`() = assertEquals(EnvironmentalPhase.Fertile, EnvironmentalPhase.from(21))
    @Test fun `from 50 is Fertile - upper boundary`() = assertEquals(EnvironmentalPhase.Fertile, EnvironmentalPhase.from(50))
    @Test fun `from 51 is Saturated - lower boundary`() = assertEquals(EnvironmentalPhase.Saturated, EnvironmentalPhase.from(51))
    @Test fun `from 80 is Saturated - upper boundary`() = assertEquals(EnvironmentalPhase.Saturated, EnvironmentalPhase.from(80))
    @Test fun `from 81 is Deluge - lower boundary`() = assertEquals(EnvironmentalPhase.Deluge, EnvironmentalPhase.from(81))
    @Test fun `from 100 is Deluge - max moisture`() = assertEquals(EnvironmentalPhase.Deluge, EnvironmentalPhase.from(100))

    // --- Food multipliers ---

    @Test fun `Parched multiplier is 0_1`() = assertEquals(0.1, EnvironmentalPhase.Parched.foodMultiplier, 0.001)
    @Test fun `Saturated multiplier is 0_5`() = assertEquals(0.5, EnvironmentalPhase.Saturated.foodMultiplier, 0.001)
    @Test fun `Fertile multiplier is 1_0`() = assertEquals(1.0, EnvironmentalPhase.Fertile.foodMultiplier, 0.001)
    @Test fun `Deluge multiplier is 0_0`() = assertEquals(0.0, EnvironmentalPhase.Deluge.foodMultiplier, 0.001)

    // --- Decay convergence ---

    @Test
    fun `decay nudges tile above baseline toward baseline`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = MOISTURE_BASELINE + 10)
        val result = decayStep(listOf(tile))
        assertEquals(MOISTURE_BASELINE + 10 - DECAY_DELTA, result[0].soilMoisture)
    }

    @Test
    fun `decay nudges tile below baseline toward baseline`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = MOISTURE_BASELINE - 10)
        val result = decayStep(listOf(tile))
        assertEquals(MOISTURE_BASELINE - 10 + DECAY_DELTA, result[0].soilMoisture)
    }

    @Test
    fun `decay does not overshoot baseline from above`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = MOISTURE_BASELINE + 1)
        val result = decayStep(listOf(tile))
        assertEquals(MOISTURE_BASELINE, result[0].soilMoisture)
    }

    @Test
    fun `decay does not overshoot baseline from below`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = MOISTURE_BASELINE - 1)
        val result = decayStep(listOf(tile))
        assertEquals(MOISTURE_BASELINE, result[0].soilMoisture)
    }

    @Test
    fun `decay at baseline leaves moisture unchanged`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = MOISTURE_BASELINE)
        val result = decayStep(listOf(tile))
        assertEquals(MOISTURE_BASELINE, result[0].soilMoisture)
    }

    @Test
    fun `decay reduces volatility by delta each step`() {
        val tile = MapTile(id = 0, col = 0, row = 0, volatility = 10)
        val result = decayStep(listOf(tile))
        assertEquals(10 - DECAY_DELTA, result[0].volatility)
    }

    @Test
    fun `decay does not make volatility negative`() {
        val tile = MapTile(id = 0, col = 0, row = 0, volatility = 0)
        val result = decayStep(listOf(tile))
        assertEquals(0, result[0].volatility)
    }

    // --- Tick integration: phase multipliers affect food output ---

    private fun tileFor(tribeId: String, moisture: Int, id: Int = 0) =
        MapTile(id = id, col = 0, row = 0, soilMoisture = moisture, occupantTribeId = tribeId)

    private fun stateWithTiles(
        population: Int = 100,
        foodSupply: Int = 300,
        devotion: Int = 50,
        tiles: List<MapTile>,
    ) = WorldState(
        worldTimeTick = 0L,
        divineFavor = 50,
        tiles = tiles,
        tribes = mapOf("echosi" to Tribe("echosi", "The Echosi", population, devotion, foodSupply)),
    )

    @Test
    fun `Fertile tiles give full farming output`() {
        val state = stateWithTiles(
            population = 100, foodSupply = 200,
            tiles = listOf(tileFor("echosi", moisture = 35)),
        )
        val result = tick(state)
        // farmed = (100 * 0.8 * 1.0).roundToInt() = 80; newFood = 200 + 80 - 100 = 180
        assertEquals(180, result.tribes["echosi"]!!.foodSupply)
        assertEquals(102, result.tribes["echosi"]!!.population)
    }

    @Test
    fun `Saturated tiles give 50 percent farming output`() {
        val state = stateWithTiles(
            population = 100, foodSupply = 200,
            tiles = listOf(tileFor("echosi", moisture = 65)),
        )
        val result = tick(state)
        // farmed = (100 * 0.8 * 0.5).roundToInt() = 40; newFood = 200 + 40 - 100 = 140
        assertEquals(140, result.tribes["echosi"]!!.foodSupply)
        assertEquals(102, result.tribes["echosi"]!!.population)
    }

    @Test
    fun `Parched tiles cause near-starvation yield`() {
        val state = stateWithTiles(
            population = 100, foodSupply = 50,
            tiles = listOf(tileFor("echosi", moisture = 10)),
        )
        val result = tick(state)
        // farmed = (100 * 0.8 * 0.1).roundToInt() = 8; newFood = 50 + 8 - 100 = -42 → starvation
        assertEquals(0, result.tribes["echosi"]!!.foodSupply)
        assertEquals(95, result.tribes["echosi"]!!.population)
        assertEquals(47, result.tribes["echosi"]!!.devotion)
    }

    @Test
    fun `Deluge tiles cause zero farming and apply population casualties`() {
        val state = stateWithTiles(
            population = 100, foodSupply = 500,
            tiles = listOf(tileFor("echosi", moisture = 90)),
        )
        val result = tick(state)
        // farmed = 0; newFood = 500 + 0 - 100 = 400 (positive)
        // afterSurvival: pop = (100 * 1.02).roundToInt() = 102
        // Deluge casualties: pop = (102 * 0.97).roundToInt() = 99
        assertEquals(400, result.tribes["echosi"]!!.foodSupply)
        assertEquals(99, result.tribes["echosi"]!!.population)
    }

    @Test
    fun `empty tile list defaults to Fertile baseline - regression guard`() {
        val tribe = Tribe("echosi", "The Echosi", 100, 50, 200)
        val state = WorldState(0L, 50, emptyList(), mapOf("echosi" to tribe))
        val result = tick(state)
        // Behaviour must match pre-Phase-7: farmed = 80, newFood = 180
        assertEquals(180, result.tribes["echosi"]!!.foodSupply)
        assertEquals(102, result.tribes["echosi"]!!.population)
    }
}
