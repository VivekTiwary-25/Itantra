# AGENTS.md — iTantra

## TTS research transfer branch

On `codex/tts-research-handoff`, read `tts-research/AGENTS.md` and
`tts-research/HANDOFF.md` first. They route the existing desktop TTS experiments,
latest frontend repairs, evidence, and cross-device setup. Root TASK's Dolphin
resume point below is separate STT work. This branch preserves the unchanged
English/Hindi app baseline; it does not claim new language phone acceptance.

Before starting any work in this repo, read `docs/SESSION_HANDOFF.md` in full for project context and current state.

## Purpose

This repository is the iTantra Android prototype for SIH 2026.

You are working on the `lane/app` branch unless the user explicitly says otherwise.

The current branch is intentionally small. Do not invent architecture or implement future work just because the repository is empty.

## Read order

Before changing code:

1. Read `TASK.md`.
2. Read `docs/PROJECT_FACTS.md`.
3. Read the relevant part of `docs/LANE_1_APP_AND_CAPTURE.md`.
4. Read `docs/CONTRACTS.md` if the task touches another lane boundary.
5. Read `docs/SHERPA_AAR_UNBLOCKER.md` only when the current task explicitly says to work on sherpa-onnx.

`TASK.md` is the active scope. The lane document is the acceptance specification. Do not implement later rungs.

## Source precedence

Authority is domain-specific when documents overlap:

1. `TASK.md` controls the current implementation scope.
2. `docs/CONTRACTS.md` controls cross-lane boundaries and interfaces.
3. `docs/LANE_1_APP_AND_CAPTURE.md` controls the Lane 1 ladder and acceptance criteria.
4. `docs/UI-Reference/itantra_ui_ux_handoff_for_claude.txt` controls UI/UX direction.

Older UI handoffs and prototypes are compatible-only visual references. They never override these canonical documents.

## Working rules

- Inspect the existing code before editing it.
- Make the smallest coherent change that satisfies the current task.
- Do not refactor unrelated code.
- Do not add architecture layers merely for future-proofing.
- Do not implement a later ladder rung while completing an earlier one.
- Do not add Bluetooth, Wi-Fi transport, STT, TTS, VAD, model conversion, compression, packet formats, or language expansion during Lane 1 work unless the active task explicitly asks for it.
- Do not change SDK/Gradle/Kotlin/JDK versions unless the current task requires it and you explain why.
- Do not commit, push, merge, or change branches unless the user explicitly asks.
- Do not mark a rung GREEN merely because code compiles. Physical acceptance tests must be performed by the user on a real phone.
- Never report percentages. Use RED / YELLOW / GREEN only where status is requested.
- Preserve the current package/namespace unless explicitly instructed otherwise.
- Treat generated Gradle/build/cache files as non-source artifacts.

## Build and test

Primary Windows build command:

`.\gradlew.bat assembleDebug`

Do not use `--offline` as the primary verification path. The current environment has previously failed offline because the Foojay plugin was not cached, while the normal build succeeded.

After source changes:

1. Run the narrowest useful checks.
2. Run `.\gradlew.bat assembleDebug`.
3. Report the exact result.
4. Tell the user the physical-phone acceptance test for the current rung.
5. Stop. Do not continue to the next rung until the user confirms the physical test.

## Device

Current connected test device, when available:

- Model: RMX3392
- Android: 14 / API 34
- Primary ABI: arm64-v8a

Do not change device settings unless explicitly asked.

## Local dev gotchas

- Pulling binary files (audio, images) off the device must go through cmd.exe, not PowerShell: `cmd /c "adb exec-out run-as <package> cat files/X > X"`. PowerShell's `>` redirect reinterprets binary output as text and corrupts it (confirmed: a 157KB WAV became 557KB and wouldn't play).
- If Bluetooth earphones are connected to the test phone during a mic-capture test, Android may route `AudioRecord` input through them instead of the built-in mic. Disconnect BT audio devices from the test phone before testing any recording rung.

## Known issues

- `PLAY LAST RECORDING` is a temporary capture-verification control, not final product UI. It occasionally cuts off or doesn't fully play. If it is investigated while the control remains, note: (a) whether it cuts off at roughly the same point every time (length/buffer issue) or randomly (`MediaPlayer` lifecycle/threading issue); (b) whether it correlates with re-recording before the previous `MediaPlayer` finishes releasing. Remove the control during final integration/product cleanup once it is no longer needed. Received-alert playback is separate integration-deferred work.

## Lane 1 boundary

Lane 1 owns the app shell, user interaction, microphone capture, WAV output, message UI, typed fallback, and voice-input UI described in the lane document.

The core capture handoff is a file path to a valid WAV containing:

- PCM
- 16,000 Hz
- 16-bit
- mono

Do not implement speech recognition internals or transport internals in Lane 1.

## Fake interfaces

Until real Speech/Transport implementations are integrated, the lane specification intentionally allows fake implementations.

Do not prematurely couple Lane 1 to unfinished Speech or Transport code.

## Response format after a coding task

End with:

### Changed
- concise list of files and behavior changed

### Verification
- commands run
- build/test result

### Physical test
- exact steps the user should perform on the phone
- state remains YELLOW until the user confirms the physical acceptance test

### Blockers / assumptions
- only real blockers or assumptions; write `None` if none

## UI reference

Before making any UI change, read `docs/UI-Reference/itantra_ui_ux_handoff_for_claude.txt`. It is the authoritative UI/UX direction.

The older handoff at `docs/UI-Reference/itantra-ui-reference/ITANTRA_UI_HANDOFF.md` and the interactive prototype source in `docs/UI-Reference/itantra-ui-reference/dist/` are secondary references for compatible layout, proportions, colors, motion, and state transitions only.

Recreate the design natively in Jetpack Compose. Do not embed it in a WebView and do not modify the prototype files.

`TASK.md` controls implementation scope. Implement only the portion of the visual reference relevant to the current rung. Never implement future-rung behavior merely because it appears in the finished prototype.

Then stop.
