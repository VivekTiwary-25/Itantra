# iTantra — Lane 1: App & Capture

**Assigned to:** Vivek
**Read this fully before writing any code. It takes about 15 minutes.**

> **Why Vivek is on this lane.** It is the one most easily accelerated with coding agents, which means it can be driven fast alongside architecture and contract work. **Once the acceptance targets below are green, this lane stops.** No polishing, no extra screens. From that point Vivek floats across Transport and Speech helping with blockers and integration, which is where the real risk sits.

---

## PART 1 — What iTantra is

### The situation

A flood has taken out the road and, with it, the mobile tower that served the village. A woman is standing in what is left of a doorway holding a phone with a full battery and no bars. Three kilometres away there is a relief team.

She has the device. She knows how to speak into it. What is missing is a path between her voice and someone's ear.

### Why a normal phone call cannot cross that gap

A phone call is not a magic connection between two people. It is a continuous river of data flowing in both directions, and it needs a river-sized channel. When there is no tower, what is left is a trickle — a short-range radio link between two handsets.

You cannot squeeze a river through a trickle by pushing harder.

### The trick

**Do not send the voice. Send the words.**

A spoken sentence, as sound, is enormous. The same sentence, as text, is tiny. Text fits through the trickle easily. And on the far end, the receiving phone turns those words back into a voice, so the listener still *hears* speech and never has to read anything.

Here is the actual arithmetic, and it is the reason this whole project exists:

```
Five seconds of speech, as raw audio:   160,000 bytes
The same sentence, as Hindi text:          ~180 bytes
                                        ------------
Ratio:                                     ~900 : 1
```

Nine hundred to one, before any compression at all.

### The whole system

```
        PHONE A                              PHONE B
      microphone                             speaker
          |                                     ^
          v                                     |
   [ speech -> text ]                   [ text -> speech ]
          |                                     ^
          +------> tiny packet ---)))  air  (((-+
```

That is it. Everything anyone on this team builds is either inside one of those boxes or on that arrow.

### What we are building for

The SIH 2026 internal hackathon at NIE, **7th and 8th September**, North Campus. 150 teams registered.

- **Round 1** — 5-minute presentation + 3-minute Q&A, 100 marks. Cuts 150 teams to ~75.
- **Round 2** — 8-minute demo + 5-minute Q&A, 100 marks.
- Final score = Round 1 × 40% + Round 2 × 60%.

**Every team member must be able to answer jury questions.** There is a section at the end of this document listing exactly what you need to be able to explain. It is not optional.

---

## PART 2 — Where your box sits

```
   +===============================+
   |   LANE 1: APP & CAPTURE       |     <-- YOU
   |   (this document)             |
   |                               |
   |   the screen, the button,     |
   |   the microphone, the         |
   |   message list                |
   +===============================+
              |         ^
     WAV file |         | text to display
              v         |
   +----------------+   |   +------------------+
   | LANE 3: SPEECH |   |   | LANE 2:TRANSPORT |
   | WAV -> text    |   +---| bytes A -> B -> C|
   | text -> sound  |       |                  |
   +----------------+       +------------------+
```

You own **everything the user sees and touches.** No models. No Bluetooth. No packet formats.

If the app were a car, you are building the steering wheel, the pedals, the dashboard and the seats. Lane 3 builds the engine. Lane 2 builds the road. You do not need to know how an engine works to build a good dashboard — but the pedal has to connect to something, and that connection is your contract.

---

## PART 3 — Your contract

This is the part that matters most. Everything else in this document is how to get here.

### What you produce

**A WAV file containing one utterance.**

- Sample rate: **16,000 Hz**
- Bit depth: **16-bit**
- Channels: **mono** (1 channel)
- Format: PCM in a WAV container

You hand Lane 3 a file path. That is the entire handoff.

**These numbers are not negotiable and not a style choice.** The speech recognition models were trained on 16 kHz mono audio. Feed a model 44,100 Hz stereo and it does not error — it produces confident nonsense. This is the single easiest way to waste a whole day on this project, so get it right at the start.

### What you accept

**A string, plus a language code.** You display it in the message list. You pass it to Lane 3 to be spoken aloud.

### How you talk to the other lanes

You call functions. You never look inside them.

```kotlin
// Lane 3 will implement these. You just call them.
fun transcribe(wavFilePath: String): String
fun speak(text: String, languageCode: String)

// Lane 2 will implement these. You just call them.
fun sendMessage(text: String)
fun onMessageReceived(callback: (String) -> Unit)
```

Until Lanes 2 and 3 exist, **write fake versions of these that return hardcoded results.** A `transcribe()` that always returns "this is a test message" lets you build and test your entire screen without waiting for anyone. Swapping the fake for the real one later is a one-line change.

This is the whole reason we split the work this way. Nobody waits.

---

## PART 4 — Concepts you actually need

Only what you need. Nothing more.

**Sound is moving pressure.** When someone speaks, their vocal cords push air, and that pressure wave moves the phone's microphone membrane, which produces a fluctuating voltage.

**Sampling** turns that smooth voltage into a list of numbers by measuring it at fixed intervals. **Sample rate** is how many measurements per second (16,000 Hz = sixteen thousand measurements per second). **Bit depth** is how precise each measurement is (16-bit = a whole number between about −32,768 and +32,767). **Channels** is how many microphones (mono = one, which is all speech needs).

**Why 16,000 Hz specifically?** There is a rule called the **Nyquist limit**: to capture a sound that wobbles at some frequency, you must sample at more than twice that frequency. Human speech carries almost all of its intelligibility below 8,000 Hz. So 16,000 Hz captures it. Music uses 44,100 Hz because cymbals and instruments go higher. Speech models are trained on 16 kHz because that is the right number for speech.

**PCM** (pulse-code modulation) is just the formal name for "the raw list of samples with no compression." **WAV** is PCM with a small header on the front describing the sample rate, bit depth and channel count. That is the entire difference between the two.

This matters because Android's `AudioRecord` gives you raw PCM. If you write those bytes straight to a file and try to open it, nothing will play it, because there is no header saying what the numbers mean. You have to write the WAV header yourself. It is 44 bytes and there are a hundred examples of it online.

**Two operating modes.** The official problem statement requires both:
- **Push-to-talk (PTT)** — hold the button, speak, release. Like a walkie-talkie.
- **PTT off** — the app listens continuously and decides for itself when a sentence has ended. Like a phone call.

We build PTT first, always. It is simple and it is our guaranteed demo. Hands-free mode is a later rung.

**Alert messages.** The brief requires that alert-type messages are announced at maximum volume and cannot be interrupted. That is a UI behaviour and it is yours, but it is late on the ladder.

---

## PART 5 — Your task ladder

Do these in order. Do not skip ahead. **Every rung has a physical test — if you cannot demonstrate it on a real phone, it is not done.**

Report status as 🔴 (not demonstrated), 🟡 (partial or blocked), or 🟢 (physically demonstrated). Never report a percentage. "80% done" means nothing to anyone.

---

### A0 — Get the existing app running on your phone
The Android project already exists and already builds. You are not creating it.

1. Install Android Studio. **Start this download tonight** — it is large and the first build is slow.
2. Clone the team repo (Vivek will send the link).
3. Open it in Android Studio, let Gradle sync, hit Run.
4. Enable Developer Options and USB Debugging on your phone, plug it in.
5. If the phone is not detected, **try a different USB cable first.** Many cables are charge-only and carry no data. This has already cost us time once.

**✅ TEST:** The app installs and opens on your physical phone, showing the default screen. Screenshot it and post in the group.

---

### A1 — Change the text on screen
Find where the screen text lives and change it to say `iTantra`.

This rung exists purely so you learn the edit → rebuild → reinstall loop. That loop is your entire working rhythm for the next three days.

**✅ TEST:** Your phone shows "iTantra".

---

### A2 — Put a button on the screen
A button labelled `HOLD TO TALK`. When tapped, some text below it changes to "pressed".

**✅ TEST:** Tap the button, see the text change.

---

### A3 — Make it respond to press-and-hold
Not a tap. Press-and-hold. While the finger is down, show `Recording...`. When released, show `Idle`.

**✅ TEST:** Hold the button for three seconds. It says "Recording..." the entire time, then "Idle" when you let go.

---

### A4 — Microphone permission
`RECORD_AUDIO` is a *runtime* permission on modern Android. That means two separate things:
1. Declaring it in `AndroidManifest.xml`
2. Actually asking the user at runtime with a permission dialog

Doing only the first silently fails, which is confusing. Do both.

**✅ TEST:** Uninstall the app. Reinstall. Launch. A permission dialog appears asking for microphone access.

---

### A5 — Record raw audio while the button is held
Use `AudioRecord` configured with:
- sample rate `16000`
- channel config `CHANNEL_IN_MONO`
- audio format `ENCODING_PCM_16BIT`

Start on press, stop on release, write the bytes to a file in the app's storage.

**✅ TEST:** Hold, speak, release. A file exists on the device with a non-zero size. Check it with Android Studio's Device Explorer.

---

### A6 — Write a real WAV header ⭐
Add the 44-byte WAV header so the file is actually playable. Pull it off the phone with `adb pull` and open it on your laptop.

**⭐ This is the most important rung in your lane.** Until you hear your own voice come out of your laptop speakers from a file the app made, you do not have a capture pipeline — you have a file of numbers. And if the sample rate is wrong, you will *hear* it: your voice will play back at the wrong speed, like a chipmunk or a slowed-down record. That is the bug this test catches.

**✅ TEST:** Record a sentence in the app, pull the file to your laptop, open it in any media player, hear yourself clearly at normal speed.

---

### A7 — Play the recording back inside the app
Add a `PLAY LAST RECORDING` button using `MediaPlayer` or `AudioTrack`.

**✅ TEST:** Record, tap play, hear yourself through the phone's speaker.

---

### A8 — Message list on screen
A scrolling list of messages. Hardcode three fake ones for now — "Water rising near the school", "Six people at the temple", "Need medical supplies".

Each entry shows the message text and a timestamp.

**✅ TEST:** Three messages visible on screen and the list scrolls.

---

### A9 — Type-and-send ⭐
A text input box and a `SEND` button. Typing a message and hitting send adds it to the message list.

**⭐ This rung is more important than it looks.** It is our fallback demo path. If speech recognition is not integrated in time on the 8th, a typed message travelling all the way across to the other phone and being spoken aloud still demonstrates the entire architecture. Do not skip it because it feels unglamorous.

**✅ TEST:** Type "hello", hit send, it appears in the list.

---

### A10 — Wire in the fake interfaces
Create the four fake functions from Part 3 (`transcribe`, `speak`, `sendMessage`, `onMessageReceived`) with hardcoded behaviour. Wire your buttons to call them.

- `HOLD TO TALK` release → calls `transcribe(path)` → whatever comes back goes into the list
- `SEND` → calls `sendMessage(text)`

**✅ TEST:** Hold the button, release, and the fake transcript appears in the message list. The plumbing is now complete even though the engine is fake.

---

### A11 — Mode toggle
A switch labelled `Push-to-talk`. When on, the hold button is shown. When off, show a `Listening...` indicator instead. It does not need to actually do anything yet — Lane 3 supplies the hands-free logic later.

**✅ TEST:** Flipping the switch changes what is on screen.

---

### A12 — Alert messages (only if the rungs above are all 🟢)
A checkbox next to the send box marked `ALERT`. Messages sent as alerts appear in the list in red, and when received are played at maximum volume and cannot be dismissed mid-playback.

**✅ TEST:** Send an alert, it shows in red, and playback cannot be stopped by tapping elsewhere.

---

## PART 6 — Not your problem

Do not read about these. Do not think about these. If you catch yourself researching one, you have drifted.

- How speech recognition works. Spectrograms, Conformers, decoders — none of it.
- How Bluetooth works. Discovery, pairing, sockets — none of it.
- Model files, ONNX, quantization.
- The ten languages. Build in English. Language handling is Lane 3.
- Packet formats, headers, checksums, message IDs.
- Voice activity detection.
- Compression.

If someone asks you a question about any of the above, the correct answer is **"not my box, ask Lane 2 / Lane 3 / Vivek."** That is not rudeness, it is how this team stays fast.

---

## PART 7 — When you get stuck

**The 30–45 minute rule.** If you have been stuck for 30–45 minutes on something *structural* — the build won't run, the permission won't grant, the file won't save — post it in the group. Do not silently burn three hours on it.

This is not a failure. It is the system working. The whole point of splitting the work is that a blocker in one lane should cost 45 minutes, not an evening.

**Use Claude Code or Codex inside the project.** This document tells you *what* to build and *how to know it worked*. It deliberately does not give you Kotlin line by line. Open the project in Claude Code and describe the rung you are on — it will write the code. Your job is to know what you are asking for and to verify it against the physical test.

**Stop when the ladder is green.** A11 is the finish line for this lane, and A12 only if everything else is already green. Every extra hour spent making this screen nicer is an hour not spent unblocking Transport or Speech, and those two lanes carry all the real uncertainty in this project.

---

## PART 8 — What you must be able to explain to a juror

Round 1 has 3 minutes of Q&A, Round 2 has 5. Questions get directed at whoever looks least sure. Be able to answer these in your own words, out loud, without notes:

**Q: Why do you record at 16 kHz mono instead of CD quality?**
Speech carries almost all its intelligibility below 8 kHz, and the Nyquist limit says you need to sample at more than twice the highest frequency you want. So 16 kHz is enough for speech and half the data of anything higher. More importantly, the recognition models were trained on 16 kHz mono — feeding them anything else produces garbage.

**Q: What is the difference between PCM and WAV?**
PCM is the raw list of audio samples. WAV is the same data with a 44-byte header describing the sample rate, bit depth and channel count so software knows how to interpret the numbers.

**Q: Why does the app have two modes?**
The problem statement requires both. Push-to-talk makes it behave like a walkie-talkie. With PTT off it should behave like a phone — always listening, deciding on its own when a sentence has ended.

**Q: How big is five seconds of audio, and why does that matter?**
About 160,000 bytes raw. The same sentence as text is about 180 bytes. That roughly 900-to-1 ratio is the entire reason this project sends text instead of voice.

**Q: Why does the app need microphone permission at runtime rather than at install?**
Modern Android treats the microphone as a sensitive capability. Declaring it in the manifest is not enough; the user must explicitly grant it while the app is running, and can revoke it later.

---

## PART 9 — Timeline

| When | What |
|---|---|
| **Tonight (4th)** | **Start the Android Studio and SDK download tonight, not tomorrow morning.** It is hours, not minutes. Aim for A0 green. |
| **5th** | A1 through A7. You should hear your own voice from a file the app made. |
| **6th** | A8 through A11. Freeze day — no new features after tonight. |
| **7th** | Round 1. Presentation and Q&A. |
| **8th** | Round 2. Demo. |

If A6 is not green by the end of the 5th, say so in the group that evening. That is the rung everything downstream depends on.
