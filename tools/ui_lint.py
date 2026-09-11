#!/usr/bin/env python3
"""Standalone UI parity source lint (mirrors the DesignInvariantTest guards).

Checks, all over `app/src/main/java/com/heretek/dorado_hd/ui`:
  1. A file constructing MiniSynth must call `.start()` (silent SFX guard).
  2. `pointerInput(Unit)` without `rememberUpdatedState` must be allowlisted
     with a reason (stale-capture guard).
  3. A function taking `onBack: () -> Unit` must pass it to DetailScaffold.
  4. The manifest uses adjustResize and DoradoRoot applies imePadding().

Exit code 1 on any finding. Run from the repository root:
    python3 tools/ui_lint.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
UI = REPO / "app/src/main/java/com/heretek/dorado_hd/ui"

# pointerInput(Unit) files whose lambdas were reviewed and read live state.
REVIEWED_POINTER_INPUT = {
    "CardViews.kt", "LabyrinthApp.kt", "MusicQuizApp.kt", "ShellGameApp.kt",
    "ShuffleByAlbumApp.kt", "SliderPuzzleApp.kt", "SnowballApp.kt",
}


def kt_files(root: Path) -> list[Path]:
    return sorted(root.rglob("*.kt"))


def check_synth(files: list[Path]) -> list[str]:
    out = []
    for f in files:
        text = f.read_text()
        if f.name != "Synth.kt" and "MiniSynth(" in text and ".start()" not in text:
            out.append(f"{f.relative_to(REPO)}: MiniSynth constructed but never started")
    return out


def check_pointer_input(files: list[Path]) -> list[str]:
    out = []
    for f in files:
        text = f.read_text()
        if "pointerInput(Unit)" in text and "rememberUpdatedState" not in text:
            if f.name not in REVIEWED_POINTER_INPUT:
                out.append(f"{f.relative_to(REPO)}: pointerInput(Unit) without rememberUpdatedState (allowlist with reason)")
    return out


def check_onback(files: list[Path]) -> list[str]:
    out = []
    signature = re.compile(r"fun \w+\(([^)]*onBack\s*:\s*\(\)\s*->\s*Unit[^)]*)\)", re.S)
    scaffold = re.compile(r"DetailScaffold\((.*?)\)\s*\{", re.S)
    for f in files:
        text = f.read_text()
        for m in signature.finditer(text):
            body = text[m.end():]
            nxt = re.search(r"\n(?:@Composable\s+)?(?:private |internal )?fun ", body)
            scoped = body[: nxt.start()] if nxt else body
            for call in scaffold.finditer(scoped):
                if "onBack" not in call.group(1):
                    out.append(f"{f.relative_to(REPO)}: DetailScaffold without onBack")
    return out


def check_ime() -> list[str]:
    out = []
    manifest = (REPO / "app/src/main/AndroidManifest.xml").read_text()
    if 'windowSoftInputMode="adjustResize"' not in manifest:
        out.append("AndroidManifest.xml: windowSoftInputMode=adjustResize missing")
    root = (UI / "DoradoRoot.kt").read_text()
    if "imePadding()" not in root:
        out.append("ui/DoradoRoot.kt: imePadding() missing")
    return out


def main() -> int:
    files = kt_files(UI / "apps")
    findings = (
        check_synth(files)
        + check_pointer_input(files)
        + check_onback(files)
        + check_ime()
    )
    if findings:
        print("ui_lint findings:")
        for f in findings:
            print(" -", f)
        return 1
    print(f"ui_lint clean ({len(files)} app files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
