# Finger Physics

- **Official package:** `FingerPhysics.exe` (finger_phys)
- **Corpus:** `Zune HD Apps (Decompiled)/finger_phys` (external, untracked)
- **Wave:** W5 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `FingerPhysics.exe` | 185 | 949 | 19556 |

## 2. Screens & navigation

- `StartupController`/`StartupView` splash → `MenuController` title
  (Play / Select Level / Options / About) → `MapController`, a scrollable
  stage map of level cards with score/medal state and pack prices (packs
  1 and 5 are always marked loaded in the child setup, the paid packs are
  gated).
- `HelpController` provides 10 illustrated how-to-play pages, reachable
  from the menu and in-game (it exposes help pages "watched" for the
  tutorial hint flag).
- In-game `GameModeController` owns the HUD and dialogs: rays intro,
  exit button, win / lose / stage-unlocked / goal / timer board / win-time
  label / map name, and a pause overlay with retry, resume, restart,
  options, select-level, next, help, sound/music toggles.
- `GameController` is a small screen stack: level picker, game mode,
  help, two `GameLoadingController` instances (one for packs 1/5, one for
  the others); level load is asynchronous with a progress view.

## 3. Rules, scoring & progression + simulation model

- A 2D rigid-body puzzle. The embedded engine (`fp.cp` namespace) provides
  bodies, circles, segments, polygons, broad-phase hashing, contacts,
  spring/gear/pin/slide/ratchet constraints and a particle/glass-break FX
  layer; the base gravity is (0, 100) units/s² with 3 solver iterations and
  10 elastic iterations, a static broad-phase hash of 30/50 and an active
  hash of 30/100.
- Levels are binary XNB maps parsed from XML-shaped nodes. The header
  ("unknown") tag selects the mode (`level_type` 1..10) and carries all
  tuning: `height_to_win`, `req_bronze`/`req_silver`/`req_gold`,
  `magnetForce` (default 10000), `ribbon_damping`/`ribbon_hardness`,
  `repulsion_factor` (default 100), `shockwave_width` (50) /
  `shockwave_impulsefactor` (100) / `shockwave_speed` (50), and accelerometer
  `accFreq` (default 60 Hz) / `accMultiplier` (default 100). Shapes are
  circles, rectangles, triangles and polygons with per-shape flags
  (static, breakable, explodable, revolving, charged, sink, queued).
- The ten modes named in the binary are Egg, Lawn, Lunar, Magnet blocks,
  Underwater, Explosive blocks, Free, Gravity blocks, Geared blocks and
  Pinned blocks. Several modes share a controller/view family (level type 7
  reuses the volcano view); mode 5 (Underwater) flips gravity to (0, -100);
  magnet mode can weld two charged bodies with a `PinJoint` when
  `magnetForce` is exceeded; two explodable bodies that touch are deleted
  and spawn a `ShockWave` with the level's width/impulse.
- Win condition: the main objective is cleared and the world stays stable
  for 5 seconds (`STABLE_TIME_TO_WIN`), after which `gameWon()` fires.
  Medals are awarded from the per-level thresholds: stacking modes compare
  the highest non-static bounding-box top against the height thresholds;
  other modes compare elapsed time or score, with the HUD drawing the three
  threshold lines/values. Stars are shown per level on the map.
- Progression: in-game about text advertises 10 stages and 90 levels; 91
  binary map files (`levels/01.xnb` … `91.xnb`) plus a `levels.bim` map
  index (icon + file name) drive the stage map. Per-pack unlock/progress
  state is persisted in `fpprefs.bin` (sound, music, best scores).
- A "next shape" queue spawns new pieces on tap; tap flashes and spawn
  sounds play. Anti-cheat flag in the level header disables score tricks.

## 4. Controls

- Tap a queued shape (or the next-shape preview) to launch it into the
  world at the spawn column; in water mode it spawns from below.
- Touch and drag any movable shape: the engine stores the picked body, the
  last touch and the touch offset from the body centre, then drives it with
  the pointer; release drops it back to the solver.
- Two charged bodies in contact can connect (magnet); explodable pairs
  detonate on contact. Ribbon-linked shapes use spring hardness/damping
  from the level header.
- The accelerometer runs at 40 Hz in game and 60 Hz in menus and drives the
  parallax background layers (`GameView.drawBack`), not the physics solver.

## 5. Content inventory (must be re-authored)

358 files: 356 XNB + 2 WMA. The root holds ~265 UI/prop textures — doodle
monsters and towers, fields/backgrounds, basket and counter sprites, stars
(bronze/silver/gold), buttons, loaders, fonts (Arial/SimpleFont equivalents
are our own), plus `Default` textures; `levels/` holds 91 binary maps;
2 WMA music tracks. Re-author everything: cartoon-style procedural/simple
vector art for bodies and backgrounds, code-defined level data for our own
90-level campaign across 10 stage themes, and synthesized music/SFX.

## 6. Implementation plan

- Engine: no engine3d needed — implement a small deterministic 2D
  rigid-body core in `ui/apps/games/FingerPhysics/Physics.kt` (bodies,
  shapes, broad-phase grid, sequential impulses, fixed 1/60 step, joints
  only for the magnet/gear/ribbon features we ship).
- Game logic: `ui/apps/games/FingerPhysics/Game.kt` — mode controllers,
  medal thresholds, stability timer, save/unlock store; levels authored as
  Kotlin data (serialized to JSON under `res/raw/` if needed).
- UI: `DetailScaffold` map/level select, HUD dialogs, pause; `SfxBank`
  for taps/pops/explosions; haptics on impacts.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): body integration
  determinism, contact resolution, joint behavior, medal math, stability
  timer, mode gravity flip, level header parsing.
- Fidelity target: full-parity mechanics with original art and 90 original
  levels. Risks: recreating a stable solver and the sheer level authoring
  volume; some modes (magnet/geared) need constraint parity work.

## 7. Citation log

- `FingerPhysics.exe!GameModel` fields (`phDragingShape`, ribbon/shockwave
  params, `accFreq`/`accMultiplier`, `bronzeReq`…), `.parserElementStart`,
  `.collFunc`/`.collFunc2`/`.collFunc3`, `.addObjectToDelete`
- `FingerPhysics.exe!GameModeController` (`GameResults`, dialog/button
  enums), `.update`
- `FingerPhysics.exe!Mode1Controller.update` (height medals)
- `FingerPhysics.exe!Mode3Controller.activate` (mode 7 view, egg flags)
- `FingerPhysics.exe!Mode4Controller` (scores, touch timer, glamp)
- `FingerPhysics.exe!Mode5Controller` (lives, waves; gravity (0,-100))
- `FingerPhysics.exe!Mode6Controller` (`PLATFORM_MIN_WIDTH`,
  `PLATFORM_Y_DISTANCE`, `platformTables`)
- `FingerPhysics.exe!GameController` (child stack, pack flags),
  `FingerPhysics.exe!MapController`, `FingerPhysics.exe!MapsInfo`
  (`levels.bim` index), `FingerPhysics.exe!HelpController`
- `FingerPhysics.exe!GameView.drawBack` (accelerometer parallax),
  `FingerPhysics.exe!Application.createAccelerometer`
- `FingerPhysics.exe!ShockWave` ctor; `FingerPhysics.exe!BlockitApp`
  (mode names array)
