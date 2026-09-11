# BBQ Battle

- **Official package:** `BBQBattle.exe` (bbqbattle)
- **Corpus:** `Zune HD Apps (Decompiled)/bbqbattle` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `BBQBattle.exe` | 24 | 145 | 4042 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 67 | 308 | 6420 |
| `ZuneGamesLib.dll` | 36 | 170 | 3708 |

- Runs in **landscape** (480×272), orientation forced to landscape-right,
  multitouch disabled by the ZuneCoreLib device layer.
- Menu buttons and wave transitions use ZuneGamesLib's push/pop `ScreenStack`;
  settings persist as `settings.xml` (control style, volume, easy mode,
  highest wave) and mid-run state as `autosave.xml`.

## 2. Screens & navigation

- Cool/loading splash → main menu (Play, Learn to play, About); Play offers
  the autosave resume prompt when one exists.
- In-game: the board with HUD (food health, credits, wave, lives, speed
  slider, pause), a build/upgrade/sell cell popup, a 5 s wave preview, pause
  menu and the milestone bonus screen.
- Options: control style (righty/lefty/thumbs), volume, difficulty
  (easy/normal). Game over shows wave/time/kills; deleting the autosave is
  the retry path. Best wave is the high score, overwritten with confirmation.

## 3. Rules, scoring & progression

A grid tower-defense on a picnic blanket. A food item (random of 4) sits at the
right edge with **100 HP**; ants march left-to-right and eat it down.

- **Board.** 11×7 cells of 36 px starting at (20, 20); routes recompute by BFS
  from the food on placement. Blocking towers are forbidden from sealing the
  route; the build popup can restrict choices to the one non-blocking tower.
- **Economy.** Start with **20 credits**. No per-kill pay: 5 s before each wave
  ends you earn `min(20, floor(credits·(0.125 + bonus)) + 10)`, and selling
  refunds 50 % of build+upgrade cost — saving is rewarded.
- **Towers** (cost / upgrades / L3 stats, max level 3):
  - **Spray can** — 6 / 3-4-5; 48 px, 5 dmg, 1.125 s; per level −0.425 s,
    +12 px, +5 dmg. Blocks path.
  - **Gum** — 8 / 4-5-6; 20 px, 0 dmg, 0.24 s; slows to 45 % for 0.25 s
    (upgrades −10 % slow, +0.55 dmg). Does not block path.
  - **Bug bomb** — 12 / 6-8-10; 66 px, 2.15 dmg splash, 0.8 s; +11 px,
    −0.15 s, +1 dmg per level; ground-only. Blocks path.
  - **Zapper** — 15 / 7-9-11; 96 px, 50 dmg, 1.5 s; fliers first, chains a
    1/15-damage bite; +16 px, −0.75 s, +25 dmg. Blocks path.
- **Creeps** (level-scaled): ant speed 20 + 1.75·lvl, HP 30 + 20·lvl; snail
  15 + 1·lvl, HP 85 + 43·lvl; bee flies at 25, HP 150 + 30.5·lvl; centipede
  min(200, 50 + 4·lvl), HP 25 + 16·lvl. Each bites for 1 damage at the goal;
  slow state uses the creep speed at half animation rate.
- **Waves.** 30 data-driven waves (spawn frequency, delay, per-wave repeat
  count, creep type/level/count rows). A wave ends when its delayed spawn
  queue empties, then the settle delay runs. Wave 30 wraps: the same 30 waves
  at enemy level + repeats·(7–9 + repeats²) and spawn interval ÷ (repeats+1).
  A 5 s countdown and a 3-wave type preview precede each wave.
- **Ramp.** Repeat scaling plus milestone choices after waves 10/20/30: one of
  damage +10 %, build cost −1, cooldown −10 %, interest +10 %, radius +7.5 %,
  upgrade cost −1. Easy mode grants each new tower a free upgrade.
- **Loss/win and save.** Food at 0 HP ends the run (wave/time/kills summary);
  no win state — score is the highest completed wave. `autosave.xml` stores
  towers, food, credits, time, waves/repeats, bonuses, creeps and kills;
  `settings.xml` stores best wave, control mode, volume and easy flag.

## 4. Controls

- Tap a grid cell: empty cell opens the build chooser (spray/gum/bomb/zapper,
  4 stacked 36 px options); occupied cell opens upgrade/sell for that tower.
  Invalid actions play a rejection cue.
- Popup opens on the side away from the screen edge, flipped depending on
  control style: righty (popup left of cell), lefty (right), thumbs (toward
  screen centre based on which half the cell is in).
- Vertical speed slider (right) sets the game-speed multiplier with audio
  time-scaled; pause top-right. No accelerometer, landscape locked, single
  touch; wave preview and interest are automatic.

## 5. Content inventory (must be re-authored)

77 files: 53 `.png`, 20 audio `.xnb`, 4 `.xml` (no music track).

- **Level data to re-author (do not copy):** `testwave.xml` (~11.9 KB, the
  30-wave table: creep rows + cadence); we generate our own 30-wave schedule.
- Towers: spray can (grass variants), gum, bug bomb (base/top/no-grass),
  zapper (grass variants), lightning, smokes, ghost/upgrade pips, runtime
  range circle. Creeps: ant, snail, centipede, bee sheets + shadow.
- Board/UI: picnic background, 4-food sheet, cell highlight, HUD icons,
  tower/preview icon sheets, slider arrow, UI bar, buttons, pause, menu panels,
  main-menu/high-score/LTP art, `Text/Fonts.xml`.
- Audio (20): place/upgrade/sell, four deaths, fire loops (spray/bomb/zapper/
  gum), invalid, cell touch, wave start, warning, milestone stinger, high
  score, play, sound toggle, button down — all re-synthesized.

Re-author: Canvas towers, four insect sheets with food-seeking walks, blanket
board, fresh wave schedule and synthesized SFX.

## 6. Implementation plan

- Engine: `ui/apps/games/bbqbattle/BbqBattleEngine.kt` — pure Kotlin `object`
  over immutable `State` (grid, towers, creeps, wave cursor, credits, food HP,
  bonuses, autosave); fixed `step(dt)` with seeded RNG; BFS placement
  validation (recompute route, reject if goal unreachable).
  ```kotlin
  object BbqBattleEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Screen: `BbqBattleScreen.kt` in `DetailScaffold`, landscape `Canvas`; board,
  towers, projectiles/beams, creeps with health bars, HUD, build/sell popup and
  wave preview all token-driven; zero corner radius.
- Inputs: cell taps and popup hit targets via `pointerInput`; speed slider drag;
  pause.
- Persistence: autosave through `graph.appState`; highest wave and options
  through `graph.games` + `graph.appState`; SFX via `SfxBank` (build, upgrade,
  sell, invalid, cell tap, wave start, deaths).
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): path BFS and
  block-rejection, interest formula and cap, tower/resale value math, upgrade
  level caps, creep level scaling on repeats, wave cadence, easy-mode free
  upgrade, food-HP loss and game-over, autosave round-trip.

## 7. Citation log

- `BBQBattle.exe!PicnicPanic.PicnicPanic` — landscape force, settings load
  (control mode, volume, easy mode), content registration, screen start.
- `BBQBattle.exe!PicnicPanic.GameScreen` constructor — 20 starting credits,
  100 food HP, first wave after 5 s.
- `BBQBattle.exe!PicnicPanic.GameScreen.CalculatePath` — 11×7 BFS route,
  blocking cells, path cache.
- `BBQBattle.exe!PicnicPanic.GameScreen.Upgrades` — six permanent bonus fields.
- `BBQBattle.exe!PicnicPanic.GameScreen.DisplayBonusUpgrades` — choices after
  waves 10/20/30 and one-time gating.
- `BBQBattle.exe!PicnicPanic.GameScreen.IncreaseGoldWithInterest` — cap 20,
  12.5 % + bonus, +10.
- `BBQBattle.exe!PicnicPanic.GameScreen.NextWave` / `PrepNextWave` — spawn
  timer, wave total time, 30-wave wrap, repeat level boosts.
- `BBQBattle.exe!PicnicPanic.GameScreen.HandleTouchesBegan` /
  `HandleTouchesMoved` / `HandleTouchesEnded` — cell popup placement per control
  style, build/upgrade/sell costs and refunds.
- `BBQBattle.exe!PicnicPanic.GameScreen.Update` — creep removal, tower update,
  game-over at food 0.
- `BBQBattle.exe!PicnicPanic.GameScreen.SaveCurretState` — autosave fields.
- `BBQBattle.exe!PicnicPanic.SprayCan` / `Gum` / `BugBomb` / `Zapper` /
  `Tower.SellValue` — costs, upgrade curves, radii, damage, fire rates,
  BlockPath flags, slow factor, 50 % refund.
- `BBQBattle.exe!PicnicPanic.Creep` / `Ant` / `Snail` / `Bee` / `Centipede` —
  slow window, health bars, per-level stats and flying flag.
- `BBQBattle.exe!PicnicPanic.Wave` / `CreepData` / `SaveFile` /
  `SettingsFile` — wave fields and persistence keys.
- `BBQBattle.exe!PicnicPanic.EndGameScreen` / `OptionsScreen` — results
  summary and mode/sound/difficulty toggles.
