# Zune HD module inventory (disassembly corpus)

Generated from the out-of-tree `zune-hd-disassembly/` corpus by
`scripts/ghidra_corpus.py` (rizin symbol recovery + full Ghidra decompilation).
Raw decompilation/symbols stay external; **this table is synthesized metadata only.**

**Coverage:** 109/109 reconstructed ARM32 PE modules analysed; 66,598 functions
decompiled; 4,169 named exports and 10,525 imports recovered.

| Module | Bytes | Exports | Functions | Role |
|---|---:|---:|---:|---|
| `AAXSDKWin.dll` | 113,152 | 74 | 360 | Audible/AAX audio SDK (spoken-word playback) |
| `ASAFilter.dll` | 39,936 | 5 | 193 | System DLL |
| `Dw.exe` | 60,928 | 0 | 260 | Native executable |
| `IECEExt.dll` | 11,776 | 43 | 117 | System DLL |
| `MtpSvc.dll` | 375,296 | 5 | 1,585 | MTP device service (USB media transfer) |
| `ZWmtStreamer.dll` | 390,144 | 5 | 1,485 | WMA/WMV streaming (PlayReady/WMDRM) |
| `abufilter.dll` | 43,520 | 5 | 241 | System DLL |
| `acsserv.dll` | 74,240 | 5 | 296 | System DLL |
| `athsrvc.dll` | 5,120 | 8 | 36 | System DLL |
| `audible.dll` | 48,128 | 5 | 267 | System DLL |
| `battdrvr.dll` | 10,240 | 10 | 71 | System DLL |
| `ceshell.dll` | 278,528 | 81 | 883 | Windows CE shell |
| `commctrl.dll` | 375,808 | 77 | 1,456 | Common controls |
| `commdlg.dll` | 87,040 | 5 | 245 | System DLL |
| `compclient.dll` | 54,784 | 190 | 277 | Composition client library |
| `compositor.exe` | 68,096 | 0 | 331 | Hardware window compositor |
| `connectionui.dll` | 38,400 | 1 | 278 | System DLL |
| `credprov.dll` | 4,608 | 6 | 25 | System DLL |
| `crypt32.dll` | 388,096 | 112 | 1,502 | CryptoAPI (TLS/certs) |
| `ddraw.dll` | 25,600 | 3 | 180 | System DLL |
| `dhcpsrv.dll` | 5,632 | 5 | 32 | System DLL |
| `gemstone.exe` | 558,080 | 0 | 2,737 | Primary Zune HD shell — Metro scene graph, gesture routing, view lifecycle |
| `hostloader.dll` | 33,792 | 1 | 193 | System DLL |
| `ietheme.dll` | 46,592 | 9 | 57 | System DLL |
| `imaging.dll` | 306,688 | 2 | 916 | System DLL |
| `imgutil.dll` | 52,224 | 12 | 281 | System DLL |
| `iphlpapi.dll` | 51,200 | 62 | 270 | System DLL |
| `jscript.dll` | 709,120 | 4 | 2,010 | JScript engine (zie.exe) |
| `jsproxy.dll` | 18,432 | 92 | 85 | System DLL |
| `libEGL.dll` | 74,240 | 36 | 320 | OpenGL ES / EGL stack |
| `libGLESv2.dll` | 263,680 | 143 | 931 | OpenGL ES / EGL stack |
| `libKD.dll` | 84,992 | 173 | 544 | Kernel/user debug + crypto helpers |
| `libnvaudio_capture.dll` | 51,200 | 5 | 288 | NVIDIA Tegra multimedia driver/library |
| `libnvaudio_encoder.dll` | 50,176 | 5 | 251 | NVIDIA Tegra multimedia driver/library |
| `libnvcwm.dll` | 11,776 | 32 | 99 | NVIDIA Tegra multimedia driver/library |
| `libnvddk_audiomixer.dll` | 5,120 | 11 | 30 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_audio.dll` | 1,358,848 | 24 | 2,082 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_image.dll` | 176,128 | 4 | 504 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_manager.dll` | 5,632 | 9 | 27 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_misc.dll` | 29,696 | 4 | 150 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_service.dll` | 6,144 | 5 | 47 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_tracklist.dll` | 7,168 | 15 | 38 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_video.dll` | 107,520 | 8 | 330 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_videorenderer.dll` | 17,920 | 1 | 107 | NVIDIA Tegra multimedia driver/library |
| `libnvmm_writer.dll` | 56,320 | 2 | 101 | NVIDIA Tegra multimedia driver/library |
| `libnvmux_file_writer.dll` | 44,544 | 5 | 242 | NVIDIA Tegra multimedia driver/library |
| `libnvodm_audiocodec.dll` | 17,408 | 12 | 86 | NVIDIA Tegra multimedia driver/library |
| `libnvodm_dtvtuner.dll` | 9,728 | 5 | 65 | NVIDIA Tegra multimedia driver/library |
| `libnvodm_imager.dll` | 160,768 | 10 | 184 | NVIDIA Tegra multimedia driver/library |
| `libnvomx.dll` | 169,472 | 22 | 561 | NVIDIA Tegra multimedia driver/library |
| `libnvsm.dll` | 49,664 | 47 | 176 | NVIDIA Tegra multimedia driver/library |
| `libnvvideo_capture.dll` | 83,968 | 5 | 427 | NVIDIA Tegra multimedia driver/library |
| `libnvvideo_encoder.dll` | 57,856 | 5 | 297 | NVIDIA Tegra multimedia driver/library |
| `libnvvideo_transform_filter.dll` | 58,368 | 3 | 342 | NVIDIA Tegra multimedia driver/library |
| `libnvxdrm.dll` | 6,144 | 7 | 39 | NVIDIA Tegra multimedia driver/library |
| `mlang.dll` | 133,632 | 14 | 392 | System DLL |
| `msacmce.dll` | 18,944 | 13 | 83 | System DLL |
| `msasn1.dll` | 42,496 | 139 | 213 | System DLL |
| `msdmo.dll` | 26,112 | 19 | 91 | System DLL |
| `mshtml.dll` | 4,115,456 | 6 | 13,036 | Trident HTML engine (zie.exe) |
| `msls31.dll` | 199,168 | 80 | 558 | System DLL |
| `msxml3.dll` | 547,328 | 10 | 2,039 | MSXML3 (feed/web parsing) |
| `netmui.dll` | 4,096 | 0 | 10 | System DLL |
| `nspm.dll` | 10,240 | 1 | 51 | System DLL |
| `ole32.dll` | 166,912 | 40 | 915 | COM runtime |
| `oleaut32.dll` | 176,640 | 231 | 654 | OLE Automation |
| `pngfilt.dll` | 45,056 | 4 | 226 | System DLL |
| `schannel.dll` | 92,160 | 37 | 319 | TLS/SSL provider |
| `secur32.dll` | 12,288 | 25 | 74 | SSPI security provider |
| `services.exe` | 11,264 | 0 | 66 | Native executable |
| `shcore.dll` | 10,752 | 31 | 76 | System DLL |
| `shdoclc.dll` | 127,488 | 0 | 0 | IE shell docs (resource-only; 0 code functions) |
| `shdocvw.dll` | 420,352 | 123 | 1,712 | Shell doc/view (IE browser control) |
| `shellcelog.dll` | 9,728 | 5 | 65 | System DLL |
| `shlwapi.dll` | 130,048 | 480 | 460 | System DLL |
| `ssllsp.dll` | 30,208 | 3 | 186 | System DLL |
| `udp2tcp.exe` | 8,704 | 0 | 66 | Native executable |
| `urlmon.dll` | 301,056 | 53 | 1,161 | URL moniker / download |
| `wininet.dll` | 453,632 | 166 | 1,723 | WinINet (HTTP/FTP) |
| `winsock.dll` | 6,144 | 33 | 75 | Winsock 1.1 |
| `wmadmod.dll` | 531,968 | 5 | 952 | System DLL |
| `wmdrm.dll` | 419,840 | 63 | 1,068 | System DLL |
| `wmvdmod.dll` | 881,152 | 3 | 1,063 | System DLL |
| `ws2serv.dll` | 30,720 | 9 | 294 | Winsock 2 service |
| `wspm.dll` | 8,704 | 1 | 53 | System DLL |
| `wzcsapi.dll` | 8,192 | 14 | 61 | Wireless Zero Config (Wi-Fi) API |
| `xhttpdll.dll` | 80,896 | 31 | 266 | HTTP client library |
| `xmllite.dll` | 154,112 | 6 | 563 | System DLL |
| `xnalauncher.exe` | 157,696 | 0 | 696 | XNA app launcher/host |
| `xuidll.dll` | 465,408 | 529 | 2,288 | Xbox UI (XUI) runtime ported to WinCE — layout, animation, touch dispatch |
| `zam_serv.dll` | 93,184 | 5 | 532 | Zune Application Manager (XNA mini-app lifecycles) |
| `zcab.dll` | 50,176 | 12 | 252 | CAB decompression helper |
| `zconfig_serv.dll` | 35,328 | 5 | 207 | Device configuration service |
| `zcontent_serv.dll` | 56,832 | 5 | 228 | Media library indexing + metadata/search service |
| `zcredentials_serv.dll` | 15,360 | 5 | 118 | Credential/DRM key service |
| `zd3d.dll` | 185,344 | 62 | 237 | Direct3D-Mobile interface (D3DX helpers) over Tegra APX |
| `zdknet.dll` | 68,096 | 38 | 382 | ZDK networking API |
| `zdksystem.dll` | 135,168 | 359 | 996 | Zune Development Kit system API (app-facing framework) |
| `zhud_serv.dll` | 189,440 | 5 | 1,167 | Playback/volume Heads-Up-Display and lock-shade service |
| `zie.exe` | 197,632 | 0 | 1,121 | Zune Internet Explorer shell |
| `ziehooks.dll` | 5,632 | 6 | 28 | Internet Explorer shell hooks |
| `zlib.dll` | 37,376 | 18 | 74 | zlib decompression (WinCE port) |
| `zmassive.dll` | 87,552 | 19 | 572 | Massive (audio/marketplace) client library |
| `zmedia_serv.dll` | 273,920 | 5 | 1,221 | Core media playback engine (pipeline, queue, EQ) |
| `znet_serv.dll` | 492,544 | 5 | 2,046 | Networking service (download, marketplace transport) |
| `zrender.dll` | 211,968 | 3 | 614 | High-level scene/texture rendering engine |
| `zserial.dll` | 19,456 | 18 | 114 | Serial/device identity helper |
| `zsplash.exe` | 18,944 | 0 | 107 | Animated startup bootsplash |
| `zuncab.exe` | 145,408 | 0 | 90 | Zune CAB extractor/installer |

## Notable modules

- **gemstone.exe** (2,737 fns) — the shell; scene classes `GemStartScene`, `GemPivotScene`,
  `GemNowPlayingScene`, `GemLibraryAlbumGridContent`, `GemLibraryLetterPickerScene`.
- **xuidll.dll** (2,288 fns, 529 exports) — the XUI engine: `XuiElement*`, `XuiText*`,
  `XuiTouch*`, `XuiScroll*`, `XuiProcessMultiTouchMessage`, `XuiSetTouchSettings`.
- **zdksystem.dll** (996 fns, 359 exports) — the app-facing ZDK framework API.
- **zd3d.dll** (237 fns, 62 exports) — D3DX/`D3DXMatrix*` helpers over Direct3D-Mobile.
- **zmedia_serv.dll** (1,221 fns) / **zhud_serv.dll** (1,167 fns) — playback + HUD.
- **znet_serv.dll** (2,046 fns) — networking/marketplace transport.
- **shdoclc.dll** — resource-only (0 code functions).

> Raw per-function decompiled C and the symbol/string dumps live only in the external
> corpus (`ghidra/decompiled/`, `ghidra/symbols/`) and are never committed.
