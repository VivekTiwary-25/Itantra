# iTantra v1 networking — implementation notes

Branch: `feature/itantra-v1-networking` (base `research/dolphin-conditioned@230eab9`,
which is `integration/tts-recovery@0e63c45` plus one STT timing-instrumentation commit).

Source of truth: `ITANTRA_V1_SPEC_AND_AUDIT.txt` (the "iTantra v1 — Complete Build
Specification" embedded verbatim, plus the Claude audit backlog #1–#9 and A–H, which
this implementation applies). Execution instructions: `EXECUTION_SPEC_BLE_BRANCH.md`.

This file records every place where the spec was ambiguous, where a library forced a
choice, or where the implementation deliberately deviates. Each item names the code
that isolates the decision so it can be revised without redesigning the stack.

## 1. Audit baseline, verified against the code at 230eab9

| Audit claim | Verified |
|---|---|
| Transport is Bluetooth Classic RFCOMM (`BluetoothRfcommTransport`) | Yes |
| A → B → C relay exists over RFCOMM | Yes (`relayToOtherPeers`) |
| Relay = in-memory flooding, TTL decrement, bounded seen-set | Yes (`TransportRelayPolicy`; the seen-set is *cleared entirely* when it overflows 2048 IDs) |
| Phones manually bonded, app connects to paired devices | Yes (`BluetoothPermissions.pairedDevices`, `createRfcommSocketToServiceRecord`) |
| Wire frame carries msgId, TTL, language, payload | Yes (protocol v2: ver, u32 id, ttl, 2-byte ISO code, u16 len, UTF-8) |
| Adjacent-peer ACK lets UI say `Delivered` | Yes. `MainActivity` sends an ACK for *every* received message, including on a relay, and the sender maps any ACK to `DELIVERED` |
| No BLE advertiser/scanner/GATT/PeerLink/fragmentation/Noise/DTN store/Spray-and-Wait/receipts/tombstones/benchmark | Yes, none existed |

## 2. Dependencies (all from Maven Central, pinned in `gradle/libs.versions.toml`)

| Purpose | Artifact | Why |
|---|---|---|
| Noise XX | `org.signal.forks:noise-java:0.1.1` | Signal's published fork of Rhys Weatherley's reference Noise-Java (listed on noiseprotocol.org). Pure Java 8 bytecode, no JNI/NDK, no runtime deps. Supports `Noise_XX_25519_ChaChaPoly_SHA256` and exposes `getHandshakeHash()`. SHA-256 of the jar: `2bbc531e5e31b3151269dbb7596548e3c884ded217ab6312c2d87591bfea543b`. |
| Ed25519, X25519, HKDF, HPKE | `com.google.crypto.tink:tink-android:1.23.0` | Google-maintained, reviewed. |
| Coroutines/Flow | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0` | `PeerLink` is suspend/Flow-based; declared directly instead of relying on the Compose transitive copy. |
| QR generation | `com.google.zxing:core:3.5.4` | Pure Java QR encoder. |
| QR scanning | `com.journeyapps:zxing-android-embedded:4.3.0` | Camera scan activity. The capsule text can also be pasted, so camera-less testing works. |

Rejected: `nl.sanderdijkhuis:noise-kotlin:1.0.1`. Its `S`-token reader only accepts a
remote static key already present in `trustedStaticKeys`, so XX with unknown
stranger relays cannot work without patching the library.

No Noise, AEAD, signature or key-agreement primitive is implemented in this repo.

## 3. Deliberate deviations from the build spec

1. **Room → framework SQLite.** The spec says "Room is appropriate" (not MUST). Room
   needs KSP, which is an extra Gradle plugin in a shared build file on AGP 9.4's
   built-in Kotlin, a combination not yet exercised in this repo. `SQLiteOpenHelper`
   gives the same durability (WAL, transactions) with zero build risk.
   Isolated in `dtn/sqlite/NetDatabase.kt`; stores are interfaces, so a Room swap
   stays local.
2. **Sealed box = HPKE.** §18 allows HPKE if the envelope is documented. The suite
   is RFC 9180 base mode, `DHKEM(X25519, HKDF-SHA256)`, `HKDF-SHA256`,
   `ChaCha20Poly1305`, `info = "itantra-v1/sealed-message"`, empty AAD. The
   ciphertext is `enc(32) || ct`. The sender is anonymous at the HPKE layer and is
   authenticated by the inner Ed25519 signature (§17). Code: `crypto/PrivateMessageCrypto.kt`.
3. **Canonical CBOR is an in-repo bounded subset codec**, not a library. CBOR is
   not cryptography. A small codec lets every length, count and depth limit be
   explicit (§49). Supported: unsigned ints, byte strings, text strings,
   arrays, maps with **unsigned-integer keys only**, booleans. It rejects negative
   integers, non-shortest integers, indefinite lengths, tags, floats, `null`/`undefined`, unsorted or
   duplicate keys, invalid UTF-8, trailing bytes, and anything over the limits.
   This matches RFC 8949 §4.2.1 core deterministic encoding for that subset.
   Code: `protocol/cbor/`.
4. **`PeerObservationEntity`, `SosIncidentEntity` and `SosSeenFrameEntity` are kept
   in memory, not in SQLite.** Peer observations are rotating short IDs + RSSI,
   pruned after 4× `peerStaleMs`, and are deliberately not persisted so there is no
   durable log of radio sightings. SOS state lives for at most minutes (2 min
   discovery TTL). Incident keys must be discarded when the incident ends (§43), so
   persisting them would work against the spec. MAC addresses never leave `transport/ble`.
5. **SOS credentials / trust State 3 are trimmed** from the v1 build (audit G).
   `credential-present` is not sent pre-accept.
6. **Relayed *interactive* SOS sessions (§41, encapsulated Noise over SOS DTN frames)
   are not built.** §41 says to build them "only after the direct path is stable".
   The direct path has no physical evidence yet. Multi-hop SOS *request*
   dissemination and cancellation (§40, §42) are built.

## 4. Audit corrections applied

| # | Applied as |
|---|---|
| 1 | Recipient ignores `sender_signing_public_key` for authentication. It verifies with the stored key of the contact whose pair secret matched the destination tag, and requires `sender_node_id` to be that same contact. The message-supplied key must also equal the stored key. |
| 2 | `pair_secret = HKDF-SHA256(ikm = X25519(own, peer), salt = none, info = "itantra-v1/pair-secret" \|\| min(pkA,pkB) \|\| max(pkA,pkB))`. `destination_tag = Trunc128(HMAC-SHA256(pair_secret, "itantra-v1/destination-tag" \|\| recipient_node_id \|\| bundle_id))`. An all-zero X25519 output is rejected. |
| 3 | `PrivateMessageV1` and `DeliveryReceiptV1` carry authenticated copies of `bundle_id`, `lifetime_ms` and `priority`, plus the `deletion_secret` itself. The recipient checks `SHA256("itantra-v1/delete" \|\| deletion_secret) == outer deletion_commitment`, which is stronger than carrying a copy of the commitment. The recipient rejects any mismatch with the outer envelope. Relays cannot check this (they can't decrypt), which is a documented limitation. |
| 4 | Relay dedupe/storage key = `(bundle_id, ciphertext_hash)`. The recipient marks an `inner_message_id` seen only after decryption, the recipient check and the signature check all pass. |
| 5 | Every signature is domain-separated: `itantra-v1/capsule`, `itantra-v1/private-message`, `itantra-v1/receipt`, `itantra-v1/sos-request`, `itantra-v1/sos-offer`, `itantra-v1/sos-cancel`. |
| 6 | Hop Noise static keys are fresh random X25519 keys per Emergency-mode session. Identity keys never enter Noise. The SOS short authentication string comes from the Noise handshake hash. |
| 7 | Advertisement = one service-data AD under the 128-bit iTantra UUID: `version(1) \| flags(1) \| short_id(8)`. Device name and TX power are off. `short_id` is random and rotates. Size: 3 (flags AD) + 28 = 31 bytes. |
| 8 | Bundle age uses `(base_age_ms, base_elapsed_ms, base_boot_count)`. Age is re-based and persisted periodically. If `Settings.Global.BOOT_COUNT` changed, the age resumes from the last persisted value. |
| 9 | Resolved by the pinned Signal noise-java fork (no JNI). See §2. |
| A | Spray token splits are two-phase: persist pending, commit on hop ACK. An unacknowledged split is reconciled if the same session peer later shows the bundle in its inventory, and rolled back after a timeout otherwise. Rollback can overcount, and the acceptance wording does not claim a perfect budget. |
| B | Receipts carry `inner_message_id`. Receipt bundles never generate receipts. |
| C | The first confirmed SOS accept wins. Later accepts get `SOS_BUSY`. |
| D | Sybil consumption of spray copies is a listed limitation. |
| E | Sealed plaintext is padded to buckets of 256 B, 1 KiB, 4 KiB, 16 KiB and 24 KiB. 24 KiB is added because 16 KiB of text plus the envelope exceeds 16 KiB. |
| F | Identity key files live in `noBackupFilesDir`. The networking DB is excluded from cloud backup and device transfer. The UI says a reinstall loses identity. |
| G | Credential-present / State 3 trimmed (see §3.5). |
| H | Scan filters are always set. Scan starts are bounded (`ScanRestartLimiter`). One GATT op is in flight per connection. Status 133 gets bounded backoff. The client and server roles run at the same time. |

## 5. Choices where the spec is silent

| Topic | Decision | Code |
|---|---|---|
| GATT fragment header | `version(1)=1 \| connection_session_id(4) \| frame_id(u16) \| fragment_index(u16) \| fragment_count(u16) \| payload`, 11 bytes. At the default ATT MTU of 23 a fragment carries 9 payload bytes. | `transport/ble/FragmentCodec.kt` |
| Frame/fragment caps | Max link frame 32 KiB, max 4096 fragments, 64 KiB reassembly memory per peer, 2 concurrent reassemblies per peer, 10 s reassembly timeout. | `ProtocolConfig` |
| Integrity below Noise | Not needed. Every frame after HELLO is a Noise frame, so corruption fails the AEAD and the session is closed. | — |
| Link-frame types | `0x01 HELLO` (plaintext: version + advertised short ID, used only for link collision resolution), `0x02 NOISE_HANDSHAKE`, `0x03 NOISE_TRANSPORT`. Nothing else is accepted, so no plaintext protocol frame can follow the handshake. | `protocol/LinkFrame.kt` |
| `PeerLink.peerSessionId` | The peer's advertised 8-byte rotating short ID, confirmed by HELLO. The 128-bit emergency session ID is not sent in plaintext; it would link rotations. | `transport/PeerLink.kt` |
| Collision rule | The smaller advertised short ID (unsigned lexicographic) initiates. The larger one waits `ROLE_WAIT_MS`, then may initiate once (role swap). If two links to the same peer form, the one whose initiator has the smaller ID is kept. | `transport/ConnectionRolePolicy.kt` |
| Noise prologue / payloads | Prologue `"itantra-v1/noise-hop"`. The GATT client is the initiator. Messages 2 and 3 carry CBOR `{1: protocol_major, 2: capability_flags}`. | `crypto/NoiseSession.kt` |
| Protocol frames (inside Noise) | Canonical CBOR map; key 0 = frame type. | `protocol/ProtocolFrames.kt` |
| Fingerprint | First 16 bytes of `SHA-256("itantra-v1/fingerprint" \|\| spk \|\| epk)`, shown as 8 groups of 4 hex digits. | `crypto/Fingerprint.kt` |
| QR payload | `ITANTRA1:` + base64url (no padding) of the signed capsule CBOR. | `identity/ContactQrCodec.kt` |
| Outer `bundle_kind` | Always `PRIVATE` in v1, for both messages and receipts. The message/receipt distinction lives inside the ciphertext so relays learn less. Padding buckets keep sizes coarse. | `protocol/BundleV1.kt` |
| Tombstone lifetime | `min(claimed remaining_lifetime, our own record's remaining lifetime)`, capped at the normal lifetime. | `dtn/TombstoneRouter.kt` |
| Seen-entry retention | Retained until the bundle's remaining lifetime plus a 1 h grace period, using wall clock *for dedupe cleanup only*. Bundle expiry uses the monotonic/boot-count age. | `dtn/` |
| Hop ACK semantics | `BUNDLE_ACK{status}` means only "I persisted these bytes" (or duplicate/rejected). Sender states: QUEUED → RELAYED on the first persisted ACK (relay or destination-claim copy). DELIVERED only after a verified signed receipt. | `dtn/` |

## 6. Android layer notes

| Topic | Decision |
|---|---|
| Foreground service | `EmergencyModeService`, type `connectedDevice`, `START_STICKY`. It restarts Emergency mode only if the user left it on. A refused background start is reported in the UI rather than crashing. |
| Bluetooth off/on | Radio work is torn down on `STATE_OFF` and restarted on `STATE_ON` while the service lives. |
| Startup sweep | `NetworkingRuntime.init` runs a DTN sweep immediately, then every 30 s. The first sweep after a reboot re-bases ages onto the new boot count (audit #8), so ageing resumes from the last persisted value. |
| GATT client | `connectGatt(TRANSPORT_LE)` → 150 ms settle → `discoverServices` → `requestMtu(517)` (failure keeps 23) → CCCD write → HELLO → READY. One op in flight, 5 s per op. Status 133 and other connect failures retry after 0.5 s / 1.5 s / 4 s. |
| GATT server | Accepts only non-prepared, offset-0 writes to ClientToServer. Pre-API-33 indications are serialised on the shared characteristic object. A superseded link for the same device never disconnects the newer connection. |
| Early frames | Frames that arrive after the peer's HELLO but before this side is READY are queued, not dropped. The first one is normally Noise message 1. |
| Scan filter | Service data under the iTantra UUID with mask on the version byte (`0x01`). This keeps screen-off scanning permitted. |
| Duplicate links | Links are keyed by the HELLO short ID. The link-layer address is used only inside `transport/ble` to avoid dialling a phone we already hold a link to. |
| RFCOMM | New `RfcommPeerLink` on its own UUID (`7a1c0010-…`), `u32` length-prefixed, always inside Noise, bonded phones only, never used for SOS. The legacy demo transport is untouched. |
| Metrics | JSONL at `<external files>/bench/events_<run>.jsonl`, CSV per benchmark at `bench/probes_<run>_<ts>.csv`. `t_ns` is `elapsedRealtimeNanos` on that phone only. |
| Lint | One pre-existing error remains, in legacy `BluetoothRfcommTransport.kt:313` (`MissingPermission`). It is identical at the base commit `230eab9`. |
