package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SimEvent(
    val id: String,
    val trigger: Trigger,
    val text: String,
    val effect: Effect? = null,
) {
    @Serializable
    data class Trigger(
        val stat: String,
        val operator: String,
        val threshold: Int,
    )

    @Serializable
    data class Effect(
        val stat: String,
        val delta: Int,
    )
}
