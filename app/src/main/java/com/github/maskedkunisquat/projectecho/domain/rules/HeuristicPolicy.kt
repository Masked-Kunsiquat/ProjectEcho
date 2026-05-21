package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import kotlin.random.Random

class HeuristicPolicy(private val random: Random = Random.Default) : TribePolicy {

    override fun chooseExpansion(tribe: Tribe, candidates: List<MapTile>): MapTile? =
        candidates.maxByOrNull { tile ->
            tribe.personality.affinityFor(tile.biome) * (tile.soilMoisture / 100f)
        }

    override fun chooseRaid(tribe: Tribe, targets: List<RaidCandidate>): RaidCandidate? {
        if (targets.isEmpty()) return null
        val first = targets.first()
        val attackBonus = 1f + tribe.personality.sophistication * ATTACK_SOPHISTICATION_BONUS
        val defenseBonus = 1f - first.defender.personality.sophistication * DEFENSE_SOPHISTICATION_BONUS
        val devotionSuppression = 1f - tribe.devotion / RAID_DEVOTION_SUPPRESSION_DIVISOR
        val threshold = tribe.personality.aggression *
            (1f - first.defender.personality.caution) *
            attackBonus * defenseBonus * devotionSuppression
        if (random.nextFloat() >= threshold) return null
        return targets.random(random)
    }
}
