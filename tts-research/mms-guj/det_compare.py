# Deterministic fp32 vs decoder-fp16 length-parity check, mirroring tts-research/mms-ben/speed
# (noise_scale=0, noise_scale_w=0 -> silence_scale=1 equivalent deterministic path).
import sherpa_onnx, sys, os, json, math

M = "D:/iTantra-tts-models/mms-guj/"
lines = [l.strip() for l in open("gu_input_normalized.txt", encoding="utf-8") if l.strip()]


def engine(model):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=model, tokens=M + "tokens.txt", data_dir="", noise_scale=0.0, noise_scale_w=0.0),
            num_threads=4)))


e32 = engine(M + "model.onnx")
e16 = engine(M + "model.fp16dec.onnx")

os.makedirs("det_fp32", exist_ok=True)
os.makedirs("det_fp16dec", exist_ok=True)

results = []
for i, l in enumerate(lines):
    a32 = e32.generate(l, sid=0, speed=1.0)
    a16 = e16.generate(l, sid=0, speed=1.0)
    sherpa_onnx.write_wave(f"det_fp32/{i+1:02d}.wav", a32.samples, a32.sample_rate)
    sherpa_onnx.write_wave(f"det_fp16dec/{i+1:02d}.wav", a16.samples, a16.sample_rate)
    n32, n16 = len(a32.samples), len(a16.samples)
    match = n32 == n16
    # SNR of fp16 against fp32 (same length required)
    snr = None
    if match and n32 > 0:
        import numpy as np
        s32 = np.array(a32.samples)
        s16 = np.array(a16.samples)
        noise = s32 - s16
        signal_power = float(np.mean(s32 ** 2))
        noise_power = float(np.mean(noise ** 2)) or 1e-12
        snr = 10 * math.log10(signal_power / noise_power) if signal_power > 0 else None
    results.append({"line": i + 1, "len32": n32, "len16": n16, "length_match": match, "snr_db": snr})
    print(f"{i+1:02d}: len32={n32} len16={n16} match={match} snr={snr}")

json.dump(results, open("det_compare.json", "w"), indent=2)
matches = sum(r["length_match"] for r in results)
print(f"length parity: {matches}/{len(results)}")
