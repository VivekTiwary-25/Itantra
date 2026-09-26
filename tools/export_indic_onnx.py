"""Bounded ONNX export probe for the official Indic-TTS checkpoints.

An export is useful only if ONNX Runtime handles more than the traced sentence.
This script does not claim an Android text frontend or sherpa-onnx integration.
"""

import argparse
import json
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
from TTS.utils.synthesizer import Synthesizer


class Acoustic(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, tokens, speaker_id):
        return self.model.inference(tokens, {"speaker_ids": speaker_id})["model_outputs"]


class Vocoder(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, mel):
        return self.model.inference(mel)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--input-file", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--speaker", default="female")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    fastpitch = args.model_dir / "fastpitch"
    hifigan = args.model_dir / "hifigan"
    messages = args.input_file.read_text(encoding="utf-8-sig").splitlines()
    synth = Synthesizer(
        tts_checkpoint=str(fastpitch / "best_model.pth"),
        tts_config_path=str(fastpitch / "config-local.json"),
        vocoder_checkpoint=str(hifigan / "best_model.pth"),
        vocoder_config=str(hifigan / "config.json"),
    )
    acoustic = Acoustic(synth.tts_model.eval())
    speaker_number = synth.tts_model.speaker_manager.name_to_id[args.speaker]
    speaker_id = torch.tensor([speaker_number], dtype=torch.long)
    tokens = torch.tensor([synth.tts_model.tokenizer.text_to_ids(messages[0])], dtype=torch.long)
    acoustic_path = args.output_dir / "acoustic.onnx"
    start = time.perf_counter()
    torch.onnx.export(
        acoustic, (tokens, speaker_id), str(acoustic_path), opset_version=17,
        input_names=["tokens", "speaker_id"], output_names=["mel"],
        dynamic_axes={"tokens": {1: "text_length"}, "mel": {1: "audio_frames"}},
    )
    print(json.dumps({"stage": "acoustic_export", "seconds": time.perf_counter() - start,
                      "bytes": acoustic_path.stat().st_size}), flush=True)
    session = ort.InferenceSession(str(acoustic_path), providers=["CPUExecutionProvider"])
    for number in (0, 1):
        test_tokens = np.array([synth.tts_model.tokenizer.text_to_ids(messages[number])], dtype=np.int64)
        try:
            result = session.run(None, {"tokens": test_tokens, "speaker_id": np.array([speaker_number], dtype=np.int64)})[0]
            print(json.dumps({"stage": "acoustic_ort", "number": number + 1,
                              "tokens": test_tokens.shape[1], "mel_shape": list(result.shape)}), flush=True)
        except Exception as error:
            print(json.dumps({"stage": "acoustic_ort", "number": number + 1,
                              "tokens": test_tokens.shape[1], "error": str(error)}), flush=True)

    vocoder = Vocoder(synth.vocoder_model.eval())
    vocoder_path = args.output_dir / "vocoder.onnx"
    mel = torch.randn(1, 80, 100)
    start = time.perf_counter()
    torch.onnx.export(
        vocoder, (mel,), str(vocoder_path), opset_version=17,
        input_names=["mel"], output_names=["waveform"],
        dynamic_axes={"mel": {2: "audio_frames"}, "waveform": {2: "audio_samples"}},
    )
    print(json.dumps({"stage": "vocoder_export", "seconds": time.perf_counter() - start,
                      "bytes": vocoder_path.stat().st_size}), flush=True)
    session = ort.InferenceSession(str(vocoder_path), providers=["CPUExecutionProvider"])
    for frames in (100, 120):
        result = session.run(None, {"mel": np.random.randn(1, 80, frames).astype(np.float32)})[0]
        print(json.dumps({"stage": "vocoder_ort", "frames": frames,
                          "waveform_shape": list(result.shape)}), flush=True)


if __name__ == "__main__":
    main()
