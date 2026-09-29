# IndicConformer quantisation ladder: results

Branch `stt/ic-quant` · recorded 2026-09-29 · desktop: Windows 11, sherpa-onnx 1.13.7 (bundled ORT 1.27.1, the same ORT as
`app/libs/sherpa-onnx-1.13.7.aar`), CPU, 2 threads, greedy · decisions and evidence: `QUANT_DECISIONS.md`.

## Summary

- **Steps tried (every step compared against INT8-B, the only accepted model):**

  | step | what | package | verdict | reason |
  |---|---|---|---|---|
  | Q1 | remaining Convs (pre-encode + depthwise) + CTC head → INT8 | 689.68 MB (−1.1 %) | **rejected** | accuracy: avg WER +1.96 pts; 7 languages > +1 pt (or +5.98) |
  | Q1 gentler | pointwise Convs + CTC head only | 689.97 MB (−1.1 %) | **rejected** | accuracy: or +1.27 pts (avg +0.46) |
  | Q2 | 4-bit MatMulNBits, block 32 | 454.12 MB (−34.9 %) | **rejected** | accuracy: mr +1.44, or +1.27 pts (avg +0.37) |
  | Q2 gentler | 4-bit, block 128 | 390.14 MB (−44.1 %) | **rejected** | accuracy: avg +0.95 pts; te +3.01, ml +2.01, mr +1.44, gu +1.21 |
  | Q3, Q4, P1 | — | — | **not attempted** | stop rule: two steps in a row rejected for accuracy |

- **Final model = INT8-B, unchanged** (the existing `multilingual-adapters` package, **697.42 MB**). No app change is needed.
- **Final test-set WER per language** (INT8-B, 50 FLEURS test clips each):

  | hi | gu | mr | ta | te | or | bn | kn | ml | avg |
  |---|---|---|---|---|---|---|---|---|---|
  | 11.63 % | 20.22 % | 20.29 % | 31.58 % | 21.03 % | 25.08 % | 15.04 % | 16.42 % | 23.74 % | 20.56 % |

  There are 0 wrong-script outputs.
- **Checks passed along the way:**
  - B1: the AAR's arm64 ORT 1.27.1 contains the MatMulNBits kernel, so 4-bit was possible.
  - 0.4: rebuilding FP32 and re-applying INT8-B gives a package byte-identical to the current one (19/19 files).
- **Phone (final model = INT8-B), realme RMX3392 / Android 14:**
  - no crash, no out-of-memory, no unsupported-operator error
  - peak RSS 972.5 MB / PSS 873.6 MB (all 9 languages loaded in turn, release-before-create)
  - load 3.3–3.9 s per language
  - warm RTF 0.130 (2 threads)
  - desktop text parity is not exact: mr and or differ on 2/5 clips, 5 languages on 1/5. The differences are single words, and
    WER on those 45 clips is 16.96 % desktop vs 17.07 % phone.
- **Not applicable:** phone checks of candidate variants (no variant was desktop-accepted).
- **Nothing PENDING.**

## Final model on the test set

50 FLEURS test clips per language: the first 50 rows, the same selection as Task 5. Clip ids and transcripts were verified identical
to Task 5 for the 7 shared languages, and the reference word counts match. Normalisation is the same as Task 5.
Dolphin numbers are from `RESULTS.md` Task 5; Task 5 did not cover kn/ml.

| language | Dolphin WER | IC INT8-B WER | final WER | final CER | final word errors / ref words | wrong-script (Dolphin → final) |
|---|---|---|---|---|---|---|
| hi | 37.90 % | 11.63 % | 11.63 % | 4.08 % | 147 / 1,264 | 0 → 0 |
| gu | 78.35 % | 20.22 % | 20.22 % | 6.87 % | 225 / 1,113 | 11 → 0 |
| mr | 70.72 % | 20.29 % | 20.29 % | 6.23 % | 212 / 1,045 | 0 → 0 |
| ta | 72.85 % | 31.58 % | 31.58 % | 12.67 % | 264 / 836 | 0 → 0 |
| te | 70.33 % | 21.03 % | 21.03 % | 7.49 % | 180 / 856 | 0 → 0 |
| or | 67.88 % | 25.08 % | 25.08 % | 7.34 % | 242 / 965 | 2 → 0 |
| bn | 40.85 % | 15.04 % | 15.04 % | 4.45 % | 148 / 984 | 0 → 0 |
| kn | N/A (not in Task 5) | 16.42 % | 16.42 % | 4.34 % | 142 / 865 | — → 0 |
| ml | N/A (not in Task 5) | 23.74 % | 23.74 % | 6.56 % | 188 / 792 | — → 0 |
| **mean of 9** | | 20.56 % | 20.56 % | | | |

Final = INT8-B, so the two IC columns are identical by construction.
Raw data: `.indicconformer-600m\quant-ladder\runs\final-int8b-test\` (per-clip CSV + `summary.json`, outside git).

## Sizes

| | bytes | MB |
|---|---|---|
| INT8-B package (shared `encoder.weights.bin` 651,952,128 + 9 wrappers + 9 token files) | 697,424,531 | 697.42 |
| Final package | 697,424,531 | 697.42 (no change) |
| IndicConformer speech-model size on the phone, before → after | 697.42 → 697.42 MB | unchanged (peak RAM 972.5 MB RSS, load 3.3–3.9 s, RTF 0.130: see Phone) |
| For reference, the rejected Q2 (4-bit block 32) package | 454,123,782 | 454.12 (−243.30 MB) |

## Phone (final model = INT8-B)

Harness: `.indicconformer-600m\quant-ladder\phone-proto\` (a copy of `android-prototype`, app id `com.chmod777.itantra.icquant`, the same
`sherpa-onnx-1.13.7.aar`). It loads the shared-encoder package from app-private files by path and runs the 9 languages one after another
in one process, releasing each recognizer before creating the next (as the app does). Each language: 5 check-set clips, pass 0 cold +
2 warm passes, 2 threads. RAM is sampled every 50 ms. Script: `tools/quant/phone_check.py`. Every pushed file was SHA-256 verified on the
device, and the files and the harness were removed afterwards. Run on 2026-09-29, ending 15:17:30 phone time, after `PART1_PHONE_DONE`.

| metric | value |
|---|---|
| phone | realme RMX3392, Android 14 (API 34), arm64-v8a, serial EILZRCFQLZIZ6H6P (the latest `PHONE=` line) |
| errors | none: no crash, no OOM, no unsupported operator; `ICQ_DONE` |
| peak RAM | **RSS 972.5 MB, PSS 873.6 MB** (whole 9-language run). After the ml decode: RSS 916 MB (anon 784, file 116), PSS 815 MB |
| load time per language (fresh recognizer, file path) | hi 3,937 ms (first); gu 3,298 · mr 3,266 · ta 3,255 · te 3,362 · or 3,346 · bn 3,316 · kn 3,312 · ml 3,366 ms |
| warm RTF, 2 threads | **0.130 overall**; per language 0.124–0.136 (hi 0.127, gu 0.124, mr 0.124, ta 0.124, te 0.127, or 0.133, bn 0.132, kn 0.136, ml 0.135) |
| repeatability | cold and warm passes gave identical text for all 45 clips |
| same text as desktop (5 clips/language; brief allows ≤ 1 of 5 different) | hi 0/5, gu 0/5, ta 0/5 differ; te, bn, kn, ml 1/5; **mr 2/5, or 2/5** |
| accuracy on those 45 clips | WER 16.96 % desktop vs 17.07 % phone (+0.11 points) |

On parity: every difference is a 1–3-word, character-level change. Examples: a vowel sign (mr नाविन्यतेची / नावीन्यतेची), a split
compound (te వీల్చైర్లలో / వీల్ చైర్లలో), a glide (ml രാത്രിക്കാഴ്ച ഉുള്ളതിനാൽ / രാത്രിക്കാഴ്ചയള്ളതിനാൽ). The phone is sometimes better
(mr 001, or 005, ml 005) and sometimes worse (mr 005, te 005, or 004). The likely source is arm64 versus x86 kernel rounding in the dynamic
INT8 path (DynamicQuantizeLinear + MatMulInteger), flipping near-tied CTC frames. That is a property of the current INT8-B package, not of this
ladder: no variant was adopted. The earlier Hindi-only phone test (10 short clips) happened to match exactly.
For context, the equivalent fused Hindi INT8-B model measured peak RSS ~900 MB and warm RTF ~0.104 on this phone (SIH_MAIN_HANDOFF §4.9).
Today's run cycles through 9 languages on longer FLEURS sentences.

## Check-set data behind every decision

The check set is 30 FLEURS validation clips per language. Values are WER (%); wrong-script was 0 for every model and language.

| model | hi | gu | mr | ta | te | or | bn | kn | ml | avg | Δavg |
|---|---|---|---|---|---|---|---|---|---|---|---|
| B0 INT8-B | 10.33 | 18.54 | 16.97 | 20.81 | 23.66 | 20.29 | 15.82 | 15.26 | 32.19 | 19.32 | — |
| Q1 | 10.85 | 20.10 | 18.77 | 22.59 | 24.73 | 26.27 | 16.73 | 16.63 | 34.81 | 21.28 | +1.96 |
| Q1 gentler | 10.20 | 19.24 | 17.51 | 21.32 | 24.30 | 21.56 | 15.64 | 15.49 | 32.80 | 19.78 | +0.46 |
| Q2 b32 | 10.59 | 19.41 | 18.41 | 20.81 | 23.66 | 21.56 | 16.36 | 15.03 | 31.39 | 19.69 | +0.37 |
| Q2 b128 | 10.20 | 19.76 | 18.41 | 21.07 | 26.67 | 20.29 | 16.55 | 15.26 | 34.21 | 20.27 | +0.95 |

Check-set reference words per language: hi 765, gu 577, mr 554, ta 394, te 465, or 552, bn 550, kn 439, ml 497.

## Reproduce

Scripts are in `docs/sih-metrics/tools/quant/`:

- `prepare_data.py` builds the check/test/calib clip sets.
- `fleurs_prefix.py` does the partial FLEURS download.
- `build_fp32_base.py`, `build_int8b.py`, `build_q1.py [--gentle]` and `build_nbits.py --step … --block 32|128 [--int8-edge N] [--q1 …]` build the variants.
- `score_package.py` scores a package; `compare.py` gives the acceptance verdict.
- `ladder_lib.py` holds the shared helpers. It packages every variant with the unmodified `multilingual-adapters/adapter_builder.py`.

Model files and audio are not in git.
