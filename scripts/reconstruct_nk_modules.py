#!/usr/bin/env python3
"""Reconstruct kernel-only PE modules from the Zune HD NK.bin image.

The main ``disassemble_zune_hd.py`` pipeline rebuilds modules from the
``EXT.bin`` ROM assets. Some high-value modules live only in the kernel image
(``NK.bin``) and are not in the ROMHDR module table of ``EXT.bin``:
``zcstfs.dll`` (the ZCSTFS volume driver), ``keyvault.dll`` (the key/keypack
driver), ``zcblock.dll``, ``zpartstream.dll`` and ``DwXfer.dll``.

This script flattens ``NK.bin`` (B000FF), locates the named module entries in
the kernel module table, reconstructs an importable ARM32 PE for each, and
writes it to the external corpus ``modules/`` directory. Output stays in the
git-ignored corpus; never commit it.

Usage:
    python3 scripts/reconstruct_nk_modules.py
    python3 scripts/reconstruct_nk_modules.py --modules zcstfs.dll,keyvault.dll
    python3 scripts/reconstruct_nk_modules.py --list
"""

import argparse
import os
import struct
import sys

BASE_DIR = os.environ.get(
    "ZUNE_HD_DISASSEMBLY_DIR",
    os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "zune-hd-disassembly")),
)
NK_PATH = os.path.join(BASE_DIR, "firmware", "NK.bin")
MODULES_DIR = os.path.join(BASE_DIR, "modules")

DEFAULT_MODULES = [
    "zcstfs.dll",
    "keyvault.dll",
    "zcblock.dll",
    "zpartstream.dll",
    "DwXfer.dll",
]

HASH = 0x7FFFFFFF


def flatten_nk(path):
    """Return (image_start, raw) for a B000FF NK image."""
    with open(path, "rb") as f:
        if f.read(7) != b"B000FF\n":
            raise ValueError("NK.bin is not a B000FF image")
        image_start, image_length = struct.unpack("<II", f.read(8))
        raw = bytearray(image_length)
        while True:
            header = f.read(12)
            if len(header) < 12:
                break
            address, length, checksum = struct.unpack("<III", header)
            if address == 0 and checksum == 0:
                break
            offset = address - image_start
            raw[offset : offset + length] = f.read(length)
    return image_start, raw


def find_module_entries(raw, image_start):
    """Index the kernel module table by module name."""
    entries = {}
    for offset in range(len(raw) - 32):
        attrs, ft_low, ft_high, size, name_ptr, e32_off, o32_off, load_off = struct.unpack_from(
            "<IIIIIIII", raw, offset
        )
        name_offset = (name_ptr & HASH) - image_start
        if not (0 <= name_offset < len(raw)):
            continue
        end = raw.find(b"\0", name_offset)
        name = raw[name_offset:end]
        if not (1 <= len(name) < 64) or not all(32 <= c < 127 for c in name):
            continue
        decoded = name.decode("ascii")
        if not (decoded.endswith(".dll") or decoded.endswith(".exe")):
            continue
        e32_rva = (e32_off & HASH) - image_start
        o32_rva = (o32_off & HASH) - image_start
        if not (0 <= e32_rva < len(raw) - 28 and 0 <= o32_rva < len(raw) - 24 and size < 0x800000):
            continue
        entries.setdefault(decoded, (offset, e32_off, o32_off, ft_low))
    return entries


def reconstruct_pe(raw, image_start, e32_off, o32_off, ft_low):
    """Rebuild a standard PE from a Windows CE e32_rom/o32_rom module pair."""
    e32_rva = (e32_off & HASH) - image_start
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
    ) = struct.unpack_from("<HHIIHHIIHH", raw, e32_rva)

    o32_rva = (o32_off & HASH) - image_start
    sections = []
    for index in range(objcnt):
        vsz, rva, psz, dataptr, _, flags = struct.unpack_from(
            "<IIIIII", raw, o32_rva + index * 24
        )
        data_offset = (dataptr & HASH) - image_start
        sections.append((vsz, rva, psz, flags, bytes(raw[data_offset : data_offset + psz])))

    raw_dirs = []
    for index in range(1, 9):
        rva, size = struct.unpack_from("<II", raw, e32_rva + 28 + index * 8)
        raw_dirs.append((rva, size))

    data_dirs = [
        raw_dirs[0],
        raw_dirs[1],
        raw_dirs[2],
        raw_dirs[3],
        (0, 0),
        raw_dirs[5],
        raw_dirs[6],
    ]
    while len(data_dirs) < 16:
        data_dirs.append((0, 0))

    valid_dirs = []
    for rva, size in data_dirs:
        if rva == 0 or size == 0:
            valid_dirs.append((0, 0))
            continue
        in_section = any(s[1] <= rva < s[1] + s[0] for s in sections)
        valid_dirs.append((rva, size) if in_section else (0, 0))
    data_dirs = valid_dirs

    dos_header = bytearray(0x80)
    dos_header[0:2] = b"MZ"
    struct.pack_into("<I", dos_header, 0x3C, 0x80)

    file_header = struct.pack(
        "<4sHHIIIHH", b"PE\0\0", 0x01C2, objcnt, ft_low, 0, 0, 224, imgflags
    )

    code_size = sections[0][2] if sections else 0
    init_data_size = sum(s[2] for s in sections[1:]) if len(sections) > 1 else 0
    optional_fixed = struct.pack(
        "<HBBIIIIIIIIIHHHHHHIIIIHHIIIIII",
        0x010B,
        8,
        0,
        code_size,
        init_data_size,
        0,
        entryrva,
        sections[0][1] if sections else 0,
        sections[1][1] if objcnt > 1 else 0,
        vbase,
        0x1000,
        0x200,
        subsysmaj,
        subsysmin,
        0,
        0,
        subsysmaj,
        subsysmin,
        0,
        vsize,
        0x400,
        0,
        subsys,
        0,
        stackmax,
        0x1000,
        0x100000,
        0x1000,
        0,
        16,
    )
    optional = optional_fixed + b"".join(struct.pack("<II", r, s) for r, s in data_dirs)

    section_names = [b".text\0\0\0", b".data\0\0\0", b".rdata\0\0", b".reloc\0\0", b".rsrc\0\0\0"]
    section_headers = bytearray()
    file_offset = 0x400
    placed = []
    for index, (vsz, rva, psz, flags, data) in enumerate(sections):
        name = section_names[index] if index < len(section_names) else f".sec{index}\0\0".encode()
        aligned = (psz + 0x1FF) & ~0x1FF
        section_headers += struct.pack(
            "<8sIIIIIIHHI", name, vsz, rva, aligned, file_offset, 0, 0, 0, 0, flags
        )
        placed.append((file_offset, data))
        file_offset += aligned

    buffer = bytearray(file_offset)
    headers = dos_header + file_header + optional + section_headers
    buffer[0 : len(headers)] = headers
    for offset, data in placed:
        buffer[offset : offset + len(data)] = data
    return bytes(buffer)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--modules", help="Comma-separated module names (default: the built-in list).")
    parser.add_argument("--list", action="store_true", help="List the requested modules without writing files.")
    args = parser.parse_args()

    if not os.path.isfile(NK_PATH):
        print(f"[!] NK image not found: {NK_PATH}", file=sys.stderr)
        return 1

    image_start, raw = flatten_nk(NK_PATH)
    entries = find_module_entries(raw, image_start)
    requested = [m.strip() for m in args.modules.split(",")] if args.modules else DEFAULT_MODULES

    os.makedirs(MODULES_DIR, exist_ok=True)
    written = 0
    for module in requested:
        if module not in entries:
            print(f"[!] {module}: not found in the kernel module table")
            continue
        _, e32_off, o32_off, ft_low = entries[module]
        if args.list:
            print(f"[*] {module}: kernel module present")
            continue
        pe = reconstruct_pe(raw, image_start, e32_off, o32_off, ft_low)
        destination = os.path.join(MODULES_DIR, module)
        with open(destination, "wb") as handle:
            handle.write(pe)
        written += 1
        print(f"[*] {module}: {len(pe):,} bytes -> {destination}")
    if not args.list:
        print(f"[*] Reconstructed {written} kernel module(s) into the external corpus.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
