# Audiosurf Tilt

- **Official package:** `Audiosurf.exe` (audiosurf)
- **Corpus:** `Zune HD Apps (Decompiled)/audiosurf` (external, untracked)
- **Wave:** W6 · **Category:** music
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Audiosurf.exe` | 128 | 736 | 19783 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneCoreLib.dll` | 67 | 286 | 6089 |
| `ZuneGamesLib.dll` | 36 | 170 | 3610 |

## 2. Screens & navigation

- Two mode layers: `AudiosurfUI.GameMode` = `UI`, `Normal`, `Hard`,
  `Visualizer`. The UI layer owns `SongBrowser` (artists/albums/playlists
  with `BrowseMode` paging and recent-song list), a song detail panel with
  per-song medals, and toolbars: `select_mode`, `select_song_button`,
  `how_to_play`, `options`, `unlocks_button`, plus sliders for SFX/control
  sensitivity and a lane-snapping checkbox.
- In-game HUD (`AudiosurfUI`): song title/progress bar, score, chain
  counter and reward text, medals (bronze/silver/gold rings or small
  medals), minimap with player marker, pause/game bar, and a "game over"
  flow that returns to the browser.
- `SongBrowser` doubles as the shop/customizer for ring designs
  (`currentRingDesign`, 39 unlockables in `SavedData`) and the coin bank
  (`bankBalance`); `HowToPlayBox` shows mode-specific help the first time a
  mode is played (per `showHowToPlayNormal/Hard/Visualizer`).

## 3. Rules, scoring & progression + simulation model

- Music game. A song from the media library is analysed and a downhill
  track ("highway") is generated from the analysis data: the highway
  requests `ZuneMedia.StartSongAnalysis(song, 1000/25 ms,
  FftResults.LinearScaleRaw)` — 25 samples per second of 256-bin linear FFT
  spectra — massages the data, then builds track nodes
  (`theEntity.InitEntity`), spawns traffic blocks (`CreateMembers`) and
  post-processes the list/array (tunnel threshold from the 80th percentile
  intensity, mode-dependent block typing). The same song always produces
  the same track.
- A hover ship (`Racer`, mass 500) flies the track across 3 lanes. The
  player steers between lanes; blocks are either coloured (type > 0,
  scoring) or grey (type 0). Collecting coloured blocks increments the
  chain and fires a fly-up effect; hitting grey blocks plays the stone
  collision sound and does not score. Chain call-outs appear at 5, 11, 25,
  50 and every 100; a "perfect" banner fires when appropriate.
- Score: each collected coloured block is worth `colorPointGain` — 1 in
  Normal, 2 in Hard. Medal thresholds are computed from the total coloured
  block count: bronze 35%, silver 70%, gold 94% in Normal; bronze 35%,
  silver 70%, gold 97.5% in Hard. Medals persist per song for both
  difficulties (`SongData.normalMedal`/`hardMedal`); the browser shows the
  achieved medal and the HUD shows the next threshold. Visualizer mode
  spawns lanes from intensity without score pressure.
- Progression: unlocking is cosmetic (ring designs and browser goodies,
  `numUnlockables = 39`), funded by `bankBalance` and tracked in
  `unlockList`; saved settings include SFX volume, control sensitivity,
  lane snapping, current ring design, and the recent-song list. The
  ship has a hover idle, thrusters, engine fins and a jump (jump time
  1.5 s, max height 1) with a landing shake.

## 4. Controls

- Four selectable input methods (`InputMethod`): `AbsoluteTouch` (ship
  follows the touch x directly), `RelativeTouch`, `Accelerometer` (tilt)
  and `ThumbTap`. The gesture path also supports side tap/relative modes.
- Accelerometer steering: the tilt dead zone is
  `0.05 + 0.3 * (1 - sensitivity)`; above it the desired lane becomes 0/1/2
  (left/centre/right) with a magnitude-based strafe, and the camera rolls
  with the device; lane snapping (setting) settles the ship in a lane.
- Menu input uses large touch lanes (x > 180 = primary, x > 90 = secondary,
  else back) with press/release detection, matching the Zune
  three-region menu gesture style.

## 5. Content inventory (must be re-authored)

The mined index for this slug is mis-linked to the Animalgrams corpus (it
lists 137 Animalgrams files); the shipped Audiosurf tree at
`audiosurf/gametitle/584E07D1/Content` holds 98 XNB plus `spritesheets.xml`
and 6 FBX material textures — 104 XNB total. Breakdown: 57 track texture
tiles (`td0..td35`, 128² and 256² pairs), 4 UI sprite sheets
(background/browser/button/loading, ~1 MB each), 3 ship part models
(Ship_New_Main/Flaps/Panels), 2 block models (DoubleSpike, DoubleLozenge),
SkyWires, cliff models, ~10 shaders (shader, model shader + lit,
GaussianBlur, glowTunnel, colortex/colorAdd/colortexadd, alpha), medal and
progress UI (MedalRings, MedalProgress, progressbar, radialprogress,
MinimapMarker), 5 fonts (Segoe — **we ship our own font instead**), 5 SFX
(UI click, hit, collision color/stone, slide), localization en/es/fr, and
a default screenshot. Re-author all: procedural track tiles and ships,
our own UI art, fonts, and synthesized SFX.

## 6. Implementation plan

- 3D: `ui/apps/engine3d/` — track ribbon generated from code-defined node
  splines (road, rails, lane lines, guard rails, cliff and skybox as
  procedural meshes), ship mesh with hover/thrusters, traffic block
  instancing (colour + grey variants), tunnel/sprite effects and beat
  flashes; no third-party 3D dependency, no Microsoft assets.
- Audio analysis: reuse the existing `analysis/` DSP package
  (`PcmFeatureAnalyzer`/`AudioFeatures`) instead of the original FFT
  service — derive per-25 Hz band energy, onset/beat strength and intensity
  envelopes; map them to node height, block placement and block types with
  a song-keyed seeded RNG so the track is deterministic. Ships with the
  player's own audio file, not the Zune MediaPlayer.
- Logic: `ui/apps/games/Audiosurf.kt` — track builder, 3-lane racer with
  hover/jump, traffic update/collision, chain counter, medal thresholds,
  per-song medal and unlock/bank store; Normal/Hard/Visualizer modes.
- UI: browser (artists/albums/playlists/recent), mode select, help,
  sliders and lane-snapping toggles in `DetailScaffold`; `SfxBank` for
  collision/collect/UI cues; HUD minimap and score bar over the GL view.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): track generation
  determinism for a fixed feature set, lane steering/dead-zone mapping,
  chain counting, medal threshold math, per-song medal persistence,
  unlock/bank rules, collision classification (colour vs grey).
- Fidelity target: full-parity Normal/Hard/Visualizer mechanics with
  DSP-generated tracks (an homage to the original analysis pipeline, not
  its code). Risks: making feature→track mapping feel musical; track/ship
  art quality; and mapping arbitrary local audio reliably.

## 7. Citation log

- `Audiosurf.exe!Highway` fields, `.CreateTrack` progress steps,
  `.PostProcessTrackList`, `.PostProcessTrackArray` (analysis via
  `ZuneMedia.StartSongAnalysis`, 25 samples/s, 256 bins);
  `Audiosurf.exe!Music.GetSongPlayhead`, `.PlaySong`
- `Audiosurf.exe!Racer` (mass, hover range, jump params, strafe),
  `Audiosurf.exe!Racer.GotoLane`, `.SetRoll`
- `Audiosurf.exe!AudiosurfGame.HandleAccelInput`,
  `.CalculateMedalThresholds`, `.UpdateScore`, `.HandleMenuTouchInput`,
  `.ShowRewardText`
- `Audiosurf.exe!InputMethod`, `AudiosurfUI.GameMode`,
  `AudiosurfUI.Achievement`
- `Audiosurf.exe!Traffic` (visibleCount, chain count, block models),
  `.HandlePlayerCollision`
- `Audiosurf.exe!Chainspans`, `Audiosurf.exe!BeatFlashes`,
  `Audiosurf.exe!Road`, `Audiosurf.exe!Minimap`, `Audiosurf.exe!Music`
- `Audiosurf.exe!SongData`, `Audiosurf.exe!SavedData`
  (`numUnlockables = 39`, `useLaneSnapping`, `controlSensetivity`,
  `bankBalance`)
- Actual content tree counted at
  `audiosurf/gametitle/584E07D1/Content` (104 XNB + spritesheets.xml);
  `_index/audiosurf-tilt.json` content section is mis-linked to Animalgrams
