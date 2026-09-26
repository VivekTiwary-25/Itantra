# RUN 2 — phone checklist

All items below are **PENDING PHONE** unless explicitly labeled as a preflight gate. RUN 1 used no phone or `adb`.

## Preflight gates before any candidate phone test

1. A speaker of each language checks and approves the ten native-script lines in that candidate worktree's `tts-research/inputs/<code>.txt`. Freeze the approved file and record its SHA-256. The RUN 1 lines are drafts; do not score them as approved text.
2. Give a listener who has not seen the text the matching ten retained WAVs. Record the meaning, number, location and action heard for each. The handoff requires at least 9/10 meanings and all three actionable details before `PASS TO PHONE`. Until then, mark **QUALITY UNVERIFIED — listener needed**.
3. Resolve the relevant license and packaging gate. Indic-TTS checkpoint terms are separately UNKNOWN despite MIT code. Meta MMS requires the team's CC BY-NC 4.0 decision before any fallback work. Piper Arjun's dataset attribution terms need review.
4. Establish a working offline Android inference path in the candidate's own `BASE_SHA` worktree, then wire that candidate through the existing `SpeechEngine.speak(text, languageCode)`/`TtsHelper` interface and package the hashed assets. Arjun has a ready sherpa-onnx ONNX package but is not yet wired. Indic-TTS FastPitch/HiFi-GAN lacks a validated dynamic acoustic ONNX and existing-runtime path; the current export fails on a second text length. Tamil, Telugu and Odia also need a verified frontend for their discarded native characters. Build with the documented SDK/JDK/temp workaround and run `.\gradlew.bat test` and `.\gradlew.bat assembleDebug` before installing. Do not combine candidate branches or mark a language DONE at this stage.
5. Record the candidate APK exact bytes, each packaged model/support-file byte count, and asset hashes. The team must set a storage budget before accepting a large model.

## Demonstration handset and full receive path

1. Use the intended receiving handset (previously RMX3392, Android 14/API 34, arm64-v8a) and a second phone or sender that can deliver the test messages through iTantra's existing local Transport path. Record actual device and build IDs. Do not change device settings beyond those needed for this test.
2. Disable usable Internet on the receiver while leaving the local radio required for iTantra enabled. Install the candidate APK and verify it launches and loads model assets entirely offline. Record installed app bytes and copied model bytes in app-private storage.
3. Before each new-language candidate, run the established English and Hindi received-text-to-audio checks on the handset. Preserve their exact text, result and any error. Repeat both after the candidate's tests. These regressions are **PENDING PHONE**.
4. Send each of the ten approved native-script messages to the receiver **twice** through the real receive path, with its ISO code. Confirm the received text and language metadata enter Logs unchanged, are ACKed, and `SpeechEngine.speak` runs for that language. Record the spoken WAV/recording or listener notes and any crash; do not use the temporary direct Test TTS control as the acceptance path.
5. Send one representative message **twenty times** through the same path. Record failures and recovery behavior. Do not send generated audio over Transport; only text and language metadata should cross the link.
6. For each run, record model load time, received-text-to-audible-start, synthesis time, audio duration, and `RTF = synthesis time / audio duration`. Record memory before load, after load and peak during synthesis (state the metric, such as process PSS and Java/native heap). Report median warm RTF and median start latency, plus raw per-run values and any crash. Record actual APK and model bytes alongside the performance log.
7. Have a blind listener write down what was heard, including the three actionable details. Apply the frozen review gates: at least 9/10 intended meanings, all three actionable details, zero failures in the twenty repeats, median warm RTF ≤1.0, and median text-to-audible-start ≤3 seconds. Any change to these review settings must occur before tests start.
8. Mark only measured outcomes PASS or FAIL. Keep all unrun phone cells as **PENDING PHONE** and all unreviewed audio as **QUALITY UNVERIFIED**. Do not claim a language works from file generation, APK build, selector presence or one playback.

## Candidate order to resume

1. Malayalam Piper Arjun is the only tested candidate with an existing sherpa-onnx ONNX package and no observed desktop frontend error. It still needs speaker approval, blind listening, app wiring/build and the phone sequence above. Do not advance to Meera solely because its listener result is missing.
2. Bengali, Gujarati, Marathi and Kannada Indic-TTS produced ten WAVs each, but their acoustic ONNX graphs failed with different text lengths. Resolve that runtime proof and weight terms before any phone run. Their original Rasa alternatives require weight access.
3. Tamil, Telugu and Odia Indic-TTS produced files but discarded native characters in the fixed draft inputs, including the number message. Resolve and re-run the **same input text** through a documented frontend before any phone run.
4. Meta MMS fallbacks remain behind the CC BY-NC 4.0 team decision. If permitted and reached under the queue stop rules, use a new worktree per candidate from `BASE_SHA`, verify the original weights/license, run the ten desktop inputs and ONNX harness, and only then follow the phone sequence.

The per-message desktop measurements and exact retained WAV paths are in `DESKTOP_RESULTS.csv`; branch/worktree decisions and model hashes are in `OG.md` and `TTS_LOG.md`.
