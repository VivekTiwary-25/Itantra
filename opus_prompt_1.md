Take over iTantra TTS with one simple goal:

**Get receive-side TTS physically working for at least English and Hindi. If safe and practical, also get Bengali and Marathi working.**

This is the final demo-critical TTS recovery pass.

## Current baseline

Start from the latest pushed integration branch:

`integration/recovery-pass`

Last known tip:

`c8f8a9b`

Fetch first and verify the actual current remote state before changing anything.

This branch already contains:

- physically verified English STT → Bluetooth → received Logs from the prior baseline
- integrated transport
- ACK/delivery-state changes
- ISO language metadata across transport
- Dolphin provisioning
- updated docs

Do NOT redesign transport.

Do NOT merge `origin/main`.

Do NOT touch relay/store-and-forward.

Do NOT revive Kannada.

---

# TTS TARGET

Minimum success condition:

### English

Phone A:
speech → STT → Send → Bluetooth

Phone B:
receive text → automatically speak it aloud in English

### Hindi

Phone A:
Hindi speech → STT → Send → Bluetooth

Phone B:
receive Hindi text → automatically speak it aloud in Hindi

If both work physically, the core TTS mission succeeds.

---

# OPTIONAL EXTENSIONS

There is reportedly newly pushed Bengali and Marathi TTS work.

Reported models:

### Bengali
Meta MMS-TTS:
`facebook/mms-tts-ben`

Converted through sherpa-onnx MMS → VITS export.

Expected runtime asset directory reportedly resembles:

`app/src/main/assets/vits-mms-ben/`

### Marathi
Meta MMS-TTS:
`facebook/mms-tts-mar`

Same conversion path.

IMPORTANT:

Do not trust these implementations blindly.

Fetch all remote branches and identify the actual pushed TTS commits/branch.

Audit them before integrating.

There is also a reported token-file issue:

- Bengali token index 57 was missing
- Marathi token index 29 was missing
- `:` was manually inserted as a replacement/placeholder

Determine whether `:` is actually the correct token at those indices.

Do NOT preserve a placeholder merely because it allows the engine to initialize.

Verify against the original model/export vocabulary or authoritative sherpa-onnx/Hugging Face artifacts where possible.

If the token mapping cannot be established confidently, leave Bengali/Marathi experimental rather than risking the English/Hindi path.

Also record the CC-BY-NC-4.0 licensing status of the MMS Bengali/Marathi models clearly in docs/reporting.

---

# FIRST: AUDIT EXISTING TTS

Before implementing:

Inspect:

- `SpeechEngine`
- `TtsHelper`
- sherpa-onnx initialization
- Piper/VITS asset paths
- English Piper model
- Hindi `hi_IN-pratham-medium`
- temporary Test TTS UI
- TTS model-loading lifecycle
- AudioTrack/audio output path
- cleanup/release behavior
- any newly pushed Bengali/Marathi code
- setup scripts
- logs
- recent TTS-related commits

Determine why TTS currently does not physically work.

Clearly separate:

**VERIFIED FACT**

from

**INFERENCE**

Do not spend the whole session auditing. Once the likely path is understood, implement the smallest robust fix.

---

# IMPLEMENTATION REQUIREMENTS

## 1. Make standalone English/Hindi TTS robust

The temporary Test TTS control must be usable to exercise TTS directly.

Get English working first.

Then Hindi.

Requirements:

- correct model selected from language code
- model initialized safely
- heavy model loading / synthesis must not freeze or crash the main UI thread
- useful logging around:
  - language selected
  - model path
  - model initialization
  - synthesis start
  - synthesis completion
  - audio playback start/end
  - caught exception/error
- do not log sensitive/full spoken text unnecessarily
- release native/audio resources safely

---

## 2. Wire receiver TTS

Once the standalone path is sane, integrate:

```text
received message
+
received ISO languageCode
        ↓
TTS routing
        ↓
correct voice
        ↓
audio output
```

The transport receive path itself must remain stable even if TTS fails.

A TTS exception must NOT:

- kill the RFCOMM reader
- crash Bluetooth transport
- lose the received text
- mark transport as failed

Received text should still appear in Logs even if speech playback fails.

Do not run expensive TTS initialization synchronously inside the transport callback/main UI path.

---

## 3. Language routing

Minimum required:

- `en` → existing English Piper voice
- `hi` → existing Hindi Piper `hi_IN-pratham-medium`

Optional only if trustworthy and buildable:

- `bn` → Bengali MMS-derived VITS
- `mr` → Marathi MMS-derived VITS

For unsupported TTS languages, fail gracefully:

- message still arrives
- text remains visible
- no app crash
- log/report that no verified TTS voice exists

Do NOT fake support for Gujarati/Tamil/Telugu/Odia merely because STT supports them.

---

# GIT HYGIENE

Create a new branch from the verified integration baseline.

Example:

`integration/tts-recovery`

Do not modify the existing recovery branch directly.

Use separate commits where possible:

1. standalone English/Hindi TTS runtime fix
2. receive-side TTS integration
3. Bengali/Marathi support if genuinely safe
4. docs/setup cleanup

Before every commit inspect:

```powershell
git status
git diff
git diff --cached
```

Do not blindly `git add .`.

Push successful checkpoints.

Do not claim physical verification unless a real phone was actually used.

---

# BUILD AND TEST

After each logical step:

```powershell
.\gradlew.bat test assembleDebug
```

At the end:

```powershell
.\gradlew.bat clean test assembleDebug
```

Record:

- build result
- JVM tests
- APK path
- SHA-256
- final branch/commit
- push result

---

# PHYSICAL TESTING

If an Android phone is connected and available through ADB, use it.

First physically test the temporary Test TTS control:

### English
Input a short English sentence.
Confirm actual audible English speech.

### Hindi
Input a short Hindi sentence.
Confirm actual audible Hindi speech.

Only after standalone TTS works, test received-message TTS.

If two phones are available:

### English end-to-end

Phone A:
speak English → STT draft → Send

Phone B:
receives exact text → text appears in Logs → phone speaks it aloud

### Hindi end-to-end

Same flow using Hindi.

Capture logcat if any crash, exception, native error, model-load failure, or AudioTrack failure occurs.

Do not diagnose from symptoms alone.

---

# SUCCESS CLASSIFICATION

At the end classify each language separately:

### English TTS
- implemented
- build-tested
- physically verified / unverified / failed

### Hindi TTS
same

### Bengali TTS
same

### Marathi TTS
same

And separately classify:

### Transport → TTS integration

### Full speech → STT → Bluetooth → TTS pipeline

Do not call Bengali/Marathi GREEN merely because their model loads.

---

# PRIORITY ORDER

If time or context becomes constrained:

1. English standalone TTS
2. Hindi standalone TTS
3. English receive-side TTS
4. Hindi receive-side TTS
5. Bengali
6. Marathi
7. docs/polish

Do not sacrifice working English/Hindi to chase Bengali/Marathi.

---

# FINAL REPORT

Return:

## ROOT CAUSE / WHAT WAS BROKEN

## ENGLISH TTS

## HINDI TTS

## BENGALI TTS

## MARATHI TTS

## RECEIVE-SIDE TTS INTEGRATION

## PHYSICAL TEST RESULTS

## BUILD RESULT

## COMMITS CREATED

## PUSH RESULTS

## FINAL APK + SHA-256

## REMAINING FAILURES

## EXACT LAST-MINUTE TEST CHECKLIST

The most important final sentence must state plainly whether this pipeline is physically GREEN:

**speaker → STT → Bluetooth → receiving phone → audible TTS**