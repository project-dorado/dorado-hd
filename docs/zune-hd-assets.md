# Zune HD firmware asset inventory (disassembly corpus)

Non-module files extracted from `PavoBaseline.Cab` (ROM file table) into the
out-of-tree `zune-hd-disassembly/assets/`. Raw assets are provisioned by the
device firmware and are **never committed**; this document is the synthesized
inventory only.

## Fonts (`assets/fonts/`, 28 files)

- **Authentic Zegoe UI** (Microsoft): `ZegoeUI.ttf`, `_B`, `_Blk`, `_L`, `_SB`,
  `_SL` — the device UI typeface. Dorado-HD ships metric-compatible **Selawik**
  (OFL) instead; users may supply Zegoe locally.
- **CJK**: `MalgunForZune.ttf` (Korean), `MeiryoForZune.ttf` (Japanese).
- **Core CE/desktop set**: Arial, Courier New, Georgia, Impact, Times New Roman,
  Trebuchet MS, Verdana, Webdings, Wingdings (Web/CE rendering fallbacks).

## Multimedia / codec firmware

| File | Role |
|---|---|
| `nvmm_h264dec.axf`, `nvmm_mpeg4dec.axf`, `nvmm_adtsdec.axf`, `nvmm_jpegdec.axf`, `nvmm_jpegenc.axf` | Tegra APX 2600 codec firmware images (H.264, MPEG-4, AAC-ADTS, JPEG decode/encode). |
| `nvddk_audiomixer_core.axf`, `nvrm_avp.axf` | Audio-mixer core and the Tegra AVP (audio/video processor) firmware. |
| `msadpcm.dll` | Microsoft ADPCM codec (WinCE). |
| `runtimeZune.v3.1.zcp` | The **XNA Game Studio 3.1 runtime** package (4.3 MB). NX container flagged `encrypted = True` with no readable manifest — i.e. marketplace DRM (AES-ECB); it is the same container family handled by `dorado-emu/Dorado.Containers`. |
| `TVOut.exe` | Small Windows CE ARM PE (TV-out helper). |

## Networking / radio firmware

| File | Role |
|---|---|
| `Acs_045E_081C_3_90_ClutchFW.hex`, `Acs_045E_091A_4_26_ConcertoFW.hex` | Intel-HEX touch-controller firmware (`045E` = Microsoft VID): "Clutch" and "Concerto" controllers. |
| `athwlan2_0.bin.z77`, `data.patch.hw2_0.bin` | Atheros Wi-Fi firmware (LZ77 "z77" compressed) + patch. |
| `calData_sd21-055-d0513.bin` | Wi-Fi calibration data. |

## Identity / provisioning

| File | Role |
|---|---|
| `sysroots.p7b`, `zuneca.p7b` | PKCS#7 certificate bundles (trust roots / CA chain). |
| `zuneroots.p7b` | PGP-encrypted root key material. |
| `zbinfs_EXT.hash` | Filesystem (binfs) hash table for the EXT partition. |
| `hdrfirmware.bin` | Discrete HD-radio (HD-Radio) firmware blob. |

## UI chrome / OS resources

| File | Role |
|---|---|
| `stdsm.bmp`, `viewsm.bmp`, `stdsm.2bp`, `viewsm.2bp`, `close.2bp`, `ok.2bp` | Boot / status bitmaps and `.2bp` mask pairs. |
| `DefaultAd.png`, `Dismiss.png` / `DismissESP.png` / `DismissFRA.png`, `devicon.ico` | Marketplace/notification chrome. |
| `Dictionary.dat` | Custom **`ZDCT`** (Zune Dictionary) data file, 1.28 MB — spelling/dictionary resource. |

## What this pass did not recover

- `runtimeZune.v3.1.zcp` payload contents (marketplace DRM; no public key).
- `zuneroots.p7b` (PGP-encrypted).
- Field semantics of the `XuiTouchSettings` struct (see
  `zune-hd-touch-settings.md` §4 — no PDB/symbol data exists in the corpus).
