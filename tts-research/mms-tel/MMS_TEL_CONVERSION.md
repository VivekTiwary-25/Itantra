# Meta MMS-TTS Telugu (`tel`) -> sherpa-onnx ONNX

Date 2026-09-27. **QUALITY UNVERIFIED — no listener. Not GREEN.**

## Sources and license
- Model: https://huggingface.co/facebook/mms-tts (repo sha `44cc7fb408064ef9ea6e7c59130d88cac1274671`, the same
  commit already validated for Bengali), files under `models/tel/`.
- **License: CC BY-NC 4.0** (same model repo/card as Bengali). Non-commercial only, attribution required:
  "Meta MMS-TTS (facebook/mms-tts, tel), CC BY-NC 4.0, converted to ONNX for sherpa-onnx". Converting does not
  change these terms.
- Conversion guide: https://k2-fsa.github.io/sherpa/onnx/tts/mms.html (`vits-mms.py`, verbatim copy reused from
  the Bengali conversion, `tts-research/mms-ben/MMS_BN_CONVERSION.md`; no per-language changes needed).
- Telugu config: `training_files = train.ltr` (not uroman, so supported), `add_blank = true`, 16,000 Hz, single
  speaker, 65-token native-script vocab. Model architecture (VITS, HiFi-GAN V1 decoder, `resblock` type 1,
  `upsample_initial_channel` 512) is identical to Bengali's, confirmed from `config.json` and by counting ONNX
  Conv nodes after export (183 Conv nodes total, 200 `/dec/` decoder-region nodes; Bengali's are the same shape).

## Hashes / sizes (SHA-256)
| file | bytes | sha256 |
|---|---|---|
| G_100000.pth | 145,505,369 | 5e2d8809c211ef5b0d73539228ea8125fed443c9419491e04d25140c9e2f0509 |
| config.json | 1,887 | ec453d50be1976ddb3b501c7dc09128cdf360b83e5b0cf7dec68dc5caa460f49 (byte-identical to Bengali's) |
| vocab.txt | 250 | 8ff1757f54545cbec761d354e200a47fd1a24d996fce67dfab73bd39b1054abd |
| model.onnx (fp32, converted) | 114,058,133 | 82ef682c0bf91b279ba09d7396c94a36d583484f2d5da70eb3e7e4fa2e3378fe |
| tokens.txt (generated, then normalized to LF) | 435 | 528152cb4121272e6e71f7a1d91c8d4922b92fae414ae599db608cb92a738bb7 |

**tokens.txt hazard (same as Bengali):** `vits-mms.py` writes tokens.txt with Python text mode, i.e. CRLF on
Windows (500 B). A CRLF tokens.txt aborts the process in sherpa-onnx on Android (see `TtsHelper.normaliseTokens`,
which is a runtime backstop, not a substitute for shipping an LF file), so it was converted to LF (`tr -d '\r'`)
before use; desktop results below use the LF file.

Large files stay outside Git (`D:\iTantra-tts-models\mms-tel`). Not quantized. Exported ONNX bytes may differ
across torch versions; pin the hash of what was validated.

## Environment
Reused the existing Bengali venv verbatim: `D:\iTantra-tts-models\.venv-mms` (Python 3.10.x, torch 2.2.2+cpu,
numpy<2, onnx 1.17.0, onnxruntime 1.18.1, sherpa-onnx 1.13.7 — matches the Android AAR). The `MMS` inference
checkout and the `monotonic_align` stub fix (32-bit MinGW vs 64-bit Python meant the Cython extension could not
be built; `core.py` is a stub, `monotonic_align/__init__.py` imports `.core`) were copied byte-for-byte from
`D:\iTantra-tts-models\mms-ben\MMS`, since none of that code is language-specific and it was already fixed once.

## Commands
```
# in D:\iTantra-tts-models\mms-tel, files from models/tel/ downloaded with curl from the pinned commit
# (MMS/ and vits-mms.py copied from mms-ben; both are language-generic)
set PYTHONPATH=D:\iTantra-tts-models\mms-tel\MMS;D:\iTantra-tts-models\mms-tel\MMS\vits
set language=Telugu
python vits-mms.py        # -> model.onnx, tokens.txt (CRLF; stripped to LF afterwards)
python tools\mms_desktop_probe.py --language-code te --model model.onnx --tokens tokens.txt \
    --input-file tts-research\inputs\te.txt --output-dir evidence\raw --repeat 2
```
The stub is safe: `maximum_path` is only used in training; the export path never calls it.

## Desktop validation (sherpa-onnx 1.13.7, CPU, 2 threads)
- 10 draft sentences (`tts-research/inputs/te.txt`, the pre-existing draft input also used for the Indic-TTS/te
  frontend-repair track) x2 passes = 20 syntheses, all succeeded, non-silent. RTF on this (busy, shared) desktop
  host ranged 0.8-6.3x fp32 -- noisy like the Bengali desktop numbers were; not a phone measurement. Different
  lengths all worked (dynamic-length graph, same as Bengali, unlike the Indic-TTS acoustic export used for the
  earlier `te-indic` candidate).
- **Frontend finding:** the `tel` vocabulary (`vocab.txt`, `tools\mms_desktop_probe.py`'s OOV report,
  `evidence/raw/timings.json`) has no Telugu digits (౦-౯, U+0C66-U+0C6F) and no ASCII period (`.`); sherpa logs
  "Skip unknown character" and drops them, exactly like Bengali's danda/native-digit gap. The vocabulary does
  contain one bare ASCII digit, `6` (U+0036) -- almost certainly a training-data artifact, not a usable digit
  path, and it is not relied on. Message 9 (`మందుల కోసం ౧౨ పెట్టెలు తీసుకురండి`) loses ౧౨ ("12") entirely
  without normalization -- the same failure mode the earlier `te-indic` Coqui frontend-repair track found and
  fixed for its own (different) checkpoint. Message 10 also contains a ZWNJ (U+200C) after a virama in
  `హైదరాబాద్‌లోని`; it too is silently dropped by the MMS frontend, with no pronunciation effect (a ZWNJ is a
  rendering hint, not a phoneme) -- this OOV drop is a no-op, unlike the digit drop, and is left alone.
- Evidence: `evidence/raw/*.wav` (+`_r2`), `evidence/te_input_normalized.txt` (line 9 patched to spell out the
  number the way `TeluguTextNormalizer` will at runtime), `evidence/det_fp32_*.wav` /
  `evidence/det_fp16dec_*.wav` (deterministic fp32-vs-fp16-decoder comparison, noise_scale=0, noise_scale_w=0,
  length_scale=1, sid=0), `evidence/compare_fp32_fp16.py`.

## Decoder-only FP16 candidate
Same script used for Bengali, no changes: `tts-research/mms-ben/speed/mms_decoder_fp16.py model.onnx
fp16/model.fp16dec.onnx /dec/ Conv,LeakyRelu,Add,Div,Constant,ConvTranspose,Tanh`. Region size (200 nodes, 1
boundary cast in) matches Bengali's region exactly, consistent with the identical decoder architecture.

| file | bytes | sha256 |
|---|---|---|
| fp16-decoder model.onnx | 85,409,051 | 9318f02ed147569af62592798b4770177eedb1ea79e9f6678b22b2edd0934677 |

Length-parity result (deterministic, noise_scale=0, noise_scale_w=0, length_scale=1, sid=0, the 10 normalized
sentences, `evidence/compare_fp32_fp16.py`, `evidence/snr.py`):

| line | fp32 samples | fp16dec samples | length match | SNR fp16dec vs fp32 |
|---|---|---|---|---|
| 1 | 77538 | 77538 | yes | 67.3 dB |
| 2 | 71161 | 71161 | yes | 69.2 dB |
| 3 | 59212 | 59212 | yes | 67.8 dB |
| 4 | 54513 | 54514 | off by 1 sample (0.002%) | **18.6 dB** |
| 5 | 68347 | 68347 | yes | 68.8 dB |
| 6 | 87212 | 87212 | yes | 67.3 dB |
| 7 | 44704 | 44704 | yes | 69.1 dB |
| 8 | 53554 | 53554 | yes | 67.2 dB |
| 9 | 49687 | 49687 | yes | 67.3 dB |
| 10 | 55742 | 55742 | yes | 66.4 dB |

9/10 sentences match fp32 exactly in length with SNR 66-69 dB (i.e. essentially identical waveforms -- much
higher than Bengali's accepted 28-30 dB, because Telugu's decoder region and boundary casts are byte-identical
in structure to Bengali's). Sentence 4 ("పాత వంతెన దాటి ఆసుపత్రి ముందు ఆగండి") differs by exactly one sample
(54513 vs 54514, i.e. 0.06 ms of extra audio) but its SNR against fp32 is only 18.6 dB -- markedly worse than
the other nine, meaning the waveforms audibly diverge somewhere in that one sentence even though the overall
duration is essentially unchanged. This is a materially different failure mode from Bengali's rejected
all-Conv fp16 (which changed 6/10 *durations*, a duration-predictor/encoder problem): here the duration
predictor and encoder stayed in fp32 and produced the same length on 9/10 and a 1-sample difference on the
10th, so this looks like a decoder-side rounding/rendering difference localized to one sentence rather than a
systemic duration regression. **This is not yet a pass or a fail by machine measurement alone**: a human
listener must confirm whether sentence 4's fp16-decoder audio still conveys the same meaning before this
candidate can be trusted end-to-end; `evidence/det_fp32_04.wav` and `evidence/det_fp16dec_04.wav` are the pair
to check first.

## Shipped candidate
Kept as a phone-test candidate (not yet GREEN): `tel` -> `vits-mms-tel/model.onnx` = the decoder-fp16 export
above, 4 threads, character frontend, sherpa defaults, `TeluguTextNormalizer` applied before synthesis.
Justification: 9/10 deterministic sentences are essentially bit-identical to fp32 (66-69 dB SNR, exact length);
the model is half the fp32 size (85.4 MB vs 114.1 MB) and, by direct architectural analogy to Bengali (identical
decoder shape, identical fp16-conversion script and region size), is expected to give a similar RTF win on
device. The one weak sentence (#4, 18.6 dB) is flagged above for priority listening rather than silently
accepted; if a listener judges it unacceptable, the fix is to fall back to the fp32 model (`model.onnx`,
82ef682c...378fe, 114,058,133 B) for `tel` specifically, which is architecturally identical to what shipped for
Bengali before the fp16 optimization and carries no such risk. Both files exist in `D:\iTantra-tts-models\mms-tel`
if that fallback is needed. As with Bengali, "shipped" here means "ready for the foreman's phone-test phase", not
GREEN; only Vivek marks GREEN after a real-device listening/timing test.
