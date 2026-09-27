# Desktop synthesis with sherpa-onnx 1.13.7 (same runtime version as the Android AAR).
import sherpa_onnx, sys, os, json, numpy as np
C = "D:/iTantra-tts-models/candidates/"; M = "D:/iTantra-tts-models/mms-ben/"
lines = [l.strip() for l in open("bn_input.txt", encoding="utf-8") if l.strip()]
def engine(model, tokens, data_dir="", ns=0.667, nw=0.8):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=model, tokens=tokens, data_dir=data_dir, noise_scale=ns, noise_scale_w=nw), num_threads=4)))
def run(name, e, sids):
    for sid in sids:
        d = f"wav/{name}" + (f"_s{sid:02d}" if len(sids) > 1 else ""); os.makedirs(d, exist_ok=True)
        for i, l in enumerate(lines):
            a = e.generate(l, sid=sid, speed=1.0)
            sherpa_onnx.write_wave(f"{d}/{i+1:02d}.wav", a.samples, a.sample_rate)
        print("done", d, flush=True)
which = sys.argv[1]
if which == "mimic3":
    e = engine(C + "vits-mimic3-bn-multi_low/bn-multi_low.onnx", C + "vits-mimic3-bn-multi_low/tokens.txt", C + "vits-mimic3-bn-multi_low/espeak-ng-data")
    run("mimic3", e, list(range(16)))
elif which == "mimic3_ns333":
    e = engine(C + "vits-mimic3-bn-multi_low/bn-multi_low.onnx", C + "vits-mimic3-bn-multi_low/tokens.txt", C + "vits-mimic3-bn-multi_low/espeak-ng-data", 0.333, 0.333)
    run("mimic3ns333", e, [int(s) for s in sys.argv[2].split(",")])
elif which == "coqui":
    run("coqui", engine(C + "vits-coqui-bn-custom_female/model.onnx", C + "vits-coqui-bn-custom_female/tokens.txt"), [0])
elif which == "mms":
    run("mms", engine(M + "model.onnx", M + "tokens.txt"), [0])
