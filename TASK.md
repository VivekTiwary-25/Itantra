# TASK.md — Current Codex Task

## Active task

**Lane 1 — A11 only**

Add a switch labelled `Push-to-talk`.

- When on, show the existing `HOLD TO TALK` control and preserve its current behavior.
- When off, hide the hold control and show exactly `Listening...`.
- The indicator is UI-only. It must not begin capture or speech processing.

## Scope

Implement A11 and nothing later.

Do not add:

- hands-free capture or VAD
- speech recognition or TTS
- Bluetooth, Wi-Fi, or transport changes
- message format or contract changes
- sherpa-onnx
- any architecture refactor

## Required workflow

1. Read `docs/UI-Reference/ITANTRA_UI_HANDOFF.md`.
2. Inspect the existing `MainActivity.kt`.
3. Make the smallest change that satisfies A11.
4. Run `.\gradlew.bat assembleDebug`.
5. Do not mark A11 GREEN.
6. Tell Vivek how to run/install the app and perform the physical acceptance test.
7. Stop.

## Physical acceptance

1. Launch the app.
2. Confirm the `Push-to-talk` switch is on and `HOLD TO TALK` is visible.
3. Turn the switch off and confirm the hold control is replaced by exactly `Listening...`.
4. Turn the switch on and confirm `HOLD TO TALK` returns.

Only Vivek's confirmation of that real-phone test changes A11 to GREEN.
