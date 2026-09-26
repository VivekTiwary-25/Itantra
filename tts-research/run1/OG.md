# Track B operational guide — RUN 1

**Date:** 2026-09-25. **Base:** `0e63c4581f4a1fa465ed64e17ef5ff4067e16edc` (`BASE_SHA`). **Phone:** none connected. This is desktop evidence, not a language acceptance record. No candidate is claimed to work in the Android app.

## Baseline and test path

- Worktree `D:\iTantra-tts-ml` is `tts/ml/piper` at `BASE_SHA`. `local.properties` points to `C:/Users/UTKARSH KESHRI/AppData/Local/Android/Sdk` and is ignored by Git. `git fetch origin` succeeded without merge.
- Untouched baseline commands: `.\gradlew.bat assembleDebug` and `.\gradlew.bat test`. They initially stopped before compilation with a JDK loopback socket error. Setting process-local JDK 25 and `TEMP`/`TMP` to the ignored `D:\iTantra-tts-ml\.gradle\tmp` made both pass. Baseline APK: `D:\iTantra-tts-ml\app\build\outputs\apk\debug\app-debug.apk`, 434,387,066 bytes, SHA-256 `d3a9505e07d5df322eea51f5965c4a674b780aee475b62b38167630b4765dd20`. The exact commands and error are in [TTS_LOG.md](TTS_LOG.md).
- App TTS contract: `SpeechEngine.speak(text: String, languageCode: String)` calls `TtsHelper.speak`. `TtsHelper` uses the checked-in sherpa-onnx 1.13.7 AAR with Piper/VITS. English is `app/src/main/assets/vits-piper-en_US-ryan-medium/en_US-ryan-medium.onnx`; Hindi is `app/src/main/assets/vits-piper-hi_IN-pratham-medium/hi_IN-pratham-medium.onnx`. `setup-models.ps1` provisions ignored ONNX files into APK assets. On first use `TtsHelper` copies each voice from assets to app-private `filesDir`, then loads it. No runtime download is present. `minSdk=26`, `arm64-v8a` ABI only.
- Desktop probe `tools/tts_desktop_probe.py` accepts Unicode text or ten-line UTF-8 input, explicit language code, model paths and output directory; it writes WAV plus generation/audio timing. The Indic-TTS branch probe uses the official FastPitch/HiFi-GAN desktop stack. `DESKTOP_RESULTS.csv` holds **80 message rows** with generation time, audio duration, absolute WAV location and hash. `tools/collect_tts_results.py` validated PCM headers, duration and non-silent samples. All eight ten-line files are **drafts**, not speaker-approved frozen tests.
- English/Hindi desktop smoke made one WAV each from the existing assets. English/Hindi **phone regression is PENDING PHONE**.

The tested drafts are byte-frozen for comparing these RUN 1 outputs. They still require speaker approval, which may create a new approved version for RUN 2.

| Language | Tested draft UTF-8 SHA-256 |
|---|---|
| `bn` | `a1c76249551773f7ab60458074874fe7e52fbb035f07fdef9a1cc516acf670ba` |
| `gu` | `50bf7948fa5bbe4c6f40fc08588963917a9458256e5d24dd362aa5d264ecfc36` |
| `mr` | `47c4e1babef075a2eb3527d63285bdc8619a72279698a7a7223afb952bc26f74` |
| `kn` | `c0e751bb8577be992410518015dcba3bc86e4997f45a4579c68fb1e4fcddce82` |
| `ml` | `be20441b189794f8f33baf0d11935739623aca823739aa2caf2f2819242afdea` |
| `ta` | `d715279212c0b121c2a709907c1efbb00498010b0112f18d481626c3c518b0a4` |
| `te` | `7897c87e391a095d05d31f2b77ef168acd6bc44c04634c65158db1b5b1a475bd` |
| `or` | `268793dce1b0805399863bff4e9ef7b7e7b64f527069525593365a2bfd40b81a` |

## Source and decision gates

- [AI4Bharat Rasa](https://huggingface.co/ai4bharat/vits_rasa_13) lists Bengali, Marathi, Kannada, Malayalam, Tamil and Telugu, CC BY 4.0, and requires login plus conditions before weights. The unauthenticated weight request returned HTTP 401. No Rasa weights were used.
- [AI4Bharat Indic-TTS](https://github.com/AI4Bharat/Indic-TTS) uses FastPitch plus HiFi-GAN and publishes the eight official archives in [its release](https://github.com/AI4Bharat/Indic-TTS/releases/tag/v1-checkpoints-release). Its [code license](https://github.com/AI4Bharat/Indic-TTS/blob/master/LICENSE.txt) is MIT; the separate checkpoint license is not stated in the downloaded archives, so redistribution status is UNKNOWN. Each unpacked checkpoint pair is about 1.65 GB. The seven downloaded Indic archives and SHA-256 values are in `TTS_LOG.md`.
- [Piper's official voice list](https://github.com/rhasspy/piper/blob/master/VOICES.md) and [Arjun model card](https://huggingface.co/rhasspy/piper-voices/blob/main/ml/ml_IN/arjun/medium/MODEL_CARD) identify the Malayalam voice. The [Piper voice repository](https://huggingface.co/rhasspy/piper-voices) shows MIT; the card refers to a dataset whose separate terms still need review. The official sherpa-onnx Piper package supplies an ONNX model, tokens and eSpeak data; no conversion is needed for the desktop probe.
- [Meta MMS's original card](https://huggingface.co/facebook/mms-tts) states CC BY-NC 4.0. [sherpa-onnx's official conversion guide](https://k2-fsa.github.io/sherpa/onnx/tts/mms.html) documents an ONNX route. The team license decision was requested; **no MMS implementation, weights or inference** proceeded while it is pending.
- The 45-minute desktop setup and 90-minute conversion timeboxes were not exhausted. Candidate progress stopped at actual access, frontend or ONNX proof failures, or at the listener hold for Arjun.

## English (`en`) baseline

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Existing Piper Ryan medium at `BASE_SHA` | One smoke WAV; new audio QUALITY UNVERIFIED | `D:\iTantra-tts-ml\local-recordings\baseline-en\01.wav` | No RUN 1 phone available; historical phone evidence is preserved | PENDING PHONE | NEEDS LISTENER for new WAV |

## Hindi (`hi`) baseline

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Existing Piper Pratham medium at `BASE_SHA` | One smoke WAV; new audio QUALITY UNVERIFIED | `D:\iTantra-tts-ml\local-recordings\baseline-hi\01.wav` | No RUN 1 phone available; historical phone evidence is preserved | PENDING PHONE | NEEDS LISTENER for new WAV |

## Bengali (`bn`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Rasa `BEN_F/BEN_M` (`tts/bn/rasa`) | No inference | None | Weights gated; HTTP 401 | PENDING PHONE | No WAV |
| Indic-TTS (`tts/bn/indic-tts`) | 10 WAVs; QUALITY UNVERIFIED | `D:\iTantra-tts-bn-indic\local-recordings\bn-indic` | Acoustic ONNX fails second text length; weight license UNKNOWN | PENDING PHONE | NEEDS LISTENER |
| MMS `ben` | Not attempted | None | CC BY-NC 4.0 decision pending | PENDING PHONE | No WAV |

## Gujarati (`gu`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Indic-TTS (`tts/gu/indic-tts`) | 10 WAVs; QUALITY UNVERIFIED | `D:\iTantra-tts-gu-indic\local-recordings\gu-indic` | Acoustic ONNX fails second text length; weight license UNKNOWN | PENDING PHONE | NEEDS LISTENER |
| MMS `guj` | Not attempted | None | CC BY-NC 4.0 decision pending | PENDING PHONE | No WAV |

## Marathi (`mr`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Rasa `MAR_F/MAR_M` (`tts/mr/rasa`) | No inference | None | Weights gated; HTTP 401 | PENDING PHONE | No WAV |
| Indic-TTS (`tts/mr/indic-tts`) | 10 WAVs; QUALITY UNVERIFIED | `D:\iTantra-tts-mr-indic\local-recordings\mr-indic` | Acoustic ONNX fails second text length; weight license UNKNOWN | PENDING PHONE | NEEDS LISTENER |
| MMS `mar` | Not attempted | None | CC BY-NC 4.0 decision pending | PENDING PHONE | No WAV |

## Kannada (`kn`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Rasa `KAN_F/KAN_M` (`tts/kn/rasa`) | No inference | None | Weights gated; HTTP 401 | PENDING PHONE | No WAV |
| Indic-TTS (`tts/kn/indic-tts`) | 10 WAVs; QUALITY UNVERIFIED | `D:\iTantra-tts-kn-indic\local-recordings\kn-indic` | Acoustic ONNX fails second text length; weight license UNKNOWN | PENDING PHONE | NEEDS LISTENER |
| MMS `kan` | Not attempted | None | CC BY-NC 4.0 decision pending | PENDING PHONE | No WAV |

## Malayalam (`ml`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Piper Arjun (`tts/ml/piper`) | 10 WAVs; QUALITY UNVERIFIED; official ONNX package runs in sherpa-onnx 1.13.7 desktop probe | `D:\iTantra-tts-ml\local-recordings\ml-arjun` | Speaker approval and blind listener result pending; no app candidate wired | PENDING PHONE | NEEDS LISTENER |
| Piper Meera, then Rasa, then MMS | Not attempted | None | Hold: Arjun produced audio; no real failure justified advancing | PENDING PHONE | No WAV |

## Tamil (`ta`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Rasa `TAM_F` (`tts/ta/rasa`) | No inference | None | Weights gated; HTTP 401 | PENDING PHONE | No WAV |
| Indic-TTS (`tts/ta/indic-tts`) | 10 WAV files; QUALITY UNVERIFIED | `D:\iTantra-tts-ta-indic\local-recordings\ta-indic` | Frontend discarded native digits `௧`, `௨` in actionable message 9; stopped before export | PENDING PHONE | NEEDS LISTENER |
| MMS `tam` | Not attempted | None | CC BY-NC 4.0 decision pending | PENDING PHONE | No WAV |

## Telugu (`te`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Rasa `TEL_F` (`tts/te/rasa`) | No inference | None | Weights gated; HTTP 401 | PENDING PHONE | No WAV |
| Indic-TTS (`tts/te/indic-tts`) | 10 WAV files; QUALITY UNVERIFIED | `D:\iTantra-tts-te-indic\local-recordings\te-indic` | Frontend discarded native digits `౧`, `౨` and U+200C; stopped before export | PENDING PHONE | NEEDS LISTENER |
| MMS `tel` | Not attempted | None | CC BY-NC 4.0 decision pending | PENDING PHONE | No WAV |

## Odia (`or`)

| Candidate | Desktop result | WAV location | Blocker | Phone | Listener |
|---|---|---|---|---|---|
| Indic-TTS (`tts/or/indic-tts`) | 10 WAV files; QUALITY UNVERIFIED | `D:\iTantra-tts-or-indic\local-recordings\or-indic` | Frontend discarded Odia nukta and native digits `୧`, `୨`; stopped before export | PENDING PHONE | NEEDS LISTENER |
| MMS `ory` | Not attempted | None | CC BY-NC 4.0 decision pending | PENDING PHONE | No WAV |

## What the evidence means

`DESKTOP_RESULTS.csv` reports file generation and duration only. No speaker has approved the test sentences, no blind listener has transcribed the audio, no candidate is integrated into the receiver app, and no candidate has an offline Android result. The Indic-TTS acoustic ONNX graph ran only its traced length in four tested languages; the exported vocoder graphs handled two frame lengths. That is insufficient for a general Android speech path. English and Hindi remain the historically phone-verified baseline at `BASE_SHA`; RUN 1 did not repeat their phone tests.
