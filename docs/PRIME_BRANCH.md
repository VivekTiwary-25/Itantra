# `Prime`: the branch visitors see

(It was first named sunday-best; Vivek renamed it to Prime on 8 Nov 2026.)

`Prime` is this repo's **default branch**. It is what anyone sees when they open
github.com/VivekTiwary-25/Itantra from Vivek's profile: recruiters, judges, other developers.
It is a **clean snapshot** of the latest working app. Your working branches are where the real
work happens. Those branches keep their agent files; `Prime` never has them.

**Read this before you touch `Prime`, and before any task that mentions "public", "visitors",
"default branch" or "clean up the repo".**

## Rules

1. **Never merge** `Prime` into a working branch, and never merge a working branch into
   `Prime`. It is updated only by taking a fresh snapshot (see "How to update").
2. **Only update it when Vivek asks.** Don't refresh it on your own just because the app changed.
3. **No AI attribution on its commits.** No `Co-Authored-By: Claude` (or any model), no session links.
   Commits are authored as `VivekTiwary-25 <vivek.rajesh.tiwary@gmail.com>`.
4. **This file itself does not go on `Prime`.** It's for agents, not visitors.

## `Prime` must NOT have

- Agent instruction and working files: `CLAUDE.md`, `AGENTS.md`, `TASK.md`, `STATUS.md`, `.claude/`,
  `tts-research/AGENTS.md`, any `*HANDOFF*` file, `docs/SESSION_HANDOFF.md`, `docs/TEAM_WORKFLOW.md`,
  `docs/LANE_1_APP_AND_CAPTURE.md`, `docs/CONTRACTS.md`, `docs/PROJECT_FACTS.md`,
  `docs/SHERPA_AAR_UNBLOCKER.md`, `docs/UI-Reference/`, `docs/sih-metrics/PART1_CHECKPOINT.md`,
  prompt files (`*Prompt*.txt`, `opus_*.txt`), `FAILED_EXPERIMENTS.txt`, `STT_TRANSPORT_VERIFICATION.md`
- Agent build specs written as instructions to a model: `docs/networking/EXECUTION_SPEC_BLE_BRANCH.md`,
  `docs/networking/ITANTRA_V1_SPEC_AND_AUDIT.txt`
- Scratch and test output: `test-runs/`, `networking-validation/`, `tts-research/run1/`,
  `tts-research/manifests/`, `tts-research/ml-android/`, `tts-research/mms-ben/int8/`,
  old one-off scripts (`setup_indicconformer_600m*.ps1`, `install-test-phone.ps1`, `tools/verify_tts_handoff.py`)
- **Any mention of agents, models or how the work was split between them**: Claude, Codex, Opus, Sonnet,
  Haiku, GPT, "agent", "subagent", "worker", "foreman", "swarm", usage percentages, branch names like
  `codex/...` used as provenance. Reword the sentence ("this pass", "the quantisation run",
  "the phone-test phase") rather than deleting useful technical content.
- Personal machine paths and usernames (`C:\Users\...`, `D:\projects\...`). Use `$env:LOCALAPPDATA`,
  environment variables or `<path-to>` placeholders. Raw logs under `docs/sih-metrics/raw/` may keep
  their original paths.
- Personal emails other than Vivek's commit identity, team-internal logistics, judge prep and pitch notes.
- Secrets of any kind (keystores, keys, `local.properties`, tokens).

## `Prime` MUST have

- The **latest working app** (all of `app/`, Gradle files, `.gitattributes`, `.gitignore`), and it must still build.
- `setup-models.ps1`, `setup-models.bat`, `setup-phone.ps1`, so someone can actually get the models onto a phone.
- A **visitor-facing `README.md`**: what iTantra is, how it works, what was measured, how to build and run it,
  the team (Team chmod 777, NIE Mysuru, SIH 2026), and third-party model licences. No "For the team" section.
- `docs/STT_INDICCONFORMER.md`, `docs/networking/IMPLEMENTATION_NOTES.md`, `docs/networking/PHYSICAL_TEST_PLAN.md`,
  `docs/sih-metrics/` (RESULTS, STT_DECISIONS, raw evidence, tools): these back up the numbers in the README.
- `tts-research/` (per-language conversion folders, evidence, results, inputs) with its visitor README,
  and `tools/` including `THIRD_PARTY.md` and the vendored library licence.

## Why

- Working branches are full of notes written for and by agents. That's personal context between Vivek
  and his tools, not something visitors should read.
- Deleting those files from a working branch would also delete them from Vivek's laptop on his next
  `git pull`, and that breaks his local Claude Code setup. A separate visitor branch avoids that.
- Git history can't be hidden on a public repo, so the clean branch is a fresh snapshot with its own
  short history instead of a cleanup commit on top of the working history.

## How to update it (only when asked)

1. Find the newest working branch that contains the others
   (`git for-each-ref --sort=-committerdate refs/remotes/origin`), and confirm the choice with Vivek.
2. Replace the snapshot:
   ```
   git checkout Prime
   git rm -r -q .
   git checkout <working-branch> -- .
   ```
3. Remove everything in "must NOT have", then fix wording and paths in the files you keep.
4. Scan, and read every hit (some are fine, like "worker thread" in code):
   ```
   git grep -nIiE "claude|codex|opus|sonnet|haiku|gpt|swarm|subagent|agents?\.md|foreman|worker|handoff" -- . ":!*.csv" ":!*.json" ":!*.log" ":!docs/sih-metrics/raw"
   git grep -nIF -e ":\Users" -e ":\projects" -- . ":!docs/sih-metrics/raw"
   ```
5. Check the "must have" list, then commit as Vivek with a plain message and no AI attribution lines.
   Push only after Vivek okays it.
