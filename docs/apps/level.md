# Level

- **Official package:** `Level.exe` (level)
- **Corpus:** `Zune HD Apps (Decompiled)/level` (external, untracked)
- **Wave:** W1 · **Category:** utilities
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** level

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`Level.exe` is a 5-type / 13-method XNA Game (647 lines, `Level` namespace). It
is a three-in-one spirit level: a horizontal bubble tube for X, a vertical
bubble tube for Y, and a round bullseye bubble for both axes, each with a
signed 0.1° readout. A save button freezes a red "memory" bubble of the
current reading for later comparison, and an info page carries calibration
advice.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Level.exe` | 5 | 13 | 647 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |

The whole app is one `Game1` class. `Game1.Initialize` sets
`ZuneDevice.SetOrientation(Unlocked)` so the three instruments track the
device in any rotation; the game runs at a fixed 30 Hz. `Graph`/`GraphDots`
implement a 400-sample rolling plot of an axis value (one-pixel-wide lines
scaled to 272 px), but the shipped `Update` never transitions into the `graph`
state declared in the `appState` enum, so the plot is unreachable in this
build and only the level/splash/about states are live.

## 2. Screens & navigation

The state machine is `appState { splash, level, graph, about }` with only three
states actually entered:

- **splash** — full-screen splash; the first finger release switches to level
  (`Game1.Update` under `thisAppState == splash`).
- **level** — the instruments, drawn every frame: background, round bullseye
  (back plate at 95,148 with bubble origin 169,222), vertical tube (origin
  33,225), horizontal tube (origin 167,79), rotated numeric labels, then the
  save/X/info system buttons along the bottom.
- **about** — logo plus a wrapped credits/calibration text page; the back
  button returns to level.

Buttons are 46×46 system-button rectangles placed at (10, 424) save,
(113, 424) X, (216, 424) info and (10, 10) back; each gets an invisible 10 px
inflated collision rectangle. There is no page stack and no animation — state
switches are immediate `thisAppState` assignments on finger release.

## 3. Rules, scoring & progression

There is no score; the core is the accelerometer transform and the peak-hold
memory.

- `Game1.Update` reads `Accelerometer.GetState().Acceleration` into `Acc`, then
  computes `angleX = 90·Acc.X` and `angleY = 90·Acc.Y` (degrees shown as
  `#0.0°`).
- Bubble travel is a clamped linear function of acceleration: round bubble
  `±25·(Acc·2)` px on each axis clamped to ±50; vertical tube `71·(Acc.Y·2)`
  clamped ±142 with ±12 px horizontal jitter; horizontal tube `−33.5·(Acc.X·2)`
  clamped ±67 with ±6..12 px vertical jitter.
- Bubble size also responds: the vertical bubble's width scales by
  `1 − |Acc.X·0.5|` clamped to half..full, and the horizontal bubble's height
  by `1 − |Acc.Y·1.5|`, so the tube grows as the device approaches level.
- Save freezes `savedAcc`, the three bubble positions and `valX/valY`, then
  draws red translucent ghost bubbles and red readouts behind the live ones;
  X clears the memory. The X button only appears after a save.
- Info opens the about state; back returns.
- While level is active and the last touch is within 120 s, every update calls
  `ZuneDevice.SignalUserActivityAndWakeScreen()`; this is the app's
  screen-on keeper (`artificialTimeoutIncrease = 120f`).

The `Graph` helper stores up to 400 dots (`value` mapped to
`x = 136 + 136·value`, appended at a fixed y) and can draw the dots or the
last three as a "leader"; `GraphDots` is just that position pair. It is dead
code in this package but documents the intended rolling history view.

## 4. Controls

Accelerometer only for measurement (no touch-drag leveling, no calibration UI
inside the app — the about text tells the user to recalibrate in Settings →
About). Touch controls are the four plain buttons: save, clear-memory (X),
info, back. Touch is single-point and processed on release
(`fingerJustReleased`), with hit testing against the inflated collision
rectangles. Orientation is deliberately unlocked so the bubble tubes can be
used in portrait or landscape.

## 5. Content inventory (must be re-authored)

16 XNB files, all sprites and one font: `sprites/bg` (and the identical
`sprites/zbdf`), `degSquareSml` (degree tick squares), horizontal, vertical
and round back/guide/bubble triples, `logo`, `SmallFont`, and the large system
button sheet. There is no audio and no text beyond the about page.

Re-authoring: bubble tubes, guides and tick marks are drawn directly on a
Compose `Canvas` using `LocalDoradoColors` (no bitmap); the degree readout is
`Selawik` text with tokens; the system buttons become text affordances. No
Microsoft sprite is copied.

## 6. Implementation plan

- Engine: new `ui/apps/LevelEngine.kt` — `object LevelEngine` + immutable
  `data class LevelState(rollDeg, pitchDeg, bubbleX, bubbleY, bubbleScale,
  savedRoll, savedPitch, hasMemory)`. Pure functions `fromAccel(ax, ay, az)`
  (exact 90·g mapping and the three clamp curves above) and
  `format(readout)` producing one-decimal degrees.
- UI: rewrite `LevelApp()` (`MiniAppsUtilities.kt:532`) as
  `ui/apps/LevelApp.kt` under `DetailScaffold(title = "level")`, drawing all
  three instruments simultaneously on one canvas instead of the current
  surface/bubble toggle; add save/X peak-hold with red ghost styling, and the
  about page as a second DetailScaffold state. Zero radius, tokens only.
- Sensors: use the existing shared `rememberTilt()` (`AppSensors.kt`) rather
  than the bespoke listener in `LevelApp`, and convert its roll/pitch into the
  device's per-axis bubble offsets. Keep the 120 s wake-screen behaviour via
  the activity window flag if the level screen stays open.
- Fidelity gaps vs current code: only a round bubble plus a numeric line
  exists; missing the horizontal and vertical tubes, the bubble squish, the
  0.1° `#0.0` readouts per axis, peak-hold, the splash state and rotation
  freedom.
- Optional: re-enable the rolling XYZ graph (`Graph`) as a long-press debug
  overlay; it is unreachable on device and therefore marked as an extension,
  not canon.
- Tests: `app/src/test/java/com/heretek/dorado_hd/LevelEngineTest.kt` —
  accelerometer→degree mapping, every clamp boundary (round ±50, vertical
  ±142, horizontal ±67), bubble scale limits, peak-hold snapshot/clear,
  readout rounding, and absent-sensor fallback (`available = false`).
- Edge cases: device flat/upside-down, sensor noise (smoothing already in
  `rememberTilt`), screen rotation mid-read, and memory saved in one
  orientation then viewed in another.

## 7. Citation log

`Level!Game1.Initialize`, `Level!Game1.LoadContent`, `Level!Game1.Update`,
`Level!Game1.Draw`, `Level!Game1.appState`, `Level!Graph.Add`,
`Level!Graph.DrawDots`, `Level!Graph.DrawLeader`, `Level!GraphDots.GraphDots`.
