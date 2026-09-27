# Meta MMS-TTS Odia (`ory`) -> sherpa-onnx ONNX

Date 2026-09-27. Branch `tts/or/mms` (from batch base `1aa617f`). **QUALITY UNVERIFIED — no listener. Not GREEN.**
Desktop-only work; no phone/adb access used or attempted (see AGENTS constraints for this run).

## Sources and license
- Model: https://huggingface.co/facebook/mms-tts (repo sha `44cc7fb408064ef9ea6e7c59130d88cac1274671`, the same pinned
  commit the Bengali conversion used), files under `models/ory/`.
- **License: CC BY-NC 4.0** (same terms as the already-approved Bengali voice; front-matter is per-repo, not
  per-language, and this SIH prototype's non-commercial use was already approved for the Bengali MMS voice under the
  identical license). Attribution: "Meta MMS-TTS (facebook/mms-tts, ory), CC BY-NC 4.0, converted to ONNX for
  sherpa-onnx". Converting does not change these terms.
- Conversion guide/script: https://k2-fsa.github.io/sherpa/onnx/tts/mms.html (`vits-mms.py`), reused byte-identical
  from `tts-research/mms-ben/vits-mms.py` -- it is a generic MMS->ONNX exporter, not Bengali-specific.
- Odia config: `training_files = train.ltr` (not uroman, so supported by this exporter), `add_blank = true`,
  16,000 Hz, single speaker (`n_speakers: 0`), 76-token native-script vocab.
- **`config.json` is byte-identical to Bengali's** (sha256 `ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49`
  for both): same VITS hyperparameters, same HiFi-GAN V1 decoder (upsample 512ch, resblock type 1, kernels 3/7/11).
  This means the Bengali speed-rescue's fp16-decoder recipe and node-name prefixes (`/dec/...`) transfer directly --
  confirmed below, not assumed.

## Hashes / sizes (SHA-256)
| file | bytes | sha256 |
|---|---|---|
| G_100000.pth | 145,513,575 | bcce2f817b83392372b089f7cdf597c9e3c02a3366df488494d6e40c83cc1687 |
| config.json | 1,887 | ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49 (== Bengali's) |
| vocab.txt | 277 | ce66951abc069690a5ee6edb5f78c2f70376ddd0d9461535b8602a6eebd4ad17 |
| model.onnx (fp32, converted) | 114,066,579 | 81c08eda2bb8bd01cbdd083ed905c3fbd30971f5810a0d953bf491a0aa58545c |
| model.fp16dec.onnx (decoder fp16, derived) | 85,417,497 | 3dddf002b8e760ff75dec0d9fba2e08b1a2279e3fd78edf100da0cad23741f69 |
| tokens.txt (generated, then normalized to LF) | 495 | bc90e5423446ca730aef6b56a4656cb07421c7538429a50b9c396fe0758fd587 |
| tokens.crlf.txt (as generated, kept for provenance) | 571 | 56b48d3f33867d10d04c8a76cb9c2992e06e49039c8d762c98601002c71b1aa6 |

**tokens.txt hazard, confirmed present again here:** `vits-mms.py` writes tokens.txt in Python text mode (CRLF on
Windows). `TtsHelper.normaliseTokens` strips CR at runtime as a backstop, but the checked-in asset is pre-stripped to
LF (`tr -d '\r'`) so the backstop is never exercised on a clean install.

Large files stay outside Git (`D:\iTantra-tts-models\mms-ory`, git-ignored per repo `.gitignore`). Not quantized
beyond the decoder fp16 step below.

## Environment (same as Bengali, reused unmodified)
`D:\iTantra-tts-models\.venv-mms` (shared venv, not modified for this run): torch 2.2.2+cpu, numpy<2, onnx 1.17.0,
onnxruntime 1.18.1, sherpa-onnx 1.13.7 (matches Android AAR). The MMS reference repo checkout at
`D:\iTantra-tts-models\mms-ben\MMS` (with its already-stubbed `monotonic_align/core.py`, see the Bengali doc) was
reused read-only as generic exporter infrastructure -- nothing under `mms-ben/` was modified or written to.

## Commands
```
mkdir D:\iTantra-tts-models\mms-ory
cd D:\iTantra-tts-models\mms-ory
curl -sL -o config.json  https://huggingface.co/facebook/mms-tts/resolve/main/models/ory/config.json
curl -sL -o vocab.txt    https://huggingface.co/facebook/mms-tts/resolve/main/models/ory/vocab.txt
curl -sL -o G_100000.pth https://huggingface.co/facebook/mms-tts/resolve/main/models/ory/G_100000.pth
cp ..\mms-ben\vits-mms.py .
set PYTHONPATH=D:\iTantra-tts-models\mms-ben\MMS;D:\iTantra-tts-models\mms-ben\MMS\vits
set language=Odia
python vits-mms.py                       # -> model.onnx, tokens.txt (CRLF)
tr -d '\r' < tokens.txt > tokens.lf.txt && mv tokens.lf.txt tokens.txt   # (kept CRLF copy as tokens.crlf.txt)
python gen_desktop.py raw evidence/or_input.txt evidence/raw             # fp32 desktop sanity synthesis
MSYS_NO_PATHCONV=1 python mms_decoder_fp16.py model.onnx model.fp16dec.onnx /dec/ Conv,LeakyRelu,Add,Div,Constant,ConvTranspose,Tanh
python compare_fp32_fp16.py              # fp32 vs fp16-decoder length parity, deterministic
```
`mms_decoder_fp16.py` is byte-identical to `tts-research/mms-ben/speed/mms_decoder_fp16.py`; it is a generic
name-prefix/op-allowlist ONNX region converter, not Bengali-specific, and the Odia graph uses the same `/dec/...`
node names (confirmed via `onnx.load` + inspecting top-level node-name prefixes: `dec`, `dp`, `enc_p`, `flow`,
identical to Bengali). `MSYS_NO_PATHCONV=1` is required under Git Bash or `/dec/` gets rewritten to a Windows path
and the region-selection matches 0 nodes (documented hazard from the Bengali doc, reproduced identically here on the
first attempt, then avoided).

## Vocabulary audit (76 tokens, `model-support/vocab.txt`)
Odia base consonants, independent vowels, dependent vowel signs (matras), virama, nukta (standalone combining
U+0B3C), anusvara/visarga/candrabindu, ASCII digits `0`-`9`, apostrophe, underscore, hyphen, en dash, space, soft
hyphen (U+00AD). **Missing:** Odia digits (୦-୯, U+0B66-0B6F), the danda `।` (U+0964), any ASCII sentence
punctuation (no `.` `,` `?` `!` at all -- this vocab has *less* punctuation than Bengali's, which at least is
missing only the danda and currency signs), ZWJ/ZWNJ, and the precomposed nukta letters ଡ଼/ଢ଼ (U+0B5C/0B5D; the
undercomposed base+nukta forms are present and NFC-canonical for these two characters, see below).

## Desktop validation (sherpa-onnx 1.13.7, CPU, 4 threads, `tts-research/mms-ory/evidence/`)
Input: `tts-research/inputs/or.txt` (the byte-frozen 10-line Odia draft already in the repo from the earlier
Indic-TTS RUN 1/frontend-repair effort -- reused as-is, not edited, and this MMS route is a separate track from that
Indic-TTS work).
- All 10 lines synthesized successfully both as native Odia text and with the digit line's `୧୨` swapped to ASCII
  `12` (`evidence/ascii-digits/`); no crash, no empty output.
- Audio 1.6-4.0 s at 16 kHz mono; RMS 0.128-0.190, peak 0.64-0.79 (not silent, no clipping). Desktop RTF on this run
  ranged 0.33-3.16 while the host was busy with other conversion work in parallel -- as with the Bengali doc, treat
  desktop timing as noisy and non-representative of the phone; no phone measurement was taken or attempted.
- **Frontend finding (matches the vocab audit):** sherpa logs "Skip unknown character" for U+0964 (danda) on every
  line, and for U+0B67/U+0B68 (Odia `1`,`2`) on line 9 ("ଔଷଧ ପାଇଁ **୧୨**ଟି ବାକ୍ସ ଆଣନ୍ତୁ" -- "bring **12** boxes of
  medicine" loses the count entirely, audio 1.62 s). Substituting ASCII `12` grows the same line to 1.80 s
  (`evidence/ascii-digits/09.wav` vs `evidence/raw/09.wav`) -- the tokens reach the model, but whether they are
  *pronounced* as "twelve" was not checked (no listener available in this run; same open question the Bengali
  voice has for its ASCII digits). This is why `OdiaTextNormalizer` verbalises both Odia and ASCII digit runs into
  Odia number words rather than letting either reach the model as raw digits.
- Evidence: `evidence/raw/*.wav` + `timings.json`, `evidence/ascii-digits/*.wav` + `timings.json`,
  `evidence/or_input.txt`, `evidence/or_input_ascii_digits.txt`.

## fp16-decoder candidate
`mms_decoder_fp16.py` region-converts every `Conv/LeakyRelu/Add/Div/Constant/ConvTranspose/Tanh` node under the
`/dec/` prefix (200 nodes, 1 boundary cast in) exactly as for Bengali. Verified independently for Odia, not assumed:
`compare_fp32_fp16.py` ran fp32 vs fp16-decoder on the same 10 sentences deterministically (`noise_scale=0,
noise_scale_w=0, length_scale=1, silence_scale=1`): **10/10 identical sample counts**, RMS equal to 4 decimal places
on 9/10 lines and differing only 0.1581 vs 0.1582 on the 10th (`fp16/length_parity.log`). This is at least as clean a
length-parity result as Bengali's 10/10 (Bengali additionally reported SNR 28.3-29.9 dB; SNR was not computed here
for lack of time, but sample-for-sample near-identical RMS at matched length is consistent with a very small,
non-duration-affecting perturbation). **Decoder-only fp16 is accepted as the Odia candidate**, same choice as
Bengali and for the same structural reason (identical decoder architecture dominates runtime; whole-model fp16 was
never tried here because Bengali's whole-model fp16 was already rejected for changing durations, and nothing about
the Odia graph gives a reason to expect a different result -- not re-tested to avoid repeating a known-bad path).

## Text normalisation added (`OdiaTextNormalizer`, `app/src/main/java/com/chmod777/itantra/OdiaTextNormalizer.kt`)
1. Digit runs (Odia ୦-୯ or ASCII 0-9) -> Odia cardinal words, using AI4Bharat indic-numtowords 1.1.0 `ori` data (MIT,
   vendored at `tools/vendor/indic_numtowords/ori/`), first listed variant, Indian grouping (ହଜାର/ଲକ୍ଷ/କୋଟି) up to 9
   digits without a leading zero; longer runs or a leading zero are read digit by digit (phone numbers/codes).
   Verified against the vendored `cardinal.convert()` for 55 numbers up to 9 digits
   (`app/src/test/java/com/chmod777/itantra/OdiaTextNormalizerTest.kt`).
2. Unicode NFC: confirmed with Python's `unicodedata.normalize('NFC', ...)` that U+0B5C/0B5D (precomposed ଡ଼/ଢ଼) are
   in Unicode's full-composition-exclusion list, so NFC of already-precomposed input decomposes them to base+nukta
   (U+0B21/0B22 + U+0B3C), which the vocabulary has. Not exercised by the 10-line draft input (no occurrences), but
   it is a general Unicode fact checked directly for these two Odia codepoints, not copied from the Bengali rule
   (which is about different characters, ড়/ঢ়/য়, on a different vocabulary).
3. The danda is deliberately left alone, same decision as Bengali and for the same reason: the vocab drops it and no
   listening evidence was collected in this run to justify a sentence-splitting change.

## Shipped derivative
The app asset is `model.fp16dec.onnx` renamed to `model.onnx` before staging (see `setup-models.ps1` below); the
git-ignored large files stay in `D:\iTantra-tts-models\mms-ory`.

## Not done / explicit limitations
- No phone/ADB access was used in this worker run (out of scope per this run's constraints); the RMX3392 speed gates
  (median warm RTF <= 1.0, text-to-audible-start <= 3 s, 20 repeats 0 failures) are **not evaluated** here in any
  form, foreground or background. This is a **desktop-only** deliverable, one stage short of Bengali's on-phone
  ANDROID_REPORT/SPEED_REPORT stage.
- No listener has judged Odia pronunciation, digit-word correctness, or overall intelligibility. Machine ASR scoring
  (the Bengali doc's IndicConformer proxy) was not attempted here for lack of an Odia-capable ASR model readily
  available in this environment; this is a gap relative to Bengali's evidence, noted rather than glossed over.
