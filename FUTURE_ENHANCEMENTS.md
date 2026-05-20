# Future Enhancements & Deferred Work

Items collected from committed phases that were not completed before the phase was merged.
Update this file when items are built or consciously dropped.

---

## Deferred Smoke Tests

Phases were committed with these smoke tests unchecked. Each is a manual on-device verification pass.

| Phase | What to verify |
|---|---|
| **9 — Radial Splash** | Touch a tile; confirm halo highlights correct neighbors; cast Rain, confirm moisture rises across the entire cluster |
| **9 — Dashboard UI** | Dashboard renders correctly at multiple screen sizes; tribe chip opens detail sheet; action chips are uniform; map fills available space |
| **10 — Dynamic Chronicle** | Run a session into starvation; confirm Chronicle shows the tribe's actual name; confirm `famine_warning` reappears after its cooldown elapses |
| **11a — Biome World Gen** | New world generates visible water bodies and coast tiles; `biome` field present on all tiles |
| **11b — Biome Simulation** | Desert tiles dry out faster than Grassland; Forest tiles stay greener; Coast tiles show fishing bonus in food output |
| **11c — Biome UI / Overlays** | CastRain visibly shifts tile moisture in Chronicle; overlay toggle switches map colouring correctly |
| **12a — Tribal Splitting** | Simulate to pop ~1 300; observe Chronicle schism entry; verify two distinct tile colors and two tribe chips in the legend |
| **12b — Tribal Personality** | Two tribes post-split with different archetypes; Biome overlay confirms preferred terrain colonisation; cast divine actions repeatedly and observe skepticism rising; let a tribe starve and recover and observe generational Chronicle entry fire |

---

## Design Enhancements

Explicit feature ideas flagged as out-of-scope during design work.

### Prayer pressure — action-relevance scaling *(from Phase 12b-2)*
Currently, any divine action halves the targeted tribe's `prayerPressure` regardless of what the tribe actually needed. The richer design: reset magnitude scales with how well the action matched the tribe's current state — CastRain on a Parched tribe clears more pressure than Plague cast on a tribe already dying. Pairs naturally with per-tribe divine targeting added in Phase 12c.

#### Tribe need states ("The Sims" model)

Rather than storing extra fields on `Tribe`, need states are *derived on the fly* from stats that already exist:

| Need | Derived from | Condition |
|---|---|---|
| `Parched` | tile avg moisture | avg soil moisture of owned tiles < 25 |
| `Hungry` | `foodSupply`, `population` | foodSupply < population × 0.5 |
| `Starving` | `foodSupply` | foodSupply == 0 for 3+ consecutive ticks |
| `UnderThreat` | tile loss rate | lost ≥ 1 tile to a raid in the last 5 ticks |
| `SpirituallyDepleted` | `skepticism`, `devotion` | skepticism > 60 && devotion < 40 |
| `Overcrowded` | pop/tile density | population / ownedTiles.size > `SPLIT_DENSITY_THRESHOLD` |
| `Thriving` | none of the above | fallback — all needs met |

No new stored fields. The derived check runs at the moment a divine action is cast.

#### Relevance scoring

Each divine action has a primary and secondary matching need. The prayer pressure reset scales with how well the action matched:

| Action | Primary match | Secondary match |
|---|---|---|
| CastRain | `Parched` | `Hungry` (indirect, moisture → food) |
| BlessHarvest | `Hungry` / `Starving` | `Thriving` (growth boost) |
| InspireDevout | `SpirituallyDepleted` | any (faith boost always has some value) |
| SendPlague | `UnderThreat` (cast on aggressor) | none — harmful to target |
| CauseFamine | none (purely punitive) | none |

```kotlin
// rough formula
val relevance = when {
    tribe.needs.contains(action.primaryNeed) -> 1.0f   // full match
    tribe.needs.contains(action.secondaryNeed) -> 0.6f // partial match
    else -> 0.2f                                        // mismatched
}
val pressureReset = tribe.prayerPressure * (0.3f + 0.5f * relevance)  // 30–80% range
```

#### UI implication
Need indicators could surface on tribe chips in the dashboard (small icons — a droplet for Parched, a skull for Starving, a sword for UnderThreat, etc.), giving the player the "Sims panel" feedback to make informed divine action choices. Design that UI in the same phase as the relevance scoring.

#### RL connection
Need states are strong candidates for inclusion in the policy network input vector — they compress a lot of game state into a small set of booleans that the network can act on directly. When Phase 14 defines the full state vector, revisit this list.

### InspireDevout — thematic and mechanical realignment

`InspireDevout` is the odd one out among divine actions and has two problems worth fixing before the prayer pressure system is built.

**Problem 1 — it can't be mistaken for a natural event**

Every other divine action has a plausible natural explanation from the tribe's perspective: CastRain is a rainstorm, SendPlague is a disease outbreak, BlessHarvest is a good harvest season, CauseFamine is drought. A +15 devotion spike has no natural analog — the tribe *knows* something happened to their collective faith. They experienced it as genuinely divine (a prophet appeared, a vision occurred). This is actually interesting, but it means the blanket skepticism gain formula is thematically backwards for this action specifically:

```kotlin
// fires for every divine action including InspireDevout — wrong for this one
val skepGain = (action.favorCost / 10f * tribe.personality.skepticismRate).roundToInt()
```

The logic for other actions — "divine intervention makes tribes more aware of and dependent on the divine, which breeds doubt over time" — works for CastRain ("why did the rain stop?") and SendPlague ("why did our god punish us?"). For InspireDevout it's contradictory: you directly filled their hearts with faith and in response they become more skeptical. A spiritual experience the tribe attributes to the divine should reduce skepticism or be neutral. Fix: exempt InspireDevout from the `skepGain` formula, or invert it to a small skepticism reduction.

**Problem 2 — devotion is mechanically passive for the tribe**

InspireDevout adds +15 devotion. What does that actually change for tribal *behaviour*? Currently: it increases divine favor regeneration (helps the player) and pushes devotion further above the 60 prayer threshold. The tribe itself doesn't act differently — it doesn't expand more boldly, resist raids more, or hold together under stress. Devotion is a player resource metric more than a tribal state.

Suggested behavioural hooks once the `TribePolicy` interface exists (Phase 14+):
- High devotion → raids less likely (tribe is waiting on divine guidance rather than acting unilaterally)
- High devotion → splits less likely (spiritual cohesion holds the community together)
- High devotion → hostility decays faster (faith community resists grudges)

**The prayer pressure paradox — keep but surface it**

InspireDevout → +15 devotion → if devotion goes above 60, prayer pressure accumulates faster → if the god then goes quiet → faith crisis → skepticism gains. Inspiring devotion can inadvertently set up a collapse by raising expectations the god can't meet. This is good design worth keeping, but it needs explicit Chronicle narration so the player understands the feedback loop ("The [tribe] prays with renewed fervour — their expectations of the divine have grown").

### Sophistication — military doctrine & faith amplification

Sophistication currently only affects food production via the biome multiplier. Two gaps to close in a future phase:

#### Military doctrine (wire into `conflictStep`)

A sophistication-10 warlike tribe currently raids identically to a sophistication-0 one with the same aggression. Sophistication should represent tactics, coordination, and logistics:

```kotlin
val attackBonus  = 1f + aggressor.personality.sophistication * 0.04f  // up to +40% at soph 10
val defenseBonus = 1f - defender.personality.sophistication * 0.03f   // up to -30% at soph 10
val threshold = aggressor.personality.aggression *
                (1f - defender.personality.caution) *
                attackBonus * defenseBonus
```

The archetype already routes the outcome — the same bonus matters more for a warlike tribe (high base aggression) than a reclusive one (low base aggression). No new stats needed; sophistication is just plugged into the existing formula.

#### Faith amplification (wire into `skepticismRate` on milestone)

The intuition "more advanced = more atheistic" doesn't hold historically. Ancient Rome, medieval Islamic scholars, and modern India are all sophisticated and devout. What actually correlates with secularism is *security and predictability*, not advancement itself.

The real dial is `traditionalism`:
- **High traditionalism × rising sophistication** → organized religion, temples, clergy — sophistication amplifies existing faith. Devotion becomes more stable and harder to erode.
- **Low traditionalism × rising sophistication** → institutional structures erode old certainties, new frameworks emerge — sophistication accelerates drift toward doubt.

Wire this at the sophistication milestone tick:

```kotlin
// each time sophistication increments
val faithDrift = (1f - tribe.personality.traditionalism) * 0.05f
tribe.copy(personality = tribe.personality.copy(
    skepticismRate = (tribe.personality.skepticismRate + faithDrift).coerceIn(0f, 2f)
))
```

At traditionalism 0.9: drift ≈ +0.005 per milestone — nearly stable.
At traditionalism 0.1: drift ≈ +0.045 per milestone — a restless, fast-advancing tribe becomes progressively quicker to doubt.

Pairs with the prayer pressure relevance work: a sophisticated low-traditionalism tribe at high skepticism is the hardest player challenge — well-matched divine actions are needed just to hold ground.

#### RL connection

Both changes make the training environment richer without adding new input features:

- **Military doctrine**: once sophistication is wired into `conflictStep`, the colosseum produces clearer reward signals — sophisticated warlike tribes genuinely dominate raids, giving the policy a meaningful incentive to grow sophistication rather than just aggression. Flat conflict outcomes produce flat gradients; differentiated outcomes train faster.
- **Faith amplification**: `skepticismRate` drift means tribes diverge in character over a full training match — a low-traditionalism tribe that survives long enough becomes genuinely harder to manipulate. The policy learns to read that trajectory, not just the current snapshot.
- **Derived input feature**: consider adding `(1f - traditionalism) * sophistication / 10f` as a single composite feature to the state vector. It compresses the "how secular-drifting is this tribe?" signal into one number the network can act on directly, rather than leaving it to the network to multiply two separate inputs and discover the interaction on its own.

---

### Inter-tribe cultural & resource diffusion *(open design question)*

Right now tribes are isolated agents — they compete for tiles but don't influence each other's internal stats. Several unanswered questions once multi-tribe is mature:

**Cultural contagion**
- Does devotion bleed across borders? A highly devout tribe adjacent to a skeptical one could slowly pull it toward faith (or vice versa). Rate could be modulated by border length and personality (`reclusive` resists outside influence; `nomadic` absorbs it easily).
- Does skepticism spread? A tribe that has openly defied the divine could seed doubt in its neighbours — especially dangerous if the player is trying to maintain devotion across multiple groups.
- Does sophistication diffuse? Adjacent tribes learning from each other over time (slower than organic growth, but a bonus for peaceful coexistence).

**Resource interaction**
- Can tribes trade food across shared borders rather than always raiding? An agrarian surplus next to a starving warlike tribe could trigger a trade event instead of a raid — personality-gated (a warlike tribe with high aggression raids first; a cautious one trades).
- Shared water tiles: coastal/river tiles adjacent to two tribes — who benefits from the fishing bonus?

**Alliances**
- Non-aggression pacts: two tribes with low mutual aggression and a long shared border could settle into a stable alliance, suppressing `conflictStep` rolls between them.
- Coordinated expansion: allied tribes expand away from each other rather than toward each other, carving out complementary territory.
- Alliance breakdown: high skepticism or a divine action targeting one partner could fracture the pact, triggering a Chronicle entry and re-enabling raids.

**Core tension to resolve before designing:** are tribes fundamentally *competitive* (zero-sum, finite grid) or *cooperative* (positive-sum emergent civilisation)? The current architecture leans competitive; introducing cooperation changes the reward signal for the RL agent (Phase 13+) and the narrative tone significantly.

---

## Long-Range Ideas *(Phase 13+ placeholders)*

No design work started. Revisit after Phase 12c (Raids) is complete.

### Tribe mergers / vassalage

The reverse of splitting: a weakened tribe absorbed by a dominant neighbour. Chronicle entry marks the annexation; territory transfers; the absorbed tribe's personality colours the dominant tribe's stats (inheriting some skepticism, boosting sophistication).

#### RL design constraint — mergers are agent decisions, not automatic triggers

Hardcoding a trigger ("merge when population > 3× opponent") replaces one heuristic with another and bakes in assumptions about optimal strategy. The agent might discover that merging at 2× is smarter in certain biome configurations, or that raiding for tiles is more efficient than absorbing. Mergers belong in the **action space**, not the world rules.

**Phased action space** — don't add merger actions to initial training. The policy needs to learn basic territorial and combat strategy first:

| Training phase | Action space |
|---|---|
| Phase 15 (initial) | expand, raid, rest |
| Later expansion | + vassalize/merge *(once basic strategy is solid)* |
| Much later | + alliance proposal *(multi-agent cooperation — significantly harder)* |

**Variable tribe count** — mergers decrease tribe count mid-episode. Handle with padding + action masking (same mechanism already planned for raids):

```text
Action space (padded to MAX_TRIBES slots):
  expand_N/S/E/W
  raid_tribe_0 … raid_tribe_N      ← masked if not adjacent
  vassalize_tribe_0 … tribe_N      ← masked if not dominant enough
  rest
```

**Alliances** are a different beast — require two policies to coordinate simultaneously, which is multi-agent cooperation on top of competition. Flag as long-range only; not Phase 15.

**Stat inheritance** — when tribe B is absorbed, tribe A's stats shift (skepticism, sophistication). Keep inheritance bounded and gradual so the state vector doesn't jump discontinuously mid-episode, which would confuse the policy.

### Option C — Reinforcement Learning agent *(research track)*
Replace the weighted-utility personality system with a small policy network trained via PPO self-play. Each tick is a step; reward signal is population growth and territory size. Revisit only after Phase 12c conflict mechanics give the reward signal enough meaning to train against.

#### Architecture

**Policy network** — a tiny custom MLP (~3 layers × 128 neurons, ~50 K parameters). This is *not* a language model like Gemma; it is a decision network: floats in, action probabilities out. Runs in microseconds on-device via TFLite.

- Input vector (~30–50 floats): `[my_pop, my_food, my_devotion, my_skepticism, my_territory, my_aggression, my_caution, my_skepticismRate, neighbor_pop, neighbor_territory, adjacent_biomes...]`
- Output (~10–15 floats, softmax): `[expand_N, expand_S, expand_E, expand_W, raid_A, raid_B, rest, ...]`
- Personality stats are *input features*, not separate networks — one shared policy handles all archetypes; the network learns that high aggression → prefer raid actions, high caution → prefer expand/rest.

**Training loop (Colosseum)**

1. Spin up a headless game: 4–6 tribes, ~200 ticks per match.
2. Assign each tribe a policy version: ~50% current policy (learning), ~50% sampled from Hall of Fame (frozen past snapshots).
3. Run the match; score tribes by population growth + territory gained.
4. Run PPO: nudge current policy weights toward actions that produced winners.
5. Every 100 matches: snapshot current policy → Hall of Fame.
6. Repeat until convergence (~10 000+ matches).

The Hall of Fame prevents the policy collapsing into one degenerate strategy (e.g. always-raid-day-1) by forcing it to stay robust against past versions of itself. Conceptually: PPO is the dog learning from experience; the Hall of Fame is the selective breeding pressure keeping good traits in the gene pool.

#### Training infrastructure

Train on **Kaggle** (30 hrs/week free GPU) or Google Colab — not on Android. Android is inference-only.

Required work before training can start:
1. **Port sim to Python** — rewrite headless `GameLoop` logic as a [Gymnasium](https://gymnasium.farama.org/) environment (`game_env.py`). Pure data structures + math; no Android or Compose.
2. **Train with CleanRL or stable-baselines3** — both open source, PPO implementations, run on Kaggle kernels cleanly.
3. **Export to TFLite** — use `jax2tf` or the stable-baselines3 ONNX exporter → TFLite converter.
4. **Drop `.tflite` into `/assets`** and replace the heuristic expand/raid functions with model inference.

Reference: [TensorFlow Blog — RL agent trained with JAX, deployed on Android via TFLite](https://blog.tensorflow.org/2022/09/building-reinforcement-learning-agent-with-JAX-and-deploying-it-on-android-with-tensorflow-lite.html)

#### Training quality checklist *(implement at Phase 15)*

**Must-haves — training won't work correctly without these:**

- **Observation normalization** — the state vector spans wildly different scales (`population` ~1200, `aggression` ~0.9, `skepticism` ~47). Normalize every input to [0, 1] at the state vectorizer (`population / MAX_POP`, etc.) before it hits the network. Skip this and training is slow and unstable.
- **Action masking** — zero out illegal action probabilities (raiding a non-adjacent tribe, expanding onto occupied tiles) before the softmax. Prevents wasted gradients on moves the tribe can't make. Both CleanRL and stable-baselines3 support this natively.
- **Reward shaping** — "population growth + territory" is sparse and delayed; a tribe can make 50 good decisions before seeing any reward. Add intermediate signals: small positive per tick alive, positive for food surplus, negative for starvation, positive for successful raids. Guard against gaming: a tribe that hoards food but never grows should not outscore one that actually expands.
- **Reward normalization** — clip or normalize reward values to a fixed range (e.g. [−1, 1]) before the gradient update. Raw population deltas vary 0–50 per tick; outliers destabilize training.
- **Map diversity** — randomize `WorldState.initial()` seeds every match. Policy must learn general spatial reasoning, not "water is always bottom-left."

**High-impact improvements:**

- **Parallel environments** — run 8–16 environments simultaneously on the Kaggle GPU for 8–16× more experience per hour. `SubprocVecEnv` in stable-baselines3 handles this in one line.
- **Centralized critic / CTDE** — during training, a shared critic network sees *all* tribe states simultaneously, giving much better credit assignment ("tribe A's population dropped because tribe B raided it, not because of tribe A's own decisions"). During deployment, each tribe uses only its own observation — the centralized critic is training-only. CleanRL has a MAPPO implementation. Significant improvement for competitive multi-agent games.
- **Curriculum learning** — start with short episodes (50 ticks, crowded starting maps so conflict happens early). Increase episode length as the policy matures. Crowded starts prevent the policy getting stuck in a "never raid" local optimum where expansion into empty space is always rewarded without conflict.
- **Entropy decay schedule** — keep PPO's entropy bonus high early (forces exploration of the full action space) and decay it over training (lets the policy commit to decisive behaviour). Without high early entropy: immediate collapse to one action. Without decay: permanently random.

**Collapse protection:**

- **Checkpoint every N episodes** — policy collapse (catastrophic forgetting) is real. Save weights frequently and have a rollback. The Hall of Fame snapshots help but checkpoint the weights separately.
- **Evaluate against heuristic baseline** — every N episodes, run the current policy against the rule-based heuristic and track win rate. The heuristic is the floor; if the policy drops below it, something regressed.

**Probably overkill for this game:**

- Recurrent policy (LSTM/GRU) — learns temporal patterns across ticks. Worth it in theory, but the momentum deltas (`populationDelta`, `territoryDelta`) and hostility map already cover the most important temporal signals as flat features. Recurrent training is significantly more complex — skip unless the flat policy demonstrably fails at something temporal.

**Rough phase breakdown:**
- Phase 13 — headless sim mode (pure Kotlin, no Android lifecycle)
- Phase 14 — Python port + Gymnasium env + state/action/reward definitions
- Phase 15 — Kaggle training run (PPO + Hall of Fame league)
- Phase 16 — TFLite export + Android inference integration

#### Architecture readiness assessment

The current codebase is in good shape for RL integration — no rewrites needed, only targeted additions at the right phase.

**Already RL-ready (no changes needed):**
- `tick()` is a pure function: `(WorldState, params) → WorldState`. This is exactly the Gymnasium `env.step()` signature. The environment interface already exists in spirit.
- Domain layer has zero Android imports — the Python port at Phase 14 is straightforward data structure + arithmetic translation.
- `Random` is injected on every function, so training runs can use seeded randomness (`Random(seed=42)`) for deterministic replay and debugging.
- `Tribe`, `TribePersonality`, and `WorldState` are all `@Serializable` data classes with primitive fields. Turning a `Tribe` into a float input vector is trivial — just list the fields.

**Needs to be added at Phase 13 (headless sim):**
- Extract `GameLoop` into a standalone Kotlin entry point that runs without any Android lifecycle. Currently it works fine in unit tests, so it's close — just needs a `main()` wrapper and no ViewModel dependency.

**Needs to be added at Phase 14 (Python port + env):**
- A `TribePolicy` interface in Kotlin so the heuristic and RL policies are swappable:
  ```kotlin
  interface TribePolicy {
      fun chooseExpansion(tribe: Tribe, candidates: List<MapTile>): MapTile?
      fun chooseRaid(tribe: Tribe, targets: List<RaidCandidate>): RaidCandidate?
  }
  ```
  `HeuristicPolicy` wraps current logic; `RLPolicy` calls the TFLite model. `tick()` accepts a `policy: TribePolicy` parameter.
- A state vectorizer (`Tribe.toFloatArray(neighbors)`) — derived from existing fields, no new stored data needed.
- A reward calculator — `reward = (new_pop - old_pop) + (new_territory - old_territory)`, computed by diffing two consecutive `WorldState`s. The data is already in the state.
- A multi-tribe variant of `WorldState.initial()` for training (4–6 tribes, randomised start positions, seeded RNG). Production path unchanged.

**Do not add RL scaffolding before Phase 13.** The Phase 12 work (raids, personality depth, splitting) is making the state space and reward signal richer. Adding the `TribePolicy` interface before that settles would mean designing it against an incomplete action space.

#### Pre-training data model additions *(do before Phase 14)*

Three fields missing from `Tribe` that would take the policy from "technically works" to "actually makes sense." All are small additions — no architectural changes needed.

**1. Population & territory momentum — CRITICAL**

The network sees a snapshot per tick. It cannot compute derivatives. "Population = 300 and rising" and "population = 300 and falling" look identical without trend data — the single biggest thing that would make training feel hobbled.

```kotlin
// add to Tribe
val populationDelta: Int = 0   // pop change vs previous tick (+/-)
val territoryDelta: Int = 0    // tiles gained/lost vs previous tick (+/-)
```

Updated at the top of `tick()` before territory and conflict steps. Two ints; high signal.

**2. Inter-tribe hostility tracking — HIGH**

The conflict model is currently stateless — aggression vs. caution evaluated from scratch each tick with no memory of history. The policy can't learn "tribe B keeps raiding us; expand away from them and toward tribe C's softer border" because there's no record of who did what.

```kotlin
// add to Tribe (or WorldState)
val hostility: Map<String, Float> = emptyMap()  // keyed by tribeId; increments on raid, decays over time
```

Unlocks: revenge raids, persistent rivalries, natural alliance formation. All of these emerge from the policy as learned behaviour rather than being hardcoded — exactly the right way to get them.

**3. Tribe age — MEDIUM**

A freshly split tribe (sophistication 0, no history) should behave differently from a centuries-old civilisation at the same population. `sophistication` signals this partially but resets to 0 on split along with age, so they conflate. A clean `foundedTick` separates them.

```kotlin
// add to Tribe
val foundedTick: Long = 0L   // worldTimeTick at creation; 0 for the first tribe
```

Young tribes learn to avoid conflict and consolidate; old ones leverage territorial depth and institutional memory. Without this, the policy treats a fresh split identically to an established tribe at the same population.

#### Model activation — DECIDED: Option B (model from tick 0)

All tribes use the RL model from the start. The model was trained on tribes at every stage of development, so it already knows how to act young (expand carefully, avoid conflict) vs. old (raid, defend territorial depth) — because `sophistication`, `foundedTick`, and the momentum deltas are in the input vector. No transition edge case, no behavioural discontinuity. Age-appropriate behaviour is learned, not gated.

#### Divine actions & domain randomization

From the tribe's perspective, a divine action is indistinguishable from a natural event — CastRain is just a rainstorm, SendPlague is just a disease outbreak, BlessHarvest is just a good season. The policy reads the *state change*, not the cause. Tribes don't model the deity; they respond to conditions. This is intentional and correct — it means training without divine actions transfers cleanly to the real game.

The one real risk is **magnitude**: divine actions produce larger instantaneous shocks (SendPlague drops population 20% in one tick; CastRain spikes moisture by 25) than normal environmental variance in training. If the policy has never seen shocks that large, it may not generalise well to recovering from or exploiting them.

Fix: **domain randomization** in the training loop. Occasionally inject random shocks at divine-action magnitude, targeting random tribes at random intervals. The policy learns robustness to sudden large state changes without ever knowing a god was involved:

```python
# in the training loop, each match
if random.random() < 0.05:   # 5% chance per tick
    target = random.choice(tribes)
    shock = random.choice([
        lambda t: t.copy(population=int(t.population * 0.8)),   # plague-scale
        lambda t: t.copy(food_supply=t.food_supply + 200),      # harvest-scale
        lambda t: t.copy(devotion=min(100, t.devotion + 15)),   # inspire-scale
        lambda t: t.copy(food_supply=max(0, t.food_supply-80)), # famine-scale
    ])
    apply shock to target tribe state
```

Small addition to the Python training loop. No design change to the Kotlin sim or the model architecture.

#### Gemma / LLM note
Gemma E4B (and similar small LMs) are *not* the right tool for the policy network — wrong architecture, 4.5 B parameters of unnecessary overhead. However, a small on-device LM *would* be interesting for a separate feature: dynamically generating Chronicle text instead of template strings. That is a distinct feature to design after Phase 16.
