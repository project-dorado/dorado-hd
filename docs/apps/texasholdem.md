# Texas Hold Em

- **Official package:** `Holdem.exe` (texasholdem)
- **Corpus:** `Zune HD Apps (Decompiled)/texasholdem` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** texasholdem

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Holdem.exe` | 210 | 901 | 22672 |
| `Microsoft.Xna.Zune.dll` | 110 | 825 | 15652 |

- Native 272x480 landscape poker table. Localized strings are pre-packed as
  XNB (`Languages/en|es|fr.xnb`), so text is not directly minable; behavior
  comes from code.
- Six seats, chip denominations, a betting dial, per-player timers and full
  hand-history/round-info overlays are all first-class UI.

## 2. Screens & navigation

- `MainMenuScreen`: Single Player, Multiplayer, Learn To Play, Options. (Holdem!MainMenuScreen)
- `SinglePlayerScreen`: Standard, Heads Up or Tournament. Standard/HeadsUp go
  to `DifficultyScreen` (Resume when a save exists, else Easy/Medium/Hard);
  Tournament goes to `TournamentScreen` listing the seven events. (Holdem!SinglePlayerScreen, Holdem!DifficultyScreen, Holdem!TournamentScreen)
- In game: `GameScreen` with `LocalPlayerHud` controls (Fold, Check, Bet/Raise
  via the rotary `Dial`, All In, timer), `PlayerHud` name/stack/action plates,
  dealer-button movement, a hand-strength hint bar, and `HandHistoryScreen` /
  `RoundInfoScreen` overlays. Pause offers resume/options/leave. (Holdem!GameScreen, Holdem!LocalPlayerHud, Holdem!Dial)
- Learning screens: `LearnToPlayScreen` links `CardRankingScreen`,
  `PlayingTheOddsScreen` and `BlindsScheduleScreen`. (Holdem!LearnToPlayScreen)
- End states: `StandardEarlyGameOverScreen` (busted bankroll),
  `TournamentGameOverScreen`, `TournamentNextTableScreen`, `ErrorScreen` and
  confirmations such as `ConfirmAllInScreen`, `ContinueWatchingConfirmationScreen`. (Holdem!ScreenCreator)

## 3. Rules, scoring & AI

- Three game types (`GameTypes`): **Standard** (6-seat single player),
  **HeadsUp** (1v1) and **Tournament** (7 events over escalating tables).
  Tables use 6 seats and fixed blinds initially; blinds are posted by
  big/little blind positions that rotate, with a pre-big state for re-posts. (Holdem!GameSettings, Holdem!CPlayer.EBlindState, Holdem!CPlayer.AdvanceBlindState)
- Blinds and structure: little blind 5, big blind 10, 16 blind rounds;
  tournament buy-ins are 0/250/500/1000/1500/5000/10000 with 100 starting
  chips for the first event, and later events scale tables/players
  (6 → 48), blind-round start indices and payout percentages 50/30/20. (Holdem!GameSettings, Holdem!Constants)
- A player's bankroll gates tournament entries; `Stats.Bankroll` is debited at
  buy-in and tournament winnings are credited. (Holdem!TournamentScreen, Holdem!TexasHoldEmState)
- AI personalities (`PokerAIType`): BaseLine, Novice, Rock, Maniac, Shark,
  Experimental — encoded in level strings as b/n/t/c/s/e. Difficulty selects a
  bag of personalities that seats draw from:
  - Easy: 6 Novice + 2 Rock
  - Medium: 2 Maniac + 2 Novice + 2 Shark + 2 Rock
  - Hard: 3 Maniac + 4 Shark + 1 Rock (Holdem!PokerAIType, Holdem!TexasHoldEmState.ResetAI)
- Hand strength is bucketed into `PokerHandType`: Nothing, Prayer, Draw,
  Power, Monster, Nuts. Preflop this is a hole-card ranking; post-flop the
  `PokerHandEvaluator` produces a float score (category plus tiebreak) used to
  compare the board-only, board+opponent and board+own best hands. (Holdem!TexasHoldEmAI.GetPokerHandType, Holdem!PokerHandEvaluator.EvaluateHand)
- Personality modifiers: Maniac and Novice promote their hand bucket one level;
  Maniac/Shark also promote when short-stacked or heads-up. Bucket → strategy:
  Nuts = All In, Monster = Raise, Power = Bet; Draw and Prayer fold or call
  depending on personality and player count (Rock always folds weak hands,
  Novice calls). (Holdem!TexasHoldEmAI.GetAIMove)
- Bluffing exists only for Maniac/Shark: a 1–100 roll against a small
  threshold (5), doubled for Maniac, doubled with Prayer-or-better, doubled
  heads-up preflop, and halved on later streets/multi-way pots; it is disabled
  when facing large raises with a weak hand. (Holdem!TexasHoldEmAI.GetAIMove, Holdem!TexasHoldEmAI.GetBetType)
- Bet sizing (`GetBetAmount`) depends on the chosen `PokerBetStrategy`
  (Fold/Bluff/Call/Bet/Raise/AllIn), personality, pot size, big blind, minimum
  raise and current call amount; any computed bet is floored to the lowest
  chip denomination in play (`AdjustBetForLowestChipInPlay`). A `DontFold`
  guard upgrades a fold back to a call when the player is priced in.
  (Holdem!TexasHoldEmAI.GetBetAmount, Holdem!TexasHoldEmAI.DontFold,
  Holdem!TexasHoldEmAI.AdjustBetForLowestChipInPlay)
- Tournament progression uses fixed difficulty per event
  (Easy, Easy, Medium, Medium, Hard, Hard, Hard), table consolidation rules
  and a payout split; the AI adapts across a hand and keeps persistent
  big-win/big-loss and power-hand counters per seat. (Holdem!Constants.TournamentAIDifficulty, Holdem!AIPersistentData)

## 4. Controls

- Betting is driven by the `Dial`: scrub/rotate it to set the amount between
  the minimum raise and the player's stack, then confirm with Bet/Raise; All In
  is a dedicated button. Fold and Check are large side buttons; a countdown
  ring shows the per-player decision timer. (Holdem!LocalPlayerHud, Holdem!Dial)
- Setup screens use sliders for buy-in, number of players and timer length
  (`BuyInSliderContent`, `NumPlayersSliderContent`, `TimerSliderContent`);
  tournament and hand-history screens scroll by drag. (Holdem!BuyInSliderContent, Holdem!NumPlayersSliderContent)
- Learn screens are scrollable text/table pages (hand ranking, odds, blind
  schedule).

## 5. Content inventory (must be re-authored)

- 111 files: 83 PNGs, 26 XNB assets, one JPG, one `Text/Fonts.xml`, and four
  language files `Languages/Languages.xnb`, `en.xnb`, `es.xnb`, `fr.xnb`.
- Audio (22 XNB): AllIn, BackDown, BettingDial, Call, CardDeal, Check,
  DealerButtonMoves, Fold, InsufficientBankroll, LocalPlayerCheckMark,
  LocalPlayerLosesPot, LocalPlayerWinsPot, MPOptions, MenuDown,
  PlayerEliminated and more.
- Textures: table/felt, cards, chips, player HUD plates, dealer button, dial,
  timer art, tournament trophies, learn-page diagrams and screen backgrounds.
  No Microsoft art/audio/strings may be copied; the card deck and all UI art
  must be re-authored (synthesized playing-card glyphs are already the
  Dorado-HD convention).

## 6. Implementation plan

- Pure-Kotlin engine (`object PokerEngine` + immutable `PokerState`) in
  `ui/apps/games/`; Compose table through `DetailScaffold`; zero corner radius;
  tokens only; deal/check/raise SFX via `SfxBank`; bankroll/stats via
  `graph.games`; saves via `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object PokerEngine {
      fun legalActions(state: PokerState, seat: Int): List<Action>
      fun apply(state: PokerState, seat: Int, action: Action): PokerState
      fun aiAction(state: PokerState, seat: Int, personality: AiType): Action
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `PokerEngine` (MoreGames.kt)

1. **One AI vs six personalities + difficulty.** Kotlin's `aiAction` is a
   single tight-passive/1-in-8 bluff heuristic. Official ships BaseLine,
   Novice, Rock, Maniac, Shark and Experimental with the Easy/Medium/Hard
   personality distributions above. Add a personality enum, the bucket →
   strategy mapping and the bluff modifiers.
2. **Two players vs six seats.** Kotlin is heads-up only with fixed 1000
   stacks and human big blind. Official Standard is a 6-seat table with dealer
   button, blind rotation and head-to-head as a separate mode.
3. **No bet sizing model.** Kotlin raises by `currentBet + lastRaiseSize`
   (clamped). Official computes the amount from personality, pot, big blind,
   minimum raise and call amount, and floors it to the lowest chip
   denomination in play.
4. **Side pots/all-in.** Kotlin clamps call/raise to the stack but has no
   side-pot accounting, dead button or chip-up logic; official uses `CPot`
   per-player pots, `GoAllIn`, `CommitCurrentBet`, `PullBackBet` and
   `ChipUp`. Implement proper pot splitting before multi-way play.
5. **No tournaments/bankroll.** Kotlin plays one hand at a time with no
   buy-ins, blind schedule, table consolidation, payouts or bankroll; official
   has a 7-event ladder with difficulty progression.
6. **Hand evaluator parity.** Kotlin's 5-of-7 enumeration with wheel handling
   is functionally close; official uses a float-scored `PokerHandEvaluator`
   and a 7-bucket `PokerHandType` for AI decisions. Keep the evaluator but
   expose a bucket for AI use and verify kicker ties.
7. **No hand history/round info/timers.** Official records every action to a
   hand history, shows round info and enforces a decision timer; Kotlin logs a
   short string list. Add an action log and per-seat timer ring.

## 7. Citation log

- `Holdem!PokerHandEvaluator.EvaluateHand` · `Holdem!TexasHoldEmAI.GetAIMove` · `Holdem!TexasHoldEmAI.GetPokerHandType` · `Holdem!TexasHoldEmAI.GetBetType` · `Holdem!TexasHoldEmAI.GetBetAmount` · `Holdem!TexasHoldEmAI.DontFold`
- `Holdem!TexasHoldEmAI.AdjustBetForLowestChipInPlay` · `Holdem!TexasHoldEmState.ResetAI` · `Holdem!AIPersistentData` · `Holdem!PokerAIType` · `Holdem!PokerBetStrategy` · `Holdem!PokerHandType`
- `Holdem!GameTypes` · `Holdem!GameLevels` · `Holdem!CPlayer.EBlindState` · `Holdem!CPlayer.AdvanceBlindState` · `Holdem!GameSettings` · `Holdem!Constants`
- `Holdem!MainMenuScreen` · `Holdem!SinglePlayerScreen` · `Holdem!DifficultyScreen.Initialize` · `Holdem!TournamentScreen` · `Holdem!LocalPlayerHud` · `Holdem!Dial` · `Holdem!LearnToPlayScreen` · `Holdem!TexasHoldEmState` · `Holdem!ScreenCreator` · `Holdem!BuyInSliderContent` · `Holdem!NumPlayersSliderContent` · `Holdem!GameScreen` · `Holdem!Constants.TournamentAIDifficulty`
