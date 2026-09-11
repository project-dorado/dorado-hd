# Decoder Ring

- **Official package:** `Decodering.exe` (decoderring)
- **Corpus:** `Zune HD Apps (Decompiled)/decoderring` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Decodering.exe` | 49 | 470 | 5826 |
| `ContentPipelineExtension.dll` | 8 | 18 | 363 |
| `Noodles.dll` | 89 | 765 | 10478 |
| `ZuneCoreLib.dll` | 67 | 286 | 6089 |

- Noodles engine with FengShui menus; screen size comes from the shell
  (`SetScreenSize`). Save state uses Noodles `PersistentSettings` plus a game
  save blob (`puzzleID`, `isGameInProgress`, per-puzzle completion stars).
- All puzzle progress is resumable: the current puzzle (grid, symbol↔letter
  links, set-in-stone flags) is serialized on every placement so the app can
  relaunch mid-solve.

## 2. Screens & navigation

- Splash (≈3 s, logo + Babaroga plate) → main menu.
- Main menu: 20 level buttons for pack 1 (frame shows completion; a child star
  sprite encodes the difficulty tier completed), a 4-way difficulty selector
  (easy/normal/hard/expert), sound on/off, reset (with confirm popup).
- Resume handling: if a puzzle is in progress and the player taps a different
  level, a popup asks to resume or overwrite the saved game.
- Game screen: scrolling/zoomable 15×15 board of cipher symbols, letter rack
  along the bottom, HUD with check toggle, reveal button, help header, pause.
- Pause menu: resume, help popup, reset, sound, difficulty (starts a fresh
  mapping), back to menu. Win state triggers a completion popup sequence, then
  returns to the menu with the next puzzle unlocked.

## 3. Rules, scoring & progression

A substitution-cipher crossword: every distinct letter used by the hidden
crossword is replaced by a symbol, and the player re-learns the mapping.

- **Puzzle grid.** Every puzzle is a 15×15 character grid; letters run as
  across/down words and `.` cells are blanks. Packs are read from
  `puzzles/pck_<pack>_<n>.txt`. Difficulty tiers are easy/normal/hard/expert.
- **Board & rack.** The board shows one symbol per non-blank cell (27 possible
  symbols, one of which is the "empty" symbol 0). The rack holds 27 letter
  tiles: a blank plus A–Z. Tiles are dragged from the rack onto symbol slots;
  multiple slots sharing a symbol share the mapping, so placing a letter on one
  instance updates every occurrence.
- **Auto-fill by symbol.** Placing a letter maps that symbol everywhere; dragging
  a letter off any slot clears the mapping. Blank cells never take a tile.
- **Pre-revealed letters.** Easy seeds 7 (T, N, S, R, L, A, E), normal 4
  (T, N, S, E), hard 3 (T, N, S), expert none; seeded links render differently
  and can't be dragged off (set-in-stone, persisted per letter).
- **Check toggle.** Checking on renders a correctness state on placed tiles;
  the default is on and the choice persists.
- **Reveal.** Enabled only while a symbol is highlighted: it maps that symbol to
  its true letter and confirms it, making those tiles set in stone (not
  grabbable). Reveals are unlimited — the puzzle is solved, not scored.
- **Win.** Complete when every letter actually used is mapped by the player and
  correct (unused letters don't count); completion records the solved
  difficulty, unlocks the next puzzle and plays a cascade.
- **Progression/persistence.** One pack exposes 20 level slots (35 puzzle files
  ship on disk); per-puzzle completion stores the difficulty tier so a level
  can be replayed harder for a better star — medals, not numeric scores.
  Prefs track sound, check default, last pack/puzzle, in-progress state, pack
  unlocks and the completion array.

## 4. Controls

- **Drag**: press a letter tile (rack or board) to grab it (scaled up, semi-
  transparent), drag it over the board, and release on a slot to drop; release
  elsewhere returns it. Holding a rack tile while a board symbol is highlighted
  places it directly without a drag.
- **Tap**: tap a highlighted symbol to select it; tap the reveal button to
  auto-place that symbol's correct letter.
- **Pan**: drag the board or rack background to scroll it; the container uses
  a flick model (velocity from recent touch history ×1.2, clamped to 3000,
  friction 3) and springs back when dragged out of bounds.
- **Zoom**: pinch with two touches to zoom (zoomed levels 0.597 / 0.36 of the
  board layout); double-tap also toggles zoom (500 ms double-tap window).
- Single-touch drags never conflict with board pan because grabs consume the
  touch; no accelerometer input.

## 5. Content inventory (must be re-authored)

100 files: 35 `.pck`, 35 `.txt`, 26 `.xnb`, 3 `.bff`, 1 `.fsf`.

- **Puzzle data to re-author (do not copy):** 35 plain-text 15×15 cipher grids
  (`pck_0_1.txt` … `pck_0_35.txt`, dots for blanks) though the UI exposes 20
  slots; we author our own grids and symbol-mapping generator.
- Game art: `tiles.pck` (tile frames), `tile_slots.pck` (slots + glows),
  `tile-shadow`, `tile_rack`, HUD placement sheets.
- Menu art: logo plates, splash, buttons, 20 level buttons, star sheet,
  difficulty ×4, header plates (check/reveal/help/pause/time), popups, win art.
- Fonts: three Noodles `.bff` fonts. Audio: generic asset references for
  insert, click, incorrect and complete cues — re-authored as synthesized
  clicks/chimes.

## 6. Implementation plan

- Engine: `ui/apps/games/decoderring/DecoderEngine.kt` — pure Kotlin `object`
  over immutable `State` (grid, symbol→letter mapping, letter usage, revealed
  set, difficulty, completion); event-driven `apply(event)` rather than a
  physics `step`; deterministic seed for the symbol substitution so a puzzle
  looks identical on replay.
  ```kotlin
  object DecoderEngine {
      fun place(state: State, symbol: Int, letter: Char): State
  }
  ```
- Data: bundled re-authored puzzle pack (our own grids) + mapping generator;
  no original puzzle text is retained.
- Screen: `DecoderScreen.kt` in `DetailScaffold`; portrait `Canvas` board with
  pan/zoom (fractional scale + offset), letter rack, HUD (check, reveal,
  pause), win overlay; token colors only, zero corner radius.
- Inputs: tile grab/drag/drop via `pointerInput`, pan with velocity fling,
  pinch zoom, tap-to-place.
- Persistence: in-progress mapping and per-puzzle completion through
  `graph.appState`; resumes on relaunch; SFX via `SfxBank` (insert, click,
  win chime).
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): grid parse and blank
  handling, mapping propagation across duplicate symbols, difficulty reveal
  sets, win detection over used letters only, reveal lock behavior, save/
  resume round-trip, fling/friction math, zoom bounds.

## 7. Citation log

- `Decodering.exe!Decodering.GameData.LoadCrosswordPuzzle` — text grid format,
  `.` blanks, 15-wide shaping, per-pack file naming.
- `Decodering.exe!Decodering.GameData.Difficulty` + `REVEALED_LETTERS_*` —
  four tiers and their seeded letters (7/4/3/0).
- `Decodering.exe!Decodering.GameData.IsGameComplete` — win rule (all used
  letters mapped by user and correct).
- `Decodering.exe!Decodering.GameData.LinkSymbol` / `DelinkSymbol` /
  `ConfirmAllLinks` — symbol mapping lifecycle and confirmation.
- `Decodering.exe!Decodering.Letter` — 27 letters incl. blank, set-in-stone
  persistence keys.
- `Decodering.exe!Decodering.Symbol` — 27 symbols with 0 = empty.
- `Decodering.exe!Decodering.GameScreen.OnUserRevealPressed` /
  `PlaceLetterInHighlightedSlots` / `GrabTile` / `DropTile` — reveal, mapping
  propagation, grab/drop flow.
- `Decodering.exe!Decodering.GameScreen.ToggleUserCheck` /
  `Board.OnCheckingToggled` — check default, persistence, checked rendering.
- `Decodering.exe!Decodering.GameScreen` / `SlotContainer` — zoomed levels
  0.597/0.36, win cascades, move threshold 40, double-tap 500 ms, flick
  1.2×/3000, friction 3, out-of-bounds return.
- `Decodering.exe!Decodering.Prefs` — 1 pack × 20 puzzles, completion array,
  in-progress flag, sound levels, check default.
- `Decodering.exe!Decodering.MainMenu` — level selection, difficulty action,
  resume/overwrite popup, star frames.
- `Decodering.exe!Decodering.GameMenu` — reveal enablement, check button
  frames, win delay and popup.
- `Decodering.exe!Decodering.AudioPlayer.SoundEffect` — cue taxonomy.
