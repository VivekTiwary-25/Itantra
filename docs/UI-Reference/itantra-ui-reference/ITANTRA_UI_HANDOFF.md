# iTantra UI Reference — Codex Handoff

> [!IMPORTANT]
> **Superseded / archival UI handoff.** The authoritative UI/UX direction is `docs/UI-Reference/itantra_ui_ux_handoff_for_claude.txt`. Keep this file and its prototype artifacts only as secondary references for compatible visual details; they never override `TASK.md`, `docs/CONTRACTS.md`, `docs/LANE_1_APP_AND_CAPTURE.md`, or the newer UI/UX handoff.

## Purpose

This prototype records the agreed visual and interaction direction for the finished Lane 1 Android screen. It is a design reference, not production code and not permission to implement future task rungs early.

Prototype URL: https://itantra-android-prototype.laucat.chatgpt.site

The accompanying `dist/` folder contains the exact HTML, CSS, and JavaScript used by the prototype. Codex should inspect or run these files locally when it needs precise layout, animation, color, spacing, or state-transition details.

## Sources of truth and precedence

When sources appear to conflict, use this order:

1. `TASK.md` — what is allowed to be implemented in the current turn/rung.
2. The Lane 1 specification — required functionality and acceptance tests.
3. This handoff and `docs/UI_VISION.md` — approved interaction direction.
4. The interactive prototype — visual treatment, proportions, motion, and state transitions.

The prototype shows the eventual combined screen. It must never be interpreted as authorization to implement later-rung functionality early.

## Implementation boundary

- Recreate the design natively in Jetpack Compose.
- Do not embed the website in a WebView.
- Do not copy web-specific architecture into the Android app.
- Preserve the repository's existing architecture and dependency choices.
- Implement only the portion relevant to the current rung.
- Make the smallest change that satisfies the current rung and its physical acceptance test.
- Do not redesign, add screens, add navigation, or invent further polish.

## Locked visual direction

- One operational screen with a modern push-to-talk radio character.
- Near-black/navy field-tool palette with high contrast.
- Large circular amber `HOLD TO TALK` control as the dominant idle action.
- Explicit text status such as `Ready`, `Listening`, or `Sent`; do not rely on color alone.
- Connection state at the top, only when connection functionality exists.
- Push-to-talk / hands-free mode control, only when its rung is reached.
- Conversation history behaves like a radio log, not decorative chat bubbles.
- Typed fallback sits at the bottom, only when its rung is reached.
- Large touch targets, restrained density, no decorative disaster imagery.

## Locked interaction direction

### Idle push-to-talk

- PTT control dominates the centre.
- Conversation history is visible below it.
- The screen is calm and static.

### PTT held

- Give one short, firm haptic confirmation when recording starts.
- Hide/fade the conversation and typed fallback.
- Replace the idle control with the focused `Listening…` state.
- Show a small instruction such as `Speak now · release when finished`.
- Use a restrained pulse/listening animation.

### PTT released

- Give one lighter haptic confirmation.
- Briefly show completion/sending feedback when real behavior supports it.
- Restore the normal operational screen and conversation automatically.

### Hands-free

- The PTT control disappears.
- Use the same listening visual language as the PTT-held state.
- Show `Listening…` and `Speak normally`.
- Hands-free does not authorize Lane 1 to implement VAD or automatic sentence detection; those belong to the speech lane.

### Listening animation

- One or two soft expanding/fading rings.
- A few restrained centre audio bars are allowed.
- Calm motion, not a dramatic waveform or music equalizer.
- Until real amplitude input is wired intentionally, the motion is state feedback rather than a claimed live waveform.

## Deliberately excluded

- Extra screens, bottom navigation, onboarding, profile/account UI, maps, dashboards, settings, decorative illustrations, slogans, elaborate branding, or unsolicited rearchitecture.
- Additional animation beyond the approved focused listening transition.
- Any functionality from a future rung.

## Verification expectation

For each rung, Codex should:

1. Inspect the repository instructions and current task first.
2. Identify which small part of this eventual design belongs to that rung.
3. Implement only that part in native Compose.
4. Run the required Windows debug build.
5. Report the exact physical test Vivek must perform on the Android phone.
6. Stop without advancing the task files or beginning the next rung.
