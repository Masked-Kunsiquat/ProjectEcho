package com.github.maskedkunisquat.projectecho

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_ROWS
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.rules.tick
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class DiagnosticTraceTest {

    @Test
    fun `print kotlin random raw sequence for parity implementation`() {
        // Print raw nextInt() (unbounded, 32-bit) values so Python can implement the same algorithm
        println("=== seed=42 ===")
        val rng42 = Random(42L)
        repeat(20) { println("  [${it}] nextInt=${rng42.nextInt()}  nextBits31=${rng42.nextBits(31)}") }

        println("=== seed=-1184234995 (iron-wrought) ===")
        val rngWorld = Random(-1184234995L)
        repeat(10) { println("  [${it}] nextInt=${rngWorld.nextInt()}") }

        println("=== seed=42 nextBoolean sequence ===")
        val rng42b = Random(42L)
        repeat(10) { println("  [${it}] nextBoolean=${rng42b.nextBoolean()}") }
    }

    @Test
    fun `trace world gen rng calls`() {
        val tribeId = "iron-wrought"
        val seed = tribeId.hashCode()
        println("Seed = $seed")

        val rng = Random(seed.toLong())
        val cellCount = GRID_COLS * GRID_ROWS

        val blobCount = rng.nextInt(1, 3)
        println("Blob count: $blobCount")

        val waterCells = mutableSetOf<Int>()
        repeat(blobCount) { b ->
            val centre = rng.nextInt(cellCount)
            val crow = centre / GRID_COLS; val ccol = centre % GRID_COLS
            val blobSize = rng.nextInt(8, 13)
            println("  Blob $b: centre=$centre (row=$crow,col=$ccol), size=$blobSize")
            val sorted = (0 until cellCount).map { ci ->
                val r = ci / GRID_COLS; val c = ci % GRID_COLS
                val dR = (r - crow).toFloat(); val dC = (c - ccol).toFloat()
                val score = sqrt((dR*dR + dC*dC).toDouble()).toFloat() + rng.nextFloat() * 0.8f
                ci to score
            }.sortedBy { (_, s) -> s }.take(blobSize).map { (ci, _) -> ci }
            waterCells += sorted
        }
        println("Total water cells: ${waterCells.size}")

        val coastCells = (0 until cellCount).filter { ci ->
            if (ci in waterCells) return@filter false
            val c = ci % GRID_COLS; val r = ci / GRID_COLS
            val nbrs = if (c % 2 == 0)
                listOf(c to r-1, c+1 to r-1, c+1 to r, c to r+1, c-1 to r, c-1 to r-1)
            else
                listOf(c to r-1, c+1 to r, c+1 to r+1, c to r+1, c-1 to r+1, c-1 to r)
            nbrs.any { (nc, nr) ->
                nc in 0 until GRID_COLS && nr in 0 until GRID_ROWS && (nr * GRID_COLS + nc) in waterCells
            }
        }.toSet()
        println("Coast cells: ${coastCells.size}")

        val biomeMap = (0 until cellCount).associate { ci ->
            ci to when {
                ci in waterCells -> BiomeType.Water
                ci in coastCells -> BiomeType.Coast
                else -> when (rng.nextInt(10)) {
                    in 0..4 -> BiomeType.Grassland
                    in 5..7 -> BiomeType.Forest
                    else    -> BiomeType.Desert
                }
            }
        }
        val desertCount = biomeMap.values.count { it == BiomeType.Desert }
        println("Desert tiles: $desertCount")
        println("Biome distribution: Water=${biomeMap.values.count { it == BiomeType.Water }}, " +
                "Coast=${biomeMap.values.count { it == BiomeType.Coast }}, " +
                "Grassland=${biomeMap.values.count { it == BiomeType.Grassland }}, " +
                "Forest=${biomeMap.values.count { it == BiomeType.Forest }}, " +
                "Desert=$desertCount")

        val startRow = rng.nextInt(GRID_ROWS)
        val startCol = rng.nextInt(GRID_COLS)
        println("Start position: row=$startRow, col=$startCol")
    }

    @Test
    fun `print initial territory`() {
        val state = WorldState.initial()
        val tribeId = state.tribes.keys.first()
        val owned = state.tiles.filter { it.occupantTribeId == tribeId }.sortedBy { it.id }
        println("Initial territory (Kotlin):")
        for (t in owned) {
            println("  id=${t.id.toString().padStart(3)} col=${t.col.toString().padStart(2)} row=${t.row} biome=${t.biome.name.padEnd(10)} moisture=${t.soilMoisture}")
        }
        println("Total: ${owned.size} tiles")
        val biomes = owned.groupBy { it.biome }.mapValues { it.value.size }
        println("Biome breakdown: $biomes")
    }

    @Test
    fun `print tick-by-tick trace for seed 42`() {
        val random = Random(42L)
        var state = WorldState.initial()
        for (i in 1..100) {
            state = tick(state, random = random)
            val tribe = state.tribes.values.firstOrNull()
            if (tribe == null) {
                println("T${i.toString().padStart(3)}: EXTINCT")
                break
            }
            val occ = state.tiles.count { it.occupantTribeId == tribe.tribeId }
            val avgM = if (occ == 0) 0
                       else state.tiles.filter { it.occupantTribeId == tribe.tribeId }
                                       .sumOf { it.soilMoisture } / occ
            val parched = state.tiles.count { it.occupantTribeId == tribe.tribeId && it.soilMoisture < 21 }
            val frontStr = state.activeFront?.let { " front=${it.type.name}@col${it.column}" } ?: ""
            println("T${i.toString().padStart(3)}: pop=${tribe.population.toString().padStart(4)} food=${tribe.foodSupply.toString().padStart(5)} tiles=${occ.toString().padStart(3)} avg_m=${avgM.toString().padStart(3)} parched=$parched$frontStr")
        }
    }
}
