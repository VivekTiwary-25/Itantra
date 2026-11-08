import sherpa_onnx, sys

M = r"D:\iTantra-tts-models\mms-tel"
lines = [l.strip() for l in open(M + r"\evidence\te_input_normalized.txt", encoding="utf-8") if l.strip()]


def engine(model):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=model, tokens=M + r"\tokens.txt",
                noise_scale=0.0, noise_scale_w=0.0, length_scale=1.0,
            ),
            num_threads=2,
        )
    ))


e32 = engine(M + r"\model.onnx")
e16 = engine(M + r"\fp16\model.fp16dec.onnx")

print("line | fp32 samples | fp16dec samples | match")
all_match = True
for i, l in enumerate(lines, start=1):
    a32 = e32.generate(text=l, sid=0, speed=1.0)
    a16 = e16.generate(text=l, sid=0, speed=1.0)
    n32, n16 = len(a32.samples), len(a16.samples)
    match = n32 == n16
    all_match &= match
    sherpa_onnx.write_wave(M + r"\evidence\det_fp32_%02d.wav" % i, a32.samples, a32.sample_rate)
    sherpa_onnx.write_wave(M + r"\evidence\det_fp16dec_%02d.wav" % i, a16.samples, a16.sample_rate)
    print(i, n32, n16, match)

print("ALL MATCH" if all_match else "MISMATCH FOUND")
