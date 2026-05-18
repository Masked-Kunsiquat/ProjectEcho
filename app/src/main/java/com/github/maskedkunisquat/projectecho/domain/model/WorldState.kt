package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

/**
 * Immutable snapshot of the entire simulation at a single point in time.
 *
 * Every tick produces a new [WorldState] rather than mutating the existing one,
 * keeping the game loop a pure function.
 *
 * @property worldTimeTick Number of ticks elapsed since the game started.
 * @property divineFavor Player's current favor currency (0–100), spent on [DivineAction]s.
 * @property tribe The tribe's current stats.
 * @property eventHistory Narrative messages shown in the Chronicle UI, ordered oldest-first.
 * @property firedEventIds IDs of [SimEvent]s that have already triggered; prevents any event from firing more than once.
 */
@Serializable
data class WorldState(
    val worldTimeTick: Long,
    val divineFavor: Int,
    val tribe: Tribe,
    val eventHistory: List<String> = emptyList(),
    val firedEventIds: Set<String> = emptySet(),
)