# Animalgrams

- **Official package:** `Anagrams.exe` (animalgrams)
- **Corpus:** `Zune HD Apps (Decompiled)/animalgrams` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Anagrams.exe` | 46 | 143 | 4320 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 67 | 286 | 6089 |
| `ZuneGamesLib.dll` | 36 | 170 | 3610 |

- ZuneCoreLib `BaseGame`/`Screen` stack at 272×480 portrait; text rendered via
  a kerning table (`kerning.txt`) and per-font XML descriptions.
- Progress and settings persist through `FileSystem` (`SaveProgress.txt`,
  `GameSettings.txt`); the app is English-only and shows a dedicated notice
  screen when the device language is not `en`.

## 2. Screens & navigation

Screen stack (ZuneGamesLib `ScreenStack`) with slide transitions:

- Loading (word-bank parse + kerning preload) → English-only notice when the
  locale is not English → main menu.
- Main menu: Play, Options, Learn to play, About. Options has a sound toggle
  and Reset (confirmed by a yes/no dialog that wipes progress).
- Play → habitat picker → animal picker → game.
- Habitat picker: 5 habitats (jungle, savanna, forest, aqua, aviary), each a
  habitat button showing "X % complete" and an earned star at 100 %.
- Animal picker ("Word select"): per-habitat page of 5 animal buttons, each with
  its own progress percentage, plus a Back item.
- Game: the animal's letter tiles, current word field, word list, hint/erase/
  re-enter/submit controls, menu and word-list buttons.
- Win popup pushes over the game with medal art and sparkles; Back returns.
- Pause is not a screen here: leaving the game via menu pops back to the animal
  picker, saving progress on the way out.

## 3. Rules, scoring & progression

There is no numeric score: progress is "words found / words available", per
animal, per habitat, and globally.

- **Board.** Each round is one animal name; tiles are its letters, one button
  per character (duplicates included) in a 4-column grid.
- **Dictionary.** `WordBank.csv` is a matrix: the header names 25 animals and
  later rows list words formable from each animal's letters, one per column —
  1,517 accepted answers total; only the current animal's column counts.
- **Building.** Tap a tile to append and disable it; erase pops one letter and
  a 0.5 s hold clears the field; re-enter restores the last submitted word.
- **Validation.** Valid only if the word is in the animal's column and not
  already found; candidates light the glow before submission. A valid new word
  is recorded (distinct cue at 6+ letters), scrolls the list to reveal it and
  flashes particles; invalid words buzz and are not recorded.
- **Hints.** Reveals the next letter of a random unfound word, preferring the
  typed prefix; it fills the field and consumes a matching tile, locking hint
  for 1 s with an invalid cue when nothing matches. All-found ends hints.
- **Completion.** Finding every word opens the win screen with that animal's
  medal/unlock art. Habitat completion (5 animals) shows a percentage and
  stars at 100 %; global completion drives the all-habitats artwork.
- **Persistence/ramp.** `SaveProgress.txt` writes `animal:word,word,…` per
  played animal (only non-empty) and reloads into found lists; Reset clears.
  There is no mechanical ramp — difficulty is dictionary shape, with words
  listed shortest-first (ties alphabetical) and hints steering to uncompleted
  words.

## 4. Controls

- Pure touch: tap tile buttons, submit, erase (tap = one letter, 0.5 s hold =
  clear), re-enter, hint, word-list button, menu button.
- Word list is a scrollable view (vertical drag; found words highlighted).
- Buttons use focus/press/unfocus art states; disabled tiles show a dimmed
  "active" frame. No accelerometer or hardware-button gameplay input.
- Back navigation is on-screen only (Back / menu items), with slide transitions
  between screens.

## 5. Content inventory (must be re-authored)

137 files: 90 `.png`, 39 audio `.xnb`, 4 `.txt`, 3 `.xml`, 1 `.csv`.

- **Word corpora to re-author (do not copy):** `WordBank.csv` (~10 KB, 123
  lines: header of 25 animal keys + 122 word rows → 1,517 entries),
  `animals.txt` (~3.4 KB, 100 lines = 25 animals × 4 sprite-coordinate lines),
  `letters.txt` and `buttons.txt` (26 lines each, letter atlas frames),
  `kerning.txt` (glyph pair adjustments).
- Art: 5 habitat backdrops with animated props (snow, bubbles, fish,
  fireflies, butterflies, birds, antelope, eagle, hummingbird), the animal
  atlas, letter atlases (normal/hilite/null), word/letter glows, sparkles,
  medals, star, shutter frames, signposts, buttons.
- UI chrome: `buttons.png`, word-list backgrounds/frames, logo, loading
  background, English-only signpost, habitat/title/win art, and XML-described
  fonts (Zegoe UI family plus letter/win/habitat display fonts).
- Audio (39 cues): menu move/forward/back, button, letter down, erase,
  resubmit, invalid word, hint valid/invalid/complete, word reveal short/long,
  win, list complete, reset bongos/maracas, per-habitat ambience and shutter
  open/close.

Re-author: our own per-animal word corpus with the same matrix shape, Canvas
habitat backdrops with the same ambient-motion roles, procedural sprites,
token fonts and synthesized SFX.

## 6. Implementation plan

- Engine: `ui/apps/games/animalgrams/AnimalgramsEngine.kt` — pure Kotlin
  `object` over immutable state (current animal, typed letters, available/
  found word sets, hint cursor, rng); deterministic and unit-testable; no
  `step(dt)` physics needed, event-driven `apply(event): State`.
  ```kotlin
  object AnimalgramsEngine {
      fun submit(state: State, word: String): State
  }
  ```
- Data: bundled re-authored `wordbank.json` (animal → words) generated by a
  build-time script; corpus kept out of the repo until scrubbed/checked in as
  synthesized content.
- Screen: `AnimalgramsScreen.kt` in `DetailScaffold`, portrait `Canvas`;
  habitat/animal pickers as zero-radius list rows; game board with letter tile
  grid, word field, found-word list, hint/erase/re-enter/submit from tokens.
- Persistence: per-animal found sets through `graph.appState`; sound toggle
  likewise; no numeric high score needed (`graph.games` reserved for badges).
- SFX via `SfxBank`: tile tap, erase, invalid, valid, hint, word reveal by
  length, win fanfare; ambient loops per habitat optional.
- Tests: `app/src/test/java/com/heretek/dorado_hd/` — submit/duplicate/erase/
  hint logic, letter-tile accounting with duplicate letters, per-animal
  percentage math, persistence round-trip, English-only gate.

## 7. Citation log

- `Anagrams.exe!Anagrams.WordBank` — CSV header/column structure, 25 keys,
  per-column word lists, length-mismatch guard.
- `Anagrams.exe!Anagrams.Progress.Save` / `Load` — `SaveProgress.txt` format
  and reset.
- `Anagrams.exe!Anagrams.GameScreen.submit_Clicked` — validity rule, progress
  append, short/long reveal cues, win trigger when all found.
- `Anagrams.exe!Anagrams.GameScreen.hint_Clicked` — prefix-preferred random
  unfound word, one-letter reveal, 1 s lockout.
- `Anagrams.exe!Anagrams.GameScreen.reenter_Clicked` / `back_Clicked` /
  `back_Pressed` / `Clear` — undo, hold-to-clear (0.5 s), tile re-enable.
- `Anagrams.exe!Anagrams.GameScreen.UpdateSubmitButtonColor` — valid-word glow.
- `Anagrams.exe!Anagrams.GameScreen.sortAvailableWords` — length then alpha.
- `Anagrams.exe!Anagrams.GameScreen` constructor + `CreateUI` — per-animal word
  pool and letter tiles from the animal key, 4-column grid.
- `Anagrams.exe!Anagrams.HabitatScreen` — five habitats and their routing.
- `Anagrams.exe!Anagrams.HabitatButton.Refresh` — 5 animals per habitat,
  percentage math, 100 % star.
- `Anagrams.exe!Anagrams.WordSelectScreen` — per-habitat animal list + header
  completion.
- `Anagrams.exe!Anagrams.WinScreen` — win popup, medal art, sparkles.
- `Anagrams.exe!Anagrams.OptionsScreen` + `SoundsButton` + `ResetConfirmationScreen`
  — sound toggle and destructive reset confirmation.
- `Anagrams.exe!Anagrams.EnglishOnlyScreen` — locale gate.
- `Anagrams.exe!Anagrams.MainMenuScreen` — Play/Options/Learn-to-play/About.
- `Anagrams.exe!Anagrams.KerningManager` — kerning table applied at load.
