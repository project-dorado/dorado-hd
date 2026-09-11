# Calculator

- **Official package:** `Calculator.exe` (calculator)
- **Corpus:** `Zune HD Apps (Decompiled)/calculator` (external, untracked)
- **Wave:** W1 · **Category:** utilities
- **Status:** `native` · **Complexity:** TBD
- **Dorado-HD id:** calculator

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

`Calculator.exe` is a 12-type / 108-method `ZuneAppLib.Application` (2,262
lines, single `Calculator` namespace). It ships two machines in one binary: a
portrait four-function calculator with a memory row, and a landscape
scientific calculator with four scrollable function pages and a statistics
scratchpad. The two share the expression state, so rotating the device keeps
the current value and swaps the keypad and display renderer.

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `Calculator.exe` | 12 | 108 | 2262 |
| `Microsoft.Xna.Zune.dll` | 83 | 798 | 14932 |
| `ZuneAppLib.dll` | 82 | 357 | 9910 |

The engine is a small stack machine: `Calculator!Expression` pairs a left
operand with a `BinaryFunction`, and `Calculator!Calculator` holds one current
expression plus a `Stack<Expression>` for parentheses. `Functions` is a static
table of arithmetic, trig, log, root, power, factorial and statistical
delegates. `DisplayRender` raster-draws `txtField` from number/symbol atlases,
right-aligned and scaled to fit.

## 2. Screens & navigation

`Calculator!Calculator.ApplicationFinishedLoading` loads both layouts and shows
the portrait view. `LoadPortraitMode` builds a 60 px keypad at (10, 142): three
number rows, a wide zero, a double-height equals, a memory row (MC / M+ / MR /
±), AC and backspace, with a black 252×60 display bezel. `LoadLandscapeMode`
builds a 480×272 landscape frame with 60×40 keys, a `StackViewController` of
scientific pages, three DEG/RAD/GRAD radio buttons, and a stats page with a
population `ScrollView`.

Navigation is rotation-only. `ApplicationFinishedLoading` subscribes to
`Device.OrientationChanged`; `PresentPortraitMode` / `PresentLandscapeMode`
cross-fade the two view controllers with a 0.25 s 136 px slide (interpolator),
then enable the new view and re-render the shared value. The scientific pages
cycle with the `fn1`…`fn4` key via `sciView.PushViewController` /
`PopViewController` (page counter wraps). `AboutViewController` is a modal with
a version block; the device info/back buttons are system buttons.

## 3. Rules, scoring & progression

There is no score; the "state machine" is the expression interpreter.

- Entry. `AppendNumber` accumulates digits with the locale decimal separator,
  toggles `hasInput`, converts C→AC once real input exists, stores figures with
  `N0` thousands separators, and caps the field at 15 significant characters
  portrait / 19 landscape (plus sign). `Negate`, `Backspace` and `ClearEntry`
  operate on `-0` and decimal edge cases explicitly.
- Binary ops. `SetBinaryFunction` stores the pending function and left side;
  pressing another operator with fresh input folds the pending expression.
  `ExecuteBinayFunction` evaluates, records `repeatFunction`/`repeatValue`, and
  `EqualsPressed` resolves the whole parenthesis chain so a second equals
  repeats the last operation on the current value.
- Parentheses. `LeftParenthesis` pushes the current expression; each
  `RightParenthesis` evaluates and pops, feeding the result to the enclosing
  operand.
- Errors. `DetectIllegalCalculations` flags divide-by-zero and even `yroot` of a
  negative; `setResult` also maps NaN/Infinity to `Err` and clears operands.
- Range. Portrait switches to `0.###E+0` above 999,999,999,999; landscape to
  `0.#####E+0` above 999,999,999,999,999 or 19 digits. `fixText` chooses
  precision so the mantissa fits the field. `Functions.Factorial` clamps its
  input to 180; trig results are rounded to 15 decimals; cube/y-root preserve
  sign. `Percent` computes `left × current ÷ 100`.
- Scientific. Angle mode converts trig inputs degrees/grads→radians and inverse
  trig outputs back (`ExecuteTrigFunction`). Statistics functions consume the
  population list built by `AddToPop` and cleared by `ClearPopulation`.
  Memory is MC / M+ / MR over one `double`; π, 2π, π/2 and e have dedicated
  inserts `InsertPi`/`Insert2Pi`/`InsertHalfPi`/`InsertE`, and `rand` calls
  `Functions.Random`.

## 4. Controls

Direct touch on keys only; no gestures, no accelerometer and no long-press.
`CalcButton` distinguishes press/focus/unfocus art with a fading outline
(`FocusFade`), and `CalcRadioButton.Update` paints the selected angle unit
white and the others gray. On the stats page the population list scrolls under
the keypad (`popView`) so past entries stay visible. Rotation is the only
navigation gesture; the `fn1` key cycles the scientific pages, and the
backspace glyph and AC/C key share one cell.

## 5. Content inventory (must be re-authored)

17 files: 4 font XNBs (`ButtonFont`, `NumberFont`, `RadioButtonFont`,
`StatsFont`), 9 PNGs (numbers, symbols, stretched button sheets and outlines,
light/dark variants) and 4 XMLs (`TextDictionary.xml`, `en`/`es`/`fr` strings).
All glyphs and chrome are look-up art; nothing is authored content.

Re-authoring: every key label becomes text in `Selawik`; the number/symbol
atlases become formatted strings in the display; button chrome becomes token
surfaces; `TextDictionary` keys become Dorado-HD string resources. No PNG or
font is copied.

## 6. Implementation plan

- Engine: keep `ui/apps/CalcEngine.kt` as the pure parser but grow it into a
  calculator state machine in `ui/apps/CalculatorEngine.kt` —
  `object CalculatorEngine` + immutable
  `data class CalculatorState(display, value, pendingOp, left, stack, memory,
  angle, population, repeatOp, repeatValue, error)`. Add the missing functions
  (factorial capped at 180, cube/cube-root, sign-correct yroot, 1/x, logʏ,
  10ˣ/2ˣ/eˣ, rand) and the repeat-equals rule; replace ad-hoc string building
  in `CalculatorApp` with `state.press(key)` transitions.
- UI: `ui/apps/CalculatorApp.kt` — keep `DetailScaffold(title = "calculator")`,
  but render the device-accurate layouts: device mode (480×272) shows the
  scientific 4-page panel with DEG/RAD/GRAD and the stats list; adaptive mode
  shows the portrait four-function block. Keys stay 4–5 per row, all colors
  from `LocalDoradoColors`, all sizes from `DoradoTokens`; no rounded shapes.
- Fidelity gaps vs the current `MiniAppsUtilities.kt:58` implementation: no
  memory, no angle modes, no stats population, no factorial/root/power block,
  no repeat-equals, no C/AC distinction, no thousands grouping, no 15/19-digit
  exponential overflow rule, no `Err` state, no per-op parenthesis stack.
- Tests: `app/src/test/java/com/heretek/dorado_hd/CalculatorEngineTest.kt` —
  operator precedence and right-associative power, paren folding, repeat
  equals, memory, angle conversion, factorial cap, divide-by-zero → error,
  overflow to exponential, backspace/negate `-0` cases; keep the existing
  `LogicTest.kt` cover.
- Edge cases: locale decimal separator, `-0` entry, `%` with no pending op,
  empty `rand(0)` returning [0,1), stats with an empty population, and
  rotation mid-entry preserving `value` but not `hasInput`.

## 7. Citation log

`Calculator!Calculator.AppendNumber`, `Calculator!Calculator.SetBinaryFunction`,
`Calculator!Calculator.ExecuteBinayFunction`, `Calculator!Calculator.EqualsPressed`,
`Calculator!Calculator.ExecuteUnaryFunction`, `Calculator!Calculator.ExecuteTrigFunction`,
`Calculator!Calculator.ExecuteStatisticalFunction`, `Calculator!Calculator.setResult`,
`Calculator!Calculator.fixText`, `Calculator!Calculator.DetectIllegalCalculations`,
`Calculator!Calculator.Percent`, `Calculator!Calculator.ClearInput`,
`Calculator!Calculator.ClearEntry`, `Calculator!Calculator.Negate`,
`Calculator!Calculator.Backspace`, `Calculator!Calculator.MemoryClear`,
`Calculator!Calculator.MemoryRecall`, `Calculator!Calculator.AddToMemory`,
`Calculator!Calculator.InsertPi`, `Calculator!Calculator.LeftParenthesis`,
`Calculator!Calculator.RightParenthesis`, `Calculator!Calculator.AddToPop`,
`Calculator!Calculator.ClearPopulation`, `Calculator!Calculator.LoadPortraitMode`,
`Calculator!Calculator.LoadLandscapeMode`, `Calculator!Calculator.PresentPortraitMode`,
`Calculator!Calculator.PresentLandscapeMode`,
`Calculator!Calculator.ApplicationFinishedLoading`,
`Calculator!Functions.Factorial`, `Calculator!Functions.YRoot`,
`Calculator!Functions.Sine`, `Calculator!DisplayRender.Draw`,
`Calculator!CalcButton.AddSymbol`, `Calculator!CalcRadioButton.Update`.
