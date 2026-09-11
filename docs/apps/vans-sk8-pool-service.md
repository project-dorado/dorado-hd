# Vans Sk8 Pool Service

- **Official package:** `Vans.exe` (vans)
- **Corpus:** `Zune HD Apps (Decompiled)/vans` (external, untracked)
- **Wave:** W6 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Vans.exe` | 49 | 345 | 12596 |
| `Microsoft.Xna.Zune.dll` | 110 | 825 | 15694 |
| `SkinnedModel.dll` | 7 | 21 | 317 |

## 2. Screens & navigation

- State machine (`GameState`): Transition → VansLogo → TitleMenu
  (a 3D `Carrousel`/`CarrouselModel`) → CareerMenu → InEvent, plus
  OptionsMenu, ExtrasScreen, ConfigurationMenu (board and wheel select),
  PlaylistCustomizationMenu / ZunePlaylistCustomizationMenu,
  CalibrationScreen, PauseScreen, PauseTrickList / TrickList,
  AchievementUnlockScreen, NewBoard/NewWheel/NewEvent/NewVideo unlock
  screens, UnlockedAllScreen, TitleInfoScreen and EventInfoScreen.
- `EventInfoScreen` describes the selected event with its goal, high score
  and reward; `CareerMenu` shows event progress stars/cups
  (half-star, activated star, trophy assets).
- In-event UI (`BaseEvent` and subclasses): balance bar, score/points UI,
  multiplier, timer/clock, pause and swipe hint arrows
  (`swipe_up/down/left/right`); `SmoothFollow` camera with trick/air
  framing.

## 3. Rules, scoring & progression + simulation model

- Drained-pool skateboarding career. Events (`VansEvent`): FreeRide,
  Training, TimedRun, PoolCleaner, TheGrind, Pro, SpinToWin,
  NeverEnoughTime, BestRun, Skate, NoBails, PoolJam, plus the Career
  wrapper. Each event is a `BaseEvent` subclass with its own timer,
  multiplier and goal (e.g. BestRun raises the multiplier cap to 10,
  NeverEnoughTime uses a 31 s timer, TheGrind decays the multiplier every
  2 s, PoolJam every 0.8 s, the default event is 91 s and a max multiplier
  of 5 decaying every 5 s).
- Player model (`BoardController`): position/direction/up vectors with a
  root at (0, 4, 0); speed physics uses gravity 10.8, drag factor 0.974 per
  step, drive acceleration `20 * speedMult * elapsed` (accel multiplier
  0.9–1.19 by wheel), and a push speed of 11. Rotation spins at
  `5.1 * spinMult` (0.82–1.39 by board) with a max rotation rate multiplier
  of 0.75. Air time integrates an air velocity; landing is judged against
  `π/12 * landingMult` (0.3–2.0) and the board rotation, and a two-finger
  touch in the air initiates a grind at a minimum speed of 6.
- Grinds are steered by the balance meter: the balance point must stay
  within ±40 (dead zone 0.05), tilt accelerates it by `tilt.X * 3`, the
  control speed self-corrects by `8 * elapsed`, and the point drifts at
  `0.4 * elapsed` scaled by balanceMult (0.5–2.0). Exceeding the limit
  crashes the skater.
- Scoring: each landed trick adds its point value — AirWalk 1200,
  StaleFish 1000, BacksideAir 1300, IndyGrab 1200, FrontsideAir 1200,
  ChristAir 1500, JapanAir 1600, JudoAir 1600, BackFlip 1400, 540Air 1400,
  KickFlip 1500, LienAir 1500, SlobAir 1700, Omar/Bucky specials 1800;
  rotation score comes from the accumulated spin and grinds score
  `grindTime/100 * 10` (10 points per 100 ms). The event multiplier
  (default up to 5) multiplies the trick total and decays on its own
  schedule; bails end the chain.
- Progression: 21 skateboards and 11 wheels, each with prop A/B values that
  map to spin (0.82–1.39), balance (2–0.5, inverse), control (2.5–0.75),
  landing (0.3–2) and speed (0.9–1.19). Career events award stars and
  unlock new boards/wheels/events/videos; 27 achievements track event
  milestones (PoolCleaner, TheGrind, Pro, Spin2Win, NeverEnoughTime,
  BestRun …). Extras include video clips and custom music playlists.

## 4. Controls

- Steering is accelerometer tilt with an explicit calibration screen; the
  same tilt vector drives both turning and the grind balance minigame, and
  a calibration value is stored in preferences.
- Touch gestures: swipe up/down/left/right hint arrows map to tricks and
  grinds; two-finger touch while airborne initiates a grind; taps start
  pushes/acceleration. Pause and playlist controls are touch buttons.
- The configuration menu previews boards/wheels in 3D and shows their
  stat changes before selection; device orientation is locked landscape
  with left/right variants.

## 5. Content inventory (must be re-authored)

831 files: 822 XNB + 6 WMA + 3 XML. 3D assets: Environments 1 and 2
(backyard pool and rooftop) with lightmaps and prop textures, two skinned
skaters (Bucky, Omar) with boards/wheels, 21 board preview textures and
11 wheel previews, four menu background art pieces. UI: career menu,
achievement screen, board/wheel stars (empty/half/full), trick list,
pause, options/playlist checkmarks, in-game balance bar, garbage/pool
map sprites, swipe hints, logos. Effects 4, font 1, six WMA music tracks,
localization XML (en/fr/es). Re-author all: our own pools, skater rigs,
board/wheel designs, trick animations and synthesized music/SFX.

## 6. Implementation plan

- 3D: `ui/apps/engine3d/` — pool environment from code-defined geometry
  and procedural textures, skinned skater with an original trick
  animation set, board/wheel swap meshes, lightmap-free baked vertex
  lighting, trick camera and shadow pass.
- Logic: `ui/apps/games/VansSkate.kt` — BoardController-equivalent state
  machine (InPool/InAir/IsGrinding/IsCrashing), speed/gravity/drag,
  balance meter, trick detection windows, score+multiplier, 12 events plus
  the career wrapper as data-driven rule sets, career stars/unlock store.
- UI: carousel career/config menus in `DetailScaffold`, event info and
  trick list, pause; `SfxBank` for push/land/grind/crash/applause;
  playlists driven by the app's own music pipeline.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): speed integration and
  cap, spin/rotation scoring, grind balance stability and crash, landing
  tolerance math, trick point table, multiplier decay per event, unlock
  and achievement rules.
- Fidelity target: full-parity trick/event mechanics with original skaters,
  pools and gear. Risks: animation quality with our own rig, balance-meter
  feel, and the asset volume for 21 boards/11 wheels.

## 7. Citation log

- `Vans.exe!Constants` (TotalSkateboards, TotalWheels, Gravity,
  DragFactor, Acceleration, Min/MaxAccel/Balance/Landing mults,
  MinimumGrindSpeed, PushSpeed, BalancePointSpeedModifier,
  BalanceMaxValue, BalanceDeadZone, AcceleratorModifier,
  ControlSpeedModifier, SpinRate, Min/MaxSpinRateMult, LandingAngle)
- `Vans.exe!BoardController.Reset`, `.SetConfiguration`, `.Update`
  (air/landing/grind states, speed integration), `.GrindBalanceCheck`,
  `.InitiateGrind`
- `Vans.exe!Player.GetTrickScore`, `.GetRotationScore`, `.GetGrindScore`,
  `.ConcateTrickAndRotation`, `LoadPlayerAnimations` trick table
- `Vans.exe!BaseEvent` (maxMultiplier, multDecrement, eventTimer, HUD
  textures), `Vans.exe!BestRun`, `.NeverEnoughTime`, `.TheGrind`,
  `.PoolJam`, `.PoolCleaner`, `.SpinToWin`, `.NoBails`, `.Pro`, `.Skate`,
  `.TimedEvent`, `.Training`
- `Vans.exe!Trick` (name, points, bail time, weight, grind flag)
- `Vans.exe!PlayerPreferences`, `.SetConfiguration`,
  `.CalculateMultiplier`; `Vans.exe!Achievement` (27 entries)
- `Vans.exe!GameState`, `Vans.exe!Carrousel`, `Vans.exe!EventInfoScreen`,
  `Vans.exe!TouchSwipe`, `Vans.exe!SmoothFollow`,
  `Vans.exe!CalibrationScreen`
