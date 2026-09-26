# Meta MMS-TTS Bengali (`ben`) -> sherpa-onnx ONNX

Date 2026-09-26. **QUALITY UNVERIFIED — no listener. Not GREEN.**

## Sources and license
- Model: https://huggingface.co/facebook/mms-tts (repo sha `44cc7fb408064ef9ea6e7c59130d88cac1274671`), files under `models/ben/`.
- **License: CC BY-NC 4.0** (model card front-matter `license: cc-by-nc-4.0`). Approved by the team for this SIH prototype; non-commercial only, attribution required:
  "Meta MMS-TTS (facebook/mms-tts, ben), CC BY-NC 4.0, converted to ONNX for sherpa-onnx". Converting does not change these terms.
- Conversion guide: https://k2-fsa.github.io/sherpa/onnx/tts/mms.html (`vits-mms.py` saved verbatim here).
- MMS inference code: https://huggingface.co/spaces/mms-meta/MMS @ `65d863f41196654aa8b8f3dc586474a4c8f30934`.
- Bengali config: `training_files = train.ltr` (not uroman, so supported), `add_blank = true`, 16,000 Hz, single speaker, 74-token native-script vocab.

## Hashes / sizes (SHA-256)
| file | bytes | sha256 |
|---|---|---|
| G_100000.pth | 145,512,166 | a8c098eab2e5e378fc52bec57683839bbc641b2241033dab17174f6e37db29a4 (equals HF LFS oid) |
| config.json | 1,887 | ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49 |
| vocab.txt | 268 | 7085f1a1f6040b4da0ac55bb3ff91b77229d1ed14f7d86df2b23676a1a2cb81b |
| model.onnx (fp32, converted) | 114,065,046 | f8f7bf4f0b703f706e0531ff2b364bf9a3ebb8bed69262258f543e0212094e71 |
| tokens.txt (generated) | 554 | dccea18a779700cf02507b18ae5a7c77f382a25802ed671bd9c28fa82635a031 |

Large files stay outside Git (`D:\iTantra-tts-models\mms-ben`). Not quantized. Exported ONNX bytes may differ across torch versions; pin the hash of what was validated.

## Environment (Windows, Python 3.10.x venv)
torch 2.2.2+cpu, numpy<2, onnx 1.17.0, onnxruntime 1.18.1, sherpa-onnx 1.13.7 (matches Android AAR).

## Commands
```
# in D:\iTantra-tts-models\mms-ben, files from models/ben/ downloaded with curl
git clone --depth 1 https://huggingface.co/spaces/mms-meta/MMS MMS
# monotonic_align: doc step builds a Cython training-only extension. Could not build here
# (32-bit MinGW vs 64-bit Python), so core.py is a stub (monotonic_align_core_stub.py) and
# monotonic_align/__init__.py was sed'ed `.monotonic_align.core` -> `.core` as in the guide.
set PYTHONPATH=<dir>\MMS;<dir>\MMS\vits ; set language=Bengali
python vits-mms.py        # -> model.onnx, tokens.txt
python tools/mms_desktop_probe.py --language-code bn --model model.onnx --tokens tokens.txt --input-file bn_input.txt --output-dir raw --repeat 2
```
The stub is safe: `maximum_path` is only used in training; the export path never calls it.

## Desktop validation (sherpa-onnx 1.13.7, CPU, 2 threads)
- 10 draft sentences x2 passes = 20 syntheses, all succeeded. Audio 1.9-3.8 s, 16 kHz mono; RMS 0.15-0.19, peak <=0.94 (not silent, no clipping).
- Desktop warm RTF ~0.33-0.42; model load timing in `evidence/raw/timings.json`. Different lengths all worked (dynamic-length graph, unlike the Indic-TTS acoustic export).
- **Frontend finding:** the vocab has no danda `।` (U+0964) and no Bengali digits `১২`; sherpa logs "Skip unknown character" and drops them
  (the reference MMS space's `filter_oov` does the same). All ten draft lines end in `।`, so sentence-final punctuation is dropped;
  line 9 lost `১২` (message meaning changes; audio 2.35 s). ASCII digits ARE in the vocab: with `12` line 9 grew to 2.81 s.
  Whether ASCII digits are pronounced correctly needs a Bengali listener. No text normalization has been added; decide with a listener.
- Evidence: `evidence/raw/*.wav` (+`_r2`), `evidence/ascii-digits/*.wav`, `timings.json` in each, `bn_input*.txt`.
