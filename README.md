# Project Echo

A minimalist god simulator for Android. You are a divine force watching over a tribe struggling to survive on a living, tile-based world. Every two seconds the world ticks forward: crops grow or fail, tribes fracture and war, devotion rises or crumbles, and civilizations live or die. Spend divine favor to intervene, read the Chronicle for signs, and shepherd your people across a contested grid.

Built natively with Kotlin and Jetpack Compose. No game engine.

---

## Screenshots

> _Add screenshots here once you have them._

---

## Gameplay

Each tick the simulation resolves in order:

1. **Divine action** — targeted intervention takes effect (directed at a specific tribe or all tribes)
2. **Survival** — each tribe farms its territory; shortfall causes starvation, surplus causes growth
3. **Territory** — tribes expand into frontier tiles scored by biome affinity; weakened tribes lose tiles to raids
4. **Split check** — a dense tribe fractures into two, each inheriting territory, population, and a mutated personality
5. **Conflict** — neighboring tribes with high aggression raid contested border tiles
6. **Events** — narrative events fire when their trigger condition is met; recurring events refire after their cooldown

### Stats

| Stat | Range | Effect |
|---|---|---|
| Population | 0 → ∞ | Guaranteed ≥ +1/tick when fed; ≥ −1/tick when starving |
| Food Supply | 0 → ∞ | Consumed 1 unit/person/tick; produced as `min(pop, tiles × 10) × 0.8 × phaseMultiplier × sophisticationBonus` |
| Devotion | 0–100 | +1 when thriving, −3 when starving; drives favor regen; reduced by skepticism |
| Divine Favor | 0–100 | Passive regen = `devotion × 3 / 100` per tick; spent on interventions |
| Soil Moisture | 0–100 per tile | Determines `EnvironmentalPhase`; decays toward biome baseline each tick; amplified by weather fronts |
| Volatility | 0–100 per tile | Amplifies weather moisture delta; accumulates during storms, decays each tick |
| Sophistication | 0–10 per tribe | Rises at population/devotion milestones; multiplies food yield on preferred biomes (`+2 %` per point) |
| Skepticism | 0–100 per tribe | Rises from divine meddling and unanswered prayer; decays passively; reduces devotion regen at high values |

### Environmental Phases

Each tile's `soilMoisture` determines its food output multiplier:

| Phase | Moisture | Food Multiplier |
|---|---|---|
| Deluge | 81–100 | Starvation penalty + population casualties |
| Saturated | 51–80 | ×0.5 |
| Fertile | 21–50 | ×1.5 |
| Parched | 0–20 | ×0.1 |

### Divine Interventions

All actions spend favor. Actions can be targeted at a specific tribe (tap a legend chip to select) or applied globally.

| Action | Cost | Effect |
|---|---|---|
| Cast Rain | 10 favor | +25 soil moisture on occupied tiles; +10 volatility per tile (divine overreach — charges the land) |
| Inspire Devout | 8 favor | +15 devotion (cap 100) |
| Cause Famine | 5 favor | −80 food (floor 0) |
| Send Plague | 15 favor | −20 % population |
| Bless Harvest | 20 favor | +200 food |

Applying any action halves `prayerPressure` on the targeted tribe — a partial answer to their prayers.

### Tribal Personalities

Each tribe carries a persistent `TribePersonality` with five archetypes loaded from `assets/personalities.json`:

| Archetype | Aggression | Caution | Skepticism Rate | Traditionalism |
|---|---|---|---|---|
| Agrarian | 0.2 | 0.7 | 0.6 | 0.7 |
| Nomadic | 0.5 | 0.3 | 0.8 | 0.3 |
| Warlike | 0.9 | 0.2 | 1.4 | 0.4 |
| Maritime | 0.3 | 0.5 | 0.5 | 0.6 |
| Reclusive | 0.1 | 0.9 | 1.2 | 0.9 |

- **Biome affinity** — each archetype prefers certain biomes; expansion scoring = `affinity × soilMoisture / 100`; sophisticated tribes get a yield multiplier on their top biomes
- **Skepticism** — rises each time you intervene (`favorCost / 10 × skepticismRate`), and from unanswered prayer (devotion > 60 with no divine action → `prayerPressure` accumulates; at cap, skepticism +1)
- **Generational turnover** — when cumulative deaths ≥ population / 2, the generation resets; skepticism partially reverts (amount controlled by `traditionalism`); Chronicle entry fires
- **Splitting** — a child tribe inherits the parent's archetype with each float mutated by ±0–0.15; starts with fresh sophistication and skepticism

### Chronicle

Narrative events are scripted in `assets/events.json`. Features:
- **Template substitution** — `{{tribeName}}`, `{{population}}`, `{{foodSupply}}`, `{{tick}}` resolve at fire time
- **Variant selection** — events with multiple `texts` entries pick one at random each firing
- **Cooldowns** — recurring events (famine warnings, faith crises) refire after their `cooldownTicks` elapses; one-and-done events fire exactly once
- **Multi-condition triggers** — `AND` / `OR` compound conditions (e.g. drought + starvation; high devotion + abundant food)

---

## Architecture

The project enforces a hard boundary between simulation logic and the Android OS.

```
app/
└── domain/          ← Pure Kotlin. Zero Android imports.
│   ├── model/       WorldState, Tribe, TribePersonality, DivineAction,
│   │                SimEvent, MapTile, BiomeType, EnvironmentalPhase,
│   │                WeatherFront, WeatherType
│   ├── rules/       GameLoop, EventEngine, EventParser,
│   │                PersonalityParser, TileNeighbors, NarrativeResolver
│   └── repository/  WorldStateRepository (interface)
├── data/            ← Android/Room implementation
│   ├── db/          AppDatabase, WorldStateDao, WorldStateEntity
│   └── repository/  RoomWorldStateRepository
├── feature/
│   └── dashboard/   GameViewModel, DashboardScreen, TribalGridMap,
│                    MapOverlay
└── ui/theme/        Color (TRIBE_COLORS palette), Type, Theme
```

**Domain layer** — `GameLoop.tick()` is a pure function: `(WorldState, DivineAction?, List<SimEvent>, List<TribePersonality>, String?, Random) → WorldState`. No coroutines, no Android context, fully unit-testable.

**Feature layer** — `GameViewModel` owns a `StateFlow<WorldState>`, advances it every 2 seconds via a coroutine loop, and saves to Room after each tick. Holds UI-only state (`mapOverlay`, `pendingTribeTarget`) separately from `WorldState`.

**UI layer** — All Composables are stateless. `DashboardScreen` receives `WorldState` and callbacks; it renders and nothing else.

---

## Map & Biomes

The world is a **16 × 6 Canvas grid** of 192 herringbone triangles (alternating ╲/╱ diagonal pairs). Each `MapTile` has an `id`, position, `soilMoisture`, `volatility`, `occupantTribeId`, and `biome`.

### Biome Types

| Biome | Moisture Baseline | Weather Resistance | Notes |
|---|---|---|---|
| Grassland | 35 | 100 % | Standard terrain |
| Forest | 45 | 75 % | Buffers against drought |
| Desert | 15 | 50 % | Dries out fast |
| Coast | 35 | 100 % | Flat fishing bonus per occupied tile |
| Water | — | — | Impassable; never claimed |

Water bodies and coastlines are generated procedurally at world creation. Remaining land tiles are distributed by weighted random seeded from the world hash.

### Weather Fronts

`RainCloud` and `HeatWave` fronts spawn on edge columns and cross the grid over time:
- **RainCloud** — +15 moisture/tick to current column (scaled by `weatherResistance × (1 + volatility/100)`)
- **HeatWave** — −12 moisture/tick (same scaling)
- Each passing tile gains +5 volatility; volatility amplifies the next front's impact
- Extreme Chronicle events fire when column volatility exceeds 70

### Map Overlays

Four overlay modes toggle above the map:

| Overlay | Tile Color |
|---|---|
| Default | Tribe color (occupied) / charcoal (empty); one swatch per tribe in legend |
| Biome | Biome identity regardless of occupancy |
| Climate | Moisture gradient (red → green → blue) |
| Volatility | Greyscale intensity |

Weather front column outline persists across all overlays.

---

## Tribal Conflict

Once two tribes exist, `conflictStep()` runs each tick after `territoryStep`:
- For each pair of neighboring tribes, a raid rolls `random < aggressor.aggression × (1 − defender.caution)`
- Success transfers one contested border tile; a Chronicle entry records the raid
- Territory is no longer sticky — weakened tribes organically lose ground to aggressive neighbors

---

## Persistence

World state is saved to a single-row Room SQLite table after every tick. `WorldState` is serialized to JSON with `kotlinx.serialization`. All new fields use backward-compatible defaults so old saves load without migration.

---

## UI

The dashboard is a single screen with a dark charcoal/amber palette:

- **Top bar** — "PROJECT ECHO" left; ⚡Favor + T:tick counter right
- **Tribal Grid Map** — fills available vertical space; herringbone Canvas grid with per-tribe colors; overlay toggle row above
- **Tribe legend** — horizontally scrollable chips (colored dot + name + Pop · Food micro-stats); tap chip to select a target tribe; ▸ to open detail sheet
- **Tribe detail sheet** — population, food supply, devotion bar, tiles occupied, environmental phase, archetype, sophistication, skepticism
- **Action panel** — fixed-size square chips with one-word labels and ⚡cost badge; disabled when favor is insufficient
- **Footer** — Chronicle button (full event history) and Manual Tick button

---

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| State | `StateFlow` + `ViewModel` |
| Persistence | Room 2.7.0 (KSP) |
| Serialization | kotlinx.serialization 1.8.1 |
| Async | Kotlin Coroutines 1.10.1 |
| Min SDK | 28 (Android 9) |
| Target SDK | 36 |
| Language | Kotlin 2.2.10 |

182 unit tests cover the domain layer. The pure-Kotlin domain boundary means every simulation rule is testable without an Android emulator.

---

## Building

Open in Android Studio (Ladybug or newer) and run on any device or emulator running Android 9+. No API keys or external services required.

```bash
./gradlew assembleDebug
```
