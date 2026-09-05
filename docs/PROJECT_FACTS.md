# Project Facts — lane/app

This file records facts confirmed by a repository audit on 2026-09-05. Treat these as current until the repository itself proves otherwise.

## Git

- Repository root on Vivek's machine: `D:/projects/SIH/iTantra`
- Remote: `origin` -> `https://github.com/VivekTiwary-25/Itantra.git`
- Branch: `lane/app`
- Tracking: `origin/lane/app`
- Working tree was clean after build verification

## Project structure

- Root Gradle Kotlin DSL project
- Single Android application module: `:app`
- Kotlin source
- Jetpack Compose UI
- No XML layout directory
- Launcher activity: `app/src/main/java/com/chmod777/itantra/MainActivity.kt`
- Current visible screen is defined directly from `MainActivity.setContent`
- Current visible text at audit time: `Hello Android!`
- No established ViewModel/repository/domain/data/navigation architecture

## Android configuration

- Gradle wrapper: 9.6.0
- Android Gradle Plugin: 9.4.0
- Kotlin Compose plugin: 2.2.10
- Gradle launcher JDK: Amazon Corretto 25.0.2
- Shell Java observed: Temurin 25.0.4
- Java source/target compatibility: Java 11
- Gradle daemon toolchain requirement: Java 25
- compileSdk: 37
- targetSdk: 37
- minSdk: 26
- applicationId: `com.chmod777.itantra`
- namespace: `com.chmod777.itantra`

## Existing app state at audit time

Not present yet:

- microphone permission
- AudioRecord capture
- PCM/WAV handling
- message UI
- transport/Bluetooth/Wi-Fi implementation
- STT/TTS implementation
- fake lane interfaces
- speech model assets
- sherpa-onnx / ONNX runtime dependency
- NDK/CMake/jniLibs/native binaries
- ABI filtering

Model files are intentionally Git-ignored.

## Build health

Normal Windows command:

`.\gradlew.bat assembleDebug`

Confirmed result on 2026-09-05:

`BUILD SUCCESSFUL`

An `--offline` attempt failed because the Foojay plugin was not already cached. That offline failure is not a source-code failure.

## Connected test phone

- Model: RMX3392
- Android 14
- API 34
- Primary ABI: arm64-v8a

## Repository policy fact relevant to future native dependencies

Dependency repositories are currently controlled from settings and use `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.

If a future dependency requires an additional Maven repository, it must be handled consistently with that settings-level policy rather than casually adding a project-level repository.
