# TASK.md — Current Codex Task

## Active task

**Integration recovery — implementation complete; physical verification pending**

The recovery work is isolated on `integration/recovery-pass`, based on the
confirmed integrated checkpoint `origin/typed-text-integration@21e28fd`.

Implemented and build/JVM-tested on the recovery branch:

- outgoing messages enter Logs only after transport reports `Sent`;
- disconnected/error sends remain in the editor with a visible deterministic error;
- received messages send ACKs and matching outgoing rows become `Delivered`;
- the selected ISO language code crosses app state, protocol v2, and receive state unchanged;
- the eight exposed language codes have automated protocol round-trip coverage;
- `setup-models.ps1` provisions the pinned Dolphin base multilingual INT8 model with verified hashes.

These recovery changes are not physically verified. Do not mark them GREEN
until the two-phone checklist in the recovery report has passed.

## Preserved standalone status

**Lane 1 — standalone work complete through A13**

Vivek has physically accepted A11–A13 and the post-acceptance correction. A0–A13 are GREEN; the accepted commits are `eedd486` and `36ba653`. Do not begin another standalone Lane 1 feature.

Implemented scope:

- A11: the Hands-free tile opens a dedicated dark/gold screen with `Listening...`, Back and Done; Main retains the dominant PTT.
- A12: Text opens the shared editor blank; PTT and Hands-free open it with the final transcript; only Send can append one outgoing message; non-empty Back offers Cancel/Discard. The standalone acceptance used a hardcoded transcript, while the integration branch now supplies `SpeechEngine` output.
- A13: Logs rows open read-only Message Detail; opening one unread incoming row marks only that message read; Logs remains newest-first and the Main badge remains derived from unread received messages.
- Accepted post-acceptance correction: Hands-free starts/stops the same local PTT WAV recorder and shares its last-recording playback file; Message Detail persists and displays date plus time.

## Physical acceptance completed

1. Launch the app and confirm Main still shows the dominant `HOLD TO TALK` control.
2. Open Hands-free. Confirm `Listening...`, Back and Done are present; use Back and confirm Main/shared state is preserved.
3. Open Text and confirm the editor is blank. Enter text, press Back, confirm Cancel stays in the editor, then confirm Discard leaves it.
4. Note the current Logs contents. Hold PTT, speak and release while keeping one orientation. Confirm the editor opens with `this is a test message`; Back/Discard it, reopen Logs, and confirm transcription alone added nothing.
5. Repeat PTT, edit the draft, press Send, then confirm Logs contains the edited text exactly once as the newest `Sent` message.
6. Open Hands-free, press Done, and confirm the same editor opens pre-filled with `this is a test message`; Back/Discard the temporary draft.
7. Open Logs without opening a row, return to Main, and confirm the unread badge did not change.
8. Open one unread received row. Confirm Message Detail shows its full text, timestamp and `Received`; return to Logs and confirm only that row is read and the Main unread badge drops by one.
9. Open the sent row and confirm Message Detail labels it `Sent`; confirm Logs remains newest-first.

The checklist above passed on Vivek's physical phone.

## Scope guardrails

Do not add receiver TTS, VAD, pause segmentation, alert metadata, durable persistence, Navigation Compose, a repository, a database, DI, a second capture pipeline, or more standalone Lane 1 rungs. Do not further redesign the integrated Speech or Transport paths while the recovery branch awaits physical acceptance.

## Next resume point

Run the pending recovery-branch physical checklist on two phones. Do not add
receiver TTS, Kannada/Malayalam selector options, relay changes, store-and-forward
changes, or further standalone Lane 1 work while completing that acceptance pass.
