# Spades

- **Official package:** `Spades.exe` (spades)
- **Corpus:** `Zune HD Apps (Decompiled)/spades` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** spades

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Spades.exe` | 55 | 286 | 7550 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZuneCoreLib.dll` | 66 | 275 | 5857 |
| `ZuneGamesLib.dll` | 26 | 134 | 2730 |

- Native 272x480 landscape card table. Bidding uses a physical-feeling
  `Wheel` control (225x686 sprite) whose scrubbed position selects the bid;
  the deck ships as a 598x256 spritesheet.
- Shares the hearts/trick card framework (`Trick`, `Hand`, `Deck`, `Player`
  hierarchy, `SaveData`, WiFi screens) with spades-specific scoring and AI.

## 2. Screens & navigation

- `MainMenuScreen`: Single Player, Multiplayer, Options, Learn To Play,
  Records; multiplayer chains through `WifiModeSelectScreen`, `HostScreen`,
  `JoinScreen`, `LobbyScreen` and `WifiGameScreen`. (Spades!MainMenuScreen..ctor)
- `SinglePlayerSetUpScreen`: difficulty toggle (Easy/Hard), score-mode toggle
  (game to 500 or 300), Blind Nil on/off, Play and Back.
  (Spades!SinglePlayerSetUpScreen..ctor, Spades!ToggleButton.CreateScoreButton,
  Spades!ToggleButton.CreateBlindNilButton)
- In game: each player HUD shows name, bid and tricks made; the deal/bid
  overlay presents the bid wheel and a Nil/Blind Nil option; the scoreboard
  overlay shows per-round bids, tricks, team totals and bag icons. (Spades!SinglePlayerGameScreen..ctor, Spades!ScoreboardScreen)
- Pause menu offers Resume, Volume, Learn To Play and Main Menu; `EndGameScreen`
  announces the winning team; `RecordScreen` lists career stats; a `TeamSelect`
  element handles two-player-vs-AI team choice. Autosave (`SpadesGame.Autosave`)
  persists bids, tricks, scores, dealer and spades-broken state. (Spades!SpadesGame, Spades!TeamSelect)

## 3. Rules, scoring & AI
- Four players in fixed partnerships (South/North vs West/East), 52 cards,
  13 each. The dealer seat is the round's starting seat for bidding and the
  first lead, and the dealer rotates each round.
  (Spades!GameManager.SetUpRound, Spades!GameManager.FindPlayerToStart)
- Bidding is 0–13 via the wheel. **Bid 0 is a Nil bid** (+100 if the player
  takes no trick, −100 otherwise). **Blind Nil** is offered before viewing the
  cards (only when the option is on) and is stored as the sentinel −1,
  worth +200/−200. (Spades!SinglePlayerGameScreen..ctor,
  Spades!SinglePlayerGameScreen.MakeBid, Spades!SinglePlayerGameScreen.BlindNil,
  Spades!GameManager.GetScoreForRound)
- Playing rules: follow the led suit if possible; a player who cannot follow
  may play anything (including spades). Spades may not be led until they have
  been "broken" by playing a spade off-suit, unless the hand is all spades. (Spades!Hand.ValidCardsToLead)
- Trick resolution: if any spade was played, the highest spade wins; otherwise
  the highest card of the led suit wins. (Spades!Trick.Resolve)
- Team round scoring (`GetScoreForRound`):
  - Team bid = sum of positive bids. If team tricks ≥ bid: **bid × 10 plus one
    point per overtrick**; if short: **−10 × bid**.
  - Overtricks ("bags") are added into the score immediately. After scoring,
    if the team's cumulative score modulo 10 plus this round's bags reaches
    10, the team suffers a **−100 bag-out**.
  - Nil and blind-nil points are added around the team total.
  - A team that crosses **+500** wins; the game also ends at **−500**
    (score-mode toggle switches the target to 300). (Spades!GameManager.GetScoreForRound, Spades!GameManager)
- AI: `EasyAIPlayer` bids `min(6, cards above Jack)` and then plays to make its
  bid — high cards while behind, dumping low cards when ahead, and it tries
  to avoid leading spades while short. `HardAIPlayer` bids by counting its
  spade run plus spades above the Jack, adding void-suit tricks and adjusting
  against the partner's bid (capped 0–10); its play logic weighs
  partner bid + own bid against tricks already won. Only Easy and Hard are
  selectable. (Spades!EasyAIPlayer.MakeBid, Spades!EasyAIPlayer.PlayCard,
  Spades!HardAIPlayer.MakeBid, Spades!HardAIPlayer.PlayCard)
- Progression: per-mode `StatTracker` (easy/hard, single-player/WiFi) tracks
  wins/losses and special events such as successful Nils and bag-outs;
  `RecordScreen` displays them. No unlocks. (Spades!StatTracker)

## 4. Controls

- Bid: scrub the bid wheel up/down; the displayed value is the wheel offset.
  Tap the Nil option for a zero bid; the Blind Nil button appears before the
  cards are revealed when Blind Nil is enabled. Confirm to send the bid.
  (Spades!Wheel.Bid, Spades!SinglePlayerGameScreen.MakeBid,
  Spades!SinglePlayerGameScreen.BlindNil)
- Play: tap a card to select and double-tap (or drag) to play it — the same
  selectedCard/doubleTap handler as Hearts, with drag tracking. (Spades!SinglePlayerGameScreen.PlayCard)
- Pause is a single corner button; the scoreboard overlay is toggled from the
  HUD; volume is adjusted in pause/options.

## 5. Content inventory (must be re-authored)

- 113 files: 87 PNGs, 21 XNB sounds, 5 XML files
  (`Text/Strings/en.xml`, `es.xml`, `fr.xml`, `Text/Fonts.xml`, `Text/Text.xml`).
- Art: deck spritesheet, bid wheel and bid appear/animation strips, bag icon,
  scoreboard panels, team/player chrome, pass arrows, and button states.
- Sounds: bagOut, button/buttonSoft, cardFlip, cardPass, cardPlay_1..3 and
  score cues. All art/audio/text must be re-authored.

## 6. Implementation plan

- Pure-Kotlin engine (`object SpadesEngine` + immutable `SpadesState`) in
  `ui/apps/games/`; Compose table through `DetailScaffold`; zero corner radius;
  tokens only; SFX via `SfxBank`; stats via `graph.games`; autosave via
  `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object SpadesEngine {
      fun legalPlays(state: SpadesState, seat: Seat): List<Card>
      fun bid(state: SpadesState, seat: Seat, bid: Int, blindNil: Boolean): SpadesState
      fun scoreRound(state: SpadesState): SpadesState
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `SpadesEngine` (MoreGames.kt)

1. **No AI play.** The UI shows "ai playing…" forever: only a random bid
   (`(1..5).random()`) exists. Official needs Easy bidding by high-card count
   and Hard bidding by spade runs/voids/partner, plus play strategies for both
   (make-the-bid vs dump, partner awareness).
2. **Nil/Blind Nil unreachable.** `SpadesEngine.bid` accepts `nil`/`blind`
   flags but the UI only offers 1..13, and there is no blind-nil pre-reveal
   button. Official: bid 0 = Nil (+100/−100), blind nil = +200/−200.
3. **Bag scoring differs.** Kotlin stores bags separately and subtracts 100
   per full 10; official adds overtricks to the score immediately and triggers
   the −100 when cumulative score mod 10 plus new bags ≥ 10. Match the
   official formula.
4. **No game target.** Official ends at ±500 (or ±300 in 300 mode); Kotlin
   plays one round and stops.
5. **No scoreboard/records/autosave.** Per-round bid/trick tables, career
   stats, dealer rotation and save/resume are missing.
6. **No difficulty/setup screen.** Easy/Hard and score-mode toggles don't
   exist; everything is hardcoded.
7. **Correct already:** follow-suit, no-leading-spades-until-broken (unless
   all spades), trump resolution (highest spade else led suit), and trick
   winner/loser mechanics match the reference. Keep them when adding AI/scoring.

## 7. Citation log

- `Spades!GameManager.ReadyToStartRound` · `Spades!GameManager.FindPlayerToStart`
- `Spades!GameManager.ReceiveBid` · `Spades!GameManager.PlayCard`
- `Spades!GameManager.GetScoreForRound` · `Spades!Hand.ValidCardsToLead`
- `Spades!Trick.Resolve` · `Spades!EasyAIPlayer.MakeBid`
- `Spades!EasyAIPlayer.PlayCard` · `Spades!HardAIPlayer.MakeBid`
- `Spades!HardAIPlayer.PlayCard` · `Spades!Wheel.Bid`
- `Spades!SinglePlayerSetUpScreen..ctor` · `Spades!SinglePlayerGameScreen..ctor`
- `Spades!SinglePlayerGameScreen.MakeBid` · `Spades!SinglePlayerGameScreen.BlindNil`
- `Spades!SinglePlayerGameScreen.PlayCard` · `Spades!ToggleButton.CreateScoreButton`
- `Spades!ToggleButton.CreateBlindNilButton` · `Spades!ScoreboardScreen` · `Spades!MainMenuScreen..ctor` · `Spades!SpadesGame` · `Spades!TeamSelect` · `Spades!StatTracker` · `Spades!GameManager.SetUpRound`
