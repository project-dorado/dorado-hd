# Color Spill

- **Official package:** `ColorSpill.exe` (Color Spill)
- **Corpus:** `—` (no assembly recovered; see §1)
- **Wave:** W3 · **Category:** games
- **Status:** `todo` · **Complexity:** TBD
- **Dorado-HD id:** —

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
| _none mined_ |  |  |  |

**This title is blocked at the corpus stage.** The mining pipeline reports
`"color-spill": "assembly not found in corpus"` and there is no
`_mine/_index/color-spill.json` or `_decompiled/color-spill/` directory. The
extracted folder named `color_spill` does not contain Color Spill: its
`gameinfo.xml` names the title **Reversi**, and its payload is
`Reversi.exe` + `OthelloRules.dll` (plus a shared `2moroGames.dll`). The
marketplace package list confirms why: ColorSpill and Reversi share the same
package GUID `97cfdcae937f46d999651163741d1f59`, so the archive extractor
collapsed the two into one folder. Finally, the original
`ColorSpill.zcp` (≈6.2 MB) is an NX container whose payload is AES-ECB
encrypted marketplace DRM and is not recoverable; only the plaintext manifest
survives.

Consequences for this spec:
- Sections 2–5 cannot be derived from code and are intentionally left as
  blocked fields rather than invented behavior.
- Section 6 lists the reimplementation shape that the marketplace description
  implies, explicitly marked provisional until the payload is recovered.
- Do not implement this title from the Reversi decompile — it is a different
  game.

## 2. Screens & navigation

_Blocked: no mined types, strings or assets exist for Color Spill. No screen
list can be asserted. Known artifact facts only: the shared GUID collision
above; the catalog description "Colorful twist on a classic game."; no
marketplace screenshots or locale files were recovered. A future spec must be
re-run from a clean ColorSpill package before any navigation work starts._

## 3. Rules, scoring & progression

_Blocked for simulation constants. The only recovered behavioral anchor is the
frozen marketplace description in `docs/zcp-inventory.md` (row 14):
"ColorSpill is a colorful twist on a classic game concept! The goal of this
game is to fill the entire board with a single color, and to do so in as few
steps as possible." That describes a flood-fill puzzle in the classic genre
formula, but no board size, color count, move par, timer, scoring rule or
progression model may be asserted from the corpus. Do not build gameplay on
this paragraph alone._

## 4. Controls

_Blocked: no input handlers were recovered (no touch, accelerometer or
button-handling code exists in the corpus). The marketplace genre implies a
tap-to-choose-color interaction, but that is an inference, not a mined fact.
Defer controls design until the assembly is mined._

## 5. Content inventory (must be re-authored)

_Blocked: the ColorSpill `.zcp` payload is encrypted, so no texture, audio or
level inventory can be produced. The only nearby content is mislabeled Reversi
data under `color_spill/gametitle/584E07D1/Content/` (fonts, images, two
sounds) and must not be treated as Color Spill content. Nothing from that
folder may be reused. When a clean package is available, inventory names and
sizes exactly as done for the other 11 W3 titles and re-author every asset._

## 6. Implementation plan (provisional — gated on corpus recovery)

- **Gate**: first re-extract ColorSpill from a clean GUID-unique `.zcp` and
  mine it with the same pipeline; only then begin implementation.
- Engine: `ui/apps/games/ColorSpill.kt` — `object ColorSpillEngine` with an
  immutable `SpillState(board: IntArray, width, height, colors, moves, par)` and
  a deterministic `step(dt)`; `floodFill(originX, originY, newColor)` returns a
  new board; region/flood logic and move counting are pure helpers.
- Screen: `ColorSpillScreen` in `DetailScaffold("color spill")`, Canvas grid
  of sharp-cornered cells using design tokens, a color-picker row, moves/par
  HUD, win/lose overlays.
- Skeleton state to keep pending constants: board size, color count, par per
  level, move-limit, difficulty ramp, and whether levels are discrete or
  endless. Fill these from the mined assembly, not from guesswork.
- Persistence/SFX: best score via `graph.games.record("color-spill", ...)`,
  resume via `graph.appState`, `SfxBank` for fill/win cues — all to be chosen
  once the real feature set is known.
- Tests: core flood-fill tests can be written ahead of content in
  `app/src/test/java/com/heretek/dorado_hd/ColorSpillEngineTest.kt`
  (single-color board is already won; connected region grows; diagonal cells
  are not connected; move counter increments), but kill/score tests must wait
  for the mined spec.

## 7. Citation log

- `_mine/_mine-report.json` — `errors["color-spill"] = "assembly not found in
  corpus"`; 62 apps, 61 mined.
- `_mine/_index/` — no `color-spill.json`; `_mine/_decompiled/` — no
  `color-spill/` directory.
- `color_spill/gametitle/584E07D1/` — contains `Reversi.exe`,
  `OthelloRules.dll`, `2moroGames.dll`; `color_spill/gametitle/gameinfo.xml`
  names the title "Reversi".
- `dorado-hd/docs/zcp-inventory.md` — GUID `97cfdcae937f46d999651163741d1f59`
  shared by ColorSpill and Reversi; ColorSpill row 14 carries the only recovered
  behavioral description.
- `Zune HD Apps/ColorSpill.zcp` — NX container, AES-ECB encrypted payload
  (per `dorado-hd/tools/zcp_inventory.py` format notes); no readable content
  inventory.
