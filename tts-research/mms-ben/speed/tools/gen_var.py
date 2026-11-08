import sherpa_onnx, sys, os
C = "D:/iTantra-tts-models/candidates/"; M = "D:/iTantra-tts-models/mms-ben/"
lines = [l.strip() for l in open("bn_input.txt", encoding="utf-8") if l.strip()]
norm = {"raw": lambda s: s, "dot": lambda s: s.replace("।", ".")}
def engine(model, tokens, data_dir="", ns=0.667, nw=0.8):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=model, tokens=tokens, data_dir=data_dir, noise_scale=ns, noise_scale_w=nw), num_threads=4)))
name, nk, ns, nw, sids = sys.argv[1], sys.argv[2], float(sys.argv[3]), float(sys.argv[4]), [int(x) for x in sys.argv[5].split(",")]
if name == "mms": e = engine(M + "model.onnx", M + "tokens.txt", "", ns, nw)
else: e = engine(C + "vits-mimic3-bn-multi_low/bn-multi_low.onnx", C + "vits-mimic3-bn-multi_low/tokens.txt", C + "vits-mimic3-bn-multi_low/espeak-ng-data", ns, nw)
for sid in sids:
    d = "wav/%s_%s_ns%s_s%02d" % (name, nk, str(ns).replace(".", ""), sid); os.makedirs(d, exist_ok=True)
    for i, l in enumerate(lines):
        a = e.generate(norm[nk](l), sid=sid, speed=1.0)
        sherpa_onnx.write_wave("%s/%02d.wav" % (d, i + 1), a.samples, a.sample_rate)
    print(d, flush=True)
