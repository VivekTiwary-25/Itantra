# Meta MMS-TTS Marathi (`mar`) -> sherpa-onnx ONNX

Date 2026-09-27. Branch `tts/mr/mms` (from `1aa617f`). **QUALITY UNVERIFIED — no listener. Not GREEN.**
No physical phone access for this worker; desktop-only evidence below.

## Sources and license
- Model: https://huggingface.co/facebook/mms-tts (repo sha `44cc7fb408064ef9ea6e7c59130d88cac1274671`), files under `models/mar/`.
- **License: CC BY-NC 4.0** (same model repo/card as Bengali; front-matter `license: cc-by-nc-4.0`). Same team approval as Bengali applies: non-commercial only, attribution required:
  "Meta MMS-TTS (facebook/mms-tts, mar), CC BY-NC 4.0, converted to ONNX for sherpa-onnx". Converting does not change these terms.
- Conversion guide: https://k2-fsa.github.io/sherpa/onnx/tts/mms.html (`vits-mms.py` saved verbatim here, identical file to Bengali's).
- MMS inference code: https://huggingface.co/spaces/mms-meta/MMS @ `65d863f41196654aa8b8f3dc586474a4c8f30934` (same commit as Bengali's clone; re-cloned fresh into this worker's model cache).
- Marathi config: `training_files = train.ltr` (not uroman, so supported, same as Bengali), `add_blank = true`, 16,000 Hz, single speaker. `config.json` is byte-identical to Bengali's (sha256 `ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49`) -- same generic MMS VITS hyperparameters, only the checkpoint weights and vocab differ per language.
- 73-symbol native-script (Devanagari) vocab -> 75-line tokens.txt (two symbols, `ʈ` and `ʇ`, get an upper-case duplicate row per `vits-mms.py`'s own logic, same as Bengali's `ʣ`-style rows).

## Hashes / sizes (SHA-256)
| file | bytes | sha256 |
|---|---|---|
| G_100000.pth | 145,511,431 | cf63dbba87cfc9d9133b59b6811781c2973f39a87e301a203176a3a8706ab1f0 |
| config.json | 1,887 | ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49 (identical to Bengali's) |
| vocab.txt | 268 | 845bf1528407e5004c6f42728d6476a5d6df8e8d2ac5c5d375f05480eb573f2f |
| model.onnx (fp32, converted) | 114,064,278 | 0938b7a815dce910aaee9dee72d5b9aabb72d301c11baf28120cb4a5b1f8a517 |
| tokens.txt (generated, then normalized to LF) | 490 | 4d968029d0754b41633cb0871cce6796a5ab3d3bc2b9b91c5721cfdf85156083 |

**tokens.txt hazard (reproduced from Bengali):** `vits-mms.py` writes tokens.txt with Python text mode, i.e. CRLF on Windows. Confirmed for Marathi too (`tokens.crlf.txt`, 565 B, sha256 `df5269b1ca687ce4a33a7f12446cfcb68d3b5e42824735342cf77634ea5a575d`); converted to LF (`tr -d '\r'`) before use, matching `TtsHelper.normaliseTokens`'s runtime backstop.

Large files (checkpoint, fp32 export) stay outside Git (`D:\iTantra-tts-models\mms-mr`). Not quantized (dynamic INT8 was a documented dead end for Bengali's identical decoder architecture; not re-tried here, see `fp16/FP16_DECODER_REPORT.md`).

## Environment
Reused the exact shared venv already set up for Bengali: `D:\iTantra-tts-models\.venv-mms` (torch 2.2.2+cpu, numpy 1.26.4, onnx 1.17.0, onnxruntime 1.18.1, sherpa-onnx 1.13.7, matching the Android AAR). No new environment was built.

## Commands
```
# in D:\iTantra-tts-models\mms-mr, files from models/mar/ downloaded with curl
git clone --depth 1 https://huggingface.co/spaces/mms-meta/MMS MMS   # same commit as Bengali's clone
# monotonic_align: same situation as Bengali -- core.py replaced with the training-only stub, and
# monotonic_align/__init__.py sed'ed `.monotonic_align.core` -> `.core`, since the Cython extension
# was never built in this environment and export never calls maximum_path.
set PYTHONPATH=<dir>\MMS;<dir>\MMS\vits ; set language=Marathi
python vits-mms.py        # -> model.onnx, tokens.txt
python tools/mms_desktop_probe.py --language-code mr --model model.onnx --tokens tokens.txt --input-file mr_input.txt --output-dir evidence/raw --repeat 1
```

## Desktop validation (sherpa-onnx 1.13.7, CPU, 2 threads)
- 10 draft Marathi sentences x1 pass = 10 syntheses, all succeeded. Audio 1.4-3.5 s, 16 kHz mono; RMS 0.157-0.202, peak 0.65-0.91 (not silent, no clipping). See `evidence/raw/*.wav`, `evidence/raw/timings.json`, `evidence/mr_input.txt`.
- Desktop RTF ranged 0.40-4.96 across the ten lines on a shared/busy host (this machine was concurrently running the fp16 conversion and other tooling); Bengali's own conversion doc records the same noise ("desktop RTF ... 0.4-1.4 on reruns while the host was busy"). Desktop timing is not a phone number and is not used for any gate.
- **Frontend findings (character vocab audit, `tokens.txt` has 73 symbols):**
  - No punctuation at all: `.` `,` `?` `!` are absent from the vocab and are dropped (`Skip unknown character`), same category as Bengali's dropped danda. All ten draft lines lost their sentence-final punctuation; commas mid-sentence are also lost.
  - **ASCII digits are only partially supported**: `0 1 2 4 6 7 9` are present, but **`3`, `5`, `8` are individually absent** and are dropped one-by-one wherever they occur (confirmed directly: `evidence/raw/05.wav`'s "5" and `evidence/raw/07.wav`'s "3" and `evidence/raw/08.wav`'s "8" are each reported OOV by the probe). This is a stronger and more specific gap than Bengali's (which had all ASCII digits but mis-spoke them and dropped its own native digits entirely).
  - **Devanagari native digits (०-९, U+0966-096F) are entirely absent** from the vocab (none of the 73 symbols is a digit in that block) -- same category as Bengali's dropped Bengali digits.
  - **Loanword/dialectal letters `ऑ` (U+0911, candra O, used in words like ऑफिस/ऑफलाइन) and the nukta consonants `ड़ ढ़ फ़ ज़` are all OOV**, and -- unlike Bengali's `ড় ঢ় য়` -- Unicode NFC/NFD does **not** help here: confirmed by direct codepoint decomposition test that these Devanagari nukta letters are canonically decomposable (e.g. U+095C DDDHA -> U+0921 + U+093C) but the checkpoint's vocab has **neither** the precomposed form **nor** the bare combining nukta mark U+093C, so both forms are unrepresentable regardless of normalisation. Left unhandled (documented limitation, mirrors Bengali's undhandled danda/currency signs).
  - A harmless case: one of the Marathi cardinal-number words used by the app's own normaliser (74, "चौर्\u200dयाहत्तर") contains an embedded ZWJ (U+200D). Confirmed via direct probe (`fp16/audio/zwj_test/01.wav`) that the ZWJ is silently dropped and nothing else is affected -- ZWJ carries no phonetic content, so this is not a meaning-changing gap, unlike the digit/punctuation drops above.
- Because ASCII digits are only *partially* supported (3 specific digits missing, not "all or none" like Bengali), no digit of either script can be trusted to reach the model unverbalised. `MarathiTextNormalizer` (see `app/src/main/java/com/chmod777/itantra/MarathiTextNormalizer.kt`) verbalises every digit run (Devanagari or ASCII) to Marathi number words via AI4Bharat indic-numtowords 1.1.0 data (MIT, `mar/data/nums.py`), verified against 3,745 sampled values (see the normaliser's own doc comment and `app/src/test/java/com/chmod777/itantra/MarathiTextNormalizerTest.kt`). Punctuation and the loanword letters above are not handled (no evidence-backed fix exists yet; a native listener is needed to judge whether their absence is acceptable).

## Decoder-only FP16 candidate
See `fp16/FP16_DECODER_REPORT.md`. Summary: accepted. Same recipe as Bengali (`/dec/` node-name-prefixed region, same op allow-list); graph node names are identical between the two languages' exports (`/dec`, `/dp`, `/enc_p`, `/flow` top-level scopes), so the script needed no changes. Deterministic length-parity check (noise_scale=0, noise_scale_w=0, length_scale=1) on the same 10 sentences: 9/10 exact sample-count match, 1/10 differs by 16 samples (1 ms) out of 20,323 (0.08%), SNR >=39.8 dB on that one line and >=65.5 dB on the rest -- no evidence of duration-predictor drift (contrast with Bengali's all-Conv fp16, which changed 6/10 sentence lengths and was rejected for that reason).

## Shipped derivative (2026-09-27)
The Android asset is the fp16-decoder model, not the fp32 export: `fp16/mms_decoder_fp16.py` (byte-identical script to Bengali's) converts the decoder to fp16 (85,415,196 B, sha256 `6868c55c1996a005a181a168afff86d4589292450258b822eb7570b3630d16f6`). `MarathiTextNormalizer` verbalises digits in the app. No dynamic INT8 experiment was attempted (Bengali's was a documented, dramatic regression on the same decoder architecture; no reason found here to expect a different result -- see `fp16/FP16_DECODER_REPORT.md` section on rejected alternatives).
