# Tug-O-War

- **Official package:** `Tuginator.exe` (Tug-O-War)
- **Corpus:** `tugowar` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Tuginator.exe` | 16 | 52 | 1,722 |
| `2moroGames.dll` | dep | 421 | 6,510 |

Portrait 272x480. A rhythm tug-of-war: each timed pull raises your side's
velocity, mistimed pulls lose it, and the rope drifts toward whoever pulls
faster. Single player faces an AI; multiplayer is hot-seat (upper/lower half
of the screen, or left/right for drag mode). Input mode can be Tap, Drag or
Shake. The shared `2moroGames` library supplies menus, touch handling and
sprite-sheet rendering. No save file was observed; results are per-session.

## 2. Screens & navigation

- **Splash → Main Menu**: `SINGLE PLAYER`, `MULTIPLAYER`, `ABOUT`.
- **Mode select**: choose `TAP`, `DRAG` or `SHAKE` input, plus a `HOTSEAT`
  toggle for multiplayer, then `NEW GAME`.
- **Gameplay**: rope and two teams drawn on the grass background, a
  fingerprint/input hint per player, input icons on the correct half for the
  chosen mode, a 5-frame countdown, and a pause key (`game_paused` page).
- **Pause**: resume, change input mode, return to menu.
- **Results**: after each round a page shows the round scores and win/loss
  tabs; the final page shows the match result — single player either a win or
  a lose banner, multiplayer a red or blue score tab with the player and
  opponent tallies.

## 3. Rules & scoring

- **Round structure**: **10 rounds** (`INT_NUMBER_OF_LEVELS = 10`).
  - Single player: beat the AI to take the round; each win raises the AI pull
    per frame by 0.05 (starts at **0.05**), and a loss resets the difficulty to
    0.05 and the player score to 0 — the single-player run is a clean streak.
  - Multiplayer (hot-seat): both sides are human; the winner of a round gets a
    point; the first to 10 (all 10 rounds played) with the higher tally wins
    the match.
- **Input velocity is the whole game.** Each input has a velocity, in abstract
  units:
  - `Pull(amount)` adds `amount` on the first pull; the second pull establishes
    the rhythm interval; every later pull adds `amount` only when it lands
    within **±0.4 s** of the previous interval, otherwise it subtracts
    `2·amount` (clamped at 0).
  - If no pull occurs for **0.5 s**, velocity decays by **1 per second**.
- **Input amounts**: a tap / drag / shake pull is worth **2** units; the
  single-player AI applies `difficulty` units every frame while the match is
  live, so the player must out-pull a steady 0.05→… AI.
- **Rope**: the rope starts at y = **−360**; each rendered frame the rope moves
  by `(playerVelocity − aiVelocity) · dt` px/s. The player wins the round when
  the rope reaches **−222** (138 px of travel); the opponent wins at **−498**
  (−138 px). The rope sprite has tiers for a velocity difference of >10, >20
  and >30 in either direction, plus a small ±4 px horizontal wobble.
- **Round resolution**: `ProcessEndGame` stops the scene, records the round
  result for both sides, and advances the round counter. In single player a
  win raises difficulty; in multiplayer both tallies persist. After round 10
  the results page is shown.
- **Shake feedback**: in Shake mode the whole screen is tinted — blue while the
  rope is above centre, red below — with alpha proportional to displacement
  (capped at 180).

## 4. Controls

- **Tap mode** — the player taps the lower strip (y > 378); in hot-seat the
  second player taps the upper strip (y < 102). Each qualifying press pulls.
- **Drag mode** — drag **downward** by more than **50 px** in the right half
  (x > 170) to pull; the second player drags downward in the left half
  (x < 102).
- **Shake mode** — shake the device; the change between consecutive
  accelerometer samples (|Δx|+|Δy|+|Δz|) must exceed **0.55** to register a
  pull, and the pull amount is applied from the render loop.
- Menus are tap targets. Multiplayer uses the same mechanics on opposite
  halves; single-player AI needs no input.

## 5. Content inventory (must be re-authored)

74 XNB files:
- `Images/Game` (40): rope centre/up/down pieces with shadow variants, team art,
  score tabs, results banners, fingerprint highlights, countdown frames
  (`go`, `1`, `2`, `3`), menu/continue/gameover text art.
- `Images/Input` (12): tap/drag/shake hint icons plus highlighted variants
  (`tap_1..3`, `drag_1..3`, `shake_1..2`, box).
- `Images/Menu` (13): main buttons 1–3, rope, back, arrows, inputmode,
  hotseat, select_mode, blank, resume, game_paused.
- `Images/Backgrounds` (4): main, splash, grass, blank.
- Fonts (4): MenuFont, RomanFont, RomanSmallFont, RomanLargeFont.
- Audio: `click` only (crowd/rope cues are absent in the package).
- No level or high-score data files; no save schema observed.

## 6. Implementation plan

- Engine: `ui/apps/games/TugOWar.kt` — `object TugOWarEngine` with immutable
  `TugState(round, playerVel, aiVel, ropeY, playerScore, aiScore, difficulty,
  mode)` and a deterministic `step(dt)`; `pull(side, nowMs)` implements the
  rhythm window (±0.4 s) and miss penalty; decay 1/s after 0.5 s.
- Screen: `TugOWarScreen` in `DetailScaffold("tug-o-war")`, Canvas rope/teams
  on a token-coloured ground, velocity-tier rope art, countdown, score tabs,
  pause/mode-select/results overlays.
- Inputs: tap/drag via Compose pointer events; shake via `AppSensors`
  accelerometer (delta threshold 0.55); multiplayer splits the screen.
- Persistence/SFX: no save needed beyond optional
  `graph.games.record("tug-o-war", score, "ai|hotseat")`; `SfxBank` click on
  pull and countdown.
- Tests (`app/src/test/java/com/heretek/dorado_hd/TugOWarEngineTest.kt`):
  rhythm window acceptance/rejection and 2× penalty; 0.5 s idle decay; rope
  win/lose thresholds −222/−498; 10-round structure; AI difficulty +0.05 per
  win and reset on loss; tap/drag/shake zone rules; single-player score reset.

## 7. Citation log

- `Tuginator!GamePlay.Update` / `SetWinner` — round loop, AI pull, score
  bookkeeping.
- `Tuginator!Input.Pull` / `Update` — first/second pull, ±0.4 s window,
  2× miss penalty, 0.5 s decay.
- `Tuginator!Input` constants — `TIME_TOLERANCE = 0.4`, `NO_INPUT_TIME = 0.5`,
  `INT_NUMBER_OF_LEVELS = 10`.
- `Tuginator!RopeMenuItem.RespondToTouches` — y>378 / y<102 tap zones, >50 px
  drag, left/right halves.
- `Tuginator!RopeMenuItem.UpdateInput` — shake delta > 0.55, pull amount.
- `Tuginator!RopeMenuItem.Render` — rope start −360, threshold −222/−498,
  velocity tiers 10/20/30, ±4 px wobble, red/blue screen tint.
- `Tuginator!RopeMenuItem.ProcessEndGame` — result pages, difficulty ramp,
  single-player reset, multiplayer tabs.
- `Tuginator!GamePlay.ResetGame` — difficulty 0.05, scores 0.
- `Tuginator!MenuInitialiser` — mode select, hotseat, pause, results wiring.
