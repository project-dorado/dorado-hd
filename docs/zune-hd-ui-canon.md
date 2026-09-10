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
  Segoe-metric) by default; users may import Zegoe themselves.
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
social · podcasts · internet · settings` (Zune HD firmware 4.x). In Dorado-HD
the functional entries are `music` and `settings`; future pivots may join.
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
  up** to reveal the home screen (ITPro Today). Dorado-HD v1 treats the Android
  lock screen as the wake surface; the in-app shade is a stretch goal.

## 6. Motion rules
- Navigation: content slides horizontally with deceleration; deeper screens
  slide in from the right while the parent dims slightly leftward.
- Crossbar pivot switch: horizontal slide, no bounce.
- Kinetic lists: fling, then long deceleration to rest.
- Everything fast: Metro is "designed to feel fast and responsive".

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
  binaries or assets: utilities (calculator, notes, stopwatch, metronome,
  alarm clock, calendar, level, piano, drum machine, chord finder, music
  quiz, shuffle by album), games (solitaire, sudoku, hexic, reversi), and
  offline mock shells for dead services (weather, twitter, facebook, email,
  messenger, msn money, zune reader).

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
- Period reviews, Sept 2009 (listed above) for interaction ground truth.
