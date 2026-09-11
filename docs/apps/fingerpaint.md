# Fingerpaint

- **Official package:** `Fingerpaint.exe` (fingerpaint)
- **Corpus:** `Zune HD Apps (Decompiled)/fingerpaint` (external, untracked)
- **Wave:** W5 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Fingerpaint.exe` | 73 | 404 | 8766 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 71 | 289 | 6335 |
| `ZuneGamesLib.dll` | 29 | 147 | 3096 |

## 2. Screens & navigation

- `MainMenu` (branded "Quickdraw"): New Game (Single Player / Multi
  Player), Join Game, About, Quit; joining scans for sessions with a
  spinner/refresh control in `JoinMenu`.
- `Lobby` hosts the multiplayer setup: host/joiner lists, ready state,
  connected-user notifications (`NotificationManager`), and a start
  transition. `NetworkHost`/`NetworkClient` wrap the session; the host owns
  round flow, the client mirrors it.
- In-round screens: `Canvas` (the drawing board), `EditMenu`/`ColorPicker`
  for tools, an on-screen keyboard for guesses and for typed text, a
  `TimerControl` bar across the top, and per-user `PointsNotification`
  popups.
- `HistoryView` replays past drawings as scrollable items; `GameOverScreen`
  shows total scores and the winner, with exit/rematch entries from the app
  menu.

## 3. Rules, scoring & progression + simulation model

- Quickdraw is a draw-and-guess party game. A round has one drawer and one
  or more guessers; the drawer receives a secret word from `DrawWords`, and
  guessers type answers that the drawer's device validates (case-insensitive
  match, trimmed to 100 chars).
- Round timing: `RoundTimer = 90000` ms (90 s) drawn as a thin bar. A round
  ends when every remote guesser has been marked correct (the host stops the
  timer and broadcasts the next round) or when the timer expires; the
  "time up!" state is announced.
- Scoring: a correct guess earns `round((1 - remainingFraction) * 100)`
  points, so a guess in the first instant is worth ~100 and a last-second
  guess ~0; each user can score once per round (`TotalScore.AddScore(user,
  score)`), and the guesser is added to the round's correct-guessed set.
  A wrong guess broadcasts an incorrect-answer notification to everyone.
  The default round structure is 2 rounds (`RoundInfo.TotalRounds = 2`),
  with the next drawer chosen by `GetNextDrawerId` and the category for the
  next word chosen from the word list; at the end `ShowPoints` presents the
  totals.
- Drawing model: `Canvas` holds a render target and a list of draw items
  (`FPLine`, `FPLineEntry`, `FPRectangle`, `FPEllipse`, `FPImage`, `FPText`)
  managed by `DrawItemManager`, so freehand strokes, shapes, images and text
  can be updated or replayed. Brush state includes active color, alpha
  levels and text size clamped to 2..240. The word list is a local XML tree
  of categories; five categories are named in the strings: Video Games,
  Movies, Actions, Places, Objects.

## 4. Controls

- Draw directly with a finger on the canvas; a stroke is a polyline of
  points with per-point force/width (`FPLineEntry` with point + force), so
  finger speed modulates thickness.
- Tool menus: brush selector (line, circle/ellipse, fill), `ColorPicker`,
  alpha presets (20/40/60/80/100), text size (small/large) and a text tool
  that invokes the on-screen keyboard; undo and redo operate on the
  draw-item list; an eraser removes strokes.
- Guessers submit text through the Zune on-screen keyboard; the keyboard
  layer is shared with the main menu and the text tool. In multiplayer,
  answer packets are sent between devices; the app also exposes help
  overlays for the keyboard and eraser (first-run hints).

## 5. Content inventory (must be re-authored)

25 files: 17 XNB UI textures (`main_01..04` title art, `main_title`/`2`,
`backGrid`, `circle`, `pixel`, buttons, spinner, `refresh_black`), one PNG
`menuIcons.png` (222x110 sprite sheet) plus its XNB, one font XNB, three
TXT layout lists (`menuIcons.txt`, `LobbyBtn.txt`, `connectBtn.txt`), the
`DrawWords.myxml` word bank, and the standard `Fonts.xml`, `Text.xml`,
`Text/Strings/en.xml`. Re-author: UI background art, a new word bank with
our own categories (keep the 5-category shape), and synthesized UI SFX.

## 6. Implementation plan

- Rendering: Compose `Canvas` inside `DetailScaffold` — strokes stored as
  typed segments (line/ellipse/rect/text/image), replayable and undoable;
  the canvas is a bitmap/`ImageBitmap` layer for cheap redraw.
- Logic: `ui/apps/games/Fingerpaint/` — `RoundEngine` (word selection,
  90 s timer, per-user scores), `WordBank` (our own categories/words),
  `DrawItem` model and history.
- Multiplayer: the original used XNA network sessions which cannot be
  reproduced; target single-device pass-and-play first, with optional
  LAN/hot-seat transport behind an interface. If cloud play is wanted
  later, `dorado-cloud` can carry the small answer packets.
- UI: game-over and lobby lists from tokens; guesses via the app's existing
  keyboard patterns; `SfxBank` for correct/wrong/round cues.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): timer-to-score math,
  one-score-per-user rule, round advancement, word bank parse/category
  selection, draw-item undo/redo, guess normalization.
- Fidelity target: faithful homage — exact rules and scoring on one device,
  original network play out of scope. Risks: canvas performance with long
  strokes and the loss of the original online session model.

## 7. Citation log

- `Fingerpaint.exe!RoundInfo` (TotalRounds/Reset), `.TotalScore.AddScore`
- `Fingerpaint.exe!Lobby` (`RoundTimer = 90000`, `TimerControl`,
  `TimerExpired`, `RemoteGuessRecieved`, `BroadcastRoundInfo`,
  `IsGuessCorrect`), `.ShowPoints`
- `Fingerpaint.exe!Canvas` (draw-item manager, brushes, text size clamp,
  colors), `Fingerpaint.exe!DrawItemManager`,
  `Fingerpaint.exe!FPLineEntry`
- `Fingerpaint.exe!DrawWords` (`Content\DrawWords.myxml`,
  `GetRandomWord`, `RandomizeList`)
- `Fingerpaint.exe!MainMenu`, `.JoinMenu`, `.Lobby`,
  `.GameOverScreen`, `.HistoryView`
- `Fingerpaint.exe!NetworkHost`, `.NetworkClient`,
  `.FingerpaintDataSender` (`SendAnswerGuessToDrawer`, `SendRoundInfo`,
  `BroadcastIncorrectAnswer`, `GetNextDrawerId`)
- `Fingerpaint.exe!TimerControl` (duration, completion event)
