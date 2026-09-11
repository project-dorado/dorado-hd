# Lucky Lanes Bowling

- **Official package:** `bowling.exe` (luckylanesbowling)
- **Corpus:** `Zune HD Apps (Decompiled)/luckylanesbowling` (external, untracked)
- **Wave:** W6 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| `bowling.exe` | 292 | 1713 | 34409 |
| `Babaroga.Movies.Xml.dll` | 13 | 26 | 301 |
| `Babaroga.Movies.dll` | 14 | 53 | 1196 |
| `Babaroga.Toolchain.ImagePacker.dll` | 5 | 17 | 353 |
| `Microsoft.Xna.Zune.dll` | 110 | 825 | 15694 |
| `Shaders.dll` | 2 | 1 | 78 |
| `SkinnedModel.dll` | 7 | 15 | 278 |
| `Zazz.dll` | 11 | 30 | 581 |

## 2. Screens & navigation

- Splash/`IntroScreen`/`LoadingScreen` → `SplashScreen`/`MainMenu`, a
  `CarouselMenu` of 3D scenes: Gametype, Lane, Ball, Character and
  Character Bio (`CarouselGametypeScene`, `CarouselLaneScene`,
  `CarouselBallScene`, `CarouselCharacterScene`,
  `CarouselCharacterBioScene`).
- `CustomizeMenu` branches: `CustomizePlayerMenu`, `CustomizeGameMenu`
  (length, bumpers, gold pins), `CustomizeLaneMenu`, `CustomizeBallMenu`,
  `CustomizeHudMenu`; `GameSpecificsMenu` sets mode details;
  `OptionsMenu`, `HighscoresMenu`, `AchievementScreen` and
  `ConfirmationMenu` complete the menu set.
- Multiplayer: `HostJoinMenu` (connection type), `NetworkGametypeMenu`,
  `NetworkLobbyMenu`, `MultiplayerReadyScreen`/`MultiplayerWaitScreen` and
  player-list/team editors.
- Gameplay flow: `GameScreen` drives `IntroScreen` → `InputScreen`
  (throw/hud) → ball simulation → `ScorecardScreen`/`HudScreen` →
  `ClosingScreen` → `GameCompleteMenu`. `TutorialScreen` wraps four
  lessons (`AimedSwingScreen`, `AimedSwipeScreen`, `CurvedSwingScreen`,
  `CurvedSwipeScreen`) plus `GameTutorial`; `WagerScreen` handles bets;
  `ViewJohnBoard` shows the overhead john-board replay.

## 3. Rules, scoring & progression + simulation model

- Ten-pin bowling on a 42-unit-wide lane 720 units long, with the ball
  simulation starting at y=4.78, z=-765. After a throw the physics steps
  the simulation until pins settle (the catch-up constant is
  `SIM_THROW_TIME = 8`), using the authored `alley-collision` planes for
  the lane, gutters and walls; pin/ball states before and after each throw
  are recorded for scoring and replays.
- Standard scorecard: `Frame`/`FrameScore` record per-ball pin counts and
  marks (`Mark.Strike` for 10 and `/` for a spare, `X` display), with
  extra shots where the frame rules allow. `GameLength` is `FullRound`
  (10 frames) or `HalfRound` (shorter league). `BumperMode` On/Off and
  `PinType` Regular/Gold modify the alley.
- Three `GameMode`s: **Regular** (classic ten-pin), **Blackjack** (three
  frames, `BLACKJACK = 21`, dealer character and face-off framing) and
  **Golf** (`MAX_NUMBER_GOLF_SHOTS = 5`, `MAX_GOLF_SCORE = 999`, lower
  score wins). Lanes are Retro, Whale, Desert, Forest, Space (or Random);
  each has an ambience track and decorated scenes.
- Opponents: 21 characters with `SkillLevel` Rookie→Babaroga and personal
  favourite balls; humans and CPU teams mix (`MaxNumPlayers`), CPU throw
  types are Normal/Straight/Gutter. Characters have betting habits, so
  wager events can stake coins (`WagerEvent`, `WagerScreen`).
- Replays: `ReplayEvent` classifies a replay as Spare, Strike, Turkey or
  Perfect and plays a skippable cutscene from recorded frame data.
- Progression: no currency grind — unlockables are gated by
  `UnlockMethod`s: play a mode (Blackjack/Golf), play multiplayer,
  score/spare/split/strike/turkey/gold-turkey/strike-out, win as/at a
  character, or gold/red pins. `Unlockable` entries map those methods to
  characters, ball skins and modes; achievements are recorded in the
  `AchievementScreen`/`AchievementData` store and high scores per
  lane/mode in `HighscoresMenu`.

## 4. Controls

- Touch throw: drag an arc from the top line (y=55) to the baseline
  (y=360) with a minimum swipe length of 30 units; the throw is sampled at
  up to 64 swing points and filtered (0.95). Resulting ball velocity and
  curve are clamped to 0.35–0.95, the spin velocity modifier is 1.3, and
  curve modifiers stay within 0.05–0.8.
- Accelerometer throw: device swing samples run between Y=0.8 and Y=0.1;
  acceleration mode uses max velocity 0.9, velocity modifier 0.55, spin
  modifier 0.65 and max spin 0.85. A tilt input screen and sensitivity
  settings are provided.
- HUD button regions cover pause, step-back, step-forward and scorecard;
  tutorial modes teach aimed/curved swing and swipe throws separately.
  Multiplayer moves are transmitted as compact throw/frame packets.

## 5. Content inventory (must be re-authored)

670 XNB files. Characters: 21 rigged models + textures + player icons.
Balls: 22 skins + Random (plain/logo/green/hot pink/storm/note/sun/skull/
cannon/sheriff/money/mushroom/cauldron/alien-tech/earth/gem/rock/scarab/
spartan/eye/nuclear/eight-ball). Lanes: 5 themed scene sets (Retro, Whale,
Desert, Forest, Space) with static props, pin racks and icon models.
Models 57, animations 78 (57 general + 21 john-board frames: strike,
spare, split, turkey, gold pin, field goal, numbers), menu images 177,
packed image sheets 18, scorecard art 32, particles 21, shaders 32,
paths 5 (throw/cpu/multiplayer paths), scenes 8, fonts 4. Audio: 20 root
XNB + effects 22 + voiceovers 56 + music 12. Text en/es/fr. Re-author all:
our own low-poly lane props, rigged characters, ball skins, UI art and
synthesized music/SFX/voices (no Microsoft assets).

## 6. Implementation plan

- 3D: `ui/apps/engine3d/` — lane module (textured quads + prop meshes),
  ball/pin rigid bodies with a fixed-step deterministic solver (same
  integrator for replay), camera manager with replay cameras and a
  simplified john-board overlay, skinned character playback for our own
  rigs.
- Logic: `ui/apps/games/Bowling.kt` — scorecard/Frame state machine, three
  modes, team/CPU turns, throw input classifier (swipe arc/accel swing),
  unlock/achievement rules, high-score store.
- UI: carousel menus and customization lists in `DetailScaffold`;
  `SfxBank` for ball roll, pin hits, crowd, replays; music from generated
  loops.
- Tests (`app/src/test/java/com/heretek/dorado_hd/`): frame/strike/spare
  scoring incl. tenth frame, golf/blackjack scoring constants, throw
  classifier clamps, deterministic pin simulation replay, unlock matrix,
  CPU turn order.
- Fidelity target: full-parity mechanics with original content; the
  campaign ships the 5 original lane themes and a smaller character roster
  first. Risks: physics tuning for believable pin action, replay camera
  parity, and the animation/voiceover asset count.

## 7. Citation log

- `bowling.exe!Physics` (`LANE_LENGTH`, `LANE_WIDTH`, `BALL_SIM_START_Y/Z`,
  `SIM_THROW_TIME`, collision-plane names), `bowling.exe!Ball`,
  `bowling.exe!Pin`
- `bowling.exe!InputBallThrowScreen` constants and `PointerDragged`,
  `PointerReleased`, `bowling.exe!Swing` (`Y_START`, `Y_STOP`, `Curve`,
  `Add`)
- `bowling.exe!Frame`, `.GetMark`, `.NumMarks`, `.NumStrikes`;
  `bowling.exe!FrameScore`; `bowling.exe!Scorecard`
- `bowling.exe!GameMode` (`BLACKJACK`, `NUM_BLACKJACK_FRAMES`),
  `GameLength` (`MAX_NUMBER_GOLF_SHOTS`, `MAX_GOLF_SCORE`), `BumperMode`,
  `PinType`, `LaneType`, `BallSkin`, `CharacterType`, `SkillLevel`
- `bowling.exe!GameData` (`MaxNumPlayers`, `IsBlackjackDealer`,
  `GetMultiplayerBlackjackFrameNumber`), `bowling.exe!Player`
- `bowling.exe!UnlockMethod`, `bowling.exe!Unlockable`,
  `bowling.exe!AchievementType`, `bowling.exe!ReplayEvent`
- `bowling.exe!MainMenu`, `.CustomizeMenu`, `.TutorialScreen`,
  `.AimedSwingScreen`, `.CurvedSwipeScreen`, `.HostJoinMenu`,
  `.HighscoresMenu`, `.AchievementScreen`
