# AUDIT_LANE_3.md — Speech lane, Round 1 prep

Audit date: 2026-09-06. This audit covers only Lane 3 (Speech: ASR, TTS, model/runtime). For the
Lane 1 (App & Capture) rung ladder, see `docs/AUDIT_LANE_1.md` — it already documents A0–A13 and
flags this same speech work as an unresolved cross-lane gap; this document does not repeat that
table.

Tags: [WORKS] = a human ran it on a physical device and saw it. [BUILT] = compiles/exists,
unverified on device. [PLANNED] = intended, no working code.

## 1. State

There is no Lane 3 rung ladder or acceptance-test document anywhere in this repository (no
`docs/LANE_3_*.md` exists — only `docs/LANE_1_APP_AND_CAPTURE.md` does). Everything below is what
this audit found by reading code directly, not by checking off a written spec.

- [BUILT] `lane/speech` (local and `origin/lane/speech`) exists as a branch but has never been
  developed: its tip is 3 commits deep (`1777a40`), identical in substance to the freshly
  initialized project on `main`. No Speech-lane code has ever been committed there.
- [BUILT] All real Speech-lane code instead lives as **uncommitted, untracked files** sitting
  directly in `lane/app`'s working tree: `app/src/main/java/speech/SpeechEngine.kt`,
  `app/src/main/java/speech/SpeechRecognizerManager.kt`,
  `app/src/main/java/com/chmod777/itantra/TtsHelper.kt`. `git status` shows them as `??` — no
  commit, anywhere, contains them.
- [BUILT] ASR: `SpeechRecognizerManager` wraps sherpa-onnx's `OfflineRecognizer` over two
  int8-quantized Whisper model pairs — `tiny.en` for `language == "en"`, `base` (multilingual) for
  `language == "hi"`. Both model pairs and their token files are present in
  `app/src/main/assets/`.
- [BUILT] TTS: `TtsHelper` wraps sherpa-onnx's `OfflineTts` over two Piper VITS models —
  `vits-piper-en_US-ryan-medium` and `vits-piper-hi_IN-pratham-medium` — both present in
  `app/src/main/assets/` with their own `tokens.txt`, `.onnx.json`, and `espeak-ng-data` folder.
  Playback is raw `AudioTrack` (`MODE_STATIC`, `ENCODING_PCM_FLOAT`), not `MediaPlayer`.
- [BUILT] `speech.SpeechEngine` is the facade Lane 1 actually calls: `transcribe(wavFilePath,
  languageCode): String`, `speak(text, languageCode)`, `release()`. It reads the WAV's sample rate
  straight out of the 44-byte header and hand-decodes 16-bit PCM to `FloatArray` itself (no WAV
  library).
- [BUILT] Runtime dependency: `com.k2fsa.sherpa.onnx` classes come from a local
  `app/libs/sherpa-onnx-1.13.7.aar` (49,113,869 bytes), referenced via
  `implementation(files("libs/sherpa-onnx-1.13.7.aar"))` in `app/build.gradle.kts`, plus a new
  `ndk { abiFilters += "arm64-v8a" }` block. Both are uncommitted.
- [BUILT] `setup-models.ps1` / `setup-models.bat` exist at the repo root: a reproducible,
  SHA-256-verified downloader for the 4 Whisper files and a tar-extract installer for the 2 Piper
  archives. Present but uncommitted (`??` in `git status`).
- [BUILT] Wired into Lane 1's `MainActivity.kt`: PTT release and Hands-free Done both now call
  `speech.transcribe(...)` for real instead of the old hardcoded string; a `LanguageSelector`
  dropdown (`en`/`hi` only) and a `TestTtsSection` panel (explicitly commented `TEMPORARY` in the
  source) were added directly to the Main screen to exercise `speak()`.
- [BUILT] `.\gradlew.bat assembleDebug` has succeeded against this exact code: `app-debug.apk`
  (354,155,398 bytes) is timestamped after every relevant source file's last edit today.
- [WORKS] Nothing. No file in this repository — no `STATUS.md` row, no `TASK.md` entry, no
  logcat capture, no test report — records a human running `transcribe()` or `speak()` and
  confirming the output on any device, RMX3392 or otherwise. Zero Lane 3 [WORKS] items exist.
- [PLANNED] The other 8 of `docs/CONTRACTS.md`'s 10 committed language codes (`gu`, `mr`, `kn`,
  `ml`, `ta`, `te`, `or`, `bn`) — no model, no code branch, nothing.
- [PLANNED] VAD, pause segmentation, and streaming/partial transcript delivery — explicitly
  unresolved per `docs/CONTRACTS.md` and not attempted in this code.

## 2. Decisions

- [BUILT] `transcribe`'s live signature is `transcribe(wavFilePath: String, languageCode: String):
  String` — it takes a second argument the `docs/CONTRACTS.md` signature
  (`transcribe(wavFilePath: String): String`) does not have. Rejected: keeping the one-argument
  contract and picking a language some other way. Why: two separate per-language model pairs exist,
  so something has to tell `SpeechRecognizerManager` which one to load — but `CONTRACTS.md` itself
  has not been updated to match, so this is a live, undocumented contract drift.
- [BUILT] Two fully separate STT model pairs (`tiny.en` vs `base`), selected by `language`, rather
  than one shared multilingual model for both languages. Rejected: a single Whisper model (e.g.
  multilingual `base` or `small`) for everything. Why not recorded in this repo. Consequence:
  `SpeechRecognizerManager.getRecognizer()` releases and fully reloads the recognizer any time the
  requested language differs from the one currently loaded — switching languages is not free.
- [BUILT] TTS keeps both `hindiTts` and `englishTts` resident simultaneously once loaded, and never
  releases either. STT instead releases and reloads on every language switch. Rejected: a
  consistent lifecycle strategy across both. Why not recorded — this looks like two different
  authors/times, not a deliberate tradeoff.
- [BUILT] `SpeechEngine.release()` only calls `recognizerManager.release()`; nothing releases
  `TtsHelper`'s `hindiTts`/`englishTts` `OfflineTts` instances or the `currentAudioTrack`.
  `MainActivity.kt`'s `DisposableEffect(Unit) { onDispose { speech.release() } }` therefore leaves
  any loaded TTS native model resident past Activity disposal. Rejected/never considered: symmetric
  release. Why not recorded.
- [BUILT] `sherpa-onnx` was pulled in as a raw local AAR file (`files("libs/sherpa-onnx-1.13.7.aar")`)
  rather than a Maven coordinate through `gradle/libs.versions.toml`. Rejected: a hosted dependency.
  Why: `settings.gradle.kts` locks resolution to `google()` + `mavenCentral()` only
  (`FAIL_ON_PROJECT_REPOS`), and sherpa-onnx is not published to either — a local file avoids
  touching that policy, at the cost of bypassing the version catalog `CLAUDE.md` says new
  dependencies should go through.
- [BUILT] `ndk { abiFilters += "arm64-v8a" }` restricts the packaged native libraries to one ABI.
  Rejected: shipping all four ABIs the AAR provides (arm64-v8a, armeabi-v7a, x86, x86_64 — confirmed
  by unzipping the AAR). Why: the only physical test device (RMX3392) is arm64-v8a, and the APK is
  already 354 MB; this change lives in `app/build.gradle.kts`, a file `CLAUDE.md` calls out as
  shared across all three lanes, and it is currently uncommitted and undiscussed with the other
  lanes.
- [BUILT] `setup-models.ps1` pins and verifies SHA-256 for all 4 Whisper `.onnx` files, but the
  script's own final-verification table leaves `Sha = ""` for both Piper TTS `.onnx` files.
  Rejected: hashing the TTS models too. Why not recorded — the TTS models are exactly as capable of
  silent corruption/substitution as the STT ones.
- [BUILT] Each Piper TTS model directory carries its own private, complete `espeak-ng-data` copy
  (17,990,884 bytes each, byte-for-byte identical between the English and Hindi folders) instead of
  one copy shared by both. Rejected: a single shared `espeak-ng-data` asset directory. Why not
  recorded — this is ~18 MB of exact duplication with no code reason found (`TtsHelper.loadModel`
  always looks under the model's own `destDir`).
- [BUILT] `SUPPORTED_LANGUAGES` in `MainActivity.kt` is hardcoded to exactly `en`/`hi`, with a
  comment citing "Bundle 1" as the reason. Rejected: exposing more of `CONTRACTS.md`'s 10 codes in
  the picker. Why not fully checkable — no "Bundle 1" document exists anywhere under `docs/`, so
  the cited authority for this scope decision isn't in this repo.

## 3. Numbers

All measured directly from the working tree during this audit; none estimated unless labeled.

- [BUILT] `app/libs/sherpa-onnx-1.13.7.aar`: 49,113,869 bytes (49.11 MB). Contains native libs for
  4 ABIs; only arm64-v8a is packaged into the APK (confirmed by unzipping the AAR).
- [BUILT] Whisper `tiny.en` (English STT): encoder 12,937,772 B + decoder 89,853,865 B + tokens
  835,554 B = **103,627,191 B (103.63 MB)**.
- [BUILT] Whisper `base` (Hindi STT, multilingual model): encoder 29,120,534 B + decoder
  130,672,026 B + tokens 816,730 B = **160,609,290 B (160.61 MB)**.
- [BUILT] Combined STT payload for both languages: **264,236,481 B (264.24 MB)**.
- [BUILT] Piper `en_US-ryan-medium` TTS folder: 81,146,192 B (81.15 MB) — of which the `.onnx`
  model itself is 63,149,198 B and the private `espeak-ng-data` copy is 17,990,884 B.
- [BUILT] Piper `hi_IN-pratham-medium` TTS folder: 81,142,276 B (81.14 MB) — `.onnx` model
  63,145,178 B, private `espeak-ng-data` copy 17,990,884 B (byte-identical size to the English
  copy — same duplicated payload, ~18 MB not shared).
- [BUILT] Total `app/src/main/assets/`: **426,524,949 B (426.52 MB)**.
- [BUILT] Resulting debug APK (`app-debug.apk`): 354,155,398 B (354.16 MB) — about 17% smaller than
  the raw assets total, consistent with zip compression of packaged assets; this is a debug,
  unshrunk build.
- [BUILT] Languages actually wired end-to-end in code today: 2 of the 10 `docs/CONTRACTS.md`
  commits to (`en`, `hi`). This is a direct code count, not an assumption.
- [PLANNED] Transcription/synthesis latency: **no number exists**. `MainActivity.kt` already
  contains `Log.d(LATENCY_TAG, ...)` calls timing release→transcript-ready and
  speak-start→speak-end, but no device run has produced a captured value anywhere in this repo —
  the instrumentation is [BUILT], the numbers are [PLANNED].
- [PLANNED] CPU or memory usage while a Whisper or Piper model is loaded/running: no profiling
  artifact of any kind exists in this repo.
- [PLANNED] Model-load ("cold start") time for the first `transcribe()`/`speak()` call per
  language: not measured; the code path that would incur it exists (`copyAssetFolder` on first TTS
  use, `OfflineRecognizer`/`OfflineTts` construction on first use per language) but has not been
  timed.

## 4. Demo-safe

- [BUILT] Zero [WORKS] items exist for this lane. Nothing here meets the bar to show a judge as
  "working," because no human has run any of it on a device and confirmed the result.

What the code, as written, would do if a judge deviated from a rehearsed happy path (code-observed,
not device-confirmed):

- [BUILT] The first `speak()` call for a given language triggers a synchronous copy of that
  language's full asset folder (~81 MB, mostly many small `espeak-ng-data` files) from `assets/`
  into `filesDir` before any audio can play — later calls skip this, so the first Hindi and first
  English TTS invocation each pay a one-time, unmeasured delay the rehearsed run would have already
  absorbed once.
- [BUILT] Switching the language picker between `en` and `hi` mid-session forces
  `SpeechRecognizerManager` to release the currently loaded Whisper recognizer and load the other
  one from scratch on the next transcription — repeated switching costs a full model reload every
  time, not just once.
- [BUILT] `TestTtsSection` and `LanguageSelector` are permanent, visible controls on the Main
  screen today, not hidden behind a debug flag — the code's own comment calls the TTS panel
  "TEMPORARY" and says to remove it once Transport exercises `speak()` for real. A judge poking at
  either control is poking at scaffolding, not the intended product surface.
- [BUILT] Nothing in `TtsHelper`/`SpeechEngine` ever releases a loaded TTS model — a demo session
  that exercises both languages' TTS repeatedly keeps accumulating native memory with no release
  path, on top of whichever Whisper model is also currently resident.

## 5. Gaps

- [PLANNED] 8 of the 10 `docs/CONTRACTS.md` language codes (`gu`, `mr`, `kn`, `ml`, `ta`, `te`,
  `or`, `bn`) have no model and no code path.
- [PLANNED] No VAD or pause segmentation exists for Hands-free; it still runs one full recording
  through the same one-shot `transcribe()` call PTT uses.
- [PLANNED] No streaming or partial transcript delivery exists; only a single blocking
  final-result call exists.
- [BUILT] No unit test, no instrumented test, and no manual test log exists anywhere in this
  repository for `SpeechEngine`, `SpeechRecognizerManager`, or `TtsHelper`.
- [BUILT] `lane/speech`'s own branch has zero commits of this work — there is no committed,
  reviewable, or recoverable-if-this-machine-is-lost history of any Lane 3 code anywhere in git.
- [BUILT] `transcribe`'s two-argument live signature has diverged from the one-argument signature
  `docs/CONTRACTS.md` documents, and `CONTRACTS.md` has not been updated to reflect it.
- [BUILT] `docs/PROJECT_FACTS.md`, dated the same day as this audit, still states "there are no
  speech model assets, sherpa-onnx/ONNX runtime dependencies... in the app" — every doc a reader
  would check for Lane 3's status currently says it doesn't exist, while 426 MB of it sits
  uncommitted in the working tree.
- [BUILT] TTS model integrity is unverified: `setup-models.ps1` has no SHA-256 for either Piper
  `.onnx` file (only the 4 Whisper files are hashed).
- [PLANNED] No profiling, latency, or memory numbers exist for ASR or TTS on the target device,
  despite the logging hooks already being present in code.

## 6. Blocked on

- Needs the Speech-lane owners (Trisha, Vaishnavi, Vachana per `README.md`) — or an explicit
  decision that `lane/app` is now the integration point instead — to actually commit this code
  somewhere. Today it exists in exactly one uncommitted working tree. [BUILT] — no date recorded
  in any doc for this decision.
- Needs Lane 1 (Vivek) to ratify the `transcribe(wavFilePath, languageCode)` signature change and
  update `docs/CONTRACTS.md` to match, since Lane 1's `MainActivity.kt` already depends on the new
  signature in the working tree. [BUILT] — no date recorded.
- Needs a scope decision on whether 2-language (`en`/`hi`) coverage is acceptable for Round 1, or
  whether more of `CONTRACTS.md`'s 10 codes are expected before then. [PLANNED] — no date recorded.
- Needs a physical-device test session — per `AGENTS.md`, only Vivek can turn any of this
  [WORKS] — to get literally any confirmed evidence for this lane before Round 1.
  `docs/LANE_1_APP_AND_CAPTURE.md`'s own timeline (Part 9) puts **Round 1 on the 7th** — tomorrow,
  relative to this audit's date of the 6th. [BUILT] fact about the date; [PLANNED] fact that no
  Lane 3 test session is scheduled anywhere in this repo.
