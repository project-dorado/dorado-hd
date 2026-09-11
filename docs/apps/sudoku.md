# Sudoku

- **Official package:** `Sudoku.exe` (sudoku)
- **Corpus:** `Zune HD Apps (Decompiled)/sudoku` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** sudoku

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Sudoku.exe` | 123 | 485 | 8120 |

- Native 272x480 portrait app with an on-screen number pad. Content is all
  XNB (100 files) plus three language XMLs.
- Puzzles live in `Content/Puzzles/Database.xml` (Classic and Mini sets, each
  with Easy/Normal/Hard arrays); the local mining copy is empty, so only the
  schema is observed. (Sudoku!PuzzleDatabase)

## 2. Screens & navigation

- `MainScreen`: Classic, Mini, Custom, Learn and Options. (Sudoku!MainScreen)
- `DifficultyScreen(type)`: Resume (when a save exists for that category) plus
  Easy, Normal and Hard. Each category keeps its own save slot (`GameFactory.SaveSlot.Classic` / `Mini`). (Sudoku!DifficultyScreen)
- `PuzzleTypeScreen` (Custom flow): choose Classic or Mini, then enter your own
  puzzle in a start/edit mode that offers Start, Hint and Undo buttons.
  Start checks solvability and uniqueness: an unsolvable grid opens
  `UnsolvableDialog`, otherwise `CustomStartConfirmDialog` confirms and locks
  the givens. (Sudoku!PuzzleTypeScreen, Sudoku!GameplayScreen.StartClicked)
- `GameplayScreen` HUD: a timer, the 9x9 or 6x6 board, a number selector
  (Standard9 or Mini6 layout) with Note and Erase buttons, and Hint/Undo
  buttons (replaced by Start in custom-entry mode). Pause opens a menu; a
  Solve button (with `SolveConfirmDialog`) completes the grid. (Sudoku!GameplayScreen, Sudoku!GridSelectorComponent)
- `OptionsScreen`: volume control, Hints on/off, High Scores, About.
  `HighScoresScreen` shows best/average times and solved counts per
  type/difficulty. (Sudoku!OptionsScreen, Sudoku!HighScoresScreen)
- Progress persists as `Sudoku_tracking` (per-puzzle times) and a per-category
  save; tracking stores best times, average times and solved counts for the
  six type/difficulty buckets. (Sudoku!GameTracking)

## 3. Rules, scoring & progression

- Two board geometries are modeled by generic houses: **Classic 9x9** (27
  houses: 9 rows, 9 columns, 9 3x3 boxes) and **Mini 6x6** (18 houses: 6 rows,
  6 columns, 6 boxes). A board is solved when every cell has a value and no
  house contains a duplicate. (Sudoku!BoardFactory.CreateStandardBoard,
  Sudoku!BoardFactory.CreateMiniBoard, Sudoku!Board.IsSolved)
- Each cell maintains a `CandidateSet`; values and pencil marks are edited
  through undoable actions (`SetValueAction`, `SetNoteAction`,
  `SetNotesAction`). The conflict map updates as values are placed/undone so
  the renderer can highlight duplicates. (Sudoku!SetValueAction.Apply, Sudoku!SetNotesAction.Apply)
- **Hint button semantics**: on an empty, non-given cell the hint copies the
  solver's current candidate set into that cell's pencil marks (it does not
  reveal the answer). It is rejected if the cell is given/already filled or if
  the last action was already a hint for the same cell. (Sudoku!GameplayScreen.HintClicked, Sudoku!GameplayScreen.CanInsertHint)
- **Solve button** runs the dancing-links solver over the current grid and
  fills in the full solution after confirmation; it is recorded as an
  aggregate action so it can be undone as one step. (Sudoku!DancingLinksSudokuSolver.Solve, Sudoku!GameplayScreen)
- Custom puzzles must have exactly one solution: `HasUniqueSolution` runs the
  DLX solver asking for two answers and accepts only one; otherwise the entry
  is rejected as unsolvable. (Sudoku!DancingLinksSudokuSolver.HasUniqueSolution,
  Sudoku!GameplayScreen.StartClicked)
- Difficulty is puzzle selection, not solver aids: the database arrays are
  indexed by `PuzzleDifficulty` and the game walks forward to the next unsolved
  puzzle in the category, skipping puzzles already recorded as completed. (Sudoku!GameTracking.UsePuzzle, Sudoku!PuzzleDatabase.GetPuzzle)
- Scoring/progression is time-based: finishing a puzzle records its elapsed
  time per puzzle index; best times, average times and solved counts are
  derived per type/difficulty and shown on the High Scores screen. There is no
  points system. (Sudoku!GameTracking.TrackTime, Sudoku!GameTracking)
- Options: Hints (default on) toggles duplicate highlighting and candidate
  display; volume cycles Off/Low/Medium/High. (Sudoku!Options, Sudoku!OptionsData)

## 4. Controls

- Tap a cell to select it (the board highlights the selection and any
  duplicates); tap a number on the keypad to place it, or toggle Note mode and
  tap numbers to add/remove pencil marks; the Erase button clears the selected
  cell's value/notes. (Sudoku!GridSelectorComponent)
- Hint and Undo are buttons flanking the keypad; Solve and Pause are reached
  from the menu; custom entry adds a Start button and uses the same hint/undo
  affordances. (Sudoku!GameplayScreen)

## 5. Content inventory (must be re-authored)

- 103 files: 100 XNB assets (10 fonts, 14 sounds, 76 textures) and 3 language
  files (`Language/en.xml`, `es.xml`, `fr.xml`).
- Fonts include Menu/Title/Timer/Text sizes plus ZegoeUISB8; sounds include
  CellMove, ChangeVolume, Hint, InsertClear, InsertValue, Invalid, MenuDown,
  MenuNavigate, NumberDown, Start, ToggleNoteButton, Undo.
- Textures cover the board grid, numbers (value and note styles), selection
  glow, keypad layouts 6 and 9, buttons, banners, conflict markers, tutorial
  pages and backgrounds.
- The puzzle database itself (`Database.xml`) is absent from the corpus; the
  reimplementation must author its own curated Classic/Mini puzzle sets.

## 6. Implementation plan

- Pure-Kotlin engine (`object SudokuEngine` + immutable board/game state) in
  `ui/apps/games/`; Compose board through `DetailScaffold`; zero corner radius;
  tokens only; cell/insert SFX via `SfxBank`; times/records via `graph.games`;
  saves and preferences via `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object SudokuEngine {
      fun puzzle(type: PuzzleType, level: Level, index: Int): Puzzle
      fun solve(board: Board): Board?
      fun hasUniqueSolution(board: Board): Boolean
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `SudokuEngine` (Games.kt)

1. **Generator vs curated database.** Kotlin builds a random solution and
   blind-carves 45 cells with no uniqueness check and no difficulty meaning.
   Official ships authored Classic/Mini puzzles in Easy/Normal/Hard buckets
   and verifies uniqueness for custom grids; add a small authored puzzle set
   plus a uniqueness-checking solver.
2. **No Mini 6x6.** Kotlin is 9x9 only; the official board/houses model is
   size-generic with a Mini keypad layout. Generalize the engine and UI.
3. **No pencil marks.** Kotlin writes values only (and uses `0` as erase).
   Add candidate sets, note mode, erase, and the hint-fills-candidates
   behavior.
4. **No conflict highlighting.** Official tracks conflicts on every set/undo
   and the Options "Hints" flag gates duplicate highlighting; Kotlin never
   marks duplicates.
5. **No undo/solve/custom entry.** Add an action stack (`UndoStack` parity),
   the DLX-style solve with confirmation, and custom puzzle entry with
   solvability feedback.
6. **Wrong scoring.** Kotlin awards `10000 − seconds` and writes it once;
   official records per-puzzle times and derives best/average/solved counts per
   type and difficulty for the High Scores screen.
7. **No pause/options/learn/about/high-score screens** and no per-category
   save/resume; add these around the existing board.

## 7. Citation log

- `Sudoku!BoardFactory.CreateStandardBoard` · `Sudoku!BoardFactory.CreateMiniBoard`
- `Sudoku!Board.IsSolved` · `Sudoku!PuzzleDatabase.GetPuzzle`
- `Sudoku!PuzzleDatabase.GetPuzzleCount` · `Sudoku!GameTracking.UsePuzzle`
- `Sudoku!GameTracking.TrackTime` · `Sudoku!DancingLinksSudokuSolver.Solve`
- `Sudoku!DancingLinksSudokuSolver.HasUniqueSolution` · `Sudoku!SetValueAction.Apply`
- `Sudoku!SetNotesAction.Apply` · `Sudoku!GameplayScreen.HintClicked`
- `Sudoku!GameplayScreen.CanInsertHint` · `Sudoku!GameplayScreen.StartClicked`
- `Sudoku!GridSelectorComponent` · `Sudoku!Options`
- `Sudoku!OptionsData` · `Sudoku!MainScreen`
- `Sudoku!DifficultyScreen` · `Sudoku!PuzzleTypeScreen`
- `Sudoku!HighScoresScreen` · `Sudoku!PuzzleDatabase` · `Sudoku!GameplayScreen` · `Sudoku!OptionsScreen` · `Sudoku!GameTracking`
