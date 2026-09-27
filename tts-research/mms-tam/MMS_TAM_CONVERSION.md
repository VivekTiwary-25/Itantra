# Meta MMS-TTS Tamil (`tam`) -> sherpa-onnx ONNX

Date 2026-09-27. Branch `tts/ta/mms` (from `1aa617f`). **QUALITY UNVERIFIED — no native Tamil listener. Not GREEN.**
Machine (ASR/OOV) screening below is not a substitute for a listener.

## Sources and license
- Model: https://huggingface.co/facebook/mms-tts (repo sha `44cc7fb408064ef9ea6e7c59130d88cac1274671`), files under `models/tam/`.
- **License: CC BY-NC 4.0** (same model card as every other MMS language in this family). Already approved for
  this SIH prototype by the Bengali precedent; non-commercial only, attribution required:
  "Meta MMS-TTS (facebook/mms-tts, tam), CC BY-NC 4.0, converted to ONNX for sherpa-onnx".
- Conversion guide/script: identical to Bengali, https://k2-fsa.github.io/sherpa/onnx/tts/mms.html (`vits-mms.py`,
  byte-for-byte the same file, saved here for provenance).
- Tamil config: `training_files = train.ltr` (not uroman, so supported directly), `add_blank = true`, 16,000 Hz,
  single speaker, 58-token native-script vocabulary (`vocab.txt`) plus one duplicate uppercase entry that
  `vits-mms.py` adds for the single ASCII letter in vocab ('a'/'A'), so `tokens.txt` has 59 lines.

## Hashes / sizes (SHA-256)
| file | bytes | sha256 |
|---|---|---|
| G_100000.pth | 145,499,887 | cbade8e2c8442db96515d30edadebf532b63eb595e4da5ffb60cc233dd2896e1 |
| config.json | 1,887 | ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49 (byte-identical to Bengali's — same VITS/HiFi-GAN V1 hyperparameters across these single-speaker MMS checkpoints) |
| vocab.txt | 206 | 14d1d61a16b99762164fab2847ce35acf037719795b6a2d47596cb7a153e75a7 |
| model.onnx (fp32, converted) | 114,052,756 | 73ea559ceff1e4d85a4bfaaa75cfff8b4a94488f304eba6b942eeea2ac824c70 |
| tokens.txt (generated, then normalized to LF) | 375 | 0b3f692319bb5fae8658e2f84bf252bca92450d0207bbba7273caa1a182d81b8 |
| **fp16-decoder model.onnx (shipped asset)** | **85,403,674** | **f6c0a040c6332537b55f0622a3c24d417892fda139ec45bdd73e822ba0cb21c3** |

**tokens.txt hazard, reproduced from Bengali:** `vits-mms.py` writes tokens.txt in Python text mode, i.e. CRLF
on Windows (434 B, `tokens.crlf.txt` kept for provenance). `TtsHelper.normaliseTokens` strips CR as a runtime
backstop, but the committed asset is already LF (`tr -d "\r"`), exactly like Bengali.

Large files stay outside Git (`D:\iTantra-tts-models\mms-tam`), matching the Bengali convention. Not quantized
with dynamic INT8 (see "What was not repeated" below).

## Environment
Same shared venv as Bengali: `D:\iTantra-tts-models\.venv-mms` (torch 2.2.2+cpu, numpy<2, onnx 1.17.0,
onnxruntime 1.18.1, sherpa-onnx 1.13.7, matching the Android AAR). The MMS reference repo clone
(`D:\iTantra-tts-models\mms-ben\MMS`) is generic VITS code, not Bengali-specific, and was reused unmodified
(including the training-only `monotonic_align` Cython stub, which the export path never calls).

## Commands
```
# in D:\iTantra-tts-models\mms-tam, files from models/tam/ downloaded with curl
export PYTHONPATH="D:/iTantra-tts-models/mms-ben/MMS;D:/iTantra-tts-models/mms-ben/MMS/vits"
export language=Tamil
python vits-mms.py                       # -> model.onnx, tokens.txt (CRLF)
tr -d '\r' < tokens.crlf.txt > tokens.txt
python tools/mms_desktop_probe.py --language-code ta --model model.onnx --tokens tokens.txt \
    --input-file evidence/ta_input.txt --output-dir evidence/raw --repeat 2
```
Export succeeded on the first attempt with the same script and environment as Bengali; no architecture
differences from Bengali were found (same VITS class, same HiFi-GAN V1 decoder shape per `config.json`).

## Desktop validation (sherpa-onnx 1.13.7, CPU, 2 threads, fp32)
- 10 draft sentences (disaster/relay domain, same style as the Bengali draft set) x2 passes = 20 syntheses,
  all succeeded, non-silent, no clipping. Audio 1.4-5.4 s at 16 kHz mono. Evidence: `evidence/raw/*.wav`
  (+`_r2`), `evidence/raw/timings.json`, `evidence/ta_input.txt`.
- Desktop RTF was noisy (0.4-6.5x across runs on a busy host, matching the Bengali report's warning that
  desktop timing is not meaningful signal); no on-device timing was attempted or is claimed here.
- **Frontend findings (the vocabulary silently drops anything absent from it, confirmed against `tokens.txt`
  and desktop probes, not merely inferred):**
  1. **Tamil digits (௦-௯, U+0BE6-0BEF) are entirely absent from the vocabulary.** Probed `evidence/ta_input_tamil_digits.txt`
     (line 9 with `௧௨` in place of ASCII `12`): both codepoints reported `Skip unknown character` and dropped.
     Evidence: `evidence/frontend-gaps/tamil-digits/*.wav`.
  2. **ASCII digit '8' is also entirely absent** — not merely mis-pronounced like Bengali's ASCII digits, but
     completely out-of-vocabulary. Confirmed two ways: `tokens.txt` has no `8` line, and a standalone probe of
     `"8 பெட்டிகள் உள்ளன."` reported `U+0038` as OOV. The other ASCII digits (0,1,2,3,4,5,6,7,9) ARE in the
     vocabulary, but there is no evidence they are pronounced as digits rather than as stray Latin-looking
     glyphs (the same open question Bengali had for its in-vocabulary ASCII digits). Evidence:
     `evidence/frontend-gaps/digit8/01.wav`.
  3. **The aytham (ஃ, U+0B83) is absent from the vocabulary** and silently dropped (probed with "ஃபோன்
     எண்ணை அனுப்புங்கள்." and two more aytham sentences; all three report `U+0B83` as OOV alongside the
     expected trailing-period OOV). Evidence: `evidence/frontend-gaps/aytham/*.wav`, `evidence/ta_input_aytham.txt`.
  4. **The full stop `.` (U+002E) is absent from the vocabulary and silently dropped**, the same situation as
     Bengali's danda: every one of the 10 draft sentences ends in `.` and it disappears from the audio. No fix
     attempted (mirrors the Bengali decision that dropping sentence-final punctuation, rather than splitting on
     it, measured no worse and is simpler).
  5. **Decomposed two-part vowel signs are a real risk, found by inspection, not (yet) heard.** The three
     precomposed Tamil vowel-sign codepoints ொ/ோ/ௌ (U+0BCA/0BCB/0BCC) are each a single token in the
     vocabulary, but each also has a Unicode canonical decomposition to two codepoints that are BOTH
     individually present in the vocabulary (e.g. ொ = ெ + ா, U+0BC6 + U+0BBE). Verified with Python
     `unicodedata`: `NFD(ொ) = U+0BC6,U+0BBE`. Text containing the decomposed form (some older content/typing
     methods produce it) would silently tokenize as two separate vowel-sign characters instead of the one the
     model was trained on, instead of being dropped outright. NFC normalisation fixes this; see below.

## Text normalisation added (`TamilTextNormalizer`, `ta` only)
Digit runs (Tamil ௦-௯ or ASCII 0-9) -> Tamil cardinal words, using AI4Bharat indic-numtowords 1.1.0
(`tools/vendor/indic_numtowords/tam/data/nums.py`, MIT, first listed variant), then Unicode NFC.
This directly addresses findings 1, 2 and 5 above (Tamil digits and ASCII '8' would otherwise be silently
lost or garbled; decomposed vowel signs are folded to the trained precomposed form). Findings 3 (aytham) and
4 (full stop) are left unhandled: both are rare enough in short relay/location/count messages that no
meaning-preserving substitute was justified without a listener, mirroring the Bengali danda decision.

Tamil's numeral system needed one structural difference from Bengali's normalizer: hundreds have a distinct
"connecting" word when a remainder follows (நூற்று எட்டு = 108, `hundreds_dict`) versus the standalone exact
form (நூறு = 100, `exceptions_dict`); Bengali's HUNDREDS array only needed the standalone form for either
case. Both Tamil forms are reproduced verbatim from the vendored AI4Bharat data (first variant each), with one
mechanical cleanup: `nums.py`'s `direct_dict['47']` has a leading-space typo (`' நாற்பத்து ஏழு'`) and
`exceptions_dict['900']` has a trailing zero-width non-joiner (U+200C); both are stripped, the same class of
mechanical fix as Bengali's CRLF tokens.txt fix, not a translation judgement call.

`app/src/test/java/com/chmod777/itantra/TamilTextNormalizerTest.kt` checks 85 numbers (0 to 999,999,999,
including 50 random 9-digit numbers) against the same `direct_dict`/`hundreds_dict`/`exceptions_dict` data,
re-run through an identical algorithm in Python (`tools/vendor/indic_numtowords/tam/data/nums.py`), plus digit-run,
leading-zero/long-run, decomposed-vowel-sign and no-op cases. All 5 test methods pass
(`./gradlew.bat testDebugUnitTest --tests "com.chmod777.itantra.TamilTextNormalizerTest"`, run 2026-09-27).

## fp16 decoder candidate (mirrors Bengali; NOT assumed, checked)
- Same tool as Bengali, unmodified: `tts-research/mms-ben/speed/mms_decoder_fp16.py model.onnx
  fp16/model.dec16_full.onnx /dec/ Conv,LeakyRelu,Add,Div,Constant,ConvTranspose,Tanh`. 200 region nodes
  converted, 1 boundary cast (near-identical region size to Bengali, consistent with the byte-identical
  `config.json`/HiFi-GAN V1 architecture). Output 85,403,674 B (fp32 was 114,052,756 B), a similar ~25%
  reduction to Bengali's fp32->fp16-decoder step.
- **Length-parity / fidelity check (desktop, not phone — no device access for this worker; this differs from
  how Bengali's equivalent check was run, labelled explicitly):** 10 of the same normalized draft sentences,
  deterministic settings (`noise_scale=0`, `noise_scale_w=0`, `silence_scale=1.0`, matching the Bengali
  methodology), fp32 vs fp16-decoder, sherpa-onnx 1.13.7, CPU, 2 threads.
  **Result: 10/10 identical sample-length output, median SNR 68.2 dB (min 65.2 dB, max 70.8 dB).**
  This is a substantially higher SNR than Bengali's on-phone deterministic check (median 29.9 dB, min 28.3 dB);
  the difference is plausibly desktop-vs-phone ORT fp16 kernel behaviour rather than a per-language effect, but
  that is a hypothesis, not verified — no phone numbers exist for Tamil to compare against.
  Evidence: `evidence/det/det_fp32_*.wav`, `evidence/det/det_fp16dec_*.wav`, `evidence/det/desktop_det_compare.log`.
- Given full length parity and high SNR, the decoder-only fp16 model is carried forward as the shipped asset,
  the same decision Bengali reached, but Tamil's own evidence supports it independently rather than by analogy.
- **Not attempted / not repeated:** whole-model fp16 (Bengali already found this damages predicted durations
  via encoder/duration-predictor drift; no reason found here to expect Tamil's encoder/duration predictor to
  behave differently, and repeating that specific negative experiment was out of scope for a bounded worker
  run). Dynamic INT8 quantization (Bengali measured it dramatically slower with no accuracy upside; nothing
  about Tamil's graph — identical `config.json` architecture — gives a reason to expect a different result).

## What was not repeated, and why
- **Dynamic INT8**: not tried. Same HiFi-GAN V1 decoder architecture as Bengali (byte-identical `config.json`),
  where INT8 was measured dramatically slower with no quality upside. No graph difference was found that would
  change that outcome.
- **On-phone timing/RTF**: not attempted. This worker has no physical device access (RMX3392 is reserved for
  the foreman); every desktop RTF number above is explicitly labelled desktop and is known to be noisy
  (matching the Bengali report's own warning). No speed gate (median warm RTF <= 1.0, median text-to-audible
  start <= 3 s, zero failures in 20 repeats) is claimed met or unmet here — that requires the phone.
- **Whole-model fp16**: not re-tried; Bengali's finding (damaged durations) is architecture-level, and Tamil
  shares that architecture.

## Android integration
- `app/src/main/assets/vits-mms-tam/{tokens.txt,config.json,MODEL_CARD}` committed (LF tokens.txt, byte count
  59 lines matching the generated+cleaned file above). `model.onnx` itself is git-ignored, exactly like Bengali;
  `setup-models.ps1 -MmsTamilModel <fp16 model path>` installs and hash-pins it
  (sha256 `f6c0a040c6332537b55f0622a3c24d417892fda139ec45bdd73e822ba0cb21c3`).
- `TtsHelper.kt`: added a `"ta"` voice entry (`vits-mms-tam/model.onnx`, `usesEspeak = false`, `numThreads = 4`
  matching Bengali's starting point since neither the graph nor the phone differ from Bengali's case; no
  independent thread sweep was possible without device access), `normalize = TamilTextNormalizer::normalize`.
  No other language's `Voice` entry was touched.
- `./gradlew.bat assembleDebug` succeeds with the new asset/code in place (2026-09-27). `./gradlew.bat
  testDebugUnitTest` passes for the whole suite, including the 5 new `TamilTextNormalizerTest` cases and the
  pre-existing `BengaliTextNormalizerTest` (unaffected).

## Explicitly NOT done here (foreman/human gates)
- No phone install, benchmark, or RTF/start-time measurement of any kind (hard constraint for this worker).
- No native Tamil listener pass on any of the evidence WAVs. Machine OOV/desktop screening above tells you
  what characters reach the model and whether output is non-silent/sane-length; it says nothing about whether
  the ten meanings, the digit words, or the dropped aytham/full-stop are acceptable to a Tamil speaker.
- No GREEN status anywhere in this document or the code comments; "DESKTOP READY" (see handback report) means
  converted + sane fp32 + evidenced frontend fixes + a fp16 candidate that passed its own fidelity check, not
  phone-verified and not listener-verified.
