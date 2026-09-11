# Piano

- **Official package:** `Piano.exe` (piano)
- **Corpus:** `Zune HD Apps (Decompiled)/piano` (external, untracked)
- **Wave:** W1 · **Category:** music
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** piano

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`Piano.exe` is a 38-type / 119-method XNA application (2,934 lines) that runs
landscape-only at 480×272. It is a multi-touch electric piano: a 52-white-key
scrollable keyboard with correctly offset black keys, per-octave colour
themes, note-name notation, three volume levels, and latching sustain/dampen
pads. Samples are a full chromatic set of recorded piano notes (A−1…C7),
transposed upward when the exact key has no sample.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Piano.exe` | 38 | 119 | 2934 |
| `Microsoft.Xna.Zune.dll` | 110 | 825 | 15652 |

Core types: `Piano!PianoKeyboard` (key layout, scroll interpolation,
multi-touch routing), `Piano!PianoKey` (per-key voice, glow, sustain/dampen,
release fade), `Piano!PianoKeySoundBank` (sample lookup + voice cap),
`Piano!PianoKeyPitch` (note arithmetic), `Piano!NotePitchExtensions`
(white/black classification), `Piano!TopButtonBar`/`OctaveBar` (controls),
`Piano!Device` (accelerometer orientation lock) and `Piano!Settings`
(`PianoSettings/Settings.xml` with `ShowNotations` and `VolumeLevel`).

## 2. Screens & navigation

`Game1` owns the whole scene: a shared sprite sheet is parsed from
`SpriteSheet.txt` and a `LoadingScreen` (`startup-notated` / `startup-normal`
plus loading-bar frames) covers startup. After load there is a single
landscape screen with three zones:

- **Top control bar** (`TopButtonBar.Load`): two 52 px key groups at x=322 and
  x=375 with an arrow at x=428 that flips between them, plus a 104×56 About
  text button. State 0 shows notation toggle + volume (three steps); state 1
  shows dampen + sustain pulse pads. `ArrowButton.Clicked` starts a 0.2 s
  cross-fade between the two states.
- **Keyboard**: 52 white keys drawn at 60 px pitch and 221 px height, with
  black keys 40×127 inset 20 px into each white key's right edge; the whole
  keyboard is translated by `WhiteKeyOffset` (in white-key units).
- **Octave bar** (`OctaveBar.Load`): a 7-notch overview strip at the left,
  drawn with the `octave_select_off` / `octave_select_keynotes` states and the
  `octave_selector` marker.

`AboutScreen` is a modal overlay dismissed by a tap. `Device` forces portrait
first, then tracks orientation, and `TouchManager` transforms touch points into
the landscape coordinate space.

## 3. Rules, scoring & progression

There is no score. The play/audio model is the interesting part:

- Voice allocation (`PianoKeySoundBank.PlayNote`): look up the exact pitch;
  if missing, walk outward up to 12 semitones (`AdjustPitchByKeyCount`) and
  play the found sample transposed with `SoundEffectInstance.Pitch`. At most
  14 live instances are kept — older ones are disposed first — and
  `InstancePlayLimitException` is swallowed so fast glissandi never crash.
- Press/release (`PianoKey.Press`/`Release`): pressing starts a voice and sets
  the glow (0.7 with dampen, 1.0 otherwise), pushing the previous voice onto
  an `oldNotes` list. Releasing while sustain is on leaves the note ringing;
  otherwise it enters a 1 s cool-down that fades the instance at double rate
  and then disposes it. `Unsustain` stops all ringing non-pressed keys;
  `Dampen` drops volume to 0.7 and `Undampen` restores 1.0. `KillSound`,
  `ReleaseAllKeys` and `DampenAllKeys`/`UndampenAllKeys`/`UnsustainAllKeys`
  are the bulk operations used by the top bar and input loss.
- Glow decays per frame: 0.4/s while sustain is latched, 2/s otherwise.
- Octave navigation: dragging the octave bar scrubs continuously
  (`OctaveBar.HandleInput`), tapping a notch snaps to one of seven octaves;
  on release the keyboard interpolates (`WhiteKeyStart`/`WhiteKeyTarget`,
  speed 8–10/s) and the octave marker follows for scrub moves.
- Settings: `Settings.Load`/`Save` read/write a storage-container
  `PianoSettings/Settings.xml` with a notation flag and 0–2 volume that maps
  to master volume 0.3/0.6/1.0. `Game1.Initialize` re-applies the level.
- Startup art and per-key colours are octave-themed: nine ebony/ivory/edge
  hues indexed by octave (`PianoKeyboard.GetColorOctave`), and the keyboard
  draws note letters when notation display is on.

## 4. Controls

Fully multi-touch. `Game1.HandleInput` routes every touch point:
`OctaveBar.HandleInput` first claims touches on the strip; a new touch on the
top bar runs `TopButtonBar.TestInput`; touches below y=56 hit the keyboard.
When a finger moves more than 10 px in a frame the path is interpolated in
10 px steps so dragging produces a glissando rather than a gap. Notes are
released when the finger lifts (`ReleaseKeysNotPressedThisFrame` after
`ResetKeysForNextFrame`). The Damper/Sustain pads are latching pulse buttons
whose outline pulses whenever a key is played. About is a tap-to-dismiss
overlay. Accelerometer is used only to keep the device locked to landscape —
there is no tilt instrument.

## 5. Content inventory (must be re-authored)

35 files: 30 audio XNBs (a chromatic sample set: `A_-1`…`A_6`, `C_0`…`C_7`,
`DSharp_0`…`DSharp_6`, `FSharp_0`…`FSharp_6`), one 284 KB sprite sheet, its
`SpriteSheet.txt` rectangle table, `Text/Fonts.xml`, and `Language/en|es|fr`
strings. The samples are recorded piano audio (Microsoft content) and the
sprite sheet is Zune UI chrome.

Re-authoring: no sample may ship. Build a `PianoVoice` synth in `MiniSynth`:
per-note frequency (equal temperament from A4 = 440), a fast attack and long
exponential decay with a few harmonic partials and a velocity/volume envelope
per the 0–2 volume setting; black keys derive from the same table. Keys,
octave bar and top-bar states are drawn with Compose canvas + tokens (zero
radius); `SpriteSheet` is not used.

## 6. Implementation plan

- Engine: new `ui/apps/piano/PianoEngine.kt` — `object PianoEngine` +
  immutable `data class PianoState(scrollWhiteKeys, volume, notation,
  sustain, dampen, activeKeys)`; pure helpers for the 52-key layout
  (white/black bounds, black-key offset), `noteFor(whiteIndex, black)`,
  `frequencyFor(midi)`, octave-bar snap/scrub maths, and voice allocation
  (cap 14, oldest-first eviction).
- Audio: `ui/apps/piano/PianoVoice.kt` on `MiniSynth` — synth-only, no
  samples; a short attack, long decay, two-three partials, and a
  dampen/sustain gate that matches the device envelope times (1 s release
  fade, 0.7 dampen gain). Note names in notation mode use `Selawik`.
- UI: rewrite `PianoApp()` (`PianoDrum.kt:31`) as
  `ui/apps/piano/PianoApp.kt`: landscape canvas keyboard scrolled by
  `WhiteKeyOffset`, multi-touch down/move/up tracking per pointer id,
  interpolated glissando, top bar with arrow-flipped notation/volume and
  dampen/sustain pads, octave strip on the left. `DetailScaffold` provides the
  back affordance; everything token-coloured, zero radius.
- Fidelity gaps vs current code: only 14 fixed white keys, tap-only play, no
  scroll/octave bar, no sustain/dampen/volume/notation, no glissando and no
  voice cap.
- Persistence: `showNotations` and `volume` in
  `graph.appState.put("piano", …)`.
- Tests: `app/src/test/java/com/heretek/dorado_hd/PianoEngineTest.kt` — note
  layout (52 whites, 36 blacks), black-key placement set, frequency table,
  octave snap points, voice-cap eviction, sustain/dampen gating and release
  fade, glissando path sampling.
- Edge cases: 10-finger chords on a small screen, pointer cancel/re-entry,
  scrolling while notes are held, sample-free range above C7 (clamp or
  octave-fold), and About overlay swallowing touch-up.

## 7. Citation log

`Piano!PianoKey.Press`, `Piano!PianoKey.Release`, `Piano!PianoKey.Dampen`,
`Piano!PianoKey.Undampen`, `Piano!PianoKey.Unsustain`,
`Piano!PianoKey.KillSound`, `Piano!PianoKey.Update`,
`Piano!PianoKeyboard.Load`, `Piano!PianoKeyboard.HandleInput`,
`Piano!PianoKeyboard.GenerateWhiteKey`,
`Piano!PianoKeyboard.ReleaseKeysNotPressedThisFrame`,
`Piano!PianoKeyboard.GetColorOctave`,
`Piano!PianoKeyPitch.AdjustPitchByKeyCount`,
`Piano!PianoKeySoundBank.PopulateSoundBankForInstrument`,
`Piano!PianoKeySoundBank.PlayNote`, `Piano!TopButtonBar.Load`,
`Piano!TopButtonBar.pianoKeyboard_KeyPlayed`, `Piano!ArrowButton.Clicked`,
`Piano!OctaveBar.HandleInput`, `Piano!OctaveBar.Load`,
`Piano!Settings.Load`, `Piano!Settings.Save`, `Piano!AboutScreen.Show`,
`Piano!Game1.HandleInput`, `Piano!TouchManager.Update`, `Piano!Device.Update`.
