# iTantra — Complete UI/UX Design Handoff v1
## Ideal-Product Mockup Brief + Exhaustive State / Edge-Case Contract

**Audience:** UI/UX designers, product designers, design-focused coding agents, prototyping agents, and reviewers.

**Purpose:** Design the complete ideal iTantra experience from first launch through trusted messaging, emergency networking, SOS, identity management, delayed delivery, failure recovery, and long-lived offline use.

---

# 0. READ THIS FIRST

This document is **not** a report of what is currently implemented.

Do not ask:
- Which feature is already coded?
- Which branch contains it?
- Which feature is physically tested?
- Which feature is partial?
- Which feature is “future”?

For this mockup exercise, assume the **complete intended iTantra product exists and works according to the product rules in this document**.

Your job is to design a coherent product around that complete capability set.

You are free to redesign:
- navigation,
- screen count,
- information architecture,
- layout,
- hierarchy,
- colors,
- typography,
- motion,
- card styles,
- interaction patterns,
- placement of controls,
- how states transition visually,
- how the app explains complicated networking concepts.

You are **not** free to accidentally remove, collapse, misrepresent, or design out any capability, trust distinction, state, or failure case listed here.

The single worst design failure would be:

> A beautiful mockup that works for the obvious happy path but has nowhere coherent for an important iTantra capability or edge case.

This document exists to prevent that.

---

# 1. PRODUCT IN ONE SENTENCE

iTantra is an **offline-first, multilingual, voice-first communication tool for infrastructure outages**, combining:

1. secure messages to already-trusted people, and
2. SOS communication with previously unknown nearby or relayed responders.

The intended product feeling is:

> **A modern push-to-talk radio with a written log: calm, direct, dependable, understandable under stress.**

It should not feel like:
- a Bluetooth settings utility,
- a cryptography dashboard,
- a network-engineering console,
- a generic WhatsApp clone,
- a disaster-themed sci-fi interface,
- a debugging tool.

---

# 2. THE TWO USER-FACING COMMUNICATION MODES

iTantra has two fundamentally different user intents.

## 2.1 Trusted Person

Mental model:

> “I know who I want to message.”

Example:

> Send Rahul: “I am safe. Meet me at the shelter.”

Rahul is already a trusted iTantra contact.

Important product truths:
- Rahul does **not** need to be nearby.
- The message may wait on the sender’s phone.
- It may later move through one or more stranger relay phones.
- Relays may carry it without reading it.
- Sender and recipient may never be in radio range of each other.
- “Another phone accepted a copy” does **not** mean Rahul received it.
- **Delivered** is reserved for recipient-confirmed delivery.

## 2.2 SOS

Mental model:

> “I need a human who can respond.”

There is no preselected trusted person.

Important product truths:
- SOS does not require a pre-existing contact relationship.
- It does not require Bluetooth pairing/bonding.
- Nearby participating phones may receive the request.
- If nobody nearby responds, the request may widen / relay farther.
- An encrypted session with a stranger does **not** automatically prove who the stranger is.
- The UI must communicate trust level explicitly.

These two modes may share visual language, components, speech tools, logs, etc., but the designer must never blur their **trust semantics**.

---

# 3. DESIGN DNA TO PRESERVE

The stable product lineage established several strong interaction principles. Preserve the principles, not necessarily the old pixels.

## 3.1 Voice first

Voice is the hero interaction.

The product should make speaking feel:
- immediate,
- obvious,
- safe,
- fast under stress.

The classic interaction is:

```text
hold / speak
    ↓
speech recognition
    ↓
editable draft
    ↓
explicit Send
```

## 3.2 Never send voice blindly

Speech recognition output must be reviewable.

The user must be able to:
- see what the app heard,
- edit it,
- correct names or mistakes,
- change recipient where appropriate,
- confirm the language context,
- explicitly send.

No accidental auto-send because the user stopped speaking.

## 3.3 Multiple input methods converge

The product supports:
- Push-to-Talk,
- Hands-free capture,
- typed text.

They should converge into the same conceptual message-composition system rather than becoming three unrelated mini-products.

## 3.4 One coherent message history

The user should not need to remember which subsystem produced a message.

Voice, hands-free, and typed messages should resolve into the same durable communication history.

The stable product philosophy treated history as a **radio log**, not chat-bubble theater.

You may rethink its visual representation, but preserve:
- clear incoming vs outgoing distinction,
- timestamps,
- recipient / sender identity where appropriate,
- message state,
- unread state,
- message detail,
- text durability.

## 3.5 State should be understandable in words

Do not rely on color, animation, tiny icons, or “technical vibes” to carry meaning.

Important states should be plainly understandable:
- Recording
- Transcribing
- Queued
- Relayed
- Delivered
- Expired
- Searching for responders
- Encrypted connection — identity not verified
- Nearby device verified
- Bluetooth unavailable
- Permission required
- etc.

## 3.6 Never lose the user’s words

If sending fails:
- preserve the draft.

If TTS fails:
- preserve the received text.

If the user tries to leave with a non-empty draft:
- provide a clear safe path.

If networking disappears:
- do not discard the message.

## 3.7 Hide complexity until it matters

The user should not have to operate:
- GATT,
- Spray-and-Wait,
- copy tokens,
- inventory sync,
- destination tags,
- Noise,
- relay tables,
- hop counters.

But when a technical condition changes the **meaning of the user’s state**, surface that meaning.

Example:
- do not show “2 relay tokens remaining”;
- do show “Relayed — another phone is carrying the encrypted message.”

## 3.8 Sparse field tool, not dashboard

Favor:
- large touch targets,
- clear hierarchy,
- low visual clutter,
- calm motion,
- high contrast,
- readable typography,
- quick recognition under stress.

Avoid cramming every capability onto Main.

## 3.9 Language belongs to the message

The communication language is a per-message concept.

A user may send one message in Hindi and another in Kannada.

The receiver should hear a received message using the language attached to that message.

## 3.10 Temporary conditions are usually states, not destinations

Recording, transcribing, connecting, sending, retrying, waiting, and error recovery should not automatically become separate full screens unless that truly improves the design.

Do not proliferate screens simply because engineering has multiple internal states.

---

# 4. REQUIRED LANGUAGE UNIVERSE

The complete product must account for all ten required languages:

- English
- Hindi
- Gujarati
- Marathi
- Kannada
- Malayalam
- Tamil
- Telugu
- Odia
- Bengali

The mockup must not structurally assume:
- only two languages,
- only eight languages,
- English as a permanent default,
- one language for the whole app session.

Design language selection so that ten options remain usable under stress.

Consider:
- full language names,
- native-script labels where useful,
- recently used languages,
- preserving a draft’s selected language,
- preventing accidental language changes while recording/transcribing,
- clear relationship between chosen language, STT, message metadata, and receive-side TTS.

Do not overcomplicate this into a language-settings dashboard unless needed.

---

# 5. COMPLETE PRODUCT LIFECYCLE

The design must work from a completely fresh install.

Do **not** assume:
- identity already exists,
- permissions are granted,
- Bluetooth is enabled,
- emergency operation is active,
- trusted contacts already exist,
- a recipient is nearby,
- a user knows cryptography.

The product lifecycle includes:

```text
install
↓
first launch / readiness
↓
local iTantra identity exists
↓
permissions / radio readiness
↓
trusted contact creation (optional but needed for Trusted Person mode)
↓
daily communication
↓
background / emergency participation
↓
SOS use when needed
↓
long-running queued / relayed messages
↓
process death / restart / reboot
↓
identity loss / reinstall situations
```

Every stage must have a coherent user experience.

---

# 6. FIRST LAUNCH / READINESS

The first-launch experience must account for at least:

## 6.1 Local identity

Each installation has its own cryptographic identity.

The user should not need to understand:
- Ed25519,
- X25519,
- key derivation,
- node IDs.

But the product should be able to explain, in human terms:

> This installation has an iTantra identity used to recognize trusted contacts and protect messages.

If the design exposes an identity fingerprint, it should explain what the fingerprint is for without implying magical guarantees.

## 6.2 Permissions

The app may require appropriate Android permissions for:
- Bluetooth scanning,
- Bluetooth advertising,
- Bluetooth connections,
- foreground connected-device operation,
- notifications,
- related legacy permission requirements on older Android versions.

Permission UX must account for:
- first ask,
- deny,
- deny again,
- “don’t ask again” / settings redirect,
- partial permission state,
- permissions revoked later,
- permissions granted while app is open,
- OS-version differences.

Never let permission denial look like:
- mysterious endless loading,
- “no people nearby,”
- silent network failure.

The UI should explain what capability is blocked and what the user can do.

## 6.3 Bluetooth / local radio readiness

Account for:
- Bluetooth off,
- Bluetooth turning on,
- user refuses to enable it,
- scanning unavailable,
- advertising unavailable,
- temporary radio failure.

Do not make “Bluetooth unavailable” look like “no iTantra users exist nearby.”

## 6.4 Notification / foreground operation readiness

Emergency/background networking may require visible foreground operation.

The app must have a coherent explanation of:
- why ongoing operation is visible,
- what “Emergency Mode active” means,
- what happens if Android/OEM restrictions prevent background operation.

Never claim background participation is active when the OS has stopped it.

---

# 7. IDENTITY VS CONTACT VS NEARBY PHONE

This distinction is absolutely non-negotiable.

The product has three different concepts:

## 7.1 “Me”

My local iTantra installation identity.

## 7.2 Trusted person

Example: Rahul.

Rahul became trusted through explicit identity exchange and confirmation.

A trusted contact is **not** merely:
- a Bluetooth device name,
- an Android device name,
- a phone number,
- a MAC address,
- a discovered radio peer,
- a display-name match.

## 7.3 Nearby networking peer

A nearby iTantra phone may simply be:
- a relay,
- an SOS participant,
- a temporary local network peer.

The user may never know the human identity behind it.

The design must not collapse all three into one generic “Devices” concept.

---

# 8. TRUSTED CONTACT CREATION — QR FLOW

The complete mockup must account for trusted-contact creation.

Conceptual flow:

```text
Rahul opens “My iTantra identity”
↓
Rahul shows QR
↓
Vivek scans it
↓
identity data is validated
↓
Vivek sees Rahul’s presented identity / fingerprint
↓
Vivek explicitly confirms
↓
Rahul becomes a trusted contact
```

Required design considerations:

## 8.1 Show-my-QR state

Account for:
- clear explanation of what is being shared,
- display name as a human hint,
- QR large enough to scan,
- fingerprint / verification information where appropriate,
- privacy around screen sharing / screenshots if the team chooses to explain it.

## 8.2 Scan state

Account for:
- camera permission denied,
- camera unavailable,
- QR not recognized,
- malformed QR,
- wrong QR type,
- old / incompatible format,
- corrupted payload,
- identity validation failure,
- duplicate existing contact,
- same identity under another local name,
- scanning one’s own identity,
- cancel scan.

## 8.3 Human confirmation

The QR’s display name is not proof of identity by itself.

The user must explicitly confirm the contact.

The design should give the human enough information to make that confirmation without forcing them to understand cryptographic internals.

## 8.4 Contact naming

The user-facing local name may be editable without changing the underlying cryptographic identity.

The design must therefore account for:
- rename contact,
- same underlying identity with renamed local label,
- prevent UI from implying name text itself is the trusted credential.

## 8.5 Duplicate / conflict cases

Account for:
- contact already trusted,
- scanned identity matches existing contact,
- same display name but different cryptographic identity,
- renamed contact later,
- identity changed after reinstall / reset.

Never silently merge two different cryptographic identities just because the display names match.

---

# 9. TRUSTED CONTACT MANAGEMENT

The product must provide a coherent home for trusted-contact concepts.

Potential information the design may need to represent:
- local contact name,
- “Trusted iTantra contact” status,
- fingerprint / identity details,
- how trust was established (QR),
- creation / added time if useful,
- rename,
- remove / forget contact,
- re-exchange identity when needed.

Designers may choose the information architecture, but the product must not become dependent on Bluetooth pairing as the contact system.

If a selected “recipient” cannot resolve to exactly one trusted contact, the product must refuse to guess.

Human-facing meaning:

> “I can’t securely identify this recipient.”

---

# 10. MESSAGE COMPOSITION — TRUSTED PERSON MODE

The complete composition model must accommodate:

- recipient,
- input method,
- language,
- message text,
- urgency / priority where the product exposes it,
- draft state,
- explicit Send.

## 10.1 Recipient

Trusted Person messages are addressed to one previously trusted person.

The design must allow selection of the intended trusted recipient.

Do not base this on:
- whoever is currently connected,
- nearest phone,
- discovered Bluetooth device,
- device name.

The recipient may be completely absent from local radio range.

## 10.2 Push-to-Talk

Required conceptual behavior:
- speech capture starts immediately on deliberate PTT interaction,
- clear recording state,
- clear release/finish behavior,
- STT runs locally,
- transcript becomes an editable draft,
- nothing sends automatically.

Account for:
- microphone permission denied,
- microphone unavailable,
- recording interrupted,
- user releases immediately,
- empty audio,
- STT produces empty output,
- STT errors,
- STT takes time,
- user backs out during transcription,
- selected language changes / should be locked during active capture,
- accidental touch cancellation,
- app backgrounds during recording,
- incoming event while recording.

## 10.3 Hands-free capture

Hands-free is a first-class voice mode, but the UX must still preserve:
- clear start,
- clear “listening” state,
- clear finish / Done,
- transcript review,
- explicit Send.

The designer may choose whether the ideal hands-free mode uses:
- VAD,
- pause detection,
- continuous interaction,
- explicit Done,
- another coherent mechanism.

But do not create ambiguity about whether the app is currently listening.

Account for:
- user leaves mode,
- microphone permission lost,
- no speech,
- very long speech,
- interruption,
- accidental backgrounding,
- transition into draft.

## 10.4 Typed text

Text is always reachable as a fallback.

Typed text must still use the same conceptual recipient / language / send semantics as voice-created drafts.

Do not force typed text to English.

## 10.5 Draft preservation

Account for:
- back navigation with non-empty draft,
- send failure,
- network unavailable,
- recipient state unchanged,
- app temporarily backgrounded,
- orientation / recreation where relevant,
- accidental navigation,
- user intentionally discards.

No silent draft loss.

---

# 11. MESSAGE SIZE / CONTENT SCOPE

Trusted Person v1 is text-focused.

Speech is converted to text locally before network transmission.

The critical product path does not assume transfer of:
- raw audio,
- images,
- video,
- arbitrary files.

Do not visually promise rich-media messaging unless explicitly added as a separate design extension.

The architecture uses a strict configurable text payload ceiling; the UI should gracefully handle:
- message too long,
- over-limit paste,
- character / byte limit explanation if needed,
- truncation must never happen silently.

---

# 12. URGENT / PRIORITY MESSAGE SEMANTICS

The network model supports normal vs urgent priority behavior.

The visual design must leave room for priority without turning every message into an alarm.

Account for:
- normal message,
- urgent message,
- clear but not deceptive urgency indicator,
- different expiry / routing treatment may exist underneath,
- urgent must not imply guaranteed delivery,
- urgent must not imply official emergency-service handling,
- relay phones still must not expose plaintext simply because a message is urgent.

If alert-style receive behavior exists, ensure the user can distinguish:
- “urgent message from trusted contact”
from
- “SOS incident.”

These are different concepts.

---

# 13. MESSAGE PERSISTENCE BEFORE TRANSMISSION

A Trusted Person message is persisted locally before transmission attempts.

The UX implication:

The user can press Send even when Rahul is not reachable.

The product should not require a live recipient connection.

The user-facing model should feel like:

> “Your message is safely queued and iTantra will attempt to move it when opportunities appear.”

Not:

> “Could not connect to Rahul, therefore you cannot send.”

---

# 14. THE TRUSTED MESSAGE LIFECYCLE

The sender-facing lifecycle includes:

## 14.1 QUEUED

Meaning:

> Stored locally. No relay has yet confirmed carrying a copy.

Possible explanation:

> “Queued — stored on this phone, waiting for a forwarding opportunity.”

## 14.2 RELAYED

Meaning:

> At least one relay accepted and persisted an encrypted copy.

Correct explanation:

> “Relayed — another phone is carrying the encrypted message. Rahul has not confirmed receiving it yet.”

Do **not** translate this into:
- Sent
- Delivered
- Received by Rahul
- On Rahul’s phone

## 14.3 DELIVERED

Meaning:

> The intended recipient accepted the message and an authenticated delivery receipt returned.

Only then may the UI say Delivered.

## 14.4 EXPIRED

Meaning:

> The message’s allowed lifetime ended without confirmed delivery.

Expired must not be presented as:
- recipient rejected,
- recipient was offline,
- no route existed,
- network failed permanently.

The app usually does not know the reason.

## 14.5 UNKNOWN

Meaning:

> State continuity was lost or the app cannot safely assert the current delivery state.

Unknown must be treated honestly.

Do not cosmetically “round” it into Queued, Relayed, or Delivered.

---

# 15. MESSAGE STATE TRANSITION EDGE CASES

The mockup must account for non-happy paths.

Examples:

- Send → Queued for a long time.
- Queued → Relayed minutes/hours later.
- Relayed → Delivered much later.
- Queued → Expired.
- Relayed → Expired.
- Any uncertain continuity → Unknown.
- App restarts while message is Queued.
- App restarts while message is Relayed.
- Device reboots while bundle is aging.
- Delivery receipt arrives long after original send.
- Duplicate receipt arrives.
- Relay copy accepted but sender misses relay acknowledgement.
- Multiple routes carry duplicate copies.
- Recipient receives duplicate copies through different paths.
- Recipient must display/speak the message only once.
- A malicious/irrelevant peer falsely claims to be destination.
- That must not cause Delivered.
- Existing honest copy must not disappear solely because a stranger claims it is destination.
- Message expires before recipient encounter.
- Storage pressure removes relay data according to policy.
- Sender’s only remaining copy must not disappear merely because another relay once accepted it.

The UI does not need to expose protocol mechanics, but it must never promise something that these mechanics do not prove.

---

# 16. RECEIVING A TRUSTED MESSAGE

When the true recipient receives and authenticates a trusted message:

1. text is stored,
2. it appears in the message history,
3. unread state is created as appropriate,
4. it may be spoken using receive-side TTS,
5. delivery confirmation is generated underneath.

Critical UI rules:

- TTS failure must not erase or hide the text.
- Duplicate network arrival must not duplicate the user-visible message.
- Duplicate network arrival must not repeatedly speak the same message.
- Relay phones must not display or speak a message they are merely carrying.
- The sender identity shown to the recipient must correspond to the previously trusted contact identity, not an untrusted display-name string contained in the message.

---

# 17. RECEIVE-SIDE TTS

Received messages are meant to be heard as well as read.

The design must account for:
- automatic or user-controlled speech policy,
- current message language,
- playback state,
- TTS unavailable / failed,
- user stops playback,
- replay message aloud,
- phone muted / audio route issues,
- user is already listening to another message,
- multiple messages arrive quickly,
- urgent message behavior,
- privacy when speaking aloud in public.

The durable truth is the received text.

TTS is an output layer, not the only representation of the message.

---

# 18. MESSAGE HISTORY / LOG

The complete product needs a durable history.

The design may use:
- radio log,
- chronological feed,
- grouped threads,
- another coherent structure.

But it must preserve the information needed to understand communication state.

At minimum be able to represent:
- incoming vs outgoing,
- trusted person involved,
- message text / preview,
- language where useful,
- timestamp,
- unread/read state,
- delivery state for outgoing messages,
- urgency if relevant,
- detail view / expanded information,
- TTS replay.

## 18.1 Unread semantics

Opening the overall history should not necessarily mark every incoming message read.

The stable philosophy marked an individual message read when that message was actually opened.

Whatever design you choose, define unread behavior explicitly.

## 18.2 Relay-only data

Messages carried only as relay ciphertext must **not** appear in the user’s human message history.

The user is not a participant in those conversations.

---

# 19. EMERGENCY MODE

Emergency Mode is a user-started operating state that enables active local participation in the outage network.

Its responsibilities underneath may include:
- BLE advertising,
- scanning,
- peer discovery,
- link management,
- relay opportunities,
- SOS discovery,
- ongoing network participation.

The user does not need to see those mechanics.

The UI **does** need to account for:

## 19.1 Start

The user intentionally enables Emergency Mode.

Explain in human terms what it does.

## 19.2 Active

Clearly communicate:
- active participation,
- background operation,
- any responder availability setting,
- any system limitation that currently prevents reliable participation.

## 19.3 Stop

The user can stop it deliberately.

Account for:
- active transfers / queued messages,
- active SOS session,
- responder participation,
- warning if stopping will interrupt an active emergency interaction.

## 19.4 Foreground system notification

Emergency operation must have an appropriate visible system-level presence when required.

The design should specify the information hierarchy for the notification, including:
- Emergency Mode active,
- SOS state if active,
- meaningful actions where safe,
- no fake “running” state if Android has killed the service.

## 19.5 Background restrictions

Account for:
- OEM battery optimization,
- service killed,
- Bluetooth revoked,
- scan restrictions,
- notification permission denied,
- phone restart.

If the product cannot actually participate in the background at that moment, say so.

---

# 20. “AVAILABLE TO HELP” RESPONDER PARTICIPATION

A phone in Emergency Mode can expose an explicit user setting conceptually like:

> Available to help nearby users

This must be opt-in / understandable.

Account for:
- on,
- off,
- Emergency Mode disabled,
- notification of incoming SOS offer,
- user busy,
- user declines,
- user ignores,
- multiple simultaneous incoming SOS requests,
- active SOS session already in progress,
- ability to stop availability.

Do not imply that enabling this makes the user:
- a medic,
- police,
- an official responder,
- trustworthy by identity,
- physically closest.

It only means they are willing to receive/respond to nearby SOS requests.

---

# 21. SOS START

SOS is explicitly activated by the person requesting help.

The user’s mental model:

> “Find me somebody who can answer me.”

The SOS creation experience must account for:

- explicit activation,
- category,
- language,
- optional brief text / speech content according to product design,
- clear indication that the app is searching,
- ability to cancel.

Core request categories in the architecture include examples such as:
- Medical
- Trapped
- Unsafe
- Communication
- Other

Do not disclose unnecessary personal data before a responder accepts.

---

# 22. SOS PRE-ACCEPT PRIVACY

Before acceptance, only minimal information should be exposed.

The product may disclose:
- request category,
- language,
- request age,
- rough radio proximity bucket,
- credential-presence flag only if that extension exists.

The product must **not automatically reveal**:
- person’s name,
- phone number,
- trusted-contact identity,
- exact GPS location,
- medical history,
- microphone stream,
- camera,
- contact list.

Design the SOS offer so it is useful enough to decide whether to respond without exposing the requester unnecessarily.

---

# 23. ROUGH PROXIMITY — NEVER FAKE DISTANCE

Nearby candidate quality may be based on radio observations.

The UI may represent coarse concepts such as:
- Strong
- Medium
- Weak

It must **not** claim:
- “2.4 metres away”
- “nearest person”
- exact physical distance
- guaranteed physical ordering

Radio strength is not a ruler.

This is a hard truthfulness rule.

---

# 24. RESPONDER RECEIVES AN SOS OFFER

A responder may see something conceptually like:

> Someone nearby is requesting help  
> Category: Medical  
> Language: Kannada  
> Connection: Encrypted  
> Identity: Not verified

Actions:
- Accept
- Decline

The final design can be different, but all essential trust information must remain clear.

Account for:
- incoming offer while app open,
- incoming offer while app backgrounded,
- multiple offers,
- offer expires before response,
- requester cancels,
- responder accepts,
- responder declines,
- responder ignores,
- network link disappears during decision,
- another responder wins first.

---

# 25. SOS TRUST STATES

This is one of the most important UI semantics in the entire product.

## 25.1 State 1 — Encrypted connection, identity not verified

Meaning:

> The communication channel is encrypted, but iTantra does not know who this human really is.

Required wording must communicate both halves.

Do not collapse this to:
- Secure person
- Trusted helper
- Verified responder
- Safe user

## 25.2 State 2 — Nearby device verified

The users may perform a physical/session verification such as:
- matching a short authentication string,
- scanning a session QR.

Meaning:

> The encrypted session has been matched to the nearby device/person the user is physically interacting with.

It does **not** mean:
- medic,
- police,
- official rescue worker,
- morally trustworthy person.

## 25.3 Optional extension — Responder credential verified

The wider architecture allows a future/optional role-credential concept.

If the mockup includes it, it must be treated as a distinct trust layer.

Example:

> Medic credential verified  
> Status information last updated: [time]

If credential status may be stale offline, the UI must show that staleness.

Never visually merge:
- encrypted,
- nearby-device verified,
- professional-role credential verified.

They prove different things.

---

# 26. SHORT AUTHENTICATION STRING / SESSION QR

For physical verification, the product must be able to represent a short-authentication check.

Potential UX:
- both devices show the same short code / words,
- users compare them,
- or one user scans a session QR.

Account for:
- codes match,
- codes do not match,
- user cannot compare,
- scan fails,
- session changes/reconnects,
- user skips verification,
- user later verifies,
- verification state must never survive into an unrelated new session incorrectly.

Do not mark identity “verified” merely because an encrypted connection exists.

---

# 27. SOS ACCEPTANCE RACE — MULTIPLE RESPONDERS

Two responders may accept almost simultaneously.

The design must account for this.

Product rule:

> First confirmed acceptance becomes the active responder; later accepts are suppressed or informed appropriately.

UI states needed conceptually:

Requester:
- searching,
- one responder confirmed,
- late secondary response received/suppressed,
- active session.

Responder A:
- accepted and connected.

Responder B:
- attempted accept but another responder already connected,
- must receive a clear non-error explanation.

Do not leave the requester in two contradictory “active responder” sessions unless the product intentionally adds multi-responder support.

---

# 28. DIRECT SOS SEARCH / EXPANSION

The system initially favors nearby direct contact.

If no one responds, the search widens.

The UI must communicate progress without pretending it knows more than it does.

Possible human-facing progression:

```text
Looking for nearby people…
↓
Still searching…
↓
Expanding the request…
↓
Trying farther relays…
```

Do not expose protocol wave numbers unless helpful.

Do not promise:
- “someone will answer,”
- “nearest responder contacted,”
- exact physical radius.

---

# 29. RELAYED SOS

If direct nearby response fails, an SOS request may travel across relays.

The design must distinguish:

## 29.1 Nearby live connection

The responder is directly reachable nearby.

## 29.2 Relayed emergency connection

The interaction is moving through intermediary phones.

This may:
- be slower,
- be intermittent,
- not feel live.

The UI must explicitly communicate this difference.

Do not style a relayed, opportunistic interaction like a guaranteed low-latency phone call.

---

# 30. ACTIVE SOS CONVERSATION

Once a responder accepts, the product provides an encrypted text communication session.

Existing speech tools can sit above it:

```text
speech
↓
on-device STT
↓
text
↓
encrypted SOS session
↓
text
↓
on-device TTS
```

Design requirements:
- text remains canonical/durable,
- voice can be used for input,
- TTS can read responses,
- trust state remains visible/accessible,
- connection quality / relayed nature can be communicated,
- user can end/cancel safely,
- the product must not silently upgrade identity trust.

Account for:
- connection temporarily lost,
- reconnect,
- responder disappears,
- requester disappears,
- idle timeout,
- delayed relayed responses,
- TTS failure,
- STT failure,
- session cancelled,
- session expired.

---

# 31. SOS CANCELLATION / END

An SOS may end because:
- requester cancels,
- responder accepts and search suppression occurs,
- active session ends,
- SOS expires,
- idle timeout,
- network disappears and session cannot continue.

The UI must clearly distinguish:
- search cancelled,
- request expired,
- responder declined,
- no response yet,
- active session ended.

Do not say “Nobody nearby” when all the app knows is “no one responded.”

---

# 32. EXPIRY AND TIME

Messages and SOS requests have lifetimes.

UX implications:
- expiry is a protocol state, not a social interpretation,
- wall-clock display time and protocol age are not the same thing,
- device clock changes should not produce absurd trust claims.

For users:
- human timestamps may still show normal local time,
- expiry wording should be understandable,
- “Expired” should not blame recipient or network.

---

# 33. OFFLINE MEANS NO INTERNET PATH — NOT “ALL RADIOS OFF”

Core communication must work without Internet.

Do not design copy that implies Bluetooth / local Wi-Fi radios are “Internet.”

Correct mental model:

> Internet unavailable; local device-to-device communication still works.

The UI may need to distinguish:
- Internet offline,
- local radio available,
- local radio unavailable,
- Emergency Mode active,
- no peers currently reachable.

---

# 34. PEER / RELAY INVISIBILITY

Most relay activity is infrastructure, not human conversation.

The normal user should not need to:
- select relays,
- approve every relay,
- know relay names,
- manage hop routes,
- choose “Phone B then Phone C.”

Relays may be arbitrary stranger phones.

The design may show simple network health/activity indicators if useful, but do not turn routing into a manual workflow.

---

# 35. PRIVACY OF RELAYED MESSAGES

A relay may carry encrypted message data but must not get the human conversation.

Therefore:
- no relay-only message should appear in the relay user’s message log,
- no relay-only message should be spoken by TTS,
- no plaintext preview,
- no sender/recipient contact name from that message,
- no responder conversation content.

If the product exposes relay activity, keep it aggregate / privacy-preserving.

---

# 36. MALICIOUS / UNCOOPERATIVE RELAYS

The system must assume relay phones may:
- drop bundles,
- delay them,
- disappear,
- duplicate ciphertext,
- lie,
- refuse deletion,
- falsely claim resource conditions.

UI implication:

Do not give users certainty the system cannot have.

Never display:
- “Guaranteed route”
- “Relay will deliver”
- “100% delivery”
- “All carriers trusted”

The correct product tone is confident about what is known, modest about what is not.

---

# 37. DUPLICATES

Network duplicates are expected.

The human experience should be deduplicated.

A recipient should not:
- see the same trusted message twice,
- hear TTS twice,
- receive repeated user notifications,
- have unread count inflate,
- see expiry reset.

Similarly, duplicate receipts should not create repeated “Delivered” events.

Mockups should account for stable user-visible state even when the network underneath retries.

---

# 38. STORAGE PRESSURE

Relay storage is finite.

The networking layer has an eviction policy that prioritizes:
- expired / cleaned data first,
- normal traffic before urgent,
- active SOS last.

The normal user should not manage individual encrypted relay bundles.

But the product may need coherent states for:
- storage critically low,
- relay participation limited,
- cannot persist new relay data,
- own message storage problem,
- database failure.

Never silently delete the sender’s only remaining copy because “another relay had it once.”

If the app cannot safely queue the user’s own message, say so clearly.

---

# 39. PROCESS DEATH / RESTART / SCREEN LOCK / REBOOT

The ideal product must account for real Android life.

Network state and queued relay data are expected to survive:
- app backgrounding,
- process death,
- screen lock,
- normal app restart,
- device reboot as far as architecture permits.

UX cases:
- app reopens with Queued messages still present,
- Relayed state preserved,
- active/expired objects reconcile,
- service restart state is clear,
- Emergency Mode may need reactivation or notification,
- any continuity uncertainty becomes Unknown rather than invented certainty.

Do not create a mockup whose state model only works while one Activity stays alive.

---

# 40. REINSTALL / RESTORE / IDENTITY LOSS

This edge case is easy to miss and must be designed.

The long-term local identity is tied to protected local key material.

A reinstall / restore scenario may lose the cryptographic identity.

Human consequence:

> Other people’s previously trusted record for “you” may no longer match this installation.

The UI must account for:
- identity lost,
- new identity generated,
- old trusted relationships no longer cryptographically valid,
- user must re-exchange QR identities with contacts,
- same display name does not magically restore trust.

Do not say:
- “Your account was restored”
if the cryptographic identity was not restored.

Potential human-facing explanation:

> “This installation has a new iTantra identity. Previously trusted contacts will need to scan your new QR again.”

---

# 41. CONTACT IDENTITY CHANGE

Related cases:
- Rahul reinstalls,
- Rahul’s identity changes,
- user scans Rahul again,
- display name is same but cryptographic identity differs.

The design must avoid silently replacing a trusted identity.

It should support a deliberate re-trust / re-exchange experience.

This is security-sensitive but should still be understandable to a non-technical user.

---

# 42. FAILURE-STATE BIBLE

The design must have a coherent response for all of the following.

Not every case needs a unique screen. Many should collapse into a small set of clear recovery patterns.

## 42.1 Device / permission failures

- Bluetooth disabled
- Bluetooth permission denied
- Bluetooth permission later revoked
- advertising unsupported
- scanner unavailable
- notification permission denied
- foreground-service restrictions
- microphone permission denied
- camera permission denied for QR scan

## 42.2 Connection failures

- peer connection timeout
- connection lost before transfer
- connection lost mid-frame
- peer disappears
- repeated connection churn
- no nearby peers
- responder disappears during SOS
- requester disappears during SOS

## 42.3 Secure-session failures

- secure handshake timeout
- malformed secure-session data
- replayed/stale session
- verification mismatch
- physical verification code mismatch

These should fail safely without confusing “encrypted” with “verified.”

## 42.4 Message-data failures

- malformed message data
- oversized payload
- corrupted ciphertext
- invalid signature
- wrong recipient
- decryption failure
- expired bundle
- duplicate bundle
- duplicate delivery receipt
- forged/invalid deletion signal

Most of these are internal and should not overwhelm the user.

User-facing copy should expose only what helps the user act.

## 42.5 Persistence failures

- database unavailable
- storage full
- process dies during operation
- reboot occurs
- continuity lost
- own message cannot be safely persisted

If safe persistence cannot be guaranteed, do not pretend the message is Queued.

## 42.6 Speech failures

- no speech detected
- STT failed
- STT empty
- STT produced questionable transcript
- TTS unavailable
- TTS failed
- audio output interrupted

Text must remain usable.

## 42.7 SOS failures

- no responder yet
- responder declines
- responder ignores
- request expires
- requester cancels
- multiple responders accept
- another responder wins first
- direct search fails and request widens
- relayed emergency connection becomes intermittent
- session times out
- trust verification fails or is skipped

## 42.8 Product safety invariant

No failure case should result in:
- app crash as the normal UX,
- accidental plaintext fallback,
- automatic trust escalation,
- false Delivered state,
- silent draft loss,
- silent deletion of the sender’s only copy.

---

# 43. ERROR-COPY PHILOSOPHY

Errors should be:
- specific enough to help,
- calm,
- human,
- honest,
- recoverable where possible.

Prefer:

> “Bluetooth is off. Turn it on to discover nearby iTantra phones.”

over:

> “GATT initialization error.”

Prefer:

> “Message is still queued on this phone.”

over:

> “Transfer failed.”

Prefer:

> “Encrypted connection — identity not verified.”

over:

> “Secure.”

The product should tell the user:
1. what happened,
2. what remains safe,
3. what they can do next.

---

# 44. SECURITY / TRUST WORDING RULES

These are hard product semantics.

## Allowed concepts

- Trusted iTantra contact
- Queued
- Relayed
- Delivered
- Expired
- Encrypted connection
- Identity not verified
- Nearby device verified
- Responder credential verified (only where that extension is genuinely represented)
- Strong / Medium / Weak radio proximity

## Forbidden shortcuts

Do not say:
- “Secure person” merely because encryption exists.
- “Trusted helper” for an unknown SOS responder.
- “Nearest person” based on RSSI.
- exact metre distance based on radio strength.
- “Delivered” after relay acceptance.
- “Sent to Rahul” if only a relay accepted it.
- “Guaranteed delivery.”
- “All relays trusted.”
- “Anonymous” in a global sense.
- “Untraceable.”
- “Sybil-proof.”
- “Bluetooth Mesh” unless that is literally what the product becomes.
- “Fully metadata private.”
- “Formally verified cryptography.”
- “Responder is a medic/police officer” without a real verified credential state.

Color must never be the only thing differentiating trust states.

---

# 45. MESSAGE PRIVACY — WHAT THE USER MAY ASSUME

For trusted messages, relays should not learn:
- plaintext,
- human sender name,
- human recipient name,
- phone numbers,
- exact location,
- contact list.

However, some relay-visible metadata exists conceptually, such as:
- opaque bundle identifier,
- ciphertext size,
- coarse priority,
- age/lifetime,
- routing state.

Therefore the UI / marketing layer must not imply:

> “Relays can see absolutely nothing.”

Correct high-level language:

> “Relay phones carry encrypted message data without access to the message text.”

---

# 46. CURRENTLY NEARBY VS EVENTUALLY REACHABLE

A huge design trap:

Do not equate “not nearby” with “cannot message.”

Trusted Person mode specifically supports:
- recipient absent now,
- message queued,
- encounters happen later,
- relay carries,
- recipient receives later.

Therefore recipient selection should not be filtered to “currently nearby people.”

A trusted contact can remain addressable even when completely offline / absent.

---

# 47. DO NOT DESIGN MANUAL ROUTING

The user should not need to decide:

> “Send via Priya’s phone, then Utkarsh’s phone.”

Routing is automatic infrastructure.

User intent is:
- who,
- what,
- language,
- urgency if applicable.

The system handles:
- peer encounters,
- relay copy decisions,
- later forwarding.

---

# 48. NORMAL MESSAGE VS SOS — DO NOT MIX

Normal Trusted Person:
- known trusted identity,
- recipient-specific encryption,
- may wait for later delivery.

SOS:
- no intended identity initially,
- asks available humans,
- begins encrypted but identity-unverified,
- staged search / expansion,
- active responder session.

Do not expose a generic “broadcast” button that ambiguously mixes these.

If Rahul is not already trusted, “search for Rahul by name offline” is not part of the product.

The fallback is SOS, not guessing an identity.

---

# 49. MAIN / HOME — WHAT IT MUST ENABLE, NOT HOW IT MUST LOOK

This document deliberately does not prescribe a Main screen.

Whatever home/navigation system you design, a user must be able to discover and reach:

- voice-first trusted messaging,
- hands-free messaging,
- typed messaging,
- trusted contacts / identity exchange,
- message history,
- language choice in the right context,
- Emergency Mode,
- obvious SOS entry,
- important network/readiness state,
- settings required for participation/recovery.

Do **not** solve this by placing every control on one scrollable screen.

Information architecture is yours to design.

---

# 50. DESIGNING THE “FIRST THREE SECONDS”

When the app opens during an outage, the user should very quickly understand:
- what primary action they can take,
- whether the app is ready to communicate,
- how to reach SOS,
- whether there is a blocking problem requiring attention.

Avoid opening onto:
- raw device lists,
- technical connection logs,
- model diagnostics,
- developer tooling.

---

# 51. DEBUG / ENGINEERING TOOLS ARE NOT PRODUCT UI

Do not promote engineering controls into the main consumer experience.

Examples of things that belong outside normal product UX:
- raw Bluetooth connect buttons,
- Net Lab,
- protocol frame counters,
- model-test panels,
- “Play last recording” debug controls,
- legacy transport switches,
- GATT status codes,
- copy-token counts,
- raw bundle IDs,
- Noise handshake details.

A separate diagnostic/developer surface may exist, but it must not define the core UX.

---

# 52. ACCESSIBILITY / STRESS DESIGN

The target situation may involve:
- panic,
- low attention,
- shaky hands,
- sunlight,
- darkness,
- noise,
- poor connectivity,
- low battery,
- unfamiliar users,
- multilingual households.

Design accordingly.

At minimum:
- large targets,
- strong text contrast,
- meaningful labels,
- no color-only status,
- readable type,
- avoid precision gestures for critical actions,
- destructive actions require clear intent,
- SOS must not hide behind obscure navigation,
- recording state must be unmistakable,
- trust state must be understandable without security expertise,
- error copy should be short and actionable,
- important states should survive localization.

---

# 53. HAPTICS / SOUND / MOTION

Use sensory feedback to reinforce state, not to invent state.

Good use:
- PTT press/release confirmation,
- SOS activation confirmation,
- incoming emergency offer,
- delivery confirmation,
- verification mismatch warning.

Avoid:
- decorative continuous animation that looks like live signal data when it is not,
- waveform visuals that imply measured audio if they are merely decorative,
- green glow that implies identity trust,
- frantic disaster aesthetics.

Motion should feel calm and functional.

---

# 54. DESTRUCTIVE / HIGH-STAKES ACTIONS

Account for confirmation or strong affordance where appropriate:

- discard non-empty draft,
- forget trusted contact,
- replace/re-trust changed identity,
- stop active Emergency Mode,
- cancel active SOS,
- end active responder session,
- wipe/reset iTantra identity,
- actions that will force contacts to re-exchange QR identity.

Do not over-confirm routine actions like ordinary Send.

---

# 55. NOTIFICATION MODEL

The design system should define notification behavior for:

- trusted message received,
- urgent trusted message,
- message Delivered,
- incoming SOS offer,
- SOS accepted,
- active SOS status,
- Emergency Mode foreground operation,
- background restriction / network participation stopped,
- identity/recovery warning where necessary.

Notification content must respect privacy:
- consider lock-screen exposure,
- avoid unnecessary plaintext if privacy mode is enabled,
- do not expose relay-carried third-party messages.

---

# 56. READ / UNREAD VS DELIVERY

Do not conflate:

- recipient network delivery,
- recipient opened/read the human message.

The core protocol guarantees a delivery receipt when the recipient accepts the message, not necessarily a social “read receipt” in the WhatsApp sense.

If the product later adds read receipts, they must be a distinct concept.

For now, do not style Delivered as “Seen.”

---

# 57. CONTACT TRUST VS SESSION TRUST

Trusted Person trust is long-lived contact identity established through QR exchange.

SOS trust begins as:
- encrypted session,
- identity unverified.

These are different trust systems.

Do not let visual components casually reuse “Trusted” badges between them without precise semantics.

---

# 58. RELAY ACTIVITY / NETWORK HEALTH

If designers want to visualize outage-network participation, it must be truthful.

Possible safe concepts:
- Emergency Mode active,
- nearby iTantra activity detected,
- relaying enabled,
- active local links count,
- recent relay activity,
- queued outgoing messages.

Risky / misleading concepts:
- global network map,
- exact mesh topology,
- precise human locations,
- predicted route to Rahul,
- “Rahul is three hops away” unless the protocol truly knows it,
- guaranteed coverage radius.

---

# 59. NO INTERNET STATE

The app should be comfortable showing:

> Internet unavailable

without making the whole product look broken.

Because offline operation is the point.

Potential readiness hierarchy:

```text
Internet: unavailable
Local iTantra radio: ready
Emergency Mode: active
Trusted messages: can queue / relay
SOS: available
```

The exact design is yours.

---

# 60. BATTERY / THERMAL / LONG-RUN CONSIDERATIONS

Because Emergency Mode may remain active for long periods, the UI should leave room for meaningful operating warnings such as:
- battery critically low,
- battery optimization is limiting background activity,
- device is too constrained for reliable background participation,
- Emergency Mode stopped by system/user.

Do not turn this into a performance dashboard.

Only surface what changes user action or trust in current operation.

---

# 61. CONFIGURABLE NETWORK PARAMETERS ARE NOT USER SETTINGS

Underlying values such as:
- relay copy budget,
- message lifetime,
- SOS expansion timing,
- hop guard,
- retry backoff,

are engineering/protocol parameters.

Do not expose them as casual consumer sliders.

If the product exposes user-facing choices like Normal vs Urgent, map those to sensible internal policies.

---

# 62. SECURITY FAILURE UX

Most security failures should not become dramatic hacker warnings.

If an incoming bundle:
- fails signature,
- is not for this recipient,
- fails decryption,
- is malformed,

it may simply be dropped internally.

Only surface a warning when:
- user action is required,
- a trusted-contact relationship may have changed,
- an active session verification fails,
- a persistent problem affects usability.

Avoid training users to ignore constant security alerts.

---

# 63. RATE LIMITING / ABUSE

The network contains resource limits.

Possible UX implications:
- temporarily unable to accept more incoming requests,
- too many SOS attempts,
- too many connection attempts,
- device under heavy load.

Do not frame rate limiting as identity proof.

A newly created identity is not automatically trustworthy just because the system permits it.

---

# 64. RESPONDER ROLE CREDENTIAL EXTENSION

If the designers choose to visualize the optional responder-credential direction, build it as an additional trust layer, not a replacement for normal SOS.

Required semantics:
- ordinary humans can respond without credentials,
- role credential may assert a role,
- offline status can be stale,
- timestamp / freshness matters,
- credential does not automatically reveal current availability or location,
- credential should not override the underlying encrypted/unverified/nearby-verified session semantics.

It is acceptable to design a placeholder extension path so later product work does not require redesigning the whole SOS trust model.

---

# 65. FUTURE TRANSPORTS MUST NOT BREAK THE UI MODEL

The underlying transport abstraction allows future local transports in addition to BLE.

The UI should not hard-code user concepts around:
- Bluetooth MAC addresses,
- GATT,
- RFCOMM.

User-facing concepts should remain:
- nearby iTantra connectivity,
- trusted person,
- queued/relayed/delivered,
- Emergency Mode,
- SOS.

This lets future Wi-Fi-based local transports fit underneath without redesigning the product semantics.

---

# 66. FIRST-LAUNCH EDGE-CASE CHECKLIST

The mockup set must prove the design can handle:

- fresh install,
- identity created,
- permissions all granted,
- one permission denied,
- Bluetooth off,
- notifications denied,
- no trusted contacts,
- “add trusted contact” path,
- show-my-QR path,
- scan path,
- malformed QR,
- duplicate QR,
- conflicting same-name identity,
- explicit trust confirmation,
- contact added,
- user returns to app with one/many contacts,
- reinstall identity-loss warning,
- re-exchange required.

---

# 67. TRUSTED-MESSAGE EDGE-CASE CHECKLIST

Mockups / state definitions must cover:

- choose trusted recipient,
- recipient absent,
- PTT recording,
- transcription,
- editable transcript,
- text compose,
- hands-free compose,
- per-message language,
- normal priority,
- urgent priority if exposed,
- send,
- Queued,
- Relayed,
- Delivered,
- Expired,
- Unknown,
- send/persist failure,
- draft preserved,
- message too long,
- duplicate network arrival,
- incoming unread,
- open message,
- TTS playback,
- TTS fail,
- relay phone carries message invisibly,
- recipient receives after long delay,
- app restart while queued,
- device reboot,
- no Internet,
- no current peers.

---

# 68. SOS EDGE-CASE CHECKLIST

Mockups / state definitions must cover:

- start SOS,
- choose/request category,
- language,
- searching nearby,
- no candidates yet,
- responder offer,
- accept,
- decline,
- ignore / timeout,
- multiple responder race,
- encrypted / identity unverified,
- physical verification offered,
- verification succeeds,
- verification fails,
- verification skipped,
- nearby live connection,
- relayed emergency connection,
- request expands,
- delayed/intermittent relayed conversation,
- requester cancels,
- responder disconnects,
- requester disconnects,
- active session timeout,
- SOS expires,
- “available to help” on/off,
- incoming SOS while responder app backgrounded,
- incoming SOS while already busy.

---

# 69. ANDROID / DEVICE EDGE-CASE CHECKLIST

The design system must account for:

- Bluetooth disabled,
- Bluetooth toggled during use,
- permission denied,
- permission revoked later,
- scanner unavailable,
- advertiser unavailable,
- connection timeout,
- disconnection mid-transfer,
- service stopped,
- OS kills process,
- app restarted,
- screen locked,
- reboot,
- storage full,
- database failure,
- low battery,
- background restriction,
- notification permission denied,
- microphone failure,
- camera failure,
- audio output unavailable.

---

# 70. TRUTHFULNESS AUDIT BEFORE APPROVING ANY MOCKUP

For every screen/state, ask:

1. Does this imply a message reached the intended recipient when only a relay has it?
2. Does this imply an unknown SOS responder’s human identity is verified when only encryption exists?
3. Does this imply precise physical distance from radio strength?
4. Does this imply a nearby radio peer is a trusted contact?
5. Does this imply the recipient must be nearby to receive a message?
6. Does this imply relays can read message text?
7. Does this imply delivery is guaranteed?
8. Does this imply “Delivered” means “read/seen”?
9. Does this imply Emergency Mode is active when Android has stopped it?
10. Does this imply reinstall preserves cryptographic identity?
11. Does this imply a display name is an authenticated identity?
12. Does this imply all messages are live / synchronous?
13. Does this expose technical internals that do not help the user?
14. Does color carry security meaning without text?
15. Does the screen still make sense with ten languages?
16. Does the screen still make sense if the user has zero contacts?
17. Does it make sense with fifty contacts?
18. Does it make sense with no Internet?
19. Does it make sense if the recipient is absent for hours?
20. Does it make sense if the app restarts?

If any answer reveals a false assumption, redesign it.

---

# 71. “DO NOT PAINT US INTO A CORNER” AUDIT

Before design handoff is considered complete, confirm the design has a coherent home for every item below.

### Identity / trust
- local identity
- show QR
- scan QR
- fingerprint / confirmation
- trusted contact
- rename contact
- forget contact
- identity changed / reinstall
- re-trust / re-exchange

### Trusted communication
- choose recipient
- PTT
- hands-free
- typed text
- ten languages
- editable draft
- explicit Send
- message too long
- urgent/normal distinction
- Queued
- Relayed
- Delivered
- Expired
- Unknown
- unread
- detail
- TTS playback/failure
- delayed/offline delivery
- duplicate suppression

### Network participation
- Emergency Mode off
- starting
- active
- stopped
- blocked by permission/radio/system
- background limitation
- “available to help”
- relay activity without plaintext exposure

### SOS requester
- start
- category/language
- searching
- widening search
- direct response
- relayed response
- cancel
- expire
- active session
- verification state
- responder loss
- timeout

### SOS responder
- availability
- incoming offer
- accept
- decline
- late accept
- already-busy
- requester cancelled
- requester disappeared
- active session
- verification

### Trust language
- encrypted
- identity unverified
- nearby device verified
- optional role credential
- stale credential information

### Platform failures
- Bluetooth off
- permissions denied
- service killed
- process death
- reboot
- storage full
- microphone/camera unavailable
- connection loss

If any item has nowhere to live without awkwardly adding a new random screen later, the information architecture is incomplete.

---

# 72. WHAT DESIGNERS ARE FREE TO REINVENT

You are explicitly free to replace:

- the old five-screen structure,
- the old Main layout,
- the Bento arrangement,
- amber/navy palette,
- old waveform/orb presentation,
- old log card styling,
- old haptic patterns,
- old language-chip placement,
- current device-selection UI,
- current contact UI,
- current emergency UI,
- current SOS UI,
- navigation model,
- typography,
- motion system,
- spacing system,
- visual brand.

You do not need to preserve historical pixels.

Preserve the **behavioral DNA and semantic truths**.

---

# 73. WHAT MUST SURVIVE ANY REDESIGN

No matter how radical the visual redesign:

1. Voice remains a first-class / hero communication path.
2. Voice output becomes a reviewable draft.
3. Send remains explicit.
4. Text remains available.
5. Hands-free remains representable.
6. Language remains per-message.
7. All ten languages fit the interaction model.
8. Received messages remain text-first durable data with TTS available.
9. Trusted contacts are cryptographic-contact concepts, not Bluetooth-device concepts.
10. QR exchange / trust confirmation has a coherent flow.
11. Recipient may be absent when message is created.
12. Store-carry-forward is compatible with the UX.
13. Queued, Relayed, Delivered, Expired, Unknown remain semantically distinct.
14. Relay acceptance never masquerades as delivery.
15. Relay-only messages remain invisible/private to relay users.
16. Emergency Mode is a real operating state.
17. SOS is separate from Trusted Person messaging.
18. SOS begins without trusted identity.
19. Encryption never masquerades as identity verification.
20. Direct vs relayed SOS can be distinguished.
21. Multiple responder acceptance can resolve coherently.
22. First launch works with zero contacts.
23. Reinstall/identity-loss is understandable.
24. Permission/radio/background failures have clear recovery.
25. No failure silently loses user text.
26. No security failure silently downgrades to plaintext.
27. No color-only security/trust semantics.
28. No exact-distance fiction.
29. No guaranteed-delivery fiction.
30. No forced exposure of networking internals.

---

# 74. REQUIRED DESIGN OUTPUT

The design team may choose its own deliverable format, but the final design proposal must include enough material to prove full coverage.

At minimum provide:

## A. Information architecture
Show how major product areas relate.

## B. Core navigation model
Explain how users reach:
- messaging,
- contacts / identity,
- history,
- Emergency Mode,
- SOS,
- settings / recovery.

## C. Primary happy-path flows
At least:
1. fresh install → trusted contact exchange,
2. PTT → draft → trusted send → Delivered,
3. recipient absent → Queued → Relayed → Delivered later,
4. receive message → read/hear,
5. start Emergency Mode,
6. start SOS → responder accepts → encrypted session,
7. verify nearby responder,
8. relayed SOS,
9. responder-side incoming SOS.

## D. Failure / edge-state components
Show how the system communicates blocked, degraded, expired, unknown, or interrupted states.

## E. Trust-state visual language
Clearly distinguish:
- trusted contact,
- encrypted-unverified SOS,
- nearby-device verified,
- optional responder credential.

## F. Message-state visual language
Clearly distinguish:
- Queued,
- Relayed,
- Delivered,
- Expired,
- Unknown.

## G. First-launch / recovery states
Include:
- permissions,
- no contacts,
- QR flows,
- reinstall identity change.

## H. Design rationale
Explain how the design:
- stays simple despite complex networking,
- remains usable under stress,
- avoids technical clutter,
- preserves truthfulness.

---

# 75. FINAL DESIGNER MANDATE

Design iTantra as a **complete ideal product**, not as a skin over today’s app.

Do not preserve a screen merely because it exists today.

Do not omit a capability merely because it is complicated.

Do not expose engineering internals merely because the product underneath is technically sophisticated.

The goal is:

> A stressed, non-technical person should be able to communicate confidently during an infrastructure outage while the interface remains honest about delivery, trust, identity, and connectivity.

The final design should feel simple.

The underlying system is not simple.

Your job is to make those two facts coexist without lying.

---

# 76. FINAL REVIEW CHECK

Before approving the mockup, ask one last question:

> **If every capability described in this document became available at once tomorrow, could this UI support all of them without a structural redesign?**

If the answer is no, the design is not finished.
