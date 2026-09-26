# TTS research workspace instructions

Read `HANDOFF.md`, `README.md`, and `ORIGINAL_TTS_HANDOFF.md` in full before
continuing. Inspect the latest frontend-repair report as well as RUN 1 logs.

This branch snapshots desktop research for offline received-text-to-speech.
Preserve the known working English/Hindi application baseline. Do not work on
STT, translation, Transport/routing/security, or UI redesign as part of this task.
The root engineering TASK's Dolphin/STT resume point is separate work.

## Evidence rules

- `evidence/`, `inputs/`, original reports, and original result tables are frozen
  recorded evidence. New experiments use new output directories and separate
  tables. Never overwrite existing WAVs to make a previous run look better.
- All eight input files are unapproved drafts. Byte-frozen is not speaker-approved.
- WAV generation, tokenizer compatibility, listening, Android runtime proof,
  and offline received-text phone acceptance are separate gates.
- All RUN 1 and repaired new-language audio remains QUALITY UNVERIFIED.
  All new-language Android/phone tests remain PENDING PHONE in the saved evidence.
- Rasa access, Indic checkpoint terms, Android/dynamic ONNX proof, and MMS
  CC BY-NC approval are unresolved gates. Do not treat MIT code as a checkpoint
  redistribution license or begin MMS without the team's decision.
- Use only the candidate queue in the original handoff and its bounded setup/
  export timeboxes. Missing listener feedback is not a failed candidate.
- Record original URLs, hashes, environment, exact commands, and measured outcomes.
  Do not infer that all languages work because they can be selected.
- The old absolute paths in reports/traces are provenance. Use clone-relative
  tables in `results/` and the new-device instructions to access the same files.
- Large weights, failed ONNX export blobs, venvs, SDKs, APKs, caches, and credentials
  are outside the transfer snapshot. Restore the selected asset using the manifest.
- Preserve vendored third-party license/metadata and attribution.

## Git and isolation

The transfer branch is `codex/tts-research-handoff`, based on app commit
`0e63c4581f4a1fa465ed64e17ef5ff4067e16edc`. The local source worktrees are
recorded in `manifests/original-worktrees.json`. They have no candidate app-code
commits. Do not switch or discard those original dirty folders.

Another device can clone this one branch; it does not need to recreate all 13
folders to read evidence. A future candidate implementation uses a fresh isolated
worktree from this research checkpoint, with the app baseline provenance retained.
Commit/push only when the user asks; do not merge research into main or UI/UX.

## Verification

Run `python tools/verify_tts_handoff.py` from the repository root to check copied
file hashes and the 80 RUN 1 / 30 repaired audio rows. This is transfer integrity,
not acoustic quality. Original frontend tests require the actual checkpoint
config and Coqui environment. See README for commands and dependency pins.

Native app source changes additionally require root engineering checks,
`.\gradlew.bat test`, `.\gradlew.bat assembleDebug`, and the phone acceptance
sequence in `run1/RUN2_CHECKLIST.md`. A desktop-only transfer does not rerun or
establish phone acceptance.
