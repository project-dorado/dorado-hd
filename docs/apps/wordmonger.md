# WordMonger

- **Official package:** `WordMonger.exe` (wordmonger)
- **Corpus:** `Zune HD Apps (Decompiled)/wordmonger` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `WordMonger.exe` | 168 | 1237 | 30513 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |

- Layered sprite engine on a 320×480 logical canvas scaled to the Zune's
  272-wide display (cell 44); 50 Hz logic. Music/SFX run on a worker thread;
  the name keyboard is the system dialog.
- Settings persist to `AppSettings.txt` (nickname, volumes); resume state to
  `ResumeState.bin`.

## 2. Screens & navigation

- Boot → main menu (Play, Scores, Rules, Options), each with normal/small
  variants.
- High-score screen: named local leaderboard plus best-word and word-power
  stats; name entry/change via keyboard dialog.
- Rules: paged tutorial panels (heading, prev/next, swipe illustration).
- Options: music and SFX volume sliders plus an enable-tutorial toggle.
- In game: board with score/bonus-word/level/time labels sliding in and out, a
  pause button, and dialogs for level change, resume and game over.

## 3. Rules, scoring & progression

A timed word-building puzzle on a refilling letter board.

- **Board.** 7 columns × 7 visible rows of 44 px tiles (49 live tiles); tiles
  spawn above and fall in. A level transition clears and refills the board.
- **Letter distribution.** Classic 98-tile English set (E×12, A/I×9, O×8,
  N/R/T×6, L/S/U/D×4, G×3, B/C/M/P×2, F/H/V/W/Y×2, K/J/X/Q/Z×1) with values
  1–10; each level draws `49 + level·7` tiles, shuffles three times, and the
  tutorial variant seeds F/U/N/O near the draw tail.
- **Word building.** Tap/drag along a row or column to select a contiguous run;
  releasing checks it against the dictionary. Valid words play a per-tile
  flying/refill animation; invalid words buzz and clear. Words play up to 7
  letters.
- **Dictionary.** Packed trie (`.dwg`, ~40.9k 4-byte nodes) plus a per-word
  quality-rank table (`.rnk`, 213,532 entries) and a 478-line bonus-word list.
  Our own trie/ranks/bonus list replace all three.
- **Scoring.** `raw = Σ letter values`; `multiplied = Σ value × tile
  multiplier` (12 seeded multiplier cells, ×2/×3/×4); points =
  `multiplied × round(wordQuality)`, ×4 for the current bonus word. Hattrick:
  three consecutive accepted words longer than 4 letters doubles the last
  word's points. Word power and speed power roll as moving averages; longest
  and best word are tracked.
- **Level end** (checked only after 15 s): board cleared → +1000 and next
  level; ≤ 20 tiles and no dictionary word possible → next level; ≤ 20 tiles
  with fewer than 800 possible words → **Rush Mode**, a 30 s ticking countdown
  that forces the next level. A periodic bonus-word timer assigns a word worth
  ×4; it clears when played or when Rush starts.
- **Hazards.** A random non-frozen tile can become a **bomb** (max 4): 30 s
  fuse with whistle, a WatchOut warning at 18 s, then explosion and run end.
  Using the bomb tile in a word defuses it. **Frozen** tiles show padlocks and
  cannot move; **cracked** tiles break.
- **Swaps and bird.** Sliding a row/column segment shifts it one cell (locks
  block); after 7 swaps a 20 % rocket swaps two arbitrary cells. A chicken
  flies across at fixed height and drops an egg 4 times in 7; hitting the bird
  with a moving tile cancels it.
- **Persistence.** Nickname, volumes and tutorial flag live in
  `AppSettings.txt`; `ResumeState.bin` restores a part-played game; local
  scores are ranked with the player name.

## 4. Controls

- **Select**: tap the first tile and drag across adjacent tiles in a straight
  line (taps also work); ending the selection on a dictionary word submits it.
- **Swap**: drag a row or column segment one cell (major axis only; wrong axis
  buzzes; locks block).
- Menus are touch buttons/sliders; tutorial bubbles gate specific taps until
  the requested tile is pressed. No accelerometer input; single touch only.

## 5. Content inventory (must be re-authored)

80 files: 60 `.xnb`, 10 `.spritedef`, 7 `.wma` music tracks, 1 `.txt`,
1 `.dwg`, 1 `.rnk`.

- **Corpora to re-author (do not copy):** `Words/USOSPD4LWL.dwg` (packed trie,
  ~164 KB) + `.rnk` (~214 KB ranks, 213,532 word entries) + `BonusWords.txt`
  (478 lines); we ship our own generated trie, ranks and bonus list.
- Backgrounds (4): game/menu background + frame each.
- Sprite sheets (10 with `.spritedef` layouts): Sprites (tiles, attachments,
  rocket, egg, padlock, cracked tile, bomb, archer, bird, gems, particle),
  CommonSprites, MainMenu/Options/HowToPlay/TutorialPanels, TableImages
  (score table, crowns), Grobold and Mentone fonts, WordFont letters.
- Audio: 7 music tracks (menu, in-game, timed, options, victory, defeat, high
  score) and ~23 samples (tile select/deselect, accepted/rejected words,
  bonus start/end, hattrick, level up, game over, bomb whistle/explosion,
  rocket, egg, bird clucks, watch-out).

Re-author: token-palette tiles/gems/bombs/bird, synthesized cues and loops,
and a trie builder over our own corpus.

## 6. Implementation plan

- Engine: `ui/apps/games/wordmonger/WordmongerEngine.kt` — pure Kotlin `object`
  over immutable `State` (board grid, bag, selection, score/stats, level,
  rush/bonus timers, bombs, bird); fixed `step(dt)` at 50 Hz with seeded RNG
  for bag shuffle, bomb/bird rolls and bonus picks; dictionary behind a
  `Lexicon` interface (trie + quality) built from our own data.
  ```kotlin
  object WordmongerEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Screen: `WordmongerScreen.kt` in `DetailScaffold`, portrait `Canvas`; tile
  grid, falling animations, attachments (bomb/lock/cracked/egg/gems), labels,
  dialogs and tutorial bubble — token-driven, zero corner radius.
- Inputs: drag-selection with straight-line locking, segment-swap drags,
  menu/slider targets, resume prompt.
- Persistence: settings, resume state and local score table via
  `graph.appState`, highscores via `graph.games`; SFX/music via `SfxBank`.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): bag counts and shuffle
  bounds, dictionary contains/quality, selection validity (axis + contiguity),
  scoring incl. multipliers/bonus/hattrick, level-end conditions and rush
  trigger, bomb fuse and defuse, frozen/cracked behavior, swap counting and
  rocket chance, resume round-trip, local score ranking.

## 7. Citation log

- `WordMonger.exe!Foundation42.Applications.Constants.SetDevice` — 320×480
  logical canvas, 272 display, cell 44, rush period 30 s, 50 Hz.
- `WordMonger.exe!Foundation42.Applications.LetterBag` — 26-letter
  distribution counts/values, `49 + level·7` draw, triple shuffle, tutorial
  seed letters.
- `WordMonger.exe!Foundation42.Applications.WordDictionaryBase.ReadFile` —
  `.dwg` node dump and `.rnk` header + rank bytes; 7-char buffer.
- `WordMonger.exe!Foundation42.Applications.ScoringSystem.Submit` /
  `UpdateScore` / `GetWordScore` — validity, quality/speed power, hattrick,
  best/longest word, bonus ×4, multiplied-value math.
- `WordMonger.exe!Foundation42.Applications.ScoringSystem.CheckEndOfLevel` /
  `UpdateRushMode` / `StartNewLevel` / `SwapTiles` — 15 s gate, +1000 clear
  bonus, ≤20-tile outcomes, 800-word rush threshold, 30 s expiry, swap
  counter and 20 % rocket.
- `WordMonger.exe!Foundation42.Applications.GameBoard.OnTouchesBegan` /
  `OnTouchesMoved` / `SwapTiles` / `SubmitWord` — selection and swap
  gestures, submission flow.
- `WordMonger.exe!Foundation42.Applications.GameTile.UpdateSprite` /
  `IGameHost.BombRandomTile` — 30 s fuse, 60 % watch-out, defuse, max 4
  bombs, frozen exclusion.
- `WordMonger.exe!Foundation42.Applications.GameBoard.BirdFly` /
  `DropEgg` / `CheckBirdHit` — bird path, 4/7 egg chance, hit cancel.
- `WordMonger.exe!Foundation42.Applications.MultiplerCell` / `DoLevelChange`
  — 12 multiplier cells, board repopulate and level info panel.
- `WordMonger.exe!Foundation42.Applications.GameState` /
  `GameOverDialog` — score/word-power/level counters, name entry and commit.
- `WordMonger.exe!Foundation42.Applications.OptionsScreen` — volumes and
  tutorial toggle.
