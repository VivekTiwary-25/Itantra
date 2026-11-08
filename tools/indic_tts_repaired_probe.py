"""Trace and synthesize the unchanged RUN 1 inputs through the strict frontend."""

import argparse
import json
import time
import wave
from pathlib import Path

from TTS.config import load_config
from TTS.tts.utils.text.tokenizer import TTSTokenizer
from TTS.utils.synthesizer import Synthesizer

from indic_frontend_repair import trace_text


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--language-code", choices=("ta", "te", "or"), required=True)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--input-file", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--speaker", default="female")
    parser.add_argument("--trace-only", action="store_true")
    args = parser.parse_args()
    lines = args.input_file.read_text(encoding="utf-8-sig").splitlines()
    if len(lines) != 10 or any(not line.strip() for line in lines):
        parser.error("input file must contain the original ten nonblank lines")
    if args.output_dir.exists() and any(args.output_dir.iterdir()):
        parser.error("output directory must be new/empty; original artifacts cannot be overwritten")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    config_path = args.model_dir / "fastpitch" / "config-local.json"
    if args.trace_only:
        tokenizer, _ = TTSTokenizer.init_from_config(load_config(str(config_path)))
        synth = None
        load_seconds = None
    else:
        start = time.perf_counter()
        synth = Synthesizer(
            tts_checkpoint=str(args.model_dir / "fastpitch" / "best_model.pth"),
            tts_config_path=str(config_path),
            vocoder_checkpoint=str(args.model_dir / "hifigan" / "best_model.pth"),
            vocoder_config=str(args.model_dir / "hifigan" / "config.json"),
        )
        load_seconds = round(time.perf_counter() - start, 3)
        tokenizer = synth.tts_model.tokenizer

    records = []
    for index, line in enumerate(lines, 1):
        trace = trace_text(line, args.language_code, tokenizer)
        record = {"number": index, **trace, "wav": None,
                  "generation_seconds": None, "audio_seconds": None}
        if synth is not None and trace["error"] is None:
            start = time.perf_counter()
            samples = synth.tts(trace["repaired_input_to_model"], speaker_name=args.speaker)
            record["generation_seconds"] = round(time.perf_counter() - start, 3)
            wav_path = args.output_dir / f"{index:02d}.wav"
            synth.save_wav(samples, str(wav_path))
            with wave.open(str(wav_path), "rb") as wav:
                record["audio_seconds"] = round(wav.getnframes() / wav.getframerate(), 3)
            record["wav"] = str(wav_path.resolve())
        records.append(record)
        print(json.dumps({"number": index, "error": record["error"], "wav": record["wav"]},
                         ensure_ascii=False), flush=True)
    output = {"language_code": args.language_code, "model_dir": str(args.model_dir.resolve()),
              "input_file": str(args.input_file.resolve()), "load_seconds": load_seconds,
              "records": records}
    (args.output_dir / "frontend_trace.json").write_text(
        json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    if any(record["error"] for record in records):
        raise SystemExit("one or more inputs cannot be represented without loss; see frontend_trace.json")


if __name__ == "__main__":
    main()
