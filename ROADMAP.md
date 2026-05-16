# Project Echo — Development Roadmap

A lightweight, text-and-UI-driven God Simulator built natively in Android with pure Kotlin and Jetpack Compose. All simulation math lives in the domain layer; all UI is stateless.

---

## Phase 1: Core Loop & Architecture ✅ COMPLETED

> Foundation: pure-Kotlin simulation engine + reactive state bridge + stateless Compose UI.

- [x] `WorldState` data class (`worldTimeTick`, `divineFavor`, `tribe`)
- [x] `Tribe` data class (`name`, `population`, `devotion`, `foodSupply`)
- [x] `GameLoop.tick(currentState: WorldState): WorldState` — pure function with starvation / thriving / equilibrium branches
- [x] `GameViewModel` — `StateFlow<WorldState>` with auto-advancing 2-second coroutine loop and manual `triggerTick()`
- [x] `DashboardScreen` — fully stateless Composable; accepts `WorldState` + `onTickPressed` callback, renders nothing else
- [x] `MainActivity` — wires ViewModel → Composable state via `collectAsState()`
- [x] Unit tests: thriving scenario + starvation scenario in `GameLoopTest`

---

## Phase 2: Divine Interventions ✅ COMPLETED

> Player agency: a sealed-class action system lets the player inject miracles directly into the simulation tick.

**Domain Layer (`/domain`)**
- [x] Define `DivineAction` sealed class in `domain/model/DivineAction.kt` with subtypes:
  - `CastRain` — replenishes food supply
  - `SendPlague` — reduces population by a fixed percentage
  - `InspireDevout` — boosts tribe devotion
  - `CauseFamine` — drains food supply sharply
  - `BlessHarvest` — large, one-time food stockpile bonus
- [x] Each subtype carries a `favorCost: Int` property (spending `divineFavor` as a resource)
- [x] Extend `GameLoop.tick()` signature: `tick(currentState: WorldState, action: DivineAction? = null): WorldState`
- [x] Implement action-resolution logic inside `tick()` before the normal food/population step
- [x] Clamp `divineFavor` to [0, 100] after action cost is deducted
- [x] Unit tests: one test per `DivineAction` subtype covering stat deltas and favor deduction

**Feature/ViewModel Layer (`/feature`)**
- [x] Add `applyDivineAction(action: DivineAction)` to `GameViewModel` — sets a pending action consumed on the next tick
- [x] Pass action down from `GameViewModel.triggerTick()` into `GameLoop.tick()`

**UI Layer (`/feature/dashboard`)**
- [x] Add a row of action buttons to `DashboardScreen` (one per `DivineAction` subtype)
- [x] Each button calls a stateless `onActionPressed: (DivineAction) -> Unit` callback — no state in the Composable
- [x] Disable buttons when `divineFavor` is insufficient (derived from `worldState`)

---

## Phase 3: The Data-Driven Event Engine

> Reactive narrative: a JSON-backed parser triggers contextual text events into a scrollable history ledger based on live tribe stats.

**JSON Schema & Assets**
- [ ] Design `SimEvent` schema: `{ "id", "trigger": { "stat", "operator", "threshold" }, "text", "effect": { "stat", "delta" } }`
- [ ] Create `app/src/main/assets/events.json` with an initial set of at least 10 events (famine warnings, devotion surges, population milestones, etc.)

**Domain Layer (`/domain`)**
- [ ] Define `SimEvent` data class in `domain/model/SimEvent.kt` mirroring the JSON schema
- [ ] Build `EventParser` in `domain/rules/EventParser.kt` — reads raw JSON string, returns `List<SimEvent>` (pure Kotlin, no Android imports)
- [ ] Build `EventEngine` in `domain/rules/EventEngine.kt` — evaluates each event's trigger condition against a `WorldState` and returns triggered event texts
- [ ] Add `eventHistory: List<String>` field to `WorldState`
- [ ] Call `EventEngine.evaluate(state, events)` inside `GameLoop.tick()` and append results to `eventHistory`
- [ ] Unit tests: `EventParser` round-trip test + `EventEngine` trigger evaluation tests

**Feature/ViewModel Layer (`/feature`)**
- [ ] Load `events.json` from `assets/` in `GameViewModel` (using `context`) and pass parsed list to `GameLoop`
- [ ] `GameViewModel` constructor accepts `Application` context (switch to `AndroidViewModel`)

**UI Layer (`/feature/dashboard`)**
- [ ] Add a `HistoryLedger` Composable to `DashboardScreen` — a `LazyColumn` rendering `eventHistory` entries in reverse-chronological order
- [ ] Auto-scroll ledger to the newest entry on each state update

---

## Phase 4: Local Persistence

> Seamless save: Room SQLite auto-saves a serialized `WorldState` snapshot so the player never loses progress when the app backgrounds.

**Dependencies**
- [ ] Add `androidx.room:room-runtime`, `androidx.room:room-ktx`, and `androidx.room:room-compiler` (KSP) to `app/build.gradle.kts`
- [ ] Add `org.jetbrains.kotlinx:kotlinx-serialization-json` for `WorldState` ↔ JSON conversion

**Domain Layer (`/domain`)**
- [ ] Define `WorldStateRepository` interface in `domain/` — `suspend fun save(state: WorldState)` and `suspend fun load(): WorldState?`

**Data/Persistence Layer (`/data`)**
- [ ] Create `WorldStateEntity` Room `@Entity` with a single-row primary key and a `stateJson: String` column
- [ ] Define `WorldStateDao` with `@Upsert` and `@Query("SELECT * FROM world_state LIMIT 1")`
- [ ] Build `AppDatabase` (`@Database`) in `data/db/AppDatabase.kt`
- [ ] Implement `RoomWorldStateRepository` satisfying the `WorldStateRepository` interface — serializes `WorldState` to/from JSON

**Feature/ViewModel Layer (`/feature`)**
- [ ] Inject `WorldStateRepository` into `GameViewModel`
- [ ] On init: call `repository.load()` on the IO dispatcher; use saved state if present, else default
- [ ] After every `triggerTick()`: call `repository.save(newState)` on the IO dispatcher (fire-and-forget, non-blocking)
- [ ] Handle `onCleared()` to flush any pending save

---

## Phase 5: Minimalist Vector UI

> Immersive dashboard: a clean geometric Compose UI with a scrolling history ledger and a canvas-drawn tribal grid map.

**Dashboard Layout Redesign**
- [ ] Replace flat stat list with a two-panel layout: stats panel (top) + history ledger (bottom, scrollable)
- [ ] Add `LinearProgressIndicator` bars for `devotion` and `divineFavor` beneath their numeric values
- [ ] Group Divine Intervention buttons into a visually distinct `ActionPanel` Composable
- [ ] Apply consistent 16 dp / 24 dp spacing grid throughout

**History Ledger**
- [ ] Extract `HistoryLedger` into its own file `feature/dashboard/HistoryLedger.kt`
- [ ] Style entries with monospace / serif typography to feel like ancient records
- [ ] Animate new entries fading in with `AnimatedVisibility`

**Tribal Grid Map (Canvas)**
- [ ] Create `TribalGridMap` Composable in `feature/dashboard/TribalGridMap.kt` using `Canvas`
- [ ] Draw a geometric hex or square grid; shade cells by population density derived from `WorldState`
- [ ] No bitmaps — pure vector shapes and `drawPath` calls only

**Color & Typography**
- [ ] Expand `Color.kt` with a dedicated dark-mode palette (deep charcoal background, amber/gold accents)
- [ ] Update `Type.kt` with at least three type scales: display (tribe name), body (stats), caption (ledger entries)
- [ ] Set `useDarkTheme = true` as default in `Theme.kt` to match the game's tone

**Polish**
- [ ] Ensure all Composables pass Compose preview annotations for rapid iteration in Android Studio
- [ ] Verify no `LocalContext` or Android references leak into domain-layer calls from UI
- [ ] Smoke-test on API 28 (minSdk) emulator for compatibility
