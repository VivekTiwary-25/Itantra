"""Desktop sherpa-onnx probe for a converted Meta MMS-TTS VITS model (character frontend).

Same sherpa-onnx version as the Android AAR (1.13.7). MMS models need no espeak data and
no lexicon; sherpa's "characters" frontend maps each character through tokens.txt.
Characters absent from tokens.txt are reported per line (the reference MMS space also drops them).
"""

import argparse
import json
import time
from pathlib import Path

import sherpa_onnx


def load_token_chars(tokens: Path) -> set:
    chars = set()
    for line in tokens.read_text(encoding="utf-8").splitlines():
        if not line:
            continue
        token = line.rsplit(" ", 1)[0]
        chars.add(token if token else " ")
    return chars


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--language-code", required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--tokens", type=Path, required=True)
    parser.add_argument("--input-file", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--repeat", type=int, default=1)
    args = parser.parse_args()

    messages = [m for m in args.input_file.read_text(encoding="utf-8-sig").splitlines() if m.strip()]
    known = load_token_chars(args.tokens)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=str(args.model), tokens=str(args.tokens)),
            num_threads=2,
        )
    )
    start = time.perf_counter()
    engine = sherpa_onnx.OfflineTts(config)
    load_seconds = time.perf_counter() - start
    records = []
    for rep in range(1, args.repeat + 1):
        for number, message in enumerate(messages, start=1):
            start = time.perf_counter()
            audio = engine.generate(text=message, sid=0, speed=1.0)
            gen = time.perf_counter() - start
            dur = len(audio.samples) / audio.sample_rate
            name = f"{number:02d}.wav" if rep == 1 else f"{number:02d}_r{rep}.wav"
            if not sherpa_onnx.write_wave(str(args.output_dir / name), audio.samples, audio.sample_rate):
                raise RuntimeError(f"could not write {name}")
            oov = sorted({c for c in message if c not in known})
            record = {
                "number": number, "repeat": rep, "language_code": args.language_code, "text": message,
                "codepoints": len(message), "oov_codepoints": [f"U+{ord(c):04X}" for c in oov],
                "wav": name, "generation_seconds": round(gen, 3), "audio_seconds": round(dur, 3),
                "rtf": round(gen / dur, 3) if dur else None, "sample_rate": audio.sample_rate,
                "samples": len(audio.samples),
            }
            records.append(record)
            print(json.dumps(record, ensure_ascii=True), flush=True)
    (args.output_dir / "timings.json").write_text(
        json.dumps({"model": str(args.model.resolve()), "sherpa_onnx": sherpa_onnx.__version__,
                    "load_seconds": round(load_seconds, 3), "records": records}, ensure_ascii=False, indent=2),
        encoding="utf-8")


if __name__ == "__main__":
    main()
