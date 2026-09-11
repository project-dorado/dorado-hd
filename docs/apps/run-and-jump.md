# Run and Jump

- **Official package:** `RunAndJump.exe` (Run and Jump)
- **Corpus:** `run_and_jump` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `RunAndJump.exe` | 30 | 158 | 3,917 |
| `Box2D.XNA.dll` | dep | 456 | 13,174 |
| `Microsoft.Xna.Zune.dll` | dep | 829 | 15,852 |
| `ZuneCoreLib.dll` | dep | 308 | 6,420 |
| `ZuneGamesLib.dll` | dep | 170 | 3,669 |

Landscape-right, 480x272 design canvas. A Box2D rigid-body platformer: one
hexagonal player body, tiled collision, sensor "boost" pads, coins, warps,
switch blocks and breakable wood. Save data is a whitespace-separated text
record persisted as `savedata.txt`; the app force-orients landscape and
disables multitouch (`IsMultitouchEnabled = false`).

## 2. Screens & navigation

- **Cool/loading boot** → **Main Menu** with three entries: `Play!` (pushes
  the level-select scroll), `Options`, `About`. A physics logo animation runs
  behind the menu.
- **Level Select** — horizontally scrolling pages of level buttons, four per
  page; each button reflects unlock state and collected-coin count. Scroll
  snaps to the last played page on entry.
- **Game** — HUD labels: coins `collected/total`, level `#N`, pause button at
  top-left. Level hints appear once as a pop-up before control starts.
- **Pause** — `Resume`, `Level Select`, `SFX` volume toggle (Off/Low/Medium/
  High), `Speed` toggle (`Fast` = 1.0x, `Normal` = 0.85x slow-motion). Changing
  speed mid-level pops a confirmation and restarts the level on accept.
- **Pop-ups** — hint text, missed-coins Retry/Next Level, unlock-set notice
  (Keep playing / Level select), finish-all notice, options reset-data
  confirmation.

## 3. Rules, scoring & progression

- **Physics**: gravity `(0, 24)` m/s² in 32-px world units, fixed step
  `1/60 · SlowMotion` with 10 velocity / 5 position iterations. The player is
  a 6-vertex standing polygon (ducking variant), density **2.425**, friction
  0, restitution 0. Terminal vertical speed is clamped to `16 · SlowMotion`.
  A tap boost applies force **775** (angled boosts use 1.35× at 60°, move
  boosts use 1×, slow/fast boosts 0.5×/2×) and is rate-limited by a **0.25 s**
  penalty. Duck holds for **0.85 s**, then unducks after 0.05 s.
- **Boost pads**: each tile in the level's boost layer is a sensor whose ID
  becomes the "current boost" while the player overlaps it. A screen tap fires
  it. IDs: 0 jump, 1 duck, 2/3 angled left/right, 4/5 move, 6 gravity flip,
  7 goal (finish), 8 null, 9/10 slow move, 11/12 fast move, 13/14 switch-block
  pairs (pink/green, purple/orange). IDs 16–31 are the same with inverted
  gravity; IDs ≥ 64 activate the sensor entity with `id − 64` (warps teleport
  the player to their sibling and offset the camera).
- **Death**: touching a surface that is not aligned with the current gravity
  floor kills the player (side/ceiling contact), and standing still where the
  current tile offers no safe boost (CurrentID −1, 8 or 13) also kills. Death
  plays the fall animation, disables the body, and respawns at the level start
  after **0.5 s**; the death counter increments only after the player has
  touched the screen. Leaving the level bounds inflated by 56 px also forces a
  respawn.
- **Coins**: sensor contacts add 1 to the level counter and play the coin cue.
  The goal sensor completes the level. Wood blocks shatter ~1 s after being hit
  and are destroyed.
- **Levels and progression**: **58** level XMLs ship (6 tutorial, 14 easy,
  20 medium, 15 hard, 3 extreme) ordered by `LevelList.xml`; the save format
  serializes 56 stat entries. New level groups unlock on lifetime coin
  milestones **31 / 85 / 135 / 220**; individual levels can also gate on
  `CoinsToUnlock`. Completing with missed coins offers `Retry` or
  `Next Level`. A beat at Fast speed sets `BeatOnFastSpeed`; all coins at Fast
  speed sets `AllCoinsFastSpeed`.
- **Scoring**: no points — the scoreboard is coins collected, deaths per level,
  and the fast/all-coins completion flags. Level select shows total coins.

## 4. Controls

- **Tap anywhere** executes the boost pad currently under the player. There is
  no jump button and no multitouch; each tap consumes the current pad.
- Boost semantics are tap-count based: repeated taps with the right pad under
  the player chain instant actions (attack-style controls), while directional
  pads produce movement/angle forces.
- Pause via the HUD button; device deactivation auto-pushes the pause screen.

## 5. Content inventory (must be re-authored)

101 files: 59 level XMLs (incl. `LevelList.xml`), 22 PNG, 16 XNB, 1 preview
atlas descriptor.
- `Images/` (22): tileset pieces (`testtileset`, `boosts`, floors, woodblock,
  switchblocks), entities (`dude`, `coin`, `goal`, `warp`), logo, messagebox,
  pause, previews and fast-mode badge.
- `Sounds/` (16): jump, duck, unduck, move slow/normal/fast, gravity, warp,
  coin, goal, wood, pink/purple switch, menu, dead, null.
- Levels must be **re-authored from scratch** — copy no XML or layout.
- Text tables `Text.xml`/`Fonts.xml`/`en.xml` for hints and credits.

## 6. Implementation plan

- Engine: `ui/apps/games/RunAndJump.kt` — `object RnJEngine` with an immutable
  `RnJState(player: Body, tiles, boosts, coins, warpPairs, gravitySign,
  slowMotion)` and a fixed-substep `step(dt)` (accumulator at 1/60 s). A small
  AABB/hex collision solver is enough; no Box2D dependency. Level data should
  be generated in Kotlin builders (procedural easy/medium/hard sets), not
  ported content.
- Screen: `RunAndJumpScreen` in `DetailScaffold("run and jump")`, landscape
  Canvas with tiles, player hexagon, coin spin, HUD (coins/level/pause), pause
  overlay with SFX + Speed toggles, level-select grid backed by an
  appState-saved progress map. Zero corner radius, token colors only.
- Persistence/SFX: `graph.appState` JSON for per-level coins/deaths/flags and
  last level; `graph.games.record("run-and-jump", totalCoins, "deaths:$n")`;
  `SfxBank` per action cue.
- Tests (`app/src/test/java/com/heretek/dorado_hd/RunAndJumpEngineTest.kt`):
  gravity substep determinism; terminal velocity clamp; duck timing;
  side-contact death; standing-on-id-8 death; warp sibling mapping and
  id+64→entity; sensor switching; coin milestone unlocks at 31/85/135/220;
  tap-boost cooldown 0.25 s.

## 7. Citation log

- `RunAndJump!Player` — 775 force, 60° angled boost, 0.25 s penalty, hex
  verts, density 2.425, 0.85 s duck, terminal clamp 16·SlowMotion.
- `RunAndJump!Player.ApplyBoostForce` — boost ID table 0–14/16+/64+.
- `RunAndJump!Player.SetUpPhysicsBody` / `Update` — body setup, respawn,
  animation frames.
- `RunAndJump!BoostContactListener.BeginContact` / `EndContact` — sensor pads,
  coin pickup, death rule, wood destruction.
- `RunAndJump!PhysicsEngine` — gravity 24, 1/60 step, 10/5 iterations,
  SlowMotion 0.85/1.0, tile merge into polygons, goal tile IDs 7/23.
- `RunAndJump!GameScreen.Update` — bounds respawn, goal flow, coin gates
  31/85/135/220, fast/all-coins flags, camera follow.
- `RunAndJump!GameScreen.LoadLevel` / `.ctor` — HUD labels, 30x9 default size,
  goal creation, hint pop-up.
- `RunAndJump!PauseScreen..ctor` — SFX toggle, Fast/Normal speed restart.
- `RunAndJump!RunAndJump..ctor` / `LoadingScreen.OnPushedToScreenStack` —
  landscape lock, no multitouch, threaded level load, 56-entry save parse.
- `RunAndJump!Warp.OnPlayerActivation` — teleport to sibling + camera offset.
- `RunAndJump!Coin.Update` / `Goal.Update` — pickup and finish sensors.
