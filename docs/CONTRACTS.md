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
fun onMessageReceived(callback: (text: String, languageCode: String) -> Unit)
```

Until the real lanes exist, fake implementations are allowed and expected.

## Resolved: onMessageReceived carries a language code

The Lane 1 prose says the app accepts "a string, plus a language code", but the original example `onMessageReceived` callback in the lane specification showed only a `String`. That mismatch is now resolved:

`onMessageReceived` takes `(text: String, languageCode: String)`, matching `speak`'s signature. The app preserves the sender's language rather than translating, so the language code travelling with a received message has to reach `speak` unchanged.

**Language code format:** ISO 639-1 two-letter codes, covering all ten target languages:

| Code | Language |
|---|---|
| `en` | English |
| `hi` | Hindi |
| `gu` | Gujarati |
| `mr` | Marathi |
| `kn` | Kannada |
| `ml` | Malayalam |
| `ta` | Tamil |
| `te` | Telugu |
| `or` | Odia |
| `bn` | Bengali |

Lane 2/3 must use these exact codes rather than inventing their own labels.

For real cross-lane integration:

- keep this resolved signature for fake plumbing and for the real integration;
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
