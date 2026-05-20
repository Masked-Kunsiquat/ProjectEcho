package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.BiomeType
import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.EnvironmentalPhase
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import com.github.maskedkunisquat.projectecho.domain.model.TribePersonality
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_SIZE
import com.github.maskedkunisquat.projectecho.domain.model.TribeNameGenerator
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

internal const val MOISTURE_BASELINE = 35
internal const val DECAY_DELTA = 1
private const val DELUGE_CASUALTY_RATE = 0.97
internal const val TILE_CAPACITY = 10
internal const val COAST_FISHING_BONUS = 5
internal const val HIGH_VOLATILITY_THRESHOLD = 70

internal const val SPLIT_DENSITY_THRESHOLD = 8
internal const val SPLIT_MIN_POPULATION    = 400
internal const val SPLIT_COOLDOWN_TICKS    = 100L
private  const val SPLIT_PARENT_SHARE      = 0.60

internal const val MAX_SOPHISTICATION = 10
private val SOPHISTICATION_POP_MILESTONES      = listOf(500, 1000, 1500)
private val SOPHISTICATION_DEVOTION_MILESTONES = listOf(75, 90)

internal const val PRAYER_THRESHOLD = 60
internal const val PRAYER_PRESSURE_CAP = 200f
internal const val SKEPTICISM_DECAY_BASE = 10f

fun tick(
    currentState: WorldState,
    action: DivineAction? = null,
    events: List<SimEvent> = emptyList(),
    targetCluster: List<Int> = emptyList(),
    personalities: List<TribePersonality> = emptyList(),
    targetTribeId: String? = null,
    random: Random = Random.Default,
): WorldState {
    var state = currentState.copy(tiles = decayStep(currentState.tiles))

    val actionApplied = action != null && state.divineFavor >= action.favorCost
    if (actionApplied) {
        val updatedTribes = state.tribes.mapValues { (id, tribe) ->
            val isTarget = targetTribeId == null || id == targetTribeId
            val afterAction = if (isTarget) when (action) {
                DivineAction.CastRain      -> tribe
                DivineAction.SendPlague    -> tribe.copy(population = (tribe.population * 0.8).roundToInt())
                DivineAction.InspireDevout -> tribe.copy(devotion = minOf(100, tribe.devotion + 15))
                DivineAction.CauseFamine   -> tribe.copy(foodSupply = maxOf(0, tribe.foodSupply - 80))
                DivineAction.BlessHarvest  -> tribe.copy(foodSupply = tribe.foodSupply + 200)
                null                       -> tribe
            } else tribe
            if (isTarget) {
                val skepGain = (action!!.favorCost / 10f * tribe.personality.skepticismRate).roundToInt()
                afterAction.copy(
                    prayerPressure = afterAction.prayerPressure * 0.5f,
                    personality = afterAction.personality.copy(
                        skepticism = minOf(100, afterAction.personality.skepticism + skepGain)
                    )
                )
            } else afterAction
        }
        val effectiveCluster = if (action == DivineAction.CastRain && targetCluster.isEmpty()) {
            state.tiles.filter { it.occupantTribeId != null }.map { it.id }
        } else targetCluster
        state = state.copy(
            tribes = updatedTribes,
            divineFavor = (state.divineFavor - action.favorCost).coerceIn(0, 100),
            tiles = applyClusterTileEffect(state.tiles, effectiveCluster, action),
        )
    }

    // Survival + generational drift
    val generationEntries = mutableListOf<String>()
    val survivedTribes = state.tribes.mapValues { (_, tribe) ->
        val occupiedTiles = state.tiles.filter { it.occupantTribeId == tribe.tribeId }

        val preferredBiomeName = tribe.personality.biomeAffinity.maxByOrNull { it.value }?.key
        val sophBonus = minOf(tribe.personality.sophistication, MAX_SOPHISTICATION) * 0.02f

        val effectiveMultiplier = if (occupiedTiles.isEmpty()) 1.0
        else occupiedTiles.map { tile ->
            val envMult = EnvironmentalPhase.from(tile.soilMoisture).foodMultiplier
            val sophMult = if (preferredBiomeName != null && tile.biome.name == preferredBiomeName)
                (1.0 + sophBonus) else 1.0
            envMult * sophMult
        }.average()

        val effectiveFarmers = if (occupiedTiles.isEmpty()) tribe.population
                               else minOf(tribe.population, occupiedTiles.size * TILE_CAPACITY)
        val coastBonus = occupiedTiles.count { it.biome == BiomeType.Coast } * COAST_FISHING_BONUS
        val farmed = (effectiveFarmers * 0.8 * effectiveMultiplier).roundToInt() + coastBonus
        val newFoodSupply = tribe.foodSupply + farmed - tribe.population

        val afterSurvival = when {
            newFoodSupply < 0 -> tribe.copy(
                foodSupply = 0,
                population = minOf(tribe.population - 1, (tribe.population * 0.95).roundToInt()).coerceAtLeast(0),
                devotion = maxOf(0, tribe.devotion - 3),
            )
            newFoodSupply > 0 -> tribe.copy(
                foodSupply = newFoodSupply,
                population = maxOf(tribe.population + 1, (tribe.population * 1.02).roundToInt()),
                devotion = minOf(100, tribe.devotion + 1),
            )
            else -> tribe.copy(foodSupply = 0)
        }

        val hasDeluge = occupiedTiles.any { EnvironmentalPhase.from(it.soilMoisture) is EnvironmentalPhase.Deluge }
        val afterDeluge = if (hasDeluge)
            afterSurvival.copy(population = (afterSurvival.population * DELUGE_CASUALTY_RATE).roundToInt())
        else afterSurvival

        // Generational drift: accumulate deaths; turnover when >= half current population
        val deathsThisTick = maxOf(0, tribe.population - afterDeluge.population)
        val newGenDeaths = afterDeluge.generationDeaths + deathsThisTick
        val halfPop = afterDeluge.population / 2
        if (halfPop > 0 && newGenDeaths >= halfPop) {
            val trad = afterDeluge.personality.traditionalism
            val newSkepticism = (afterDeluge.personality.skepticism * trad).roundToInt()
            val nudge = ((1f - trad) * 10f).roundToInt()
            val newDevotion = when {
                afterDeluge.devotion > 50 -> maxOf(50, afterDeluge.devotion - nudge)
                afterDeluge.devotion < 50 -> minOf(50, afterDeluge.devotion + nudge)
                else -> afterDeluge.devotion
            }
            generationEntries += "A new generation of the ${afterDeluge.name} comes of age, carrying echoes of the past."
            afterDeluge.copy(
                generationDeaths = 0,
                devotion = newDevotion,
                personality = afterDeluge.personality.copy(skepticism = newSkepticism),
            )
        } else {
            afterDeluge.copy(generationDeaths = newGenDeaths)
        }
    }

    // Sophistication milestones
    val sophisticationEntries = mutableListOf<String>()
    val withSophistication = survivedTribes.mapValues { (_, tribe) ->
        val popMet = SOPHISTICATION_POP_MILESTONES.count { it <= tribe.population }
        val devMet = SOPHISTICATION_DEVOTION_MILESTONES.count { it <= tribe.devotion }
        val expectedSoph = popMet + devMet
        if (tribe.personality.sophistication < expectedSoph) {
            sophisticationEntries += "The ${tribe.name} advances — their mastery of the land deepens."
            tribe.copy(personality = tribe.personality.copy(sophistication = tribe.personality.sophistication + 1))
        } else tribe
    }

    // Unanswered prayer pressure + passive skepticism decay
    val prayerChronicleEntries = mutableListOf<String>()
    val withPrayerDecay = withSophistication.mapValues { (tId, tribe) ->
        val actionTargetsThisTribe = actionApplied && (targetTribeId == null || tId == targetTribeId)
        val newPressure = if (!actionTargetsThisTribe && tribe.devotion > PRAYER_THRESHOLD)
            tribe.prayerPressure + (tribe.devotion - PRAYER_THRESHOLD).toFloat()
        else tribe.prayerPressure

        val (finalPressure, skeptAfterPrayer) = if (newPressure >= PRAYER_PRESSURE_CAP) {
            prayerChronicleEntries += "The prayers of ${tribe.name} go unanswered. Doubt spreads among the faithful."
            0f to minOf(100, tribe.personality.skepticism + 1)
        } else {
            newPressure to tribe.personality.skepticism
        }

        val decayRate = maxOf(0.01f, tribe.personality.skepticismRate)
        val newDecayBuffer = tribe.skepticismDecayBuffer + 1f / decayRate
        val (finalDecayBuffer, skeptAfterDecay) = if (newDecayBuffer >= SKEPTICISM_DECAY_BASE) {
            0f to maxOf(0, skeptAfterPrayer - 1)
        } else {
            newDecayBuffer to skeptAfterPrayer
        }

        tribe.copy(
            prayerPressure = finalPressure,
            skepticismDecayBuffer = finalDecayBuffer,
            personality = tribe.personality.copy(skepticism = skeptAfterDecay),
        )
    }

    // Devotion regen with skepticism penalty
    val regenAmount = (withPrayerDecay.values.maxOfOrNull { tribe ->
        val base = tribe.devotion * 3 / 100
        (base * (1f - tribe.personality.skepticism / 200f)).roundToInt()
    } ?: 0)
    val regenedFavor = minOf(100, state.divineFavor + regenAmount)

    val postTerritoryState = state.copy(
        worldTimeTick = state.worldTimeTick + 1,
        divineFavor = regenedFavor,
        tribes = withPrayerDecay,
        tiles = territoryStep(state.tiles, withPrayerDecay),
        eventHistory = state.eventHistory + generationEntries + sophisticationEntries + prayerChronicleEntries,
    )
    val postConflictState = conflictStep(postTerritoryState, random)
    val postTickState = splitStep(postConflictState, random)

    val currentTick = postTickState.worldTimeTick
    val eligibleEvents = events.filter { event ->
        val lastFired = postTickState.eventCooldowns[event.id]
        when {
            lastFired == null -> true
            event.cooldownTicks == null || event.cooldownTicks < 0 -> false
            else -> currentTick >= lastFired + event.cooldownTicks
        }
    }
    val triggered = EventEngine.evaluate(postTickState, eligibleEvents)

    val eventedState = if (triggered.isEmpty()) {
        postTickState
    } else {
        val resolvedTexts = triggered.mapNotNull { event ->
            event.texts.randomOrNull(random)?.let { NarrativeResolver.resolve(it, postTickState) }
        }
        postTickState.copy(
            eventHistory = postTickState.eventHistory + resolvedTexts,
            eventCooldowns = postTickState.eventCooldowns + triggered.map { it.id to currentTick },
        )
    }

    return weatherStep(eventedState, random)
}

fun weatherStep(state: WorldState, random: Random = Random.Default): WorldState {
    var working = state

    if (working.activeFront == null && working.worldTimeTick >= working.nextSpawnTick) {
        val startEdge = if (random.nextBoolean()) 0 else GRID_COLS - 1
        val direction = if (startEdge == 0) 1 else -1
        val type = if (random.nextBoolean()) WeatherType.RainCloud else WeatherType.HeatWave
        val horizonSide = if (direction > 0) "western" else "eastern"
        val warning = when (type) {
            WeatherType.RainCloud -> "Dark clouds gather on the $horizonSide horizon…"
            WeatherType.HeatWave  -> "A shimmering heat bends the $horizonSide horizon…"
        }
        working = working.copy(
            activeFront = WeatherFront(type = type, column = startEdge, direction = direction),
            eventHistory = working.eventHistory + warning,
        )
    }

    val front = working.activeFront ?: return working

    val updatedTiles = working.tiles.map { tile ->
        if (tile.col != front.column || tile.biome == BiomeType.Water) return@map tile
        val amplifiedDelta = (front.type.moistureDelta * tile.biome.weatherResistance *
                (1f + tile.volatility / 100f)).roundToInt()
        tile.copy(
            soilMoisture = (tile.soilMoisture + amplifiedDelta).coerceIn(0, 100),
            volatility = minOf(100, tile.volatility + tile.biome.volatilityGain),
        )
    }

    val nextColumn = front.column + front.direction
    val exited = nextColumn < 0 || nextColumn >= GRID_COLS

    val extremeEvent = if (updatedTiles.any { it.col == front.column && it.biome != BiomeType.Water && it.volatility > HIGH_VOLATILITY_THRESHOLD }) {
        when (front.type) {
            WeatherType.RainCloud -> "A great storm tears through the valley."
            WeatherType.HeatWave  -> "The land cracks and bleaches under relentless heat."
        }
    } else null

    return working.copy(
        tiles = updatedTiles,
        activeFront = if (exited) null else front.copy(column = nextColumn),
        nextSpawnTick = if (exited) working.worldTimeTick + random.nextLong(20L, 41L) else working.nextSpawnTick,
        eventHistory = working.eventHistory +
                listOfNotNull(extremeEvent, if (exited) "The storm has passed. The land is still." else null),
    )
}

internal fun territoryStep(tiles: List<MapTile>, tribes: Map<String, Tribe>): List<MapTile> {
    val working = tiles.toMutableList()
    for ((tribeId, tribe) in tribes) {
        val expected = maxOf(0, tribe.population * GRID_SIZE / 500)
        val occupiedIndices = working.indices.filter { working[it].occupantTribeId == tribeId }
        val excess = occupiedIndices.size - expected
        if (excess > 0) {
            occupiedIndices.takeLast(excess).forEach { idx ->
                working[idx] = working[idx].copy(occupantTribeId = null)
            }
        } else if (excess < 0) {
            val deficit = -excess
            val frontier = working.indices.filter { idx ->
                val t = working[idx]
                if (t.occupantTribeId != null) return@filter false
                if (t.biome == BiomeType.Water) return@filter false
                occupiedIndices.any { ownedIdx ->
                    val o = working[ownedIdx]
                    val dCol = abs(t.col - o.col)
                    val dRow = abs(t.row - o.row)
                    (dCol == 0 && dRow == 0) || (dCol + dRow == 1)
                }
            }
            // Prefer tiles matching the tribe's top biome affinity
            val scoredFrontier = frontier.sortedByDescending { idx ->
                val t = working[idx]
                tribe.personality.affinityFor(t.biome) * (t.soilMoisture / 100f)
            }
            scoredFrontier.take(deficit).forEach { idx ->
                working[idx] = working[idx].copy(occupantTribeId = tribeId)
            }
        }
    }
    return working
}

private fun applyClusterTileEffect(
    tiles: List<MapTile>,
    cluster: List<Int>,
    action: DivineAction,
): List<MapTile> {
    if (cluster.isEmpty()) return tiles
    val clusterSet = cluster.toHashSet()
    return tiles.map { tile ->
        if (tile.id !in clusterSet) tile
        else when (action) {
            DivineAction.CastRain     -> if (tile.biome == BiomeType.Water) tile else tile.copy(
                soilMoisture = (tile.soilMoisture + 25).coerceIn(0, 100),
                volatility   = minOf(100, tile.volatility + 10),
            )
            DivineAction.BlessHarvest -> tile.copy(soilMoisture = (tile.soilMoisture + 8).coerceIn(0, 100))
            DivineAction.CauseFamine  -> tile.copy(soilMoisture = (tile.soilMoisture - 15).coerceIn(0, 100))
            else                      -> tile
        }
    }
}

internal fun splitStep(
    state: WorldState,
    random: Random = Random.Default,
): WorldState {
    if (state.worldTimeTick < state.lastSplitTick + SPLIT_COOLDOWN_TICKS) return state

    for ((tribeId, tribe) in state.tribes) {
        val occupiedTiles = state.tiles.filter {
            it.occupantTribeId == tribeId && it.biome != BiomeType.Water
        }
        if (tribe.population < SPLIT_MIN_POPULATION) continue
        if (occupiedTiles.isEmpty()) continue
        if (tribe.population / occupiedTiles.size <= SPLIT_DENSITY_THRESHOLD) continue

        val centroidCol = occupiedTiles.map { it.col }.average()
        val centroidRow = occupiedTiles.map { it.row }.average()
        val sortedByDist = occupiedTiles.sortedBy { tile ->
            val dCol = tile.col - centroidCol
            val dRow = tile.row - centroidRow
            dCol * dCol + dRow * dRow
        }
        val parentCount  = (sortedByDist.size * SPLIT_PARENT_SHARE).roundToInt()
        val childTileIds = sortedByDist.drop(parentCount).map { it.id }.toHashSet()

        val childId   = "$tribeId-${state.worldTimeTick}"
        val childName = TribeNameGenerator.generate(childId.hashCode())
        val parentPop  = (tribe.population  * SPLIT_PARENT_SHARE).roundToInt()
        val parentFood = (tribe.foodSupply  * SPLIT_PARENT_SHARE).roundToInt()

        // Child personality: mutate each float by ±0.15 from parent
        val parentPersonality = tribe.personality
        val childPersonality = TribePersonality(
            archetypeId   = parentPersonality.archetypeId,
            aggression    = (parentPersonality.aggression    + random.nextFloat() * 0.3f - 0.15f).coerceIn(0f, 1f),
            caution       = (parentPersonality.caution       + random.nextFloat() * 0.3f - 0.15f).coerceIn(0f, 1f),
            skepticismRate= (parentPersonality.skepticismRate + random.nextFloat() * 0.3f - 0.15f).coerceAtLeast(0f),
            traditionalism= (parentPersonality.traditionalism + random.nextFloat() * 0.3f - 0.15f).coerceIn(0f, 1f),
            biomeAffinity = parentPersonality.biomeAffinity.mapValues { (_, v) ->
                (v + random.nextFloat() * 0.3f - 0.15f).coerceIn(0.1f, 3.0f)
            },
            sophistication = 0,
            skepticism     = 0,
        )

        val updatedTiles  = state.tiles.map { tile ->
            if (tile.id in childTileIds) tile.copy(occupantTribeId = childId) else tile
        }
        val updatedTribes = state.tribes.toMutableMap().also { m ->
            m[tribeId] = tribe.copy(population = parentPop, foodSupply = parentFood)
            m[childId] = Tribe(
                tribeId        = childId,
                name           = childName,
                population     = tribe.population - parentPop,
                devotion       = tribe.devotion,
                foodSupply     = tribe.foodSupply - parentFood,
                personality    = childPersonality,
                generationDeaths = 0,
            )
        }
        return state.copy(
            tiles         = updatedTiles,
            tribes        = updatedTribes,
            lastSplitTick = state.worldTimeTick,
            eventHistory  = state.eventHistory +
                "The ${tribe.name} fractures. The dissenters call themselves $childName.",
        )
    }
    return state
}

internal fun conflictStep(state: WorldState, random: Random = Random.Default): WorldState {
    if (state.tribes.size < 2) return state

    val tribeIds = state.tribes.keys.toList()
    val chronicleEntries = mutableListOf<String>()
    var tiles = state.tiles

    for (aggressorId in tribeIds) {
        val aggressor = state.tribes[aggressorId] ?: continue
        for (defenderId in tribeIds) {
            if (aggressorId == defenderId) continue
            val defender = state.tribes[defenderId] ?: continue

            val aggressorTileIds = tiles.filter { it.occupantTribeId == aggressorId }.map { it.id }.toHashSet()
            val defenderBorderTiles = tiles.filter { tile ->
                tile.occupantTribeId == defenderId &&
                getNeighbors(tile.id).any { it in aggressorTileIds }
            }
            if (defenderBorderTiles.isEmpty()) continue

            val threshold = aggressor.personality.aggression * (1f - defender.personality.caution)
            if (random.nextFloat() < threshold) {
                val target = defenderBorderTiles.random(random)
                tiles = tiles.map { t ->
                    if (t.id == target.id) t.copy(occupantTribeId = aggressorId) else t
                }
                chronicleEntries += "The ${aggressor.name} raid the ${defender.name} frontier."
            }
        }
    }

    return if (chronicleEntries.isEmpty()) state
    else state.copy(tiles = tiles, eventHistory = state.eventHistory + chronicleEntries)
}

fun decayStep(tiles: List<MapTile>): List<MapTile> = tiles.map { tile ->
    val baseline = tile.biome.moistureBaseline
    tile.copy(
        soilMoisture = when {
            tile.soilMoisture > baseline -> maxOf(baseline, tile.soilMoisture - DECAY_DELTA)
            tile.soilMoisture < baseline -> minOf(baseline, tile.soilMoisture + DECAY_DELTA)
            else -> tile.soilMoisture
        },
        volatility = maxOf(0, tile.volatility - DECAY_DELTA),
    )
}
