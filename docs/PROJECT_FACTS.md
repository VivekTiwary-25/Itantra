# Project Facts — integration/tts-recovery

This file records repository facts refreshed from `origin` on 2026-09-11. Treat these as current until the repository itself proves otherwise.

## Git

- Repository root on Vivek's machine: `D:/projects/SIH/iTantra`
- Remote: `origin` -> `https://github.com/VivekTiwary-25/Itantra.git`
- Working canonical application branch: `integration/tts-recovery`, tracking `origin/integration/tts-recovery`
- Latest implementation checkpoint: `3688704` (`Speak received messages in their own language`)
- Parent recovery checkpoint: `integration/recovery-pass@c8f8a9b`
- Confirmed integrated baseline: `origin/typed-text-integration@21e28fd`
- A10 is GREEN and committed as `9ab8b22` (`feat(app): complete A10 fake interface wiring`).
- A11–A13 are physically accepted and committed as `eedd486` (`feat(app): complete A11-A13 standalone flows`).
- The accepted post-acceptance Hands-free capture/Message Detail date correction is committed as `36ba653` (`fix(app): share Hands-free capture with PTT`).
- Recovery Bundle A is `020b40a` (delivery-result handling and integrated ACK).
- Recovery Bundle B is `6cabd83` (ISO language metadata and protocol v2).
- Recovery Bundle C is `3730123` (pinned Dolphin provisioning).
- The three recovery bundles are implementation/build evidence only; their phone tests are pending.
- TTS runtime fix `76867a8` records physical standalone English and Hindi synthesis/playback on RMX3392.
- Receive-side TTS wiring is `3688704`; its physical receive-side and full-pipeline tests are not recorded.

## Project structure

- Root Gradle Kotlin DSL project with one Android application module: `:app`
- Kotlin and native Jetpack Compose UI; no XML layout directory and no WebView prototype embedding
- Launcher activity: `app/src/main/java/com/chmod777/itantra/MainActivity.kt`
- `ITantraApp` owns manual screen selection, the editor draft, selected-message state and one shared observable message list.
- Screen destinations are Main, Hands-free, New Message, Logs and Message Detail.
- State is kept locally with Compose `rememberSaveable`; the message list saver preserves text, date, timestamp, direction, read state, language code, transport message ID and delivery state across Activity recreation.
- No ViewModel, repository, database, DI or Navigation Compose architecture has been introduced.
- Real Speech and Bluetooth RFCOMM transport implementations are present in the same app module.

## Implemented standalone app behavior

- Main shows `iTantra`, the dominant `HOLD TO TALK` control, status text, a temporary `PLAY LAST RECORDING` verification control, and Hands-free/Text/Logs tiles.
- Runtime microphone permission and `AudioRecord` capture produce PCM WAV at 16,000 Hz, 16-bit, mono. The latest recording can be played in-app.
- The Hands-free tile opens a dedicated sparse dark/gold screen with `Listening...`, Back and Done. Entering starts real local microphone/WAV capture using the same `PcmRecorder` and `recording.wav` as PTT; Done finalizes that shared recording, while Back finalizes it and returns without opening the editor.
- Text opens the shared New Message editor blank.
- PTT release and Hands-free Done submit the shared WAV to `SpeechEngine` and open the same editor with its final transcript.
- Voice transcripts are editable drafts. Only Send calls the real transport; an outgoing row is appended only after transport reports `Sent`.
- Back from a non-empty editor offers Cancel/Discard; an empty editor leaves normally.
- Logs shows the shared message list newest-first with explicit Sent/Received labels. Rows open a read-only Message Detail screen with stored date and time.
- Opening Logs alone does not change read state. Opening an unread received row marks only that message read; outgoing messages remain read.
- The Main badge is derived from messages where direction is `RECEIVED` and `isRead` is false.

A0–A13 are physically accepted on Vivek's phone and GREEN. This evidence is
preserved and is separate from the recovery branch's pending integration tests.

## Integrated and deferred boundaries

- `SpeechEngine.transcribe(wavFilePath, languageCode)` is integrated. English uses Whisper tiny.en; `hi`, `gu`, `mr`, `ta`, `te`, `or`, and `bn` use the Dolphin base multilingual INT8 model.
- English PTT STT → editable draft → Send → Bluetooth → received Logs is PHYSICALLY VERIFIED at baseline `21e28fd`.
- Hindi Dolphin STT and the English regression were physically checked before the recovery branch; the new multilingual transport metadata path is still UNVERIFIED on phones.
- Hands-free has real local microphone/WAV capture until Done through the shared PTT recorder. VAD, pause segmentation and final continuous Hands-free state integration remain deferred.
- Lane 1 does not assume partial or streaming transcription; Lane 3's eventual decoder/API will determine the result model.
- Bluetooth RFCOMM send/receive is integrated. Protocol v2 carries a two-byte ISO language code; ACK updates matching sent rows to `Delivered`.
- Failed or disconnected sends remain in the editor and are not appended to Logs.
- Standalone RFCOMM connect, repeated delivery, ACK, reconnect and relaunch/reconnect have prior physical evidence, but the recovery branch's ACK UI and protocol-v2 framing still require physical regression tests.
- Received messages are stored and ACKed before `SpeechEngine.speak(text, languageCode)` runs on `Dispatchers.Default`. Only English and Hindi have verified voices; unsupported selector languages do not invoke TTS.
- Receive-side TTS and the full speaker → STT → Bluetooth → audible TTS pipeline remain physically unverified.
- Alert metadata has no agreed cross-lane representation. Maximum-volume, non-interruptible received-alert TTS is deferred until Lane 1/Lane 2 metadata and Lane 3 playback integration are defined.
- `PLAY LAST RECORDING` is test-only and remains until final integration/product cleanup no longer needs it.
- The app uses the checked-in sherpa-onnx 1.13.7 AAR and arm64-v8a ABI filter.
- `.onnx` model binaries remain Git-ignored. `setup-models.ps1` restores Whisper,
  Dolphin and Piper binaries; Dolphin's dated official archive, archive hash,
  model hash and tracked token hash are pinned.

## Android configuration

- Gradle wrapper: 9.6.0
- Android Gradle Plugin: 9.4.0
- Kotlin Compose plugin: 2.2.10
- Compose BOM: 2026.02.01
- Java source/target compatibility: Java 11
- Gradle daemon toolchain requirement: Java 25
- compileSdk: 37
- targetSdk: 37
- minSdk: 26
- applicationId and namespace: `com.chmod777.itantra`

## Build health

Primary Windows command:

`.\gradlew.bat assembleDebug`

The normal debug build is the required verification path. A previous `--offline` attempt failed because the Foojay plugin was not cached; that was not a source failure.

`powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-models.ps1` completed
successfully with all seven ignored model binaries present and verified where
hashes are pinned.

`.\gradlew.bat test assembleDebug` completed successfully after each recovery
bundle. Five JVM tests pass on the final implementation state. The recovery
branch has not been installed or exercised on a phone during this unattended pass.

Commit `76867a8` records physical standalone English and Hindi TTS playback on
RMX3392. No repository evidence records a physical receive-side TTS or full
speech-to-speech two-phone test after `3688704`.

## Connected test phone

- Model: RMX3392
- Android: 14 / API 34
- Primary ABI: arm64-v8a

## Repository policy relevant to future native dependencies

Dependency repositories are controlled from `settings.gradle.kts` with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`. Any future repository addition must follow that settings-level policy.
