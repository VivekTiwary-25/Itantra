# iTantra UI/UX - current handoff

Updated: 2026-09-26. This is the resume point for website/design work on
`UI/UX_discussion` in https://github.com/VivekTiwary-25/Itantra.

## Why this workspace exists

The user wants one phone-framed, interactive website that feels like the
finished ideal iTantra Android app. Reviewers should experience flows, shared
state, back actions, failures, and trust/delivery consequences by using it.
System dependencies are faked visibly through controls outside the phone.

An earlier website followed the narrower Android UI handoff. The user corrected
that direction and explicitly required the complete ideal product, rather than
the current implementation's five screens/eight languages. The existing visual
and interaction work was extended, not replaced from scratch. The user now
wants this UI/UX branch to transfer all files and context to another device and
coding agent.

## Read order and authority

1. `AGENTS.md` here.
2. This handoff.
3. `iTantra_Complete_Ideal_UI_UX_Design_Handoff_v1.md` in full for product scope.
4. `iTantra_UI_Correction_Brief.md` for the correction requirements.
5. Inspect current HTML/CSS/JS before editing.

The user's priority is ideal-product handoff, correction brief, current
prototype, historical UI sources, then engineering context. Historical files
that describe themselves as authoritative retain their original wording for
provenance; they do not override that newer instruction. Root native-app tasks
and engineering readiness do not reduce this mockup's scope. Raise genuine
contradictions rather than silently changing the product.

## Canonical files and provenance

| Path | Role |
|---|---|
| `current-prototype/dist/index.html` | Phone screens, dialogs, simulator panel, assumptions |
| `current-prototype/dist/styles.css` | Dark navy/amber/cyan aesthetic, layout, motion |
| `current-prototype/dist/app.js` | Navigation, shared state, simulation actions |
| `current-prototype/.openai/hosting.json` | Existing hosted-site identity and static asset root |
| `review/itantra_ideal_product_review_guide.pdf` | Current six-page printable/fillable review guide |
| `review/build_review_guide.py` | Portable guide generator; walkthrough text maintained manually |
| Two ideal/correction Markdown files here | Exact copies of the user's authoritative inputs |
| `historical/itantra-ui-v2-prototype.zip` | Original approved visual-direction prototype bundle |
| `historical/previous-review-prototype/dist/` | Earlier partial review website, superseded |
| `historical/previous-review-guide.pdf` | Guide for that earlier partial site, superseded |
| `historical/itantra_ui_ux_handoff_for_claude_original.txt` | Exact original earlier handoff input |
| `itantra_ui_ux_handoff_for_claude.txt` and `itantra-ui-reference/` | Pre-existing engineering/history references |
| `engineering-context/SIH_MAIN_HANDOFF.txt` | Original engineering context input |

The current site snapshot was copied byte-for-byte from the corrected local
`itantra-site-edit/dist` checkout. The cloud source checkpoint was
`af2b1f4a7b65b3926c4e0aa630d11fa0db36d18f` (Sites version 2).
The previous local folders `itantra-review-site/` and `itantra-site-edit/` are
untracked local work areas, not required by the transfer package. Their nested
Git directories, temporary screenshots/tests, runtime dependencies, research,
and deployment archives are not part of this UI/UX commit.

## Current website behavior

- First launch offers simulated Nearby devices, Notifications, Microphone, and
  Camera permissions. Home shows readiness/degraded state.
- Home preserves the dominant Hold to talk orb and Hands-free/Text/Logs tiles.
- Ten message/SOS language choices: English, Hindi, Gujarati, Marathi, Kannada,
  Malayalam, Tamil, Telugu, Odia, and Bengali. Sample transcripts are canned;
  the rest of the interface is English.
- PTT shows recording then processing and opens the shared editable draft.
  Hands-free shows Listening, accepts Simulate speech in Hands-free, then Done
  produces the same editor. Voice result failures offer retry or typed fallback.
- Private messaging requires one explicitly selected valid trusted contact,
  nonempty text, and at most 500 Unicode code points. Urgent is an outgoing
  flag; it does not promise delivery. Simulated storage failures retain draft.
- QR flow includes My identity, simulated scan, identity comparison and explicit
  confirmation, local label, trust addition, rename, forget, duplicate/self/
  invalid/same-name/changed-identity/camera outcomes. Default fake identity is
  Rahul. QR pixels are a visual placeholder, not a real encoded identity.
- Send stores the private outgoing message as Queued and returns Home.
  Simulator controls advance the latest outgoing message to Relayed, Delivered,
  Expired, or Unknown with guarded transitions. Relay acceptance is not delivery;
  Delivered requires the intended recipient's confirmation, not a read receipt.
- Shared newest-first Logs and message detail show direction, language, time,
  urgency, delivery state and explanation. Incoming messages begin unread.
  Opening Logs does not mark read; opening an incoming row does. Duplicate
  receive is ignored. Play aloud/Stop/finish/failure are simulated status changes.
- Emergency Mode enables local participation; Available to help is opt-in and
  gated by mode/readiness. Incoming SOS offers have limited pre-acceptance facts,
  Accept/Decline, timeout, already-answered and busy cases.
- Requester SOS chooses category/language/optional note, starts search, widens
  through relays after a short fake delay (or manual control), and accepts a
  direct/relayed responder. Two accepts show one winner and a later rejection.
- An SOS conversation begins encrypted-but-unverified. Nearby code comparison
  can mark its device verified; mismatch/skip retain unverified status. A relayed
  session cannot claim nearby verification. Replies, voice draft, receive, speech
  playback, interruption, timeout, end/cancel/expiry are represented.
- Simulated reinstall changes local identity and invalidates contacts until a
  new QR exchange. This mockup retains message history during that simulation.
- Simulated readiness cases include Bluetooth off, denied nearby/notification
  permission, stopped background participation, low battery, restart and reinstall.

## State and simulation implementation

There is no frontend build, dependency bundle, server logic, radio stack,
encryption implementation, actual camera, microphone access, STT, or audio TTS.
Everything is plain browser HTML/CSS/JS. The shared `state` object in `app.js`
owns contacts, messages, draft, identity, readiness, Emergency Mode, offers, and
the latest SOS. It is stored in localStorage under `itantra-ideal-review-v1`.
Transient screen/navigation/capture state is separate. Reset full review clears
simulated data and returns to onboarding with permissions unset.

The simulator sits outside the phone and only affects applicable scenarios.
Voice and storage results remain selected until changed; the speech-failure
checkbox is not automatically cleared after one playback. The current review
guide starts from reset, grants four permissions, adds Rahul, then proceeds.

## Exact My assumptions list

1. All communications, contacts, QR scans, device encounters, voice and speech playback are simulated.
2. A trusted message goes to one explicitly chosen contact. Recipient selection is independent of nearby radio discovery.
3. Queued, Relayed, Delivered, Expired and Unknown are separate visible states; relay acceptance never means recipient delivery.
4. “Available to help” is opt-in and only works while Emergency Mode can participate.
5. One confirmed responder owns an SOS session; a second acceptance is shown as too late.
6. Nearby-device verification checks the active session, not a person's role or intentions.
7. Text persists when speech playback fails. Simulated restart preserves messages; simulated reinstall changes identity.

## Known limits to carry forward

This is a broad interactive review prototype, not proof that every state in the
long ideal-product handoff has been exhaustively audited or implemented.

- SOS navigation copy says a session can be resumed through Logs, but
  `renderLogs()` currently renders trusted messages only. SOS uses a single
  latest object rather than a complete historical/resumable SOS log. This is a
  concrete follow-up discrepancy; do not claim it works in review instructions.
- Incoming simulated SOS offers are fixed Medical/Kannada/direct examples.
  Requester relay acceptance is available, but there is no incoming relayed-offer
  selector in the simulator.
- Urgent is selectable for outgoing trusted messages. Receive simulator creates
  ordinary incoming messages; automatic urgent alert/TTS policies are not fully
  represented. Speech feedback is text-only, with no real sound.
- My assumptions are displayed as seven statements. Per-item live Accept/Change
  controls and two-option live alternatives requested in the initial brief have
  not been implemented in the site. The PDF provides response boxes/reasons.
- The mockup retains history after simulated identity loss. Confirm this choice
  against the ideal source if revising identity/data recovery.
- Browser Back/Escape are handled, but exhaustive Android-system/back/background
  semantics have not been demonstrated by this website.

The next action should follow the user's next design/reviewer request. Keep
these limits visible rather than using this handoff as authority to reduce scope.

## Verification performed before transfer

The corrected website had a passing local browser smoke run for onboarding,
QR trust, PTT to editable draft, queued/relayed/delivered messages, receive-side
speech failure, requester/responder/relayed SOS, identity loss, and mobile
horizontal layout. Desktop and phone-sized screenshots were inspected.
`node --check` passed. This was browser simulation evidence, not native/radio
acceptance and not complete ideal-product coverage.

The current guide was rendered and inspected across six A4 pages. PDF checks
found 74 fields and 74 widgets, with no missing normal appearances. Assumptions
are extracted directly from the site's HTML when regenerating it. The guide's
11 tasks were checked against the site's handlers and labels.

For a new device, use the local-server commands in `README.md` and walk through
the current guide. In particular check draft cancellation, trusted recipient
gating, unread behavior, delivery explanations, and direct/relayed trust gates.
Reset affects data in the current browser origin only.

## Hosted website

- URL: https://itantra-app-flow-review.kotesarajveer.chatgpt.site/
- Existing project ID: `appgprj_6ab6a676630081918ffc79a6017883e1`
- Current transferred cloud version: 2; deployment succeeded.
- Access was read as public on 2026-09-26; preserve the live access setting
  unless the user asks to change it.

GitHub is now the transferable UI/UX source location. Committing/pushing here
does not automatically update the separately hosted Site. Future publishing
must use the existing project, sync/push the exact intended source through the
Sites workflow, save a version, and verify deployment. Preserve the project ID;
never create a new Site just because this clone has no local Sites Git metadata.
Authentication is account/tool based and is not included in this package.

On this Windows machine the Sites helper successfully saved/pushed source but
its bash-based archive packaging failed because Windows bash did not understand
the Windows script path. Packaging was completed with the plugin's Node
`prepare-site-build.cjs` and Windows `tar.exe`, then the exact saved source
version was published. Temporary Git safe-directory trust was passed per-command;
no persistent global Git trust change was made. Another device should use its
available supported Sites workflow rather than copy machine-specific paths.
