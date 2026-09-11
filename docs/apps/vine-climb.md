# Vine Climb

- **Official package:** `VineClimb.exe` (vine_climb)
- **Corpus:** `Zune HD Apps (Decompiled)/vine_climb` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `VineClimb.exe` | 32 | 191 | 3742 |
| `2moroGames.dll` | 92 | 413 | 6199 |

- Fixed 60 Hz time step (`TargetElapsedTime = 1/60 s`); 480×272 back buffer
  with an explicit 272×480 portrait render target blitted back rotated.
- `GamePad.Back` exits the app; orientation locked portrait through the shared
  2moroGames touch handler, and settings persist to isolated storage on
  deactivate/exit.

## 2. Screens & navigation

Page enum (in order): loading, splash, main, game, info, help, pause, game-over
(`Constants.PageIDs`). Registered menu pages line up as: loading(0), splash(1),
main(2), game(3), help(5), pause(6), game-over(7); page 4 is reserved but not
registered. Flow:

- Loading → splash (tap plays) → main menu.
- Main menu: title art, PLAY, HELP, sound toggle, high-score readout; transition
  is a drop-down push (`DropDownEffect` both directions).
- HELP page lists collect-items vs avoid-items with an animated demo and a BACK
  item; pop returns to main.
- PLAY pushes the game page (id 3), whose background *is* the live `GamePlay`
  render; the top-right pause button pushes page 6.
- Pause: RESUME, RESTART, MAIN MENU. Game-over: score, optional record banner,
  RESTART, MAIN MENU.
- Back handling is gamepad-only (exit); gameplay itself has no back gesture.

## 3. Rules, scoring & progression

Endless vertical climber. The world scrolls upward 3 px/tick while the monkey
is alive; camera top = `gameY − 350`, bottom = top + 480.

- **Vines.** Exactly 4 lanes at x = 70 + 54·i px, each a chain of segments.
  A segment recycles when its top passes the screen bottom; a new one appends
  when the lane's top rises above `screenTop − 100`, overlapping 80 px, length
  random 200–384 px. Type: 10 % spiky on outer lanes (0/3), 10 % electric on
  inner lanes (1/2), else green; all green during the tutorial.
- **Player.** Starts on lane 1, climbs automatically at 3 px/tick and moves
  sideways at ≤ 2 px/tick toward the target lane centre; jumps are blocked
  while jumping, falling or at a lane edge. There is no grab-miss state — the
  run ends only on any falling state.
- **Victory/defeat.** No win state: score climbs until the monkey falls. Fall
  sources: enemy contact (20×20 centre hitbox), spiky vines, or an energised
  electric vine. The world stops after a 5-tick freeze; after 100 ticks the
  GAME OVER page pushes.
- **Electric vines** run a 100-tick cycle, energised on ticks 0 and 2 (brief
  spark), otherwise dormant; contact while live triggers the zap-death
  animation (aqua flash, electrocute + shriek cues).
- **Enemies** spawn every random 100–180 ticks at `gameY − 500` on a random
  lane: odd roll → snake (drifts down 2 px/tick in screen space, wraps at
  y > 480 to −128), even roll → chameleon (masked idle for its first 100
  ticks, then reveals).
- **Collectables** spawn on the same cadence and lane: multiples of 4 → mango
  (2000), else multiples of 3 → berries (250), else odd → dragonfly (500),
  else nut (250). Mango uses the collect-all cue, others the collect cue.
- **Scoring.** +1 per tick while alive and out of tutorial (≈60 pts/s), plus
  collectable values with a floating score rising 200 px over 100 ticks and a
  40-frame flourish (grows 50 px then shrinks into the HUD corner). Score
  zero-pads to 6 digits; the tutorial line fades over the first 50 points and
  ends permanently on the first left/right input.
- **Progression/persistence.** No levels; only run length ramps. High score
  lives in `GameSettings` (isolated-storage XML, key `Highscore`), swapping in
  a NEW RECORD banner when beaten; `PlaySounds` toggles music+SFX together,
  `ShowHands` keeps the tutorial-hand preference. Elapsed time is informational.

## 4. Controls

- Portrait touch: bottom-left paw button (0, 352) → jump left; bottom-right paw
  (144, 352) → jump right; pause at (224, 0). Buttons are plain hit-test menu
  items; touching them immediately calls the corresponding game method (no
  drag, no swipe in gameplay).
- Shared `TouchHandler` maps panel coordinates into the portrait frame and
  latches a "tap just occurred" on release with a short inactivity window; the
  same handler also exposes swipe details, unused by this title.
- No accelerometer input anywhere in the app; movement is tap/paw only.
- Gamepad: Back exits. No other physical-button bindings in gameplay.

## 5. Content inventory (must be re-authored)

42 files: 41 `.xnb` + 1 `.wma`.

- Fonts (6): menu, small menu, score, large score, floating score, floaty
  menu. Backgrounds (2): splash, solid black. Menu art (4): title, both paws,
  pause.
- Parallax layers (4): sky far, tree far/mid/near at native heights 701/512/
  594 (scroll 1/3/4 px/tick, horizontal rates 1/2/3).
- Collectables (4): mango, berries, nut, dragonfly (60-tick idle swap,
  0.1 rad/tick orbit at 10 px). Vines (4): green, spiky, electric off/on.
- Character atlases (3): monkey 120 frames ×2 layers 128×128 (jump 14+14,
  climb 20+20, eat 10, zap 16, fall 16); snake 30 frames; chameleon 9 frames.
- Audio (13): one music loop + click, collect, collect-all, crunch, eat,
  electric-vine, electrocute, jump, shriek, sparkle, vine-snap, whoosh.

Re-author all raster art as Canvas/vector sprites, rebuild fonts from tokens,
and synthesize 12 SFX plus one looping chiptune.

## 6. Implementation plan

- Engine: `ui/apps/games/vineclimb/VineClimbEngine.kt` — pure Kotlin `object`
  over an immutable `State`; deterministic `step(dt)` at fixed 60 Hz; seeded
  RNG so spawn rolls and segment lengths replay identically in tests.
  ```kotlin
  object VineClimbEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Screen: `ui/apps/games/vineclimb/VineClimbScreen.kt` in `DetailScaffold`,
  portrait `Canvas`; layers, vines, actors, HUD (6-digit score, pause, paw
  buttons) drawn from tokens only; state collected via `collectAsState`.
- Controls: two bottom paw hit targets via `pointerInput`; pause/menu rendered
  as zero-radius overlay panels; Back exits via the platform handler.
- Persistence: high score + sound toggle through `graph.games` and
  `graph.appState`; SFX through `SfxBank` (collect, collect-all, zap, fall,
  jump, eat, UI click) plus one looping music cue routed through the same
  manager.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): spawn-roll distribution
  (mango/berries/dragonfly/nut), gravity and freeze timing, vine recycle and
  segment-length bounds, electric cycle phasing, high-score update, tutorial
  fade at 50.

## 7. Citation log

- `VineClimb.exe!VineClimb.GamePlay.Update` / `ResetGame` — scroll, layers,
  spawn/lane constants, collisions, +1 score, game-over timing, start state.
- `VineClimb.exe!VineClimb.GamePlay.UpdateCollectablesList` /
  `UpdateEnemiesList` / `MaintainVines` — spawn cadences, roll tables, segment
  recycle 200–384 px with 80 px overlap, type distribution.
- `VineClimb.exe!VineClimb.GamePlay.MoveLeft` / `MoveRight` /
  `PositionOfVine_px` — tutorial exit, swing, lane x = 70 + 54·i.
- `VineClimb.exe!VineClimb.MonkeyPlayer.Update` / `JumpLeft` / `JumpRight` /
  `BeginFalling` / `BeginZapping` — climb, gravity 0.2, lane bounds, fall
  velocity (±1, −5), flash colours.
- `VineClimb.exe!VineClimb.ElectricVine.Update` / `SpikyVine.Update` /
  `SnakeEnemy.Update` / `ChameleonEnemy.Update` — hazard timings and drift.
- `VineClimb.exe!VineClimb.MangoCollectable` (2000) /
  `DragonflyCollectable` (500) / `BerriesCollectable` / `NutCollectable` (250)
  / `FloatingScore` (100-tick, 200 px rise) / `VineSwing`.
- `VineClimb.exe!VineClimb.GamePlay.Render` / `GameSettings` /
  `Constants.PageIDs` / `SoundIDs` — flourish, tutorial fade, persistence,
  screen and cue enums.
- `VineClimb.exe!VineClimb.MenuInitialiser.InitialiseMenu` / `VineClimb` —
  page wiring, paw/pause buttons, 480×272 back buffer, portrait target, 60 Hz.
- `2moroGames.dll!_2moroGames.Touch.TouchHandler.Update` — portrait mapping,
  tap latch.
