package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_COOLDOWN_TICKS
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_DENSITY_THRESHOLD
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_DEVOTION_CAP
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_MIN_POPULATION
import com.github.maskedkunisquat.projectecho.domain.rules.splitStep
import com.github.maskedkunisquat.projectecho.domain.rules.territoryStep
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        tiles = emptyList(),
        tribes = mapOf(
            "echosi" to Tribe(
                tribeId = "echosi",
                name = "The Echosi",
                population = population,
                devotion = devotion,
                foodSupply = foodSupply,
            )
        ),
    )

    // --- Phase 1 ---

    @Test
    fun `thriving tick - food is plentiful`() {
        val state = stableState(population = 100, foodSupply = 200, divineFavor = 10)

        val result = tick(state)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(11, result.divineFavor)                               // 10 + 1 regen (devotion 51 ≥ 40)
        assertEquals(180, result.tribes["echosi"]!!.foodSupply)            // 200 + (100*0.8=80) - 100
        assertEquals(102, result.tribes["echosi"]!!.population)            // (100 * 1.02).roundToInt()
        assertEquals(51, result.tribes["echosi"]!!.devotion)              // 50 + 1
    }

    @Test
    fun `starving tick - food runs out`() {
        val state = stableState(population = 100, foodSupply = 0, devotion = 20, divineFavor = 10)
        // 0 + (100*0.8=80) - 100 = -20 → starvation

        val result = tick(state)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(10, result.divineFavor)                               // no regen: devotion 17 < 40
        assertEquals(0, result.tribes["echosi"]!!.foodSupply)             // clamped to 0
        assertEquals(95, result.tribes["echosi"]!!.population)            // (100 * 0.95).roundToInt()
        assertEquals(17, result.tribes["echosi"]!!.devotion)              // 20 - 3
    }

    // --- Phase 2: Divine Interventions ---

    @Test
    fun `CastRain - deducts favor without direct food bonus`() {
        val state = stableState(population = 50, foodSupply = 100, divineFavor = 20)

        val result = tick(state, DivineAction.CastRain)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(11, result.divineFavor)                               // 20 - 10 (CastRain) + 1 regen
        assertEquals(90, result.tribes["echosi"]!!.foodSupply)            // 100 + (50*0.8=40) - 50, no rain bonus
        assertEquals(51, result.tribes["echosi"]!!.population)            // grew since fed
    }

    @Test
    fun `CastRain - raises soilMoisture and volatility on targeted tiles`() {
        val tile = MapTile(
            id = 0, col = 0, row = 0,
            soilMoisture = 40, volatility = 10,
            occupantTribeId = "echosi",
            biome = BiomeType.Grassland,
        )
        val state = WorldState(
            worldTimeTick = 0L,
            divineFavor = 20,
            tiles = listOf(tile),
            tribes = mapOf("echosi" to Tribe(tribeId = "echosi", name = "The Echosi", population = 5, devotion = 50, foodSupply = 200)),
        )

        val result = tick(state, DivineAction.CastRain, targetCluster = listOf(0))

        val resultTile = result.tiles[0]
        // decayStep first: soilMoisture 40→39 (baseline 35 → -1), volatility 10→9
        // CastRain effect: soilMoisture 39+25=64, volatility 9+10=19
        assertEquals(64, resultTile.soilMoisture)
        assertEquals(19, resultTile.volatility)
    }

    @Test
    fun `SendPlague - reduces population and deducts favor`() {
        val state = stableState(population = 100, foodSupply = 300, divineFavor = 20)

        val result = tick(state, DivineAction.SendPlague)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(6, result.divineFavor)                                // 20 - 15 (SendPlague) + 1 regen (devotion 51 ≥ 40)
        assertEquals(82, result.tribes["echosi"]!!.population)            // (80 * 1.02).roundToInt()
        assertEquals(284, result.tribes["echosi"]!!.foodSupply)           // 300 + (80*0.8=64) - 80
    }

    @Test
    fun `InspireDevout - boosts devotion and deducts favor`() {
        val state = stableState(devotion = 50, divineFavor = 20)

        val result = tick(state, DivineAction.InspireDevout)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(13, result.divineFavor)                               // 20 - 8 (InspireDevout) + 1 regen (devotion 66 ≥ 40)
        assertEquals(66, result.tribes["echosi"]!!.devotion)              // 50 + 15 (action) + 1 (thriving tick)
    }

    @Test
    fun `InspireDevout - devotion clamped at 100`() {
        val state = stableState(devotion = 90, divineFavor = 20)

        val result = tick(state, DivineAction.InspireDevout)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(100, result.tribes["echosi"]!!.devotion)             // min(100, 90+15)=100, then +1 clamped to 100
    }

    @Test
    fun `CauseFamine - drains food and deducts favor`() {
        val state = stableState(population = 50, foodSupply = 200, divineFavor = 20)

        val result = tick(state, DivineAction.CauseFamine)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(16, result.divineFavor)                               // 20 - 5 (CauseFamine) + 1 regen (devotion 51 ≥ 40)
        assertEquals(110, result.tribes["echosi"]!!.foodSupply)           // (200-80) + (50*0.8=40) - 50
    }

    @Test
    fun `CauseFamine - food clamped at zero`() {
        val state = stableState(population = 50, foodSupply = 30, divineFavor = 20)

        val result = tick(state, DivineAction.CauseFamine)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(0, result.tribes["echosi"]!!.foodSupply)             // max(0, 30-80)=0; starvation tick keeps it 0
    }

    @Test
    fun `BlessHarvest - large food bonus and deducts favor`() {
        val state = stableState(population = 50, foodSupply = 100, divineFavor = 30)

        val result = tick(state, DivineAction.BlessHarvest)

        assertEquals(1L, result.worldTimeTick)
        assertEquals(11, result.divineFavor)                               // 30 - 20 (BlessHarvest) + 1 regen (devotion 51 ≥ 40)
        assertEquals(290, result.tribes["echosi"]!!.foodSupply)           // (100+200) + (50*0.8=40) - 50
    }

    @Test
    fun `action skipped when favor is insufficient`() {
        val state = stableState(population = 50, foodSupply = 300, divineFavor = 3)

        val result = tick(state, DivineAction.CastRain)                    // costs 10, player has 3

        assertEquals(1L, result.worldTimeTick)
        assertEquals(4, result.divineFavor)                                // action skipped + 1 regen (devotion 51 ≥ 40)
        assertEquals(290, result.tribes["echosi"]!!.foodSupply)           // 300 + (50*0.8=40) - 50, no +50 from CastRain
    }

    @Test
    fun `tick with empty tribes map advances worldTimeTick without throwing`() {
        val emptyState = stableState().copy(tribes = emptyMap())
        val result = tick(emptyState)
        assertEquals(1L, result.worldTimeTick)
    }

    @Test
    fun `favor clamped to hundred when above max`() {
        val state = stableState(divineFavor = 150)                         // above the [0,100] range

        val result = tick(state, DivineAction.CastRain)                    // costs 10 → (150-10)=140 → coerceIn → 100

        assertEquals(1L, result.worldTimeTick)
        assertEquals(100, result.divineFavor)
    }

    // --- Phase 12A: Tribal Splitting ---

    private fun makeTile(id: Int, tribeId: String?, biome: BiomeType = BiomeType.Grassland): MapTile {
        val cellIdx = id / 2
        return MapTile(
            id = id,
            col = cellIdx % GRID_COLS,
            row = cellIdx / GRID_COLS,
            biome = biome,
            occupantTribeId = tribeId,
        )
    }

    private fun splitReadyState(
        tribeId: String = "alpha",
        population: Int = 500,
        foodSupply: Int = 3000,
        tileCount: Int = 50,
        currentTick: Long = 200L,
        lastSplitTick: Long = 0L,
    ): WorldState {
        val tiles = (0 until tileCount).map { i -> makeTile(i, tribeId) }
        return WorldState(
            worldTimeTick = currentTick,
            divineFavor = 50,
            tiles = tiles,
            tribes = mapOf(
                tribeId to Tribe(
                    tribeId = tribeId,
                    name = "The Alpha",
                    population = population,
                    devotion = 50,
                    foodSupply = foodSupply,
                )
            ),
            lastSplitTick = lastSplitTick,
        )
    }

    @Test
    fun `splitStep - split triggers when population and density conditions met`() {
        // 500 pop / 50 tiles = density 10 > SPLIT_DENSITY_THRESHOLD(8); pop ≥ SPLIT_MIN_POPULATION(400)
        val state = splitReadyState()

        val result = splitStep(state)

        assertEquals(2, result.tribes.size)
        assertTrue(result.eventHistory.any { "fractures" in it })
    }

    @Test
    fun `splitStep - blocked when population is below minimum`() {
        // density = 399 / 10 = 39 > threshold, but pop < SPLIT_MIN_POPULATION
        val state = splitReadyState(population = SPLIT_MIN_POPULATION - 1, tileCount = 10)

        val result = splitStep(state)

        assertEquals(1, result.tribes.size)
    }

    @Test
    fun `splitStep - blocked when density is at or below threshold`() {
        // 400 / 50 = 8 == SPLIT_DENSITY_THRESHOLD → condition is <=, so no split
        val state = splitReadyState(population = SPLIT_MIN_POPULATION, tileCount = 50)

        val result = splitStep(state)

        assertEquals(1, result.tribes.size)
    }

    @Test
    fun `splitStep - all tiles accounted for with no overlap`() {
        val tribeId = "alpha"
        val state = splitReadyState(tribeId = tribeId)

        val result = splitStep(state)

        assertEquals(2, result.tribes.size)
        val childId = result.tribes.keys.first { it != tribeId }
        val parentTileIds = result.tiles.filter { it.occupantTribeId == tribeId }.map { it.id }.toSet()
        val childTileIds  = result.tiles.filter { it.occupantTribeId == childId  }.map { it.id }.toSet()
        val originalIds   = state.tiles.map { it.id }.toSet()
        assertEquals(originalIds, parentTileIds + childTileIds)
        assertTrue((parentTileIds intersect childTileIds).isEmpty())
    }

    @Test
    fun `splitStep - population is conserved across the split`() {
        val tribeId = "alpha"
        val originalPop = 500
        val state = splitReadyState(tribeId = tribeId, population = originalPop)

        val result = splitStep(state)

        val childId   = result.tribes.keys.first { it != tribeId }
        val parentPop = result.tribes[tribeId]!!.population
        val childPop  = result.tribes[childId]!!.population
        // allow ±1 for integer rounding
        assertTrue(kotlin.math.abs((parentPop + childPop) - originalPop) <= 1)
    }

    @Test
    fun `splitStep - food supply is conserved across the split`() {
        val tribeId = "alpha"
        val originalFood = 3000
        val state = splitReadyState(tribeId = tribeId, foodSupply = originalFood)

        val result = splitStep(state)

        val childId    = result.tribes.keys.first { it != tribeId }
        val parentFood = result.tribes[tribeId]!!.foodSupply
        val childFood  = result.tribes[childId]!!.foodSupply
        assertTrue(kotlin.math.abs((parentFood + childFood) - originalFood) <= 1)
    }

    @Test
    fun `splitStep - cooldown blocks split within window`() {
        // lastSplitTick=100, currentTick=150: 150 < 100 + SPLIT_COOLDOWN_TICKS(100) → blocked
        val state = splitReadyState(currentTick = 150L, lastSplitTick = 100L)

        val result = splitStep(state)

        assertEquals(1, result.tribes.size)
    }

    @Test
    fun `splitStep - blocked when tribe devotion exceeds SPLIT_DEVOTION_CAP`() {
        // 500 pop / 50 tiles = density 10 > threshold, but devotion 90 > SPLIT_DEVOTION_CAP(80) → blocked
        val state = splitReadyState().let { s ->
            s.copy(tribes = s.tribes.mapValues { (_, t) -> t.copy(devotion = SPLIT_DEVOTION_CAP + 10) })
        }
        val result = splitStep(state)
        assertEquals(1, result.tribes.size)
    }

    @Test
    fun `splitStep - split allowed after cooldown has expired`() {
        // lastSplitTick=100, currentTick=200: 200 >= 100 + 100 → allowed
        val state = splitReadyState(currentTick = 200L, lastSplitTick = 100L)

        val result = splitStep(state)

        assertEquals(2, result.tribes.size)
    }

    @Test
    fun `territoryStep - release branch runs in single-tribe world`() {
        // pop=10 → expected = 10 * 192 / 500 = 3; tribe has 20 tiles → 17 released
        val tiles  = (0 until 20).map { i -> makeTile(i, "alpha") }
        val tribes = mapOf("alpha" to Tribe("alpha", "Alpha", population = 10, devotion = 50, foodSupply = 100))

        val result = territoryStep(tiles, tribes)

        assertEquals(3, result.count { it.occupantTribeId == "alpha" })
    }

    @Test
    fun `territoryStep - release branch runs in multi-tribe world`() {
        // Phase 12c restores release for multi-tribe; same excess as single-tribe case → 17 released
        val tilesA = (0 until 20).map { i -> makeTile(i,  "alpha") }
        val tilesB = (20 until 30).map { i -> makeTile(i, "beta") }
        val tribes = mapOf(
            "alpha" to Tribe("alpha", "Alpha", population = 10, devotion = 50, foodSupply = 100),
            "beta"  to Tribe("beta",  "Beta",  population = 10, devotion = 50, foodSupply = 100),
        )

        val result = territoryStep(tilesA + tilesB, tribes)

        assertEquals(3, result.count { it.occupantTribeId == "alpha" })
    }
}
