# Hairball

- **Official package:** `Hairball.exe` (Hairball)
- **Corpus:** `hairball` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Hairball.exe` | 28 | 129 | 2,843 |
| `Microsoft.Xna.Zune.dll` | dep | 829 | 15,852 |
| `ZuneCoreLib.dll` | dep | 304 | 6,297 |
| `ZuneGamesLib.dll` | dep | 170 | 3,667 |

Portrait 272x480, fixed 30 fps target. Constant free-fall "drain" game: the
player is a rotating hairball riding rising blocks of sludge. A front
foreground strip animates up and down to frame the action; a grey overlay and
pause glyph cover the board when paused. High scores persist to
`HairballHighScores.xml`; volume settings persist separately.

## 2. Screens & navigation

- **Splash → Loading → Main Menu.** Menu entries: `Start`, `High Scores`,
  `How To Play`, `Options`. A large rotating hairball and logo animate behind
  the text; a foreground strip anchors the bottom.
- **Gameplay** — score at the lower-left (`Score: 000000`, black drop shadow),
  a centred Ready/set/GO countdown, the pause glyph. Pause is toggled by any
  finger **release**; while paused a grey sheet covers the screen and a `Quit`
  menu entry appears beside the pause button.
- **Game Over** — `Game Over` art, final score and high-score comparison from
  `HighScoreMgr` (`Final Score`, `High Score`, `New High Score`), entries
  `Retry` and `Menu`.
- **High Scores / How To Play / Options / About** — static list or text
  screens; Options adjusts music and effects volume with left/right arrows;
  About credits G.A.M.E. Studios.

## 3. Rules, scoring & progression

- The player hairball is a 20x20 box at (136, 40) that falls **12.8 px per
  frame**, forever. Sludge blocks scroll upward at the current game speed.
- Sludge field: **40 recycled blocks** laid out on a grid of at most **6
  columns × 5 rows**, spaced **42 px horizontally / 80 px vertically**. The
  first row starts left at (32, 400) or right at (240, 400); each new row
  alternates direction. When laying a row, a slot is skipped with probability
  **1/6** (`rand.Next(6) == 1`) until 5 blocks have been placed or 6 columns
  scanned. A new row is queued whenever at least **4** blocks are free and the
  last placed row is at or above y=720.
- **Speed ramp**: starts at **3.5** px/frame and increases **+0.3** at every
  12-second boundary of total game time, clamped at **15** px/frame.
- **Collision**: AABB against active sludge. Landing on top snaps the player
  to `blockTop − 20`; blocks push the player up. The player is clamped to
  x ∈ [20, 252] and y ≤ 460. Falling off the top of the screen
  (`y ≤ −20`) ends the run: game-over jingle, stop activity signal, exit to
  the game-over screen. The hairball rotates with tilt and flips sprite
  direction.
- **Scoring**: the score counter increments whenever total game milliseconds
  are even — at the 30 fps target that is effectively **+1 per frame (≈30/s)**,
  displayed zero-padded to six digits. `HighScoreMgr` saves the run score and
  shows top scores; nothing else affects score.
- **Progression** is endless: one continuous run, no levels, no lives; the
  only difficulty ramp is the speed increase above.

## 4. Controls

- **Tilt** (accelerometer X): if `accel.X > 0.05` the player moves right by
  `7 + 6·accel.X` px/frame with a roll, otherwise flips and moves left by
  `7 − 6·|accel.X|`. There is no vertical control; gravity always wins.
- **Touch**: any touch **release** toggles pause/resume. Pausing stops the
  activity signal, pauses in-game music and adds a `Quit` entry.
- Engine pause (app deactivation) forces the same paused state; resuming
  restarts the activity signal and music.

## 5. Content inventory (must be re-authored)

24 XNB files:
- `Sprites/` (15): background layers 1–2, Foreground, Sludge, Hairball,
  GameLogo, HairballLogo, PauseButton, GreyScreen, Panel/PanelSmall, Button,
  ArrowButton/ArrowButtonDisabled, GameOver.
- `Fonts/` (3): menufont, instructionfont, countdownfont.
- `Sounds/` (6): Beat, Button, GameOver, Menu, Play, Whohoo — plus re-authored
  menu/in-game music loops driven by `ZuneSound`.
Re-author all art as vector/token shapes; keep a 5-block × 6-slot grid look.

## 6. Implementation plan

- Engine: `ui/apps/games/Hairball.kt` — `object HairballEngine` with immutable
  `HairballState(playerX, playerY, blocks: List<Block>, speed, score, paused)`
  and a deterministic `step(dt: Float, tiltX: Float): HairballState`. Sludge
  placement, the shuffle/queue rule and the 12-second speed tick are pure.
- Screen: `HairballScreen` in `DetailScaffold("hairball")`, Canvas-drawn
  circles (hairball) and sharp-corner quads (sludge, zero radius); score text
  bottom-left; grey pause overlay; touch-release pause only.
- Tilt: reuse the HD accelerometer feed (`AppSensors`) and clamp to the same
  ±7 px/frame envelope; ignore sensor noise below the 0.05 deadzone.
- Persistence/SFX: best score via `graph.games.record("hairball", score, null)`;
  `SfxBank` for Beat/Go, Button, GameOver; optional looping drain ambience.
- Tests (`app/src/test/java/com/heretek/dorado_hd/HairballEngineTest.kt`):
  40-block recycling; row skip probability under seeded RNG; speed steps at
  12 s boundaries and 15 cap; landing snap; top-of-screen loss; score tick
  monotonic per frame.

## 7. Citation log

- `Hairball!Gameplay.LoadContent` — player size/position, 40 sludge blocks,
  extents 15x10, starting speed 3.5, music switch.
- `Hairball!Gameplay.UpdatePlayerInput` — tilt deadzone and
  `7 ± 6·accel.X` movement, touch-release pause.
- `Hairball!Gameplay.HandleGameSpeed` — +0.3 every 12 s, cap 15.
- `Hairball!Gameplay.CountDown` / `DrawCountDown` — Ready/set/GO timing, Beat
  and Whohoo cues.
- `Hairball!PhysicsManager.Update` / `TestAABBvsAABB` — 12.8 fall, snap,
  clamps, top-of-screen loss.
- `Hairball!LevelManager` — 6x5 grid, 42/80 spacing, 1-in-6 skip, 4-block
  reserve, alternating placement rows, 720 recycle threshold.
- `Hairball!ScoreManager.Update` — per-frame +1 counter rule.
- `Hairball!HighScoreMgr` — `HairballHighScores.xml` persistence and game-over
  score text.
- `Hairball!MainMenuScreen..ctor` / `GameOverScreen..ctor` — entry lists and
  Retry/Menu navigation.
- `Hairball!OptionsScreen` — music/effects volume arrows.
- `Hairball!GameplayUtil.UpdateForeground` — foreground strip travel −16/−32.
