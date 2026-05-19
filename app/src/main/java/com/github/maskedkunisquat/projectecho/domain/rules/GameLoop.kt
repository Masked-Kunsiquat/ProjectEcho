package com.github.maskedkunisquat.projectecho.domain.rules

import com.github.maskedkunisquat.projectecho.domain.model.DivineAction
import com.github.maskedkunisquat.projectecho.domain.model.EnvironmentalPhase
import com.github.maskedkunisquat.projectecho.domain.model.MapTile
import com.github.maskedkunisquat.projectecho.domain.model.SimEvent
import com.github.maskedkunisquat.projectecho.domain.model.WeatherFront
import com.github.maskedkunisquat.projectecho.domain.model.WeatherType
import com.github.maskedkunisquat.projectecho.domain.model.WorldState
import com.github.maskedkunisquat.projectecho.domain.model.GRID_COLS
import kotlin.math.roundToInt
import kotlin.random.Random

internal const val MOISTURE_BASELINE = 35
internal const val DECAY_DELTA = 1
private const val DELUGE_CASUALTY_RATE = 0.97

fun tick(
    currentState: WorldState,
    action: DivineAction? = null,
    events: List<SimEvent> = emptyList(),
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
        )
    }

    val survivedTribes = state.tribes.mapValues { (_, tribe) ->
        val occupiedTiles = state.tiles.filter { it.occupantTribeId == tribe.tribeId }
        val effectiveMultiplier = if (occupiedTiles.isEmpty()) {
            1.0 // no tile data → default to Fertile baseline, preserving pre-Phase-7 behaviour
        } else {
            occupiedTiles.map { EnvironmentalPhase.from(it.soilMoisture).foodMultiplier }.average()
        }

        val farmed = (tribe.population * 0.8 * effectiveMultiplier).roundToInt()
        val newFoodSupply = tribe.foodSupply + farmed - tribe.population

        val afterSurvival = when {
            newFoodSupply < 0 -> tribe.copy(
                foodSupply = 0,
                population = (tribe.population * 0.95).roundToInt(),
                devotion = maxOf(0, tribe.devotion - 3),
            )
            newFoodSupply > 0 -> tribe.copy(
                foodSupply = newFoodSupply,
                population = (tribe.population * 1.02).roundToInt(),
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
    )

    val unfiredEvents = events.filter { it.id !in postTickState.firedEventIds }
    val triggered = EventEngine.evaluate(postTickState, unfiredEvents)

    val eventedState = if (triggered.isEmpty()) {
        postTickState
    } else {
        postTickState.copy(
            eventHistory = postTickState.eventHistory + triggered.map { it.text },
            firedEventIds = postTickState.firedEventIds + triggered.map { it.id },
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
