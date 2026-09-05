# SHERPA_AAR_UNBLOCKER.md

## Purpose

This is a separate team-lead unblocker, not a Lane 1 rung.

Goal: make the official sherpa-onnx Android runtime available to this Android project so the Speech lane can later integrate ASR/TTS models.

Do **not** integrate an ASR model, TTS model, VAD model, language pack, or speech feature as part of this task.

## Current project facts that matter

- Kotlin / Jetpack Compose Android app
- single `:app` module
- minSdk 26
- compile/target SDK 37
- current device ABI: arm64-v8a
- no NDK/CMake/jniLibs/native dependency setup exists
- no sherpa/ONNX dependency exists
- repositories are controlled from `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS`

## Upstream facts checked 2026-09-05

Official k2-fsa sherpa-onnx Android guidance supports both STT and TTS.

The official documentation recommends using the latest release and says Windows users can use prebuilt Android artifacts rather than building the native libraries from source.

At bundle creation time, the official release page identifies v1.13.7 as the latest release.

The upstream repository maintains an Android AAR module and produces Android AAR artifacts.

Because upstream packaging/dependency examples can evolve, do not guess an old artifact coordinate from memory.

## Integration task procedure

When this task is explicitly activated:

1. Re-check the current official k2-fsa sherpa-onnx Android documentation and latest release.
2. Inspect the current project's `settings.gradle.kts`, version catalog, and `app/build.gradle.kts`.
3. Choose the smallest supported **prebuilt** integration path that:
   - comes from the official k2-fsa project/release path;
   - supports arm64-v8a;
   - supports minSdk 26;
   - avoids building sherpa-onnx from source unless there is no viable prebuilt route;
   - does not add any speech model.
4. Pin the exact sherpa version used.
5. Keep repository configuration compatible with `FAIL_ON_PROJECT_REPOS`.
6. Make only the Gradle/dependency/native-packaging changes needed for the runtime.
7. Run `.\gradlew.bat assembleDebug`.
8. Verify the built APK contains the required arm64-v8a native libraries for the chosen sherpa package.
9. Install/launch on RMX3392 if the device is available and verify there is no startup/linker crash.
10. Report exactly which artifact/version/path was chosen and why.

## Acceptance

This unblocker is GREEN only when:

- the chosen sherpa-onnx runtime is pinned and reproducible;
- `assembleDebug` succeeds;
- required arm64-v8a native libraries are packaged;
- the app installs/launches on the physical arm64-v8a phone without a native-linker/startup crash.

No transcription or synthesis result is required for this unblocker.

## Non-goals

Do not:

- download or bundle speech models;
- implement `transcribe`;
- implement `speak`;
- implement VAD;
- change Lane 1 UI;
- refactor the app architecture;
- change SDK/JDK/Kotlin/AGP versions unless absolutely required and explicitly justified.
