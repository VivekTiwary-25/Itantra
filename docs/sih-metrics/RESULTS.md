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
- **Languages switched to IC**: hi, gu, mr, ta, te, or, bn, plus kn and ml (no Dolphin baseline; added to the picker at Vivek's request).
- **Final debug APK**: 323,609,847 B (308.62 MiB) (323,335,841 B before kn/ml were added to the picker), from a clean-built base and `gradlew assembleDebug`. IC itself (about 697 MB) is not in the APK; it is pushed to the phone's private files folder (`docs/STT_INDICCONFORMER.md`).
- **Phone**: realme RMX3392, Android 14, arm64-v8a, MediaTek mt6877, 7.7 GB RAM (`EILZRCFQLZIZ6H6P`).
- **Phone result: NOT PASSED under the stop rule.** Transcripts differ from desktop in more than 1 of 5 clips for gu (2), mr (2) and ta (2); hi and te 0, or 1, bn 1.
  The differences are single-character or matra flips (INT8 kernels on arm64 differ slightly from x86, as the earlier prototype also saw). On these 5 clips the phone WER is within about 5 points of desktop and sometimes better.
  No crash, no out-of-memory, no language switch over 6 s. **Accepted by Vivek** as arm64 vs x86 rounding: phone output differs from desktop by single characters on some clips; phone WER on the 5-clip spot check is within a few points of desktop, in both directions.
- **kn and ml added to the language picker** at Vivek's request (see the kn/ml phone checks below).
- **PENDING**: GREEN status (only Vivek); merge of `stt/indicconformer` (not merged, review first).

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
| kn | 5/5 (2 runs) | 0.0820 / 0.0820 | hi to kn 3.4 s (re-run); 7.2 s on the first run after reinstall, see note | 0.129 to 0.138 (re-run); 0.135 to 0.314 first run | 848 MB | 937 MB |
| ml | 4/5 | 0.2535 / 0.2535 | hi to ml 4.0 s | 0.160 to 0.238 | 912 MB | 994 MB |

Switch = release the loaded recognizer, then build the next one from `<lang>.onnx` (logcat tag `ITANTRA_PERF_STT`, `loadMs` of the first decode). Peak PSS from `dumpsys meminfo` sampled every second; VmHWM from `/proc/self/status`.
There was no 2x memory transient across switches. Raw: `raw/task5b-indicconformer/phone/` (`<lang>-prime-hi-logcat.txt`, `-meminfo-samples.txt`, `<lang>-results.json`; Hindi in `hi-*`). Phone WER/CER columns are on 5 clips only, a sanity check and not a quality measure.
Method note: the app UI was not driven; the clips go through the app's own speech layer by instrumentation, and the model is read from `files/indicconformer` by path.

**kn/ml note.** The first kn run happened right after reinstalling the app and its switch took 7,196 ms, over the 6 s line (known issue candidate); a re-run in the same session took 3,433 ms with warm RTF 0.13,
so the slow case was a cold file cache after reinstall, not steady behaviour. First-run log: `raw/task5b-indicconformer/phone/kn-prime-hi-run1-coldcache-logcat.txt`. The kn/ml phone check was run on the build that has them in the picker;
the picker UI itself was not exercised by hand (no UI driving), only the speech layer with `-e lang kn|ml`.
**Parity note (accepted by Vivek):** phone output differs from desktop by single characters on some clips; phone WER on the 5-clip spot check is within a few points of desktop, in both directions.

### Q2 (4-bit, block 32) evaluated, not shipped

Requested by Vivek: switch to Q2 (`stt/ic-quant` package `q2-nbits-b32-noq1`, 454,123,782 B) if the phone shows no crash/OOM/unsupported operator and warm RTF is at most 20% slower than INT8-B (0.130). **Result: the app stays on INT8-B (697.42 MB).**
Reason: Q2's warm RTF is 0.207 (limit 0.156). Full reasoning: `docs/sih-metrics/STT_DECISIONS.md` D12.

Desktop WER on the 50 FLEURS test clips per language (same clips and normalisation as above; Q2 raw: `raw/task5b-q2/<lang>-results.csv`, `summary.json`, `benchmark-run.log`):

| Language | Dolphin WER | INT8-B WER (ships) | Q2 WER | Q2 vs INT8-B | Q2 CER | Q2 wrong-script | Q2 word errors / words |
|---|---|---|---|---|---|---|---|
| hi | 0.3790 | 0.1163 | 0.1163 | +0.00 pts | 0.0414 | 0 | 147/1,264 |
| gu | 0.7835 | 0.2022 | 0.2022 | +0.00 pts | 0.0693 | 0 | 225/1,113 |
| mr | 0.7072 | 0.2029 | 0.2029 | +0.00 pts | 0.0570 | 0 | 212/1,045 |
| ta | 0.7285 | 0.3158 | 0.3074 | -0.84 pts | 0.1233 | 0 | 257/836 |
| te | 0.7033 | 0.2103 | 0.2290 | +1.87 pts | 0.0773 | 0 | 196/856 |
| or | 0.6788 | 0.2508 | 0.2539 | +0.31 pts | 0.0781 | 0 | 245/965 |
| bn | 0.4085 | 0.1504 | 0.1524 | +0.20 pts | 0.0445 | 0 | 150/984 |
| kn | n/a | 0.1642 | 0.1699 | +0.58 pts | 0.0457 | 0 | 147/865 |
| ml | n/a | 0.2374 | 0.2285 | -0.88 pts | 0.0636 | 0 | 181/792 |
| mean of 9 | | 0.2056 | 0.2069 | +0.14 pts | | | |

Phone (RMX3392, quant harness, 5 clips per language, 9 languages in turn, 2 threads; raw `raw/task5b-q2/phone/` and `raw/task5b-q2/phone-control-int8b/`):

| | Q2 | INT8-B control (same harness, same afternoon) | INT8-B recorded by the quant agent |
|---|---|---|---|
| model size | 454.12 MB | 697.42 MB | 697.42 MB |
| warm RTF (overall) | **0.207** | **0.128** | 0.130 |
| peak RSS / PSS | 758 / 681 MB | 870 / 843 MB | 972.5 / 873.6 MB |
| load per language | 3.5 to 5.3 s | 3.1 to 4.0 s | 3.3 to 3.9 s |
| clips differing from desktop (of 45) | 18 | 8 | 8 |
| crash / OOM / unsupported operator | none | none | none |

Nothing else changed in the app for this task: the shipped IC model, the model-path constant, `setup-models.ps1` hashes, the pushed phone files and the APK size (323,609,847 B) are as recorded above.
PENDING: Vivek decides whether to revisit Q2 (for example if a faster 4-bit kernel or a different block size is worth trying); GREEN status (only Vivek); merge of `stt/indicconformer`.

## Task 5b add-on: idle resources, real message size, TTS speed (2026-09-29)

Phone for all phone numbers: realme RMX3392, Android 14, arm64-v8a, 7.7 GB RAM (`EILZRCFQLZIZ6H6P`). Build: `stt/indicconformer` at `547186e` plus the seven MMS voices copied into the local (git-ignored) assets folder for the TTS runs
(that measurement APK was 874,855,207 B; the APK size recorded above, 323,609,847 B, is the build without the MMS voices). Model: IndicConformer INT8-B (Hindi for the transcription step).

### 1. Idle CPU and memory (app on the main screen)

Driven through the real UI (`adb shell input`): hold-to-talk for 3 s with language Hindi, then the temporary Test TTS control. CPU from `adb shell top -b -d 5 -n 12` (60 s); memory from `dumpsys meminfo com.chmod777.itantra` (kB).
CPU is in top's units: 100% = one core, 800% = the whole phone.

| State | Avg CPU (12 x 5 s) | TOTAL PSS | TOTAL RSS | SWAP PSS | How reached |
|---|---|---|---|---|---|
| (a) idle, no models loaded | **107.9%** (median 110, range 94.6 to 121) = about 13.5% of the whole phone | 141,234 kB (138 MB) | 158,816 kB (155 MB) | 75,019 kB | fresh start, main screen, no `model load` line in logcat |
| (b) after one transcription | not sampled | **812,715 kB (794 MB)** | **832,540 kB (813 MB)** | 72,287 kB | after one 3 s hold-to-talk in Hindi: IC Hindi loaded in 4,107 ms, decode 360 ms for 2.72 s of audio (RTF 0.132); the audio was silence, so the text was empty |
| (c) after one message spoken | not sampled | 920,732 kB (899 MB) | 447,384 kB (437 MB) | 567,016 kB | after (b), one English Piper sentence (Ryan) spoken; same process, so it includes (b)'s IC model |

Notes:
- The 108% idle CPU is UI work, not model work. A per-thread `top -H` sample on a fresh start shows the main thread at about 60 to 64%, `RenderThread` at 25 to 50% and the GPU backend thread (`mali-cmar-backe`) at about 18 to 20%
  (`raw/task-idle/a2_idle_threads_top.txt`): continuous Compose animation (the Hands-free waveform) on the main screen. The first sample in the series is no lower than the rest.
- (c) is not a clean number. The phone was swapping (SWAP PSS jumped from 72 MB to 567 MB and RSS fell from 833 MB to 447 MB while PSS rose), so model pages were moved to zram between (b) and (c).
  Read (c) as "about 0.9 GB PSS with IC and one TTS voice loaded", not as an exact figure.
- One run each, not repeated. Raw: `raw/task-idle/`.

### 2. Real message size (JVM test, no phone)

`MessageSizeMeasurementTest` builds a real outgoing private message on the default BLE path: real Noise XX link, real end-to-end sealing and signing, real DTN bundle, real fragmentation (test-double links, no radio).
Result file: `raw/task-msgsize/message-size.txt`. Sentences: en "Meet me at the shelter, the water is rising.", hi "आश्रय स्थल पर मिलिए, पानी बढ़ रहा है।", ta "தண்ணீர் உயர்கிறது, தங்குமிடத்தில் என்னைச் சந்திக்கவும்.".

| Language | Text (UTF-8) | Sealed bundle | Link frame carrying it (after Noise) | GATT fragments at MTU 517 (512 B each) | GATT fragments at MTU 23 (20 B each) |
|---|---|---|---|---|---|
| en | 44 B | 1,195 B | 1,227 B | 3 (512 + 512 + 236), 1,260 B on air | 137 (last one shorter), 2,734 B on air |
| hi | 95 B | 1,195 B | 1,227 B | 3, 1,260 B on air | 137, 2,734 B on air |
| ta | 153 B | 1,195 B | 1,227 B | 3, 1,260 B on air | 137, 2,734 B on air |

- The sealed size does not depend on the text. Messages are padded to a fixed bucket (256 / 1,024 / 4,096 B and so on) to hide length. The signed message with its keys and signature already exceeds 256 B, so every short sentence lands in the 1,024 B bucket, and envelope overhead brings it to 1,195 B.
  A text of a few dozen to about 150 bytes costs the same number of bytes on air.
- Each GATT fragment has an 11-byte header (version 1, session id 4, frame id 2, index 2, count 2). The app requests ATT MTU 517, which Android caps at 512-byte attribute values, so 501 payload bytes per fragment; if MTU negotiation fails it falls back to 20-byte fragments (9 payload bytes).
- Other link frames the sender emits around the message (capability and inventory exchanges) add 230 to 390 B and are not part of the message.

Compared with the same sentence spoken. PCM = 16,000 Hz x 2 bytes x duration, where the duration is the phone's own TTS speaking exactly these sentences (warm second run; `raw/task-msgsize/tts_duration_logcat.txt`):

| Language | Spoken duration (TTS) | PCM size | PCM : text | PCM : sealed bundle | PCM : bundle frame (MTU 517) | PCM : bytes on air at MTU 23 |
|---|---|---|---|---|---|---|
| en | 2.380 s (Piper Ryan) | 76,161 B | 1,731 : 1 | 63.7 : 1 | 62.1 : 1 | 27.9 : 1 |
| hi | 2.290 s (Piper Pratham) | 73,288 B | 771 : 1 | 61.3 : 1 | 59.7 : 1 | 26.8 : 1 |
| ta | 3.426 s (MMS Tamil) | 109,632 B | 717 : 1 | 91.7 : 1 | 89.3 : 1 | 40.1 : 1 |

The text-versus-voice ratio (hundreds to one) holds for raw text. Once the message is padded, signed and encrypted for the secure path, its on-air size is about 60 to 90 times smaller than raw PCM speech, not 700 to 1,700 times.
Limitations: the duration is the TTS's, not a human speaker's, so real speech would differ by a modest factor; one run each.

### 3. TTS speed on the phone (app visible, Test TTS control, warm)

The six MMS voice files existed locally (the converted fp16-decoder models in the `itantra-complete-v1` worktree, SHA-256 verified by `setup-models.ps1`), so the runs were done. Each language: one cold speak (model init), then 5 warm speaks of the app's own sample sentence, 4 threads, app in the foreground.
RTF = synthesis time / audio duration. Text-to-audible-start = time from `speak lang=` to `playback start` in the log (synthesis plus queueing; playback then takes the audio duration). Raw logs: `raw/task-tts/<lang>_logcat.txt`; statistics: `raw/task-tts/summary.txt`.

| Language | Median RTF | RTF range | Median text-to-audible-start | Range | Median audio length | First speak (cold, includes model load) |
|---|---|---|---|---|---|---|
| gu | **0.685** | 0.663 to 0.708 | 1,153 ms | 1,113 to 1,188 | 1.62 s | 3,307 ms |
| mr | **0.859** | 0.779 to 0.928 | 1,652 ms | 1,579 to 1,858 | 1.98 s | 3,992 ms |
| ta | **0.925** | 0.910 to 0.947 | 1,366 ms | 1,336 to 1,432 | 1.47 s | 3,641 ms |
| te | **0.919** | 0.857 to 0.969 | 1,657 ms | 1,492 to 1,744 | 1.75 s | 3,755 ms |
| or | **0.974** | 0.833 to 1.065 | 1,343 ms | 1,142 to 1,584 | 1.38 s | 3,712 ms |
| kn | **0.884** | 0.859 to 1.154 | 2,029 ms | 1,924 to 2,679 | 2.28 s | not captured (model was already loaded) |

- All six are close to real time (median RTF 0.69 to 0.97, single runs up to 1.07 and 1.15): synthesis is barely faster than playback for or, ta, te and kn. A first speak after a language switch costs an extra 3.3 to 4.0 s.
- The sentences are the app's short samples (20 to 23 characters), so start times for longer sentences will be longer.
- Kannada: the first automated attempt logged nothing and the second stopped after 3 of 6 taps (the phone's log buffer rotates and taps did not always register). The final Kannada run streamed logcat to a file and completed 6 of 6.
- Not measured: English and Hindi (Piper), Malayalam and Bengali (not requested).

PENDING: a repeat of the idle memory step (c) on a quiet phone.
