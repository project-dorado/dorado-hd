# Metronome

- **Official package:** `Metronome.exe` (metronome)
- **Corpus:** `Zune HD Apps (Decompiled)/metronome` (external, untracked)
- **Wave:** W1 · **Category:** music
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** metronome

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`Metronome.exe` is a DiNoGames XNA game: 5 types / 29 methods / 897 lines. It
is a dial metronome with a 40–340 BPM range, an up-to-16-beat accent grid,
one-key tempo stepping, direct BPM dragging on the dial ring, and a pulsing
beat indicator. Both an on-screen slider and the hardware D-pad change the
tempo; the dial doubles as a play/pause target.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Metronome.exe` | 5 | 29 | 897 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |

`Metronome` is the game class; `Beat` is a 30×30 grid cell with a hit test;
`TouchGUIControl`/`TouchButton` are a two-class widget kit used only for the
two tempo buttons (`Prev-On` / `Next-On`). `Metronome.Initialize` calls
`ZuneMedia.Lock(fLock: true)` so the metronome owns the media lock while it
runs; the constructor stops any playing `MediaPlayer` and sets a 0.01 s fixed
timestep. The screen is a fixed 272×480.

## 2. Screens & navigation

Single screen plus a splash overlay. `Metronome.LoadContent` builds
background, `Index`, `Beat`, slider track/cap, both arrow buttons, fonts
(`Arial32` for the BPM readout, `Arial12` for beat numbers), and the tick
sound. There is no page navigation and no About screen; the only on-screen
controls are the two tempo arrows at y=264 flanking the dial, the beat-count
slider near the bottom, the tappable dial and the 16 beat cells.

The first four seconds show the splash: `SplashTimer` starts at 4.0,
`Update` decrements it, `SplashAlpha` fades only over the final second, and
input is ignored until it reaches zero. The two style-arrow handlers
(`StyleForwardBtnExecute` / `StyleBackBtnExecute`) and the
`IndicatorTextures` list exist but only one indicator texture is loaded and
neither handler is ever wired to a button, so the visual style is fixed at
index 0 in the shipped build.

## 3. Rules, scoring & progression

- Tempo. `CalcBeatSettings` clamps BPM to 40–340, stores
  `BeatLength = 60 / BPM` seconds and places the dial index at
  `angle = ToRadians(BPM − 12)` on a dial centred at (136, 130) with radius
  99. The `ForwardBtnExecute`/`BackBtnExecute` buttons and the D-pad Up/Down
  (debounced by `InputTimer > 10`) step BPM by exactly 1.
- Dial drag. `HandleInput` measures the touch's distance from the centre: a
  press inside `radius − 30` toggles pause; a position between
  `radius − 30` and `radius + 20` computes
  `angle = π + atan2(centreY − y, centreX − x)` and, when the angle is in
  [0.5, 5.7831855] rad, sets `BPM = degrees(angle) + 11`. That maps the
  ring sweep onto the 40–340 range.
- Beat cycle. The beat-count slider clamps x to [22, 248] and computes
  `BeatsPerCycle = round(16 · (x − 22) / 226)`, so 0–16 beats. Each beat is a
  30×30 cell laid out eight per row at
  `x = 11.7 + 30.4·(i mod 8)`, `y = 354 + 71·floor(i / 8)`. Tapping a cell
  toggles `IsAccented`. Growing the count appends cells; shrinking removes
  them from the end.
- Clock. Every frame adds `ElapsedGameTime` to `CurrentBeatTimer`; when it
  reaches `BeatLength` it subtracts one beat length (so tempo changes retime
  the running stream), advances `CurrentBeat` with wraparound, sets the
  indicator alpha to 255, and calls `PlayTick(0.5)` on accented beats or
  `PlayTick(0)` otherwise. With no beats configured it still ticks unaccented.
  The indicator alpha decays at 1500 units/s.
- Sound. `PlayTick` disposes and recreates the `SoundEffectInstance` each beat
  and sets `Pitch` to the accent offset, so each tick is an independent
  one-shot.
- The app calls `ZuneDevice.SignalUserActivity` twice a minute while running
  (`TotalGameTime.Seconds % 30 == 0`) to keep the screen awake.

## 4. Controls

Tap the left/right arrows (or D-pad up/down) to step ±1 BPM; drag around the
dial ring to set tempo continuously; tap the dial centre to pause/resume; drag
the bottom slider to change the beat count 0–16; tap any beat cell to toggle
its accent. The D-pad debounce (`InputTimer > 10`) prevents key repeat from
running away. All input goes through `HandleInput` and `TouchButton` press
handling; there is no accelerometer and no multitouch requirement.

## 5. Content inventory (must be re-authored)

13 XNB files: `bg` (and `Splash`, which is the same 522 KB image), `Beat`
(30×30 cell), `BeatIndicator1` (the pulse overlay), `Index` (dial pointer),
`Prev-On` / `Next-On` arrows, `slider` / `SliderTrack` / `SliderCap`,
`Arial32` and `Arial12` fonts, and `tick` audio. There is no text content
beyond the numeric readout.

Re-authoring: all art becomes Compose canvas shapes and text using tokens
(dial arc, index line, square beat cells, slider track/cap); the tick becomes a
short `MiniSynth` click (noise burst + sine pip, higher pitch for accents); no
XNB is copied.

## 6. Implementation plan

- Engine: new `ui/apps/MetronomeEngine.kt` — `object MetronomeEngine` +
  immutable `data class MetronomeState(bpm, running, beats: List<Boolean>,
  currentBeat, pulse)`; pure functions `clampBpm`, `beatLengthMs`,
  `bpmForDialAngle(angle)`, `dialAngleForBpm(bpm)`, `setBeatCount(n)`,
  `toggleAccent(i)`, `advance(elapsedMs)`.
- UI: rewrite `MetronomeApp()` (`MiniAppsUtilities.kt:442`) as
  `ui/apps/MetronomeApp.kt`: `DetailScaffold(title = "metronome")`, a large
  BPM readout with ±1 arrows (replace the current ±10/±5/±1 row), a draggable
  dial, a 16-cell two-row accent grid, and the beat-count slider. Tokens only,
  zero radius.
- Audio: one `MiniSynth` loop; per beat `metronomeClick(accent)` (already
  exists in `Synth.kt`) — accent = higher pitch/level. Do not load samples.
- Fidelity gaps vs current code: range is 30–300 instead of 40–340, step
  controls are coarse, no dial drag/pause, only 4 time signatures instead of
  16 accentable beats, no beat-count slider, no splash, no indicator pulse
  and no wake-lock behaviour.
- Persistence: last BPM and beat pattern as a string blob in
  `graph.appState.put("metronome", …)`.
- Tests: `app/src/test/java/com/heretek/dorado_hd/MetronomeEngineTest.kt` —
  clamp 40/340, `beatLengthMs` values, dial-angle round trip, slider→count
  mapping (22→0, 135→8, 248→16), accent advance/wrap, pulse decay.
- Edge cases: changing BPM mid-beat, zero beats (unaccented tick), all-accent
  pattern, 1 BPM vs 340 BPM buffer cadence, and pause preserving the beat
  index.

## 7. Citation log

`Metronome!Metronome.CalcBeatSettings`, `Metronome!Metronome.Update`,
`Metronome!Metronome.HandleInput`, `Metronome!Metronome.PlayTick`,
`Metronome!Metronome.Draw`, `Metronome!Metronome.LoadContent`,
`Metronome!Metronome.ForwardBtnExecute`, `Metronome!Metronome.BackBtnExecute`,
`Metronome!Metronome.Initialize`, `Metronome!Beat.IsPointOnBeat`,
`Metronome!TouchButton.HandleTapEnd`, `Metronome!TouchButton.HandleTapStart`.
