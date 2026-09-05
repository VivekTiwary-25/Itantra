# TASK.md — Current Codex Task

## Active task

**Lane 1 — A1 only**

Change the visible app text from the default greeting to:

`iTantra`

## Scope

Implement A1 and nothing later.

Do not add:

- a button
- press-and-hold behavior
- microphone permission
- audio recording
- WAV code
- message UI
- fake Speech/Transport interfaces
- sherpa-onnx
- any architecture refactor

## Required workflow

1. Inspect the existing `MainActivity.kt`.
2. Make the smallest change that satisfies A1.
3. Run `.\gradlew.bat assembleDebug`.
4. Do not mark A1 GREEN.
5. Tell Vivek how to run/install the app and perform the physical acceptance test.
6. Stop.

## Physical acceptance

The phone visibly shows:

`iTantra`

Only Vivek's confirmation of that real-phone test changes A1 to GREEN.
