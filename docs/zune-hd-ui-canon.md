# Zune HD UI Canon

The authoritative description of the Microsoft Zune HD on-device interface that
Dorado-HD replicates. Compiled from contemporaneous reviews (CNET, Gizmodo,
SlashGear, TechCrunch, Windows Central, ZDNet, PCMag, ITPro Today, Sept 2009),
the ZuneDev/Dorado decompilation corpus, and ZuneRedux community resources.
Every screen in Dorado-HD must be checkable against this document.

## 1. Hardware & canvas

- Display: 3.3" capacitive multi-touch OLED, **480x272** (wide), Tegra APX.
- Buttons: power/hold (top), home (below screen), media/quickplay (left edge).
- On Android we re-render this canvas at `480x272` design units (see
  `DeviceCanvas`) for **device mode**, and reflow the same components for
  **adaptive mode** on tall phones.

## 2. Core design language (Metro / Zegoe)

- White text on matte black. Typography **is** the UI; chrome is banned.
- Font: Zegoe UI (Microsoft-modified Segoe). We ship **Selawik** (OFL,
  Segoe-metric) by default; authentic Zegoe import is not implemented in v1
  (planned/post-device).
- Menu labels are lowercase, oversized, and **deliberately cropped at the
  right screen edge** (Gizmodo: "the word marketplace is cut off at the
  penultimate letter").
- Opacity communicates state: active 100%, neighbors ~40%, metadata ~60%.
- Zero corner radius, zero drop shadows, no skeuomorphism. Ever.
- Accent color themes: Zune pink `#FA2A55` (default), orange `#F09609`,
  cyan `#1BA1E2`, lime `#339933`, purple `#A200FF`.
- Kinetic scrolling with strong deceleration (cubic/exponential ease-out).
- Lists stagger their entrance (~15-25 ms per item cascading slide).

## 3. Navigation model

### 3.1 Home menu (default view)
A vertical text list: `music · videos · pictures · radio · marketplace ·
social · podcasts · internet · settings` (Zune HD firmware 4.x). All nine entries
are functional in Dorado-HD — matching the launch reviews (Sept 2009: Engadget,
CNET, Gizmodo), which document every entry as a first-class surface rather than a
placeholder.
- Flick vertically to scroll (kinetic).
- Tap an entry to enter. The whole entry is the button — text only.
- Items are cropped at the right edge as a signature.

### 3.2 Quickplay (the "left screen")
- **Slide the home menu right** to reveal Quickplay parked "left and rear"
  with a parallax 3D slide (Windows Central: "left and to the rear, in a bit
  of visual 3D trickery").
- Quickplay contains: **Now Playing** (current track card: art, title,
  artist), **Pins** (user-pinned items — long-press anything to pin),
  **History** (recently played), **New** (recently added).
- Purpose: bypass collection drilling; reach relevant content instantly.
- On device the left-edge hardware button summons it; in Dorado-HD it is the
  home screen's left page.

### 3.3 Crossbar ("sub-menus arrayed left to right across the top")
- Inside `music`: top crossbar row is `albums · artists · playlists · songs ·
  genres` (real Zune order).
- Flick horizontally on the crossbar (or content) to switch pivots; content
  slides horizontally beneath the fixed crossbar.
- Lists scroll vertically: textual (artists, genres) or grid (albums).

### 3.4 Back gesture = tap the screen header (and the screen-edge back arrow)
- There is no hardware back button. The top-of-screen heading (e.g. "music"
  on the Music screen or "settings" on Settings) functions as the back button
  (ZDNet Quick Start Guide; Gizmodo). Tapping the header returns up one level.
- The header is rendered with the signature oversized typography (40sp,
  Selawik/Zegoe Light) without vertical clipping; long titles crop horizontally
  at the right edge as a signature.
- Now Playing is the device's documented exception: it shows an explicit
  left-arrow back button (Gizmodo). Dorado-HD preserves this.
- Dorado-HD additionally places a faint back arrow at the right edge of the
  header on every detail screen, so the affordance is also visible
  to users who never realize the header text is tappable. The arrow and the
  header text are equivalent back actions; the header remains the
  canon-correct wayfinding signature.

### 3.5 Alphabet jump
- A **separated rail of faint letters** runs along the right edge of long
  lists. Tap any letter → a full A–Z index pops up → pick a letter to jump
  to the first row whose label begins with that letter.
- This is the same right-edge rail the device shipped; earlier descriptions
  described an "inline" placement (letters among the rows). Inline placement
  was widely reported as a mis-tap hazard (Gizmodo: "I've accidentally hit a
  letter when I meant to hit an artist"); the separated rail is the
  canon-correct affordance.

### 3.6 Apps (marketplace)
- Official apps and games were delivered exclusively through the **Apps
  section of Zune Marketplace** (Wikipedia, *List of Zune applications*).
  There was no home-menu `apps` entry.
- In Dorado-HD the `marketplace` pivot's `apps` section lists installed
  mini-apps plus the frozen official catalog (unavailable entries dim).
- Mini-apps open fullscreen and return via the cropped header / system back
  (the device used its physical home button). See §8.

## 4. Now Playing (the signature screen)

Layout (from ITPro Today's walkthrough):
- Explicit **back arrow** top-left.
- **Artist** name and **album** name (tappable: artist → their crossbar page
  with albums/songs/bio/photos/related; song title → the album's track list).
- **Album art** prominent.
- Bottom row: **shuffle**, **repeat**, **rating** (heart / broken heart).
- The card **floats over artist photography** fetched from zune.net keyed by
  MusicBrainz ID (see `net/` in Dorado-HD; recreated by ZuneArtistImages).

Interactions:
- **Idle for a few seconds → screensaver**: metadata (artist, track, album,
  length, art) slowly scrolls over the artist photo, "super-smooth".
- **Swipe left/right → skip** to next/previous track.
- **Tap empty space → transport overlay**: play/pause center, volume up /
  volume down at top/bottom, previous/next at left/right (ITPro Today).
  Also summoned by the device's media button.
- **Swipe up/down (overlay visible) → volume** up/down.

Ratings (tri-state heart, from the Zune desktop/HD family):
- Heart = favorite (prioritized by Smart DJ/Quickplay).
- Broken heart = dislike (skipped in shuffle).
- Unrated = neutral.
- Smart DJ honored ratings: hearts were prioritized and broken hearts
  skipped when shuffling (Microsoft press release, Sept 15 2009). Dorado-HD's
  shuffle mirrors this on-device behavior.

## 5. Lock/wake behavior
- Wake shows the user wallpaper behind a "software shade"; **slide the shade
  up** to reveal the home screen (ITPro Today). Dorado-HD implements this as an
  in-app `WallpaperManager`-backed shade (`ui/DoradoRoot.kt`,
  `design/components/LockShade.kt`); the Android lock screen remains the OS wake
  surface beneath it.

## 6. Motion rules
- Navigation: content slides horizontally with deceleration; deeper screens
  slide in from the right while the parent dims slightly leftward.
- Crossbar pivot switch: horizontal slide, no bounce.
- Kinetic lists: fling, then long deceleration to rest.
- Everything fast: Metro is "designed to feel fast and responsive".
- The device's touch/kinetic parameters are the `XuiTouchSettings` values the
  shell applied (`gemstone.exe` VA `0x1C900`–`0x1CB64`, handed to
  `XuiSetTouchSettings`). Dorado-HD consumes the corroborated drag/skip deadband
  `25.0` (`0x10` → `DoradoTokens.SKIP_DRAG_PX`).
- **Kinetic integrator (device, cited).** `xuidll.dll@0x41841D58` integrates
  `pos += (dt_ms/1000)·v` with `dt` floored at 33.333 ms (30 fps), updates
  `v' = v·(1 + c[+0xB0])` clamped to `±(step·c[+0xB4])`, and ticks at 16 ms
  (62.5 Hz, `FUN_41848B98`); drag velocity is capped at 32.0 (`FUN_4184C310`).
  **Correction:** the earlier attribution of HD's retention `0.95` to
  `XuiTouchSettings[0x1C]` is wrong — the integrator reads `[0x08]/[0x0C]`, never
  `[0x1C]`. `DoradoMotion.KINETIC_FRAME_RETENTION` (0.95) /
  `…LANE_FRAME_RETENTION` (0.94) are empirical approximations of this glide.
  Full extraction: `docs/zune-hd-touch-settings.md` §6; audit:
  `docs/zune-hd-parity-audit.md` §3.

## 7. Banned in Dorado-HD (invariant list, mirrors Dorado)
- `RoundedCornerShape` / any nonzero corner radius on UI chrome.
- Drop shadows on text/buttons; gradient chrome; skeuomorphic textures.
- Icon-only navigation in the main hierarchy (text is navigation).
- Material default colors/typography leaking into screens.
- Star ratings (Zune is heart-based, tri-state).

## 8. Mini-app platform (Dorado-HD)
- Apps open fullscreen: no MiniPlayer; no crossbar unless the app defines one.
- The cropped header is the back affordance; system back also returns to
  marketplace/apps (canon §3.6).
- Apps must render correctly in device mode (480x272) and adaptive mode.
- Dorado-HD ships behavioral re-implementations only — no Microsoft code,
  binaries or assets.
- **All 62 official packages are implemented** (program record:
  [`official-apps-audit.md`](official-apps-audit.md), per-app behavior:
  [`apps/`](apps/)). The `OfficialCatalog` → `DoradoApps` mapping is total:
  every catalog row resolves to a launchable app.
  - **Utilities & music (12):** calculator, notes, stopwatch, alarm clock,
    calendar, level, metronome, piano, drum machine, chord finder, music
    quiz, shuffle by album.
  - **Card, board & AI (9):** solitaire, sudoku, hexic, reversi, hearts,
    spades, checkers, chess, texas hold 'em.
  - **Casual & puzzle (23):** color spill, supernova, tiles, slider puzzle,
    shell game, trash throw, tug-o-war, snowball, run and jump, hairball,
    splatter bug, goo splat, a beanstalk tale, animalgrams, bbq battle,
    bees, castles and cannons, decoder ring, fan prediction, penalty flick
    soccer, space battle 2, vine climb, wordmonger.
  - **Touch, toy & physics (5):** dr optics, fingerpaint, 3D picture puzzle,
    finger physics, tiki totems.
  - **Big engines (6):** audiosurf tilt, echoes, labyrinth, lucky lanes
    bowling, PGR Ferrari, vans sk8.
  - **Dead-service local UIs (7 packages + the social shell):** weather,
    twitter, facebook, email, messenger, msn money, zune reader, zunesocial —
    pixel-faithful layouts with simulated local content, clearly offline.
- Provenance: every behavior is re-derived from the decrypted package tree
  (`docs/zcp-inventory.md`, `docs/apps/`), never copied; all artwork, levels,
  word lists and audio are re-authored in code (`NOTICE.md`).

## 9. Reference library
- dorado (desktop) design-system skill + extracted Zune assets (MIT).
- ZuneRedux/zune-hd-apps — original HD app archive (design reference only).
  The corpus's NX container, Authenticode signature, manifest records and
  AES-ECB-encrypted payload are documented in `docs/zcp-inventory.md`
  (regenerable via `tools/zcp_inventory.py`). Metadata only; binaries are
  never bundled or shipped.
- spidersandmoths/ZuneArtistImages — recreated catalog.zune.net semantics.
- zuneupdate.com — community resource server (resources.zune.net).
- BillyOutlast/MusicIn2001 — behavioral spec only (Research-Only license).
- Microsoft UI Patents — authoritative interaction mathematics and state machines:
  US 8,560,975 B2 (Cropped typography and kinetic scroll), US 8,826,177 B2
  (Application Pivot Navigation), US 8,429,565 B2 (Lock screen shade gesture),
  US D628,583 S (Quickplay parallax shelf).
- Zune HD Disassembly Corpus (`docs/zune-hd-disassembly.md`) — generated by
  `scripts/disassemble_zune_hd.py` from `PavoBaseline.Cab`. Reconstructs the
  native `gemstone.exe` shell, `xuidll.dll` XUI runtime, and authentic scene
  hierarchy (`GemStartScene`, `GemTiltScene`, `GemPivotScene`, `GemNowPlayingScene`).
- Touch & kinetic ground truth (`docs/zune-hd-touch-settings.md`) — the
  80-byte `XuiTouchSettings` struct (`xuidll.dll` VA `0x41874314`), its XUI
  defaults, and the shell's effective `gemstone.exe` values.
- Period reviews, Sept 2009 (listed above) for interaction ground truth.

## 10. Post-device extensions

Behaviors Dorado-HD adds that the Zune HD did **not** ship. These are never
canon; they must be labelled as extensions wherever they surface.

**Shipped:** Dynamic Mix (similar-to-track/album/favorites + Top Played from
persisted play counts), on-device DSP audio features, Last.fm scrobbling with an
offline queue, LRCLIB lyrics, the Glance Now Playing widget, cloud update-check,
Device Link (mDNS discovery + paired LAN sync), EQ presets, **fade-through
crossfade** (`crossfade` setting: linear volume ramp over the track tail plus a
fade-in on the next item — Media3 has no overlapping crossfade; labelled
"fade-through" wherever it surfaces), and the **sleep timer**
(settings ▸ playback; countdown shown in the Now Playing status OSD), and the
**live FFT visualizer** (permission-free PCM tap into `FeatureMath`'s FFT;
procedural animation remains the fallback).

**Pending:** live-radio pause-and-cache and richer lock-screen art/controls —
tracked in `docs/parity-roadmap.md` (M5/M6/M10).

- **Lane fling tuning.** The device's kinetic glide is `pos += (dt/1000)·v` at
  62.5 Hz (`xuidll.dll@0x41841D58`; see §6). Dorado-HD approximates it with a
  `KINETIC_FRAME_RETENTION` of 0.95 for vertical lists and 0.94 for horizontal
  lanes (the longer retention over-glides a carousel on a modern phone). These are
  empirical approximations, not device constants (`zune-hd-parity-audit.md` §3).

## 11. Device label vocabulary (cited)

Original wording recovered from the shell resource keys, recorded so display copy
can be aligned canon-first (do not change copy without updating this section):

- `"Various Artist"` (`gemstone.exe@0x119A4`) — HD currently uses `"unknown artist"`.
- `" by "` title/artist separator (`gemstone.exe@0x15A60`) — HD uses an em dash.
- Empty-state keys `noItems` / `noItems_local` / `noItems_online` / `Empty`
  (`gemstone.exe@0x14160/0x148BC/0x14840/0x12B7C`) — HD uses bespoke per-screen copy.
- Transport/status words `Ffwd`, `Mute`, `Disconnected`, `Refresh`, `Wishlist`
  (`gemstone.exe@0x19D18/0x19D2C/0x19D50/0x12054/0x19790`).
- Transport labels and the volume formatter also live in `zhud_serv.dll`
  (`Play/Pause/Ffwd/Mute@0x419B4C58-84`, `FormatVolumeEx@0x419D7E88`).

Tracked in [`zune-hd-parity-audit.md`](zune-hd-parity-audit.md) §10.

> **Decision (M12).** The strings above are recovered from the shell's *resource
> keys*, not confirmed display copy; the copy is therefore left unchanged in v1
> rather than guessed. Revisit only with a confirmed display-string source.
