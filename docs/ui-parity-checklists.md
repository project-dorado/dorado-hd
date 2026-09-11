# UI parity coverage checklist

**Generated** from `docs/official-apps.json`, the golden set, the emulator
crawl summary and `docs/ui-parity-gaps.json`. One row per registry app:
every app is exercised by the smoke suite (3 configs), the layout-bounds
gate (portrait + landscape) and the on-device crawl; goldens add pixel
regression for screens without a live clock.

| App | Spec | Smoke | Bounds | Golden | Crawl | Open gaps | Notes |
|---|---|:--:|:--:|:--:|:--:|---:|---|
| `3d-picture-puzzle` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `a-beanstalk-tale` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `alarm` | ✅ | ✅ | ✅ | — | ✅ | 0 | clock/date screen — golden intentionally excluded |
| `animalgrams` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `audiosurf-tilt` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `bbq-battle` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `bees` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `calculator` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `calendar` | ✅ | ✅ | ✅ | — | ✅ | 0 | clock/date screen — golden intentionally excluded |
| `castles-and-cannons` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `checkers` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `chess` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `chordfinder` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `color-spill` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `decoder-ring` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `dr-optics-light-lab` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `drummachine` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `echoes` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `email` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `facebook` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `fan-prediction` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `finger-physics` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `fingerpaint` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `goo-splat` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `hairball` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `hearts` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `hexic` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `labyrinth` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `level` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `lucky-lanes-bowling` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `messenger` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `metronome` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `msnmoney` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `musicquiz` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `notes` | ✅ | ✅ | ✅ | — | ✅ | 0 | clock/date screen — golden intentionally excluded |
| `penalty-flick-soccer` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `pgr-ferrari-edition` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `piano` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `reversi` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `run-and-jump` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `shell-game-of-the-future` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `shufflebyalbum` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `slider-puzzle` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `snowball` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `solitaire` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `space-battle-2` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `spades` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `splatter-bug` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `stopwatch` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `sudoku` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `supernova` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `texasholdem` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `tiki-totems` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `tiles` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `trash-throw` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `tug-o-war` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `twitter` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `vans-sk8-pool-service` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `vine-climb` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `weather` | ✅ | ✅ | ✅ | — | ✅ | 0 | clock/date screen — golden intentionally excluded |
| `wordmonger` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `zunereader` | ✅ | ✅ | ✅ | ✅ | ✅ | 0 |  |
| `zunesocial` | — | ✅ | ✅ | ✅ | ✅ | 0 |  |

**Totals:** 63 apps · specs 62 · goldens 59 · crawl 63/63 · open gaps 0.

Spec-item fidelity (screens, controls, scoring, persistence, audio) is
tracked per app in `docs/ui-parity-gaps.json` and the behavioral specs;
simplifications accepted during implementation are recorded in each spec's
implementation-plan section and in the W1–W7 commit history.
