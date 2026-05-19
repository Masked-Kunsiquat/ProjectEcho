package com.github.maskedkunisquat.projectecho.domain.model

import kotlinx.serialization.Serializable

const val GRID_COLS = 16
const val GRID_ROWS = 6
const val GRID_SIZE = GRID_COLS * GRID_ROWS * 2  // 192 triangles

@Serializable
data class MapTile(
    val id: Int,
    val col: Int,
    val row: Int,
    val soilMoisture: Int = 50,
    val volatility: Int = 0,
    val occupantTribeId: String? = null,
    val biome: BiomeType = BiomeType.Grassland,
)
