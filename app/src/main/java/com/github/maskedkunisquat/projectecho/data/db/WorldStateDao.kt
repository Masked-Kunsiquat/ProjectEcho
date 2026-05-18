package com.github.maskedkunisquat.projectecho.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WorldStateDao {
    @Upsert
    suspend fun upsert(entity: WorldStateEntity)

    @Query("SELECT * FROM world_state LIMIT 1")
    suspend fun load(): WorldStateEntity?
}
