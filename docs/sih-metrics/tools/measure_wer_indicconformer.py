#!/usr/bin/env python3
"""Task 5b: score IndicConformer (multilingual-adapters, NeMo CTC) on the Task 5 FLEURS clips.

Reuses measure_wer.py for clip selection, normalisation, wrong-script test and
WER/CER, so numbers are directly comparable with the Task 5 Dolphin baseline.
One recognizer is alive at a time (release before loading the next language),
matching the app's switching rule. CPU, 2 threads, greedy search.
"""

from __future__ import annotations

import argparse
import csv
import gc
import json
import platform
import sys
import time
from pathlib import Path

import jiwer
import sherpa_onnx

sys.path.insert(0, str(Path(__file__).resolve().parent))
import measure_wer as base  # noqa: E402

# kn and ml are IC-only extras; they are not part of the Task 5 Dolphin baseline.
base.LANGUAGES.setdefault("kn", ("kn_in", (0x0C80, 0x0CFF)))
base.LANGUAGES.setdefault("ml", ("ml_in", (0x0D00, 0x0D7F)))

DEFAULT_LANGS = ["hi", "gu", "mr", "ta", "te", "or", "bn", "kn", "ml"]


def make_recognizer(model_dir: Path, language: str):
    return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(model_dir / f"{language}.onnx"),
        tokens=str(model_dir / "languages" / language / "tokens.txt"),
        num_threads=2,
        sample_rate=16000,
        feature_dim=80,
        decoding_method="greedy_search",
        provider="cpu",
    )


def check_same_clips(repo: Path, language: str, rows: list[dict[str, str]]) -> bool:
    """Confirm the 50 clips equal the ones committed for Task 5 (Dolphin baseline)."""
    task5 = repo / "docs" / "sih-metrics" / "raw" / "task5-wer" / f"{language}-results.csv"
    if not task5.exists():
        return True  # kn/ml: no Dolphin baseline
    with task5.open("r", encoding="utf-8", newline="") as handle:
        committed = list(csv.DictReader(handle))
    same = [r["id"] for r in committed] == [r["id"] for r in rows] and [
        r["transcription"] for r in committed
    ] == [r["transcription"] for r in rows]
    return same


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--count", type=int, default=50)
    parser.add_argument("--languages", nargs="+", default=DEFAULT_LANGS)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    repo = args.repo.resolve()
    fleurs_root = repo / "docs" / "sih-metrics" / "raw" / "task5-wer"
    out_root = args.out or (repo / "docs" / "sih-metrics" / "raw" / "task5b-indicconformer")
    out_root.mkdir(parents=True, exist_ok=True)
    (out_root / "environment.json").write_text(
        json.dumps(
            {
                "model": "IndicConformer-600M multilingual-adapters (shared INT8-B encoder + per-language wrapper)",
                "model_dir": str(args.model_dir),
                "sherpa_onnx": getattr(sherpa_onnx, "__version__", "?"),
                "jiwer": getattr(jiwer, "__version__", "?"),
                "python": sys.version,
                "platform": platform.platform(),
                "settings": "sherpa-onnx from_nemo_ctc, CPU, 2 threads, greedy_search, feature_dim=80",
                "dataset": "google/fleurs test, first 50 rows per language config (same as Task 5)",
                "normalization": "lowercase, strip Unicode punctuation, NFC, collapse whitespace (measure_wer.normalize)",
                "languages": args.languages,
                "note": "decode_seconds are desktop timings taken while another process used the CPU; not a result",
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )

    summaries = []
    for language in args.languages:
        rows = base.ensure_fleurs(language, fleurs_root, args.count)
        same = check_same_clips(repo, language, rows)
        print(f"{language}: clips identical to Task 5 set: {same}", flush=True)
        if not same:
            raise SystemExit(f"{language}: clip set differs from Task 5 - refusing to score")

        load_start = time.perf_counter()
        recognizer = make_recognizer(args.model_dir, language)
        load_seconds = time.perf_counter() - load_start

        result_rows, references, hypotheses = [], [], []
        for index, row in enumerate(rows, start=1):
            hypothesis, decode_seconds = base.transcribe(recognizer, fleurs_root / row["wav"])
            ref_n = base.normalize(row["transcription"])
            hyp_n = base.normalize(hypothesis)
            references.append(ref_n)
            hypotheses.append(hyp_n)
            result_rows.append(
                {
                    **row,
                    "reference_normalized": ref_n,
                    "hypothesis": hypothesis,
                    "hypothesis_normalized": hyp_n,
                    "decode_seconds": f"{decode_seconds:.6f}",
                    "wrong_script": str(base.wrong_script(hyp_n, base.LANGUAGES[language][1])).lower(),
                }
            )
            print(f"{language} {index:02d}/{args.count}", flush=True)
        del recognizer
        gc.collect()

        out_csv = out_root / f"{language}-results.csv"
        with out_csv.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(result_rows[0]))
            writer.writeheader()
            writer.writerows(result_rows)
        words = jiwer.process_words(references, hypotheses)
        chars = jiwer.process_characters(references, hypotheses)
        summaries.append(
            {
                "language": language,
                "clips": len(result_rows),
                "word_count": words.hits + words.substitutions + words.deletions,
                "word_errors": words.substitutions + words.deletions + words.insertions,
                "wer": words.wer,
                "character_count": chars.hits + chars.substitutions + chars.deletions,
                "character_errors": chars.substitutions + chars.deletions + chars.insertions,
                "cer": chars.cer,
                "wrong_script_outputs": sum(r["wrong_script"] == "true" for r in result_rows),
                "results_csv": out_csv.relative_to(repo).as_posix(),
                "desktop_load_seconds_unreliable": round(load_seconds, 2),
            }
        )
        (out_root / "summary.json").write_text(
            json.dumps(summaries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        s = summaries[-1]
        print(
            f"== {language}: WER {s['wer']:.4f} ({s['word_errors']}/{s['word_count']}) "
            f"CER {s['cer']:.4f} wrong-script {s['wrong_script_outputs']}",
            flush=True,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
