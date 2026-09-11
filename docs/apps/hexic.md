# Hexic

- **Official package:** `Hexic.exe` (hexic)
- **Corpus:** `Zune HD Apps (Decompiled)/hexic` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** hexic

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Hexic.exe` | 148 | 649 | 14219 |

- Native 272x480 portrait marble-popper; the board is a 10-column hex grid
  whose columns alternate between 8 and 9 cells (85 in total), not a square
  grid. Touch gestures are taps and a circular rotation swipe.
- All content is XNB (91 files); no loose PNGs. A separate fingerprint of
  device settings drives piece size/offsets.

## 2. Screens & navigation

- `MainScreen`: Marathon, Timed, Survival, Learn and Options entries; Exit
  closes the app. Each mode opens `DifficultyScreen` with Normal, Hard and
  Expert choices (plus Continue when a save exists), then
  `GameplayScreen(board)`. (Hexic!MainScreen..ctor, Hexic!DifficultyScreen..ctor)
- `GameplayScreen` HUD: level number, running score, a combo/target bar, the
  hex cursor with rotation arrows, a pause button and a help overlay
  (`HelpOverlayScreen`) that can be toggled in-game. (Hexic!GameplayScreen, Hexic!HelpOverlayScreen)
- Pause (`PauseScreen`) offers resume, volume and main menu;
  `GameOverScreen` renders a reason-specific title (win for pearl/cleared/
  highest-level, loss for bomb/locked/time) plus the final score and, in
  applicable modes, four stat icons; `HighScoresScreen` shows trip records. (Hexic!GameOverScreen.GetTitleText, Hexic!HighScoresScreen, Hexic!PauseScreen)
- `LearnScreen` has a basics page plus per-mode lessons
  (Marathon/Timed/Survival); `OptionsScreen` holds volume, High Scores and
  About; `AboutScreen` is static. (Hexic!LearnScreen, Hexic!OptionsScreen, Hexic!AboutScreen)
- Three save files back resume: `hexmarathon.sav`, `hextimed.sav`,
  `hexsurvival.sav`. (Hexic!MainScreen..ctor)

## 3. Rules, scoring & progression

- Match types: **Cluster** (3+ same-colour neighbours), **Bonus** (cluster
  containing a star), **ThreeBonus** (three stars together), **Flower** (a
  6-piece ring around a centre), **Bomb** (cluster containing a bomb) and
  **BombBonus** (a bomb-and-star trio). Neighbours are the hex-adjacent cells. (Hexic!MatchType, Hexic!Board.IsMatchOnBoard)
- Cursor shapes are `Standard_1x2`, `Standard_2x1`, `Flower`, `Pearl_1x2`
  and `Pearl_2x1`; a rotation gesture turns the selected set clockwise or
  counter-clockwise. (Hexic!CursorType, Hexic!CursorController.OnRotation)
- Scoring (all scaled by the current level multiplier):
  - Cluster: `(5 + 10 × (simultaneous colour clusters − 1)) × level × pieces`,
    then multiplied by (star bonus pieces + 1). (Hexic!Board.OnClusterMatch)
  - ThreeBonus: `100 × pieces × level`, and every piece of the cluster also
    clears its surrounding neighbours (flowers/pearls are protected).
    (Hexic!Board.OnAllBonusClusterMatch)
  - Flower: base `1000 × level × (stars + 1)`, with +500 if the ring centre was
    a black-pearl seed and +1500 if it was a pearl; the centre then becomes a
    FlowerStar unless it already was one. (Hexic!Board.OnFlowerMatch)
  - BombBonus: `5 × level`. (Hexic!Board.OnBombAndBonusMatch)
  - Score is capped at 999,999,999. (Hexic!Score.Add)
- Pearl chain: forming a flower around a FlowerStar converts the centre into a
  Black Pearl (10000, or 15000 if the ring was FlowerStars). A flower or a 3+
  cluster made entirely of black pearls is an **instant win** — 50000/75000 —
  and ends the game as a win. (Hexic!Board.OnFlowerMatch,
  Hexic!Board.OnClusterMatch)
- Bomb timer: bomb pieces tick down (`BombStates.Tick`) and detonate on zero;
  a detonation wipes the board and ends the game as a loss. (Hexic!Board.OnBombExplosion)
- Levels: 7 level records drive colours, multipliers, target combos, bonus and
  bomb frequency, bomb count/grace and start timers. Level 1 has 5 colours and
  multiplier ×1; level 4+ has 6 colours, level 6+ 7 colours; multipliers rise
  to ×7. Bombs unlock at level 3 (frequency 15, count 10, grace 5) and tighten
  to frequency 10, count 6, grace 1 at level 7. A level-up fires when the
  combo target reaches zero; the level-7 record has an endless (−1) target.
  (Hexic!GameLevel..ctor, Hexic!GameLevel.IncrementLevel,
  Hexic!Board.CheckForLevelUp)
- Difficulty is a starting offset into that table, not an AI setting:
  **Normal starts at level 1, Hard at level 3, Expert at level 5**. (Hexic!GameLevel..ctor)
- Modes (Hexic!GameMode):
  - **Marathon** — clear the combo target to advance; wins require pearls.
  - **Timed** — starts with a 50 s clock, ticking only after the first valid
    rotation; a valid rotation adds +0.2–0.5 s per cleared piece, an invalid
    one subtracts the same, a flower resets the clock to 60 s and each level-up
    adds that record's start seconds. Timeout is a loss.
    (Hexic!TimedBoard, Hexic!TimedBoard.DidWeLose)
  - **Survival** — pieces lock after a countdown; clearing the whole board or
    reaching survival level 50 wins, and a board where everything has locked
    loses. (Hexic!SurvivalBoard.DidWeWin, Hexic!SurvivalBoard.DidWeLose)
- Hints: `SearchBoardForHints` scans the board for a valid rotation and shows
  a hint cursor. (Hexic!Board.SearchBoardForHints)
- Progression is per-mode: game/trip statistics (flowers, black pearls,
  bonus clusters, bombs diffused, best scores) persist across sessions. (Hexic!StatisticsTracker)

## 4. Controls

- Tap/drag moves the cursor across the hex grid; the cursor highlights the
  pieces that will rotate. A circular swipe around the cursor rotates the
  selected set: the code watches the gesture's rotation direction and shows a
  single or double arrow while dragging, then commits on release if the angle
  passes a threshold. (Hexic!CursorController.OnRotation,
  Hexic!CursorController.OnRotationInProgress)
- Pause and help-overlay buttons sit at the top/bottom edges; a horizontal
  scrub controls volume in options.

## 5. Content inventory (must be re-authored)

- 94 files: 91 XNB assets (13 fonts, 28 sounds, 50 textures) and 3 language
  XML files (`Language/en.xml`, `es.xml`, `fr.xml`).
- Fonts include the Zegoe UI family (SB8/10/12/14) plus game/menu/learn faces;
  sound cues cover clusters, bonuses, black pearls, bombs (warn/explode/remove),
  level slide and menu clicks; textures cover the board, cursor arrows, pieces,
  bombs, pearls, flowers and screens. No raw art or audio may be copied; only
  the synthesized board, shapes and original SFX may ship.

## 6. Implementation plan

- Pure-Kotlin engine (`object HexicEngine` + immutable board state) in
  `ui/apps/games/`; Compose Canvas through `DetailScaffold`; zero corner radius;
  tokens only; SFX via `SfxBank`; scores/stats via `graph.games`; saves via
  `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object HexicEngine {
      fun rotate(board: Board, cursor: Cursor, dir: Direction): Board
      fun resolve(board: Board, level: Int): ResolveResult
      fun hints(board: Board): List<Cursor>
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `HexicEngine` (Games.kt)

1. **Wrong board geometry.** Kotlin uses a 7x7 square with 8-neighbour
   adjacency; official is a 10-column hex grid (8/9 cells per column) with
   hex adjacency. Rebuild the board model first — everything else depends on it.
2. **Three colours vs five-to-seven.** Official starts at 5 colours and adds
   one at levels 4 and 6; `HexColor` should carry level-driven colour counts
   plus star, bomb, FlowerStar, Pearl1 and Pearl2 piece kinds.
3. **Rotation semantics.** Kotlin cycles one tile A→B→C. Official rotates a
   selected 1x2/2x1 pair or a flower ring CW/CCW; the cursor and selection
   state must exist, and colours must cycle, not just advance one tile.
4. **No special pieces or win conditions.** Stars/bonuses, bombs with ticking
   countdowns, flowers, black pearls and the pearl-cluster/pearl-flower wins
   are all missing. Add them with the official scoring formulas.
5. **No modes/levels/difficulty.** Marathon/Timed/Survival, the 7-level table
   (multiplier, colours, combo target, bomb tuning) and Normal/Hard/Expert =
   start level 1/3/5 are absent; Kotlin scores one point per cleared cell with
   no multiplier.
6. **No timers/combos/locks.** Timed clock with per-move deltas, Marathon combo
   bar, and Survival piece locking all need new state and UI.
7. **No hints/saves/stats.** Official scans for hints, autosaves the board and
   tracks trip stats; Kotlin persists only a best score.

## 7. Citation log

- `Hexic!MatchType` · `Hexic!HexColor` · `Hexic!CursorType` · `Hexic!GameMode` · `Hexic!Difficulty` · `Hexic!GameLevel..ctor`
- `Hexic!GameLevel.IncrementLevel` · `Hexic!Board.CheckForLevelUp` · `Hexic!Board.IsMatchOnBoard` · `Hexic!Board.OnClusterMatch` · `Hexic!Board.OnFlowerMatch` · `Hexic!Board.OnAllBonusClusterMatch`
- `Hexic!Board.OnBombAndBonusMatch` · `Hexic!Board.OnBombExplosion` · `Hexic!Board.SearchBoardForHints` · `Hexic!Score.Add` · `Hexic!TimedBoard.DidWeLose` · `Hexic!SurvivalBoard.DidWeWin`
- `Hexic!SurvivalBoard.DidWeLose` · `Hexic!CursorController.OnRotation` · `Hexic!GameOverScreen.GetTitleText` · `Hexic!MainScreen..ctor` · `Hexic!DifficultyScreen..ctor` · `Hexic!GameplayScreen` · `Hexic!HelpOverlayScreen` · `Hexic!HighScoresScreen` · `Hexic!LearnScreen` · `Hexic!OptionsScreen` · `Hexic!StatisticsTracker` · `Hexic!TimedBoard` · `Hexic!SurvivalBoard` · `Hexic!PauseScreen` · `Hexic!AboutScreen` · `Hexic!MainScreen..ctor` · `Hexic!CursorController.OnRotationInProgress`
