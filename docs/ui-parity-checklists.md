# UI parity coverage checklist

**Generated** from `docs/official-apps.json`, the golden set, the crawl
summaries and `docs/ui-parity-gaps.json`. Every app is exercised by the
smoke suite (3 configs), the layout-bounds gate (portrait + landscape),
the interactive flow crawl (primary flow, resume, cold start), the
on-device crawl and two golden frames (device 480x272 + adaptive
landscape) with the wall clock frozen.

| App | Spec | Smoke | Bounds | Goldens | Crawl | Flow | Open gaps |
|---|---|:--:|:--:|:--:|:--:|:--:|---:|
| `3d-picture-puzzle` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `a-beanstalk-tale` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `alarm` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `animalgrams` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `audiosurf-tilt` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `bbq-battle` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `bees` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `calculator` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `calendar` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `castles-and-cannons` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `checkers` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `chess` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `chordfinder` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `color-spill` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `decoder-ring` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `dr-optics-light-lab` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `drummachine` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `echoes` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `email` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `facebook` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `fan-prediction` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `finger-physics` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `fingerpaint` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `goo-splat` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `hairball` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `hearts` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `hexic` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `labyrinth` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `level` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `lucky-lanes-bowling` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `messenger` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `metronome` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `msnmoney` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `musicquiz` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `notes` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `penalty-flick-soccer` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `pgr-ferrari-edition` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `piano` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `reversi` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `run-and-jump` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `shell-game-of-the-future` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `shufflebyalbum` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `slider-puzzle` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `snowball` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `solitaire` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `space-battle-2` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `spades` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `splatter-bug` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `stopwatch` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `sudoku` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `supernova` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `texasholdem` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `tiki-totems` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `tiles` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `trash-throw` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `tug-o-war` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `twitter` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `vans-sk8-pool-service` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `vine-climb` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `weather` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `wordmonger` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `zunereader` | ✅ | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |
| `zunesocial` | — | ✅ | ✅ | 2/2 | ✅ | ✅ | 0 |

**Totals:** 63 apps · specs 62 · goldens 126/126 · crawl 63/63 · flow crawl 63/63 · open gaps 0.

Spec-item fidelity (screens, controls, scoring, persistence, audio) is
tracked per app in `docs/ui-parity-gaps.json` and the behavioral specs;
simplifications accepted during implementation are recorded in each spec's
implementation-plan section and in the W1–W7 commit history.
