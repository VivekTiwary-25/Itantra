# AUDIT_LANE_1.md — Round 1 prep

Audit date: 2026-09-06. Branch: `lane/app`. Working tree has uncommitted changes (see §1/§2) —
this audit covers both the last committed state (`523598b`) and the current uncommitted working
tree, and marks which is which throughout.

Tags: [WORKS] = a human ran it on a physical device and saw it. [BUILT] = compiles, unverified on
device. [PLANNED] = intended, no working code.

## 1. State

Ladder rungs, per `STATUS.md` (the human-owned record) and `TASK.md`'s physical-acceptance
checklist, both dated 2026-09-06, committed as of `eedd486`/`36ba653`:

- [WORKS] A0 — app launches on RMX3392. Evidence: `STATUS.md` row GREEN; no separate description recorded beyond "runs on physical phone."
- [WORKS] A1 — screen shows the literal text `iTantra`. Evidence: `STATUS.md` GREEN.
- [WORKS] A2 — `HOLD TO TALK` button exists; a tap changed its status text to `pressed`. Evidence: `STATUS.md` GREEN.
- [WORKS] A3 — true press-and-hold: status text read `Recording...` while held and `Idle` on release. Evidence: `STATUS.md` GREEN; `SESSION_HANDOFF.md` records the specific bug found and fixed (custom `pointerInput` conflicted with `Material3 Button`; gesture moved to a plain `Box`).
- [WORKS] A4 — runtime microphone permission dialog appeared and was grantable. Evidence: `STATUS.md` GREEN.
- [WORKS] A5 — holding the button recorded raw 16 kHz mono PCM16. Evidence: `STATUS.md` GREEN.
- [WORKS] A6 — a WAV header was added; the pulled file played correctly off-device (on a laptop). Evidence: `STATUS.md` GREEN; `AGENTS.md` records the exact pull command needed (`cmd /c "adb exec-out run-as ... "`) after a PowerShell-redirect corruption was found (157 KB → 557 KB, unplayable).
- [WORKS] A7 — the last recording played back inside the app via `PLAY LAST RECORDING`. Evidence: `STATUS.md` GREEN. Known caveat recorded in `AGENTS.md`: this control "occasionally cuts off or doesn't fully play" — that caveat was never resolved, only noted.
- [WORKS] A8 — a scrolling message list showed three fake messages with timestamps. Evidence: `STATUS.md` GREEN.
- [WORKS] A9 — typed text could be sent and appeared in the shared message list. Evidence: `STATUS.md` GREEN.
- [WORKS] A10 — fake `transcribe`/`speak`/`sendMessage`/`onMessageReceived` stand-ins wired into the UI end-to-end. Evidence: `STATUS.md` GREEN, committed `9ab8b22`. `SESSION_HANDOFF.md` records a state-loss bug found during this rung (an accidental phone rotation reset a plain-`remember` list) that led to switching the message list to `rememberSaveable`.
- [WORKS] A11 — Hands-free tile opened a dedicated dark/gold screen with `Listening...`, Back and Done; Main kept the dominant PTT control. Evidence: `TASK.md` physical-acceptance checklist item 2, marked passed; `STATUS.md` GREEN, committed `eedd486`.
- [WORKS] A12 — PTT, Hands-free-Done and Text all converge on one shared New Message editor; only Send appends a message; non-empty Back offers Cancel/Discard. Evidence: `TASK.md` checklist items 3–6, marked passed; `STATUS.md` GREEN, committed `eedd486`.
- [WORKS] A13 — Logs rows open a read-only Message Detail screen; opening one unread received row marks only that row read and drops the Main badge by one; Logs stays newest-first. Evidence: `TASK.md` checklist items 7–9, marked passed; `STATUS.md` GREEN, committed `eedd486`.
- [WORKS] Post-acceptance correction — Hands-free now starts/stops the same `PcmRecorder`/`recording.wav` PTT uses, and shares its last-recording playback; Message Detail persists and shows date plus time. Evidence: `STATUS.md` "Update rule" line, `SESSION_HANDOFF.md`, committed `36ba653`.
- [BUILT] Uncommitted, off-ladder — real ASR (sherpa-onnx Whisper `tiny.en`/`base` int8) and real TTS (sherpa-onnx Piper VITS, `en_US-ryan-medium` + `hi_IN-pratham-medium`) are wired into `MainActivity.kt` through a new `speech.SpeechEngine`, replacing the hardcoded `this is a test message` stand-in and the no-op `speak`. This exists only in the uncommitted working tree — not in any commit, not in `STATUS.md`, not in `TASK.md`. It compiles: `.\gradlew.bat assembleDebug` has produced `app/build/outputs/apk/debug/app-debug.apk` (354 MB, timestamped after the current source files). No human has run this build on a phone as far as any file in this repo records.
- [PLANNED] A14+ / anything past A13 — `TASK.md` explicitly says standalone Lane 1 work stops at A13; no further standalone rung is defined or scheduled.

## 2. Decisions

- [WORKS] Fixed capture format (16 kHz / 16-bit / mono / PCM WAV), no negotiation — alternative rejected: letting `AudioRecord` pick a device-default format — why: `docs/CONTRACTS.md` and `docs/LANE_1_APP_AND_CAPTURE.md` state the ASR models are trained on exactly this format and "will silently produce garbage" otherwise.
- [WORKS] One shared `PcmRecorder`/`recording.wav` used by both PTT and Hands-free — alternative rejected: a second, Hands-free-specific capture pipeline — why: `TASK.md`'s post-acceptance correction and `SESSION_HANDOFF.md`'s "do not blindly replace... the one shared `PcmRecorder`" instruction both treat a second pipeline as explicitly out of scope.
- [WORKS] Voice transcripts land as an editable draft in the shared New Message editor; only explicit Send calls `sendMessage` — alternative rejected: auto-sending on transcription completion — why: `docs/CONTRACTS.md` "Voice transcript delivery" section fixes this as the resolved cross-lane contract; avoids unreviewed text leaving the device.
- [WORKS] `onMessageReceived` carries `(text, languageCode)`, not just `text` — alternative rejected: text-only callback (the original lane-doc example) — why: `docs/CONTRACTS.md` records this as a resolved mismatch: the app preserves the sender's language rather than translating, so a language code has to travel with a received message to reach `speak`.
- [WORKS] No ViewModel/repository/database/DI/Navigation Compose; one `ITantraApp` composable owns all state via `rememberSaveable` — alternative rejected: introducing an architecture layer now — why: `AGENTS.md` says not to add layers "merely for future-proofing" and the ladder spec "stops advancing once its acceptance ladder is green."
- [WORKS] Message list persists only across Activity recreation (custom `Saver`, not a database) — alternative rejected: Room/DataStore-backed durable storage — why: `STATUS.md`/`TASK.md` guardrails explicitly list "durable persistence" as out of scope for standalone Lane 1 work.
- [WORKS] `sendMessage`, `speak`, `onMessageReceived` (Transport side) remain literal no-ops in `MainActivity.kt` — alternative rejected: any real network/Bluetooth call — why: `docs/CONTRACTS.md`/`AGENTS.md` fake-interface rule; Lane 2 does not exist yet in this tree.
- [WORKS] Custom `pointerInput`/`detectTapGestures` moved onto a plain `Box` rather than a `Material3 Button`'s own click handling — alternative rejected: layering the custom gesture on top of `Button` — why: `SESSION_HANDOFF.md` records that combination silently ate the press-and-hold gesture during A3.
- [WORKS] `graphicsLayer { alpha = ... }` ordered before `clip`/`background` in the recording-pulse modifier chain — alternative rejected: applying alpha after background — why: `SESSION_HANDOFF.md` records the pulse animation was running but invisible because the opaque button fill was drawn on top of it.
- [WORKS] `PLAY LAST RECORDING` kept as a visible, permanent-looking button rather than removed or hidden behind a debug flag — alternative rejected: removing it once A7 was accepted — why: `AGENTS.md`/`STATUS.md` both currently still list it as present and only schedule its removal for "final integration/product cleanup."
- [BUILT] Real STT/TTS (sherpa-onnx) added directly into `lane/app`'s `MainActivity.kt` and a new `speech` package, uncommitted — alternative that the written scope docs require: leaving `transcribe`/`speak` as fakes until Lane 3 delivers them, per `docs/CONTRACTS.md` ownership boundaries ("Speech: ASR/STT implementation, TTS implementation... belongs to Speech lane") and `AGENTS.md`'s explicit rule ("Do not add ... STT, TTS ... during Lane 1 work unless the active task explicitly asks for it") — why this was done anyway is not recorded anywhere in this repo's docs; no `TASK.md` entry, no `STATUS.md` row, and no commit authorizes it.
- [BUILT] `sherpa-onnx` pulled in as a local `files("libs/sherpa-onnx-1.13.7.aar")` dependency rather than a version-catalog/repository coordinate — alternative rejected: adding a Maven coordinate through `gradle/libs.versions.toml` — why not recorded; this bypasses the version catalog that `CLAUDE.md` says new dependencies should go through, though it does keep `settings.gradle.kts`'s `FAIL_ON_PROJECT_REPOS` policy technically intact (no new repository was added).
- [BUILT] `ndk { abiFilters += "arm64-v8a" }` added to `app/build.gradle.kts` — alternative rejected: shipping all four ABIs sherpa-onnx's AAR provides (arm64-v8a, armeabi-v7a, x86, x86_64) — why not recorded in any doc; matches the single physical test device (RMX3392, arm64-v8a) and keeps the already-huge APK from growing further, but `app/build.gradle.kts` is a file `CLAUDE.md` calls out as shared across lanes ("coordinate before making large or unusual changes"), and this change is uncommitted and undiscussed.
- [BUILT] UI language selection hardcoded to two entries (`en`, `hi`) in `SUPPORTED_LANGUAGES`, with a code comment saying "Only en and hi are safe per Bundle 1" — alternative implied by `docs/CONTRACTS.md`: all ten committed language codes — why not recorded; no "Bundle 1" document exists anywhere in `docs/`, so the stated reason cannot be checked against a source.

## 3. Numbers

All measured directly from the working tree just now; none are estimated.

- [BUILT] `app/build/outputs/apk/debug/app-debug.apk`: 354,155,398 bytes (~338 MiB), built from the current uncommitted source (file timestamp is after the last source-file edit).
- [BUILT] `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: 1,097,079 bytes (~1.05 MiB).
- [BUILT] `app/libs/sherpa-onnx-1.13.7.aar`: 49,113,869 bytes (~46.8 MiB), uncommitted.
- [BUILT] Whisper ASR assets (uncommitted, git-ignored by `*.onnx`): `base-decoder.int8.onnx` 125 MB, `base-encoder.int8.onnx` 28 MB, `tiny.en-decoder.int8.onnx` 86 MB, `tiny.en-encoder.int8.onnx` 13 MB, `base-tokens.txt` 800 KB, `tiny.en-tokens.txt` 816 KB.
- [BUILT] Piper TTS asset folders (uncommitted): `vits-piper-en_US-ryan-medium` 78 MB total (61 MB is the `.onnx` model itself, ~18 MB is `espeak-ng-data` plus tokens/config, none of which is git-ignored); `vits-piper-hi_IN-pratham-medium` 78 MB total, same breakdown.
- [BUILT] `app/src/main/assets` total: 408 MB.
- [BUILT] Native libraries actually packaged into `app-debug.apk` (arm64-v8a only, confirmed via the APK's own zip listing): `libonnxruntime.so` 21,684,880 bytes, `libsherpa-onnx-jni.so` 4,761,536 bytes, `libsherpa-onnx-c-api.so` 4,456,784 bytes, `libsherpa-onnx-cxx-api.so` 440,760 bytes, `libandroidx.graphics.path.so` 10,096 bytes. The AAR itself contains armeabi-v7a/x86/x86_64 copies of the same libraries too (confirmed in `merged_native_libs` intermediates), but the `abiFilters` setting keeps them out of the final APK.
- [WORKS] Text-to-speech, ASR-to-text ratio the project's own pitch relies on ("~900:1 smaller") — not measured anywhere in this repo; `CLAUDE.md` states it as a design rationale, not a measured result. [PLANNED] as a number.
- [PLANNED] Transcribe/speak latency — `MainActivity.kt` already contains `Log.d(LATENCY_TAG, ...)` timing hooks around both `speech.transcribe()` and `speech.speak()` calls (`ITANTRA_LATENCY` tag), but no logcat capture exists in this repo, so no actual millisecond figure can be reported. Do not assume a number here.
- [WORKS] Git history on this branch: 10 commits total (`ba0433f` through `523598b`), per `git log`.
- [BUILT] Working-tree diff size against the last commit: `app/build.gradle.kts` +5/-0 lines; `MainActivity.kt` +226/-25 lines (246 lines touched); five new untracked paths (`app/libs/`, `app/src/main/assets/`, `TtsHelper.kt`, `speech/`, two `setup-models.*` scripts).

## 4. Demo-safe

Based only on the last physically-accepted commit (`36ba653`) — this is the code `STATUS.md` calls
GREEN, and it is different from what currently sits in the uncommitted working tree (see §1/§2):

- [WORKS] Launch app, show Main with `HOLD TO TALK`, hold-to-record, release, see the fixed transcript `this is a test message` land in an editable draft.
- [WORKS] Edit the draft, press Send, show it appear in Logs newest-first labeled `Sent`.
- [WORKS] Open Hands-free, show `Listening...`, press Done, show the same shared-editor flow.
- [WORKS] Open Logs, open an unread received row, show Message Detail with date/time/direction, return to Logs, show the unread badge on Main drop by one.
- [WORKS] All of the above requires no network connection — the manifest requests no `INTERNET` permission and nothing in the committed code path calls out anywhere.

What breaks if the judge deviates from that exact happy path (still talking about the committed,
GREEN build):

- [WORKS] Every PTT/Hands-free transcript is the literal fixed string `this is a test message` regardless of what was actually said — a judge who speaks something else and expects to see it transcribed will see a mismatch. This is documented, expected behavior, not a bug.
- [WORKS] `PLAY LAST RECORDING` is recorded as occasionally cutting off or not fully playing (`AGENTS.md` "Known issues") — an unresolved, known flake.
- [WORKS] Rotating the phone previously reset in-memory state before the message list was made `rememberSaveable` (`SESSION_HANDOFF.md` A10 note); the fix is committed, but no rotation-specific regression test exists to guarantee every screen's local state (e.g. an in-progress Hands-free session, an open dropdown) survives rotation cleanly.
- [WORKS] If Bluetooth audio is connected to the phone, `AGENTS.md` records that `AudioRecord` may route through it instead of the built-in mic — a judge's own earbuds paired to the phone could silently break capture.

Separately — the uncommitted working tree (not what was demoed/accepted) additionally risks, if it
is what actually gets installed for a demo:

- [BUILT] The real ASR/TTS path (`SpeechEngine`) has never been run on a device per any record in this repo. First-run behavior (model load time, `TtsHelper`'s synchronous per-language asset copy of ~18 MB of small files out of `assets/` into `filesDir` on first `speak()` call, memory pressure from a 354 MB APK with two Whisper models and two Piper models resident) is entirely unverified.
- [BUILT] Selecting Hindi (`hi`) routes ASR through the multilingual `base` Whisper model rather than `tiny.en`; that code path has no recorded test run at all.

## 5. Gaps

- [PLANNED] No Transport (Lane 2). `sendMessage`/`onMessageReceived` are no-ops. Two phones cannot exchange a message with this branch alone.
- [PLANNED] Only 2 of the 10 language codes `docs/CONTRACTS.md` commits to (`en`, `hi`) have any model or code path, real or fake, in this repo. The other 8 (`gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`) have nothing.
- [PLANNED] No VAD or pause segmentation for Hands-free. It records continuously until the user manually presses Done or Back; that is documented as intentionally deferred to Lane 3, not built.
- [PLANNED] No alert metadata contract exists across Lane 1/Lane 2, and no max-volume/non-interruptible received-alert playback path exists at all.
- [PLANNED] No durable persistence beyond `rememberSaveable`. Process death outside a normal recreation (e.g., the OS killing the app in the background, a swipe-from-recents) has no recorded test and no code path to survive it.
- [WORKS] No passing instrumented UI test exists for A11–A13. `SESSION_HANDOFF.md` records that an attempted Compose flow test compiled but could not run on RMX3392/ColorOS (`UiAutomation.grantRuntimePermission` was denied), and that test was deleted rather than fixed. The only remaining instrumented test is the original app-context smoke test.
- [BUILT] `app/libs/sherpa-onnx-1.13.7.aar` (49 MB) and the non-`.onnx` TTS support files (~36 MB combined across both languages: `espeak-ng-data`, `tokens.txt`, `.onnx.json`, `MODEL_CARD`) are not covered by `.gitignore`, unlike the `.onnx` model weights themselves (`*.onnx` is ignored). A plain `git add -A` today would stage tens of MB of binary/model-support material into the repository.
- [BUILT] The real Speech integration (`speech/SpeechEngine.kt`, `speech/SpeechRecognizerManager.kt`, `TtsHelper.kt`, the `sherpa-onnx` dependency, the `ndk` block) is undocumented anywhere in `TASK.md`, `STATUS.md`, `docs/CONTRACTS.md`, or `docs/SHERPA_AAR_UNBLOCKER.md`, and contradicts the explicit scope rule against adding real STT/TTS during Lane 1 work. This is a gap in process, not just code: whoever wrote it did not update the docs that are supposed to be the source of truth.
- [PLANNED] No mic-permission-denied UX. The permission request fires once on launch with an empty result callback; if denied, PTT/Hands-free silently no-op (`onStartRecording` returns `false`) with no retry path shown to the user.

## 6. Blocked on

- Lane 2 (Transport): needed to replace the no-op `sendMessage`/`onMessageReceived` before any real device-to-device demo is possible. [PLANNED] — no delivery date is recorded in any doc in this repo.
- Lane 3 (Speech): the lane docs still assign ASR/TTS/VAD ownership to Lane 3, but a real (uncommitted) ASR/TTS implementation already exists inside `lane/app`'s own tree. Before Round 1, someone needs to decide whether that code moves to `lane/speech`, gets reviewed and formally adopted into Lane 1's contract, or is discarded — otherwise the two lanes will build the same thing twice or ship an unreviewed implementation. [PLANNED] — no date recorded.
- Cross-lane alert metadata (Lane 1 + Lane 2 together): required before any received-alert TTS/UI behavior can be built. `docs/CONTRACTS.md` calls this "an unresolved prerequisite" with no owner or date named.
