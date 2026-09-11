#!/usr/bin/env python3
"""Mine the external decompiled Zune HD app corpus into indexes + spec skeletons.

Reads the committed register (``docs/official-apps.json``) and the external
corpus (``Zune HD Apps (Decompiled)/``), then produces, all *outside* the repo:

    _decompiled/<slug>/<Assembly>/    C# from ilspycmd (main exe + local DLLs)
    _decompiled/_framework/<Dll>/     shared Zune framework assemblies, once
    _index/<slug>.json                types/methods/strings/constants/content
    _mine-report.json                 coverage + failures

With ``--specs`` it also writes ``docs/apps/<slug>.md`` skeletons (committed;
filled in by hand/agents). No Microsoft code or asset bytes are ever written
into the repository.

Usage:
    python3 tools/app_mine.py                    # decompile + index everything
    python3 tools/app_mine.py --index-only       # re-index without decompiling
    python3 tools/app_mine.py --specs --limit 3  # skeletons for the first 3
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import subprocess
import sys
from collections import Counter
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
WORKSPACE = REPO.parent
DEFAULT_CORPUS = WORKSPACE / "Zune HD Apps (Decompiled)"
DEFAULT_MINE = WORKSPACE / "Zune HD Apps (Decompiled)" / "_mine"
REGISTER = REPO / "docs" / "official-apps.json"
SPECS_DIR = REPO / "docs" / "apps"
ILSPY = Path.home() / ".dotnet" / "tools" / "ilspycmd"

FRAMEWORK_DLLS = {
    "ZuneAppLib.dll",
    "Microsoft.Xna.Zune.dll",
    "ZuneCoreLib.dll",
    "ZuneGamesLib.dll",
    "Noodles.dll",
    "Microsoft.Xna.Framework.dll",
    "Microsoft.Xna.Framework.Game.dll",
    "System.Xml.Linq.dll",
}


def log(msg: str) -> None:
    print(msg, flush=True)


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def decompile(assembly: Path, out_dir: Path) -> bool:
    if not ILSPY.exists():
        raise SystemExit(f"ilspycmd not found at {ILSPY}")
    marker = out_dir / ".ok"
    if marker.exists():
        return True
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = [str(ILSPY), str(assembly), "-o", str(out_dir)]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    ok = proc.returncode == 0 and any(out_dir.glob("*.cs"))
    if ok:
        marker.write_text("ok\n")
    else:
        log(f"    ! decompile failed for {assembly.name}: {proc.stderr.strip()[:200]}")
    return ok


TYPE_RE = re.compile(
    r"^\s*(?:\[[^\]]*\]\s*)*(?:public|internal|private|protected|static|sealed|abstract|partial|\s)*"
    r"\b(class|struct|enum|interface)\s+([A-Za-z_][\w]*)",
    re.MULTILINE,
)
METHOD_RE = re.compile(
    r"^\s*(?:public|private|protected|internal|static|virtual|override|sealed|async|extern|unsafe|new|\s)+"
    r"[\w<>\[\],\.\?]+\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*$",
    re.MULTILINE,
)
STRING_RE = re.compile(r'(?<!@)"((?:[^"\\\n]|\\.){0,200})"')
NUMBER_RE = re.compile(r"(?<![\w.])(\d+(?:\.\d+f?)?)(?![\w.])")
NAMESPACE_RE = re.compile(r"^namespace\s+([\w\.]+)", re.MULTILINE)


def png_dims(path: Path) -> tuple[int, int] | None:
    try:
        with path.open("rb") as fh:
            head = fh.read(24)
        if len(head) >= 24 and head[:8] == b"\x89PNG\r\n\x1a\n":
            w, h = struct.unpack(">II", head[16:24])
            return int(w), int(h)
    except OSError:
        pass
    return None


def xnb_info(path: Path) -> dict:
    try:
        with path.open("rb") as fh:
            head = fh.read(64)
    except OSError:
        return {}
    if len(head) < 10 or head[:3] != b"XNB":
        return {}
    info: dict = {"platform": head[3], "version": head[4], "flags": head[5]}
    info["compressed"] = bool(head[5] & 0x80)
    if not info["compressed"]:
        try:
            pos = 10
            # 7-bit encoded reader-name length
            length = 0
            shift = 0
            while pos < len(head):
                b = head[pos]
                pos += 1
                length |= (b & 0x7F) << shift
                shift += 7
                if not b & 0x80:
                    break
            if length:
                info["reader"] = head[pos : pos + length].decode("utf-8", "replace")
        except (IndexError, UnicodeDecodeError):
            pass
    return info


def parse_cs(path: Path) -> dict:
    text = path.read_text(encoding="utf-8", errors="replace")
    types = [{"kind": m.group(1), "name": m.group(2)} for m in TYPE_RE.finditer(text)]
    methods = [
        {"name": m.group(1), "params": m.group(2).strip()[:160]}
        for m in METHOD_RE.finditer(text)
        if m.group(1) not in ("if", "for", "while", "switch", "catch", "using", "lock", "return")
    ]
    strings = Counter(m.group(1) for m in STRING_RE.finditer(text))
    numbers = Counter(m.group(1) for m in NUMBER_RE.finditer(text))
    return {
        "lines": text.count("\n") + 1,
        "namespaces": sorted(set(NAMESPACE_RE.findall(text))),
        "types": types,
        "methodCount": len(methods),
        "methods": methods[:4000],
        "stringLiterals": [s for s, _ in strings.most_common(1500)],
        "numericLiterals": [n for n, _ in numbers.most_common(1500)],
    }


def content_inventory(app_dir: Path) -> dict:
    files = []
    totals: Counter[str] = Counter()
    for path in sorted(app_dir.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(app_dir).as_posix()
        ext = path.suffix.lower() or "(none)"
        entry = {"path": rel, "ext": ext, "size": path.stat().st_size}
        if ext == ".png":
            dims = png_dims(path)
            if dims:
                entry["width"], entry["height"] = dims
        elif ext == ".xnb":
            info = xnb_info(path)
            if info:
                entry["xnb"] = info
        files.append(entry)
        totals[ext] += 1
    return {"fileCount": len(files), "byExt": dict(totals.most_common()), "files": files}


def find_assembly(corpus: Path, exe: str) -> tuple[str, Path, Path] | None:
    """Return (app_dir_name, build_dir, exe_path) for the register exe."""
    for exe_path in sorted(corpus.glob(f"*/gametitle/*/{exe}")):
        build_dir = exe_path.parent
        return exe_path.parents[2].name, build_dir, exe_path
    return None


def mine_app(entry: dict, corpus: Path, mine: Path, *, index_only: bool) -> dict:
    slug = entry["slug"]
    found = find_assembly(corpus, entry["exe"])
    result = {"slug": slug, "title": entry["title"], "exe": entry["exe"],
              "corpusDir": None, "assemblies": [], "content": {}, "error": None}
    if not found:
        result["error"] = "assembly not found in corpus"
        return result
    app_dir_name, build_dir, exe_path = found
    result["corpusDir"] = app_dir_name

    app_out = mine / "_decompiled" / slug / exe_path.stem
    fw_root = mine / "_decompiled" / "_framework"
    assemblies = []

    def handle(assembly: Path, out_dir: Path, role: str) -> None:
        name = assembly.name
        digest = sha256_file(assembly)
        parsed = {}
        if not index_only:
            if name in FRAMEWORK_DLLS:
                out_dir = fw_root / f"{Path(name).stem}-{digest[:8]}"
            if decompile(assembly, out_dir):
                cs = sorted(out_dir.glob("*.cs"))
                if cs:
                    parsed = parse_cs(cs[0])
        else:
            cs = sorted(out_dir.glob("*.cs"))
            if cs:
                parsed = parse_cs(cs[0])
        assemblies.append({
            "name": name, "role": role, "sha256": digest,
            "decompiled": None if not parsed else str(out_dir.relative_to(mine)),
            "lines": parsed.get("lines"), "types": parsed.get("types", [])[:400],
            "methodCount": parsed.get("methodCount"),
            "namespaces": parsed.get("namespaces", []),
            "stringLiterals": parsed.get("stringLiterals", []),
            "numericLiterals": parsed.get("numericLiterals", []),
        })

    handle(exe_path, app_out, "app")
    for dll in sorted(build_dir.glob("*.dll")):
        handle(dll, mine / "_decompiled" / slug / dll.stem, "dependency")

    content_dir = build_dir / "Content"
    result["content"] = content_inventory(content_dir) if content_dir.exists() else {}
    result["assemblies"] = assemblies
    index_path = mine / "_index" / f"{slug}.json"
    index_path.parent.mkdir(parents=True, exist_ok=True)
    index_path.write_text(json.dumps(result, indent=1) + "\n")
    return result


SPEC_TEMPLATE = """# {title}

- **Official package:** `{exe}` ({corpus})
- **Corpus:** `{corpus_path}` (external, untracked)
- **Wave:** {wave} · **Category:** {category}
- **Status:** `{status}` · **Complexity:** {complexity}
- **Dorado-HD id:** {installed}

> Synthesized from the decompiled reference. No Microsoft code, strings, or
> assets are copied; behavior is re-derived and all content is re-authored.

## 1. Device behavior (mined)

| Assembly | Types | Methods | Source lines |
|---|---:|---:|---:|
{assembly_table}

## 2. Screens & navigation

_TODO: from the mined types above (views/controllers/scenes)._

## 3. Rules, scoring & progression

_TODO: logic classes, constants, win/lose conditions._

## 4. Controls

_TODO: touch/accelerometer mapping from input handlers._

## 5. Content inventory (must be re-authored)

_TODO: content totals from `_index/{slug}.json`._

## 6. Implementation plan

- Pure-Kotlin engine: `app/src/main/java/com/heretek/dorado_hd/ui/apps/...`
- Compose screen: `...`
- Tests: `app/src/test/...`

## 7. Citation log

_TODO: `Assembly!Type.Method` references used above._
"""


def write_spec(entry: dict, mined: dict | None, specs_dir: Path) -> None:
    spec = specs_dir / f"{entry['slug']}.md"
    if spec.exists():
        return
    rows = []
    corpus_path = "—"
    if mined and mined.get("corpusDir"):
        corpus_path = f"Zune HD Apps (Decompiled)/{mined['corpusDir']}"
    for a in (mined or {}).get("assemblies", []):
        rows.append(
            f"| `{a['name']}` | {len(a.get('types') or [])} | "
            f"{a.get('methodCount') or '—'} | {a.get('lines') or '—'} |"
        )
    body = SPEC_TEMPLATE.format(
        title=entry["title"], exe=entry["exe"],
        corpus=entry.get("corpusDir") or "—",
        corpus_path=corpus_path, wave=entry["wave"], category=entry["category"],
        status=entry["status"], complexity=entry.get("complexity") or "TBD",
        installed=entry.get("installedId") or "—",
        assembly_table="\n".join(rows) if rows else "| _none mined_ |  |  |  |",
        slug=entry["slug"],
    )
    spec.write_text(body)


def size_tier(lines: int | None, content: int) -> str:
    if lines is None:
        return "?"
    if lines < 1500 and content < 40:
        return "S"
    if lines < 5000:
        return "M"
    if lines < 15000:
        return "L"
    return "XL"


def write_audit(register: list[dict], mine: Path, repo: Path) -> None:
    """Regenerate docs/official-apps-audit.md + official-apps-gaps.json."""
    rows = []
    for entry in register:
        index_path = mine / "_index" / f"{entry['slug']}.json"
        stats = {"lines": None, "assemblies": 0, "contentFiles": 0, "types": 0}
        if index_path.exists():
            try:
                idx = json.loads(index_path.read_text())
                main = next((a for a in idx.get("assemblies", []) if a.get("role") == "app"), {})
                stats = {
                    "lines": main.get("lines"),
                    "assemblies": len(idx.get("assemblies", [])),
                    "contentFiles": (idx.get("content") or {}).get("fileCount", 0),
                    "types": len(main.get("types") or []),
                }
            except (json.JSONDecodeError, OSError):
                pass
        row = dict(entry)
        row["sourceLines"] = stats["lines"]
        row["assemblies"] = stats["assemblies"]
        row["contentFiles"] = stats["contentFiles"]
        row["mainTypes"] = stats["types"]
        row["sizeTier"] = size_tier(stats["lines"], stats["contentFiles"])
        rows.append(row)

    (repo / "docs" / "official-apps-gaps.json").write_text(json.dumps(rows, indent=2) + "\n")

    by_wave: dict[str, list[dict]] = {}
    for row in rows:
        by_wave.setdefault(row["wave"], []).append(row)
    status_counts = Counter(r["status"] for r in rows)
    lines = [
        "# Official Zune HD app reimplementation — audit register",
        "",
        "**Status:** program in progress. This file is generated by",
        "`tools/app_mine.py --audit` from `docs/official-apps.json` plus the",
        "external decompiled corpus (counts only; no Microsoft content).",
        "",
        "**Method:** every official package is decompiled with `ilspycmd` into the",
        "external corpus, indexed (types/methods/strings/constants/content), and",
        "reimplemented clean-room in Kotlin/Compose. Decompiled sources are never",
        "committed; specs under `docs/apps/` are synthesized analysis with",
        "`Assembly!Type.Method` citations.",
        "",
        f"**Coverage:** {len(rows)} packages — "
        + ", ".join(f"{k} {v}" for k, v in sorted(status_counts.items()))
        + ".",
        "",
        "| Wave | Apps | Focus |",
        "|---|---:|---|",
        "| W1 | 12 | utilities & music (fidelity upgrade of existing apps) |",
        "| W2 | 9 | card & board games + AI |",
        "| W3 | 12 | casual / puzzle A |",
        "| W4 | 11 | casual / puzzle B + word |",
        "| W5 | 5 | touch / toy / physics |",
        "| W6 | 6 | big 3D engines |",
        "| W7 | 7 (+ social shell) | dead-service pixel-faithful local UIs |",
        "",
        "## Per-app matrix",
        "",
        "`Tier` is a source-size proxy (S/M/L/XL from the decompiled main assembly",
        "and content count); `Complexity` is the authored engine-effort estimate.",
        "",
        "| Wave | App | Status | Exe | Assemblies | Main lines | Content | Tier | Complexity | Spec |",
        "|---|---|---|---|---:|---:|---:|:--:|:--:|---|",
    ]
    for row in sorted(rows, key=lambda r: (r["wave"], r["title"].lower())):
        spec = f"[`{row['slug']}.md`](apps/{row['slug']}.md)"
        lines.append(
            f"| {row['wave']} | {row['title']} | {row['status']} | `{row['exe']}` | "
            f"{row['assemblies']} | {row['sourceLines'] or '—'} | {row['contentFiles']} | "
            f"{row['sizeTier']} | {row['complexity'] or 'TBD'} | {spec} |"
        )
    lines += [
        "",
        "## Legend",
        "",
        "- `status`: `native` (existing Compose app), `mock` (dead-service shell),",
        "  `todo` (not yet implemented).",
        "- `Tier`: rough source-size proxy only; not a difficulty rating.",
        "- Every package has a behavioral spec under `docs/apps/` before",
        "  implementation starts (documentation-first gate).",
        "",
    ]
    (repo / "docs" / "official-apps-audit.md").write_text("\n".join(lines) + "\n")
    log(f"audit written for {len(rows)} apps -> docs/official-apps-audit.md")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--mine", type=Path, default=DEFAULT_MINE)
    parser.add_argument("--index-only", action="store_true")
    parser.add_argument("--specs", action="store_true")
    parser.add_argument("--audit", action="store_true",
                        help="regenerate docs/official-apps-audit.md + gaps register")
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args(argv)

    register = json.loads(REGISTER.read_text())
    if args.limit:
        register = register[: args.limit]
    results = []
    for entry in register:
        log(f"== {entry['slug']} ({entry['exe']})")
        res = mine_app(entry, args.corpus, args.mine, index_only=args.index_only)
        if res.get("error"):
            log(f"    ! {res['error']}")
        else:
            main_assembly = next((a for a in res["assemblies"] if a["role"] == "app"), {})
            log(
                f"    {res['corpusDir']}: {len(res['assemblies'])} assemblies, "
                f"{main_assembly.get('lines') or '?'} main lines, "
                f"{res['content'].get('fileCount', 0)} content files"
            )
            entry["corpusDir"] = res["corpusDir"]
        results.append(res)
        if args.specs:
            write_spec(entry, res, SPECS_DIR)

    if args.specs:
        REGISTER.write_text(json.dumps(register, indent=2) + "\n")

    if args.audit:
        write_audit(register, args.mine, REPO)

    report = {
        "apps": len(results),
        "mined": sum(1 for r in results if not r.get("error")),
        "errors": {r["slug"]: r["error"] for r in results if r.get("error")},
    }
    (args.mine / "_mine-report.json").write_text(json.dumps(report, indent=2) + "\n")
    log(f"\nmined {report['mined']}/{report['apps']} apps; report at {args.mine}/_mine-report.json")
    return 1 if report["errors"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
