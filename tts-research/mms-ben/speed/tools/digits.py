import sherpa_onnx, os
M = "D:/iTantra-tts-models/mms-ben/"
e = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=M + "model.onnx", tokens=M + "tokens.txt"), num_threads=4)))
tests = {"a_ascii12": "ওষুধের জন্য 12টি বাক্স নিয়ে আসুন।", "b_word12": "ওষুধের জন্য বারোটি বাক্স নিয়ে আসুন।",
         "c_ascii5": "5 জন আহত।", "d_word5": "পাঁচ জন আহত।", "e_ascii250": "250 জন লোক আটকে আছে।", "f_word250": "আড়াইশো জন লোক আটকে আছে।",
         "g_ascii3": "3 নম্বর ঘরে যান।", "h_word3": "তিন নম্বর ঘরে যান।"}
os.makedirs("wav/digits", exist_ok=True)
for k, t in tests.items():
    for r in range(2):
        a = e.generate(t, sid=0, speed=1.0); sherpa_onnx.write_wave("wav/digits/%s_%d.wav" % (k, r), a.samples, a.sample_rate)
