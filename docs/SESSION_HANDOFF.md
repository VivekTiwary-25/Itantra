# SESSION_HANDOFF.md — iTantra lane/app

Updated on 2026-09-06 after physical acceptance and the accepted post-acceptance capture/date correction. This is a resume snapshot, not a replacement for the domain-specific sources listed below.

## Source precedence

When documents overlap, authority depends on the subject:

1. `TASK.md` controls current implementation scope.
2. `docs/CONTRACTS.md` controls cross-lane boundaries and interfaces.
3. `docs/LANE_1_APP_AND_CAPTURE.md` controls the Lane 1 ladder and acceptance criteria.
4. `docs/UI-Reference/itantra_ui_ux_handoff_for_claude.txt` controls UI/UX direction.

Older UI handoffs and generated prototypes are compatible-only secondary references. `STATUS.md` is the live status record.

## Current ladder status

| Rung | Goal | Status |
|---|---|---|
| A0–A9 | App shell, capture/WAV/playback, message history and typed fallback | GREEN |
| A10 | Fake Speech/Transport interfaces wired into UI | GREEN — committed as `9ab8b22` |
| A11 | Dedicated Hands-free destination | GREEN — physically accepted by Vivek; committed as `eedd486` |
| A12 | Voice inputs converge on the shared editor | GREEN — physically accepted by Vivek; committed as `eedd486` |
| A13 | Message Detail and per-message read/unread behavior | GREEN — physically accepted by Vivek; committed as `eedd486` |

GREEN means physically demonstrated by Vivek on a real phone. A11–A13 meet that condition.

Standalone Lane 1 feature implementation ends after A13 for now. Do not start another standalone Lane 1 rung.

## What A11–A13 now contain

### A11 — Dedicated Hands-free destination

- Main permanently retains its dominant `HOLD TO TALK` control.
- The prominent Hands-free tile opens a dedicated sparse dark/gold screen.
- The screen shows the resting `Listening...` shell, Back and Done.
- Entering Hands-free starts real local microphone capture through the same `PcmRecorder` and `recording.wav` used by PTT; Done finalizes that shared WAV before opening the existing fake transcript/editor path.
- Back stops/finalizes the shared recorder and returns to Main without opening the editor. `PLAY LAST RECORDING` plays whichever path captured most recently.
- There is still no VAD, pause segmentation, real STT, streaming transcript or final Speech integration.

### A12 — Shared editor convergence

- Text clears the current draft and opens the shared New Message editor blank.
- PTT release calls the existing hardcoded `transcribe(recordingFile.absolutePath)` and opens that editor with `this is a test message` pre-filled.
- Hands-free Done is a temporary fake-completion path which calls the same stand-in and opens the same editor pre-filled.
- Voice output is an editable draft. It is not appended to Logs and does not call transport merely because transcription completed.
- Only Send trims non-empty editor text, calls fake `sendMessage(text)`, appends one read outgoing message and returns to Main.
- Back leaves an empty editor immediately. Back from a non-empty editor shows `Discard this message?` with Cancel and Discard.
- A10 remains a valid accepted plumbing milestone; A12 refines that flow into the agreed review-before-Send UX.

### A13 — Message Detail and read/unread behavior

- Logs reads the same app-owned message list in newest-first order.
- Every row is openable and explicitly labels its direction as Sent or Received.
- Opening a row shows a read-only Message Detail screen with full text, stored date and time, and direction.
- Opening an unread received row replaces only that item with an `isRead = true` copy before showing detail.
- Opening Logs alone does not mark anything read; sent messages remain read.
- The Main badge is still derived with `RECEIVED && !isRead`.
- Back from detail returns to Logs.

## Current state ownership

`ITantraApp` remains the single host above all five screens. It owns:

- the current screen;
- the current editor draft;
- the selected message index;
- one `SnapshotStateList<Message>` shared by Main, New Message, Logs and Message Detail.

The existing `MessageListSaver` persists every `Message` field (`text`, `date`, `timestamp`, `direction`, `isRead`) across Activity recreation. Screen, draft and selected index use local `rememberSaveable` state. No ViewModel, repository, database, DI or Navigation Compose was added.

## Verification history

- `.\gradlew.bat assembleDebug` completed successfully after the accepted A11–A13 source was finalized.
- During the interrupted run, `.\gradlew.bat assembleDebugAndroidTest` compiled an attempted Compose flow test, but `.\gradlew.bat connectedDebugAndroidTest` could not run it: RMX3392/ColorOS denied `UiAutomation.grantRuntimePermission` before its feature assertions began.
- The non-runnable Compose test addition was removed instead of being committed. The original app-context instrumented smoke test remains.
- Vivek then completed the required real-phone checklist. That physical evidence is why A11–A13 are GREEN.

## Fake versus real boundary

- PTT transcription still uses the hardcoded `this is a test message`; real STT belongs to Lane 3.
- Hands-free now performs real local microphone/WAV capture until Done using Lane 1's proven PTT recorder. VAD, pause segmentation, real STT and final Speech integration still belong to Lane 3.
- Lane 1 assumes only that an eventual final transcript can reach the editor. Whether Lane 3 later provides final-only results, partial/streaming results or another compatible model is intentionally unresolved.
- Exact Hands-free state and event mapping must follow Lane 3's actual interface during integration.
- `sendMessage` and `onMessageReceived` remain fake/no-op Transport stand-ins; real sending and receiving belong to Lane 2.
- `speak` remains a fake/no-op Speech stand-in; real TTS belongs to Lane 3.
- Alert metadata is unresolved across Lane 1 and Lane 2. Do not invent a packet, callback or local `Message` field.
- Maximum-volume, non-interruptible received-alert TTS depends on Lane 2 supplying agreed metadata and Lane 3 supplying playback behavior.
- `PLAY LAST RECORDING` is a temporary capture-verification control. Remove it during final integration/product cleanup after it is no longer needed.

These are integration-deferred items, not ordinary RED standalone Lane 1 rungs.

## Useful debugging history

### A3 press gesture did not fire

A custom `pointerInput`/`detectTapGestures` handler conflicted with Material3 `Button` pointer handling. Moving the custom gesture to a plain `Box` fixed press-and-hold. Do not layer this gesture detector onto another clickable Compose component.

### Recording pulse looked invisible

The animation ran, but `.graphicsLayer { alpha = idleAlpha }` came after the button's clip/background, so it did not fade the fill drawn earlier in the modifier chain. The opaque amber button hid the amber pulse. Moving `graphicsLayer` before clip/background fixed the occlusion. For future invisible-render bugs, inspect modifier order and use a high-contrast static diagnostic before rewriting animation math.

### A10 transcript-path investigation

An initial permission gate around transcript delivery was removed so only real `recorder.start()` and `recorder.stop()` remain microphone-permission gated. When the symptom persisted, temporary logs proved a normal release completed the whole path: `released=true`, `transcribe()` called, `onTranscript` entered and the shared list grew from 3 to 4.

A later instrumented run showed the fourth item rendered in Logs, then an accidental phone rotation recreated the Activity and reset the old plain-`remember` list to its three initial messages. That run proved an Activity-recreation state-loss bug, but the rotation was accidental and did not prove recreation caused the earlier same-orientation report. The list, screen and related state were changed to recreation-safe saveable state; A10 was subsequently physically accepted and committed as `9ab8b22`. Temporary diagnostic logging was removed.

## Local device and build gotchas

- Test device when connected: RMX3392, Android 14 / API 34, arm64-v8a.
- Pull binary audio through cmd.exe: `cmd /c "adb exec-out run-as <package> cat files/X > X"`. PowerShell `>` corrupts binary output.
- Disconnect Bluetooth audio devices before microphone capture tests; Android may route `AudioRecord` input through them.
- Use `.\gradlew.bat assembleDebug` as the primary build. Do not lead with `--offline`; the Foojay plugin has previously been absent from cache.
- `PLAY LAST RECORDING` can occasionally cut off. It is test-only and is not evidence about the deferred received-alert playback path.

## Commit history and working-tree rule

- A10 remains `9ab8b22`.
- A11–A13 are committed as `eedd486`.
- Documentation reconciliation is `5e12125`; this acceptance record follows it.

Do not rewrite these commits. Standalone Lane 1 is complete; only explicitly assigned integration work should modify it.

## Exact resume point

Start only an explicitly assigned cross-lane integration audit, integration task, or final product cleanup. Keep the fake-versus-real boundaries above intact until the owning lanes agree on their contracts.
