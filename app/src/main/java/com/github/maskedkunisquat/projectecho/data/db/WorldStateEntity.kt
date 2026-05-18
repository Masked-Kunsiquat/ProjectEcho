package com.github.maskedkunisquat.projectecho.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table storing the serialized [WorldState] snapshot. */
@Entity(tableName = "world_state")
data class WorldStateEntity(
    @PrimaryKey val id: Int = 0,
    val stateJson: String,
)
