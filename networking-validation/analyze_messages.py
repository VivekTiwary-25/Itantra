"""Builds direct_messages.jsonl + summary stats from the two phones' val.jsonl files.

Latency is only ever computed on ONE phone's clock:
  relayed_ms   = sender: queued -> RELAYED (peer's hop ACK after it accepted the bundle)
  delivered_ms = sender: queued -> DELIVERED (peer's signed receipt verified)
Receiver-side records are joined by bundle ID for integrity (text hash) and duplicates.
"""
import json
import statistics
import sys
from collections import Counter, defaultdict

base = sys.argv[1]
logs = {p: [json.loads(l) for l in open(f"{base}/phone_{p.lower()}_val.jsonl", encoding="utf-8") if l.strip()] for p in "AB"}

rows = []
for sender, receiver in (("A", "B"), ("B", "A")):
    s_ev, r_ev = logs[sender], logs[receiver]
    states = defaultdict(dict)
    for e in s_ev:
        if e["ev"] == "state_change":
            states[e["bundle"]].setdefault(e["state"], e["t_ns"])
    deliveries = defaultdict(list)
    for e in r_ev:
        if e["ev"] == "delivered":
            deliveries[e["bundle"]].append(e)
    for q in (e for e in s_ev if e["ev"] == "queued"):
        b = q["bundle"]
        st = states.get(b, {})
        got = deliveries.get(b, [])
        rows.append({
            "id": q["id"], "direction": f"{sender}->{receiver}", "bundle": b,
            "payload_bytes": q["bytes"], "sender_queued_t_ns": q["t_ns"],
            "relayed_ms": round((st["RELAYED"] - q["t_ns"]) / 1e6, 1) if "RELAYED" in st else None,
            "delivered_ms": round((st["DELIVERED"] - q["t_ns"]) / 1e6, 1) if "DELIVERED" in st else None,
            "receiver_delivered_t_ns": got[0]["t_ns"] if got else None,
            "received": bool(got), "receiver_delivery_events": len(got),
            "integrity_ok": bool(got) and got[0]["text_sha"] == q["text_sha"] and got[0]["bytes"] == q["bytes"],
            "final_sender_state": "DELIVERED" if "DELIVERED" in st else ("RELAYED" if "RELAYED" in st else "QUEUED"),
            "success": bool(got) and "DELIVERED" in st,
        })

with open(f"{base}/direct_messages.jsonl", "w", encoding="utf-8") as out:
    for r in rows:
        out.write(json.dumps(r) + "\n")


def dist(values):
    v = sorted(x for x in values if x is not None)
    if not v:
        return None
    p95 = v[min(len(v) - 1, int(round(0.95 * (len(v) - 1))))]
    return {"n": len(v), "min": v[0], "median": statistics.median(v), "p95": p95, "max": v[-1]}


summary = {}
for run in sorted({r["id"].rsplit("-", 1)[0] for r in rows}):
    rs = [r for r in rows if r["id"].rsplit("-", 1)[0] == run]
    summary[run] = {
        "direction": rs[0]["direction"], "attempts": len(rs),
        "received": sum(r["received"] for r in rs), "delivered_state": sum(r["final_sender_state"] == "DELIVERED" for r in rs),
        "failures": sum(not r["success"] for r in rs), "integrity_ok": sum(r["integrity_ok"] for r in rs),
        "duplicate_deliveries": sum(max(0, r["receiver_delivery_events"] - 1) for r in rs),
        "relayed_ms": dist(r["relayed_ms"] for r in rs), "delivered_ms": dist(r["delivered_ms"] for r in rs),
    }
json.dump(summary, open(f"{base}/message_summary.json", "w", encoding="utf-8"), indent=1)
for run, v in summary.items():
    r, d = v["relayed_ms"], v["delivered_ms"]
    print(f"{run:6s} {v['direction']} attempts={v['attempts']} received={v['received']} DELIVERED={v['delivered_state']} "
          f"fail={v['failures']} integrity={v['integrity_ok']} dups={v['duplicate_deliveries']}")
    print(f"        queued->RELAYED   ms min={r['min']} med={r['median']} p95={r['p95']} max={r['max']}")
    print(f"        queued->DELIVERED ms min={d['min']} med={d['median']} p95={d['p95']} max={d['max']}")
print("rows:", len(rows), "states:", dict(Counter(r["final_sender_state"] for r in rows)))
