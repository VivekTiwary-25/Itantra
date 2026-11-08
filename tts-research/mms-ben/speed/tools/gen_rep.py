import sherpa_onnx, sys, os
M = "D:/iTantra-tts-models/mms-ben/"
lines = [l.strip() for l in open("bn_input.txt", encoding="utf-8") if l.strip()]
e = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=M + "model.onnx", tokens=M + "tokens.txt"), num_threads=4)))
for rep in range(3):
    for mode in ("raw", "split"):
        d = "wav/rep_mms_%s_r%d" % (mode, rep); os.makedirs(d, exist_ok=True)
        for i, l in enumerate(lines):
            t = l if mode == "raw" else l.replace("।", ".")
            a = e.generate(t, sid=0, speed=1.0)
            sherpa_onnx.write_wave("%s/%02d.wav" % (d, i + 1), a.samples, a.sample_rate)
        print(d, flush=True)
