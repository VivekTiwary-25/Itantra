"""Summarise every probe CSV pulled from both phones into fragmentation_results.json.

RTT is measured on the originating phone's own clock (probe sent -> echo decoded).
With echo_payload=true the payload crosses the link twice and success requires the
echoed bytes to be identical. Nothing is excluded; failures are counted per cause.
"""
import csv
import glob
import json
import os
import statistics
import sys

base = sys.argv[1]
runs = []
for phone in ("a", "b"):
    for path in sorted(glob.glob(f"{base}/bench_phone_{phone}/bench/probes_*.csv")):
        rows = list(csv.DictReader(open(path, encoding="utf-8")))
        if not rows:
            continue
        ok = [r for r in rows if r["result"] == "success"]
        rtts = sorted(float(r["rtt_ms"]) for r in ok if r["rtt_ms"])
        fails = {}
        for r in rows:
            if r["result"] != "success":
                fails[r["failure_cause"]] = fails.get(r["failure_cause"], 0) + 1
        payload = int(rows[0]["payload_bytes"])
        echo = rows[0]["echo_payload"] == "true"
        entry = {
            "file": os.path.relpath(path, base).replace("\\", "/"),
            "origin_phone": f"PHONE_{phone.upper()}",
            "transport": rows[0]["transport"],
            "negotiated_mtu": rows[0]["negotiated_mtu"],
            "payload_bytes": payload,
            "echo_payload": echo,
            "link_frame_bytes": int(rows[0]["link_frame_bytes"]),
            "first_hop_fragments_per_frame": rows[0]["first_hop_fragments"],
            "attempts": len(rows),
            "successes": len(ok),
            "failures": fails,
            "phase": ("before 512-byte fragment fix" if any("IllegalArgumentException" in c for c in fails)
                      else "fault-injection trigger (corrupt: no echo expected; replay: echo expected)" if len(rows) == 1 and payload == 64
                      else "after fix"),
        }
        if rtts:
            entry["rtt_ms"] = {
                "min": rtts[0], "median": statistics.median(rtts),
                "p95": rtts[min(len(rtts) - 1, round(0.95 * (len(rtts) - 1)))], "max": rtts[-1],
            }
            if echo and payload >= 2000:
                # Payload bytes moved per round trip (there and back) / median RTT. Includes Noise,
                # CBOR, fragment headers, GATT write/indication acks, and both phones' processing.
                entry["payload_goodput_bytes_per_s_median"] = round(2 * payload / (entry["rtt_ms"]["median"] / 1000))
        runs.append(entry)

normal = [r for r in runs if r["phase"] == "after fix"]
aggregate = {
    "post_fix_non_fault_runs": len(normal),
    "attempts": sum(r["attempts"] for r in normal),
    "successes": sum(r["successes"] for r in normal),
    "failures": sum(r["attempts"] - r["successes"] for r in normal),
    "by_payload": {
        str(p): {"attempts": sum(r["attempts"] for r in normal if r["payload_bytes"] == p),
                 "successes": sum(r["successes"] for r in normal if r["payload_bytes"] == p)}
        for p in sorted({r["payload_bytes"] for r in normal})
    },
    "pre_fix_multi_fragment": {"attempts": sum(r["attempts"] for r in runs if r["phase"].startswith("before")),
                               "successes": sum(r["successes"] for r in runs if r["phase"].startswith("before"))},
}
print("AGGREGATE", json.dumps(aggregate))
json.dump({"method": __doc__.strip(), "aggregate": aggregate, "runs": runs}, open(f"{base}/fragmentation_results.json", "w", encoding="utf-8"), indent=1)
for r in runs:
    rtt = r.get("rtt_ms", {})
    print(f"{r['origin_phone']} {r['payload_bytes']:>6} B frags={r['first_hop_fragments_per_frame']:>3} "
          f"{r['successes']}/{r['attempts']} {r['phase']:28s} med={rtt.get('median', '-')} p95={rtt.get('p95', '-')} "
          f"goodput={r.get('payload_goodput_bytes_per_s_median', '-')} fails={r['failures']}")
