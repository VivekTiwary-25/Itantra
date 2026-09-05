# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read this first

This repo already has `AGENTS.md` at the root — it is the authoritative process/working-rules document for this
codebase (read order, scope discipline, build/report rules, device info). **Read it in full before making any
change**, then also read `TASK.md`, which names the single currently-active work item. Do not implement anything
beyond what `TASK.md` describes, even if `docs/LANE_1_APP_AND_CAPTURE.md` (see below) shows it coming later.

`STATUS.md` tracks per-rung status as RED (not demonstrated) / YELLOW (implemented, unverified) / GREEN (physically
verified on device). Only the human (Vivek) upgrades a rung to GREEN, after a real-phone test — a successful build
is never sufficient. Check this file to see what's actually already working versus merely coded.

Note: `AGENTS.md` points to `docs/UI-Reference/ITANTRA_UI_HANDOFF.md`, but the real path has one more directory
segment: `docs/UI-Reference/itantra-ui-reference/ITANTRA_UI_HANDOFF.md`.

## What this project is

iTantra is a Smart India Hackathon prototype for offline multilingual speech communication over low-bandwidth local
links (no cell tower, no internet). The core idea: don't send voice (huge), send text (tiny, ~900:1 smaller), and
re-synthesize speech on the receiving end:

```
Speech → ASR → Text → Bluetooth/Relay → Text → TTS
```

The full system is split into three independent lanes/branches that only integrate later:

- **`lane/app`** (this branch) — App & Capture. Owns the screen, push-to-talk, mic capture, WAV creation, message
  UI, typed fallback. This is the only lane implemented in this working tree.
- **`lane/transport`** — Bluetooth Classic discovery/connection and phone-to-phone(-to-phone) relay.
- **`lane/speech`** — sherpa-onnx runtime, Whisper ASR (WAV → text), TTS (text → speech).

**The Lane 1 (this branch) contract**, defined in `docs/CONTRACTS.md` / `docs/LANE_1_APP_AND_CAPTURE.md`:
- Produces one WAV file per utterance: **16,000 Hz, 16-bit, mono, PCM**. This format is non-negotiable — the ASR
  models are trained on it and will silently produce garbage on anything else. Handoff to Speech is a file path.
- Accepts a string (+ language code) to display and to hand to Speech for TTS.
- Talks to the other lanes only through function calls it never looks inside:
  `transcribe(wavFilePath): String`, `speak(text, languageCode)`, `sendMessage(text)`, `onMessageReceived(callback)`.
  Until Lanes 2/3 exist for real, these are expected to be **fake/hardcoded stubs** — do not build real Bluetooth,
  STT, TTS, VAD, or packet-format logic in this lane.

## Rung-based workflow (important — this is how work is scoped here)

Lane 1 work proceeds one acceptance-tested "rung" at a time (A0 → A12), fully specified in
`docs/LANE_1_APP_AND_CAPTURE.md` (Part 5). Each rung has an exact physical test that must be performed on a real
phone, not just a successful build. `TASK.md` names the one active rung — implement only that rung's behavior, make
the smallest coherent change, and stop; do not pull forward later-rung functionality (mic permission, recording,
WAV, message list, transport, fake interfaces, etc.) just because the finished UI reference shows it.

Before any UI change, read `docs/UI-Reference/itantra-ui-reference/ITANTRA_UI_HANDOFF.md`. The interactive
prototype under `docs/UI-Reference/itantra-ui-reference/dist/` is the visual/motion reference only — recreate it
natively in Jetpack Compose, never embed it via WebView, and only implement the slice of it that belongs to the
current rung.

Connected physical test device: RMX3392, Android 14 (API 34), arm64-v8a.

## Commands

Primary Windows build/verification command (do not use `--offline` as the default path — it has previously failed
here on an uncached Foojay plugin, which is not a source problem):

```
.\gradlew.bat assembleDebug
```

Unit tests:

```
.\gradlew.bat test
.\gradlew.bat test --tests "com.chmod777.itantra.ExampleUnitTest"
```

Instrumented tests (requires a connected device/emulator):

```
.\gradlew.bat connectedAndroidTest
```

## Architecture

- Single-module Android project: root Gradle Kotlin DSL build, one `:app` module. No multi-module split exists.
- Kotlin + Jetpack Compose only — there is no XML layout directory and no plan to add one.
- Entry point: `app/src/main/java/com/chmod777/itantra/MainActivity.kt`. The whole UI is currently one
  `ComponentActivity` calling `setContent` with one screen composable (`HoldToTalkScreen`). There is no
  navigation graph, ViewModel, repository, or domain layer — don't introduce one preemptively; the lane spec
  explicitly stops advancing once its acceptance ladder is green.
- Theming lives in `app/src/main/java/com/chmod777/itantra/ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`), applied
  via the `SIH_iTantraTheme` wrapper.
- Package/namespace/applicationId: `com.chmod777.itantra`. Preserve this unless explicitly told to change it.
- minSdk 26, compileSdk/targetSdk 37, Java 11 source/target compatibility, Kotlin 2.2.10, AGP 9.4.0, Gradle wrapper
  9.6.0. Do not bump SDK/Gradle/Kotlin/JDK versions without an explicit task reason.
- `settings.gradle.kts` locks dependency repos with `RepositoriesMode.FAIL_ON_PROJECT_REPOS` (google() + mavenCentral()
  only) — new dependencies go through the version catalog `gradle/libs.versions.toml`, not ad hoc project-level
  repositories.
- Speech model files (`.onnx`, `.bin`, model dirs) are intentionally git-ignored — never commit them.
- `docs/SHERPA_AAR_UNBLOCKER.md` describes a separate, explicitly-scoped team-lead task (making the sherpa-onnx
  Android AAR available for later Speech-lane integration) — only act on it when a task explicitly activates it,
  and even then it excludes any model/ASR/TTS/VAD implementation.

## Shared files across lanes

`AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, and `settings.gradle.kts` affect the
other lanes/branches too. Coordinate before making large or unusual changes to these.
