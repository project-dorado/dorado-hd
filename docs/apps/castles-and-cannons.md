# Castles and Cannons

- **Official package:** `CastlesAndCannons.exe` (castlesandcannons)
- **Corpus:** `Zune HD Apps (Decompiled)/castlesandcannons` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `CastlesAndCannons.exe` | 59 | 280 | 8509 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 67 | 304 | 6297 |
| `ZuneGamesLib.dll` | 36 | 170 | 3669 |

- Internal title is "Base Defender"; landscape-right orientation, single touch,
  ZuneGamesLib screen stack with shutter/slide transitions.
- `progress.xml` stores level scores, unlock count and skill levels; level
  stats come from `Data/levels.xml` (16 entries) and a base `Data/player.xml`.

## 2. Screens & navigation

- Loading → main menu (Play, Learn to play, Options, Reset, About) → level
  select.
- Level select map shows 16 numbered nodes: locked, completed (silver) and
  gold (score ≥ 1000) states, plus the level's best score.
- In game: battlefield with a camera that scrolls 0–480 px, castle health bars
  for both sides, unit buy bar (soldier/archer/knight/bomber + heal), gold and
  score labels, boss-alert banner, pause.
- Pause overlays: resume, options, leave-level confirmation; time and level
  number shown. On leave, progress is not counted.
- End level: score breakdown (time, offense, defense), retry/continue;
  WinScreen when all 16 levels are completed. SkillScreen spends earned
  upgrade points; Options toggles volume; Reset wipes progress after
  confirmation.

## 3. Rules, scoring & progression

A one-lane castle siege: both sides spawn unit armies that march across the
field, and the player fires an arcing cannon to wipe them out before they
reach your castle.

- **Battlefield.** Your castle left, the enemy's right; 16 levels over six
  backdrops (hills 1–3, plains 4–6, desert 7–9, waterfall 10–12, mountain
  13–15, volcano 16). Five levels inject a boss.
- **Castle.** Each side's max health comes from level stats; a unit reaching
  the enemy wall attacks for its castle damage until killed. Health drives the
  defence score; one boss level is a multi-stage final encounter.
- **Gold.** Starts per-level, +1 every `GoldPerSecond`, +1 per kill
  (knight/bomber 2–3), and ground coins are tapped to collect. Caps at 999;
  each gold medal adds +1 starting gold.
- **Units.** Buy buttons with 5 s cooldowns spawn an army spaced by a per-unit
  frequency; slots unlock with `AvailableUnits` (soldier always, then
  archer/knight/bomber). Costs 5/8/12/20, frequencies 1.0/0.75/1.2/2.5 s.
  Soldier speed 35, armor 10, 100 HP, 5 castle damage, 1 gold/kill; archer 40,
  armor 0, 100 HP, 3 damage, ranged (stops to shoot); knight 55, armor 40,
  100 HP, 8 damage, 2 gold; bomber 25, armor 55, 100 HP, throws bombs instead
  of melee, 3 gold. Heal: 15 gold, 30 s cooldown, restores castle health.
- **Cannon.** Two cannons, damage 100 + bonuses, 3.5 s cooldown (−0.75 per
  Defense level). Press and drag to aim: launch speed 150–450 px/s by drag
  length, ball gravity 600 px/s², ground impact with splash scaled by distance
  and reduced by armor `(100−armor)/100`; a meter shows readiness.
- **Auto-snipe.** When enabled, a sighter arrow fires every `SnipeFrequency`
  at the lowest-HP enemy within 180 px (never bombs) for `SnipeDamage`.
- **Enemy side.** The AI uses the same army system with per-level stats; a boss
  level announces (dragon, trojan, mole, imps, brute, super-boss) and spawns
  that fight's boss.
- **Score.** `time + offense + defense`: time 250 if ≤180 s else
  `max(360−t,0)/180·250`; offense `min(400, kills/fired·325)` plus half of
  `min(200, gold·3.5)`; defense `health/max·400` plus the other half. Gold
  medal at ≥ 1000, silver below. First completions award 1 upgrade point
  (levels 1–12) or 2 (13–15), all persisted with the score map; completing
  all 16 shows the win screen.
- **Skill tree** (3 branches × 3 tiers, each upgrade costing the next tier
  index). Offense: +1 unit damage, +8 armor, +1 unit per army. Defense:
  +10 cannon damage, −0.75 s cooldown, +200 health. Utility: +5 snipe damage,
  −0.35 s snipe interval, faster gold tick.

## 4. Controls

- Tap buy buttons along the bottom bar to spawn armies; the button is disabled
  until gold covers its cost, shows a charge animation during cooldown, and
  heal uses a longer 30 s cooldown.
- Press on a cannon and drag: the drag vector sets the shot direction and
  distance (shorter drag = lob, longer = fast flat shot); release beyond a
  10 px threshold fires, otherwise it cancels. Only one shot per cannon is in
  flight; a cooldown meter gates the next.
- Drag horizontally in the upper battlefield (y < 220) to pan the camera
  across the 480 px world; ground coins are collected by tapping them.
- Single touch; no accelerometer, no multitouch. Pause and menus are on-screen
  buttons, and the app opens the pause screen when it loses focus.

## 5. Content inventory (must be re-authored)

147 files: 69 `.png`, 68 audio `.xnb`, 7 `.xml`, 3 `.txt`.

- **Level data to re-author (do not copy):** `Data/levels.xml` (~10 KB: 16
  PlayerStats entries with health, gold, gold rate, snipe, cannons, units;
  5 boss entries) and `Data/player.xml` (base stats). We author our own table.
- Art: 6 backdrops, light/dark castles, 8 unit sheets (4 classes × 2 sides),
  boss part sets (dragon, trojan, mole, imps, brute, multi-part final + egg),
  cannons (base/on/fire/meters), coin, arrow/fireball/rock/bomb projectiles +
  poofs, health bars, gem icons, death/debris sheets, upgrade stars, light/
  dark parts atlases.
- UI: map frame + lock/played/gold nodes, menu chrome, buy/sell/repair/pause/
  charge buttons, skill-screen buttons, shutter halves, victory/defeat plates.
- Audio (68): per-family spawn/attack/death, cannon fire/charge/impact, arrow,
  boss roars/deaths, coin, UI, level win/lose — all synthesized fresh.

## 6. Implementation plan

- Engine: `ui/apps/games/castlesandcannons/CastlesEngine.kt` — pure Kotlin
  `object` over immutable `State` (units, armies, coins, cannon balls, castle
  HP, gold, timer, stats); fixed-step `step(dt)`; deterministic RNG (army
  counts, unit part variations).
  ```kotlin
  object CastlesEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Screen: `CastlesScreen.kt` in `DetailScaffold`, landscape `Canvas` with
  camera offset state; castles, unit walk cycles, projectiles, coins, health
  bars, boss banner, buy bar and pause all token-driven, zero corner radius.
- Inputs: cannon drag (press/drag/release via `pointerInput`), buy buttons,
  coin taps, horizontal camera drag, pause.
- Persistence: level scores/unlocks/skills via `graph.appState` (or a dedicated
  progress file), best scores via `graph.games`; SFX via `SfxBank`.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): cannon ballistics and
  splash falloff, score formula components and 1000 medal threshold, gold tick
  and cap, army spawn ordering/frequency, unit armor math, unlock and skill
  point awarding, skill-level cost math, boss level spawning.

## 7. Citation log

- `CastlesAndCannons.exe!Base_Defender.BaseDefender` / `GameScreen` — landscape
  force, level bands, cannons, buy/heal costs 5/8/12/20/15, unit gating.
- `CastlesAndCannons.exe!Base_Defender.GameScreen.CalculateScore` / `Update` —
  score terms, 1000 threshold, timers, boss alerts.
- `CastlesAndCannons.exe!Base_Defender.GameScreen.HandleTouchesMoved` —
  camera pan, coin pickup.
- `CastlesAndCannons.exe!Base_Defender.Cannon` / `Cannonball` — drag aim,
  150–450 speed, cooldown, gravity 600, y-195 impact.
- `CastlesAndCannons.exe!Base_Defender.Player` — gold cap/ticks, cannon
  100 dmg/3.5 s, auto-snipe.
- `CastlesAndCannons.exe!Base_Defender.Soldier` / `Archer` / `Knight` /
  `Bomber` / `Army` / `ArmyManager` / `ArmyButton` — unit stats, army
  frequencies, button gating/cooldowns.
- `CastlesAndCannons.exe!Base_Defender.Unit.ApplySplashDamage` — falloff and
  armor formula.
- `CastlesAndCannons.exe!Base_Defender.SkillTree` / `Progress` /
  `PlayerStats` / `BossType` — skill branches, 16-level score map and unlock/
  medal rules, stat schema, boss taxonomy.
- `CastlesAndCannons.exe!Base_Defender.LevelSelectScreen` / `EndLevelScreen` /
  `SkillScreen` / `PauseScreen` — navigation and medal display.
