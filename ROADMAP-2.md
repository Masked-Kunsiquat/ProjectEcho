# ProjectEcho — Development Roadmap

## Status

**V1 Complete (Phases 1–5):** Flat single-tribe simulator with pure-Kotlin game loop, 5 divine interventions, JSON-backed event engine, Room persistence, and a Canvas 16×6 herringbone tribal map.

**V2 (Phases 6–10):** Architectural shift to a spatially-aware, tile-backed simulation engine. Every feature below builds on the tile grid as the new source of truth.

---

## Phase 6 — Tile-Based Database Overhaul

> Transform the flat V1 data model into a grid-backed world state holding 192 individual `MapTile` objects.

- [x] Add `MapTile.kt` to `domain/model/` — data class with: `id: Int`, `col: Int`, `row: Int`, `soilMoisture: Int` (0–100), `volatility: Int` (0–100), `occupantTribeId: String?`
- [x] Transform `WorldState.kt` — replace single `tribe: Tribe` with `tiles: List<MapTile>` (192 entries mirroring the 16×6 herringbone grid) and a `tribes: Map<String, Tribe>` registry
- [x] Update `Tribe.kt` to act as a census: keep global counters (`tribeId: String`, `population: Int`, `foodSupply: Int`, `devotion: Int`); remove tile-local fields — individual `MapTile` structures now track which tribe occupies each cell
- [x] Update `WorldStateEntity.kt` and `RoomWorldStateRepository.kt` to serialize the expanded `WorldState` (tiles list + tribes map) via `kotlinx.serialization`
- [x] Update `GameLoop.tick()` to accept and return the new `WorldState` structure; route tile-level reads/writes through the tile list
- [x] Update `GameLoopTest.kt` and `GameViewModelTest.kt` for new model signatures
- [ ] Smoke test: app launches, tribe renders on grid, tick advances without crash

---

## Phase 7 — Environmental Phases & Natural Decay Logic

> Give the land a living metabolism — soil moisture and volatility drift on their own, and phase thresholds govern food output.

- [x] Add a `decayStep()` function inside `GameLoop.kt` that runs at the end of every tick; nudge each tile's `soilMoisture` and `volatility` toward a fertile baseline by a configurable delta constant
- [x] Define `EnvironmentalPhase` sealed class (or enum) in `domain/model/` with 4 variants:
  - `Deluge` — moisture **81–100**: starvation penalty, active population casualties each tick
  - `Saturated` — moisture **51–80**: 50% food efficiency
  - `Fertile` — moisture **21–50**: 100% food growth (baseline)
  - `Parched` — moisture **0–20**: 10% crop yield
- [x] Wire `EnvironmentalPhase` resolution into the survival phase of `GameLoop.tick()` — per-tile phase determines each tile's food delta contribution for any tribe occupying it
- [x] Add at least 2 new environmental trigger events to `assets/events.json` (e.g., "The fields lie scorched and cracked" for Parched; "The rivers spill their banks" for Deluge)
- [x] Write unit tests covering each phase threshold, boundary conditions (e.g., exactly 81 = Deluge), and decay convergence
- [ ] Smoke test: manually set tile moisture values in a test; confirm food output matches expected phase multipliers

---

## Phase 8 — Passive Climate Conveyor (Autonomous Weather)

> The world moves entirely on its own. A drifting climate front crosses the grid without any player input, telegraphing its arrival in the Chronicle.

- [ ] Add `WeatherType` enum to `domain/model/` with moisture delta values per type: `RainCloud` (+15 moisture/tick to affected column), `HeatWave` (-12 moisture/tick to affected column)
- [ ] Add `WeatherFront.kt` to `domain/model/` — data class with: `type: WeatherType`, `column: Int` (current grid column, 0–15), `direction: Int` (+1 eastward or -1 westward)
- [ ] Add optional `activeFront: WeatherFront?` field to `WorldState`
- [ ] In `GameLoop.tick()`, add a `weatherStep()` sub-function:
  - [ ] Every N ticks (configurable constant, e.g., 10), spawn a new `WeatherFront` on a random edge column with a random direction
  - [ ] Each tick, advance `activeFront.column` by its `direction`; apply that front's moisture delta to all tiles in the current column
  - [ ] When the front exits the grid (column < 0 or > 15), clear `activeFront`
- [ ] On weather spawn, append a telegraphed warning to `eventHistory` (e.g., "Dark clouds gather on the eastern horizon…" for RainCloud; "A shimmering heat bends the horizon…" for HeatWave)
- [ ] On weather exit, append a closing note to `eventHistory` (e.g., "The storm has passed. The land is still.")
- [ ] Write unit tests: front spawns on edge, advances column correctly each tick, applies moisture delta only to current column's tiles, clears on grid exit
- [ ] Smoke test: observe Chronicle ledger for weather warnings; confirm moisture values shift on the affected column's tiles each tick

---

## Phase 9 — Radial Splash Targeting (UX & Fat-Finger Fix)

> Redesign divine interventions from single-tile pokes to spatial cluster actions with an on-screen halo indicator.

- [ ] Add `TileNeighbors.kt` utility to `domain/rules/` — pure Kotlin function `getNeighbors(tileId: Int, cols: Int = 16): List<Int>` returning the geometric neighbors for a herringbone triangle tile (handles edge tiles, corner tiles, and interior tiles differently)
- [ ] Update `TribalGridMap.kt` — add touch/pointer input handling on the `Canvas`; on finger press, calculate the touched tile using existing triangle geometry
- [ ] Add `hoveredCluster: List<Int>` state to `TribalGridMap` — on each press, call `getNeighbors()` and redraw the touched tile plus its neighbors with a radial amber glow and white outline halo
- [ ] Update `GameViewModel.kt` — replace single-tile `applyDivineAction(action)` with `applyDivineAction(action, targetCluster: List<Int>)` that accepts a tile ID list
- [ ] Update the action phase in `GameLoop.tick()` — iterate over `targetCluster` tiles and apply stat modifications (moisture ±, volatility ±) to each; population/food effects scale proportionally by the number of occupied tiles in the cluster
- [ ] Write unit tests for `getNeighbors()` covering: a center tile, a left-edge tile, a right-edge tile, a corner tile
- [ ] Smoke test: touch a tile on-device; confirm radial halo highlights the correct neighbors; cast Rain, confirm moisture increases across the entire highlighted cluster

---

## Phase 10 — Future Runway (Placeholders)

> Stubs for the next generation of social and civilizational mechanics. No implementation yet — just defined triggers and expected outputs.

- [ ] **Tribal Splitting**
  - Trigger: tile population density on a single `MapTile` exceeds a configurable threshold
  - Expected output: a new `Tribe` entry is inserted into `WorldState.tribes`; excess population density migrates to an adjacent unoccupied `MapTile` slot; Chronicle logs the schism
  - Status: *placeholder — no implementation*

- [ ] **Sophistication Progression**
  - Outline: a `sophisticationLevel: Int` counter on `Tribe` that rises as population and devotion milestones are crossed; higher levels unlock new narrative event categories, new `DivineAction` types, and unique Chronicle entries
  - Status: *placeholder — no implementation*

- [ ] **Skepticism / Defiance System**
  - Outline: a `skepticism: Int` counter on `Tribe` that increments when interventions are too frequent or too dramatic; high skepticism reduces devotion regen rate, eventually triggering defiance events that drain divine favor automatically
  - Status: *placeholder — no implementation*

---

*Each phase is designed to be committed as its own micro-branch and PR. Complete all checkboxes in a phase before beginning the next.*