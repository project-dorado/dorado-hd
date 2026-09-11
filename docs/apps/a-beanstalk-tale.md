# A Beanstalk Tale

- **Official package:** `BeanstalkTale.exe` (beanstalktale)
- **Corpus:** `Zune HD Apps (Decompiled)/beanstalktale` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `BeanstalkTale.exe` | 73 | 352 | 12062 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |

- 480×272 back buffer; the game world is authored 272-wide portrait and the
  device presents it rotated, with off-screen guards for a wider viewport
  (`WinPhoneAddX/Y`).
- Pauses inside the app when deactivated (sound suspended, screens frozen);
  resumes on activation. Player profile, high scores and settings are restored
  at startup and written on change.

## 2. Screens & navigation

XNA GameScreen stack with four states (transition-on, active, transition-off,
hidden) and push/pop routing through `ScreenManager`.

- Logo → loading → main menu.
- Main menu entries: play, options, help, high scores, statistics, trophy room,
  fight statistics and credits (plus an exit confirmation message box).
- Gameplay pushes a pause menu (resume, retry, main menu, options); death opens
  the same pause overlay in game-over mode with the final score, and a new-best
  flag drives the high-score entry prompt.
- High-score sheet, statistics (lifetime counters), fight statistics (per enemy
  kill/fired/hit tallies) and the trophy/cup cabinet are separate screens.
- Help is a multi-page picture/text tutorial; options expose music and sound
  volumes plus an unused "ungulate" joke selection.

## 3. Rules, scoring & progression

Vertical endless climber up a snake-stalk. The world grows upward; camera pan
follows the pig whenever it rises (`newOffsetY = −pigY + 385`).

- **Platforms are leaves.** The next leaf is placed when the highest leaf nears
  the screen top: x = 10–252, y = highest − (20–100) px. Kinds: normal, dry
  (cracks/breaks), jumppad, horizontal/vertical/circular movers and boss-fight
  ground; 10 % of eligible placements also spawn a pickup.
- **Physics.** Gravity (0, 0.001)·ms; horizontal speed comes from tilt and is
  damped; jump −0.5, jumppad −0.9, reduced hop −0.4; ground y = 499. Tilt
  clamps ±1 (±0.5 on a balloon).
- **Score.** `score = maxHeight + bonus`: height above the start line plus food
  pickups worth 500 + 50·pattern (16 patterns → 500–1250). No timer.
- **Pickups** (per leaf roll): food adds bonus + floating "+N"; shield = 40×40
  pickup for 10 s invulnerability (0.2 s blink); balloon = 5 s steady lift at
  −0.2 in blue/red/yellow ending in a particle pop (suppressed near bosses).
- **Specials** every 1500 px: a 10-leaf dry-leaf run, a 5-jumppad high-jump
  area (first 50 px up, rest ~300 px), a basic enemy encounter, or a 10-platform
  horizontal-mover run. Bosses every 8000 px: six boss-ground leaves, then frog,
  beehive or spider boss (random order; each counts toward the cabinet).
- **Enemies.** Basic tier: wasp, spider (2 scales), bug (3 skins), treant,
  gazer, mushroom man and a bug swarm from the stalk. Contact kills the pig;
  arrows kill any non-boss on hit and each death type increments its counter;
  landing on enemies scores jump-kills. Bosses alone survive arrow hits.
- **Arrows.** A 0.2 s bow cooldown fires left/up/right at 400 px/s with a 4×4
  box; leaving the field counts a miss, as does dying with arrows in flight.
- **Run end.** Falling > 520 px below the camera kills the pig; at 720 px the
  game-over pause opens and the score is checked against the table. A 3-2-1-Go
  countdown precedes each run.
- **Progression** is the cup system, not levels: ten materials (wooden →
  diamond) across counters such as height (10k–5M px), jumps (100–25k), flower
  jumps (50–1k), arrows fired/hit/missed, balloon rides, shields, starts and
  per-family kills; new thresholds queue a banner + cup for 5 s.
- **Persistence.** `profile.dat` stores counters/cup flags in binary;
  `highscore.dat` holds the ranked score/name table.

## 4. Controls

- **Tilt** steers horizontally (`accelX × 1.2`, clamped; × 0.5 on balloon).
- **Tap** anywhere while standing on the start ground triggers a jump; tap
  selects and fires an arrow when the pig is off the ground and above y = 300:
  touch x < pigX − 30 → left, > pigX + 30 → right, otherwise straight up, with
  the arrow angled toward the touch point.
- **Pause** button (50×50, bottom-left of the portrait frame) opens the pause
  screen; touch state must be pressed (not moved) to arm it.
- Before the run, any released tap starts the game and the countdown.
- No hardware-button gameplay mapping; Back is reserved for app navigation.

## 5. Content inventory (must be re-authored)

93 `.xnb` assets: ~35 UI/background atlases and sprites, 10 fonts, 32 sounds,
1 music track, plus animation sheets.

- Player sheets (walk down/left, jump, shoot, balloon ride), arrow, food atlas
  (16 patterns), 3 balloons, shield/upgrade FX, particle atlas.
- Enemies: wasp, throwing wasp, spiders (2 scales), 3 bug skins, treant, gazer,
  mushroom man, frog boss + tongue, beehive boss, spider boss + legs + web,
  bug-swarm entrant.
- World: sky tiles, ground, house, stalk bottom/mid tiles, leaf platforms
  (normal, dry/breakable, jumppad, mover variants), decorations.
- UI: logo/splash/loading/credits/high-score/statistics/trophy backgrounds,
  title art, big/small buttons, option branch/slider, pause button, cup sheet
  (10 materials × 5 tiers) and jump-cup sheet.
- Audio: bow, low/high jump, food pickup, shield pickup, balloon pop, break
  leaf, spike, beak/hit/death growls, frog, beetle, spider + boss retreat,
  wasps, bee attack, boss arrival, trophy fanfare, click, plus main-menu and
  in-game music loops.

Re-author: vector/Canvas art for pig, enemies and bosses; three parallax sky
layers; procedural leaf/platform tiles; token fonts; synthesize all SFX and two
short chiptune loops. No asset bytes are reused.

## 6. Implementation plan

- Engine: `ui/apps/games/beanstalk/BeanstalkEngine.kt` — pure Kotlin `object`
  with immutable world state; fixed 60 Hz `step(dt)`; deterministic seeded RNG
  for leaf placement, pickups, enemy/special scheduling.
  ```kotlin
  object BeanstalkEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Screen: `BeanstalkScreen.kt` inside `DetailScaffold`, portrait `Canvas`;
  parallax sky, tiled stalk, leaves, actors, HUD score/cups from tokens only;
  zero corner radius throughout.
- Inputs: accelerometer tilt via sensor stream, tap-to-jump/shoot via
  `pointerInput`, pause hit target; on-screen countdown.
- Persistence: lifetime counters + cup flags through `graph.appState`; best
  score and score table via `graph.games`; SFX via `SfxBank` (bow, jump,
  pickup, break, hit, boss roars, fanfare) and two looping music cues.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): gravity/jump integration
  vs recorded values, leaf placement bounds, special-area cadence (1500 px),
  boss cadence (8000 px), pickup roll distribution and value math, arrow
  hit/miss accounting, cup threshold awarding, high-score update.

## 7. Citation log

- `BeanstalkTale.exe!DiNoGames.DiNoGame` — 480×272 back buffer, logo screen,
  deactivate/activate pause handling.
- `BeanstalkTale.exe!DiNoGames.GameplayScreen.Update` / `HandleInput` /
  `UpdateAllGameElements` / `AddSpecialArea` / `AddPickup` — camera, countdown,
  death/game-over 520/720, controls, leaf spawn, 1500/8000 cadences, pickup
  rolls.
- `BeanstalkTale.exe!DiNoGames.Player.Update` / `Jump` / `GetBalloon` /
  `Invulnerable` / `Die` / `Shoot` — gravity/damping/score, impulses, 5 s
  balloon, 10 s shield, 0.2 s bow cooldown.
- `BeanstalkTale.exe!DiNoGames.Pickup.TakeMe` / `Arrow.Update` /
  `CheckCollision` / `Missed` — food 500+50·pattern, speed 400, hit/miss
  counters, boss exception.
- `BeanstalkTale.exe!DiNoGames.PlayerProfile` / `PlatformKind` / `SpecialType`
  — counters/thresholds, cup materials, taxonomies.
- `BeanstalkTale.exe!DiNoGames.ScreenManager` / `ScreenState` /
  `MainMenuScreen` / `PauseMenuScreen` / `HighscoreScreen` /
  `StatisticsScreen` / `TrophyScreen` — navigation, lifecycle, persistence
  and the high-score check.
