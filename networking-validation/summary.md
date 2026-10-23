# Two-phone physical validation — summary (2026-09-25)

**Phones:** PHONE_A realme RMX5108 (`VCU4AAP7KVN7FQGQ`) and PHONE_B realme RMX5070
(`a83e3811`), both Android 16 / API 36 / arm64-v8a.
**Path:** PHONE_A ⇄ BLE GATT ⇄ PHONE_B only. The laptop sent control commands and
read results. It never carried application data. Contacts were set up by copying each
phone's signed capsule (QR content) over adb; each phone verified the signature on receipt.
**Branch:** `feature/itantra-v1-networking`. Fixes are in commits `5dca7fc` … `87af871`.

Two phones cannot show multi-hop. A→B→C still needs a third phone.

## Gates

| Gate | Result |
|---|---|
| Radio: advertise, scan, mutual discovery, GATT, services, MTU, CCCD, HELLO | PASS. Cold start on B's clock: discovered 90 ms, connected 1.50 s, MTU 517 at 1.93 s, READY 2.16 s |
| Noise XX over real BLE | PASS on every session. Handshake 180–370 ms at MTU 517 |
| Tamper / replay | PASS. A bit-flipped frame and a replayed frame were each rejected with `Noise transport authentication failed`; the session was torn down and rebuilt |
| Identity / recipient checks | PASS. Capsule signatures verified on import; every delivery passed signature + recipient + envelope checks (0 rejections of genuine traffic) |
| Fragmentation / reassembly | PASS after fix. 8 B / 200 B / 2 000 B / 16 000 B, byte-identical echoes, both directions. Post-fix non-fault probes 284/287 (the 3 failures predate fix `87af871`). Before fix `5dca7fc`: multi-fragment 0/40 |
| Trusted messages A→B | 160/160 delivered, DELIVERED on sender, integrity OK, 0 duplicates |
| Trusted messages B→A | 105/105 delivered, DELIVERED on sender, integrity OK, 0 duplicates |
| Disconnect / reconnect | PASS after fixes (see `reconnect_results.json`) |
| DTN persistence (two phones) | PASS. Queued while the peer was off → survived a sender app restart → delivered once on reconnect → receipts → tombstones emptied both stores |
| SOS direct | PASS. Decline keeps searching; accept → encrypted + unverified on both phones, matching SAS, chat both ways, signed cancel |

## Latency (each measured on the sender's own clock)

Clean run (20 messages each way, one per 10 s):

| | queued → RELAYED (peer's hop ACK) median / p95 | queued → DELIVERED (signed receipt) median / p95 |
|---|---|---|
| A→B | 2.35 s / 3.55 s | 6.79 s / 7.96 s |
| B→A | 2.04 s / 3.65 s | 3.92 s / 6.60 s |

This is dominated by the 5 s minimum between inventory rounds, not the radio.
Probe RTT (encrypted, echoed): 200 B ≈ 52–104 ms median. 16 000 B ≈ 3.0–3.4 s median, which
is about 9.5–10.9 kB/s of payload moved per round trip (2 × payload / RTT, including Noise,
CBOR, fragment headers, GATT acks and both phones' processing).

Rate-limited runs are kept in `direct_messages.jsonl`. `A2B` holds the 34 messages
stalled by the lifetime-cap bug (up to 12 min). `A2Bp`/`B2Ap` were run before the
oldest-first fix. `A2Bq`/`B2Aq` show the 16 destination copies per session per minute
limit, where receipts count too: p95 queued→RELAYED is 58 s at 15 msgs/min each way.

## Bugs found on real phones and fixed

1. `5dca7fc`: fragments were 514 B at MTU 517; Android rejects attribute values over 512 B → every multi-fragment frame failed. A write throwing mid-frame also left the link READY.
2. `c25f758`: Android reuses one LE connection per phone pair. A stale server link ignored the re-subscribing client (no HELLO); the server was stuck at MTU 23; the duplicate rule kept a dead link and killed the peer's re-dial.
3. `ca403ff`: per-session lifetime caps starved a legitimate recipient after 16 messages; there was no retry while idle; newest-first ordering starved older messages.
4. `4b13e27`: a send on a dying link from the periodic round crashed the whole app.
5. `87af871`: a peer restarting with a new short ID led to a stale same-device link and double dials; two GATT clients on one device confused the peer's server.

Plus debug-only validation hooks (`df8bf3c`). JVM tests: 73/73. The earlier "73" was a
miscount of 69; +4 regression tests since.

## Not proven here

- Anything multi-hop: A→B→C relay, spray token splits across relays, the multi-hop SOS waves, two responders accepting at once.
- Screen-off operation: the phone stayed awake on USB; settings were not changed.
- Long-duration and Realme battery-optimisation behaviour.
- Product UI path (PTT → STT → recipient picker → TTS) on these phones. The validation drove `ItantraNetwork.queueTrustedMessage` directly.
