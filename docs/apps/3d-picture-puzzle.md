# 3D Picture Puzzle

- **Official package:** `PicturePuzzle3D.exe` (3d_puzzle)
- **Corpus:** `Zune HD Apps (Decompiled)/3d_puzzle` (external, untracked)
- **Wave:** W5 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `PicturePuzzle3D.exe` | 37 | 195 | 4908 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneGamesLibGl.dll` | 100 | 452 | 9352 |

## 2. Screens & navigation

- Title/loading: `LoadingScreen`, then `MainMenuScreen` with picture, grid
  (3x3 / 4x4), layers (one/two-sided), category and sound options; a saved
  game offers Resume/New Game through `ConfirmationScreen`.
- Side screens: `BestTimesScreen` (four slots keyed by grid + sides),
  `OptionsScreen`, `AboutScreen`, `LearnToPlayScreen`, `PicturePickerScreen`
  (a 2x3 grid of 128x128 tiles drawn from the device picture albums).
- Gameplay screen stack: `BoardScreen` (3D board host) → `MixUpScreen`
  (automatic shuffle phase with "Press To Start") → `BoardControlScreen`
  (timer box, back/pause, Flip, Hint) → `FinishedScreen` (play time, new
  best time, press to continue). `PauseScreen` can interpose.
- Transitions are slide/break-apart screen animations
  (`SlidingTransition`, `BreakapartScreen`).

## 3. Rules, scoring & progression + simulation model

- The board is an NxN grid (3x3 or 4x4; the engine tolerates 1..6) of tiles
  with exactly one empty cell. Tiles are placed from a random permutation;
  the empty cell is chosen randomly. A "Move" swaps a tile with the empty
  cell; `IsComplete` is true only when every remaining tile sits at its
  original grid coordinate and all tiles come from the same picture.
- One-sided play uses a single image. Two-sided play adds a second board
  behind the first: a tile carries a front/back image flag, and a piece can
  be transferred between boards (the camera spins 180° in 0.5 s; front and
  back cameras sit at ±gridSize*100 units, perspective FOV is π/4 rad, near
  1, far 10000).
- Shuffle phase: `m_moves = 4 * (gridSize + 1)` (16 moves for 3x3, 20 for
  4x4). Each move slides a random piece along the row/column of the empty
  slot; in two-sided mode every third move instead flips a random piece to
  the other board, so both sides get mixed.
- Pictures come from 6 categories (Animals, Architecture, Cartoons, Plants,
  Scenery, Other) plus two generated "Numbers" grids: 3x3 uses red/yellow
  number grids, 4x4 uses green/orange. Category "Random" rolls among the
  six. The Hint overlay textures a red number strip for front tiles and a
  green strip for back tiles.
- Scoring is time-only: elapsed time accumulates while a game is active and
  is persisted for Resume. Best times are stored per configuration
  (`OneSided3x3`, `OneSided4x4`, `TwoSided3x3`, `TwoSided4x4`); a run that
  beats the record shows "New Best Time" with the victory fanfare. A
  completed puzzle saves its piece info so the finished board can be
  restored. The glow value on the active tile is a sine of elapsed ms/160,
  and the hint scale constant is 0.0617 (3x3) / 0.0622 (4x4).

## 4. Controls

- Touch a tile: a ray is unprojected from the touch point through the board
  camera and the first piece whose bounds intersect it is picked
  (`GetSelectedPiece`, `CalculateTouchRay`).
- Drag toward the empty cell: the piece follows only if the release lands on
  a legal slide; release is evaluated after a 0.3 s fling window
  (`HandleTouchesEnded` → `OnTouchEnded`). Fast flings use `ZuneFlingInputMessage`.
- Double-tap a piece in two-sided mode to swap it to the other board
  (`DoubleTapComponent`); the Flip button spins the view; the Hint button
  must be held (focus) to display the number overlay; the pause button exits
  to the menu/pause screen.

## 5. Content inventory (must be re-authored)

Index reports 148 files: 145 XNB + 3 XML. XNB inventory by directory:
`Images/` 33 UI textures; `Models/puzzlepiece.xnb` (one tile mesh);
`Shaders/` 2 (GridShader, GridShaderHint); `Sounds/` 10 (snap, whoosh 1–5,
button, invalid, popup, victory fanfare); `Pictures/` 99 pictures —
Animals 12, Architecture 35, Cartoons 8, Plants 12, Scenery 17, Other 11,
Numbers 4 (two 3x3 and two 4x4 number grids). Text: `Fonts.xml`,
`Text.xml`, `Text/Strings/en.xml`. All of it is re-authored: the tile mesh
is trivial, number grids become procedural textures, and the photo
categories are replaced by our own generated/abstract art (no MS imagery).

## 6. Implementation plan

- 3D: `ui/apps/engine3d/` — indexed tile mesh with two UV sets, a two-
  sampler material (photo + hint strip), perspective camera, ray picking by
  unprojection; no third-party 3D dependency.
- Logic: `ui/apps/games/PicturePuzzle.kt` — grid/permutation model,
  shuffle, move validation, completion check, best-time buckets; seeded RNG
  for deterministic tests and optional replay.
- UI: `DetailScaffold`; timer/logo/buttons as Compose; `SfxBank` for
  snap/whoosh/victory; hint strip and number grids drawn procedurally.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): shuffle move counts,
  completion detection, swap legality, best-time per config, camera flip
  mid-shift, pick ray ↔ tile mapping.
- Fidelity target: full-parity mechanics, original content. Risks: picking
  precision on the tilted 3D board, parity of the two-sided transfer and
  flip animation, and replacing 99 photos with enough visual variety.

## 7. Citation log

- `PicturePuzzle3D.exe!PuzzleBoard.InitializeBoard`, `.IsComplete`, `.Swap`
- `PicturePuzzle3D.exe!BoardScreen.Initialize`, `.GenerateBoards`,
  `.GenerateBoardHelper`, `.ResumeGame`
- `PicturePuzzle3D.exe!MixUpScreen.ResetStats`, `.MovePiece`,
  `.GetRandomPiece`, `.FinishedSwappingPieces`
- `PicturePuzzle3D.exe!BoardControlScreen.Update`, `.HandleTouchesBegan`,
  `.HandleTouchesMoved`, `.HandleTouchesEnded`, `.GetSelectedPiece`,
  `.CalculateTouchRay`, `.OnTouchEnded`, `.Flip`
- `PicturePuzzle3D.exe!Camera.UpdateViewProjectionMatrix`,
  `.ShiftViewToOtherSide`
- `PicturePuzzle3D.exe!BestTimesScreen.GetKey`, `.GetBestTime`,
  `.SetBestTime`; `PicturePuzzle3D.exe!FinishedScreen.OnPushedToScreenStack`
- `PicturePuzzle3D.exe!PicturePickerScreen` (ctor),
  `PicturePuzzle3D.exe!PictureImage.Initialize`,
  `.GetRandomNumbersImage`; `ImageSource` enum
- `PicturePuzzle3D.exe!Extensions.FormatTimeSpan` (time display)
