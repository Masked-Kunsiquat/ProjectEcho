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
Currently, any divine action halves the targeted tribe's `prayerPressure` regardless of what the tribe actually needed. The richer design: reset magnitude scales with how well the action matched the tribe's current state — CastRain on a Parched tribe clears more pressure than Plague cast on a tribe already dying. Requires explicit tribe-level need states (e.g. `hungry`, `under_threat`, `spiritually_depleted`). Pairs naturally with per-tribe divine targeting added in Phase 12c.

---

## Long-Range Ideas *(Phase 13+ placeholders)*

No design work started. Revisit after Phase 12c (Raids) is complete.

### Tribe mergers / vassalage
The reverse of splitting: a weakened tribe is absorbed by a dominant neighbour. Chronicle entry marks the annexation; territory transfers wholesale; the absorbed tribe's personality and history could colour the dominant tribe's stats (e.g. inheriting some of the vassal's skepticism, boosting sophistication).

### Option C — Reinforcement Learning agent *(research track)*
Replace the weighted-utility personality system with a small policy network trained via Q-learning. Each tick is a step; reward signal is population growth or territory size. Requires a headless fast-forward simulation mode for training convergence (~10 000+ ticks), and TensorFlow Lite for on-device inference. Revisit only after Phase 12c conflict mechanics give the reward signal enough meaning to train against.
