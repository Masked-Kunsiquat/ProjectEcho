package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TribePersonality(
    val archetypeId: String,
    val aggression: Float,
    val caution: Float,
    val skepticismRate: Float,
    val traditionalism: Float,
    val biomeAffinity: Map<String, Float>,
    val sophistication: Int = 0,
    val skepticism: Int = 0,
) {
    fun affinityFor(biome: BiomeType): Float = biomeAffinity[biome.name] ?: 1.0f

    companion object {
        fun default() = agrarian()

        val ALL_ARCHETYPES by lazy {
            listOf(agrarian(), nomadic(), warlike(), maritime(), reclusive())
        }

        fun agrarian() = TribePersonality(
            archetypeId = "agrarian",
            aggression = 0.2f, caution = 0.7f, skepticismRate = 0.6f, traditionalism = 0.7f,
            biomeAffinity = mapOf("Forest" to 1.5f, "Grassland" to 1.2f, "Desert" to 0.3f, "Coast" to 1.0f, "Water" to 0.0f),
        )

        fun nomadic() = TribePersonality(
            archetypeId = "nomadic",
            aggression = 0.5f, caution = 0.3f, skepticismRate = 0.8f, traditionalism = 0.3f,
            biomeAffinity = mapOf("Forest" to 0.7f, "Grassland" to 1.0f, "Desert" to 1.4f, "Coast" to 0.8f, "Water" to 0.0f),
        )

        fun warlike() = TribePersonality(
            archetypeId = "warlike",
            aggression = 0.9f, caution = 0.2f, skepticismRate = 1.4f, traditionalism = 0.4f,
            biomeAffinity = mapOf("Forest" to 0.9f, "Grassland" to 1.1f, "Desert" to 0.8f, "Coast" to 0.7f, "Water" to 0.0f),
        )

        fun maritime() = TribePersonality(
            archetypeId = "maritime",
            aggression = 0.3f, caution = 0.5f, skepticismRate = 0.5f, traditionalism = 0.6f,
            biomeAffinity = mapOf("Forest" to 0.8f, "Grassland" to 1.0f, "Desert" to 0.4f, "Coast" to 2.0f, "Water" to 0.0f),
        )

        fun reclusive() = TribePersonality(
            archetypeId = "reclusive",
            aggression = 0.1f, caution = 0.9f, skepticismRate = 1.2f, traditionalism = 0.9f,
            biomeAffinity = mapOf("Forest" to 1.8f, "Grassland" to 0.9f, "Desert" to 0.2f, "Coast" to 0.6f, "Water" to 0.0f),
        )
    }
}
