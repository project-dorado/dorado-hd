# Stopwatch

- **Official package:** `StopWatch.exe` (stopwatch)
- **Corpus:** `Zune HD Apps (Decompiled)/stopwatch` (external, untracked)
- **Wave:** W1 · **Category:** utilities
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** stopwatch

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`StopWatch.exe` is a 22-type / 88-method ZuneAppLib application (2,441 lines)
with two modes behind a twist header: a stopwatch with lap splits, and a
countdown timer with hour/minute/second wheels and a flashing alarm. It runs
portrait-only on a 272×432 content area, keeps the screen awake while either
timer is running, and restores both timers (including wall-clock catch-up)
after relaunch.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `StopWatch.exe` | 22 | 88 | 2441 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneAppLib.dll` | 57 | 367 | 7742 |
| `ZuneCoreLib.dll` | 67 | 286 | 6089 |

Structure: `StopWatch!Watch` (Application + `WatchState { TIMER, STOPWATCH }`),
`StopWatch!StopWatch` and `!CountdownTimer` (`Function` subclasses that
own a view), `!Timer` (the shared elapsed-time engine), `!StopWatchControls`
and `!CountdownTimerControls`, `!Lap`/`!Laps`/`!LapFx`,
`StopWatch!DigitGroup`/`!Digits` (digit-sprite renderers) and a local
`ZuneAppLib.UI.FixedScrollView` wheel implementation.

## 2. Screens & navigation

`StopWatch!Watch.ApplicationFinishedLoading` forces portrait, creates a
`TwistController` with *Timer* and *Stopwatch* items in a 48 px header and a
(0, 40, 272, 480) content rect, initialises both `CountdownTimer` and
`StopWatch` functions, then restores `data`. The two pages are peers — a
horizontal twist switches modes, carrying over the running state of both.
Each page is ~432 px tall:

- **Stopwatch** (`StopWatchTimerView` + `Laps` + `StopWatchControls`): large
  minute/second digit groups (1.15 scale), small hour/millisecond groups and
  a colon; a split-time area top-right; a scrolling lap list below; and three
  round buttons — a 113 px-tall primary start/pause button centred near the
  bottom, a back/close reset button bottom-left and a system Add lap button
  bottom-right.
- **Timer** (`CountdownTimerView` + `CountdownTimerControls`): three wheel
  columns (hours, minutes, seconds) while editing, or the large countdown
  readout while running; the same start/pause and reset geometry; an alarm
  stop button that only appears when ringing.
- `LapFx` plays a three-frame 80 ms burst at the new lap row (76×37 art) to
  draw the eye to the split.

The mode is written back to `data` on exit and restored on launch, so the app
reopens on the last-used page.

## 3. Rules, scoring & progression

- Time engine (`StopWatch!Timer`): a `DateTime` accumulator advanced by
  `gameTime.ElapsedGameTime` while running; `reset` clears only when stopped
  and `setCache`/`getCache` snapshot the value for lap maths. The stopwatch
  auto-stops just before 23:59:59.95 (`StopWatchControls.update`), and
  `start` resets if re-armed at that point.
- Stopwatch controls (`StopWatchControls`): the primary button toggles
  start/pause and swaps its art; Lap records `now − lastLap` (also the total
  at the tap) and is enabled only while running; reset is enabled only when
  stopped or non-zero and clears the timer and laps. `Laps` stores each
  `Lap` (number, split, total) and `SaveLap` writes
  `number totalOADate lapOADate`.
- Countdown (`CountdownTimerControls`): the hour wheel wraps 0–23 and minute/
  second wrap 0–59, all 75 px rows with snap. `SetStartTimeFromScrolls`
  converts wheel positions to a start `DateTime`; starting hides the wheels
  and shows `start − now + 1 s` so zero is never shown early. At zero the
  timer stops, numbers dim, `start` plays at 0.85 volume and a 0.5 s flash
  begins (`Alarm` raises volume to 1.0 over 30 s); `StopAlarm` is a button and
  the alarm also stops itself after `alarmSeconds > 29`.
- Persistence (`Watch.SaveSettings`/`LoadSettings`): the `data` file stores
  mode, culture, stopwatch elapsed (OADate), the wall-clock start if running,
  countdown start/current values, running flags and laps. On load, running
  timers catch up by the wall-clock delta; an already-expired countdown resets
  to zero. `OnExiting` stops the alarm and saves.
- Wake: while either timer runs `Watch.Update` calls `StartActivitySignal`
  and stops it when both are idle.

## 4. Controls

Round-button taps for start/pause, lap and reset; vertical flick scrolling with
centre snap on the three countdown wheels; horizontal twist on the header for
mode switching. The lap button is disabled until running and reset until there
is something to clear, so the state machine lives in the enabled art.
Long-press does nothing beyond the enlarged start hit area. No accelerometer,
single-touch only (`IsMultitouchEnabled = false`).

## 5. Content inventory (must be re-authored)

23 files: 17 PNG images (digit sprites `big_0`…`big_9`, `big_colon`, three
start/pause/reset button states plus `but_big_pause_on`, `grad`, and a system
button sheet), one XNB (`Sounds/start`) and five XML files (`Fonts.xml`,
`Text.xml`, en/es/fr strings — a handful of words). The whole UI is digit art
and three buttons.

Re-authoring: digits become large `Selawik` text with tabular alignment (or a
drawn seven-segment look), buttons become token shapes with text labels, and
`start` becomes a short `MiniSynth` tone. The `LapNumber`/`LapTime`/`SplitFont`
faces map to existing token type steps; no PNG or font is copied.

## 6. Implementation plan

- Engine: new `ui/apps/StopwatchEngine.kt` — `object StopwatchEngine` +
  immutable `data class StopwatchState(running, elapsedMs, laps)` and
  `data class CountdownState(hours, minutes, seconds, remainingMs, running,
  alarming)`. Pure functions: `tick`, `lap`, `reset`, `start(fromScrolls)`,
  `alarmPhase(nowMs)` and serialise/deserialise for the `data` blob.
- UI: rewrite `StopwatchApp()` (`MiniAppsUtilities.kt:330`) as
  `ui/apps/stopwatch/StopwatchApp.kt` with two twist pages under one
  `DetailScaffold(title = "stopwatch")`: stopwatch (big split digits, lap
  list with split and total, start/pause/lap/reset) and timer (three snapping
  wheel columns, countdown readout, alarm state). Token colors, zero radius,
  `Selawik` digits.
- Persistence: one JSON blob in `graph.appState.put("stopwatch", …)` holding
  mode, elapsed, laps and countdown values; restore with wall-clock catch-up
  the way `LoadSettings` does, ticking from `SystemClock.elapsedRealtime()`.
- Audio: alarm cadence and lap cue through `SfxBank` on `MiniSynth`; no
  sample assets.
- Fidelity gaps vs current code: no countdown mode (the largest gap), no
  persistence/restore, no split-vs-total lap rows, no 23:59:59.95 cap, no
  comma-grouped hundredths formatting per the device, no alarm flash/crescendo
  and no wake lock while running.
- Tests: `app/src/test/java/com/heretek/dorado_hd/StopwatchEngineTest.kt` —
  elapsed monotonicity, lap split = total − last lap, reset only when
  stopped, countdown conversion from wheel values, alarm phase/crescendo
  timing, expire-on-restore, and blob round trip. Keep `LogicTest.kt` pass.
- Edge cases: process death while running, device reboot (wall-clock catch-up),
  zero-length countdown, lap while paused (disabled), 24 h rollover, and DST
  shifts during catch-up.

## 7. Citation log

`StopWatch!Watch.ApplicationFinishedLoading`, `StopWatch!Watch.LoadSettings`,
`StopWatch!Watch.SaveSettings`, `StopWatch!Watch.OnExiting`, `StopWatch!Watch.Update`,
`StopWatch!WatchState`, `StopWatch!StopWatchControls.addButtons`,
`StopWatch!StopWatchControls.update`, `StopWatch!StopWatchControls.reset`,
`StopWatch!StopWatchControls.StartOn`, `StopWatch!StopWatchControls.SetTime`,
`StopWatch!StopWatch.SaveLaps`, `StopWatch!StopWatch.LoadLaps`,
`StopWatch!Timer.update`, `StopWatch!Timer.start`,
`StopWatch!Timer.stop`, `StopWatch!Timer.reset`,
`StopWatch!Timer.setCache`, `StopWatch!Timer.getCache`,
`StopWatch!CountdownTimerControls.update`,
`StopWatch!CountdownTimerControls.SetStartTimeFromScrolls`,
`StopWatch!CountdownTimerControls.ResetScrollsFromTime`,
`StopWatch!CountdownTimerControls.Alarm`,
`StopWatch!CountdownTimerControls.StopAlarm`,
`StopWatch!Lap.setLap`, `StopWatch!Lap.SaveLap`,
`StopWatch!StopWatchTimerView.update`,
`StopWatch!CountdownTimerView.update`,
`StopWatch!DigitGroup.setDigits`, `StopWatch!Tools.floatToInt`.
