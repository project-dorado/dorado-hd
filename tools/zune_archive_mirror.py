#!/usr/bin/env python3
"""Mirror the Zune Archive (archive.org / ephemera.zunes.me) to an external dir.

Clean-room posture: this script only *downloads* third-party content into an
external, untracked mirror. Nothing it fetches may be committed to any Dorado
repository. Metadata manifests written alongside the files stay in the mirror.

Usage:
    python3 zune_archive_mirror.py --dry-run            # size pre-flight only
    python3 zune_archive_mirror.py --stage apps         # app-relevant items
    python3 zune_archive_mirror.py                      # everything (staged)
    python3 zune_archive_mirror.py --root /mnt/zune     # external drive

Resume and integrity:
    Partial files live as ``<name>.part`` and resume with HTTP Range. Completed
    files are checked against the archive.org MD5 when one is published; a bad
    file is re-fetched. ``_manifest.json`` records per-file status.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

METADATA_URL = "https://archive.org/metadata/{identifier}"
DOWNLOAD_URL = "https://archive.org/download/{identifier}/{name}"
USER_AGENT = (
    "dorado-hd-zune-mirror/0.1 "
    "(+https://github.com/project-dorado/dorado-hd; corpus mirror)"
)

# Curated from https://ephemera.zunes.me/archive (the Zune Archive lists on
# archive.org). Stage order is priority order; `all` mirrors the whole archive.
CATEGORIES: dict[str, list[str]] = {
    "Documentation": [
        "zune-documents",
        "zune-datamining",
        "zune-manuals",
        "other-manuals",
        "zune-naming-convention",
        "zune-patents",
        "zune-training-drive",
        "zunepedia",
        "zune-breakout",
        "babyface-ne-yo-zune-performance",
        "visiting-zune-hq-for-the-zune-3.0-briefing",
        "zune-8-80-goods-limited-edition",
    ],
    "Games & Applications": [
        "zune-hd-official-apps-decompiled",
        "zune-hd-official-apps",
        "zune-hd-homebrew-gen-4",
        "open-zdk",
        "xna-game-studio",
        "visual-studio-zune-suite",
        "zune-sd-homebrew-gen-1-3",
        "tutorials-and-troubleshooting",
    ],
    "Helpful Resources": [
        "helpful-resources",
        "zune-ifixit-guides",
    ],
    "Media": [
        "default-zune-backgrounds",
        "other-zune-media",
        "welcome-to-the-social-lp-test",
        "zune-arts",
        "zune-characters",
        "zune-elements",
        "zune-stock-images",
        "zune-themepacks",
        "zune-zodiac",
    ],
    "Miscellaneous": [
        "audiobook-software",
        "gigabeat",
        "windows-embedded-ce",
        "windows-phone",
        "zegoe-ui-fonts",
        "zune-3d-models",
        "abyss-web-server",
        "any-video-converter-free-1",
        "media-collection-reset-tool",
        "microsoft-.net-v3.5-setup",
        "microsoft-fix-it-tool",
        "resource-editor",
        "vnc-viewer-v6.21.406",
    ],
    "Tools": [
        "zune-explorer",
        "zune-explorer-enabler",
        "zune-modding-helper",
        "zune-slayer",
        "zune-tag",
        "zunify",
        "zuse-me",
        "zense-me",
        "zune-discord-rpc",
        "zpl-2-m3u",
        "patreon-to-zune",
        "kritzu-playlist-generator",
        "libmtp-zune-support",
        "android-file-transfer-linux",
        "nezu-zune-30-emulator",
        "JpegBook",
    ],
    "Zune Firmware": [
        "updating-the-zune-firmware",
        "zune-30-keel-firmware",
        "zune-30-keel-v2.14test-firmware",
        "zune-80-120-draco-firmware",
        "zune-flash-scorpius-firmware",
        "zune-hd-pavo-firmware",
    ],
    "Zune Software": [
        "microsoft-zune-dorado",
        "zune-software",
        "xune-software",
        "restore-zune-software-functionality",
    ],
}

STAGES: dict[str, list[str]] = {
    "apps": [
        "zune-hd-official-apps-decompiled",
        "zune-hd-official-apps",
        "zune-datamining",
        "zune-documents",
        "zune-hd-homebrew-gen-4",
        "open-zdk",
        "xna-game-studio",
        "visual-studio-zune-suite",
    ],
    "docs": [],  # filled below from CATEGORIES
    "tools": [],
    "media": [],
    "firmware": [],
}
for _cat, _ids in CATEGORIES.items():
    if _cat == "Documentation":
        STAGES["docs"].extend(_ids)
    elif _cat == "Helpful Resources":
        STAGES["docs"].extend(_ids)
    elif _cat == "Tools":
        STAGES["tools"].extend(_ids)
    elif _cat == "Miscellaneous":
        STAGES["tools"].extend(_ids)
    elif _cat == "Media":
        STAGES["media"].extend(_ids)
    elif _cat in ("Zune Firmware", "Zune Software"):
        STAGES["firmware"].extend(_ids)

STAGE_ORDER = ["apps", "docs", "tools", "media", "firmware"]
DEFAULT_ROOT = Path(__file__).resolve().parents[2] / "Zune Archive"
RESERVE_BYTES = 10 * 1024**3  # keep 10 GiB headroom on the target volume


def log(msg: str) -> None:
    print(msg, flush=True)


def http_json(url: str, retries: int = 3) -> dict:
    last: Exception | None = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            last = exc
            time.sleep(2**attempt)
    raise RuntimeError(f"metadata fetch failed: {url}: {last}")


def item_files(identifier: str) -> tuple[dict, list[dict]]:
    meta = http_json(METADATA_URL.format(identifier=identifier))
    files = meta.get("files") or []
    return meta.get("metadata") or {}, files


def category_for(identifier: str) -> str:
    for cat, ids in CATEGORIES.items():
        if identifier in ids:
            return cat
    return "Unsorted"


def selected_items(args: argparse.Namespace) -> list[str]:
    if args.item:
        return list(dict.fromkeys(args.item))
    stages = args.stage or ["all"]
    if "all" in stages:
        ids: list[str] = []
        for cat in CATEGORIES.values():
            ids.extend(cat)
        return list(dict.fromkeys(ids))
    ids = []
    for stage in stages:
        if stage not in STAGES:
            raise SystemExit(f"unknown stage {stage!r}; choose from {STAGE_ORDER + ['all']}")
        ids.extend(STAGES[stage])
    return list(dict.fromkeys(ids))


def originals_only(files: list[dict], include_derivatives: bool) -> list[dict]:
    out = []
    for f in files:
        name = f.get("name") or ""
        if not name or name.startswith("."):
            continue
        if name.endswith("/"):
            continue
        if not include_derivatives and f.get("source") == "derivative":
            continue
        out.append(f)
    return out


def file_size(f: dict) -> int:
    try:
        return int(f.get("size") or 0)
    except (TypeError, ValueError):
        return 0


def md5_file(path: Path, chunk: int = 1024 * 1024) -> str:
    h = hashlib.md5()
    with path.open("rb") as fh:
        while True:
            block = fh.read(chunk)
            if not block:
                break
            h.update(block)
    return h.hexdigest()


def download_file(identifier: str, name: str, dest: Path, expected_md5: str | None) -> str:
    """Download with resume; returns 'ok' | 'downloaded' | 'unverified' | 'failed'."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")
    url = DOWNLOAD_URL.format(
        identifier=identifier, name=urllib.parse.quote(name)
    )

    # archive.org regenerates _meta.xml / _files.xml per request, so their
    # published md5s are not stable; verify presence only.
    dynamic_metadata = name.startswith(".") or name.endswith(
        ("_meta.xml", "_files.xml", "_reviews.xml")
    )

    if dest.exists() and expected_md5 and not dynamic_metadata:
        if md5_file(dest) == expected_md5:
            return "ok"
        dest.unlink()
    elif dest.exists() and (not expected_md5 or dynamic_metadata):
        return "unverified"

    pos = part.stat().st_size if part.exists() else 0
    headers = {"User-Agent": USER_AGENT}
    if pos:
        headers["Range"] = f"bytes={pos}-"
    req = urllib.request.Request(url, headers=headers)
    mode = "ab" if pos else "wb"
    try:
        with urllib.request.urlopen(req, timeout=120) as resp, part.open(mode) as out:
            if pos and resp.status != 206:
                # Server refused the range; restart cleanly.
                out.close()
                part.unlink(missing_ok=True)
                return download_file(identifier, name, dest, expected_md5)
            shutil.copyfileobj(resp, out, length=1024 * 1024)
    except (urllib.error.URLError, TimeoutError) as exc:
        log(f"    ! {name}: {exc}")
        return "failed"

    if expected_md5 and not dynamic_metadata:
        got = md5_file(part)
        if got != expected_md5:
            log(f"    ! md5 mismatch for {name}: {got} != {expected_md5}")
            part.unlink(missing_ok=True)
            return "failed"
    dest.unlink(missing_ok=True)
    part.rename(dest)
    if dynamic_metadata:
        return "unverified"
    return "redownloaded" if pos else "downloaded"


def mirror_item(
    identifier: str,
    root: Path,
    *,
    include_derivatives: bool,
    dry_run: bool,
) -> dict:
    meta, files = item_files(identifier)
    cat = category_for(identifier)
    item_dir = root / cat / identifier
    picked = originals_only(files, include_derivatives)
    total = sum(file_size(f) for f in picked)
    title = (meta.get("title") or "").replace("\n", " ")[:70]
    result = {
        "identifier": identifier,
        "category": cat,
        "title": title,
        "files": len(picked),
        "bytes": total,
        "downloaded": 0,
        "failed": [],
        "skipped": 0,
        "unverified": 0,
    }
    if dry_run:
        log(f"  {identifier:<38} {total / 1e9:8.2f} GB  {len(picked):>4} files  {title}")
        return result

    log(f"\n== {identifier} ({cat}) — {total / 1e9:.2f} GB, {len(picked)} files")
    manifest_path = item_dir / "_manifest.json"
    manifest: dict = {"identifier": identifier, "files": {}}
    if manifest_path.exists():
        try:
            manifest = json.loads(manifest_path.read_text())
        except json.JSONDecodeError:
            pass

    for f in picked:
        name = f["name"]
        dest = item_dir / name
        expected = (f.get("md5") or "").lower() or None
        status = download_file(identifier, name, dest, expected)
        if status == "ok":
            result["skipped"] += 1
        elif status in ("downloaded", "redownloaded"):
            result["downloaded"] += 1
        elif status == "unverified":
            result["unverified"] += 1
        else:
            result["failed"].append(name)
        manifest["files"][name] = {
            "size": file_size(f),
            "md5": expected,
            "status": status,
        }
    manifest["updated"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    item_dir.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    return result


def check_capacity(root: Path, need_bytes: int, force: bool) -> None:
    root.mkdir(parents=True, exist_ok=True)
    free = shutil.disk_usage(root).free
    log(
        f"capacity: need {need_bytes / 1e9:.2f} GB, free {free / 1e9:.2f} GB, "
        f"reserve {RESERVE_BYTES / 1e9:.0f} GB"
    )
    if need_bytes + RESERVE_BYTES > free and not force:
        raise SystemExit(
            "insufficient free space for a full mirror; use --stage to fetch a "
            "subset, --root to target another volume, or --force to override."
        )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT,
                        help=f"mirror root (default: {DEFAULT_ROOT})")
    parser.add_argument("--item", action="append", default=[],
                        help="specific archive.org identifier (repeatable)")
    parser.add_argument("--stage", action="append", choices=STAGE_ORDER + ["all"],
                        help="stage(s) to mirror (default: all)")
    parser.add_argument("--dry-run", action="store_true",
                        help="size pre-flight only; download nothing")
    parser.add_argument("--include-derivatives", action="store_true",
                        help="also fetch archive.org derivative files")
    parser.add_argument("--force", action="store_true",
                        help="ignore the capacity gate")
    parser.add_argument("--list", action="store_true",
                        help="list categories/items and exit")
    args = parser.parse_args(argv)

    if args.list:
        for stage in STAGE_ORDER:
            log(f"[{stage}]")
            for identifier in STAGES[stage]:
                log(f"  {identifier}  ({category_for(identifier)})")
        return 0

    items = selected_items(args)
    log(f"mirror root: {args.root}")
    log(f"items: {len(items)}")

    if args.dry_run:
        total = 0
        failures = []
        for identifier in items:
            try:
                res = mirror_item(identifier, args.root, include_derivatives=args.include_derivatives, dry_run=True)
                total += res["bytes"]
            except RuntimeError as exc:
                failures.append(identifier)
                log(f"  {identifier:<38} METADATA FAILED: {exc}")
        log(f"\nTOTAL: {total / 1e9:.2f} GB across {len(items) - len(failures)} items")
        for identifier in failures:
            log(f"  missing metadata: {identifier}")
        check_capacity(args.root, total, args.force)
        return 0

    total = sum(
        mirror_item(i, args.root, include_derivatives=args.include_derivatives, dry_run=True)["bytes"]
        for i in items
    )
    check_capacity(args.root, total, args.force)

    results = []
    for identifier in items:
        results.append(
            mirror_item(
                identifier,
                args.root,
                include_derivatives=args.include_derivatives,
                dry_run=False,
            )
        )

    fetched = sum(r["downloaded"] for r in results)
    skipped = sum(r["skipped"] for r in results)
    failed = [(r["identifier"], n) for r in results for n in r["failed"]]
    log(f"\nDONE: {fetched} downloaded, {skipped} already present, {len(failed)} failed")
    for identifier, name in failed:
        log(f"  FAILED {identifier}: {name}")
    report = args.root / "_mirror-report.json"
    report.write_text(json.dumps({"results": results, "failed": failed}, indent=2) + "\n")
    log(f"report: {report}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
