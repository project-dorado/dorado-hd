# Reversi

- **Official package:** `Reversi.exe` (color_spill)
- **Corpus:** `Zune HD Apps (Decompiled)/color_spill` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** reversi

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Reversi.exe` | 17 | 76 | 1963 |
| `2moroGames.dll` | 89 | 402 | 5976 |
| `OthelloRules.dll` | 14 | 47 | 807 |

- Native 480x272 landscape (rotated to the 272x480 panel) app built on the
  shared `2moroGames` menu/animation framework with an explicit
  `TouchHandler(ScreenAlignment.AlignmentPortrait)`; `OthelloRules.dll` holds
  all board and AI logic.
- Reversi.exe itself is a thin shell: settings persistence, menus, stats and
  the rules-library bridge. It has no localization XML (English strings only).

## 2. Screens & navigation

- Page IDs: Loading → Splash → Main → (Pause, Options, Message, Question,
  Statistics, Skins). Page changes use the framework's slide/drop transitions. (Reversi!Constants.PageIDs, Reversi!MenuInitialiser)
- Main menu starts/resumes a game with New Game; the board menu offers Pause,
  Undo, Options, Skins and Statistics; two-player mode is reached by setting
  the AI side to None. (Reversi!GameSettings.AIPlayer)
- Options page: AI difficulty (Easy/Medium/Hard), AI side (Black/White/None),
  "Show Moves" hint highlighting, and skin selection (Stone/Bright/Woodgrain). (Reversi!GameSettings)
- Statistics page shows games played and wins/losses against each difficulty
  (six counters) with a Reset Statistics button. (Reversi!GameSettings.Statistics,
  Reversi!StatisticsPage)
- On game over, `ShowMessage` announces the winner or a tie with the final
  piece tally, then resets the board for the next game. (Reversi!Reversi.EventGameOverMessageDismissed)

## 3. Rules, scoring & AI

- Standard Othello: 8x8 board, four centre stones (White d4/e5, Black d5/e4),
  **Black moves first**. (OthelloRules!Board.SetupGame,
  OthelloRules!Game.ResetGame)
- A move is legal only on an empty square that brackets one or more opposing
  stones between the new stone and an own stone along some direction; applying
  the move flips every bracketed run in all eight directions. (OthelloRules!Board.IsMoveValid, OthelloRules!Board.CalculateBoardAfterMove)
- Passing is automatic: after a move, if the opponent has no valid move the
  state is cloned with the turn returned to the mover; if neither side has a
  move the game ends. The winner is the side with more pieces, otherwise a
  tie. (OthelloRules!Game.PerformMoveInternal,
  OthelloRules!Game.DetermineWinningPlayer)
- Undo pops the saved game-state stack (100 levels of history) and skips back
  over any auto-pass so the undo returns to the human's turn. (OthelloRules!Game.UndoMove)
- AI personalities shipped by the rules library:
  - `RandomAIPlayer` — picks any legal move; used for **Easy**.
  - `NaiveAIPlayer` — 1-ply greedy: applies each legal move and keeps the one
    maximizing its own piece count; used for **Medium**.
  - `TempHardAIPlayer` — the naive piece-count choice plus a +20 bonus for
    corner moves and +5 for "strong edge" moves; used for **Hard**.
    (OthelloRules!NaiveAIPlayer.DetermineMove,
    OthelloRules!TempHardAIPlayer.DetermineMove)
  - `ScoredAIPlayer` is present in the library (positional weights: corner 50,
    corner-adjacent edge/inner −40, C-squares 10/5, edge 1; a 2-ply min/max
    over opponent replies) but this build never instantiates it.
    (OthelloRules!ScoredAIPlayer.DetermineMove,
    OthelloRules!ScoredAIPlayer.DetermineScoresForMove, Reversi!Reversi.UpdateGamePlayers)
- Difficulty/AI mapping is applied per game: `UpdateGamePlayers` constructs the
  player for the chosen difficulty and assigns it to the chosen colour, or
  gives both colours to the board menu for local two-player. (Reversi!Reversi.UpdateGamePlayers)
- Progression is statistics only: wins/losses per difficulty and total games
  played persist in `GameSettings.xml`; no unlocks or ratings. (Reversi!GameSettings)

## 4. Controls

- Tap an empty legal square to place a stone (MoveValid / MoveInvalid sounds;
  the drop sound accompanies the flip animation). "Show Moves" draws the legal
  move markers when enabled. (Reversi!GameSettings, Reversi!Constants.SoundIDs)
- The framework's `TouchHandler` converts screen taps/swipes; the board menu
  buttons drive Undo, Pause and page navigation.

## 5. Content inventory (must be re-authored)

- 87 XNB files only: 9 fonts, 76 images, 2 sounds (`click`, `drop`). No
  language XML.
- Images include the splash/black backgrounds, three board skins
  (Stone, Bright, Woodgrain) with their piece/button art, glow and move-marker
  overlays, and menu button states.
- Fonts include the Roman family (regular/small/large) and per-skin faces;
  all art and strings must be re-authored.

## 6. Implementation plan

- Pure-Kotlin engine (`object ReversiEngine` + immutable `ReversiState`) in
  `ui/apps/games/`; Compose board through `DetailScaffold`; zero corner radius;
  tokens only; flip/drop SFX via `SfxBank`; stats via `graph.games`; settings
  (side, difficulty, show-moves, skin) via `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object ReversiEngine {
      fun legalMoves(board: Board, player: Piece): Set<Square>
      fun apply(board: Board, move: Square, player: Piece): Board
      fun bestMove(board: Board, player: Piece, level: Level): Square?
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `ReversiEngine` (Games.kt)

1. **AI strength.** Kotlin's AI maximizes flips (`maxBy score().second`) — the
   closest analogue is a mixture of Naive and a flip-count heuristic, not the
   shipped mapping. Implement Easy = random legal move, Medium = 1-ply piece
   count, Hard = piece count + corner/strong-edge bonuses; optionally port the
   unused positional `ScoredAIPlayer` depth-2 as an extra level.
2. **No difficulty or side selection.** Official lets the player choose
   Easy/Medium/Hard, play Black/White, or turn the AI off for two-player.
   Add a setup screen; the Kotlin game hardcodes human Black vs AI White.
3. **No undo.** Official keeps a 100-deep state stack and skips auto-passes on
   undo; Kotlin has no move history.
4. **No statistics.** Per-difficulty win/loss counters and a stats page are
   missing; Kotlin writes one `graph.games` result when the board is full.
5. **No hint toggle.** Official "Show Moves" can be turned off; Kotlin always
   draws legal-move dots for the human.
6. **No skins.** Three official board skins; Kotlin uses theme tokens (fine for
   canon, but there is no skin setting).
7. **Correct already:** 8x8 setup, bracketing flips, pass-then-pass game end,
   and score-by-tally match `OthelloRules` semantics. Keep the pass logic when
   adding difficulty/undo.

## 7. Citation log

- `OthelloRules!Board.SetupGame` · `OthelloRules!Board.IsMoveValid`
- `OthelloRules!Board.DetermineValidMoves` · `OthelloRules!Board.CalculateBoardAfterMove`
- `OthelloRules!Game.ResetGame` · `OthelloRules!Game.PerformMoveInternal`
- `OthelloRules!Game.DetermineWinningPlayer` · `OthelloRules!Game.UndoMove`
- `OthelloRules!NaiveAIPlayer.DetermineMove` · `OthelloRules!TempHardAIPlayer.DetermineMove`
- `OthelloRules!RandomAIPlayer.DetermineMove` · `OthelloRules!ScoredAIPlayer.DetermineMove`
- `OthelloRules!ScoredAIPlayer.DetermineScoresForMove` · `Reversi!Reversi.UpdateGamePlayers`
- `Reversi!GameSettings` · `Reversi!GameSettings.Statistics`
- `Reversi!Constants.PageIDs` · `Reversi!Constants.SoundIDs` · `Reversi!MenuInitialiser` · `Reversi!StatisticsPage` · `Reversi!Reversi.EventGameOverMessageDismissed` · `Reversi!GameSettings.AIPlayer`
