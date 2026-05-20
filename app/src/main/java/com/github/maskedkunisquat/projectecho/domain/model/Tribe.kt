package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Tribe(
    val tribeId: String,
    val name: String,
    val population: Int,
    val devotion: Int,
    val foodSupply: Int,
    val personality: TribePersonality = TribePersonality.default(),
    val generationDeaths: Int = 0,
)
