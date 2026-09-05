# STATUS.md — iTantra lane/app

Status meanings:

- GREEN: physically demonstrated on a real device
- YELLOW: implemented/partially verified but physical acceptance not yet confirmed
- RED: not demonstrated

Never convert status to percentages.

## Lane 1 ladder

| Rung | Goal | Status |
|---|---|---|
| A0 | Existing app runs on physical phone | GREEN |
| A1 | Screen says `iTantra` | GREEN |
| A2 | `HOLD TO TALK` button; tap changes text to `pressed` | GREEN |
| A3 | True press-and-hold UI: `Recording...` while held, `Idle` on release | GREEN |
| A4 | Runtime microphone permission | GREEN |
| A5 | Record raw 16 kHz mono PCM16 while held | GREEN |
| A6 | Add valid WAV header; pulled file plays correctly on laptop | GREEN |
| A7 | Play last recording inside app | GREEN |
| A8 | Scrolling message list with three fake messages + timestamps | GREEN |
| A9 | Type-and-send fallback | GREEN |
| A10 | Fake Speech/Transport interfaces wired into UI | RED |
| A11 | Push-to-talk mode toggle | RED |
| A12 | Alert-message behavior, only after earlier rungs are green | RED |

## Team-lead unblocker

| Task | Status |
|---|---|
| sherpa-onnx Android/AAR integration, without model integration | RED |

## Current critical path

A10

A0-A9 are complete: full capture/playback pipeline, a shared observable message list (direction + read state) driving a Bento-style main screen (Hands-free tile inert, Text + Logs tiles with derived unread badge), a dedicated New Message editor, and a Logs screen with explicit Sent/Received labels, per docs/UI-Reference/itantra_ui_ux_handoff_for_claude.txt.

## Update rule

Only change a rung to GREEN after Vivek confirms the exact physical acceptance test succeeded.
