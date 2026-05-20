# ProjectEcho — Roadmap V3: RL Readiness

## Status

**V2 Complete (Phases 1–12c):** Spatially-aware multi-tribe simulation with personality, splitting, raids, biomes, prayer pressure, and per-tribe divine targeting. 182 unit tests pass. Architecture is already RL-ready in spirit — `tick()` is a pure function, domain layer has zero Android imports, all state is serializable.

**V3 Goal:** Ship a trained on-device RL policy (PPO, TFLite) that replaces the heuristic expand/raid logic. Everything in this roadmap builds toward that — enriching the simulation so the training environment is worth learning from, adding the data model fields the policy network needs, building the headless/policy infrastructure required before Python training can start, and running the training pipeline itself.

Phase numbering continues from ROADMAP-2 (1–12c).

---

## Block 1 — Simulation Depth

> Make the simulation rich enough that the reward signal is worth training against. These phases improve existing mechanics — they don't add new stored fields and they're all unit-testable.

---

## Phase 13 — InspireDevout Realignment & Devotion Behavioral Hooks

> `InspireDevout` is the odd action out: every other divine intervention has a plausible natural explanation (rainstorm, disease, famine), but a +15 devotion spike has no natural analog. The tribe knows something divine happened — which means the current blanket skepticism formula is backwards for this action specifically. This phase fixes the mechanical contradiction and gives devotion actual influence over tribal behavior.

### InspireDevout skepticism fix

- [x] In `GameLoop.kt`, locate the `skepGain` calculation applied after every divine action; add a guard so `InspireDevout` is exempt from the formula entirely (or optionally inverts it: small `skepticism -= 1`, clamped at 0)
- [x] Add a Chronicle event to `events.json` that fires when devotion crosses 60 after an InspireDevout action — e.g. *"The {{tribeName}} prays with renewed fervour. Their expectations of the divine have grown."* This surfaces the prayer-pressure paradox: inspiring faith raises expectations the player may not meet
- [x] Update unit tests in `SkepticismDualForceTest.kt` — assert that applying `InspireDevout` does not increase skepticism; update any test that assumed blanket skepticism gain applies to all actions

### Devotion behavioral hooks

Currently devotion affects only divine favor regen and the prayer threshold. Give it actual influence over tribal decisions:

- [x] In `conflictStep()`: multiply raid threshold by `(1f - tribe.devotion / 200f)` for the aggressor — a tribe deep in prayer is less likely to initiate raids (at devotion 100: ~50% raid suppression; at devotion 0: no effect)
- [x] In `splitStep()`: add a devotion guard — skip split if the candidate tribe's devotion exceeds a threshold constant (e.g. `SPLIT_DEVOTION_CAP = 80`); spiritual cohesion holds the community together
- [x] Document both constants in `GameLoop.kt` with names (`RAID_DEVOTION_SUPPRESSION_DIVISOR`, `SPLIT_DEVOTION_CAP`)
- [x] Write unit tests: high-devotion tribe fails raid roll more often (seed-controlled), split blocked above cap

---

## Phase 14 — Sophistication Depth: Military Doctrine & Faith Amplification

> Sophistication currently only affects food yield via the biome multiplier. A sophistication-10 warlike tribe raids identically to a sophistication-0 one — which means the stat has no effect on the conflict system at all. This phase wires it into raids and into the long-run evolution of a tribe's relationship with faith.

### Military doctrine

- [x] In `conflictStep()`, replace the bare `aggression * (1 - caution)` threshold with a formula that includes sophistication:
  ```kotlin
  attackBonus  = 1f + aggressor.personality.sophistication * 0.04f   // up to +40% at soph 10
  defenseBonus = 1f - defender.personality.sophistication * 0.03f    // up to -30% at soph 10
  threshold = aggressor.personality.aggression *
              (1f - defender.personality.caution) *
              attackBonus * defenseBonus
  ```
- [x] The existing archetype routing remains unchanged — this bonus matters more for a warlike tribe (high base aggression) than a reclusive one (low base)
- [x] Write unit tests: high-sophistication aggressor succeeds more raids on same seed; high-sophistication defender loses fewer tiles on same seed

### Faith amplification

The current model treats sophistication as always drifting toward secularism. Historically that's wrong: what correlates with secularism is *security and predictability*, not advancement itself. The real dial is `traditionalism`.

- [x] In `tick()`, at the sophistication milestone block (where `sophistication` is incremented): after incrementing, update `skepticismRate`:
  ```kotlin
  val faithDrift = (1f - tribe.personality.traditionalism) * 0.05f
  // clamp skepticismRate to [0f, 2f]
  ```
  At traditionalism 0.9 (reclusive/agrarian): drift ≈ +0.005 per milestone — nearly stable.
  At traditionalism 0.1 (nomadic): drift ≈ +0.045 per milestone — a restless tribe grows progressively quicker to doubt.
- [x] Because `traditionalism` is a personality field (on `TribePersonality`), `skepticismRate` drift should update `tribe.personality` via `.copy()`; no new stored fields needed
- [x] Write unit tests: low-traditionalism tribe's `skepticismRate` grows faster at each sophistication milestone than high-traditionalism tribe; high-traditionalism tribe's rate barely changes

---

## Phase 15 — Tribe Need States & Action Relevance Scoring

> Currently any divine action halves `prayerPressure` by a flat 50%, regardless of what the tribe actually needed. The richer design: scale the reset by how well the action matched the tribe's current state — CastRain on a Parched tribe clears far more pressure than Plague cast on a tribe already dying. This phase also adds the need indicator UI that makes informed intervention possible.

### Need states (derived, no new stored fields)

Add a computed function to derive a tribe's needs from stats that already exist:

| Need | Derived from | Condition |
|---|---|---|
| `Parched` | tile avg moisture | avg soil moisture of owned tiles < 25 |
| `Hungry` | `foodSupply`, `population` | foodSupply < population × 0.5 |
| `Starving` | `foodSupply` | foodSupply == 0 (checked via survival phase output) |
| `UnderThreat` | tile loss in last 5 ticks | lost ≥ 1 tile to a raid in the last 5 ticks* |
| `SpirituallyDepleted` | `skepticism`, `devotion` | skepticism > 60 && devotion < 40 |
| `Overcrowded` | pop / tile density | population / ownedTiles.size > `SPLIT_DENSITY_THRESHOLD` |
| `Thriving` | none of the above | fallback — all needs met |

*`UnderThreat` requires a `lastRaidTick: Long = -1L` field on `Tribe` (small addition, serialized with default). Update in `conflictStep()` when a tile is transferred.

- [ ] Add `lastRaidTick: Long = -1L` to `Tribe.kt` (the only new stored field in this phase; backward-compatible default)
- [ ] Add a pure function `Tribe.needs(ownedTiles: List<MapTile>, currentTick: Long): Set<TribeNeed>` to `Tribe.kt` — derived from existing fields, no Android context
- [ ] Define `enum class TribeNeed` in `domain/model/` with the 7 variants above

### Relevance scoring

- [ ] In `GameLoop.kt`, replace the flat `prayerPressure *= 0.5f` in the divine action block with a relevance-scaled reset:
  ```kotlin
  val relevance = when {
      tribe.needs(...).contains(action.primaryNeed) -> 1.0f
      tribe.needs(...).contains(action.secondaryNeed) -> 0.6f
      else -> 0.2f
  }
  val pressureReset = tribe.prayerPressure * (0.3f + 0.5f * relevance)  // 30–80% range
  tribe.prayerPressure -= pressureReset
  ```
- [ ] Define the primary/secondary need mapping per action (in `DivineAction.kt` or a companion object):

  | Action | Primary need | Secondary need |
  |---|---|---|
  | CastRain | `Parched` | `Hungry` |
  | BlessHarvest | `Hungry` / `Starving` | `Thriving` |
  | InspireDevout | `SpirituallyDepleted` | any |
  | SendPlague | `UnderThreat` (on aggressor) | — |
  | CauseFamine | — (punitive) | — |

### Need indicator UI

- [ ] Add small need icons to `TribeLegendChip` in `DashboardScreen.kt` — rendered as Unicode glyphs styled with Compose color (droplet 💧 for Parched, skull ☠ for Starving, sword ⚔ for UnderThreat, etc.); use U+FE0E text variation selector on glyphs that need Compose color styling (per existing UI pattern in `feedback_ui_patterns.md`)
- [ ] Icons only appear when the need is active; `Thriving` shows nothing
- [ ] Write unit tests: each need state derives correctly from its condition, boundary cases (exactly at threshold), relevance scoring matches expected 1.0/0.6/0.2 values

---

## Block 2 — Pre-Training Data Model

> Three fields missing from `Tribe` that take the RL policy from "technically works" to "actually makes sense." All small additions — no architectural changes. Add them before the headless and policy phases so they're in the state vector from the start.

---

## Phase 16 — Tribe Momentum, Hostility & Age Fields

### Population & territory momentum — CRITICAL

The policy sees a snapshot per tick. It cannot compute derivatives. "Population = 300 and rising" and "population = 300 and falling" look identical without trend data.

- [ ] Add `populationDelta: Int = 0` and `territoryDelta: Int = 0` to `Tribe.kt` (both serialized, backward-compatible defaults)
- [ ] At the **top** of `GameLoop.tick()`, before any simulation steps, compute deltas from the previous tick:
  ```kotlin
  val prevPop = state.tribes[tribe.tribeId]?.population ?: tribe.population
  val prevTiles = state.tiles.count { it.occupantTribeId == tribe.tribeId }
  // after steps complete, set:
  // tribe.copy(populationDelta = newPop - prevPop, territoryDelta = newTiles - prevTiles)
  ```
- [ ] Write unit tests: delta is positive after a growth tick, negative after starvation, zero when stable

### Inter-tribe hostility tracking — HIGH

The conflict model is currently stateless — aggression vs. caution is evaluated from scratch each tick with no memory of history. The policy can't learn "tribe B keeps raiding us; expand away from them."

- [ ] Add `hostility: Map<String, Float> = emptyMap()` to `Tribe.kt` (keyed by tribeId; serialized; default empty)
- [ ] In `conflictStep()`, when aggressor successfully transfers a tile: `aggressor.hostility[defender.tribeId] += 0.1f`; `defender.hostility[aggressor.tribeId] += 0.15f` (defender remembers harder)
- [ ] In `tick()`, decay all hostility values each tick: `hostility[id] *= 0.98f`; remove entries that drop below `0.01f`
- [ ] When a tribe is removed (absorbed or extinct), clean its id from all remaining tribes' hostility maps
- [ ] Wire the Phase 13 devotion hook: when `tribe.devotion > 70`, decay hostility slightly faster (`*= 0.96f` instead of `0.98f`)
- [ ] Write unit tests: hostility increments on raid, decays toward zero over time, removed tribe ids are cleaned up

### Tribe age — MEDIUM

A freshly split tribe (sophistication 0, no history) should behave differently from a centuries-old civilisation at the same population. `sophistication` resets to 0 on split; `foundedTick` separates them.

- [ ] Add `foundedTick: Long = 0L` to `Tribe.kt` (serialized; 0 for the first tribe)
- [ ] Set `foundedTick = worldTimeTick` when creating a child tribe in `splitStep()`
- [ ] Expose tribe age (`worldTimeTick - foundedTick`) in `TribeDetailSheet` for player visibility
- [ ] Write unit test: child tribe's `foundedTick` equals the tick on which the split fires; original tribe's `foundedTick` remains 0

---

## Block 3 — Headless & Policy Infrastructure

> The two phases that bridge Kotlin simulation to Python training. Neither adds new game mechanics — they're infrastructure.

---

## Phase 17 — Headless Simulation Mode

> `GameLoop.tick()` is already a pure function. This phase wraps it in a standalone Kotlin entry point that runs without any Android lifecycle, ViewModel, or Room dependency — enabling batch runs, stress tests, and ground-truth output for validating the Python port.

- [ ] Add a new Kotlin module (or `main/headless/` package in the domain layer) with a `main()` entry point that:
  - Creates a `WorldState` via `WorldState.initial()` with a given seed
  - Loads `SimEvent` list from a bundled JSON string (or file path argument)
  - Loads `TribePersonality` list from a bundled JSON string
  - Runs N ticks via `GameLoop.tick()` in a loop using seeded `Random`
  - Optionally serializes each post-tick `WorldState` to JSON (one file per tick or a single JSONL)
- [ ] Zero Android imports in this module — if any Android import appears, the build must fail
- [ ] Accept command-line args: `--ticks 1000 --seed 42 --output states.jsonl`
- [ ] Write a "parity test": run 100 ticks headless with seed 42; assert final `worldTimeTick`, total population, and tile count match a known-good snapshot (prevents silent drift)
- [ ] Verify: headless run of 1 000 ticks completes in < 10 seconds on a developer machine

---

## Phase 18 — TribePolicy Interface & State Vectorizer

> The last Kotlin phase before Python. Introduces the `TribePolicy` abstraction so the heuristic and RL policies are swappable, and builds the state vectorizer and reward calculator that the Python port will mirror exactly.

### TribePolicy interface

- [ ] Add `TribePolicy.kt` to `domain/rules/`:
  ```kotlin
  interface TribePolicy {
      fun chooseExpansion(tribe: Tribe, candidates: List<MapTile>): MapTile?
      fun chooseRaid(tribe: Tribe, targets: List<RaidCandidate>): RaidCandidate?
  }
  data class RaidCandidate(val tile: MapTile, val defenderTribeId: String)
  ```
- [ ] Add `HeuristicPolicy.kt` — wraps the current expansion scoring (`affinity × soilMoisture`) and conflict threshold (`aggression × (1 - caution)`) logic extracted from `GameLoop.kt`; the game loop delegates to it; behavior is identical to pre-refactor on the same seed
- [ ] Update `GameLoop.tick()` signature: add `policy: TribePolicy = HeuristicPolicy()` parameter; route expansion candidate selection and raid resolution through `policy.chooseExpansion` / `policy.chooseRaid`
- [ ] Write a behavioral parity test: run 200 ticks with `HeuristicPolicy` and with the raw pre-refactor logic using the same seed; assert identical final `WorldState`

### State vectorizer

- [ ] Add `Tribe.toFloatArray(ownedTiles: List<MapTile>, neighbors: List<Tribe>, currentTick: Long): FloatArray` to `Tribe.kt` — encodes all input features normalized to [0, 1]:
  - Own tribe: `population/MAX_POP`, `foodSupply/MAX_FOOD`, `devotion/100`, `skepticism/100`, `ownedTiles/GRID_SIZE`, `aggression`, `caution`, `skepticismRate/2`, `sophistication/10`, `populationDelta/MAX_DELTA`, `territoryDelta/MAX_DELTA`, `hostility[neighborId]` (one entry per neighbor slot, padded to `MAX_TRIBES`), `(currentTick - foundedTick)/MAX_AGE`, need state booleans (7 booleans)
  - Neighbor tribe (one slot per neighbor, padded to `MAX_TRIBES`): `pop`, `territory`, `hostility_toward_me`
  - Document the index layout as a constant array of label strings (`STATE_VECTOR_LABELS`) next to the function
- [ ] `MAX_POP`, `MAX_FOOD`, `MAX_DELTA`, `MAX_AGE`, `MAX_TRIBES` defined as constants in a companion object

### Reward calculator & training init

- [ ] Add `WorldState.reward(prev: WorldState, tribeId: String): Float` — `(newPop - prevPop) + (newTiles - prevTiles)` diffing two consecutive states; used by both headless batch runner and Python env
- [ ] Add `WorldState.initialForTraining(numTribes: Int, seed: Long): WorldState` — places 4–6 tribes in randomized starting positions with seeded RNG; production path (`WorldState.initial()`) unchanged
- [ ] Write unit tests: vectorizer output length matches `STATE_VECTOR_LABELS` length, all values in [0, 1], reward is positive after growth tick, negative after territory loss

---

## Block 4 — RL Pipeline

> Training, export, and deployment. Phases 19–20 happen off-device (Kaggle or Colab); Phase 21 brings the trained model back to Android.

---

## Phase 19 — Python Port & Gymnasium Environment

> Rewrite the headless `GameLoop` as a Python [Gymnasium](https://gymnasium.farama.org/) `Env`. This is a translation exercise — the Kotlin logic is authoritative; the Python must match it tick-for-tick on the same seed sequence.

- [ ] Create `training/game_env.py` — `class ProjectEchoEnv(gymnasium.Env)` with:
  - `reset(seed)` → initial observation vector (float array) for all tribes
  - `step(action_dict)` → `(observations, rewards, dones, truncated, info)`
  - `observation_space`: `Box(low=0, high=1, shape=(STATE_VECTOR_LENGTH,), dtype=float32)` per tribe
  - `action_space`: `Discrete(NUM_ACTIONS)` per tribe — `{expand_N, expand_S, expand_E, expand_W, raid_tribe_0, …, raid_tribe_N, rest}`
- [ ] **Observation normalization** — every input divided by its max constant before returning from `step()`. Skip this and training is slow and unstable.
- [ ] **Action masking** — compute a boolean mask each step; zero out illegal action logits (raiding non-adjacent tribe, expanding onto occupied tile) before softmax. Both CleanRL and stable-baselines3 support this natively.
- [ ] **Reward shaping** — augment the sparse terminal reward with:
  - `+0.01` per tick alive
  - `+0.1` per food surplus tick (foodSupply > population consumption)
  - `−0.5` per starvation tick
  - `+0.5` per successful raid
  - Guard: a tribe that hoards food but never grows should not outscore one that actually expands
- [ ] **Reward normalization** — clip rewards to [−1, 1] before gradient update; raw population deltas vary 0–50/tick and will destabilize training if passed raw
- [ ] **Domain randomization** — 5% chance per tick of injecting a divine-action-magnitude shock to a random tribe (plague-scale pop drop, harvest-scale food spike, inspire-scale devotion boost, famine-scale food drop); the policy learns robustness to sudden large state changes
- [ ] **Map diversity** — randomize `WorldState.initial()` seeds every episode; policy must learn general spatial reasoning, not "water is always bottom-left"
- [ ] Parity test: run 100 ticks with seed 42 on both Kotlin headless and Python env; assert matching final population and tile counts per tribe

---

## Phase 20 — Kaggle Training (PPO + Hall of Fame League)

> Train on [Kaggle](https://www.kaggle.com/) (30 hrs/week free GPU) or Google Colab using CleanRL or stable-baselines3. Do not train on Android — Android is inference-only.

### Policy network

- [ ] Tiny custom MLP: ~3 layers × 128 neurons, ~50 K parameters — inputs floats, outputs action probabilities via softmax. This is not a language model; it runs in microseconds.
- [ ] Personality stats (`aggression`, `caution`, etc.) are *input features*, not separate networks — one shared policy handles all archetypes; the network learns that high aggression → prefer raid actions

### Training loop

- [ ] Spin up 4–6 tribes per match; ~200 ticks per episode
- [ ] **Hall of Fame** — snapshot current policy weights every 100 matches; ~50% of opponents drawn from Hall (frozen past snapshots). Prevents collapse to one degenerate strategy.
- [ ] **Parallel environments** — `SubprocVecEnv` with 8–16 simultaneous environments for 8–16× more experience per GPU hour
- [ ] **Curriculum learning** — start with 50-tick episodes, crowded starting maps (conflict happens early); increase episode length as policy matures; crowded starts prevent "always expand into empty space" local optimum
- [ ] **Entropy decay schedule** — high PPO entropy bonus early (forces exploration); decay over training (lets policy commit to decisive behavior); without high early entropy: immediate collapse to one action
- [ ] **Checkpoint every N episodes** — policy collapse (catastrophic forgetting) is real; save weights frequently and keep rollback capability; Hall of Fame snapshots help but are separate from weight checkpoints
- [ ] **Evaluate against heuristic baseline every N episodes** — run current policy against `HeuristicPolicy` and track win rate; heuristic is the training floor; regression below it signals a problem
- [ ] Success criterion: policy win rate vs. heuristic baseline > 60% at convergence

---

## Phase 21 — TFLite Export & Android Inference

> Export the trained policy to TFLite and wire it into the Android app via the `TribePolicy` interface built in Phase 18.

- [ ] Export trained model to TFLite via `jax2tf` → TFLite converter, or stable-baselines3 ONNX exporter → TFLite converter
- [ ] Drop `.tflite` into `/assets/` (same pattern as `personalities.json` and `events.json`)
- [ ] Add `RLPolicy.kt` to the Android feature layer implementing `TribePolicy`; loads the TFLite model from assets, calls `Tribe.toFloatArray()` for each tribe, runs inference, maps output logits to `chooseExpansion` / `chooseRaid` decisions
- [ ] Add a toggle in `GameViewModel` (`useRLPolicy: Boolean`, default `true`); accessible via a settings or debug button; allows side-by-side comparison of heuristic vs. RL behavior
- [ ] All tribes use the RL model from tick 0 (Option B — decided in architecture notes); no transition edge case
- [ ] Measure on-device inference time; must be comfortably < 200 ms (tick interval is 2 000 ms); log inference time in debug builds
- [ ] Smoke test: observe tribal behavior with RL policy enabled; verify tribes make meaningful territorial and conflict decisions; chronicle entries should show raids and expansions

---

## Long-Range Placeholders (Post-Phase 21)

No design work started. Revisit after Phase 21 delivers the initial RL agent and the policy has demonstrated basic expand/raid/rest strategy.

### Tribe mergers / vassalage

The reverse of splitting: a weakened tribe absorbed by a dominant neighbour. Explicitly deferred — the policy needs to learn basic territorial strategy before merger decisions are added to the action space. When ready, mergers are an *agent decision*, not an automatic trigger.

- [ ] Add `vassalize_tribe_N` actions to action space (masked unless dominant enough)
- [ ] Tribe B absorbed: territory transfers, B's personality stats influence A's (skepticism, sophistication) — bounded and gradual so state vector doesn't jump discontinuously

### Inter-tribe cultural & resource diffusion *(open design question)*

- Devotion/skepticism bleeding across shared borders (modulated by border length and personality)
- Trade food across borders instead of always raiding (personality-gated: cautious tribes trade, warlike tribes raid)
- Shared coast/river tiles — who gets the fishing bonus?
- Resolve the core tension first: are tribes fundamentally competitive (zero-sum grid) or cooperative (positive-sum civilisation)? Answer changes the RL reward signal significantly.

### Alliance mechanics

Non-aggression pacts between low-mutual-aggression tribes; coordinated expansion; alliance breakdown via high skepticism or divine action targeting one partner. Multi-agent cooperation is significantly harder than competition — do not add before the competitive policy is solid.

### Gemma / LLM Chronicle generation *(distinct from policy network)*

A small on-device LM generating dynamic Chronicle text instead of template strings. Gemma E4B is the candidate. This is a separate feature from the policy network (different architecture, different purpose) and should be designed after Phase 21 to avoid conflating the two.

### World as adaptive AI (Option A — reactive climate)

**Design philosophy:** The World is a closed system in dynamic tension with its inhabitants. Tribes behave like a virus — expanding, consuming, pushing soil moisture above its natural baseline. The World's decay system is the immune response, constantly pulling conditions back toward equilibrium. High collective sophistication lets tribes resist that pull (the `SOPH_MOISTURE_CEILING` mechanic), but the World can escalate.

God sits between both systems as mediator. Divine actions don't override the World — they *bias* it. CastRain shifts weather generation probabilities; the World runs its own logic from there. The outcome is never fully certain, which is closer to how most theological traditions describe divine action than a direct miracle would be.

**Technical direction (post-Phase 21):**

- Train a small generative model (not a policy — no action selection) that observes aggregate `WorldState` signals (total population, average sophistication, territorial coverage, mean soil moisture) and outputs a probability distribution over weather front types and spawn rates for the next N ticks
- Training signal: keep tribal population in a "productive tension" band — not thriving so easily that God becomes irrelevant, not dying so fast that intervention is pointless
- God's divine actions become inputs to the World model's context at inference time — CastRain on a region slightly increases the model's rain-front probability for that column cluster; it doesn't guarantee rain
- The result: the World responds to civilisational pressure the way climate responds to human activity — feedback loops, lag, partial reversibility
- Separate from the Tribe RL policy (different architecture, different training objective); both run at inference time on-device, neither knows about the other directly

**Why this is worth building:** It makes the training environment for the Tribe policy richer (tribes can't memorise fixed weather patterns) and gives the player's divine role genuine weight — they are managing the relationship between two adaptive systems, not just keeping a tribe's health bar up.

---

## Summary

| Phase | What | Key file(s) |
|---|---|---|
| 13 | InspireDevout fix + devotion behavioral hooks | `GameLoop.kt`, `events.json` |
| 14 | Sophistication → military doctrine + faith drift | `GameLoop.kt` |
| 15 | Tribe need states + action relevance + chip UI | `Tribe.kt`, `GameLoop.kt`, `DashboardScreen.kt` |
| 16 | populationDelta, territoryDelta, hostility, foundedTick | `Tribe.kt`, `GameLoop.kt` |
| 17 | Headless standalone sim mode (no Android) | new `headless/Main.kt` |
| 18 | TribePolicy interface + state vectorizer + reward calc | new `TribePolicy.kt`, `HeuristicPolicy.kt`, `GameLoop.kt` |
| 19 | Python port + Gymnasium env (off-device) | `training/game_env.py` |
| 20 | Kaggle PPO training + Hall of Fame league | Kaggle notebook |
| 21 | TFLite export + Android inference via RLPolicy | new `RLPolicy.kt`, `/assets/*.tflite` |

---

*Each phase is designed to be committed as its own micro-branch and PR. Complete all checkboxes in a phase before beginning the next.*
