#!/usr/bin/env python3
"""Measure iTantra STT WER/CER on 50 FLEURS test clips per language.

Recognizer construction mirrors SpeechRecognizerManager.kt: English uses
Whisper tiny.en INT8 and seven Indic languages share Dolphin base CTC INT8,
with two CPU threads and greedy search.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import platform
import re
import subprocess
import sys
import time
import unicodedata
from pathlib import Path

import jiwer
import numpy as np
import sherpa_onnx
import soundfile as sf
from datasets import Audio, load_dataset


LANGUAGES = {
    "en": ("en_us", (0x0041, 0x024F)),
    "hi": ("hi_in", (0x0900, 0x097F)),
    "gu": ("gu_in", (0x0A80, 0x0AFF)),
    "mr": ("mr_in", (0x0900, 0x097F)),
    "ta": ("ta_in", (0x0B80, 0x0BFF)),
    "te": ("te_in", (0x0C00, 0x0C7F)),
    "or": ("or_in", (0x0B00, 0x0B7F)),
    "bn": ("bn_in", (0x0980, 0x09FF)),
}


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFC", text).lower()
    text = "".join(ch if not unicodedata.category(ch).startswith("P") else " " for ch in text)
    return re.sub(r"\s+", " ", text).strip()


def wrong_script(text: str, expected_range: tuple[int, int]) -> bool:
    letters = [ch for ch in text if unicodedata.category(ch).startswith("L")]
    if not letters:
        return False
    lo, hi = expected_range
    return not any(lo <= ord(ch) <= hi for ch in letters)


def to_pcm16_wav(source_bytes: bytes, destination: Path) -> tuple[int, int, float]:
    samples, sample_rate = sf.read(io.BytesIO(source_bytes), dtype="float32", always_2d=True)
    mono = samples.mean(axis=1)
    if sample_rate != 16000:
        old_x = np.arange(len(mono), dtype=np.float64) / sample_rate
        new_len = round(len(mono) * 16000 / sample_rate)
        new_x = np.arange(new_len, dtype=np.float64) / 16000
        mono = np.interp(new_x, old_x, mono).astype(np.float32)
        sample_rate = 16000
    destination.parent.mkdir(parents=True, exist_ok=True)
    sf.write(destination, mono, 16000, subtype="PCM_16", format="WAV")
    return sample_rate, len(mono), len(mono) / 16000.0


def ensure_fleurs(language: str, root: Path, count: int) -> list[dict[str, str]]:
    manifest_path = root / "dataset" / language / "manifest.csv"
    if manifest_path.exists():
        with manifest_path.open("r", encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))
        paths = [row["wav"] for row in rows[:count]]
        if (
            len(rows) >= count
            and len(set(paths)) == count
            and all((root / path).exists() for path in paths)
        ):
            return rows[:count]

    config = LANGUAGES[language][0]
    local_parquet = (
        root
        / "source-parquet"
        / "parquet-data"
        / config
        / "test-00000-of-00001.parquet"
    )
    if local_parquet.exists():
        dataset = load_dataset("parquet", data_files=str(local_parquet), split="train")
    else:
        dataset = load_dataset("google/fleurs", config, split="test", streaming=True)
    dataset = dataset.cast_column("audio", Audio(decode=False))
    rows: list[dict[str, str]] = []
    for source_index, item in enumerate(dataset, start=1):
        if len(rows) >= count:
            break
        relative_wav = Path("dataset") / language / f"{source_index:03d}-{item['id']}.wav"
        rate, frames, duration = to_pcm16_wav(item["audio"]["bytes"], root / relative_wav)
        rows.append({
            "id": str(item["id"]),
            "language": language,
            "fleurs_config": config,
            "wav": relative_wav.as_posix(),
            "transcription": item["transcription"],
            "raw_transcription": item["raw_transcription"],
            "sample_rate": str(rate),
            "channels": "1",
            "subtype": "PCM_16",
            "frames": str(frames),
            "duration_seconds": f"{duration:.6f}",
        })
    if len(rows) != count:
        raise RuntimeError(f"{config}: requested {count} clips, found {len(rows)}")
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    with manifest_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    return rows


def make_recognizers(assets: Path) -> tuple[object, object]:
    whisper = sherpa_onnx.OfflineRecognizer.from_whisper(
        encoder=str(assets / "tiny.en-encoder.int8.onnx"),
        decoder=str(assets / "tiny.en-decoder.int8.onnx"),
        tokens=str(assets / "tiny.en-tokens.txt"),
        language="en",
        task="transcribe",
        num_threads=2,
        decoding_method="greedy_search",
        provider="cpu",
    )
    dolphin = sherpa_onnx.OfflineRecognizer.from_dolphin_ctc(
        model=str(assets / "dolphin-base-ctc-multi-lang-int8" / "model.int8.onnx"),
        tokens=str(assets / "dolphin-base-ctc-multi-lang-int8" / "tokens.txt"),
        num_threads=2,
        sample_rate=16000,
        decoding_method="greedy_search",
        provider="cpu",
    )
    return whisper, dolphin


def transcribe(recognizer: object, wav_path: Path) -> tuple[str, float]:
    samples, sample_rate = sf.read(wav_path, dtype="float32", always_2d=True)
    stream = recognizer.create_stream()
    stream.accept_waveform(sample_rate, samples[:, 0])
    started = time.perf_counter()
    recognizer.decode_stream(stream)
    return stream.result.text, time.perf_counter() - started


def git_value(repo: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=repo, text=True).strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--count", type=int, default=50)
    parser.add_argument("--languages", nargs="+", default=list(LANGUAGES))
    args = parser.parse_args()

    repo = args.repo.resolve()
    raw_root = repo / "docs" / "sih-metrics" / "raw" / "task5-wer"
    raw_root.mkdir(parents=True, exist_ok=True)
    assets = repo / "app" / "src" / "main" / "assets"
    metadata = {
        "dataset": "google/fleurs",
        "split": "test",
        "selection": "first 50 rows from each requested language configuration",
        "normalization": "lowercase, strip Unicode punctuation, Unicode NFC, collapse whitespace",
        "method": "same model files, desktop sherpa-onnx; phone spot-check recorded separately",
        "commit": git_value(repo, "rev-parse", "HEAD"),
        "branch": git_value(repo, "branch", "--show-current"),
        "python": sys.version,
        "platform": platform.platform(),
        "sherpa_onnx": getattr(sherpa_onnx, "__version__", "1.13.7"),
        "jiwer": getattr(jiwer, "__version__", "4.0.0"),
        "languages": args.languages,
        "clips_per_language": args.count,
    }
    (raw_root / "environment.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    manifests = {lang: ensure_fleurs(lang, raw_root, args.count) for lang in args.languages}
    whisper, dolphin = make_recognizers(assets)
    summaries = []
    for language in args.languages:
        recognizer = whisper if language == "en" else dolphin
        output_path = raw_root / f"{language}-results.csv"
        result_rows = []
        references, hypotheses = [], []
        for index, row in enumerate(manifests[language], start=1):
            hypothesis, decode_seconds = transcribe(recognizer, raw_root / row["wav"])
            reference_normalized = normalize(row["transcription"])
            hypothesis_normalized = normalize(hypothesis)
            references.append(reference_normalized)
            hypotheses.append(hypothesis_normalized)
            result_rows.append({
                **row,
                "reference_normalized": reference_normalized,
                "hypothesis": hypothesis,
                "hypothesis_normalized": hypothesis_normalized,
                "decode_seconds": f"{decode_seconds:.6f}",
                "wrong_script": str(wrong_script(hypothesis_normalized, LANGUAGES[language][1])).lower(),
            })
            print(f"{language} {index:02d}/{args.count} {decode_seconds:.3f}s", flush=True)

        with output_path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(result_rows[0]))
            writer.writeheader()
            writer.writerows(result_rows)
        words = jiwer.process_words(references, hypotheses)
        chars = jiwer.process_characters(references, hypotheses)
        summaries.append({
            "language": language,
            "clips": len(result_rows),
            "word_count": words.hits + words.substitutions + words.deletions,
            "word_errors": words.substitutions + words.deletions + words.insertions,
            "wer": words.wer,
            "character_count": chars.hits + chars.substitutions + chars.deletions,
            "character_errors": chars.substitutions + chars.deletions + chars.insertions,
            "cer": chars.cer,
            "wrong_script_outputs": sum(row["wrong_script"] == "true" for row in result_rows),
            "results_csv": output_path.relative_to(repo).as_posix(),
        })
        (raw_root / "summary.json").write_text(json.dumps(summaries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summaries, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
