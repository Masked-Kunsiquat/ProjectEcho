# Project Echo

A minimalist god simulator for Android. You are a divine force watching over **The Iron-Wrought** — a tribe struggling to survive. Every two seconds the world ticks forward: crops grow or fail, devotion rises or crumbles, and your people live or die. Spend divine favor to intervene, read the Chronicle for signs, and keep your civilization from extinction.

Built natively with Kotlin and Jetpack Compose. No game engine.

---

## Screenshots

> _Add screenshots here once you have them._

---

## Gameplay

Each tick the simulation resolves in three phases:

1. **Divine action** — if you spent favor, it takes effect first
2. **Survival** — the tribe farms 80% of what it consumes; shortfall causes starvation, surplus causes growth
3. **Events** — scripted narrative events fire once when their trigger condition is met

### Stats

| Stat | Range | Effect |
|---|---|---|
| Population | 0 → ∞ | Grows ~2 % per tick when fed; shrinks ~5 % when starving |
| Food Supply | 0 → ∞ | Consumed at 1 unit per person per tick; tribe farms back 80 % |
| Devotion | 0–100 | +1 when thriving, −3 when starving; drives favor regeneration |
| Divine Favor | 0–100 | Passive regen = `devotion × 3 / 100` per tick; spent on interventions |

### Divine Interventions

| Action | Cost | Effect |
|---|---|---|
| Cast Rain | 10 favor | +50 food |
| Inspire Devout | 8 favor | +15 devotion (cap 100) |
| Cause Famine | 5 favor | −80 food (floor 0) |
| Send Plague | 15 favor | −20 % population |
| Bless Harvest | 20 favor | +200 food |

### Chronicle

13 one-time narrative events are scripted in `assets/events.json`. Each fires exactly once when its condition is met (e.g. population ≥ 200, food < 50, devotion < 5). New events appear as a snackbar and are preserved in the Chronicle bottom sheet.

---

## Architecture

The project enforces a hard boundary between simulation logic and the Android OS.

```
app/
└── domain/          ← Pure Kotlin. Zero Android imports.
│   ├── model/       WorldState, Tribe, DivineAction, SimEvent
│   ├── rules/       GameLoop, EventEngine, EventParser
│   └── repository/  WorldStateRepository (interface)
├── data/            ← Android/Room implementation
│   ├── db/          AppDatabase, WorldStateDao, WorldStateEntity
│   └── repository/  RoomWorldStateRepository
├── feature/
│   └── dashboard/   GameViewModel, DashboardScreen, HistoryLedger, TribalGridMap
└── ui/theme/        Color, Type, Theme
```

**Domain layer** — `GameLoop.tick()` is a pure function: `(WorldState, DivineAction?, List<SimEvent>) → WorldState`. No coroutines, no Android context, fully unit-testable in isolation.

**Feature layer** — `GameViewModel` owns a `StateFlow<WorldState>`, advances it every 2 seconds via a coroutine loop, and fire-and-forgets saves to Room after each tick.

**UI layer** — All Composables are stateless. `DashboardScreen` receives `WorldState` and callbacks; it renders and nothing else. The only local state in the UI is animation infrastructure (`Animatable`, scroll state, sheet visibility hoisted to `MainActivity`).

---

## Persistence

World state is saved to a single-row Room SQLite table after every tick. `WorldState` is serialized to JSON with `kotlinx.serialization` before storage and deserialized on app launch, so progress survives backgrounding and process death.

---

## UI

The dashboard is a single screen with a dark charcoal/amber palette:

- **Header** — app title and current tick counter
- **Tribe name** — large serif display font
- **Stats** — Population and Food as plain rows; Devotion and Divine Favor as labelled progress bars
- **Divine Interventions** — five amber `OutlinedButton`s in a 3 + 2 grid inside a Surface card; disabled when favor is insufficient
- **Tribal Grid Map** — a 16 × 6 Canvas grid of 192 triangles; alternating ╲/╱ diagonal splits produce a herringbone texture; amber cells represent claimed territory spreading outward from a tribe-seeded homeland as population grows toward 500
- **Footer** — Chronicle button (opens `ModalBottomSheet` with full event history) and Manual Tick button

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

---

## Project Structure

```
domain/model/
  WorldState.kt        Serializable game snapshot (tick, favor, tribe, history)
  Tribe.kt             Civilization stats (name, population, devotion, food)
  DivineAction.kt      Sealed class — 5 player interventions with favor costs
  SimEvent.kt          JSON-backed event: trigger condition + narrative text + optional stat effect

domain/rules/
  GameLoop.kt          Pure tick function — action resolution → survival → event evaluation
  EventEngine.kt       Evaluates trigger conditions against a WorldState
  EventParser.kt       JSON → List<SimEvent> via kotlinx.serialization

domain/repository/
  WorldStateRepository.kt    save / load interface

data/db/
  AppDatabase.kt        Room singleton
  WorldStateEntity.kt   Single-row entity (id=0, stateJson: String)
  WorldStateDao.kt      @Upsert + SELECT LIMIT 1

data/repository/
  RoomWorldStateRepository.kt    Serializes WorldState ↔ JSON for storage

feature/dashboard/
  GameViewModel.kt         StateFlow owner; 2-second auto-tick loop; save-on-tick
  GameViewModelFactory.kt  Injects WorldStateRepository
  DashboardScreen.kt       Root stateless Composable
  HistoryLedger.kt         LazyColumn with per-entry Animatable fade-in
  TribalGridMap.kt         Canvas 16×6 triangle grid; blob territory spread

ui/theme/
  Color.kt    EchoBackground (#0F0F0F), EchoAmber (#FFB300), EchoGold (#FFD54F) + supporting tokens
  Type.kt     displayLarge (Serif 28sp), bodySmall (Monospace 12sp), titleMedium (Serif 16sp) + 3 more
  Theme.kt    ProjectEchoTheme — hard-coded dark Material3 scheme

assets/
  events.json    13 one-time narrative events (JSON array of SimEvent objects)
```

---

## Building

Open in Android Studio (Ladybug or newer) and run on any device or emulator running Android 9+. No API keys or external services required.

```bash
./gradlew assembleDebug
```
