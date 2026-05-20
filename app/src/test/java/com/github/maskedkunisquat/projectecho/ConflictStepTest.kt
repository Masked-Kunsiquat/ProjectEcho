package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.conflictStep
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictStepTest {

    // Tile helper: col/row derived from id (same convention as GameLoopTest)
    private fun tile(id: Int, col: Int, row: Int, owner: String? = null) =
        MapTile(id = id, col = col, row = row, occupantTribeId = owner)

    private fun personality(aggression: Float = 0.5f, caution: Float = 0.5f) =
        TribePersonality(
            archetypeId    = "test",
            aggression     = aggression,
            caution        = caution,
            skepticismRate = 1.0f,
            traditionalism = 0.5f,
            biomeAffinity  = emptyMap(),
        )

    private fun tribe(id: String, name: String, aggression: Float = 0.5f, caution: Float = 0.5f) =
        Tribe(
            tribeId    = id,
            name       = name,
            population = 100,
            devotion   = 50,
            foodSupply = 200,
            personality = personality(aggression, caution),
        )

    private fun worldWith(tiles: List<MapTile>, tribes: Map<String, Tribe>) = WorldState(
        worldTimeTick = 0L,
        divineFavor   = 50,
        tiles         = tiles,
        tribes        = tribes,
    )

    @Test
    fun `conflictStep returns unchanged state with fewer than 2 tribes`() {
        val tiles = listOf(tile(0, 0, 0, "alpha"), tile(1, 0, 0, "alpha"))
        val state = worldWith(tiles, mapOf("alpha" to tribe("alpha", "Alpha")))

        val result = conflictStep(state)

        assertEquals(state, result)
    }

    @Test
    fun `conflictStep returns unchanged state when tribes are not adjacent`() {
        // alpha owns col=0; beta owns col=15 — 15 columns apart, never adjacent
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 0, row = 0, owner = "alpha"),
            tile(30, col = 15, row = 0, owner = "beta"),
            tile(31, col = 15, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", "Alpha"),
            "beta"  to tribe("beta",  "Beta"),
        ))

        val result = conflictStep(state, Random.Default)

        assertEquals(state.tiles, result.tiles)
        assertTrue(result.eventHistory.isEmpty())
    }

    @Test
    fun `conflictStep transfers border tile on successful raid`() {
        // alpha (col=0) adjacent to beta (col=1); aggression=1.0, defender caution=0.0 → always raids
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 0, row = 0, owner = "alpha"),
            tile(2, col = 1, row = 0, owner = "beta"),
            tile(3, col = 1, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", "Alpha", aggression = 1.0f, caution = 0.5f),
            "beta"  to tribe("beta",  "Beta",  aggression = 0.0f, caution = 0.0f),
        ))

        // threshold = 1.0 * (1 - 0.0) = 1.0; nextFloat always < 1.0 → raid always succeeds
        val result = conflictStep(state, Random(seed = 0L))

        val alphaTiles = result.tiles.count { it.occupantTribeId == "alpha" }
        val betaTiles  = result.tiles.count { it.occupantTribeId == "beta" }
        assertTrue("alpha should have gained a tile", alphaTiles > 2)
        assertTrue("beta should have lost a tile",   betaTiles < 2)
    }

    @Test
    fun `conflictStep appends Chronicle entry on successful raid`() {
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 0, row = 0, owner = "alpha"),
            tile(2, col = 1, row = 0, owner = "beta"),
            tile(3, col = 1, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", "The Iron", aggression = 1.0f, caution = 0.5f),
            "beta"  to tribe("beta",  "The Ash",  aggression = 0.0f, caution = 0.0f),
        ))

        val result = conflictStep(state, Random(seed = 0L))

        assertTrue(result.eventHistory.any { "Iron" in it && "Ash" in it && "raid" in it })
    }

    @Test
    fun `conflictStep produces no transfer when roll fails`() {
        // aggression=0.0 → threshold=0.0; nextFloat >= 0.0 always → never raids
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 0, row = 0, owner = "alpha"),
            tile(2, col = 1, row = 0, owner = "beta"),
            tile(3, col = 1, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", "Alpha", aggression = 0.0f, caution = 0.5f),
            "beta"  to tribe("beta",  "Beta",  aggression = 0.0f, caution = 0.5f),
        ))

        val result = conflictStep(state, Random.Default)

        assertEquals(state.tiles, result.tiles)
        assertTrue(result.eventHistory.isEmpty())
    }

    @Test
    fun `conflictStep updated tile has correct new owner`() {
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 0, row = 0, owner = "alpha"),
            tile(2, col = 1, row = 0, owner = "beta"),
            tile(3, col = 1, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", "Alpha", aggression = 1.0f, caution = 0.5f),
            "beta"  to tribe("beta",  "Beta",  aggression = 0.0f, caution = 0.0f),
        ))

        val result = conflictStep(state, Random(seed = 0L))

        val capturedTiles = result.tiles.filter { it.id in listOf(2, 3) && it.occupantTribeId == "alpha" }
        assertTrue("at least one beta tile should be owned by alpha", capturedTiles.isNotEmpty())
    }
}
