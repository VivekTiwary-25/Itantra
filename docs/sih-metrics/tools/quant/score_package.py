#!/usr/bin/env python3
"""Score one IndicConformer shared-encoder package (<pkg>/<l>.onnx + <pkg>/languages/<l>/tokens.txt)
on a clip set built by prepare_data.py.

Recognizer: sherpa-onnx OfflineRecognizer.from_nemo_ctc, CPU, 2 threads, greedy, 16 kHz, 80 mel bins
(the same construction as the app / multilingual-adapters verify scripts). One language's recognizer
is alive at a time. Normalisation and wrong-script rule are imported from measure_wer.py (Task 5).
Average WER = unweighted mean of the per-language WERs.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from pathlib import Path

import jiwer
import sherpa_onnx
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from measure_wer import normalize, wrong_script  # noqa: E402

SCRIPT = {"hi": (0x0900, 0x097F), "mr": (0x0900, 0x097F), "bn": (0x0980, 0x09FF), "gu": (0x0A80, 0x0AFF),
          "or": (0x0B00, 0x0B7F), "ta": (0x0B80, 0x0BFF), "te": (0x0C00, 0x0C7F), "kn": (0x0C80, 0x0CFF),
          "ml": (0x0D00, 0x0D7F)}
ORDER = ["hi", "gu", "mr", "ta", "te", "or", "bn", "kn", "ml"]


def recognizer(pkg: Path, lang: str):
    return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(pkg / f"{lang}.onnx"), tokens=str(pkg / "languages" / lang / "tokens.txt"),
        num_threads=2, sample_rate=16000, feature_dim=80, decoding_method="greedy_search", provider="cpu")


def score_language(pkg: Path, data: Path, set_name: str, lang: str, limit: int | None, out: Path) -> dict:
    with (data / set_name / lang / "manifest.csv").open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))[:limit]
    t0 = time.perf_counter()
    rec = recognizer(pkg, lang)
    load_s = time.perf_counter() - t0
    result_rows, refs, hyps = [], [], []
    for row in rows:
        samples, rate = sf.read(data / row["wav"], dtype="float32", always_2d=True)
        stream = rec.create_stream()
        stream.accept_waveform(rate, samples[:, 0])
        rec.decode_stream(stream)
        hyp = stream.result.text
        ref_n, hyp_n = normalize(row["transcription"]), normalize(hyp)
        refs.append(ref_n)
        hyps.append(hyp_n)
        result_rows.append({"id": row["id"], "wav": row["wav"], "reference_normalized": ref_n, "hypothesis": hyp,
                            "hypothesis_normalized": hyp_n,
                            "wrong_script": str(wrong_script(hyp_n, SCRIPT[lang])).lower()})
    del rec
    with (out / f"{lang}-results.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(result_rows[0]))
        writer.writeheader()
        writer.writerows(result_rows)
    words, chars = jiwer.process_words(refs, hyps), jiwer.process_characters(refs, hyps)
    return {"language": lang, "clips": len(rows), "word_count": words.hits + words.substitutions + words.deletions,
            "word_errors": words.substitutions + words.deletions + words.insertions, "wer": words.wer,
            "character_count": chars.hits + chars.substitutions + chars.deletions,
            "character_errors": chars.substitutions + chars.deletions + chars.insertions, "cer": chars.cer,
            "wrong_script_outputs": sum(r["wrong_script"] == "true" for r in result_rows),
            "load_seconds_desktop_unreliable": round(load_s, 3)}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pkg", type=Path, required=True)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--set", default="check")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--languages", nargs="+", default=ORDER)
    parser.add_argument("--limit", type=int, default=None)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    done: dict[str, dict] = {}
    if (args.out / "summary.json").exists():   # merge: keep languages scored earlier into the same run dir
        previous = json.loads((args.out / "summary.json").read_text(encoding="utf-8"))
        if previous.get("package") == str(args.pkg) and previous.get("set") == args.set:
            done = {s["language"]: s for s in previous["languages"]}
    for lang in args.languages:
        done[lang] = score_language(args.pkg, args.data, args.set, lang, args.limit, args.out)
        s = done[lang]
        print(f"{lang} WER {s['wer']:.4f} CER {s['cer']:.4f} wrong-script {s['wrong_script_outputs']}", flush=True)
        partial = {"package": str(args.pkg), "set": args.set, "languages": [done[k] for k in ORDER if k in done]}
        (args.out / "summary.json").write_text(json.dumps(partial, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summaries = [done[k] for k in ORDER if k in done]
    avg = sum(s["wer"] for s in summaries) / len(summaries)
    report = {"package": str(args.pkg), "set": args.set, "sherpa_onnx": sherpa_onnx.__version__,
              "average_wer": avg, "languages": summaries}
    (args.out / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"average WER {avg:.4f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
