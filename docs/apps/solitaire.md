# Solitaire

- **Official package:** `Solitaire.exe` (solitaire)
- **Corpus:** `Zune HD Apps (Decompiled)/solitaire` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** solitaire

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Solitaire.exe` | 41 | 179 | 5675 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 70 | 308 | 6420 |
| `ZuneGamesLib.dll` | 36 | 170 | 3669 |

- Native 272x480 landscape card table. A spritesheet `ImageMaps/Deck.xnb` and
  `Images/Deck.png` (488x407) hold the 52 faces plus backs; the bottom bar
  carries score, time, Undo, Pause and Hint.
- All movement is touch: pick up / drag / double-tap with an undoable action
  log (`GameAction` + `CardMoveTag`) rather than free-form physics.

## 2. Screens & navigation

- `MenuScreen` base plus `LoadingScreen` → `GameScreen`; the in-game bottom
  bar shows current score and elapsed time with Undo / Pause / Hint buttons. (Solitaire!BottomBar)
- `PauseScreen`: Resume, New Game, Options, Learn To Play, Statistics, About.
  New Game asks for confirmation via `BigMessageBoxScreen`. (Solitaire!PauseScreen)
- `OptionScreen` exposes the two play options: **deal type** (One Card /
  Three Card) and **scoring method** (Standard / Vegas); changing either
  mid-game warns that the current game will be lost. (Solitaire!GameScreen, Solitaire!Options)
- `StatisticsScreen` shows wins/losses, win percentage, fastest win, fewest
  moves and the high Standard and Vegas scores
  (Solitaire!Statistics); `AboutScreen` and `LearnToPlayScreen` complete the
  menu set.
- On win the game triggers fanfare/fireworks effects and writes stats; when no
  move remains after exhausting the deck the `GameOverMessageBoxScreen`
  appears. (Solitaire!GameScreen, Solitaire!Solver.IsGameOver)

## 3. Rules, scoring & AI

- Klondike: seven tableau columns (1..7 cards, top face-up), four foundations,
  a deck and a face-up hand. Empty columns accept a **King only**; otherwise a
  card may be placed on a card one rank higher and of the opposite colour. (Solitaire!CardColumn.CanAdd)
- Foundations accept an Ace on empty and then same-suit ascending cards; a
  stack is "full" when its King is on top. (Solitaire!CardStack.CanPush, Solitaire!CardStack.IsFull)
- Face-down column cards flip when exposed; removing/flipping awards score in
  Standard mode. `Solver.CanAutoComplete` is true only when every tableau card
  is face-up, the deck and hand are empty, and repeated automated hint moves
  would finish all four foundations — otherwise the game continues. (Solitaire!Solver.CanAutoComplete)
- Draw variants: `DealType.OneCard` turns one card into the hand; `DealType.ThreeCard`
  turns three (the default). Only the top hand card is playable. (Solitaire!GameScreen.Deal, Solitaire!Options.DealType)
- **Standard scoring**: +5 for a card from hand/discard to a column, +5 for
  flipping a tableau card, +10 for a card sent to a foundation, −15 when a
  card is pulled back off a foundation, and −20 for each deck recycle beyond
  the third pass. At win, a time bonus of `700000 / seconds` is added (only
  when the game lasted at least 30 s). Score never drops below 0.
  (Solitaire!Scoring.Standard, Solitaire!Scoring.Standard.ComputeBonusPoints,
  Solitaire!GameScreen.MoveCardAction)
- **Vegas scoring**: a new game costs 52 (score starts at −52), each card to a
  foundation scores +5, a card pulled back costs −5, and the deck may be
  recycled at most **three times** (the deal button disables when the deck is
  empty after the third pass). Score is clamped to ±9999. (Solitaire!Scoring.Vegas, Solitaire!GameScreen.Deal, Solitaire!GameScreen)
- Hints: `Solver.FindHint` searches column tops, the hand and the discard for a
  "good" move; the board glows the hint cards for ~3 s. If no hint exists the
  game checks whether the deck can still produce one; if not, the game-over
  dialog is shown. The same simulator powers auto-complete. (Solitaire!Solver.FindHint, Solitaire!Solver.IsGameOver)
- There is no opponent AI — the "AI" is the hint/auto-complete solver. A
  five-card poker `HandChecker` exists in the binary with self-tests but is not
  reachable from gameplay (dead code).
- Progression: statistics persist wins, losses, win/loss percentage, fastest
  win, fewest moves, and high Standard and Vegas scores; Standard and Vegas
  scores themselves are settings-backed and survive restarts. (Solitaire!Statistics, Solitaire!Scoring.Load)

## 4. Controls

- Drag a card (or face-up run) onto a legal column/foundation; a tap selects
  and a double-tap is a shortcut (the code checks for a repeated tap on the
  same card inside a short window before acting). Cards animate along
  interpolated paths. (Solitaire!GameScreen.CheckForDoubleTap)
- Tap the deck to deal one or three cards; tap the empty deck slot to recycle
  the discard when the rules allow. Bottom bar: Undo (action-log based),
  Pause, Hint. All moves are recorded as undoable actions with card-move tags. (Solitaire!GameScreen.Deal, Solitaire!BottomBar)

## 5. Content inventory (must be re-authored)

- 48 files: 29 PNGs, 14 XNB assets (including `ImageMaps/Deck.xnb` and
  `Fanfare.xnb`), and 5 XML files (`Text/Strings/en.xml`, `es.xml`, `fr.xml`,
  `Text/Fonts.xml`, `Text/Text.xml`).
- Images include the deck spritesheet, background, bottom-bar chrome, Back /
  Menu / LTP buttons, and the win `Fanfare` animation frame.
- Sounds: cardFlip, cardPass, cardPlay_1..3, cardsArranged, button/buttonSoft,
  win fanfare cues. All art/audio/text must be re-authored.

## 6. Implementation plan

- Pure-Kotlin engine (`object SolitaireEngine` + immutable game state) in
  `ui/apps/games/`; Compose table through `DetailScaffold`; zero corner radius;
  tokens only; card SFX via `SfxBank`; stats via `graph.games`; scores/options
  via `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object SolitaireEngine {
      fun newGame(deal: DealType, rng: Random): GameState
      fun move(state: GameState, from: Pile, to: Pile, count: Int): GameState
      fun hint(state: GameState): Hint?
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `SolitaireEngine` (Games.kt)

1. **No scoring.** Kotlin has no Standard/Vegas modes, no move points, no
   recycle penalty, no Vegas 3-pass cap and no time bonus. Add a scoring model
   with both variants and a score/time bottom bar.
2. **Draw-one only.** `tapStock` always draws a single card. Official defaults
   to three-card draw with a one-card option; the hand must hold up to three
   cards with only the top playable.
3. **Unlimited recycling.** Kotlin recycles the waste forever; Vegas must stop
   after three passes and Standard must charge 20 after the third.
4. **Single-card moves.** `placeTop` only ever moves the top card; official
   moves whole face-up descending runs to columns/foundations.
5. **No undo/hint.** Add the action-log undo and the Solver-style hint
   (column tops, hand, discard) plus game-over detection that simulates
   dealing through the deck.
6. **No auto-complete/win state.** Official plays out a won position and fires
   fanfare; Kotlin has no win detection at all.
7. **No stats or settings.** Wins/losses/fastest/fewest/best scores and the
   DealType/ScoringMethod/deck-location settings are missing; the Kotlin score
   model needs persistence under `graph.appState`.

## 7. Citation log

- `Solitaire!CardColumn.CanAdd` · `Solitaire!CardStack.CanPush`
- `Solitaire!CardStack.IsFull` · `Solitaire!Solver.CanAutoComplete`
- `Solitaire!Solver.FindHint` · `Solitaire!Solver.HintMoveExists`
- `Solitaire!Solver.IsGameOver` · `Solitaire!Scoring.Standard`
- `Solitaire!Scoring.Standard.ComputeBonusPoints` · `Solitaire!Scoring.Vegas`
- `Solitaire!Scoring.Load` · `Solitaire!GameScreen.Deal`
- `Solitaire!GameScreen.MoveCardAction` · `Solitaire!GameScreen.CheckForDoubleTap`
- `Solitaire!Options` · `Solitaire!Options.DealType`
- `Solitaire!DealType` · `Solitaire!ScoringMethod`
- `Solitaire!Statistics` · `Solitaire!PauseScreen`
- `Solitaire!BottomBar` · `Solitaire!GameScreen`
