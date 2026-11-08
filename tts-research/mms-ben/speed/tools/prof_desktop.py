import onnxruntime as ort, numpy as np, time, json, sys, collections
from tok import load_tokens, encode
model, tokens, inp, threads = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
t = load_tokens(tokens)
lines = [l for l in open(inp, encoding="utf-8").read().split("\n") if l.strip()]
so = ort.SessionOptions(); so.intra_op_num_threads = threads; so.inter_op_num_threads = 1
so.enable_profiling = True; so.profile_file_prefix = "prof"
s = ort.InferenceSession(model, so, providers=["CPUExecutionProvider"])
tot_audio = tot_t = 0
for l in lines:
    ids = np.array([encode(l, t)], dtype=np.int64)
    feeds = {"x": ids, "x_length": np.array([ids.shape[1]], dtype=np.int64), "noise_scale": np.array([0.667], np.float32),
             "length_scale": np.array([1.0], np.float32), "noise_scale_w": np.array([0.8], np.float32)}
    t0 = time.time(); y = s.run(None, feeds)[0]; dt = time.time() - t0
    dur = y.shape[-1] / 16000; tot_audio += dur; tot_t += dt
    print("len %d audio %.2fs time %.2fs rtf %.2f" % (ids.shape[1], dur, dt, dt / dur))
pf = s.end_profiling()
ev = json.load(open(pf))
by_op = collections.Counter(); by_node = collections.Counter()
for e in ev:
    if e.get("cat") == "Node" and e["name"].endswith("_kernel_time"):
        by_op[e["args"]["op_name"]] += e["dur"]; by_node[e["name"][:-12]] += e["dur"]
T = sum(by_op.values())
print("total rtf %.2f" % (tot_t / tot_audio))
for k, v in by_op.most_common(12): print("%-18s %5.1f%%" % (k, 100 * v / T))
dec = sum(v for k, v in by_node.items() if "/dec/" in k)
print("decoder (dec/) share %.1f%%" % (100 * dec / T))
for k, v in by_node.most_common(8): print("  %5.1f%% %s" % (100 * v / T, k))
