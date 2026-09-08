# STATUS.md — iTantra integration/recovery-pass

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
| A11 | Dedicated Hands-free destination with `Listening...`, Back and Done | GREEN |
| A12 | PTT, Hands-free and Text converge on the shared New Message editor | GREEN |
| A13 | Message Detail with per-message read/unread behavior | GREEN |

A10 remains a valid accepted plumbing milestone. A12 refines its user experience by treating voice results as editable drafts which enter Logs only after Send.

## Current critical path

A0–A13 standalone Lane 1 work remains GREEN from its accepted physical tests.

The confirmed integrated baseline is `origin/typed-text-integration@21e28fd`:

| Checkpoint | Status |
|---|---|
| English PTT STT → editable draft → Send → Bluetooth → received Logs | PHYSICALLY VERIFIED at `21e28fd` |
| Standalone RFCOMM connect, repeated text delivery, ACK, reconnect, relaunch/reconnect | PHYSICALLY VERIFIED in the preserved transport checkpoint |
| Relay/store-and-forward | UNVERIFIED |
| Receiver TTS | UNVERIFIED and intentionally not integrated |

Recovery branch changes:

| Item | Status |
|---|---|
| Send-result handling, visible disconnected/error state, integrated ACK and delivery display | IMPLEMENTED + JVM-TESTED + BUILD-TESTED; physical verification pending |
| ISO language metadata across app and transport protocol v2 | IMPLEMENTED + JVM-TESTED + BUILD-TESTED; multilingual physical transport test pending |
| Pinned Dolphin model provisioning in `setup-models.ps1` | IMPLEMENTED + BUILD-TESTED; setup script and official archive/hash comparison passed |

## Integration-deferred work

These are not standalone Lane 1 RED rungs.

| Item | Dependency / state |
|---|---|
| Recovery-branch delivery/ACK and protocol-v2 regression | Physical two-phone verification pending |
| Multilingual language metadata across Bluetooth | Physical two-phone verification pending |
| Add Hands-free VAD, pause segmentation and final continuous-mode state mapping | Deferred; local shared WAV capture is already GREEN |
| Decide final-only versus partial/streaming transcript delivery and exact Hands-free state/event mapping | Lane 3 decoder/API |
| Define alert metadata | Unresolved prerequisite across Lane 1 and Lane 2; do not invent a local field |
| Maximum-volume, non-interruptible received-alert TTS | Lane 2 Transport metadata plus Lane 3 Speech playback |
| Remove `PLAY LAST RECORDING` | Final integration/product cleanup after capture verification is no longer needed |

## Update rule

Vivek physically accepted A11–A13 (`eedd486`) and the post-acceptance Hands-free capture/Message Detail date correction (`36ba653`).

Do not mark the recovery-branch changes physically verified or GREEN until the
required two-phone tests pass on the recovery branch APK.
