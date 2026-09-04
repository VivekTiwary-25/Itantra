# iTantra

iTantra is our Smart India Hackathon prototype for offline multilingual speech communication over low-bandwidth local links.

The core flow is:

**Speech → ASR → Text → Bluetooth / Relay → Text → TTS**

The prototype is being developed as three independent lanes that will later integrate into one Android app.

## Team Lanes

### App & Capture — Vivek

* Android app shell
* Push-to-talk
* Microphone permission
* 16 kHz mono audio capture
* WAV save/playback
* Message display

Branch:

```bash
lane/app
```

### Transport & Relay — Utkarsh + Yash

* Bluetooth Classic
* Device discovery / connection
* Send and receive bytes
* Direct Phone A → Phone B
* Minimum Phone A → Phone B → Phone C relay
* Message ID + TTL / hop-count logic

Branch:

```bash
lane/transport
```

### Speech — Trisha + Vaishnavi + Vachana

* sherpa-onnx Android runtime
* Whisper int8 ASR
* WAV → text
* English/Hindi TTS
* Text → spoken audio

Branch:

```bash
lane/speech
```

## Git Rules

**Nobody works directly on `main`.**

`main` is the stable integration branch.

Each team works only on its assigned lane branch.

Before starting:

```bash
git clone https://github.com/VivekTiwary-25/Itantra.git
cd Itantra
git checkout <your-lane-branch>
```

Check your branch with:

```bash
git branch
```

Only green, physically tested work gets merged into `main`.

Vivek handles integration merges.

## First Milestone

Before implementing your lane:

1. Open the project in Android Studio.
2. Let Gradle sync finish.
3. Connect a real Android phone with USB debugging enabled.
4. Build and install the existing app.
5. Open it successfully.
6. Send a screenshot in the group.

Do not start lane implementation until this works.

## Working Rule

Work one acceptance-test rung at a time:

**Build → Run → Physically verify → Commit → Next rung**

Do not ask an agent to implement the entire lane at once.

If the same structural failure repeats for roughly **30–45 minutes** with no new information being learned, escalate it in the group.

## Shared Files

Be careful with files such as:

* `AndroidManifest.xml`
* `app/build.gradle.kts`
* `gradle/libs.versions.toml`
* `settings.gradle.kts`

These affect multiple lanes. If a large or unusual change is needed, coordinate before making it.

## Speech Models

Large model files are intentionally excluded from Git.

Do not commit `.onnx`, `.bin`, or model directories.

Use the model download instructions in the Speech lane brief.

## Prototype Target

Target end-to-end flow:

**Mic → ASR → Text → Bluetooth / Relay → Receiver → Display → TTS**

Fallback if integration is incomplete:

**Typed text → Transport → Display → TTS**

with ASR demonstrated separately as:

**WAV → Transcript**
