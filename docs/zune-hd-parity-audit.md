# Zune HD on-device parity audit — Dorado-HD vs firmware disassembly

**Date:** 2026-09-10
**Method:** systematic comparison of the reconstructed Zune HD firmware corpus
(`zune-hd-disassembly/` — 109 ARM32 PE modules, 66,598 functions, 4,169 named
exports, 22,479 strings) against the Dorado-HD Android app source.
**Scope:** *on-device* parity — every behavior the Zune HD itself had. Dorado-HD's
additions beyond the device (scrobble, lyrics, DSP, Dynamic Mix, cloud, LAN sync,
Now Playing widget) are **not** scored here; they are post-device extensions
(canon §10).

**Provenance.** Device evidence is cited as `module.dll@<VA>` (virtual address in
the reconstructed PE) or as a recovered string. HD evidence is `path:line`. Raw
decompiled C, symbol dumps and strings stay in the external corpus and are never
committed; this document is synthesized analysis only.

**Companion:** [`zune-hd-parity-gaps.json`](zune-hd-parity-gaps.json) is the
machine-readable gap register.

---

## 1. On-device parity scorecard

| Domain | On-device parity | Notes |
|---|---:|---|
| A. Shell & navigation | **~78%** | Core surfaces match; a set of device scenes is absent (audiobooks, share, queue, inbox…). |
| B. Now Playing & transport | **~75%** | Music/video/radio work; no scrubber, queue list, or picture NP. |
| C. Motion & kinetics | **~60%** | Shape matches; the integrator mathematics diverges (see §4). |
| D. HUD / always-on | **~45%** | Transport overlay + shade; no dimmer/power-off, PIN lock, status OSD. |
| E. Library & indexing | **~80%** | Pivots + A–Z rail; no search UI, no built-in playlists, no letter/grid thresholds. |
| F. Playback engine | **~70%** | Playback/shuffle/repeat/hearts; no EQ, queue engine, RDS, WMA/DRM. |
| G. Marketplace & commerce | **~30%** | Frozen 62-entry catalog; no client/search/cart. |
| H. Social / inbox / identity | **~30%** | Feed mock; no inbox, user card, Passport. |
| I. Settings & device | **~70%** | Most pages; no PIN/screen-lock, Wi-Fi info, TV-out. |
| J. Tokens / fonts / strings | **~70%** | Palette/scale canon-derived; label vocabulary diverges. |

**Weighted on-device parity: ~68%.** (The device is 2009 hardware; several device
subsystems — marketplace, DRM, Passport, MTPZ, TV-out, telemetry — are
deliberately out of scope; see §12.)

---

## 2. Shell & navigation (gemstone.exe — 74 `Gem*Scene` identifiers)

Full device scene list recovered from `gemstone.exe.strings.json`. Status:
✅ parity · 🟡 partial · ❌ missing · N/A out of scope.

| Device scene | Dorado-HD | Status |
|---|---|---|
| `GemStartScene` / `GemStartListScene` | `ui/screens/HomeScreens.kt:68,111` | ✅ |
| `GemTiltScene` (Quickplay) | `ui/screens/HomeScreens.kt:147` | ✅ |
| `GemPivotScene` | `design/components/CrossbarBar.kt` | ✅ |
| `GemOverlayScene` | `ui/screens/NowPlayingScreen.kt:477` | ✅ |
| `GemSyncScene` | `ui/screens/DeviceScreen.kt` | ✅ |
| `GemLibrarySongScene` / `LibraryListScene` | `ui/screens/MusicScreen.kt:257` | ✅ |
| `GemLibraryArtistScene` (+About/Related) | `ui/screens/DetailScreens.kt:169,225,320` | ✅ |
| `GemLibraryPodcastScene` / Video | `ui/screens/Podcasts.kt:142`; `MediaScreens.kt:76` | ✅ |
| `GemLibraryLetterPickerScene` | `design/components/KineticList.kt:106` | ✅ |
| `GemNowPlayingRadioPresetListScene` | `ui/screens/Radio.kt:110` | ✅ |
| `GemMarketplaceScene` / Games | `ui/screens/MarketplaceScreens.kt:56,205` | ✅ |
| `GemSettingScene` / About | `ui/screens/SettingsScreen.kt:43,254` | ✅ |
| `GemStartGridScene` | — (list only) | ❌ |
| `GemStartExtrasScene` | Marketplace apps/games pivots | 🟡 |
| `GemQueueListScene` / `GemNowPlayingMusicListScene` | — (`enqueue` exists, no queue UI) | ❌ |
| `GemLibraryAudiobookPartScene` | — (no audiobook model) | ❌ |
| `GemLibrarySendComposeScene` / `…SendFriendsListScene` | — (no `ACTION_SEND`) | ❌ |
| `GemPictureTouchClientScene` / `GemZoomTouch` | `MediaScreens.kt:657` static `Fit` | ❌ |
| `GemLibraryArtistPhotoGridContentScene` | single image `DetailScreens.kt:275` | 🟡 |
| `GemLibraryCategoryScene` | `DetailScreens.kt:484` (genre only) | 🟡 |
| `GemLibraryGameScene` | Marketplace games pivot | 🟡 |
| `GemNowPlayingPicturesScene` / `…MainScene` | no picture NP mode | 🟡 |
| `GemNowPlayingRadioScene` / `…MainScene` | unified music NP reused | 🟡 |
| `GemMarketplaceSearchScene` | — (frozen catalog) | ❌ |
| `GemMarketplaceMusicTopScene` / `…GenresContentScene` | "featured" only `MarketplaceScreens.kt:256` | ❌ |
| `GemMarketplaceDetailsScene` (album/artist) | `DetailScreens.kt:63` | ✅ |
| `GemMarketplaceGamesDetailsScene` (+Screens) | catalog rows only | ❌ |
| `GemCartScene` / `GemCartConfirmScene` | — | ❌ (N-A candidate) |
| `GemSocialScene` | `MediaScreens.kt:632` (mock) | ✅/mock |
| `GemUserCardScene` | — | ❌ |
| `GemInboxListContentScene` / `GemInboxDetailsScene` | `ui/apps/mocks/Mocks.kt:90` (mock) | ❌ |
| `GemSettingPinLockScene` / `…ScreenLockScene` | `LockShade` only (no PIN) | ❌ |
| `GemSettingWifiInfoScene` | — | ❌ |
| `GemSettingStorageScene` | `SettingsScreen.kt:124`, `DeviceScreen.kt:333` | 🟡 |
| `GemSettingLegalScene` | footnote `SettingsScreen.kt:261` | 🟡 |
| `GemRefreshScene` | Settings/library refresh (no scene) | 🟡 |
| `GemTvOutputSelectorScene` | — | ❌ (hardware N-A) |
| `GemAcsFWScene` | `cloud/CloudUpdateService.kt` (different path) | 🟡 |
| Widgets (`GemContextMenu`, `GemWaitCursor`, `GemTimeline`, `GemTunerBar`, …) | Android/Compose runtime | N/A |

### Highest-value missing surfaces
1. **Queue surface** (`GemQueueListScene` / `GemNowPlayingMusicListScene`) — `PlaybackController.enqueue` (`media/PlaybackController.kt:153`) already exists; only the list UI is missing.
2. **Picture pinch-zoom/pan** (`GemPictureTouchClientScene` / `GemZoomTouch`) — device-signature viewer; HD is static.
3. **Sharing** (`GemLibrarySendComposeScene`) — scoped as roadmap M5.
4. **Audiobooks** (`GemLibraryAudiobookPartScene`) — whole media type absent.
5. **Marketplace discovery** (search/top/genres) — makes the frozen catalog navigable.

---

## 3. Motion, touch & kinetics (resolved)

### 3.1 The device integrator (authoritative)
Recovered from `xuidll.dll@0x41841D58` (scroll tick), `@0x4184C310` (drag
velocity), `@0x41848B98` (timer), `@0x41848FC4` (rubber-band),
`@0x4184C660` (default touch settings).

- **Position:** `pos += (dt_ms / 1000) · v`, where `dt_ms = max(33.333, now − lastTick)`
  (30 fps floor; literals `xuidll.dll@0x41801920 = 1000.0`,
  `@0x41803ce4 = 33.333`).
- **Velocity (active/drag):** `v' = v · (1 + c[+0xB0])`, clamped to
  `±( step[+0x88] · c[+0xB4] )`.
- **Stop:** when `pos` reaches the target `[+0xA0]`, the timer is killed and the
  shell snap callbacks fire; velocity zero-compare is `0.0`.
- **Tick:** `XuiSetTimer(elem, 1, 0x10)` → **16 ms = 62.5 Hz** (`@0x41848B98`).
- **Drag→velocity:** `(Δpx/Δt) · scale`, hard-capped at **32.0**
  (`@0x4184C310`, literal `@0x41804168 = 32`).
- **Rubber-band:** explicit damped spring `-(pos−target)·k[+0xD8] − v[+0x50]·d[+0xD4] + v[+0x50]` (`@0x41848FC4`).
- **Touch properties** (the ~20-float `XuiTouchSettings` vocabulary recovered from
  the `xuidll` string table `0x41803F74–0x41804150`): `HorzScroll`, `VertScroll`,
  `HorizontalFling`, `VerticalFling`, `HorizontalScrub`, `VerticalScrub`,
  `HorizontalRubberBand`, `VerticalRubberBand`, `FadeIn`, `FadeOut`,
  `PositionOffset`, `CenterSmallCanvas`, `EnableBandBreak`, `KeyScrollDistance`,
  `SmoothScroll`, `ScrollPaddingBefore/After`, `ZoomTarget`, `Hollow`, `Direction`.

### 3.2 Correction: the `0.95` retention provenance is false
The previous docs (`zune-hd-touch-settings.md` §6, canon §6) attributed HD's
per-frame retention to `XuiTouchSettings[0x1C] = 0.95`. **This is not supported:**
- the integrator reads the element's in-place block at `+0xA8` fields **`+0xB0/+0xB4`**
  = settings **`[0x08]/[0x0C]`** — `xuidll` defaults **1.9 / 1.5**, shell **1.9 / 1.2**;
- `[0x1C]` defaults to **0.7** and is **never read** by `@0x41841D58`;
- the update `v·(1+c)` is multiplicative-with-clamp, **not** `v·retention`.

### 3.3 HD state and gaps

| Aspect | Device | Dorado-HD | Gap |
|---|---|---|---|
| Tick | 62.5 Hz (`@0x41848B98`) | `KINETIC_FRAME_HZ = 60f` (`design/DoradoMotion.kt:30`) | 4% friction-conversion drift |
| Integrator | dt-scaled, 30 fps floor, step-clamped | continuous `animateDecay` (`design/components/ZuneFling.kt:31`) | model differs |
| Retention | `(1+c)` clamp, no `[0x1C]` | `0.95`/`0.94` (`DoradoMotion.kt:29,38`) | empirical, provenance wrong |
| Rubber-band | explicit spring (`@0x41848FC4`) | platform overscroll; "no springs" invariant | under-models device |
| Axis hysteresis | `XuiTouchEnableAxisHysteresis` (`xuidll@0x418331CC`) | implicit Compose latch | not tunable |
| Velocity cap | 32.0 | none | absent |
| Item snap / SmoothScroll | `XuiTouchSnapToTarget` (`@0x41833134`) | `scrollToItem` for A–Z only | absent |
| Navigation easing | per-element timelines (`TransFrom/To`, `EnterTransIndex/LeaveTransIndex`) | one bezier 380/420/340 ms | different model |

**Recommendation:** re-derive the HD fling friction from the dt-scaled device model
(or explicitly label `0.95/0.94` an empirical approximation), and stop citing
`[0x1C]`. Add the velocity cap and (optionally) item snapping.

---

## 4. HUD / always-on (`zhud_serv.dll`, 1,167 fns, 5 exports)

Device HUD scenes recovered: `HudMediaController{Music,Radio,Video}Scene`,
`HudScreenLockScene`, `HudPinLockScene`, `HudDimmerScene`, `HudOffSwitchScene`,
`HudMessageBoxScene`, `HudWaitMessageBoxScene`, `HudKeyboardScene`,
`HudNetwork{Connect,List}Scene`, `HudAutoConnectScene`, `HudWaitCursorScene`,
`HudDebugOverlayScene`, + root scenes (`ScreenLockRootScene`, `PinLockRootScene`,
`MessageBoxRootScene`, `DimmerRootScene`, …).

Inline HUD dwells are **recoverable** from `XuiSetTimer` call sites (**contradicts
`zune-hd-touch-settings.md` §5**): 7000 ms dim (`@0x419CD420`), 5000 ms saver,
3000 ms notifications, 30000 ms background (`@0x419C0584`), 12000 ms constant.

| Device HUD | Dorado-HD | Status |
|---|---|---|
| Transport overlay (`HudMediaController*`) | `TransportOverlay` `ui/screens/NowPlayingScreen.kt:477` | ✅ |
| Play/Pause/Next/Prev | `PlaybackController.kt:160-171`; `NowPlayingScreen.kt:563-592` | ✅ |
| Volume + mute | swipe volume only; **no mute** | 🟡 |
| `ProgressSlider` + `PlayPosition`/`Elapsed`/`Remaining` | no scrubber in NP | ❌ |
| `Ffwd` | — | ❌ |
| Lock/pin (`HudScreenLock`/`HudPinLock`) | `LockShade` (no PIN) `design/components/LockShade.kt` | ❌/🟡 |
| Dimmer / power-off (`HudDimmer`/`HudOffSwitch`) | — | ❌ |
| Message boxes | not modelled | ❌ |
| Status OSD (`batteryIcon`,`wifiIcon`,`Clock`,`Ambient`) | — | ❌ |
| Screensaver idle | `IDLE_SCREENSAVER_MS=5000` `design/DoradoTokens.kt:68` | ✅ |
| Wait cursor | Compose loading | 🟡 |

---

## 5. Library & indexing (`zcontent_serv.dll` + `zconfig_serv.dll`)

Device substrate: `zunedb.dat`, `zconfig.dat`, `drmStore.dat`, `AlbumArt`,
`mtp:DatabaseGenerationNumber`, `CloudModifiedItems`; `zconfig_serv` exposes
`LibraryLetterThreshold` / `AlbumGridThreshold`.

| Device behavior | Dorado-HD | Status |
|---|---|---|
| Indexed library (tracks/albums/artists/genres/playlists/pictures) | `data/repo/LibraryRepository.kt:35-48` | ✅ |
| A–Z letter picker | `design/components/KineticList.kt:66,176` | ✅ |
| **Indexed search** | `Daos.kt:68` `search()` defined, **no caller** | ❌ |
| Letter/grid thresholds (settings) | — | ❌ |
| Built-in playlists (`BuiltIn-FavoriteTracks`, `…MostPlayedArtists`, `…RecentTracks`) | Quickplay history/pins + Top Played `HomeScreens.kt:311` | 🟡 |
| Sort keys (article-strip, `MMM yyyy`) | plain tags | 🟡 |
| Album-art cache/thumbnails | per-track resolve | 🟡 |

---

## 6. Playback engine (`zmedia_serv.dll`)

Device: `CMediaQueueBase`/`CProgressivePlay`/`CTrackListQueue`, queue
capacities/history, `ZMediaQueue/MediaItemPositioned|Stopped|Paused|Playing`;
EQ (`Software\Microsoft\Zune\Equalizer`, `Preset%02d`); `FFTGRABBER`/`ZMedia/Viz`;
`SmartDJStore.dat`; RDS (`Radio_RDS_Capture.dat`); marketplace image/related
endpoints; DRM (`CeDRM_MGR_ParsePlayOPL`).

| Device behavior | Dorado-HD | Status |
|---|---|---|
| Play/shuffle/repeat/seek/hearts | `media/PlaybackController.kt:142-245` | ✅ |
| Queue engine (capacity/history/position events) | thin Media3 wrapper | ❌ |
| Equalizer presets | — (no `AudioEffect`) | ❌ |
| FFT visualizer bound to playback | procedural bars; real FFT exists separately `analysis/FeatureMath.kt` | 🟡 |
| WMA/WMV + WMDRM/PlayReady | `data/model/MediaFormats.kt:17-23` drops `wma`; no DRM | ❌ |
| RDS / FM tuner | HTTP-stream presets only `Radio.kt` | ❌ |
| Smart DJ store | `PlaybackController.smartShuffleOrder:245` | 🟡 |

---

## 7. Marketplace & commerce (gemstone + `znet_serv.dll`)

Device: `GemMarketplace*`, `GemCart*`; `znet_serv` endpoints
(`album?q=%s`, `/albums?orderby=ReleaseDate`, `/recommendations/artists`,
`chart/zune/tracks`), Passport sign-in (`SignInRequest/Response`,
`urn:passport:compact`), usage reporting (`usageReport`, `SEID_Stats`).

| Device | Dorado-HD | Status |
|---|---|---|
| Marketplace catalog/search/artwork client | frozen 62-entry `data/official/OfficialCatalog.kt`; `MarketplaceScreens.kt:322` | ❌ |
| Music genres / top charts | "featured" only | ❌ |
| Game detail + screenshots | catalog rows | ❌ |
| Cart / purchase / confirm | — | ❌ (N-A candidate) |
| Sign-in / subscriptions | Last.fm + cloud OAuth only | ❌ |
| Usage/telemetry | — | ❌ (privacy: N-A) |

---

## 8. Social / inbox / identity

| Device | Dorado-HD | Status |
|---|---|---|
| Social feed | `MediaScreens.kt:632` (mock); cloud social service exists | ✅/mock |
| Inbox list/details (`GemInbox*`) | `ui/apps/mocks/Mocks.kt:90` | ❌ |
| User card (`GemUserCardScene`) | — | ❌ |
| WMA streaming / DRM (`ZWmtStreamer.dll`, `XDRM*`) | not supported | ❌ (N-A) |
| Credentials/DRM store (`zcredentials_serv`) | no keyvault | ❌ (N-A) |
| MTPZ/UPnP identity (`/Device.xml`, `urn:…mtpz:1`) | custom LAN sync (`sync/`) | 🟡 |

---

## 9. Settings & device (gemstone `GemSetting*` + `zconfig_serv`)

| Device | Dorado-HD | Status |
|---|---|---|
| Settings root / About / Storage | `SettingsScreen.kt:43,254,124` | ✅/🟡 |
| Legal | footnote `SettingsScreen.kt:261` | 🟡 |
| Screen lock / PIN lock | `LockShade` only | ❌ |
| Set time / lock-on | OS-managed / wake shade | N-A |
| Wi-Fi info (`GemSettingWifiInfoScene`) | — | ❌ |
| TV-out selector | — | ❌ (hardware) |
| Config store (`zconfig_serv`: `SwapEffect`, `ImageProduction`, …) | N/A (Android) | N/A |
| Cloud/OTA (`UPDATE`, `DColdReboot`) | `cloud/CloudUpdateService.kt` | 🟡 |

---

## 10. Tokens, typography, strings, assets

- **No color hexes or numeric point sizes exist in the UI binaries.** XUI carries
  color/size as *property names* only (`TextColor`, `FillColor`, `PointSize`,
  `Font`, `LineHeight`, `TextScale`). HD's palette/scale are canon/review-derived,
  not byte-citable — the audit cannot verify them against the binaries.
- **Font posture is correct:** HD bundles only OFL Selawik (`design/DoradoTheme.kt:16-20`);
  the device's six Zegoe UI faces live only in the external corpus. **Gap:** the
  docs promise "import your own Zegoe" (`zune-hd-ui-canon.md:20`,
  `DoradoTheme.kt:13`) but **no font-import path exists**. Also Selawik has no
  Black(900)/Medium(500), so `FontWeight.Medium` (`widget/NowPlayingWidget.kt:94`)
  snaps.
- **String parity drift** (device vocabulary HD could adopt):
  - `"Various Artist"` (`gemstone@0x119A4`) vs HD `"unknown artist"`
    (`data/scan/MediaLibraryScanner.kt:64`);
  - `" by "` composition (`gemstone@0x15A60`) vs HD em-dash (`ui/components/Common.kt:291`);
  - empty-state keys **`Empty`**, **`noItems`/`noItems_local`/`noItems_online`**
    (`gemstone@0x12B7C`, `0x14160/0x148BC/0x14840`) vs bespoke HD copy;
  - transport/status words HD lacks: **`Ffwd`**, **`Mute`** (`gemstone@0x19D18/0x19D2C`;
    `zhud_serv@0x419B4C70/84`), `Disconnected`, `Refresh`, `Wishlist`.
- **Docs↔code naming drift (values equal):** `SurfaceTileHover`→`tilePressed`,
  `SurfaceBorderSubtle`→`border`, accent labels; `TYPE_HEADER_CROP_VISIBLE=48`
  undocumented (`design/DoradoTokens.kt:25`).

---

## 11. Deliberately out of scope (device features we do not intend to reproduce)

These are device-era subsystems whose servers/hardware are dead or proprietary;
they are recorded as **N-A**, not gaps:

- Marketplace backend + Passport/Microsoft account + subscriptions.
- DRM license acquisition (`CeDRM_Mgr_*`), PlayReady/WMDRM, secure clock.
- WMA/WMV playback and `ZWmtStreamer` HTTP streaming.
- MTPZ/UPnP device identity and Wi-Fi config (`wzcsapi`).
- TV-out selector; per-app power/display/suspend timeouts (ZAM).
- Usage/telemetry reporting (`usageReport`, `SEID_Stats`).
- ACS touch-controller firmware update (`GemAcsFWScene`).

---

## 12. Prioritized gap register (summary)

Full machine-readable register: [`zune-hd-parity-gaps.json`](zune-hd-parity-gaps.json).

| # | Gap | Axis | Effort | Status |
|---|---|---|---|---|
| 1 | Queue surface (`GemQueueListScene`) | device | S | ❌ |
| 2 | Picture pinch-zoom/pan (`GemPictureTouchClientScene`/`GemZoomTouch`) | device | M | ❌ |
| 3 | Now Playing scrubber + elapsed/remaining | device | S | ❌ |
| 4 | Kinetic model re-derivation (dt-scaled; fix `0.95` provenance) | device | M | 🟡 |
| 5 | Library search UI (wire existing `search()`) | device | S | ❌ |
| 6 | HUD dimmer/power-off + status OSD | device | M | ❌ |
| 7 | EQ presets | device | M | ❌ |
| 8 | Share / `ACTION_SEND` + Zune-Card export | device | M | ❌ (M5) |
| 9 | Audiobooks | device | L | ❌ |
| 10 | Marketplace search/top/genres | device | L | ❌ |
| 11 | Built-in playlists | device | M | 🟡 |
| 12 | Inbox + user card | device | M | ❌ |
| 13 | PIN/screen-lock + Wi-Fi info | device | M | ❌ |
| 14 | String parity (`Various Artist`, ` by `, `noItems`, `Ffwd`, `Mute`) | device | XS | 🟡 |
| 15 | Font-import path (or drop the claim) | doc/device | S | ❌ |
| 16 | Velocity cap + item snap | device | S | ❌ |

---

## 13. Corrections to existing docs

- **`zune-hd-touch-settings.md`**: replace the `[0x1C]=0.95` retention provenance
  with the resolved integrator model (§3); add the recoverable HUD timer
  constants; add the XUI touch property-name vocabulary; fix
  `XuiProcessMultiTouchMessage` VA to `0x4181F100`; correct §5 "not recoverable"
  claims (dwell timers **and** field names are now recoverable).
- **`zune-hd-ui-canon.md` §6**: cite the device tick (62.5 Hz) and the
  `pos += (dt/1000)·v` model; do not cite `[0x1C]`.
- **`design/DoradoMotion.kt`**: mark `0.95/0.94` as empirical approximations of the
  device glide, not device constants.
- **Label parity**: canon-cite `"Various Artist"`, the ` by ` separator, and the
  `noItems`/`Empty` empty-state vocabulary before changing display copy.

## 14. Roadmap delta

Add to `parity-roadmap.md`: queue surface, picture touch/zoom, NP scrubber,
library-search UI, HUD dimmer/status, built-in playlists, inbox/user-card,
PIN/Wi-Fi settings, string-parity pass, font-import decision; and annotate
marketplace/DRM/Passport/MTPZ as documented N-A (§11).
