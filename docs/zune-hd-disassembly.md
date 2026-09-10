# Zune HD Disassembly & Reverse Engineering Architecture

This document specifies the internal software architecture, firmware layout, and reverse-engineering pipeline of the Microsoft Zune HD on-device platform that **Dorado-HD** replicates.

Sister specification to the desktop client disassembly documented in `zune-disassembly/README.md`.

---

## 1. Hardware & Platform Stack

| Component | Specification |
|---|---|
| **SoC** | Nvidia Tegra APX 2600 (dual ARM11 MPCore @ 533–600 MHz, Ultra-Low Power GeForce GPU) |
| **Operating System** | Windows Embedded CE 6.0 R3 (Core architecture codenamed "Pavo") |
| **Display** | 3.3" capacitive multi-touch OLED, 480x272 native resolution (design units in Dorado-HD) |
| **Execution Architecture** | eXecute In Place (XIP) from flash ROM mapped into kernel space (`0x81790000`–`0x83D5160C`) |
| **Native Shell** | `gemstone.exe` (ARM32 native PE executable) |
| **UI Engine** | `xuidll.dll` (Microsoft Xbox UI / XUI runtime ported from Xbox 360 to Windows CE) |
| **Graphics API** | Direct3D-Mobile hardware accelerated via `zd3d.dll` / `zrender.dll` |

---

## 2. Firmware Packaging & ROM Layout

Firmware updates were delivered over the air and via the Zune desktop client through Microsoft Update baseline CABs. The Zune HD package is **`PavoBaseline.Cab`** (firmware v4.5, 28,384,856 bytes).

### Container Contents (`PavoBaseline.Cab`)

| File | Size | Description |
|---|---|---|
| **`EXT.bin`** | 39,366,567 bytes | Main OS system partition, services, shell, assets, and XUI engine |
| **`NK.bin`** | 12,004,095 bytes | Windows CE kernel (`nk.exe`), core HAL, and boot drivers |
| **`Recovery.bin`** | 12,004,095 bytes | Golden recovery image |
| **`ZBoot.bin`** | 662,963 bytes | Second-stage bootloader |

### Windows CE Binary Image Record Format (`.bin`)

Windows CE `.bin` files start with the 7-byte signature `B000FF\n` (`42 30 30 30 46 46 0A`), followed by the image start virtual address (`0x01790000` in `EXT.bin`) and length (`0x025C160C`).

The payload consists of contiguous memory records:
- `ULONG dwRecAddr`: Memory destination address.
- `ULONG dwRecLen`: Data byte length.
- `ULONG dwRecChk`: Arithmetic checksum.
- `BYTE bData[dwRecLen]`: Raw bytes.
- Termination record: `dwRecAddr == 0`, `dwRecChk == 0`, `dwRecLen` = jump entry point.

Flattening these records yields a contiguous 39,589,388 byte memory segment spanning physical addresses `0x81790000` through `0x83D5160C`.

---

## 3. Table of Contents (TOC) & Metadata

At offset `0x40` of `EXT.bin` resides the ROM signature `ECEC` (`45 43 45 43`) followed by a 32-bit pointer to the master **`ROMHDR`** structure at offset `0x025C0188`.

### `ROMHDR` Layout (84 bytes, 21 DWORDs)

```
dllfirst        = 0x4041C0C6
dlllast         = 0x41C2C0C6
physfirst       = 0x81790000
physlast        = 0x83D5160C
nummods         = 109  (Executable modules: EXEs and DLLs)
numfiles        = 60   (Static data files: fonts, graphics, XMLs)
ulRAMStart      = 0x80BD0000
ulRAMFree       = 0x80BF4000
ulRAMEnd        = 0x85000000
cpuType         = 0x000001C2 (ARM)
```

Immediately following `ROMHDR` are:
1. **109 `TOCentry` records** (32 bytes each) describing every executable module.
2. **60 `FILESentry` records** (28 bytes each) describing every data file and asset.

---

## 4. Key Subsystem Inventory

### Core UI Shell & Engines

| Module | Size | Role |
|---|---|---|
| **`gemstone.exe`** | 558,080 B | The Zune HD shell; defines the entire Metro scene graph, gesture routing, and view lifecycles |
| **`xuidll.dll`** | 465,408 B | Xbox UI (XUI) engine; handles visual tree compilation, layout, animations, and touch dispatch |
| **`zd3d.dll`** | 185,344 B | Direct3D-Mobile hardware driver interface for Nvidia Tegra APX |
| **`zrender.dll`** | 211,968 B | High-level rendering engine for UI scenes and media textures |
| **`zhud_serv.dll`** | 189,440 B | System Head-Up Display (volume overlay, transport controls, lock shade) |
| **`zmedia_serv.dll`** | 273,920 B | Media playback service (audio/video pipeline, playlist queue, equalizer) |
| **`zcontent_serv.dll`** | 56,832 B | Media library indexing, metadata database, and search service |
| **`zam_serv.dll`** | 93,184 B | Zune Application Manager (manages XNA mini-app lifecycles) |
| **`compositor.exe`** | 68,096 B | Hardware window compositor |
| **`zie.exe`** | 197,632 B | Zune Internet Explorer shell |
| **`zsplash.exe`** | 18,944 B | Animated startup bootsplash |

---

## 5. Authentic Metro Scene Hierarchy (from `gemstone.exe`)

Disassembly and symbol extraction from `gemstone.exe` reveals the authentic Microsoft internal naming and scene structure that Dorado-HD's Kotlin composables replicate:

### Navigation & Home
- **`GemStartScene` / `GemStartListScene`**: Main Home text menu (`ui/screens/HomeScreens.kt`).
- **`GemTiltScene`**: Quickplay 3D parallax shelf parked "left and rear" (`ui/screens/HomeScreens.kt:QuickplayPage`).
- **`GemPivotScene`**: Horizontal crossbar pivots (`design/components/CrossbarBar.kt`).

### Media & Playback
- **`GemNowPlayingScene`**: Master Now Playing container (`ui/screens/NowPlayingScreen.kt`).
- **`GemNowPlayingMusicScene` / `GemNowPlayingMusicMainScene`**: Track artwork, artist, title card.
- **`GemNowPlayingMusicShowScene`**: Smooth screensaver drifting over artist photography.
- **`GemNowPlayingMusicListScene` / `GemQueueListScene`**: Now Playing track queue.
- **`GemNowPlayingRadioScene` / `GemNowPlayingRadioMainScene`**: FM / HD Radio interface.

### Library & Wayfinding
- **`GemLibraryArtistScene` / `GemLibraryArtistAlbumListContent`**: Artist drilldown.
- **`GemLibraryLetterPickerScene`**: The signature right-edge alphabet jump rail (`ui/components/AlphabetJumpRail.kt`).
- **`GemLibraryAlbumGridContent`**: Album art square grid.
- **`GemLibrarySongScene` / `LibrarySongListContent`**: Vertical kinetic song list.

### Marketplace & Settings
- **`GemMarketplaceScene` / `GemMarketplaceGamesScene`**: Catalog browser (`ui/screens/MarketplaceScreens.kt`).
- **`GemSettingScene` / `GemSettingAboutScene` / `GemSettingLockOnScene`**: Settings hierarchy.
- **`GemOverlayScene`**: Transport and volume overlay.

---

## 6. Authentic Zegoe UI Typography

The official Zegoe UI TrueType fonts are stored uncompressed within the ROM file table and extracted byte-for-byte:

- **`ZegoeUI.ttf`**: Regular (Version 3.01, 11 tables, 31,884 bytes)
- **`ZegoeUI_B.ttf`**: Bold (31,876 bytes)
- **`ZegoeUI_Blk.ttf`**: Black (45,604 bytes)
- **`ZegoeUI_L.ttf`**: Light (34,148 bytes)
- **`ZegoeUI_SB.ttf`**: Semibold (46,252 bytes)
- **`ZegoeUI_SL.ttf`**: Semilight (34,768 bytes)

In Dorado-HD, **Selawik** (OFL) is shipped by default as the metric-compatible open-source substitute. Users may provide authentic Zegoe UI fonts locally.

---

## 7. Automated Disassembly Pipeline

The repo provides an end-to-end extraction and decompilation script:

```bash
# Extract assets and reconstruct valid ARM32 PE binaries (< 10 seconds):
python3 scripts/disassemble_zune_hd.py --extract-only

# Run deep Ghidra headless analysis on gemstone.exe and xuidll.dll:
python3 scripts/disassemble_zune_hd.py --ghidra
```

### Output Directory Structure (`zune-hd-disassembly/`)

```
zune-hd-disassembly/
├── firmware/
│   ├── PavoBaseline.Cab
│   ├── EXT.bin
│   └── NK.bin
├── assets/
│   ├── fonts/
│   │   ├── ZegoeUI.ttf, ZegoeUI_B.ttf, ZegoeUI_Blk.ttf...
│   │   └── MeiryoForZune.ttf, MalgunForZune.ttf...
│   ├── DefaultAd.png, Dismiss.png, Zune.png...
│   └── runtimeZune.v3.1.zcp
├── modules/
│   ├── gemstone.exe, xuidll.dll, zhud_serv.dll...
│   └── (109 reconstructed ARM32 PE binaries)
├── ghidra/
│   ├── project/
│   │   └── ZuneHD_Project.gpr / ZuneHD_Project.rep
│   └── decompiled/
└── README.md
```
