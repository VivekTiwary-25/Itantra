"""Verify the new audio and write a separate 30-row repair measurement CSV."""

import csv
import hashlib
import json
import struct
import wave
from pathlib import Path


ROOT = Path("/mnt/d")
OUT = ROOT / "iTantra-tts-ta-indic" / "FRONTEND_REPAIR_RESULTS.csv"
BASE = ROOT / "iTantra-tts-ml" / "DESKTOP_RESULTS.csv"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


with BASE.open(encoding="utf-8-sig", newline="") as stream:
    original = {(row["language_code"], int(row["number"])): row
                for row in csv.DictReader(stream)}

rows = []
for code in ("ta", "te", "or"):
    candidate = ROOT / f"iTantra-tts-{code}-indic"
    folder = candidate / "local-recordings" / f"{code}-indic" / "repair-20260926"
    trace = json.loads((folder / "frontend_trace.json").read_text(encoding="utf-8"))
    assert len(trace["records"]) == 10
    for record in trace["records"]:
        number = record["number"]
        prior = original[(code, number)]
        assert record["original_input"] == prior["draft_text"]
        original_wav = Path("/mnt/d" + prior["wav_path"][2:].replace("\\", "/"))
        assert sha256(original_wav) == prior["wav_sha256"]
        assert record["error"] is None
        wav_path = folder / f"{number:02d}.wav"
        assert wav_path.exists() and str(wav_path) == record["wav"]
        with wave.open(str(wav_path), "rb") as wav:
            assert wav.getnchannels() == 1
            assert wav.getsampwidth() == 2
            assert wav.getframerate() == 22050
            assert wav.getnframes() > 0
            duration = wav.getnframes() / wav.getframerate()
            payload = wav.readframes(wav.getnframes())
            samples = struct.unpack("<" + "h" * (len(payload) // 2), payload)
            assert any(sample != 0 for sample in samples)
            assert abs(duration - record["audio_seconds"]) < 0.001
        rows.append({
            "language_code": code,
            "number": number,
            "original_input": record["original_input"],
            "frontend_before": record["original_frontend_output"],
            "frontend_after": record["repaired_frontend_output"],
            "original_dropped_codepoints": " ".join(record["original_dropped_codepoints"]),
            "repaired_information_preserved_at_tokenizer": "YES" if not record["repaired_dropped_codepoints"] else "NO",
            "warnings": " | ".join(record["warnings"]),
            "wav_path": str(wav_path).replace("/mnt/d/", "D:\\").replace("/", "\\"),
            "wav_sha256": sha256(wav_path),
            "generation_seconds": record["generation_seconds"],
            "audio_seconds": record["audio_seconds"],
            "remaining_failure": "QUALITY UNVERIFIED; PENDING PHONE; weight terms UNKNOWN; Android runtime unproved",
        })

assert len(rows) == 30
with OUT.open("w", encoding="utf-8", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
print(f"verified {len(rows)} new non-silent PCM WAVs; wrote {OUT}")
