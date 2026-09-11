# Snowball

- **Official package:** `Snowball.exe` (Snowball)
- **Corpus:** `snowball` (external, untracked)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Snowball.exe` | 200 | 793 | 18,517 |
| `Microsoft.Xna.Zune.dll` | dep | 829 | 15,852 |
| `ZuneCoreLib.dll` | dep | 304 | 6,297 |

Portrait 272x480 at a 1/60 s target. A tilt-controlled snowball rolls over a
tile grid that fades away under it. Two play modes: a 25-level Campaign and an
endless Survival score chase. The build embeds its own Box2D fork (world,
joints, contact listener) and uses `StorageManager` for level progress, high
scores and user settings. A campaign level editor also exists in the code
(`EditorBoard`) but is not reachable from the shipped menus.

## 2. Screens & navigation

- **Splash → Main Menu**: `BtnPlay` (Campaign → Level Select), `BtnSurvival`
  (Survival), `BtnHighscores`, `BtnOptions`; menu background snow/frost effects.
- **Level Select**: 5 columns of 50 px icons for 25 levels; icons are locked,
  unlocked (numbered) or completed (tinted, numbered). Locked groups of five
  open when a five-level block is finished.
- **Campaign game**: HUD shows one icon per remaining number coin along the
  bottom-left and the level code (e.g. `1-7`) bottom-right; the first time a
  mode is played a tutorial card covers the screen and is dismissed by a tap.
- **Countdown** 3-2-1-GO (500 ms initial delay, then 800 ms per step, scaling
  and fading) before control is handed over.
- **Pause** (any touch released after the countdown starts): `Resume`, `Quit`
  to menu; background music pauses and physics/player body freeze.
- **Game over**: Campaign win/fail pages (next level, retry or menu) and a
  Survival page with the score and play again. **Highscores** and **Options**
  (FX and BG volume, help/about, back) are pushed screens.

## 3. Rules, scoring & progression

- Board is **8 columns × 13 rows**; block size = 272/8 = **34 px**; board spans
  272x442 px under a 40 px HUD. Every cell starts as a `Fadeable` block; level
  XML may replace cells with `SafeBlock` (safe zone), `WallBlock`, `IceBlock`
  or other collision entities and may add items (coins, number coins).
- The player is a dynamic snowball; touching a fadeable tile starts its fade.
  `TileState` cycles **Normal → Fading → Kill → Respawning**:
  - Fading: after the fade duration elapsed, alpha drops 0.1 per update until 0.
  - Kill: while alpha is 0 the tile is a hole; if the player is over it,
    `GameOver` fires (player death particles + lose cue).
  - Respawning: alpha rises 0.1 per update back to full.
- **Campaign** (`NumbersBoard`, 25 levels):
  - Level fade/respawn timings are 2500 ms / 3500 ms.
  - Number coins must be collected in **descending order** — the player is
    allowed to take only the current highest number; collecting any other coin
    ends the level immediately.
  - Collecting the current coin removes one HUD icon and spawns star particles;
    collecting the last coin finishes the level with a celebration and the win
    page.
  - Completing a level marks it done and unlocks in blocks of five: after
    completing the first block each next level unlocks, and every subsequent
    five completions unlock the next five (`UnlockLevels(n+1, n+5)`).
- **Survival** (`SurvivalBoard`):
  - Each star is worth **1000 points**; the score HUD updates with commas.
  - **12 coins** are always live; collecting one immediately spawns a
    replacement at a random position at least 25 px from the edges and outside
    a 35 px box around screen centre, with a random slow rotation.
  - Every fade-color cycle shortens the tile fade duration by **1 ms** and
    lengthens respawn by **15 ms**, so holes stay open longer as the run goes.
  - The run ends when the player falls through a fully faded tile.
- Progression/persistence: `LevelProgress` (completed/unlocked per level),
  `Highscores` (seeded with two placeholder rows, then top entries) and
  `UserSettings` (FX/BG volume, tutorial-seen flags) load from user storage;
  first run creates all 25 locked with level 1 unlocked.
- Movement damping: while the tilt vector opposes the current velocity the
  component is scaled by 0.98 per frame, giving a slight counter-steer feel.

## 4. Controls

- **Tilt** drives the snowball: accelerometer vector (deadzone 0.1) is
  multiplied by speed **100** and applied as a linear impulse every frame.
- **Tap** dismisses the tutorial and dismisses the countdown overlay; **touch
  release** (after gameplay starts) toggles pause.
- Menus are tap targets (`CMenu.OnTouch`); the level-select snowflake rotates
  continuously; highscore/options screens are simple tap lists.
- No multitouch gestures; no on-screen d-pad.

## 5. Content inventory (must be re-authored)

87 files: 62 XNB + 25 XML.
- `Sprites/` (8): Player, Block, SafeBlock, IceBlock, WallBlock, Coin, Star,
  StarSmall; `Sprites/Countdown` (4): Count1–3, CountGo; `Sprites/Menu` (33):
  page backgrounds, arrows, play/survival/highscores/options buttons, icons
  complete/unlocked/locked, game-over banners; `Sprites/Particles` (5).
- `Levels/` (25 XML): **do not port** — re-author 25 campaign layouts in code
  or a neutral format.
- Audio: BG_Menu, BG_Game, FX_Click, FX_Countdown, FX_Go, FX_Lose,
  FX_CoinCollected, FX_StarCollected.
- Fonts (4 XNB) and `Highscores.xml`/LevelProgress/UserSettings schemas.

## 6. Implementation plan

- Engine: `ui/apps/games/Snowball.kt` — `object SnowballEngine` with immutable
  `SnowballState(mode, grid, player, score, progress)` and `step(dt, tilt)`.
  Model the tile state machine exactly (fade/respawn counters in ms) and keep
  grid content as a neutral `TileKind` enum; level layouts generated in Kotlin.
- Screen: `SnowballScreen` in `DetailScaffold("snowball")` — Canvas grid of
  sharp quads fading by alpha, snowball circle with rotation, HUD icons and
  level code, countdown overlay, pause and game-over pages.
- Sensors: reuse `AppSensors` accelerometer, scale by 100 impulse, apply the
  0.98 counter-steer damping; tap dismisses tutorial/countdown.
- Persistence/SFX: `graph.appState` JSON for LevelProgress + settings;
  `graph.games.record("snowball", score, "survival")` and campaign completions;
  `SfxBank` for countdown, star, lose and click.
- Tests (`app/src/test/java/com/heretek/dorado_hd/SnowballEngineTest.kt`):
  tile Normal→Fading→Kill→Respawning timing; standing on killed tile kills;
  campaign ordered-coin rule rejects out-of-order pickup; one HUD icon per
  coin; survival +1000 and 12-coin repopulation bounds; fade −1 ms / respawn
  +15 ms per colour cycle; five-level unlock block rule; determinism.

## 7. Citation log

- `Snowball!GameBoard..ctor` / `Initialize` — 8x13 grid, 34 px blocks, tile
  creation and player spawn.
- `Snowball!GameBoard.Update` — fade trigger, kill check, game-over routing.
- `Snowball!BoardTile` — TileType/TileState enums and alpha transitions.
- `Snowball!GameBoard.LoadLevel` — collision/item layers, SafeZone mapping.
- `Snowball!Player.Update` / `applyMovementBoost` — impulse 100, deadzone 0.1,
  0.98 damping.
- `Snowball!NumbersBoard.ItemCollected` — descending-order rule, HUD icons,
  completion, star particles.
- `Snowball!SurvivalBoard` — 1000/star, 12 coins, spawn margins, fade/respawn
  decrements, game-over score page.
- `Snowball!CountdownSequence` — 500 ms delay, 800 ms steps, cues.
- `Snowball!GameManager.StartGame` — mode → board (8x13) creation.
- `Snowball!LevelSelect.Init` / `UnlockLevels` / `CompletedLastLevel` — 5x5
  icon grid, block unlocks, TotalLevels 25.
- `Snowball!GameOverCampaign..ctor` / `Init` — completion/unlock bookkeeping,
  next-level navigation.
- `Snowball!ContactListener.PlayerVsObject` — coin pickup trigger.
- `Snowball!Globals` — 272x480, HUD offset 40, layers/z-order.
