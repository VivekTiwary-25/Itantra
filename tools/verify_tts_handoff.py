"""Verify the transferred evidence using only Python's standard library."""
import array
import csv
import hashlib
import json
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_path(relative):
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT):
        raise ValueError(f"Path outside clone: {relative}")
    return path


def main():
    manifest = json.loads((ROOT / "tts-research/manifests/copied-files.json").read_text(encoding="utf-8"))
    unique = {}
    for record in manifest["records"]:
        path = checked_path(record["path"])
        if path.stat().st_size != record["bytes"] or sha256(path) != record["sha256"]:
            raise ValueError(f"Changed copied source: {record['path']}")
        unique[record["path"]] = record["sha256"]
    count = 0
    for relative, expected in (("tts-research/results/run1.csv", 80),
                               ("tts-research/results/frontend-repair.csv", 30)):
        with (ROOT / relative).open(encoding="utf-8-sig", newline="") as stream:
            rows = list(csv.DictReader(stream))
        if len(rows) != expected:
            raise ValueError(f"Wrong row count in {relative}: {len(rows)}")
        for record in rows:
            path = checked_path(record["wav_path"])
            if sha256(path) != record["wav_sha256"]:
                raise ValueError(f"WAV hash differs: {record['wav_path']}")
            with wave.open(str(path), "rb") as audio:
                if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate()) != (1, 2, 22050):
                    raise ValueError(f"Unexpected WAV format: {path}")
                frames = audio.getnframes()
                samples = array.array("h", audio.readframes(frames))
                if sys.byteorder != "little":
                    samples.byteswap()
            if not samples or max(abs(s) for s in samples) < 100:
                raise ValueError(f"Silent/empty WAV: {path}")
            if abs(frames / 22050 - float(record["audio_seconds"])) > 0.002:
                raise ValueError(f"WAV duration differs: {path}")
            count += 1
    wavs = list((ROOT / "tts-research/evidence").rglob("*.wav"))
    if len(wavs) != 112:
        raise ValueError(f"Expected 112 retained WAVs, found {len(wavs)}")
    for path in (ROOT / "tts-research").rglob("*"):
        if path.is_file() and path.suffix.lower() in {".onnx", ".pth", ".safetensors"}:
            raise ValueError(f"Large model unexpectedly included: {path}")
    print(f"PASS: {len(unique)} unique copied files, {count} candidate audio rows, 112 retained WAVs.")
    print("Transfer integrity only. Audio quality remains unverified; phone gates remain pending.")


if __name__ == "__main__":
    main()
