# iTantra film-demo builds (Vachana / Yash / Vivek)

Three actor-specific APKs for screen-recording the SIH film. They share one
codebase (`app/src/demo`) and **simulate everything locally**: no real STT, TTS,
Bluetooth, QR trust or cross-phone delivery. The editor supplies continuity.
The real app is the `full` flavor and is untouched.

| Actor   | Flavor        | Package                            |
|---------|---------------|------------------------------------|
| Vachana | `demoVachana` | `com.chmod777.itantra.demo.vachana` |
| Yash    | `demoYash`    | `com.chmod777.itantra.demo.yash`    |
| Vivek   | `demoVivek`   | `com.chmod777.itantra.demo.vivek`   |
| (real)  | `full`        | `com.chmod777.itantra`              |

All three install side by side with each other and with the real app.

## Build

```
.\gradlew.bat assembleDemoVachanaRelease assembleDemoYashRelease assembleDemoVivekRelease
```

Output: `app\build\outputs\apk\demo<Actor>\release\app-demo<Actor>-release.apk`.
Release variants are non-debuggable (smooth Compose animation) and signed with
the local debug key so they sideload directly. `assemble…Debug` also works.
The real app is now `assembleFullDebug` (`assembleDebug` builds all four).

Publish to the rolling GitHub release `demo-latest`:

```
powershell -ExecutionPolicy Bypass -File tools\publish-demo-apks.ps1
```

Download on a phone from the repo's Releases page → **iTantra SIH Demo — Latest**
→ `iTantra-<Actor>-latest.apk`. APKs signed on a different machine need the old
demo app uninstalled first.

## Director console (hidden)

On Home, **press and hold the "iTantra" wordmark for about one second** (wait until Home has
settled after launch). It is never meant to be filmed.

- **Before filming**: notification permission (Allow), channel status (Settings),
  exact timers. On Realme/ColorOS, also enable *banner / floating* notifications for
  iTantra in Settings, or the notification arrives without dropping down.
- **Language & mock transcripts**: pick the language (same as Home's picker); edit the
  **Push-to-talk** and **Hands-free** transcripts for that language — separate fields.
- **Simulate incoming message**: role preset, From, Message, Mode (Normal / Urgent /
  SOS request with Accept-Decline / SOS reply), optional audio clip (Choose file or
  Record here), delay (3/5/10/15/30 s or custom), notification on/off, **Arm** or
  **Trigger now**. The Logs row is written before the notification is posted.
- **Reset**: Clear Logs, Cancel pending (also clears shown notifications), Reset
  everything to the actor's defaults.

Only one event can be armed at a time; arming again replaces it. Timers use an exact
alarm, so they fire after Home/app switching and even if the process is killed.

## Default transcripts (English; all ten languages have editable defaults)

| Actor   | Push-to-talk | Hands-free |
|---------|--------------|------------|
| Vachana | Hey, remember to bring milk on your way back. | I need help near the south gate. |
| Yash    | Yep, got it. | Yep, got it. |
| Vivek   | I'm coming. Stay where you are. | I'm coming. Stay where you are. |

Vachana's trusted list: Yash, Trisha, Utkarsh Keshri, Vaishnavi M H (never herself,
never Vivek). "+ Add trusted contact" accepts any QR code (hidden fallback: hold the
viewfinder 1 s) and asks only for a name.

## Shot checklist

**Vachana — Path 1 send**: Home → English ▾ → English → hold orb, speak, release →
To · Select trusted contact → Yash → Send → "Sent to Yash" → Home.

**Yash — Path 1 receive/reply**: console → preset *Vachana · milk* (+ audio clip) →
Arm → leave app, scroll Reels → notification → Logs → Vachana → Play audio →
hold reply orb, speak, release → To · Vachana → Send.

**Vachana — Path 1 return**: console → preset *Yash's reply* → Arm → open notes/PDF →
notification → Logs → Yash → cut.

**Vachana — Path 2 SOS**: Home → Hands-free → speak → Done → SOS → Send → "SOS sent".

**Vivek — Path 2 respond**: console → preset *Vachana · SOS* → Arm → leave app →
notification → **Accept** → Logs → Vachana SOS → hold reply orb → Send.

**Vachana — Path 2 final**: console → preset *Vivek's SOS reply* → Arm → leave app →
notification → Logs → Vivek → cut.

Reset between takes: console → *Reset for another take*.
