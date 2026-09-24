# iTantra BLE / DTN Networking Branch — Claude Opus Execution Spec

## Mission

You are working inside the existing iTantra Android repository.

Your job is to implement the **new iTantra v1 networking architecture** on **ONE dedicated Git branch**, prove it independently, benchmark it, and then — in a second sequential batch of work in the **same terminal session and same branch** — integrate it back into the current iTantra product.

This is **not** a request to lightly patch the old RFCOMM transport.

The existing RFCOMM implementation is useful prior art and may be reused behind the new abstraction, but the target architecture is the newer BLE/GATT + PeerLink + secure DTN architecture defined in the project specification.

Do not silently reduce scope because the full architecture is large. Implement the complete specified architecture as far as the repository, Android platform, available libraries, and available physical devices allow. If a component cannot be completed, leave the codebase in a clean state and report the exact blocker and the next executable step.

Do not fabricate successful tests, device behavior, measurements, or architecture completion.

---

# 0. SOURCE-OF-TRUTH ORDER

Before changing code, inspect the repository and locate these files.

Use this precedence:

1. **`understanding(1).txt` / the latest iTantra v1 Complete Build Specification**
   - This is the architecture source of truth.
   - If there are multiple copies, locate the newest complete one and state which file you selected.
2. Any explicit architecture audit/corrections contained in that spec.
3. The current repository implementation.
4. Existing docs such as:
   - `STATUS.md`
   - `TASK.md`
   - `AGENTS.md`
   - `docs/CONTRACTS.md`
   - `docs/PROJECT_FACTS.md`
   - `docs/SESSION_HANDOFF.md`
5. This execution spec.

If an old repo rule such as `TASK.md` or `AGENTS.md` explicitly forbids changing networking because it was written for an earlier lane/task, **do not ignore it silently**.

Instead:

- preserve the old rule/history;
- create or update the appropriate task/scope document for this new authorized networking branch;
- state what scope rule you changed and why.

Do not alter architecture decisions from the source-of-truth spec merely because the old RFCOMM implementation is easier.

---

# 1. BRANCH / REPOSITORY SAFETY

Use **one branch for both batches**.

Preferred branch name:

`feature/itantra-v1-networking`

Before creating it:

1. inspect `git status`;
2. identify current branch;
3. do not discard, reset, overwrite, or stash user work without understanding it;
4. if there are uncommitted unrelated changes, preserve them safely;
5. branch from the correct current integration base.

After branch creation, print:

- base branch
- new branch
- current HEAD
- whether the working tree was clean
- any pre-existing changes you preserved

Commit at meaningful phase boundaries with descriptive messages.

Do not rewrite shared Git history.

---

# 2. CURRENT IMPLEMENTATION — KNOWN AUDIT BASELINE

Treat these as findings to VERIFY against the repository, not assumptions to blindly repeat.

The prior audit found:

- existing transport = Bluetooth Classic RFCOMM;
- existing A → B → C relay path exists over RFCOMM;
- relay is in-memory flooding with TTL decrement + basic duplicate suppression;
- phones are manually bonded;
- app connects to paired devices;
- existing wire framing carries message ID, TTL, language and payload;
- existing adjacent-peer ACK semantics incorrectly allow the UI to say `Delivered` when a relay merely ACKs;
- there is no BLE advertiser;
- there is no BLE scanner used by the product;
- there is no GATT client/server;
- there is no `PeerLink`;
- there is no GATT fragmentation/reassembly;
- there is no Noise session;
- there is no secure DTN bundle layer;
- there is no durable Room store/carry/forward;
- there is no Spray-and-Wait implementation;
- there are no DTN receipts/tombstones/expiry;
- there is no benchmark harness;
- there are no transport timing logs sufficient for defensible BLE measurements.

The existing RFCOMM socket core may be reusable behind an eventual `RfcommPeerLink`.

Do not destroy the existing working RFCOMM demo path unnecessarily.

---

# 3. TARGET ARCHITECTURE

Implement the complete networking architecture described by the latest source-of-truth specification.

At minimum, the networking branch is expected to contain the following layers.

## 3.1 BLE discovery / readiness

Implement:

- BLE advertising
- BLE scanning
- service UUID filtering
- compact service data
- rotating advertised identifier
- emergency-mode/session peer identity
- RSSI capture
- deduplication of scan results
- bounded scan restart behavior
- deterministic simultaneous-connection collision handling
- bounded retry / role swap where required by the spec

Do not use Bluetooth MAC addresses as application identity above the link layer.

## 3.2 BLE GATT byte transport

Implement the canonical v1 BLE transport:

- GATT server
- GATT client
- iTantra custom service
- required characteristics
- CCC descriptor
- client → server `WRITE_WITH_RESPONSE`
- server → client `INDICATE`
- MTU request / negotiated MTU handling
- notification/indication enablement
- operation completion callbacks
- one-GATT-operation-in-flight serialization per connection
- correct close/disconnect discipline
- bounded retry/backoff for transient Android GATT failures such as status 133
- explicit connection state machine

Do not assume default ATT MTU is large enough.

## 3.3 PeerLink abstraction

Introduce the transport boundary required by the architecture.

The rest of the networking stack must not depend directly on Android GATT objects or Bluetooth MAC addresses.

Implement the equivalent of:

- `PeerLink`
- `BleGattPeerLink`
- `PeerLinkManager`

and, where practical without destabilizing the known demo path:

- `RfcommPeerLink`

The exact API must follow the source-of-truth spec.

If the spec requires suspend functions / Flow, add the required direct coroutine dependency rather than relying on accidental transitive dependencies.

## 3.4 Framing

Implement a typed application frame layer appropriate for `PeerLink`.

Requirements include:

- explicit frame type
- self-delimiting framing
- strict maximum lengths
- malformed-frame rejection
- bounded allocations
- tests

Follow the source-of-truth specification for CBOR / binary layout.

If a field width or fragment header detail is genuinely unspecified in the current spec:

1. do not invent it invisibly;
2. identify the ambiguity;
3. choose the smallest defensible implementation decision that satisfies existing bounds;
4. document it prominently;
5. keep the decision isolated so it can be revised without redesigning the stack.

## 3.5 Fragmentation / reassembly

Implement:

- fragmentation based on negotiated link payload
- message/frame identity
- fragment index/count or the exact spec equivalent
- duplicate fragment handling
- conflicting fragment rejection
- reassembly timeout
- hard fragment count cap
- hard frame-size cap
- per-peer memory bounds
- cleanup after completion/failure
- tests

The design must remain safe at small MTUs.

## 3.6 Foreground lifecycle

Implement the Android lifecycle needed by the spec, including the equivalent of:

- `EmergencyModeService`
- foreground service for connected-device work
- required manifest permissions
- API-level handling
- service lifecycle
- transport cleanup
- survival across ordinary Activity recreation

Do not claim process-death survivability unless the persistent layers actually provide it.

## 3.7 Noise / hop security

Implement the hop/session security layer defined by the source-of-truth spec.

Requirements include:

- a pinned, auditable Noise implementation or equivalent explicitly allowed by the spec
- Noise XX if that is still the frozen choice
- fresh per-session keys/static material as specified
- handshake state
- handshake transcript/hash exposure if required
- malformed handshake rejection
- replay/session separation tests
- encrypted application frames above `PeerLink`

If the chosen library requires JNI/NDK or causes an architecture-level dependency change, document that explicitly before committing to it.

Do not write home-grown cryptography.

## 3.8 Identity / trust foundations

Implement the Phase-1 foundations required by the DTN layer, including the source-of-truth equivalents of:

- protocol constants/config
- canonical CBOR
- Ed25519 identity/signing
- X25519 encryption/key agreement material
- secure local key storage
- Android backup exclusion where required
- signed identity capsule
- domain separation
- QR export/import
- trusted-contact storage

Follow the frozen specification exactly.

## 3.9 Direct trusted messaging

Implement the secure known-recipient message path specified in the architecture, including where applicable:

- pair-secret derivation
- direction-bound destination tags
- signed inner message
- sealed/encrypted content
- recipient verification order
- destination recognition without exposing unnecessary identity to relays

Do not replace this with Bluetooth device names, MACs, phone numbers, or plaintext recipient names.

## 3.10 Durable DTN bundle layer

Implement the architecture's bundle layer, including:

- `BundleV1` or exact equivalent
- durable storage
- Room database if still specified
- bundle store
- seen/dedupe store
- inventory exchange
- paginated / bounded inventory summaries
- `WANT_RELAY`
- `WANT_DESTINATION`
- store/carry/forward across encounters
- reboot/process-survival behavior supported by the spec
- age/lifetime tracking
- expiry

Do not confuse old hop TTL flooding with the new DTN routing model.

## 3.11 Spray-and-Wait

Implement the specified bounded replication algorithm.

If the frozen algorithm remains binary Spray-and-Wait:

- maintain copy-token state
- split tokens correctly
- stop spraying at the defined threshold
- reconcile token state on later encounters
- persist token state
- test duplicate/repeated encounters

Do not silently fall back to flooding.

## 3.12 Delivery state / cleanup

Implement the architecture's actual delivery semantics:

- queued
- relayed
- delivered
- expired

Implement:

- end-recipient delivery receipts
- receipt routing if required
- tombstones
- expiry cleanup
- suppression of stale replicas

Fix the current semantic bug where a relay's adjacent-hop ACK can cause the sender UI to claim `Delivered`.

Hop ACK and end-recipient delivery receipt are different things.

## 3.13 SOS / emergency stranger path

Implement the SOS path exactly as specified by the source-of-truth architecture, including its trust/security differences from known-recipient messaging.

Do not force bonded RFCOMM to be required for SOS.

If responder/offline credentials are part of the frozen v1 architecture, implement their verification boundary as specified.

## 3.14 Hardening

Add bounds and defenses required by the spec:

- malformed frame handling
- malformed CBOR
- duplicate storms
- oversized claims
- per-peer byte/memory limits
- connection limits
- handshake limits
- timeouts
- rate limits
- reconnect storms
- process/activity lifecycle tests where practical
- screen-off/background behavior checks where physical devices permit

---

# 4. BATCH 1 — BUILD AND PROVE THE NETWORKING SUBSYSTEM

## Batch-1 goal

Produce a **standalone, independently testable networking subsystem** implementing the new architecture.

Do NOT wire STT or TTS into it yet.

Do not let current UI structure dictate the architecture.

A minimal diagnostic UI or benchmark screen is allowed if needed for device testing.

## Batch-1 execution order

Follow architecture dependencies, not filename convenience.

Recommended sequence:

1. repository/scope preparation
2. protocol foundations and tests
3. BLE advertise/scan
4. GATT client/server
5. `PeerLink`
6. framing
7. fragmentation/reassembly
8. deterministic connection role/collision handling
9. foreground service/lifecycle
10. three-phone raw relay proof
11. Noise/session security
12. identity/trust foundations
13. direct trusted messaging
14. durable bundle store + inventory
15. Spray-and-Wait
16. receipts/tombstones/expiry
17. SOS path
18. hardening
19. benchmark harness
20. full networking acceptance pass

You may adjust the order when code dependencies require it, but explain major deviations.

---

# 5. BENCHMARKING MUST BE BUILT INTO BATCH 1

We need real transport evidence for the SIH deck.

Instrumentation is not an afterthought.

Implement a benchmark/debug mode that can operate without STT/TTS contaminating timings.

## 5.1 Required measurements

Where the architecture permits, collect:

- scan/discovery time
- connection setup milestones
- service discovery time
- MTU negotiation result/time
- CCCD/indication readiness time
- payload bytes
- framing bytes
- fragmentation bytes
- number of fragments
- A → B transfer behavior
- B relay processing time
- B → C transfer behavior
- total relay-path timing
- throughput
- repeated-send success rate
- timeout/failure counts

## 5.2 Clock correctness

Do not report one-way cross-device latency by naïvely subtracting timestamps from unsynchronized phone clocks.

For defensible transport timing:

- use monotonic clocks such as `SystemClock.elapsedRealtimeNanos()` or the project-standard equivalent;
- for end-to-end relay timing, use an echo/probe path measured on the originating phone's clock where appropriate;
- record B receive → B forward processing time on B's own monotonic clock;
- keep raw logs sufficient to audit every reported value.

If another defensible synchronization method is implemented, document it.

## 5.3 Benchmark result definitions

Define success explicitly.

Example:

- a probe is successful only when the expected echo/result returns before timeout;
- report attempts, successes, failures;
- do not exclude failed trials silently.

Produce machine-readable output:

- CSV, JSONL, or equivalent
- timestamps
- device identifier/session identifier
- negotiated MTU
- payload size
- fragment count
- route/hops
- result
- failure cause

## 5.4 Measurement labels

Every result must state what was actually measured.

Never label:

- RFCOMM results as BLE GATT;
- desktop results as Android;
- planned/target values as measured.

---

# 6. BATCH-1 ACCEPTANCE GATES

Do not declare Batch 1 complete merely because the project compiles.

The strongest available acceptance target is:

## Gate A — static/unit correctness

- project builds
- protocol tests pass
- codec bounds tests pass
- fragmentation/reassembly tests pass
- routing/token tests pass
- malformed-input tests pass where practical

## Gate B — two-phone BLE link

On real Android phones where available:

- advertiser visible
- scanner finds peer
- deterministic connection role resolves
- GATT connection reaches ready state
- MTU handled
- bidirectional application frame exchange works
- reconnect works after disconnect

## Gate C — three-phone relay

On A, B, C:

- A and B establish usable link
- B and C establish usable link
- B can maintain required simultaneous roles
- A-originated message reaches C through B
- duplicate handling works
- benchmark instrumentation records the trial

## Gate D — DTN behavior

Where physical testing permits:

- destination unavailable initially
- relay stores bundle
- later encounter occurs
- bundle forwards according to copy-token policy
- destination receives/authenticates message
- sender state does not become `Delivered` merely because a relay accepted the bundle
- final receipt/tombstone behavior works as specified

## Gate E — security boundary

- relays cannot read protected known-recipient message plaintext
- malformed/untrusted frames fail safely
- identity/destination recognition follows the spec

## Gate F — repeatability

Run repeated trials.

Do not call the architecture validated from one lucky send.

---

# 7. BATCH-1 CHECKPOINT REPORT

After Batch 1 reaches the strongest achievable gate, print a structured report before beginning integration.

Report:

## IMPLEMENTED

Exact completed architecture components.

## TESTED

What was actually executed, on what devices/environment, and how many times.

## NOT YET PROVEN

Code that exists but lacks physical/device validation.

## BLOCKED / INCOMPLETE

Exact blockers.

## BENCHMARK OUTPUT

Paths to raw benchmark files and a short summary.

## COMMITS

Relevant commit hashes.

## READY FOR PRODUCT INTEGRATION?

Answer:

- YES
- PARTIALLY
- NO

with the reason.

Do not fabricate green status.

---

# 8. BATCH 2 — INTEGRATE THE SAME BRANCH INTO THE CURRENT iTANTRA PRODUCT

Batch 2 happens **in the same Git branch and same terminal working context** after the Batch-1 checkpoint.

Do not create a second feature branch for this workflow unless repository safety makes it unavoidable.

## Batch-2 goal

Connect the proven networking subsystem back into the existing iTantra application.

Target product data flow:

`microphone / STT`
→ `text + language + message metadata`
→ `new networking stack`
→ `remote recipient`
→ `received text`
→ `TTS`

The networking layer must remain independent of speech-model implementation details.

## 8.1 Inspect current app wiring first

Before integration, locate:

- current STT output boundary
- selected-language state
- current send action
- current paired-device/transport UI
- current RFCOMM transport calls
- current receive callback
- current TTS invocation
- current message/delivery state UI

Document the integration points.

## 8.2 Replace transport coupling cleanly

Do not spray GATT/DTN calls throughout `MainActivity`.

Route the product through the new abstractions.

The app should speak to a product-level networking API / repository / manager, not to raw `BluetoothGatt`, sockets, or MAC addresses.

## 8.3 Preserve working product behavior

Keep, as appropriate:

- language selection
- text-send functionality
- received text display
- STT/TTS boundaries
- existing demo flows that do not conflict with the new architecture

Do not regress unrelated speech code.

## 8.4 TTS behavior on relays

A relay phone must not automatically speak every relayed message merely because it forwards it.

Only the intended local-delivery path should trigger product-level TTS, according to the architecture.

## 8.5 Delivery UI semantics

Correct the app states so that:

- hop ACK ≠ delivered
- relay acceptance ≠ delivered
- only the architecture-defined end-recipient proof causes `Delivered`

## 8.6 Compatibility path

If the architecture permits RFCOMM as an optional `PeerLink`, keep it behind the abstraction without making it the required SOS or primary v1 BLE path.

Do not let compatibility code leak MAC-based identity upward.

---

# 9. BATCH-2 ACCEPTANCE

After integration, test the strongest available form of:

## Product test A

Phone A:

- user selects language
- produces/enters text
- sends through new networking stack

Phone B / relay:

- receives only at transport/router layer unless it is the destination
- forwards according to architecture
- does not incorrectly speak/display private relay payload

Phone C / destination:

- receives authentic message
- product obtains text
- TTS boundary is invoked correctly

## Product test B — end-to-end benchmark hook

Expose timestamps/hooks needed later to measure:

`speech end on sender`
→ `remote speech start`

Do not fabricate this number if STT/TTS are not yet instrumented.

The networking branch only needs to make the transport timestamps accessible cleanly.

## Product test C — legacy regression

If old RFCOMM remains as optional fallback/demo transport, verify that the abstraction does not accidentally break it.

---

# 10. ENGINEERING RULES

Throughout both batches:

- inspect before editing;
- build frequently;
- test after meaningful changes;
- prefer small coherent commits;
- do not leave placeholder success paths;
- do not comment out failing architecture merely to make builds green;
- do not replace cryptographic requirements with mock crypto;
- do not fake multiple peers in production code and call it multi-hop validation;
- diagnostic fakes are acceptable only when clearly separated and labelled;
- keep production and benchmark code separable;
- preserve raw measurement evidence;
- never invent benchmark values;
- never claim a physical test you did not execute;
- never silently weaken source-of-truth security or routing semantics.

When blocked by an Android/device-specific issue:

1. capture logs;
2. isolate the failing layer;
3. add a reproducible diagnostic;
4. try the next technically justified fix;
5. report the blocker only when you have exhausted reasonable local fixes.

---

# 11. DEVICE / TOOLING DISCOVERY

At the beginning, inspect available tooling:

- Gradle wrapper
- Android SDK
- JDK
- adb
- connected physical devices
- device API levels / ABIs
- current build variants

Do not assume only one phone exists because repository docs mention one.

If multiple phones are connected/available, identify them safely for testing.

If fewer than three physical phones are available, still build the architecture and automated tests, then explicitly distinguish:

- implemented
- emulator/static-tested
- two-phone tested
- three-phone tested

---

# 12. FINAL REPORT

At the end of Batch 2, provide:

## BRANCH

Name + HEAD commit.

## ARCHITECTURE STATUS

For every major component:

- IMPLEMENTED + TESTED
- IMPLEMENTED, NOT PHYSICALLY TESTED
- PARTIAL
- BLOCKED
- NOT STARTED

## PRODUCT INTEGRATION STATUS

Exactly what is wired into the app.

## PHYSICAL TEST MATRIX

Devices, Android versions, topology, successful/failed trials.

## BENCHMARK ARTIFACTS

Paths to raw files and definitions of each metric.

## KNOWN LIMITATIONS

Concrete, not generic.

## NEXT EXECUTABLE STEPS

Only genuine remaining work.

---

# 13. START NOW

Begin by:

1. reading the complete architecture spec;
2. reading the existing audit/status/task/agent docs;
3. inspecting Git state;
4. verifying the audit against the code;
5. creating the single networking branch safely;
6. writing a concise implementation plan mapped to the source-of-truth phases;
7. starting Batch 1.

Do not ask me to restate architecture that already exists in the repository.

Do not stop after planning.

Execute.
