# TASK.md — Current Codex Task

## Active task (branch `feature/itantra-v1-networking`)

**iTantra v1 networking: BLE/GATT + PeerLink + Noise XX + secure DTN, then product integration**

Vivek explicitly authorized this work on 2026-09-24 by asking for
`docs/networking/EXECUTION_SPEC_BLE_BRANCH.md` to be executed. The architecture
source of truth is the build specification plus audit corrections in
`docs/networking/ITANTRA_V1_SPEC_AND_AUDIT.txt`. Implementation decisions,
ambiguities and deviations are recorded in `docs/networking/IMPLEMENTATION_NOTES.md`.

Scope rules changed for this branch only, and why:

- `AGENTS.md` forbids adding Bluetooth/transport, packet formats, databases and
  architecture layers during Lane 1 work. This branch is not Lane 1 work: it is
  the explicitly requested networking rebuild, so those layers are in scope here.
- The earlier guardrail "do not add relay changes or store-and-forward changes"
  (written for the Dolphin research branch below) does not apply to this branch.
  The demonstrated RFCOMM path is preserved as prior art behind the new
  abstraction rather than redesigned in place.
- `AGENTS.md` "do not commit or change branches unless asked" is satisfied by the
  execution spec's explicit instruction to create one branch and commit at phase
  boundaries.
- Physical GREEN status is still only assigned by Vivek after real-phone tests.

The previous task text is preserved below unchanged.

---

## Previous active task

**Integrated source prepared for Dolphin conditioning research**

The working canonical application line is `integration/tts-recovery`, tracking
`origin/integration/tts-recovery`. Its latest implementation checkpoint is
`3688704`, based on the confirmed integrated checkpoint
`origin/typed-text-integration@21e28fd` through `integration/recovery-pass`.

Implemented and build/JVM-tested on the integrated recovery line:

- outgoing messages enter Logs only after transport reports `Sent`;
- disconnected/error sends remain in the editor with a visible deterministic error;
- received messages send ACKs and matching outgoing rows become `Delivered`;
- the selected ISO language code crosses app state, protocol v2, and receive state unchanged;
- the eight exposed language codes have automated protocol round-trip coverage;
- `setup-models.ps1` provisions the pinned Dolphin base multilingual INT8 model with verified hashes.

The recovery-specific ACK/delivery UI and the full multilingual protocol matrix
are not separately confirmed by the physical evidence below. Do not generalize
the demonstrated English/Hindi paths to those checks or to other languages.

Physical evidence on the same line now includes the internal-hackathon demonstration:

- `76867a8` fixes the TTS runtime/asset failure and records physical standalone
  English and Hindi synthesis/playback on RMX3392;
- `3688704` wires received English/Hindi messages to TTS after Logs and ACK
  handling, without running TTS on the RFCOMM reader thread;
- the complete English speech → STT → text transmission/relay → receive → TTS →
  audible output pipeline is GREEN on real phones and was consistently reliable
  during the demonstration;
- receive-side TTS and Transport/relay are physically verified; Hindi transport
  and TTS worked, while Hindi Dolphin STT accuracy remains unreliable and can
  produce badly incorrect text or the wrong script;
- this result provides no physical-verification claim for any other language.

## Preserved standalone status

**Lane 1 — standalone work complete through A13**

Vivek has physically accepted A11–A13 and the post-acceptance correction. A0–A13 are GREEN; the accepted commits are `eedd486` and `36ba653`. Do not begin another standalone Lane 1 feature.

Implemented scope:

- A11: the Hands-free tile opens a dedicated dark/gold screen with `Listening...`, Back and Done; Main retains the dominant PTT.
- A12: Text opens the shared editor blank; PTT and Hands-free open it with the final transcript; only Send can append one outgoing message; non-empty Back offers Cancel/Discard. The standalone acceptance used a hardcoded transcript, while the integration branch now supplies `SpeechEngine` output.
- A13: Logs rows open read-only Message Detail; opening one unread incoming row marks only that message read; Logs remains newest-first and the Main badge remains derived from unread received messages.
- Accepted post-acceptance correction: Hands-free starts/stops the same local PTT WAV recorder and shares its last-recording playback file; Message Detail persists and displays date plus time.

## Physical acceptance completed

1. Launch the app and confirm Main still shows the dominant `HOLD TO TALK` control.
2. Open Hands-free. Confirm `Listening...`, Back and Done are present; use Back and confirm Main/shared state is preserved.
3. Open Text and confirm the editor is blank. Enter text, press Back, confirm Cancel stays in the editor, then confirm Discard leaves it.
4. Note the current Logs contents. Hold PTT, speak and release while keeping one orientation. Confirm the editor opens with `this is a test message`; Back/Discard it, reopen Logs, and confirm transcription alone added nothing.
5. Repeat PTT, edit the draft, press Send, then confirm Logs contains the edited text exactly once as the newest `Sent` message.
6. Open Hands-free, press Done, and confirm the same editor opens pre-filled with `this is a test message`; Back/Discard the temporary draft.
7. Open Logs without opening a row, return to Main, and confirm the unread badge did not change.
8. Open one unread received row. Confirm Message Detail shows its full text, timestamp and `Received`; return to Logs and confirm only that row is read and the Main unread badge drops by one.
9. Open the sent row and confirm Message Detail labels it `Sent`; confirm Logs remains newest-first.

The checklist above passed on Vivek's physical phone.

## Scope guardrails

Do not add more TTS languages, VAD, pause segmentation, alert metadata, durable persistence, Navigation Compose, a repository, a database, DI, a second capture pipeline, or more standalone Lane 1 rungs. Do not redesign the physically demonstrated Transport/TTS path while researching the unresolved Hindi STT accuracy problem.

## Next resume point

The next code branch is `research/dolphin-conditioned`, based on the canonical
integrated line. Its purpose is future Dolphin STT/language-conditioning research;
the experiment has not started. Preserve the demonstrated English pipeline and
Hindi Transport/TTS behavior, and do not add Kannada/Malayalam selector options,
relay changes, store-and-forward changes, or further standalone Lane 1 work.
