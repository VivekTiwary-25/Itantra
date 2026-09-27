# Malayalam (ml) Piper Arjun — Android technical validation

Date: 2026-09-26. Branch `codex/tts-research-handoff`, base 919b361. Device RMX3392, Android 14, arm64-v8a.

**QUALITY UNVERIFIED — no one has listened to this audio. Not GREEN. Only Vivek marks GREEN.**

## Model, source, license
- `vits-piper-ml_IN-arjun-medium`, official sherpa-onnx release asset
  https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ml_IN-arjun-medium.tar.bz2
  (67,222,458 B, SHA-256 `3058d098e8b1ffcdd6069e96b1d492f319333235912a627c309c7c54cea59acf`, matches `manifests/external-assets.json`).
- `ml_IN-arjun-medium.onnx` 62,946,436 B, SHA-256 `33c97f81a1d326e0c524e321940dacf3ac1b48b6b5c486a6afa8bff245695cf7` (git-ignored; restored by `setup-models.ps1`).
- Committed support files (byte-exact, `-text`): `tokens.txt`, `ml_IN-arjun-medium.onnx.json`, `MODEL_CARD`, `espeak-ng-data/` (355 files total in dir incl. these).
- Voice repository rhasspy/piper-voices is MIT. MODEL_CARD: single speaker, medium, 22,050 Hz, fine-tuned from the US-English lessac voice.
  Training dataset: https://www.kaggle.com/code/mpwolke/indic-tts-malayalam-speech-corpus, license "See URL".
  **Attribution/dataset-license review is still open** (dataset terms were not independently confirmed here). MODEL_CARD ships in the APK assets as the attribution record.

## Change
`TtsHelper` voice map gains `"ml"` (same sherpa-onnx VITS path, no new architecture). `setup-models.ps1` provisions the ONNX with pinned hashes
(verified end to end, including a real re-download). Test-only additions: Malayalam in the temporary Test TTS control's language list only (not the compose/STT list),
and a debug-source-set `DebugTtsReceiver` (adb broadcast, base64 UTF-8 text) that calls the same `TtsHelper.speak`. Transport already accepted `ml`; it still carries text + language code only.

## Sizes
- APK before (baseline, no ml): 434,387,066 B. After: 502,698,082 B. Delta +68,311,016 B (debug build).
- Model dir in app-private storage after first use: ~80 MB each (ml 80,374 KB, en 80,574 KB, hi 80,570 KB `du -sk`).

## Phone results (all measured; raw logs in this folder)
- Model loads locally, 22,050 Hz, on device. Synthesis and AudioTrack playback complete (playback drained) — **audibility by ear not confirmed by me**.
- 10 draft sentences (`inputs/ml.txt`, unapproved drafts) x 2 passes = 20 syntheses, 0 failures, 0 crashes (no FATAL/AndroidRuntime lines).
- Codepoint counts received by TtsHelper equal the file's (62,42,32,46,61,62,39,37,35,44) in both passes: no Unicode/script corruption at the text boundary. (Actual pronunciation correctness is unjudged.)
- Warm RTF (synthesis/audio): median 0.70 (min 0.61, max 0.78). Warm text-to-audible-start: median 2.59 s, max 3.76 s (2 of 20 above 3 s: the two longest lines).
- Cold, first ever (asset copy 3.0 s + model init 5.9 s + synth 4.1 s): 13.1 s to audible start. Cold with assets already installed (process just started): 7.6 s (init 3.95 s).
- Memory after loading ml in a fresh process: PSS 250,972 KB, native heap 158,444 KB, Java heap 10,128 KB (one reading, dumpsys meminfo).
- English regression (Ryan): "this is a test message" and a 54-char sentence, warm RTF 0.63-0.65. Hindi regression (Pratham): 22/28/53-char sentences, warm RTF 0.62-0.71. No errors.
  (First English/Hindi calls of a run include model load; a first Hindi attempt in the driver was invalid because PowerShell mis-decoded the script and was redone via UTF-8 base64.)

## Not done / still unverified
- Listening, native-speaker approval of the input lines and pronunciation; dataset attribution decision.
- Real two-phone receive path (only one phone was connected): Malayalam was driven at `TtsHelper.speak` directly, not through Transport -> Logs -> speak.
- Offline (no Internet) run; 20 repeats of one message through the real path; blind listener gates from RUN2_CHECKLIST.
- Bengali and other languages: not started (out of scope).
