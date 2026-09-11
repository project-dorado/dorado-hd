# Checkers

- **Official package:** `Checkers.exe` (checkers)
- **Corpus:** `Zune HD Apps (Decompiled)/checkers` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** checkers

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Checkers.exe` | 58 | 273 | 7603 |
| `Microsoft.Xna.Zune.dll` | 110 | 825 | 15694 |
| `ZuneCoreLib.dll` | 64 | 253 | 5197 |
| `ZuneGamesLib.dll` | 25 | 129 | 2513 |

- Native 272x480 portrait Zune HD app; board is a 34 px grid starting at y=92,
  with a board-flip flag for playing Black from the top.
- The engine is an 0x88 draughts search core (16x16 array with rank<<4|file
  indexing, `SQUARE`/`RANK`/`FILE`), not the visual 8x8 coordinates.

## 2. Screens & navigation

- Boot: `LoadingScreen` → `MainMenuScreen` with five entries — Single Player,
  Multiplayer, Learn To Play, Records, Options. (Checkers!MainMenuScreen..ctor)
- Single Player opens `SinglePlayerSetUpScreen` if no autosave exists, else a
  `LoadSaveScreen` asking to resume. Setup has Mode (Regular/Suicide),
  Difficulty (easy/intermediate/hard), Play As (side) and Color/theme toggles,
  then a Play button or Back. (Checkers!SinglePlayerSetUpScreen..ctor)
- Multiplayer chain: `MultiplayerModeScreen` → `HostScreen` / `SearchScreen` /
  `LobbyScreen` / `WifiGameScreen` over `NetworkManager` session state.
- In game: HUD shows player/opponent names, the current game-mode label, a
  difficulty strip drawn at (106,455), a status/thinking animation, and two
  buttons — `but_undo` (bottom right) and `but_pause` (bottom left). The last
  move and any multi-jump squares are highlighted. (Checkers!GameScreen..ctor)
- Pause: `SinglePlayerPauseScreen` (or `MultiplayerPauseScreen`) with resume /
  options / learn / main-menu entries; leaving mid-game saves a `SaveSlot`.
- End of game pushes `EndOfGameScreen` (win/lose/draw banners); a `DrawOfferScreen`
  exists for offered draws. Records are shown on `RecordsScreen`; rules and
  controls live in two scrollable tabs of `LearnToPlayScreen`.

## 3. Rules, scoring & progression

- 8x8 board, 12 men per side, play only on dark squares. Men move one square
  diagonally forward; kings move one square in any of the four diagonals. (`Evaluate.normaldir`, `Evaluate.crowndir`)
- Capture by jumping an adjacent enemy piece to the empty square beyond;
  men jump forward only, kings any diagonal direction. Captures are mandatory:
  move generation emits only jump moves when any exist
  (`ForceJumpEnabled = true`; generation sets `OnlyJumpsAvailable`). (Checkers!Evaluate.generate_move_list_for_player)
- Multi-jumps are recursive chains; a chain move is encoded with `special = 2`
  (MOREJUMPS) plus a `jumped_squares` list, and a man that finishes on rank 0/7
  is crowned (special = 1, promoted_piece = KING). (Checkers!Evaluate.create_multijump_move, Checkers!Evaluate.move_type)
- Evaluation is material + positional: man = 1000, king = 1399; center-square
  bonus table (0/1/2/4), per-rank advancement bonus up to 1500 for a man one
  rank from crowning, mobility, attack/defense bonuses; constants include
  MULTI_JUMP_BONUS = 1000 and CROWNING_BONUS = 200. (Checkers!Evaluate.piece_value)
- Search is negascout/minimax, iterative deepening, with killer moves, history
  and static-exchange pruning (MaxPly = 100, MaxGenMoves = 250). The game
  asks for a fixed depth per difficulty, with a companion time budget:
  easy = 1 ply, intermediate = 3 plies, hard = 8 plies. Hint is a 4-ply search.
  (Checkers!engine.Ziggurat_move, Checkers!engine.Ziggurat_depth_move,
  Checkers!engine.GetHint)
- No opening book: `engine.make_opening_book_move` always returns false.
- Suicide mode inverts the evaluator's sign so the search plays to lose
  (misère); regular mode is normal. Draws/stalemates end the game. (Checkers!engine.Ziggurat_move, Checkers!Evaluate)
- Progression is stat tracking, not unlockables: `StatTracker` keeps
  wins/losses/draws per difficulty (easy/intermediate/hard) and per WiFi,
  separately for Regular and Suicide modes; `RecordsScreen` displays them. (Checkers!StatTracker.IncreaseWin)
- `SaveSlot` persists FEN string, difficulty, colors, flipped board, suicide
  mode and both players' elapsed times; `GetFenString`/`LoadFenString` back the
  in-game save/restore. Undo takes back two half-moves; a warning popup appears
  once four undos have been used. (Checkers!GameScreen..ctor)

## 4. Controls

- Tap own piece → tap a highlighted destination to move. Tap selection is
  validated against engine move starts, not raw board geometry. (Checkers!BaseGameScreen.HandleTouchesBegan)
- Press-and-drag from an own piece switches to a "ghost" piece that follows the
  finger; release over a legal destination commits the move. (Checkers!BaseGameScreen.HandleTouchesMoved, ...HandleTouchesEnded)
- Buttons: undo and pause; board coordinates flip when playing Black so the
  human's pieces are always at the bottom.

## 5. Content inventory (must be re-authored)

- 92 files total: 71 PNG images, 16 XNB sounds, 5 XML text files
  (`Text/Strings/en.xml`, `es.xml`, `fr.xml`, `Text/Fonts.xml`, `Text/Text.xml`).
- Board art (wood theme, `bg_game`, `bg_main`, `bg_loading`), piece sprites
  (`piece_*` base/man/king), selection/glow/explosion/beam effects, win/lose
  banners, and two full rule/control illustration sets for Learn To Play
  (`ltp_board`, `ltp_move`, `ltp_jump`, `ltp_forcejump`, `ltp_multijump`,
  `ltp_king`, `ltp_slide1/2`, `ltp_drag`, `ltp_pause`, `ltp_undo`).
- Sound inventory includes ButtonDown, PieceMovement, PieceJump,
  PieceDestruction, KingMeGood/KingMeBad, DefeatFanfare, DrawFanfare and
  ScreenTransVert. All art/sound/text must be re-authored.

## 6. Implementation plan

- Pure-Kotlin engine as an `object` + immutable state/`data class` in
  `ui/apps/games/`; Compose screen through `DetailScaffold`; zero corner radius;
  colors/sizes from `LocalDoradoColors`/`DoradoTokens`; moves through `SfxBank`;
  stats/records through `graph.games`; persistence through `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object CheckersEngine {
      fun legalMoves(state: CheckersState, color: CheckersColor): List<Move>
      fun apply(state: CheckersState, move: Move): CheckersState
      fun bestMove(state: CheckersState, level: Int): Move?
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `CheckersEngine` (MoreGames.kt)

1. **No difficulty levels.** Existing AI is a single greedy `aiMove` that picks
   the longest jump. Official needs easy/intermediate/hard = 1/3/8-ply
   negascout, plus a 4-ply hint, with positional evaluation (center, rank,
   mobility) rather than jump distance.
2. **No Suicide mode.** Setup Mode toggle and sign-inverted evaluation are
   missing; add it to the engine state and score/records.
3. **No undo.** Official takes back two half-moves (with a warning after four
   undos); the Kotlin engine has no move history.
4. **No save/resume.** Official persists a FEN + difficulty + clock via
   `SaveSlot`; grid state is only in Compose memory today.
5. **No records.** `StatTracker` wins/losses/draws per difficulty/mode and a
   Records screen are absent; only a single `graph.games.record` call exists.
6. **AI is synchronous in UI.** The official engine searches on a worker with
   an `IsAIThinking` flag; the Kotlin call must move to `Dispatchers.Default`
   and show a thinking state.
7. **Rules are otherwise faithful** — forced captures, recursive multi-jump,
   crowning and "no move = loss" are already correct; add explicit tests for
   forced-jump and king capture-direction parity.

## 7. Citation log

- `Checkers!engine.game_difficulty` · `Checkers!engine.Ziggurat_move`
- `Checkers!engine.Ziggurat_depth_move` · `Checkers!engine.GetHint`
- `Checkers!Evaluate.generate_move_list` · `Checkers!Evaluate.generate_move_list_for_player`
- `Checkers!Evaluate.create_multijump_move` · `Checkers!Evaluate.move_type`
- `Checkers!Evaluate.piece_value` · `Checkers!BaseGameScreen.HandleTouchesBegan`
- `Checkers!BaseGameScreen.HandleTouchesMoved` · `Checkers!BaseGameScreen.HandleTouchesEnded`
- `Checkers!MainMenuScreen..ctor` · `Checkers!SinglePlayerSetUpScreen..ctor`
- `Checkers!GameScreen..ctor` · `Checkers!LearnToPlayScreen..ctor`
- `Checkers!StatTracker.IncreaseWin` · `Checkers!SaveSlot` · `Checkers!Evaluate.normaldir` · `Checkers!Evaluate.crowndir`
