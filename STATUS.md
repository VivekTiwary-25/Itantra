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
| A5 | Record raw 16 kHz mono PCM16 while held | RED |
| A6 | Add valid WAV header; pulled file plays correctly on laptop | RED |
| A7 | Play last recording inside app | RED |
| A8 | Scrolling message list with three fake messages + timestamps | RED |
| A9 | Type-and-send fallback | RED |
| A10 | Fake Speech/Transport interfaces wired into UI | RED |
| A11 | Push-to-talk mode toggle | RED |
| A12 | Alert-message behavior, only after earlier rungs are green | RED |

## Team-lead unblocker

| Task | Status |
|---|---|
| sherpa-onnx Android/AAR integration, without model integration | RED |

## Current critical path

A5 -> A6

A6 is the first major capture milestone: a valid 16 kHz, 16-bit, mono PCM WAV that can be pulled from the phone and played at normal speed.

## Update rule

Only change a rung to GREEN after Vivek confirms the exact physical acceptance test succeeded.
