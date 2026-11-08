import json, sys, collections, re
ev = json.load(open(sys.argv[1]))
by_op = collections.Counter(); by_node = collections.Counter(); runs = 0; model_run = 0
for e in ev:
    if e.get("cat") == "Session" and e["name"] == "model_run": runs += 1; model_run += e["dur"]
    if e.get("cat") == "Node" and e["name"].endswith("_kernel_time"):
        by_op[e["args"]["op_name"]] += e["dur"]; by_node[e["name"][:-12]] += e["dur"]
T = sum(by_op.values())
print("runs", runs, "model_run total %.1fs, kernel total %.1fs" % (model_run / 1e6, T / 1e6))
for k, v in by_op.most_common(8): print("  %-16s %5.1f%%" % (k, 100 * v / T))
groups = collections.Counter()
for k, v in by_node.items():
    m = re.match(r"/(dec|flow|enc_p|dp|emb_g)/?(ups\.\d|resblocks\.(\d+)|conv_pre|conv_post)?", k)
    if not m: groups["other"] += v; continue
    if m[1] != "dec": groups[m[1]] += v
    elif m[3] is not None: groups["dec/resblocks stage%d" % (int(m[3]) // 3)] += v
    else: groups["dec/" + (m[2] or "misc")] += v
for k, v in sorted(groups.items(), key=lambda kv: -kv[1]): print("  %-26s %5.1f%%" % (k, 100 * v / T))
