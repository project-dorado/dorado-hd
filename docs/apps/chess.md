# Chess

- **Official package:** `Chess.exe` (chess)
- **Corpus:** `Zune HD Apps (Decompiled)/chess` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** chess

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Chess.exe` | 58 | 291 | 8111 |
| `Microsoft.Xna.Zune.dll` | 110 | 825 | 15652 |
| `ZuneGamesLib.dll` | 85 | 402 | 6409 |

- Native 272x480 portrait app. The engine is the same 0x88 search core as
  Checkers (16x16 array, `SQUARE`/`RANK`/`FILE`), with chess move generation
  and a static-exchange helper.
- An external 13.3 MB `chess.bok` opening book ships beside the binary
  (30-byte records), loaded by the `book` class.

## 2. Screens & navigation

- `CastleMenu` is the splash/title screen (torches, "Castle/Chess" art); it
  auto-pushes `MainMenu` when active. (Chess!CastleMenu.UpdateComponent)
- `MainMenu`: Single Player, Two Player, How To Play, Records, Options.
  Single Player resumes `autosave1` through `OverwriteMenu` or opens `SetUp`;
  Two Player resumes `autosave2` or starts immediately; Records pushes
  `TrophyScreen`; How To Play pushes `LearnToPlayScreen`. (Chess!MainMenu.Initialize)
- `SetUpMenu` (single player): difficulty toggle (easy/intermediate/hard),
  color toggle (play White or Black), Play, Back. `OptionsMenu`: sound,
  theme (wood_2d / metal_3d) and About; Back returns to the menu. (Chess!SetUpMenu.Initialize, Chess!OptionsMenu.Initialize, Chess!Theme)
- In game: turn shield, difficulty chip, AI "thinking" torches/text, captured
  pieces held in side "jail" trays, last-move marker, check/castle banners,
  hint mover and undo button; `InGameOptionsMenu` allows difficulty change
  (with `ChangeDiffConfirmPopUp`), theme, sound and main menu. Popups:
  `UndoConfirmPopUp`, `ResetConfirmPopUp`, `OverwriteGamePopUp`, `EndGamePopUp`. (Chess!InGameOptionsMenu.Initialize, Chess!GameScreen)
- `LearnToPlayScreen` presents Rules and Controls as two headed scroll pages;
  each heading is a scroll card (rules, controls, promotion, castling). (Chess!LearnToPlayScreen.Initialize)

## 3. Rules, scoring & AI

- Standard FIDE chess on an 8x8 board. Move specials are encoded per move:
  NORMAL, KINGSIDECASTLE, QUEENSIDECASTLE, ENPASSANT, PAWN_PROMOTION and
  ROOKCASTLEMOVE (the rook half of a castle). Castling is generated only when
  the king and rook have not moved, intermediate squares are empty, and the
  king does not pass through or land on an attacked square. (Chess!Evaluate, Chess!Evaluate.generate_move_list)
- Pseudo-legal moves are filtered by `IsKingSafeAfterMove`; pawns generate
  double-pushes from the start rank, en-passant captures, and promotion moves. (Chess!Evaluate.generate_move_list)
- **Promotion auto-selects a queen** — the promotion loop only emits the queen
  piece index, and the Learn To Play text states the pawn "defaults to a
  Queen". There is no under-promotion UI. (Chess!Evaluate.generate_move_list)
- Material values: pawn 1000, knight 3000, bishop 3299, rook 5000, queen 10000,
  king 100000. Positional evaluation constants include doubled-pawn penalty
  500, isolated-pawn penalty 200, king-side castling bonus 100, queen-side 10,
  trade-when-ahead bonus 200, trade-when-behind 100, center occupation/attack
  13, mobility 1, bishop-block penalty 40, knight-on-rim penalty 13, king
  tropism 6, rook-on-open-file 2. (Chess!Evaluate, Chess!Evaluate.load_fen)
- Search: iterative deepening negascout over a 100-ply cap, with null-move,
  killer-move, history and static-exchange pruning; mate score uses
  MATEVALUE = 50000. `See.StaticExchangeEvaluation` scores capture sequences. (Chess!Evaluate, Chess!See.StaticExchangeEvaluation)
- Difficulty presets (depth, seconds): **easy = 1 ply / 3 s, intermediate =
  3 plies / 6 s, hard = 5 plies / 40 s**. The opening book is consulted first
  at every level; easy is silently upgraded to intermediate while the engine
  believes the player is losing. Hint runs a 4-ply search (5 s budget).
  (Chess!engine.Ziggurat_move, Chess!engine.Ziggurat_depth_move,
  Chess!engine.GetHint, Chess!engine.UpdateEngine)
- Progression: no unlocks; wins/losses feed a `StatTracker`, and the Records
  screen (`TrophyScreen`) presents career/stat pages. Save slots are fixed
  autosaves (`autosave1` single-player, `autosave2` two-player) plus manual
  `SaveScreen`/`SaveSlot` entries. (Chess!MainMenu.Initialize, Chess!SaveSlot)

## 4. Controls

- Tap a friendly piece to select it (select-piece sound) and tap a legal
  destination square to move (select-destination sound); invalid destinations
  play the bad-check feedback. A drag path is also supported by the shared
  `PieceMover` ghost — the GameScreen keeps separate movers for board pieces,
  the hint arrow and captured "jail" pieces. (Chess!GameScreen)
- Castling is played by moving the king two squares toward the rook; the
  engine generates the rook half as a separate ROOKCASTLEMOVE step. (Chess!Evaluate)
- Undo takes back two half-moves behind confirmation; Reset and difficulty
  changes are confirmed; pawn promotion needs no input because it is auto-Q.

## 5. Content inventory (must be re-authored)

- 59 files: 28 PNGs, 21 XNB sounds, 4 XML language/font files, 4 JPGs, one
  `Textures/ltpsheet.txt` sprite-sheet description, and the 13.3 MB
  `chess.bok` opening book (do **not** ship or mine this file).
- Languages: `Language/en.xml`, `es.xml`, `fr.xml` (11–12 KB each).
- Sounds: ButtonDown, ScrollClick, SelectPiece, SelectDestination, castle,
  checkGood/checkBad, checkMateGood/checkMateBad, crowningGood/crowningBad,
  closeCOA, plus fanfares. Learn To Play uses a sprite sheet of annotated
  board diagrams (`ltp_board`, `ltp_king`, castle/promotion pages).
- The opening book must be replaced by a small synthesized/sound tree or
  omitted; it is an external data file the clean-room build cannot copy.

## 6. Implementation plan

- Pure-Kotlin engine (`object` + immutable `ChessState` data class) in
  `ui/apps/games/`; Compose screen with `DetailScaffold`; zero corner radius;
  `LocalDoradoColors`/`DoradoTokens` only; moves via `SfxBank`; records via
  `graph.games`; save slots via `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object ChessEngine {
      fun legalMoves(state: ChessState): List<ChessMove>
      fun apply(state: ChessState, move: ChessMove): ChessState
      fun bestMove(state: ChessState, level: Difficulty): ChessMove?
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `ChessEngine` (MoreGames.kt)

1. **AI strength/levels.** Existing UI calls `bestMove(state, depth = 2)` with
   no difficulty selection. Official needs easy/intermediate/hard =
   1/3/5-ply, the easy→intermediate promotion when losing, hint search, and
   mate-distance scoring. Keep the alpha-beta structure, add levels.
2. **Opening book.** No book exists in Kotlin; add a small synthesized opening
   table consulted before search, or document omission.
3. **No undo/hint.** Official offers confirmed 2-half-move undo and a hint
   highlight; neither exists. Add a move-history stack and hint API.
4. **Promotion UI parity.** Kotlin generates Q/R/B/N promotions; official
   auto-queens. Either auto-queen (canonical) or keep under-promotion as a
   documented divergence; do not present a chooser the device never had.
5. **No records/themes.** Official has `TrophyScreen` stats, two board themes,
   and autosave slots; Kotlin records a single result and has one theme.
6. **No local two-player mode.** Official MainMenu offers Two Player; Kotlin
   is human-White vs AI only. Add a both-human mode with shared controls.
7. **Evaluation depth.** Kotlin uses material-only values (100/320/330/500/900).
   Official scales are P1000/N3000/B3299/R5000/Q10000 plus structure terms;
   port the constants for rating-accurate play.

## 7. Citation log

- `Chess!engine.Ziggurat_move` · `Chess!engine.Ziggurat_depth_move`
- `Chess!engine.GetHint` · `Chess!engine.UpdateEngine`
- `Chess!Evaluate.piece_value` · `Chess!Evaluate.initialize_board`
- `Chess!Evaluate.generate_move_list` · `Chess!book.make_opening_book_move`
- `Chess!See.StaticExchangeEvaluation` · `Chess!MainMenu.Initialize`
- `Chess!SetUpMenu.Initialize` · `Chess!OptionsMenu.Initialize`
- `Chess!InGameOptionsMenu.Initialize` · `Chess!GameScreen`
- `Chess!GameTimer` · `Chess!Theme`
- `Chess!LearnToPlayScreen.Initialize` · `Chess!SaveSlot` · `Chess!CastleMenu.UpdateComponent` · `Chess!Evaluate.load_fen`
