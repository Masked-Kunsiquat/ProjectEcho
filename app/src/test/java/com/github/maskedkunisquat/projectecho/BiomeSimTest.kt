package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_SIZE
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.COAST_FISHING_BONUS
import com.github.maskedkunisquat.projectecho.domain.rules.HIGH_VOLATILITY_THRESHOLD
import com.github.maskedkunisquat.projectecho.domain.rules.WEATHER_VOLATILITY_GAIN
import com.github.maskedkunisquat.projectecho.domain.rules.decayStep
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import com.github.maskedkunisquat.projectecho.domain.rules.weatherStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiomeSimTest {

    // --- decayStep biome moisture baseline ---

    @Test
    fun `decayStep drives Desert tile toward moisture baseline 15`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = 35, biome = BiomeType.Desert)
        val result = decayStep(listOf(tile)).first()
        assertEquals(34, result.soilMoisture)   // 35 > Desert baseline 15 → -1
    }

    @Test
    fun `decayStep drives Forest tile upward toward moisture baseline 45`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = 35, biome = BiomeType.Forest)
        val result = decayStep(listOf(tile)).first()
        assertEquals(36, result.soilMoisture)   // 35 < Forest baseline 45 → +1
    }

    @Test
    fun `decayStep keeps Grassland tile stable at baseline 35`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = 35, biome = BiomeType.Grassland)
        val result = decayStep(listOf(tile)).first()
        assertEquals(35, result.soilMoisture)
    }

    @Test
    fun `decayStep does not move moisture for tile already at its biome baseline`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = 15, biome = BiomeType.Desert)
        val result = decayStep(listOf(tile)).first()
        assertEquals(15, result.soilMoisture)
    }

    // --- weatherStep biome weather resistance ---

    private fun makeTiles(
        biome: BiomeType,
        moisture: Int = 35,
        volatility: Int = 0,
    ): List<MapTile> = (0 until GRID_SIZE).map { id ->
        val cellIdx = id / 2
        MapTile(
            id = id,
            col = cellIdx % GRID_COLS,
            row = cellIdx / GRID_COLS,
            soilMoisture = moisture,
            biome = biome,
            volatility = volatility,
        )
    }

    private fun stateWithFront(front: WeatherFront, tiles: List<MapTile>) = WorldState(
        worldTimeTick = 0L,
        divineFavor = 50,
        tiles = tiles,
        tribes = emptyMap(),
        activeFront = front,
        nextSpawnTick = Long.MAX_VALUE,
    )

    @Test
    fun `rain front applies half delta to Desert tiles (resistance 0_5)`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val result = weatherStep(stateWithFront(front, makeTiles(BiomeType.Desert)))
        // 15 * 0.5f * 1.0f = 7.5f → roundToInt() = 8
        result.tiles.filter { it.col == 5 }.forEach { assertEquals(43, it.soilMoisture) }  // 35 + 8
        result.tiles.filter { it.col != 5 }.forEach { assertEquals(35, it.soilMoisture) }
    }

    @Test
    fun `rain front applies 75 percent delta to Forest tiles (resistance 0_75)`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val result = weatherStep(stateWithFront(front, makeTiles(BiomeType.Forest)))
        // 15 * 0.75f * 1.0f = 11.25f → roundToInt() = 11
        result.tiles.filter { it.col == 5 }.forEach { assertEquals(46, it.soilMoisture) }  // 35 + 11
        result.tiles.filter { it.col != 5 }.forEach { assertEquals(35, it.soilMoisture) }
    }

    @Test
    fun `rain front applies zero delta to Water tiles (resistance 0_0)`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val result = weatherStep(stateWithFront(front, makeTiles(BiomeType.Water)))
        result.tiles.filter { it.col == 5 }.forEach { assertEquals(35, it.soilMoisture) }
    }

    // --- volatility amplifies weather delta ---

    @Test
    fun `weather delta doubles at volatility 100`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        // Grassland (resistance 1.0), volatility 100: 15 * 1.0 * (1 + 100/100f) = 30
        val result = weatherStep(stateWithFront(front, makeTiles(BiomeType.Grassland, volatility = 100)))
        result.tiles.filter { it.col == 5 }.forEach { assertEquals(65, it.soilMoisture) }  // 35 + 30
    }

    @Test
    fun `weather delta amplified at volatility 50`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        // Grassland, volatility 50: 15 * 1.0 * (1 + 50/100f) = 22.5f → 23
        val result = weatherStep(stateWithFront(front, makeTiles(BiomeType.Grassland, volatility = 50)))
        result.tiles.filter { it.col == 5 }.forEach { assertEquals(58, it.soilMoisture) }  // 35 + 23
    }

    // --- volatility gain from weather ---

    @Test
    fun `weather front increases volatility on land tiles in current column`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val result = weatherStep(stateWithFront(front, makeTiles(BiomeType.Grassland)))
        result.tiles.filter { it.col == 5 }.forEach {
            assertEquals(WEATHER_VOLATILITY_GAIN, it.volatility)
        }
        result.tiles.filter { it.col != 5 }.forEach {
            assertEquals(0, it.volatility)
        }
    }

    @Test
    fun `Water tiles do not gain volatility from weather front`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val result = weatherStep(stateWithFront(front, makeTiles(BiomeType.Water)))
        result.tiles.filter { it.col == 5 }.forEach { assertEquals(0, it.volatility) }
    }

    // --- extreme Chronicle events gated on high volatility ---

    @Test
    fun `extreme storm Chronicle entry fires when RainCloud passes high-volatility column`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        // Start at threshold; after WEATHER_VOLATILITY_GAIN the tile crosses > HIGH_VOLATILITY_THRESHOLD
        val tiles = makeTiles(BiomeType.Grassland, volatility = HIGH_VOLATILITY_THRESHOLD)
        val result = weatherStep(stateWithFront(front, tiles))
        assertTrue(result.eventHistory.any { it == "A great storm tears through the valley." })
    }

    @Test
    fun `extreme heat Chronicle entry fires when HeatWave passes high-volatility column`() {
        val front = WeatherFront(type = WeatherType.HeatWave, column = 5, direction = 1)
        val tiles = makeTiles(BiomeType.Grassland, volatility = HIGH_VOLATILITY_THRESHOLD)
        val result = weatherStep(stateWithFront(front, tiles))
        assertTrue(result.eventHistory.any { it == "The land cracks and bleaches under relentless heat." })
    }

    @Test
    fun `no extreme Chronicle entry when column volatility is below threshold`() {
        val front = WeatherFront(type = WeatherType.RainCloud, column = 5, direction = 1)
        val tiles = makeTiles(BiomeType.Grassland, volatility = 0)
        val result = weatherStep(stateWithFront(front, tiles))
        assertTrue(result.eventHistory.none { it == "A great storm tears through the valley." })
    }

    // --- Coast fishing bonus ---

    @Test
    fun `Coast tiles add fishing bonus on top of phase food contribution`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = 35,
                           biome = BiomeType.Coast, occupantTribeId = "t")
        val state = WorldState(
            worldTimeTick = 0L,
            divineFavor = 50,
            tiles = listOf(tile),
            tribes = mapOf("t" to Tribe(tribeId = "t", name = "The Test",
                                        population = 5, devotion = 50, foodSupply = 100)),
        )
        val tribe = tick(state).tribes["t"]!!
        // effectiveFarmers = min(5, 1*10) = 5; Fertile multiplier = 1.5
        // farmed = (5*0.8*1.5).roundToInt() + 1*COAST_FISHING_BONUS = 6 + 5 = 11
        // newFoodSupply = 100 + 11 - 5 = 106
        assertEquals(106, tribe.foodSupply)
    }

    @Test
    fun `non-Coast tile provides no fishing bonus`() {
        val tile = MapTile(id = 0, col = 0, row = 0, soilMoisture = 35,
                           biome = BiomeType.Grassland, occupantTribeId = "t")
        val state = WorldState(
            worldTimeTick = 0L,
            divineFavor = 50,
            tiles = listOf(tile),
            tribes = mapOf("t" to Tribe(tribeId = "t", name = "The Test",
                                        population = 5, devotion = 50, foodSupply = 100)),
        )
        val tribe = tick(state).tribes["t"]!!
        // farmed = (5*0.8*1.5).roundToInt() = 6; no bonus
        // newFoodSupply = 100 + 6 - 5 = 101
        assertEquals(101, tribe.foodSupply)
    }
}
