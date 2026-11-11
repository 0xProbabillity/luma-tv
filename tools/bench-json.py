#!/usr/bin/env python3
"""Turns tools/baseline.sh reports into one JSON file the website and the release build can read.

    python3 tools/bench-json.py report1.txt report2.txt ... [--out docs/data/bench.json]
    python3 tools/bench-json.py --check docs/data/bench.json

Each report is one launcher measured on the same TV (written by tools/baseline.sh; tools/bench.sh runs them for you).
The file says WHICH TV, WHICH versions and WHEN, so a number is never shown without where it came from.
Schema 1:
  {"schema": 1, "generated": "...", "device": {...}, "results": [{"id","name","package","version","compile",
   "metrics": {...every METRIC...}, "highlights": {...the few numbers the website shows...}}]}
"""
import argparse, datetime, json, re, sys

NAMES = {"shangos": "shangOS", "luma": "Luma TV", "upstream": "Luma TV", "stock": "The TV's own launcher"}
# The numbers the website shows (name -> (metric, divisor, decimals)); everything else stays in "metrics" for anyone who wants it.
HIGHLIGHTS = {
    "memory_idle_mb": ("mem_pss_kb", 1024, 1),
    "memory_after_returns_mb": ("mem_after_returns_pss_kb", 1024, 1),
    "first_frame_ms": ("launch_median_ms", 1, 0),
    "return_to_home_ms": ("return_ms_median", 1, 0),
    "cpu_idle_percent_of_core": ("cpu_idle_pct", 1, 1),
    "janky_frames_percent": ("jank_pct", 1, 1),
    "apk_mb": ("apk_bytes", 1048576, 1),
}


def parse(path):
    label, ts, info, metrics = None, None, {}, {}
    for line in open(path, encoding="utf-8", errors="replace"):
        line = line.rstrip("\n")
        m = re.match(r"== (\S+)\s+(\S+)", line)
        if m:
            label, ts = m.group(1), m.group(2)
        m = re.match(r"INFO (\w+)=(.*)$", line)
        if m:
            info[m.group(1)] = m.group(2).strip()
        m = re.match(r"METRIC (\w+)=(.*)$", line)
        if m:
            try:
                v = float(m.group(2))
                metrics[m.group(1)] = int(v) if v.is_integer() else v
            except ValueError:
                pass
    if not label:
        sys.exit(f"{path}: not a baseline.sh report (no '== label' line)")
    return label, ts, info, metrics


def result_of(path):
    label, ts, info, metrics = parse(path)
    key = re.sub(r"[-_].*$", "", label)  # shangos-light -> shangos
    base = NAMES.get(key, label)
    variant = label[len(key) + 1:] if len(label) > len(key) else ""
    comp = re.search(r"status=([a-z-]+)", info.get("compile", ""))
    hl = {}
    for name, (metric, div, dec) in HIGHLIGHTS.items():
        if metric in metrics:
            hl[name] = round(metrics[metric] / div, dec) if dec else int(round(metrics[metric] / div))
    return {
        "id": label, "name": base, "variant": variant or None, "package": info.get("package"), "version": info.get("version"),
        "measured_at": ts, "compile": comp.group(1) if comp else None, "metrics": metrics, "highlights": hl,
        "complete": all(k in metrics for k in ("launch_median_ms", "mem_pss_kb", "cpu_idle_pct", "jank_pct")),
    }, info


def build(paths):
    results, devices = [], []
    for p in paths:
        r, info = result_of(p)
        results.append(r)
        devices.append((info.get("device"), info.get("memory"), info.get("display")))
    if len(set(devices)) > 1:
        sys.exit("These reports come from different TVs; a comparison needs the same TV: " + "; ".join(str(d[0]) for d in devices))
    dev, mem, disp = devices[0]
    dev = re.sub(r"^(\S+?)\.? \1\b", r"\1", dev or "")
    mem_kb = re.search(r"(\d+)", mem or "")
    return {
        "schema": 1,
        "generated": max((r["measured_at"] for r in results if r.get("measured_at")), default=datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")),
        "tool": "tools/bench.sh (tools/baseline.sh)",
        "method": "https://github.com/madushas/shangos-launcher/blob/main/docs/MEASURING.md",
        "device": {"description": dev, "memory_kb": int(mem_kb.group(1)) if mem_kb else None, "display": (lambda m: f"{m.group(1)} at {m.group(2)} dpi" if m else (disp or ""))(re.search(r"(\d+x\d+).*?(\d+)\s*$", disp or ""))},
        "results": results,
    }


def check(path):
    d = json.load(open(path, encoding="utf-8"))
    problems = []
    if d.get("schema") != 1:
        problems.append("schema must be 1")
    if not d.get("device", {}).get("description"):
        problems.append("device.description is missing")
    if not d.get("results"):
        problems.append("no results")
    for r in d.get("results", []):
        for k in ("id", "name", "version", "metrics", "highlights"):
            if not r.get(k):
                problems.append(f"{r.get('id', '?')}: {k} is missing")
        if "launch_median_ms" not in r.get("metrics", {}):
            problems.append(f"{r.get('id', '?')}: no valid launch runs")
        for k, v in r.get("highlights", {}).items():
            if not isinstance(v, (int, float)) or v < 0:
                problems.append(f"{r.get('id', '?')}: {k} is not a number")
    return problems


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("reports", nargs="*")
    ap.add_argument("--out", default="docs/data/bench.json")
    ap.add_argument("--check", metavar="FILE", help="validate a bench.json and exit non-zero on problems")
    a = ap.parse_args()
    if a.check:
        probs = check(a.check)
        for p in probs:
            print("bench:", p, file=sys.stderr)
        print("ok" if not probs else f"{len(probs)} problem(s)")
        sys.exit(1 if probs else 0)
    if not a.reports:
        ap.error("give at least one report")
    d = build(a.reports)
    import os
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"wrote {a.out}: " + ", ".join(f"{r['name']} {r['version']}" for r in d["results"]))


if __name__ == "__main__":
    main()
