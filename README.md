# Dorado-HD

<div align="center">

# 📱 Dorado-HD
**The Zune HD, reborn as an Android music player — sister app to Dorado**

[![Android](https://img.shields.io/badge/Platform-Android%209%2B-3DDC84)]()
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)]()
[![Audio](https://img.shields.io/badge/Audio-Media3%20%2F%20ExoPlayer-FA2A55)]()
[![License](https://img.shields.io/badge/License-MIT-111111)]()

</div>

---

Dorado-HD is a faithful re-creation of the **Microsoft Zune HD on-device
interface** (2009) for modern Android phones: white-on-black typography-first
Metro design, a text menu cropped at the screen edge, Quickplay parked
"left and rear", crossbar navigation, swipe-to-skip Now Playing floating
over artist photography, and the tri-state heart rating.

It is the **device-side sibling** of [Dorado](https://github.com/project-dorado/dorado)
(the Zune *desktop* re-implementation). Just as the Zune HD was the device and
Zune 4.8 was the desktop, Dorado-HD is the phone and Dorado is the desktop.

## ✨ What's implemented

The current port faithfully reproduces the device across **home + quickplay
+ 5 media pivots + 29 installed mini-apps**, plus the canon-defined
companion apps and dead-service mock shells — design-language-correct,
tokenized, and audited by Robolectric unit tests and a source-level
design-invariant scanner.

### Shell — home & quickplay (canon §3.1–§3.2)

- **Home menu** with the full canonical 9 entries — `music · videos ·
  pictures · radio · marketplace · social · podcasts · internet ·
  settings` — lowercase, oversized, right-edge cropped, **kinetically
  scrollable**. The "dorado hd" watermark sits at the bottom-left at the
  canon's `0.08` textWatermark opacity.
- **Quickplay** slides out as a 3D-parallax "left and rear" panel using
  `rotationY` + `cameraDistance` (the device's literal visual depth). Inside:
  Now Playing card, **Smart DJ lane** (hearts-first, broken-skipped
  one-tap build), Pins, History, New.
- **Lock shade** with a translucently-tinted, **WallpaperManager-backed**
  background that fades up under the time/date when you drag (canon §5).
- **Device mode** letterboxes the whole UI in the canonical 480×272 canvas.

### Crossbar nav (canon §3.3)

Every collection screen now uses a horizontal `CrossbarBar` pivot strip
with right-edge-cropped labels. The crossbar pivot transition rides the
spec'd 380 ms deceleration via `DoradoMotion.PIVOT_SLIDE_MS`.

- **Music** — `albums · artists · playlists · songs · genres` (the real
  device order).
- **Artist** — `albums · songs · bio · photos · related` (canon §4). Bio
  via Wikipedia REST summary keyed by artist name, photos from
  MusicBrainz.
- **Marketplace** — `music · videos · podcasts · apps` crossbar with the
  **Apps pivot** listing every installed mini-app plus the frozen
  official catalog (62 entries from the ZuneRedux archive).
- **Videos** — `all · movies · tv · music videos`.
- **Pictures** — `all · albums · date taken · favorites`.
- **Podcasts** — `audio · video · subscriptions · episodes`.
- **Radio** — `FM · HD · presets`.

### Music + Now Playing (canon §3.4 + §4)

- **Alphabet rail** with **full A–Z popup** (canon §3.5) — separated
  right-edge rail (Gizmodo's mis-tap hazard fixed).
- **Now Playing** with explicit back arrow; **tappable song title**
  → album track list, tappable artist → artist page, swipe ← → to skip.
- **Transport overlay** (tap empty space) — play/pause center, prev/next
  sides, volume up/down at top/bottom, vertical-swipe volume control.
- **Smart DJ shuffle** — hearts prioritized, broken hearts skipped
  (canon §4, Microsoft press release Sept 15 2009).
- **Screensaver** — tokenized 5 s idle, surfaces artist/track/album/duration
  on a slow Ken Burns artist photo or palette wash; **tap-to-show-overlay**
  combines dismiss + overlay in one gesture (matches the device).
- **Tri-state heart rating** (heart / broken heart / none).
- **Detail scaffold** has both a **faint right-edge back arrow** *and* the
  cropped header — either returns to the parent destination. This mirrors
  the device's Now-Playing back-arrow affordance and Gizmodo's
  continued complaint that the cropped header was hard to discover.

### Settings (canon §3.6)

- 5-color **accent picker**.
- **Display mode** toggle, **artist photos** toggle, **artist photo
  source** prompt (URL template with `{mbid}`).
- **Library location** showing the Room DB path + live MediaStore track
  count.
- **Import music** via SAF tree picker with `takePersistableUriPermission`.
- **Watch media store** (opt-in) registers a debounced `ContentObserver`.
- **Refresh collection** + **scan status** (last scan timestamp, scanned/
  removed counts, last import result).
- **About** footer with the canonical Selawik↔Zegoe attribution.

### Mini-app platform (canon §8 — 29 apps, behavioral re-implementation)

The `DoradoApps.all` registry is a `by lazy` build so it's populated before
the first UI lookup (including Robolectric tests).

#### Utilities (12)

calculator · notes · stopwatch · metronome · alarm clock · calendar ·
level · piano · drum machine · chord finder · music quiz ·
shuffle by album. PCM-synth piano/drum/metronome; accelerometer level;
Room-backed notes/calendar/alarms; AlarmManager with full-track or
radio wake.

#### Games (9)

solitaire · sudoku · hexic · reversi · **hearts · spades · checkers ·
chess · texas hold 'em**. All seven non-trivial engines are pure-Kotlin
and unit-tested (dealer logic, hand-rank hierarchy, check/stalemate
detection, AI sanity). Chess is alpha-beta depth 3; poker bot is a
tight-passive-bluff heuristic.

#### Mock shells (8)

weather · twitter · facebook · email · messenger · msn money ·
zune reader · zunesocial — era-faithful canned layouts for the dead
marketplace services (canon §8 mock category).

### Reverse-engineering deliverable

`docs/zcp-inventory.md` lists all 62 official Zune HD marketplace
packages with title / GUID / description. Regenerable from
`tools/zcp_inventory.py` against the ZuneRedux `zune-hd-apps`
archive. Binaries are encrypted (AES-ECB, marketplace DRM) and
**never** bundled; metadata only.

### Tests

153 Robolectric unit tests + a design-invariant source scanner that
forbids `RoundedCornerShape`, `spring(`, raw hex `Color(0x…)`, and
named `Color.Black/White/Red/…` constants in `ui/` and `ui/apps/`.

### Build

`./gradlew assembleDebug + test + lint` is green on JDK 21 / AGP 9.4.

---

## 📄 Design canon

`docs/zune-hd-ui-canon.md` is the authoritative interaction spec, compiled
from 2009 reviews and community resources. `docs/design-tokens.md` holds
the portable token set. `docs/zcp-inventory.md` lists the 62 official
Zune HD marketplace packages whose metadata was used to design the
mini-app platform.

---

## 🔒 Licensing posture

- **Dorado** (MIT) — design tokens, concepts. Thank you.
- **MedTune** (MIT) — starting skeleton; see NOTICE.md.
- **MusicIn2001** — Research-Only license: used strictly as a behavioral
  specification. **No code was copied.**
- **Selawik** (SIL OFL 1.1) — Segoe-metric stand-in for Zegoe. Import your
  own Zegoe if you have it.
- The official Zune HD `.zcp` packages (review/Zune HD Apps/) are the
  marketplace archive; their encrypted payloads are **never** bundled, and
  only the manifest metadata (title, GUID, description) is read.
- Zune, Zegoe and the Zune HD are Microsoft trademarks. Dorado-HD is an
  independent homage; nothing Microsoft is bundled.

---

## 📊 Parity summary

| Area | Device has | Dorado-HD has | Notes |
|---|---|---|---|
| Home menu (9 entries, kinetic, edge-crop) | ✅ | ✅ | `LazyColumn` via `KineticList`; right-edge-cropped via `EdgeCropText` |
| Quickplay (parallax reveal) | ✅ | ✅ | 3D parallax `rotationY` + `cameraDistance` |
| Smart DJ (hearts/breaks) | ✅ | ✅ | Surfaced as a lane in Quickplay |
| Lock shade (wallpaper behind) | ✅ | ✅ | Real `WallpaperManager.getDrawable()` |
| Now Playing (scrub, transport, screensaver) | ✅ | ✅ | Scrubber + transport + screensaver; tap combines dismiss + overlay |
| Artist bio / photos / related | ✅ | ✅ | Wikipedia REST + MusicBrainz; related pivot ranks by on-device audio similarity (M9) |
| Music crossbar (5 pivots) | ✅ | ✅ | 380 ms deceleration per `DoradoMotion.PIVOT_SLIDE_MS` |
| Album art palette wash | ✅ | ✅ | |
| Tri-state heart rating | ✅ | ✅ | Persisted in Room |
| Videos (with player) | ✅ | ✅ | MediaStore + ExoPlayer surface view + scrubber + transport |
| Pictures (gallery) | ✅ | ✅ | MediaStore buckets + viewer; pinned-pictures open in Quickplay |
| Podcasts (RSS) | ✅ | ✅ | `XmlPullParser` RSS reader, episodes feed, plays via Media3 |
| Radio (HD / FM) | ✅ | ✅ | FM = user stations, HD = presets; canvas dial + drag |
| Internet browser | ✅ | ✅ | WebView + URL bar + in-page back/forward + bookmarks |
| Marketplace (apps pivot) | ✅ | ✅ | Installed registry + frozen 62-entry catalog |
| Mini-app platform | ✅ | ✅ | 29 apps (12 utilities + 9 games + 8 mocks) |
| Crossbar pivot nav | ✅ | ✅ | All collection screens have horizontal pivot pivots |
| Screensaver / now-playing art | ✅ | ✅ | |
| Widget (home-screen) | ✅ | ✅ | Glance `NowPlayingWidget`: Now Playing + transport |
| Wi-Fi sync with Dorado (sibling project) | ✅ | ◐ | M8.1–8.3: engine + transport core + Device view (simulated target); LAN pairing pending M8.2b |
| USB MTPZ sync to physical Zune HD | ✅ | ❌ | Stretch goal — see roadmap |

### Design-invariant scanner (enforced by `DesignInvariantTest`)

| Rule | Source location | Check |
|---|---|---|
| No `RoundedCornerShape` | anywhere | grep `RoundedCornerShape` |
| No `spring(` in nav | `ui/`, `ui/apps/` | grep `spring` |
| No raw hex color literals | `ui/`, `design/components/`, `ui/apps/` | regex `Color\(0x[0-9A-Fa-f]{8}\)` |
| No named `Color.Black/White/Red/…` | `ui/`, `design/components/`, `ui/apps/` | regex `\bColor\.(Black\|White\|Red\|…)\b` (games + `PianoDrum.kt` allowlisted) |
| 62-entry catalog invariant | `OfficialCatalog.all.size == 62` | unit test |
| `DoradoApps` registry resolves every `installedId` | unit test | |

### Token table (enforced via `DoradoTokens` consumption)

| Token | Value | Used by |
|---|---|---|
| `CANVAS_WIDTH × CANVAS_HEIGHT` | 480 × 272 | `DeviceCanvas` letterbox |
| `EDGE`, `CROSSBAR_LEAD`, `CROSSBAR_HEIGHT`, `ROW_HEIGHT` | 16 / 24 / 34 / 40 | screen padding, crossbar row |
| `ALBUM_TILE` / `APP_TILE` / `GRID_GUTTER` | 92 / 92 / 8 | grid layout |
| `MINI_PLAYER_HEIGHT` | 32 | MiniPlayer |
| Type scale (`TYPE_MENU_ITEM` … `TYPE_CAPTION`, `TYPE_ALPHABET`) | 34 / 40 / 22 / 18 / 26 / 15 / 14 / 11 / 9 / 10 | all screens |
| `LETTER_SPACING_EDGE / HEADER / CROSSBAR` | -0.5 / -1.0 / -0.3 | letter-tracking tokens |
| Mini-app tokens | `PIANO_KEY_W` 24 × `PIANO_KEY_H` 72, `DIAL_HEIGHT` 96, `SUDOKU_CELL` 36, `CARD_W` 44 × `CARD_H` 62, `HEX_RADIUS` 14 | mini-apps |
| `SKIP_DRAG_PX`, `IDLE_SCREENSAVER_MS` | 24, 5000 | Now Playing |
| Motion durations | `PIVOT_SLIDE_MS` 380, `QUICKPLAY_MS` 420, `DEPTH_MS` 340, `STAGGER_MS` 20 | every nav |

---

## 🛠️ Building

```bash
# Android SDK + JDK 21 required; local.properties points at your SDK
./gradlew assembleDebug          # debug APK
./gradlew test                   # unit tests + design-invariant audit (Robolectric)
./gradlew lint                   # android lint
```

The release build minifies with R8; keep rules in `app/proguard-rules.pro`.

---

## 🛣️ Roadmap

### M4 — MiniApp + marketplace completeness ✅ *complete*

- Every empty list long-press under `ui/screens/` now pins to Quickplay
  (Genres, Videos, Pictures, Podcast feeds + episodes, Marketplace albums
  + installed apps). Non-actionable affordances (buttons, picture buckets)
  no longer carry a dead `onLongClick`.
- The shared `trackMenuActions` helper adds **play-next** to every track
  row (the Quicklist replacement the community asked for since firmware
  3.x removed it).
- Marketplace `videos` and `podcasts` pivots draw from the library; the
  `EmptyPivot("coming soon")` is gone, and a `games` pivot joins the
  crossbar.

Next up: **M10 Always-on surfaces** — M9 Modern Listening is complete (audio
similarity, Dynamic Mix, DSP features, Last.fm scrobbling, LRCLIB lyrics,
persisted play counts). Outstanding elsewhere: M8 Device Link 8.2b (live LAN
pairing) — see [`docs/parity-roadmap.md`](docs/parity-roadmap.md).

### M5 — Social + discovery

- Now Playing: system share-sheet action (artist + title + album).
- Now Playing: Zune-Card-style artwork export (PNG/JPEG).
- On-device podcast search (curated top-charts).
- Wire Smart DJ as a tap-to-build-mix action in the music crossbar.

### M6 — Community wishlist *(3-5 days)*

- EQ presets (rock / acoustic / hip-hop / pop / classical / electronic;
  6 presets, no custom).
- Crossfade on `play()` (toggle in display settings).
- Live-radio pause-and-cache ("Live / Rewind ±5 min" UI). Requires a
  real-time streaming source.
- Lock-screen Now Playing art + controls (richer surface than the
  default Media3 notification).
- Soft keyboard with "bulge around the user's finger" (Wikipedia-cited
  affordance).

### M7 — Design-token + invariant-test hygiene

- Token-ize the screensaver's raw dp sizes (already done in Sprint 1).
- Add unit tests for `KineticList` A–Z popup, `HomePages` parallax math,
  `StaggerEntrance` clamp (already done in Sprint 1).
- Extend `DesignInvariantTest` to catch `Color.Black/White/Red/…`
  (already done in Sprint 1).

### Future (post-M7)

- **Wi-Fi sync with Dorado** — the phone enrolls as a Zune-HD-like
  device in Dorado's sync engine (JSON manifest, ZMDB-style database).
- **Real MTP/MTPZ sync to a physical Zune HD over USB host** — stretch.
- **Home-screen widget** — Zune-style Now Playing with transport.
- **Live radio cache** (M6) is contingent on a real-time streaming
  source being available.

---

*"The Zune HD's UI is everything but an example of Apple minimalism."* —
Gizmodo, 2009
