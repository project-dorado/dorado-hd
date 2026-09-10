# Dorado-HD on-device bug hunt — 2026-09-10

**Build:** `com.heretek.dorado_hd.debug` v0.1.0 (applicationId suffix `.debug`), targetSdk 37.
**Device:** Android Virtual Device `xune-test`, Android 16 (API 36), x86_64.
**Method:** installed the debug APK, pushed `../test-media/` via `adb`, indexed it with
MediaStore, ran the app, exercised every screen, ran `monkey` stress, and cross-checked
static analysis. Evidence = reproduced crashes (`logcat -b crash`) and screenshots.

Severity: **S1** process death / unusable core feature · **S2** broken feature ·
**S3** UI/UX / canon deviation.

---

## S1 — App never requests media permissions → empty library (Android 13+)

- `app/src/main/AndroidManifest.xml:5-13` declares no `READ_MEDIA_AUDIO`,
  `READ_MEDIA_VIDEO`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`, or
  `READ_EXTERNAL_STORAGE` (maxSdk 32), and there is **no runtime permission request code**.
- Yet the collection is built from `MediaStore` (`data/scan/MediaLibraryScanner.kt`,
  `ui/screens/MediaScreens.kt`, `data/repo/DeviceLinkRepository.kt`,
  `ui/screens/MarketplaceScreens.kt`).
- **Repro:** pushed 5 audio tracks + 1 video + 1 image; `content query` confirms
  `is_music=1` and `duration > 30000`; `refresh collection` → app Room `tracks` count = 0;
  Music/Videos/Pictures all empty. `appops set … READ_MEDIA_AUDIO allow` does not help
  (permission not declared ⇒ not grantable).
- The SAF **Import music** flow *does* work (it holds a persisted tree URI), which is why the
  app can populate only via that workaround.
- **Impact:** on every Android 13+ device the entire collection is empty for a normal user.
  The README's MediaStore-based promises (albums, art, similarity, sync) are unreachable.

## S1 — `IllegalArgumentException: Invalid token LIMIT` crashes four screens

Appending `LIMIT n` to the `sortOrder` argument of `ContentResolver.query(...)` is rejected by
modern MediaProvider. Four screens pass it:

| Screen | Sort-order string | Reproduced stack |
|---|---|---|
| Videos | `MediaScreens.kt:96` | `MediaScreensKt$VideosScreen$1$1$1.invokeSuspend(MediaScreens.kt:87)` |
| Pictures | `MediaScreens.kt:351` | `MediaScreensKt$PicturesScreen$1$1$1.invokeSuspend(MediaScreens.kt:343)` |
| Marketplace → videos | `MarketplaceScreens.kt:101` | `MarketplaceScreensKt$MarketplaceVideos$1$1$1.invokeSuspend(MarketplaceScreens.kt:93)` |
| Settings → device link | `DeviceLinkRepository.kt:102`, `:135` | `DeviceLinkRepository.queryVideos(DeviceLinkRepository.kt:93)` |

All four reproduce as `FATAL EXCEPTION: main` → process death. `monkey` aborted after 120
events with this same crash, so it is trivially hit by random input. Correct fix: use the
`Bundle` query-args overload (`ContentResolver.QUERY_ARG_LIMIT`, API 30+) or simply cap while
reading the cursor (minSdk 26 safe).

## S1 — Exported deep link crashes on any opaque `doradohd:` URI

`MainActivity.kt:33-38`:
```kotlin
val data = intent?.data ?: return
if (!data.scheme.equals("doradohd", ignoreCase = true)) return
val params = data.queryParameterNames.associateWith { ... }   // line 36
```
`queryParameterNames` throws `UnsupportedOperationException: This isn't a hierarchical URI`
for an opaque URI. `MainActivity` is `exported="true"`, so any app can crash it:
`adb shell am start -n com.heretek.dorado_hd.debug/com.heretek.dorado_hd.MainActivity -d 'doradohd:opaque'`
→ `RuntimeException: Unable to start activity … UnsupportedOperationException`. Security/DoS issue.

## S1 — Duplicate `LazyRow` key crash in Quickplay history (found by monkey)

`ui/screens/HomeScreens.kt:314` keys the Quickplay card row by
`"${it.kind}:${it.refId}:${it.label}"`. Quickplay **History** legitimately stores the same track
multiple times (one row per play), so the key collides:
`IllegalArgumentException: Key "TRACK:20:…" was already used` → process death. Reproduced by
`monkey` at event 1293. Fix: index-qualified key (`itemsIndexed`).

## S2 — Calculator has no `+` key

`ui/apps/MiniAppsUtilities.kt:124-142` — `BASIC_KEYS` and `SCIENTIFIC_KEYS` contain
`/ * -` but no `+`. Addition is impossible.

## S2 — Drum-machine step toggles never update the UI

`ui/apps/PianoDrum.kt:97` stores the grid as a plain `Array<BooleanArray>` and toggles it at
`:158`. Plain arrays are not Compose snapshot state, so tapping a step does not recompose —
the grid appears frozen. (Playback still works because the audio loop reads the same instance.)

## S2 — Alarm "edit time" duplicates the alarm instead of editing

`ui/apps/MiniAppsUtilities.kt:788-792` — the `edit time` action disables the existing alarm and
calls `graph.alarms.add(alarm.copy(id = 0, hour = (alarm.hour + 1) % 24))`, i.e. it adds a new
alarm one hour later. Resource: `alarm.id.toInt()` (`:776`) also truncates the Long id used as a
`PendingIntent` request code.

## S3 — Home menu shows an alphabet rail

The 9-entry Home menu renders `KineticList`'s right-edge A–Z rail (`I M P R S V`). Canon §3.5
scopes the rail to long lists; the device home menu had none. It adds clutter and a mis-tap
target over the cropped labels.

## S3 — Blank art/tiles (no placeholder or fallback)

`design/components/AlbumArt.kt` draws only `colors.tile` + a 0.5 dp border and then an
`AsyncImage` with no error/placeholder. With no artwork (SAF-imported tracks have no
`albumArtUri`) the tiles are effectively invisible on black. The marketplace "installed"
mini-app tiles (`MarketplaceScreens.kt`) are likewise blank squares. In the marketplace catalog
the alphabet rail overlaps the `open` / `unavailable` labels on the right.

## S3 — Landscape/adaptive crowding

In landscape adaptive mode the Home watermark (`design/…HomeScreens`) overlaps the last visible
menu row, and the Now Playing transport overlay crowds prev/next into the bottom corner. Device
mode (480×272) was not re-verified here.

---

## Verified NOT bugs (avoided false positives)

- The Now Playing "duplicate metadata + small square" is the **screensaver** layer (by design):
  drifting metadata + 56 dp art at `BottomEnd` (`NowPlayingScreen.kt:404-450`). Swipe-to-skip is
  intentionally inert while the screensaver is up.
- Back-stack growth: `DoradoNav.push` has no dedup, but no unbounded duplication was observed
  in the exercised flows.

## Tooling notes

- `adb shell uiautomator dump` crashes (`UiAutomationService … already registered`) while the
  mobile-mcp automation session is active; use screenshots + coordinate taps.
- mobile-mcp's element tree intermittently returned a stale screen for this Compose app.

## Fix status — applied and re-verified on the same emulator

| Bug | Fix | Re-verification |
|---|---|---|
| S1 media permissions | `AndroidManifest.xml` declares `READ_MEDIA_{AUDIO,VIDEO,IMAGES}` + `READ_MEDIA_VISUAL_USER_SELECTED` + `READ_EXTERNAL_STORAGE (maxSdk 32)`; `MainActivity` requests them and refreshes the library on grant | Fresh install → permission dialog appears; after Allow, Room `tracks` = 5 straight from MediaStore (no SAF import); album art resolves |
| S1 `LIMIT` crashes (×4) | Removed `LIMIT` from the `sortOrder` string and capped while reading the cursor (`MediaScreens.kt` videos/pictures, `MarketplaceScreens.kt`, `DeviceLinkRepository.kt` ×2) | Videos, Pictures, Marketplace→videos and Settings→device link now open with no crash |
| S1 opaque deep link | `MainActivity.handleCloudRedirect` rejects non-hierarchical URIs + `runCatching` | `am start … -d 'doradohd:opaque'` no longer crashes |
| S1 duplicate lazy key | `HomeScreens.kt` Quickplay row uses `itemsIndexed` keys | `monkey` 2500 events: **no crashes** (previously aborted at event 120/1293) |
| S2 calculator `+` | Added `+`/`±`/`C`/`bksp`/`=` row (`MiniAppsUtilities.kt`) | `+` key visible and wired to the engine (which already supports `+`) |
| S2 drum grid | Grid is now a `SnapshotStateList<Boolean>` (`PianoDrum.kt`) | Tapping steps lights them (kick/snare/hat) |
| S2 alarm edit | `AlarmDao.setTime` + repo + UI update in place (`MiniAppsUtilities.kt`) | DB shows **1** alarm, hour bumped 7→8, no duplicate |
| S3 home alphabet rail | `KineticList(showAlphabet = false)` on the home menu | Home renders without the rail |
| S3 blank art | `AlbumArt` shows an accent placeholder when `model == null` | MediaStore art now resolves; placeholders visible when absent |

**Verification:** `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` — all green
(design-invariant audit included). Final `monkey -p com.heretek.dorado_hd.debug 2500` completed
with zero crashes.

## Remaining / lower priority (not fixed)

- `MediaScreens.kt:221` video strip is display-only (no seek gesture); video `ExoPlayer` can play
  concurrently with music; `WebView` in `InternetScreen` is not destroyed on dispose.
- `MediaScreens.kt` Pictures "favorites" pivot tap is a no-op; Pictures "albums" pivot uses a
  non-lazy `Column`.
- Missing empty states (Music pivots, Genre, PodcastFeed, Notes/Alarm/Calendar).
- Podcast RSS fetch runs on the calling dispatcher (`Podcasts.kt:131,159` — blocking I/O on main).
- Cloud sign-in performs blocking `HttpURLConnection` work off `Dispatchers.IO` (`CloudSignIn.kt`,
  `CloudHttp.kt`) and can throw on malformed token JSON.
- Game AI (chess depth-3, sudoku generation) runs on the main thread → jank/ANR risk.
- Metronome BPM changes don't retime the running loop; multiple duplicate dead long-press handlers.
- `monkey` needs a clean re-run after the last fix plus deeper per-game stress (chess/sudoku).
