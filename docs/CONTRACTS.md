# iTantra Cross-Lane Contracts

This file is the source of truth for cross-lane boundaries and interfaces. `docs/LANE_1_APP_AND_CAPTURE.md` remains the source of truth for the Lane 1 ladder and acceptance criteria.

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

Until the real lanes exist, fake implementations are allowed and expected. Preserve these signatures unless a proven integration contradiction requires a coordinated change.

## Voice transcript delivery and the shared editor

The current `transcribe(wavFilePath): String` function is a final-result stand-in. Its returned text is a **draft**:

1. Lane 1 opens the shared New Message editor with the transcript pre-filled.
2. The user may edit or discard it.
3. Only the user's explicit Send action calls `sendMessage(text)` and appends the outgoing message to the shared message list.

PTT and Hands-free must not auto-send or append a transcript as a sent message. Typed Text opens the same editor blank.

Lane 3 may ultimately expose final-only delivery, partial/streaming updates, or another compatible result model. That interface is intentionally unresolved. Lane 1 requires only that an eventual final transcript can be delivered to the editor and must not invent streaming callbacks or real-time transcript acceptance criteria now.

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

## Integration-deferred contracts

- Real `sendMessage` and incoming receive behavior depend on Lane 2 Transport.
- Real PTT transcription depends on Lane 3 Speech.
- Hands-free continuous capture, VAD, segmentation, and exact UI state/event mapping depend on Lane 3's real interface.
- Alert metadata is an unresolved prerequisite across Lane 1 and Lane 2. Do not add an alert field to the local `Message`, packet, or callback until those lanes agree on the contract.
- Maximum-volume, non-interruptible playback for received alerts depends on Lane 2 delivering agreed alert metadata and Lane 3 providing the required speech playback behavior.

## Ownership boundaries

Lane 1:
- screen and user interaction
- microphone permission/capture
- WAV creation
- playback/UI
- one shared observable message list and editor flow
- typed fallback and read/unread presentation
- PTT and dedicated Hands-free UI
- alert UI behavior only after its cross-lane metadata contract is resolved and integration is explicitly scheduled

Speech:
- ASR/STT implementation
- TTS implementation
- model/runtime details
- language/model handling
- audio processing after Lane 1's WAV handoff
- VAD/hands-free speech segmentation and capture-processing behavior

Transport:
- Bluetooth/Wi-Fi transport implementation
- actual byte/message movement
- transport-specific connection behavior

Do not cross these boundaries merely because another lane is unfinished.
