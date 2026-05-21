package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.tanh

private const val NUM_ACTIONS     = 12
private const val ACTION_EXPAND_N  = 0
private const val ACTION_EXPAND_S  = 1
private const val ACTION_EXPAND_E  = 2
private const val ACTION_EXPAND_W  = 3
private const val ACTION_RAID_BASE = 4
private const val ACTION_REST      = 11

@Serializable
private data class LayerWeights(val w: List<List<Float>>, val b: List<Float>)

@Serializable
private data class PolicyWeights(val layers: List<LayerWeights>)

/**
 * On-device RL policy backed by a trained MLP loaded from JSON.
 *
 * Call [setWorldState] once before each [tick] so the policy can build
 * observations and action masks; [chooseExpansion] and [chooseRaid] then
 * look up the pre-computed action for each tribe.
 */
class RLPolicy private constructor(
    // Each layer: (weight matrix [outDim × inDim], bias [outDim])
    private val weights: List<Pair<Array<FloatArray>, FloatArray>>,
) : TribePolicy {

    private var cachedActions: Map<String, Int> = emptyMap()
    private var worldState: WorldState? = null

    /** Actions chosen in the most recent [setWorldState] call: tribeId → action index. */
    val lastActions: Map<String, Int> get() = cachedActions

    fun setWorldState(state: WorldState) {
        worldState = state
        cachedActions = state.tribes.mapValues { (_, tribe) ->
            val ownedTiles = state.tiles.filter { it.occupantTribeId == tribe.tribeId }
            val neighbors  = state.tribes.values.filter { it.tribeId != tribe.tribeId }
            val obs        = tribe.toFloatArray(ownedTiles, neighbors, state.tiles, state.worldTimeTick)
            val mask       = buildMask(tribe, state, ownedTiles)
            infer(obs, mask)
        }
    }

    override fun chooseExpansion(tribe: Tribe, candidates: List<MapTile>): MapTile? {
        if (candidates.isEmpty()) return null
        return when (cachedActions[tribe.tribeId] ?: ACTION_REST) {
            ACTION_EXPAND_N -> candidates.minWithOrNull(compareBy({ it.row },  { it.col  }))
            ACTION_EXPAND_S -> candidates.minWithOrNull(compareBy({ -it.row }, { it.col  }))
            ACTION_EXPAND_E -> candidates.minWithOrNull(compareBy({ -it.col }, { it.row  }))
            ACTION_EXPAND_W -> candidates.minWithOrNull(compareBy({ it.col },  { it.row  }))
            else            -> null
        }
    }

    override fun chooseRaid(tribe: Tribe, targets: List<RaidCandidate>): RaidCandidate? {
        if (targets.isEmpty()) return null
        val action = cachedActions[tribe.tribeId] ?: return null
        if (action < ACTION_RAID_BASE || action >= ACTION_REST) return null
        val raidIdx     = action - ACTION_RAID_BASE
        val state       = worldState ?: return null
        val sortedOthers = state.tribes.keys.filter { it != tribe.tribeId }.sorted()
        if (raidIdx >= sortedOthers.size) return null
        val targetId = sortedOthers[raidIdx]
        return targets.firstOrNull { it.defenderTribeId == targetId }
    }

    // -------------------------------------------------------------------------

    private fun buildMask(tribe: Tribe, state: WorldState, ownedTiles: List<MapTile>): BooleanArray {
        val mask     = BooleanArray(NUM_ACTIONS)
        mask[ACTION_REST] = true

        val ownedIds    = ownedTiles.map { it.id }.toHashSet()
        val neighborIds = ownedIds.flatMap { getNeighbors(it) }.toHashSet()
        if (state.tiles.any { it.occupantTribeId == null && it.biome != BiomeType.Water && it.id in neighborIds }) {
            mask[ACTION_EXPAND_N] = true
            mask[ACTION_EXPAND_S] = true
            mask[ACTION_EXPAND_E] = true
            mask[ACTION_EXPAND_W] = true
        }

        // Starvation guard: a tribe with no food cannot sustain a raid
        if (tribe.foodSupply > 0) {
            val sortedOthers = state.tribes.keys.filter { it != tribe.tribeId }.sorted()
            for ((i, otherId) in sortedOthers.take(Tribe.MAX_TRIBES - 1).withIndex()) {
                val other    = state.tribes[otherId] ?: continue
                if (other.divineShieldTicks > 0) continue
                val otherIds = state.tiles.filter { it.occupantTribeId == otherId }.map { it.id }.toHashSet()
                val adjacent = otherIds.any { oid -> getNeighbors(oid).any { it in ownedIds } }
                if (adjacent) mask[ACTION_RAID_BASE + i] = true
            }
        }
        return mask
    }

    private fun infer(obs: FloatArray, mask: BooleanArray): Int {
        var x = obs
        for ((layerIdx, layer) in weights.withIndex()) {
            val (w, b) = layer
            val isLast = layerIdx == weights.size - 1
            val out    = FloatArray(b.size)
            for (row in w.indices) {
                var sum = b[row]
                for (col in x.indices) sum += w[row][col] * x[col]
                out[row] = if (isLast) sum else tanh(sum.toDouble()).toFloat()
            }
            x = out
        }
        // Masked argmax: ignore logits for actions the mask marks invalid
        var bestIdx = ACTION_REST
        var bestVal = Float.NEGATIVE_INFINITY
        for (i in x.indices) {
            if (i < mask.size && mask[i] && x[i] > bestVal) {
                bestVal = x[i]
                bestIdx = i
            }
        }
        return bestIdx
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): RLPolicy {
            val pw = json.decodeFromString<PolicyWeights>(jsonString)
            val layers = pw.layers.map { layer ->
                val w = Array(layer.w.size) { r -> layer.w[r].toFloatArray() }
                val b = layer.b.toFloatArray()
                w to b
            }
            return RLPolicy(layers)
        }
    }
}
