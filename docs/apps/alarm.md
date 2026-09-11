# Alarm Clock

- **Official package:** `AlarmClock.exe` (alarm_clock)
- **Corpus:** `Zune HD Apps (Decompiled)/alarm_clock` (external, untracked)
- **Wave:** W1 · **Category:** utilities
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** alarm

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`AlarmClock.exe` is a 24-type / 124-method application (3,050 source lines across
the `AlarmClock`, `AlarmClock.Common`, `AlarmClock.Timers`, `AlarmClock.Views`
namespaces) built on the ZuneAppLib `Application`/`ViewController` stack. It is
three products in one shell: a live clock face, a one-shot alarm with
buzzer/playlist/radio sources, and a countdown sleep timer that fades music and
offers shake-to-snooze. A full-screen screensaver takes over after idle.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `AlarmClock.exe` | 24 | 124 | 3050 |
| `Microsoft.Xna.Zune.dll` | 111 | 829 | 15852 |
| `ZuneAppLib.dll` | 57 | 367 | 7743 |
| `ZuneCoreLib.dll` | 68 | 304 | 6297 |

State lives in one serialized `AlarmClockSettings` object (time, type, buzzer,
playlist, radio preset, snooze length, sleep duration/volume, dim, clock format).
`AlarmClock!AlarmClockApp.OnExiting` writes it to `settings.xml`; on first run the
defaults are a 20-minute sleep timer, medium sleep volume, `12h` clock, `Buzzer`
ringtone and a `Playlist` alarm type.

## 2. Screens & navigation

`AlarmClock!AlarmClockApp.CreateTwistPages` builds a `TwistController` with four
swipeable pages and a 32 px header: **Clock**, **Alarm**, **Sleep**, **Settings**
(`PageTitleClock` … `PageTitleSettings`). Pages are plain `ViewController`s, not
stack navigations, so horizontal twisting is the only top-level movement.
Modals are presented for configuration: `BuzzerViewController`,
`PlaylistViewController`, `RadioPresetViewController` and
`InformationViewController`, each with a navigation bar and back button.

The Clock page (`AlarmClock!ClockViewController`) shows a large `TimeView`
clock, an alarm on/off button with its time label, a sleep on/off button with
its countdown label, and a snooze badge while an alarm is ringing. The Alarm
page (`AlarmClock!AlarmViewController`) hosts a `TimeControls` hour/minute
wheel, an alarm-type selector and a context-sensitive *Configure* button that
opens the matching modal. Sleep (`AlarmClock!SleepViewController`) hosts a
second `TimeControls` and a four-level volume button; Settings
(`AlarmClock!SettingsViewController`) exposes 12/24-hour format, dim 1–10,
snooze 1–30 minutes and an info button. `AlarmClock!ScreensaverView` overlays
everything after 15 s of inactivity and is removed on the first touch.

## 3. Rules, scoring & progression

`AlarmClock!AlarmTimer.Enable` starts a repeating 1 s tick. Each tick
(`AlarmClockTicked`) compares the floored minute of local time against the
alarm time; if it matches and `HasAlerted` is false, `SoundAlarm` dispatches on
the configured type. Buzzer plays a looping `SoundEffectInstance` and schedules
a 1800 s timeout that silences, disables the alarm and raises `OnAlarmExpired`
(the clock page then disables the button). Playlist applies the stored shuffle
and repeat flags before `MediaPlayer.Play`; if the playlist is empty or expired
it falls back to the buzzer and shows the `ContentError` message. Radio plays
the selected preset's frequency/subchannel through `ZuneRadio`. Snooze
(`AlarmTimer.Snooze`) defers by `SnoozeTimeInMinutes` (default 10, range 1–30)
and re-arms the same minute comparison.

`AlarmTimer.AlarmSettingsStatus` gates the alarm on AC power (line status must
be plugged in, else `InvalidPower`), playlist validity and preset validity.
`SleepTimer.Enable` counts one second at a time; at zero it raises
`OnSleepAlarm` and for the following nine seconds plays `Ping` at the chosen
volume while watching the accelerometer. A delta greater than 1 g on any axis
triggers `OnSleepReset` (shake to restart the countdown); after −10 s
`OnSleepOutOfTime` fires. Every state change persists through the settings
events (`OnAlarmTimeChanged`, `OnAlarmTypeChanged`, `OnSleepTimeChanged`,
`OnClockSettingsChanged`).

## 4. Controls

All navigation is horizontal swipes on the twist header/content
(`AlarmClock!AlarmClockApp.CreateTwistPages`). Time selection is direct
manipulation of snapping scroll wheels: `AlarmClock!TimeControls` builds hours
(0–23), a 12-hour wheel (1–12), minutes (0–59) and an AM/PM postfix wheel at
75 px per row with wraparound (`FixedScrollView`, 0.25 s snap). Type selects
among Buzzer/Playlist/Radio; the Configure button opens the picker for the
current type. Settings rows are vertical `SelectionControl` wheels. The
screensaver consumes the first tap: if the alarm is ringing it snoozes, if the
sleep timer is out of time it restarts, otherwise it just dismisses
(`AlarmClock!AlarmClockApp.ScreenSaver_OnTouchesBegan`). Accelerometer motion
during the sleep alarm resets the timer. The device stays portrait
(`OrientationDetectionMethod.None`) and single-touch.

## 5. Content inventory (must be re-authored)

41 files: 29 PNG, 8 XNB, 3 XML, 1 WMA. Images: digit sprites `big_0`…`big_9`,
`big_colon`, AM/PM art, toggle button families (`but_alarm`, `but_sleep`,
`but_snooze`, shuffle/repeat on/off/focus), `grad`, and three system-button
sheets. Sounds: `Buzzer`, `Ping`, `Ring`, `beep` plus a silent `blank.wma`
placeholder used to stop radio playback. Text: `Fonts.xml` and `Strings/en.xml`;
`settings.xml` is runtime state, not shipped art.

Re-authoring: digits/AM-PM/colon become typography (`Selawik` + tokens);
button art becomes token-colored rectangles (zero radius); the four ringtones
become `MiniSynth` patterns in `SfxBank`/a new `AlarmTones` synth table; no
image or audio asset is copied. The plugin strings become Dorado-HD
localization keys.

## 6. Implementation plan

- Engine: `ui/apps/AlarmClockEngine.kt` — `object AlarmClockEngine` plus
  immutable `data class AlarmClockState(clockFormat, dim, alarmEnabled,
  alarmMinuteOfDay, alarmType, buzzer, playlistId, radioPreset, snoozeMinutes,
  sleepSecondsLeft, sleepVolume, snoozing)`. Pure functions: `tick(nowMinute)`,
  `snooze()`, `sleepTick(accelDeltaG)`, `nextAlarmAt()`.
- UI: `ui/apps/AlarmClockApp.kt` — replace the current list-only screen
  (`MiniAppsUtilities.kt:829`) with a four-page `DetailScaffold` shell
  (clock / alarm / sleep / settings) using tokens only; a `TimeView`-style big
  clock, scroll-wheel `TimeColumn` composables, and a screensaver overlay after
  15 s idle with tap-to-snooze.
- Persistence: settings blob through `graph.appState.put("alarm", …)`; alarms
  keep using `graph.alarms` + `AlarmScheduler`/`AlarmReceiver` for real OS
  delivery. Add alarm type + buzzer choice + playlist/radio reference to
  `AlarmEntity` (or to the settings blob keyed by alarm id).
- Audio: buzzer/beep/ring/ping via `MiniSynth` patterns (repeating loop for
  buzzer, 9-second ping cadence for sleep), never sampled assets.
- Sensors: sleep shake-reset via `rememberTilt()` with a >1 g delta check.
- Tests: `app/src/test/java/com/heretek/dorado_hd/AlarmClockEngineTest.kt`
  (minute-match fires once, snooze re-arms, 30-min buzzer expiry, sleep
  countdown/reset/volume cadence, 12h/24h formatting) and extend `LogicTest.kt`.
- Edge cases: DST/clock jumps on the minute compare; expired playlist fallback
  to buzzer; power-unplugged invalid state; screensaver suppressed while a
  config modal is open.

## 7. Citation log

`AlarmClock!AlarmClockSettings`, `AlarmClock!AlarmTimer.Enable`,
`AlarmClock!AlarmTimer.SoundAlarm`, `AlarmClock!AlarmTimer.Snooze`,
`AlarmClock!AlarmTimer.AlarmClockTicked`,
`AlarmClock!AlarmTimer.PlayBuzzer`, `AlarmClock!AlarmTimer.PlayPlaylist`,
`AlarmClock!AlarmTimer.AlarmSettingsStatus`, `AlarmClock!SleepTimer.Enable`,
`AlarmClock!SleepTimer.SleepClockTicked`, `AlarmClock!TimeControls`,
`AlarmClock!ClockViewController`, `AlarmClock!AlarmViewController`,
`AlarmClock!SleepViewController`, `AlarmClock!SettingsViewController`,
`AlarmClock!BuzzerViewController`, `AlarmClock!PlaylistViewController`,
`AlarmClock!RadioPresetViewController`, `AlarmClock!ScreensaverView.Notify`,
`AlarmClock!ScreensaverView.ClockTick`,
`AlarmClock!AlarmClockApp.CreateTwistPages`,
`AlarmClock!AlarmClockApp.ScreenSaver_OnTouchesBegan`,
`AlarmClock!AlarmClockApp.OnExiting`, `AlarmClock!AlarmClockApp.Main`.
