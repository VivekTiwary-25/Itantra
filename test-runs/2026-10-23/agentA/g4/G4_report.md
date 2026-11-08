# G4 report + T4B2 receipt watch (Agent A, 2026-10-23 16:05-16:10, read-only)

No taps and no Bluetooth changes. The only debug commands used were `capsule` and `state`.

## G4: PASS
- RMX5108 contacts (db\RMX5108_after_G4, and again db\RMX5108_after_G4_watch): exactly 1 row.
  `a63bb237e90f6e6e66ddcfc6b504fa8e | vimalesh | b9bd cc40 2eac acb1 65dd c754 af3f 33b1 | key_epoch 1 | QR_VERIFIED | created 16:03:49.141`
  The signing and encryption keys are byte-identical to the G1 row. The name is "vimalesh", as asked.
- I2221 contacts are unchanged: exactly 1 row, `27043a6b... | Utkarsh Keshri | e41c e39b 73d9 dbe5 0033 12cd ff66 0450 | QR_VERIFIED | created 15:34:39.418` (the same keys as G1/G3).
- Own fingerprints (`capsule`, 16:05, g4\capsule.txt): I2221 `b9bd cc40 2eac acb1 65dd c754 af3f 33b1`, RMX5108 `e41c e39b 73d9 dbe5 0033 12cd ff66 0450`. Neither is 91c4....
- Screenshots: g4\RMX5108\after_G4.png (Home, "Emergency mode on · 1 nearby", Logs badge 1) and g4\I2221\after_G4.png ("You" screen, where its QR was shown for the scan; Contacts "Utkarsh Keshri e41c...").

## T4B2 receipt after re-trust: NOT verified (prediction confirmed)
I watched from the G4 save (16:03:49) to 16:09:39. Polls ran at 16:05:21-16:09:36 (g4\state_poll.jsonl), and every one was identical:
- RMX5108: `outgoing {RELAYED=3, DELIVERED=4}`, `bundles 596de98a:LOCAL:t=2;a10b5860:LOCAL:t=2;0059cd96:RELAY:t=2`, contacts "vimalesh".
- I2221: `delivered 5`, `bundles 596de98a:RELAY:t=1;a10b5860:RELAY:t=2;0059cd96:LOCAL:t=2`.

Events since 16:03:49 (g4\*_bench\) show only frame and inventory traffic. All 12 RMX5108 `dtn_inventory_rx` lines are `"summaries":3,"wants":0,"tombstones_tx":0`. There was no dtn_bundle_tx/rx, no receipt and no tombstone. DB at 16:09 (db\RMX5108_after_G4_watch): outgoing 4d818151 is still RELAYED (updated 15:50:53.398), and `receipts` still has 4 rows, none for 4d818151. RMX5108 still holds 0059cd96 as a RELAY copy, with its seen row kept until 2026-10-24 16:50:58. a10b5860 is unchanged: RELAYED on RMX5108 and a RELAY copy t=2 on I2221. 596de98a is also unchanged.

Code reason: dtn/DtnNode.kt:183 in `planEncounter` skips any summary whose bundle is already stored or seen. That happens before the destination check at :185. I2221 advertises the receipt 0059cd96. RMX5108 already holds it (as a RELAY copy, stored before the contact was re-added) and has it in `seen`, so it never sends a DESTINATION want. As a result `acceptReceipt` (:346-366) never runs, and the message stays RELAYED. Nothing re-checks held RELAY copies when a contact is added. This is the same root cause as E1. Effect: T4B2 was delivered and spoken on I2221 at 15:50:53, but RMX5108 shows "Relayed ··" for it, and will keep doing so at least until the seen row expires (by then the bundle has expired too).

## Checks
- check_models.ps1 (all four phones, read-only): I2221 19 OK, RMX3392 19 OK, RMX5070 19 OK, RMX5108 19 OK.
- `logcat -g`: I2221 main and system 256 MiB; RMX5108 main and system 256 MiB.
- Battery (`dumpsys battery`): I2221 60 % and RMX5108 80 %, both not charging (AC/USB powered false, status 3).
- Final state: both phones Emergency ON and linked (mtu 517). RMX5108 is on Home. **I2221 is still on the "You" (QR) screen, not Home.** I did not tap it because this task was read-only. A single Back press on I2221 returns it to Home, if wanted.
