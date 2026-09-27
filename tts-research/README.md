# Continue TTS research on another computer

Read [HANDOFF.md](HANDOFF.md), [AGENTS.md](AGENTS.md), and the
[original task handoff](ORIGINAL_TTS_HANDOFF.md) first.

## Clone

```sh
git clone --branch codex/tts-research-handoff https://github.com/VivekTiwary-25/Itantra.git Itantra-tts
cd Itantra-tts
```

This gives you the preserved Android baseline and the consolidated research.
The old worktree folder layout is not required. Open the repository in your
coding agent and give it this prompt:

> Continue the existing offline TTS research. Read root AGENTS.md, then
> tts-research/AGENTS.md, HANDOFF.md, ORIGINAL_TTS_HANDOFF.md, and the latest
> frontend-repair report in full. Verify the transferred evidence before
> editing. RUN 1 logs are historical; the frontend repair is newer, but no
> new-language audio has listener approval or phone acceptance. Preserve
> English/Hindi and the isolated candidate queue. My next task is: [your task].

## Verify and listen without installing models

From the repository root, with Python 3:

```sh
python tools/verify_tts_handoff.py
```

Use `python3` on systems where appropriate. The verifier requires no model,
Coqui, sherpa-onnx, Android SDK, or external worktree. Open WAVs under
`tts-research/evidence/`. `results/run1.csv` contains 80 candidate rows;
`results/frontend-repair.csv` contains 30 new rows, with relative file paths.
Read the associated draft input and trace when reviewing meaning, numbers,
locations, and actions. Blind listeners should hear the audio before seeing
the text; the source handoff defines how approval is scored.

## Restore selected model files

Use [external-assets.json](manifests/external-assets.json) for archive URLs,
SHA-256, sizes, and extraction layouts. Download the selected official archive
or copy it from the original machine. Keep large archives/checkpoints outside
Git and check their hashes:

```powershell
Get-FileHash -Algorithm SHA256 'D:\tts-models\ta.zip'
```

```sh
sha256sum /path/to/tts-models/ta.zip
```

Indic ZIPs include their language root (for example `ta/fastpitch/` and
`ta/hifigan/`). Extract into an external model-parent directory. Piper Arjun
extracts into `vits-piper-ml_IN-arjun-medium/` and supplies its ONNX, tokens, and
eSpeak support data. The archive checksums are recorded provenance; mismatches
must be investigated, not silently accepted. Respect the remaining source
license/access gates before distribution or candidate promotion.

## Recreate the desktop environment

Indic experiments used Linux via WSL Ubuntu and Python 3.10.21. Windows-native
Coqui installation failed for missing MSVC in RUN 1. Start with a Python 3.10
venv and the recorded direct dependency pins:

```sh
python3.10 -m venv .venv-indic
. .venv-indic/bin/activate
python -m pip install torch==2.2.2 torchaudio==2.2.2 --index-url https://download.pytorch.org/whl/cpu
python -m pip install TTS==0.22.0 onnx==1.17.0 onnxruntime==1.18.1
```

The third-party number library is vendored at `tools/vendor` at 1.1.0, with MIT
license/metadata. A new install may resolve transitive dependencies differently;
record its actual environment and failures rather than claiming the old venv
was transferred. `run1/DESKTOP_COMMANDS.md` is the exact original invocation log.

Prepare the external Indic speaker config (example path adapted to your machine):

```sh
python tools/prepare_indic_config.py --model-dir /path/to/tts-models/ta
```

Then run the original probe with a new output directory:

```sh
python tools/indic_tts_desktop_probe.py --language-code ta --model-dir /path/to/tts-models/ta --input-file tts-research/inputs/ta.txt --output-dir local-recordings/new-ta-run
```

For the repaired frontend:

```sh
python tools/test_indic_frontend_repair.py --language-code ta --config /path/to/tts-models/ta/fastpitch/config-local.json
python tools/indic_tts_repaired_probe.py --language-code ta --model-dir /path/to/tts-models/ta --input-file tts-research/inputs/ta.txt --output-dir local-recordings/new-ta-repaired
```

Use `te` or `or` and the matching assets/input to test the other repaired
languages. Do not overwrite frozen `tts-research/evidence` files. Export probe
`tools/export_indic_onnx.py` remains the original failed dynamic-length proof,
not a production conversion recipe.

For Piper Arjun, a separate environment with `sherpa-onnx==1.13.7` can run:

```sh
python tools/tts_desktop_probe.py --language-code ml --model /path/to/vits-piper-ml_IN-arjun-medium/ml_IN-arjun-medium.onnx --tokens /path/to/vits-piper-ml_IN-arjun-medium/tokens.txt --data-dir /path/to/vits-piper-ml_IN-arjun-medium/espeak-ng-data --input-file tts-research/inputs/ml.txt --output-dir local-recordings/new-ml-run
```

## Resume an isolated candidate

When a specific candidate task is assigned, a new worktree can include the
transferred scripts/evidence while preserving the same unchanged app base:

```sh
git worktree add -b codex/tts/ml-arjun-next ../Itantra-ml-next HEAD
```

Use a fresh name if occupied. Record both the research checkpoint SHA and
`APP_BASE_SHA=0e63c4581f4a1fa465ed64e17ef5ff4067e16edc`. Do not merge the UI/UX
branch or a newer main automatically into the experiment.

## Android build and phone tests

Only after the selected candidate clears its listener/license/runtime gates:
configure the new machine's SDK/JDK, restore baseline model assets using the
root `setup-models.ps1`, and use the normal `gradlew.bat test` and
`gradlew.bat assembleDebug` commands. The original JDK loopback fix was a
process-local JDK 25 plus short ignored TEMP/TMP; apply it only if the same
failure recurs. The original APK is not part of the transfer.

Follow [RUN2_CHECKLIST.md](run1/RUN2_CHECKLIST.md) for real received-text testing.
Do not mark a new language working based on these saved desktop files.
