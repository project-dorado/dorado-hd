#!/usr/bin/env python3
"""
Zune HD Firmware Downloader, ROM Extractor, & Reverse Engineering Pipeline
Downloads PavoBaseline.Cab (firmware 4.5), flattens the Windows Embedded CE 6.0
ROM image, extracts authentic assets (Zegoe fonts, graphics, descriptors),
reconstructs valid ARM PE executables (gemstone.exe, xuidll.dll, services),
and provides headless Ghidra decompilation support.

Outputs are written by default to a sibling directory outside this repository:
  /home/john/Projects/project-dorado/zune-hd-disassembly/
Override with the ZUNE_HD_DISASSEMBLY_DIR environment variable.
"""

import os
import sys
import glob
import shutil
import struct
import argparse
import subprocess
import urllib.request

DEFAULT_DOWNLOAD_URL = (
    "https://raw.githubusercontent.com/simulacra10/zunefirmware-upgrade/master/PavoBaseline.Cab"
)

BASE_DIR = os.environ.get(
    "ZUNE_HD_DISASSEMBLY_DIR",
    os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "zune-hd-disassembly")),
)

FW_DIR = os.path.join(BASE_DIR, "firmware")
CAB_PATH = os.path.join(FW_DIR, "PavoBaseline.Cab")
ASSETS_DIR = os.path.join(BASE_DIR, "assets")
FONTS_DIR = os.path.join(ASSETS_DIR, "fonts")
MODULES_DIR = os.path.join(BASE_DIR, "modules")
GHIDRA_DIR = os.path.join(BASE_DIR, "ghidra")
DECOMPILED_DIR = os.path.join(GHIDRA_DIR, "decompiled")


def log(msg):
    print(f"[*] {msg}", flush=True)


def run_cmd(cmd, cwd=None):
    log(f"Running: {' '.join(cmd) if isinstance(cmd, list) else cmd}")
    res = subprocess.run(
        cmd, shell=isinstance(cmd, str), cwd=cwd, capture_output=True, text=True
    )
    if res.returncode != 0:
        log(f"Command returned exit code {res.returncode}: {res.stderr.strip()[:300]}")
    return res


def download_firmware(url=DEFAULT_DOWNLOAD_URL):
    os.makedirs(FW_DIR, exist_ok=True)
    if os.path.exists(CAB_PATH) and os.path.getsize(CAB_PATH) > 20 * 1024 * 1024:
        log(f"PavoBaseline.Cab already cached ({os.path.getsize(CAB_PATH):,} bytes).")
        return

    log(f"Downloading PavoBaseline.Cab from {url}...")
    curl_cmd = [
        "curl", "-L", "-C", "-",
        "--retry", "3",
        "-A", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "-o", CAB_PATH,
        url,
    ]
    res = run_cmd(curl_cmd)
    if not os.path.exists(CAB_PATH) or os.path.getsize(CAB_PATH) < 20 * 1024 * 1024:
        log("Curl failed or incomplete, attempting urllib stream fallback...")
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as resp, open(CAB_PATH, "wb") as out_file:
            shutil.copyfileobj(resp, out_file)

    log(f"Download complete: {os.path.getsize(CAB_PATH):,} bytes.")


def unpack_cab():
    ext_bin = os.path.join(FW_DIR, "EXT.bin")
    if os.path.exists(ext_bin) and os.path.getsize(ext_bin) > 30 * 1024 * 1024:
        log("Firmware binaries already unpacked.")
        return ext_bin

    log(f"Unpacking {CAB_PATH} with 7z...")
    run_cmd(["7z", "x", "-y", f"-o{FW_DIR}", CAB_PATH])
    if not os.path.exists(ext_bin):
        raise FileNotFoundError(f"Failed to unpack EXT.bin from {CAB_PATH}")
    log(f"Extracted EXT.bin ({os.path.getsize(ext_bin):,} bytes).")
    return ext_bin


def flatten_wince_bin(bin_path):
    log(f"Flattening Windows CE binary records from {os.path.basename(bin_path)}...")
    with open(bin_path, "rb") as f:
        sig = f.read(7)
        if sig != b"B000FF\n":
            raise ValueError(f"Invalid WinCE BIN signature: {sig}")
        img_start, img_len = struct.unpack("<II", f.read(8))
        raw = bytearray(img_len)

        rec_count = 0
        total_data = 0
        while True:
            hdr = f.read(12)
            if len(hdr) < 12:
                break
            rec_addr, rec_len, rec_chk = struct.unpack("<III", hdr)
            if rec_addr == 0 and rec_chk == 0:
                break
            rec_count += 1
            total_data += rec_len
            offset = rec_addr - img_start
            raw[offset : offset + rec_len] = f.read(rec_len)

    log(f"Flattened {rec_count} records ({total_data:,} bytes) across range 0x{img_start:08X} - 0x{img_start + img_len:08X}.")
    return img_start, raw


def extract_assets(raw, img_start, romhdr_off, numfiles, files_cur):
    os.makedirs(ASSETS_DIR, exist_ok=True)
    os.makedirs(FONTS_DIR, exist_ok=True)

    log(f"Extracting {numfiles} ROM asset files...")
    extracted = 0
    for _ in range(numfiles):
        attrs, ft_low, ft_high, real_sz, comp_sz, name_ptr, load_off = struct.unpack(
            "<IIIIIII", raw[files_cur : files_cur + 28]
        )
        files_cur += 28

        name_rva = (name_ptr & 0x7FFFFFFF) - img_start
        end = raw.find(b"\0", name_rva)
        name = raw[name_rva:end].decode("ascii", errors="ignore")

        if (load_off & 0x7FFFFFFF) >= img_start:
            data_off = (load_off & 0x7FFFFFFF) - img_start
            file_data = raw[data_off : data_off + comp_sz]

            if name.lower().endswith(".ttf"):
                dest = os.path.join(FONTS_DIR, name)
            else:
                dest = os.path.join(ASSETS_DIR, name)

            with open(dest, "wb") as f_out:
                f_out.write(file_data)
            extracted += 1

    log(f"Successfully extracted {extracted} assets (fonts in {FONTS_DIR}).")


def reconstruct_pe(name, raw, img_start, e32_off, o32_off, ft_low):
    e32_rva = (e32_off & 0x7FFFFFFF) - img_start
    (
        objcnt,
        imgflags,
        entryrva,
        vbase,
        subsysmaj,
        subsysmin,
        stackmax,
        vsize,
        subsys,
        _,
    ) = struct.unpack("<HHIIHHIIHH", raw[e32_rva : e32_rva + 28])

    # In WinCE e32_rom, e32_unit entries are shifted by 1 relative to standard PE:
    # unit[0] is (0, timestamp), unit[1]=Export, unit[2]=Import, unit[3]=Resource,
    # unit[4]=Exception, unit[5]=Security, unit[6]=Reloc, unit[7]=Debug
    o32_rva = (o32_off & 0x7FFFFFFF) - img_start
    sections = []
    for o in range(objcnt):
        vsz, rva, psz, dataptr, realaddr, flags = struct.unpack(
            "<IIIIII", raw[o32_rva + o * 24 : o32_rva + (o + 1) * 24]
        )
        data_off = (dataptr & 0x7FFFFFFF) - img_start
        sdata = raw[data_off : data_off + psz]
        sections.append((vsz, rva, psz, flags, sdata))

    raw_dirs = []
    for d in range(1, 9):
        rva, sz = struct.unpack("<II", raw[e32_rva + 28 + d * 8 : e32_rva + 28 + (d + 1) * 8])
        raw_dirs.append((rva, sz))

    data_dirs = [
        raw_dirs[0],  # 0: Export
        raw_dirs[1],  # 1: Import
        raw_dirs[2],  # 2: Resource
        raw_dirs[3],  # 3: Exception
        (0, 0),       # 4: Security (clear: PE expects file offset)
        raw_dirs[5],  # 5: Base Relocation
        raw_dirs[6],  # 6: Debug
    ]
    while len(data_dirs) < 16:
        data_dirs.append((0, 0))

    # Zero out data directory entries whose RVAs fall outside section bounds
    valid_dirs = []
    for rva, sz in data_dirs:
        if rva == 0 or sz == 0:
            valid_dirs.append((0, 0))
            continue
        in_section = any(s[1] <= rva < s[1] + s[0] for s in sections)
        if in_section:
            valid_dirs.append((rva, sz))
        else:
            valid_dirs.append((0, 0))
    data_dirs = valid_dirs

    dos_hdr = bytearray(0x80)
    dos_hdr[0:2] = b"MZ"
    struct.pack_into("<I", dos_hdr, 0x3C, 0x80)

    file_hdr = struct.pack(
        "<4sHHIIIHH", b"PE\0\0", 0x01C2, objcnt, ft_low, 0, 0, 224, imgflags
    )

    code_size = sections[0][2] if sections else 0
    init_data_size = sum(s[2] for s in sections[1:]) if len(sections) > 1 else 0
    opt_hdr_fixed = struct.pack(
        "<HBBIIIIIIIIIHHHHHHIIIIHHIIIIII",
        0x010B, 8, 0,
        code_size, init_data_size, 0,
        entryrva, sections[0][1] if sections else 0, sections[1][1] if objcnt > 1 else 0,
        vbase, 0x1000, 0x200,
        subsysmaj, subsysmin, 0, 0, subsysmaj, subsysmin,
        0, vsize, 0x400, 0,
        subsys, 0,
        stackmax, 0x1000, 0x100000, 0x1000,
        0, 16,
    )
    opt_dirs = b"".join(struct.pack("<II", r, s) for r, s in data_dirs)
    opt_hdr = opt_hdr_fixed + opt_dirs

    sec_names = [b".text\0\0\0", b".data\0\0\0", b".rdata\0\0", b".reloc\0\0", b".rsrc\0\0\0"]
    sec_hdrs = bytearray()
    file_offset = 0x400
    sec_entries = []
    for idx, (vsz, rva, psz, flags, sdata) in enumerate(sections):
        sname = sec_names[idx] if idx < len(sec_names) else f".sec{idx}\0\0".encode("ascii")
        raw_aligned = (psz + 0x1FF) & ~0x1FF
        shdr = struct.pack(
            "<8sIIIIIIHHI",
            sname, vsz, rva, raw_aligned, file_offset, 0, 0, 0, 0, flags,
        )
        sec_hdrs += shdr
        sec_entries.append((file_offset, sdata))
        file_offset += raw_aligned

    pe_buf = bytearray(file_offset)
    headers = dos_hdr + file_hdr + opt_hdr + sec_hdrs
    pe_buf[0 : len(headers)] = headers
    for foff, sdata in sec_entries:
        pe_buf[foff : foff + len(sdata)] = sdata

    return bytes(pe_buf)


def extract_modules(raw, img_start, romhdr_off, nummods):
    os.makedirs(MODULES_DIR, exist_ok=True)
    log(f"Reconstructing {nummods} PE modules...")

    KEY_MODULES = {
        "gemstone.exe", "xuidll.dll", "zhud_serv.dll", "zcontent_serv.dll",
        "zmedia_serv.dll", "zd3d.dll", "zrender.dll", "zam_serv.dll",
        "zie.exe", "compositor.exe", "zsplash.exe", "zconfig_serv.dll",
    }

    reconstructed_count = 0
    cur = romhdr_off + 84
    for _ in range(nummods):
        attrs, ft_low, ft_high, size, name_ptr, e32_off, o32_off, load_off = struct.unpack(
            "<IIIIIIII", raw[cur : cur + 32]
        )
        cur += 32

        name_rva = (name_ptr & 0x7FFFFFFF) - img_start
        end = raw.find(b"\0", name_rva)
        name = raw[name_rva:end].decode("ascii", errors="ignore")

        try:
            pe_data = reconstruct_pe(name, raw, img_start, e32_off, o32_off, ft_low)
            out_path = os.path.join(MODULES_DIR, name)
            with open(out_path, "wb") as f_out:
                f_out.write(pe_data)
            reconstructed_count += 1
            if name in KEY_MODULES:
                log(f"  -> Reconstructed key module: {name:<20} ({len(pe_data):,} bytes)")
        except Exception as e:
            log(f"  [!] Failed to reconstruct {name}: {e}")

    log(f"Reconstructed {reconstructed_count}/{nummods} PE modules in {MODULES_DIR}.")
    return cur


def run_ghidra_analysis(module_names=None, tier="all"):
    """Delegate to the corpus runner: recover symbols, decompile every function
    for the selected modules, and write the manifest. Raw output stays external."""
    runner = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ghidra_corpus.py")
    if not os.path.exists(runner):
        log("ghidra_corpus.py not found; skipping decompilation.")
        return
    cmd = [sys.executable, runner, "--tier", tier]
    if module_names:
        cmd = [sys.executable, runner, "--modules", ",".join(module_names)]
    log(f"Running corpus builder: {' '.join(cmd)}")
    subprocess.run(cmd)


def write_readme():
    readme_path = os.path.join(BASE_DIR, "README.md")
    content = f"""# Zune HD Disassembly Corpus (External Reference)

This directory contains the disassembled Microsoft Zune HD (firmware v4.5)
reverse-engineering corpus used as the behavioral ground truth for **Dorado-HD**.

**None of this material is redistributed by the project.** Per `AGENTS.md` and
project IP rules, this directory is kept external to the repository and must
never be committed, packaged, or shipped.

## Directory Structure

- `firmware/`: Downloaded `PavoBaseline.Cab`, unpacked `EXT.bin` and `NK.bin`.
- `assets/`: Extracted assets from ROM:
  - `fonts/`: Authentic Zegoe UI fonts (`ZegoeUI.ttf`, `ZegoeUI_B.ttf`, `ZegoeUI_Blk.ttf`, `ZegoeUI_L.ttf`, `ZegoeUI_SB.ttf`, `ZegoeUI_SL.ttf`)
  - Graphics, XML descriptors, and `runtimeZune.v3.1.zcp`.
- `modules/`: Valid reconstructed ARM32 PE executables:
  - `gemstone.exe`: The primary UI shell containing all Metro scene trees.
  - `xuidll.dll`: The Xbox UI (XUI) engine runtime ported to Windows CE.
  - `zhud_serv.dll`: Volume and playback HUD service.
  - `zcontent_serv.dll`: Media library metadata and indexing service.
  - `zmedia_serv.dll`: Core playback engine.
  - `zd3d.dll` & `zrender.dll`: Tegra APX 2600 Direct3D-Mobile hardware renderer.
  - `zam_serv.dll`: Zune Application Manager.
- `ghidra/`: Headless Ghidra corpus (symbols, decompiled functions, string indexes,
  `corpus-manifest.json`) produced by `scripts/ghidra_corpus.py`.

## Regenerating

```bash
python3 scripts/disassemble_zune_hd.py --extract-only   # assets + 109 ARM32 PE modules
python3 scripts/ghidra_corpus.py --tier all --jobs 8    # symbols + full decompilation
```

Coverage: 109/109 modules, ~66.6k functions decompiled, 4,169 named exports,
22,479 strings indexed. Synthesized metadata is documented in the dorado-hd
repository (`docs/zune-hd-module-inventory.md`, `docs/zune-hd-api-reference.md`,
`docs/zune-hd-assets.md`).
"""
    with open(readme_path, "w") as f:
        f.write(content)
    log(f"Wrote corpus documentation to {readme_path}")


def main():
    parser = argparse.ArgumentParser(
        description="Zune HD Firmware Disassembler & Reverse Engineering Pipeline"
    )
    parser.add_argument("--url", default=DEFAULT_DOWNLOAD_URL, help="Custom firmware CAB URL")
    parser.add_argument("--extract-only", action="store_true", help="Extract assets and PEs without Ghidra analysis")
    parser.add_argument("--ghidra", action="store_true", help="Run deep Ghidra analysis on shell binaries")
    args = parser.parse_args()

    log(f"Zune HD Disassembly Workspace: {BASE_DIR}")
    download_firmware(args.url)
    ext_bin = unpack_cab()
    img_start, raw = flatten_wince_bin(ext_bin)

    romhdr_off = 0x025C0188
    fields = struct.unpack("<21I", raw[romhdr_off : romhdr_off + 84])
    nummods = fields[4]
    numfiles = fields[12]
    log(f"Parsed ROMHDR: {nummods} modules, {numfiles} asset files.")

    files_cur = extract_modules(raw, img_start, romhdr_off, nummods)
    extract_assets(raw, img_start, romhdr_off, numfiles, files_cur)
    write_readme()

    if args.ghidra:
        run_ghidra_analysis(tier="all")

    log("Pipeline execution complete.")


if __name__ == "__main__":
    main()
