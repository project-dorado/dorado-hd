#!/usr/bin/env python3
"""
Zune HD reverse-engineering corpus builder.

Takes the reconstructed ARM32 PE modules produced by ``disassemble_zune_hd.py``
and, for each module:

  1. recovers named exports/imports with rizin -> ``ghidra/symbols/<mod>.symbols.json``;
  2. imports the module into its own headless-Ghidra project, applies the recovered
     symbol names, decompiles every function, and exports
     ``<mod>.functions.json`` / ``<mod>.strings.json`` / per-function ``.c`` files;
  3. records the result in ``ghidra/corpus-manifest.json``.

Raw output (decompiled C, symbol/string dumps) is Microsoft-derived and stays in
this out-of-tree corpus; it is never committed. Only synthesized analysis is.

Usage:
    python3 scripts/ghidra_corpus.py --tier 1            # Zune-native modules
    python3 scripts/ghidra_corpus.py --modules gemstone.exe,xuidll.dll
    python3 scripts/ghidra_corpus.py --tier all --jobs 8 --resume
"""

import argparse
import concurrent.futures
import hashlib
import json
import os
import subprocess
import sys
import time

BASE_DIR = os.environ.get(
    "ZUNE_HD_DISASSEMBLY_DIR",
    os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "zune-hd-disassembly")),
)
MODULES_DIR = os.path.join(BASE_DIR, "modules")
GHIDRA_DIR = os.path.join(BASE_DIR, "ghidra")
DECOMPILED_DIR = os.path.join(GHIDRA_DIR, "decompiled")
SYMBOLS_DIR = os.path.join(GHIDRA_DIR, "symbols")
PROJECTS_DIR = os.path.join(GHIDRA_DIR, "projects")
LOGS_DIR = os.path.join(GHIDRA_DIR, "logs")
MANIFEST = os.path.join(GHIDRA_DIR, "corpus-manifest.json")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
GHIDRA_SCRIPT_DIR = os.path.join(SCRIPT_DIR, "ghidra")
ANALYZE_HEADLESS = "/home/john/.local/ghidra/support/analyzeHeadless"

# Zune shell + first-party services (highest signal for Dorado-HD parity).
TIER_1 = [
    "gemstone.exe", "xuidll.dll", "zdksystem.dll", "zd3d.dll", "zrender.dll",
    "zhud_serv.dll", "zcontent_serv.dll", "zmedia_serv.dll", "znet_serv.dll",
    "zconfig_serv.dll", "zcredentials_serv.dll", "zam_serv.dll", "ziehooks.dll",
    "zie.exe", "compositor.exe", "zsplash.exe", "xnalauncher.exe", "zuncab.exe",
    "zcab.dll", "zdknet.dll", "zserial.dll", "zmassive.dll", "zlib.dll",
    "ZWmtStreamer.dll",
]


def log(msg):
    print(f"[*] {msg}", flush=True)


def run(cmd, timeout):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def extract_symbols(mod, mod_path, timeout):
    """Return (exports, imports) lists recovered by rizin, and write the symbols file."""
    exports, imports = [], []
    try:
        res = run(["rizin", "-q", "-e", "scr.color=0", "-c", "iEj", mod_path], timeout)
        if res.returncode == 0 and res.stdout.strip():
            exports = json.loads(res.stdout)
    except Exception as ex:
        log(f"  [{mod}] export scan failed: {ex}")
    try:
        res = run(["rizin", "-q", "-e", "scr.color=0", "-c", "iij", mod_path], timeout)
        if res.returncode == 0 and res.stdout.strip():
            for item in json.loads(res.stdout):
                imports.append({
                    "name": item.get("name"),
                    "vaddr": item.get("plt"),
                    "lib": item.get("libname"),
                    "type": item.get("type"),
                })
    except Exception as ex:
        log(f"  [{mod}] import scan failed: {ex}")

    clean_exports = [
        {
            "name": e.get("name") or e.get("realname"),
            "vaddr": e.get("vaddr"),
            "type": e.get("type"),
            "size": e.get("size", 0),
            "ordinal": e.get("ordinal"),
        }
        for e in exports
        if e.get("vaddr") and (e.get("name") or e.get("realname"))
    ]

    os.makedirs(SYMBOLS_DIR, exist_ok=True)
    sym_path = os.path.join(SYMBOLS_DIR, f"{mod}.symbols.json")
    with open(sym_path, "w") as f:
        json.dump({"module": mod, "exports": clean_exports, "imports": imports}, f)
    return clean_exports, imports


def build_module(mod, max_functions, use_symbols, timeout):
    mod_path = os.path.join(MODULES_DIR, mod)
    if not os.path.isfile(mod_path):
        return {"module": mod, "status": "missing"}

    started = time.time()
    exports, imports = extract_symbols(mod, mod_path, 120) if use_symbols else ([], [])
    sym_path = os.path.join(SYMBOLS_DIR, f"{mod}.symbols.json")

    project_dir = os.path.join(PROJECTS_DIR, mod)
    os.makedirs(project_dir, exist_ok=True)
    os.makedirs(LOGS_DIR, exist_ok=True)
    os.makedirs(DECOMPILED_DIR, exist_ok=True)

    cmd = [
        ANALYZE_HEADLESS,
        project_dir,
        "p_" + mod.replace(".", "_"),
        "-import", mod_path,
        "-overwrite",
        "-analysisTimeoutPerFile", str(timeout),
        "-scriptPath", GHIDRA_SCRIPT_DIR,
        "-postScript", "ExportDecompiled.java", DECOMPILED_DIR, str(max_functions),
        sym_path if use_symbols else "",
        "-deleteProject",
    ]

    log_path = os.path.join(LOGS_DIR, f"{mod}.log")
    status = "ok"
    returncode = 0
    try:
        res = run(cmd, timeout + 120)
        returncode = res.returncode
        with open(log_path, "w") as f:
            f.write(res.stdout or "")
            f.write("\n--- STDERR ---\n")
            f.write(res.stderr or "")
        if res.returncode != 0:
            status = "error"
    except subprocess.TimeoutExpired:
        status = "timeout"
        returncode = -1
        with open(log_path, "a") as f:
            f.write("\nTIMEOUT\n")
    except Exception as ex:
        status = "error"
        with open(log_path, "a") as f:
            f.write(f"\nEXCEPTION: {ex}\n")

    fn_index = os.path.join(DECOMPILED_DIR, f"{mod}.functions.json")
    str_index = os.path.join(DECOMPILED_DIR, f"{mod}.strings.json")
    functions = decompiled = strings = 0
    if os.path.isfile(fn_index):
        try:
            data = json.load(open(fn_index))
            functions = len(data)
            decompiled = sum(1 for d in data if "lines" in d)
        except Exception:
            status = "error" if status == "ok" else status
    if os.path.isfile(str_index):
        try:
            strings = len(json.load(open(str_index)))
        except Exception:
            pass

    return {
        "module": mod,
        "size": os.path.getsize(mod_path),
        "sha256": sha256(mod_path),
        "status": status,
        "returncode": returncode,
        "duration_s": round(time.time() - started, 1),
        "exports": len(exports),
        "imports": len(imports),
        "functions": functions,
        "decompiled": decompiled,
        "strings": strings,
    }


def select_modules(args):
    if args.modules:
        return [m.strip() for m in args.modules.split(",") if m.strip()]
    all_modules = sorted(os.listdir(MODULES_DIR))
    if args.tier == "1":
        return [m for m in TIER_1 if m in all_modules]
    if args.tier == "2":
        return [m for m in all_modules if m not in TIER_1]
    return all_modules


def main():
    parser = argparse.ArgumentParser(description="Build the Zune HD RE corpus (Ghidra).")
    parser.add_argument("--tier", choices=["1", "2", "all"], default="all")
    parser.add_argument("--modules", help="Comma-separated module names (overrides --tier).")
    parser.add_argument("--jobs", type=int, default=max(1, min(8, (os.cpu_count() or 4) - 2)))
    parser.add_argument("--max-functions", type=int, default=0, help="0 = decompile every function.")
    parser.add_argument("--no-symbols", action="store_true", help="Skip rizin symbol recovery.")
    parser.add_argument("--resume", action="store_true", help="Skip modules already in the manifest as ok.")
    parser.add_argument("--timeout", type=int, default=3600, help="Per-module analysis timeout (s).")
    args = parser.parse_args()

    if not os.path.exists(ANALYZE_HEADLESS):
        log(f"Ghidra analyzeHeadless not found at {ANALYZE_HEADLESS}.")
        sys.exit(1)
    os.makedirs(PROJECTS_DIR, exist_ok=True)

    modules = select_modules(args)
    existing = {}
    if os.path.isfile(MANIFEST):
        try:
            for entry in json.load(open(MANIFEST)):
                existing[entry["module"]] = entry
        except Exception:
            existing = {}
    if args.resume:
        modules = [m for m in modules if existing.get(m, {}).get("status") != "ok"]

    log(f"Building corpus for {len(modules)} module(s) with {args.jobs} worker(s).")

    results = dict(existing)
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        futures = {
            pool.submit(build_module, m, args.max_functions, not args.no_symbols, args.timeout): m
            for m in modules
        }
        for future in concurrent.futures.as_completed(futures):
            mod = futures[future]
            try:
                entry = future.result()
            except Exception as ex:
                entry = {"module": mod, "status": "error", "error": str(ex)}
            results[mod] = entry
            log(
                f"  [{entry.get('status'):>7}] {mod:<24} "
                f"fns={entry.get('functions', 0):>6} dec={entry.get('decompiled', 0):>6} "
                f"exp={entry.get('exports', 0):>4} {entry.get('duration_s', 0)}s"
            )
            with open(MANIFEST, "w") as f:
                json.dump(sorted(results.values(), key=lambda r: r["module"]), f, indent=1)

    ok = sum(1 for r in results.values() if r.get("status") == "ok")
    log(f"Manifest written: {MANIFEST} ({ok}/{len(results)} ok).")


if __name__ == "__main__":
    main()
