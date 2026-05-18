package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

/**
 * A scripted in-game event loaded from `assets/events.json`.
 *
 * Each event fires at most once: after triggering, its [id] is added to
 * [WorldState.firedEventIds] so it never fires again.
 *
 * @property id Unique string key matching the JSON definition.
 * @property trigger Condition that must hold for the event to fire.
 * @property text Narrative string appended to the Chronicle when the event fires.
 * @property effect Optional stat mutation applied when the event fires.
 */
@Serializable
data class SimEvent(
    val id: String,
    val trigger: Trigger,
    val text: String,
    val effect: Effect? = null,
) {
    /**
     * Condition that activates the parent [SimEvent].
     *
     * @property stat World stat to check: `"population"`, `"devotion"`, `"foodSupply"`, or `"divineFavor"`.
     * @property operator Comparison to apply: `"lt"`, `"gt"`, `"lte"`, `"gte"`, or `"eq"`.
     * @property threshold Integer value the stat is compared against.
     */
    @Serializable
    data class Trigger(
        val stat: String,
        val operator: String,
        val threshold: Int,
    )

    /**
     * Side-effect applied to the world when the parent event fires.
     *
     * @property stat Stat to modify: `"population"`, `"devotion"`, `"foodSupply"`, or `"divineFavor"`.
     * @property delta Amount to add (positive) or subtract (negative) from the stat.
     */
    @Serializable
    data class Effect(
        val stat: String,
        val delta: Int,
    )
}
