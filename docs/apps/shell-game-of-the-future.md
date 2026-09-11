# Shell Game of the Future

- **Official package:** `Shells.exe` (Shell Game of the Future)
- **Corpus:** `shellgameofthefuture` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Shells.exe` | 33 | 110 | 3,208 |
| `Microsoft.Xna.Zune.dll` | dep | 825 | 15,694 |
| `ZuneGamesLib.dll` | dep | 402 | 6,409 |

Portrait 272x480. Three rocket robots stand on launch pads; the player charges
them by shaking the device, watches them fly a scrolling star field, then taps
the robot believed to hold the hidden space trophy. Trophies are collectibles
persisted to `UnlockedTrophies.xml`. `AllowMultipleInputs` is enabled; the app
auto-pauses on deactivation.

## 2. Screens & navigation

- **Loading** with logo and publisher/copyright lines → **Game** (main menu is
  the game screen itself: robot reveal + trophy rows + charge meter).
- **In-game HUD**: charge meter across the bottom (empty/full fill, plus
  low/medium/high lamps that light at charge > 0, 72 and 144), pause button
  bottom-right, a contextual tip sign (`Shake` → `Choose` → `ShowDevice`), and
  three horizontally draggable trophy rows (Easy / Medium / Hard) behind the
  robots.
- **Pause screen**: `Resume`, `About`, `Reset`, `Volume` (Off/Low/Medium/High)
  plus decorative robot frames. The pause screen doubles as the
  fanfare/collection view.
- **Fanfare** slides the award row to centre, flies the trophy to the row,
  unlocks it, and fires a star/firework celebration (short for a repeat trophy,
  full finale when all trophies are unlocked).

## 3. Rules, scoring & progression

- **Charge**: the shake detector keeps the last 10 accelerometer samples and
  sums the per-sample distance; a sum above **2** fires a shake event with
  `Strength`. Each shake adds `Strength · 4` charge, and `AdjustCharge` halves
  the amount (`Charge += amount/2`), clamped to **0–186**. The meter's three
  lamps light at **72** and **144**.
- While the robots are not all sitting still, each update drains charge via
  `AdjustCharge(−5)`, i.e. **2.5 charge units per frame**. A launch begins when
  charge > 1 and no charge change has happened for **0.4 s**; charge then
  quantises the hidden shell: **< 72 → Easy**, **< 144 → Medium**, else
  **Hard**.
- **Flight**: three robots each follow a generated linear path over a grid of
  waypoints (`x ∈ {10, 52.5, 95, 137.5, 180}`, `y = 5..250` in steps of 5).
  The path length loop starts at 1.5 / 2.5 / 3.0 and shrinks 0.15 per waypoint,
  giving roughly 11 / 17 / 21 waypoints before the three final pad positions
  are appended. Cruise speed is **400 / 475 / 575** px/s for Easy / Medium /
  Hard. Each robot's path last waypoint is one of the three launch pads,
  shuffled independently.
- **Hidden trophy**: after the paths are built, one of the three robots is
  assigned a trophy from the pool for the charge tier (locked trophies first,
  avoiding the last 5 awarded); the other two get none. `PrepareRobots` only
  runs when the meter has charge and the timeout has elapsed.
- **Reveal**: when all robots reach `WaitPostFlight`, tapping a robot selects
  it. If it held the trophy, all robots switch to a success reveal, the trophy
  plays its fly-to-row fanfare and is permanently unlocked. If not, the chosen
  robot opens an empty bay while the robot that actually held the trophy
  auto-opens and reveals it after ~0.4 s.
- **Trophy collection**: trophy names encode tier (`easy-`, `med-`, `hard-`)
  and atlas rectangles come from `Trophies.txt`; each trophy has a coloured
  and silhouette frame and is stored unlocked by name.
- Tapping a robot before `WaitPostFlight` (or when not ready) shows the
  `Shake`/device tip instead — no score is taken.

## 4. Controls

- **Shake (accelerometer)** is the primary charge input; there is no tilt
  steering. Shake magnitude scales charge so vigorous shaking charges faster.
- **Single tap** on a robot after the flight selects it; taps on a robot in
  `Sitting` state hit only its lower body (`Bounds.Y + 30`), which surfaces the
  help bubble.
- **Horizontal drag** on a trophy row scrolls that row (one tracked touch ID;
  scrolling stops while the fanfare animates).
- Pause button or app deactivation freezes input and pops the pause screen.

## 5. Content inventory (must be re-authored)

94 files: 65 PNG + 23 XNB + 4 XML + 1 JPG + 1 TXT.
- `Textures/` (19): robot body parts/animation strips (sitting, charge,
  boot-deploy, dial reveal, mouth, antenna, ear light, victory, fail),
  fire/flying-fire, trophy atlas `Trophies.png` + `Trophies.txt`.
- `Textures/UI` (36): menu frames, charge meter empty/full, charge lamps,
  pause/back/resume/about/reset/volume buttons with on states, popups, signs.
- `Background/` (3): background, stars, about background; `Loading/` (3);
  `Shake Particles/` (3); `Trophy Sky Effect/` (3).
- Audio (23): charge loop, menu slides/down, robot turn-on, open/close robot,
  boot deploy, dials, hover sign, trophy fly/settle, rocket boots loop,
  robot land, object reveal, choose right/wrong, failure robot off, fanfare,
  firework/comet bursts.
- Language XML `en/es/fr`; re-author all text and trophy names.

## 6. Implementation plan

- Engine: `ui/apps/games/ShellGame.kt` — `object ShellGameEngine` with
  immutable `ShellState(charge, phase, robots, paths, hiddenIndex, tier,
  unlocked: Set<String>)` and deterministic `step(dt, shakeStrength)` plus
  `choose(robotIndex)`. RNG for path generation, trophy pick and last-5 ring
  must be seeded and stored in state.
- Screen: `ShellGameScreen` in `DetailScaffold("shell game of the future")`
  rendering a star Canvas, robot sprites via shapes, a token-coloured charge
  meter with the 72/144 lamp marks, tip sign, and draggable trophy rows.
- Sensors: sample the HD accelerometer at frame rate; keep a 10-sample ring and
  the `sum(delta) > 2` shake trigger, forwarding strength into `step`.
- Persistence/SFX: unlocked trophy names + charge preset in
  `graph.appState`; `graph.games` optional best-tier stat; `SfxBank` for
  charge/launch/land/reveal/fanfare cues.
- Tests (`app/src/test/java/com/heretek/dorado_hd/ShellGameEngineTest.kt`):
  charge clamp 0–186 and halving; lamp thresholds; drain −5 while not sitting;
  launch timeout 0.4 s; tier selection 72/144; path waypoint counts 11/18/21;
  hidden trophy exclusivity; reveal-correct unlocks only once; wrong pick
  reveals the true holder; last-5 trophy avoidance.

## 7. Citation log

- `Shells!GameScreen.PrepareRobots` — charge tiers, speeds 400/475/575, path
  grid, waypoint loop, trophy pick, last-5 ring.
- `Shells!GameScreen.HandleNonFanfare` — launch condition, drain −5, flight
  completion, success/failure state routing.
- `Shells!GameScreen.shakeDetector_DeviceShook` / `AdjustCharge` — strength ×4.
- `Shells!GameScreen.robot_Touched` — post-flight selection and tip fallback.
- `Shells!GameScreen.displayFanfare` — row scroll, trophy fly, unlock.
- `Shells!ChargeMeter.AdjustCharge` — amount/2, clamp, 72/144 lamps.
- `Shells!ShakeDetector.UpdateComponent` — 10-sample window, threshold 2.
- `Shells!Robot.TouchBegan` — sitting lower-body hit box, post-flight box.
- `Shells!Trophy.Load` / `Unlock` / `Save` — atlas naming, silhouette,
  `UnlockedTrophies.xml`.
- `Shells!TrophyRowComponent.TouchMoved` / `TouchEnded` — horizontal drag with
  one tracked touch id; `ForgetTouch` clears focus before fanfare.
- `Shells!PauseScreen..ctor` — Resume/About/Reset/Volume entries.
