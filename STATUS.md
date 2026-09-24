# STATUS.md — iTantra integration/tts-recovery

Status meanings:

- GREEN: physically demonstrated on a real device
- YELLOW: implemented or partially verified, but physical acceptance is not yet confirmed
- RED: not demonstrated

Never convert status to percentages.

## v1 networking branch (`feature/itantra-v1-networking`)

Evidence levels: JVM = automated JVM tests over in-memory TEST-DOUBLE links (real
fragmentation, Noise, crypto and DTN; no radio). DEVICE-1 = one physical RMX3392
(Android 14). No two-phone or three-phone run has happened yet.

| Component | Status | Evidence |
|---|---|---|
| Canonical CBOR, ProtocolConfig, frame codecs | YELLOW | JVM |
| Identity capsule, QR codec, trusted contacts | YELLOW | JVM; QR scan/paste UI not exercised on a phone |
| Keystore-wrapped identity, no-backup storage | YELLOW | DEVICE-1 instrumented test (wrapping key reports TEE) |
| Pair secret, destination tags, sign + HPKE seal, recipient verification | YELLOW | JVM, including impersonation/tamper tests |
| GATT fragmentation / reassembly | YELLOW | JVM |
| BLE advertise, scan, GATT server start | YELLOW | DEVICE-1: advertiser accepted the 31-byte payload, server registered, scan running. Nothing was seen by a second radio |
| GATT client link, MTU, CCCD, HELLO, collision rule | RED | Not demonstrated; needs two phones |
| Noise XX hop sessions | YELLOW | JVM (tamper, replay, plaintext, timeout); not over BLE |
| DTN store/carry/forward, Spray-and-Wait, receipts, tombstones, expiry | YELLOW | JVM scenarios (spec Tests A–E, H); SQLite durability on DEVICE-1 |
| Direct SOS (offer/accept/decline/SAS/chat) | YELLOW | JVM |
| Multi-hop SOS requests and cancellation | YELLOW | JVM unit logic only |
| Relayed interactive SOS session | RED | Not built (spec §41: after direct SOS is stable) |
| EmergencyModeService foreground service | YELLOW | DEVICE-1: foreground, type connectedDevice, no crash |
| Transport benchmark harness | YELLOW | JVM probes through 2 hops; no radio measurement exists |
| Gates B–F (`docs/networking/PHYSICAL_TEST_PLAN.md`) | RED | Not run |
| Product integration: STT draft → trusted recipient → Queued/Relayed/Delivered UI | YELLOW | Build + JVM (state mapping); not run on a phone |
| Product integration: receive → Logs → TTS only on the end recipient | YELLOW | Build; relay-never-delivers covered by JVM integration test |
| Legacy RFCOMM demo behind opt-in switch, ACK no longer shown as Delivered | YELLOW | Build + JVM; not re-run physically |

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

The working canonical application line is `integration/tts-recovery`. Its latest
implementation checkpoint is `3688704`; `integration/recovery-pass@c8f8a9b` is
its preserved parent checkpoint, not the current integrated tip.

The confirmed integrated baseline is `origin/typed-text-integration@21e28fd`:

| Checkpoint | Status |
|---|---|
| English PTT STT → editable draft → Send → Bluetooth → received Logs | PHYSICALLY VERIFIED at `21e28fd` |
| Standalone RFCOMM connect, repeated text delivery, ACK, reconnect, relaunch/reconnect | PHYSICALLY VERIFIED in the preserved transport checkpoint |
| Transport and relay during the internal-hackathon pipeline | GREEN — physically demonstrated on real phones |
| Store-and-forward | RED — not established by the internal-hackathon result |
| Standalone English and Hindi TTS | GREEN — `76867a8` records physical synthesis/playback on RMX3392 |
| Receive-side English/Hindi TTS | GREEN — physically demonstrated on real phones |
| Full English speech → STT → text transmission/relay → receive → audible TTS | GREEN — consistently reliable during the demonstrated pipeline |
| Hindi transport and receive-side TTS | GREEN — physically demonstrated |
| Hindi Dolphin STT accuracy | YELLOW — working but unreliable; badly incorrect text or the wrong script can occur |
| Other-language end-to-end paths | RED — not demonstrated by this evidence |

Integrated recovery-line changes:

| Item | Status |
|---|---|
| Send-result handling, visible disconnected/error state, integrated ACK and delivery display | IMPLEMENTED + JVM-TESTED + BUILD-TESTED; physical verification pending |
| ISO language metadata across app and transport protocol v2 | IMPLEMENTED + JVM-TESTED + BUILD-TESTED; English/Hindi paths physically exercised, other exposed languages pending |
| Pinned Dolphin model provisioning in `setup-models.ps1` | IMPLEMENTED + BUILD-TESTED; setup script and official archive/hash comparison passed |

## Integration-deferred work

These are not standalone Lane 1 RED rungs.

| Item | Dependency / state |
|---|---|
| Recovery-branch delivery/ACK UI cases | Not separately established by the hackathon demonstration |
| Language metadata beyond demonstrated English/Hindi paths | Physical two-phone verification pending |
| Improve Hindi Dolphin STT accuracy | Unresolved research work; Transport and TTS are already physically working |
| Add Hands-free VAD, pause segmentation and final continuous-mode state mapping | Deferred; local shared WAV capture is already GREEN |
| Decide final-only versus partial/streaming transcript delivery and exact Hands-free state/event mapping | Lane 3 decoder/API |
| Define alert metadata | Unresolved prerequisite across Lane 1 and Lane 2; do not invent a local field |
| Maximum-volume, non-interruptible received-alert TTS | Lane 2 Transport metadata plus Lane 3 Speech playback |
| Remove `PLAY LAST RECORDING` | Final integration/product cleanup after capture verification is no longer needed |

## Update rule

Vivek physically accepted A11–A13 (`eedd486`) and the post-acceptance Hands-free capture/Message Detail date correction (`36ba653`).

The internal-hackathon result establishes the English full pipeline, receive-side
TTS, and Transport/relay as GREEN. It does not establish other-language paths,
store-and-forward, or every recovery-specific ACK/delivery UI case.
