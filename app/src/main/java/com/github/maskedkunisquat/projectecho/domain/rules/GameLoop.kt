package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.EnvironmentalPhase
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import com.github.maskedkunisquat.projectecho.domain.model.GRID_SIZE
import com.github.maskedkunisquat.projectecho.domain.model.Tribe
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

internal const val MOISTURE_BASELINE = 35
internal const val DECAY_DELTA = 1
private const val DELUGE_CASUALTY_RATE = 0.97
internal const val TILE_CAPACITY = 10  // max people a single tile can feed at full multiplier

fun tick(
    currentState: WorldState,
    action: DivineAction? = null,
    events: List<SimEvent> = emptyList(),
    targetCluster: List<Int> = emptyList(),
    random: Random = Random.Default,
): WorldState {
    var state = currentState.copy(tiles = decayStep(currentState.tiles))

    if (action != null && state.divineFavor >= action.favorCost) {
        val updatedTribes = state.tribes.mapValues { (_, tribe) ->
            when (action) {
                DivineAction.CastRain    -> tribe.copy(foodSupply = tribe.foodSupply + 50)
                DivineAction.SendPlague  -> tribe.copy(population = (tribe.population * 0.8).roundToInt())
                DivineAction.InspireDevout -> tribe.copy(devotion = minOf(100, tribe.devotion + 15))
                DivineAction.CauseFamine -> tribe.copy(foodSupply = maxOf(0, tribe.foodSupply - 80))
                DivineAction.BlessHarvest -> tribe.copy(foodSupply = tribe.foodSupply + 200)
            }
        }
        state = state.copy(
            tribes = updatedTribes,
            divineFavor = (state.divineFavor - action.favorCost).coerceIn(0, 100),
            tiles = applyClusterTileEffect(state.tiles, targetCluster, action),
        )
    }

    val survivedTribes = state.tribes.mapValues { (_, tribe) ->
        val occupiedTiles = state.tiles.filter { it.occupantTribeId == tribe.tribeId }
        val effectiveMultiplier = if (occupiedTiles.isEmpty()) {
            1.0 // no tile data → default to Fertile baseline, preserving pre-Phase-7 behaviour
        } else {
            occupiedTiles.map { EnvironmentalPhase.from(it.soilMoisture).foodMultiplier }.average()
        }

        val effectiveFarmers = if (occupiedTiles.isEmpty()) tribe.population
                               else minOf(tribe.population, occupiedTiles.size * TILE_CAPACITY)
        val farmed = (effectiveFarmers * 0.8 * effectiveMultiplier).roundToInt()
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

        // Flooding kills regardless of food stores
        val hasDeluge = occupiedTiles.any { EnvironmentalPhase.from(it.soilMoisture) is EnvironmentalPhase.Deluge }
        if (hasDeluge) afterSurvival.copy(population = (afterSurvival.population * DELUGE_CASUALTY_RATE).roundToInt())
        else afterSurvival
    }

    val regenAmount = (survivedTribes.values.maxOfOrNull { it.devotion } ?: 0) * 3 / 100
    val regenedFavor = minOf(100, state.divineFavor + regenAmount)

    val postTickState = state.copy(
        worldTimeTick = state.worldTimeTick + 1,
        divineFavor = regenedFavor,
        tribes = survivedTribes,
        tiles = territoryStep(state.tiles, survivedTribes),
    )

    val currentTick = postTickState.worldTimeTick
    val eligibleEvents = events.filter { event ->
        val lastFired = postTickState.eventCooldowns[event.id]
        when {
            lastFired == null -> true
            event.cooldownTicks == null -> false
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
        if (tile.col == front.column)
            tile.copy(soilMoisture = (tile.soilMoisture + front.type.moistureDelta).coerceIn(0, 100))
        else
            tile
    }

    val nextColumn = front.column + front.direction
    val exited = nextColumn < 0 || nextColumn >= GRID_COLS
    return working.copy(
        tiles = updatedTiles,
        activeFront = if (exited) null else front.copy(column = nextColumn),
        nextSpawnTick = if (exited) working.worldTimeTick + random.nextLong(20L, 41L) else working.nextSpawnTick,
        eventHistory = if (exited) working.eventHistory + "The storm has passed. The land is still."
                       else working.eventHistory,
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
                occupiedIndices.any { ownedIdx ->
                    val o = working[ownedIdx]
                    val dCol = abs(t.col - o.col)
                    val dRow = abs(t.row - o.row)
                    (dCol == 0 && dRow == 0) || (dCol + dRow == 1)
                }
            }
            frontier.take(deficit).forEach { idx ->
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
            DivineAction.CastRain     -> tile.copy(soilMoisture = (tile.soilMoisture + 15).coerceIn(0, 100))
            DivineAction.BlessHarvest -> tile.copy(soilMoisture = (tile.soilMoisture + 8).coerceIn(0, 100))
            DivineAction.CauseFamine  -> tile.copy(soilMoisture = (tile.soilMoisture - 15).coerceIn(0, 100))
            else                      -> tile
        }
    }
}

fun decayStep(tiles: List<MapTile>): List<MapTile> = tiles.map { tile ->
    tile.copy(
        soilMoisture = when {
            tile.soilMoisture > MOISTURE_BASELINE -> maxOf(MOISTURE_BASELINE, tile.soilMoisture - DECAY_DELTA)
            tile.soilMoisture < MOISTURE_BASELINE -> minOf(MOISTURE_BASELINE, tile.soilMoisture + DECAY_DELTA)
            else -> tile.soilMoisture
        },
        volatility = maxOf(0, tile.volatility - DECAY_DELTA),
    )
}
