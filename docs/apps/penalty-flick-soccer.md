# Penalty! Flick Soccer

- **Official package:** `Penalty.exe` (penalty)
- **Corpus:** `Zune HD Apps (Decompiled)/penalty` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Penalty.exe` | 32 | 173 | 19599 |
| `2moroGames.dll` | 92 | 409 | 6049 |

- 2moroGames engine: fixed 60 Hz, 480×272 back buffer presented as a 272×480
  portrait render target, portrait touch handler, settings in `GameSettings.xml`
  (`Penalty` container). A pseudo-3D renderer projects the pitch and characters
  with per-mode cameras and vanishing points.
- Music/crowd loops pause when the MP3 player is playing; state save is
  requested on every scoring change.

## 2. Screens & navigation

Page enum: main, splash, options, gameplay, leaderboard, country select, new/continue, challenge
select, training menu, post-training, paused, post challenge, trophy, about.

- Splash → main menu (World Game, Challenge, Training, Options, About) with a
  sliding sidebar. World Game runs the country select (32 nations with their
  own keeper/skin colors) → bracket → match; Challenge lists Mad Minute and
  Goal Streak with their high scores; Training has the tutorial and Practice
  with a post-training menu.
- Match HUD: score, high score, per-side kick-result strips, countdown digits,
  result banners, pause (resume/new game); post-match win/game-over, plus a
  trophy page for tournament victory.

## 3. Rules, scoring & progression

Penalty shoot-out football with three distinct rule sets.

- **Match structure (World Game).** A 32-nation knockout: the player picks a
  team, the bracket seeds 8 first-round matches (15 matches over rounds
  8→4→2→1), and the player's fixture is random among the 8. Each match
  alternates roles every kick — swipe to shoot, hold a zone to defend — until
  both sides have taken 5 kicks; higher score wins and a tie enters sudden
  death (extra paired kicks decided by first differential). Wins advance the
  slot, other results are simulated. Rounds are group, quarter, semi, final and
  grand final; the last win shows the trophy. Per-side result strips record
  every kick as Goal/Miss/Save, and a loss resets the tournament.
- **Goal geometry.** Posts x ±3.5 m, crossbar 2.2 m, goal line 11 m from the
  spot, net depth 1.8 m (3 m in SD stages 1–2, 2 m in 3–4). Post bounce 0.6,
  net 0.1, ground 0.4.
- **Flick physics.** Swipe speed = recent movement length ×30, clamped 350–750,
  × stage factor (1.0; 1.3 in SD2). Direction projects through camera-relative
  basis vectors into a 3D kick force. Arc spin is the cross of swipe direction
  against the running velocity average, clamped ±0.5, applied as a Magnus-style
  force in flight. The ball is a 3D point mass with gravity, ground/keeper/
  post/net collisions and a stop threshold; up to 3 balls are live.
- **Keeping.** Holding the lower screen maps to dive high/low left or right, or
  jump up (centre holds commit less). Keeper body 0.5×0.75×0.5 m, reach 0.6 m;
  dives 3.5 m high / 3 m low, steps 1 m. Reaction time and random-dive odds
  ramp by stage (0.5/0.4/0.3/0.2/0/0.2 s and matching probabilities); the AI
  keeper estimates whether the ball reaches goal before reacting.
- **Sudden death + Goal Streak.** SD stages 1–4 use distinct cameras, shot
  spots and goal depths; goals keep a streak alive, a miss resets it, and the
  best streak persists.
- **Mad Minute.** 60 s after a 3-2-1 countdown, unlimited kicks with pitch
  powerups: Time Freeze, Points Bonus and Ball Reset Speedup; Ball Frenzy adds
  balls (cap 3) and vuvuzelas bounce the ball. Ends at zero and records the
  best score.
- **Progression.** Mid-bracket progress (teams, round, opponent, per-side
  scores) saves so Continue resumes exactly; Mad Minute and Goal Streak high
  scores persist. Options has sound and Reset Scores (confirm popup).
- **Tutorial.** Three gated lessons: direct flick, curved (spin) shot, then
  defending as keeper.

## 4. Controls

- **Shooting** (kicking states): swipe anywhere; direction and force come from
  the swipe, arc gives curve. Tutorial messaging sets the expectation
  ("swipe to direct", "add spin by swiping in an arc").
- **Defending** (keeping states): hold a screen zone — left third dives left,
  right third dives right, upper zone goes high, center holds jump/step with
  reduced commitment.
- **Menus**: tap sidebar buttons, country list scroll/drag, leaderboard scroll
  with smoothing; result banners can be tapped (or auto-advance after a 150/
  120-tick delay) to continue.
- Portrait touch input only; no accelerometer. The engine's TouchHandler
  exposes tap and swipe details plus current positions, which the game reads
  directly.

## 5. Content inventory (must be re-authored)

133 files: 131 `.xnb` + 2 `.wma` music tracks.

- **Character/ball atlases:** three ~4 MB keeper sheets (front, front variant,
  rear) covering idle/block/dive/get-up; ball sheet (~1 MB) + shadow.
- **Pitches and goals:** base field + keeper field + four SD fields; five goal
  variants (open, keeper view, SD1–4); billboard, crowd strip, kicker backdrop.
- **Props/HUD:** vuvuzela sheet, two powerup sheets, result banners
  (goal/miss/save/SD/win/game-over), Ball Frenzy/Time Freeze text, round
  banners, digits 0–9, hit/miss buttons, kick-result frames, tutorial cards.
- **Menus:** splash/menu/about/trophy/country-select art, 32 flag frames,
  list/leaderboard chrome, buttons/popups, per-entry sidebar states.
- **Audio (30 cues + 2 loops):** menu touch/sidebar/swish/country scroll, four
  bounces, keeper/net/crossbar hits, vuvuzela, powerups, four kick whooshes,
  body fall, counter beep, crowd loops, goal-scored ×3, goal-saved, plus
  `music` and `crowd_ambient`.

Re-author: Canvas 2.5D keeper/ball sprites, procedural pitch/goal backdrops,
our own 32 flag glyphs, token HUD, synthesized SFX and two loops.

## 6. Implementation plan

- Engine: `ui/apps/games/penaltyflick/PenaltyEngine.kt` — pure Kotlin `object`
  over immutable `State` (match/bracket, stage, ball list, keeper, powerups,
  timers, rng); fixed `step(dt)` at 60 Hz with vector/3D mass math ported
  behaviourally (gravity, spin force, bounce efficiencies as plain numbers).
  ```kotlin
  object PenaltyEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Screen: `PenaltyScreen.kt` in `DetailScaffold`, portrait `Canvas` with a
  lightweight pseudo-3D projection (pitch trapezoid, goal frame, sprite scale
  by depth); HUD banners, kick-result strips, bracket and menus token-styled,
  zero corner radius.
- Inputs: swipe vector from `pointerInput` drag history → kick force/spin;
  hold-zone detection for keeper dives; tap banners to continue.
- Persistence: bracket/save data and high scores via `graph.appState` +
  `graph.games`; SFX and two music loops via `SfxBank`.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): swipe→force clamp and
  spin math, ball flight/bounce/stop, goal-box detection, keeper dive zones
  and reaction probabilities, five-kick + sudden-death resolution, Mad Minute
  timer/powerups, bracket seeding and progression, save/continue round-trip.

## 7. Citation log

- `Penalty.exe!Penalty.Constants.PageIDs` / `SoundIDs` — screen and cue
  taxonomies.
- `Penalty.exe!Penalty.GamePlay` — goal dimensions, bounces, tick constants,
  mad-minute time, 3-ball cap, stage cameras/kick spots, keeper reaction
  arrays; swipe dispatch and stage force multipliers.
- `Penalty.exe!Penalty.Ball.Kick` / `Ball.Update` — swipe ×30 clamp, direction
  basis, spin ±0.5, 3D integration and stop threshold.
- `Penalty.exe!Penalty.Goalkeeper` / `UpdateFromInput` — body/reach/dive dims,
  ball estimation, hold-zone dive/jump mapping.
- `Penalty.exe!Penalty.GamePlay.UpdateGameState...` /
  `LeaderboardDisplay` — five-kick rounds, result strips, sudden-death
  progression, 32-team 8/4/2/1 bracket with random player fixture.
- `Penalty.exe!Penalty.Powerup.HitEffect` / `MatchType` — powerup and round
  taxonomies.
- `Penalty.exe!Penalty.GameSettings.SaveGameData` / `CountryManager` —
  persistence fields and the 32 nations.
- `Penalty.exe!Penalty.MenuInitialiser` — tutorial directives and HUD wiring.
- `2moroGames.dll!_2moroGames.Touch.TouchHandler` — tap/swipe/position inputs
  and move-velocity average used for spin.
