package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_SIZE
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.tick
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
        nextSpawnTick: Long = Long.MAX_VALUE,
    ) = WorldState(
        worldTimeTick = tick,
        divineFavor = 50,
        tiles = tiles,
        tribes = emptyMap(),
        activeFront = front,
        nextSpawnTick = nextSpawnTick,
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
        val front = WeatherFront(type = WeatherType.RainCloud, column = GRID_COLS - 1, direction = 1)
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
    fun `no front spawned before nextSpawnTick`() {
        val result = weatherStep(stateWithFront(null, tick = 9L, nextSpawnTick = 10L))
        assertNull(result.activeFront)
    }

    @Test
    fun `front spawns when worldTimeTick reaches nextSpawnTick`() {
        val result = weatherStep(stateWithFront(null, tick = 10L, nextSpawnTick = 10L), Random(42))

        assertNotNull(result.activeFront)
        // Spawn tick also advances the front: col 0 → 1 (eastward) or col 15 → 14 (westward)
        val col = result.activeFront!!.column
        assertTrue("spawned front must have advanced one step inward", col == 1 || col == GRID_COLS - 2)
    }

    @Test
    fun `spawned front direction matches its edge`() {
        val rng = Random(0)
        val result = weatherStep(stateWithFront(null, tick = 10L, nextSpawnTick = 10L), rng)

        val front = result.activeFront
        if (front != null) {
            if (front.column <= 1) assertEquals(1, front.direction)
            if (front.column >= GRID_COLS - 2) assertEquals(-1, front.direction)
        }
    }

    @Test
    fun `spawn appends warning to event history`() {
        val result = weatherStep(stateWithFront(null, tick = 10L, nextSpawnTick = 10L), Random(42))

        assertTrue(result.eventHistory.isNotEmpty())
    }

    @Test
    fun `no spawn when active front already exists`() {
        val existingFront = WeatherFront(type = WeatherType.RainCloud, column = 7, direction = 1)
        val result = weatherStep(stateWithFront(existingFront, tick = 10L, nextSpawnTick = 10L), Random(42))

        // Existing front just advances — no new spawn
        assertEquals(8, result.activeFront?.column)
    }

    @Test
    fun `nextSpawnTick randomised on front exit`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = GRID_COLS - 1, direction = 1)
        val result = weatherStep(stateWithFront(front, tick = 50L), Random(42))

        assertNull(result.activeFront)
        // nextSpawnTick must be in range [50+20, 50+40] = [70, 90]
        assertTrue(result.nextSpawnTick in 70L..90L)
    }

    // --- Pipeline ordering: decay before weather ---

    @Test
    fun `tick preserves full weather delta for the current tick before decay runs`() {
        // Tiles start at baseline (35) — decay leaves them unchanged.
        // Rain front on column 5: weatherStep should push col-5 tiles to 50 (+15).
        // If decayStep ran after weatherStep in the same tick it would reduce col-5 to 49,
        // so this test fails on the wrong ordering.
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val state = stateWithFront(front)

        val result = tick(state)

        result.tiles.filter { it.col == 5 }.forEach { assertEquals(50, it.soilMoisture) }
        result.tiles.filter { it.col != 5 }.forEach { assertEquals(35, it.soilMoisture) }
        assertEquals(6, result.activeFront?.column)
    }
}
