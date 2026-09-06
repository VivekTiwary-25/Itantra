# Project Facts — lane/app

This file records repository facts confirmed on 2026-09-06. Treat these as current until the repository itself proves otherwise.

## Git

- Repository root on Vivek's machine: `D:/projects/SIH/iTantra`
- Remote: `origin` -> `https://github.com/VivekTiwary-25/Itantra.git`
- Branch: `lane/app`, tracking `origin/lane/app`
- A10 is GREEN and committed as `9ab8b22` (`feat(app): complete A10 fake interface wiring`).
- A11–A13 are physically accepted and committed as `eedd486` (`feat(app): complete A11-A13 standalone flows`).
- The accepted post-acceptance Hands-free capture/Message Detail date correction is committed as `36ba653` (`fix(app): share Hands-free capture with PTT`).

## Project structure

- Root Gradle Kotlin DSL project with one Android application module: `:app`
- Kotlin and native Jetpack Compose UI; no XML layout directory and no WebView prototype embedding
- Launcher activity: `app/src/main/java/com/chmod777/itantra/MainActivity.kt`
- `ITantraApp` owns manual screen selection, the editor draft, selected-message state and one shared observable message list.
- Screen destinations are Main, Hands-free, New Message, Logs and Message Detail.
- State is kept locally with Compose `rememberSaveable`; the message list has a saver that preserves text, date, timestamp, direction and read state across Activity recreation.
- No ViewModel, repository, database, DI or Navigation Compose architecture has been introduced.

## Implemented standalone app behavior

- Main shows `iTantra`, the dominant `HOLD TO TALK` control, status text, a temporary `PLAY LAST RECORDING` verification control, and Hands-free/Text/Logs tiles.
- Runtime microphone permission and `AudioRecord` capture produce PCM WAV at 16,000 Hz, 16-bit, mono. The latest recording can be played in-app.
- The Hands-free tile opens a dedicated sparse dark/gold screen with `Listening...`, Back and Done. Entering starts real local microphone/WAV capture using the same `PcmRecorder` and `recording.wav` as PTT; Done finalizes that shared recording, while Back finalizes it and returns without opening the editor.
- Text opens the shared New Message editor blank.
- PTT release and the Hands-free temporary Done path use the hardcoded `this is a test message` transcript and open the same editor pre-filled.
- Voice transcripts are editable drafts. Only Send calls the fake `sendMessage` and appends one timestamped outgoing message.
- Back from a non-empty editor offers Cancel/Discard; an empty editor leaves normally.
- Logs shows the shared message list newest-first with explicit Sent/Received labels. Rows open a read-only Message Detail screen with stored date and time.
- Opening Logs alone does not change read state. Opening an unread received row marks only that message read; outgoing messages remain read.
- The Main badge is derived from messages where direction is `RECEIVED` and `isRead` is false.

A0–A13 are physically accepted on Vivek's phone and GREEN.

## Fake and integration-deferred boundaries

- `transcribe(wavFilePath)` still returns a hardcoded final transcript. Real PTT STT waits on Lane 3.
- Hands-free now has real local microphone/WAV capture until Done through the shared PTT recorder. VAD, pause segmentation, real STT and final Speech state integration still wait on Lane 3.
- Lane 1 does not assume partial or streaming transcription; Lane 3's eventual decoder/API will determine the result model.
- `sendMessage`, `speak` and `onMessageReceived` are still fake/no-op stand-ins. Real message movement waits on Lane 2; real STT/TTS waits on Lane 3.
- Alert metadata has no agreed cross-lane representation. Maximum-volume, non-interruptible received-alert TTS is deferred until Lane 1/Lane 2 metadata and Lane 3 playback integration are defined.
- `PLAY LAST RECORDING` is test-only and remains until final integration/product cleanup no longer needs it.
- There are no speech model assets, sherpa-onnx/ONNX runtime dependencies, NDK/CMake integration, `jniLibs`, native binaries or ABI filters in the app.
- Model files remain intentionally Git-ignored.

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

`assembleDebug` completed successfully after both the accepted A11–A13 source and post-acceptance correction were finalized. During the interrupted run, `assembleDebugAndroidTest` compiled an attempted Compose flow test, but `connectedDebugAndroidTest` could not run it because the RMX3392/ColorOS build denied `UiAutomation.grantRuntimePermission`. That non-runnable test addition was removed; the original app-context instrumented smoke test remains. Physical acceptance, not that failed harness, is the GREEN evidence.

## Connected test phone

- Model: RMX3392
- Android: 14 / API 34
- Primary ABI: arm64-v8a

## Repository policy relevant to future native dependencies

Dependency repositories are controlled from `settings.gradle.kts` with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`. Any future repository addition must follow that settings-level policy.
