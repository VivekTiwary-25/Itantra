# Desktop fp32 sanity synthesis for Gujarati MMS, mirroring tts-research/mms-ben/speed/tools/gen_desktop.py.
import sherpa_onnx, sys, os, time, json

M = "D:/iTantra-tts-models/mms-guj/"
lines = [l.strip() for l in open(sys.argv[1] if len(sys.argv) > 1 else "gu_input.txt", encoding="utf-8") if l.strip()]
model = sys.argv[2] if len(sys.argv) > 2 else M + "model.onnx"
tokens = sys.argv[3] if len(sys.argv) > 3 else M + "tokens.txt"
outdir = sys.argv[4] if len(sys.argv) > 4 else "raw"

e = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
    model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=model, tokens=tokens, data_dir="", noise_scale=0.667, noise_scale_w=0.8),
        num_threads=4)))

os.makedirs(outdir, exist_ok=True)
timings = []
for i, l in enumerate(lines):
    t0 = time.time()
    a = e.generate(l, sid=0, speed=1.0)
    dt = time.time() - t0
    dur = len(a.samples) / a.sample_rate
    sherpa_onnx.write_wave(f"{outdir}/{i+1:02d}.wav", a.samples, a.sample_rate)
    timings.append({"line": i + 1, "text_len": len(l), "synth_s": dt, "audio_s": dur, "rtf": dt / dur if dur else None})
    print(f"{i+1:02d}: synth={dt:.2f}s audio={dur:.2f}s rtf={dt/dur:.2f}")

json.dump(timings, open(f"{outdir}/timings.json", "w"), indent=2)
print("done", outdir)
