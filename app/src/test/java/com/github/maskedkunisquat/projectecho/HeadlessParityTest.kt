package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.headless.runHeadless
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parity tests for the headless simulation runner.
 *
 * SNAPSHOT VALUES — after first run, copy the printed values into the constants below
 * and commit. Future runs will catch any unintended simulation drift.
 *
 * To recapture: set both constants to -1, run the test, read the console output.
 */
class HeadlessParityTest {

    // Known-good snapshot at seed=42, ticks=100.
    // Set to -1 to skip assertion and print capture values instead.
    private val snapshotTotalPop = 713
    private val snapshotOccupiedTiles = 78

    @Test
    fun `worldTimeTick equals ticks run`() {
        val state = runHeadless(ticks = 100, seed = 42L)
        assertEquals(100L, state.worldTimeTick)
    }

    @Test
    fun `deterministic - identical seeds produce identical results`() {
        val run1 = runHeadless(ticks = 100, seed = 42L)
        val run2 = runHeadless(ticks = 100, seed = 42L)

        val pop1 = run1.tribes.values.sumOf { it.population }
        val pop2 = run2.tribes.values.sumOf { it.population }
        val tiles1 = run1.tiles.count { it.occupantTribeId != null }
        val tiles2 = run2.tiles.count { it.occupantTribeId != null }

        assertEquals("total population must be deterministic", pop1, pop2)
        assertEquals("occupied tile count must be deterministic", tiles1, tiles2)
        assertEquals("tribe count must be deterministic", run1.tribes.size, run2.tribes.size)
    }

    @Test
    fun `known-good snapshot - seed 42 tick 100`() {
        val state = runHeadless(ticks = 100, seed = 42L)
        val totalPop = state.tribes.values.sumOf { it.population }
        val occupiedTiles = state.tiles.count { it.occupantTribeId != null }

        println(">>> Parity snapshot (update constants if these look correct):")
        println(">>>   snapshotTotalPop     = $totalPop")
        println(">>>   snapshotOccupiedTiles = $occupiedTiles")

        if (snapshotTotalPop >= 0) assertEquals("totalPop drift detected", snapshotTotalPop, totalPop)
        if (snapshotOccupiedTiles >= 0) assertEquals("occupiedTiles drift detected", snapshotOccupiedTiles, occupiedTiles)
    }

    @Test
    fun `performance - 1000 ticks complete in under 10 seconds`() {
        val startMs = System.currentTimeMillis()
        runHeadless(ticks = 1000, seed = 42L)
        val elapsedMs = System.currentTimeMillis() - startMs
        println("1000-tick run completed in ${elapsedMs}ms")
        assertTrue("1000 ticks took ${elapsedMs}ms — must be < 10 000ms", elapsedMs < 10_000L)
    }
}
