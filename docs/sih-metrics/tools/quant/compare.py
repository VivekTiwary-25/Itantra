#!/usr/bin/env python3
"""Desktop acceptance check for one ladder step against the last accepted model (check set).

Accuracy drop = ANY of: (a) average WER up by > 0.5 points; (b) any language's WER up by > 1.0 point;
(c) any language's wrong-script count up. Size rule: candidate package must be smaller
(unless --equal-size-ok, used for Q4). Also reports identical-transcript counts per language.
"""
from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


def load(run: Path) -> tuple[dict, dict[str, list[str]]]:
    summary = json.loads((run / "summary.json").read_text(encoding="utf-8"))
    hyps = {}
    for s in summary["languages"]:
        with (run / f"{s['language']}-results.csv").open(encoding="utf-8", newline="") as handle:
            hyps[s["language"]] = [r["hypothesis_normalized"] for r in csv.DictReader(handle)]
    return summary, hyps


def compare(base_run: Path, cand_run: Path, base_bytes: int, cand_bytes: int, equal_size_ok: bool = False) -> dict:
    base, base_h = load(base_run)
    cand, cand_h = load(cand_run)
    b = {s["language"]: s for s in base["languages"]}
    rows, reasons = [], []
    for s in cand["languages"]:
        lang, o = s["language"], b[s["language"]]
        d_wer = (s["wer"] - o["wer"]) * 100
        d_ws = s["wrong_script_outputs"] - o["wrong_script_outputs"]
        same = sum(x == y for x, y in zip(base_h[lang], cand_h[lang]))
        rows.append({"language": lang, "base_wer": o["wer"], "cand_wer": s["wer"], "delta_wer_points": round(d_wer, 2),
                     "base_cer": o["cer"], "cand_cer": s["cer"], "base_wrong_script": o["wrong_script_outputs"],
                     "cand_wrong_script": s["wrong_script_outputs"], "identical_transcripts": f"{same}/{len(cand_h[lang])}"})
        if d_wer > 1.0:
            reasons.append(f"(b) {lang} WER +{d_wer:.2f} points")
        if d_ws > 0:
            reasons.append(f"(c) {lang} wrong-script +{d_ws}")
    d_avg = (cand["average_wer"] - base["average_wer"]) * 100
    if d_avg > 0.5:
        reasons.insert(0, f"(a) average WER +{d_avg:.2f} points")
    smaller = cand_bytes < base_bytes or (equal_size_ok and cand_bytes <= base_bytes)
    verdict = "DESKTOP-ACCEPTED" if not reasons and smaller else ("REJECTED-ACCURACY" if reasons else "REJECTED-NOT-SMALLER")
    return {"verdict": verdict, "accuracy_drop_reasons": reasons, "base_average_wer": base["average_wer"],
            "cand_average_wer": cand["average_wer"], "delta_average_wer_points": round(d_avg, 2),
            "base_bytes": base_bytes, "cand_bytes": cand_bytes, "size_delta_bytes": cand_bytes - base_bytes, "languages": rows}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-run", type=Path, required=True)
    parser.add_argument("--cand-run", type=Path, required=True)
    parser.add_argument("--base-pkg", type=Path, required=True)
    parser.add_argument("--cand-pkg", type=Path, required=True)
    parser.add_argument("--equal-size-ok", action="store_true")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    size = lambda p: json.loads((p / "package_manifest.json").read_text(encoding="utf-8"))["total_bytes"]
    result = compare(args.base_run, args.cand_run, size(args.base_pkg), size(args.cand_pkg), args.equal_size_ok)
    text = json.dumps(result, ensure_ascii=False, indent=1)
    if args.out:
        args.out.write_text(text + "\n", encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
