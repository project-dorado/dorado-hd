# UI parity audit — Phase 1 (Dorado-HD)

**Date:** 2026-09-11 · **Scope:** all 63 registry apps + the shared UI harness
**Companion register:** [`ui-parity-gaps.json`](ui-parity-gaps.json) (machine-readable)
**Program:** UI Parity Test, Audit & Remediation (goal: *usable + spec-faithful*)

## 1. Method & evidence

| Evidence | What it covers | Result |
|---|---|---|
| `AppSmokeSuite` (Robolectric Compose) | every registry app renders through the real host path at DEVICE 480×272, adaptive PORTRAIT 400×800, adaptive LANDSCAPE 800×360 | **63/63 green** |
| `tools/emulator_crawl.py` (Android 16 x86_64) | deep-link launch + screenshot + package-filtered crash scan per app | **63/63 launch, 0 crashes, 0 blank** |
| Static audit passes (3 parallel) | W1/W2/W7 apps + harness; W3/W4 casual; W5/W6 engines — flow, scaling, input keys, loops, persistence, concrete `file:line` defects | 63 open findings |
| Screenshot triage | all 63 entry screens read for clipping, overlap, overflow, empty layout | 15 ranked visual failures + cross-app patterns |

Baseline artifacts: `reports/emulator/crawl.json`, `crawl-summary.json`,
`shots/<slug>.png` (git-ignored; regenerate with the tool).

## 2. Severity & acceptance bar

- **P0** crash, softlock, dead-end, lost save, or input that breaks the run.
- **P1** systemic/visible defect: unreachable or off-screen controls, dropped
  input, stuck overlay, IME-obscured fields, silent SFX, layout overflow.
- **P2** fidelity/polish gap: empty-state copy, duplicated headings,
  inconsistent button rhythm, two-line row clashes.
- **Acceptance:** zero open P0/P1; every P2 closed or documented N/A; every app
  has a smoke test and crawl evidence; back/IME/layout contracts pass at all
  three configurations.

**Canon clarification.** Cropped typography (`alarm cloc`, `run and jun`) is the
Zune HD design language (canon §7) and is **not** a defect. The audit
distinguishes it from *functional* overflow: controls clipped off-screen,
words wrapped inside a button, or rows hidden under system bars.

## 3. Systemic classes (fix once, benefits everything)

| # | Class | Register | Notes |
|---|---|---|---|
| H-01 | Back always exits the mini-app | `DoradoRoot.kt:122`; no per-screen `BackHandler` | Every multi-screen app and overlay loses state on system back |
| H-02/03 | Context menu top-left + prompt text leak | `Common.kt:131-195` | Menus render over the header; nested prompts inherit text |
| H-04 | No IME insets | `MainActivity.kt:28`, `DoradoRoot.kt:102` | Compose/search fields covered by the keyboard |
| H-05 | Adaptive width ~227 units vs 480-authored rows | `DeviceCanvas.kt:42-57` | Root of the off-screen-control class (msnmoney, alarm, fan-prediction, shell game, solitaire, messenger) |
| H-06 | Bottom content under the system nav bar | weather/calendar | Reserve insets inside apps, not just at the root |
| H-07 | Wake shade raised on any `onPause` | `DoradoRoot.kt:83-92` | Crawl-confirmed: shade over the *next* app after a deep-link relaunch |
| H-08 | Room writes keyed on ticking state | stopwatch 30 Hz, drum per frame, alarm 1 Hz, messenger per keystroke | Jank/battery; debounce helper |
| H-09 | Composition side effects / double records | Sudoku, Reversi, Hearts, Spades, Solitaire | Move to effects with once-guards |
| H-10 | Silent SFX | Checkers, Chess, Hexic (`synth.start()` missing) | One-line each |
| H-11 | Bars/toasts overlay content | Facebook/Twitter shells, MiniPlayer | Reserve padding |
| H-12/13/14 | Menu rhythm, duplicate headings, empty states | across apps | P2 polish |

## 4. P0 game-breakers (per-app)

Run-and-jump death softlock · Hairball + Splatter Bug frozen retry ·
Vine Climb tap rewind · BBQ + Bees per-frame gesture churn · Slider Puzzle
pause/autosave · WordMonger save-on-exit · Castles stale camera ·
Texas Hold'em buy-in lock · Facebook About trap · Twitter orphan DMs ·
Piano octave snap · Hearts PassPicker duplicates · Metronome dial drag ·
Calendar month grid + nested prompt · Shell Game/Snowball/Tug-O-War
no-sensor dead input · AlarmManager exact-alarm crash + lost sleep timer ·
3D Picture Puzzle GL rebuild leak · PGR per-frame mesh leak + background
audio · Echoes countdown input · plus the cold-start state-clobber races
(Supernova, Snowball, RunAndJump, SpaceBattle, Penalty, VineClimb,
DecoderRing).

## 5. Visual failures from the emulator baseline

Ranked in the register (`V-01`…`V-15`): msnmoney tab bar, solitaire action
column, fan-prediction overflow, shell-game shop column, shuffle track rows,
zunesocial/zunereader truncation, audiosurf row clash, drummachine dead
panel, calculator scientific default + wrapped key, alarm tab clip, messenger
presence clip, 3D puzzle double title, weather nav-bar collision, twitter
banner crop.

## 6. Remediation waves

1. **W-H (harness):** H-01…H-11 + the adaptive overflow contract.
2. **W-P0:** every P0 row, each with a regression test (UI test where
   applicable, engine test otherwise).
3. **W-P2:** fidelity rows + spec-checklist closure, then record goldens.
4. **W-V:** full unit + UI suites, lint, emulator re-crawl, closeout docs.

Every fix updates its register row to `fixed` with the commit SHA; the
register is the falsifiable progress tracker for the program.

---

## 7. Remediation results (Phase 2/4 closeout)

**Register:** all P0/P1 rows are `fixed`; the two remaining P2 rows are closed
(H-13 duplicate headings) or documented as accepted variation (H-12 menu
rhythm, matching both rhythms present in the device-era UI).

**Harness (W-H):** hardware back follows the in-app header contract on every
sub-screen (`DetailScaffold(onBack)` → `BackHandler`); the context
menu/prompt anchors bottom-center and resets per request; the root applies
IME insets with `adjustResize`; the wake shade triggers on `ON_STOP` only;
`MiniSynth.start()` added where missing; Room writes throttled.

**Per-app (W-P0):** the 24 game-breakers (softlocks, frozen retries, dropped
inputs, stale captures, lost saves, buy-in loss, GL rebuild storms, sensorless
dead ends, exact-alarm crashes, cold-start clobber races) and the layout
overflow class are fixed, each with a regression test where the defect is
engine-expressible.

**Verification layers now in CI (`testDebugUnitTest`):**

| Layer | Coverage | Result |
|---|---|---|
| Unit/engine suites | 1,406 tests | green |
| `AppSmokeSuite` | 63 apps × 3 parity configs | green |
| `LayoutBoundsTest` | 63 apps × portrait/landscape: no unreachable clickables | 126 green |
| `BackContractTest` | system back on game sub-screens returns in-app | green |
| `GoldenCaptureTest` | 59 apps' device-mode entry frames, pixel tolerance | green |
| `DesignInvariantTest` | token/corner/motion invariants + silent-synth, unkeyed-pointer-input, onBack-forwarding and IME structure guards | 0 violations |
| Emulator crawl | 63 apps deep-link launch, crash + blank scan | 63/63 ok |

**Coverage checklist:** `docs/ui-parity-checklists.md` (63 apps × spec/smoke/bounds/golden/crawl).

**Residual, documented:** four clock/date screens (alarm, calendar, weather,
notes) are excluded from pixel goldens because their entry frame renders the
wall clock/date — they stay covered by smoke, layout, back and crawl layers;
GL resource behaviour is guarded by pure cache-policy tests rather than GL
instrumentation; IME correctness is structural (root insets + `adjustResize`)
and visually spot-checked on the emulator rather than simulated in Robolectric;
canvas-drawn text is not exposed to the accessibility tree (a standing
fidelity/accessibility opportunity, not a parity regression).
