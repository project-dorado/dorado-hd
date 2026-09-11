# Music Quiz

- **Official package:** `MusicQuiz.exe` (musicquiz)
- **Corpus:** `Zune HD Apps (Decompiled)/musicquiz` (external, untracked)
- **Wave:** W1 · **Category:** music
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** musicquiz

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`MusicQuiz.exe` is a 29-type / 134-method quiz game (4,686 lines) built on
ZuneCoreLib/ZuneGamesLib. A round is 20 questions drawn from the user's
library in one of several formats — audio-clip name that song/album/artist,
an album-art jigsaw, "which of these doesn't belong", and a bonus album-art
round — with a per-question countdown, a limited hint, and per-mix high
scores. Difficulty selects four options (Normal) or six (Hardcore).

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `MusicQuiz.exe` | 29 | 134 | 4686 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 68 | 303 | 6258 |
| `ZuneGamesLib.dll` | 36 | 170 | 3667 |

The screen stack is `LoadingScreen` → `CoolScreen` → `MainMenuScreen` with
push/pop transitions (`SlideScreen.TransitionHelper`) to `Quiz`,
`CustomMixScreen`, `SettingsScreen`, `LearnToPlayScreen`, `AboutScreen`,
`DiscoverScreen`, `ConfirmScreen`, `PauseScreen` and `ResultsScreen`.
`Score`, `HighScores` and `SerializableDictionary` handle persistence.

## 2. Screens & navigation

- **Main menu** (`MainMenuScreen`): MegaMix, Custom Mix, high scores and
  settings; `hasXUniqueSongsWithArt` greys out entries the library cannot
  support and shows a warning when content is thin.
- **Quiz** (`Quiz`): question header, countdown, progress label, answer
  drawers, a music bar with play/pause, the hint button, the Zune marketplace
  button and the pause button. Each question type swaps the centre content:
  `ShowClip` (audio answers), `ShowOddOneOut`, `ShowPuzzle` (album art) and
  `ShowAnswerButtons`.
- **Results** (`ResultsScreen`): correct count, score, bonus points and high
  score, with `HighScoreParticles` decoration.
- **Sub-screens**: `CustomMixScreen` (artists/genres/playlists selectors),
  `SettingsScreen` (sound level, reset data, toggles), `PauseScreen`
  (resume/main menu), `ConfirmScreen` (main-menu / exit / reset / low-content
  confirmations), `LearnToPlayScreen`, `AboutScreen`, `DiscoverScreen`
  (launches a Zune Marketplace search URL).

Navigation is a stack (`ScreenStack.Push` / `PopTo`) with slide transitions;
pausing pushes a modal over the quiz and the quiz itself pauses its timers
(`Quiz.PauseQuiz` / `ResumeQuiz`).

## 3. Rules, scoring & progression

- Round shape. `Quiz` constructor sets `totalQuestions = 20` and
  `answersPerQuestion = EasyMode ? 4 : 6`; the main menu toggles `EasyMode`.
- Question selection. `NewQuestion` keeps shuffled pools of available types
  and previously used songs/albums, forbids two album-art questions in a row
  (`PreviousWasNotAlbumArt`), re-rolls when art is missing (`BadArt`) and
  falls back to a different type when the library is exhausted. Audio
  questions validate `IsContentValid`; after 25 expired songs
  `TooMuchExpiredContent` aborts the round with a message.
- Types. Clip questions (`Question.CreateClipQuestion`) cover SongName,
  AlbumName, ArtistName; odd-one-out variants cover songs, albums and album
  art; PuzzleArtistName/PuzzleAlbumName use `AlbumArtPuzzle` (jigsaw with
  `Start`, `GetHint` revealing pieces, `ShowAll` on answer).
- Timing and points. A per-question countdown starts at 15 s
  (`Timer.Create(15f, OutOfTime)`); `Quiz.Update` computes `currentPoints =
  min(10, floor(timeRemaining) + 1)` and paints the music-bar highlight with
  `timeLeft / 15`. Correct answers (`Correct`) add `currentPoints` and flash
  green; wrong answers disable the chosen button, and `OutOfTime` reveals the
  answer with an orange label before `Proceed`.
- Hint. `NewHint` plays the target song from a random offset (start for very
  short tracks, up to a random window for longer ones) and restarts the 15 s
  timer; the hint button allows 4 uses on Normal, 2 on Hardcore, then
  disables.
- Bonus round. After question 20, if enabled, `SlideOutBonusPopup` shows a
  "Get ready" banner then a 30 s `BonusQuestion` (album art), where
  `currentPoints = bonus.NumberCorrect`; `EndBonusRound` adds
  `NumberCorrect × 5` and pushes results.
- High scores. `Score` keeps separate easy/hard maxima; `HighScores.UpdateHighScore`
  files the result under MegaMix, artist, genre or playlist name;
  `SaveHighScores` writes artists/genres/playlists XML files and `Reset`
  clears all of them. `MusicQuiz.OnExiting` saves on the way out.

## 4. Controls

Everything is touch, in a 272×480 portrait frame. Tap answer buttons and menu
entries; tap/drag the music bar and hint button; pause opens a modal.
Odd-one-out and puzzle questions accept a tap on the offending piece, and
puzzle pieces can be dragged (`AlbumArtPuzzle.Update`). Album-art drawers
animate up from the bottom (`ShowCurrentDrawer`). `SettingsScreen` cycles
sound Off/Low/Medium/High. No accelerometer, no long-press.

## 5. Content inventory (must be re-authored)

79 files: 57 PNG, 17 XNB, 5 XML. Images are the quiz chrome — background,
album art placeholders, answer states (correct/wrong/wrong-correction), puzzle
piece masks, music bar frames, hint/pause/listen buttons, popups (countdown,
bonus), high-score particles, top bar and drawer parts, note effects. Sounds
are 13 XNBs (correct/wrong/select cues, countdown, banners). Text files are
localization plus about/learn-to-play copy. Question prompts are built at
runtime from library metadata, not shipped.

Re-authoring: all chrome becomes token surfaces and `Selawik` text; cues move
to `SfxBank` (`score`/`win`/`lose`/`error`/`tick`); puzzle art is cut from the
user's album art at runtime. No Microsoft PNG or string is copied, and the
marketplace `Discover` link is replaced by an in-app notice.

## 6. Implementation plan

- Engine: extend `ui/apps/QuizEngine.kt` into a full round machine —
  `object QuizEngine` + immutable `data class QuizState(difficulty, questionIndex,
  total, question, secondsLeft, points, score, correct, hintsLeft, phase)`.
  `buildQuestion` already groups by artist/album; add odd-one-out across the
  three dimensions, content-validity checks (distinct artists/albums with
  art), the 20-question pool with no repeats, timer→points conversion and the
  bonus round.
- UI: move `MusicQuizApp()` out of `MiniAppsUtilities.kt:750` into
  `ui/apps/musicquiz/MusicQuizApp.kt`; keep `DetailScaffold(title = "music
  quiz")` and add menu → quiz → results states with an animated progress bar,
  answer feedback colours from tokens, and a pause overlay. Album-art puzzle
  becomes a 3×3 `Canvas` crop of the current track's art.
- Audio: clip playback through `graph.controller.play(track, seek)` for the
  hint and audio questions; UI cues through `SfxBank` on `MiniSynth`.
- Persistence: high scores in `graph.games` (per mix name + difficulty);
  sound level, enabled mix sources and difficulty in
  `graph.appState.put("musicquiz", …)`.
- Fidelity gaps vs current code: no timer or points, no 20-question round,
  no difficulty/options count, no album-art or odd-one-out types, no hints,
  bonus round, results or high scores, and only two hard-coded question
  kinds. Also `QuizEngine` currently requires 4 tracks (should be 4/6 unique
  values per dimension).
- Tests: `app/src/test/java/com/heretek/dorado_hd/MusicQuizEngineTest.kt` —
  round length 20, option count 4 vs 6, distinct-answer invariants for every
  type, no album-art twice in a row, expired-content abort at 25, points =
  seconds remaining capped at 10, hint limit 4/2, bonus ×5, high-score
  max-per-mix round trip. Keep existing `LogicTest.kt` coverage.
- Edge cases: library too small (show `NeedMoreContent`), tracks with blank
  artist/album, albums with no art, pausing during a question, leaving the
  screen mid-round (round ends), and hint on a very short song.

## 7. Citation log

`MusicQuiz!Quiz.NewQuestion`, `MusicQuiz!Quiz.Correct`, `MusicQuiz!Quiz.OutOfTime`,
`MusicQuiz!Quiz.Proceed`, `MusicQuiz!Quiz.EndOfQuiz`, `MusicQuiz!Quiz.EndBonusRound`,
`MusicQuiz!Quiz.NewHint`, `MusicQuiz!Quiz.Update`, `MusicQuiz!Quiz.PauseQuiz`,
`MusicQuiz!Quiz.ResumeQuiz`, `MusicQuiz!Quiz.TooMuchExpiredContent`,
`MusicQuiz!Quiz.IsContentValid`, `MusicQuiz!Quiz.ShowPuzzle`,
`MusicQuiz!Quiz.ShowClip`, `MusicQuiz!Quiz.ShowOddOneOut`,
`MusicQuiz!Question.CreateClipQuestion`, `MusicQuiz!AlbumArtPuzzle.Start`,
`MusicQuiz!AlbumArtPuzzle.GetHint`, `MusicQuiz!AlbumArtPuzzle.ShowAll`,
`MusicQuiz!BonusQuestion.NewQuestion`, `MusicQuiz!Score.Set`,
`MusicQuiz!HighScores.UpdateHighScore`, `MusicQuiz!HighScores.SaveHighScores`,
`MusicQuiz!HighScores.Reset`, `MusicQuiz!HintButton`, `MusicQuiz!ResultsScreen`,
`MusicQuiz!SettingsScreen`, `MusicQuiz!MainMenuScreen.hasXUniqueSongsWithArt`,
`MusicQuiz!MusicQuiz.OnExiting`.
