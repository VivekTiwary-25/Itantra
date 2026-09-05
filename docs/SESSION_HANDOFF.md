# SESSION_HANDOFF.md — iTantra lane/app

Written at a tool switch, to carry full context into whatever picks this up next. This is a knowledge dump, not a replacement for `AGENTS.md` (process rules), `STATUS.md` (live ladder status — always check it directly, this file is a snapshot), `docs/CONTRACTS.md` (cross-lane function contracts), or `docs/LANE_1_APP_AND_CAPTURE.md` (the ladder spec itself).

## Ladder status (from STATUS.md at handoff time)

| Rung | Goal | Status |
|---|---|---|
| A0 | Existing app runs on physical phone | GREEN |
| A1 | Screen says `iTantra` | GREEN |
| A2 | `HOLD TO TALK` button; tap changes text to `pressed` | GREEN |
| A3 | True press-and-hold UI: `Recording...` while held, `Idle` on release | GREEN |
| A4 | Runtime microphone permission | GREEN |
| A5 | Record raw 16 kHz mono PCM16 while held | GREEN |
| A6 | Add valid WAV header; pulled file plays correctly on laptop | GREEN |
| A7 | Play last recording inside app | GREEN |
| A8 | Scrolling message list with three fake messages + timestamps | GREEN |
| A9 | Type-and-send fallback | GREEN |
| A10 | Fake Speech/Transport interfaces wired into UI | **RED — implemented, has an open bug, see below** |
| A11 | Push-to-talk mode toggle | RED — not started |
| A12 | Alert-message behavior, only after earlier rungs are green | RED — not started |

`STATUS.md` is the live source of truth. Re-read it directly before trusting this table — it may have moved since this was written.

## CURRENT OPEN BUG — not yet fixed, fix this first

**Symptom:** Holding and releasing `HOLD TO TALK` does not add anything to Logs, even though the wiring was reported as complete. Typed `SEND` correctly appends to Logs.

**Diagnosed root cause:** In `MainActivity.kt`'s `MainScreen`, the `onPress` gesture handler gates the fake-transcript append behind the same `hasMicPermission` check used for the real `AudioRecord` calls:

```kotlin
if (released) {
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    if (hasMicPermission) {
        onTranscript(transcribe(recordingFile.absolutePath))   // <- wrongly gated
    }
}
```

`transcribe()` is a hardcoded stand-in (`private fun transcribe(wavFilePath: String): String = "this is a test message"`) — it never reads `wavFilePath` and has no reason to depend on real microphone permission. Both haptics and the `Recording...`/`Idle` status text are already unconditional (driven only by `isHolding`), so when this gate is false the user sees a fully convincing press/hold/release — correct haptics, correct status text — while silently nothing gets recorded *and* nothing gets appended to the message list. It looks like a UI bug; it's actually a permission-state bug wearing a UI costume.

**Permission was confirmed actually granted** (verified indirectly: A7's `PLAY LAST RECORDING` played back correctly earlier in this session, which requires a real recorded file, which requires the permission to have been granted at that point). So this is a **logic bug, not a permission problem** — the gating itself is wrong, not the permission state.

**THE FIX HAS NOT BEEN APPLIED YET.** Exact next action:

1. Keep `hasMicPermission` gating `recorder.start()` / `recorder.stop()` only (real recording still correctly requires real permission).
2. Let `onTranscript(transcribe(recordingFile.absolutePath))` fire unconditionally on `released`, same as the haptic and status text already do.
3. Build (`.\gradlew.bat assembleDebug`), uninstall/reinstall fresh on device.
4. Retest hold-to-talk end to end: hold, speak, release → confirm `this is a test message` appears in Logs as `↑ Sent`, same as the earlier synthetic-touch verification already showed working once (before this gating bug was traced).
5. Once confirmed by the user on their own physical device, commit, mark A10 GREEN in `STATUS.md` (same pattern as every prior rung), then move to A11.

**Working tree state at handoff:** `app/src/main/java/com/chmod777/itantra/MainActivity.kt` and `docs/CONTRACTS.md` have uncommitted changes (the A10 wiring + the resolved `onMessageReceived` contract, respectively) — **intentionally left uncommitted** because A10 isn't done until the bug above is fixed and physically reconfirmed. Do not commit them as "complete" without applying the fix first.

## What's next after the bug fix: A11

Once A10 is GREEN, the next rung (from `docs/LANE_1_APP_AND_CAPTURE.md`):

**A11 — Push-to-talk mode toggle.** A switch labelled `Push-to-talk`. When on, the hold button is shown (current behavior). When off, show a `Listening...` indicator instead. It does not need to actually do anything yet — Lane 3 supplies the real hands-free logic later. **Physical acceptance test:** flipping the switch changes what's on screen. Scope guardrail: UI toggle only, no real hands-free capture/VAD logic (that's explicitly Lane 3's territory per `docs/CONTRACTS.md`). Note the current `HandsFreeTile` on the main screen (added during A9, per the UI/UX handoff doc) is already an inert placeholder tile for this — A11 is where it needs to become real.

## Bugs found and fixed this session (root cause + lesson)

### 1. A3 gesture conflict: custom pointerInput on a Material3 Button
- **Symptom:** Press-and-hold produced literally nothing — no haptic, no fade, no text change, no pulse. Not even the haptic (the first line inside `onPress`) fired.
- **Root cause:** The press-and-hold gesture (`Modifier.pointerInput(view) { detectTapGestures(onPress = {...}) }`) was layered directly onto a Material3 `Button`, which has its own internal click/ripple pointer-input handling on the same node. The two gesture detectors conflicted, and the custom `onPress` never received the down event at all.
- **Fix:** Moved the gesture handler off `Button` onto a plain, non-interactive `Box` (same visuals: `.clip(CircleShape).background(amber)`), keeping everything else identical.
- **Lesson:** Never bolt a custom `pointerInput`/`detectTapGestures` block onto an already-clickable Compose component (`Button`, `Surface`, anything with `Modifier.clickable`). Use a plain container for custom gesture handling instead.

### 2. RecordingPulse ring invisibility — the real saga, three wrong turns before the right one
- **Symptom:** The "pulsing ring" behind the button was never visible during hold, across three separate reimplementations.
- **Wrong turn #1 (ring-alpha inversion):** The original ring's alpha formula was `((1.38f - scale) / 0.6f)` — alpha was *highest* when the ring was smallest (hidden behind the opaque inner disc) and *lowest* when the ring was largest (exposed). This was a real bug and got legitimately fixed (flipped to `((scale - 0.78f) / 0.6f)`, alpha rising with size) — but fixing it made zero visible difference, which was the first sign the real bug was elsewhere.
- **Wrong turn #2 (assuming it was still alpha/timing math):** Redesigned into a 3-ring staggered "radar ping" effect with corrected alpha-vs-size correlation. Still completely invisible.
- **The actual root cause:** In the button's own modifier chain, `.graphicsLayer { alpha = idleAlpha }` was placed **after** `.clip(CircleShape).background(amber)`. `Modifier.graphicsLayer` only affects modifiers/content that come *after* it in the chain — so the amber fill was drawn *outside* the fading layer and never actually faded. The button stayed fully opaque through the entire "hold" state, sitting on top of the ring/disc underneath — and since both the button and the ring/disc use the exact same amber (`0xFFF8AD3C`), an always-opaque button completely and invisibly hid an amber-on-amber ring. It was never an alpha-math problem; it was an occlusion problem caused by a modifier ordering mistake one call up the tree, and it survived two full ring rewrites because neither touched the actual bug.
- **Escalation method that finally found it** (worth reusing for any "nothing renders" bug):
  1. Swapped the ring for a hardcoded, always-on, fully opaque diagnostic (bright cyan, 6dp stroke, zero alpha/scale math) to separate "is anything drawing at all" from "is the alpha math wrong." It *still* didn't show — surprising, and the key clue that the bug wasn't in the ring's own code.
  2. Added a live on-screen debug counter (`progress=0.XXX` `Text`, reading the exact same `animateFloat` driving the rings) to rule out "the animation isn't actually running." Confirmed it was cycling correctly — ruled out the animation driver entirely.
  3. Used `adb shell input motionevent DOWN`/`UP` to synthetically hold the button (removing human timing/hand variance from the loop), paired with `adb shell screencap` and measuring the rendered circle's actual pixel diameter against the known dp→px density. This showed the on-screen amber circle was still 184dp (the *button's* full size) during "hold," not 112dp (the inner disc) — proof the button had never actually faded, which pointed straight at the modifier chain instead of the ring.
- **Fix:** Reordered to `.graphicsLayer { alpha = idleAlpha }.clip(CircleShape).background(amber).pointerInput(...)`, so the layer wraps the fill.
- **Lesson:** `graphicsLayer` (and any directional Compose modifier — alpha, scale, clip) only affects what comes *after* it in the chain; placing it wrong silently no-ops for everything before it. When a fade/animation visually "isn't happening," check modifier *order* before re-deriving the math a third time. And prefer measuring actual on-screen pixels (screenshot + synthetic input) over visual inspection or user reports alone when the question is "is this rendering at all" vs "is this occluded."

## Local dev gotchas (already recorded in AGENTS.md, repeated here for visibility)

- **Binary file pulls must go through cmd.exe, not PowerShell.** `adb exec-out ... cat file > output` run inside PowerShell has its `>` redirect reinterpret binary output as text and corrupts it — confirmed: a 157KB WAV became 557KB and wouldn't play. Working form: `cmd /c "adb exec-out run-as <package> cat files/X > X"`.
- **Bluetooth audio devices can silently hijack mic input.** If BT earphones are connected to the test phone during a mic-capture test, Android may route `AudioRecord` input through them instead of the built-in mic. Disconnect BT audio devices from the test phone before testing any recording rung.
- **Known issue, not yet investigated (flagged relevant to A12):** `PLAY LAST RECORDING` occasionally cuts off or doesn't fully play. Next time it happens, check: does it cut off at roughly the same point every time (length/buffer issue) or randomly (`MediaPlayer` lifecycle/threading issue), and does it correlate with re-recording quickly before the previous `MediaPlayer` instance finishes releasing. This becomes directly relevant at A12, where alert playback must be reliable and undismissible.

## Key architectural decisions

### Shared message state (A9)
One shared, observable `mutableStateListOf<Message>`, owned in a new `ITantraApp` host composable that sits above `MainScreen`, `LogsScreen`, and `NewMessageScreen` — all three read/write the same list, never separate per-screen stores. `Message` carries `text`, `timestamp`, `direction` (`SENT`/`RECEIVED`), and `isRead`. The unread badge shown on the main screen's Logs tile is *derived* (`messages.count { RECEIVED && !isRead }`), not a separately maintained counter that could drift. This came from `docs/UI-Reference/itantra_ui_ux_handoff_for_claude.txt` (uploaded mid-session, saved into the repo, and treated as authoritative — it superseded an earlier, informal decision to put the typed-message box on the Logs screen; the doc moved it to its own dedicated New Message screen instead).

### onMessageReceived contract resolution (A10)
The original lane spec was ambiguous: prose said the app receives "a string, plus a language code," but the example `onMessageReceived` callback in the same doc showed only a `String`. Resolved and documented in `docs/CONTRACTS.md`:

```kotlin
fun onMessageReceived(callback: (text: String, languageCode: String) -> Unit)
```

matching `speak(text, languageCode)`'s signature, because the app is designed to preserve the sender's original language rather than translate it. Language codes use the ISO 639-1 two-letter set, covering all ten target languages: `en` (English), `hi` (Hindi), `gu` (Gujarati), `mr` (Marathi), `kn` (Kannada), `ml` (Malayalam), `ta` (Tamil), `te` (Telugu), `or` (Odia), `bn` (Bengali). Lane 2/3 must use these exact codes rather than inventing their own labels.

### Why PLAY LAST RECORDING stayed on the main screen
The newer UI/UX handoff mockup's main-screen layout omits it. It was kept anyway (explicit user decision) because it's still useful for capture-pipeline verification (A5/A6/A7) independent of the newer UX direction — removing it wasn't requested and would regress a physically-verified rung's manual test path for no reason.

## Resume point, in one sentence

A0-A9 are GREEN; A10 is implemented but has one known, diagnosed, unfixed logic bug (transcript append wrongly gated behind mic permission) — fix it exactly as described above, rebuild, have the user physically reconfirm hold-to-talk end to end, then commit + mark A10 GREEN, then move to A11 (Push-to-talk mode toggle, scope above) — do not implement A11 until asked.
