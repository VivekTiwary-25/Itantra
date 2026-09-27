# TTS_LOG — Track B

## 2026-09-25 — baseline

- Handoff read in full. `BASE_SHA`: `0e63c4581f4a1fa465ed64e17ef5ff4067e16edc`.
- Worktree: `D:\iTantra-tts-ml`, branch `tts/ml/piper`. At this run's first inspection, both this pre-existing `TTS_LOG.md` and the handoff file were untracked. Neither was staged.
- SDK: local `local.properties` points to `%LOCALAPPDATA%\Android\Sdk`; Git ignores it.
- Existing engine: sherpa-onnx 1.13.7 via `TtsHelper`, Piper/VITS voices English Ryan medium and Hindi Pratham medium. Model assets install from APK assets into app files on first use. `SpeechEngine.speak(text, languageCode)` is the integration interface. ABI arm64-v8a; minSdk 26.
- Untouched `BASE_SHA` baseline: plain `.\gradlew.bat assembleDebug` and `.\gradlew.bat test` both failed before compilation with `java.io.IOException: Unable to establish loopback connection`; stacktrace ended in JDK `PipeImpl`/Unix domain socket `Invalid argument: connect`. With process-local `JAVA_HOME` set to the cached JDK 25 and `TEMP`/`TMP` set to `D:\iTantra-tts-ml\.gradle\tmp`, `assembleDebug` passed in 4m 13s and `test` passed in 26s. APK: `app/build/outputs/apk/debug/app-debug.apk`, 434,387,066 bytes. No source changes preceded these runs.
- `git fetch origin` succeeded without merge. No phone is connected, so English/Hindi phone regression is **PENDING PHONE**.

## Candidate: Bengali Rasa (`tts/bn/rasa`)

- Worktree `D:\iTantra-tts-bn-rasa`, created from `BASE_SHA`. Original model card: https://huggingface.co/ai4bharat/vits_rasa_13 (retrieved 2026-09-25), CC BY 4.0. Weight access requires login and acceptance of contact-sharing conditions. Unauthenticated `model.safetensors` HEAD returned HTTP 401. **ACCESS BLOCK**; no inference, WAV, or Android work. Its custom VITS frontend/export remains unverified.

## Candidate: Bengali Indic-TTS (`tts/bn/indic-tts`)

- Worktree `D:\iTantra-tts-bn-indic`, created from `BASE_SHA`. Official code: https://github.com/AI4Bharat/Indic-TTS (MIT code license). Official release: https://github.com/AI4Bharat/Indic-TTS/releases/tag/v1-checkpoints-release. A distinct weight license was not found in the release/archive: **UNKNOWN, review before distribution**.
- `bn.zip` downloaded from the official release, 1,512,955,815 bytes, SHA-256 `4250053d69a15c75ad3e48a1e2d2ef025f75a5b22e9fde2e0a45225b6899fb6f`. Unpacked FastPitch checkpoint 637,449,049 bytes and HiFi-GAN checkpoint 1,016,383,548 bytes, plus configs/speaker IDs. Weight files remain outside Git at `D:\iTantra-tts-models\bn`.
- Desktop setup: Windows Coqui TTS 0.22.0 build failed for absent MSVC. WSL Ubuntu Python 3.10.21 with CPU torch 2.2.2, torchaudio 2.2.2 and TTS 0.22.0 ran the official FastPitch/HiFi-GAN path. The release config's stale relative speaker file path was corrected only in an external `config-local.json`; original config stays intact. `female` speaker ID is 1.
- Ten draft Bengali texts in `D:\iTantra-tts-bn-indic\tts-research\inputs\bn.txt` produced `01.wav`–`10.wav` under `D:\iTantra-tts-bn-indic\local-recordings\bn-indic`. `timings.json` and `run.log` retain per-item generation/audio durations and the exact inputs. **QUALITY UNVERIFIED — listener needed**; text drafts have no speaker approval.
- ONNX proof: acoustic and vocoder exported to `local-recordings/bn-indic/onnx`. The acoustic graph runs only the traced 44-token example; a 37-token second input fails ONNX Runtime at `/encoder/encoder/fft_layers.0/self_attn/Reshape_4` (`requested shape {44,1,512}`). The vocoder graph runs both 100 and 120 frame inputs. `export.log` has exact results. **Missing dynamic acoustic/Android inference proof** is a real candidate blocker; do not mark this phone ready. No app source was wired.
- MMS fallback is held at the handoff's licensing decision gate; Meta's original model card lists CC BY-NC 4.0. Team decision requested asynchronously. Bengali phone work remains **PENDING PHONE**.

## Candidates: Gujarati, Marathi, Kannada

- `tts/gu/indic-tts` (`D:\iTantra-tts-gu-indic`): official `gu.zip` SHA-256 `7cb5bfa35e14f93469c3f56afa9225d0494615a961868b2966ac4f04f8f7e8d0`; ten WAVs, inputs and timings at `local-recordings/gu-indic`. Acoustic ONNX fails on second text length (52 traced vs 40 actual); vocoder ONNX runs 100/120 frames. **QUALITY UNVERIFIED — listener needed; PENDING PHONE.** Weight license remains UNKNOWN; no app wiring.
- `tts/mr/rasa` (`D:\iTantra-tts-mr-rasa`) and `tts/kn/rasa` (`D:\iTantra-tts-kn-rasa`): same official Rasa shared-weight access gate (HTTP 401 without login/conditions); no WAV. **PENDING PHONE** only if access and later gates clear.
- `tts/mr/indic-tts` (`D:\iTantra-tts-mr-indic`): official `mr.zip` SHA-256 `d4457b2b9d2604396ab214d6ef8b3d4689f3bfd1312814ca295f44af5def90a0`; ten WAVs, inputs and timings at `local-recordings/mr-indic`. Acoustic ONNX fails on second text length (47 traced vs 37 actual); vocoder ONNX runs 100/120 frames. **QUALITY UNVERIFIED — listener needed; PENDING PHONE.** Weight license UNKNOWN; no app wiring.
- `tts/kn/indic-tts` (`D:\iTantra-tts-kn-indic`): official `kn.zip` SHA-256 `1ba360afa417f4346ddb8573ac74032c93dde83f4a5b90b6ef0d7c7976268216`; ten WAVs, inputs and timings at `local-recordings/kn-indic`. Acoustic ONNX fails on second text length (52 traced vs 39 actual); vocoder ONNX runs 100/120 frames. **QUALITY UNVERIFIED — listener needed; PENDING PHONE.** Weight license UNKNOWN; no app wiring.
- Each Indic-TTS archive has a FastPitch checkpoint near 637 MB and HiFi-GAN near 1,016 MB unpacked. The exact per-candidate timings and raw ONNX errors are kept beside the WAVs. The existing sherpa-onnx `TtsHelper` config accepts VITS models, not this two-stage FastPitch/HiFi-GAN stack; no phone package has been proven. I did not start any MMS fallback merely because listener review is absent.

## Candidate: Malayalam Piper Arjun (`tts/ml/piper`)

- Official Piper voice list: https://github.com/rhasspy/piper/blob/master/VOICES.md. Original voice/model card: https://huggingface.co/rhasspy/piper-voices/blob/main/ml/ml_IN/arjun/medium/MODEL_CARD. The voice repository is MIT; its card points to a training dataset with license described at the linked dataset page, which still needs attribution review.
- Official sherpa-onnx release asset `vits-piper-ml_IN-arjun-medium.tar.bz2` downloaded, SHA-256 `3058d098e8b1ffcdd6069e96b1d492f319333235912a627c309c7c54cea59acf`, with 62,946,436-byte ONNX model, `tokens.txt`, and `espeak-ng-data`. Files remain outside Git at `D:\iTantra-tts-models\vits-piper-ml_IN-arjun-medium`.
- `tools/tts_desktop_probe.py` uses sherpa-onnx 1.13.7, matching the Android AAR, and takes explicit `--language-code ml`, ten UTF-8 strings, model paths, and output directory. Ten draft Malayalam inputs at `tts-research/inputs/ml.txt` yielded `local-recordings/ml-arjun/01.wav`–`10.wav`; `timings.json` records model load, per-item generation and audio durations. The first attempt generated one WAV but failed while printing Unicode to a CP1252 console; the probe now ASCII-escapes console JSON and a complete rerun succeeded. **QUALITY UNVERIFIED — listener needed; PENDING PHONE.** No Meera search or app wiring was attempted because Arjun produced audio and has no conversion blocker.

## Candidates: Tamil, Telugu, Odia

- `tts/ta/rasa` (`D:\iTantra-tts-ta-rasa`) and `tts/te/rasa` (`D:\iTantra-tts-te-rasa`): same shared Rasa weight gate, HTTP 401 without login/conditions. No WAV or phone test.
- `tts/ta/indic-tts` (`D:\iTantra-tts-ta-indic`): official `ta.zip` SHA-256 `9c443f2304df959db3e8ae308a6380cefb24084901551a9e77188a992cf578e5`; ten WAVs/timings at `local-recordings/ta-indic`. The published frontend discarded Tamil `௧` and `௨` from draft message 9, so its actionable number was not passed through. **UNSUPPORTED INPUT BLOCKER; QUALITY UNVERIFIED; PENDING PHONE.** Stopped before ONNX work under the source handoff's unsupported-script rule. Weight license UNKNOWN.
- `tts/te/indic-tts` (`D:\iTantra-tts-te-indic`): official `te.zip` SHA-256 `c568b55c5e3317c18fc942f46703ff0664beef75c23fcfa059a8396574b0cf23`; ten WAVs/timings at `local-recordings/te-indic`. The frontend discarded Telugu `౧`, `౨` from draft message 9 and U+200C from message 10. **UNSUPPORTED INPUT BLOCKER; QUALITY UNVERIFIED; PENDING PHONE.** Stopped before ONNX work. Weight license UNKNOWN.
- `tts/or/indic-tts` (`D:\iTantra-tts-or-indic`): official `or.zip` SHA-256 `eff767d91415fde8b9c24a01aa37076c56d468a97efca42e5c9dc78f44c9500b`; ten WAVs/timings at `local-recordings/or-indic`. The frontend discarded Odia nukta `଼` and digits `୧`, `୨`, including the actionable number in message 9. **UNSUPPORTED INPUT BLOCKER; QUALITY UNVERIFIED; PENDING PHONE.** Stopped before ONNX work. Weight license UNKNOWN.
- All three fallbacks are Meta MMS and remain at the CC BY-NC 4.0 decision gate. No MMS branch, weight download, conversion or inference was started without that decision. I did not edit the fixed draft text to hide unsupported characters; it requires speaker approval before any quality scoring.

## Output audit and baseline desktop smoke

- `tools/collect_tts_results.py` validated 80 retained candidate WAVs as non-silent mono 22,050 Hz 16-bit PCM with durations matching their timing records. It wrote `DESKTOP_RESULTS.csv` with each message, generation time, audio duration, absolute WAV path and SHA-256. A valid waveform does **not** establish intelligibility or phone success.
- Isolated sherpa-onnx 1.13.7 desktop smoke synthesized one English Ryan WAV (`local-recordings/baseline-en/01.wav`, generation 0.165 s, audio 1.672 s) and one Hindi Pratham WAV (`local-recordings/baseline-hi/01.wav`, generation 0.178 s, audio 1.905 s) from the already downloaded original app assets. This is not the deferred English/Hindi phone regression.
- `TransportFrameCodec` encodes only message text as UTF-8 plus language metadata, and receive-side `MainActivity` invokes `SpeechEngine.speak(text, languageCode)` after storing and ACKing. No generated audio was put on Transport.

## Final RUN 1 verification

- `python -m py_compile tools\tts_desktop_probe.py tools\collect_tts_results.py` and WSL `python -m py_compile` for the Indic probe/export scripts passed.
- `python tools\collect_tts_results.py` passed: 80 validated candidate WAVs and `DESKTOP_RESULTS.csv` written.
- `DESKTOP_COMMANDS.md` records the exact desktop invocation for each tested candidate; `OG.md` records the byte-frozen draft-input hashes and per-language gates.
- With process-local cached JDK 25 and short ignored `TEMP`/`TMP`, `.\gradlew.bat assembleDebug` passed again (`BUILD SUCCESSFUL`, 36 tasks up-to-date) and `.\gradlew.bat test` passed again (`BUILD SUCCESSFUL`, 24 tasks up-to-date). Android source remains unchanged.
- Each attempted candidate worktree is still at `0e63c45`; `git diff --name-only` found **zero tracked-file changes** in all 13 candidate worktrees. No add, commit, push, merge, reset or branch deletion occurred. The untracked handoff file was never staged.
- All Android section 5 measurements, offline receive/playback, RTF/start latency, memory, installed asset bytes, repeat reliability and English/Hindi phone regression are **PENDING PHONE**. `RUN2_CHECKLIST.md` gives the exact follow-up sequence.
