# Zune HD Touch & Kinetic Settings (disassembly extraction)

Ground-truth record of the on-device touch/kinetic configuration recovered from
the reconstructed Zune HD firmware corpus (`zune-hd-disassembly/`). This is the
primary source cited by the canon for motion constants (`docs/zune-hd-ui-canon.md`
§6) and the Dorado-HD kinetic scroller.

Regenerable with `scripts/disassemble_zune_hd.py --extract-only`; the raw
binaries live only in the out-of-tree `zune-hd-disassembly/` corpus.

---

## 1. `XuiTouchSettings` — the 80-byte touch struct

The XUI runtime stores a single global `XuiTouchSettings` structure (20 × 32-bit
floats) at `xuidll.dll` VA `0x41874314` (base `0x41873BB0` + `0x764`).

```
typedef struct XuiTouchSettings {  // 80 bytes, 20 x float32
    float field[20];
} XuiTouchSettings;
```

### Accessors (xuidll.dll)

| Export / function | VA | Behavior |
|---|---|---|
| `XuiSetTouchSettings` | `0x41848DD0` | Copies `0x50` bytes from the caller into `0x41874314`. `NULL` → `E_INVALIDARG` (`0x80070057`). |
| `XuiGetTouchSettings` | `0x41848E10` | Copies `0x50` bytes from `0x41874314` to the caller. `NULL` → `E_INVALIDARG`. |
| `XuiTouchGetSettings` | `0x4183310C` | Per-element getter: if the element carries the override flag (`element+0x4 & 0x4000`), copies `0x50` bytes from `element+0xA8`; otherwise falls back to the global default. |
| `XuiTouchSetSettings` | `0x418330E4` | Per-element setter (stores the override on the element). |
| `XuiTouchSnapToTarget` | `0x41833134` | Snaps an element to its scroll target. |
| `XuiScrollEndGetDirection` | `0x41832010` | Returns the scroll-end direction. |
| `XuiTouchEnableAxisHysteresis/HorizontalAxis/VerticalAxis` | `0x418331CC` / … | Axis lock + hysteresis toggles. |
| `XuiTouchGetPositionOffset` / `XuiTouchGetPositionTarget` / `XuiTouchGetState` / `XuiTouchGetCanvasSize` | `0x4183301C` … | Scroller state getters. |
| `XuiProcessMultiTouchMessage` | `0x4181F100` | Multi-touch dispatch. |

The default struct is populated by `FUN_4184C660` (`xuidll.dll` VA `0x4184C660`).

## 2. Field values

The field *layout* is exact (offset order verified from the store sequence in
`FUN_4184C660` and the `XuiSetTouchSettings` call site in `gemstone.exe`). The
field *names/semantics* are not recoverable from the stripped binaries (no PDB
is present in the corpus), so offsets are given rather than invented names.

| Offset | `xuidll.dll` default | `gemstone.exe` effective |
|---|---|---|
| `0x00` | 300.0 | 300.0 |
| `0x04` | 1.9 | 1.9 |
| `0x08` | 1.9 | 1.9 |
| `0x0C` | 1.5 | 1.2 |
| `0x10` | 25.0 | 25.0 |
| `0x14` | 60.0 | 45.0 |
| `0x18` | 0.05 | 0.05 |
| `0x1C` | 0.7 | 0.95 |
| `0x20` | 1.0 | 0.0 |
| `0x24` | 0.5 | 0.5 |
| `0x28` | 0.5 | 0.5 |
| `0x2C` | 1.0 | 1.0 |
| `0x30` | 10.0 | 10.0 |
| `0x34` | 100.0 | 140.0 |
| `0x38` | 1.0 | 1.0 |
| `0x3C` | 1.0 | 2.0 |
| `0x40` | 0.6 | 0.6 |
| `0x44` | 0.25 | 0.25 |
| `0x48` | 0.6 | 0.6 |
| `0x4C` | 1.0 | 1.0 |

- **`xuidll.dll` default** — the runtime baseline built by `FUN_4184C660`; each
  field is loaded from a literal-pool float at `0x41801358`…`0x4180418C`.
- **`gemstone.exe` effective** — what the Zune HD shell actually applied. Built
  at `gemstone.exe` VA `0x1C900`–`0x1CB64` from a per-field config read
  (`0x704AC`) layered over the literal defaults, then handed to
  `XuiSetTouchSettings` (thunk at `gemstone.exe` VA `0x6AECC`).

Fields `0x00`, `0x04`, `0x08`, `0x10`, `0x18`, `0x24`, `0x28`, `0x2C`, `0x30`,
`0x38`, `0x40`, `0x44`, `0x48`, `0x4C` are identical in both; the shell
overrides `0x0C`, `0x14`, `0x1C`, `0x20`, `0x34`, `0x3C`.

## 3. What Dorado-HD consumes

Only the offsets with the strongest corroboration are used, and each usage is
recorded at the token:

| Offset | Value | Dorado-HD token | Rationale |
|---|---|---|---|
| `0x10` | 25.0 | `DoradoTokens.SKIP_DRAG_PX` | Both XUI and the shell agree on `25.0`; it is the scroll/skip drag deadband nearest the previously hand-tuned `24`. |
| `0x1C` | 0.95 | `DoradoMotion.KINETIC_FRAME_RETENTION` | Shell-lengthened from the XUI default `0.7`; used to reproduce the signature long deceleration of the kinetic scroller. |

Offsets without corroborated semantics are **not** turned into tokens. They
remain here for a future pass if symbol data surfaces.

## 4. Confidence & provenance

- **High confidence:** the existence, address, size and 20 values of
  `XuiTouchSettings`; the accessor/override model; the per-offset values in §2.
- **Medium confidence (recovered 2026-09-10):** the field *vocabulary*. The `xuidll`
  string table (`0x41803F74–0x41804150`) exposes ~20 touch/scroll property names —
  `HorzScroll`, `VertScroll`, `HorizontalFling`, `VerticalFling`,
  `HorizontalScrub`, `VerticalScrub`, `HorizontalRubberBand`,
  `VerticalRubberBand`, `FadeIn`, `FadeOut`, `PositionOffset`,
  `CenterSmallCanvas`, `EnableBandBreak`, `KeyScrollDistance`, `SmoothScroll`,
  `ScrollPaddingBefore/After`, `ZoomTarget`, `Hollow`, `Direction` — matching the
  struct arity. The name→offset **mapping is not proven**, so offsets remain the
  authoritative identifier.
- **Low confidence:** the exact name↔offset assignment (no `.pdb`/`.map`/`.sym`
  exists in `PavoBaseline.Cab`; the XUI headers are not public).
- **Method:** `scripts/disassemble_zune_hd.py` (ROM TOC → ARM32 PE rebuild),
  `llvm-objdump --triple=armv6-none-eabi`, and Ghidra headless decompilation
  (`ZuneHD_Project`). Float literals resolved from the reconstructed map.

## 5. Assessed but not recovered

These were investigated for app parity and could not be pinned to a value from
the corpus, so **no code change was made** (a guessed value would violate the
canon-first rule):

- **HUD/overlay/volume dwell — RECOVERED (2026-09-10).** `zhud_serv.dll` hides its
  dwell values behind `XuiSetTimer` call sites, recoverable from the decompiled
  timer arguments: **7000 ms** dim (`FUN_419CD420`), **5000 ms** screensaver,
  **3000 ms** notifications (`FUN_419CDAC8`/`FUN_419CDF00`), **30000 ms** background
  (`FUN_419C0584`), **12000 ms** constant. HD's single `IDLE_SCREENSAVER_MS = 5000`
  matches the saver but has no dimmer/notification ladder.
- **Now Playing screensaver idle timeout** — no inline timeout constant is
  present near `GemNowPlayingMusicShowScene`; the value arrives through the
  shell's config reader (`gemstone.exe` `0x704AC`), not a literal.
- **Rating tri-state values** — the heart/broken/none states are an enum in the
  library service, not a touch setting; no numeric constant is needed
  (Dorado-HD's `Rating` already matches canon §4).
- **Typography metrics** — the authentic Zegoe UI TTFs are extracted, but are
  not redistributed; Dorado-HD ships metric-compatible Selawik, so no token
  change is warranted.

## 6. Kinetic scroll integrator (resolved 2026-09-10)

The XUI scroll tick is `xuidll.dll` VA `0x41841D58`. State (uint index → byte
offset): flags `[0x25]`=+0x94 (bit 2 = active), velocity `[0x2A]`=+0xA8,
position `[0x27]`=+0x9C, target `[0x28]`=+0xA0, step `[0x22]`=+0x88, last-tick
`[0x2E]`=+0xB8, coefficients `[0x2C]`=+0xB0 and `[0x2D]`=+0xB4 (the element's
in-place touch block at `+0xA8`, fields `+0x08`/`+0x0C`).

- **Position:** `pos += (dt_ms / 1000) · v`, dt floored at **33.333 ms** (30 fps);
  the divisor literal is `1000.0` (`xuidll@0x41801920`) and the floor is
  `33.333` (`@0x41803CE4`).
- **Velocity:** `v' = v · (1 + c[+0xB0])`, clamped to `±( step · c[+0xB4] )`.
- **Stop:** when `pos` reaches the target the timer is killed (`XuiKillTimer`)
  and the shell snap callback runs; the velocity zero-compare is `0.0`.
- **Tick:** `XuiSetTimer(elem, 1, 0x10)` = **16 ms (62.5 Hz)** (`FUN_41848B98`).
- **Drag→velocity:** `(Δpx/Δt)·scale`, capped at **32.0** (`FUN_4184C310`;
  literal `@0x41804168 = 32`).
- **Rubber-band:** explicit damped spring
  `-(pos−target)·k[+0xD8] − v[+0x50]·d[+0xD4] + v[+0x50]` (`FUN_41848FC4`).

**Correction:** the earlier claim that `XuiTouchSettings[0x1C]` (=0.95) is the
per-frame retention is **not supported** — the integrator reads `+0xB0/+0xB4`
(= fields `[0x08]/[0x0C]`; defaults 1.9/1.5, shell 1.9/1.2), never `[0x1C]`
(default 0.7). The device is a **dt-scaled, clamped proportional glide at 62.5 Hz
with a 30 fps floor**, not a per-frame `retention^k` model. HD's
`DoradoMotion.KINETIC_FRAME_RETENTION` (0.95) / `KINETIC_LANE_FRAME_RETENTION`
(0.94) are empirically tuned approximations of this glide and should be labelled
as such (see [`zune-hd-parity-audit.md`](zune-hd-parity-audit.md) §3).


