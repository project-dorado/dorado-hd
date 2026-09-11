# PGR: Ferrari Edition

- **Official package:** `PGRZune.exe` (pgr_ferrari_edition)
- **Corpus:** `Zune HD Apps (Decompiled)/pgr_ferrari_edition` (external, untracked)
- **Wave:** W6 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `PGRZune.exe` | 400 | 3059 | 53447 |
| `Microsoft.Xna.Zune.dll` | 110 | 825 | 15652 |

## 2. Screens & navigation

- App states (`AppState`/`GameStateManagement`): `StateLoad` → `StateIntro`
  → `StateMenu` → `StateGame` (→ `StatePause`, `StateEndRace`,
  `StateExitStage`) with `StateGame` running the countdown and race.
- Menu pages: Main, Quick Race (car/stage/difficulty summary), Career
  (`CareerStagePage`, `EventPage`, payouts, medals, trophies,
  `DifficultyCareerButton`), Garage (`CarSelectPage`, `CarColorPage`,
  `CarStatsItem`, purchase), Records (`RecordsStageItem`, lap/kudos
  records with `WatchRecordReplayAction`), Replays (`ReplayListPage`),
  Data Share (send/receive replays: `DataShareHome/Lobby`), Multiplayer
  (`MultiplayerHome/Join`, lobby), Options and Credits/About.
- In-race HUD (`HUD` + `CHud*` elements): time, lap counter, position,
  speed/tachometer, kudos ticker and combo, countdown, elimination
  warnings, records entry, trophy popups, genboxes and text wraps.
- Replays use `GameState_Replay` with replay cameras, and the ghost cars
  (`GhostPlayState`, `GhostCarManager`) render alongside the race.

## 3. Rules, scoring & progression + simulation model

- Racing across three cities (London, Tokyo, New York) on shared track
  segments. The shipped content has 24 stage definitions (`levelNN.lvldef`)
  plus 33 career events (`eventNN.lvldef`), 48 track GSR files (city
  short/medium/long variants with reverse and barrier forms), 12 car
  definitions and 24 factory ghosts. The simulation is a fixed 30 Hz tic
  (`TICS_PER_SECOND = 30`, `FIXED_TIME_STEP = 1/30`), which makes replay
  and ghost recording deterministic.
- `CCarAI` opponents use a `CLineTracker` to follow author-defined racing
  lines; `GameInput` carries gas, brake, signed steer and a button
  byte (bit 1 = handbrake). Race rules include `MAX_CHECKPOINTS = 3`,
  `MAX_LAPS = 6` and `MAX_PLAYERS = 4`.
- Car simulation (`CPhysCar`): a rigid body plus four wheels with
  suspension (rest height 0.15, spring strength 2.5*mass, inverse wheel
  mass 40, front/rear radii from the car def), 5- or 6-speed gearbox
  (reverse + 5 gears), engine torque curve (min/max torque, low/high RPM)
  and filtered RPM. Handling defines side grip
  `2*clamp(lerp(1.05,1.25,handling*0.1),1,1.2)`; forward grip is 2; drag
  interpolates by velocity alignment between frontal and side drag areas
  (areas converted from in² to m²); downforce is
  `0.5*sideGrip*lerp(v²/3086.42, 0.2*v²/3086.42, alignment)`. The
  handbrake flag cuts drive and changes wheel friction, enabling drifts.
- Gamemodes (`EGamemodeType` and the `Gamemode*` family):
  FreeRace/Quick Race, TimeTrial (with factory/ friend ghosts), Career,
  Multiplayer (MP sync/end-race/summary states), and the Career event
  types: Breakthrough, Cone Sprint, Eliminator, Kudos Challenge,
  One-on-One, Overtake, Speed Challenge, Time-vs-Kudos and Street Race.
  Difficulty is Easy/Medium/Hard, and credit rewards are 100/200/300 per
  difficulty tier.
- Kudos is the skill score: Drift 400, Burnout 100, 360 400, Raceline 200,
  Draft 100, Overtake 200, Slingshot 400, Clean Section 200, Clean Lap
  400, Clean Race 800, Clean Race Win 5000, Air 400, Speed 400, Cone Gate
  25. Combos build while maneuvers chain and are stashed; records keep
  stage/lap times, kudos scores and kudos moments per stage/mode.
- Progression: career event medals gate stage unlocks; cars cost credits
  (and some also kudos) with class/priority/stat ratings
  (accel/speed/handling/weight/brake); the profile stores credits, car
  ownership, paints/skins, event medals, trophies and records. Replays and
  ghosts are saved via the chunk file writer/reader.

## 4. Controls

- Touch gas/brake rectangles plus an optional auto-gas mode; steering is
  accelerometer tilt with a tilt-sensitivity setting, and an optional
  camera-tilt mode. Tilt is clamped/softened around zero before being
  applied.
- Swipe handling starts a swipe on press outside the pause/camera/gas/
  brake regions; swipe length over 20 units triggers the handbrake bit,
  and reversals (dot < 0 or a >100-unit length jump) cancel the swipe.
- Pause, camera-change and handbrake buttons are touch regions; the
  pause state disables race input. Menu navigation is touch with
  directional items.

## 5. Content inventory (must be re-authored)

329 files: 87 XNB (audio, showroom, cone meshes), 62 GSR meshes (12 car
bodies + 48 track segments + 2 cone variants), 57 lvldef (24 stages +
33 career events), 54 TEX car/track textures (each car has skin, normal
and damaged-normal maps plus brakelights), 24 PGG factory ghosts, 17 GLFX
shaders (car/object/particle/world reflection and specular passes),
12 cardef, 10 DEF (engines), 3 PLT particle/level templates plus
credits/INI. Audio: 11 engine sets × 4 looped bands (idle/low/mid/high),
collision/scrape/skid/wind, gameplay cues (countdown, go, checkpoint,
eliminated, kudos tick/stash/fail, cone miss, time up) and menu cues.
Re-author all: our own low-poly car fleet and code-defined tracks with
procedural city dressing, procedural textures, synthesized engine loops
and SFX, and our own ghost data.

## 6. Implementation plan

- 3D: `ui/apps/engine3d/` — car mesh with wheel nodes, material states
  (skin/damaged/brakelight), track ribbon meshes built from code-defined
  centerlines (spline + road width), instanced city props, fog and a
  reflection/specular material pass; no third-party 3D dependency.
- Logic: `ui/apps/games/Pgr.kt` — fixed 30 Hz deterministic step, CPhysCar
  equivalent (gears, torque curve, wheel grip, drag, downforce), AI line
  following, race modes/timers, kudos tracker with combo/stash, ghost and
  replay buffer, career/credits/unlock store. Tracks/levels are code
  data: spline control points, checkpoint/lap metadata, event rules.
- UI: `DetailScaffold` menus (main/quick race/career/garage/records/
  replays/options) with stat bars and medal icons; in-race HUD drawn over
  the GLSurfaceView; `SfxBank` for engine-band crossfades and race cues.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): gear/torque math,
  grip/downforce curves, fixed-step replay determinism, kudos maneuver
  values and combo stash, event win conditions, ghost record/playback,
  credit/unlock rules.
- Fidelity target: full-parity racing sim mechanics with original cars,
  cities and events (smaller initial car/event roster). Risks: physics
  feel parity, AI quality, and the content volume of four cars per class
  plus multi-segment cities.

## 7. Citation log

- `PGRZune.exe!BaseApp` (`TICS_PER_SECOND`, `FIXED_TIME_STEP`),
  `PGRZune.exe!GameContainer` (`MAX_CHECKPOINTS`, `MAX_LAPS`,
  `MAX_PLAYERS`), `ECity`, `EStages`, `ECarClass`
- `PGRZune.exe!CPhysCar.Init`, `.Update` (grip, drag, downforce, gears)
- `PGRZune.exe!DefCar` fields (stats, torque/RPM, gear ratios, drag
  areas, costs), `ECarColor`, `CarInstance`
- `PGRZune.exe!PGRKudosTracker` ctor (maneuver values),
  `ManeuverDrift`, `Maneuver360`, `ManeuverRaceline`,
  `ManeuverBurnout`, `ManeuverOvertake`, `ManeuverSlingshot`,
  `ManeuverDraft`, `ManeuverConeGate`, `ManeuverClean*`, `ManeuverAir`,
  `ManeuverSpeed`
- `PGRZune.exe!PlayerActor.GetCarInput` (touch gas/brake/swipe,
  auto-gas), `PGRZune.exe!GameInput`, `EButtons`
- `PGRZune.exe!EGamemodeType`, `EDifficulty`, `GamemodeFreeRace`,
  `GamemodeCareer`, `GamemodeTimeTrial`, `GamemodeMultiplayer`,
  `GamemodeBreakthrough`, `GamemodeConeSprint`, `GamemodeEliminator`,
  `GamemodeKudosChallenge`, `GamemodeOneonOne`, `GamemodeOvertake`,
  `GamemodeSpeedChallenge`, `GamemodeTimeVsKudos`, `GamemodeStreetRace`
- `PGRZune.exe!GameData` (credit gains, `PurchaseCar`), `Profile`,
  `SaveManager`, `Records`, `StageRecord`, `LapTime`,
  `MomentRecord`; `GhostCarManager`, `EGhostType`, `ReplayManager`
- Content tree `Cars/`, `Tracks/`, `Levels/`, `Levels/Career/`,
  `FactoryGhosts/` (counted; no layouts copied)
