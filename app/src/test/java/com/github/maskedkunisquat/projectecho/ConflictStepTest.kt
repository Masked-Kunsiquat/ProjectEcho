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

    // Hex tile layout: id = row * 16 + col.
    // tile(0, col=0, row=0) and tile(1, col=1, row=0) are adjacent hex neighbors.
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
        val tiles = listOf(tile(0, 0, 0, "alpha"))
        val state = worldWith(tiles, mapOf("alpha" to tribe("alpha", "Alpha")))

        val result = conflictStep(state)

        assertEquals(state, result)
    }

    @Test
    fun `conflictStep returns unchanged state when tribes are not adjacent`() {
        // alpha owns col=0 (id=0); beta owns col=15 (id=15) — 15 columns apart, never adjacent
        val tiles = listOf(
            tile(0,  col = 0,  row = 0, owner = "alpha"),
            tile(15, col = 15, row = 0, owner = "beta"),
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
        // alpha (col=0) adjacent to beta (col=1); aggression=1.0, caution=0.0 → always raids
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", "Alpha", aggression = 1.0f, caution = 0.5f),
            "beta"  to tribe("beta",  "Beta",  aggression = 0.0f, caution = 0.0f),
        ))

        val result = conflictStep(state, Random(seed = 0L))

        val alphaTiles = result.tiles.count { it.occupantTribeId == "alpha" }
        val betaTiles  = result.tiles.count { it.occupantTribeId == "beta" }
        assertTrue("alpha should have gained a tile", alphaTiles > 1)
        assertTrue("beta should have lost a tile",   betaTiles < 1)
    }

    @Test
    fun `conflictStep appends Chronicle entry on successful raid`() {
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
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
            tile(1, col = 1, row = 0, owner = "beta"),
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
    fun `high-devotion aggressor raids less frequently than zero-devotion aggressor`() {
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
        )
        fun raidCount(devotion: Int, trials: Int): Int {
            val state = worldWith(tiles, mapOf(
                "alpha" to tribe("alpha", "Alpha", aggression = 1.0f, caution = 0.5f).copy(devotion = devotion),
                "beta"  to tribe("beta",  "Beta",  aggression = 0.0f, caution = 0.0f),
            ))
            val rng = Random(seed = 42L)
            var count = 0
            repeat(trials) {
                if (conflictStep(state, rng).tiles.count { it.occupantTribeId == "alpha" } > 1) count++
            }
            return count
        }
        val devout  = raidCount(devotion = 100, trials = 20)
        val warlike = raidCount(devotion = 0,   trials = 20)
        assertTrue("devout aggressor should raid less often than zero-devotion aggressor", devout < warlike)
    }

    @Test
    fun `conflictStep updated tile has correct new owner`() {
        val tiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
        )
        val state = worldWith(tiles, mapOf(
            "alpha" to tribe("alpha", "Alpha", aggression = 1.0f, caution = 0.5f),
            "beta"  to tribe("beta",  "Beta",  aggression = 0.0f, caution = 0.0f),
        ))

        val result = conflictStep(state, Random(seed = 0L))

        val capturedTiles = result.tiles.filter { it.id == 1 && it.occupantTribeId == "alpha" }
        assertTrue("beta's tile should be owned by alpha after raid", capturedTiles.isNotEmpty())
    }

    @Test
    fun `high-sophistication aggressor raids more often than low-sophistication aggressor`() {
        val baseTiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
        )
        fun raidCount(aggressorSoph: Int): Int {
            val aggressor = tribe("alpha", "Alpha", aggression = 0.5f, caution = 0.0f)
                .let { t -> t.copy(devotion = 0, personality = t.personality.copy(sophistication = aggressorSoph)) }
            val defender = tribe("beta", "Beta", aggression = 0.0f, caution = 0.0f).copy(devotion = 0)
            val state = worldWith(baseTiles, mapOf("alpha" to aggressor, "beta" to defender))
            return (0 until 100).count { i ->
                conflictStep(state, Random(i.toLong())).tiles.count { it.occupantTribeId == "alpha" } > 1
            }
        }
        val lowSoph  = raidCount(0)
        val highSoph = raidCount(10)
        assertTrue("soph-10 aggressor ($highSoph raids) should out-raid soph-0 ($lowSoph raids)", highSoph > lowSoph)
    }

    @Test
    fun `high-sophistication defender is raided less often than low-sophistication defender`() {
        val baseTiles = listOf(
            tile(0, col = 0, row = 0, owner = "alpha"),
            tile(1, col = 1, row = 0, owner = "beta"),
        )
        fun raidCount(defenderSoph: Int): Int {
            val aggressor = tribe("alpha", "Alpha", aggression = 0.7f, caution = 0.0f).copy(devotion = 0)
            val defender = tribe("beta", "Beta", aggression = 0.0f, caution = 0.0f)
                .let { t -> t.copy(devotion = 0, personality = t.personality.copy(sophistication = defenderSoph)) }
            val state = worldWith(baseTiles, mapOf("alpha" to aggressor, "beta" to defender))
            return (0 until 100).count { i ->
                conflictStep(state, Random(i.toLong())).tiles.count { it.occupantTribeId == "alpha" } > 1
            }
        }
        val lowDefSoph  = raidCount(0)
        val highDefSoph = raidCount(10)
        assertTrue("soph-10 defender ($highDefSoph raids taken) should be raided less than soph-0 ($lowDefSoph)", highDefSoph < lowDefSoph)
    }
}
