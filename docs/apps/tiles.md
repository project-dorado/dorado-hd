# Tiles

- **Official package:** `Tiles.exe` (Tiles)
- **Corpus:** `tiles` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Tiles.exe` | 19 | 97 | 2,083 |

Portrait 272x480. A 3×3 sliding-number puzzle (the "15-puzzle" formula at
3×3) with two modes: Classic (counts moves) and Time Trial (counts seconds).
Save data persists to `tiles.sav`; soft-key art sits along the bottom of the
board. No dependency assemblies were mined for this title.

## 2. Screens & navigation

- **Main Menu**: `Classic Mode`, `Time Trial`, `High Scores`, `Options`,
  `Help`, `About`, a sound on/off toggle, all with matching soft-key art.
- **Game**: board occupies y=140..413 with 91 px tiles; a moves or time strip
  at the top; two touch soft keys at the bottom — `Restart` (left, x=0) and
  `Menu` (x=148), both 124x47 at y=433. Confirm overlays appear over a fill
  panel: quitting shows Yes/No, restarting shows Yes/No.
- **Game Over**: shows the final result — for Classic the move count and best
  moves, for Time Trial the elapsed and best time — with `Restart` and `Main
  Menu` soft keys.
- **High Scores**: lists least moves, quickest time, wins and losses; offers a
  `Reset Data` confirmation.
- **Help / About / Options**: text pages; Options toggles audio.

## 3. Rules, scoring & progression

- **Board**: `numberOfTiles = 9` — a **3×3** grid of nine tiles (8 numbered
  plus the blank). Tiles are placed in a 3-row × 3-column pattern with 91 px
  steps starting at y=140; tile art is loaded from `sprites/0..8`. The blank
  is array slot **0** (top-left): the code never reassigns `blankTileArrayID`,
  so it keeps its default 0 (only the debug `EndGameTest` swaps it to 8).
- **Shuffle**: after building the board, **1000 random legal blank swaps** are
  applied (a swap is legal only with an orthogonal neighbour), guaranteeing a
  solvable layout.
- **Countdown**: `timesToRandomizeTiles = 8`. A 1 s timer tick re-runs the
  1000-swap shuffle and decrements the counter while > 0; the overlay shows
  `countdown_3` while > 6, `_2` > 4, `_1` > 2, `_go` > 0. On reaching 0 the
  timer switches to a 1000 ms period and starts counting elapsed play time.
- **Move**: a touch whose **previous position is (0,0)** (a fresh press) on a
  tile orthogonally adjacent to the blank swaps the two; `swapCount` increments
  and one of three Move cues plays at random. Presses on non-adjacent tiles do
  nothing; multi-touch during a press aborts the move handling.
- **Win**: every numbered tile must occupy its home slot — slots 1..7 must
  hold values 0..6 in order and slot 0 must hold value **8** (the blank
  sprite), so the solved board has the blank at the top-left with the numbers
  ascending across the rest. On win the game stops the clock, records the
  result, plays the Win cue if the mode's best was beaten, increments the wins
  counter and opens the game-over page. Quitting or restarting with
  `swapCount > 0` increments the losses counter.
- **Best scores**: Classic default best is **500 moves**, Time Trial default is
  **600 s**; both are updated only when the new result beats them. Wins/losses
  and the audio flag are stored in the same save file and reloaded on boot; if
  loading fails, defaults are restored.
- **Progression** is endless: no levels; each completed game re-shuffles and
  resets the countdown, with the only meta-progression being the two best
  records and the wins/losses tally.

## 4. Controls

- Single-finger **tap** a tile adjacent to the blank to slide it; no drag,
  swipe or multitouch board gestures.
- Bottom soft keys are tap regions; `Restart` and `Menu` open Yes/No confirm
  overlays (buttons swap to the confirm soft keys while active).
- Back/app deactivation shows the same quit confirmation; the options screen
  toggles audio with a tap.

## 5. Content inventory (must be re-authored)

85 files: 74 sprites + 4 sounds + 3 fonts (+ XNB copies).
- `sprites/` (74): nine number tiles (0–8, PNG + XNB), `GameBackground`,
  `BackgroundFill`, `countdown_1/2/3/go`, `Quit`, `Restart`, `MovesLabel`,
  `TimeLabel`, `wellDone`, `YesSoftkey`/`NoSoftkey`, `MenuSoftkey`,
  `RestartSoftkey`, `ResetSoftkey`, `ClassicMode`, `TimeTrial`, sound buttons,
  `MainMenuButton`, `Instructions`, `Statistics`, `About`.
- `sounds/` (4): Move1, Move2, Move3, Win (WAV + XNB).
- Fonts: ArialFont, ArialFont_9, MenuFont.
- `tiles.sav` schema: leastMoves, leastTime, win, lose, audioEnabled.

## 6. Implementation plan

- Engine: `ui/apps/games/Tiles.kt` — `object TilesEngine` with immutable
  `TilesState(slots: List<Int>, swapCount, elapsedMs, phase, audioOn)` and a
  deterministic `step(dt)` driving countdown ticks and the clock; helpers
  `legalMove(index)`, `swap(index)`, `isSolved()`, `shuffle(rng)`.
- Screen: `TilesScreen` in `DetailScaffold("tiles")`, Canvas tile faces with
  token numerals (no raster art), moves/time strip, bottom soft keys and
  confirm overlays, mode-aware game-over panel, high-scores panel.
- Persistence/SFX: least moves/time + win/lose via `graph.appState` JSON;
  `graph.games.record("tiles", moves, "classic")` and time variant; `SfxBank`
  for Move1–3 and Win.
- Tests (`app/src/test/java/com/heretek/dorado_hd/TilesEngineTest.kt`):
  1000-swap shuffle stays legal/solvable; only adjacent-to-blank moves apply;
  win pattern detection; countdown 8-tick re-shuffle schedule and display
  windows; clock start at tick 0; best-score update only on improvement;
  losses increment on quit with moves; save round-trip defaults 500/600.

## 7. Citation log

- `Tiles!GamePlayScreen.InitializeTiles` — 3x3 layout, 91 px steps, y=140,
  nine tile sprites.
- `Tiles!GamePlayScreen.RandomizeTiles` — 1000 legal blank swaps.
- `Tiles!GamePlayScreen.UpdateTime` — 8-tick countdown re-shuffle, 1 s tick,
  elapsed-seconds counting.
- `Tiles!GamePlayScreen.UpdateLogic` — adjacency rule, previous-position
  check, swap, random Move cue, `swapCount`.
- `Tiles!GamePlayScreen.CheckIfGameOver` — 1–8 ordering check, clock stop,
  Win cue, win counter.
- `Tiles!GamePlayScreen.Draw` — countdown thresholds > 6/4/2/0, mode HUD,
  soft keys.
- `Tiles!NumberTile..ctor` — sprite/value/position tile model.
- `Tiles!Tiles.SaveGameData` / `LoadDefaults` — defaults 500/600, win/lose,
  audio flag.
- `Tiles!GameScreens.SetFinalScoreAndTime` — final result routing.
- `Tiles!MainMenuScreen` — Classic/Time Trial/high scores/options/help/about.
