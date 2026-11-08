# Indic text-to-speech research

How iTantra's voices were chosen and converted so they run on an ordinary Android phone with no internet.

iTantra reads incoming messages aloud. English, Hindi and Malayalam use Piper voices. The other seven Indic
languages use Meta's MMS-TTS models, which had to be converted to ONNX for sherpa-onnx and then sped up for
phones (for some languages, by running the decoder in fp16).

## What's in here

| Folder | What it is |
|---|---|
| `mms-ben/`, `mms-guj/`, `mms-kan/`, `mms-mr/`, `mms-ory/`, `mms-tam/`, `mms-tel/` | One folder per language: the conversion recipe, hashes, fidelity checks and phone or desktop timings |
| `mms-ben/speed/` | The Bengali speed study that the fp16-decoder approach came from |
| `frontend-repair/` | Fixes for digits and special characters that the Tamil, Telugu and Odia models were silently dropping |
| `evidence/` | The audio samples and traces behind each comparison |
| `results/` | Result tables for the first desktop run and the frontend repair |
| `inputs/` | The test sentences used for each language |
| `original-scripts/` | Scripts that collected the result tables |

The desktop probe scripts these notes refer to live in [`../tools/`](../tools/).

## Status, honestly

Most of the converted voices were checked on a desktop for fidelity against the original model, and some were
timed on a phone. Several still have **no native-listener approval**, and the conversion reports say which at the
top. Treat those voices as working candidates, not finished ones.

## Licences

Meta MMS-TTS models are CC BY-NC 4.0 (non-commercial). Piper voices and sherpa-onnx carry their own licences.
See the root README and [`../tools/THIRD_PARTY.md`](../tools/THIRD_PARTY.md).
