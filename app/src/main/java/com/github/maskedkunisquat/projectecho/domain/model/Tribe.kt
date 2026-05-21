package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

internal const val PARCHED_MOISTURE_THRESHOLD      = 25
internal const val WATERLOGGED_MOISTURE_THRESHOLD  = 60
internal const val HUNGRY_FOOD_TICKS               = 3
internal const val UNDER_THREAT_TICKS              = 5L
internal const val MIN_VIABLE_POPULATION           = 20
internal const val SPIRITUALLY_DEPLETED_SKEPTICISM = 60
internal const val SPIRITUALLY_DEPLETED_DEVOTION   = 40
// Mirrors SPLIT_DENSITY_THRESHOLD in GameLoop.kt — keep in sync
internal const val NEEDS_DENSITY_THRESHOLD         = 8

@Serializable
data class Tribe(
    val tribeId: String,
    val name: String,
    val population: Int,
    val devotion: Int,
    val foodSupply: Int,
    val personality: TribePersonality = TribePersonality.default(),
    val generationDeaths: Int = 0,
    val prayerPressure: Float = 0f,
    val skepticismDecayBuffer: Float = 0f,
    val divineShieldTicks: Int = 0,
    val lastRaidTick: Long = -1L,
    val populationDelta: Int = 0,
    val territoryDelta: Int = 0,
    val hostility: Map<String, Float> = emptyMap(),
    val foundedTick: Long = 0L,
) {
    fun needs(ownedTiles: List<MapTile>, currentTick: Long): Set<TribeNeed> {
        val result = mutableSetOf<TribeNeed>()
        val avgMoisture = if (ownedTiles.isEmpty()) 50
                         else ownedTiles.sumOf { it.soilMoisture } / ownedTiles.size
        if (avgMoisture < PARCHED_MOISTURE_THRESHOLD) result += TribeNeed.Parched
        if (avgMoisture > WATERLOGGED_MOISTURE_THRESHOLD) result += TribeNeed.Waterlogged
        if (foodSupply < population * HUNGRY_FOOD_TICKS) result += TribeNeed.Hungry
        if (foodSupply == 0) result += TribeNeed.Starving
        if (population < MIN_VIABLE_POPULATION) result += TribeNeed.Endangered
        if (lastRaidTick >= 0L && currentTick - lastRaidTick <= UNDER_THREAT_TICKS) result += TribeNeed.UnderThreat
        if (personality.skepticism > SPIRITUALLY_DEPLETED_SKEPTICISM && devotion < SPIRITUALLY_DEPLETED_DEVOTION) {
            result += TribeNeed.SpirituallyDepleted
        }
        if (ownedTiles.isNotEmpty() && population > NEEDS_DENSITY_THRESHOLD * ownedTiles.size) {
            result += TribeNeed.Overcrowded
        }
        if (result.isEmpty()) result += TribeNeed.Thriving
        return result
    }

    fun toFloatArray(
        ownedTiles: List<MapTile>,
        neighbors: List<Tribe>,
        allTiles: List<MapTile>,
        currentTick: Long,
    ): FloatArray {
        val needs = needs(ownedTiles, currentTick)
        val sortedNeighbors = neighbors.sortedBy { it.tribeId }.take(MAX_TRIBES)
        val neighborIds = sortedNeighbors.map { it.tribeId }

        val vec = FloatArray(STATE_VECTOR_SIZE)
        var i = 0

        vec[i++] = (population.toFloat() / MAX_POP).coerceIn(0f, 1f)
        vec[i++] = (foodSupply.toFloat() / MAX_FOOD).coerceIn(0f, 1f)
        vec[i++] = (devotion.toFloat() / 100f).coerceIn(0f, 1f)
        vec[i++] = (personality.skepticism.toFloat() / 100f).coerceIn(0f, 1f)
        vec[i++] = (ownedTiles.size.toFloat() / GRID_SIZE).coerceIn(0f, 1f)
        vec[i++] = personality.aggression.coerceIn(0f, 1f)
        vec[i++] = personality.caution.coerceIn(0f, 1f)
        vec[i++] = (personality.skepticismRate / SKEPTICISM_RATE_MAX_NORM).coerceIn(0f, 1f)
        vec[i++] = (personality.sophistication.toFloat() / MAX_SOPHISTICATION_NORM).coerceIn(0f, 1f)
        // Deltas shifted from [-MAX_DELTA, MAX_DELTA] to [0, 1]
        vec[i++] = ((populationDelta.toFloat() / MAX_DELTA + 1f) / 2f).coerceIn(0f, 1f)
        vec[i++] = ((territoryDelta.toFloat() / MAX_DELTA + 1f) / 2f).coerceIn(0f, 1f)

        for (j in 0 until MAX_TRIBES) {
            vec[i++] = if (j < neighborIds.size) (hostility[neighborIds[j]] ?: 0f).coerceIn(0f, 1f) else 0f
        }

        vec[i++] = ((currentTick - foundedTick).toFloat() / MAX_AGE).coerceIn(0f, 1f)

        vec[i++] = if (TribeNeed.Parched in needs) 1f else 0f
        vec[i++] = if (TribeNeed.Hungry in needs) 1f else 0f
        vec[i++] = if (TribeNeed.Starving in needs) 1f else 0f
        vec[i++] = if (TribeNeed.Endangered in needs) 1f else 0f
        vec[i++] = if (TribeNeed.UnderThreat in needs) 1f else 0f
        vec[i++] = if (TribeNeed.SpirituallyDepleted in needs) 1f else 0f
        vec[i++] = if (TribeNeed.Overcrowded in needs) 1f else 0f

        for (j in 0 until MAX_TRIBES) {
            if (j < sortedNeighbors.size) {
                val nbr = sortedNeighbors[j]
                val nbrTiles = allTiles.count { it.occupantTribeId == nbr.tribeId }
                vec[i++] = (nbr.population.toFloat() / MAX_POP).coerceIn(0f, 1f)
                vec[i++] = (nbrTiles.toFloat() / GRID_SIZE).coerceIn(0f, 1f)
                vec[i++] = (nbr.hostility[tribeId] ?: 0f).coerceIn(0f, 1f)
            } else {
                vec[i++] = 0f; vec[i++] = 0f; vec[i++] = 0f
            }
        }

        return vec
    }

    companion object {
        const val MAX_POP = 2000f
        const val MAX_FOOD = 5000f
        const val MAX_DELTA = 200
        const val MAX_AGE = 10000L
        const val MAX_TRIBES = 8
        private const val SKEPTICISM_RATE_MAX_NORM = 2f
        private const val MAX_SOPHISTICATION_NORM = 10f

        val STATE_VECTOR_SIZE: Int = 19 + 4 * MAX_TRIBES  // 51 when MAX_TRIBES=8

        val STATE_VECTOR_LABELS: List<String> = buildList {
            add("pop"); add("food"); add("devotion"); add("skepticism"); add("territory")
            add("aggression"); add("caution"); add("skepticismRate"); add("sophistication")
            add("popDelta"); add("terrDelta")
            repeat(MAX_TRIBES) { j -> add("hostility_$j") }
            add("age")
            add("need_Parched"); add("need_Hungry"); add("need_Starving")
            add("need_Endangered"); add("need_UnderThreat")
            add("need_SpirituallyDepleted"); add("need_Overcrowded")
            repeat(MAX_TRIBES) { j ->
                add("nbr_${j}_pop"); add("nbr_${j}_territory"); add("nbr_${j}_hostility")
            }
        }
    }
}
