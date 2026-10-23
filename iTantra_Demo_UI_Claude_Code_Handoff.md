# iTantra demo UI — complete Claude Code handoff

Paste this entire file into Claude Code while it is opened at the Android repository root. The project is iTantra, Team chmod 777, SIH26173. The deliverable is a working Android UI for the specific filmed demo journey and a coherent set of reachable preview states. Implement the agreed UI in the actual app, using its existing speech, networking, identity, storage, and SOS mechanisms. This is a build request, not a request for a design essay or an audit report.

## Your mission

Build the narrow, polished UI that lets the team screen record these journeys on Android phones:

1. Vachana selects a language, holds a push-to-talk orb, speaks to **Yash**, reviews/edit the transcript, chooses **Urgent** in the shared **Normal | Urgent | SOS** selector, taps **Send**, returns to Home, and the message moves through the actual private-message stack. Yash receives the message in **Logs**, opens the same message-detail view, and hears text-to-speech.
2. Vachana uses **Hands-free** to speak “I need help near the south gate,” stops with **Done**, reviews/edits the draft in the same composer with **SOS** selected, taps **Send**, and returns Home. Her SOS appears in Logs with its live state. On Vivek’s nearby phone, an SOS glass sheet displays the request; he can **Accept** or **Decline**. Once an actual session is established, Vachana sees a restrained **“Vivek is responding” / “Open conversation”** prompt on Home. The emergency conversation allows replies using compact **Hold to talk**, **Hands-free**, and **Type** controls.

Design only the required app surfaces. Other destinations and unneeded actions must be reachable as a tasteful **Coming soon** screen rather than half-implemented product screens. Keep critical live mechanisms and safety checks intact; do not replace real networking with staged success screens.

## Repository first — short discovery, then implementation

Before editing, identify the current branch, working-tree status, real launcher/MainActivity, UI framework, navigation, theme, speech and TTS integration, private-message send/receive API, Logs storage, trusted-contact identity selection, Urgent flag, SOS state machine and responder entry points. Respect AGENTS.md and existing user changes. Locate existing tests and build commands. Identify which currently usable flow is diagnostic-only. You may consult existing website/prototype assets if they are actually present in this checkout; they are visual references, not authoritative architecture.

Do not assume this repo matches screenshots, browser prototypes, or another machine’s worktree. If there are conflicting branches, work on an isolated feature branch/worktree from the best existing integration base and state which base you chose. Do not wipe, rewrite, or merge other people’s work. Avoid large unrelated refactors. Preserve actual data and security semantics even if the UI is narrower.

Write a concise implementation map for yourself, then code. Ask only if a necessary choice cannot be inferred from the repo or this handoff; continue with independent work in the meantime.

## What we have locked as product design

### Design language

- Minimal dark, navy/charcoal **field radio** tone with brutalist efficiency and restrained bento layout. The product is not a generic dashboard.
- Core, high-touch objects are satisfying soft **clay/neumorphic** elements: PTT orb, Hands-free control, three-way mode slider, Send, Accept. Temporary/secondary surfaces use restrained **glass**: language sheet, incoming SOS sheet, contextual prompt. Typography and space provide hierarchy; avoid random large 3D icons, boxed cards around every line, harsh dividers, dashboard status blocks, and default chevron-list rows.
- Keep Home within one phone viewport on common demo devices. Support sensible responsive layouts, safe areas, larger text, accessible contrast, and touch targets. Respect reduced motion and maintain legible Indian-script text. Do not depend on network fonts or images.
- Motion is expressive but short. The touchable element depresses/raises; no perpetual distracting HUD animations. SOS is subtly warm red; Urgent is muted amber; Normal is neutral. Do not treat Urgent and SOS as the same semantic state.
- The public cinematic college map, satellite transition, hop animation, actor footage, subtitles, and music are **video editing assets**, not Android UI work.

### Home

- Home’s job is to **make a draft**. No permanent SOS button/card, readiness banner, nearby-device list, repeated Emergency/Logs cards, “Ready to speak” label, “Release to review…” instruction, idle queue summary, or generic diagnostics on Home.
- Top: iTantra identity and a compact globe/language control such as **English ▾**. Keep room for navigation only to real demo screens; unavailable destinations go to Coming soon if shown.
- Centre: a large tactile hold-to-talk orb. **Idle** is calm. **Hold** depresses a little and gives one haptic pulse; the mic icon and text fully disappear; show a waveform inside the orb and subtle circular yellow fading ripples outside. **Release** transitions cleanly to a restrained transcribing state and then the shared editable composer. On failure: a short muted amber/red motion, a specific fix if known, and **Try again** / **Type instead**. Never throw away captured words.
- Under the orb, retain the existing well-liked **Hands-free** tile in spirit, typography, and relative prominence; do not reinvent it. It starts a continuous foreground listening view. It stops when the user taps **Done**, then opens the same shared editor. No voice path auto-sends.
- Bottom small bento pair: **Write message** (outlined pencil-in-square compose symbol, minimal text) and **Logs** (restrained SMS/chat symbol and only unread incoming count). No repeated Logs action elsewhere on Home. Logs badge is the count of unread incoming messages only; outgoing items are never included, opening Logs alone does not mark incoming read, and opening an individual incoming item does.
- No nearby-devices destination on ordinary Home; nearby phones are routes, whereas human contacts are recipients. Bluetooth/readiness issues appear contextually when they block an action.

### Language selector

- Tap **English ▾** to open a medium-height glass bottom sheet labelled **Message language**, with clean text rows, native names beside English names, and a quiet selected mark. No per-row cards or search field for the fixed list. Collapse on selection and update Home label.
- Intended list: English; Hindi · हिन्दी; Gujarati · ગુજરાતી; Marathi · मराठी; Kannada · ಕನ್ನಡ; Malayalam · മലയാളം; Tamil · தமிழ்; Telugu · తెలుగు; Odia · ଓଡ଼ିଆ; Bengali · বাংলা.
- Do not present unsupported end-to-end languages as ready. Inspect actual STT/TTS coverage in the integration branch. If a language cannot be used in this demo build, show it in the list with an honest disabled/Coming soon state. **Film the reliable English path**; Hindi only if this branch genuinely supports the full route. No automatic language detection, translation, or transliteration. Typed characters remain exactly as written. Language selection drives speech recognition and message metadata/TTS. Lock changes during recording and transcription.
- If the selected language/script and typed text appear mismatched, a tiny nonblocking yellow hint may appear in the editor; sending still works. Keep the detection conservative rather than labelling ordinary mixed-script messages incorrectly.

### The one shared New message composer

- Three entry paths (**PTT**, **Hands-free**, **Write message**) converge on one shared screen. Speech transcript is editable. **Send** is always explicit. The page top is simply a small back arrow and **New message**. Back must preserve the draft unless the user deliberately discards it; use restrained discard confirmation when relevant.
- Near the top, one wide low-profile tactile **three-way slider**: **Normal | Urgent | SOS**. One raised thumb moves across an inset track; only the active mode gains elevation and color. It is not three separate rows/buttons or two nested toggles. Add sensible haptic feedback to changes.
- Display **one** quiet explanation line under the control, corresponding only to the current selection. Suggested concise copy: Normal — **Private to one trusted person. Kept for up to 24 hours.** Urgent — **Private to one trusted person. Shared more widely for up to 6 hours.** SOS — **Requests help from available responders. No trusted contact needed.** Confirm timing/routing details against actual protocol settings before showing exact hours; if different, use true and concise copy. No guarantees of delivery or responder availability.
- Normal and Urgent show a lightweight recipient line: **To · Yash · ⓘ**. Muted “To,” a modest initial/identity dot, tappable name for changing the single trusted recipient, tiny info action explaining **“Only this trusted person can read the message. They do not need to be nearby.”** No “Trusted” or “QR verified” badge. No manage-contacts button or explanatory paragraph in composer.
- If no trusted contacts exist, the recipient picker shows a single **Add trusted contact** path; do not pretend a Bluetooth device or an arbitrary name is a recipient. An existing authentic trusted identity must be used for the filmed private send. Do not seed a fake identity or bypass trust checks. Full contact-management UI can be Coming soon; if trusted-contact creation is needed to prepare the actual demo, preserve the existing QR pairing route as a functional setup path or expose a minimal functional add path rather than breaking it.
- SOS hides the recipient line completely and does not require a trusted contact. It stays on this exact same editor, with a restrained red tint. The final action remains **Send**, never “Request help.” No urgency checkbox or SOS home card.
- The writing area is calm and spacious, with placeholder **Write your message…**, ordinary editable typography, no speech bubble, giant border, character counter, “Text only” caption, or unnecessary repetition. A compact language indicator is acceptable near Send if useful. Keyboard must not cover the editor or Send.
- Mode switching preserves each mode’s draft/state. In particular, switching from Normal/Urgent to SOS and back restores the private recipient and text; SOS has its own draft. Normal/Urgent should retain their prior choices on round trips as agreed, without silently copying an SOS request into private mode. Only one trusted recipient per private message; no multiple-send feature.
- The mode control maps to **real** routing semantics: Normal and Urgent carry a trusted-recipient ID, with Urgent actually passing the protocol priority flag; SOS invokes the real SOS entry and omits recipient. The UI must not merely recolor a button while dispatching everything as Normal.

### All sends and Logs

- Pressing **Send** briefly depresses the control and shows a modest Sending… state. On a successful enqueue/send, all modes return to **Home**. The item appears in **Logs** on the sender, including SOS. Do not strand the sender on a searching screen or invent a separate success page.
- For private messages, convey the actual progression without conflating hop handoff and recipient confirmation. We approved the human-facing direction **Sending… → On its way → Delivered**, where **Delivered** appears only after authentic recipient confirmation. Reconcile with actual Queued, Relayed, Delivered, Expired, Unknown state. If the radio has not carried it, an accurate “Queued”/“Waiting to send” is preferable to “On its way.” Make failure/expiry visible where actionable; never claim delivery on enqueue or next-hop receipt.
- Logs is a minimal real list, not a Coming soon placeholder. It contains outgoing and incoming items with natural sender/recipient, urgent/SOS treatment, meaningful status, and time. Incoming unread badge logic above. A tap opens the existing **message detail**; it is the same message stored in Logs, not a new receiver-only overlay.
- A received Normal private item joins Logs and increments unread until that item is opened. Urgent uses the same underlying Logs/detail view; on arrival it may take visual priority. When the user taps an urgent arrival notification, it opens the matching message detail. If the app is foregrounded, show a tasteful arrival affordance linking to that detail. Do not require the trusted recipient to “accept” a message. Show its actual speech playback state there. Preserve real auto-speak-on-receipt if the app already does it; do not invent a second playback that talks over it. Playback affordance can be a small waveform plus pause/replay depending on actual TTS capabilities.
- Messages received solely as relay ciphertext never appear in the relay phone’s readable Logs and never trigger TTS. Normal and Urgent detail must use the real saved message and language tag; no fake text insertion for the filmed journey.

### SOS searching, acceptance, and conversation

- After Vachana sends SOS: return Home. Put **SOS · Searching for nearby responders…** at the top of Logs with a quiet red active indicator. Opening the SOS record shows the original message, a subtle slow pulse, progress and **Cancel SOS**. The Log entry remains the record of the request and later indicates the real accepted/ended state; it must **not morph into a chat**.
- When a responder accepts, show Vachana a small glass prompt on Home: **Vivek is responding** / **Open conversation**. The name is illustrative: if the SOS responder is not a known/trusted contact, avoid pretending the app knows their real name; prefer **Someone is responding** unless a self-described local display name is actually available, and do not imply verified identity. Opening it leads to the linked emergency conversation. Return Home should leave a sensible way to re-enter that active conversation from the SOS Log detail/prompt.
- On Vivek’s phone, incoming SOS is a temporary glass sheet above Home, with **SOS**, **Someone nearby needs help**, the original message **I need help near the south gate.**, tactile **Accept** and quiet **Decline**. No map, distance, “nearest helper,” device internals, verbose identity warning, or unrelated buttons. Respect actual eligibility/availability logic. An unanswered request and a declined request must not silently become accepted.
- Once an actual responder session exists, open a minimal **Emergency conversation** with small red active dot. Show the initiating request and actual replies, e.g. Vivek: **I’m coming. Stay where you are.** At the bottom offer compact **Hold to talk | Hands-free | Type** choices. The active method expands to a familiar reply area; others remain small. The conversation stays an SOS, so the Normal/Urgent/SOS selector does not repeat inside it. Use the actual emergency chat/send mechanism and correct thread/session identity; never route SOS chat replies through an ordinary trusted recipient.
- There is an existing six-digit nearby-device comparison in the diagnostic SOS mechanism. The agreed **video script removes the code-comparison scene entirely**. In product code, do not bypass, delete, forge, or auto-confirm any existing check that the protocol requires. Integrate a minimal honest step if the active session requires it; keep it out of the film’s spotlight through normal editing, without showing a false direct jump in an uncut live capture. It confirms a device/session match, not a real-world human identity. If the code check is optional, follow real protocol semantics and do not invent a required blocker.
- Preserve actual cancel, first-accept, expiry, deduplication, drop/reconnect behavior. Use concise real UI states when they occur; no unbounded spinner or false connected state. The filmed SOS is a **nearby direct responder** scenario; no multi-hop interactive SOS depiction.

## Route table — important for coherence

| User action | Screen/result |
| --- | --- |
| Home PTT release with recognized words | Shared New message editor, Normal selected initially |
| Home Hands-free → Done | Same editor with editable transcript; filmed SOS take selects/restores SOS |
| Home Write message | Same editor, focused text input |
| Select Normal/Urgent | Show one trusted recipient line and corresponding explanation |
| Select SOS | Hide recipient, preserve SOS draft, show SOS explanation |
| Send successful private/SOS | Home; real outgoing record in Logs |
| Tap Log item | Stored message detail or SOS request detail |
| Tap urgent arrival | Its stored message detail in Logs |
| SOS responder Accept | Establish actual session; sender gets responder prompt; both can open linked emergency conversation |
| Back/leave active conversation | Home/Logs navigation retains ability to return |

## Other routes: Coming soon

Anything outside this narrow scope that remains visible in navigation should lead to one polished **Coming soon** destination, using the same dark type, restrained glass and a single back-to-Home action. Examples: Settings, advanced radio/network settings, diagnostics, complete Contacts/identity management, QR identity details, long-term contact detail actions, nonessential help/about and features with no ready screen. Use per-feature short titles such as “Contacts · Coming soon” if helpful; do not show a broken button, fake functioning service, or blank page.

Exception: real QR identity/trust setup that the private demo requires must remain accessible by an existing working path or a small working bridge. Preserve real permissions, language gating, Logs, notification entry, and SOS chat. Coming soon is an honest UI boundary, not a way to hide a prerequisite or swallow an in-progress user action.

Do not create a whole new interface for each entry point. Reuse the shared composer for private sends from contacts, Home PTT, Hands-free, and typed messages wherever those entry points exist. For this filmed build, a full Contacts app is not required; reuse actual stored trusted contact identity for Yash and a minimal authentic selector.

## Important relationship to the video plan

The film opens with a floating glass problem statement; Vachana is separated from Yash across campus; she records PTT, chooses Urgent, sends; a cinematic map shows encrypted private hops and Yash hears TTS. The second beat is SOS hands-free to nearby Vivek, who chooses to accept and responds. The video is about feel; no model-name or benchmark montage in app UI. The map, satellites, travel shot, dialogue, voice-over, visual effects, and credits are made in the edit, not inside the app.

The older written video blueprint may appear in the workspace. **These later user decisions supersede it** where it disagrees: remove its six-digit-code *scene*; all Sends return Home; SOS searching belongs in Logs; SOS Log is a record, not the chat; the sender opens a separate conversation after the response prompt; Yash’s urgent message lives in Logs/message detail, not a novel overlay. The video may intercut screens out of real-time order, but do not break actual app navigation.

## Implementation rules

- Build native Android UI using the existing framework (likely Kotlin/Compose; verify in repo) and theme. Reuse core speech/network layers and storage. Do not introduce a separate web demo or a fake click-through-only prototype as a substitute for Android behavior.
- Inspect true API contracts before wiring. The current capability inventory reported PTT, Hands-free, typed entry, logs/TTS in MainActivity; Normal/Urgent priority in network API but not MainActivity send path; QR, SOS, responder/chat and Urgent testing only in NetworkLabActivity. That inventory was for commit **ad120d3** and may now be stale; the current checkout wins. Move useful UI and state connections into product navigation without duplicating protocol logic. Do not ship the diagnostic screen as the product UI.
- Keep Android runtime permissions and error handling working. The phone can have no cellular/internet but Bluetooth must be enabled and permissioned. Never suggest airplane mode is sufficient if the handset also disables Bluetooth.
- Language support in the desired product lists ten languages; the build may cover fewer today. Gate unsupported live paths honestly. Do not add cloudy translation or a server dependency.
- Do not invent metrics, status words, recipient identities, delivery receipts, working multi-hop radio runs, responder identities, or TTS languages. Protocol-backed state controls what UI may truthfully say.
- Preserve drafts and selected recipient across navigation/background/interruption where practical; avoid sending stale text after changing mode. Persist SOS pending state from actual backend; do not create a local animation timer that fabricates acceptance.
- Provide restrained accessibility: readable contrast, touch targets, screen-reader labels, reduced-motion fallback, haptic only where supported, meaningful focus order and back behaviour. For modes and urgency, never rely on color alone.
- Avoid adding 3D campus visual assets or animation libraries to the Android binary. Native orb/waveform/sheet motion should be lightweight.
- Keep the film demo copy easy to change from constants/configuration. The quoted lines above are **example lines to film**, not hard-coded messages that appear as sent without speech/typing.

## Expected delivery

1. Implement the screens, navigation, styling, real state and wiring for the two journeys. Enable a reviewer to traverse both on a real phone; Coming soon destinations work for rest.
2. Build the Android app and run the smallest relevant tests/build verification available. If connected phones are available, exercise private send/receive/TTS and direct SOS entry/accept/chat. Do not claim a hardware test if unavailable.
3. Give me a short screen-by-screen demonstration path, a list of files changed, and any specific missing backend prerequisite preventing a *real* take. Distinguish working product UI from externally filmed/animated assets only where relevant to the handoff.
4. Supply a concise “what to record on Phone A / Yash’s phone / Vivek’s phone” sequence with exact taps and suggested recording handles. Stop when the integrated demo paths are runnable and recordable; do not expand the rest of the app.

## Acceptance checks

- [ ] Home fits a real device and keeps PTT, language, Hands-free, Write message, and Logs clear without repeated SOS or device dashboards.
- [ ] PTT hold provides haptic, depressed orb, internal waveform, fading ripples; transcript is editable and never auto-sent.
- [ ] Hands-free → Done produces editable text via the shared composer.
- [ ] Language sheet lists intended languages and gates unsupported live choices honestly.
- [ ] Normal/Urgent/SOS is one tactile control; switching preserves respective draft and recipient state.
- [ ] Normal/Urgent require one real trusted contact; SOS hides recipient; Send invokes the correct underlying mode.
- [ ] Send returns Home; sender sees an accurate outgoing entry in Logs; status is derived from real state.
- [ ] Incoming urgent private message lives in Logs, opens the same message detail, and respects actual TTS behaviour; unread badge counts unread incoming only.
- [ ] Sent SOS appears in Logs with searching/cancel, while responder incoming appears on a nearby phone with Accept/Decline.
- [ ] Accepted SOS produces an honest Home response prompt and a separately reachable emergency conversation; three compact input choices work through actual chat mechanism.
- [ ] Required existing safety/identity checks remain intact even though the code-comparison scene is absent from the video.
- [ ] Other visible routes lead to an elegant Coming soon screen; live demo prerequisites remain functional.
- [ ] Android build succeeds, or blockers are specific and reproducible.

Execute the work. Show me the actual changed app and the shortest path to screen-record it.
