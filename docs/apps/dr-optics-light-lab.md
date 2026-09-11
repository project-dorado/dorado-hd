# Dr Optics Light Lab

- **Official package:** `DrOptics.exe` (droptics)
- **Corpus:** `Zune HD Apps (Decompiled)/droptics` (external, untracked)
- **Wave:** W5 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `DrOptics.exe` | 46 | 285 | 8363 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 68 | 304 | 6297 |
| `ZuneGamesLib.dll` | 36 | 170 | 3669 |

## 2. Screens & navigation

- `LoadingScreen` → `MainMenuScreen` (Play / Learn To Play / Options /
  About) → `LevelSelectScreen` with per-difficulty pages of level buttons,
  lock badges, completion percentage and unlock animations.
- In-game `LevelScreen`/`GameScreen` shows an in-scene HUD: completion
  percentage, level description, erase/restart, pause. `PauseScreen`
  interposes (resume/restart/options/back), `OptionsScreen` holds sound
  volume tiers, `EraseConfirmScreen` and `ExitConfirmScreen` confirm
  destructive actions, `AboutScreen`/credits closes with a version block.
- `TutorialScreen` runs the interactive move/rotate/unlock prompts before
  the first real level.
- Level select is grouped by difficulty; a full-screen "unlocking" caption
  plays when a difficulty tier opens.

## 3. Rules, scoring & progression + simulation model

- Laser optics sandbox in a top-down room. Each level is authored in
  `Content/Levels/Levels.xml` with `MaxBeams` (default 75) and `Difficulty`
  attributes; the shipped file contains 41 levels: 8 at difficulty 1, 14 at
  2, 8 at 3, 6 at 4, 4 at 5 and one special difficulty-6 level that reuses
  the menu background.
- A laser (fixed, moving, rotating or orbiting) emits a beam. The beam pool
  holds at most `MaxBeams` beams and recursion depth is `maxBeamDepth = 15`
  (recomputed as `maxBeams / laserCount` when a laser is added), so each
  beam spawns child beams recursively until it hits an object or the depth
  budget runs out. `TestObjectCollision` picks the nearest contact point.
- Mirrors reflect (`dir' = dir - 2*(dir·n)*n`): plane, arc, rectangular,
  polygonal and oscillating mirrors all implement their own contact and
  resultant-beam rules. Lenses refract using their index of refraction and
  the medium under the contact point (Snell's law, with total-internal
  reflection fallback). Rocks block; rock belts and moving sinks animate.
- Sinks are the goals. Each sink only absorbs power from a beam whose
  source-laser color matches (`Red`, `Green`, `Blue`); collected energy
  integrates as `stored += (applied - 0.8*stored) * dt`, clamped to
  [0, 1.25], and is "satisfied" once stored > 1.0. `CompletePercentage` is
  the mean of each sink's fill fraction clamped to 1; the level is solved
  when every sink is satisfied.
- Progression: solved level indices persist to `opticsHistory.xml`
  (`History.Load`/`Save`). Difficulty tiers unlock when enough total levels
  are solved; the computed cutoffs require 6, 16, 20 and 24 solved levels
  for difficulties 2–5 respectively. A `Locked` flag on objects is toggled
  in the optional lock mode; locked objects reject dragging until
  re-enabled. Scores are per-level completion, not points.

## 4. Controls

- One finger drags the selected movable object by its average position
  delta (`PerformTouchUpdates`); picks the closest touchable, unlocked
  object within 5000 squared units and ignores deltas over 100 units.
- Two fingers rotate the object (angle between the pointers is integrated)
  and pinch to grow/shrink it (`UpdatePositionOrientationAndGrowth`).
  Objects are clamped so they stay on the 480x272 logical screen with a
  10-unit pad.
- In lock mode a tap toggles the object's lock state and plays the
  lock/unlock cue. Buttons use the standard pressed-button sound; the game
  pauses on device deactivation.

## 5. Content inventory (must be re-authored)

54 files: 33 PNG (menu/game backgrounds 480x272 — `game_bg1..5`,
`menu_bg`; `logo.png` 469x203; `beams.png` 480x204; buttons 396x66/360x82;
`goal_{red,green,blue}[_full]` 25x25; node dots 13x13; `win_glow` 189x190;
`confirm_box` 344x96; arrows 11x17/27x37; `level_frame_white` 185x106;
`lock` 24x25; `squarehalf_full` 480x272), 16 sound XNBs (ambient loops,
lock/unlock, button, level transitions, completed, error), one authored
`Levels.xml` (41 levels), and 5 XML text files in en/es/fr. Re-author all
art procedurally (room backgrounds, mirror/lens/sink sprites) and the 16
SFX as synthesized one-shots in `SfxBank`.

## 6. Implementation plan

- Rendering: Compose canvas layer inside `DetailScaffold` (the game is 2D
  with textured quads and additive beam strips) — no engine3d needed.
- Logic: `ui/apps/games/OpticsLab.kt` — level schema types (lasers, mirrors,
  lenses, sinks, rocks), beam recursion with depth cap, Snell/reflection
  math, sink energy integration, history/unlock model. Pure Kotlin and
  deterministic from a level ID + object state.
- UI: difficulty/level select lists, HUD percentage, lock-mode toggle;
  `SfxBank` for the 16 cues; haptics optional.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): reflection and
  refraction angles, total-internal reflection, beam depth/count caps,
  nearest-hit selection, sink fill/decay/satisfaction, unlock cutoffs,
  schema parsing of original level files.
- Fidelity target: full-parity rules with original 41-level content.
  Risks: beam recursion cost on device, numerically fragile grazing
  contacts, and the authored level set's difficulty curve.

## 7. Citation log

- `DrOptics.exe!Level` ctor (MaxBeams/Difficulty), `.ResetLevel`,
  `.AddLaser`, `.AddSink`, `.AddLens`, `.IndexOfRefractionOfPoint`,
  `.Update`, `.GetActiveBeam`, `.RecursiveTruncateAndPropogate`,
  `.TestObjectCollision`, `.PerformTouchUpdates`, `.GetNumBeams`,
  `.CompletePercentage`
- `DrOptics.exe!ArcMirror.DoesBeamContact`, `.GetResultantBeam`
- `DrOptics.exe!Lens.DoesBeamContact`, `.GetResultantBeam`
- `DrOptics.exe!Sink` ctor, `.DoesBeamContact`, `.GetResultantBeam`,
  `.UpdatePower`, `.Update`
- `DrOptics.exe!History.Load`, `.LeftToUnlock`, `.AddLevel`, `.Delete`
- `DrOptics.exe!LevelSelectScreen.UnlockLevels`, `.UnlockAllLevels`
- `Content/Levels/Levels.xml` (41 `<Level>` nodes; attribute keys
  `MaxBeams`, `Difficulty`) — counted only, no content copied
