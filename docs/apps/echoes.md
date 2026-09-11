# Echoes

- **Official package:** `Echoes.exe` (echos)
- **Corpus:** `Zune HD Apps (Decompiled)/echos` (external, untracked)
- **Wave:** W6 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Echoes.exe` | 121 | 753 | 20747 |
| `Halfbrick.dll` | 102 | 420 | 9062 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |

## 2. Screens & navigation

- State machine in `Game` (`GameState`): Splash → Title → Difficulty →
  Mode → NumPlayers → LevelSelect (an "Overworld" map of nodes: `AreaMap`,
  `ModeMap`, `CandyMap`, `HippyMap`, `JackpotMap`, `MultiplayerMap`) →
  Instructions → CountDown → Playing → LevelComplete/Victory/GameOver →
  HighScores/Achievements/Credits. Replay and NameInput states interpose
  after big scores.
- Menus are drawn on the arena background (`OptionsMap`, `CreditsMap`);
  `LevelInfo`/`LockedInfo` boxes explain nodes and locks, `Achievements`
  lists the trophy set, and `Marketing/Upsell` handles store entries.
- In-game HUD: lives, score (crystals collected / total), timer/`HUD_Corner_Time`,
  score bars, help boxes, pause and replay buttons; multiplayer shows a
  score panel per player and team-coloured HUD corners.

## 3. Rules, scoring & progression + simulation model

- Arena collect-em-up. Each level is a tile map (`Halfbrick.dll` tile-map
  classes) containing crystals; the goal is to collect every crystal.
  Collecting a crystal raises the score and records a snapshot of the
  player's path; the game replays those snapshots as "echoes" — ghost
  copies of the player's own earlier movement, sampled every
  `SnapshotFrequency = 0.05 s` and replayed by `Echo.SnapshotTraverser`.
  Touching an echo, flyer, riser or hazard costs a life; `lives < 0` ends
  the run, and a hit grants brief invulnerability (`STILL_SAFE_TIME = 0.1`,
  `STILL_SAFE_AFTER_HIT_TIME = 0.25`).
- Player model: a 21-unit-radius puck with move speed 5.25 and dampening
  0.7, rotatable through ±90°, scale 1..5; the trail (fresh/blue dots) is
  drawn behind it. Crystals carry power-ups: TimeFreeze (4.285714 s),
  PlayerGhost (5.714286 s), ExtraLife, PulseRing (0.5 s), MultiRing,
  Shrink (11.428572 s, size 0.4), CrystalMagnet, Secret, Stench.
- Win/lose: in Normal/Arcade, `score >= totalCrystalCount` enters Victory
  (1.5 s flourish) and `LevelComplete` unlocks the next level; in the
  endless modes (CandyStore, Hippy, Survival, Multiplayer) clearing all
  crystals resets the non-secret crystals and keeps the round going;
  Cooperative advances a round once the coop crystal target is met and
  ends when all players are dead; multiplayer teams (ALL_GROW, TUG_O_WAR,
  GAUNTLET, GAUNTLET_LITE, HALF_LIFE, BOUNTY) end on a target score/TUG
  trophy position and record wins.
- Progress: `Progress`/`ProgressRecord` stores per-mode progress, unlock
  index and player name; achievements (12 types: CoolCat, SpeedDemon,
  DareDevil, TreasureSeeker, EchoCenturion, GhostHunter, PulseKing,
  CandyWizard, Veteran, Champion, …) are ranked bronze→jewel via a local
  score tracker. Champion unlocks at campaign indices 4/9/14/21; CoolCat
  tiers at 3/5/7/9 remaining lives. High scores, replays and multiplayer
  stats are saved locally (`HighScores`, `MultiplayerStats`,
  `SharedOptions`).

## 4. Controls

- `MFGamePad` is a virtual gamepad: two analog thumbsticks plus 21
  hit-tested touch regions (`trdata[21]`), with an optional flipped
  left/right control layout (`CONTROLS_FLIPPED`). Movement comes from the
  sticks; `ControllerItem` covers Ok/Cancel/Pause/Replay/Reset and the two
  movement axes for menus and replay scrubbing.
- Replay controls: pause/back and scrubbing in `UpdateReplaying`, with a
  fastest-time replay mode triggered from score screens.
- Difficulty (Easy/Medium/Hard) and mode are chosen with the same
  virtual-pad regions; no accelerometer is used.

## 5. Content inventory (must be re-authored)

241 files: 240 XNB + `Credits.xml`. Levels: 100 files = ~50 tile-map XNBs
plus their `_image` previews (campaign names such as Ampersand, Arena,
Asterisk, Basement, Blueprint, Bridges, Castle, Celtic, Cheese, City,
Crossroads, Crow, Eye, Footprint, Guitar, Horseshoe, Hourglass, Labyrinth,
Ladder, Leaf, Mandelbrot, Music, Peace, plus C-/J-/M-/S-prefixed variants
for the mode maps). Textures (100): player/echo hats, trails, shoes 0–14,
crystals and power-up icons, trophies, safe-zone/tracker/life HUD art;
Particles 6; Audio 22 (collect, death, echo spawn/explosion, grow, shrink,
magnet, pulse ring, saw, screams, clock ticks, countdown); HighScores 5,
Fonts 3. Re-author all art as vector/procedural sprites and the 50 arenas
as our own tile layouts.

## 6. Implementation plan

- Rendering: Compose canvas top-down arena (content is sprite/tile based;
  engine3d is not required). Tile maps loaded from our own compact format;
  echoes and trails drawn as batched paths/dots.
- Logic: `ui/apps/games/EchoesEngine.kt` — deterministic 60 Hz tick,
  player puck physics, 0.05 s snapshot recorder and echo replay, crystal
  spawn/power-up table, hazard AABB/circle checks, mode state machine,
  achievement/progress store. Seeded RNG for reproducible echo runs.
- UI: Overworld node map, HUD, level info/achievements/credits from
  `DetailScaffold`; `SfxBank` for collect/hit/power-up/countdown.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): snapshot replay
  determinism, collision/hit/invulnerability windows, power-up durations,
  mode win/continue rules, unlock ordering, achievement thresholds.
- Fidelity target: full-parity mechanics with original arenas. Risks:
  echo-replay determinism across frame rates, the volume of arena content,
  and multiplayer over the modern stack (scope to local/co-op first).

## 7. Citation log

- `Echoes.exe!Game.UpdatePlaying`, `.LevelComplete`,
  `.GetNumCrystalsRemaining`, `.GetLevelScore`, `.SaveAllData`
- `Echoes.exe!Player` constants (`SnapshotFrequency`, `moveSpeed`,
  `moveDampen`, power-up lengths, `MIN/MAX_SCALE`, `SAFE_ZONE_BUFFER`)
- `Echoes.exe!Echo.SnapshotTraverser` (snapshot replay, forward/reverse)
- `Echoes.exe!Crystal.GetScore`, `.GetPowerupType`, `.Reset`;
  `PowerupType` enum; `GameDifficulty`, `GameMode`, `MultiplayerType`
- `Echoes.exe!MFGamePad` (two sticks, 21 touch regions, controls flip)
- `Echoes.exe!Controller` (`ControllerItem`, thresholds)
- `Echoes.exe!UpdateReplaying`, `ReplayManager`, `HighScores`,
  `Progress`, `Achievements`, `MultiplayerStats`
