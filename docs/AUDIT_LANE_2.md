# AUDIT_LANE_2.md — Round 1 prep

Audit date: 2026-09-06. Audited ref: `origin/lane/transport` @ `25faa4e` ("Add in-memory store and
forward relay"), all authored by Utkarsh. This is the tip of the remote branch as of this audit —
the local `lane/transport` branch in this working tree is stale (stuck 3 commits back, at
`1777a40`, from 2026-09-04); `git fetch` before trusting a local checkout of this branch.

This lane, unlike Lane 1, has no `AGENTS.md`, `TASK.md`, `STATUS.md`, or `docs/` on its branch — no
rung ladder, no human-owned GREEN/YELLOW/RED record, no physical-acceptance checklist exists for it
anywhere in this repo. Every tag below is this audit's own assessment, not a lane-owned record.

Tags: [WORKS] = a human ran it on a physical device and saw it. [BUILT] = compiles, unverified on
device. [PLANNED] = intended, no working code.

## 1. State

There is no rung ladder to check against, so this is a feature-by-feature read of what's on the
branch. **Zero [WORKS] items** — no file in this repo, on any branch, records a human running any
of this on a phone.

- [BUILT] Runtime Bluetooth permission handling, version-aware (`BLUETOOTH_SCAN`/`CONNECT`/
  `ADVERTISE` on API 31+, `ACCESS_FINE_LOCATION` below it) — `BluetoothPermissions.kt`.
- [BUILT] Paired-device listing and nearby-device discovery via `BluetoothAdapter` — 
  `BluetoothDiscovery.kt`.
- [BUILT] Device pairing (bond) request flow — `BluetoothPermissions.requestPairing`.
- [BUILT] RFCOMM server (`listen`) and client (`connect`) socket setup on a fixed service UUID,
  with multi-peer support (`ConcurrentHashMap<address, BluetoothSocket>`), so one phone can hold
  two simultaneous peer links (needed for a 3-phone relay) — `BluetoothRfcommTransport.kt`.
- [BUILT] A versioned wire protocol (1-byte version, 4-byte message ID, 1-byte TTL, 1-byte
  language, 2-byte length, UTF-8 text) plus a separate 5-byte ACK frame, framed by hand over
  `DataInputStream`/raw `OutputStream` — no serialization library.
- [BUILT] Delivery acknowledgements: sender gets `onAcknowledgementReceived(messageId)` when the
  peer's ACK frame arrives.
- [BUILT] Disconnect/reconnect handling: a dead socket is dropped from the peer map, the "current"
  send target fails over to another live peer if one exists, and the listener/connector can be
  invoked again without restarting the app.
- [BUILT] TTL + message-ID relay policy (`TransportRelayPolicy`): first sight of an ID forwards
  with `ttl - 1`, a repeat ID is `Duplicate`, `ttl == 0` is `TtlExpired`. Covered by 3 passing unit
  tests (`TransportRelayPolicyTest`, JVM, no device/emulator).
- [BUILT] In-memory relay queue (`pendingRelays`, cap 100): if a message needs relaying but no
  other peer is connected yet, it is held and flushed to the next peer that connects, skipping the
  original sender's address so it doesn't echo back.
- [BUILT] A raw test/demo screen (`BluetoothPermissionScreen` in `MainActivity.kt`, replaces Lane
  1's screen entirely on this branch) exercising all of the above — permission grant, adapter
  on/off, paired list, discovery, pairing, listen/connect, send text, show send/receive/ACK/relay
  state as plain text.
- [PLANNED] Multi-hop relay beyond one middle phone (3-phone chain) — the code supports it
  structurally (multi-peer sockets + TTL + relay-to-other-peers), but no test, log, or note in this
  repo shows three physical phones relaying a message; this audit did not attempt it.
- [PLANNED] Any integration with Lane 1's `sendMessage(text)` / `onMessageReceived(text,
  languageCode)` contract shape (`docs/CONTRACTS.md`) — this branch's own screen calls its
  transport class directly; nothing here has been adapted to Lane 1's function signatures yet.

## 2. Decisions

- Hand-rolled binary framing over raw Bluetooth streams, no serialization library (Protobuf,
  kotlinx.serialization, JSON) — alternative rejected: none evaluated in any commit message or
  comment; the format is a fixed 9-byte header (version, ID, TTL, language, length) + UTF-8 body —
  why: not recorded, but it's the smallest possible framing for a link this constrained, and it
  reuses the same byte layout for both messages and the leaner 5-byte ACK frame.
- A single shared `TransportRelayPolicy` instance across all peer sockets on one phone, not one per
  peer — alternative rejected: per-peer dedup — why: a relay's whole point is phone-wide
  "have I seen this ID from any neighbor," so a single instance is the correct choice, not an
  oversight, but it is undocumented as a deliberate decision anywhere.
- `MessageLanguage` wire enum currently has exactly two values, `ENGLISH`/`HINDI` (wire values 1/2)
  — alternative available: `docs/CONTRACTS.md` already specifies 10 ISO 639-1 codes (`en`, `hi`,
  `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`) as the resolved cross-lane contract — why not
  recorded; this is a real, unflagged mismatch, not a stated scope cut (see §5).
- Text payload capped at 4,096 UTF-8 bytes, TTL capped at 255, default TTL 3 — alternative
  rejected: unbounded — why not recorded, but 4 KB matches "send text, not voice" and TTL 3 covers
  a 3-phone relay chain with margin.
- Pending-relay queue capped at 100 entries, oldest dropped first (`poll()` on overflow) —
  alternative rejected: unbounded queue — why not recorded; reasonable but unmeasured (no note on
  what memory/latency cost 100 entries represents).
- `TransportRelayPolicy`'s seen-ID set, when it exceeds 2,048 entries, is **entirely cleared** and
  reseeded with only the newest ID (`TransportRelayPolicy.kt`, `remember()`) rather than evicting
  the oldest — this silently forgets every previously-seen ID at once, re-opening the door to
  duplicates/loops for messages already forwarded. Not called out anywhere as a known limitation;
  this audit is the first place it's written down.
- Fixed hardcoded `SERVICE_UUID` shared by every iTantra phone (`BluetoothRfcommTransport.kt`
  comment: "Every iTantra phone must use this exact UUID or client connections will fail") —
  alternative rejected: per-session negotiated UUID — why: simplicity for a fixed-protocol
  hackathon build; means the UUID becomes a cross-lane constant the moment integration starts.
- No serialization/versioning library dependency added to `gradle/libs.versions.toml` — the
  version byte in the protocol is the only forward-compatibility mechanism, and nothing currently
  reads or branches on it (`startReading` rejects anything that isn't exactly
  `PROTOCOL_VERSION`).

## 3. Numbers

All measured directly by this audit, in a disposable `git worktree` checked out at
`origin/lane/transport` (`25faa4e`), then deleted — not from any file already in the repo, because
no build artifact or measurement for this lane exists anywhere else.

- [BUILT] `./gradlew.bat test`: **BUILD SUCCESSFUL**, 25s, 24 tasks executed. 3 `TransportRelayPolicyTest` cases pass (JVM unit tests, no device/emulator involved).
- [BUILT] `./gradlew.bat assembleDebug`: **BUILD SUCCESSFUL**, 17s.
- [BUILT] Resulting `app-debug.apk`: 11,773,712 bytes (~11.2 MiB) — this is the whole lane-2 branch's app, including its own standalone test/demo screen; not comparable to Lane 1's APK, which additionally bundles ~400 MB of ASR/TTS model assets.
- [BUILT] Transport-package source: 797 lines across 4 files (`BluetoothDiscovery.kt` 108,
  `BluetoothPermissions.kt` 96, `BluetoothRfcommTransport.kt` 552, `TransportRelayPolicy.kt` 41).
- [BUILT] Wire protocol overhead per message: 9 bytes (1 version + 4 message ID + 1 TTL + 1
  language + 2 length) before the UTF-8 text body; ACK frame is 5 bytes total. Computed from the
  `writeMessage`/`sendAcknowledgement` code, not measured over an actual radio link.
- [PLANNED] Any latency, throughput, range, or reliability number for an actual RFCOMM link
  (connect time, send-to-receive time, relay hop time, packet loss over distance) — **none
  exists**. No test, log, or note in this repo has ever exercised this code over a real Bluetooth
  radio between two devices. Do not assume RFCOMM connect/send timings from the code; they are
  unmeasured.

## 4. Demo-safe

**Nothing.** There is no [WORKS] item in §1 to show a judge — no human has run any part of this
lane on a phone, so there is nothing this audit can certify as demo-ready, unrehearsed or
otherwise, regardless of how much code compiles and passes JVM unit tests.

If this branch were built and installed fresh tomorrow with zero prior device testing, first-run
risk is concentrated in exactly the parts a JVM test cannot cover: whether the Android 12+
permission prompts actually appear and grant correctly on the target device (RMX3392, API 34),
whether `listenUsingRfcommWithServiceRecord`/`createRfcommSocketToServiceRecord` actually
connect two real radios on the fixed UUID, and whether a middle phone genuinely holds two live
sockets at once for a 3-phone relay. None of that has been tried once, per every file in this repo.

## 5. Gaps

- No physical-device run of any kind, on any hardware. Zero.
- No rung ladder, no `STATUS.md`, no `TASK.md`, no `AGENTS.md`, no `docs/` folder on this branch —
  there is no human-owned acceptance record for this lane to check work against.
- No instrumented (`androidTest`) coverage of Bluetooth behavior — the only device-adjacent test in
  the tree is the unmodified template stub (`ExampleInstrumentedTest`, checks the package name).
  Everything Bluetooth-specific is untested except by manual/eventual device run.
- `MessageLanguage` supports 2 of the 10 languages `docs/CONTRACTS.md` commits every lane to.
- The relay dedup set's overflow behavior (full clear at 2,048 IDs) reintroduces duplicate
  forwarding/loop risk once that many distinct message IDs have passed through a phone — untested,
  unflagged.
- No integration with Lane 1's actual `sendMessage`/`onMessageReceived` function signatures from
  `docs/CONTRACTS.md` — this branch calls its own transport class from its own throwaway test
  screen, not from anything Lane 1 will ship.
- No multi-hop (3-phone) relay has ever been exercised, physically or by test; only the two-party
  path (`decideForIncoming` unit tests) is verified at all, and only in isolation from real sockets.
- No alert-message / max-volume-non-interruptible-playback metadata exists here either — matches
  the cross-lane "unresolved" status recorded in Lane 1's `docs/CONTRACTS.md`, not a gap unique to
  this audit.
- The local `lane/transport` branch in this working tree is 14 commits behind `origin/lane/transport`; anyone auditing or building from the local branch name alone gets stale, effectively empty code.

## 6. Blocked on

- **From Lane 1 (App):** the real `sendMessage(text)` / `onMessageReceived(text, languageCode)`
  signatures and the shared `Message` model this transport needs to plug into, per
  `docs/CONTRACTS.md` — needed before this lane's transport can stop being its own standalone demo
  screen and start being the thing Lane 1 calls. No date given anywhere in this repo.
- **From Lane 1/Lane 2 jointly:** an agreed alert-message metadata shape — recorded as unresolved
  in `docs/CONTRACTS.md` and `STATUS.md`; blocks both received-alert UI (Lane 1) and any alert
  field in this lane's wire protocol. No date given.
- **From Lane 3 (Speech):** nothing structural — this lane's protocol already carries a language
  code per message; Lane 3 needs to consume `onMessageReceived`'s eventual real (not this branch's
  demo-screen) callback once Lane 1 exposes it. No date given.
- **Cross-lane decision needed, not owned by any one lane:** whether the 10-language contract in
  `docs/CONTRACTS.md` or this lane's current 2-language wire enum is the one that ships — someone
  has to reconcile this before integration, not after.
