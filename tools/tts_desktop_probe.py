"""Isolated sherpa-onnx TTS probe; no Android or transport dependency.

Use the same sherpa-onnx version as the Android AAR (1.13.7). The input file
contains one UTF-8 message per line. Draft messages require speaker approval
before intelligibility scoring.
"""

import argparse
import json
import time
from pathlib import Path

import sherpa_onnx


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--language-code", required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--tokens", type=Path, required=True)
    parser.add_argument("--data-dir", type=Path, required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input-file", type=Path)
    source.add_argument("--text")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    messages = args.input_file.read_text(encoding="utf-8-sig").splitlines() if args.input_file else [args.text]
    if (args.input_file and len(messages) != 10) or any(not message.strip() for message in messages):
        parser.error("input file must contain ten nonblank UTF-8 lines; --text must be nonblank")
    for path in (args.model, args.tokens, args.data_dir):
        if not path.exists():
            parser.error(f"missing model support path: {path}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(args.model),
                tokens=str(args.tokens),
                data_dir=str(args.data_dir),
            ),
            num_threads=2,
        )
    )
    start = time.perf_counter()
    engine = sherpa_onnx.OfflineTts(config)
    load_seconds = time.perf_counter() - start
    records = []
    for number, message in enumerate(messages, start=1):
        start = time.perf_counter()
        audio = engine.generate(text=message, sid=0, speed=1.0)
        generation_seconds = time.perf_counter() - start
        output = args.output_dir / f"{number:02d}.wav"
        if not sherpa_onnx.write_wave(str(output), audio.samples, audio.sample_rate):
            raise RuntimeError(f"could not write {output}")
        record = {
            "number": number,
            "language_code": args.language_code,
            "text": message,
            "wav": str(output.resolve()),
            "generation_seconds": round(generation_seconds, 3),
            "audio_seconds": round(len(audio.samples) / audio.sample_rate, 3),
            "sample_rate": audio.sample_rate,
            "samples": len(audio.samples),
        }
        records.append(record)
        print(json.dumps(record, ensure_ascii=True), flush=True)
    (args.output_dir / "timings.json").write_text(
        json.dumps({"model": str(args.model.resolve()), "load_seconds": round(load_seconds, 3),
                    "records": records}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
