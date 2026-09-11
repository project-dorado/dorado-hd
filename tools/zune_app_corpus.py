#!/usr/bin/env python3
"""Extract and verify the decompiled Zune HD official-app corpus (external).

Input:  ``Zune Archive/Games & Applications/zune-hd-official-apps-decompiled``
        (fetched by ``zune_archive_mirror.py``): 61 app ZIPs + ``hashes.txt``.

Output: ``Zune HD Apps (Decompiled)/<app-dir>/gametitle/...`` plus a
        ``_verification.json`` report. The output tree is Microsoft content:
        external, untracked, reference-only. Never commit any of it.

Verification maps every ``hashes.txt`` entry (md5 + relative path) onto the
extracted tree. Upstream has one known quirk: the hashes file says
``slider_puzele`` while the ZIP is ``slider_puzzle.zip``; the extractor
resolves leftovers by elimination when the mapping is unambiguous.

Usage:
    python3 zune_app_corpus.py --extract --verify
    python3 zune_app_corpus.py --verify-only
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEFAULT_MIRROR = (
    Path(__file__).resolve().parents[2]
    / "Zune Archive"
    / "Games & Applications"
    / "zune-hd-official-apps-decompiled"
)
DEFAULT_OUT = Path(__file__).resolve().parents[2] / "Zune HD Apps (Decompiled)"


def log(msg: str) -> None:
    print(msg, flush=True)


def parse_hashes(path: Path) -> list[tuple[str, str]]:
    entries: list[tuple[str, str]] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line:
            continue
        parts = line.split(None, 1)
        if len(parts) != 2:
            continue
        md5, rel = parts[0].lower(), parts[1].lstrip("./")
        entries.append((md5, rel))
    return entries


def map_zips_to_prefixes(zip_stems: list[str], prefixes: set[str]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    remaining_prefixes = set(prefixes)
    for stem in zip_stems:
        if stem in remaining_prefixes:
            mapping[stem] = stem
            remaining_prefixes.discard(stem)
    unresolved = [s for s in zip_stems if s not in mapping]
    if unresolved:
        if len(unresolved) == 1 and len(remaining_prefixes) == 1:
            mapping[unresolved[0]] = next(iter(remaining_prefixes))
            remaining_prefixes.clear()
        else:
            raise SystemExit(
                "ambiguous ZIP→app-dir mapping: "
                f"zips={unresolved} prefixes={sorted(remaining_prefixes)}"
            )
    if remaining_prefixes:
        raise SystemExit(f"app dirs with no ZIP: {sorted(remaining_prefixes)}")
    return mapping


def safe_extract(zf: zipfile.ZipFile, dest: Path) -> int:
    dest.mkdir(parents=True, exist_ok=True)
    count = 0
    for info in zf.infolist():
        name = info.filename
        if name.startswith("/") or ".." in Path(name).parts:
            raise SystemExit(f"unsafe path in archive: {name}")
        zf.extract(info, dest)
        if not info.is_dir():
            count += 1
    return count


def md5_file(path: Path, chunk: int = 1024 * 1024) -> str:
    h = hashlib.md5()
    with path.open("rb") as fh:
        while True:
            block = fh.read(chunk)
            if not block:
                break
            h.update(block)
    return h.hexdigest()


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mirror", type=Path, default=DEFAULT_MIRROR)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--extract", action="store_true",
                        help="extract app ZIPs into the corpus tree")
    parser.add_argument("--verify", action="store_true",
                        help="verify extracted files against hashes.txt")
    parser.add_argument("--verify-only", action="store_true",
                        help="skip extraction; verify only")
    args = parser.parse_args(argv)

    if not (args.extract or args.verify or args.verify_only):
        args.extract = args.verify = True

    hashes_path = args.mirror / "hashes.txt"
    if not hashes_path.exists():
        raise SystemExit(f"hashes.txt not found under {args.mirror}; fetch the item first")
    entries = parse_hashes(hashes_path)
    prefixes = {rel.split("/", 1)[0] for _, rel in entries}
    log(f"hashes.txt: {len(entries)} entries, {len(prefixes)} app dirs")

    zips = sorted(args.mirror.glob("*.zip"))
    if not zips:
        raise SystemExit(f"no ZIPs under {args.mirror}")

    if args.extract:
        mapping = map_zips_to_prefixes([z.stem for z in zips], prefixes)
        for alias, prefix in sorted(mapping.items()):
            if alias != prefix:
                log(f"  alias: {alias}.zip -> {prefix}/")
        total = 0
        for z in zips:
            prefix = mapping[z.stem]
            dest = args.out / prefix
            with zipfile.ZipFile(z) as zf:
                total += safe_extract(zf, dest)
        log(f"extracted {total} files -> {args.out}")

    if args.verify:
        ok = mismatch = missing = 0
        problems: list[dict] = []
        for md5, rel in entries:
            path = args.out / rel
            if not path.exists():
                missing += 1
                problems.append({"path": rel, "status": "missing"})
                continue
            got = md5_file(path)
            if got == md5:
                ok += 1
            else:
                mismatch += 1
                problems.append({"path": rel, "status": "mismatch",
                                 "expected": md5, "got": got})
        report = {
            "entries": len(entries),
            "ok": ok,
            "mismatch": mismatch,
            "missing": missing,
            "problems": problems,
        }
        report_path = args.out / "_verification.json"
        report_path.write_text(json.dumps(report, indent=2) + "\n")
        log(f"verify: {ok}/{len(entries)} ok, {mismatch} mismatch, {missing} missing")
        log(f"report: {report_path}")
        return 1 if problems else 0

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
