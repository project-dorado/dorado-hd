# Space Battle 2

- **Official package:** `Zauri.exe` (space_battle_2)
- **Corpus:** `Zune HD Apps (Decompiled)/space_battle_2` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Zauri.exe` | 97 | 809 | 18109 |
| `ContentLibrary.dll` | 13 | 20 | 463 |
| `Microsoft.Xna.Zune.dll` | 111 | 798 | 14932 |

- Portrait 272×480 (`OriginalWidth/Height`), single-touch with optional tilt;
  `GraphicsWidth/Height` adapt at runtime. Campaign stages load from embedded
  XML scripts (entity/batch/background/path/autopilot records) that may set
  their own play-area `width` (default 400 px) with a panning background.
- Preferences (`Prefs`, v12) and highscores persist to `highscores.bin` plus
  per-stage ghosts; 20 audio cues, no music track.

## 2. Screens & navigation

`ScreenManager` state machine (NoChange/Init/Splash/Menu/Game/Paused) with a
layered SexyMenu system:

- Splash → main menu: Play, Options, High Score, How To Play, Race, Customize.
- Level select: two planet-select screens (levels 1–10) and 5 race tracks,
  with difficulty (normal/hard/elite), locks and best scores per node.
- Customize ship: 4 categories × 10 parts, stat bars (power/armor/speed/
  boost), 5 palettes, zaurium purchase, equip/save; difficulty-gated tiers.
  Help: four pages (game, controls, customize, race).
- In game: HUD (score, lives, zaurium, shields, ammo, weapon icons,
  speedometer, boss bar), pause menu, stage-complete stats, game-over stats.
- Race menus: time trial, transmit mode, net lobby for ghost exchange, and a
  race-over plate comparing your time to the ghost.
- End game: stage-10 win plate with total score and updated bests, or final
  stats with retry/quit.

## 3. Rules, scoring & progression

A vertically scrolling shoot-'em-up with racing and customization layers.

- **Campaign.** 10 stages over 5 worlds, driven by scripted spawn records:
  drones (mindless/charger/seeker), asteroids in four sizes (small→epic),
  turrets (fixed/aim/fire-shot) and bosses. Bosses are multi-part — body,
  shell, appendages, tell animations and weak points that expose only while
  attacking — with a dedicated health bar; stage 10's final boss ends in the
  victory screen.
- **Player ship.** Starts with 3 lives and 3 secondary ammo. Primary weapons
  auto-fire (photon/spread/wave/sweep, upgrade per weapon level); the fire
  action spends ammo on the equipped secondary: wave, sweep, laser, rockets,
  bomb, aura, dual sweep, uber laser, more rockets or droid. Part stats:
  acceleration 8 + 2.4·speed tier, max velocity 10; shields 75→125 by hull
  tier with recharge delay 12.5 s→7.5 s and speed 0.01→0.06; degradation
  always 0.005. On Elite shields halve, delays ×1.25 and max ammo drops 9→5.
- **Damage/death.** A hit with shields down destroys the ship; respawn has 3 s
  invincibility and a brief collision-control penalty. In Normal mode losing
  all lives ends the run; Racing never dies.
- **Powerups.** Kill drops are boxes of: shields (capped), upgrade (primary
  level, random when maxed), ammo (to 9/5), zaurium, extra life, race time, or
  a randomizer picking from valid options.
- **Score, lives, currency.** Kills/pickups add score; life thresholds
  (`NextLife` 2000 with +3000 then +1500 steps on Normal; 5000/+5000 on
  Hard/Elite) grant extra ships. Zaurium buys parts; per-stage highscores are
  stored per difficulty and total score separately.
- **Racing.** 5 tracks with boost gates (overdrive) and a finish line; runs
  record ghost samples (position/time) that replay alongside, can be exchanged
  in the net lobby (send/receive/transmit), and are timed as SS.mmm against a
  win/lose plate.
- **Customization.** Purchases unlock 4 categories × 10 parts on a 1–5 stat
  scale (power/armor/speed/boost + cost); higher tiers are difficulty-locked.
  Equipping changes hull/wing art, thruster color and stats. Difficulty
  (Normal/Hard/Elite) also tunes shields/ammo and part availability.

## 4. Controls

- **Touch absolute:** touching moves the ship toward the finger (play-area
  mapped, −60 px Y offset so the thumb doesn't cover the ship), with
  acceleration-limited chase; a touch also fires the secondary when ammo
  remains (taps > 50 px away don't count) while primary fire is automatic.
- **Tilt:** the accelerometer vector (screen-relative transform) steers the
  ship; sensitivity is configurable 1–9 in options and recalibrated when
  entering a stage (current acceleration captured as the neutral point).
- Pause is the HUD button/menu action; the `Input` layer exposes four player
  indexes and menu actions (Back/Select/Fire/Tap/Play/Pause/direction/Spawn).
- No multitouch gameplay; options expose effects volume, sound levels
  (off/low/med/high) and control style.

## 5. Content inventory (must be re-authored)

280 files: 237 `.xnb` + 43 `.png`.

- **Ships/parts:** 10 hull + 10 wing sheets, thruster, shield bubble/meter, 5
  palettes, droid sheets. **Enemies:** asteroid rock/ice/fire sheets, drones,
  turrets, racer droid, 10 named enemies (wasp, hornet, redeye, telson,
  orbital, general, …); each boss family has body/shell/appendage/tell/
  weakpoint/icon sheets.
- **Worlds:** 5 worlds × 3 parallax layers (planet or station top/mid/bottom),
  asteroid belts, nebula, atmosphere overlays, starfield.
- **Race/UI:** boost gate, gate explosion, finish line, speedometer; 5 fonts,
  menu textures (background/grid/sliders/arrows/locks/level-select art),
  placement files, HUD bar/pack, end plates, localization en/es/fr.
- **Audio (20 cues):** bomb, boss die/rush, enemy hit, explosion, extra life,
  game over, pickups, laser/cannon, photon single/double, rocket, player die,
  UI click/swoosh — all re-synthesized. Art is likewise re-authored: vector
  ships and multi-part bosses, procedural planet parallax, token HUD, and our
  own script-driven 10-stage/5-race data.

## 6. Implementation plan

- Engine: `ui/apps/games/spacebattle2/SpaceBattleEngine.kt` — pure Kotlin
  `object` over immutable `State` (ship, entities, projectiles, powerups,
  script cursor, score/lives/ammo/zaurium, race ghost); fixed 60 Hz `step(dt)`
  with seeded RNG for drops and AI.
  ```kotlin
  object SpaceBattleEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Data: bundled re-authored stage scripts (same record vocabulary: script/
  batch/entity/background/path/autopilot) and part tables; compact ghosts.
- Screen: `SpaceBattleScreen.kt` in `DetailScaffold`, portrait `Canvas` with
  parallax layers, entity rendering, HUD bar, menus and level select drawn
  from tokens; zero corner radius.
- Inputs: drag-position steering via `pointerInput`, optional accelerometer
  tilt with neutral calibration, fire/pause targets.
- Persistence: prefs (sound, sensitivity, control style, build), per-stage
  highscores, total score and ghosts via `graph.appState`/`graph.games`.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): steering/tilt, fire
  cadence and ammo, shield/Elite math, extra-life thresholds, powerup caps,
  boss weak-point gating, stage-script parsing, ghost determinism, race
  timing, part purchase gating.

## 7. Citation log

- `Zauri.exe!Zauri.ZauriGame` / `GameScreen` — 272×480, state machine, stage
  width, HUD wiring, Normal/Racing flow, win/lose conditions.
- `Zauri.exe!Zauri.GameScreen.Update` — autofire, secondary fire gating at
  50 px, TouchAbsolute steering, tilt steering.
- `Zauri.exe!Zauri.Player` / `Player.SetShipStats` / `PlayerShip` — lives,
  ammo 9/5, NextLife thresholds, acceleration/shield/recharge tiers, Elite
  modifiers, degradation 0.005, boost/collision penalty.
- `Zauri.exe!Zauri.SecondaryWeapons` / `PartType` / `PowerUpType` /
  `PowerUp` — weapon/part/powerup taxonomies and rolling rules.
- `Zauri.exe!Zauri.Prefs` — 10 levels, 5 races, 5 colors, sensitivity,
  sound levels, 4 categories × 10 parts.
- `Zauri.exe!Zauri.StageManager` — script record vocabulary and width.
- `Zauri.exe!Zauri.Boss` / `GenericBoss` / `WeakPoint` / `Appendage` /
  `Drone` / `Turret` / `Asteroid` — boss part structure and enemy taxonomies.
- `Zauri.exe!Zauri.BoostGate` / `FinishLine` / `Ghost` / `GhostData` /
  `NetLobby` — race objects, ghost capture and exchange.
- `Zauri.exe!Zauri.EndGameScreen` / `StageCompleteMenu` / `HighscoreMenu` /
  `OptionsMenu` / `CustomizeShipMenu` / `ControlStyle` / `Difficulty` —
  navigation, options and difficulty gates.
