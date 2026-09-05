# TASK.md — Current Codex Task

## Active task

**Lane 1 — A3 only**

Make `HOLD TO TALK` respond to press-and-hold.

While the finger is down:

- Fade/disappear the idle PTT presentation and replace it with the focused active voice state.
- Show exactly:

`Recording...`

- Show a restrained soft pulsing-ring/listening indicator.
- Give one short, firm haptic confirmation when the hold begins.

When released:

- Give one lighter haptic confirmation.
- Restore the PTT screen and show exactly:

`Idle`

## Scope

Implement A3 and nothing later.

Do not add:

- microphone permission
- audio recording
- WAV code
- conversation history
- typed fallback
- hands-free mode
- transport
- fake Speech/Transport interfaces
- sherpa-onnx
- any architecture refactor

## Required workflow

1. Read `docs/UI-Reference/itantra-ui-reference/ITANTRA_UI_HANDOFF.md`.
2. Inspect the existing `MainActivity.kt`.
3. Make the smallest change that satisfies A3.
4. Run `.\gradlew.bat assembleDebug`.
5. Do not mark A3 GREEN.
6. Tell Vivek how to run/install the app and perform the physical acceptance test.
7. Stop.

## Physical acceptance

1. Press and hold `HOLD TO TALK` for three seconds.
2. Confirm the idle PTT presentation fades/disappears and the focused active voice state replaces it.
3. Confirm the active state shows exactly `Recording...` for the entire hold.
4. Confirm the restrained soft pulsing-ring/listening indicator is visible.
5. Confirm one short, firm haptic feedback occurs when the hold begins.
6. Release the button.
7. Confirm one lighter haptic feedback occurs and the PTT screen is restored with exactly `Idle`.

Only Vivek's confirmation of that real-phone test changes A3 to GREEN.
