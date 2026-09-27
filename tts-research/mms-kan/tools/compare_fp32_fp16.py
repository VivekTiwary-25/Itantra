#!/usr/bin/env python3
"""Deterministic fp32-vs-decoder-fp16 length-parity check (mirrors the Bengali
gate in tts-research/mms-ben/speed/SPEED_REPORT.md section 2: noise=0,
noise_scale_w=0, length_scale=1, silence_scale=1, >=10 sentences). Reports
per-sentence sample-count and SNR; a length mismatch fails the candidate.
"""
import argparse
import json
from pathlib import Path

import numpy as np
import sherpa_onnx


def make_engine(model, tokens, num_threads=2):
    vits = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=model, tokens=tokens, data_dir="", noise_scale=0.0, noise_scale_w=0.0, length_scale=1.0
    )
    cfg = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=num_threads, debug=False)
    )
    return sherpa_onnx.OfflineTts(cfg)


def snr_db(a: np.ndarray, b: np.ndarray) -> float:
    n = min(len(a), len(b))
    a, b = a[:n], b[:n]
    noise = a - b
    num = float(np.sum(a.astype(np.float64) ** 2))
    den = float(np.sum(noise.astype(np.float64) ** 2))
    if den <= 0:
        return float("inf")
    return 10.0 * np.log10(num / den) if num > 0 else float("-inf")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tokens", required=True)
    ap.add_argument("--fp32-model", required=True)
    ap.add_argument("--fp16-model", required=True)
    ap.add_argument("--input-file", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    lines = [l for l in Path(args.input_file).read_text(encoding="utf-8").splitlines() if l.strip()]

    fp32 = make_engine(args.fp32_model, args.tokens)
    fp16 = make_engine(args.fp16_model, args.tokens)

    rows = []
    all_ok = True
    for idx, line in enumerate(lines, start=1):
        gc32 = sherpa_onnx.GenerationConfig()
        gc32.sid = 0
        gc32.speed = 1.0
        gc32.silence_scale = 1.0
        a32 = fp32.generate(line, gc32)

        gc16 = sherpa_onnx.GenerationConfig()
        gc16.sid = 0
        gc16.speed = 1.0
        gc16.silence_scale = 1.0
        a16 = fp16.generate(line, gc16)

        s32 = np.asarray(a32.samples, dtype=np.float32)
        s16 = np.asarray(a16.samples, dtype=np.float32)
        match = len(s32) == len(s16)
        all_ok = all_ok and match
        snr = snr_db(s32, s16)
        rows.append(
            {
                "idx": idx,
                "text": line,
                "fp32_samples": len(s32),
                "fp16_samples": len(s16),
                "length_match": match,
                "snr_db": snr,
            }
        )
        print(f"[{idx:02d}] fp32={len(s32)} fp16={len(s16)} match={match} snr={snr:.1f}dB")

    Path(args.out).write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    n_match = sum(r["length_match"] for r in rows)
    print(f"\n{n_match}/{len(rows)} length-matched. overall {'PASS' if all_ok else 'FAIL'}")


if __name__ == "__main__":
    main()
