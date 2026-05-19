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
- [x] Smoke test: app launches, tribe renders on grid, tick advances without crash

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
- [x] Smoke test: manually set tile moisture values in a test; confirm food output matches expected phase multipliers

---

## Phase 8 — Passive Climate Conveyor (Autonomous Weather)

> The world moves entirely on its own. A drifting climate front crosses the grid without any player input, telegraphing its arrival in the Chronicle.

- [x] Add `WeatherType` enum to `domain/model/` with moisture delta values per type: `RainCloud` (+15 moisture/tick to affected column), `HeatWave` (-12 moisture/tick to affected column)
- [x] Add `WeatherFront.kt` to `domain/model/` — data class with: `type: WeatherType`, `column: Int` (current grid column, 0–15), `direction: Int` (+1 eastward or -1 westward)
- [x] Add optional `activeFront: WeatherFront?` field to `WorldState`
- [x] In `GameLoop.tick()`, add a `weatherStep()` sub-function:
  - [x] Every N ticks (configurable constant, e.g., 10), spawn a new `WeatherFront` on a random edge column with a random direction
  - [x] Each tick, advance `activeFront.column` by its `direction`; apply that front's moisture delta to all tiles in the current column
  - [x] When the front exits the grid (column < 0 or > 15), clear `activeFront`
- [x] On weather spawn, append a telegraphed warning to `eventHistory` (e.g., "Dark clouds gather on the eastern horizon…" for RainCloud; "A shimmering heat bends the horizon…" for HeatWave)
- [x] On weather exit, append a closing note to `eventHistory` (e.g., "The storm has passed. The land is still.")
- [x] Write unit tests: front spawns on edge, advances column correctly each tick, applies moisture delta only to current column's tiles, clears on grid exit
- [x] Smoke test: observe Chronicle ledger for weather warnings; confirm moisture values shift on the affected column's tiles each tick
- [x] **Post-smoke additions:** Replace fixed `tick % N` spawn interval with `nextSpawnTick: Long` on `WorldState`; on front exit, schedule next spawn at `currentTick + random(20, 40)` for unpredictable gaps; add semi-transparent column highlight to `TribalGridMap` (blue for RainCloud, orange for HeatWave)

---

## Phase 9 — Radial Splash Targeting & UI Overhaul

> Redesign divine interventions from single-tile pokes to spatial cluster actions with an on-screen halo indicator. Simultaneously overhaul the dashboard layout so the map is the hero, tribe identity scales to multi-tribe, and the action panel is uniform.

### Radial Splash Targeting
- [x] Add `TileNeighbors.kt` utility to `domain/rules/` — pure Kotlin function `getNeighbors(tileId: Int, cols: Int = 16): List<Int>` returning the geometric neighbors for a herringbone triangle tile (handles edge tiles, corner tiles, and interior tiles differently)
- [x] Update `TribalGridMap.kt` — add touch/pointer input handling on the `Canvas`; on finger press, calculate the touched tile using existing triangle geometry
- [x] Add `hoveredCluster: List<Int>` state to `TribalGridMap` — on each press, call `getNeighbors()` and redraw the touched tile plus its neighbors with a radial amber glow and white outline halo
- [x] Update `GameViewModel.kt` — replace single-tile `applyDivineAction(action)` with `applyDivineAction(action, targetCluster: List<Int>)` that accepts a tile ID list
- [x] Update the action phase in `GameLoop.tick()` — iterate over `targetCluster` tiles and apply stat modifications (moisture ±, volatility ±) to each; population/food effects scale proportionally by the number of occupied tiles in the cluster
- [x] Write unit tests for `getNeighbors()` covering: a center tile, a left-edge tile, a right-edge tile, a corner tile
- [ ] Smoke test: touch a tile on-device; confirm radial halo highlights the correct neighbors; cast Rain, confirm moisture increases across the entire highlighted cluster

### Dashboard UI Overhaul

```
┌─────────────────────────────────┐
│ PROJECT ECHO      ⚡67   T:142  │  ← Divine Favor + Tick (global)
├─────────────────────────────────┤
│                                 │
│                                 │
│           M A P                 │  ← hero; fills available space
│        (fills space)            │
│                                 │
├─────────────────────────────────┤
│ ● The Iron-Wrought          ▸   │  ← legend chip; tap for detail sheet
│   Pop 847  ·  Food 1,204        │
├─────────────────────────────────┤
│  [ Rain ] [Harvest] [Inspire]   │  ← fixed-size square chips
│  [Famine] [Plague ]             │    ⚡cost badge, uniform size always
├─────────────────────────────────┤
│  [ Chronicle ]  [ Manual Tick ] │
└─────────────────────────────────┘
```

- [x] **Top bar** — global-only row: "PROJECT ECHO" left, Divine Favor (⚡icon + number) + Tick counter right; remove Divine Favor from the stats column
- [x] **Map promoted** — `TribalGridMap` fills the available vertical space between the top bar and tribe legend (remove fixed 120dp height); map is the visual centrepiece
- [x] **Tribe legend chip** — replace the plain `displayLarge` tribe name with a `TribeLegendRow`: colored dot + tribe name + inline micro-stats (population · food supply) on one line; tapping the chip opens a tribe detail bottom sheet; row is horizontally scrollable for future multi-tribe support
- [x] **Tribe detail bottom sheet** — shows full per-tribe stats (population, food supply, devotion progress bar, tiles occupied, current `EnvironmentalPhase`); dismissed by swipe
- [x] **Action panel** — replace variable-width `OutlinedButton` labels with fixed-size square chips; shorten labels to one word ("Rain", "Harvest", "Inspire", "Famine", "Plague"); show favor cost as a small ⚡badge; eliminates the oval/circle inconsistency
- [x] Smoke test: dashboard renders correctly at multiple screen sizes; tribe chip opens detail sheet; action chips are uniform; map fills available space

### Simulation Engine Fixes (landed during Phase 9)

- [x] **Population growth guarantee** — `(pop * 1.02).roundToInt()` silently rounded back to the same integer at small populations (e.g. 10 * 1.02 = 10). Growth now guarantees ≥ +1/tick when fed; starvation guarantees ≥ −1/tick when hungry.
- [x] **Fertile soil surplus** — `Fertile.foodMultiplier` raised from 1.0 → 1.5 so Fertile land produces a net food surplus (120 food per 100 people vs. 100 consumed). At the old value, tribes on baseline soil always drained food to zero and could never grow without divine intervention.
- [x] **Territory expansion** — `territoryStep()` only shrank territory (population drop → release tiles). Expansion branch added: when population exceeds current tile count, the tribe claims adjacent unclaimed frontier tiles one at a time per tick.
- [x] **Carrying capacity** — added `TILE_CAPACITY = 10` constant; farming output is now `min(population, tiles × TILE_CAPACITY) × 0.8 × soilMultiplier`. `TILE_CAPACITY` is a per-tile production limit, not a strict population ceiling: at full grid (192 tiles) with Fertile soil (×1.5), output plateaus at `192 × 10 × 0.8 × 1.5 = 2 304` food/tick, which equals consumption at pop ≈ 2 300. Above that, the tribe starves back to equilibrium.

---

## Phase 10 — Dynamic Chronicle (Living Narrative)

> Make every Chronicle entry feel authored, not canned — tribe names in the text, varied flavour per event, and recurring conditions that can speak again.

- [x] **Template substitution in event text** — extend `EventEngine` (or a new `NarrativeResolver`) to replace `{{tribeName}}`, `{{population}}`, `{{foodSupply}}`, and `{{tick}}` placeholders in event text strings at fire time before appending to `eventHistory`
- [x] **Multiple text variants per event** — change `text: String` in `SimEvent` / `events.json` to `texts: List<String>`; at fire time pick one at random; update `EventParser` and all existing events to use the new array format (single-item arrays preserve current behaviour)
- [x] **Optional re-fire with cooldown** — add an optional `cooldownTicks: Int?` field to `SimEvent`; replace the blanket `firedEventIds: Set<String>` block with a `eventCooldowns: Map<String, Long>` map storing the tick the event last fired; an event may re-fire once `worldTimeTick >= lastFiredTick + cooldownTicks` (events without a cooldown remain one-and-done)
- [x] Update `events.json` — add `{{tribeName}}` to at least 5 existing event strings; add 2–3 variant strings to at least 3 high-frequency events (e.g. `famine_warning`, `tribe_grows`, `devotion_surge`); set a `cooldownTicks` on recurring-condition events (`famine_warning`, `faith_wavers`, `divine_power_wanes`)
- [x] **EventEngine stat resolver map** — replace the cascading `if/when` stat branches in `EventEngine.matches()` with a `Map<String, (WorldState) -> Double?>` dispatch table; adding a new triggerable stat becomes one line; unknown stat keys return `null` and log a warning instead of silently skipping
- [x] **Multi-condition triggers** — extend `SimEvent.Trigger` to support an optional `conditions: List<Trigger>` with a `logic: "AND" | "OR"` field alongside the existing single-condition shape; `EventParser` handles both; single-condition events in `events.json` require no changes
- [x] Add 2–3 multi-condition events to `events.json` exercising the new format (e.g. drought + starvation combo, high devotion + abundant food)
- [x] Update `WorldStateEntity` serialization for the new `eventCooldowns` map field
- [x] Write unit tests: template tokens resolve correctly, unknown tokens pass through unchanged, variant selection is within the texts array, cooldown blocks re-fire before expiry and allows it after, one-and-done events (no cooldown) still fire exactly once, AND/OR multi-condition logic resolves correctly, unknown stat key returns null without crashing
- [ ] Smoke test: run a session into starvation; confirm Chronicle shows the tribe's actual name and that `famine_warning` reappears after its cooldown elapses

---

## Phase 11a — Biomes: Model & World Generation

> Give each tile a permanent biome identity; update world generation to produce varied landscapes.

- [x] Add `BiomeType` enum to `domain/model/` with 5 variants and their properties:
  - `Grassland` — moisture baseline 35, full weather effect, standard food (current default behaviour)
  - `Forest` — moisture baseline 45, weather effect at 75%, buffers against Parched
  - `Desert` — moisture baseline 15, weather effect at 50%, HeatWave raises `volatility`
  - `Coast` — moisture baseline 35, full weather effect, flat fishing bonus added to per-tile food contribution
  - `Water` — always `occupantTribeId = null` (impassable); no moisture or food logic
- [x] Add `biome: BiomeType` field to `MapTile` (default `Grassland` for backwards compatibility)
- [x] Update `WorldState.initial()` procedural generation:
  - Stamp 1–2 water body blobs using the existing distance-weighted blob algorithm
  - Mark all land tiles adjacent to `Water` as `Coast`
  - Distribute remaining tiles between `Grassland`, `Forest`, and `Desert` by weighted random seeded from world hash
- [x] Write unit tests: `Water` tiles have `occupantTribeId = null`, coast adjacency marking is correct, biome distribution is seeded and repeatable
- [ ] Smoke test: new world generates visible water bodies and coast tiles; `biome` field present on all tiles

---

## Phase 11b — Biomes: Simulation Integration

> Wire biome properties into the active simulation pipeline and add unit test coverage.

- [x] Update `decayStep()` in `GameLoop` — use each tile's `BiomeType.moistureBaseline` instead of the hardcoded `MOISTURE_BASELINE = 35`
- [x] Update `weatherStep()` — scale moisture delta by `BiomeType.weatherResistance`; any weather front passing a tile raises its `volatility` by a fixed delta (completing the loop: `decayStep` already drains it downward)
- [x] Wire `volatility` as a weather intensity multiplier in `weatherStep()` — high volatility amplifies the moisture delta (`delta * (1 + volatility / 100f)`); creates emergent storms/droughts without a separate stat
- [x] Gate extreme Chronicle events on high volatility (e.g. "A great storm tears through the valley" when volatility > 70 during a RainCloud pass; "The land cracks and bleaches" during a HeatWave)
- [x] Update `EnvironmentalPhase` food contribution in `GameLoop.tick()` — `Coast` tiles add a flat fishing bonus on top of the phase multiplier
- [x] Write unit tests: biome moisture baseline used in decay, weather delta scaled by resistance, Coast fishing bonus applied, volatility amplifies weather delta correctly
- [ ] Smoke test: Desert tiles dry out faster; Forest tiles stay greener; Coast tiles show fishing bonus in food output

---

## Phase 11c — Biomes: UI, Overlays & Action Rework

> Surface biome data visually and rework CastRain to flow through the simulation.

- [ ] Rework `DivineAction.CastRain` — instead of `+50 foodSupply` directly, push `soilMoisture` up on all occupied tiles (makes the action flow through the simulation rather than bypassing it)
- [ ] Update `TribalGridMap` — colour tiles by biome when unoccupied (e.g. deep blue for Water, tan for Desert, dark green for Forest, teal for Coast, keep existing amber/charcoal for occupied/Grassland)
- [ ] Add `MapOverlay` enum to the feature layer (`Default`, `Biome`, `Climate`, `Volatility`); add `overlay: MapOverlay` parameter to `TribalGridMap`:
  - `Default` — current occupancy colouring (amber = occupied, grey = empty)
  - `Biome` — tile coloured by `BiomeType` regardless of occupancy
  - `Climate` — tile coloured on a moisture gradient (red=Parched → blue=Deluge)
  - `Volatility` — greyscale intensity by `volatility` value
- [ ] Add overlay toggle row above the map in `DashboardScreen` (small icon/label buttons; persists in `GameViewModel` as UI state, not `WorldState`)
- [ ] Weather front column outline persists across all overlay modes (positional indicator, not data)
- [ ] Write unit test: CastRain raises `soilMoisture` on occupied tiles instead of adding `foodSupply` directly
- [ ] Smoke test: CastRain visibly shifts tile moisture in Chronicle; overlay toggle switches map colouring correctly

---

## Phase 12 — Future Runway (Placeholders)

> Stubs for the next generation of social and civilizational mechanics. No implementation yet — just defined triggers and expected outputs.

- [ ] **Tribal Splitting**
  - Trigger: tile population density on a single `MapTile` exceeds a configurable threshold
  - Expected output: a new `Tribe` entry is inserted into `WorldState.tribes`; excess population density migrates to an adjacent unoccupied `MapTile` slot; Chronicle logs the schism
  - Status: *placeholder — no implementation*
  - **Design note for multi-tribe:** The carrying capacity system (Phase 9) makes territory the scarce resource — a tribe at its ceiling *must* expand to grow, and expansion stops at another tribe's border. This is the right foundation for conflict. One thing to revisit before implementation: the current territory formula (`expected = population × 192 / 500`) makes territory *follow* population. With carrying capacity, causality is reversed — territory *determines* the population ceiling. At scale the formula always demands more tiles than the grid holds, so `territoryStep` perpetually tries to expand (harmless, just semantically odd). Multi-tribe will likely need territory to be driven by something other than raw population — devotion, strength, or divine favor — so that two tribes compete for finite land rather than each computing an uncapped "expected" tile count independently.

- [ ] **Sophistication Progression**
  - Outline: a `sophisticationLevel: Int` counter on `Tribe` that rises as population and devotion milestones are crossed; higher levels unlock new narrative event categories, new `DivineAction` types, and unique Chronicle entries
  - Status: *placeholder — no implementation*

- [ ] **Skepticism / Defiance System**
  - Outline: a `skepticism: Int` counter on `Tribe` that increments when interventions are too frequent or too dramatic; high skepticism reduces devotion regen rate, eventually triggering defiance events that drain divine favor automatically
  - Status: *placeholder — no implementation*

---

*Each phase is designed to be committed as its own micro-branch and PR. Complete all checkboxes in a phase before beginning the next.*