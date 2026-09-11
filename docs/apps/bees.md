# Bees!!!

- **Official package:** `Bees.exe` (bees)
- **Corpus:** `Zune HD Apps (Decompiled)/bees` (external, untracked)
- **Wave:** W4 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Bees.exe` | 42 | 235 | 5677 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `Noodles.dll` | 89 | 763 | 10526 |
| `ZuneCoreLib.dll` | 66 | 288 | 6288 |

- Portrait shell: `SetScreenSize(272, 480)` inside the Noodles engine, whose
  FengShui menu system drives every screen from XML menu descriptions.
- Haptics: the device is vibrated 0.4 s on bee/baddie explosions; sound and
  vibration toggles are menu items.
- Progress persists in Noodles `PersistentSettings` (`user.settings`): per-theme
  jar counts, upgrade flags, unlocked bee types and one-shot tip flags.

## 2. Screens & navigation

Noodles menu pages, keyed by name: `splash` → `mainmenu` → `worldmap` →
(level info | `hive`) → `game` → `results`; `pause` overlays the game.

- Main menu: play, help/how-to, about. World map: five environment nodes
  (grass, swamp, desert, snow, space) × 5 levels, gated by previous completion.
- Level info: description + best score, then launches. Hive shop: jar purchases
  unlock bee types, protection and extra honeycomb capacity, priced per theme.
- Game: the pollinator board with bee slots, hive, paths, flowers, a touch
  menu (select bee, move bees, bank, pause) and popups/tips.
- Results: final score and earned jars, with a bee celebration; pause offers
  resume, restart, quit to map and help.

## 3. Rules, scoring & progression

Cooperative hive-harvest game on a node grid (max 5 columns × 6 rows; the
board is computed to fit under the HUD with 6 px margins).

- **Board.** Level XML defines flower/hive nodes and the paths between them.
  Flowers (6 colors; value 15 + 10·color, i.e. 15–65 pollen per petal unit)
  grow (21 s) and wilt (15 s), hold up to 4 petals and can run out; in the
  space theme empty flowers bite. 3 flowers start pre-bloomed in adventure.
- **Bees.** One hive with four slots: normal (harvest 7 s, strength 2),
  pollinator (6 s, strength 2, re-blooms flowers), worker (1.8 s, strength 3),
  ninja (11 s, strength 1, 50 damage). Worker/ninja/fat unlock via shop; the
  hive self-spawns replacements every 5–20 s (rate grows 3 s per live bee)
  after a red/yellow stoplight warning.
- **Harvest → deposit.** A bee flies the path graph, orbits a node, takes a
  petal (15 score; pollen = flower value × petals up to strength), carries it
  home and deposits, scoring the carried pollen and queueing it into the hive
  tank. The tank converts 25 pollen → 1 honey at 32 pollen/s, capacity
  `clamp(3 + hive_upgrade + extras, 3, 10)`; overflow stops conversion and
  warns the bee to deposit.
- **Banking.** Bank converts `honey·25 + pollen` into jar progress with a +15 %
  bonus; reaching the theme cost awards a jar worth its value and score. Jar
  math: grass 80/150, swamp 100/200, desert 150/250, snow 150/300, space
  200/500 (cost/value).
- **Streak.** Each deposit increments the streak, which sets the simultaneous
  bee cap (`min(streak+1, hive_upgrade+2)`, min 2) and gates baddie spawns.
  Any collision (bee-bee or bee-baddie) explodes both, dumps carried honey/
  pollen and resets the streak.
- **Baddies.** Spawn on a timer once the streak reaches 2: bears (brown 15 HP/
  100, black 20/250, polar 15/200, moon 20/250; kills score value × defeated
  combo), a snow bunny, a gopher (5 s warning, 10 s dance) and a hummingbird
  (500 × combo). All warn before acting.
- **Modes.** Adventure: 240 s, last 30 s blocks new spawns with a warning.
  Challenge: bee cap grows with time (`max(2, t/90 s)`, +1 at streak ≥ 2) and
  the tank drains 8.4/s while no harvest arrives (honey converts back). Battle
  and PollenBattle variants reuse the combat values. A scripted 8-step
  tutorial gates expected actions (land, harvest, deposit, purchase jar, …).
- **Progression.** 5 environments × 5 levels. Shop prices are theme jars
  (protection 10 grass; honeycomb extras 5/2×3/5×4); `hive_upgrade` tiers 1–4
  expand capacity; per-theme jars persist capped at 99. Adventure always ends
  as a scored win; only battle modes can lose. Per-level bests are remembered.

## 4. Controls

- Touch-driven, portrait: tap a bee slot to select (cycles through available
  bees), tap a flower/node or path to send the selected bee there, tap the hive
  to deposit, and use `move bees` to route every landed bee at once.
- `bank` converts the hive tank to jar progress; `purchase jar` appears when a
  jar is due. The pause and help controls are on-screen buttons.
- No accelerometer input; no multitouch. One-shot tips (multi-bee, collision,
  time warning) pop up once and are remembered; Noodles menus use touch with
  hover/press states.

## 5. Content inventory (must be re-authored)

141 files: 54 `.pck` packed atlases, 58 `.xnb`, 26 `.xml`, 3 `.bff` fonts
(no dedicated music track).

- **Level data to re-author (do not copy):** 26 XML levels — 25 adventure
  levels (five per environment) + tutorial; each ~0.5–1.2 KB of flower/hive
  nodes (coordinates, colors) and the path graph.
- Bees: four bodies, helmets, protection bubble, safebee frames. World: five
  theme tile/background/overworld sets, flower/node skins, pollen, honeycomb,
  hive parts (tires/duck/fireplace/wings/fire/flag), stoplight, sun/moon,
  space chomper, warning, explosion. Baddies: four bears, bunny, gopher+hole,
  hummingbird+wings.
- UI: honey jar/icons, coin icon, HUD sheet, locked/sold art, panels
  (main/pause/item/textbox), cursor, arrows, menu backdrops (splash/main/
  worldmap/level info/hive/game/results), title, logo, preview art, font set.
- Audio (16): deliver ×4, explode ×5, gather ×4, select ×3 plus UI cues — all
  re-synthesized.

Re-author: our own 25 node/path layouts, Canvas bee/flower/hive/baddie art,
cyclic backdrops, token fonts and synthesized SFX.

## 6. Implementation plan

- Engine: `ui/apps/games/bees/BeesEngine.kt` — pure Kotlin `object` over
  immutable `State` (nodes, paths, flowers w/ petals, bees, hive tank, streaks,
  baddies, timers, mode); fixed-step `step(dt)`; deterministic seeded RNG for
  flower colors, spawn pacing and baddie picks.
  ```kotlin
  object BeesEngine {
      fun step(state: State, input: Input, dt: Float): State
  }
  ```
- Screen: `BeesScreen.kt` in `DetailScaffold`, portrait `Canvas`; environment
  backdrop, grid nodes, path highlights, bee flight/orbit, hive fill meter,
  jars/score HUD, tips and pause/menu overlays — all token-driven, zero radius.
- Inputs: bee-slot taps, node/path taps, hive bank, plus buttons via
  `pointerInput`; haptic pulse on explosion.
- Persistence: jars, upgrades/unlocks, per-level best and tips via
  `graph.appState`; scores in `graph.games`; SFX via `SfxBank` (select,
  gather, deliver, explode, jar award, UI).
- Tests: pollen→honey conversion/cap, bank bonus and jar thresholds, streak/
  slot math, baddie cadence and combo scoring, harvest values, challenge
  drain, win/lose resolution, persistence round-trip.

## 7. Citation log

- `Bees.exe!Bees.Game.BeesShell` / `GameScreen` — portrait shell, menu
  registry, grid/score/jar constants, modes, timers, collections.
- `Bees.exe!Bees.Game.GameScreen.Restart` / `BankHoney` / `AddToScore` —
  theme jar table, max-honey formula, per-mode limits, pre-bloomed flowers,
  15 % bank bonus, score/pollen routing.
- `Bees.exe!Bees.Game.GameScreen.Update` — 32/s per-25 conversion, bee caps,
  30 s warning, challenge drain, baddie gating, collision dump, win/lose.
- `Bees.exe!Bees.Game.Bee` — per-type strength/harvest/orbit/speed/damage,
  harvest score 15, deposit score.
- `Bees.exe!Bees.Game.Flower` — value 15 + 10·color, 4-petal cap, 21 s growth,
  15 s wilt, re-bloom, space danger.
- `Bees.exe!Bees.Game.Hive` — spawn 5–20 s + 3 s per bee, stoplight, deposit/
  streak increment.
- `Bees.exe!Bees.Game.Bear` / `Gopher` / `Hummingbird` / `Bunny` — baddie
  values, warnings/timings, combo scoring.
- `Bees.exe!Bees.Menu.HiveMenu` / `WorldMapMenu` / `MainMenu` /
  `LevelInfoMenu` / `ResultsMenu` — shop prices/upgrades, map gating, level
  launch, score and 99-cap jar display; `LevelConfig`/`GameMode`/`GameAction`/
  `EnvironmentTheme` cover the level schema and taxonomies.
