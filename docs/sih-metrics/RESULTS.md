# SIH measurement results

Branch: `Complete-App-V1`  
Commit: `ad120d32575aff00bd09d412150b3a1eec3ba615`  
Recorded: 2026-09-29

Task order requested: 5, 2, 3, 4, 6, 7, 8, 10. Tasks 9, 11, and 12 were skipped as requested.

For Task 5, each FLEURS clip was converted to 16 kHz, mono, 16-bit PCM WAV. The desktop recognizer mirrors `SpeechRecognizerManager.kt`: English uses Whisper tiny.en INT8; Hindi, Gujarati, Marathi, Tamil, Telugu, Odia, and Bengali use Dolphin base multilingual CTC INT8; CPU, two threads, greedy decoding. Text normalization is lowercase, Unicode NFC, Unicode punctuation removal, and whitespace collapsing. A wrong-script output contains alphabetic characters but none from the target script.

| Metric | Value | Device | Method | Runs | Raw log | Status (MEASURED / MISSING) |
|---|---|---|---|---:|---|---|
| Task 5 WER/CER — English | WER 0.1441; CER 0.0700; 155 word errors / 1,076 reference words; wrong script 0 | Windows desktop | FLEURS `en_us` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/en-results.csv` | MEASURED |
| Task 5 WER/CER — Hindi | WER 0.3790; CER 0.2066; 479 / 1,264; wrong script 0 | Windows desktop | FLEURS `hi_in` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/hi-results.csv` | MEASURED |
| Task 5 WER/CER — Gujarati | WER 0.7835; CER 0.5625; 872 / 1,113; wrong script 11 | Windows desktop | FLEURS `gu_in` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/gu-results.csv` | MEASURED |
| Task 5 WER/CER — Marathi | WER 0.7072; CER 0.2369; 739 / 1,045; wrong script 0 | Windows desktop | FLEURS `mr_in` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/mr-results.csv` | MEASURED |
| Task 5 WER/CER — Tamil | WER 0.7285; CER 0.2721; 609 / 836; wrong script 0 | Windows desktop | FLEURS `ta_in` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/ta-results.csv` | MEASURED |
| Task 5 WER/CER — Telugu | WER 0.7033; CER 0.2759; 602 / 856; wrong script 0 | Windows desktop | FLEURS `te_in` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/te-results.csv` | MEASURED |
| Task 5 WER/CER — Odia | WER 0.6788; CER 0.2633; 655 / 965; wrong script 2 | Windows desktop | FLEURS `or_in` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/or-results.csv` | MEASURED |
| Task 5 WER/CER — Bengali | WER 0.4085; CER 0.1387; 402 / 984; wrong script 0 | Windows desktop | FLEURS `bn_in` test, same model files, desktop sherpa-onnx | 50 clips | `docs/sih-metrics/raw/task5-wer/bn-results.csv` | MEASURED |
| Task 5 phone parity | MISSING — no phone was connected, so five clips per language could not be run through the app and compared with desktop output | RMX3392 / Android 14 expected | `adb devices -l` | 0 | `docs/sih-metrics/raw/task5-wer/environment.json` | MISSING |
| Task 2 model size — Whisper tiny.en encoder INT8 | 12,937,772 B; 12.34 MiB | N/A | File size inspection | 1 | `app/src/main/assets/tiny.en-encoder.int8.onnx` | MEASURED |
| Task 2 model size — Whisper tiny.en decoder INT8 | 89,853,865 B; 85.69 MiB | N/A | File size inspection | 1 | `app/src/main/assets/tiny.en-decoder.int8.onnx` | MEASURED |
| Task 2 model size — Whisper base encoder INT8 | 29,120,534 B; 27.77 MiB | N/A | File size inspection | 1 | `app/src/main/assets/base-encoder.int8.onnx` | MEASURED |
| Task 2 model size — Whisper base decoder INT8 | 130,672,026 B; 124.62 MiB | N/A | File size inspection | 1 | `app/src/main/assets/base-decoder.int8.onnx` | MEASURED |
| Task 2 model size — Dolphin base multilingual CTC INT8 | 103,729,802 B; 98.92 MiB | N/A | File size inspection | 1 | `app/src/main/assets/dolphin-base-ctc-multi-lang-int8/model.int8.onnx` | MEASURED |
| Task 2 model size — Piper English Ryan | 63,149,198 B; 60.22 MiB | N/A | File size inspection | 1 | `app/src/main/assets/vits-piper-en_US-ryan-medium/en_US-ryan-medium.onnx` | MEASURED |
| Task 2 model size — Piper Hindi Pratham | 63,145,178 B; 60.22 MiB | N/A | File size inspection | 1 | `app/src/main/assets/vits-piper-hi_IN-pratham-medium/hi_IN-pratham-medium.onnx` | MEASURED |
| Task 2 model size — Piper Malayalam Arjun | 62,946,436 B; 60.03 MiB | N/A | File size inspection | 1 | `app/src/main/assets/vits-piper-ml_IN-arjun-medium/ml_IN-arjun-medium.onnx` | MEASURED |
| Task 2 APK size | MISSING — no debug or release APK was produced | N/A | `assembleDebug assembleRelease`; retry with `--no-daemon` | 2 build attempts | `docs/sih-metrics/raw/task2-build.txt` | MISSING |
| Task 3 RAM and CPU | MISSING — no connected phone | RMX3392 / Android 14 expected | Required `dumpsys meminfo` and `top` commands | 0 | `docs/sih-metrics/raw/task2-build.txt` | MISSING |
| Task 4 STT speed | MISSING — no connected phone | RMX3392 / Android 14 expected | Required `ITANTRA_PERF_STT` logcat collection | 0 | `docs/sih-metrics/raw/task5-wer/environment.json` | MISSING |
| Task 6 TTS speed | MISSING — no connected phone and the seven optional MMS voice model binaries are absent | RMX3392 / Android 14 expected | Required visible-app warm benchmark | 0 | `docs/sih-metrics/raw/task2-build.txt` | MISSING |
| Task 7 TTS clarity | MISSING — the full ten-voice model set and the listener STT setup are unavailable | N/A | Required synthesis and machine-listener scoring | 0 | `tts-research/mms-ben/speed/SPEED_REPORT.md` | MISSING |
| Task 8 real message size | MISSING — the default BLE path needs a live secure link and negotiated MTU to measure bytes on air | Connected peer phones required | Required outgoing-message path measurement | 0 | `docs/sih-metrics/raw/task2-build.txt` | MISSING |
| Task 10 per-hop relay delay | MISSING — requires three or more connected phones and their benchmark CSVs | Three or more phones required | `ProbeService` / `BenchmarkRunner`, 20 trials per hop count | 0 | `docs/sih-metrics/raw/task2-build.txt` | MISSING |

## Code and environment changes

- Added `docs/sih-metrics/tools/measure_wer.py`. Keep it: it is a reusable, app-configuration-matched WER/CER runner.
- Added the compact result record and raw Task 5 manifests, transcripts, summaries, and environment metadata. Keep them.
- Added `.gitignore` rules for downloaded FLEURS WAV and parquet source files. Keep them: they are reproducible public inputs totaling more than 1.6 GB, while the compact auditable transcripts and score tables remain available for review.
- Ran `setup-models.ps1`, restoring ignored model binaries required for measurement. This did not change tracked source.
- Created `.venv-sih-metrics` for `sherpa-onnx`, `jiwer`, `datasets`, and `soundfile`. It is already ignored and should remain local.
- No app behavior was changed.

## Task 5b — IndicConformer

**Summary**

- **Scenario A**: IndicConformer (IC) beats Dolphin in all 7 Indian languages by 25.8 to 58.1 WER points, so IC replaces Dolphin everywhere. English stays on Whisper tiny.en.
  Dolphin and Whisper base are removed from the app. Full reasoning: `docs/sih-metrics/STT_DECISIONS.md`.
- **Languages switched to IC**: hi, gu, mr, ta, te, or, bn. kn and ml were measured (IC only) but are not in the picker; Vivek decides.
- **Final debug APK**: 323,335,841 B (308.36 MiB) from a clean `gradlew clean assembleDebug`. IC itself (about 697 MB) is not in the APK; it is pushed to the phone's private files folder (`docs/STT_INDICCONFORMER.md`).
- **Phone**: realme RMX3392, Android 14, arm64-v8a, MediaTek mt6877, 7.7 GB RAM (`EILZRCFQLZIZ6H6P`).
- **Phone result: NOT PASSED under the stop rule.** Transcripts differ from desktop in more than 1 of 5 clips for gu (2), mr (2) and ta (2); hi and te 0, or 1, bn 1.
  The differences are single-character or matra flips (INT8 kernels on arm64 differ slightly from x86, as the earlier prototype also saw). On these 5 clips the phone WER is within about 5 points of desktop and sometimes better.
  No crash, no out-of-memory, no language switch over 6 s. **Per the rule the branch was NOT pushed** and `PART1_DONE` was not written; Vivek to decide whether the differences are acceptable.
- **PENDING**: Vivek's decision on the parity finding; push of `stt/indicconformer`; kn/ml picker decision; GREEN status (only Vivek).

### Dolphin vs IndicConformer (desktop, 50 FLEURS test clips per language, sherpa-onnx 1.13.7, CPU, 2 threads, greedy)

| Language | Dolphin WER | IC WER | Dolphin CER | IC CER | Wrong-script (Dolphin / IC) | IC word errors / words | Winner |
|---|---|---|---|---|---|---|---|
| hi | 0.3790 | 0.1163 | 0.2066 | 0.0408 | 0 / 0 | 147/1,264 | IC |
| gu | 0.7835 | 0.2022 | 0.5625 | 0.0687 | 11 / 0 | 225/1,113 | IC |
| mr | 0.7072 | 0.2029 | 0.2369 | 0.0623 | 0 / 0 | 212/1,045 | IC |
| ta | 0.7285 | 0.3158 | 0.2721 | 0.1267 | 0 / 0 | 264/836 | IC |
| te | 0.7033 | 0.2103 | 0.2759 | 0.0749 | 0 / 0 | 180/856 | IC |
| or | 0.6788 | 0.2508 | 0.2633 | 0.0734 | 2 / 0 | 242/965 | IC |
| bn | 0.4085 | 0.1504 | 0.1387 | 0.0445 | 0 / 0 | 148/984 | IC |
| kn | n/a | 0.1642 | n/a | 0.0434 | n/a / 0 | 142/865 | report only |
| ml | n/a | 0.2374 | n/a | 0.0656 | n/a / 0 | 188/792 | report only |

Raw: `docs/sih-metrics/raw/task5b-indicconformer/<lang>-results.csv`, `summary.json`, `benchmark-run.log`, `model-files-sha256.csv`; Dolphin: `raw/task5-wer/`.
The 50 clips per language are the same as Task 5 (the scorer refuses to run unless ids and references match). kn_in and ml_in are FLEURS test rows 1 to 50 as well.
Desktop timings were not recorded as results (CPU shared with another agent).

### Phone parity and performance (RMX3392, app's `SpeechEngine.transcribe()` via `am instrument`, 5 clips per language)

| Language | Identical to desktop | Phone WER vs desktop WER (5 clips) | Switch into language | Warm RTF | Peak PSS | Peak VmHWM |
|---|---|---|---|---|---|---|
| hi | 5/5 (4 runs) | 0.0650 / 0.0650 | en to hi 4.3 to 5.3 s; cold load 4.4 s | 0.16 to 0.25 | 862 to 905 MB | 947 to 988 MB |
| gu | 3/5 | 0.1852 / 0.1944 | hi to gu 3.8 s | 0.147 to 0.160 | 895 MB | 979 MB |
| mr | 3/5 | 0.1750 / 0.1625 | hi to mr 3.9 s | 0.146 to 0.155 | 870 MB | 953 MB |
| ta | 3/5 | 0.4677 / 0.5161 | hi to ta 3.9 s | 0.149 to 0.172 | 909 MB | 992 MB |
| te | 5/5 | 0.0870 / 0.0870 | hi to te 3.9 s | 0.148 to 0.154 | 851 MB | 934 MB |
| or | 4/5 | 0.2326 / 0.2326 | hi to or 4.0 s | 0.148 to 0.160 | 859 MB | 943 MB |
| bn | 4/5 | 0.1053 / 0.0947 | hi to bn 3.9 s | 0.148 to 0.163 | 868 MB | 959 MB |

Switch = release the loaded recognizer, then build the next one from `<lang>.onnx` (logcat tag `ITANTRA_PERF_STT`, `loadMs` of the first decode). Peak PSS from `dumpsys meminfo` sampled every second; VmHWM from `/proc/self/status`.
There was no 2x memory transient across switches. Raw: `raw/task5b-indicconformer/phone/` (`<lang>-prime-hi-logcat.txt`, `-meminfo-samples.txt`, `<lang>-results.json`; Hindi in `hi-*`). Phone WER/CER columns are on 5 clips only, a sanity check and not a quality measure.
Method note: the app UI was not driven; the clips go through the app's own speech layer by instrumentation, and the model is read from `files/indicconformer` by path.
