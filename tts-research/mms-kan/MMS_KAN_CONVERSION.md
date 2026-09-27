# Meta MMS-TTS Kannada (`kan`) -> sherpa-onnx ONNX

Date 2026-09-27. **QUALITY UNVERIFIED — no listener. Not GREEN.** Branch `tts/kn/mms`, worker worktree
`D:\projects\sih\itantra-tts-workers\kn`. Follows the recipe of `tts-research/mms-ben/MMS_BN_CONVERSION.md`
(Bengali), verified independently for Kannada rather than assumed.

## Sources and license
- Model: https://huggingface.co/facebook/mms-tts (repo sha `44cc7fb408064ef9ea6e7c59130d88cac1274671`), files under `models/kan/`.
- **License: CC BY-NC 4.0** (same repo/model card as Bengali; front-matter `license: cc-by-nc-4.0`). Non-commercial
  only, attribution required: "Meta MMS-TTS (facebook/mms-tts, kan), CC BY-NC 4.0, converted to ONNX for sherpa-onnx".
- Conversion guide/script: same as Bengali, https://k2-fsa.github.io/sherpa/onnx/tts/mms.html (`vits-mms.py`, byte-identical
  copy reused from `tts-research/mms-ben/vits-mms.py`).
- MMS inference code / `vits` package: same shared clone as Bengali, `D:\iTantra-tts-models\mms-ben\MMS` (the
  `monotonic_align` stub applied for Bengali is not language-specific; reused as-is, training-only code path).
- Kannada config: `training_files = train.ltr` (not uroman, so supported), `add_blank = true`, 16,000 Hz, single
  speaker, 75-token native-script vocab. `config.json` is **byte-identical** to Bengali's (same hyperparameters:
  VITS, resblock "1", kernels 3/7/11, upsample 8/8/2/2, upsample_initial_channel 512) -- same architecture family,
  confirmed independently rather than assumed from the Bengali report.

## Hashes / sizes (SHA-256)
| file | bytes | sha256 |
|---|---|---|
| G_100000.pth | 145,513,045 | 0734f8bdade8a250d608b0ce2abcd862a719971ce15eca9211b7dca73cc02125 (equals HF `X-Linked-ETag`, checked against the HF `resolve` redirect headers before download) |
| config.json | 1,887 | ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49 (identical to Bengali's) |
| vocab.txt | 272 | 35d425d34431e0983ea3acf080e88546e7ccfefc9533dbc144010eb7c70e821f |
| model.onnx (fp32, converted) | 114,065,814 | e299a92e5b1a9433f3d96492d1c93ad24a109a702b869e960156ab15acadfe6f |
| tokens.txt (generated, then normalized to LF) | 562 | a4a44037f7492c9e5b69a4483250e54af9a2d528bd4e19de95a21ff0c5e82a8a |

**tokens.txt hazard (same as Bengali):** `vits-mms.py` writes tokens.txt in Python text mode, i.e. CRLF on Windows
(sha `9e4719f47c7a846f2f746abcbdaeb811b245c29dd401ea408526e2abf192f05e`, preserved as `tokens.crlf.txt`). A CRLF
tokens.txt aborts the process in sherpa-onnx on Android (`TtsHelper.normaliseTokens` is the runtime backstop), so
it was converted to LF (`tr -d '\r'`) before use; desktop results below use the LF file.

Large files stay outside Git (`D:\iTantra-tts-models\mms-kan`), matching the Bengali convention. Not quantized.

## Environment
Reused the exact Bengali venv `D:\iTantra-tts-models\.venv-mms` (Python 3.10.11, torch 2.2.2+cpu, numpy 1.26.4,
onnx 1.17.0, onnxruntime 1.18.1, sherpa-onnx 1.13.7 -- matches the Android AAR).

## Commands
```
# D:\iTantra-tts-models\mms-kan, config.json/vocab.txt/G_100000.pth downloaded with curl from
# huggingface.co/facebook/mms-tts/resolve/main/models/kan/
set MSYS_NO_PATHCONV=1
PYTHONPATH=D:/iTantra-tts-models/mms-ben/MMS;D:/iTantra-tts-models/mms-ben/MMS/vits language=Kannada \
  .venv-mms/Scripts/python.exe vits-mms.py        # -> model.onnx, tokens.txt (75 region nodes region unrelated; conversion itself is architecture-identical to Bengali)
python tools/mms_desktop_probe.py --language-code kn --model model.onnx --tokens tokens.txt \
  --input-file evidence/kn_input.txt --output-dir evidence/raw --repeat 2
```
`tools/mms_desktop_probe.py` here is a fresh script (the Bengali one used at conversion time was not committed to
the repo); it reports per-line missing vocabulary codepoints and writes WAV + timing/RMS/peak JSON.

## Desktop validation (sherpa-onnx 1.13.7, CPU, 2 threads, fp32)
- 10 draft sentences x2 passes = 20 syntheses, all succeeded, non-silent (RMS 0.14-0.20, peak 0.77-0.89, no
  clipping), 16 kHz mono. Different lengths all worked (dynamic-length graph). Desktop RTF was noisy (host busy
  during the run, 0.50-6.35), matching the Bengali report's caveat that desktop timing is not a gate; see
  `evidence/raw/timings.json`.
- **Frontend finding (vocabulary audit):** the 75-token vocab has ASCII digits 0-9 and no punctuation at all --
  not just no danda: no `.` (U+002E) either, unlike Bengali which at least had a distinct "danda dropped, ASCII
  digits present" story. Every one of the 10 draft sentences ends in `.`; all had it silently dropped
  ("Skip unknown character" x1-3 per line). Native Kannada digits ೦-೯ (U+0CE6-U+0CEF) are **not** in the vocab and
  are dropped (line 9, "೧೨" -> both digits lost, message meaning changes). ASCII digits ARE accepted by the
  frontend (`evidence/ascii-digits/`, "12" not reported missing) but their pronunciation is unverified by a
  listener, exactly the Bengali situation -- neither may reach the model un-normalised.
- Evidence: `evidence/raw/*.wav` (+`_r2`), `evidence/raw/timings.json`, `evidence/ascii-digits/01.wav`, `kn_input*.txt`.

## Number-word data: an evidence-based deviation from the Bengali recipe
Bengali's normaliser used AI4Bharat indic-numtowords 1.1.0 lookup data directly (flat unit words 0-99 concatenated
with scale words -- no fusion needed in Bengali). Kannada's module in the *same* package (`indic_numtowords.kan`,
already available in `D:\iTantra-tts-models\.venv-mms`) instead *fuses* a hundred with a following multiple of
ten into one word via a numeric-range dispatch, and that fusion is **measurably broken**:
- Calling `kan.cardinal.convert(n)` for every `n` in 100-999 (`tts-research/mms-kan/kan_units0_999.json`) shows
  every `n` of the form (hundred digit)+10/20/30/50/60/70/90 returns a truncated word, e.g. `convert(110)` ->
  `"ನೂ"`, `convert(870)` -> `"ಎಂಟುನೂ"` (should be a complete "eight hundred seventy"-equivalent word). X40 and X80
  happen to fall inside the code's `>30<50` / `>70<90` range checks and are not truncated, which is why the break
  is uneven (7 of 9 per hundred, 63 of 900 three-digit numbers, plus every larger number whose last three digits
  land on one of these). This is a bug in the vendored third-party package, not a Kannada-language fact.
- Separately, `direct_dict["83"]` in the same module is a verbatim duplicate of `direct_dict["82"]`
  (`"ಎಂಬತ್ತೆರಡು"`, "eighty-two") -- i.e. 83 is given the wrong word entirely. Corrected by pattern-matching the
  package's own `X-ಮೂರು` stem+suffix convention used at 23/33/43/63/73/93 (see `KannadaTextNormalizer.kt`).
- Given these, `KannadaTextNormalizer` does **not** port the fusion algorithm. It composes a hundreds group as
  the standalone hundred word (`convert(100)`, `convert(200)`, ..., `convert(900)`, all verified non-truncated)
  plus the separate 1-99 word, space-joined, and joins the ಸಾವಿರ/ಲಕ್ಷ/ಕೋಟಿ scale words the same unfused way (using
  their nominative forms, not the library's genitive ಸಾವಿರದ/ಲಕ್ಷದ, which exist only to support the fusion). This
  is a deliberate, evidence-driven simplification, not a linguistic judgement call -- **a native Kannada listener
  should confirm the unfused reading ("ಇನ್ನೂರು ಎಂಬತ್ತೆಂಟು") is acceptable**; it was chosen only because the fused
  alternative is demonstrably broken in the reference data, not because it was judged more natural.
- Full derivation/verification scripts: `kan_table_gen.log` (0-999 lookup + spot checks), `kan_reference_gen.log`
  / `kan_normalizer_reference.json` (composed reference values used by the JVM test), `kan_dicts_partial.json`
  (raw `higher_dict`/`hundreds_dict`/`exceptions_dict` dump for provenance).

## fp16-decoder candidate
`tts-research/mms-ben/speed/mms_decoder_fp16.py` (unmodified, reused as-is) converts the HiFi-GAN decoder region
(`/dec/`, same op-type allowlist as Bengali) to fp16:
| file | bytes | sha256 |
|---|---|---|
| fp32 export (input) | 114,065,814 | e299a92e5b1a9433f3d96492d1c93ad24a109a702b869e960156ab15acadfe6f |
| fp16-decoder candidate | 85,416,732 | e38700a8ff860fc1371526d555dbdd06ab7d91c4e4cfa297dfb96cdfcb4cd492 |

Region node count (200) is identical to Bengali's, consistent with the identical architecture. Deterministic
fp32-vs-fp16 length-parity check (noise=0, noise_scale_w=0, length_scale=1, silence_scale=1, the same 10
sentences as the fp32 probe, `tools/compare_fp32_fp16.py`, results in `speed/fp32_vs_fp16_det.json`):

**10/10 sentences length-matched, SNR 63.0-69.8 dB (all higher than Bengali's 28.3-29.9 dB range).** PASS --
this was verified independently for Kannada, not assumed from the Bengali result. The fp16-decoder model is
shipped as the app asset (`app/src/main/assets/vits-mms-kan/model.onnx`, same file name convention as Bengali).

## Shipped derivative (2026-09-27)
The app asset is the fp16-decoder model, not the fp32 export. Digits (native and ASCII) are normalised in the
app (`KannadaTextNormalizer`); punctuation (all of it, not just the danda) is still dropped -- no listener
evidence yet justifies inventing a punctuation-to-pause mapping. See the top-level worker report for desktop
in-app-equivalent listening evidence (`evidence/shipped_fp16_normalized/`) and phone-benchmark readiness.
