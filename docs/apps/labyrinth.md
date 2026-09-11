# Labyrinth

- **Official package:** `Labyrinth.exe` (labyrinth)
- **Corpus:** `Zune HD Apps (Decompiled)/labyrinth` (external, untracked)
- **Wave:** W6 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Labyrinth.exe` | 75 | 291 | 9880 |
| `Labyrinth.Levels.dll` | 0 | — | 15 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZuneCoreLib.dll` | 66 | 275 | 5853 |
| `ZuneGamesLib.dll` | 26 | 134 | 2730 |

## 2. Screens & navigation

- `LoadingScreen` → `MainMenuScreen` (illustrated menu mirroring the Zune
  home layout: Play / Options / About, with an ILLO sign and story art) →
  `LevelSelectScreen`, a vertical curtain-and-rope stage: act dividers with
  story illustrations and a 4-column grid of level buttons, plus four bonus
  buttons at the bottom.
- `GameScreen` hosts the maze with a small pause button; `PauseScreen`
  interposes (resume, restart, options, back) and `LevelEndConfirmScreen`
  confirms abandoning a level. `LevelCompleteScreen`/`LevelCompleteSign`
  show time, best time and par stars; `EndSceneScreen` handles the story
  ending; `ClearDataConfirmScreen`/`ClearDataPrompt` clear save data.
- `OptionsScreen` includes the tilt calibration slider (`TiltSlider`) and
  volume; `AboutScreen` carries credits.

## 3. Rules, scoring & progression + simulation model

- Roll a marble from spawn to the open goal through a themed maze. A level
  is loaded from the current act's content (`Level.LoadCurrentScene`);
  reaching the goal completes it. Holes and spikes consume the marble
  ("MarbleKilled"), respawning it at the last checkpoint; the timer rewinds
  to the checkpoint time on death.
- Pickups: every scene contains collectible `PickupType.Pickup` sprites
  (apple, axe, flower, heart, key art). Collecting all of them opens the
  goal (`Goal.AnimateOpen`); `PickupType.Switch`/checkpoint objects set the
  respawn point and can toggle layers. Goal closed/opened state and
  pickup-driven visibility are part of the scene data.
- Physics: the marble is a radius-13, mass-1 disc with restitution 0.5 and
  a per-step velocity multiplier of 0.97 (friction), speed capped at
  750 px/s. Walls are rectangle/ellipse/polygon collision shapes (level
  data) resolved by shape-specific collision helpers; the marble steps by
  `pos += velocity * dt` then damps.
- Tilt gravity: acceleration = `(-accel.Y, -accel.X) * tiltSetting` at
  1950 px/s², negated in landscape-left; when the tilt opposes the current
  velocity the input is multiplied by 1.05 (braking assist). The tilt
  slider maps 0.5–1.5 with default 1.0.
- Scoring/progression: per-scene `ParTime` and `RecordTime`; beating par
  returns true from `LevelCompleteSign.SetTime`, which spawns the star
  confetti. First completion unlocks the next scene and advances the story
  (`LastCompletedSceneIndex`, `QuitMidLevel`); save data is written to
  `NewSaveData.xml` by the `LevelIndex` singleton. The content ships 112
  scenes across 5 acts plus 4 bonus levels: BB 24, HG 24, JBS 24, RRH 16,
  SW 24; the in-game about text advertises "more than 110 levels".

## 4. Controls

- Steering is purely tilt: the accelerometer vector drives gravity; there
  are no touch-steer controls in play. `TiltSlider` calibrates sensitivity
  (0.5–1.5) and is saved in settings.
- The pause button is a large invisible hit rect across the upper screen
  (50, 50, 380x172); orientation detection is disabled while a level is
  active and re-armed to landscape on exit. Device deactivation
  auto-pauses.
- Menus use fling scrolling (the level select is a `ScrollableView` with a
  rope indicator) and standard button taps.

## 5. Content inventory (must be re-authored)

369 files: 220 PNG, 138 XNB, 6 XML, 5 TXT. In-game art 35 PNG: `marble.png`
28x28, `marble_gold.png` 28x28, shadow/lighting sheets, `hole.png` 36x36,
goal base/cover/top, spawn base/top, 5 pickups (~23x36), particles and 8
spike sprites. UI 23 PNG (480x272 level-select background, boxes, rope
36x272, tilt slider 162x42, Stars 49x16, LevelEndStars 76x141). Buttons 22
(150–327 px wide). Curtain 8 (480x272). Story ILLO 17 at 290x95. Levels:
`Levels/Index.xml` plus 224 level assets (map XNB + 480x272 `bg.png`) in
packs BB/HG/JBS/RRH/SW/Bonus. Sounds 22 (ball roll/hit, inhole, collect,
key, switch, wood, wind, marble killed, level complete, pause). Re-author:
procedural marble/particle art, our own 116 original mazes and backdrops,
synthesized SFX.

## 6. Implementation plan

- Rendering: Compose canvas 2D with a textured marble, shadow and hole
  layers (engine3d is unnecessary). Backdrops are our own generated
  gradients/patterns; pickups and switches are simple vector sprites.
- Logic: `ui/apps/games/LabyrinthEngine.kt` — deterministic 60 Hz step,
  circle-vs-rect/ellipse/polygon collision, checkpoint/respawn, pickup
  counters, par/best times and act unlock state; levels authored as our
  own compact text/JSON data (collision shapes + layers).
- UI: act/story level select with rope scroll, pause/options (including a
  sensitivity slider), level-complete sign with par stars; `SfxBank` for
  roll/hit/collect/goal/fanfare. Marble roll volume scales with speed.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): collision response
  and tunneling guards at max speed, checkpoint rewind, pickup-open-goal
  rule, par-star math, act unlock, save/restore, tilt-to-acceleration
  mapping incl. landscape flip and 1.05 assist.
- Fidelity target: full-parity mechanics; ship 5 acts of original mazes
  (target 116 levels, phased authoring). Risks: collision robustness at
  750 px/s, tilt feel/calibration across devices, and maze authoring
  volume.

## 7. Citation log

- `Labyrinth.exe!GameScreen.Update` (tilt vector, 1950 px/s², 1.05
  assist, 750 cap), `.OnPushedToScreenStack`, `.OnPoppedFromScreenStack`,
  `.level_ReachedGoal`, `.Hole_BallConsumed`, `.Respawn`, `.Reset`,
  `.Retry`, `.pause`
- `Labyrinth.exe!Marble` constants, `.Step`, `.HandlePickupCollisionPoint`
- `Labyrinth.exe!Level.Respawn`, `.Update` (pickup count opens goal),
  `Goal.AnimateOpen`
- `Labyrinth.exe!LevelCompleteSign.SetTime`, `LevelCompleteScreen.SetTime`
- `Labyrinth.exe!LevelSelectScreen.GenerateScrollView`
- `Labyrinth.exe!TiltSlider` (0.5–1.5), `Labyrinth.exe!LevelIndex`
  (`NewSaveData.xml`, acts/scenes/bonus), `Labyrinth.exe!PickupType`
- Content tree `Levels/{BB,HG,JBS,RRH,SW,Bonus}` (112 scenes + 4 bonus,
  counted; no layouts copied)
