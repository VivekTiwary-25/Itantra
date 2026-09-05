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
| A10 | Fake Speech/Transport interfaces wired into UI | GREEN |
| A11 | Push-to-talk mode toggle | RED |
| A12 | Alert-message behavior, only after earlier rungs are green | RED |

## Team-lead unblocker

| Task | Status |
|---|---|
| sherpa-onnx Android/AAR integration, without model integration | RED |

## Current critical path

A11

A0-A10 are complete: full capture/playback pipeline, fake Speech/Transport plumbing, and a shared observable message list (direction + read state) that survives Activity recreation. The current A11 scope is a UI-only Push-to-talk mode toggle; real hands-free capture remains Lane 3 work.

## Update rule

Only change a rung to GREEN after Vivek confirms the exact physical acceptance test succeeded.
