package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

/**
 * A scripted in-game event loaded from `assets/events.json`.
 *
 * Events with no [cooldownTicks] fire exactly once. Events with a cooldown may
 * re-fire after [cooldownTicks] ticks have elapsed since the last firing; this is
 * tracked by [WorldState.eventCooldowns].
 *
 * @property id Unique string key matching the JSON definition.
 * @property trigger Condition (or compound conditions) that must hold for the event to fire.
 * @property texts One or more narrative variants; one is chosen at random when the event fires.
 * @property cooldownTicks Optional re-fire cooldown in ticks. Null means one-and-done.
 * @property effect Optional stat mutation applied when the event fires.
 */
@Serializable
data class SimEvent(
    val id: String,
    val trigger: Trigger,
    val texts: List<String> = emptyList(),
    val cooldownTicks: Int? = null,
    val effect: Effect? = null,
) {
    /**
     * A single condition or a compound logical group of conditions.
     *
     * Single-condition shape (existing JSON is unchanged):
     *   { "stat": "foodSupply", "operator": "lt", "threshold": 50 }
     *
     * Multi-condition shape:
     *   { "logic": "AND", "conditions": [ ... ] }
     *
     * A trigger is treated as multi-condition when [conditions] is non-null.
     * Single-condition triggers that omit [stat]/[operator]/[threshold] fall back to
     * empty-string defaults and will never match any stat resolver — a safe no-op.
     */
    @Serializable
    data class Trigger(
        val stat: String = "",
        val operator: String = "",
        val threshold: Int = 0,
        val logic: String? = null,
        val conditions: List<Trigger>? = null,
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
