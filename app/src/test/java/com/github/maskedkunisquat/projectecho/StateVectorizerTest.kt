package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.model.reward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateVectorizerTest {

    private fun simpleTribe(
        id: String = "alpha",
        population: Int = 100,
        food: Int = 300,
        devotion: Int = 50,
    ) = Tribe(
        tribeId = id,
        name = id,
        population = population,
        devotion = devotion,
        foodSupply = food,
        personality = TribePersonality.default(),
    )

    private fun grassTile(id: Int, owner: String? = null) =
        MapTile(id = id, col = id % 16, row = id / 16, biome = BiomeType.Grassland, soilMoisture = 50,
                occupantTribeId = owner)

    // --- State vectorizer ---

    @Test
    fun `toFloatArray length matches STATE_VECTOR_LABELS`() {
        val tribe = simpleTribe()
        val owned = (0 until 5).map { grassTile(it, "alpha") }
        val vec = tribe.toFloatArray(owned, emptyList(), owned, currentTick = 10L)
        assertEquals(Tribe.STATE_VECTOR_LABELS.size, vec.size)
        assertEquals(Tribe.STATE_VECTOR_SIZE, vec.size)
    }

    @Test
    fun `toFloatArray all values normalized to 0 to 1 range`() {
        val tribe = simpleTribe(population = 500, food = 2000, devotion = 80)
            .copy(populationDelta = 50, territoryDelta = -5, foundedTick = 0L)
        val owned = (0 until 10).map { grassTile(it, "alpha") }
        val neighbors = listOf(simpleTribe("beta", 200, 400, 60))
        val allTiles = owned + (10 until 15).map { grassTile(it, "beta") }
        val vec = tribe.toFloatArray(owned, neighbors, allTiles, currentTick = 100L)

        vec.forEachIndexed { i, v ->
            assertTrue(
                "Index $i ('${Tribe.STATE_VECTOR_LABELS[i]}') = $v is out of [0,1]",
                v in 0f..1f,
            )
        }
    }

    @Test
    fun `toFloatArray devotion index is correct`() {
        val tribe = simpleTribe(devotion = 75)
        val owned = listOf(grassTile(0, "alpha"))
        val vec = tribe.toFloatArray(owned, emptyList(), owned, currentTick = 0L)
        assertEquals(0.75f, vec[2], 0.001f)  // index 2 = devotion/100
    }

    @Test
    fun `toFloatArray zero population produces zero pop feature`() {
        val tribe = simpleTribe(population = 0)
        val vec = tribe.toFloatArray(emptyList(), emptyList(), emptyList(), currentTick = 0L)
        assertEquals(0f, vec[0], 0.001f)
    }

    @Test
    fun `toFloatArray delta is 0_5 when delta is zero`() {
        val tribe = simpleTribe().copy(populationDelta = 0, territoryDelta = 0)
        val vec = tribe.toFloatArray(emptyList(), emptyList(), emptyList(), currentTick = 0L)
        assertEquals(0.5f, vec[9], 0.001f)   // popDelta index
        assertEquals(0.5f, vec[10], 0.001f)  // terrDelta index
    }

    // --- Reward calculator ---

    @Test
    fun `reward is positive after population growth`() {
        val tribeId = "alpha"
        val tile = grassTile(0, tribeId)
        val prev = WorldState(0L, 50, listOf(tile),
            mapOf(tribeId to simpleTribe(tribeId, population = 100)))
        val next = WorldState(1L, 50, listOf(tile),
            mapOf(tribeId to simpleTribe(tribeId, population = 110)))
        val r = next.reward(prev, tribeId)
        assertTrue("reward should be positive after growth, got $r", r > 0f)
    }

    @Test
    fun `reward is negative after territory loss`() {
        val tribeId = "alpha"
        val tile = grassTile(0, tribeId)
        val prev = WorldState(0L, 50, listOf(tile, grassTile(1, tribeId)),
            mapOf(tribeId to simpleTribe(tribeId, population = 100)))
        val next = WorldState(1L, 50, listOf(tile, grassTile(1, null)),
            mapOf(tribeId to simpleTribe(tribeId, population = 100)))
        val r = next.reward(prev, tribeId)
        assertTrue("reward should be negative after territory loss, got $r", r < 0f)
    }

    @Test
    fun `reward returns extinction penalty when tribe absent from next state`() {
        val tribeId = "alpha"
        val prev = WorldState(0L, 50, listOf(grassTile(0, tribeId)),
            mapOf(tribeId to simpleTribe(tribeId)))
        val next = WorldState(1L, 50, listOf(grassTile(0, null)), emptyMap())
        assertEquals(-10f, next.reward(prev, tribeId), 0.001f)
    }

    @Test
    fun `reward is zero when nothing changes`() {
        val tribeId = "alpha"
        val tile = grassTile(0, tribeId)
        val state = WorldState(0L, 50, listOf(tile),
            mapOf(tribeId to simpleTribe(tribeId, population = 100)))
        assertEquals(0f, state.reward(state, tribeId), 0.001f)
    }

    // --- initialForTraining ---

    @Test
    fun `initialForTraining creates correct number of tribes`() {
        val state = WorldState.initialForTraining(numTribes = 4, seed = 42L)
        assertEquals(4, state.tribes.size)
    }

    @Test
    fun `initialForTraining tribes do not share tiles`() {
        val state = WorldState.initialForTraining(numTribes = 4, seed = 42L)
        state.tiles.filter { it.occupantTribeId != null }.forEach { tile ->
            val count = state.tiles.count { it.id == tile.id && it.occupantTribeId != null }
            assertEquals("tile ${tile.id} should have exactly one owner", 1, count)
        }
    }

    @Test
    fun `initialForTraining different seeds produce different states`() {
        val stateA = WorldState.initialForTraining(numTribes = 4, seed = 1L)
        val stateB = WorldState.initialForTraining(numTribes = 4, seed = 2L)
        assertNotEquals(stateA.tiles, stateB.tiles)
    }

    @Test
    fun `initialForTraining same seed is deterministic`() {
        val stateA = WorldState.initialForTraining(numTribes = 4, seed = 42L)
        val stateB = WorldState.initialForTraining(numTribes = 4, seed = 42L)
        assertEquals(stateA, stateB)
    }
}
