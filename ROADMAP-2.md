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

- [x] Rework `DivineAction.CastRain` — instead of `+50 foodSupply` directly, push `soilMoisture` up on all occupied tiles (makes the action flow through the simulation rather than bypassing it); also raise `volatility` on those tiles by a small fixed amount — restoring the original divine-overreach mechanic: meddling charges the land so the next natural weather front hits harder than it should
- [x] Update `TribalGridMap` — colour tiles by biome when unoccupied (e.g. deep blue for Water, tan for Desert, dark green for Forest, teal for Coast, keep existing amber/charcoal for occupied/Grassland)
- [x] Add `MapOverlay` enum to the feature layer (`Default`, `Biome`, `Climate`, `Volatility`); add `overlay: MapOverlay` parameter to `TribalGridMap`:
  - `Default` — current occupancy colouring (amber = occupied, grey = empty)
  - `Biome` — tile coloured by `BiomeType` regardless of occupancy
  - `Climate` — tile coloured on a moisture gradient (red=Parched → blue=Deluge)
  - `Volatility` — greyscale intensity by `volatility` value
- [x] Add overlay toggle row above the map in `DashboardScreen` (small icon/label buttons; persists in `GameViewModel` as UI state, not `WorldState`)
- [x] Weather front column outline persists across all overlay modes (positional indicator, not data)
- [x] Write unit test: CastRain raises `soilMoisture` on occupied tiles instead of adding `foodSupply` directly
- [ ] Smoke test: CastRain visibly shifts tile moisture in Chronicle; overlay toggle switches map colouring correctly

---

## Phase 12a — Tribal Splitting

> A dense tribe fractures: a splinter group breaks away, claims the frontier tiles, and from that point the two tribes compete on the same finite grid.

### Territory logic change
The single-tribe `territoryStep` formula (`expected = population × 192 / 500`) makes territory *follow* population. For multi-tribe this is dangerous: a tribe weakened by plague or drought releases tiles via the formula, which the neighbour immediately claims, deepening the starvation spiral. Proper conflict mechanics don't exist yet, so tile-capture must not happen passively.

Fix: suppress the **release** branch of `territoryStep` when `tribes.size > 1`. Territory becomes sticky — tribes keep their land even when weakened. The **expansion** branch stays active so genuinely unclaimed tiles (near water bodies) are still contested organically. Inter-tribe competition in Phase 12a is via population and food dynamics. Tile-capture (raids, battle outcomes) is Phase 12b.

### Checklist
- [x] Add `lastSplitTick: Long = 0L` to `WorldState` (serialized, backward-compatible default)
- [x] Add constants to `GameLoop`: `SPLIT_DENSITY_THRESHOLD = 8`, `SPLIT_MIN_POPULATION = 400`, `SPLIT_COOLDOWN_TICKS = 100L`
- [x] Modify `territoryStep()` — suppress the release branch (`excess > 0`) when `tribes.size > 1`; single-tribe behaviour unchanged
- [x] Add `splitStep(state, random)` to `GameLoop`:
  - Cooldown guard: skip if `worldTimeTick < lastSplitTick + SPLIT_COOLDOWN_TICKS`
  - Per-tribe: skip if pop < `SPLIT_MIN_POPULATION` or density ≤ `SPLIT_DENSITY_THRESHOLD`
  - Partition tiles by distance from centroid: parent keeps near 60%, child gets outer 40%
  - Create child `Tribe` with 40% pop + food, same devotion; generate name via `TribeNameGenerator`
  - Child `tribeId = "${parentId}-${worldTimeTick}"`
  - Update `occupantTribeId` on child's tiles; update parent tribe with reduced pop + food
  - Append Chronicle: `"The ${tribe.name} fractures. The dissenters call themselves ${childName}."`
  - Set `lastSplitTick = worldTimeTick`; return after first split (one split per tick)
- [x] Wire `splitStep` into `tick()` — after `territoryStep`, before the event engine
- [x] Add `TRIBE_COLORS` list to `ui/theme/Color.kt` (6-slot palette; slot 0 = existing Amber)
- [x] Update `TribalGridMap` — add `tribeColors: Map<String, Color>` param; `Default` overlay uses `tribeColors[tile.occupantTribeId] ?: emptyColor` instead of single `occupiedColor`
- [x] Update `DashboardScreen` — compute `tribeColorMap` from `worldState.tribes.keys.sorted()` → palette index; pass to `TribalGridMap` and `TribeLegendChip`; add `tribeColor: Color` param to `TribeLegendChip` for the legend dot
- [x] Write unit tests: split trigger, min-pop guard, density guard, tile + pop + food conservation, cooldown, `territoryStep` release suppressed in multi-tribe
- [ ] Smoke test: simulate to pop ~1300; observe Chronicle schism entry; verify two distinct tile colors and two tribe chips in the legend

---

## Phase 12b — Tribal Personality & Intelligent Expansion

> Give each tribe a persistent personality that shapes where it expands and how it behaves — and that evolves over time as the tribe grows, splits, and is meddled with. Tribes stop being identical agents and start feeling like distinct civilisations.

### Design

Each tribe carries a `TribePersonality` struct with four layers:

1. **Archetype weights** — biome affinities, aggression, caution, skepticismRate, traditionalism. Loaded from `assets/personalities.json` (same pattern as `events.json`). Set at birth, mutated on each split so personalities drift across generations.
2. **Sophistication** — rises with population and devotion milestones. Does not give generic bonuses; instead it *amplifies the archetype*. A sophisticated `agrarian` farms more efficiently (higher effective yield on preferred biomes). A sophisticated `warlike` tribe picks raid targets more precisely (Phase 12c). Sophistication is the tribe getting better at being itself. Long-term this is also the bridge to Option C — at very high sophistication, the weighted-utility expansion logic could be replaced by a trained policy network.
3. **Skepticism** — rises when divine interventions are too frequent or too dramatic. The *rate* is personality-modulated: `warlike` tribes accrue skepticism fast (they resent outside interference); `reclusive` tribes distrust the divine by nature; `maritime` tribes are relatively indifferent to rain. High skepticism reduces devotion regen and eventually triggers defiance Chronicle events.
4. **Generational turnover** — a generation is not measured in ticks but in *cumulative deaths*. When enough of the current population has died and been replaced (tracked via `generationDeaths: Int` on `Tribe`), a generation turns over. On turnover, skepticism and devotion partially revert toward the archetype baseline — the new generation didn't live through what the old one did. How much reverts is controlled by `traditionalism`: a `reclusive` tribe with strong oral tradition passes memories down faithfully; a `nomadic` tribe lives in the present and resets fast. Sophistication is fully retained (accumulated knowledge survives the generation). A Chronicle entry marks each turnover.

All four layers interact. A sophisticated warlike tribe that has gone skeptical is a different narrative from a naive agrarian that has never been touched. A high-traditionalism tribe that has been meddled with will carry that skepticism for many generations; a low-traditionalism tribe forgets quickly.

### Archetype table (`personalities.json`)

Behavioral traits:

| id | aggression | caution | skepticismRate | traditionalism |
| --- | --- | --- | --- | --- |
| `agrarian` | 0.2 | 0.7 | 0.6 | 0.7 |
| `nomadic` | 0.5 | 0.3 | 0.8 | 0.3 |
| `warlike` | 0.9 | 0.2 | 1.4 | 0.4 |
| `maritime` | 0.3 | 0.5 | 0.5 | 0.6 |
| `reclusive` | 0.1 | 0.9 | 1.2 | 0.9 |

Biome affinities:

| id | Forest | Grassland | Desert | Coast |
| --- | --- | --- | --- | --- |
| `agrarian` | 1.5 | 1.2 | 0.3 | 1.0 |
| `nomadic` | 0.7 | 1.0 | 1.4 | 0.8 |
| `warlike` | 0.9 | 1.1 | 0.8 | 0.7 |
| `maritime` | 0.8 | 1.0 | 0.4 | 2.0 |
| `reclusive` | 1.8 | 0.9 | 0.2 | 0.6 |

### Checklist
- [x] Add `TribePersonality.kt` to `domain/model/` — `@Serializable` data class with `archetypeId: String`, `aggression: Float`, `caution: Float`, `skepticismRate: Float`, `traditionalism: Float`, `biomeAffinity: Map<String, Float>`, `sophistication: Int = 0`, `skepticism: Int = 0`; include a `default()` companion for backward-compatible deserialization
- [x] Add `assets/personalities.json` — array of archetype objects matching the tables above; parsed by a new `PersonalityParser` in `domain/rules/` (mirrors `EventParser` pattern)
- [x] Add `personality: TribePersonality = TribePersonality.default()` and `generationDeaths: Int = 0` fields to `Tribe.kt`
- [x] Update `WorldState.initial()` — pick a random archetype from `TribePersonality.ALL_ARCHETYPES` (seeded from world RNG); assign to the starting tribe
- [x] Update `splitStep()` — child inherits parent's archetype weights with each float mutated by `±random(0.0, 0.15)`; clamp scalars to `[0.0, 1.0]`, biome affinities to `[0.1, 3.0]`; child starts with `sophistication = 0`, `skepticism = 0`, `generationDeaths = 0` (clean slate)
- [x] Add **sophistication milestones** in `tick()` — after survival step, check if tribe crossed a population or devotion threshold (pop 500, 1000, 1500; devotion 75, 90); if so increment `sophistication` and append a Chronicle entry; milestone thresholds defined as constant lists
- [x] Wire sophistication into food output — tiles matching the tribe's top biome affinity gain `(1 + sophistication * 0.02)` yield multiplier (caps at `sophistication = 10`)
- [x] Add **skepticism accumulation** in `tick()` — each time a divine action is applied to a tribe, increment `tribe.personality.skepticism` by `(action.favorCost / 10f * personality.skepticismRate).roundToInt()`; clamp to 100
- [x] Wire skepticism into devotion regen — reduce the regen multiplier by `skepticism / 200f` (at max skepticism, regen halved)
- [x] Add **generational drift** in `tick()` — accumulate deaths each tick into `generationDeaths`; when `generationDeaths >= tribe.population / 2`, revert `skepticism` by `skepticism * (1 - traditionalism)`, nudge `devotion` toward 50 by `(1 - traditionalism) * 10`, reset `generationDeaths = 0`, append Chronicle entry
- [x] Add skepticism-driven Chronicle events to `events.json` — `tribal_wariness` triggers at `skepticism > 60`; `tribal_apostasy` at `skepticism > 85`; wired through EventEngine stat resolver
- [x] Update `territoryStep()` expansion scoring — score each frontier tile: `score = personality.affinityFor(tile.biome) * (tile.soilMoisture / 100f)`; claim highest-scoring tiles first
- [x] Expose `archetypeId`, `sophistication`, `skepticism` in `TribeDetailSheet`
- [x] Write unit tests: archetype parsed correctly, default used when absent, expansion prefers high-affinity biomes, child personality mutates within bounds, sophistication milestone increments, skepticism accrues at personality-modulated rate, devotion regen reduced at high skepticism, generational turnover fires at correct death threshold, high-traditionalism tribe retains more skepticism than low-traditionalism tribe after turnover — 162 tests pass
- [ ] Smoke test: two tribes post-split with different archetypes; Biome overlay confirms preferred terrain colonisation; cast divine actions repeatedly and observe skepticism rising; let a tribe starve and recover and observe generational Chronicle entry fire

---

## Phase 12b-2 — Skepticism: Dual-Force Model & Decay

> Skepticism currently only rises (via divine intervention). This phase adds the opposing forces — passive decay and the unanswered-prayer pressure — so the stat becomes a living tension rather than a one-way ratchet.

### Design

Two forces push skepticism **up**, one pulls it **down**:

| Force | Trigger | Direction |
|---|---|---|
| Divine meddling | Player applies an action (already implemented) | ↑ |
| Unanswered prayer | Tribe is devout but ignored | ↑ |
| Passive decay | Every tick, personality-modulated | ↓ |

The intended equilibrium: occasional, measured intervention keeps skepticism stable. Spam → overexposure rises. Ignore a faithful tribe → unanswered-prayer pressure builds. The player finds the middle path.

**Unanswered prayer — pressure accumulator approach**

`devotion - PRAYER_THRESHOLD` (where `PRAYER_THRESHOLD ≈ 60`) represents how intensely the tribe is praying that tick. Add this to a running float `prayerPressure` on `Tribe`. When `prayerPressure` crosses `PRAYER_PRESSURE_CAP`, convert to `skepticism += 1`, reset `prayerPressure = 0f`, and append a Chronicle entry (e.g. *"The prayers of {{tribeName}} go unanswered. Doubt spreads among the faithful."*). This avoids the "every-tick" sensitivity problem — at devotion 70 (pressure 10/tick), skepticism gains 1 roughly every 20 ticks; at devotion 100 (pressure 40/tick), every 5 ticks.

Accumulation rules:
- Only increments when `tribe.devotion > PRAYER_THRESHOLD` **and** no divine action was applied to this tribe this tick.
- **Freezes** (neither grows nor drains) when devotion drops below `PRAYER_THRESHOLD` mid-accumulation — the backlog is banked, not erased.
- **Carries through generational turnover** — the grievance lives in the oral tradition.

When a divine action **is** applied: halve `prayerPressure` on the targeted tribe (`prayerPressure *= 0.5f`). A partial answer eases the backlog but doesn't erase it. *(Future enhancement: scale the halving by action relevance to current tribal need — e.g. CastRain on a Parched tribe resets more pressure than a Plague cast elsewhere. Requires explicit tribe-level need states; out of scope for 12c-2.)*

> **Open question — tick granularity:** A tick is intentionally vague (could be days, weeks, or seasons). Exact thresholds (`PRAYER_THRESHOLD`, `PRAYER_PRESSURE_CAP`) and decay intervals should be tuned against observed session feel rather than locked in upfront.

**Passive decay — personality-modulated interval**

Rather than −1 every tick (too fast), decay fires every `SKEPTICISM_DECAY_BASE * skepticismRate` ticks, where `SKEPTICISM_DECAY_BASE ≈ 10`. Tribes with low `skepticismRate` (agrarian 0.6, maritime 0.5) forget quickly — decay fires ~every 6–7 ticks. Warlike (1.4) and reclusive (1.2) hold grudges — decay fires ~every 14 ticks. Implementation: `skepticismDecayBuffer += 1f / tribe.personality.skepticismRate` each tick; when buffer ≥ `SKEPTICISM_DECAY_BASE`, decrement skepticism and reset buffer (mirrors prayerPressure pattern).

### Checklist
- [x] Add `prayerPressure: Float = 0f` and `skepticismDecayBuffer: Float = 0f` to `Tribe` (both serialized, backward-compatible defaults)
- [x] In `applyDivineAction`: halve `prayerPressure` on the targeted tribe (`tribe.prayerPressure *= 0.5f`) — partial answer, not full reset. Until Phase 12c adds `targetTribeId`, apply to **all** tribes as a temporary simplification (god acted; everyone feels it)
- [x] In `tick()`, after survival phase, if `tribe.devotion > PRAYER_THRESHOLD` and no divine action was applied to this tribe this tick: `prayerPressure += (tribe.devotion - PRAYER_THRESHOLD).toFloat()`; if devotion ≤ `PRAYER_THRESHOLD`, leave `prayerPressure` unchanged (freeze); when `prayerPressure >= PRAYER_PRESSURE_CAP`: `skepticism = min(100, skepticism + 1)`, `prayerPressure = 0f`, append Chronicle entry
- [x] In `tick()`, accumulate `skepticismDecayBuffer += 1f / tribe.personality.skepticismRate` each tick; when buffer ≥ `SKEPTICISM_DECAY_BASE`: `skepticism = max(0, skepticism - 1)`, reset buffer
- [x] Tune `PRAYER_THRESHOLD`, `PRAYER_PRESSURE_CAP`, and `SKEPTICISM_DECAY_BASE` against a live session; document chosen values as named constants in `GameLoop.kt`
- [x] Add unanswered-prayer Chronicle event text to `events.json` (or inline in `GameLoop` if one-off) — e.g. *"The prayers of {{tribeName}} go unanswered. Doubt spreads among the faithful."*
- [x] Write unit tests: prayerPressure accumulates only when devout and no action applied, freezes when devotion drops below threshold, halves on divine action, converts correctly at cap with Chronicle appended, skepticism decays faster for low-skepticismRate archetypes, prayerPressure survives generational turnover unchanged — 172 tests pass
- [ ] Smoke test: leave a high-devotion tribe unattended; observe skepticism climbing in Chronicle with "unanswered prayer" entries; intervene occasionally; observe pressure halving and stabilisation

---

## Phase 12c — Tribal Conflict & Raids

> Tribes with contested borders and high aggression initiate raids. Successful raids transfer tiles and generate Chronicle drama. This is when the map becomes truly contested.

### Design

The `aggression` weight from Phase 12b drives raid initiation probability. Border tiles — tiles owned by tribe A adjacent to tiles owned by tribe B — are conflict candidates. Each tick, for each pair of neighbouring tribes, a raid roll is made weighted by the aggressor's `aggression` and the defender's `caution`. On success, one border tile transfers. This replaces the current sticky-territory workaround from Phase 12a.

- [x] **Border detection** — utility function `getBorderTiles(tileId, tiles): List<Int>` returning tiles owned by a different tribe adjacent to the given tile; add to `TileNeighbors.kt`
- [x] **Raid resolution** — new `conflictStep(state, random)` in `GameLoop.kt` after `territoryStep`; for each pair of neighbouring tribes, roll `random.nextFloat() < aggressor.personality.aggression * (1 - defender.personality.caution)`; on success transfer one contested tile, append Chronicle entry (e.g., *"The Ironborn raid the Ashwood frontier."*)
- [x] **`territoryStep` release restored** — once conflict can transfer tiles, the Phase 12a sticky-territory suppression can be removed; weakened tribes now lose territory to neighbours organically rather than to the unclaimed pool
- [x] **Per-tribe divine targeting** — add `targetTribeId: String?` to `applyDivineAction` so `SendPlague` and `InspireDevout` can be directed at a specific tribe; UI: tapping a tribe's legend chip before pressing an action sets the target; also replaces the Phase 12b-2 "halve all tribes" simplification — `prayerPressure *= 0.5f` now applies only to the targeted tribe
- [x] Write unit tests: border tile detection correct, raid roll fires only between neighbours, tile transfer updates `occupantTribeId`, Chronicle entry generated on raid — 182 tests pass
- [ ] Smoke test: two tribes share a border; observe raid entries in Chronicle; map tiles change colour at the contested edge

---

## Phase 13+ — Future Runway (Placeholders)

> Long-range ideas. No design work started.

- [ ] **Tribe mergers / vassalage** — the reverse of splitting: a weakened tribe absorbed by a dominant neighbour, Chronicle entry, territory transfer
- [ ] **Option C — Reinforcement Learning agent** *(future research track)* — replace the weighted-utility personality with a small policy network trained via Q-learning; each tick is a step, reward signal is population growth or territory size; requires a headless fast-forward simulation mode for training convergence (~10 000+ ticks); TensorFlow Lite for on-device inference; revisit after Phase 12c conflict mechanics give the reward signal meaning

---

*Each phase is designed to be committed as its own micro-branch and PR. Complete all checkboxes in a phase before beginning the next.*