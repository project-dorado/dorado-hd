# SuperNova

- **Official package:** `Supernova.exe` (SuperNova)
- **Corpus:** `supernova` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Supernova.exe` | 21 | 109 | 3,304 |
| `Microsoft.Xna.Zune.dll` | dep | 829 | 15,852 |
| `ZuneCoreLib.dll` | dep | 308 | 6,420 |
| `ZuneGamesLib.dll` | dep | 170 | 3,669 |

Portrait 272x480. A one-touch chain-reaction game: a pool of drifting coloured
star dots bounces around the playfield; one tap anywhere spawns a white
explosion dot, and every star it touches explodes and passes the chain on. A
52 px strip at the bottom is reserved for HUD/time. Top-10 high scores persist
through the `Settings` store.

## 2. Screens & navigation

- **Main Menu**: `Play`, a `Mode` toggle cycling Normal → Hard → Endless,
  `Sounds`, `About`, and a slide-out `High Scores` panel (10 entries). Mode is
  persisted in settings; the game boots in Normal.
- **Level flow**: every level opens with a **Level Start overlay** showing the
  goal (`needed of total Stars`); play begins after it is dismissed. A
  **Level Complete overlay** shows the level and its score with a `Continue`
  button; a **Retry overlay** appears when the goal is missed or the timer
  expires.
- **Game HUD**: star counter `current`, goal `needed/total`, a star
  on/off indicator, pause button, and a large shrinking `timeLeft` numeral
  during Normal/Hard.
- **Pause**: `Resume`/quit plus an exit-confirm page; a settings/volume string
  is shown on the menu. App deactivation pushes pause automatically.
- **About** page scrolls in beside the menu; transitions use the shared
  slide-screen machinery.

## 3. Rules, scoring & progression

- **Pool**: 60 dots pre-built from **19** star colours (`star_blue`,
  `star_cobalt`, … `star_yellow`); one extra white (`star_white`) "spawner"
  dot. On level start, `totalDots` dots spawn at
  random positions (x anywhere, y in [0, 480−52)) with a random direction and
  speed `50 + rand·50` (Hard: `50 + rand·25`, then ×0.85) px/s. Spawn uses a
  0.5 s grow interpolation and then enters `Moving`.
- **One tap per level**: `HandleInput` is accepted only while `moveAvailable`;
  a tap spawns the white dot already exploded at the touch point (plus a white
  screen flash and the initial-explosion cue) and disables further input.
- **Chain**: every moving dot that enters an exploding dot's radius explodes
  too; the chain number increments per generation and the score awarded for a
  dot is `chainNumber · value`, where `value` = **5** (Hard: **10**, and the
  grow/stay/shrink timings are ×0.75). Explosion animation grows to 5× the
  10 px base over 0.5 s, holds 0.75 s, then shrinks over 0.5 s; the floating
  score value is printed at the dot.
- **Motion**: moving dots bounce off all four edges; the bottom limit is
  `480 − 10 − 52 = 418` px. After the chain ends (no explosion left and no
  pending input) all remaining active dots shrink away and the level resolves.
- **Levels**: Normal and Hard each have **12** fixed levels given as
  (total dots, needed to pass):
  `(60,50) (55,48) (50,43) (45,38) (40,30) (35,22) (30,17) (25,13) (20,9)
  (15,6) (10,4) (5,2)`; Hard differs only at level 12 → `(5,3)`. Clearing
  ≥ needed completes the level; otherwise Retry. Normal/Hard end after level
  12; the separate **Endless** mode runs 60 dots with 0 needed and loops
  (level counter capped at 99).
- **Timer**: 30 s per level on Normal, 15 s on Hard, no timer in Endless.
  Reaching 0 fails the level (units tick down audibly).
- **Score**: `levelScore` (chain sum) is multiplied by the level's score
  multiplier and added to the running total. Multipliers by level:
  `1,1,1,2,2,4,4,8,12,30,60,150`; Endless always ×1. A new best score inserts
  into the top-10 list and shows a `New High Score` badge.

## 4. Controls

- **Tap** anywhere below the top HUD strip (the pause button's bounds are
  excluded) to fire the single shot; `HandleTouchesEnded` uses the first touch.
- No drag, tilt or multitouch; menus are tap targets, the high-score panel
  slides via the menu button.
- App inactive → pause; exit-confirm handles back-style quits.

## 5. Content inventory (must be re-authored)

68 files: 52 PNG + 14 XNB.
- `Images/` (54): `star_*` colour set (19 colours + white), 12 level
  backgrounds (`bg_lv01`–`bg_lv12`), `bg_main`, `bg_about`, UI chrome
  (buttons, panels, overlays), explosion/particle textures.
- Audio (12): button_click, countdown, explosion_initial,
  explosion_regular_1/3/5, fail, intro_theme_vo, level_win, level_win_bad,
  mechanical_popup, screen_whoosh.
- Fonts XML and `en.xml` strings (`Normal-Mode`, `Hard-Mode`, `Endless-Mode`,
  `Continue`, `Mode`, scores).
- Replace all star/background art with token-coloured generated shapes.

## 6. Implementation plan

- Engine: `ui/apps/games/Supernova.kt` — `object SupernovaEngine` with
  immutable `NovaState(mode, level, dots, exploding, score, timeLeft)` and a
  deterministic `step(dt)`; `LevelTable` constant for the 12 (total, needed)
  pairs and the multiplier table; `tap(x, y)` returns a new state with the
  spawner inserted.
- Screen: `SupernovaScreen` in `DetailScaffold("supernova")`, Canvas circles
  with per-colour tokens, explosion radius animation, floating score text,
  HUD star/timer, mode toggle, level-start/complete/retry overlays and
  high-score panel.
- Persistence/SFX: mode + high scores via `graph.appState`/`graph.games`
  (`graph.games.record("supernova", totalScore, "L$level")`); `SfxBank` for
  countdown, initial/regular explosions (rotating variants), win/fail.
- Tests (`app/src/test/java/com/heretek/dorado_hd/SupernovaEngineTest.kt`):
  level table and Hard level-12 exception; one-tap lock; chain propagation and
  `chainNumber·value` scoring; Hard ×2 value / ×0.75 timings / ×0.85 speed;
  edge bounces with the 418 px bottom limit; timeout 30/15 s; multiplier table;
  Endless loops past level 12; high-score top-10 insert.

## 7. Citation log

- `Supernova!DotManager..ctor` — 60-dot pool, 19 colours, white spawner.
- `Supernova!DotManager.ResetLevel` — Normal/Hard 12-level tables, Endless
  CreateLevel(60,0), invalid-level guard.
- `Supernova!DotManager.StartLevel` — spawn spread, speed formula, Hard
  modifier, 0.5 s grow.
- `Supernova!DotManager.HandleInput` — moveAvailable lock, white flash, spawn.
- `Supernova!DotManager.CheckCollisions` / `Update` — chain scoring, needed
  check, complete/retry callbacks.
- `Supernova!Dot` — 10 px base, ×5 explosion, 0.5/0.75/0.5 timings, edge
  bounce, `MaxHeight = 480 − 10 − 52`, score text.
- `Supernova!LevelScreen.ScoreMultiplier` — multiplier table.
- `Supernova!LevelScreen.OnUncovered` — 30 s / 15 s timers, Endless 0.
- `Supernova!LevelScreen.Update` / `HandleTouchesEnded` — timer fail, HUD
  exclusion, tap routing.
- `Supernova!HighScores` — top-10 insert and persistence.
- `Supernova!MainMenuScreen` — Play/Mode/Sounds/About/high-score panel,
  `GetModeString`.
