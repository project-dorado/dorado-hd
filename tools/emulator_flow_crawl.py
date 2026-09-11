#!/usr/bin/env python3
"""Interactive flow crawl (Phase A2): enter each app, drive its primary flow,
then background/foreground and cold-restart it while watching for crashes.

Per app:
  1. launch the deep link, lift the wake shade, capture `entry.png`
  2. element-driven taps: the first matching primary control (play/start/
     continue/...), then pause/back, capturing a screenshot per step
  3. background via HOME and relaunch (resume path), capture `resume.png`
  4. force-stop and relaunch (cold path), capture `cold.png`
  5. scan logcat for the app package's FATAL EXCEPTION after every step

Evidence lands in the git-ignored `reports/emulator/flows/<slug>/` with a
`flow.json` per app plus a `flow-summary.json`. Requires the emulator's
uiautomator service to be healthy; reboot the emulator if dumps start
returning exit 137.

Usage:
    python3 tools/emulator_flow_crawl.py                     # all apps
    python3 tools/emulator_flow_crawl.py --apps notes,pgr-ferrari-edition
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.heretek.dorado_hd.debug"
REPO = Path(__file__).resolve().parents[1]
REGISTER = REPO / "docs" / "official-apps.json"
DEFAULT_OUT = REPO / "reports" / "emulator" / "flows"

PRIMARY_WORDS = [
    "press to start", "play again", "continue", "new game", "start",
    "single player", "classic", "normal", "easy", "play", "solo draw",
    "resume", "quick race", "career", "level", "tutorial", "campaign",
]
PAUSE_WORDS = ["pause", "menu", "back"]


def adb(*args: str, timeout: int = 60, binary: bool = False):
    return subprocess.run(
        ["adb", *args], capture_output=True, timeout=timeout,
        **({} if binary else {"text": True}),
    ).stdout


def slugs_all() -> list[str]:
    data = json.loads(REGISTER.read_text())
    slugs = [r["installedId"] for r in data if r.get("installedId")]
    if "zunesocial" not in slugs:
        slugs.append("zunesocial")
    return slugs


def launch(slug: str) -> None:
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW",
        "-d", f"dorado://app/{slug}", PKG)


def screen_size() -> tuple[int, int]:
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def screenshot(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as fh:
        fh.write(adb("exec-out", "screencap", "-p", binary=True, timeout=90))


def dump_nodes() -> list[dict]:
    out = adb("shell", "uiautomator", "dump", "/sdcard/flow.xml", timeout=40)
    if "dumped" not in (out or ""):
        return []
    xml = adb("shell", "cat", "/sdcard/flow.xml", timeout=40)
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    nodes = []
    for node in root.iter("node"):
        bounds = node.get("bounds") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        label = (node.get("text") or node.get("content-desc") or "").strip()
        clickable = node.get("clickable") == "true"
        if not label and not clickable:
            continue
        nodes.append({
            "label": label,
            "clickable": clickable,
            "cx": (x1 + x2) // 2,
            "cy": (y1 + y2) // 2,
            "area": (x2 - x1) * (y2 - y1),
        })
    # Clickable nodes win; then top-to-bottom, left-to-right.
    return sorted(nodes, key=lambda n: (not n["clickable"], n["cy"], n["cx"]))


def tap(node: dict) -> None:
    adb("shell", "input", "tap", str(node["cx"]), str(node["cy"]))


def press_back() -> None:
    adb("shell", "input", "keyevent", "4")


def go_home() -> None:
    adb("shell", "input", "keyevent", "3")


def force_stop() -> None:
    adb("shell", "am", "force-stop", PKG)


def lift_shade(width: int, height: int) -> None:
    adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.8)),
        str(width // 2), str(int(height * 0.2)), "200")


def crashes() -> list[str]:
    out = adb("logcat", "-d", "-b", "crash", "-t", "600")
    lines = out.splitlines()
    hits = []
    for i, line in enumerate(lines):
        if "FATAL EXCEPTION" in line:
            block = lines[i:i + 12]
            if any(PKG in entry for entry in block):
                hits.extend(block)
    return hits[:12]


def pick(nodes: list[dict], words: list[str]) -> dict | None:
    for word in words:
        for node in nodes:
            if node["label"].lower().startswith(word) or word in node["label"].lower():
                return node
    return None


def crawl(slug: str, out: Path, width: int, height: int) -> dict:
    app_dir = out / slug
    steps: list[dict] = []
    crash_hits: list[str] = []

    def note(action: str, shot: str | None = None) -> None:
        nonlocal crash_hits
        found = crashes()
        if found:
            crash_hits = found
        steps.append({"action": action, "shot": shot, "crash": bool(found)})

    adb("logcat", "-c")
    launch(slug)
    time.sleep(2.2)
    lift_shade(width, height)
    time.sleep(0.5)
    screenshot(app_dir / "entry.png")
    note("entry", "entry.png")
    if crash_hits:
        return {"slug": slug, "steps": steps, "crash": crash_hits, "ok": False}

    # Primary action (up to two: e.g. difficulty -> start).
    for index in range(2):
        nodes = dump_nodes()
        target = pick(nodes, PRIMARY_WORDS)
        if not target:
            break
        tap(target)
        time.sleep(1.4)
        shot = f"step{index + 1}.png"
        screenshot(app_dir / shot)
        note(f"tap:{target['label'][:40] or 'control'}", shot)
        if crash_hits:
            return {"slug": slug, "steps": steps, "crash": crash_hits, "ok": False}

    # Pause/menu via an explicit control, else system back.
    nodes = dump_nodes()
    pause = pick(nodes, PAUSE_WORDS)
    if pause:
        tap(pause)
    else:
        press_back()
    time.sleep(1.0)
    screenshot(app_dir / "pause.png")
    note("pause", "pause.png")
    if crash_hits:
        return {"slug": slug, "steps": steps, "crash": crash_hits, "ok": False}

    # Resume after backgrounding.
    go_home()
    time.sleep(1.5)
    launch(slug)
    time.sleep(1.5)
    screenshot(app_dir / "resume.png")
    note("resume", "resume.png")
    if crash_hits:
        return {"slug": slug, "steps": steps, "crash": crash_hits, "ok": False}

    # Cold start after process death.
    force_stop()
    time.sleep(1.0)
    adb("logcat", "-c")
    launch(slug)
    time.sleep(2.2)
    lift_shade(width, height)
    time.sleep(0.5)
    screenshot(app_dir / "cold.png")
    note("cold", "cold.png")
    return {"slug": slug, "steps": steps, "crash": crash_hits, "ok": not crash_hits}


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apps", help="comma-separated slugs (default: all)")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)

    slugs = args.apps.split(",") if args.apps else slugs_all()
    width, height = screen_size()
    results = []
    for slug in slugs:
        try:
            result = crawl(slug, args.out, width, height)
        except subprocess.TimeoutExpired:
            result = {"slug": slug, "steps": [], "crash": ["timeout"], "ok": False}
        results.append(result)
        (args.out / slug).mkdir(parents=True, exist_ok=True)
        (args.out / slug / "flow.json").write_text(json.dumps(result, indent=2) + "\n")
        flag = "OK   " if result["ok"] else "CRASH"
        print(f"{flag} {slug}  steps={len(result['steps'])}", flush=True)

    summary = {
        "apps": len(results),
        "ok": sum(1 for r in results if r["ok"]),
        "crashes": [r["slug"] for r in results if not r["ok"]],
        "at": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }
    (args.out / "flow-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary))
    return 1 if summary["crashes"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
