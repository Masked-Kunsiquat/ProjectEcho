package com.github.maskedkunisquat.projectecho.domain.repository

import com.github.maskedkunisquat.projectecho.domain.model.WorldState

/**
 * Persistence contract for [WorldState]. Implementations handle the storage medium.
 */
interface WorldStateRepository {
    suspend fun save(state: WorldState)
    suspend fun load(): WorldState?
}
