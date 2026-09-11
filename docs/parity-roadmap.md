# Dorado-HD Parity Roadmap

Planning artifact for the next parity passes. Mirrors the structure of the
sibling [dorado](https://github.com/project-dorado/dorado) `docs/parity/`
corpus. Every gap below is backed by a code location so the roadmap stays
falsifiable.

Ground rules (AGENTS.md): the canon is law — change
`docs/zune-hd-ui-canon.md` first with a source citation, then the code.
`DesignInvariantTest` audits zero corner radius, no Material chrome leakage,
hearts-not-stars, and the motion rules. Features the Zune HD never shipped
must be labelled **post-device extension** (canon §10), never canon.

## 1. Verified baseline

A code-verified snapshot (not README claims):

| Surface | State | Anchor |
|---|---|---|
| Home (9 entries, kinetic, edge-crop) | ✅ | `ui/screens/HomeScreens.kt`, `design/components/KineticList.kt` |
| Quickplay (3D parallax, Smart DJ lane) | ✅ | `ui/screens/HomeScreens.kt` |
| Crossbar pivots (music/artist/marketplace/videos/pictures/podcasts/radio) | ✅ | `design/components/CrossbarBar.kt` |
| Now Playing (scrub, transport overlay, swipe-skip, screensaver) | ✅ | `ui/screens/NowPlayingScreen.kt` |
| Tri-state heart rating | ✅ | `media/PlaybackController.kt` (`setRating`) |
| Lock/wake shade (WallpaperManager) | ✅ | `ui/DoradoRoot.kt:115`, `design/components/LockShade.kt` |
| 9 media pivots + Internet + Social(mock) | ✅ | `ui/nav/DoradoNav.kt:14-37` |
| 29 mini-apps (12 util + 9 games + 8 mocks) | ✅ | `ui/apps/DoradoApps.kt` |
| Frozen 62-entry marketplace catalog | ✅ | `data/official/OfficialCatalog.kt` |
| Design-invariant scanner | ✅ | `app/src/test/.../DesignInvariantTest.kt` |

## 2. Parity axes

1. **On-device parity** — every behavior the Zune HD itself had (widget,
   lock screen, the M4–M7 wishlist).
2. **Cross-project parity** — interop with the sister desktop `dorado`
   (Wi-Fi sync, and eventually physical Zune HD over USB).
3. **Modern parity** — what great Zune-successor projects solved that the
   2009 device could not (dynamic mixes, on-device recommendation,
   scrobbling, lyrics).

## 3. Verified gap inventory

| Gap | Evidence (current code) | Axis | Effort |
|---|---|---|---|
| Home-screen widget | no `appwidget`/Glance; `AndroidManifest.xml` has no receiver | device | M |
| Wi-Fi sync with Dorado | no `sync/` package; desktop already has `SyncModels.cs`, `SyncEngine.BuildPlan`, `IDeviceTransport` + `SimulatedDeviceTransport` | cross-project | L |
| USB MTP/MTPZ to a physical Zune HD | stretch; spec is in `dorado/.agents/skills/zune-hardware-sync` (MTPZ handshake, F-marker ZMDB, PPP interceptor `0x922C/0x922D`, host `192.168.55.100` / device `.101`) | cross-project | XL |
| Long-press → pin incomplete | was `onLongClick = {}` across `DetailScreens`/`MediaScreens`/`MusicScreen`/`Podcasts`/`MarketplaceScreens`; now pin + shared `trackMenuActions` | device (M4) | ✅ done |
| Play-next / Quicklist action | was absent; now `trackMenuActions` adds play-next to every track row | device (M4) | ✅ done |
| Marketplace curated pivots | was `EmptyPivot("videos","coming soon")`; now library-backed videos/podcasts | device (M4) | ✅ done |
| Games pivot in Marketplace | was absent; now a 5th crossbar pivot over installed + frozen catalog games | device (M4) | ✅ done |
| Share / Zune-Card artwork export | no `ACTION_SEND` anywhere | device (M5) | S |
| EQ presets / crossfade | 0 matches; `PlaybackController` has shuffle/repeat/rating only | modern (M6) | M |
| Related-artists pivot is a thin empty-state | honest text at `DetailScreens.kt:321-335` ("only artists who share genres… a thin slice") | modern | M |
| Scrobbling / lyrics / tag editor | 0 matches; `dorado` already uses Last.fm + LRCLIB | modern | M |
| Live-radio pause-and-cache | `Radio.kt` dial/presets only | device (M6) | M |
| Canon drift | §3.1 still says only `music`/`settings` functional; §5 still calls the in-app shade a stretch goal | doc | XS |

## 4. Milestones

### M4 — Mini-app + marketplace completeness ✅ *complete*

- Wire `onLongClick → QuickplayRepository.pin(...)` at every empty list site
  (Videos, Pictures, podcast feeds + episodes, marketplace albums + installed
  apps, artist → songs, playlist detail, genre). Picture buckets and buttons
  had no meaningful action, so their dead `onLongClick` was removed instead.
- Add **play-next** to the long-press context menu (the Quicklist
  replacement); back it with `PlaybackController.enqueue` (`PlaybackController.kt:132`).
- Replace `EmptyPivot("coming soon")` in Marketplace with curated slices of
  the user's library.
- Add a `games` pivot using `OfficialCatalog.GAMES`.
- **Accept:** no empty `onLongClick = {}` remains under `ui/screens/`
  (mini-app internals are out of scope); track rows use the shared
  `trackMenuActions` (pin + play-next); the PinKind coverage test names every
  Quickplay surface.

### M5 — Social + discovery

- Now Playing system share sheet (`ACTION_SEND`, artist + title + album).
- Zune-Card-style artwork export (PNG/JPEG).
- On-device podcast search (curated top charts).
- Wire Smart DJ as a tap-to-build-mix action in the music crossbar.

### M6 — Community wishlist

- EQ presets (rock / acoustic / hip-hop / pop / classical / electronic).
- Crossfade on `play()` (settings toggle).
- Live-radio pause-and-cache ("Live / Rewind ±5 min") — capability-gated on a
  real-time streaming source.
- Lock-screen Now Playing art + controls (richer than the default Media3
  notification).
- Soft keyboard "bulge around the finger" affordance (Wikipedia-cited).

### M7 — Design-token + invariant-test hygiene

- Tokenize remaining raw dp sizes.
- Unit tests for `KineticList` A–Z popup, `HomePages` parallax math,
  `StaggerEntrance` clamp.
- Keep `DesignInvariantTest` catching named `Color.Black/White/…`.

### M8 — Device Link (Wi-Fi sync with Dorado) — *TODO (8.1–8.3 landed, 8.2b/8.4 pending)*

Mirror the desktop Phase 9 semantics on the phone side, 1:1 with
`Dorado.Domain/Models/SyncModels.cs`:

- ✅ **M8.1 — pure-JVM core.** New `sync/` package (`SyncModels.kt`,
  `SyncEngine.kt`): `SyncMode`, `SyncCategoryRule`, `SyncGroup`, `SyncPlan`,
  `TransferItem`, `DeviceSnapshot` matching the C# contract, plus
  `buildDefaultGroup()` and `buildPlan()`. `SyncEngineTest` locks the output
  against goldens generated from the real desktop engine (a throwaway C#
  harness referencing `Dorado.Application`).
- ✅ **M8.2 — transport core.** `DeviceTransport` interface +
  `SimulatedDeviceTransport` (mirrors the desktop simulator: 32 GB seed,
  system-partition accounting, category/title ordering) + `SyncEngine.applyPlan`
  (removals first, progress reporting), all unit-tested. `SyncProtocol` holds
  the JSON-RPC 2.0 contract constants and the 6-digit pairing-code generator.
- ⬜ **M8.2b — live transport.** mDNS (`NsdManager`, `_dorado-sync._tcp`), TLS
  socket, pairing handshake. The desktop side now exists (`SyncEndpointHost` +
  `SyncTcpServer`, LAN port + pairing code in settings); the HD client is the
  remaining half.
- ✅ **M8.3 — Device view.** `DeviceScreen` (Settings → device link): pairing
  state + 6-digit code, storage gas gauge, the four sync rules (tap to cycle
  presets), guest-session toggle, dry-run "what will sync" manifest,
  apply-to-device, and reverse copy-back → pending-imports queue. Backed by
  `DeviceLinkRepository` (source = phone library; target =
  `SimulatedDeviceTransport`). The four rule strings persist in `DoradoSettings`.
- **Accept:** golden test — `buildPlan` output equals the desktop `SyncPlan`
  for identical inputs; JVM round-trip test; `SyncRulePresets`/mapping tests.

**Cross-repo behavior (the goldens lock it in):**

- ✅ **Fixed upstream in `dorado`** this pass, mirrored here, both sides
  re-goldened:
  - `REMOVE` now carries the device item's `SizeBytes`, so freed space backs
    later adds — `totalRemoveBytes` is meaningful and the desktop Device
    view's "projected free" is now correct (was always 0).
  - `NewestCount` now parses the first digit run anywhere — `"Newest 25 Items"`
    → 25 (was null). The desktop suite is **189/189 green**; `SyncEngineTest`
    is green against the new fixtures.
- ⚠️ **Intentional desktop parity** (do not change on one side only):
  - The **picture** rule inherits the **video** rule's mode
    (`BuildPlan_KeepsExisting_AndRemovesStale` asserts this).
  - A successful `KEEP` carries only title + size (no detail/source path).

The golden generator is vendored at `tools/sync_golden/` (C# console
referencing `dorado/src/Dorado.Application`); re-run it after any desktop
`SyncEngine` change.

### M9 — Modern Listening (Rune-inspired) — *in progress*

- ✅ **M9.1 — audio feature + similarity core.** Ported the sibling desktop
  Phase 13a (`AudioFeatures`, `AudioFeatureExtractor`, `AudioAnalysisService`,
  cosine ranking) into `analysis/`. The artist page's `related` pivot now ranks
  by on-device audio similarity — the device genuinely sourced this from
  Last.fm/friends, so a real signal is the honest fix — with genre overlap as
  fallback. Pure JVM, unit-tested.
- ✅ **M9.2 — Dynamic Mix.** `DynamicMix`/`DynamicMixService` (mirrors the
  desktop): SimilarToTrack / SimilarToAlbum / SimilarToFavorites /
  PlaylistsIncludingArtist, plus TopPlayed (reads persisted play counts via
  `PlayCountStore`; surfaced as a **play top played mix** row in Quickplay).
  Surfaced as **start mix**
  in every track's long-press menu and a **play favorites mix** row in
  Quickplay.
- ✅ **M9.3 — DSP + persistence.** `FeatureMath` (radix-2 FFT, RMS,
  zero-crossing rate, loudness, spectral centroid) + hybrid
  `AudioFeatureExtractor.fromPcm` (DSP-refined energy/centroid/acousticness
  over the metadata prior); `PcmDecoder` (MediaExtractor/MediaCodec) +
  `PcmFeatureAnalyzer` glue; Room `track_features` (DB v3 + migration) behind
  `FeatureStore`/`RoomFeatureStore`; `AudioAnalysisService` preloads on start
  and persists analyses. Trigger via Settings ▸ device ▸ *analyze library*.
  DSP math and the store are tested; the decoder is best-effort glue that
  falls back to the metadata prior.
- ✅ **M9.4 — scrobbling + lyrics.** Last.fm `track.scrobble` with the
  documented `api_sig` signing (`LastFmSignature`), a durable offline queue
  (Room `scrobble_queue`, DB v4) behind `ScrobbleStore`/`RoomScrobbleStore`,
  and `ScrobbleService` (order-preserving flush, stops at the first failure).
  `PlaybackController` queues a scrobble once a track passes the Last.fm
  threshold (half, or 4 min). Settings ▸ *scrobbling* holds the opt-in, the
  user-supplied API key/secret/session key, and a manual flush. Lyrics via
  LRCLIB (`LrcLibService` + pure `LrcLibParser`) surfaced from Now Playing.
  Signing, queue semantics and parsing tested; the HTTP clients are glue.
- ✅ **M9.2b — play counts.** Persisted per-track counts (`play_counts`, DB v5 +
  `MIGRATION_4_5`, `PlayCountStore`/`RoomPlayCountStore`), recorded at the
  playback transition behind the same threshold as scrobbling, so *Top Played*
  is real.
- All of the above are canon §10 post-device extensions.

### M10 — Always-on surfaces

- **Glance widget**: Zune-style Now Playing + transport; add the appwidget
  receiver to `AndroidManifest.xml`. RemoteViews are outside Compose, so keep
  widget XML zero-corner-radius to honor the invariant's spirit.
- Richer lock-screen Now Playing art/controls.
- Optional sleep timer (post-device extension).

### M11 — Hardware stretch (capability-gated)

- USB-host MTP/MTPZ transport, F-marker ZMDB parser, PPP HTTP interceptor
  per `zune-hardware-sync`. Likely N-A; document rather than ship. Never
  bundle Microsoft binaries.

## 5. OSINT source map (inspiration, not code)

| Source | What to borrow | License posture |
|---|---|---|
| [losses/rune](https://github.com/losses/rune) | Dynamic **Mix**, on-device audio analysis → recommendations, scrobbling, lyrics, tag editor | MPL-2.0 → **behavioral only, never copy** |
| [ZuneDev/Xune](https://github.com/ZuneDev/Xune) | Iris UI-library semantics (motion/typography cross-check) | reference only |
| [zunes/Zune-Research](https://github.com/zunes/Zune-Research) | firmware / ZCP / OpenZDK ground truth for canon citations | docs reference |
| [ZuneDev/ZuneNet](https://github.com/ZuneDev/ZuneNet) | self-hostable social backend shape (beyond the mock) | reference only |
| [zunes/ZuneDiscordRPC](https://github.com/zunes/ZuneDiscordRPC) | now-playing presence idea | reference only |
| [dumbie/ZuseMe](https://github.com/dumbie/ZuseMe) | Last.fm scrobble pattern | reference only |
| [cdtinney/spune](https://github.com/cdtinney/spune) | visualizer for the screensaver backdrop | reference only |
| [whoozle/android-file-transfer-linux](https://github.com/whoozle/android-file-transfer-linux) | MTP client reference for the stretch transport | reference only |
| sibling `dorado` | `SyncModels.cs`, `SyncEngine`, `IDeviceTransport`, `Plugins.Protocol` | MIT — same project family |

## 6. Canon + doc changes required first

1. **Canon §3.1** — functional home entries are now all 9 (`music…settings`);
   add a Sept-2009 source citation.
2. **Canon §5** — promote the in-app shade from "stretch goal" to implemented
   (WallpaperManager-backed; `DoradoRoot.kt:115`), citing ITPro Today.
3. **New canon §10 — Post-device extensions** — EQ, crossfade, scrobble,
   lyrics, Mix, widget, sleep timer. Label so they are never mistaken for
   device-canon behavior.

## 7. Verification gates (every milestone)

- `./gradlew assembleDebug test lint` green (JDK 21 / AGP 9.4).
- `DesignInvariantTest` 0 violations; no `RoundedCornerShape`, no `spring(`,
  no raw hex / named `Color.*` in `ui/`.
- New sizes/colors/durations go through `DoradoTokens` / `LocalDoradoColors`.
- New pure-logic engines unit-tested (the games set this bar).

## 8. Recommended first sprint

1. **Doc sync** (XS) — canon §3.1/§5 + new §10.
2. **M4 closeout** (S–M) — pin wiring + play-next + games/curated pivots.
3. **M8.1** (M) — land the Kotlin `SyncModels` mirror + `SyncEngine.buildPlan()`
   with golden tests against the desktop semantics (pure JVM, unblocks M8).

Front-loads cheap device-parity wins, then the one architectural gap that
crosses into the sister project.
