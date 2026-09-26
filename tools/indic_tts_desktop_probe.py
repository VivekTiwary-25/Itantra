"""Run the official Indic-TTS FastPitch + HiFi-GAN stack on ten draft texts.

This is desktop evidence only. The speaker must approve the input and a blind
listener must judge the retained WAVs before any quality decision.
"""

import argparse
import json
import time
import wave
from pathlib import Path

from TTS.utils.synthesizer import Synthesizer


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--language-code", required=True)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--input-file", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--speaker", default="female")
    args = parser.parse_args()
    messages = args.input_file.read_text(encoding="utf-8-sig").splitlines()
    if len(messages) != 10 or any(not message.strip() for message in messages):
        parser.error("input file must contain ten nonblank UTF-8 messages")
    fastpitch = args.model_dir / "fastpitch"
    hifigan = args.model_dir / "hifigan"
    args.output_dir.mkdir(parents=True, exist_ok=True)
    start = time.perf_counter()
    synth = Synthesizer(
        tts_checkpoint=str(fastpitch / "best_model.pth"),
        tts_config_path=str(fastpitch / "config-local.json"),
        vocoder_checkpoint=str(hifigan / "best_model.pth"),
        vocoder_config=str(hifigan / "config.json"),
    )
    load_seconds = time.perf_counter() - start
    records = []
    for number, message in enumerate(messages, 1):
        start = time.perf_counter()
        samples = synth.tts(message, speaker_name=args.speaker)
        generation_seconds = time.perf_counter() - start
        wav_path = args.output_dir / f"{number:02d}.wav"
        synth.save_wav(samples, str(wav_path))
        with wave.open(str(wav_path), "rb") as wav:
            audio_seconds = wav.getnframes() / wav.getframerate()
            sample_rate = wav.getframerate()
        record = {
            "number": number,
            "language_code": args.language_code,
            "text": message,
            "wav": str(wav_path.resolve()),
            "generation_seconds": round(generation_seconds, 3),
            "audio_seconds": round(audio_seconds, 3),
            "sample_rate": sample_rate,
        }
        records.append(record)
        print(json.dumps(record, ensure_ascii=False), flush=True)
    (args.output_dir / "timings.json").write_text(
        json.dumps({"model_dir": str(args.model_dir.resolve()),
                    "load_seconds": round(load_seconds, 3), "records": records},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
