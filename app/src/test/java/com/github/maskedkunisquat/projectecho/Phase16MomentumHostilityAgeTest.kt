package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_COOLDOWN_TICKS
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_DENSITY_THRESHOLD
import com.github.maskedkunisquat.projectecho.domain.rules.SPLIT_MIN_POPULATION
import com.github.maskedkunisquat.projectecho.domain.rules.conflictStep
import com.github.maskedkunisquat.projectecho.domain.rules.splitStep
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase16MomentumHostilityAgeTest {

    private fun personality(aggression: Float = 0.5f, caution: Float = 0.5f) =
        TribePersonality(
            archetypeId    = "test",
            aggression     = aggression,
            caution        = caution,
            skepticismRate = 1.0f,
            traditionalism = 0.5f,
            biomeAffinity  = emptyMap(),
        )

    private fun tribe(
        id: String,
        pop: Int = 100,
        food: Int = 200,
        devotion: Int = 50,
        aggression: Float = 0.5f,
        caution: Float = 0.5f,
        hostility: Map<String, Float> = emptyMap(),
    ) = Tribe(
        tribeId     = id,
        name        = id.replaceFirstChar { it.uppercase() },
        population  = pop,
        devotion    = devotion,
        foodSupply  = food,
        personality = personality(aggression, caution),
        hostility   = hostility,
    )

    // tile(id, col, row) — id encodes grid position for adjacency: cellIdx = id / 2
    private fun tile(id: Int, col: Int, row: Int, owner: String? = null) =
        MapTile(id = id, col = col, row = row, occupantTribeId = owner)

    private fun worldWith(tiles: List<MapTile>, tribes: Map<String, Tribe>, tick: Long = 0L) = WorldState(
        worldTimeTick = tick,
        divineFavor   = 50,
        tiles         = tiles,
        tribes        = tribes,
    )

    // ── Population & territory momentum ─────────────────────────────────────

    @Test
    fun `populationDelta is positive after a growth tick`() {
        // pop=50, food=300, no tiles: farmed = round(50*0.8) = 40; newFood = 300+40-50 = 290 > 0 → grows
        val state = worldWith(emptyList(), mapOf("alpha" to tribe("alpha", pop = 50, food = 300)))

        val result = tick(state)

        assertTrue(
            "populationDelta should be positive after a growth tick",
            result.tribes["alpha"]!!.populationDelta > 0,
        )
    }

    @Test
    fun `populationDelta is negative after a starvation tick`() {
        // pop=50, food=0, no tiles: farmed=40; newFood = 0+40-50 = -10 < 0 → starvation
        val state = worldWith(emptyList(), mapOf("alpha" to tribe("alpha", pop = 50, food = 0)))

        val result = tick(state)

        assertTrue(
            "populationDelta should be negative after a starvation tick",
            result.tribes["alpha"]!!.populationDelta < 0,
        )
    }

    @Test
    fun `populationDelta is zero when population is stable`() {
        // pop=5, food=1, no tiles: farmed = round(5*0.8) = 4; newFood = 1+4-5 = 0 → else branch, pop unchanged
        val state = worldWith(emptyList(), mapOf("alpha" to tribe("alpha", pop = 5, food = 1)))

        val result = tick(state)

        assertEquals(
            "populationDelta should be zero when food production exactly offsets consumption",
            0,
            result.tribes["alpha"]!!.populationDelta,
        )
    }

    @Test
    fun `territoryDelta is positive when tribe expands onto new tiles`() {
        // Tile 0 is owned; tile 2 (adjacent cell) is empty — large pop drives expansion
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(2, col = 1, row = 0),
        )
        val state = worldWith(
            tiles  = tiles,
            tribes = mapOf("alpha" to tribe("alpha", pop = 500, food = 5000)),
        )

        val result = tick(state)

        assertTrue(
            "territoryDelta should be positive when tribe expands onto empty tiles",
            result.tribes["alpha"]!!.territoryDelta > 0,
        )
    }

    // ── Inter-tribe hostility ────────────────────────────────────────────────

    @Test
    fun `hostility increments for both aggressor and defender on successful raid`() {
        // alpha (tiles 0,1) adjacent to beta (tiles 2,3); aggression=1.0, caution=0.0 → always raids
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 0, row = 0, owner = "alpha"),
            tile(2, col = 1, row = 0, owner = "beta"),
            tile(3, col = 1, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", aggression = 1.0f, caution = 0.5f, devotion = 0),
            "beta"  to tribe("beta",  aggression = 0.0f, caution = 0.0f),
        ))

        val result = conflictStep(state, Random(seed = 0L))

        val alphaTowardBeta = result.tribes["alpha"]!!.hostility["beta"] ?: 0f
        val betaTowardAlpha = result.tribes["beta"]!!.hostility["alpha"] ?: 0f
        assertTrue("aggressor should gain hostility toward defender after raid", alphaTowardBeta > 0f)
        assertTrue("defender should gain hostility toward aggressor after raid", betaTowardAlpha > 0f)
        assertTrue(
            "defender remembers harder (0.15) than aggressor gains (0.10)",
            betaTowardAlpha > alphaTowardBeta,
        )
    }

    @Test
    fun `hostility decays after a tick with no raids`() {
        // Seed alpha with hostility; no tiles so no raids can occur
        val state = worldWith(
            tiles  = emptyList(),
            tribes = mapOf(
                "alpha" to tribe("alpha", pop = 50, food = 300, hostility = mapOf("beta" to 0.5f)),
                "beta"  to tribe("beta",  pop = 50, food = 300),
            ),
        )

        val result = tick(state)

        val hostilityAfter = result.tribes["alpha"]!!.hostility["beta"] ?: 0f
        assertTrue(
            "hostility should decay each tick (0.5 * 0.98 ≈ 0.49 < 0.5)",
            hostilityAfter < 0.5f,
        )
    }

    @Test
    fun `hostility decays below threshold and is removed after enough ticks`() {
        // pop=5, food=1 → stable each tick; no tiles → no raids; hostility decays to zero
        var state = worldWith(
            tiles  = emptyList(),
            tribes = mapOf(
                "alpha" to tribe("alpha", pop = 5, food = 1, hostility = mapOf("beta" to 0.05f)),
                "beta"  to tribe("beta",  pop = 5, food = 1),
            ),
        )

        repeat(200) { state = tick(state) }

        val hostilityAfter = state.tribes["alpha"]?.hostility?.get("beta") ?: 0f
        assertTrue(
            "hostility should drop below 0.01 and be removed after enough ticks (was 0.05, 200 ticks at 0.98 decay)",
            hostilityAfter == 0f,
        )
    }

    @Test
    fun `extinct tribe ID is removed from all remaining hostility maps`() {
        // beta starts at pop=0 → goes extinct immediately; alpha held hostility toward it
        val state = worldWith(
            tiles  = emptyList(),
            tribes = mapOf(
                "alpha" to tribe("alpha", pop = 50, food = 300, hostility = mapOf("beta" to 0.5f)),
                "beta"  to tribe("beta",  pop = 0,  food = 0),
            ),
        )

        val result = tick(state)

        assertFalse("beta should be extinct", result.tribes.containsKey("beta"))
        assertFalse(
            "alpha's hostility map should not retain the extinct 'beta' ID",
            result.tribes["alpha"]!!.hostility.containsKey("beta"),
        )
    }

    // ── Tribe age (foundedTick) ──────────────────────────────────────────────

    private fun splitState(worldTick: Long): WorldState {
        val pop = SPLIT_MIN_POPULATION + 100  // 600 — safely above split threshold
        // 4 tiles → pop/tiles = 600/4 = 150 > SPLIT_DENSITY_THRESHOLD (8) → triggers split
        val tiles = (0 until 4).map { i ->
            MapTile(id = i * 2, col = i, row = 0, occupantTribeId = "alpha")
        }
        return WorldState(
            worldTimeTick = worldTick,
            divineFavor   = 50,
            tiles         = tiles,
            tribes        = mapOf(
                "alpha" to Tribe(
                    tribeId    = "alpha",
                    name       = "Alpha",
                    population = pop,
                    devotion   = 30,
                    foodSupply = pop * 10,
                    personality = TribePersonality(
                        archetypeId    = "test",
                        aggression     = 0f,
                        caution        = 0f,
                        skepticismRate = 1f,
                        traditionalism = 0.5f,
                        biomeAffinity  = emptyMap(),
                        sophistication = 0,
                        skepticism     = 0,
                    ),
                    foundedTick = 0L,
                )
            ),
            lastSplitTick = 0L,
        )
    }

    @Test
    fun `child tribe foundedTick equals the world tick at split time`() {
        val splitTick = SPLIT_COOLDOWN_TICKS + 1L
        val state = splitState(splitTick)

        val result = splitStep(state, Random(seed = 0L))

        val childTribe = result.tribes.values.firstOrNull { it.tribeId != "alpha" }
        assertFalse("A child tribe should have been created by splitStep", childTribe == null)
        assertEquals(
            "child tribe foundedTick should equal the world tick when the split fires",
            splitTick,
            childTribe!!.foundedTick,
        )
    }

    @Test
    fun `original tribe foundedTick remains 0 after a split`() {
        val state = splitState(SPLIT_COOLDOWN_TICKS + 1L)

        val result = splitStep(state, Random(seed = 0L))

        assertEquals(
            "original tribe foundedTick should not change after a split",
            0L,
            result.tribes["alpha"]!!.foundedTick,
        )
    }
}
