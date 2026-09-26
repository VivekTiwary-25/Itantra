# Bengali MMS-TTS on Android — technical validation

Date 2026-09-26, branch `codex/tts-research-handoff`, RMX3392 (MediaTek mt6877, 2x2.5 GHz + 6x2.0 GHz, Android 14).
**QUALITY UNVERIFIED — nobody has listened. Not GREEN. Performance FAILS the frozen RUN2 gates (see below).**

## Integration (existing architecture, no new runtime)
`"bn"` -> `Voice("vits-mms-ben", "model.onnx", usesEspeak = false, numThreads = 4)` in `TtsHelper`; `Voice` gained `usesEspeak` (MMS uses sherpa's
character frontend: empty `dataDir`, no espeak copy) and `numThreads` (default 2 for all other voices, unchanged). Assets:
`vits-mms-ben/{model.onnx (git-ignored), tokens.txt (LF), config.json, MODEL_CARD}`. `setup-models.ps1 -MmsBengaliModel <path>` installs/hash-checks the
converted model (no official prebuilt exists; without it the step warns and Bengali is skipped). License CC BY-NC 4.0, see `MMS_BN_CONVERSION.md` and `MODEL_CARD`.

## Sizes
- model.onnx 114,065,046 B (fp32, not quantized); on-device model dir 111,524 KB.
- APK: 502,698,082 B (with Malayalam, checkpoint a97b761) -> 608,503,353 B. **+105,805,271 B.** (Baseline without ml was 434,387,066 B; total vs. baseline +174,116,287 B.)

## Phone results (final APK, logs `android/final_logcat.log`; bn had assets reinstalled first)
- Loads and speaks; 22 Bengali syntheses in the final run (10 sentences x2 passes + cold + 1), no crash/error/exception (0 FATAL/AndroidRuntime/failed lines).
- Text integrity: codepoints received by TtsHelper equal the input file's (44,37,28,43,51,45,32,27,34,44) in the cold run and both passes. The danda `।` and Bengali digits `১২` are dropped by the model's vocab (see conversion doc); pronunciation not judged.
- **Cold, first ever (fresh assets):** 14.1 s to audible start = asset copy 1.9 s + model init 4.1 s + synthesis 8.1 s.
- **Warm:** median RTF **2.46** (2.11-2.73); median text-to-audible-start **6.9 s** (max 9.0 s) for 1.8-3.8 s of audio. Frozen gates are RTF <= 1.0 and start <= 3 s: **FAIL**.
- Thread experiment (same model, warm median RTF): 2 threads 4.39 (`bn_2threads_logcat.log`), 4 threads 2.56 (`bn_4threads_logcat.log`), 6 threads 2.91 (`bn_6threads_logcat.log`). 4 kept. Final-run 4-thread median 2.46.
- Memory (dumpsys meminfo): bn only, after first synthesis: PSS 299,622 KB (native heap 201,848 KB). After bn+en+hi+ml all loaded: PSS 728,244 KB (native heap 624,272 KB).
- Piper voices for comparison on the same phone (this run): English RTF 0.65-0.72, Hindi 0.62-0.68, Malayalam 0.69: unchanged from before, no regression, no errors; regression texts were sent as UTF-8 base64 (Hindi codepoints correct).

## Blocker / open items
1. **Speed**: fp32 MMS VITS is ~2.5x slower than real time on this phone even with 4 threads (Piper is ~0.7x). It is *functionally* working; interactive receive-side speech would lag ~7 s per short sentence.
   Not addressed here by design (quantization deferred). Candidates for the next step, not tried: int8/fp16 quantization, ORT graph optimization, NNAPI/XNNPACK, shorter sentence chunking.
2. No listener validation; danda/digit handling needs a Bengali listener and a decision (ASCII digits are in the vocab; Bengali digits are not).
3. Not exercised: real two-phone receive path, offline test, 20 repeats through Transport, blind-listener gates.
4. Memory: all four voices resident is ~728 MB PSS; consider unloading/lazy policy before shipping.
5. APK is now ~608 MB (debug build); storage budget not set.
