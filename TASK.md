# TASK.md — Current Codex Task

## Active task

**Lane 1 — A11–A13 verification and physical acceptance**

The standalone A11–A13 implementation is present in the working tree. Its connected UI test did not run through the feature assertions because device permission setup failed, and Vivek's real-phone acceptance is still pending. Do not begin another standalone Lane 1 feature while these rungs remain YELLOW.

Implemented scope:

- A11: the Hands-free tile opens a dedicated dark/gold screen with `Listening...`, Back and Done; Main retains the dominant PTT.
- A12: Text opens the shared editor blank; PTT and the temporary Hands-free completion open it with the hardcoded transcript; only Send appends one outgoing message; non-empty Back offers Cancel/Discard.
- A13: Logs rows open read-only Message Detail; opening one unread incoming row marks only that message read; Logs remains newest-first and the Main badge remains derived from unread received messages.

## Required physical acceptance

1. Launch the app and confirm Main still shows the dominant `HOLD TO TALK` control.
2. Open Hands-free. Confirm `Listening...`, Back and Done are present; use Back and confirm Main/shared state is preserved.
3. Open Text and confirm the editor is blank. Enter text, press Back, confirm Cancel stays in the editor, then confirm Discard leaves it.
4. Note the current Logs contents. Hold PTT, speak and release while keeping one orientation. Confirm the editor opens with `this is a test message`; Back/Discard it, reopen Logs, and confirm transcription alone added nothing.
5. Repeat PTT, edit the draft, press Send, then confirm Logs contains the edited text exactly once as the newest `Sent` message.
6. Open Hands-free, press Done, and confirm the same editor opens pre-filled with `this is a test message`; Back/Discard the temporary draft.
7. Open Logs without opening a row, return to Main, and confirm the unread badge did not change.
8. Open one unread received row. Confirm Message Detail shows its full text, timestamp and `Received`; return to Logs and confirm only that row is read and the Main unread badge drops by one.
9. Open the sent row and confirm Message Detail labels it `Sent`; confirm Logs remains newest-first.

Only Vivek's confirmation changes A11–A13 to GREEN.

## Scope guardrails

Do not add real STT/TTS, continuous capture, VAD, transport, alert metadata, durable persistence, Navigation Compose, a repository, a database, DI, or more standalone Lane 1 rungs. Integration-deferred work starts only when explicitly assigned.

## Next resume point

Run the checklist above. If any step fails, keep the affected rung YELLOW and diagnose that exact behavior. If all steps pass, update status and history as directed; do not start extra standalone Lane 1 work.
