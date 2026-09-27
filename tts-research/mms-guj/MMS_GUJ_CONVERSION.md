# Meta MMS-TTS Gujarati (`guj`) -> sherpa-onnx ONNX

Date 2026-09-27. Branch `tts/gu/mms` (from `1aa617f`, `research/bn-tts-speed` tip).
**QUALITY UNVERIFIED — no native listener. Not GREEN.** Recipe reused from
`tts-research/mms-ben/MMS_BN_CONVERSION.md` and `tts-research/mms-ben/speed/SPEED_REPORT.md`
(the Bengali golden reference); every claim below was independently re-checked for Gujarati,
not assumed.

## Sources and license
- Model: https://huggingface.co/facebook/mms-tts, files under `models/guj/`.
- **License: CC BY-NC 4.0** (same as the whole facebook/mms-tts collection; the model repo has no
  per-language override). Non-commercial only, attribution required:
  "Meta MMS-TTS (facebook/mms-tts, guj), CC BY-NC 4.0, converted to ONNX for sherpa-onnx". Converting
  does not change these terms.
- Conversion guide/script: identical `vits-mms.py` from https://k2-fsa.github.io/sherpa/onnx/tts/mms.html,
  saved verbatim here (same file already used for Bengali).
- MMS inference code / `vits` package / `monotonic_align` Cython stub: reused byte-identical from the
  shared `D:\iTantra-tts-models\mms-ben\MMS` checkout (same upstream commit, same stub rationale:
  `maximum_path` is training-only and never called during export/inference).
- Gujarati config: `training_files = train.ltr` (not uroman, so native script is directly supported,
  same as Bengali), `add_blank = true`, 16,000 Hz, single speaker. `config.json` is **byte-identical**
  to Bengali's (same hyperparameters for every MMS language in this family: VITS + HiFi-GAN V1 decoder,
  `resblock "1"`, kernels 3/7/11, `upsample_initial_channel 512`) — confirmed by matching SHA-256
  (`ec453d50...`). This means Gujarati has the same heavy decoder as Bengali, so the same fp16-decoder
  speed rescue was expected to transfer, and was verified to (see below), not assumed.

## Hashes / sizes (SHA-256)
| file | bytes | sha256 |
|---|---|---|
| G_100000.pth | 145,501,427 | 427ac3c74f61be494b389cae7d771311d0bcf576f4e2f1b22f257539e26e323a |
| config.json | 1,887 | ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49 (same as Bengali) |
| vocab.txt | 232 | 611d4c5d7ba4bce727c1154277aea43df7a534e22e523877d1885a36727d63c3 |
| model.onnx (fp32, converted) | 114,054,295 | 593b73e7fd485d28bd12454510ba8840487044e57291ea65002194a4aa176cba |
| tokens.txt (generated, then normalized to LF) | 402 | (see `model-support/tokens.txt`) |
| **model.fp16dec.onnx (shipped app asset)** | **85,405,213** | **3ce0a3206080915f6541c00bcd26df1f2817d118d6e324fda62011d679583601** |

**tokens.txt hazard, same as Bengali:** `vits-mms.py` writes tokens.txt in Python text mode, so it comes
out CRLF on Windows. `TtsHelper.normaliseTokens` strips CR as a runtime backstop, but the checked-in
`model-support/tokens.txt` here and the app asset were both converted to LF (`tr -d '\r'`) before use.

Large files stay outside Git (`D:\iTantra-tts-models\mms-guj`, shared `.venv-mms` at
`D:\iTantra-tts-models\.venv-mms`). Exported ONNX bytes may differ across torch versions; pin the hash
of what was validated.

## Environment
Same shared Windows Python 3.10.11 venv as Bengali: `D:\iTantra-tts-models\.venv-mms`
(torch 2.2.2+cpu, onnx 1.17.0, onnxruntime 1.18.1, sherpa-onnx 1.13.7 — matches the Android AAR).
No new environment was created; the venv already had every package this conversion needed.

## Commands
```
# D:\iTantra-tts-models\mms-guj, guj files downloaded from HF with curl
cp -r D:\iTantra-tts-models\mms-ben\MMS .          # shared MMS checkout + monotonic_align stub
export PYTHONPATH=D:\iTantra-tts-models\mms-guj\MMS;D:\iTantra-tts-models\mms-guj\MMS\vits
export language=Gujarati
python vits-mms.py                                  # -> model.onnx, tokens.txt (CRLF)
tr -d '\r' < tokens.txt > tokens.lf.txt && mv tokens.lf.txt tokens.txt
python gen_gu_desktop.py evidence/gu_input.txt model.onnx tokens.txt evidence/raw
```

## Vocabulary / frontend inspection (done before any synthesis was trusted)
`vocab.txt` has **60 tokens**: native Gujarati letters, matras (vowel signs), virama, anusvara,
visarga, avagraha-less vocalic-R sign, apostrophe, hyphen, space. Critically, **unlike Bengali**,
the Gujarati checkpoint's vocabulary has **no digit characters of any kind** — neither Gujarati
૦-૯ nor ASCII 0-9 — and **no punctuation** beyond apostrophe/hyphen (no danda, no currency signs).
It also lacks the candra vowel signs ૅ/ૉ (used in some loanword spellings, e.g. ડૉક્ટર "doctor").

This was verified empirically, not just by reading `vocab.txt`:
- `evidence/raw/*.wav` (fp32, raw draft text): sherpa logs `Skip unknown character` for every ASCII
  period `.` (sentence-final, all ten lines) and for `\U+0AC9` (ૉ, in an early draft of the doctor
  sentence) and for `\U+0AE7`/`\U+0AE8` (Gujarati ૧/૨ in "૧૨ બોક્સ" = "12 boxes").
- `evidence/ascii-digits/01.wav`: the same sentence with ASCII `12` instead of Gujarati ૧૨ still logs
  `Skip unknown character` for `1` and `2` — confirming ASCII digits are **also** dropped here (the
  opposite of Bengali, where ASCII digits are in-vocabulary but mis-pronounced). This is why the
  Gujarati normalizer converts both digit systems, unconditionally, rather than mapping one to the
  other.
- The doctor sentence was respelled ડોક્ટર (longો, in-vocabulary) instead of ડૉક્ટર (candra ૉ,
  out-of-vocabulary) for the retained evidence set — a legitimate alternate spelling, not a
  workaround — and the candra-vowel gap is left undocumented-but-unfixed, same policy as Bengali
  leaving the danda dropped: one word in one draft sentence is not enough evidence to justify adding
  a general candra-vowel rule, and no such rule was added.

## Desktop validation (sherpa-onnx 1.13.7, CPU, 4 threads)
- fp32, raw 10-line draft (`evidence/gu_input.txt`, `evidence/raw/`): all ten syntheses succeeded,
  non-silent, 1.9-3.8 s audio at 16 kHz mono. Desktop RTF was noisy (0.2-3.6 across lines on a busy
  host during this run) — **desktop timing only, not a device number**, exactly the caveat
  `SPEED_REPORT.md` gives for Bengali's desktop runs.
- fp32, digit-normalized version of the same 10 lines (`evidence/gu_input_normalized.txt`,
  `evidence/fp32_normalized/`): same non-silence/duration sanity, line 9's meaning is now preserved
  (see normalization section).
- Different lengths all worked (dynamic-length graph via the exported dynamic axes, same as Bengali;
  not the fixed-length Indic-TTS acoustic graph that failed in RUN 1).

## Text normalization: `GujaratiTextNormalizer` (justified by the above evidence)
Digit runs (Gujarati ૦-૯ or ASCII 0-9) -> Gujarati cardinal words, using AI4Bharat
`indic-numtowords` 1.1.0 (MIT), `lang="gu"` (its ISO 639-1 code; the package does not use `guj`).
Runs of up to 9 digits without a leading zero are read as one number with Indian grouping
(હજાર/thousand, લાખ/lakh, કરોડ/crore); longer runs or a leading zero are read digit-by-digit —
same policy shape as `BengaliTextNormalizer`, but every word table (`UNITS`, `HUNDREDS`, `SCALES`)
was regenerated from the Gujarati-language output of the package, not copied from Bengali's Kotlin
arrays. All words the algorithm can produce for 0-999,999,999 were checked programmatically against
`vocab.txt` before shipping: zero missing characters. Then Unicode NFC (no observed precomposed-form
gap analogous to Bengali's ড়/ঢ়/য়; Gujarati has no equivalent nukta letters in this vocabulary, so NFC
here is a defensive no-op, not a fix for an observed problem).

Verified: `દવા માટે ૧૨ બોક્સ લઈ આવો` -> `... બાર બોક્સ ...` ("12 boxes" -> "twelve boxes", meaning
preserved) instead of silently losing the count. JVM test `GujaratiTextNormalizerTest` checks 75
numbers (0 to 999,999,999, including every scale boundary) against the Python package's own output,
plus digit-run/leading-zero/NFC-no-op cases — same shape and coverage as `BengaliTextNormalizerTest`.

Not handled (documented, not fixed, same as Bengali's unhandled list): sentence-final period/danda
(dropped; the vocabulary simply has no punctuation), commas or decimal points inside numbers,
ordinals, dates/phone-number context, candra vowel signs.

## Decoder-only fp16 candidate
`tts-research/mms-guj/mms_decoder_fp16.py` (identical script to Bengali's, generic and
graph-topology-driven, not Gujarati-specific) converts only the HiFi-GAN decoder region:
```
python mms_decoder_fp16.py model.onnx model.fp16dec.onnx /dec/ Conv,LeakyRelu,Add,Div,Constant,ConvTranspose,Tanh
```
fp32 in `593b73e7...76cba` (114,054,295 B) -> fp16-decoder out `3ce0a320...83601` (85,405,213 B).
`region nodes: 200, boundary casts in: 1` — same order of magnitude as Bengali's decoder-only region,
consistent with the identical architecture.

**Length-parity / fidelity check** (`det_compare.py`, deterministic: `noise_scale=0`,
`noise_scale_w=0`, on the digit-normalized 10-line set, matching the Bengali methodology exactly):

| line | len32 (samples) | len16 (samples) | match | SNR dB |
|---|---|---|---|---|
| 01 | 57698 | 57698 | yes | 67.9 |
| 02 | 40290 | 40290 | yes | 67.5 |
| 03 | 37218 | 37218 | yes | 67.3 |
| 04 | 46434 | 46434 | yes | 69.7 |
| 05 | 58040 | 58040 | yes | 66.5 |
| 06 | 52915 | 52915 | yes | 66.5 |
| 07 | 30306 | 30306 | yes | 66.4 |
| 08 | 31330 | 31330 | yes | 68.0 |
| 09 | 31842 | 31842 | yes | 69.2 |
| 10 | 43514 | 43514 | yes | 69.1 |

**10/10 length parity, SNR 66.4-69.7 dB** (Bengali's decoder-fp16 check was 10/10 parity at 28.3-29.9 dB
SNR; Gujarati's numerical agreement with fp32 is even tighter here, though SNR is not directly
comparable across two different waveforms/checkpoints — parity is the load-bearing number).
**Accepted as the shipped candidate**, same conclusion as Bengali, independently re-verified rather
than assumed. Whole-model fp16 was not attempted: Bengali's report already demonstrated it damages
predicted durations for this exact architecture (`resblock "1"`, same config.json), and nothing here
gave a reason to expect Gujarati's duration predictor to behave differently, so re-running a known-bad
experiment was skipped per the error policy ("don't blindly repeat a rejected optimization without a
specific reason to believe the graph differs materially") — it does not, byte-for-byte in config.json.

Dynamic INT8 was not attempted for the same reason: Bengali's report found it dramatically slower with
no architecture-specific reason to expect otherwise here (same Conv/ConvTranspose-dominated decoder).

## What was NOT done here (explicitly out of scope for this worker)
- No phone/ADB access; no on-device RTF, text-to-audible-start, or memory numbers. All timing above is
  desktop-only and explicitly labelled as such.
- No native Gujarati listener; audio quality, naturalness, and whether "બાર" for the digit run reads
  naturally in context are unverified. Machine screening was not even attempted (no Gujarati ASR model
  was set up) — this is stated rather than silently skipped.
- `TtsHelper.kt` changes are limited to adding the `"gu"` voice entry; no other language's entry, and
  no shared plumbing (`installAssets`, `normaliseTokens`, playback) was touched.
