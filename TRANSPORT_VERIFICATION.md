# Transport Verification Checkpoint

## Status

The standalone Bluetooth Classic RFCOMM transport is physically **GREEN**.

This checkpoint is the baseline for any later app-integration work. The
transport implementation must not be changed as part of the first integration
rung unless evidence shows that the transport itself regressed.

## Exact tested build

- Repository: `https://github.com/VivekTiwary-25/Itantra.git`
- Branch: `lane/transport`
- Commit: `25faa4e` (`Add in-memory store and forward relay`)
- Build: one debug APK built from commit `25faa4e` and installed identically on
  both physical test phones
- Test roles: Phone A = connector/client; Phone B = listener/server

The APK file hash was not captured during the physical run. The commit above
is the source identity associated with the tested build.

## Physical evidence

Using the same APK on both phones, the following passed:

- RFCOMM connection established.
- Direct text transfer succeeded.
- Repeated distinct messages were delivered without crashes.
- ACK/delivery confirmation succeeded; the sender displayed `Delivery:
  delivered` and the receiver sent the acknowledgement.
- Disconnect and reconnect succeeded.
- Closing and relaunching both apps, then reconnecting, succeeded.
- `RELAUNCH_TEST` was received exactly.
- The sender and receiver displayed the same message ID: `1645226908`.

Screenshots from the physical run show Phone A connected to `realme P3 5G`
with `Delivery: delivered`, and Phone B connected to `realme P4x 5G` with the
exact `RELAUNCH_TEST` payload and matching acknowledgement.

## Next permitted rung

Proceed to typed-text integration only:

`real iTantra UI -> typed text -> verified transport -> real iTantra UI`

The first integration payload must be `INTEGRATION123`. Do not add STT,
received-message TTS, relay changes, Kannada work, or protocol redesign until
typed-text integration is physically GREEN.

## Repository visibility audit

At the time of this checkpoint, the clone exposed these refs:

- `lane/transport`
- `origin/main`
- `origin/lane/app`
- `origin/lane/speech`
- `origin/lane/transport`

The requested `recover/tts-working`, `lane/app-transport-integration`, and
`7cd52a0` were not available in the local clone or fetched remote refs. The
verified transport checkout was not switched, reset, rebased, or rewritten.
