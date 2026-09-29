# STT decisions log (Task 5b: IndicConformer vs Dolphin)

Every decision has the numbers that caused it. Scoring: `docs/sih-metrics/tools/measure_wer.py` normalisation (via `measure_wer_indicconformer.py`),
50 FLEURS test clips per language (the same clips as Task 5; the scorer verifies clip ids and references equal the committed Task 5 CSVs),
desktop sherpa-onnx 1.13.7, CPU, 2 threads, greedy. Raw: `raw/task5-wer/summary.json` (Dolphin), `raw/task5b-indicconformer/summary.json` and `<lang>-results.csv` (IC).

## D1. Data source for the benchmark (2026-09-29)

Full FLEURS parquet downloads ran at about 230 KB/s (about 300 MB per language), so only the first 50 rows of each test parquet were streamed over HTTP range
requests and saved as 50-row parquets. `hi_in` was downloaded in full earlier. The audio pack from Vivek's friend never arrived and was not needed.
The scorer refused to run unless the 50 clips equalled the Task 5 clips (ids and reference text): it passed for all seven baseline languages.

## D2. Winner rule

IC wins if its WER is at least 2 points lower than Dolphin's. Within 2 points: IC wins on CER at least 2 points lower and wrong-script not higher; otherwise tie, and ties go to IC.
Dolphin is kept only where it beats IC by at least 2 WER points.

## D3. Per-language winner

| lang | Dolphin WER | IC WER | WER gap (points) | Dolphin CER | IC CER | wrong-script Dolphin / IC | winner |
|---|---|---|---|---|---|---|---|
| hi | 0.3790 | 0.1163 | 26.3 | 0.2066 | 0.0408 | 0 / 0 | IC |
| gu | 0.7835 | 0.2022 | 58.1 | 0.5625 | 0.0687 | 11 / 0 | IC |
| mr | 0.7072 | 0.2029 | 50.4 | 0.2369 | 0.0623 | 0 / 0 | IC |
| ta | 0.7285 | 0.3158 | 41.3 | 0.2721 | 0.1267 | 0 / 0 | IC |
| te | 0.7033 | 0.2103 | 49.3 | 0.2759 | 0.0749 | 0 / 0 | IC |
| or | 0.6788 | 0.2508 | 42.8 | 0.2633 | 0.0734 | 2 / 0 | IC |
| bn | 0.4085 | 0.1504 | 25.8 | 0.1387 | 0.0445 | 0 / 0 | IC |

Every gap is far above the 2-point threshold; the CER rule was never needed. No language is a tie and Dolphin wins none.

## D4. Scenario A (IC wins or ties in all 7)

Per the rule for Step 1.3: IC for all seven Indian languages, Dolphin removed from the app (about 99 MB, `model.int8.onnx` 103,729,802 B), English stays on Whisper tiny.en.
Scenario B/C were not triggered: Dolphin wins 0 of 7 (C needs 4 or more). `PART1_SCENARIO=A` was appended to `D:\projects\SIH\agent-status.txt`.

## D5. kn and ml (report only)

| lang | IC WER | IC CER | wrong-script | word errors / words |
|---|---|---|---|---|
| kn | 0.1642 | 0.0434 | 0 | 142 / 865 |
| ml | 0.2374 | 0.0656 | 0 | 188 / 792 |

Initially report-only. **Vivek then decided to add both to the language picker**: `kn` and `ml` are now in `SUPPORTED_LANGUAGES` (`MainActivity.kt`) and in `indicConformerLanguages`
(`SpeechRecognizerManager.kt`), the setup script treats their IC files as required, and the Test TTS list no longer adds Malayalam separately (it now equals the picker list). There is no Dolphin baseline for them.
TTS for these two is unchanged: Malayalam uses the bundled Piper Arjun voice; Kannada uses the optional MMS voice, and `canSpeak` reports when it is not installed.
Their phone checks are in D11.

## D6. Whisper base removed

Before this change, `SpeechRecognizerManager` routed every code other than `en` and the seven Dolphin languages to Whisper base (`hi` language hint). The app's picker only offers `en` plus the seven, so nothing reachable routes to it.
After the swap, an unknown language code returns an empty transcript and logs an error (no crash, editor still opens), so Whisper base
(`base-encoder.int8.onnx` 29,120,534 B, `base-decoder.int8.onnx` 130,672,026 B, `base-tokens.txt`) was removed from the app and from `setup-models.ps1`. Saves about 152 MB.

## D7. Model location and loading

IC is read from `filesDir/indicconformer` by absolute path (`assetManager = null`), not from APK assets, following the earlier Android prototype (no transient 2x memory spike). The folder name is the single constant
`SpeechRecognizerManager.INDIC_CONFORMER_DIR_NAME`. Per-language wrapper ONNX files reference one shared 652 MB weight file, so a language switch releases the old recognizer then loads the new wrapper.
Two sessions alive at once would need about 1.3 GB, so release always precedes load.

## D8. Dolphin baseline reproducibility

`measure_wer.py` read Dolphin from `app/src/main/assets`. Since Dolphin is no longer an app asset, the script gets a `--dolphin-dir` option, and the local baseline copy was moved to `.dolphin-baseline/`
(git-ignored). The committed Task 5 CSVs remain the record of Dolphin's numbers.

## D9. Hindi phone check (RMX3392, Android 14, 7.7 GB RAM)

5 of 5 transcripts identical to desktop through the app's `SpeechEngine.transcribe()` (instrumentation), so the "differs in more than 1 of 5" stop rule was not triggered. Switch en to hi 4.3 to 5.3 s (under the 6 s limit),
peak PSS 862 to 905 MB, no crash. The other six languages' phone results are recorded in `RESULTS.md` (Task 5b section).

## D10. Phone vs desktop parity: accepted by Vivek

The Step 1.5 stop rule (more than 1 of 5 clips differing from desktop) tripped for gu (2 of 5), mr (2 of 5) and ta (2 of 5); hi 0, te 0, or 1, bn 1. Vivek reviewed the transcript diffs and accepted them as
arm64 vs x86 INT8 rounding: phone output differs from desktop by single characters on some clips; phone WER on the 5-clip spot check is within a few points of desktop, in both directions.
Numbers behind it (5 clips each, phone WER / desktop WER): hi 0.0650 / 0.0650, gu 0.1852 / 0.1944, mr 0.1750 / 0.1625, ta 0.4677 / 0.5161, te 0.0870 / 0.0870, or 0.2326 / 0.2326, bn 0.1053 / 0.0947.
No crash, no out-of-memory, no language switch over 6 s. Decision: proceed to push `stt/indicconformer` (not merged into `Complete-App-V1`).

## D11. kn and ml phone checks (RMX3392, new build with both in the picker)

5 clips each, through the app's `SpeechEngine`, `-e prime hi` (IC to IC switch). kn: 5/5 identical to desktop (two runs), phone WER 0.0820 = desktop 0.0820; ml: 4/5 identical (one single-character flip),
phone WER 0.2535 = desktop 0.2535. Peak PSS kn 848 MB / ml 912 MB, no crash. Switch into ml 4.0 s; switch into kn 3.4 s on the re-run but **7.2 s on the first run after reinstalling the app**
(cold file cache), which is over the 6 s limit: recorded as a known issue for the first switch after a fresh install; not reproducible on the re-run. Both are within the accepted parity category (D10).
Final debug APK with the picker change: 323,609,847 B (308.62 MiB).
