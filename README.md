# iTantra

**When every signal fades, your voice still travels.**

Smart India Hackathon 2026 · Problem statement **SIH26173** · Team **chmod 777**, The National Institute of Engineering, Mysuru · Team ID 148903

iTantra is an offline Android app for talking when the network is gone. You speak; the phone turns your speech into
text on the device; the text is sealed and hops from phone to phone over Bluetooth Low Energy; the receiving phone
reads it aloud. No cell towers, no internet, no SIM, no extra hardware.

```
Speak → speech-to-text (on the phone) → seal → phone-to-phone over Bluetooth LE → verify & open → text-to-speech
```

Sending words instead of audio is what makes this work on a weak local link: a short spoken sentence is about 76 KB
of raw audio, but travels as a single sealed message of about 1.2 KB (measured below).

## Two modes

**Normal mode: a private message to one QR-verified contact**
- Sealed for the contact's phone (HPKE) and signed by the sender (Ed25519), using Google Tink.
- Every hop is encrypted (Noise XX). Relay phones carry the message but cannot read it.
- Messages wait and move with people (store-carry-forward) for up to 24 hours, up to 16 hops.
- A signed receipt comes back, so the sender sees "Delivered".

**SOS mode: a call for help to anyone nearby**
- Five categories: Medical, Trapped, Unsafe, Communication, Other.
- The search widens in waves until someone accepts: the 3 strongest-signal phones first, 5 phones at 10 s,
  then up to 2 hops at 25 s and 3 hops at 60 s.
- Both phones show the same 6-digit code so the two people can confirm each other in person. The first helper to
  accept takes the call; the others are told it is taken.
- Rate limits keep spam down.

## Languages

English + 9 Indic languages: Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali.

| | Model | Runs on |
|---|---|---|
| Speech → text, Indic | AI4Bharat IndicConformer 600M, one shared INT8 core + 9 small language heads (697 MB) | phone, via sherpa-onnx |
| Speech → text, English | Whisper tiny.en (INT8) | phone, via sherpa-onnx |
| Text → speech | Piper (English, Hindi, Malayalam), Meta MMS (7 Indic languages) | phone, via sherpa-onnx |

## What we measured

All phone numbers are from an ordinary mid-range phone: **Realme 9 Pro+ (RMX3392), Android 14, 7.7 GB RAM**.
Full tables, methods and raw logs: [`docs/sih-metrics/RESULTS.md`](docs/sih-metrics/RESULTS.md) and
[`docs/sih-metrics/STT_DECISIONS.md`](docs/sih-metrics/STT_DECISIONS.md).

**Accuracy** (word error rate, Google FLEURS test set, 50 clips per language)

| Hindi | Gujarati | Marathi | Tamil | Telugu | Odia | Bengali | Kannada | Malayalam | English |
|---|---|---|---|---|---|---|---|---|---|
| 11.6 % | 20.2 % | 20.3 % | 31.6 % | 21.0 % | 25.1 % | 15.0 % | 16.4 % | 23.7 % | 14.4 % |

On the 7 languages both models support, this is about 3× fewer wrong words than Dolphin base, the published
multilingual model we started with (average 63 → 21 wrong words per 100).

**Speed and memory**
- Speech-to-text runs at RTF ~0.13: 10 s of speech becomes text in about 1.3 s.
- Peak memory with the speech model loaded: about 0.97 GB. No crashes or out-of-memory errors across all 9 Indic languages.

**Size**
- Speech model: 697 MB for all 9 Indic languages (the original 32-bit model is about 2.5 GB; nine separate copies would be about 5.9 GB).
- One sealed message: 1,195 bytes, 60–90× smaller than the raw audio of the same sentence.

## Where things are

| Path | What it is |
|---|---|
| `app/src/main/java/com/chmod777/itantra/MainActivity.kt` | App screens and flow |
| `app/src/main/java/speech/` | Speech-to-text and text-to-speech engines |
| `app/src/main/java/com/chmod777/itantra/crypto/` | Sealing, signing, Noise sessions |
| `app/src/main/java/com/chmod777/itantra/transport/` | Bluetooth LE links, framing, relay policy |
| `app/src/main/java/com/chmod777/itantra/dtn/`, `protocol/` | Store-carry-forward and message formats |
| `app/src/main/java/com/chmod777/itantra/sos/` | SOS waves and helper matching |
| `docs/networking/` | Protocol spec, implementation notes, physical test plan |
| `docs/sih-metrics/` | Measurements and decision logs |

## Build and run

1. Open the project in Android Studio and let Gradle sync.
2. Restore the pinned speech model files (they are not in Git), from the repository root:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-models.ps1
   ```
3. The IndicConformer model (~700 MB) is not part of the APK. Stage it and push it to the phone as described in
   [`docs/STT_INDICCONFORMER.md`](docs/STT_INDICCONFORMER.md).
4. Build and install on a real Android phone (arm64-v8a) with USB debugging enabled. Bluetooth tests need two or more phones.

## Third-party models and licenses

This project builds on open models and libraries, each under its own license: AI4Bharat IndicConformer (MIT),
OpenAI Whisper (MIT), Piper and its voices, Meta MMS-TTS (CC BY-NC 4.0), k2-fsa sherpa-onnx (Apache 2.0) and
Google Tink (Apache 2.0). Vendored code is listed in [`tools/THIRD_PARTY.md`](tools/THIRD_PARTY.md).

## For the team

Lane rules, branch workflow and model-handling rules are in [`docs/TEAM_WORKFLOW.md`](docs/TEAM_WORKFLOW.md).
