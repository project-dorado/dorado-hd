# Slider Puzzle

- **Official package:** `PuzzleGame.exe` (Slider Puzzle)
- **Corpus:** `slider_puzele` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `PuzzleGame.exe` | 32 | 136 | 3,895 |
| `Microsoft.Xna.Zune.dll` | dep | 827 | 15,830 |
| `ZuneCoreLib.dll` | dep | 288 | 6,340 |
| `ZuneGamesLib.dll` | dep | 147 | 3,083 |

Portrait 272x480 by default; flips to landscape-right for rotated puzzles and
restores portrait on the results screen. Microsoft-IT branded. Supports
single-player, local pictures from the Zune media library, built-in images and
Zune network multiplayer (host/join). Options persist to `options.xml`, high
scores to `scores.xml`.

## 2. Screens & navigation

- **Main Menu**: `Single Player`, `Multiplayer`, `Options`, `High Scores`,
  plus a Quit flow with a Yes/No `QuitConfirmScreen`.
- **Single-player flow**: `SelectPictureSource` (built-in images / Zune
  pictures / generated tile art) → `SelectPuzzleType` (image or numbered
  tiles) → picture picker (`SelectPictureFromBuiltInScreen` /
  `SelectPictureFromZuneScreen`) → `SelectNumTilesScreen` (`8`, `15`, `24`,
  `Back`) → `GameScreen`.
- **Multiplayer flow**: `SelectMultiGameScreen` (Host / Join) → lobby with
  player list and START GAME, waiting/tap-to-continue screens, and a
  `Players in room:` HUD; a gamer leaving ends the match
  (`PlayerLeftScreen`).
- **Game HUD**: board plus `Pause` button; pause screen offers resume, options
  (sound, share-high-score), number-overlay toggle and an image peek toggle,
  and a confirm-quit. A **solved** overlay draws the complete picture with a
  `TapToContinue` prompt over a grey band; tapping it opens the results screen.
- **Results** (`RestartScreen`): win shows moves and elapsed time; loss shows a
  red lose label (host/client variants in multiplayer) and a menu/restart
  button. High scores are separated into 8/15/24 lists.

## 3. Rules, scoring & progression

- Classic **N-puzzle** with one empty cell. Three sizes: **8 = 4 rows × 2
  columns**, **15 = 5×3**, **24 = 6×4** (portrait 272 wide; a 33 px strip at
  the top is reserved, so the board is 480−33 = 447 px tall). If the rotated
  flag is set the device switches to landscape-right and rows/columns swap.
- Tile geometry: cell width = screenWidth/columns, cell height = boardHeight/
  rows; each cell crops the source image (`imageWidth/columns`,
  `imageHeight/rows`). Tiles are instantiated from the bottom-right cell
  backwards, so index 0 (bottom-right) is rebuilt as the blank with the `Z1`
  art; the remaining slots carry the descending number overlay
  (`count, count−1, …`) and their crop.
- **Shuffle**: exactly `rand.Next(200, 301)` **legal** blank-slide moves are
  applied, so every start is solvable.
- **Move**: tapping a tile orthogonally adjacent to the blank slides it into
  the blank with a **0.15 s** interpolated motion; the move counter increments.
  Taps on non-adjacent or empty tiles are ignored (all buttons are briefly
  disabled during the animation).
- **Win**: every tile is in its original cell. On win the full image appears
  with a `TapToContinue` prompt, the move counter freezes, and the elapsed time
  (`finishTime − startTime`) is recorded. The results screen sorts that time
  into the 10-entry high-score list for the chosen size (8/15/24) and trims to
  the top 10. `GameOptions.shareHighScore` optionally exchanges lists in
  multiplayer.
- **Multiplayer**: each client reconstructs the same picture and tile layout;
  the host's start seeds the shuffle, tiles are synchronized, and when a player
  has fewer than 4 tiles left everyone sees `"<Gamertag> only has N tiles
  left!"`; finishing broadcasts the finish and shows the solved overlay.
- **Number overlay**: toggles a translucent number/text layer over the picture
  so players can solve photo puzzles by number.

## 4. Controls

- Single-finger **tap** on a tile adjacent to the blank; no drag, no
  multitouch for board moves.
- Menu buttons are simple tap targets; the picture picker is a scrollable
  list of album/photo buttons; the game board uses device orientation as
  described (landscape only for rotated layouts).
- App deactivation/back button reaches the quit-confirm flow instead of
  losing the board silently.

## 5. Content inventory (must be re-authored)

51 files: 47 XNB + 3 XML + 1 TXT.
- `Images/Puzzle` (33): bundled photo set (named like camera files) used by the
  built-in picker — replace with original photography/art or procedural
  gradients; `Images/` (12): title backgrounds, `buttons` atlas (+`buttons.txt`),
  `p8/p15/p24` number-button art, `Z1` blank tile, back/menu/quit frames,
  `simple_button`.
- `Text` (2) + `Strings/en.xml`: localize labels (`Singleplayer`,
  `Multiplayer`, `Options`, `HScores`, `TapToSelect`, `TapToContinue`, …).
- Audio: `Button` tap and `Honk` win cue.
- High-score/options persistence schemas (`scores.xml`, `options.xml`).

## 6. Implementation plan

- Engine: `ui/apps/games/SliderPuzzle.kt` — `object SliderPuzzleEngine` with
  immutable `SlideState(rows, cols, tiles: List<Int>, moves, startedAtMs)` and
  `step(dt)` for slide interpolation; `newGame(rows, cols, rng)` performs the
  200–300 legal shuffles; `canMove(index)` and `applyMove(index)` are pure.
- Screen: `SliderPuzzleScreen` in `DetailScaffold("slider puzzle")`, Canvas
  tiles cropped from a procedurally drawn picture, 0.15 s slide animation,
  solved overlay + tap-to-continue, results panel with moves/time, high-score
  list per size. Menus: source → type → size → game.
- Persistence/SFX: `graph.appState` for mid-board save and options;
  `graph.games.record("slider-puzzle", moves, "8") / ("15") / ("24")` or the
  time-based variant; `SfxBank` for tile move and win.
- Tests (`app/src/test/java/com/heretek/dorado_hd/SliderPuzzleEngineTest.kt`):
  shuffle is legal and solvable; win detection; move counter; adjacency rule;
  size geometry (4x2/5x3/6x4); 0.15 s animation end-state; results sorting and
  top-10 trim.

## 7. Citation log

- `PuzzleGame!SelectNumTilesScreen.setUpGame` — 8/15/24 → 4x2/5x3/6x4, 33 px
  offset, crop math, reverse numbering, blank `Z1`, 200–300 shuffle moves.
- `PuzzleGame!GameScreen.moveTile` — adjacency branches, 0.15 s interpolators,
  move counting, button disable/enable.
- `PuzzleGame!GameScreen.CheckWin` / `solved_Clicked` — completion, `Honk`,
  finish time, results push.
- `PuzzleGame!GameScreen.HandleTouchesEnded` — tap-to-continue on solved.
- `PuzzleGame!Tile.isCorrect` / `changeRowCol` / `moveTileShuffle` — position
  bookkeeping and shuffle moves.
- `PuzzleGame!CONFIG` — board metrics, image source enum, multiplayer packets.
- `PuzzleGame!RestartScreen..ctor` — move/time formatting, 8/15/24 high-score
  lists, top-10 trim, share-high-score packet.
- `PuzzleGame!PauseScreen` — options overlay, number overlay, image peek, quit.
- `PuzzleGame!SelectPictureFromBuiltInScreen` / `SelectPictureFromZuneScreen`
  — picture sources and pickers.
- `PuzzleGame!HighScores` — three persisted lists and `hasGuid` de-dup.
