#!/usr/bin/env python3
"""Desktop sanity probe for a sherpa-onnx MMS VITS export (mirrors the Bengali
recipe in tts-research/mms-ben/MMS_BN_CONVERSION.md, rewritten here because that
exact script was not committed to the repo).

For each line of --input-file: loads the vocab from --tokens, reports which
codepoints in the line are NOT in the vocab (sherpa-onnx silently drops these
with a "Skip unknown character" log instead of raising), synthesizes --repeat
times, and writes WAV + per-line timing/length/RMS/peak to --output-dir.
"""
import argparse
import json
import time
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx


def load_vocab_chars(tokens_path: str) -> set:
    chars = set()
    for line in Path(tokens_path).read_text(encoding="utf-8").splitlines():
        if not line:
            continue
        # token id is the last space-separated field; the token itself is
        # everything before it (the literal token may itself be a space).
        token = line.rsplit(" ", 1)[0]
        if token == "":
            token = " "
        chars.add(token)
    return chars


def missing_chars(line: str, vocab: set) -> list:
    return [ch for ch in line if ch not in vocab and not ch.isspace()]


def write_wav(path: str, samples: np.ndarray, sample_rate: int):
    pcm16 = (np.clip(samples, -1.0, 1.0) * 32767.0).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sample_rate)
        w.writeframes(pcm16.tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--language-code", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--tokens", required=True)
    ap.add_argument("--input-file", required=True)
    ap.add_argument("--output-dir", required=True)
    ap.add_argument("--repeat", type=int, default=1)
    ap.add_argument("--num-threads", type=int, default=2)
    ap.add_argument("--data-dir", default="")
    args = ap.parse_args()

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    vocab = load_vocab_chars(args.tokens)

    vits_config = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=args.model, tokens=args.tokens, data_dir=args.data_dir
    )
    model_config = sherpa_onnx.OfflineTtsModelConfig(
        vits=vits_config, num_threads=args.num_threads, debug=False
    )
    config = sherpa_onnx.OfflineTtsConfig(model=model_config)
    load_start = time.time()
    tts = sherpa_onnx.OfflineTts(config)
    load_s = time.time() - load_start

    lines = [
        l for l in Path(args.input_file).read_text(encoding="utf-8").splitlines() if l.strip()
    ]

    results = {"language": args.language_code, "model_load_s": load_s, "lines": []}

    for idx, line in enumerate(lines, start=1):
        missing = missing_chars(line, vocab)
        for rep in range(1, args.repeat + 1):
            t0 = time.time()
            audio = tts.generate(line, sid=0, speed=1.0)
            dt = time.time() - t0
            samples = np.asarray(audio.samples, dtype=np.float32)
            duration_s = len(samples) / audio.sample_rate
            rms = float(np.sqrt(np.mean(samples**2))) if len(samples) else 0.0
            peak = float(np.max(np.abs(samples))) if len(samples) else 0.0
            suffix = "" if rep == 1 else f"_r{rep}"
            wav_name = f"{idx:02d}{suffix}.wav"
            write_wav(str(out_dir / wav_name), samples, audio.sample_rate)
            results["lines"].append(
                {
                    "idx": idx,
                    "rep": rep,
                    "text": line,
                    "missing_chars": missing,
                    "missing_codepoints": [f"U+{ord(c):04X}" for c in missing],
                    "synth_s": dt,
                    "audio_duration_s": duration_s,
                    "rtf": dt / duration_s if duration_s > 0 else None,
                    "sample_rate": audio.sample_rate,
                    "rms": rms,
                    "peak": peak,
                    "wav": wav_name,
                }
            )
            print(
                f"[{idx:02d}{suffix}] dur={duration_s:.2f}s synth={dt:.2f}s "
                f"missing={missing if missing else 'none'}"
            )

    (out_dir / "timings.json").write_text(
        json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8"
    )


if __name__ == "__main__":
    main()
