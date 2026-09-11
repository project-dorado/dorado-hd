# Splatter Bug

- **Official package:** `SplatterBug.exe` (Splatter Bug)
- **Corpus:** `splatter_bug` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `SplatterBug.exe` | 27 | 122 | 2,698 |

Portrait 272x480. Bugs crawl up or down the screen and the player squishes the
pests while letting harmless butterflies and ladybirds escape. Three hearts,
one score, a 750 ms spawn cadence. Settings (high score, sound flag) persist
to `settings.xml`; no dependency assembly was mined for this title.

## 2. Screens & navigation

- **Splash** → **Main Menu**: `New Game`, `Help`, `Options`, `Scores`,
  `About`, with the 2play logo art.
- **Game**: background image; score at top, `Lives:` and heart icons below it,
  a pause soft key at top-left, and a bottom bar for in-game keys. A squished
  bug prints its value (+points or `-life`) for ~1 s.
- **Pause**: `Resume`, `Restart`, `Main Menu`, plus sound toggle; the board is
  dimmed behind the pop-up.
- **Game Over**: `Restart` / `Main Menu` with the `gameover` banner.
- **Options**: sound on/off, reset scores (with confirm), help; **Scores**
  shows the persisted high score; **Help** explains "squish the bugs, free the
  critters"; **About** shows the Microsoft copyright page.

## 3. Rules, scoring & progression

- **Spawn**: every **750 ms** a bug is created. Its horizontal position is
  `rand(0..191) + 40` px; its direction is a random 0 or π rotation, so
  non-rotated bugs crawl from y=480 to above the top edge, rotated bugs from
  y=0 to below the bottom. Bugs never change direction and never collide with
  each other.
- **Bug table** (sprite size, hit points, speed px/frame):

  | Bug | Size | HP | Speed |
  |---|---:|---:|---:|
  | Ant | 60 | 10 | 6 |
  | Butterfly | 70 | 30 | 8 |
  | Cockroach | 70 | 10 | 7 |
  | Earwig | 60 | 10 | 10 |
  | Ladybird | 60 | 30 | 5 |
  | Spider | 36 | 20 | 4 |
  | Tarantula | 162 | 50 | 6 |

- **Spawn weights** out of 100: Ant < 20, Butterfly < 35, Cockroach < 50,
  Earwig < 65, Ladybird < 80, Spider < 95, else Tarantula (5%).
- **Rule**: **squish the pests, let the harmless pass.** Butterfly and Ladybird
  are harmless — tapping one costs a **life** and does **not** score; letting
  one leave the screen awards its hit points. For the other five, tapping
  awards hit points and letting one escape costs a life. A **Tarantula**
  requires **3 taps** before it dies (each preliminary tap plays a squish cue).
- **Lives**: starts at **3**; each failure decrements; the game-over check fires
  when the counter reaches **−1**, i.e. on the fourth failure (hearts render
  only while lives > 0). A `"Bug escaped! -life"` toast fades over 1000 ms.
- **Death visuals**: a killed bug swaps to its squashed sprite, prints the
  score/life text and fades out over **5000 ms** before being removed; a
  Tarantula death additionally spawns a burst of 10 small spiders.
- **Scoring/progression**: score only ever rises from hit points (harmless
  escapes and pest kills). There are no levels or waves; difficulty is fixed by
  the spawn weights. The high score is updated at game over and written to
  `settings.xml`; Options' reset clears it.
- **Sound**: each bug type has a squish sample; the shipped code routes every
  squish to `AudioManager.PlaySound(0)` (the ant sample), so a reimplementation
  may either mirror that or map samples per bug — document whichever is chosen.

## 4. Controls

- **Tap** a bug's touch surface (`SpriteWidth × SpriteHeight` at its position)
  to squish it; the touch state must be a press (`Pressed`). One touch id is
  latched so a second finger cannot also squish during the same press.
- The pause key is a tap target at the top-left; bottom-bar menu items are tap
  or soft-key regions with focus/pressed states.
- No tilt, drag, swipe or multitouch requirement.

## 5. Content inventory (must be re-authored)

80 XNB files:
- `Images/Game` (30): walk animation strips (ants, butterfly, cockroach,
  earwig, ladybird, baby spider, tarantula), single-frame variants, heart,
  pause/menu/restart/resume/continue keys, gameover banner.
- `Images/Game/Bugs` (7): squashed sprites per bug.
- `Images/Menu` (28): background bars, logo, back/about/help/options/newgame
  buttons with `_off`/`_on` variants, warning/confirm panels.
- `Images/Backgrounds` (4): bg, fadebg, menubg, splash.
- `Images/Font` (4): ArialFont, LargeMenuFont, MenuFont, `Yikes!`.
- Audio (7): ant, butterfly, cockroach, earwig, ladybird, spider, tarantula.
- `settings.xml` schema: highScore, soundOn.

## 6. Implementation plan

- Engine: `ui/apps/games/SplatterBug.kt` — `object SplatterBugEngine` with
  immutable `BugState(score, lives, bugs: List<Bug>, spawnClockMs)` and a
  deterministic `step(dt, taps)` plus a `BugKind` table holding size, HP, speed
  and weight. RNG is seeded into the state.
- Screen: `SplatterBugScreen` in `DetailScaffold("splatter bug")`, Canvas
  crawlers as token-coloured shapes, score/lives HUD, escape toast, pause,
  game-over and options overlays.
- Persistence/SFX: high score + sound flag via `graph.appState`;
  `graph.games.record("splatter-bug", score, null)`; `SfxBank` squish cues
  (per-bug or the shipped single-slot behaviour, chosen at build time).
- Tests (`app/src/test/java/com/heretek/dorado_hd/SplatterBugEngineTest.kt`):
  750 ms spawn cadence; spawn x range 40–231; direction/edge exit handling;
  harmless tap loses a life and escaping harmless scores; pest escape loses a
  life; tarantula needs 3 taps; lives end at −1; fade removal after 5000 ms;
  high-score persistence.

## 7. Citation log

- `SplatterBug!GamePlayScreen.Update` — 750 ms spawn interval, x range, random
  rotation, movement and edge-exit scoring/life rules.
- `SplatterBug!GamePlayScreen.UpdateLogic` — tap-to-squish, tarantula 3-hit
  rule, harmless-vs-pest life/score inversion, one latched touch id.
- `SplatterBug!GamePlayScreen.BugSelection` — weight bands.
- `SplatterBug!Bug..ctor` — per-bug size, hit points, speed, fade 5000 ms.
- `SplatterBug!GamePlayScreen.UpdateTarantulaDeath` — 10-spider burst.
- `SplatterBug!GameScreens.ResetGamePlayScreenVariables` — lives 3, score 0.
- `SplatterBug!GameScreens.SetMaxScore` / `ResetScores` — persisted best.
- `SplatterBug!GameSettings` — `settings.xml`, highScore, soundOn.
- `SplatterBug!GamePlayScreen.Draw` — HUD hearts, `+points`/`-life` text,
  escape toast over 1000 ms.
- `SplatterBug!PauseScreen` / `GameOverMenuScreen` — navigation entries.
