package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe

interface TribePolicy {
    fun chooseExpansion(tribe: Tribe, candidates: List<MapTile>): MapTile?
    fun chooseRaid(tribe: Tribe, targets: List<RaidCandidate>): RaidCandidate?
}

data class RaidCandidate(val tile: MapTile, val defenderTribeId: String, val defender: Tribe)
