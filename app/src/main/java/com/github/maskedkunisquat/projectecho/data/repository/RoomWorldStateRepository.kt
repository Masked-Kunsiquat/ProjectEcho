package com.github.maskedkunisquat.projectecho.data.repository

import com.github.maskedkunisquat.projectecho.data.db.WorldStateDao
import com.github.maskedkunisquat.projectecho.data.db.WorldStateEntity
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.repository.WorldStateRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomWorldStateRepository(private val dao: WorldStateDao) : WorldStateRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(state: WorldState) {
        dao.upsert(WorldStateEntity(stateJson = json.encodeToString(state)))
    }

    override suspend fun load(): WorldState? =
        dao.load()?.stateJson?.let { json.decodeFromString<WorldState>(it) }
}
