import csv, json, sys, unicodedata, re
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8")
repo, out = Path(sys.argv[1]), Path(sys.argv[2])
raw = repo / "docs/sih-metrics/raw"
BLOCK = {"hi": (0x0900, 0x097F), "mr": (0x0900, 0x097F), "gu": (0x0A80, 0x0AFF), "kn": (0x0C80, 0x0CFF), "ml": (0x0D00, 0x0D7F),
         "ta": (0x0B80, 0x0BFF), "te": (0x0C00, 0x0C7F), "or": (0x0B00, 0x0B7F), "bn": (0x0980, 0x09FF), "en": (0x41, 0x7A)}
def norm(t):
    t = unicodedata.normalize("NFC", t).lower().replace("\u200c", "").replace("\u200d", "")
    t = "".join(" " if unicodedata.category(c).startswith("P") else c for c in t)
    return re.sub(r"\s+", " ", t).strip()
def lev(a, b):
    prev = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        cur = [i]
        for j, y in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (x != y)))
        prev = cur
    return prev[-1]
def desk(lang):
    for p in [raw / f"task5b-q2/{lang}-results.csv", raw / f"task5b-indicconformer/{lang}-results.csv", raw / f"task5-wer/{lang}-results.csv"]:
        if p.exists():
            return p.parent.name, {r["wav"].split("/")[-1]: r.get("hypothesis", "") for r in csv.DictReader(open(p, encoding="utf-8"))}
    return None, {}
summary = {}
for lang in ["en", "hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn"]:
    man = {r["wav"].split("/")[-1]: r for r in csv.DictReader(open(repo / f"docs/sih-metrics/raw/task5-wer/dataset/{lang}/manifest.csv", encoding="utf-8"))}
    res = json.load(open(out / f"{lang}-results.json", encoding="utf-8"))
    dsrc, dmap = desk(lang)
    ce = cn = we = wn = same = 0; inscript = total = 0; rows = []
    lo, hi_ = BLOCK[lang]
    for r in res:
        ref = norm(man[r["clip"]]["raw_transcription"]); hyp = norm(r["text"])
        ce += lev(ref, hyp); cn += len(ref)
        we += lev(ref.split(), hyp.split()); wn += len(ref.split())
        letters = [c for c in hyp if c.isalpha()]
        total += len(letters); inscript += sum(lo <= ord(c.upper() if lang == "en" else c) <= hi_ for c in letters)
        same += int(r["text"].strip() == dmap.get(r["clip"], "").strip())
        rows.append({"clip": r["clip"], "cer": lev(ref, hyp) / max(1, len(ref)), "ref": ref, "hyp": hyp, "ms": r["total_ms"], "model_present": r["model_present"]})
    summary[lang] = {"clips": len(res), "model_present": all(r["model_present"] for r in res), "cer": ce / cn, "wer": we / wn,
                     "in_script": inscript / max(1, total), "same_as_desktop": same, "desktop_source": dsrc,
                     "mean_ms": sum(r["total_ms"] for r in res) / len(res), "rows": rows}
    s = summary[lang]
    print(f"{lang}: model_present={s['model_present']} CER={s['cer']:.3f} WER={s['wer']:.3f} script={s['in_script']:.2f} same_as_desktop={same}/{len(res)} ({dsrc}) mean_ms={s['mean_ms']:.0f}")
json.dump(summary, open(out / "summary.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
