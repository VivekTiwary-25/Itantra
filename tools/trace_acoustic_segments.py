"""Record the exact per-sentence IDs used by the Coqui inference path."""

import argparse
import json
from pathlib import Path

import pysbd
from TTS.config import load_config
from TTS.tts.utils.text.tokenizer import TTSTokenizer


parser = argparse.ArgumentParser()
parser.add_argument("--config", type=Path, required=True)
parser.add_argument("--repair-dir", type=Path, required=True)
args = parser.parse_args()
out = args.repair_dir / "acoustic_segments.json"
if out.exists():
    parser.error("segment trace already exists; refusing overwrite")
trace = json.loads((args.repair_dir / "frontend_trace.json").read_text(encoding="utf-8"))
tokenizer, _ = TTSTokenizer.init_from_config(load_config(str(args.config)))
segmenter = pysbd.Segmenter(language="en", clean=True)  # Coqui Synthesizer._get_segmenter("en")
result = []
for row in trace["records"]:
    before = []
    after = []
    for segment in segmenter.segment(row["original_input"]):
        ids = tokenizer.text_to_ids(segment)
        before.append({"text": segment, "codepoints": [f"U+{ord(ch):04X}" for ch in segment],
                       "token_ids": ids, "tokenizer_output": tokenizer.ids_to_text(ids)})
    for segment in segmenter.segment(row["repaired_input_to_model"]):
        ids = tokenizer.text_to_ids(segment)
        decoded = tokenizer.ids_to_text(ids)
        cleaned = tokenizer.text_cleaner(segment)
        assert decoded == cleaned, (row["number"], decoded, cleaned)
        after.append({"text": segment, "codepoints": [f"U+{ord(ch):04X}" for ch in segment],
                      "token_ids": ids, "tokenizer_output": decoded})
    result.append({"number": row["number"], "original_segments": before, "repaired_segments": after})
out.write_text(json.dumps({"language_code": trace["language_code"], "records": result},
                          ensure_ascii=False, indent=2), encoding="utf-8")
print(f"wrote {out} with {len(result)} per-message segment traces")
