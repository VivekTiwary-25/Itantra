#!/usr/bin/env python3
"""Build the FLEURS clip sets for the IndicConformer quantisation ladder.

check : first 30 rows of the FLEURS validation split per language (all step decisions)
test  : first 50 rows of the FLEURS test split per language (final report only; same
        selection rule as measure_wer.py Task 5, so the 7 shared languages have the same clips)
calib : 100 rows of the FLEURS train split, spread across the 9 languages (Q4 only)

Rows come from a local copy of the parquet file when one exists (read-only; e.g. the Part 1 agent's
Task 5 download or the HF cache), otherwise only the needed prefix is downloaded (fleurs_prefix.py).
WAVs are 16 kHz mono PCM16, converted with measure_wer.to_pcm16_wav.
"""
from __future__ import annotations

import argparse
import csv
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from fleurs_prefix import first_rows_local, first_rows_remote  # noqa: E402
from measure_wer import to_pcm16_wav  # noqa: E402

LOCAL_PARQUET_DIRS = [
    Path("D:/projects/SIH/iTantra/docs/sih-metrics/raw/task5-wer/source-parquet/parquet-data"),
    Path.home() / ".cache/huggingface/hub/datasets--google--fleurs/snapshots/70bb2e84b976b7e960aa89f1c648e09c59f894dd/parquet-data",
]

LANGS = {"hi": "hi_in", "gu": "gu_in", "mr": "mr_in", "ta": "ta_in", "te": "te_in",
         "or": "or_in", "bn": "bn_in", "kn": "kn_in", "ml": "ml_in"}
SETS = {"check": ("validation", 30), "test": ("test", 50)}


def calib_counts() -> dict[str, int]:
    counts = {lang: 100 // len(LANGS) for lang in LANGS}
    for lang in list(LANGS)[: 100 % len(LANGS)]:
        counts[lang] += 1
    return counts


def first_rows(root: Path, config: str, split: str, count: int) -> tuple[list[dict], str]:
    name = f"{config}/{split}-00000-of-00001.parquet"
    for base in LOCAL_PARQUET_DIRS:
        local = base / name
        if local.exists() and local.stat().st_size > 0:
            try:
                rows = first_rows_local(local, count)
                if len(rows) == count:
                    return rows, str(local)
                print(f"  local {local} has only {len(rows)} rows", flush=True)
            except Exception as exc:  # partial download by another process, etc.
                print(f"  local {local} unusable: {exc}", flush=True)
    return first_rows_remote(config, split, count, root / "_prefix-cache"), "remote-prefix"


def build(root: Path, set_name: str, split: str, counts: dict[str, int]) -> None:
    for lang, count in counts.items():
        config = LANGS[lang]
        manifest = root / set_name / lang / "manifest.csv"
        if manifest.exists():
            with manifest.open(encoding="utf-8", newline="") as handle:
                existing = list(csv.DictReader(handle))
            if len(existing) == count and all((root / r["wav"]).exists() for r in existing):
                print(f"{set_name} {lang}: cached ({count})", flush=True)
                continue
        out = []
        items, source = first_rows(root, config, split, count)
        for index, item in enumerate(items, start=1):
            rel = Path(set_name) / lang / f"{index:03d}-{item['id']}.wav"
            rate, frames, duration = to_pcm16_wav(item["audio"]["bytes"], root / rel)
            out.append({"id": str(item["id"]), "language": lang, "fleurs_config": config, "split": split,
                        "wav": rel.as_posix(), "transcription": item["transcription"],
                        "raw_transcription": item["raw_transcription"], "frames": str(frames),
                        "duration_seconds": f"{duration:.6f}", "source": source})
        manifest.parent.mkdir(parents=True, exist_ok=True)
        with manifest.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(out[0]))
            writer.writeheader()
            writer.writerows(out)
        print(f"{set_name} {lang}: wrote {len(out)}", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--sets", nargs="+", default=["check", "test", "calib"])
    args = parser.parse_args()
    for set_name in args.sets:
        if set_name == "calib":
            build(args.root, "calib", "train", calib_counts())
        else:
            split, count = SETS[set_name]
            build(args.root, set_name, split, {lang: count for lang in LANGS})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
