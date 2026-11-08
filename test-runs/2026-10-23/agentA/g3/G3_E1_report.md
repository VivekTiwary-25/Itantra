# G3 + E1 report (Agent A, 2026-10-23, read-only)

Phones: RMX5108 (adb-VCU4AAP7KVN7FQGQ-...) and I2221 (adb-10BF3C28T90015K-...). Nothing was tapped. The only commands used were `capsule`, `state`, run-as cat, screencap and logcat -d.

## G3: PASS
- I2221 contacts (db\I2221_after_G3, and again db\I2221_after_E1watch): exactly 1 row.
  `27043a6bc5f23b73a439283047a06575 | Utkarsh Keshri | e41c e39b 73d9 dbe5 0033 12cd ff66 0450 | key_epoch 1 | QR_VERIFIED | created 15:34:39.418`
  The signing and encryption keys and the node id are byte-identical to the G1 row (db\I2221_after_G1). Only created_at changed. There is no 43c5 row and no other extra row.
- RMX5108 contacts are unchanged from t4b1: 1 row, `a63bb237... | iTantra user | b9bd cc40 2eac acb1 65dd c754 af3f 33b1 | QR_VERIFIED | created 15:15:24.432`.
- Own fingerprints (`capsule`, 15:38): I2221 `b9bd cc40 2eac acb1 65dd c754 af3f 33b1`, RMX5108 `e41c e39b 73d9 dbe5 0033 12cd ff66 0450`. Neither is 91c4.... localShortIds are 3e8925a5 and 4e6943fa.
- Screenshots: g3\I2221\I2221_after_G3.png (the "You" screen with Contacts "Utkarsh Keshri e41c e39b 73d9 dbe5...") and g3\RMX5108\RMX5108_after_G3.png (the "You" screen, own fingerprint e41c...).
- Note: RMX5108's MainActivity resumed at 15:34:35-15:34:44 (logcat MTK_APPList). This fits its QR being shown for the scan.

## E1: CONFIRMED (never delivered)
I watched from the G3 save (15:34:39) to 15:45:48, about 11 minutes. Both phones stayed linked the whole time (BLE_GATT, mtu 517).
- `state` was polled every 60 s from 15:38:37 to 15:44:39 (g3\e1_state_poll.jsonl). Every reading was the same:
  - I2221: `bundles 596de98a:RELAY:t=1;a10b5860:RELAY:t=2`, `delivered 4`
  - RMX5108: `outgoing {RELAYED=2, DELIVERED=4}`, `bundles 596de98a:LOCAL:t=2;a10b5860:LOCAL:t=2`
- Bench events after 15:34:39 (g3\*_bench\): I2221 has only frame_tx/rx and 22 dtn_inventory_tx. All 23 `dtn_inventory_rx` lines are `"summaries":2,"wants":0,"tombstones_tx":0`. RMX5108 mirrors this. After G3 there was no dtn_bundle_tx/rx, no delivery and no receipt. The last `dtn_bundle_rx` on I2221 is still the 15:18:39 relay copy: `{"wall_ms":1792748919614,...,"ev":"dtn_bundle_rx","mode":"RELAY","bytes":1195,"ack":"PERSISTED"}`.
- DB after the watch (db\*_after_E1watch):
  - I2221 `delivered` still has 4 rows (S2 A, S3T3, S3T6, S3T7). There is no row for 596de98a or a10b5860, and no tombstone for them.
  - RMX5108 `outgoing` 596de98a is RELAYED (updated 14:15:42) and a10b5860 is RELAYED (updated 15:18:39). `receipts` has 4 rows, none for these two bundles.

### Why (code: app/src/main/java/com/chmod777/itantra/dtn/DtnNode.kt)
- In `planEncounter`, DtnNode.kt:183 runs `if (store.bundle(storageKey) != null || store.seen(storageKey) != null) continue`. This comes before the destination check at :185 (`crypto.matchDestinationTag(...)`). I2221 already holds both bundles as RELAY copies and has `seen` rows for them, so it skips them and never sends a DESTINATION want. That is why every inventory shows `wants:0`. Restoring the contact would make :185 match, but :185 is never reached.
- `ingestAsRelay` (DtnNode.kt:263-294) stores the bundle as RELAY and marks it seen (:288-289). It never checks whether the bundle is addressed to this node.
- The only place a bundle is turned into a delivery is `ingestAsDestination` (DtnNode.kt:301-322 -> acceptMessage :325-345). It runs only for a frame received with mode DESTINATION. There are only two call sites for destination matching: DtnNode.kt:185 and :302. I found no path (on contact add, sweep :429ff, or anywhere else) that re-checks held RELAY copies against the new contacts.
- The seen rows are kept until 2026-10-24 15:15:42 (596de98a) and 16:18:39 (a10b5860), which is 25 h after receipt. So even if the relay copy were dropped, :183 would still skip the bundle until the seen row expires. That is probably after the bundle's own lifetime, but I did not check the lifetime config.
- Engine bug (for the foreman; NOT fixed): a message that reaches its real recipient as a RELAY copy, while the recipient does not trust the sender, is never delivered after trust is restored. The sender stays "Relayed" with no receipt.

## Other observations
- The I2221 contact name is "Utkarsh Keshri", which is RMX5108's self name ("kept the name"). This is as expected.
- No new UI bug was seen in this read-only step.

## Addendum (15:57, found during T4B2)
In RMX5108's outgoing table, 596de98a (S3T4) has recipient_node_id 4b8ce00f1be19228ddfedff1b0b4fa41. That is I2221's OLD identity, from before the approved reset. a10b5860 (T4B1) has recipient a63bb237 (the current I2221). So 596de98a could never be delivered to the current I2221, whatever the trust. The real E1 case (a sealed message addressed to the current identity, held as a RELAY copy and never delivered after re-trust) is a10b5860 only. The verdict for that bundle does not change.
