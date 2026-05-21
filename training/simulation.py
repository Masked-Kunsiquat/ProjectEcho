"""
ProjectEcho — Python simulation engine.

Faithful port of the Kotlin GameLoop. Uses a Java-compatible LCG so
seed-for-seed results match the Kotlin headless runner exactly.
"""
from __future__ import annotations

import ctypes
import math
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

# ---------------------------------------------------------------------------
# XorWow RNG — matches kotlin.random.Random (Kotlin 2.x, XorWowRandom)
# ---------------------------------------------------------------------------

def _s32(x: int) -> int:
    return ctypes.c_int32(x).value

def _u32(x: int) -> int:
    return x & 0xFFFFFFFF


class JavaRandom:
    """XorWow PRNG matching kotlin.random.Random (Kotlin 2.x).

    Kotlin Random(seed: Long) = XorWowRandom(seed.toInt(), seed.ushr(32).toInt())
    with 64 warmup iterations before the first user call.
    """

    def __init__(self, seed: int):
        seed1        = _s32(seed)                                            # seed.toInt()
        seed2        = _s32(seed >> 32)                                      # seed.ushr(32).toInt()
        self._x      = seed1
        self._y      = seed2
        self._z      = 0
        self._w      = 0
        self._v      = _s32(~seed1)                                          # seed1.inv()
        self._addend = _s32(_s32(seed1 << 10) ^ (_u32(seed2) >> 4))         # (seed1 shl 10) xor (seed2 ushr 4)
        for _ in range(64):                                                  # warmup
            self._bits(32)

    def _bits(self, n: int) -> int:
        """nextBits(n): top n bits of XorWow output as a signed int."""
        t = self._x
        t = _s32(t ^ (_u32(t) >> 2))                                        # t xor (t ushr 2)
        self._x = self._y
        self._y = self._z
        self._z = self._w
        self._w = self._v
        self._v = _s32((self._v ^ _s32(self._v << 4)) ^ (t ^ _s32(t << 1))) # (v^v<<4)^(t^t<<1)
        self._addend = _s32(self._addend + 362437)
        result = _s32(self._v + self._addend)
        upper  = _u32(result) >> (32 - n)                                    # result ushr (32-n)
        mask   = _s32(-n) >> 31                                              # -1 for n>0, 0 for n=0
        return _s32(upper & mask)

    def next_int(self, bound: Optional[int] = None) -> int:
        if bound is None:
            return self._bits(32)
        assert bound > 0
        n = bound
        if n & (n - 1) == 0:                                                 # power of 2
            return self._bits((n - 1).bit_length())
        while True:
            bits = self._bits(31)                                            # [0, 2^31)
            v    = bits % n
            if bits - v + (n - 1) <= 0x7FFFFFFF:                            # reject on int32 overflow
                return v

    def next_int_range(self, lo: int, hi: int) -> int:
        return lo + self.next_int(hi - lo)

    def next_long_range(self, lo: int, hi: int) -> int:
        n = hi - lo
        if n <= 0x7FFFFFFF:                                                  # fits in positive Int
            return lo + self.next_int(n)
        while True:
            hi32 = _u32(self._bits(32))                                      # nextBits(32) as unsigned
            lo31 = self._bits(31)                                            # nextBits(31)
            bits = ctypes.c_int64((hi32 << 32) | lo31).value
            val  = bits % n
            if bits - val + (n - 1) >= 0:
                return lo + ctypes.c_int64(val).value

    def next_float(self) -> float:
        return self._bits(24) / (1 << 24)

    def next_bool(self) -> bool:
        return self._bits(1) != 0

    def choice(self, seq: list):
        return seq[self.next_int(len(seq))]


def java_hashcode(s: str) -> int:
    """Java/Kotlin String.hashCode() — 32-bit signed integer."""
    h = ctypes.c_int32(0).value
    for c in s:
        h = ctypes.c_int32(31 * h + ord(c)).value
    return h


def _round(x: float) -> int:
    """Kotlin roundToInt() — round half toward positive infinity."""
    return math.floor(x + 0.5)


# ---------------------------------------------------------------------------
# Grid constants
# ---------------------------------------------------------------------------

GRID_COLS = 16
GRID_ROWS = 6
GRID_SIZE = GRID_COLS * GRID_ROWS  # 96

# ---------------------------------------------------------------------------
# GameLoop constants
# ---------------------------------------------------------------------------

MOISTURE_BASELINE       = 35
DECAY_DELTA             = 1
DELUGE_CASUALTY_RATE    = 0.97
TILE_CAPACITY           = 10
HIGH_VOLATILITY_THRESHOLD = 70

SPLIT_DENSITY_THRESHOLD = 8
SPLIT_MIN_POPULATION    = 400
SPLIT_COOLDOWN_TICKS    = 100
SPLIT_PARENT_SHARE      = 0.60
SPLIT_DEVOTION_CAP      = 80
SPLIT_MIN_FOOD_TICKS    = 1

MAX_SOPHISTICATION              = 10
SOPHISTICATION_POP_MILESTONES   = [500, 1000, 1500]
SOPHISTICATION_DEVOTION_MILESTONES = [75, 90]

PRAYER_THRESHOLD          = 60
PRAYER_PRESSURE_CAP       = 200.0
SKEPTICISM_DECAY_BASE     = 10.0
SKEPTICISM_RATE_CLAMP_MAX = 2.0
FAITH_DRIFT_SCALE         = 0.05
SOPH_MOISTURE_CEILING     = 50

RAID_DEVOTION_SUPPRESSION_DIVISOR = 200.0
ATTACK_SOPHISTICATION_BONUS       = 0.04
DEFENSE_SOPHISTICATION_BONUS      = 0.03

# Tribe normalisation constants (mirror Tribe.kt companion)
MAX_POP               = 2000.0
MAX_FOOD              = 5000.0
MAX_DELTA             = 200
MAX_AGE               = 10000
MAX_TRIBES            = 8
_SKEP_RATE_NORM       = 2.0
_SOPH_NORM            = 10.0

# Need thresholds (mirror Tribe.kt)
PARCHED_MOISTURE_THRESHOLD      = 25
WATERLOGGED_MOISTURE_THRESHOLD  = 60
HUNGRY_FOOD_TICKS               = 3
UNDER_THREAT_TICKS              = 5
MIN_VIABLE_POPULATION           = 20
SPIRITUALLY_DEPLETED_SKEPTICISM = 60
SPIRITUALLY_DEPLETED_DEVOTION   = 40
NEEDS_DENSITY_THRESHOLD         = 8

STATE_VECTOR_SIZE = 19 + 4 * MAX_TRIBES  # 51

# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------

class BiomeType(Enum):
    Grassland = "Grassland"
    Forest    = "Forest"
    Desert    = "Desert"
    Coast     = "Coast"
    Water     = "Water"

    @property
    def moisture_baseline(self) -> int:
        return {BiomeType.Grassland: 35, BiomeType.Forest: 45, BiomeType.Desert: 15,
                BiomeType.Coast: 35, BiomeType.Water: 50}[self]

    @property
    def weather_resistance(self) -> float:
        return {BiomeType.Grassland: 1.00, BiomeType.Forest: 0.75, BiomeType.Desert: 0.50,
                BiomeType.Coast: 1.00, BiomeType.Water: 0.00}[self]

    @property
    def volatility_gain(self) -> int:
        return {BiomeType.Grassland: 5, BiomeType.Forest: 3, BiomeType.Desert: 8,
                BiomeType.Coast: 6, BiomeType.Water: 0}[self]


def _env_food_mult(moisture: int) -> float:
    if moisture >= 81: return 0.0
    if moisture >= 61: return 0.5
    if moisture >= 21: return 1.5
    return 0.1


class WeatherType(Enum):
    RainCloud = "RainCloud"
    HeatWave  = "HeatWave"

    @property
    def moisture_delta(self) -> int:
        return {WeatherType.RainCloud: 15, WeatherType.HeatWave: -12}[self]


class TribeNeed(Enum):
    Parched            = "Parched"
    Waterlogged        = "Waterlogged"
    Hungry             = "Hungry"
    Starving           = "Starving"
    Endangered         = "Endangered"
    UnderThreat        = "UnderThreat"
    SpirituallyDepleted = "SpirituallyDepleted"
    Overcrowded        = "Overcrowded"
    Thriving           = "Thriving"

# ---------------------------------------------------------------------------
# Name generator  (mirrors TribeNameGenerator.kt exactly)
# ---------------------------------------------------------------------------

_ADJECTIVES = [
    "Ashen","Bone","Cinder","Crimson","Dusk",
    "Ember","Flint","Gilded","Hollow","Iron",
    "Obsidian","Pale","Sable","Salt","Scarlet",
    "Silent","Smoldering","Stone","Thorn","Verdant",
]
_EPITHETS = [
    "Blessed","Born","Bound","Eaters","Fallen",
    "Forged","Keepers","Kindled","Kin","Made",
    "Marked","Risen","Runners","Scarred","Singers",
    "Sworn","Touched","Walkers","Watchers","Woven",
]

def generate_tribe_name(seed: int) -> str:
    rng = JavaRandom(seed)
    return f"The {_ADJECTIVES[rng.next_int(len(_ADJECTIVES))]}-{_EPITHETS[rng.next_int(len(_EPITHETS))]}"

# ---------------------------------------------------------------------------
# Data models
# ---------------------------------------------------------------------------

@dataclass
class TribePersonality:
    archetype_id:   str
    aggression:     float
    caution:        float
    skepticism_rate: float
    traditionalism: float
    biome_affinity: dict
    sophistication: int   = 0
    skepticism:     int   = 0

    def affinity_for(self, biome: BiomeType) -> float:
        return self.biome_affinity.get(biome.value, 1.0)

    def copy(self, **kw) -> TribePersonality:
        d = {**self.__dict__, "biome_affinity": dict(self.biome_affinity)}
        d.update(kw)
        return TribePersonality(**d)


def _agrarian()  -> TribePersonality:
    return TribePersonality("agrarian",  0.2,0.7,0.6,0.7, {"Forest":1.5,"Grassland":1.2,"Desert":0.3,"Coast":1.0,"Water":0.0})
def _nomadic()   -> TribePersonality:
    return TribePersonality("nomadic",   0.5,0.3,0.8,0.3, {"Forest":0.7,"Grassland":1.0,"Desert":1.4,"Coast":0.8,"Water":0.0})
def _warlike()   -> TribePersonality:
    return TribePersonality("warlike",   0.9,0.2,1.4,0.4, {"Forest":0.9,"Grassland":1.1,"Desert":0.8,"Coast":0.7,"Water":0.0})
def _maritime()  -> TribePersonality:
    return TribePersonality("maritime",  0.3,0.5,0.5,0.6, {"Forest":0.8,"Grassland":1.0,"Desert":0.4,"Coast":2.0,"Water":0.0})
def _reclusive() -> TribePersonality:
    return TribePersonality("reclusive", 0.1,0.9,1.2,0.9, {"Forest":1.8,"Grassland":0.9,"Desert":0.2,"Coast":0.6,"Water":0.0})

ALL_ARCHETYPES = [_agrarian, _nomadic, _warlike, _maritime, _reclusive]


@dataclass
class MapTile:
    id:               int
    col:              int
    row:              int
    soil_moisture:    int   = 50
    volatility:       int   = 0
    occupant_tribe_id: Optional[str] = None
    biome:            BiomeType = BiomeType.Grassland

    def copy(self, **kw) -> MapTile:
        d = dict(id=self.id, col=self.col, row=self.row, soil_moisture=self.soil_moisture,
                 volatility=self.volatility, occupant_tribe_id=self.occupant_tribe_id, biome=self.biome)
        d.update(kw)
        return MapTile(**d)


@dataclass
class Tribe:
    tribe_id:              str
    name:                  str
    population:            int
    devotion:              int
    food_supply:           int
    personality:           TribePersonality = field(default_factory=_agrarian)
    generation_deaths:     int   = 0
    prayer_pressure:       float = 0.0
    skepticism_decay_buffer: float = 0.0
    divine_shield_ticks:   int   = 0
    last_raid_tick:        int   = -1
    population_delta:      int   = 0
    territory_delta:       int   = 0
    hostility:             dict  = field(default_factory=dict)
    founded_tick:          int   = 0

    def needs(self, owned_tiles: list, current_tick: int) -> set:
        r = set()
        avg_m = 50 if not owned_tiles else sum(t.soil_moisture for t in owned_tiles) // len(owned_tiles)
        if avg_m < PARCHED_MOISTURE_THRESHOLD:      r.add(TribeNeed.Parched)
        if avg_m > WATERLOGGED_MOISTURE_THRESHOLD:  r.add(TribeNeed.Waterlogged)
        if self.food_supply < self.population * HUNGRY_FOOD_TICKS: r.add(TribeNeed.Hungry)
        if self.food_supply == 0:                   r.add(TribeNeed.Starving)
        if self.population < MIN_VIABLE_POPULATION: r.add(TribeNeed.Endangered)
        if self.last_raid_tick >= 0 and current_tick - self.last_raid_tick <= UNDER_THREAT_TICKS:
            r.add(TribeNeed.UnderThreat)
        if self.personality.skepticism > SPIRITUALLY_DEPLETED_SKEPTICISM and self.devotion < SPIRITUALLY_DEPLETED_DEVOTION:
            r.add(TribeNeed.SpirituallyDepleted)
        if owned_tiles and self.population > NEEDS_DENSITY_THRESHOLD * len(owned_tiles):
            r.add(TribeNeed.Overcrowded)
        if not r:
            r.add(TribeNeed.Thriving)
        return r

    def to_float_array(self, owned_tiles: list, neighbors: list, all_tiles: list, current_tick: int) -> list:
        nds  = self.needs(owned_tiles, current_tick)
        nbrs = sorted(neighbors, key=lambda t: t.tribe_id)[:MAX_TRIBES]
        nids = [n.tribe_id for n in nbrs]
        v = [0.0] * STATE_VECTOR_SIZE
        i = 0
        def c(x): return min(1.0, max(0.0, x))
        v[i]=c(self.population/MAX_POP);            i+=1
        v[i]=c(self.food_supply/MAX_FOOD);          i+=1
        v[i]=c(self.devotion/100.0);                i+=1
        v[i]=c(self.personality.skepticism/100.0);  i+=1
        v[i]=c(len(owned_tiles)/GRID_SIZE);         i+=1
        v[i]=c(self.personality.aggression);        i+=1
        v[i]=c(self.personality.caution);           i+=1
        v[i]=c(self.personality.skepticism_rate/_SKEP_RATE_NORM); i+=1
        v[i]=c(self.personality.sophistication/_SOPH_NORM);       i+=1
        v[i]=c((self.population_delta/MAX_DELTA+1.0)/2.0); i+=1
        v[i]=c((self.territory_delta/MAX_DELTA+1.0)/2.0);  i+=1
        for j in range(MAX_TRIBES):
            v[i] = c(self.hostility.get(nids[j],0.0)) if j<len(nids) else 0.0; i+=1
        v[i]=c((current_tick-self.founded_tick)/MAX_AGE); i+=1
        for nd in [TribeNeed.Parched,TribeNeed.Hungry,TribeNeed.Starving,
                   TribeNeed.Endangered,TribeNeed.UnderThreat,
                   TribeNeed.SpirituallyDepleted,TribeNeed.Overcrowded]:
            v[i] = 1.0 if nd in nds else 0.0; i+=1
        for j in range(MAX_TRIBES):
            if j < len(nbrs):
                nb = nbrs[j]
                nt = sum(1 for t in all_tiles if t.occupant_tribe_id == nb.tribe_id)
                v[i]=c(nb.population/MAX_POP);    i+=1
                v[i]=c(nt/GRID_SIZE);             i+=1
                v[i]=c(nb.hostility.get(self.tribe_id,0.0)); i+=1
            else:
                v[i]=0.0; i+=1; v[i]=0.0; i+=1; v[i]=0.0; i+=1
        return v

    def copy(self, **kw) -> Tribe:
        d = dict(tribe_id=self.tribe_id,name=self.name,population=self.population,
                 devotion=self.devotion,food_supply=self.food_supply,personality=self.personality,
                 generation_deaths=self.generation_deaths,prayer_pressure=self.prayer_pressure,
                 skepticism_decay_buffer=self.skepticism_decay_buffer,
                 divine_shield_ticks=self.divine_shield_ticks,last_raid_tick=self.last_raid_tick,
                 population_delta=self.population_delta,territory_delta=self.territory_delta,
                 hostility=dict(self.hostility),founded_tick=self.founded_tick)
        d.update(kw)
        return Tribe(**d)


@dataclass
class WeatherFront:
    type:      WeatherType
    column:    int
    direction: int


@dataclass
class WorldState:
    world_time_tick: int
    divine_favor:    int
    tiles:           list
    tribes:          dict
    event_history:   list = field(default_factory=list)
    event_cooldowns: dict = field(default_factory=dict)
    active_front:    Optional[WeatherFront] = None
    next_spawn_tick: int = 10
    last_split_tick: int = 0

    def copy(self, **kw) -> WorldState:
        d = dict(world_time_tick=self.world_time_tick,divine_favor=self.divine_favor,
                 tiles=list(self.tiles),tribes=dict(self.tribes),
                 event_history=list(self.event_history),event_cooldowns=dict(self.event_cooldowns),
                 active_front=self.active_front,next_spawn_tick=self.next_spawn_tick,
                 last_split_tick=self.last_split_tick)
        d.update(kw)
        return WorldState(**d)

    def reward(self, prev: WorldState, tribe_id: str) -> float:
        if tribe_id not in self.tribes:
            return -10.0
        prev_pop   = prev.tribes[tribe_id].population if tribe_id in prev.tribes else 0
        prev_tiles = sum(1 for t in prev.tiles if t.occupant_tribe_id == tribe_id) if tribe_id in prev.tribes else 0
        new_pop    = self.tribes[tribe_id].population
        new_tiles  = sum(1 for t in self.tiles if t.occupant_tribe_id == tribe_id)
        return float(new_pop - prev_pop + new_tiles - prev_tiles)

# ---------------------------------------------------------------------------
# Hex neighbour function  (flat-top, even-q offset — mirrors TileNeighbors.kt)
# ---------------------------------------------------------------------------

def get_neighbors(tile_id: int, cols: int = GRID_COLS, rows: int = GRID_ROWS) -> list:
    col, row = tile_id % cols, tile_id // cols
    if col % 2 == 0:
        dirs = [(col,row-1),(col+1,row-1),(col+1,row),(col,row+1),(col-1,row),(col-1,row-1)]
    else:
        dirs = [(col,row-1),(col+1,row),(col+1,row+1),(col,row+1),(col-1,row+1),(col-1,row)]
    return [r*cols+c for c,r in dirs if 0<=c<cols and 0<=r<rows]

# ---------------------------------------------------------------------------
# Policy interface & implementations
# ---------------------------------------------------------------------------

class TribePolicy:
    def choose_expansion(self, tribe: Tribe, candidates: list) -> Optional[MapTile]: ...
    def choose_raid(self, tribe: Tribe, targets: list) -> Optional[object]: ...


@dataclass
class RaidCandidate:
    tile:              MapTile
    defender_tribe_id: str
    defender:          Tribe


class HeuristicPolicy(TribePolicy):
    def __init__(self, rng: Optional[JavaRandom] = None):
        self.rng = rng or JavaRandom(0)

    def choose_expansion(self, tribe: Tribe, candidates: list) -> Optional[MapTile]:
        if not candidates: return None
        return max(candidates, key=lambda t: tribe.personality.affinity_for(t.biome) * (t.soil_moisture / 100.0))

    def choose_raid(self, tribe: Tribe, targets: list) -> Optional[RaidCandidate]:
        if not targets: return None
        first = targets[0]
        atk = 1.0 + tribe.personality.sophistication * ATTACK_SOPHISTICATION_BONUS
        dfs = 1.0 - first.defender.personality.sophistication * DEFENSE_SOPHISTICATION_BONUS
        sup = 1.0 - tribe.devotion / RAID_DEVOTION_SUPPRESSION_DIVISOR
        thr = min(1.0, max(0.0, tribe.personality.aggression * (1.0 - first.defender.personality.caution) * atk * dfs * sup))
        if self.rng.next_float() >= thr: return None
        return self.rng.choice(targets)

# ---------------------------------------------------------------------------
# Simulation steps
# ---------------------------------------------------------------------------

def decay_step(tiles: list, tribes: dict) -> list:
    out = []
    for t in tiles:
        soph = tribes[t.occupant_tribe_id].personality.sophistication if t.occupant_tribe_id in tribes else 0
        eb   = min(t.biome.moisture_baseline + soph, SOPH_MOISTURE_CEILING)
        m    = t.soil_moisture
        if   m > eb: m = max(eb, m - DECAY_DELTA)
        elif m < eb: m = min(eb, m + DECAY_DELTA)
        out.append(t.copy(soil_moisture=m, volatility=max(0, t.volatility - DECAY_DELTA)))
    return out


def territory_step(tiles: list, tribes: dict, policy: TribePolicy) -> list:
    w = list(tiles)
    for tid, tribe in tribes.items():
        expected = max(0, tribe.population * GRID_SIZE // 500)
        occ = [i for i,t in enumerate(w) if t.occupant_tribe_id == tid]
        excess = len(occ) - expected
        if excess > 0:
            for idx in occ[-excess:]:
                w[idx] = w[idx].copy(occupant_tribe_id=None)
        elif excess < 0:
            deficit    = -excess
            occ_ids    = {w[i].id for i in occ}
            nbr_ids    = set()
            for oid in occ_ids: nbr_ids.update(get_neighbors(oid))
            frontier   = [i for i,t in enumerate(w)
                          if t.occupant_tribe_id is None and t.biome != BiomeType.Water and t.id in nbr_ids]
            remaining  = list(frontier)
            for _ in range(deficit):
                chosen = policy.choose_expansion(tribe, [w[i] for i in remaining])
                if chosen is None: break
                ci = next(i for i in remaining if w[i].id == chosen.id)
                remaining.remove(ci)
                w[ci] = w[ci].copy(occupant_tribe_id=tid)
    return w


def conflict_step(state: WorldState, rng: JavaRandom, policy: TribePolicy) -> WorldState:
    if len(state.tribes) < 2: return state
    tids      = list(state.tribes.keys())
    chronicle = []
    raided    = set()
    hchanges  : dict = {}
    tiles     = list(state.tiles)

    for aid in tids:
        aggressor = state.tribes.get(aid)
        if aggressor is None: continue
        for did in tids:
            if aid == did: continue
            defender = state.tribes.get(did)
            if defender is None: continue
            atk_ids  = {t.id for t in tiles if t.occupant_tribe_id == aid}
            borders  = [t for t in tiles if t.occupant_tribe_id == did
                        and any(n in atk_ids for n in get_neighbors(t.id))]
            if not borders: continue
            if defender.divine_shield_ticks > 0: continue
            cands  = [RaidCandidate(t, did, defender) for t in borders]
            chosen = policy.choose_raid(aggressor, cands)
            if chosen is not None:
                tiles = [t.copy(occupant_tribe_id=aid) if t.id == chosen.tile.id else t for t in tiles]
                raided.add(did)
                hchanges.setdefault(aid,{})[did] = hchanges.get(aid,{}).get(did,0.0)+0.1
                hchanges.setdefault(did,{})[aid] = hchanges.get(did,{}).get(aid,0.0)+0.15
                chronicle.append(f"The {aggressor.name} raid the {defender.name} frontier.")

    if not chronicle and not raided: return state
    new_tribes = {}
    for tid, tribe in state.tribes.items():
        ch = hchanges.get(tid, {})
        nh = dict(tribe.hostility)
        for oid, delta in ch.items():
            nh[oid] = min(1.0, nh.get(oid, 0.0) + delta)
        new_tribes[tid] = tribe.copy(
            last_raid_tick=state.world_time_tick if tid in raided else tribe.last_raid_tick,
            hostility=nh)
    return state.copy(tiles=tiles, tribes=new_tribes, event_history=state.event_history+chronicle)


def split_step(state: WorldState, rng: JavaRandom) -> WorldState:
    if state.world_time_tick < state.last_split_tick + SPLIT_COOLDOWN_TICKS:
        return state
    for tid, tribe in state.tribes.items():
        occ = [t for t in state.tiles if t.occupant_tribe_id == tid and t.biome != BiomeType.Water]
        if tribe.population < SPLIT_MIN_POPULATION:                  continue
        if not occ:                                                   continue
        if tribe.population // len(occ) <= SPLIT_DENSITY_THRESHOLD:  continue
        if tribe.devotion > SPLIT_DEVOTION_CAP:                      continue

        cc = sum(t.col for t in occ) / len(occ)
        cr = sum(t.row for t in occ) / len(occ)
        by_dist     = sorted(occ, key=lambda t: (t.col-cc)**2+(t.row-cr)**2)
        p_count     = _round(len(by_dist) * SPLIT_PARENT_SHARE)
        child_ids   = {t.id for t in by_dist[p_count:]}
        child_id    = f"{tid}-{state.world_time_tick}"
        child_name  = generate_tribe_name(java_hashcode(child_id))
        p_pop       = _round(tribe.population  * SPLIT_PARENT_SHARE)
        p_food      = _round(tribe.food_supply * SPLIT_PARENT_SHARE)
        c_pop       = tribe.population  - p_pop
        c_food      = tribe.food_supply - p_food
        if c_food < c_pop * SPLIT_MIN_FOOD_TICKS: continue

        p = tribe.personality
        cp = TribePersonality(
            archetype_id=p.archetype_id,
            aggression   =min(1.0,max(0.0,p.aggression   +rng.next_float()*0.3-0.15)),
            caution      =min(1.0,max(0.0,p.caution       +rng.next_float()*0.3-0.15)),
            skepticism_rate=max(0.0,       p.skepticism_rate+rng.next_float()*0.3-0.15),
            traditionalism=min(1.0,max(0.0,p.traditionalism+rng.next_float()*0.3-0.15)),
            biome_affinity={k:min(3.0,max(0.1,v+rng.next_float()*0.3-0.15)) for k,v in p.biome_affinity.items()},
            sophistication=0, skepticism=0,
        )
        nt = [t.copy(occupant_tribe_id=child_id) if t.id in child_ids else t for t in state.tiles]
        new_tribes = dict(state.tribes)
        new_tribes[tid]      = tribe.copy(population=p_pop, food_supply=p_food)
        new_tribes[child_id] = Tribe(tribe_id=child_id, name=child_name, population=c_pop,
                                     devotion=tribe.devotion, food_supply=c_food,
                                     personality=cp, founded_tick=state.world_time_tick)
        return state.copy(tiles=nt, tribes=new_tribes, last_split_tick=state.world_time_tick,
                          event_history=state.event_history+[
                              f"The {tribe.name} fractures. The dissenters call themselves {child_name}."])
    return state


def weather_step(state: WorldState, rng: JavaRandom) -> WorldState:
    w = state
    if w.active_front is None and w.world_time_tick >= w.next_spawn_tick:
        edge  = 0 if rng.next_bool() else GRID_COLS-1
        dirn  = 1 if edge == 0 else -1
        wtype = WeatherType.RainCloud if rng.next_bool() else WeatherType.HeatWave
        side  = "western" if dirn > 0 else "eastern"
        warn  = (f"Dark clouds gather on the {side} horizon…" if wtype == WeatherType.RainCloud
                 else f"A shimmering heat bends the {side} horizon…")
        w = w.copy(active_front=WeatherFront(wtype, edge, dirn), event_history=w.event_history+[warn])

    front = w.active_front
    if front is None: return w

    new_tiles = []
    for t in w.tiles:
        if t.col != front.column or t.biome == BiomeType.Water:
            new_tiles.append(t); continue
        delta = _round(front.type.moisture_delta * t.biome.weather_resistance * (1.0 + t.volatility/100.0))
        new_tiles.append(t.copy(
            soil_moisture=min(100,max(0,t.soil_moisture+delta)),
            volatility   =min(100,t.volatility+t.biome.volatility_gain)))

    nx     = front.column + front.direction
    exited = nx < 0 or nx >= GRID_COLS
    extreme = None
    if any(t.col==front.column and t.biome!=BiomeType.Water and t.volatility>HIGH_VOLATILITY_THRESHOLD for t in new_tiles):
        extreme = ("A great storm tears through the valley." if front.type==WeatherType.RainCloud
                   else "The land cracks and bleaches under relentless heat.")
    extra = [e for e in [extreme, "The storm has passed. The land is still." if exited else None] if e]
    return w.copy(
        tiles=new_tiles,
        active_front=None if exited else WeatherFront(front.type, nx, front.direction),
        next_spawn_tick=w.world_time_tick+rng.next_long_range(20,41) if exited else w.next_spawn_tick,
        event_history=w.event_history+extra)

# ---------------------------------------------------------------------------
# Main tick function  (no divine-action support — pass action=None always)
# ---------------------------------------------------------------------------

def tick(state: WorldState, rng: JavaRandom, policy: Optional[TribePolicy] = None) -> WorldState:
    if policy is None: policy = HeuristicPolicy(rng)

    prev_pop   = {tid: t.population for tid,t in state.tribes.items()}
    prev_tiles = {tid: sum(1 for t in state.tiles if t.occupant_tribe_id==tid) for tid in state.tribes}

    # 1. Decay
    w = state.copy(tiles=decay_step(state.tiles, state.tribes))

    # 2. Survival + generational drift
    gen_entries = []
    survived    = {}
    for tid, tribe in w.tribes.items():
        occ      = [t for t in w.tiles if t.occupant_tribe_id == tid]
        soph_b   = min(tribe.personality.sophistication, MAX_SOPHISTICATION) * 0.02
        if not occ:
            eff_mult = 1.0
        else:
            eff_mult = sum(_env_food_mult(t.soil_moisture)*(1.0+soph_b*tribe.personality.affinity_for(t.biome))
                           for t in occ) / len(occ)
        farmers  = tribe.population if not occ else min(tribe.population, len(occ)*TILE_CAPACITY)
        farmed   = _round(farmers * 0.70 * eff_mult)
        nf       = tribe.food_supply + farmed - tribe.population

        if nf < 0:
            a = tribe.copy(food_supply=0,
                           population=max(0, min(tribe.population-1, _round(tribe.population*0.90))),
                           devotion=max(0,tribe.devotion-3))
        elif nf > 0:
            a = tribe.copy(food_supply=nf,
                           population=max(tribe.population+1, _round(tribe.population*1.02)),
                           devotion=min(100,tribe.devotion+1))
        else:
            a = tribe.copy(food_supply=0)

        if any(t.soil_moisture >= 81 for t in occ):
            a = a.copy(population=_round(a.population*DELUGE_CASUALTY_RATE))

        deaths       = max(0, tribe.population - a.population)
        new_gd       = a.generation_deaths + deaths
        half_pop     = a.population // 2
        if half_pop > 0 and new_gd >= half_pop:
            trad     = a.personality.traditionalism
            new_skep = _round(a.personality.skepticism * trad)
            nudge    = _round((1.0-trad)*10.0)
            if   a.devotion > 50: nd = max(50, a.devotion-nudge)
            elif a.devotion < 50: nd = min(50, a.devotion+nudge)
            else:                 nd = a.devotion
            gen_entries.append(f"A new generation of the {a.name} comes of age, carrying echoes of the past.")
            a = a.copy(generation_deaths=0, devotion=nd, personality=a.personality.copy(skepticism=new_skep))
        else:
            a = a.copy(generation_deaths=new_gd)
        survived[tid] = a

    # 3. Extinction
    extinct     = {tid for tid,t in survived.items() if t.population <= 0}
    ext_entries = [f"The {survived[tid].name} have perished from the land." for tid in extinct]
    living      = {tid:t for tid,t in survived.items() if tid not in extinct}
    tiles       = w.tiles
    if extinct:
        tiles   = [t.copy(occupant_tribe_id=None) if t.occupant_tribe_id in extinct else t for t in tiles]
    w           = w.copy(tiles=tiles)

    # 4. Hostility decay
    decayed = {}
    for tid, tribe in living.items():
        df = 0.96 if tribe.devotion > 70 else 0.98
        nh = {k:v*df for k,v in tribe.hostility.items() if k not in extinct and v*df >= 0.01}
        decayed[tid] = tribe.copy(hostility=nh)

    # 5. Sophistication milestones
    soph_entries = []
    with_soph    = {}
    for tid, tribe in decayed.items():
        pm = sum(1 for m in SOPHISTICATION_POP_MILESTONES      if m <= tribe.population)
        dm = sum(1 for m in SOPHISTICATION_DEVOTION_MILESTONES if m <= tribe.devotion)
        if tribe.personality.sophistication < pm + dm:
            soph_entries.append(f"The {tribe.name} advances — their mastery of the land deepens.")
            drift = (1.0-tribe.personality.traditionalism)*FAITH_DRIFT_SCALE
            nr    = min(SKEPTICISM_RATE_CLAMP_MAX, max(0.0, tribe.personality.skepticism_rate+drift))
            tribe = tribe.copy(personality=tribe.personality.copy(sophistication=tribe.personality.sophistication+1, skepticism_rate=nr))
        with_soph[tid] = tribe

    # 6. Prayer pressure + passive skepticism decay
    pray_entries = []
    with_pray    = {}
    for tid, tribe in with_soph.items():
        np = tribe.prayer_pressure + (tribe.devotion-PRAYER_THRESHOLD if tribe.devotion > PRAYER_THRESHOLD else 0)
        if np >= PRAYER_PRESSURE_CAP:
            pray_entries.append(f"The prayers of {tribe.name} go unanswered. Doubt spreads among the faithful.")
            fp = 0.0; sk = min(100, tribe.personality.skepticism+1)
        else:
            fp = np;  sk = tribe.personality.skepticism
        dr  = max(0.01, tribe.personality.skepticism_rate)
        nb  = tribe.skepticism_decay_buffer + 1.0/dr
        if nb >= SKEPTICISM_DECAY_BASE: fb=0.0;  sk=max(0,sk-1)
        else:                            fb=nb
        with_pray[tid] = tribe.copy(prayer_pressure=fp, skepticism_decay_buffer=fb,
                                    personality=tribe.personality.copy(skepticism=sk))

    # 7. Favor regen
    regen = max((_round(t.devotion*3//100 * (1.0-t.personality.skepticism/200.0)) for t in with_pray.values()), default=0)
    new_favor = min(100, w.divine_favor + regen)

    # 8. Territory step + tick increment
    post = w.copy(world_time_tick=w.world_time_tick+1, divine_favor=new_favor, tribes=with_pray,
                  tiles=territory_step(w.tiles, with_pray, policy),
                  event_history=w.event_history+gen_entries+ext_entries+soph_entries+pray_entries)

    # 9-11. Conflict, shield decay, split
    post = conflict_step(post, rng, policy)
    post = post.copy(tribes={tid:t.copy(divine_shield_ticks=max(0,t.divine_shield_ticks-1))
                              for tid,t in post.tribes.items()})
    post = split_step(post, rng)

    # 12. Weather
    post = weather_step(post, rng)

    # 13. Finalise deltas
    final_tribes = {}
    for tid, tribe in post.tribes.items():
        if tid not in prev_pop:
            final_tribes[tid] = tribe.copy(population_delta=0, territory_delta=0)
        else:
            nt = sum(1 for t in post.tiles if t.occupant_tribe_id==tid)
            final_tribes[tid] = tribe.copy(population_delta=tribe.population-prev_pop[tid],
                                           territory_delta=nt-prev_tiles[tid])
    return post.copy(tribes=final_tribes)

# ---------------------------------------------------------------------------
# World initialisation  (mirrors WorldState.kt)
# ---------------------------------------------------------------------------

def _build_world(rng: JavaRandom, tribe_id: str, tribe_name: str, population: int,
                 personality: TribePersonality, training_mode: bool = False,
                 all_start_cells: list = None, tribe_idx: int = 0,
                 tiles_working: list = None) -> tuple:
    """Shared biome-generation logic used by initial() and initial_for_training()."""
    cell_count = GRID_COLS * GRID_ROWS

    water_cells = set()
    for _ in range(rng.next_int_range(1, 3)):
        centre     = rng.next_int(cell_count)
        crow, ccol = centre//GRID_COLS, centre%GRID_COLS
        blob_size  = rng.next_int_range(8, 13)
        scores     = []
        for ci in range(cell_count):
            r,c   = ci//GRID_COLS, ci%GRID_COLS
            score = math.sqrt(float(r-crow)**2+float(c-ccol)**2) + rng.next_float()*0.8
            scores.append((ci, score))
        scores.sort(key=lambda x: x[1])
        water_cells.update(ci for ci,_ in scores[:blob_size])

    coast_cells = set()
    for ci in range(cell_count):
        if ci in water_cells: continue
        c,r = ci%GRID_COLS, ci//GRID_COLS
        if c%2==0: nbrs=[(c,r-1),(c+1,r-1),(c+1,r),(c,r+1),(c-1,r),(c-1,r-1)]
        else:       nbrs=[(c,r-1),(c+1,r),(c+1,r+1),(c,r+1),(c-1,r+1),(c-1,r)]
        if any(0<=nc<GRID_COLS and 0<=nr<GRID_ROWS and nr*GRID_COLS+nc in water_cells for nc,nr in nbrs):
            coast_cells.add(ci)

    biome_map = {}
    for ci in range(cell_count):
        if   ci in water_cells: biome_map[ci] = BiomeType.Water
        elif ci in coast_cells: biome_map[ci] = BiomeType.Coast
        else:
            v = rng.next_int(10)
            biome_map[ci] = BiomeType.Grassland if v<=4 else (BiomeType.Forest if v<=7 else BiomeType.Desert)

    return biome_map, water_cells


def initial() -> WorldState:
    """Single starting tribe — matches Kotlin WorldState.initial() exactly."""
    tribe_id   = "iron-wrought"
    tribe_name = generate_tribe_name(java_hashcode(tribe_id))
    population = 100
    rng        = JavaRandom(java_hashcode(tribe_id))
    cell_count = GRID_COLS * GRID_ROWS

    # Water blobs
    water_cells = set()
    for _ in range(rng.next_int_range(1, 3)):
        centre    = rng.next_int(cell_count)
        crow,ccol = centre//GRID_COLS, centre%GRID_COLS
        blob_size = rng.next_int_range(8, 13)
        scores    = []
        for ci in range(cell_count):
            r,c   = ci//GRID_COLS, ci%GRID_COLS
            score = math.sqrt(float(r-crow)**2+float(c-ccol)**2) + rng.next_float()*0.8
            scores.append((ci,score))
        scores.sort(key=lambda x: x[1])
        water_cells.update(ci for ci,_ in scores[:blob_size])

    # Coast
    coast_cells = set()
    for ci in range(cell_count):
        if ci in water_cells: continue
        c,r = ci%GRID_COLS, ci//GRID_COLS
        if c%2==0: nbrs=[(c,r-1),(c+1,r-1),(c+1,r),(c,r+1),(c-1,r),(c-1,r-1)]
        else:       nbrs=[(c,r-1),(c+1,r),(c+1,r+1),(c,r+1),(c-1,r+1),(c-1,r)]
        if any(0<=nc<GRID_COLS and 0<=nr<GRID_ROWS and nr*GRID_COLS+nc in water_cells for nc,nr in nbrs):
            coast_cells.add(ci)

    # Biomes
    biome_map = {}
    for ci in range(cell_count):
        if   ci in water_cells: biome_map[ci]=BiomeType.Water
        elif ci in coast_cells: biome_map[ci]=BiomeType.Coast
        else:
            v=rng.next_int(10)
            biome_map[ci]=BiomeType.Grassland if v<=4 else (BiomeType.Forest if v<=7 else BiomeType.Desert)

    # Territory  (uses noise — mirrors initial() exactly)
    start_row    = rng.next_int(GRID_ROWS)
    start_col    = rng.next_int(GRID_COLS)
    claimed_count = population * GRID_SIZE // 500
    scores = []
    for id in range(GRID_SIZE):
        row,col = id//GRID_COLS, id%GRID_COLS
        if biome_map.get(id)==BiomeType.Water:
            scores.append((id, float('inf')))
        else:
            dr,dc = float(row-start_row), float(col-start_col)
            scores.append((id, math.sqrt(dr*dr+dc*dc)+rng.next_float()*1.5))
    scores.sort(key=lambda x: x[1])
    occupied_ids = {id for id,_ in scores[:claimed_count]}

    tiles = [MapTile(id=id, col=id%GRID_COLS, row=id//GRID_COLS, biome=biome_map.get(id,BiomeType.Grassland),
                     occupant_tribe_id=tribe_id if id in occupied_ids and biome_map.get(id)!=BiomeType.Water else None)
             for id in range(GRID_SIZE)]

    personality = ALL_ARCHETYPES[rng.next_int(len(ALL_ARCHETYPES))]()
    tribe = Tribe(tribe_id=tribe_id, name=tribe_name, population=population,
                  devotion=50, food_supply=500, personality=personality)
    return WorldState(world_time_tick=0, divine_favor=50, tiles=tiles, tribes={tribe_id: tribe})


def initial_for_training(num_tribes: int, seed: int) -> WorldState:
    """Randomised multi-tribe starting state for RL training."""
    assert 1 <= num_tribes <= MAX_TRIBES
    rng        = JavaRandom(seed)
    cell_count = GRID_COLS * GRID_ROWS

    # Water blobs
    water_cells = set()
    for _ in range(rng.next_int_range(1, 3)):
        centre    = rng.next_int(cell_count)
        crow,ccol = centre//GRID_COLS, centre%GRID_COLS
        blob_size = rng.next_int_range(8, 13)
        scores    = []
        for ci in range(cell_count):
            r,c   = ci//GRID_COLS, ci%GRID_COLS
            score = math.sqrt(float(r-crow)**2+float(c-ccol)**2) + rng.next_float()*0.8
            scores.append((ci,score))
        scores.sort(key=lambda x: x[1])
        water_cells.update(ci for ci,_ in scores[:blob_size])

    coast_cells = set()
    for ci in range(cell_count):
        if ci in water_cells: continue
        c,r = ci%GRID_COLS, ci//GRID_COLS
        if c%2==0: nbrs=[(c,r-1),(c+1,r-1),(c+1,r),(c,r+1),(c-1,r),(c-1,r-1)]
        else:       nbrs=[(c,r-1),(c+1,r),(c+1,r+1),(c,r+1),(c-1,r+1),(c-1,r)]
        if any(0<=nc<GRID_COLS and 0<=nr<GRID_ROWS and nr*GRID_COLS+nc in water_cells for nc,nr in nbrs):
            coast_cells.add(ci)

    biome_map = {}
    for ci in range(cell_count):
        if   ci in water_cells: biome_map[ci]=BiomeType.Water
        elif ci in coast_cells: biome_map[ci]=BiomeType.Coast
        else:
            v=rng.next_int(10)
            biome_map[ci]=BiomeType.Grassland if v<=4 else (BiomeType.Forest if v<=7 else BiomeType.Desert)

    tiles_w = [MapTile(id=id, col=id%GRID_COLS, row=id//GRID_COLS, biome=biome_map.get(id,BiomeType.Grassland))
               for id in range(cell_count)]

    land_cells  = [ci for ci in range(cell_count) if biome_map[ci]!=BiomeType.Water]
    start_cells = []
    min_dsq     = 9
    for _ in range(num_tribes):
        cands = land_cells if not start_cells else [
            ci for ci in land_cells if all(
                (ci//GRID_COLS-sc//GRID_COLS)**2+(ci%GRID_COLS-sc%GRID_COLS)**2 >= min_dsq
                for sc in start_cells)
        ]
        start_cells.append(rng.choice(cands if cands else land_cells))

    tribes_out = {}
    for idx, sc in enumerate(start_cells):
        tid      = f"training-tribe-{idx}"
        tname    = generate_tribe_name(java_hashcode(tid))
        pers     = ALL_ARCHETYPES[rng.next_int(len(ALL_ARCHETYPES))]()
        pop      = 100
        claim    = pop * GRID_SIZE // 500
        srow,scol = sc//GRID_COLS, sc%GRID_COLS
        avail    = [i for i,t in enumerate(tiles_w) if t.biome!=BiomeType.Water and t.occupant_tribe_id is None]
        avail.sort(key=lambda i: (tiles_w[i].row-srow)**2+(tiles_w[i].col-scol)**2)
        for i in avail[:claim]:
            tiles_w[i] = tiles_w[i].copy(occupant_tribe_id=tid)
        tribes_out[tid] = Tribe(tribe_id=tid, name=tname, population=pop, devotion=50, food_supply=500, personality=pers)

    return WorldState(world_time_tick=0, divine_favor=50, tiles=tiles_w, tribes=tribes_out)

# ---------------------------------------------------------------------------
# Headless runner  (for CLI use and parity testing)
# ---------------------------------------------------------------------------

def run_headless(ticks: int, seed: int) -> WorldState:
    rng   = JavaRandom(seed)
    state = initial()
    pol   = HeuristicPolicy(rng)
    for _ in range(ticks):
        state = tick(state, rng, pol)
    return state


if __name__ == "__main__":
    import sys, time
    n     = int(sys.argv[1]) if len(sys.argv)>1 else 1000
    s     = int(sys.argv[2]) if len(sys.argv)>2 else 42
    t0    = time.time()
    final = run_headless(n, s)
    ms    = (time.time()-t0)*1000
    print(f"Completed {n} ticks in {ms:.0f}ms")
    print(f"worldTimeTick  : {final.world_time_tick}")
    print(f"tribes         : {len(final.tribes)}")
    print(f"totalPopulation: {sum(t.population for t in final.tribes.values())}")
    print(f"occupiedTiles  : {sum(1 for t in final.tiles if t.occupant_tribe_id)}")
