"""Deterministic fp32 vs decoder-fp16 length-parity check (noise_scale=0, noise_scale_w=0, length_scale=1)."""
import sys, time, json
from pathlib import Path
import numpy as np
import sherpa_onnx

tokens = "tokens.txt"
lines = [l for l in Path("mr_input.txt").read_text(encoding="utf-8-sig").splitlines() if l.strip()]

def run(model_path, out_dir):
    Path(out_dir).mkdir(exist_ok=True)
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=model_path, tokens=tokens,
                noise_scale=0.0, noise_scale_w=0.0, length_scale=1.0,
            ),
            num_threads=2,
        )
    )
    engine = sherpa_onnx.OfflineTts(config)
    results = []
    for i, text in enumerate(lines, 1):
        audio = engine.generate(text=text, sid=0, speed=1.0)
        n = len(audio.samples)
        sherpa_onnx.write_wave(f"{out_dir}/{i:02d}.wav", audio.samples, audio.sample_rate)
        rms = float(np.sqrt(np.mean(np.asarray(audio.samples, dtype=np.float32) ** 2)))
        results.append({"n": i, "samples": n, "seconds": round(n / audio.sample_rate, 4), "rms": round(rms, 5)})
    return results

fp32 = run("model.onnx", "det_fp32")
fp16 = run("model.fp16dec.onnx", "det_fp16dec")

print(f"{'#':>3} {'fp32 samples':>13} {'fp16 samples':>13} {'delta':>7} {'match':>6}")
all_match = True
for a, b in zip(fp32, fp16):
    delta = b["samples"] - a["samples"]
    match = delta == 0
    all_match &= match
    print(f"{a['n']:>3} {a['samples']:>13} {b['samples']:>13} {delta:>7} {str(match):>6}")

print()
print("ALL LENGTHS MATCH" if all_match else "LENGTH MISMATCH DETECTED")
Path("det_compare_result.json").write_text(json.dumps({"fp32": fp32, "fp16dec": fp16, "all_match": all_match}, indent=2), encoding="utf-8")
