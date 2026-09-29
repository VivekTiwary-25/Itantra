# IndicConformer quantisation ladder: decisions log

Branch `stt/ic-quant` (worktree `D:\projects\SIH\iTantra-quant`, from `origin/Complete-App-V1` @ `60d4bc0`).
Part 2 of the STT work, run in parallel with the Part 1 agent (benchmark and app swap on `stt/indicconformer`).
Model files live outside git under `D:\projects\SIH\iTantra\.indicconformer-600m\quant-ladder\<step>\`. Nothing
under `.indicconformer-600m\` outside `quant-ladder\` was modified. Scripts: `docs/sih-metrics/tools/quant/`.

## Setup decisions

- **Python**: own venv `quant-ladder\.venv` (Python 3.12.10). `sherpa-onnx==1.13.7` to match `app/libs/sherpa-onnx-1.13.7.aar`.
  Its desktop wheel bundles ORT 1.27.1, the same ORT version as the AAR. Quantisers come from `onnxruntime` 1.30.0 (+ `onnx` 1.23.0, `onnx_ir` for MatMulNBits).
- **Package size definition**: `shared/encoder.weights.bin` + 9 × `<lang>.onnx` + 9 × `languages/<lang>/tokens.txt`,
  the files the app needs at runtime. This matches the 697.42 MB in `multilingual-adapters/storage_report.md`. Build-side extras
  (`encoder_only.onnx`, `languages/<l>/head.onnx`, `metadata.json`) are not counted.
- **Wrappers**: every variant is packaged with the unmodified `multilingual-adapters/adapter_builder.py`. `ladder_lib.build_package`
  imports it and points its `SRC_ENC`/`OUT` globals at the variant, so every package has the same shape as the current one
  (one shared external-weights file + thin sherpa `nemo_ctc` wrappers + tokens).
  - Side effect found and fixed: the first import wrote `__pycache__/adapter_builder.cpython-312.pyc` into
    `multilingual-adapters\`, and one helper call wrote `package_manifest.json` there. Both files were mine
    (created 2026-09-29 09:50–09:54; they did not exist before). I deleted both. Bytecode writing is now off for that import,
    and `package_manifest()` refuses to write outside `quant-ladder\`. No original file was changed.
- **Clip sets** (`quant-ladder\data\`, built by `prepare_data.py`):
  - check = first 30 rows of the FLEURS **validation** split per language
  - test = first 50 rows of the FLEURS **test** split (the same selection rule as Task 5)
  - calib = 100 **train** rows (12 hi + 11 for each of the other 8 languages)

  The link measured 47–75 KB/s, and every FLEURS parquet file is one row group whose audio sits in a single
  snappy-compressed dictionary page, so pyarrow can't read part of it. `fleurs_prefix.py` downloads only the
  prefix of that page (resumable HTTP Range requests) and decodes the first N values. It is verified byte-identical
  to a full pyarrow read on a local file. Test-split parquet already downloaded by the Part 1 agent is read in place (read-only).
- **Scoring**: `score_package.py` uses sherpa-onnx `OfflineRecognizer.from_nemo_ctc`, CPU, 2 threads, greedy, 16 kHz, 80 bins,
  with one language's recognizer alive at a time. Normalisation and the wrong-script rule are imported from `measure_wer.py`;
  I added the kn (U+0C80–0CFF) and ml (U+0D00–0D7F) script ranges. "Average WER" = the unweighted mean of the 9 per-language WERs.
- **Desktop speed** is not reported: the Part 1 agent shares the CPU.

## B1: ONNX Runtime in the app's AAR, and 4-bit support

- `app/libs/sherpa-onnx-1.13.7.aar` → `jni/arm64-v8a/libonnxruntime.so` (21,684,880 B,
  SHA-256 `dc5e4c172b1be9e530c6a62ad8f1be3e0a911cabdee6195abf28dab72477e194`). Version string `1.27.1`, symbol version `VERS_1.27.1`.
- MatMulNBits evidence in that arm64 library (`strings`):
  - the CPU kernel source `onnxruntime/contrib_ops/cpu/quantization/matmul_nbits.cc` and `matmul_nbits_impl.cc` are compiled in
  - kernel classes `onnxruntime::contrib::MatMulNBits<float>` and `MatMulNBits<MLFloat16>` are present, plus the error string
    "MatMulNBits accuracy level must be between 0 and 4"
  - the MLAS n-bit GEMM entry point `MlasQNBitGemmBatch` (`MLAS_QNBIT_GEMM_DATA_PARAMS`) is present, plus KleidiAI packing
    strings ("Failed to get KleidiAI packed filter size."), i.e. the arm64 kernels
- **Conclusion: MatMulNBits (4-bit) is supported by the bundled arm64 CPU runtime, so Q2 and Q3 are possible.** This is static
  evidence. The phone checks prove it at runtime (the load must hit no unsupported-operator error).
- The same library also contains `ConvInteger`, `MatMulInteger`, `DynamicQuantizeLinear` and `DequantizeLinear` (opsets 10–25),
  which is what Q1 and Q4 need.

## Step 0.4: FP32 base rebuild

- FP32 base = `model/assets/encoder.onnx` (366 external FP32 tensors + the positional-table Constant), with the exact INT8-B rewrite
  of the 48 pointwise Convs into Transpose→MatMul→Add→Transpose. Saved as `quant-ladder\fp32-base\encoder_pw_fp32.onnx`
  (1,067,306 B, `9b578bf3…b451f`) + `encoder_pw_fp32.data` (2,471,755,776 B, `5bb7efdf…a28`).
- Re-applying the INT8-B recipe with ORT 1.30.0's quantiser (the original used 1.20.1), then `adapter_builder.py`, gives a package
  that is **byte-identical to `multilingual-adapters\`: all 19 deployable files have equal SHA-256**
  (e.g. `shared/encoder.weights.bin` `083b3ee0b25bde0301f3b43ad2ebe8099a5b6bad0fed063e1a43891dfe444e09`, 697,424,531 B total).
- Hindi check set: existing package WER 10.33 %, rebuild WER 10.33 %, 30/30 identical transcripts. **0.4 PASSED**, so Q2 and Q3 build from the FP32 base.

## B0: baseline (existing `multilingual-adapters\` INT8-B package, 697,424,531 B)

Check set (30 FLEURS validation clips per language):

| lang | hi | gu | mr | ta | te | or | bn | kn | ml | avg |
|---|---|---|---|---|---|---|---|---|---|---|
| WER | 10.33 | 18.54 | 16.97 | 20.81 | 23.66 | 20.29 | 15.82 | 15.26 | 32.19 | **19.32** |
| CER | 3.56 | 5.74 | 4.63 | 6.56 | 7.22 | 5.44 | 3.62 | 6.01 | 10.93 | |
| wrong-script | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | |

Raw data: `quant-ladder\runs\b0-check\`.

## Q1: remaining Convs + CTC head, dynamic INT8 → REJECTED (accuracy)

- Built from the FP32 base: the INT8-B MatMul recipe, plus `quantize_dynamic` on the 29 remaining encoder Convs
  (5 pre-encode, 24 depthwise 1024×1×9 → ConvInteger). These are per-tensor: ORT's dynamic Conv quantiser gives the
  same result with `per_channel` on or off, so per-channel is not available for Conv. The per-language CTC head
  becomes per-channel INT8 MatMulInteger (`ladder_lib.quantize_wrapper_heads`).
- Package 689,679,440 B (−7,745,091 B, −1.1 %).
- Check set: average WER +1.96 points. Per language: hi +0.52, gu +1.56, mr +1.81, ta +1.78, te +1.08, **or +5.98**, bn +0.91, kn +1.37, ml +2.62.
  Wrong-script stays 0. Fails (a) and (b). Likely cause: per-tensor quantisation of the depthwise convolutions (one scale for 1024 channels).
- Next: the gentler version, "only pointwise Convs; keep depthwise at full precision" = pre_encode conv.3 + conv.6 (1×1) + the CTC head.

### Q1 gentler version (pointwise Convs + CTC head only) → REJECTED (accuracy). Q1 is CLOSED.

- Only pre_encode conv.3 / conv.6 (1×1 → ConvInteger) and the per-language CTC head (per-channel INT8 MatMulInteger) are quantised.
  The depthwise and 3×3 pre-encode Convs stay FP32. Package 689,967,026 B (−7,457,505 B).
- Check set: average WER +0.46 points (passes (a)). Per language: hi −0.13, gu +0.69, mr +0.54, ta +0.51, te +0.65,
  **or +1.27**, bn −0.18, kn +0.23, ml +0.60. Wrong-script stays 0. Fails (b) on Odia.
- Q1 is closed (the full and gentler versions both dropped accuracy). **The last accepted model stays B0 (INT8-B)**, so
  Q2 carries no Q1 changes: CTC head FP32, all non-pointwise Convs FP32.
- This counts as one step rejected for accuracy. If Q2 is also rejected for accuracy, the ladder stops (two in a row).
- Discarded build: `quant-ladder\q2-nbits-b32\` was pre-built carrying the gentle-Q1 changes before that verdict came in
  (450,299,496 B). It is not a valid ladder step and was not scored. The valid Q2 build is `quant-ladder\q2-nbits-b32-noq1\`.

## Q2: 4-bit MatMulNBits, block 32 → REJECTED (accuracy)

- Built from the FP32 base (pointwise rewrite included). All 265 weight MatMuls are converted to `com.microsoft::MatMulNBits`:
  bits 4, asymmetric (uint4 + zero point), block 32 along K, `accuracy_level=4` (int8 activation compute, the arm64 fast path).
  Built with ORT 1.30.0 `MatMulNBitsQuantizer`. Everything else is as in INT8-B: Convs FP32, CTC head FP32
  (Q1 was not accepted). Step dir `quant-ladder\q2-nbits-b32-noq1\`.
- **Package 454,123,782 B (−243,300,749 B, −34.9 % vs INT8-B)**. `shared/encoder.weights.bin` is much smaller, and the
  wrappers shrink from 5.05 to ~2.6 MB because the graph has fewer nodes.
- sherpa-onnx 1.13.7 (ORT 1.27.1) loads and runs it on desktop without errors.
- Check set: average WER +0.37 points (passes (a)). Per language: hi +0.26, gu +0.87, **mr +1.44**, ta 0.00, te 0.00,
  **or +1.27**, bn +0.55, kn −0.23, ml −0.80. Wrong-script stays 0. Fails (b) on Marathi and Odia.
- Next is the prescribed gentler version, block size 128. Note for the reviewer: a larger block means coarser scales, so block 128
  is normally *less* accurate than block 32 (it is only smaller). The step is run as specified, but a pass is not expected.

### Q2 gentler version (block 128) → REJECTED (accuracy). Q2 is CLOSED.

- Same as Q2, with block size 128. Package 390,144,474 B (−307,280,057 B, −44.1 %). Step dir `quant-ladder\q2g-nbits-b128\`.
- Check set: **average WER +0.95 points** (fails (a)). Per language: hi −0.13, **gu +1.21**, **mr +1.44**, ta +0.25, **te +3.01**,
  or 0.00, bn +0.73, kn 0.00, **ml +2.01**. Wrong-script stays 0. As expected, it is worse than block 32.

## Ladder stopped: two steps in a row rejected for accuracy (Q1, Q2)

- Per the brief ("Two steps in a row rejected for accuracy → stop the ladder; the model is at its limit"), **Q3, Q4 and P1 were
  not attempted.** No desktop-accepted variant exists besides B0, so **the final model is INT8-B (no change)**, i.e. the
  existing `multilingual-adapters\` package (697,424,531 B).
- Consequence for the phone checks: there is no candidate variant to phone-test. Only the INT8-B reference measurement
  (RAM, load, RTF, parity) applies, and it needs the phone after `PART1_PHONE_DONE`.
- Built-but-unused: `quant-ladder\q3-*` was never built. The 100-clip calibration set (`data\calib\`) was prepared for Q4 and is unused.

### Observations for the reviewer (not decisions: the rules above were applied as written)

- Q2 block 32 missed only on rule (b), in two languages (mr +1.44, or +1.27), while the average rose just +0.37 and two languages
  improved (kn −0.23, ml −0.80). With 30 clips per language (394–765 reference words), those misses are 8 words (mr, of 554) and
  7 words (or, of 552), which is comparable to the swings between near-identical variants here: the gentle Q1 moved Odia by the same +1.27.
  If 243 MB (−35 %) matters for the demo, a larger check set (e.g. all ~250–400 validation clips per language) would show whether
  that per-language miss is real. That is outside this brief.
- Q3 (4-bit middle blocks, INT8 edges) was the natural next candidate after a Q2 miss. The stop rule prevented it, because Q1 had
  already failed first. Reviewer's option if wanted: `build_nbits.py --step q3-mixed-e2 --block 32 --int8-edge 2 --q1 none`.
- Not in the ladder, but worth noting: the largest single FP32 tensor left in every variant is the 41 MB relative-positional
  table (`Constant_1970`, [1, 9999, 1024] FP32). It is 6 % of the INT8-B package.

## Coordination

- `agent-status.txt` later showed `PART1_SCENARIO=A` (IndicConformer will be used), so the ladder results stay relevant.
- The phone check waits for `PART1_PHONE_DONE`. Script: `tools/quant/phone_check.py`. It refuses emulators and non-arm64 devices,
  streams the package into the harness app's private storage, and removes the pushed files afterwards.

## Phone check (after `PART1_PHONE_DONE`; latest `PHONE=` line: realme RMX3392, serial EILZRCFQLZIZ6H6P)

- Only INT8-B was measured: it is the reference and also the final model. No variant was desktop-accepted, so there is nothing else to phone-test.
- The first push attempt truncated a wrapper (4.3 of 5.05 MB): `adb exec-in` streams can be cut short. The size check caught it.
  `phone_check.py` now verifies every file by SHA-256 on the device and retries. Several files needed 1–3 retries, and all ended intact.
- Result: no errors; peak RSS 972.5 MB / PSS 873.6 MB; load 3.3–3.9 s per language; warm RTF 0.130.
- Text parity with desktop misses the "≤ 1 of 5" line in mr and or (2/5 each). The differences are single-word, character-level CTC
  flips; on the 45 clips WER is 16.96 % desktop vs 17.07 % phone, and the phone output is deterministic. This is recorded as a property of
  the existing package on arm64 (dynamic-INT8 kernel rounding), not a reason to change anything: criterion 6 gates candidate variants,
  and none exists.
- Clean-up: the pushed model files and clips were deleted, `results_int8b.json` was pulled to `quant-ladder\runs\phone\`, and the harness app was uninstalled.
