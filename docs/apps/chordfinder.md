# Chord Finder

- **Official package:** `ChordFinder.exe` (chord_finder)
- **Corpus:** `Zune HD Apps (Decompiled)/chord_finder` (external, untracked)
- **Wave:** W1 · **Category:** music
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** chordfinder

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`ChordFinder.exe` is a 10-type / 30-method ZuneAppLib application (1,221 lines;
`Chords`, `GuitarZune` and a local `ZuneAppLib.UI` copy). It is a guitar chord
reference: pick a root and a chord quality on two snapping wheels, browse the
voicings on a third, and see one rendered fretboard diagram with finger
numbers, open-string circles and mute crosses. A small practising metronome
(40–226 BPM with a 4/4 accent) ships in the same assembly.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `ChordFinder.exe` | 10 | 30 | 1221 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneAppLib.dll` | 57 | 367 | 7743 |
| `ZuneCoreLib.dll` | 68 | 304 | 6297 |

`ChordFinder!Chord` owns the chord database as an `XmlDocument` loaded from
`Content\Text\ChordsDB.xml`; `ChordFinder!Finger` turns one XML string entry into a
view (mute, open, or a pressed-fret button stamped with the finger index). The
main screen is `ChordFinder!TestViewController` despite its name; it hosts the
fretboard and the three wheels. `ChordFinder!MetronomeViewController` is a
fully implemented second screen that the shipped navigation never opens.

## 2. Screens & navigation

One screen plus modals (`ChordFinder!GuitarZuneApp.ApplicationFinishedLoading`):
the root is a black `ViewController`; a `StackViewController` pushes
`TestViewController` and presents it modally. The chord screen is a static
272×480 layout:

- Fretboard: six vertical string lines at `x = 24 + i·38`, six fret wires at
  `y = fretHeight + j·54 − 16` (fret height 54, five frets, width 5·38 + 14),
  and a five-row fret-number column at `x = 237`.
- Three `FixedScrollView` wheels at y=336, each 76×135 with a 45 px row and
  0.25 s snap: root (x=8), quality (x=108), voicing (x=196).
- A gradient banner over the wheels, and a 26×26 help button at (236, 8) with
  a 26 px input margin that presents `AboutViewController` (titled
  *Information*) as a modal.

`ChordFinder!FixedScrollView` is a wraparound list: rows outside the visible
three are dimmed, a scroll-speed threshold stops coasting, and an interpolator
snaps the nearest row to the centre before firing `OnStoppedMoving`, which
recomputes the chord. `GuitarZuneApp` is single-touch and exits on the
hardware Back button.

## 3. Rules, scoring & progression

There is no score. The selection state machine is
`(root, quality, voicingIndex) → chord XML node → finger view`:

- `ChordFinder!TestViewController.GetChord` selects
  `/chords/chord[@name='<Root> <Quality>']/guitarString`; if the node set is
  empty it retries with the enharmonic spelling of the root (the code swaps
  C#↔Db, D#↔Eb, F#↔Gb, G#↔Ab, A#↔Bb), then sets
  `VarianceCount = guitarStringNodes / 6 − 1`.
- `ResolveChordVariance` maps the eight quality keys (`maj`, `min`, `maj5`,
  `maj7`, `dom7`, `min7`, `aug`, `sus4`) to display names (Major, Minor,
  Major Flat Five, Major Seven, Dominant Seven, Minor Seven, Augmented,
  Suspended).
- `DrawFingers` scans each six-string voicing: an empty or `-1` fret is muted,
  `0` is an open string, positive values place a finger button at
  `y = 54 + 54·(fret − startingFret)`. If the highest fret is ≤ 5 the window
  starts at fret 1 and the nut is drawn thick (7 px); otherwise the lowest
  positive fret becomes the window start and the nut thins to 3 px.
  `WriteFretNumbers` labels the five frets accordingly.
- Fingers carry an index read from the second XML field and draw it in the
  button (`FretMarker` font), so barre shapes show the correct finger numbers.

The metronome is a `System.Threading.Timer`: `WeightButton.BPM` converts BPM
to an interval of `1000 / (BPM/60)` ms, `TickerEvent` plays the tick sample
and accents every fourth beat (`SoundEffect.Play(gain, pitch 0.32)`), and
Start seeds the counter so the first beat is accented. Dragging the weight
maps y ∈ [144, 330] to BPM = y − 104; the wheel pauses while dragging and
restarts after release.

## 4. Controls

- Root/quality/voicing wheels: vertical flick-drag with wraparound and
  centre-snap; the row nearest the centre is white, the rest gray
  (`FixedScrollView.OnStoppedMoving` handlers in the `TestViewController`
  constructor).
- Help: tap the top-right button → modal About; hardware Back dismisses.
- Weight metronome: press-and-drag vertically; Start/Stop are plain buttons;
  tapping the logo stops the timer and dismisses the modal.
- No accelerometer, no multitouch (`GuitarZuneApp` sets
  `IsMultitouchEnabled = false`).

## 5. Content inventory (must be re-authored)

40 files: 26 XNB, 9 PNG, 1 WAV, 4 XML. The database is
`Text/ChordsDB.xml` — **512 chord nodes, 3,090 guitar-string entries, 106
distinct chord names** (Microsoft-authored content, never copied). The rest is
chrome: `wallpaper`, `metro_bg`, `logo`, fret/finger/arrow/variance art,
`Sounds/tick.wav`, fonts and `Strings/en.xml`. `Text/Variations.xml` is a
377-byte quality map.

Re-authoring: build a fresh chord table from public-domain music theory
(root × 8 qualities × N voicings, 6 strings each) with finger indices; a much
smaller curated set is acceptable as long as every displayed shape is
musically correct and no Microsoft table is transcribed. All fretboard,
finger, arrow and banner art becomes Compose canvas drawing with tokens; the
tick becomes a `MiniSynth` click.

## 6. Implementation plan

- Engine: new `ui/apps/chordfinder/ChordDatabase.kt` —
  `object ChordDatabase` + immutable `data class ChordSelection(root, quality,
  voicing)`; `data class Voicing(strings: List<Int>, fingers: List<Int>,
  baseFret: Int)` with `baseFret` derived by the >5 rule. Keep the existing
  `ChordData` shape as the fallback table while the fuller table is authored.
- UI: replace `ChordFinderApp()` (`MiniAppsUtilities.kt:615`) with
  `ui/apps/chordfinder/ChordFinderApp.kt`: `DetailScaffold(title = "chord
  finder")`, a canvas fretboard (existing `Fretboard` composable is close —
  add finger numbers, the fret-number column and the thick/thin nut), and
  three snapping text columns instead of the current single-row pickers.
  All colors via `LocalDoradoColors`, sizes via `DoradoTokens`.
- Fidelity gaps vs current code: only 7 natural roots (no enharmonic
  C#/Db … A#/Bb), 7 qualities with a `dim` key that the device does not have
  and no `maj5`/`aug`/`sus4`, only one voicing per chord (no variation wheel),
  no window-shift for shapes above fret 5, no finger numbers, and the
  fallback silently substitutes nothing (good) but cannot browse alternatives.
- Optional fidelity: port the built-in metronome (BPM 40–226, tap-drag
  weight, accent every fourth beat) as a second page or omit it explicitly.
- Audio: `MiniSynth` + `SfxBank.play("tick")` for the metronome; no WAV.
- Tests: `app/src/test/java/com/heretek/dorado_hd/ChordFinderTest.kt` —
  enharmonic fallback, voicing count > 0, six-string shapes, window shift for
  frets > 5, quality mapping, and metronome interval/accent maths.
- Edge cases: roots with no voicing in a quality (show the empty-state rather
  than substituting), scroll wrap at both ends, and single-voicing chords
  (variation wheel of length 1).

## 7. Citation log

`ChordFinder!Chord.ChordName`, `ChordFinder!Chord.ChordModulation`, `ChordFinder!Chord.VarianceCount`,
`ChordFinder!Finger.UpdateFinger`, `ChordFinder!TestViewController.GetChord`,
`ChordFinder!TestViewController.DrawFingers`,
`ChordFinder!TestViewController.WriteFretNumbers`,
`ChordFinder!TestViewController.GetVariationsScroller`,
`ChordFinder!TestViewController.ResolveChordVariance`,
`ChordFinder!TestViewController` constructor, `ChordFinder!FixedScrollView.Update`,
`ChordFinder!FixedScrollView.CreateInterpolator`,
`ChordFinder!MetronomeViewController.WeightButton.TouchesMoved`,
`ChordFinder!MetronomeViewController.TickerEvent`,
`ChordFinder!MetronomeViewController.btnStart_OnClick`,
`ChordFinder!MetronomeViewController.btnStop_OnClick`,
`ChordFinder!AboutViewController`,
`ChordFinder!GuitarZuneApp.ApplicationFinishedLoading`.
