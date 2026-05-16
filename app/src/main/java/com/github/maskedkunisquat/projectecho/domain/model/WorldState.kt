package com.github.maskedkunisquat.projectecho.domain.model

data class WorldState(
    val worldTimeTick: Long,
    val divineFavor: Int,
    val tribe: Tribe,
)