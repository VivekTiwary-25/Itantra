# fp32 vs decoder-fp16 length parity, deterministic (noise=0, noise_w=0, silence_scale=1),
# mirroring tts-research/mms-ben/speed/SPEED_REPORT.md section 2/fidelity check.
import sherpa_onnx, sys, math

M = "D:/iTantra-tts-models/mms-ory/"

def engine(model, threads=4):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=model, tokens=M + "tokens.txt", data_dir="",
                noise_scale=0.0, noise_scale_w=0.0, length_scale=1.0
            ),
            num_threads=threads
        ),
        silence_scale=1.0
    ))

lines = [l.strip() for l in open(M + "evidence/or_input.txt", encoding="utf-8") if l.strip()]

e32 = engine(M + "model.onnx")
e16 = engine(M + "model.fp16dec.onnx")

def rms(samples):
    return math.sqrt(sum(s * s for s in samples) / len(samples)) if len(samples) else 0.0

print(f"{'#':<3}{'len32':>8}{'len16':>8}{'match':>7}{'rms32':>9}{'rms16':>9}")
all_match = True
for i, l in enumerate(lines):
    a32 = e32.generate(l, sid=0, speed=1.0)
    a16 = e16.generate(l, sid=0, speed=1.0)
    match = len(a32.samples) == len(a16.samples)
    all_match &= match
    print(f"{i+1:<3}{len(a32.samples):>8}{len(a16.samples):>8}{str(match):>7}{rms(a32.samples):>9.4f}{rms(a16.samples):>9.4f}")

print()
print("ALL LENGTHS MATCH" if all_match else "LENGTH MISMATCH DETECTED")
