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
