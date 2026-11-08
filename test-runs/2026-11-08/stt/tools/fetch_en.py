# Stream the FLEURS en_us test tar and keep the first 5 clips that have a transcript; stop early.
import csv, io, sys, tarfile, urllib.request
from pathlib import Path
import soundfile as sf, numpy as np
out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
base = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/en_us/"
tsv = urllib.request.urlopen(base + "test.tsv", timeout=60).read().decode("utf-8").splitlines()
meta = {}
for line in tsv:
    p = line.split("\t")
    meta[p[1]] = (p[0], p[2], p[3])
rows, seen_ids = [], set()
resp = urllib.request.urlopen(base + "audio/test.tar.gz", timeout=60)
with tarfile.open(fileobj=resp, mode="r|gz") as tar:
    for m in tar:
        name = Path(m.name).name
        if not m.isfile() or name not in meta: continue
        fid, raw, norm = meta[name]
        if fid in seen_ids: continue
        raw_bytes = tar.extractfile(m).read(); data, rate = sf.read(io.BytesIO(raw_bytes), dtype="float64"); print(name, sf.info(io.BytesIO(raw_bytes)).subtype, float(np.abs(data).max()))
        if data.ndim > 1: data = data[:, 0]
        assert rate == 16000, rate
        idx = len(rows) + 1
        rel = f"dataset/en/{idx:03d}-{fid}.wav"
        peak = float(np.abs(data).max()); data = (np.clip(data, -1, 1) * 32767).astype(np.int16) if peak <= 1.0 else data.astype(np.int16); sf.write(out / f"{idx:03d}-{fid}.wav", data, 16000, subtype="PCM_16")
        rows.append({"id": fid, "language": "en", "fleurs_config": "en_us", "wav": rel, "transcription": norm,
                     "raw_transcription": raw, "sample_rate": "16000", "channels": "1", "subtype": "PCM_16",
                     "frames": str(len(data)), "duration_seconds": f"{len(data)/16000:.6f}"})
        seen_ids.add(fid)
        print(rel, len(data)/16000, norm[:60])
        if len(rows) >= 5: break
with open(out / "manifest.csv", "w", encoding="utf-8", newline="") as h:
    w = csv.DictWriter(h, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)
