# Dorado-HD UI/UX deep audit — 2026-09-11

**Build:** `com.heretek.dorado_hd.debug` v0.1.0, targetSdk 37, debug APK built from
HEAD (`df3579a` + the fix commits below).
**Device:** AVD `xune-test`, Android 16 (API 36), x86_64, 1080×2400 @ 420 dpi.
**Mode:** both **device mode** (letterboxed 480×272 design canvas, landscape) and
**adaptive mode** (phone-native scaling, portrait/landscape), plus a small-screen
`wm size` sweep.
**Method:** static sweep of every UI source file, per-screen interaction plan,
on-device execution with screenshots + `logcat` crash capture, `monkey` stress,
and cross-checks against `docs/zune-hd-ui-canon.md`, `docs/design-tokens.md`,
`docs/zcp-inventory.md` and the external decompilation corpus.

Severity: **S1** crash / unusable core surface · **S2** broken feature ·
**S3** UI/UX / canon / polish.

> Companion history: [`bug-hunt-report.md`](bug-hunt-report.md) (2026-09-10
> on-device hunt) and [`zune-hd-parity-audit.md`](zune-hd-parity-audit.md)
> (device-parity scorecard). This document covers the follow-up deep sweep the
> user requested: every route, every mini-app, functionality, broken UI,
> clutter, clipping and scaling.

---

## 1. Headline defects fixed

### S1 — crashes / unusable

| # | Defect | Evidence | Fix |
|---|---|---|---|
| 1 | **Mini-apps rendered two stacked 48dp headers** (`MiniAppScaffold` wrapped apps that already self-scaffold), leaving ~144dp of play area and pushing controls off-canvas — calculator, all games, all mocks. | screenshot: two “calculator” headers | removed the redundant wrapper (`MarketplaceScreens.kt`) — commit `7548ba1` |
| 2 | **Hexic froze on the first 3-tile match**: cleared cells were re-filled with a matchable colour, so cluster detection re-cleared them forever. | `Games.kt` clear loop | cleared tiles become `EMPTY`, ignored by cluster detection; board is observable state — `e451d1b` |
| 3 | **Sudoku was dealt pre-solved** (playable grid = solution, mask inverted); the timer stopped instantly and a 10 000 score was recorded on open. | `SudokuEngine.newGame` | puzzle + clue mask + stored solution; win compares to the solution — `e451d1b` |
| 4 | **Widget transport taps crashed**: `MediaController` was built on Glance’s background dispatcher and asserts the main looper. | `widget/NowPlayingWidget.kt` | actions run on `Dispatchers.Main.immediate` — `7548ba1` |
| 5 | **Chess castling NPE** after a rook was captured (stale castling rights → `!!` on a missing rook); rights masks were also inverted for a1/h1/a8/h8. | `ChessEngine.apply` | rights revoked on capture, masks corrected, rook moves guarded — `e451d1b` |
| 6 | **Hearts’ passing phase never ended** (`pendingPassFrom` stayed set), so play could never begin. | `HeartsEngine.passCards` | `completePassing()` closes the phase with AI passes — `e451d1b` |
| 7 | **Hold’em never dealt streets** (state copy discarded) and per-player contributions were hardcoded, producing a call-war to zero. | `PokerEngine.advance` | per-street contributions + `acted` mask, street dealing, kicker showdown, pot award — `e451d1b` |
| 8 | **Checkers/Chess tap overlay was detached from the board** (root-level sibling offset by the header) and covered the back header; most taps mis-mapped. | `Games.kt`/`MoreGames.kt` | draw + hit-test on one canvas with a shared origin — `e451d1b` |
| 9 | **Checkers winner never set** (`state.turn == nextColor` always false). | `CheckersEngine.apply` | opponent with no moves ⇒ mover wins — `e451d1b` |
| 10 | **Piano was silent** (`exp(-6) ≈ 0.0025` gain, ~−52 dB). | `Synth.sineNote` | removed the constant decay — `26ec13f` |
| 11 | **Mini-apps could not fit 480×272**: calculator scientific rows, shuffle-by-album’s play button, sudoku’s 324dp board were all unreachable; **no scroll container existed in `ui/apps/`**. | arithmetic + screenshots | weighted calculator rows; side-by-side shuffle layout; Sudoku/Reversi/Hexic boards scale to the tighter axis; mocks scroll — `26ec13f`, `677743b`, `e451d1b` |
| 12 | **First-launch crashes on Game AI / search etc. were latent** — chess minimax ran on the main thread; AI now runs on `Dispatchers.Default` at depth 2. | `ChessApp` | `e451d1b` |

### S2 — broken features

| # | Defect | Fix |
|---|---|---|
| 13 | Cloud sign-in silently always failed (`NetworkOnMainThreadException` swallowed); malformed token JSON was an unguarded crash; OAuth `state` was optional and the redirect host unchecked. | IO-dispatched exchange, `runCatching` parse, exact state match, host check, sign-out disables cloud — `4c69cdb` |
| 14 | Podcast RSS parser assigned every `<title>` to the *feed*, so imports produced zero episodes. `PodcastFetcher` also did blocking I/O on main. | channel/item scoping + `Dispatchers.IO` + feed refresh + real feed title — `31922b4` |
| 15 | Video player: no seek gesture; a second ExoPlayer played over music; in-content duplicate back button; local playback did not suppress the MiniPlayer. | seek bar accepts tap/drag, music pauses on entry, playback is a fullscreen route, header is the back — `4c69cdb` |
| 16 | WebView was never destroyed; bookmarks/history were an untappable string. | `onRelease { destroy() }`, tappable recent strip — `4c69cdb` |
| 17 | Pictures: favorites pivot was a no-op; the viewer always opened at index 0; albums pivot was a non-scrolling `Column`; system Back exited the whole screen from the viewer; pan was unbounded. | favorites open, tapped index used, albums use `KineticList`, `BackHandler`, pan edge-clamped — `4c69cdb` |
| 18 | Radio dial gesture re-keyed on every frequency change (one step per drag); the needle was mirrored (~2.2 MHz off). | `rememberUpdatedState` + corrected mapping/ticks — `31922b4` |
| 19 | Marketplace details’ back popped the whole marketplace; long descriptions were single-line clipped; videos claimed “none” while still loading. | `onBack` wired, wrapping scrollable description, loading state — `31922b4` |
| 20 | Metronome BPM changes didn’t retime the click loop (display/audio desync); piano/drum wrote AudioTrack on the UI thread. | one loop re-reads BPM; suspend `playSamples` on IO — `26ec13f` |
| 21 | Alarm: delete didn’t cancel its PendingIntent; editing re-armed disabled alarms; only hour+1 was editable; 64-bit ids truncated to request codes; weekly mask double-counted the offset. | cancel-on-delete, enabled guard, hh:mm prompt, hashed request codes, mask loop fixed — `26ec13f` |
| 22 | Calendar appointments matched by day-of-month (Aug 5 lit Sep 5); add-only, no delete. | keyed by full `LocalDate`; long-press lists/deletes — `26ec13f` |
| 23 | Music Quiz hung on “loading…” with <4 albums/artists. | attempts once, then explains the requirement — `26ec13f` |
| 24 | Chord Finder silently drew C-major for uncatalogued chords; fret 5/open/muted were wrong. | `shapeFor` returns null + “no open shape”; fretboard redrawn with O/X and 5 frets — `26ec13f` |
| 25 | Home history long-press offered “unpin” (a silent no-op); Smart DJ only read the first 60 title-sorted tracks. | history offers play/pin, pins offer unpin; full library — `4c69cdb` |
| 26 | Music/Marketplace/Detail/Media lists had no MiniPlayer inset (last row unreachable); albums grid tiles collapsed to zero height on load failure. | bottom padding everywhere; square `aspectRatio` tiles — `4c69cdb` |
| 27 | **Canon §8 violation**: MiniPlayer drew over mini-apps. | suppressed for MiniApp/Video/PictureDetail/NowPlaying routes — `7548ba1` |

### S3 — UI/UX, canon, clutter, scaling

| # | Defect | Fix |
|---|---|---|
| 28 | `safeDrawingPadding` was applied **inside** the scaled canvas: real insets shrank/offset the 480×272 composition (and adaptive density multiplied them). | insets moved outside the canvas — `7548ba1` |
| 29 | Portrait device mode swapped the canvas to 272×480 while every screen is landscape-authored. | canvas stays 480×272; portrait letterboxes — `7548ba1` |
| 30 | MiniPlayer’s Material `IconButton` enforced 48dp over a 32dp bar, overhanging content and stealing taps. | bare 32dp clickable box — `7548ba1` |
| 31 | Now Playing: no back arrow in the empty state; shuffle/repeat/heart taps didn’t reset the screensaver timer; volume readout never hid (and never showed 0); dim was an opaque layer *under* the saver; OSD clock froze. | all fixed — `4c69cdb` |
| 32 | Skip deadband compared design units to raw px (density-sensitive sensitivity). | converted via `LocalDensity` — `4c69cdb` |
| 33 | Alphabet rail appeared on tiny lists (1-video screen) and its 32dp column covered trailing labels. | rail only for ≥12 items; lists reserve end padding — `4c69cdb` |
| 34 | Crossbar pivots: the 22dp gap between labels was part of the previous label’s hit target. | full-height clickable per label, gap outside — `4c69cdb` |
| 35 | AlbumArt showed a blank bordered tile when a URI failed to load. | error state falls back to the placeholder — `4c69cdb` |
| 36 | Lock shade: dismiss raced the slide-off animation; offset leaked across reveals; Back didn’t dismiss; wallpaper decoded on the composition thread at full size (≈33 MB for 4K). | animate-then-dismiss, reset, `BackHandler`, off-main downsampled decode — `4c69cdb` |
| 37 | Settings info rows (library path, scan status) were single-line clipped; sign-in/update results were indistinguishable. | wrapping info rows, sign-in feedback, `checkStatus()` distinguishes unreachable from up-to-date — `31922b4` |
| 38 | Device Link status said “not paired” even when paired; the manifest silently truncated at 200. | status derived from pairing state; cap 100 with “+N more” — `31922b4` |
| 39 | Podcasts “video” pivot duplicated audio; feed header was a hardcoded “podcast”; no empty state. | explicit unsupported note; real title; empty state — `31922b4` |
| 40 | Mocks (weather/reader/money) clipped their last rows with no scroll; long sentences were cropped. | all mock pages scroll and wrap — `677743b` |
| 41 | Hexic circles derived radius from width only and overflowed their cells in landscape; Sudoku/Reversi lost the last row to inter-cell gaps. | tighter-axis radius; gap-aware cell size — `677743b` |
| 42 | Calculator “bksp” label wrapped in narrow keys. | renamed to “del” — `pending` (see commit list) |

---

## 2. On-device verification (post-fix)

Screenshots captured under `/tmp/opencode/dorado-hd-audit/screens/` (evidence,
not committed):

- **Device mode 480×272**: home scroll + watermark/safe-area behaviour; radio
  (needle at 98.7 now maps correctly); marketplace music/apps pivots; calculator
  (single header, full keypad); Sudoku (real puzzle, all 9 rows); Hexic (7×7
  grid, tap cascaded 25 tiles with no freeze); album detail; mini-app with music
  playing **without** the MiniPlayer.
- **Adaptive portrait**: marketplace featured tiles (square), apps pivot
  (catalog labels clear of the alphabet rail), calculator (full pad), weather
  mock (all 7 days visible, scrolls).
- **Stress**: `monkey -p com.heretek.dorado_hd.debug --throttle 100 1200` —
  1 200 events, `logcat -b crash` empty.
- **Gates**: `:app:assembleDebug`, `:app:testDebugUnitTest` (175 tests),
  `:app:lintDebug` all green after every batch.

## 3. Fix commits

| Commit | Area |
|---|---|
| `7548ba1` | mini-app scaffold, fullscreen MiniPlayer rules, device canvas/insets, widget thread |
| `26ec13f` | mini-apps: calculator, synth/audio, alarms, calendar, quiz, chord, level, notes, stopwatch, metronome, shuffle |
| `e451d1b` | games: engines, scoring, hit-testing, scaling |
| `4c69cdb` | core screens: home/music/media/now-playing/components/lock/cloud |
| `31922b4` | podcasts, radio, marketplace, settings, device link |
| `677743b` | hexic/sudoku/reversi cell sizing; mock scroll/wrap |

## 4. Known remaining / deliberately deferred

These were found and are recorded here rather than changed in this pass:

1. **Podcast/radio `mediaId` namespacing.** Episode/station entities reuse
   MediaStore numeric ids as `Track.mediaId`, so ratings/history keyed by
   `mediaId` can collide with songs that share the id. Needs a
   `(kind, id)` key or synthetic id range — data-model change, tracked for the
   next parity pass.
2. **Spades scoring/AI depth.** Rules are now coherent and bag penalties apply,
   but AI play and nil-handling remain deliberately simple.
3. **Pivot state resets** when returning from a detail pushed inside a pivot
   (e.g., Marketplace → Album → back returns to the first pivot). Pager state
   is not hoisted; cosmetic, not incorrect.
4. **Device Link page composes eagerly** (up to ~100 manifest rows in a
   `verticalScroll` column). Convert to `LazyColumn` when the page is next
   touched; low user impact today.
5. **`ZuneFling` velocity cap is in px/s** while adaptive mode scales density;
   the cap is therefore effectively lower on high-scale layouts. The kinetic
   retention model itself remains an empirical approximation (canon §10, parity
   audit §3).
6. **SpectrumVisualizer reads animation state in composition** (recomposes per
   frame while playing); move the phase read into the draw scope when
   profiling warrants it.
7. **Canon string decisions** (`Various Artist`, ` by `, `noItems`, `Ffwd`,
   `Mute`) remain per the M12 decision in canon §11.
8. **Audiobooks, queue capacity events, DRM/MTPZ/TV-out** remain out of scope
   per parity audit §11.

## 5. How to re-verify

```bash
export JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21/libexec
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.heretek.dorado_hd.debug --throttle 100 1200
adb logcat -b crash -d
```

Manual matrix: device mode (landscape letterbox) and adaptive mode, each of
home/quickplay, music (5 pivots + search), album/artist/genre/playlist, now
playing (overlay/queue/saver), settings, device link, videos/player,
pictures/viewer, radio, podcasts/feed, marketplace (5 pivots + search), social,
internet, lyrics, and all 29 mini-apps.
