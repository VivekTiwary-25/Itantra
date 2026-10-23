# PART 1 checkpoint (Sonnet agent): IndicConformer vs Dolphin

Written 2026-09-29 while work was in progress. **Nothing from Part 1 is committed.** Branch `stt/indicconformer`
(worktree `D:\projects\SIH\iTantra`) sits at `60d4bc0` (= `origin/Complete-App-V1`), with uncommitted changes listed below.
Never `git add` anything under `.indicconformer-600m\`, `model-staging\`, any `.onnx/.bin/.data`, venv or audio file.

## Status file (`D:\projects\SIH\agent-status.txt`)

Written so far: `PHONE=realme RMX3392 / Android 14 / EILZRCFQLZIZ6H6P`.
**Not written yet** (do not write early): `PART1_SCENARIO=`, `PART1_PHONE_DONE`, `PART1_DONE`.

## Done

- **Step 0**
  - `.gitignore` change (two ignore rules) committed on `UI/UX_discussion` as `e9d27d7`.
  - Branch `stt/indicconformer` created from `origin/Complete-App-V1`. Deviation: local `Complete-App-V1` is checked out in worktree
    `D:\projects\SIH\itantra-complete-v1` (with uncommitted edits, untouched) and is behind origin, so `checkout`+`pull` was not possible.
    Upstream of the new branch was unset so a bare `git push` cannot hit `Complete-App-V1`.
  - `docs/sih-metrics/RESULTS.md` and `tools/measure_wer.py` confirmed present.
  - SHA-256 of all 30 model files: `docs/sih-metrics/raw/task5b-indicconformer/model-files-sha256.csv`
    (encoder `shared/encoder.weights.bin` 651,952,128 B, sha `083b3ee0...e444e09`; each `<lang>.onnx` about 5.05 MB).
  - venv `D:\projects\SIH\iTantra\.venv-sih-metrics` (Python 3.12; sherpa-onnx **pinned 1.13.7** to match Task 5, jiwer, datasets,
    soundfile, onnxruntime, onnx, huggingface_hub).
  - Phone: realme RMX3392, Android 14 (SDK 34), arm64-v8a, MediaTek mt6877, 8 cores, MemTotal 7,721,024 kB, about 2.2 GB available at test time.
    An emulator (`emulator-5554`) is also attached: ignore it and always pass `-s EILZRCFQLZIZ6H6P`.
- **Step 1.1, Hindi only**: IndicConformer (sherpa-onnx `from_nemo_ctc`, CPU, 2 threads, greedy, one recognizer at a time) on the identical 50 FLEURS `hi_in` clips
  (checked equal to the committed Task 5 clip ids and references).
- **Step 1.4, partial (built, not committed)**: app routing to IC for `hi gu mr ta te or bn`, setup script parameter, docs. Whisper base and Dolphin are **not removed yet**.
- **Step 1.5, Hindi only**: phone check passed (below). Other languages not run.

## Numbers so far

| Item | Value | Device / method | Clips | Raw |
|---|---|---|---:|---|
| Hindi WER/CER, IndicConformer | WER 0.1163 (147/1,264 words), CER 0.0408, wrong-script 0 | desktop sherpa-onnx 1.13.7, CPU, 2 threads, greedy | 50 | `raw/task5b-indicconformer/hi-results.csv`, `summary.json` |
| Hindi WER/CER, Dolphin (Task 5) | WER 0.3790 (479/1,264), CER 0.2066, wrong-script 0 | same | 50 | `raw/task5-wer/hi-results.csv` |
| Phone vs desktop transcripts (hi) | **5/5 identical**, both passes in run 1, and again in run 4 | RMX3392, app's `SpeechEngine.transcribe()` via `am instrument` | 5 | `raw/task5b-indicconformer/phone/hi-results-run1.json`, `hi-results-run4.json` |
| en to hi switch time | 5,332 ms (run 1, `loadMs` of first decode), 4,293 ms, 4,591 ms (runs 2, 3). Under the 6 s limit | RMX3392, release Whisper then load IC, file path | 3 runs | `phone/hi-logcat.txt`, `hi-run2-...`, `hi-run3-...` |
| Cold hi load, fresh process | 4,410 ms (`deltaMs` 4,406) | RMX3392 | 1 run | `phone/hi-run4-cold-noprime-logcat.txt` |
| Decode / RTF (warm, ITANTRA_PERF_STT) | decode 0.8 to 3.8 s per clip for 5.1 to 15 s audio; RTF 0.16 to 0.25, later runs 0.17 to 0.19 | RMX3392, 2 threads | 5 clips x 4 runs | phone logcats |
| Peak RAM | PSS 862 to 905 MB (`dumpsys meminfo`, 1 s sampling); VmHWM 947 to 988 MB | RMX3392 | 4 runs | `phone/*-meminfo-samples.txt`, `STTPARITY` lines in logcats |
| APK size (provisional) | 504,947,903 B (481.6 MiB), debug, **still contains** Dolphin, Whisper base and TTS voices | Windows Gradle build | n/a | rebuild after removals |

The first run's early logcat lines were rotated out (small ring buffer); runs 2 to 4 stream logcat to a file.
Do not report desktop speeds as results (the Opus agent used the CPU concurrently).

## BENCHMARK RESULT (finished, exit 0; all nine languages, clips verified identical to the Task 5 set)

Desktop sherpa-onnx 1.13.7, CPU, 2 threads, greedy, 50 FLEURS test clips per language. Raw: `raw/task5b-indicconformer/<lang>-results.csv`, `summary.json`, `benchmark-run.log`;
Dolphin numbers from `raw/task5-wer/summary.json`.

| lang | Dolphin WER | IC WER | Dolphin CER | IC CER | wrong-script Dolphin / IC | IC word errors / words | winner |
|---|---|---|---|---|---|---|---|
| hi | 0.3790 | 0.1163 | 0.2066 | 0.0408 | 0 / 0 | 147/1264 | IC |
| gu | 0.7835 | 0.2022 | 0.5625 | 0.0687 | 11 / 0 | 225/1113 | IC |
| mr | 0.7072 | 0.2029 | 0.2369 | 0.0623 | 0 / 0 | 212/1045 | IC |
| ta | 0.7285 | 0.3158 | 0.2721 | 0.1267 | 0 / 0 | 264/836 | IC |
| te | 0.7033 | 0.2103 | 0.2759 | 0.0749 | 0 / 0 | 180/856 | IC |
| or | 0.6788 | 0.2508 | 0.2633 | 0.0734 | 2 / 0 | 242/965 | IC |
| bn | 0.4085 | 0.1504 | 0.1387 | 0.0445 | 0 / 0 | 148/984 | IC |
| kn (report only) | n/a | 0.1642 | n/a | 0.0434 | n/a / 0 | 142/865 | not in picker |
| ml (report only) | n/a | 0.2374 | n/a | 0.0656 | n/a / 0 | 188/792 | not in picker |

**Step 1.2/1.3 outcome: IC wins all 7 by 19 to 71 WER points, far beyond the 2-point rule, so Scenario A.**
`PART1_SCENARIO=A` has been appended to the status file. `PART1_PHONE_DONE` and `PART1_DONE` are NOT written.
STT_DECISIONS.md, RESULTS.md section, Dolphin/Whisper-base removal, rebuild, phone checks for gu mr ta te or bn, commit and push are all still TO DO (steps 3 to 8 below; skip the benchmark steps 1 and 2).

## Files created / changed (all uncommitted)

Modified:
- `.gitignore` (adds `/model-staging/`)
- `app/src/main/java/speech/SpeechRecognizerManager.kt`: IC (NeMo CTC) routing for the 7 languages, English stays on Whisper tiny.en, IC read from
  `filesDir/indicconformer` by absolute path (`assetManager = null`), one constant `INDIC_CONFORMER_DIR_NAME`, recognizer reused per language,
  release-before-load, missing model logs an error and returns an empty transcript (editor still opens). Whisper base branch and Dolphin branch removed from this file.
- `setup-models.ps1`: new `-IndicConformerModel <dir>` and `-IndicConformerSkipHash`; validates, hash-checks and stages into `model-staging\indicconformer`. Tested, exit 0.

New:
- `docs/STT_INDICCONFORMER.md` (layout, staging, adb push commands)
- `docs/sih-metrics/tools/measure_wer_indicconformer.py` (scorer; imports `measure_wer.py`; adds `kn`, `ml`; needs `--model-dir`)
- `docs/sih-metrics/raw/task5b-indicconformer/` (`environment.json`, `hi-results.csv`, `summary.json`, `model-files-sha256.csv`, `phone/`)
- `app/src/androidTest/java/com/chmod777/itantra/SttParityInstrumentedTest.kt` (scratch instrumentation runner; ask Vivek whether to commit it)
- this file

Ignored / local only: `model-staging/indicconformer` (665 MB), `docs/sih-metrics/raw/task5-wer/dataset/hi/` (WAVs), `docs/sih-metrics/raw/task5-wer/source-parquet/`.

Scratch scripts (not in repo): `C:\Users\Arena\AppData\Local\Temp\claude\D--projects-sih-itantra\0a6af35a-5861-4dec-8cb7-9d2345aab94b\scratchpad\`
(`stream_fleurs.py`, `stream.log`, `dl_fleurs.py`, `hub_probe.py`).

## Background processes

- FLEURS stream (`stream_fleurs.py`) **finished** (exit 0). All eight 50-row parquets are saved in
  `docs/sih-metrics/raw/task5-wer/source-parquet/parquet-data/<cfg>/test-00000-of-00001.parquet` (`gu mr ta te or bn kn ml`), plus the full `hi_in`. Nothing to stop.
- **Benchmark running** (started after the stream finished): `measure_wer_indicconformer.py ... --languages hi gu mr ta te or bn kn ml`, log at
  `docs/sih-metrics/raw/task5b-indicconformer/benchmark-run.log`; it ends with a line `exit: 0` when done and rewrites
  `raw/task5b-indicconformer/summary.json` plus one `<lang>-results.csv` per language. If the log has no `exit:` line and no python process is running, it was interrupted: rerun the same command.

## State on the phone (EILZRCFQLZIZ6H6P)

- `com.chmod777.itantra` (IC build) and `com.chmod777.itantra.test` (instrumentation APK) installed.
- `/data/data/com.chmod777.itantra/files/indicconformer/` holds the full package (697 MB, all 9 languages), plus `files/stt-parity/hi/` with 5 clips and `results-hi.json`.
- Other apps on the phone (`icproto`, `demo.*`) are not mine; leave them.

## Audio pack

`D:\projects\SIH\sih-audio-pack\` (Vivek's friend) had still not arrived at the last check. It is no longer needed for the benchmark, because the stream delivered the
same first-50 FLEURS test rows the Task 5 baseline used (the scorer verifies clip ids and references equal the committed Task 5 CSVs and refuses to score otherwise).
If the pack arrives anyway, tell Vivek the benchmark already ran from FLEURS; do not redo it unless asked.

## Exact next steps

1. Check `raw/task5b-indicconformer/benchmark-run.log` for the final `exit: 0` line. If it is missing and no python is running, rerun (all nine in one run so `summary.json` is complete):
   `.\.venv-sih-metrics\Scripts\python.exe docs\sih-metrics\tools\measure_wer_indicconformer.py --model-dir "D:\projects\SIH\iTantra\.indicconformer-600m\multilingual-adapters" --languages hi gu mr ta te or bn kn ml`
   The scorer re-checks that clips equal the Task 5 set and refuses to score otherwise.
2. Read `raw/task5b-indicconformer/summary.json` for the nine IC results.
3. Build the table (language, Dolphin WER, IC WER, Dolphin CER, IC CER, winner) using the Step 1.2 rules
   (IC wins at 2+ WER points lower; within 2 points, IC wins on CER 2+ lower and wrong-script not higher; ties go to IC; Dolphin kept only if it wins by 2+ WER points).
   `kn` and `ml`: report only, do not add to the picker. Log every decision with its numbers in `docs/sih-metrics/STT_DECISIONS.md` (new file).
4. Decide the scenario. Hindi already favours IC by 26 WER points. Append `PART1_SCENARIO=A|B|C` to the status file.
   - C (Dolphin wins 4+): revert `SpeechRecognizerManager.kt`, write results, append `PART1_PHONE_DONE` and `PART1_DONE`, stop.
   - A/B: continue.
5. Step 1.4 remainder: remove Dolphin and Whisper base from the app if nothing routes to them (Scenario A: `git rm` `app/src/main/assets/dolphin-base-ctc-multi-lang-int8/tokens.txt`
   and `app/src/main/assets/base-tokens.txt`; delete the model binaries from `assets/` so the APK actually shrinks; drop them from `setup-models.ps1` downloads and final verification;
   update `README.md` lines about Dolphin and `measure_wer.py` (add a `--dolphin-dir`, since it reads Dolphin from `assets/`) and mention how to re-fetch Dolphin for the baseline).
   Scenario B: keep the winners' route per language, Dolphin only for languages it wins.
   Then `.\gradlew.bat assembleDebug` and record the final APK size.
6. Step 1.5 for the other languages: reinstall, push the 5 clips of each switched language (`files/stt-parity/<lang>/`), run
   `am instrument ... -e lang <lang> -e prime en -e passes 1 -e limit 5` (see `SttParityInstrumentedTest.kt` header), pull `results-<lang>.json` with
   `cmd /c "adb -s <serial> exec-out run-as com.chmod777.itantra cat files/stt-parity/results-<lang>.json > <out>"` (PowerShell `>` corrupts binary/UTF-8),
   compare with desktop. Stop and report if more than 1 of 5 clips differs from desktop, if it crashes or runs out of memory (state total RAM 7.7 GB), and note a language switch over 6 s as a known issue.
   Stream logcat to a file during runs (the ring buffer is small). Then append `PART1_PHONE_DONE`.
7. Write the "Task 5b — IndicConformer" section (summary first: scenario, languages switched, WER per language Dolphin vs IC, APK size, phone used, phone results, what is PENDING)
   in `docs/sih-metrics/RESULTS.md`. Do not touch `QUANT_*` files.
8. Commit only files you created or changed for this task (explicit paths, no `git add -A`), end the message with
   `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Push with
   `git push -u origin stt/indicconformer`. Do **not** merge into `Complete-App-V1`. Append `PART1_DONE`.

Standing rules: every number needs value, device, method, clip count and raw log path; score only with `measure_wer.py` normalisation; never report percentages
for RED/YELLOW/GREEN status (AGENTS.md); only Vivek marks anything GREEN.

## UPDATE (after the benchmark): progress and current blocker

Done since the table above (all still uncommitted):
- `STT_DECISIONS.md` written (D1 to D9).
- Dolphin and Whisper base removed: `git rm` of `app/src/main/assets/base-tokens.txt` and `.../dolphin-base-ctc-multi-lang-int8/tokens.txt` (staged as deleted); local model binaries deleted from `assets/`;
  Dolphin baseline copy kept in git-ignored `.dolphin-baseline/`; `setup-models.ps1` trimmed (ran clean, exit 0); `README.md` updated; `measure_wer.py` gained `--dolphin-dir`.
- Clean rebuild: `.\gradlew.bat clean assembleDebug` OK. **Final debug APK 323,335,841 B (308.36 MiB).** (The earlier 504.9 MB was stale incremental-packaging bloat, not a real size.)
- `docs/sih-metrics/tools/phone_check_stt.ps1` added: one command runs the six-language phone check and compares with desktop.

**Blocker: at about 15:00 IST the phone (RMX3392, EILZRCFQLZIZ6H6P) was no longer visible to adb or to Windows USB** (`adb kill-server`/`start-server` did not help, no emulator either).
Nothing was installed or pushed in that attempt. The phone still holds the earlier IC model and the Hindi results from the previous session (old build with Dolphin removed only from the routing).

Next: reconnect the phone (cable, unlock, allow USB debugging), then
`.\docs\sih-metrics\tools\phone_check_stt.ps1 -Serial EILZRCFQLZIZ6H6P -Languages gu,mr,ta,te,or,bn -Prime hi`
(if `files/indicconformer` is missing after a reinstall, push it first per `docs/STT_INDICCONFORMER.md`). Then: append `PART1_PHONE_DONE` immediately, write the RESULTS.md "Task 5b" section,
commit explicit paths, `git push -u origin stt/indicconformer`, append `PART1_DONE`. `PART1_PHONE_DONE` has NOT been written.

## UPDATE 2: Q2 (4-bit) switch task, paused while Vivek unplugs the phone (2026-09-29 about 16:45 IST)

Task: switch the app to Q2 (block 32, `quant-ladder\q2-nbits-b32-noq1\package`, 454,123,782 B) only if the phone check shows no crash/OOM/unsupported-op AND warm RTF <= 1.2 x 0.130 = 0.156.

Done, uncommitted:
- Step 1, desktop, 50 test clips per language, `measure_wer_indicconformer.py --model-dir <q2 package> --out raw/task5b-q2 --model-label ...`: `raw/task5b-q2/summary.json`, `<lang>-results.csv`, `benchmark-run.log`.
  WER Q2 vs INT8-B: hi 11.63/11.63, gu 20.22/20.22, mr 20.29/20.29, ta 30.74/31.58, te 22.90/21.03, or 25.39/25.08, bn 15.24/15.04, kn 16.99/16.42, ml 22.85/23.74; mean 20.69 vs 20.56 (+0.14 pts); wrong-script 0 everywhere.
- SHA-256/sizes of the 19 Q2 runtime files: `raw/task5b-q2/model-files-sha256.csv` (encoder weights 433,954,816 B `a9199c84...949fe`; tokens byte-identical to INT8-B).
- Step 2, Q2 phone run (quant harness `com.chmod777.itantra.icquant`, streamed logcat): `raw/task5b-q2/phone/logcat_q2b32_stream.txt`, `results_q2b32.json`.
  Result: no crash, no OOM, no unsupported operator (`ICQ_DONE`). **Overall warm RTF 0.207 (limit 0.156, INT8-B reference 0.130)**, per language 0.18 to 0.20 (mr 0.305).
  Peak RSS 758 MB / PSS 681 MB (INT8-B 972 / 874 MB). Load 3.5 to 5.3 s per language. Parity vs desktop (check-set clips, 5 per language): 18 of 45 clips differ (INT8-B: 8 of 45).
- The first attempt of the phone check stalled about 30 min with no visible cause (logcat had rotated); the streamed re-run worked.

Not finished:
- **Control run**: INT8-B through the same harness right now, to rule out the phone simply being slower this afternoon than when 0.130 was recorded (15:17).
  It was interrupted mid-push at Vivek's request (files partly in `files/pkgs/int8bctl` of the harness app; `phone_check.py` skips files whose SHA already matches).
  Command (PowerShell, from repo root, phone attached):
  `.\.venv-sih-metrics\Scripts\python.exe D:\projects\SIH\iTantra-quant\docs\sih-metrics\tools\quant\phone_check.py --serial EILZRCFQLZIZ6H6P --pkg D:\projects\SIH\iTantra\.indicconformer-600m\multilingual-adapters --name int8bctl --desktop-run D:\projects\SIH\iTantra\.indicconformer-600m\quant-ladder\runs\b0-check --data D:\projects\SIH\iTantra\.indicconformer-600m\quant-ladder\data --apk D:\projects\SIH\iTantra\.indicconformer-600m\quant-ladder\phone-proto\app\build\outputs\apk\debug\app-debug.apk --out D:\projects\SIH\iTantra\docs\sih-metrics\raw\task5b-q2\phone-control-int8b`
  (wake the screen first: `adb shell input keyevent KEYCODE_WAKEUP`; it takes about 8 min; then `adb uninstall com.chmod777.itantra.icquant` to remove the harness).
- Step 3 decision: current data says **do not switch** (RTF 0.207 vs 0.156 limit). If the control INT8-B also reads about 0.19 to 0.21 today, the phone is just slower now: compare Q2 against the control (0.207 / control) instead of the recorded 0.130 and tell Vivek.
  If the control reads about 0.13, Q2 is about 59% slower and stays out: skip step 4.
- Step 5: write the Q2 results and the decision into `RESULTS.md` (Task 5b) and `STT_DECISIONS.md` (D12). Step 6: commit explicit paths (raw/task5b-q2 without model files, the scorer `--model-label` change), `git push -u origin stt/indicconformer`, append `PART1_DONE`.
- The main app (`com.chmod777.itantra`) on the phone is untouched and still holds the INT8-B model in `files/indicconformer`.
