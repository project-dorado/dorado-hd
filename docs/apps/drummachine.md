# Drum Machine

- **Official package:** `DrumMachine.exe` (drums)
- **Corpus:** `Zune HD Apps (Decompiled)/drums` (external, untracked)
- **Wave:** W1 · **Category:** music
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** drummachine

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`DrumMachine.exe` is a third-party (DiNoGames, Dirk Nordhusen) 22-type /
169-method drum pad and pattern sequencer (4,356 lines). Pads are freely
placed on a 272×480 stage; each pad can layer two samples, loop while held,
and be recorded into a timestamped pattern that plays back over the stage.
A metronome, an arrange mode with three presets, a runtime configuration
dialog and an options dialog round it out.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `DrumMachine.exe` | 22 | 169 | 4356 |

`Game1` owns the drum list, texture set, GUI controls, dialogs, recorded
pattern and playback state; `Drum` owns hit geometry, sample layering and
loop retrigger; `Dialog` + `TouchButton`/`TouchSlider`/`TouchCheckBox`/
`TouchRadioButton` are a small immediate-mode widget kit; `DiscoLight`/
`LightCone` drive the animated stage lighting.

## 2. Screens & navigation

There is one play screen plus overlay dialogs (`DrumMachine!Dialog.Show` /
`Hide`; dialogs draw through 512×512 render targets and grab input while
open). The screen has a fixed top-right config button, and the
`DrumMachine!Menu` slides in from the right edge with entries **Arrange**,
**Record**, **Configure**, **Options**, **Metronome**, **Info** and **Hide**
(`Menu.ArrangeMenuEntryExecute` … `HideMenuEntryExecute`). Arrange mode shows
three preset buttons (1/2/3), the OK button and a zoom slider. Record mode
shows Stop while recording; Play mode runs the captured pattern.
`Game1.SetArrangeMode` / `RecordButtonExecute` / `StopButtonExecute` /
`PlayButtonExecute` / `OKButtonExecute` are the mode transitions; each mode
shows or hides the relevant controls, so navigation is menu-driven rather
than a page stack.

## 3. Rules, scoring & progression

- Pads. `Drum.ChangeType` binds a `DrumType` (Kickbass, Tom, Snare, HiHat,
  Crash, Ride, Misc) to a texture, default sample, layer depth and hit zones.
  `Drum.PointOnDrum` hit-tests the scaled collision rectangle against the
  texture's own alpha (transparent pixels do not hit).
- Layered sounds. Every pad exposes `Sound1`/`Sound2`. Zone logic keys on the
  hit point: Kickbass/Tom/Snare/Crash/Misc split at `position.X + 15` for loop
  switching, HiHat hits right of the split play `Sound2` (closed) and left
  play `Sound1` (open), and Ride uses a radial 40·size split between bell and
  edge. `Drum.Play(1|2)` dispatches the matching `SoundItem`.
- Looping. With `DoLoop` (or the default `CurrentBPS = 0.25 s` retrigger),
  `Drum.Update` re-fires the held pad every `1/BPS` seconds while a touch ID
  remains down.
- Arrange. Pads are dragged by their selected touch ID (`Drum.Select` /
  `DeSelect`), drawn lime while selected; three presets rebuild fixed pad
  layouts (`Game1.LoadStandardArrangement1/2/3`); a config dialog edits type,
  both samples, zoom, layer depth, loop and can delete or create pads.
- Polyphony. `Game1.PlaySound` keeps at most 12 `SoundEffectInstance`s; the
  oldest is stopped and disposed first. Volume comes from the options dialog
  (`DrumsVolume`).
- Recording. While `RecordMode`, every hit appends
  `DrumRecordEvent { Time = now − RecordStartTime, Drum, Sound }`.
  `PlayButtonExecute` copies `RecordedPattern` to `PlaybackPattern` and
  `Game1.Update` fires events whose timestamp has elapsed (removing each in
  turn); the pattern loops implicitly by re-recording/clearing.
- Metronome. BPM defaults to 120, range 40–340, slider step 3; `MetronomeTimer
  = 60/BPM`; audio (tick sample) and visual (pad flash) styles are independent
  checkboxes. Options store music/drums volume, pad animation, spotlights and
  colour.
- Persistence: none beyond the session (patterns and layout reset on exit).

## 4. Controls

Multi-touch and deeply gesture-driven. Tap a pad to strike; hold for loop
retrigger; different zones of one pad trigger the second sample. In Arrange
mode, drag a pad with one finger to reposition (`Drum.Select` records the
selection offset) and use the zoom slider to scale the whole kit. Menu entries
are taps; sliders drag horizontally; checkboxes toggle. The metronome and
options dialogs are modal (input grabbed, first outside tap dismisses). There
is no accelerometer input.

## 5. Content inventory (must be re-authored)

139 XNB files: 39 UI/texture XNBs (pad art KickBass/Tom/Snare/HiHat/Crash/
RedButton, floor, menu, dialogs, sliders, buttons, splash, fonts, light ray)
and 100 per-sound XNBs under `Sound/`. The shipped audio set is
kick 1–21, conga 1–4, hihat 1–14, crash 1–10, clap 1–3 and misc 1–48; the
code additionally references tom 1–8, snare 1–30, ride 1–2 and misc up to 67,
which are absent from this package (the app was authored against a larger
library). These are third-party samples and must not be copied.

Re-authoring: synthesize a compact kit in `MiniSynth` — kick (swept sine),
snare (noise + tone), closed/open hat (noise bursts), tom (pitched sine),
crash/ride (noise + metallic partials), clap (multi-burst noise) and a few
percussion/misc voices. Pad chrome becomes token rectangles/circles with zero
radius; the six texture families collapse to labelled token surfaces.

## 6. Implementation plan

- Engine: new `ui/apps/drums/DrumMachineEngine.kt` — `object DrumMachineEngine`
  + immutable `data class DrumMachineState(mode, bpm, pads, recorded,
  selectedPad)`; `data class DrumPad(id, x, y, size, type, soundA, soundB,
  loopBps, layerDepth)`. Pure functions: `hit(pad, u, v)` zone split,
  `record(elapsedMs, pad, zone)`, `dueEvents(elapsedMs)`, `setBpm`.
- UI: replace `DrumMachineApp()` (`PianoDrum.kt:118`) with
  `ui/apps/drums/DrumMachineApp.kt` using `DetailScaffold(title = "drum
  machine")`, a `Canvas` stage for draggable pads, a text menu row
  (arrange/record/play/config/options/metronome), and the existing
  `MiniSynth` for audio. Everything from `LocalDoradoColors` /
  `DoradoTokens`; no rounded corners.
- Fidelity gaps vs current code: only 3 fixed rows × 16 steps (no free-form
  pads, no drag, no layers/loop, no record/playback, no metronome, no
  options), and no polyphony cap or per-pad sample choice.
- Persistence: pad layout + BPM + options as a JSON string in
  `graph.appState.put("drums", …)`; recorded patterns do not need to survive
  exit (device didn't).
- Tests: `app/src/test/java/com/heretek/dorado_hd/DrumMachineEngineTest.kt` —
  zone split (left/right and radial), loop retrigger timing, 12-voice
  polyphony eviction, record → playback timestamp ordering and one-shot
  removal, BPM clamp 40–340.
- Edge cases: overlapping pads and z-order (layer depth), deleting the
  selected pad, loop with multiple fingers on one pad, BPM changes mid-record,
  and zero-length patterns.

## 7. Citation log

`DrumMachine!Game1.LoadContent`, `DrumMachine!Game1.LoadStandardArrangement1`,
`DrumMachine!Game1.PlaySound`, `DrumMachine!Game1.RecordButtonExecute`,
`DrumMachine!Game1.PlayButtonExecute`, `DrumMachine!Game1.StopButtonExecute`,
`DrumMachine!Game1.SetArrangeMode`, `DrumMachine!Game1.HandleInput`,
`DrumMachine!Game1.Update`, `DrumMachine!Drum.Play`,
`DrumMachine!Drum.PointOnDrum`, `DrumMachine!Drum.ChangeType`,
`DrumMachine!Drum.Update`, `DrumMachine!Drum.Select`, `DrumMachine!Drum.DeSelect`,
`DrumMachine!DrumRecordEvent`, `DrumMachine!ConfigDialog`,
`DrumMachine!MetronomeDialog.TempoForwardExecute`,
`DrumMachine!MetronomeDialog.BPMSliderExecute`,
`DrumMachine!OptionsDialog.DrumsVolSliderExecute`, `DrumMachine!Menu.Show`,
`DrumMachine!DiscoLight.AddLightCone`, `DrumMachine!Dialog.Show`.
