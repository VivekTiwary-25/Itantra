# STATUS.md — iTantra lane/app

Status meanings:

- GREEN: physically demonstrated on a real device
- YELLOW: implemented or partially verified, but physical acceptance is not yet confirmed
- RED: not demonstrated

Never convert status to percentages.

## Standalone Lane 1 ladder

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
| A8 | Scrolling message list with three fake messages and timestamps | GREEN |
| A9 | Type-and-send fallback with shared message state | GREEN |
| A10 | Fake Speech/Transport interfaces wired into UI | GREEN |
| A11 | Dedicated Hands-free destination with `Listening...`, Back and Done | YELLOW — implementation present; automated device verification and Vivek physical acceptance pending |
| A12 | PTT, Hands-free and Text converge on the shared New Message editor | YELLOW — implementation present; automated device verification and Vivek physical acceptance pending |
| A13 | Message Detail with per-message read/unread behavior | YELLOW — implementation present; automated device verification and Vivek physical acceptance pending |

A10 remains a valid accepted plumbing milestone. A12 refines its user experience by treating voice results as editable drafts which enter Logs only after Send.

## Current critical path

Vivek's physical acceptance of A11–A13. No additional standalone Lane 1 implementation should begin before those tests are confirmed.

## Integration-deferred work

These are not standalone Lane 1 RED rungs.

| Item | Dependency / state |
|---|---|
| Replace hardcoded PTT transcription | Lane 3 Speech integration |
| Replace Hands-free `Listening...` and fake completion with continuous capture, VAD and segmentation | Lane 3 Speech integration |
| Decide final-only versus partial/streaming transcript delivery and exact Hands-free state/event mapping | Lane 3 decoder/API |
| Replace fake `sendMessage` and receive callback behavior | Lane 2 Transport integration |
| Define alert metadata | Unresolved prerequisite across Lane 1 and Lane 2; do not invent a local field |
| Maximum-volume, non-interruptible received-alert TTS | Lane 2 Transport metadata plus Lane 3 Speech playback |
| Remove `PLAY LAST RECORDING` | Final integration/product cleanup after capture verification is no longer needed |

## Team-lead unblocker

| Task | Status |
|---|---|
| sherpa-onnx Android/AAR integration, without model integration | RED |

## Update rule

Only change A11, A12 or A13 to GREEN after Vivek confirms its exact physical acceptance test succeeded.
