"""Audit retained desktop WAVs and write one row per tested message."""

import array
import csv
import hashlib
import json
import wave
from pathlib import Path


ROOT = Path("D:/iTantra-tts-ml")
CANDIDATES = {
    "bn": (Path("D:/iTantra-tts-bn-indic"), "bn-indic", "Indic-TTS"),
    "gu": (Path("D:/iTantra-tts-gu-indic"), "gu-indic", "Indic-TTS"),
    "mr": (Path("D:/iTantra-tts-mr-indic"), "mr-indic", "Indic-TTS"),
    "kn": (Path("D:/iTantra-tts-kn-indic"), "kn-indic", "Indic-TTS"),
    "ml": (ROOT, "ml-arjun", "Piper Arjun"),
    "ta": (Path("D:/iTantra-tts-ta-indic"), "ta-indic", "Indic-TTS"),
    "te": (Path("D:/iTantra-tts-te-indic"), "te-indic", "Indic-TTS"),
    "or": (Path("D:/iTantra-tts-or-indic"), "or-indic", "Indic-TTS"),
}


def main() -> None:
    rows = []
    for code, (worktree, folder, candidate) in CANDIDATES.items():
        output = worktree / "local-recordings" / folder
        records = json.loads((output / "timings.json").read_text(encoding="utf-8"))["records"]
        if len(records) != 10:
            raise ValueError(f"{code}: expected ten timing records, found {len(records)}")
        for number, record in enumerate(records, 1):
            if record["number"] != number or record["language_code"] != code:
                raise ValueError(f"{code}: wrong row at {number}")
            wav_path = output / f"{number:02d}.wav"
            with wave.open(str(wav_path), "rb") as wav:
                if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) != (1, 2, 22050):
                    raise ValueError(f"unexpected PCM format: {wav_path}")
                frames = wav.getnframes()
                samples = array.array("h", wav.readframes(frames))
            if not samples or max(abs(sample) for sample in samples) < 100:
                raise ValueError(f"empty or nearly silent WAV: {wav_path}")
            measured_duration = frames / 22050
            if abs(measured_duration - record["audio_seconds"]) > 0.002:
                raise ValueError(f"duration mismatch: {wav_path}")
            rows.append({
                "language_code": code,
                "candidate": candidate,
                "number": number,
                "draft_text": record["text"],
                "generation_seconds": record["generation_seconds"],
                "audio_seconds": record["audio_seconds"],
                "wav_path": str(wav_path),
                "wav_sha256": hashlib.sha256(wav_path.read_bytes()).hexdigest(),
                "quality": "QUALITY UNVERIFIED — listener needed",
                "phone": "PENDING PHONE",
            })
    destination = ROOT / "DESKTOP_RESULTS.csv"
    with destination.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(f"Validated {len(rows)} PCM WAVs; wrote {destination}")


if __name__ == "__main__":
    main()
