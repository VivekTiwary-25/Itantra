# Desktop synthesis with sherpa-onnx 1.13.7 (same runtime version as the Android AAR).
# Adapted from tts-research/mms-ben/speed/tools/gen_desktop.py for Odia (mms-ory).
import sherpa_onnx, sys, os, json, time
M = "D:/iTantra-tts-models/mms-ory/"

def engine(model, tokens, data_dir="", ns=0.667, nw=0.8, threads=4):
    return sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=model, tokens=tokens, data_dir=data_dir, noise_scale=ns, noise_scale_w=nw
            ),
            num_threads=threads
        )))

def run(lines, name, e, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    timings = []
    for i, l in enumerate(lines):
        t0 = time.time()
        a = e.generate(l, sid=0, speed=1.0)
        dt = time.time() - t0
        path = f"{out_dir}/{i+1:02d}.wav"
        sherpa_onnx.write_wave(path, a.samples, a.sample_rate)
        dur = len(a.samples) / a.sample_rate
        timings.append({"line": i + 1, "text_len": len(l), "gen_s": dt, "audio_s": dur, "rtf": dt / dur if dur else None, "samples": len(a.samples), "sample_rate": a.sample_rate})
        print(f"{i+1:02d}: gen={dt:.2f}s audio={dur:.2f}s rtf={dt/dur if dur else float('nan'):.2f}", flush=True)
    with open(f"{out_dir}/timings.json", "w", encoding="utf-8") as f:
        json.dump(timings, f, ensure_ascii=False, indent=2)

if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "raw"
    input_file = sys.argv[2] if len(sys.argv) > 2 else "evidence/or_input.txt"
    out_dir = sys.argv[3] if len(sys.argv) > 3 else f"evidence/{which}"
    model = sys.argv[4] if len(sys.argv) > 4 else M + "model.onnx"
    lines = [l.strip() for l in open(input_file, encoding="utf-8") if l.strip()]
    e = engine(model, M + "tokens.txt")
    run(lines, which, e, out_dir)
