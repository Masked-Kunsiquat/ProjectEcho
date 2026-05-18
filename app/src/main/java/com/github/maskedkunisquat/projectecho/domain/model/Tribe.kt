package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a player's civilization within the simulation.
 *
 * @property name The tribe's display name.
 * @property population Number of living tribe members. Grows ~2% per tick when fed; shrinks ~5% per tick when starving.
 * @property devotion The tribe's faith level (0–100). Drops during starvation, rises when the tribe is thriving.
 * @property foodSupply Total food units available. Each tick the tribe consumes one unit per population member.
 */
@Serializable
data class Tribe(
    val name: String,
    val population: Int,
    val devotion: Int,
    val foodSupply: Int,
)
