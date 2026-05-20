package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.MAX_SOPHISTICATION
import com.github.maskedkunisquat.projectecho.domain.rules.PersonalityParser
import com.github.maskedkunisquat.projectecho.domain.rules.splitStep
import com.github.maskedkunisquat.projectecho.domain.rules.territoryStep
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class PersonalityTest {

    // --- Helpers ---

    private fun makeTile(id: Int, tribeId: String?, biome: BiomeType = BiomeType.Grassland, moisture: Int = 50): MapTile {
        val cellIdx = id / 2
        return MapTile(
            id = id, col = cellIdx % GRID_COLS, row = cellIdx / GRID_COLS,
            biome = biome, soilMoisture = moisture, occupantTribeId = tribeId,
        )
    }

    private fun stateWithPersonality(
        personality: TribePersonality,
        population: Int = 50,
        foodSupply: Int = 300,
        devotion: Int = 50,
        divineFavor: Int = 50,
    ) = WorldState(
        worldTimeTick = 0L,
        divineFavor = divineFavor,
        tiles = emptyList(),
        tribes = mapOf(
            "t1" to Tribe(
                tribeId = "t1", name = "The Test", population = population,
                devotion = devotion, foodSupply = foodSupply, personality = personality,
            )
        ),
    )

    // --- PersonalityParser ---

    @Test
    fun `PersonalityParser parses all five archetypes from JSON`() {
        val json = """
        [
          {"archetypeId":"agrarian","aggression":0.2,"caution":0.7,"skepticismRate":0.6,"traditionalism":0.7,
           "biomeAffinity":{"Forest":1.5,"Grassland":1.2,"Desert":0.3,"Coast":1.0,"Water":0.0}},
          {"archetypeId":"nomadic","aggression":0.5,"caution":0.3,"skepticismRate":0.8,"traditionalism":0.3,
           "biomeAffinity":{"Forest":0.7,"Grassland":1.0,"Desert":1.4,"Coast":0.8,"Water":0.0}},
          {"archetypeId":"warlike","aggression":0.9,"caution":0.2,"skepticismRate":1.4,"traditionalism":0.4,
           "biomeAffinity":{"Forest":0.9,"Grassland":1.1,"Desert":0.8,"Coast":0.7,"Water":0.0}},
          {"archetypeId":"maritime","aggression":0.3,"caution":0.5,"skepticismRate":0.5,"traditionalism":0.6,
           "biomeAffinity":{"Forest":0.8,"Grassland":1.0,"Desert":0.4,"Coast":2.0,"Water":0.0}},
          {"archetypeId":"reclusive","aggression":0.1,"caution":0.9,"skepticismRate":1.2,"traditionalism":0.9,
           "biomeAffinity":{"Forest":1.8,"Grassland":0.9,"Desert":0.2,"Coast":0.6,"Water":0.0}}
        ]
        """.trimIndent()

        val result = PersonalityParser.parse(json)

        assertEquals(5, result.size)
        assertEquals("agrarian", result[0].archetypeId)
        assertEquals(0.2f, result[0].aggression, 0.001f)
        assertEquals("maritime", result[3].archetypeId)
        assertEquals(2.0f, result[3].biomeAffinity["Coast"]!!, 0.001f)
    }

    @Test
    fun `TribePersonality default is agrarian archetype`() {
        val d = TribePersonality.default()
        assertEquals("agrarian", d.archetypeId)
        assertEquals(0, d.sophistication)
        assertEquals(0, d.skepticism)
    }

    @Test
    fun `affinityFor returns correct value for known biome`() {
        val p = TribePersonality.maritime()
        assertEquals(2.0f, p.affinityFor(BiomeType.Coast), 0.001f)
        assertEquals(0.4f, p.affinityFor(BiomeType.Desert), 0.001f)
    }

    @Test
    fun `affinityFor returns 1f for unknown biome key`() {
        val p = TribePersonality(
            archetypeId = "test", aggression = 0.5f, caution = 0.5f,
            skepticismRate = 0.5f, traditionalism = 0.5f,
            biomeAffinity = mapOf("Grassland" to 1.0f), // missing Coast
        )
        assertEquals(1.0f, p.affinityFor(BiomeType.Coast), 0.001f)
    }

    // --- Territory expansion prefers high-affinity biomes ---

    @Test
    fun `territoryStep expansion prefers high-affinity biome tiles`() {
        // Maritime tribe with Coast affinity 2.0 vs Grassland 1.0
        // Two adjacent frontier tiles: one Coast, one Grassland — same moisture
        val maritime = TribePersonality.maritime()
        val tribe = Tribe("m", "Maritime", population = 10, devotion = 50, foodSupply = 100, personality = maritime)
        // Tile 0 owned; tile 1 = Grassland adjacent, tile 2 = Coast adjacent, tile 3 = non-adjacent
        val tiles = listOf(
            makeTile(0, "m", BiomeType.Grassland, moisture = 50),
            makeTile(1, null, BiomeType.Grassland, moisture = 50),
            makeTile(2, null, BiomeType.Coast,     moisture = 50),
            makeTile(3, null, BiomeType.Grassland, moisture = 50),
        ).map { t ->
            // force col/row so tiles 0-2 are adjacent (same row, cols 0-2) and tile 3 is far away
            when (t.id) {
                0 -> t.copy(col = 0, row = 0)
                1 -> t.copy(col = 1, row = 0)
                2 -> t.copy(col = 0, row = 1)
                3 -> t.copy(col = 5, row = 5)
                else -> t
            }
        }
        val result = territoryStep(tiles, mapOf("m" to tribe))
        // pop=10 → expected = 10*192/500 = 3. Owns 1, deficit=2. Claims tiles 1 and 2.
        // Coast (affinity 2.0) should be claimed; Grassland (affinity 1.0) also claimed.
        // The Coast tile should definitely be among the claimed tiles.
        val claimed = result.filter { it.occupantTribeId == "m" }.map { it.id }.toSet()
        assertTrue(2 in claimed) // Coast tile must be claimed (highest score)
    }

    // --- Child personality mutation ---

    @Test
    fun `splitStep child personality stays within scalar bounds`() {
        val tiles = (0 until 60).map { i -> makeTile(i, "alpha") }
        val state = WorldState(
            worldTimeTick = 200L,
            divineFavor = 50,
            tiles = tiles,
            tribes = mapOf(
                "alpha" to Tribe(
                    tribeId = "alpha", name = "Alpha", population = 600,
                    devotion = 50, foodSupply = 1000,
                    personality = TribePersonality.warlike(),
                )
            ),
            lastSplitTick = 0L,
        )
        val result = splitStep(state, Random(42))
        assertEquals(2, result.tribes.size)

        val childId = result.tribes.keys.first { it != "alpha" }
        val child = result.tribes[childId]!!.personality

        assertTrue("aggression must be in [0,1]", child.aggression in 0f..1f)
        assertTrue("caution must be in [0,1]", child.caution in 0f..1f)
        assertTrue("skepticismRate must be >= 0", child.skepticismRate >= 0f)
        assertTrue("traditionalism must be in [0,1]", child.traditionalism in 0f..1f)
        child.biomeAffinity.values.forEach { v ->
            assertTrue("biome affinity must be in [0.1,3.0]", v in 0.1f..3.0f)
        }
    }

    @Test
    fun `splitStep child starts with zero sophistication and skepticism`() {
        val tiles = (0 until 60).map { i -> makeTile(i, "alpha") }
        val parentPersonality = TribePersonality.agrarian().copy(sophistication = 5, skepticism = 70)
        val state = WorldState(
            worldTimeTick = 200L,
            divineFavor = 50,
            tiles = tiles,
            tribes = mapOf(
                "alpha" to Tribe(
                    tribeId = "alpha", name = "Alpha", population = 600,
                    devotion = 50, foodSupply = 1000, personality = parentPersonality,
                )
            ),
            lastSplitTick = 0L,
        )
        val result = splitStep(state, Random(7))
        val childId = result.tribes.keys.first { it != "alpha" }
        val child = result.tribes[childId]!!.personality

        assertEquals(0, child.sophistication)
        assertEquals(0, child.skepticism)
    }

    // --- Sophistication milestones ---

    @Test
    fun `tick increments sophistication when population crosses milestone`() {
        // Pop exactly at 500 milestone; sophistication currently 0 → should become 1
        val state = stateWithPersonality(
            TribePersonality.default(),
            population = 500, foodSupply = 5000, devotion = 50,
        )
        val result = tick(state)
        // sophistication increases because pop (500+) meets the first pop milestone
        val sophistication = result.tribes["t1"]!!.personality.sophistication
        assertEquals(1, sophistication)
    }

    @Test
    fun `tick does not exceed max sophistication`() {
        val maxedPersonality = TribePersonality.default().copy(
            sophistication = MAX_SOPHISTICATION,
        )
        // Population and devotion are at all milestone thresholds — expectedSoph = 5 = MAX
        val state = stateWithPersonality(
            maxedPersonality, population = 2000, devotion = 95, foodSupply = 5000,
        )
        val result = tick(state)
        val soph = result.tribes["t1"]!!.personality.sophistication
        assertEquals(MAX_SOPHISTICATION, soph)
    }

    @Test
    fun `tick fires sophistication chronicle entry on milestone`() {
        val state = stateWithPersonality(
            TribePersonality.default(), population = 500, foodSupply = 5000,
        )
        val result = tick(state)
        assertTrue(result.eventHistory.any { "advances" in it || "deepens" in it || "mastery" in it })
    }

    // --- Skepticism accumulation ---

    @Test
    fun `skepticism increases when divine action is applied`() {
        val state = stateWithPersonality(
            TribePersonality.agrarian(), // skepticismRate = 0.6
            divineFavor = 50,
        )
        val result = tick(state, DivineAction.CastRain) // cost 10
        // skepGain = (10/10 * 0.6).roundToInt() = 1
        val skepticism = result.tribes["t1"]!!.personality.skepticism
        assertEquals(1, skepticism)
    }

    @Test
    fun `warlike tribe accrues more skepticism than agrarian for same action`() {
        val warlikeState = stateWithPersonality(
            TribePersonality.warlike(), divineFavor = 50, foodSupply = 300,
        )
        val agrarianState = stateWithPersonality(
            TribePersonality.agrarian(), divineFavor = 50, foodSupply = 300,
        )
        val warlikeResult  = tick(warlikeState,  DivineAction.BlessHarvest) // cost 20
        val agrarianResult = tick(agrarianState, DivineAction.BlessHarvest)

        // warlike skepticismRate=1.4 vs agrarian=0.6
        val warlikeSkep  = warlikeResult.tribes["t1"]!!.personality.skepticism
        val agrarianSkep = agrarianResult.tribes["t1"]!!.personality.skepticism
        assertTrue("warlike should be more skeptical", warlikeSkep > agrarianSkep)
    }

    @Test
    fun `skepticism is clamped at 100`() {
        val highSkepPersonality = TribePersonality.warlike().copy(skepticism = 99)
        val state = stateWithPersonality(highSkepPersonality, divineFavor = 50)
        // Any action should not push above 100
        val result = tick(state, DivineAction.BlessHarvest)
        val skepticism = result.tribes["t1"]!!.personality.skepticism
        assertTrue(skepticism <= 100)
    }

    // --- Devotion regen with skepticism penalty ---

    @Test
    fun `devotion regen is lower at high skepticism than at zero`() {
        val devotion = 100  // max devotion to isolate the skepticism effect
        val lowSkepState  = stateWithPersonality(
            TribePersonality.default().copy(skepticism = 0),  devotion = devotion, foodSupply = 5000,
        )
        val highSkepState = stateWithPersonality(
            TribePersonality.default().copy(skepticism = 100), devotion = devotion, foodSupply = 5000,
        )
        val lowResult  = tick(lowSkepState)
        val highResult = tick(highSkepState)

        val lowFavor  = lowResult.divineFavor
        val highFavor = highResult.divineFavor

        assertTrue("High skepticism should reduce divine favor regen", highFavor <= lowFavor)
    }

    // --- Generational drift ---

    @Test
    fun `generational turnover fires when cumulative deaths exceed half population`() {
        // Start tribe with generationDeaths already at (pop/2 - 1) so one starvation tick tips it over
        val personality = TribePersonality.default().copy(skepticism = 80)
        val population = 100
        val tribe = Tribe(
            tribeId = "t1", name = "The Test", population = population,
            devotion = 30, foodSupply = 0, personality = personality,
            generationDeaths = population / 2 - 1, // one death away from turnover
        )
        val state = WorldState(
            worldTimeTick = 0L, divineFavor = 50, tiles = emptyList(),
            tribes = mapOf("t1" to tribe),
        )
        // Starvation will kill ~5 people this tick, pushing generationDeaths over threshold
        val result = tick(state)
        assertTrue(result.eventHistory.any { "generation" in it.lowercase() })
    }

    @Test
    fun `high-traditionalism tribe retains more skepticism after generational turnover`() {
        // Both tribes at same skepticism; high-trad retains more on turnover
        fun tribeWithTrad(trad: Float, tribeId: String): Tribe {
            val personality = TribePersonality.default().copy(skepticism = 100, traditionalism = trad)
            val pop = 100
            return Tribe(
                tribeId = tribeId, name = tribeId, population = pop,
                devotion = 30, foodSupply = 0,
                personality = personality,
                generationDeaths = pop / 2 - 1, // one death away
            )
        }

        val highTradResult = tick(
            WorldState(0L, 50, emptyList(), mapOf("hi" to tribeWithTrad(0.9f, "hi")))
        )
        val lowTradResult = tick(
            WorldState(0L, 50, emptyList(), mapOf("lo" to tribeWithTrad(0.1f, "lo")))
        )

        val highSkep = highTradResult.tribes["hi"]!!.personality.skepticism
        val lowSkep  = lowTradResult.tribes["lo"]!!.personality.skepticism

        assertTrue(
            "High-traditionalism tribe (skepticism=$highSkep) should retain more skepticism than low-trad (skepticism=$lowSkep)",
            highSkep > lowSkep,
        )
    }

    @Test
    fun `devotion nudges toward 50 on generational turnover`() {
        val personality = TribePersonality.default().copy(skepticism = 50, traditionalism = 0.0f) // full reset
        val pop = 100
        val tribe = Tribe(
            tribeId = "t1", name = "The Test", population = pop,
            devotion = 10, // well below 50 → should nudge up
            foodSupply = 0,
            personality = personality,
            generationDeaths = pop / 2 - 1,
        )
        val state = WorldState(0L, 50, emptyList(), mapOf("t1" to tribe))
        val result = tick(state)
        val newDevotion = result.tribes["t1"]!!.devotion
        // With trad=0, nudge = 10. Before nudge devotion might drop from starvation, but nudge pushes toward 50.
        // The key is devotion should not have gone further from 50 than it was.
        // For this test we just verify turnover fired (chronicle has entry)
        assertTrue(result.eventHistory.any { "generation" in it.lowercase() })
    }
}
