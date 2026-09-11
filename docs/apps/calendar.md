# Calendar

- **Official package:** `Calendar.exe` (calendar)
- **Corpus:** `Zune HD Apps (Decompiled)/calendar` (external, untracked)
- **Wave:** W1 · **Category:** utilities
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** calendar

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`Calendar.exe` is the largest W1 utility: 66 types / 471 methods / 13,305 lines
across `ZuneHDCalendar`, `.Data`, `.Navigation`, `.UI`, `.UI.Controls`,
`.UI.Pages` and `.UI.Views`. It is a full PIM: day timeline, month grid,
continuous agenda, appointment editor, date/time pickers, recurring events,
reminders with snooze/dismiss, clash detection and XML persistence. The
`ZHDEmail.Common.dll` dependency contributes shared account/identity helpers
(`ZHDEmail.Common`).

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Calendar.exe` | 66 | 471 | 13305 |
| `Microsoft.Xna.Zune.dll` | 111 | 827 | 15830 |
| `ZHDEmail.Common.dll` | 36 | 97 | 1811 |
| `ZuneAppLib.dll` | 53 | 366 | 7780 |
| `ZuneCoreLib.dll` | 71 | 295 | 6389 |

## 2. Screens & navigation

The app boots through `Calendar!Calendar.LoadContent` → `App.LoadStateData` →
`AppointmentData.Load("1", …)` → `App.LoadStartupPage` →
`NavigationService.NavigateTo(new DayPage())`. Navigation is a real stack
(`Calendar!NavigationService.NavigateTo` / `GoBack`); pages disable the page
beneath them and restore it on back. A `MessageBox` layer floats above all
pages (`MessageBoxWrapper.Update`), and `ApplicationBar` supplies the three
system buttons plus an overflow menu.

- **Day** (`DayPage` → `DayPageView`): a `SwipeBar` with *Day* and *Agenda*
  pivots dragged by a `SwipeController`. The Day pivot mounts a `DayScroller`
  (hour rows, all-day strip, appointment rendering); Agenda mounts a
  continuously scrolling `Agenda`. App-bar buttons are Today, New and Month;
  `DayPageOverlay` dims the timeline while drag-selecting.
- **Month** (`MonthPage` → `MonthPageView` → `MonthViewControl`): a month grid
  with appointment dots; tapping a date returns to Day, dragging scrolls
  months, and a Today button re-centres.
- **Appointment** (`ViewAppointmentPage` → view; `EditAppointmentPage` →
  editor): the editor exposes subject, location, all-day toggle, date/time
  textboxes (which open the pickers), length, reminder, recurrence, status and
  notes, with a *More* disclosure for the lower block.
- **Pickers**: `DatePickerPageView` snaps day/month/year wheels;
  `TimePickerPageView` snaps hour/minute/AM-PM wheels. `AboutPageView` is a
  static credits page.

## 3. Rules, scoring & progression

Data is one `List<Appointment>` (`Calendar!AppointmentData`). An `Appointment`
carries Id, ClientID, Subject, Location, StartDate/EndDate, AllDay, `HowLong`
(Zero/30m/1h/90m/2h/AllDay/Custom), `Reminder` (None, 1/5/10/15/30 min, 1 h,
18 h, 1 day, 1 week), `Occurs` (Once, Daily, Weekdays, Weekly, Monthly,
Annually), `Status` (Free, Tentative, Busy, OutOfOffice), Notes, a recurrence
master reference and a deleted-occurrence list. `Appointment.CopyToAppointment`
is the edit-copy path; `ResetRenderParts` lays out one rectangle per spanned
day.

`AppointmentData.Add` expands recurrence (`ProcessRecurringAdd`), seeds
reminders (`AddReminders`) and sorts; `RemoveAt` tears down instances/reminders
(`ProcessRecurringRemove`). `AppointmentHasClash` ignores Free events and
checks interval overlap against one-shot and recurring neighbours for the same
day (status colours in `Appointment.DrawAppointmentMarker`: solid = Busy,
outline = Free, diagonal hatch = Tentative, translucent = OutOfOffice).
`CheckReminders` polls every 60 s, `ShowReminders` presents a Snoozer/Dismiss
message box, `SnoozeReminder` re-arms at +5 minutes, `DismissReminder` stamps
`LastReminderDate` and rolls recurring reminders to the next occurrence;
left/right swipes cycle multiple due reminders.

Persistence is XML: `AppointmentData.Load` reads
`<account>/AppointmentData.dat` and falls back to `AppointmentData.bak`;
`Save` debounces 5 s and `DoBackupAndSave` copies the previous file to `.bak`;
`App.SaveStateData` stores `AppState` to `appState.dat` and `OnExiting`
forces any pending save. The account id used at boot is `"1"`.

## 4. Controls

Touch-first and orientation-aware (`App.Init` sets keyboard-orientation
detection; pages rebuild bounds for portrait/landscape). Gestures: horizontal
swipe on `SwipeController` pivots Day↔Agenda and on `DayScroller` moves the
current day; kinetic vertical scroll on `DayScroller`, `Agenda` and
`ScrollableDataView`; timeline drag to create/select a range; long-press for
View/Edit/Delete; month-cell tap to jump; wheel drag with snap. Text entry is
mediated by `Textbox` and reminders swipe left/right to cycle.

## 5. Content inventory (must be re-authored)

54 files: 41 XNB and 13 XML. Fonts are 11 Segoe faces (`Segoe-10`,
`SegoeSemiBold-8`…`-16`, `SegoeSemiLight-10/-66`, `DebugFont`). Images are the
app-bar transport/action set (back, edit, save, cancel, done, new, today,
month, trash, refresh, overflow dots, checkbox/circle states, clash badge,
gradient, logo). The XMLs are view templates (`Agenda.xml`,
`MonthPageView.xml`, `DatePickerPageView.xml`, `PopupList*.xml`) plus
localization — chrome and views only, no lookup tables.

Re-authoring: every font/image becomes Compose typography and token surfaces
(zero radius); the view-template XMLs become Kotlin composables; only the
interaction geometry (272×480, 30 px hour rows, picker 45 px rows) survives as
numeric fact. No Microsoft art is copied.

## 6. Implementation plan

- Engine: new `ui/apps/calendar/CalendarEngine.kt` — `object CalendarEngine` +
  immutable `data class CalendarState(selectedDate, viewMode, appointments,
  reminders, messageBox)`. Pure logic: recurrence expansion, clash detection,
  reminder due-set, snooze/dismiss and day/week/month grid maths.
- UI: replace `CalendarApp()` (`MiniAppsUtilities.kt:959`) with a screen that
  keeps `DetailScaffold(title = "calendar")` but adds the device's three-view
  structure: day timeline with hour rows and overlapping-column layout, month
  grid, and a swipeable agenda pivot; a `SwipeBar`-equivalent text row
  (day | agenda) built from tokens. Appointment editor and date/time wheels
  are new files under `ui/apps/calendar/`.
- Data: `graph.calendar` (`CalendarRepository`/`AppointmentEntity`) already
  covers title/notes/startAt/endAt. Extend the entity (or a JSON blob in
  `graph.appState`) with location, allDay, length, reminder, occurs and status,
  and store the last view/date in `graph.appState.put("calendar", …)`.
- Reminders: schedule with the existing `AlarmScheduler`/`AlarmReceiver`
  mechanism and a snooze action; keep the 60 s in-app poll for the open app.
- Tests: `app/src/test/java/com/heretek/dorado_hd/CalendarEngineTest.kt` —
  recurrence expansion for all six `Occurs` modes, deleted occurrences,
  reminder offsets, 5-minute snooze, clash overlap (Free excluded), and
  month-grid wiring. Extend `LogicTest.kt` where cheap.
- Fidelity gaps vs current code: no agenda/day views, recurrence, reminders,
  clash status, length/status fields or pickers; `MonthGrid` only adds
  appointments from a title prompt.
- Edge cases: month length/leap years, Jan 1 week row, DST transitions,
  reminder exactly at boot, a recurring event straddling midnight, `.bak`
  recovery.

## 7. Citation log

`Calendar!AppointmentData.Add`, `Calendar!AppointmentData.RemoveAt`,
`Calendar!AppointmentData.ProcessRecurringAdd`,
`Calendar!AppointmentData.ProcessRecurringRemove`,
`Calendar!AppointmentData.AddReminders`, `Calendar!AppointmentData.CheckReminders`,
`Calendar!AppointmentData.ShowReminders`, `Calendar!AppointmentData.SnoozeReminder`,
`Calendar!AppointmentData.DismissReminder`, `Calendar!AppointmentData.AppointmentHasClash`,
`Calendar!AppointmentData.Load`, `Calendar!AppointmentData.Save`,
`Calendar!AppointmentData.DoBackupAndSave`, `Calendar!Appointment`,
`Calendar!Appointment.DrawAppointmentMarker`, `Calendar!Appointment.ResetRenderParts`,
`Calendar!DayPageView.FinishedLoading`, `Calendar!DayPageView.Swipe`,
`Calendar!DayPageView.EditAppointment`, `Calendar!DayPageView.ShowMonthView`,
`Calendar!DayPageView.appBar_ButtonClicked`, `Calendar!DayScroller`,
`Calendar!Agenda.RefreshAppointments`, `Calendar!MonthPageView.Setup`,
`Calendar!MonthViewControl.Clicked`, `Calendar!EditAppointmentPageView.Save`,
`Calendar!EditAppointmentPageView.DisplayAppointment`,
`Calendar!ViewAppointmentPageView.Setup`, `Calendar!DatePickerPageView.Setup`,
`Calendar!TimePickerPageView.Setup`, `Calendar!NavigationService.NavigateTo`,
`Calendar!NavigationService.GoBack`, `Calendar!SwipeController.AddPivotItem`,
`Calendar!MessageBox.ShowFormatted`, `Calendar!App.LoadStateData`,
`Calendar!App.SaveStateData`, `Calendar!Calendar.LoadContent`.
