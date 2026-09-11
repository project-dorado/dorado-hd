# Goo Splat

- **Official package:** `GooSplat.exe` (Goo Splat)
- **Corpus:** `goosplat` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `GooSplat.exe` | 34 | 129 | 3,537 |
| `Microsoft.Xna.Zune.dll` | dep | 825 | 15,694 |
| `ZuneGamesLib.dll` | dep | 402 | 6,409 |

Portrait 272x480 canvas. One horizontal spawn band above a rising "sludge"
pool; a top score bar and a bottom-right pause button. XNA content pipeline
(`.xnb` audio, `.png` textures, XML text tables); the app deserializes its
save data from XML (`GameProgress.xml`, `Stats.xml`).

## 2. Screens & navigation

- **Menu** — logo over stomach background; `Resume` (slides up into the paused
  game), `Play` (starts a fresh run), volume toggle (Off/Low/Medium/High),
  `High Score`, `About`. Jelly-shaped help buttons under the title show
  picture bubbles explaining each colour.
- **Game** — top bar prints `Score`, `Level`, `High Score`; bottom-right pause.
  Touching the pause button slides the top bar down and pops back to the menu.
  App deactivation pauses automatically.
- **Game over** — slides a `Breakdown` panel over the game screen: `Level N`,
  `Score N`, then five counters (green, red, blue, yellow, peppers) each under
  a jelly icon, and a `MainMenu` button that pops both screens.
- **High Score / About / overwrite-save confirmation** — standard pushed
  screens; the confirmation guards starting `Play` when a run is in progress.

## 3. Rules, scoring & progression

- Jellies spawn in batches on a timer. Tick length starts at **1.25 s** and
  shrinks by **0.015 s** per level. Each tick adds `rand(1..N)` jellies:
  N=3 below level 4, N=4 for levels 4–8, N=5 above 8. Spawn X is 35–237,
  spawn Y is −20 up to `sludge.Level − 40`; a candidate position is retried
  (max 60 tries) until it is clear of every jelly within 20+radius px.
- Each spawn is first a shrinking **shadow marker** (2.5 → 0.5 over ~0.4 s)
  that plays a random hit-wall cue and then materialises as a small green
  jelly (descent speed 25–40 × `max(1, level/5.5)`) or the rolled power-up.
- Jelly radius is **30 px**. Life cycle: `HitWall` (0.1 s) → `Stall`
  (`0.5 − 0.03·level` s) → `Slide`; a jelly that falls (electric effect or
  shock) accelerates at **700 px/s²** and doubles its score value. Frozen
  jellies thaw after **5 s**; squished jellies fade for **1 s** then vanish.
- **Sludge**: rises toward `targetLevel` at 20 px/s. It starts the run at
  level 370 and levels up +15 (cap 420). A non-power-up jelly absorbed at the
  sludge line lowers `targetLevel` by `radius/1.75` (≈17.1). The run ends as
  soon as the sludge surface climbs above **level 70**.
- **Scoring**: small green jelly **1**, large green **5**, power-up **3**, all
  **doubled while falling**. Explosions/freezes add the power-up's own value
  plus every nearby jelly's value. Tapping a bomb detonates it; tapping ice
  freezes all jellies (already-frozen ones shatter for ice-shatter points);
  tapping electricity drops every other jelly into a falling state (making
  them all worth double if then caught).
- **Pepper** (rare bonus): when the sludge is below 280 and no pepper is on
  screen, a **1-in-500 per frame** roll spawns one from a screen edge at
  `(level+70)` px/s. Tapping it gives 3 points plus a full-board explosion,
  launches the sludge's fire animation and raises `targetLevel` by 50.
- **Two small greens merge**: when two unpaired small jellies on the floor
  come within one radius they attract at 200 px/s and combine into a large
  green with averaged speed ×1.5 and a 0.05 s stall.
- **Level up**: while `Score ≥ ScoreTarget`. On level-up the level increments,
  timer shortens, sludge target rises 15, and the target grows by an
  accelerating step: increase starts at 5 and gains `level·2` each level, so
  the first four targets are 5, 14, 29, 52.
- **Progression** is endless; `GameProgress` persists run level, score, all
  live jellies (by XML type), sludge/pepper state and per-colour squish
  counters, so a menu `Resume` restores mid-run. `Stats` persists lifetime
  high level/score and per-colour totals; passing the high score plays a
  level-up jingle.

## 4. Controls

- **Single-finger tap per jelly.** Every new touch-down is matched against
  jellies from the topmost down; hit test is `distance < radius` (+10 px for
  small jellies and for falling jellies). A consumed touch is removed from
  the list, so **multitouch** splats several jellies in one gesture burst.
- Frozen jelly: first tap cracks (`CrackFrozen`), second shatters
  (`ShatterFrozen`) and awards score.
- No tilt, no drag; jellies walk/slide on their own.

## 5. Content inventory (must be re-authored)

112 content files: 86 PNG + 22 XNB + 4 XML.
- `Textures/JellySmall` (15), `JellyLarge` (9), `JellyBomb/Ice/Electric` (5/4/6),
  `Sludge` (17: four strip anims + floaties), `pepper` (3), `IceShatter` (11
  shards), `UI` (14: buttons, logo, popup, scorebar, stomach background),
  `blank.png`, `circle.png` (shadow marker).
- Audio cues: hit-wall 1–5, jelly fall 1–2, single-splat 1–2, crack/shatter
  ice, touch red/blue/yellow, sludge happy/unhappy/burp, level-up, game over,
  intro flyby, menu down.
- Text tables `en/es/fr` and `Fonts.xml`; re-author strings and use Dorado
  tokens for layout.

## 6. Implementation plan

- Engine: `ui/apps/games/GooSplat.kt` — `object GooSplatEngine` + immutable
  `GooSplatState(level, score, scoreTarget, jellies, sludgeLevel, …)` with a
  deterministic `step(dt: Float): GooSplatState`. Jelly is a sealed data type
  (`Small`, `Large`, `PowerUp(Kind)`) plus a `JellyState` enum mirroring the
  reference state machine; spawn, absorb, merge, pepper and level-up live in
  pure helpers.
- Screen: `GooSplatScreen` inside `DetailScaffold("goo splat")`, Canvas draw
  of circles/rounded-free blobs (radius zero), HUD row for score/level/best,
  pause + game-over overlays; touch routed per-pointer (multi-touch list) to
  the topmost jelly hit.
- Persistence/SFX: `graph.appState.put("goo-splat", json)` for the saved run,
  `graph.games.record("goo-splat", score, "L$level")` for lifetime best;
  `SfxBank` cues for splat/crack/freeze/shock/level-up/game-over.
- Tests (`app/src/test/java/com/heretek/dorado_hd/GooSplatEngineTest.kt`):
  spawn-batch sizes vs level; absorb lowers target by radius/1.75; falling
  doubles score; freeze-then-bomb shatters; merge makes large green; pepper
  applies +50; game over at level < 70; deterministic step under fixed seed.

## 7. Citation log

- `GooSplat!Jelly` — radius, stall time, state machine, gravity 700, thaw 5 s.
- `GooSplat!Jelly.TestInput` / `GooSplat!Jelly.OnTouched` — hit padding, frozen
  two-tap, squish.
- `GooSplat!GameScreen..ctor` / `GooSplat!GameScreen.ConfigureCurrentGame` —
  timer 1.25 s, spawn batch formula, pause wiring, save restore.
- `GooSplat!GameScreen.UpdateComponent` — pepper odds 500 / speed level+70 /
  fail line 70 / score target check.
- `GooSplat!GameScreen.AddJelly` — spawn position ranges and retry rule.
- `GooSplat!GameScreen.LevelUp` — target increase formula, sludge +15, timer.
- `GooSplat!GameScreen.ExplodeBomb` / `ExplodeJellies` / `FreezeJellies` /
  `ShockJellies` — per-power-up resolution and score values.
- `GooSplat!GameScreen.HandleJellyInput` / `UpdateJellies` — multitouch
  matching, absorb, small-green merge.
- `GooSplat!SmallGreenJelly.Update` / `GooSplat!PowerUpJelly.Update` — merge
  attraction 200, large-green speed, die timers 20/12 s.
- `GooSplat!ShadowOfJelly.Update` — shadow shrink 2.5→0.5, hit-wall cue,
  materialise into small green / power-up.
- `GooSplat!Sludge` — bottom level 420, default speed 20, state timing.
- `GooSplat!Pepper.UpdateComponent` — tap radius, +50, fire state.
- `GooSplat!GameProgress` / `GooSplat!Stats` — persisted run and lifetime data.
- `GooSplat!JellyScoreComponent` — per-colour breakdown counters.
- `GooSplat!TopBar` — HUD score/level/best columns.
