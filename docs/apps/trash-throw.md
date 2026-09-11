# Trash Throw

- **Official package:** `TrashThrow.exe` (Trash Throw)
- **Corpus:** `trashthrow` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `TrashThrow.exe` | 27 | 133 | 4,904 |
| `2moroGames.dll` | dep | 413 | 6,180 |

Portrait 272x480. A pseudo-3D flick-to-basket game: a crumpled ball starts in
front of the camera and the player swipes to throw it toward a bin in one of
three scenes (Bedroom, Office, Dentist). Perspective camera, a simple 3D mass
integrator, wind, and per-scene streaks. Settings (three high scores, sound)
persist to `GameSettings.xml`; the shared `2moroGames` library provides the
touch/swipe handler and menu widgets.

## 2. Screens & navigation

- **Splash → Main Menu**: `NEW GAME`, `HIGHSCORES`, `ABOUT`,
  `TURN SOUND ON/OFF`; a menu toolbar with back/continue keys.
- **Scene Select**: `BEDROOM`, `DENTIST`, `OFFICE` — tapping one resets the
  selected scene's streak and starts play.
- **Gameplay**: full-screen scene render, a toolbar score label
  (`Score N` / `Best N`), a wind indicator (left/right arrows driven by the
  active wind speed), and a pause key. Swiping anywhere on the gameplay page
  throws the ball.
- **Pause**: `RESUME`, `MAIN MENU`, sound toggle.
- **High Scores**: overall best plus per-scene bests (bedroom, office,
  dentist) and best-streak text; **Reset Scores** asks `RESET/CANCEL` through a
  confirm page; **About** shows the 2011 Microsoft copyright.

## 3. Rules, scoring & progression

- **Camera/space**: each scene sets a perspective (camera at 2.8–3.2 m height,
  FOV **23.2°**, 272x480 projection, screen centre at (136, 36)). The ball is a
  **0.1 m radius** mass; its start point is the camera position plus a
  `(0, −0.45, 0.5)` offset with +1.25 m forward and −0.75 m down, so the ball
  sits just in front of the view.
- **Scenes**: goals are baskets or boxes placed ~8.75–10.25 m away.

  | Scene | Goal | Base radius/top/height | Throw force (up, forward) | Wind max |
  |---|---:|---:|---:|---:|
  | Bedroom | basket | 0.175 / 0.20 / 0.35 m | 300 / 310 N | 0.5 |
  | Office | basket | 0.19 / 0.25 / 0.60 m | 350 / 350 N | 4.0 |
  | Dentist | basket | 0.30 / 0.30 / 0.50 m | 340 / 350 N | 3.0 |

- **Throw**: the horizontal component of the swipe sets the lateral force:
  `swipeDx(px)/10`, clamped to **±15**, multiplied by 5 → **±75 N** on X; the
  rest of the force vector is the scene's fixed up/forward push. Gravity is
  **9.8 m/s²** (applied as mass × 9.8 once airborne); while the ball is waiting
  for the flick it is held and may receive the wind force only after release.
- **Wind**: each new throw re-rolls `(rand−0.5)·2·sceneMax`, never exactly 0,
  and shows it on the wind indicator. While airborne the wind applies
  `windSpeed · 0.5` on X every step, so Office/Dentist drift strongly.
- **Bounce/settle**: hitting the ground reflects with 50% restitution and
  begins a reset fade (30 frames to death, 11-frame alpha fade). Bouncing off
  the basket rim uses a full-efficiency reflection. Once the ball touches the
  ground twice it stops and the throw resolves.
- **Scoring**: a made basket is **+1** to the current streak. A miss (ball
  dies on the ground) resets the streak to **0**. The run is endless; only the
  streak matters. A new per-scene record plays the record cue, otherwise the
  ordinary score cue; a miss plays the fail cue.
- **Reset cadence**: after every resolution the game waits **30 ticks**
  (≈0.5 s at 60 fps) and then re-rolls the wind and returns the ball to the
  start position (`ThrowState.Undecided`).
- **Progression**: three independent per-scene bests; no levels, no unlock
  gates. Highscores are written when a scene's best improves, plus an overall
  best shown on the scores page.

## 4. Controls

- **Swipe** is the only gameplay input. The shared `TouchHandler` classifies a
  release as a swipe when the start-to-end path is **≥ 30 px** and the gesture
  lasted **≤ 60 ticks** (~1 s); the horizontal distance is what the game uses,
  vertical distance is ignored for aim.
- **Tap** drives menus/buttons (tap = release after > 3 ticks with no movement).
- No tilt, no multitouch gameplay; the pause key is a drawn toolbar button
  handled by the menu layer before the swipe is consumed.

## 5. Content inventory (must be re-authored)

39 XNB files:
- `Game/Scenarios` (3): packed scene atlases — `new_bedroom_packed`,
  `dentist_packed`, `office_packed`; `Game/Scenarios/Office` also ships
  unpacked parts (background, bin front/whole, desk, fan1–6, paper,
  paper shadow), and Bedroom ships `bedroom_packed`.
- `Game/` (2): `closest` marker, `corner` frame.
- `Menu/` (10): toolbar, border, on/off buttons, gameplay bar, back, wind
  left/right, about, game button; `Menu/Backgrounds` (2): main, splash.
- Audio (7): bucket, click, failed, record, ruffles ×2, score.
- Fonts: Menu, MenuSmall.
- Re-author the three scenes as flat vector backdrops with a projected bin;
  no Microsoft art.

## 6. Implementation plan

- Engine: `ui/apps/games/TrashThrow.kt` — `object TrashThrowEngine` with
  immutable `ThrowState(scene, ballPos, ballVel, wind, streak, phase,
  resetTicks)` and a fixed-step `step(dt)`; `startThrow(scene, rng)` re-rolls
  wind; `flick(dxPx)` clamps and converts to force; goal hit test as a
  cylinder/box in scene space. Keep camera math as a small projection helper.
- Screen: `TrashThrowScreen` in `DetailScaffold("trash throw")`, Canvas-drawn
  scenes and ball, wind arrows, score/best toolbar, pause/scene-select/
  highscores/reset overlays.
- Persistence/SFX: per-scene bests via `graph.games.record("trash-throw",
  streak, "bedroom") / ("office") / ("dentist")`; settings via
  `graph.appState`; `SfxBank` for throw/settle/score/record/fail.
- Tests (`app/src/test/java/com/heretek/dorado_hd/TrashThrowEngineTest.kt`):
  9.8 gravity integration; swipe clamp ±15 → ±75 N; wind re-roll excludes 0 and
  respects scene maxima (0.5/3/4); made-basket +1 and miss resets to 0; 30-tick
  reset cadence; ground bounce then settle; per-scene best persistence.

## 7. Citation log

- `TrashThrow!GamePlay.Update` — goal success/miss, streak reset, 30-tick
  reset, wind force `windSpeed·0.5`.
- `TrashThrow!GamePlay.FlickFromSwipe` — `dx/10` clamp ±15, ×5 force.
- `TrashThrow!GamePlay.ResetForNewThrow` — 30 ticks, wind re-roll, non-zero.
- `TrashThrow!Ball.Update` / `BeginResetFade` / `FlickBall` — gravity 9.8,
  ground bounce, 30/11 frame fade, reset cues.
- `TrashThrow!Ball.GetRadius_m` — 0.1 m ball.
- `TrashThrow!AbstractScenario.Update` — goal proximity and rim bounce.
- `TrashThrow!NewBedroomScenario` / `OfficeScenario` / `DentistScenario` —
  camera heights, FOV, start offset, throw forces, wind maxima, goal sizes.
- `TrashThrow!GameSettings` — `GameSettings.xml`, three bests, sound.
- `2moroGames!TouchHandler.Update` — swipe threshold 30 px / 60 ticks, tap
  detection.
- `2moroGames!SwipeDetails.GetSwipeDistanceX_px` — horizontal-only aim.
- `TrashThrow!WindIndicator.SetWindSpeed` — HUD indicator.
