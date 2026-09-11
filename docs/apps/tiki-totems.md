# Tiki Totems

- **Official package:** `TikiTotems.exe` (tiki_totems)
- **Corpus:** `Zune HD Apps (Decompiled)/tiki_totems` (external, untracked)
- **Wave:** W5 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `TikiTotems.exe` | 26 | 222 | 3364 |
| `Farseer Physics 3.0 XNA.dll` | 132 | 579 | 15986 |
| `Microsoft.Xna.Framework.Game.dll` | 17 | 77 | 1052 |
| `Microsoft.Xna.Framework.dll` | 232 | 1063 | 15066 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `TikiZuneHDContent.dll` | 3 | — | 56 |
| `ZuneCoreLib.dll` | 71 | 290 | 5709 |
| `ZuneGamesLib.dll` | 29 | 147 | 2648 |

## 2. Screens & navigation

- `MainMenuScreen`: PLAY (tutorial/classic packs), TUTORIAL, OPTIONS,
  RESET PACKS, plus a premium/extra-pack entry. `SetsScreen` chooses the
  classic (`ClassicPackScreen`) or `PremiumPackScreen` set; `LevelsScreen`
  shows the level grid per pack.
- `GameScreen` runs a level with `GameGUIPanel` (level label, blocks-left
  counter, pause), `GamePausePanel` (RESUME / RESTART / SKIP / sound and
  music toggles), `GameFailPanel` + `FailAnimationPanel`, `GameVictoryPanel`
  and `GameSkipPanel` ("ARE YOU SURE YOU WANT TO SKIP THIS LEVEL?").
- `OptionsScreen` (SOUND / MUSIC toggles), `ResetPacksPanel` (double
  confirmation), `GlobalAnimationPanel` (clouds, fire, meteors) and
  `Meteor` decorate the menus.

## 3. Rules, scoring & progression + simulation model

- Goal (in-game about text): "Appease the Tiki Gods by carefully removing
  the unnecessary blocks that dim the might and glory of their beautiful
  totems." A level spawns a totem plus destructible and special blocks;
  tapping a destructible block removes it, and the level's `ToDestroy`
  count tracks how many countable blocks remain.
- Win: when the countable-block count reaches zero AND the totem is stable
  — linear velocity |vx|,|vy| ≤ 0.2 and |angular velocity| ≤ 0.25 — for
  `VICTORY_TIME` = 1 s, the level is solved. Lose: the totem touches the
  ground plane (the lava line at y=481 in world units), the totem leaves
  the screen, or an explosion/fall breaks it; failure plays the fail
  animation and the fail panel.
- Block types: `BLOCK_TOTEM`, `BLOCK_NORMAL` (destructible + countable),
  `BLOCK_INDESTRUCT`, `BLOCK_ELASTIC`, `BLOCK_VANISH`,
  `BLOCK_ELASTIC_INDESTRUCT`, `BLOCK_EXPLOSIVE_VANISH` and
  `BLOCK_EXPLOSIVE_TIMER`. Vanishing blocks disappear when two of them
  touch; explosive-vanish pairs also detonate; the timer bomb shows a 3 s
  countdown, blinks, then explodes. An explosion applies a unit linear
  impulse along the radial direction to every live block: full strength
  within 48 world units of the blast, fading linearly to zero at 192 units.
- Physics: Farseer 3.0 (Box2D-style) world stepped at a fixed 1/60 s with
  gravity (0, 20); `TikiGlobals.WORLD_SCALER` = 32 px per world unit,
  ground box 160 wide at (136, 481), block recharge time 0.5 s. Blocks are
  rigid polygons with a `BlockType`-dependent texture size (40/80/120/160/
  200 px sides).
- Progression: 13 shipped pack assets — `Tutorial`, `Pack01`–`Pack05` and
  `Extra02`–`Extra08`. `PackSaveState` stores per-pack `UnlockedLevelId`
  and whether the pack is an extra (premium) pack; completing a level
  advances the pack's unlocked level id. The original had classic/premium
  tiers; the re-author ships all packs as free original content.

## 4. Controls

- Tap a destructible block to remove it (blocks are `Button` instances and
  only destructible ones get a click handler); touch ownership is decided
  by testing the touch point against the block's rotated polygon
  (`CouldOwnTouch`).
- The pause button opens the pause panel; victory/fail/skip panels use
  large text buttons ("CONTINUE", "RESTART", "SKIP IT!", "CANCEL").
- No steering axis: the whole interaction is tap-to-remove plus physics.
  Device deactivation pauses audio.

## 5. Content inventory (must be re-authored)

162 XNB files: 8 fonts (`tikiFont12_b` … `tikiFont24_r`), 131 images —
60 block textures covering the 5x6 size/style matrix
(`40/80/120/160/200` x `normal/indestruct/elastic/elaunbr/timebomb/vanish`),
totem variants (`totem_wood`, `totem_stone`, `totem_gold`), UI panels,
buttons, clouds, lava/fire and menu art — 13 pack files (`Packs/*.xnb`
binary levels), and 10 sound loops/one-shots (`zbicie_v1`, `ogien_v3`,
`beep`, `explode`, `upadek_v2`, `wygrana v5`, `przegrana_v5ima`, three
music/ambient loops). Re-author: original block/totem/UI art, our own
packs and levels, synthesized SFX/music.

## 6. Implementation plan

- Engine: a small deterministic 2D rigid-body core shared with the other
  physics toy titles (`ui/apps/games/physics2d/`): polygons, contacts,
  sequential impulses, fixed 1/60 step — no third-party dependency and no
  engine3d needed.
- Logic: `ui/apps/games/TikiTotems/` — block types and traits, level model
  (`ToDestroy`), win/lose state machine, explosion impulses, pack save
  store; levels defined as original Kotlin/JSON data.
- UI: `DetailScaffold` pack/level select and HUD panels; `SfxBank` for
  beep/break/explosion/victory/lose; sky/cloud/lava decoration drawn
  procedurally or as our own layers.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): block type traits,
  vanish/explosion pair rules, totem-ground fail, stability window, pack
  unlock progression, explosion falloff, deterministic replay of a scripted
  level.
- Fidelity target: full-parity mechanics with original content and all
  packs free. Risks: stable polygon stacking parity, matching the feel of
  elastic/timer blocks, and authoring enough levels to replace 13 packs.

## 7. Citation log

- `TikiTotems.exe!GameScreen.InitializeWorld`, `.LoadLevel`,
  `.Update` (1/60 step), `.OnBlocksCollision`, `.CheckForVictory`,
  `.OnVictoryTimer`, `.OnBlockDestroyed`, `.TriggerFail`,
  `.GenerateExplosion`, `.SetLevelAndPackNumbers`
- `TikiTotems.exe!GameBlock` ctor, `.Init`, `.InitNormal`,
  `.InitIndestruct`, `.InitElastic`, `.InitVanish`,
  `.InitExplosiveVanish`, `.InitExplosiveTimer`, `.BreakTotem`,
  `.IsStable`, `.destroyBody`, `.markForDestruction`, `.CouldOwnTouch`,
  `.Update`
- `TikiTotems.exe!TikiGlobals` (`WORLD_SCALER`, `RECHARGE_TIME`,
  `VICTORY_TIME`, `SOUND_ON`, `MUSIC_ON`)
- `TikiTotems.exe!MainMenuScreen`, `.SetsScreen`, `.LevelsScreen`,
  `.ClassicPackScreen`, `.PremiumPackScreen`, `.GamePausePanel`,
  `.GameVictoryPanel`, `.GameFailPanel`, `.GameSkipPanel`
- `TikiTotems.exe!PackSaveState`; `TikiZuneHDContent.dll!Block`,
  `TikiZuneHDContent.dll!Level` (types only)
