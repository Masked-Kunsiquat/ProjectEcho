package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.headless.runHeadless
import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.HeuristicPolicy
import com.github.maskedkunisquat.projectecho.domain.rules.RaidCandidate
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TribePolicyTest {

    private fun tribe(
        id: String = "t",
        aggression: Float = 0.5f,
        caution: Float = 0.5f,
        devotion: Int = 0,
        sophistication: Int = 0,
    ) = Tribe(
        tribeId = id, name = id, population = 100, devotion = devotion, foodSupply = 200,
        personality = TribePersonality(
            archetypeId = "test",
            aggression = aggression, caution = caution,
            skepticismRate = 1f, traditionalism = 0.5f,
            biomeAffinity = mapOf(
                "Grassland" to 1.0f, "Forest" to 2.0f,
                "Desert" to 0.5f, "Coast" to 0.8f, "Water" to 0.0f,
            ),
            sophistication = sophistication,
        ),
    )

    // --- chooseExpansion ---

    @Test
    fun `chooseExpansion picks highest affinity-moisture tile`() {
        val policy = HeuristicPolicy()
        val aggressor = tribe()
        // Forest/moisture=80 > Grassland/moisture=90 because affinity matters:
        // forest: 2.0 * 0.80 = 1.60; grassland: 1.0 * 0.90 = 0.90
        val candidates = listOf(
            MapTile(id = 1, col = 1, row = 0, biome = BiomeType.Grassland, soilMoisture = 90),
            MapTile(id = 2, col = 2, row = 0, biome = BiomeType.Forest, soilMoisture = 80),
            MapTile(id = 3, col = 3, row = 0, biome = BiomeType.Desert, soilMoisture = 100),
        )
        val chosen = policy.chooseExpansion(aggressor, candidates)
        assertEquals(2, chosen?.id)
    }

    @Test
    fun `chooseExpansion returns null for empty candidates`() {
        val policy = HeuristicPolicy()
        assertNull(policy.chooseExpansion(tribe(), emptyList()))
    }

    // --- chooseRaid ---

    @Test
    fun `chooseRaid returns null when aggression is zero`() {
        val policy = HeuristicPolicy(Random(42))
        val aggressor = tribe(aggression = 0.0f)
        val defender = tribe(id = "def")
        val targets = listOf(RaidCandidate(MapTile(1, 1, 0), "def", defender))
        // threshold = 0 → nextFloat >= 0 always → null
        repeat(20) { assertNull(policy.chooseRaid(aggressor, targets)) }
    }

    @Test
    fun `chooseRaid returns candidate when threshold always met`() {
        // aggression=1.0, caution=0.0, devotion=0, sophistication=0 → threshold=1.0 (clamped)
        // nextFloat() is always in [0,1) so the check nextFloat() >= 1.0 is always false → raid always fires
        val policy = HeuristicPolicy(Random(0))
        val aggressor = tribe(aggression = 1.0f, caution = 0.0f, devotion = 0)
        val defender = tribe(id = "def", caution = 0.0f)
        val target = RaidCandidate(MapTile(1, 1, 0), "def", defender)
        val result = policy.chooseRaid(aggressor, listOf(target))
        assertEquals(target, result)
    }

    @Test
    fun `chooseRaid returns null for empty targets`() {
        val policy = HeuristicPolicy()
        assertNull(policy.chooseRaid(tribe(), emptyList()))
    }

    // --- Behavioral parity ---

    @Test
    fun `HeuristicPolicy produces same output as tick default for identical seed`() {
        // Verify that passing HeuristicPolicy explicitly matches the default behavior
        val seed = 42L
        val stateA = runHeadless(ticks = 100, seed = seed)

        val rng = Random(seed)
        var stateB = WorldState.initial()
        val policy = HeuristicPolicy(rng)
        repeat(100) { stateB = tick(stateB, random = rng, policy = policy) }

        assertEquals("explicit HeuristicPolicy must match runHeadless default", stateA, stateB)
    }

    @Test
    fun `tick with explicit policy is deterministic for same seed`() {
        fun run(seed: Long): WorldState {
            val rng = Random(seed)
            var state = WorldState.initial()
            val policy = HeuristicPolicy(rng)
            repeat(200) { state = tick(state, random = rng, policy = policy) }
            return state
        }
        assertEquals(run(99L), run(99L))
    }
}
