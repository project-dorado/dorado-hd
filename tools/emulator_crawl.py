#!/usr/bin/env python3
"""On-device UI crawl for the 63-registry parity sweep (Phase 0).

For every mini-app it launches the debug deep link (`dorado://app/<slug>`),
captures a screenshot, dumps the accessibility tree, and reports:
  - crash: a FATAL EXCEPTION for the app package in logcat
  - blank: near-uniform screenshot (stddev below a threshold)
  - textNodes: visible text nodes in the uiautomator dump

Evidence lands in `reports/emulator/` (shots/<slug>.png + crawl.json +
crawl-summary.json). The directory is git-ignored; summaries are copied into
the audit docs.

Usage:
    python3 tools/emulator_crawl.py                 # all apps
    python3 tools/emulator_crawl.py --apps pgr-ferrari-edition,notes
    python3 tools/emulator_crawl.py --force         # ignore the manifest cache
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

PKG = "com.heretek.dorado_hd.debug"
REPO = Path(__file__).resolve().parents[1]
REGISTER = REPO / "docs" / "official-apps.json"
DEFAULT_OUT = REPO / "reports" / "emulator"
BLANK_STDDEV = 2.0


def adb(*args: str, timeout: int = 60, binary: bool = False):
    result = subprocess.run(
        ["adb", *args],
        capture_output=True,
        timeout=timeout,
        **({} if binary else {"text": True}),
    )
    return result.stdout


def registry_slugs() -> list[str]:
    data = json.loads(REGISTER.read_text())
    slugs = [row["installedId"] for row in data if row.get("installedId")]
    # Social shell lives only in the registry (no official package).
    if "zunesocial" not in slugs:
        slugs.append("zunesocial")
    return slugs


def launch(slug: str) -> None:
    adb(
        "shell", "am", "start",
        "-a", "android.intent.action.VIEW",
        "-d", f"dorado://app/{slug}",
        PKG,
    )


def dismiss_shade(width: int, height: int) -> None:
    """Swipe up to lift the wake shade if a previous session left the app paused."""
    adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.8)),
        str(width // 2), str(int(height * 0.2)), "200")


def crash_lines() -> list[str]:
    """FATAL EXCEPTION blocks that belong to the app package (uiautomator and
    other system processes also crash into this buffer and must not count)."""
    out = adb("logcat", "-d", "-b", "crash", "-t", "600")
    lines = out.splitlines()
    hits: list[str] = []
    for i, line in enumerate(lines):
        if "FATAL EXCEPTION" not in line:
            continue
        block = lines[i : i + 14]
        if any(PKG in entry for entry in block):
            hits.extend(block)
    return hits[:12]


def text_node_count(xml: str) -> int:
    return len(re.findall(r'text="[^"]+"', xml))


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apps", help="comma-separated slugs (default: all)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--delay", type=float, default=2.2)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args(argv)

    slugs = args.apps.split(",") if args.apps else registry_slugs()
    shots = args.out / "shots"
    shots.mkdir(parents=True, exist_ok=True)
    manifest_path = args.out / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}

    size = adb("shell", "wm", "size")
    m = re.search(r"(\d+)x(\d+)", size)
    width, height = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)

    rows = []
    for slug in slugs:
        if not args.force and manifest.get(slug, {}).get("ok"):
            rows.append(manifest[slug])
            continue
        adb("logcat", "-c")
        launch(slug)
        time.sleep(args.delay)
        # Launching a singleTask activity pauses the previous instance, which
        # can raise the wake shade; lift it so the screenshot shows the app.
        dismiss_shade(width, height)
        time.sleep(0.6)
        shot = shots / f"{slug}.png"
        with shot.open("wb") as fh:
            fh.write(adb("exec-out", "screencap", "-p", binary=True, timeout=90))
        try:
            img = Image.open(shot).convert("L")
            pixels = list(img.get_flattened_data())
            stddev = statistics.pstdev(pixels) if pixels else 0.0
        except OSError:
            stddev = 0.0
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/dorado-ui.xml", timeout=30)
            xml = adb("shell", "cat", "/sdcard/dorado-ui.xml", timeout=30)
        except (subprocess.TimeoutExpired, OSError):
            xml = ""
        crash = crash_lines()
        row = {
            "slug": slug,
            "crash": bool(crash),
            "crashExcerpt": crash[:6],
            "blank": stddev < BLANK_STDDEV,
            "stddev": round(stddev, 2),
            "textNodes": text_node_count(xml),
            "shot": str(shot.relative_to(REPO)),
            "at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        }
        rows.append(row)
        # First-run wake shade: if the screen is blank and the app just launched,
        # a stale pause shade may be covering it; dismiss and retry once.
        if row["blank"] and not row["crash"]:
            dismiss_shade(width, height)
            time.sleep(0.8)
            with shot.open("wb") as fh:
                fh.write(adb("exec-out", "screencap", "-p", binary=True, timeout=90))
            img = Image.open(shot).convert("L")
            stddev = statistics.pstdev(list(img.get_flattened_data()))
            row["blank"] = stddev < BLANK_STDDEV
            row["stddev"] = round(stddev, 2)
            row["retriedAfterShade"] = True
        row["ok"] = not row["crash"] and not row["blank"]
        manifest[slug] = row
        flag = "OK " if row["ok"] else ("CRASH" if row["crash"] else "BLANK")
        print(f"{flag:5} {slug}  stddev={row['stddev']} text={row['textNodes']}", flush=True)

    (args.out / "crawl.json").write_text(json.dumps(rows, indent=2) + "\n")
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    bad = [r for r in rows if not r["ok"]]
    summary = {
        "apps": len(rows),
        "ok": len(rows) - len(bad),
        "crashes": [r["slug"] for r in rows if r["crash"]],
        "blanks": [r["slug"] for r in rows if r["blank"] and not r["crash"]],
        "at": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }
    (args.out / "crawl-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
