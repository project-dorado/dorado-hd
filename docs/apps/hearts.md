# Hearts

- **Official package:** `Hearts.exe` (hearts)
- **Corpus:** `Zune HD Apps (Decompiled)/hearts` (external, untracked)
- **Wave:** W2 · **Category:** games
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** hearts

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Hearts.exe` | 58 | 298 | 8239 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZuneCoreLib.dll` | 66 | 275 | 5853 |
| `ZuneGamesLib.dll` | 26 | 134 | 2730 |

- Native 272x480 landscape-style card table (backdrop art, four seat labels,
  a deck spritesheet, pass arrows); input is touch-only, no accelerometer.
- Assembly description states the pitch: "Play as a Novice or Expert, and
  choose from three different game modes."

## 2. Screens & navigation

- `MainMenuScreen`: Single Player, Multiplayer, Options, Learn To Play,
  Records; multiplayer chains through `WifiModeSelectScreen`, `HostScreen`,
  `JoinScreen`, `LobbyScreen` and `WifiGameScreen`. (Hearts!MainMenuScreen..ctor)
- `SinglePlayerSetUpScreen`: a three-way mode toggle (Standard, Wildcard,
  Turbo) whose right-hand panel lists the point cards of the selected mode,
  plus an Easy/Hard difficulty toggle and Play/Back. Selecting Play pushes
  `SinglePlayerGameScreen(mode, difficulty)`. (Hearts!SinglePlayerSetUpScreen..ctor,
  Hearts!ToggleButton.CreateDifficultyButton)
- In game: seat name plates and per-seat round scores, the passing overlay
  (`arrow_left`/`arrow_right`/`arrow_across` depending on direction), the trick
  area, and a bottom status bar; an in-game pause button pushes `PauseScreen`
  (Resume / Volume / Learn / Main Menu). Autosave via `HeartsGame.Autosave`
  supports resume. (Hearts!SinglePlayerGameScreen..ctor)
- End of round/game: `ShootTheMoonScreen` (moon + rocket animation) when a
  player takes every penalty card, and `EndGameScreen` at game over;
  `ScoreboardScreen` shows round-by-round bids/tricks and `RecordScreen`
  the career stats (including times shot the moon). (Hearts!GameManager.ResolveRound, Hearts!ShootTheMoonScreen..ctor)

## 3. Rules, scoring & progression

- Four players, 52 cards, 13 each. The holder of the 2♣ leads the first trick;
  `FindPlayerToStart` locates that seat. (Hearts!GameManager.FindPlayerToStart)
- Passing: each player passes 3 cards. The direction starts at **Left** and
  advances each round through the `PassStyle` cycle Left → Right → Across →
  None → Left. Passing is skipped on a None round. (Hearts!GameManager.PassCards, Hearts!GameManager.PassStyle)
- Leading restrictions: a player holding the 2♣ must lead it; otherwise hearts
  may not be led until "broken", unless the hand is all hearts. (Hearts!Hand.ValidCardsToLead)
- First-trick rule: when following suit on trick one, only zero-value cards are
  legal if you hold any; when void, you may only discard zero-value cards.
  This is the "no points on the first trick" rule. (Hearts!Hand.ValidCardsToFollow)
- Scoring per card: hearts = 1 point, Q♠ = 13. (Hearts!Deck.AssignPointValues)
- **Wildcard mode** adds J♦ = −10 points (a bonus card) and 7♣ = +7 points;
  both are shown with their values in setup. AI seats specifically hunt J♦ when
  diamonds are led below the jack. (Hearts!Deck.AssignPointValues,
  Hearts!MediumAIPlayer.FollowingNotShooting)
- **Turbo mode**: if a player has taken an Ace in their tricks, all hearts they
  take count double (`DoubleHearts`). (Hearts!Hand.DoubleHearts,
  Hearts!Hand.ScoreForThisHand)
- **Shooting the moon** (exactly one player takes every penalty card): normally
  the shooter scores 0 and each opponent +26. If any opponent is pushed to
  ≥100 and the shooter is *not* the sole lowest score, the alternative
  "doubling" resolution applies: the shooter gets −26 and the others 0. (Hearts!GameManager.ResolveRound)
- Game target: 100 points. When anyone reaches 100 the game ends and the
  **lowest** score wins; a tie for lowest is an end-game tie. (Hearts!GameManager,
  Hearts!GameManager.ResolveRound)
- AI personalities: `EasyAIPlayer` leads the lowest card of its shortest suit
  (avoiding penalty cards), follows with its lowest card and passes its three
  highest cards; `MediumAIPlayer` adds shoot-the-moon detection
  (`IdentifyAbilityToShoot`) and shooting lead/follow/pass plans;
  `HardAIPlayer` additionally tracks which suits everyone is void in and only
  plays "safe" cards when dumping. Only Easy and Hard are selectable in the
  shipped setup UI. (Hearts!EasyAIPlayer.PlayCard,
  Hearts!MediumAIPlayer.IdentifyAbilityToShoot,
  Hearts!HardAIPlayer.IsEveryoneVoidOfSuit, Hearts!ToggleButton.CreateDifficultyButton)
- Records are per-mode stats (`Stats` keyed by Standard/Wildcard/Turbo),
  including times the player shot the moon. (Hearts!StatTracker)

## 4. Controls

- Tap a card to raise/select it; double-tap (or drag) to play the selected
  card. During the pass, tap three cards to mark them, then confirm; the three
  passes are sent left/right/across per the round. The touch handler tracks
  `selectedCard`, `startDragPosition` and a double-tap flag before calling
  `PlayCard()`. (Hearts!SinglePlayerGameScreen)
- Pause is a single button; volume is adjusted from the pause/options screens.

## 5. Content inventory (must be re-authored)

- 109 files: 86 PNGs, 18 XNB sounds, 5 XML files
  (`Text/Strings/en.xml`, `es.xml`, `fr.xml`, `Text/Fonts.xml`, `Text/Text.xml`).
- Card deck spritesheet, per-suit pass arrows (left/right/across, each with
  locked variants), score-round highlights, shoot-the-moon movie stills
  (`shootmoon`, `shootmoon_moon`, `shootmoon_rocket`), scoreboard and record
  icons (heart, spade, moon).
- Sounds include cardFlip, cardPass, cardPlay_1..3, buttonSoft, shotTheMoon,
  score and fanfare cues. All art/audio/text must be re-authored.

## 6. Implementation plan

- Pure-Kotlin engine (`object HeartsEngine` + immutable `HeartsState`) in
  `ui/apps/games/`; Compose table through `DetailScaffold`; zero corner radius;
  tokens only; card/announcement SFX via `SfxBank`; stats via `graph.games`;
  autosave via `graph.appState`.
- Engine surface sketch:
  ```kotlin
  object HeartsEngine {
      fun legalPlays(state: HeartsState, seat: HeartsSeat): List<Card>
      fun play(state: HeartsState, card: Card): HeartsState
      fun aiPlay(state: HeartsState, seat: HeartsSeat, level: Level): Card
  }
  ```
- Unit tests in `app/src/test/java/com/heretek/dorado_hd/LogicTest.kt`.

### Fidelity deltas vs existing `HeartsEngine` (MoreGames.kt)

1. **One AI.** `aiPlay` is a single random-ish strategy. Official has Easy,
   Medium and Hard personalities (with shooting plans and void counting);
   implement at least Easy/Hard and expose the difficulty toggle.
2. **No game modes.** Wildcard (J♦ −10, 7♣ +7) and Turbo (double hearts after
   taking an Ace) are absent; `SolCard` has no wildcard value concept. Add a
   mode to state and the setup screen.
3. **No shoot-the-moon.** Kotlin ends the "round" when all hands are empty and
   simply sums penalty points; add all-penalty detection, +26/−26 resolution,
   the announcement screen and a 100-point game loop.
4. **Pass direction never rotates.** `passDirection` is fixed at `1` (right);
   official cycles Left→Right→Across→None starting at Left. Also the Kotlin
   full-pass chooses random cards for the AI seats — the official AIs pass
   strategically (hard AI dumps short suits and guards Q♠/J♦).
5. **First-trick rule missing.** `legalPlays` enforces only follow-suit and
   no-leading-hearts; add "no point cards on trick one" for both following and
   discarding.
6. **No records/autosave.** Official keeps per-mode stats and an autosave with
   pass direction, reviewed status and trick state; Kotlin records the summed
   score once and keeps nothing else.
7. **Correct already:** trick winner (highest of led suit), Q♠ = 13, hearts = 1
   and the hearts-broken rule match the reference.

## 7. Citation log

- `Hearts!GameManager.FindPlayerToStart` · `Hearts!GameManager.PassCards`
- `Hearts!GameManager.PassStyle` · `Hearts!GameManager.GameMode`
- `Hearts!GameManager.ResolveRound` · `Hearts!Deck.AssignPointValues`
- `Hearts!Hand.ValidCardsToLead` · `Hearts!Hand.ValidCardsToFollow`
- `Hearts!Hand.DoubleHearts` · `Hearts!Hand.ScoreForThisHand`
- `Hearts!EasyAIPlayer.PlayCard` · `Hearts!MediumAIPlayer.IdentifyAbilityToShoot`
- `Hearts!MediumAIPlayer.FollowingNotShooting` · `Hearts!HardAIPlayer.IsEveryoneVoidOfSuit`
- `Hearts!SinglePlayerSetUpScreen..ctor` · `Hearts!ToggleButton.CreateDifficultyButton`
- `Hearts!SinglePlayerGameScreen..ctor` · `Hearts!ShootTheMoonScreen..ctor` · `Hearts!MainMenuScreen..ctor` · `Hearts!StatTracker`
