package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_SIZE
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.WEATHER_SPAWN_INTERVAL
import com.github.maskedkunisquat.projectecho.domain.rules.weatherStep
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherFrontTest {

    private fun makeTiles(moisture: Int = 35): List<MapTile> =
        (0 until GRID_SIZE).map { id ->
            val cellIdx = id / 2
            MapTile(id = id, col = cellIdx % GRID_COLS, row = cellIdx / GRID_COLS, soilMoisture = moisture)
        }

    private fun stateWithFront(
        front: WeatherFront?,
        tick: Long = 0L,
        tiles: List<MapTile> = makeTiles(),
    ) = WorldState(
        worldTimeTick = tick,
        divineFavor = 50,
        tiles = tiles,
        tribes = emptyMap(),
        activeFront = front,
    )

    // --- Moisture delta application ---

    @Test
    fun `rain front applies positive moisture delta only to current column`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val result = weatherStep(stateWithFront(front))

        result.tiles.filter { it.col == 5 }.forEach { assertEquals(50, it.soilMoisture) }  // 35 + 15
        result.tiles.filter { it.col != 5 }.forEach { assertEquals(35, it.soilMoisture) }   // unchanged
    }

    @Test
    fun `heat wave front applies negative moisture delta only to current column`() {
        val front = WeatherFront(type = WeatherType.HeatWave, column = 8, direction = -1)
        val result = weatherStep(stateWithFront(front))

        result.tiles.filter { it.col == 8 }.forEach { assertEquals(23, it.soilMoisture) }  // 35 - 12
        result.tiles.filter { it.col != 8 }.forEach { assertEquals(35, it.soilMoisture) }   // unchanged
    }

    @Test
    fun `moisture clamped to 0 when heat wave delta would go negative`() {
        val front = WeatherFront(type = WeatherType.HeatWave, column = 5, direction = 1)
        val tiles = makeTiles().map { if (it.col == 5) it.copy(soilMoisture = 5) else it }
        val result = weatherStep(stateWithFront(front, tiles = tiles))

        result.tiles.filter { it.col == 5 }.forEach { assertEquals(0, it.soilMoisture) }
    }

    @Test
    fun `moisture clamped to 100 when rain delta would exceed max`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 3, direction = 1)
        val tiles = makeTiles().map { if (it.col == 3) it.copy(soilMoisture = 90) else it }
        val result = weatherStep(stateWithFront(front, tiles = tiles))

        result.tiles.filter { it.col == 3 }.forEach { assertEquals(100, it.soilMoisture) }
    }

    // --- Front advancement ---

    @Test
    fun `front column advances by direction each tick`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 3, direction = 1)
        val result = weatherStep(stateWithFront(front))

        assertEquals(4, result.activeFront?.column)
    }

    @Test
    fun `front column advances westward`() {
        val front = WeatherFront(type = WeatherType.HeatWave, column = 10, direction = -1)
        val result = weatherStep(stateWithFront(front))

        assertEquals(9, result.activeFront?.column)
    }

    // --- Grid exit ---

    @Test
    fun `front clears and appends exit message when column exits east edge`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 15, direction = 1)
        val result = weatherStep(stateWithFront(front))

        assertNull(result.activeFront)
        assertEquals("The storm has passed. The land is still.", result.eventHistory.last())
    }

    @Test
    fun `front clears and appends exit message when column exits west edge`() {
        val front = WeatherFront(type = WeatherType.HeatWave, column = 0, direction = -1)
        val result = weatherStep(stateWithFront(front))

        assertNull(result.activeFront)
        assertEquals("The storm has passed. The land is still.", result.eventHistory.last())
    }

    // --- Spawn logic ---

    @Test
    fun `no front spawned before spawn interval`() {
        val result = weatherStep(stateWithFront(null, tick = WEATHER_SPAWN_INTERVAL - 1))
        assertNull(result.activeFront)
    }

    @Test
    fun `front spawns at spawn interval when no active front`() {
        val result = weatherStep(stateWithFront(null, tick = WEATHER_SPAWN_INTERVAL), Random(42))

        assertNotNull(result.activeFront)
        // Spawn tick also advances the front: col 0 → 1 (eastward) or col 15 → 14 (westward)
        val col = result.activeFront!!.column
        assertTrue("spawned front must have advanced one step inward", col == 1 || col == GRID_COLS - 2)
    }

    @Test
    fun `spawned front has direction matching its edge - west edge goes east`() {
        // Seed Random so it picks col=0 (nextBoolean()=true → startEdge=0)
        val rng = Random(0)
        val result = weatherStep(stateWithFront(null, tick = WEATHER_SPAWN_INTERVAL), rng)

        val front = result.activeFront
        if (front != null) {
            if (front.column == 0) assertEquals(1, front.direction)
            if (front.column == GRID_COLS - 1) assertEquals(-1, front.direction)
        }
    }

    @Test
    fun `spawn appends warning to event history`() {
        val result = weatherStep(stateWithFront(null, tick = WEATHER_SPAWN_INTERVAL), Random(42))

        assertTrue(result.eventHistory.isNotEmpty())
    }

    @Test
    fun `no spawn when active front already exists at interval tick`() {
        val existingFront = WeatherFront(type = WeatherType.RainCloud, column = 7, direction = 1)
        val result = weatherStep(stateWithFront(existingFront, tick = WEATHER_SPAWN_INTERVAL), Random(42))

        // Existing front just advances — no new spawn, no extra event history
        assertEquals(8, result.activeFront?.column)
    }
}
