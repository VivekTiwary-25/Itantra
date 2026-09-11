You are performing an unattended, CODE-ONLY integration recovery pass for iTantra.

The user will not be present to perform physical phone tests while you work.

Therefore:

**DO NOT claim any new runtime capability is physically verified or GREEN.**

You may implement, build-test, JVM-test, document, commit, and push clean code changes.

Physical verification will happen later when the user returns.

---

# CURRENT EVIDENCE-BASED BASELINE

Previous audit established:

- `origin/typed-text-integration@21e28fd` is currently the best integrated app + speech + transport state.
- Standalone transport source `25faa4e` has physically verified RFCOMM connection, repeated text delivery, ACK, reconnect, and relaunch/reconnect.
- `21e28fd` has physically verified English PTT STT → editable draft → Send → Bluetooth → received Logs.
- Receiver TTS is deliberately absent.
- Relay/store-and-forward are not physically verified.
- `origin/experiment/mms-tts` is experimental and must NOT be merged.

Before doing anything, FETCH and independently confirm that these refs have not changed.

If `origin/typed-text-integration` moved since the audit, inspect the delta before choosing a baseline.

---

# GIT HYGIENE — NON-NEGOTIABLE

Do not modify the existing verified branch directly.

Create a NEW recovery/integration branch from the confirmed best integrated baseline.

Use a clear branch name such as:

`integration/recovery-pass`

or another similarly explicit name if that already exists.

Before changes record:

```powershell
git status
git branch --show-current
git log -1 --oneline
```

Requirements:

- preserve `21e28fd` or its confirmed successor untouched;
- no force pushes;
- no rebasing shared branches;
- no destructive resets;
- do not use `git add .` blindly;
- inspect `git diff`;
- stage only intended files;
- inspect `git diff --cached` before every commit;
- keep unrelated work out;
- leave `prompt2.md` and other unrelated untracked user files untouched.

Each logical bundle gets its OWN commit.

A build-tested commit must be described as implementation/build-tested only.

Do NOT use commit messages implying physical verification.

Example acceptable wording:

`Integrate ACK and transport delivery result handling`

Not:

`Verify ACK works`

because no physical phone test is available during this session.

Push each successful clean checkpoint if remote authentication allows it.

If a push fails, record the exact error and continue locally if safe.

---

# GENERAL EXECUTION RULE

For each bundle:

1. inspect current implementation;
2. explain internally what must change;
3. make only that bundle's changes;
4. inspect diff;
5. run relevant JVM/unit tests;
6. run Android build;
7. if build/tests fail, diagnose and fix only that bundle;
8. if the bundle cannot be made cleanly buildable, revert that bundle and document the blocker;
9. if successful, commit it separately;
10. continue to the next bundle.

Do not pile later bundles onto a broken earlier bundle.

At the end, the branch should be clean.

---

# BUNDLE A — DELIVERY STATE + INTEGRATED ACK

The audit found two concrete integration defects:

1. The app currently appends outgoing messages locally regardless of `Sent`, `NotConnected`, or error result.
2. `NotConnected` is silently ignored.
3. Standalone ACK works physically, but typed integration does not invoke `sendAcknowledgement()`.

Fix these seams minimally.

Goals:

- transport result must influence the app-visible send/delivery state;
- do not present a failed/not-connected send as successfully delivered;
- handle `NotConnected` visibly and deterministically;
- wire the existing ACK mechanism into the integrated receive path;
- preserve the existing physically verified transport implementation as much as possible;
- do not redesign the UI unnecessarily.

Add or improve automated tests where practical for result-state mapping and ACK-related pure logic.

Do not alter relay behavior.

After implementation:

```powershell
.\gradlew.bat test assembleDebug
```

or the repository-equivalent Gradle commands.

If successful, create a dedicated commit.

Mark in documentation/report:

**IMPLEMENTED + BUILD/JVM TESTED — PHYSICAL VERIFICATION PENDING**

---

# BUNDLE B — PRESERVE LANGUAGE METADATA END TO END

The audit found:

- the application exposes English, Hindi, Gujarati, Marathi, Tamil, Telugu, Odia, Bengali;
- the transport protocol supports only a two-value English/Hindi enum;
- integrated sending currently forces every message to English;
- the contract requires the sender's language to survive to the receiver.

Correct this.

Goal:

```text
STT / selected language
        ↓
ISO language code
        ↓
app Message
        ↓
transport frame
        ↓
receiver
        ↓
same ISO language code
```

Examples:

```text
English    en
Hindi      hi
Gujarati   gu
Marathi    mr
Tamil      ta
Telugu     te
Odia       or
Bengali    bn
```

Do not add Kannada or Malayalam to the runtime selector as part of this work.

Choose the smallest clean representation that allows the actual language identifier to cross the wire.

Prefer the application's existing ISO-language representation rather than maintaining an artificially restricted transport enum, unless current implementation constraints strongly justify otherwise.

Update:

- transport framing/serialization,
- parser/receiver,
- application send path,
- received Message construction,
- relevant data structures,
- tests,
- contracts/docs directly affected by the API change.

Ensure malformed/unsupported language metadata fails safely rather than corrupting the reader/socket state.

Add automated round-trip tests for at least all currently exposed language codes.

Build and test.

If successful, commit separately.

Status must remain:

**IMPLEMENTED + BUILD/JVM TESTED — MULTILINGUAL PHYSICAL TRANSPORT TEST PENDING**

---

# BUNDLE C — DOLPHIN MODEL PROVISIONING / REPRODUCIBILITY

The audit found that `.onnx` model binaries are ignored and `setup-models.ps1` restores Whisper/Piper assets but does NOT provision the Dolphin multilingual model required for seven exposed STT languages.

Make the multilingual STT setup reproducible from a clean checkout IF repository evidence provides enough trustworthy information to do so.

Inspect:

- current local model filenames/paths;
- existing setup scripts;
- documentation;
- commit history;
- model source information already recorded in the repo.

Goals:

- `setup-models.ps1` or an appropriate companion setup mechanism obtains/places the exact required Dolphin model files;
- correct destination paths;
- clear failure messages;
- ideally integrity/version information where evidence permits;
- documentation explaining what gets downloaded/restored.

CRITICAL:

Do NOT invent a model URL, hash, release location, license, filename, or source.

If the repository does not contain enough evidence to create a trustworthy deterministic provisioning path:

- do NOT fabricate one;
- leave runtime code unchanged;
- add a precise blocker note/report explaining exactly what information is missing;
- do not commit a fake setup solution.

If provisioning can be implemented reliably, test the script as far as practical without damaging the known local model installation.

Commit separately only if the change is sound.

---

# BUNDLE D — DOCUMENTATION RECONCILIATION

After Bundles A–C, reconcile relevant canonical docs with actual code.

Audit identified stale statements in:

- `STATUS.md`
- `TASK.md`
- `docs/PROJECT_FACTS.md`
- potentially `docs/CONTRACTS.md`

Update them to reflect actual implementation status.

Maintain strict status language:

- PHYSICALLY VERIFIED
- IMPLEMENTED
- BUILD-TESTED
- JVM-TESTED
- UNVERIFIED
- FAILED / REVERTED

Do not convert the unattended changes from this session into physical GREEN claims.

Preserve the recorded physical checkpoints that already exist.

Explicitly state which new code changes await phone verification.

Commit documentation reconciliation separately.

---

# DO NOT TOUCH DURING THIS PASS

Do NOT:

- integrate `origin/experiment/mms-tts`;
- wire received messages into TTS;
- revive Kannada;
- add Malayalam;
- modify speech model accuracy behavior unnecessarily;
- redesign the app;
- redesign transport architecture beyond the language-metadata requirement;
- attempt relay improvements;
- attempt store-and-forward improvements;
- merge `origin/main` wholesale;
- revive stale `7cd52a0`;
- overwrite the known physical transport checkpoint.

Receiver TTS will be handled later with the user present because TTS itself remains physically unverified.

---

# FINAL BUILD

After all successful bundles:

Run the complete available project tests/build from the final recovery branch.

At minimum:

```powershell
.\gradlew.bat test assembleDebug
```

Record:

- command;
- success/failure;
- test counts if available;
- warnings/errors;
- final APK path if generated.

Calculate and record the SHA-256 of the final APK if practical.

Do NOT install it on a phone or claim runtime success unless physical devices are actually available and explicitly part of this session.

---

# FINAL GIT CHECK

Report:

```powershell
git branch --show-current
git status
git log --oneline --decorate -10
```

Confirm whether the tree is clean.

Report every commit created in this session:

- hash
- message
- files/purpose
- build/test result
- push result

Report final remote branch name and tip if push succeeds.

---

# FINAL REPORT

Return:

## EXECUTIVE SUMMARY

## BASELINE CONFIRMED

## BUNDLE A — DELIVERY STATE + ACK
- changes
- tests/build
- commit
- push
- physical status

## BUNDLE B — LANGUAGE METADATA
- changes
- tests/build
- commit
- push
- physical status

## BUNDLE C — DOLPHIN PROVISIONING
- changes or blocker
- evidence
- tests
- commit/push if applicable

## BUNDLE D — DOCUMENTATION

## FINAL BUILD RESULT

## COMMITS CREATED

## PUSH RESULTS

## FINAL BRANCH / WORKING TREE

## PHYSICAL TESTS REQUIRED WHEN USER RETURNS

Provide an exact small phone-test checklist for the user.

The final test checklist should include:

1. typed `INTEGRATION123` A → B;
2. repeated integrated sends;
3. disconnect/reconnect;
4. app relaunch/reconnect;
5. successful integrated ACK/delivery display;
6. disconnected-send behavior;
7. English STT → transport regression;
8. Hindi STT → exact Hindi text received;
9. Gujarati → exact Gujarati text received;
10. Bengali → exact Bengali text received;
11. other exposed languages only where STT itself is ready for acceptance.

Finish by stating clearly:

**What is physically GREEN from previous evidence**

versus

**What this unattended session only implemented/build-tested and still needs physical verification.**