# Dorado-HD Design Tokens

Ported from the Dorado `zune-design-system` skill (MIT) and adapted for the
Zune HD device UI. All Compose code must consume these tokens; raw hex literals
in screens are an invariant violation.

## Surfaces
| Token | Hex | Usage |
|---|---|---|
| `SurfaceBackground` | `#111111` | App canvas |
| `SurfaceElevated` | `#181818` | Panels, crossbar strip |
| `SurfaceTile` | `#202020` | Album art placeholder, cards |
| `SurfaceTileHover` | `#2A2A2A` | Pressed/hovered tile |
| `SurfaceBorderSubtle` | `#2C2C2C` | Hairline dividers (0.5-1 dp) |

## Accents (user-selectable)
| Name | Primary | Bright |
|---|---|---|
| Zune Pink (default) | `#FA2A55` | `#FF4D79` |
| Zune Orange | `#F09609` | `#FFA726` |
| Zune Cyan | `#1BA1E2` | `#33B5E5` |
| Zune Lime | `#339933` | `#4CAF50` |
| Zune Purple | `#A200FF` | `#B388FF` |

## Text opacities (white only)
| State | Opacity |
|---|---|
| Active / primary | 1.0 |
| Hover / pressed | 0.85 |
| Secondary metadata | 0.60 |
| Inactive crossbar/menu | 0.40 |
| Watermark type | 0.08 |

## Type scale (device-mode design units, 480x272 canvas)
| Role | Size | Weight | Case |
|---|---|---|---|
| Home menu item | 34 | Light | lowercase |
| Screen header (back-tap) | 40 | Light | lowercase |
| Crossbar pivot | 18 | Light | lowercase |
| Now Playing title | 26 | Light | lowercase |
| Now Playing artist/album | 15 | Light | lowercase |
| List primary | 14 | Normal | — |
| List secondary | 11 | Normal | — |
| Caption / time | 9 | Normal | — |
| Alphabet index | 10 | Normal | — |

## Motion
- Deceleration: cubic ease-out; pivot slides 320-420 ms.
- Kinetic fling: proportional (exponential) decay with per-frame velocity
  retention 0.95 at 60 Hz — the shell's `XuiTouchSettings[0x1C]`; see
  `docs/zune-hd-touch-settings.md`.
- List stagger entrance: 15-25 ms/item.
- Now Playing screensaver text drift: continuous, ~40 px/s, opacity 0.9.
- Quickplay reveal: 420 ms with parallax at 0.6x.

## Geometry
- Device canvas: 480 x 272 design units.
- Screen margins: 16 (edges), 24 (crossbar leading).
- Header height: 48.
- Crossbar strip height: 34.
- List row height: 40; album grid tile: 92 with 8 gutter.
- Mini-app tiles (marketplace > apps): 92, shared with album tile.
- Podcast / radio station rows: 40.
- Piano key: 24 wide x 72 high (white); black key 14 x 44.
- Radio dial: 96 tall; minor tick 8, major tick 16 (every 1 MHz).
- Sudoku cell: 36; board gap 1 (block gap 4).
- Playing card: 44 x 62 (corner radius 0).
- Hexic hex radius: 14, board gap 4.
- Corner radius: **0 everywhere.**
