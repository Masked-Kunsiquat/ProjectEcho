package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

internal const val PARCHED_MOISTURE_THRESHOLD      = 25
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
}
