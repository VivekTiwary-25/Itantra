# Two-phone physical validation — plan and method

Date: 2026-09-25. Branch `feature/itantra-v1-networking`, base HEAD `64ba490`.
Phones: PHONE_A = realme RMX5108 (`VCU4AAP7KVN7FQGQ`), PHONE_B = realme RMX5070
(`a83e3811`). Both run Android 16 / API 36 / arm64-v8a. See `device_info.txt`.

## Ground rules

- The only application-data path is PHONE_A ⇄ BLE GATT ⇄ PHONE_B.
- The laptop does orchestration only: install, `am start`, `am broadcast` commands to the
  debug-only `ValidationReceiver` (DUMP-protected), `run-as cat` of result files, logcat.
- Contact setup: each phone's signed capsule string (the same content as its QR code)
  is copied by adb to the other phone, where the capsule signature is verified before
  it is trusted. This replaces a camera scan and is setup only; no message bytes move this way.
- Timing: every latency is measured on one phone's own `elapsedRealtimeNanos`. Values
  from two different phones are never subtracted.

## Gates

| Gate | Method | Evidence |
|---|---|---|
| 4 Radio | Start Emergency mode on both; poll state; read `peer_discovered`, `gatt_*`, `mtu_result`, `link_ready` | `events_*.jsonl`, logcat |
| 5 Noise / security | `noise_done` on both; encrypted probes; debug fault switches corrupt / replay the next Noise frame; expect the receiver to terminate the session and links to re-form | JSONL + `session_closed` events |
| 6 Trusted messages | `send` N tagged messages each way; sender records queue → RELAYED → DELIVERED on its own clock; receiver records delivery with text hash | `val.jsonl` → `direct_messages.jsonl` |
| 7 Fragmentation | Echoing probes with payloads 8 B, 200 B, 2 000 B and 16 000 B; success needs byte-identical echo | `probes_*.csv` → `fragmentation_results.json` |
| 8 Disconnect / reconnect | drop links, Bluetooth off/on, app force-stop + restart; measure time to a new session; send after each | `reconnect_results.json` |
| 9 DTN persistence | Queue while B is stopped → A shows Queued → restart B → Delivered on A, one delivery on B, tombstone removes A's copy; A process restart with a queued message | `dtn_results.json` |
| 10 SOS direct | B available-to-help, A starts SOS, B accepts, compare SAS, chat both ways | `val.jsonl` state lines |

## Incident during setup

A third iTantra node (advertised ID `0c849827…`, believed to be the RMX3392 left in
Emergency mode after the 2026-09-24 single-phone smoke test) linked to both phones at
first start. Vivek switched it off; tests start only after both phones show exactly one
session (A↔B) and the node has aged out of both scans.
