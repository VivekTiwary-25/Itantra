# TTS research transfer handoff

Snapshot date: 2026-09-26. Repository: https://github.com/VivekTiwary-25/Itantra.
Branch: `codex/tts-research-handoff`. App baseline:
`0e63c4581f4a1fa465ed64e17ef5ff4067e16edc`.

## What the user is doing

The team is investigating the eight missing offline TTS languages while
preserving the working English/Hindi receive-side speech pipeline. There was
a desktop RUN 1 across the candidate queue, followed by isolated Tamil/Telugu/
Odia frontend repairs. The user now wants the current files and enough context
on GitHub to continue on another computer with another coding agent.

The requested task here was packaging and pushing that existing state. No new
language integration, model conversion, listener approval, phone test, licensing
decision, or candidate promotion is implied by this transfer.

## Read order

1. `AGENTS.md` here and this handoff.
2. `ORIGINAL_TTS_HANDOFF.md`: complete candidate queue, tracks, acceptance gates,
   timeboxes, licensing decisions, and stop rules. Its filename originally
   contained v1, but its own heading says draft v2; the content is preserved.
3. `run1/TTS_LOG.md`, `run1/OG.md`, `run1/RUN2_CHECKLIST.md`.
4. `frontend-repair/FRONTEND_REPAIR_REPORT.md` and the three per-language logs.
5. Inspect `tools/` and actual evidence before proposing the next change.

RUN 1 records are historical and correctly describe their own earlier state.
The later frontend report supersedes only the statement that ta/te/or still
silently discard the tested characters. It does not clear their listener,
checkpoint-license, dynamic acoustic runtime, or Android gates.

## Worktree explanation and exact current state

A worktree is another local folder checked out on its own branch, sharing Git
history. Those folders and their uncommitted files do not travel automatically
when a branch is pushed.

- Twelve candidate folders still point to BASE_SHA: bn/mr/kn/ta/te Rasa and
  bn/gu/mr/kn/ta/te/or Indic-TTS. They have no tracked app-source changes.
- Their useful work was untracked helper scripts, input files, repair reports,
  and ignored generated evidence. This transfer copies those files into one
  dedicated research branch while retaining original paths/hashes as provenance.
- Malayalam was originally in `D:\iTantra-tts-ml` on `tts/ml/piper`. That folder
  was later reused for `UI/UX_discussion`. Its original TTS files remained local
  and untracked. They are included here; the UI/UX commit is not this branch's base.
- `manifests/original-worktrees.json` records the inspected branches/heads/status.
  `manifests/copied-files.json` maps each source path to its new path/hash, and
  records excluded large export artifacts. Identical tools from multiple
  candidates were deduplicated after checking their bytes.
- Existing candidate folders and the UI/UX checkout were not switched, cleaned,
  reset, committed, or merged to make this handoff. The new transfer worktree is
  an independent checkpoint. No force-push is needed.

Do not recreate all old worktrees just to review results. Clone the transfer
branch, read/listen to the saved evidence, and create a fresh candidate worktree
only when the next implementation task requires one.

## Current results, without overstating them

| Candidate | Saved desktop outcome | Remaining gate |
|---|---|---|
| Existing English Ryan / Hindi Pratham | Historical working phone baseline; one fresh desktop smoke WAV each | RUN 1 did not repeat their phone regression |
| Malayalam Piper Arjun | Ten WAVs through official sherpa-onnx package; ready ONNX format | Speaker approval, blind listening, attribution review, app wiring and phone tests |
| Bengali/Gujarati/Marathi/Kannada Indic-TTS | Ten WAVs each; export proof attempted | Acoustic graph fails different text length; checkpoint terms UNKNOWN; no listener/phone proof |
| Tamil/Telugu/Odia Indic-TTS RUN 1 | Ten WAVs each, with native character loss | Original evidence retained; superseded frontend handling below |
| Tamil/Telugu/Odia repaired frontend | Five reported regression checks per language and ten new WAVs each; tested characters have token IDs | All audio QUALITY UNVERIFIED; runtime/license/Android gates remain |
| Rasa bn/mr/kn/ta/te | Access-gated weights; unauthenticated HTTP 401 during RUN 1 | Accepted official access and later runtime/quality proof |
| Meta MMS fallback queue | Not downloaded or implemented | Human CC BY-NC 4.0 decision, then queue/gates |

All 80 RUN 1 candidate WAVs and 30 repaired WAVs remain QUALITY UNVERIFIED.
The eight ten-line input files are still unapproved drafts. No new-language
candidate has been wired into Android or passed the offline received-text path.
Do not say the frontend repair makes Tamil/Telugu/Odia phone-ready.

## What the frontend repair actually changed

Original Coqui checkpoint vocabularies dropped unsupported characters while
synthesis continued. The wrapper now verbalizes ta/te/or numbers with pinned
AI4Bharat `indic-numtowords` 1.1.0; maps Odia equivalent nukta spellings to the
checkpoint's precomposed characters; narrowly handles observed Telugu U+200C
after virama; and rejects remaining unsupported input instead of silently losing it.
Leading-zero digit runs and unsupported joiner contexts are explicitly rejected.

The input files, original checkpoints/configs, and original WAVs were unchanged.
New output is under each `evidence/<code>-indic/repair-20260926/` directory.
`frontend_trace.json` retains original/repaired codepoints/text/token IDs.
`acoustic_segments.json` records the actual split-segment acoustic token sequences.
The written Telugu shaping request changes when U+200C is removed; identical
token/audio output is not proof that the intended pronunciation is correct.
A native listener must resolve that, and the contextual number wording.

## Files transferred

- The unchanged baseline Android source from BASE_SHA, including its existing
  TTS interface and model-provisioning script.
- Exact original handoff; RUN 1 operational guide/log/checklist/commands/results.
- Latest frontend repair report, 30-row results, and ta/te/or continuation logs.
- Eight byte-frozen draft input files.
- Generic Piper/sherpa and Indic desktop probes, ONNX probe, repair wrapper,
  repaired probe, real-vocabulary regression tests, acoustic trace helper.
- Vendored number-word package code, license, and metadata.
- 112 WAVs total: 80 RUN 1 candidate, 30 repaired, two English/Hindi smoke files.
  Audio payload is 21,662,148 bytes. Timings, errors, run logs and trace data accompany it.
- Portable result tables in `results/` have clone-relative WAV paths. Their
  original paths are retained in an added provenance column. Original tables
  and traces were not rewritten.
- Source-copy and external-asset manifests, plus a portable transfer verifier.

The two original collectors contain machine-specific paths; they are preserved
in `original-scripts/` for provenance. Use `tools/verify_tts_handoff.py` to audit
this transferred snapshot. CLI inference/repair probes accept explicit model,
input, and output paths and can be used on the new computer.

## Things GitHub does not restore

Large candidate checkpoints/archive weights and failed ONNX export blobs are
excluded. They remain under `D:\iTantra-tts-models` and the original ignored
recording folders on the source machine. Neither Python/WSL venvs, SDK/JDK,
APK/build/cache files, `local.properties`, nor credentials are included.

`manifests/external-assets.json` records official archive URLs, exact size,
recorded RUN 1 SHA-256, extraction path and remaining license gate. The release
URLs/sizes were checked against the official GitHub release metadata during
transfer; the hashes come from the saved download logs. Re-download only the
candidate you need, or copy the original archive/model directory separately
and verify it. Do not download all seven 1.5 GB Indic archives by default.

For Android English/Hindi and STT assets, the baseline root `setup-models.ps1`
restores its ignored binaries. That script is separate from the research
candidate model manifest. The new clone needs its own local Android SDK path.

Recorded desktop environment: Python 3.10.21 in WSL Ubuntu, CPU torch/torchaudio
2.2.2, Coqui TTS 0.22.0, ONNX 1.17.0, ONNX Runtime 1.18.1; Piper desktop used
sherpa-onnx 1.13.7 to match the Android AAR. Version pins are a reproduction
starting point, not a portable frozen venv. See README for rerun commands.

The original Indic config has stale speaker-file paths. Before running its
probe on a new computer, create external `fastpitch/config-local.json` from the
original config and point both `speakers_file` fields to the current
`fastpitch/speakers.pth` path. The README includes a small path-preparation
helper; original model config and checkpoint bytes are preserved.

## Resume point

First verify the transfer, then collect native-speaker input approval and blind
listener feedback on the retained audio. Check whether the human has new results
or a licensing/access decision beyond these saved reports before doing new work.
No such newer feedback is recorded in this snapshot.

Malayalam Arjun is the next candidate with the most direct saved runtime path.
Missing feedback is a hold, not a reason to skip to Meera. For Indic-TTS,
frontend compatibility alone does not solve the variable-length acoustic graph
or Android runtime gap. A future experiment must follow the bounded original
queue and stop rules; preserve English/Hindi throughout.

Phone acceptance remains exactly the sequence/gates in
`run1/RUN2_CHECKLIST.md`: real incoming text with language metadata, offline
receiver, repeated messages, audible timing/RTF/memory/bytes, blind listener and
English/Hindi regression. Generated audio must never be sent over Transport.

## Verification scope for this transfer

Copied evidence hashes were checked against source and original CSV WAV hashes.
The portable verifier passed for 306 unique copied files, 110 candidate audio
rows, and 112 retained WAVs. All nine tool scripts compiled. The local speaker
config helper passed checks for both path replacements, original-config
preservation, and refusing to overwrite an existing local config. Git attributes
preserve frozen file bytes across Windows/Linux line-ending settings.
The consolidated scripts are syntax-checkable without loading models; the
transfer verifier uses Python's standard library. This task does not rerun
inference, re-score sound, repeat old build measurements, or establish new phone
acceptance. Original build/JVM results and failures remain in the RUN 1 log.
