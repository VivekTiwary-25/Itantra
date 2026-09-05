# iTantra Cross-Lane Contracts

Source of truth: `docs/LANE_1_APP_AND_CAPTURE.md`.

This file is a concise implementation boundary, not a replacement for the lane document.

## Capture output: Lane 1 -> Speech

Lane 1 produces one WAV file per utterance.

Required format:

- sample rate: 16,000 Hz
- bit depth: 16-bit
- channels: mono
- encoding: PCM
- container: WAV

Handoff: file path.

Do not silently change this audio format.

## Functions shown by the Lane 1 specification

Speech-side functions:

```kotlin
fun transcribe(wavFilePath: String): String
fun speak(text: String, languageCode: String)
```

Transport-side functions:

```kotlin
fun sendMessage(text: String)
fun onMessageReceived(callback: (String) -> Unit)
```

Until the real lanes exist, fake implementations are allowed and expected.

## Known contract ambiguity — do not silently "fix"

The Lane 1 prose says the app accepts "a string, plus a language code", but the example `onMessageReceived` callback shown in the same specification provides only a `String`.

For early Lane 1 rungs, this does not block work.

For real cross-lane integration:

- do not invent a new packet or callback shape without surfacing this mismatch;
- keep the shown interfaces for fake plumbing unless the integration owner explicitly resolves the language-code contract;
- do not design packet headers/checksums/message IDs from Lane 1.

## Ownership boundaries

Lane 1:
- screen and user interaction
- microphone permission/capture
- WAV creation
- playback/UI
- message list
- typed fallback
- mode UI
- alert UI behavior when explicitly scheduled

Speech:
- ASR/STT implementation
- TTS implementation
- model/runtime details
- language/model handling
- VAD/hands-free speech segmentation logic

Transport:
- Bluetooth/Wi-Fi transport implementation
- actual byte/message movement
- transport-specific connection behavior

Do not cross these boundaries merely because another lane is unfinished.
