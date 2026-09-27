# iTantra TTS: research and implementation handoff — draft v2

**For:** a browser research agent, a terminal coding agent, and their human teammate.  
**Updated:** 24 September 2026.  
**Scope:** received text → audible, fully offline speech on an ordinary Android phone. Do not work on STT, translation, Bluetooth, routing, security, or visual redesign in this task.

## Start here — instructions for the friends using this document

**You do not need to understand Git in advance. Follow these steps in order.** One friend can do the browser track while another starts the terminal track. Give each agent **this whole document**, not a pasted model table without the stop rules.

### A. Hand this to the agents

1. The friend doing online research opens a browser-capable AI chat, attaches this document, and says: **“Read the whole handoff. You own Track A only. Find the original model evidence in its specified order. Return the evidence packet. Do not claim any model has passed an app or phone test.”** They do not need the Android repository or a Git branch.
2. The friend who can access the Android repository opens their terminal coding agent **inside the repository**, attaches this document, and says: **“Read the whole handoff. You own Track B. First report the repository state and identify a known-good base. Do not edit files until the base and a separate worktree are established. Preserve Hindi and English. Start one candidate only after checking original model sources.”**
3. When Track A finishes, give its full evidence packet to the Track B agent with: **“Use this as source evidence for the candidate queue. Check the original links and flag any conflict with the handoff. Continue the isolated experiment.”** A browser finding is a lead; the terminal agent still has to test it.

### B. Terminal friend: get to a safe starting point

4. If you **already have the repo**, open a terminal in its top-level folder. If you **do not have it**, ask the team member who owns it for the repository URL and access, then run `git clone <TEAM_REPO_URL> iTantra` and `cd iTantra`. Never paste credentials into an AI chat. If there is no accessible repo, stop the coding track and report that blocker; Track A may still proceed.
5. Run these commands one at a time. Send the outputs to your terminal agent:

   ```bash
   git status --short
   git branch --show-current
   git log -1 --oneline
   git rev-parse HEAD
   ```

   `git status --short` shows local changes; an empty result means the working tree is clean. The final command gives the exact current commit ID. **Do not run `git reset --hard`, `git clean`, `git pull`, or `git stash` to make the output look clean.** Those commands can discard or obscure a teammate's unfinished work.
6. Tell the agent which commit the team considers the **latest working English/Hindi build**. If unsure, have it inspect Git history, APKs, and the build before choosing. The newest commit is not automatically stable. Write down that commit ID as `BASE_SHA`. Keep the existing worktree, even if it has uncommitted changes.
7. Have the agent run `git fetch origin` to update its view of remote commits **without merging them**. If this repository has no `origin` remote or fetch is blocked, record that and continue only from a locally verified `BASE_SHA`.
8. Create a separate worktree for the **first selected candidate**. For example, if starting Bengali Rasa, run the following from the original repo folder, substituting the real commit ID for `BASE_SHA`:

   ```bash
   git worktree add -b tts/bn/rasa ../iTantra-tts-bn BASE_SHA
   cd ../iTantra-tts-bn
   git status --short
   git rev-parse HEAD
   ```

   A worktree is another folder with its own branch. Your original app folder stays as it was. If the branch or destination folder already exists, **do not delete either**; choose a new name such as `tts/bn/rasa-2` and `../iTantra-tts-bn-2` with the agent.
9. Only now tell the agent: **“Build the untouched baseline in this worktree and test English/Hindi first. Record the commands and results. Then begin the named TTS candidate.”** If the baseline fails, stop model changes and report the failing build; do not blame the new model.

### C. When one candidate is finished

10. Ask the agent for the exact test log and `git status --short`. For a pass, it should stage **only relevant source/config/manifest files** using `git add <file1> <file2>`, inspect `git diff --cached`, and commit with a specific message, such as `git commit -m "Add Bengali TTS candidate and evidence"`. Model weights go into the team's agreed asset storage, with a source URL and SHA-256 in the manifest; do not stage giant binaries by accident with `git add .`.
11. Give the teammate responsible for integration the branch name, commit ID, build instructions, model-asset instructions, and phone-test evidence. They review the changes before merging. If the candidate failed, keep its log and do the **next listed candidate in a new branch/worktree from the same `BASE_SHA`**. Do not turn a failed experiment into the shared app branch.
12. After a passing branch is reviewed and integrated, the integration teammate provides its **new working commit ID**. Use that as the next `BASE_SHA` so later candidates include previously accepted languages. The browser agent may keep supplying source evidence, but only a phone-tested terminal result can mark a language DONE. Show the precise branch and evidence to the integration teammate before pushing, merging, or replacing an existing build.

## 1. Confirmed baseline and win

**Only English and Hindi TTS are confirmed working.** Preserve both. The other eight required languages—Bengali, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia—are implementation tasks. An earlier report that all ten worked is withdrawn for this handoff. Neither a language selector nor a model file proves that the corresponding TTS path works.

The author cannot inspect the Android repository or current APK. The terminal agent must establish the actual current TTS engine, helper/interface, asset format, packaging, and build before editing. Earlier builds used sherpa-onnx and Piper for English/Hindi; treat that as a lead, not an assertion about today's branch.

**Win:** all eight missing languages synthesize understandable speech in their own script, on the receiving Android phone, without usable Internet at runtime, while English and Hindi still pass their baseline checks. The result must be reproducible from documented sources/asset hashes. If fewer than eight pass, report exactly which ones pass and why the others do not.

## 2. Approved candidate families and exact queue

Use original model owners and official runtime documentation. No iTantra-named third-party model packs, random mirrors, community conversions, newly trained voices, or unrelated TTS model searches in this task. A model family is a **candidate**, not an assertion that it already runs inside Android.

| Language | Candidate 1 | Candidate 2 | Conditional last candidate |
|---|---|---|---|
| Bengali | AI4Bharat `vits_rasa_13`, `BEN_F`/`BEN_M` | AI4Bharat Indic-TTS `bn` FastPitch + HiFi-GAN | Meta MMS `facebook/mms-tts-ben` |
| Gujarati | AI4Bharat Indic-TTS `gu` FastPitch + HiFi-GAN | — | Meta MMS `facebook/mms-tts-guj` |
| Marathi | AI4Bharat `vits_rasa_13`, `MAR_F`/`MAR_M` | AI4Bharat Indic-TTS `mr` | Meta MMS `facebook/mms-tts-mar` |
| Kannada | AI4Bharat `vits_rasa_13`, `KAN_F`/`KAN_M` | AI4Bharat Indic-TTS `kn` | Meta MMS `facebook/mms-tts-kan` |
| Malayalam | Official Piper `ml_IN/arjun`, then `ml_IN/meera` | AI4Bharat `vits_rasa_13`, `MAL_F` | Meta MMS `facebook/mms-tts-mal` |
| Tamil | AI4Bharat `vits_rasa_13`, `TAM_F` | AI4Bharat Indic-TTS `ta` | Meta MMS `facebook/mms-tts-tam` |
| Telugu | AI4Bharat `vits_rasa_13`, `TEL_F` | AI4Bharat Indic-TTS `te` | Meta MMS `facebook/mms-tts-tel` |
| Odia | AI4Bharat Indic-TTS `or` FastPitch + HiFi-GAN | — | Meta MMS `facebook/mms-tts-ory` |

**Why this order:** AI4Bharat Rasa is a shared ~40.2-million-parameter VITS model that lists the six languages shown above, but its weights require access and its custom Python implementation has **no verified export into iTantra's Android runtime**. It does **not** list Gujarati or Odia. Indic-TTS has official checkpoints for all eight, but uses an acoustic model and a separate vocoder; the downloadable archives are about 1.4 GB per language. Test *whether a viable trimmed mobile package exists* before planning to ship them. Piper publishes Malayalam voices with an already established ONNX deployment path. The official Meta MMS models cover all eight, and sherpa-onnx documents a conversion route, but the weights are CC BY-NC 4.0. A converted or mirrored copy does not change those terms.

**MMS decision gate:** Before implementing any MMS fallback, give the human teammate its original model card and non-commercial license. The team decides whether that license fits the intended demo and distribution. If it does not, mark MMS unavailable and report the remaining blocker; do not relabel those weights with another repository's license. Do not treat this decision as permission to make claims about future unrestricted redistribution.

**Not in this queue:** IndicF5 (~0.4B parameters, reference audio/text) and Indic Parler-TTS (~0.9B parameters) support Indic languages but add substantial size/integration work. Do not pivot to them under this handoff. Report a blocker if the bounded queue fails.

## 3. Track A — browser agent instructions

The browser agent can complete this track **without a terminal, repo, APK, or phone**. Its job is to produce an implementation-ready evidence packet, not to say a model works in iTantra.

1. For **each candidate in the queue**, open the original owner page and record the exact model identifier, supported language/script, weight availability and access requirements, model architecture, files and reported size, code license **and weight license separately**, required text normalization/phonemization, and published inference command. Copy direct official URLs and retrieval date. If a fact is not documented, mark UNKNOWN; do not infer it from a community mirror.
2. For each candidate, check the **official sherpa-onnx or model-owner documentation** for a compatible Android or ONNX inference path. Distinguish `ready official package`, `documented conversion`, `conversion needs research`, and `no identified phone path`. This classification is evidence from docs, not a benchmark result.
3. Apply an early feasibility gate to Indic-TTS: inspect release archive sizes and the acoustic/vocoder files required for one language. If a credible phone-sized package cannot be established from the official materials, mark `SIZE/PORT UNCERTAIN` and put the exact missing measurement into the terminal handoff. Do not recommend downloading eight 1.4 GB archives by default.
4. Produce **one row per language**, in the queue order: original source links → license/access → Android path → known obstacle → exact first test command or official procedure → next candidate if it fails. Provide a separate MMS licensing decision card. State clearly: `NO AUDIO OR PHONE TESTS PERFORMED` unless such tests actually occurred.
5. Stop when every candidate has enough facts for a terminal go/no-go test, or a named source is inaccessible. No general survey and no unlisted models. Hand the evidence packet to the coding agent. The browser agent may revise a factual error in this list with primary evidence, but must log the change and why.

## 4. Track B — terminal coding agent instructions

The terminal agent can start on the repository while browser research is under way. It **must not** pretend it has a model's license or weights until the evidence packet or original source confirms them.

1. Make a clean worktree/branch from a known-good commit. Record SHA, Git status, build command, ABI/min SDK, TTS class/interface, English/Hindi model paths, baseline APK, the two-language smoke test, and the app's storage/download mechanism. Do not overwrite anyone's dirty worktree. If the current build is broken, find the latest known-good commit and report the delta before model changes.
2. Create a tiny *isolated* TTS test path that passes a Unicode string and explicit language code to the existing engine and saves a WAV plus generation timing. Avoid a new framework or UI rewrite. Check that native-script text reaches the engine intact and that only text, never generated audio, is sent over the existing link.
3. For one language at a time, take **only the next candidate in section 2**. Confirm its original license/access and required files. Run desktop inference on the frozen test inputs in section 5. If desktop fails, record why and advance. If it passes, establish an ONNX/Android inference path in an isolated harness; only then wire it through the existing app's TTS interface. A gated Python demo is not an Android implementation.
4. On the target phone, run offline playback from **received text** using the full app path. Benchmark and score as in section 5. Re-run English and Hindi after each language. Commit a passing language with model source/revision, checksum and required attribution. Keep large weights outside Git unless the repo already has an agreed binary-asset policy.
5. If a candidate fails, keep the branch/log and move to the next candidate. If it passes every gate, mark that language DONE and **stop searching** for a prettier voice. If the permitted queue is exhausted, report the exact blocker and best partial result; do not train, port a new inference framework, or silently substitute Android cloud TTS.

## 5. Same test for every model

Before testing, the human teammate and a speaker of each language approve a frozen UTF-8 test file with **ten correct native-script messages**: two short alerts, two locations/directions, two help requests, two ordinary sentences, one number, and one name/place. Three messages must contain actionable details. A browser agent may prepare draft text, but cannot mark the file approved on behalf of a speaker. Use the same file for every candidate; no cherry-picked demos.

**Desktop:** Synthesize all ten. Log model/files, exact command, errors, elapsed generation time, audio duration, and ten WAVs. A listener who has not seen the text writes the meaning and any numbers/locations/actions heard. `PASS TO PHONE` requires at least 9/10 messages to retain the intended meaning and all three actionable messages to preserve their critical details. A model that cannot reach this score stops here. No listener means `QUALITY UNVERIFIED`, not PASS.

**Android:** On the actual demonstration handset, disable usable Internet while leaving the local radio needed by iTantra enabled. Repeat the ten incoming-text messages twice, then one representative message twenty times. Log model load time, received-text-to-audible-start, synthesis time, audio duration, `RTF = synthesis time / audio duration`, crashes, model asset bytes and installed app bytes. Draft engineering pass gates: ≥9/10 intelligible; all three actionable details preserved; zero failures in twenty repeats; median warm RTF ≤1.0; median text-to-audible-start ≤3 seconds. These numeric gates are **review settings**: the team can change them *before tests start*, then freeze them. Do not lower a gate to rescue a tested model. No storage cap is invented without the actual APK/model baseline; record exact bytes and ask the team to fix the budget before accepting a large model.

Each candidate record must say `PASS / FAIL / UNVERIFIED`, why, and the *next action*. Do not equate “a WAV file exists” with successful TTS.

## 6. Git, time and failure controls

- Use one branch/worktree per candidate: `tts/<language>/<model>`. Keep Hindi/English intact, avoid unrelated changes, and never force-push over another teammate. Commit code/config and attribution; store large downloaded weights separately with SHA-256 and reproducible fetch instructions.
- **45-minute desktop setup timebox** after weights are available; **90-minute export/Android-harness proof timebox** for a model that needs conversion. At the limit, record the precise blocker and proceed to the next listed candidate. These are effort caps, not latency requirements.
- A license mismatch, unavailable official weights, desktop unintelligibility, unsupported script/front-end, infeasible phone asset size, or missing Android inference proof is a failed/blocked candidate. No open-ended repair effort after the timebox.
- Each passing language must preserve app build, its own offline phone test, and Hindi/English regression. If all candidates fail, return a measured partial result, not a success claim.

## 7. Final handoff between agents and back to the team

**Browser agent output:** a source/evidence table, direct owner links, license card, explicit uncertainty and recommended first experiment for each language. **Never:** `integrated`, `intelligible`, or `works offline` without a real test.

**Terminal agent output:** for all ten languages, `current/winning model | desktop result | phone offline result | intelligibility | RTF and start latency | model/app bytes | branch/commit | blocker`. Include commands, raw logs, WAVs where sharing is appropriate, asset hashes, and build instructions. Separate these claims: `language selectable`, `audio produced`, `listener understood`, `phone gate passed`.

## Original sources to consult

- AI4Bharat VITS Rasa model card (languages, speakers, access/license): https://huggingface.co/ai4bharat/vits_rasa_13
- AI4Bharat Indic-TTS code and official checkpoints: https://github.com/AI4Bharat/Indic-TTS and https://github.com/AI4Bharat/Indic-TTS/releases/tag/v1-checkpoints-release
- Piper official voice list, Malayalam models and per-voice metadata: https://github.com/rhasspy/piper/blob/master/VOICES.md
- Meta original MMS model collection/licensing: https://huggingface.co/facebook/mms-tts
- sherpa-onnx official MMS-to-ONNX conversion and TTS catalogue: https://k2-fsa.github.io/sherpa/onnx/tts/mms.html and https://k2-fsa.github.io/sherpa/onnx/tts/all/
- Excluded IndicF5 size/requirements: https://huggingface.co/ai4bharat/IndicF5
