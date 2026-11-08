# Test 4b direction 2: sender removes receiver while a message is in flight (Agent A, 2026-10-23 15:47-15:57)

Phones: RMX5108 (sender) and I2221 (receiver). Both run commit 4729bd2. Nothing was fixed or edited. The only debug commands used were `state` and `capsule`. Screenshots are in t4b2\<phone>\NN_before_* / NN_after_*, in order. Start markers are in t4b2\markers.txt and every `state` reading is in t4b2\state_poll.jsonl. DB copies: db\*_t4b2_before, db\RMX5108_t4b2_queued, db\RMX5108_t4b2_removed, db\*_t4b2_after. Bench events and logcat: t4b2\*_bench\, t4b2\*_t4b2_logcat.txt.

## Timeline (phone wall clock)
| time | what | evidence |
|---|---|---|
| 15:47:59 | Start. Both linked. RMX5108 `{RELAYED=2, DELIVERED=4}`. I2221 delivered=4. | markers.txt, the last bench line on each phone |
| 15:48:16 | I2221 BT off by adb (`cmd bluetooth_manager disable` -> Success). `bluetooth_on=0`, manager `enabled: false`, `state: BLE_ON` (low-energy-only mode, as in direction 1). I2221 `state`: `emergency: Error(Bluetooth turned off. Emergency radio work paused.)` | 01_bt_off.png |
| 15:48:17 | RMX5108: `link_disconnected ... central disconnected status=0`, then `short_id_rotated` (4e6943fa -> 58296b50). It kept trying to connect to the BLE-only chip: `gatt_connect_failed` x4 (status -3/147), then `link_failed`. | RMX5108 bench |
| 15:49:35 | RMX5108 Write message, To "iTantra user", typed `T4B2 sender removes receiver`, Send. Result: `product_queued bundle 4d818151`. The outgoing row is `4d818151... QUEUED`, recipient a63bb237 (I2221). `state` shows `{QUEUED=1,...}` and bundle `4d818151:LOCAL:t=4`. | 03-05_*.png, db\RMX5108_t4b2_queued |
| 15:50:2x | RMX5108 You > Contacts > More > Remove. The dialog reads: "Remove vimalesh? You will not be able to send them anything new, and they disappear from your contacts. A message you already sent is sealed and on its way: removing them does not call it back, and they will still receive it. ..." I tapped Remove. Afterwards Contacts shows "Nobody yet..." and the contacts table is empty, but the outgoing row is still QUEUED and bundle 4d818151 is still stored (LOCAL t=4). | 07-09_*.png, db\RMX5108_t4b2_removed |
| 15:50:46 | I2221 BT on by adb (`enable: Success`, `bluetooth_on=1`, `state: ON`). I2221 `emergency_on` restarted by itself at 15:50:45.737. | 10_before_bt_on.png |
| 15:50:52.66 | Link ready (mtu 517) and Noise handshake done. | both bench files |
| 15:50:53.240 | RMX5108 `dtn_bundle_tx mode DESTINATION bytes 1195 tokens 0` | RMX5108 bench |
| 15:50:53.268 | I2221 `product_delivered bundle 4d818151 lang en` | I2221 bench |
| 15:50:53.316 / 15:50:56.844 | I2221 `product_tts_start lang en` / `product_tts_returned lang en`. The message was spoken (judged from the logs only). | I2221 bench |
| 15:50:53.350 | I2221 `dtn_bundle_rx mode DESTINATION ack ACCEPTED_AS_DESTINATION_ATTEMPT` | I2221 bench |
| 15:50:53.398 | The RMX5108 outgoing row for 4d818151 becomes RELAYED (via the hop ACK). | db\RMX5108_t4b2_after |
| 15:50:53.440 | RMX5108 `dtn_tombstone_rx applied:true`. RMX5108 drops bundle 4d818151 and stores a tombstone for it. | RMX5108 bench + DB |
| 15:50:58.57 / .75 | I2221 sends its signed receipt (bundle 0059cd96) as `dtn_bundle_tx mode RELAY bytes 427 tokens 2`. RMX5108 `dtn_bundle_rx mode RELAY ack PERSISTED`. RMX5108 stored its own receipt only as a RELAY copy. | both bench files |
| 15:51:10-15:56:26 | Six `state` polls, all identical. I2221 delivered=5, bundles `...;0059cd96:LOCAL:t=2`. RMX5108 `{RELAYED=3, DELIVERED=4}`, bundles `596de98a:LOCAL;a10b5860:LOCAL;0059cd96:RELAY`, contacts "". | state_poll.jsonl |

## Facts
1. Yes, the T4B2 message was sent after the contact was removed, 77 s after removal and 6.5 s after I2221's Bluetooth came back. It was sent in DESTINATION mode, not RELAY.
2. It was delivered on I2221. The `delivered` row is `4D818151... | sender 27043A6B... | T4B2 sender removes receiver | 15:50:53.255`. It shows in I2221's Logs as "Utkarsh Keshri, 1m, T4B2 sender removes receiver" (13_logs.png). It was spoken according to the logs (`product_tts_start` -> `product_tts_returned`, 3.5 s).
3. RMX5108 shows the message as "You: T4B2 sender removes receiver — Relayed ··", with the recipient shown as the raw hex id "a63bb237" (15_logs.png). The outgoing state is RELAYED, the `receipts` table still has 4 rows, and there is none for 4d818151. The label stayed the same through 15:56:26.
4. The two old stuck bundles are kept separate. 596de98a (S3T4) and a10b5860 (T4B1) are unchanged: RMX5108 holds them LOCAL t=2 / RELAYED, and I2221 holds them RELAY t=1 / t=2. They were not touched in this test.
5. Final state as asked: I2221 BT ON (`bluetooth_on=1`), both phones Emergency ON and linked ("Emergency mode on · 1 nearby"), RMX5108 on Home, I2221 on Home. The contact was NOT re-added. RMX5108 now trusts nobody, and I2221 still trusts RMX5108 ("Utkarsh Keshri", e41c...).

## Why (code)
- `AppState.removeContact` (app/src/main/java/com/chmod777/itantra/ui/app/AppState.kt:541-546) only deletes the contacts row (`NetDatabase.remove`, dtn/sqlite/NetDatabase.kt:115) and clears the selected recipient. It does not touch `outgoing` or `bundles`. The queued sealed bundle stays in the store.
- `DtnNode.inventory()` (dtn/DtnNode.kt:143-160) and `serveWant` (DtnNode.kt:206-226, the DESTINATION branch at :212) never look at the contact list. The bundle is advertised and served to whoever claims it as destination.
- I2221 still trusts RMX5108, so its `planEncounter` matches the destination tag (DtnNode.kt:185) and opens the message (ingestAsDestination :301-322, acceptMessage :325-345). TTS runs from AppState.kt:788-790.
- The sender's state is set to RELAYED by `onAck` (DtnNode.kt:371-383) on ACCEPTED_AS_DESTINATION_ATTEMPT. Only a verified receipt moves it to DELIVERED (`acceptReceipt`, DtnNode.kt:346-366). That requires the receipt to match a trusted contact: planEncounter :185 for the tag, and :352 `recipientNodeId == opened.from.nodeId`. RMX5108 has no contact any more, so it requested I2221's receipt 0059cd96 only as a RELAY copy (DtnNode.kt:188 -> ingestAsRelay :263-294) and never opened it.
- The UI text is intentional. SettingsScreen.kt:316-322 says "removing them does not call it back, and they will still receive it."

## Judgement (Opus)
- **Delivery after removal: acceptable and by design. It is not a leak.** The message was encrypted for I2221's keys while the user still trusted I2221. Only I2221 can open it, and the Remove dialog tells the user exactly this before they confirm. No third party gained anything. If the team wants "remove = cancel unsent messages", that is a product decision: removeContact would have to delete or tombstone the matching QUEUED outgoing bundles. I am reporting it, not fixing it.
- **Bug (engine/UI, NOT fixed): the sender shows "Relayed ··" for a message that was delivered and spoken.** RMX5108 even received and applied the destination's tombstone at 15:50:53.440, and it holds the signed receipt (0059cd96) as a relay copy. Because the contact is gone, the receipt can never be verified, so the label is stuck. It is also likely (my prediction, not tested) that it stays "Relayed" after G4 re-adds I2221. The receipt 0059cd96 is already in RMX5108's bundles/seen, so the same skip that causes E1 (DtnNode.kt:183) would stop it from being opened as destination. Watch for this after G4.
- **UI bugs, not fixed:**
  - (a) The RMX5108 Settings contact list and the Remove dialog showed the contact as "vimalesh", but the DB local_name was "iTantra user" (since Test 5 at 15:15) and the composer showed "iTantra user" (06_after_you_scrolled.png, 08_remove_dialog.png vs db\RMX5108_t4b2_before). The list shows a stale name; the cause is unclear.
  - (b) After removal, the Logs show the recipient only as the raw hex "a63bb237" (and older rows "4b8ce00f").
  - (c) The Remove dialog shows no fingerprint (as already noted in G2).
- **Side notes:**
  - With BT "off", vivo keeps BLE in BLE_ON mode, the same as direction 1. RMX5108 kept trying to GATT-connect and failed (status -3/147); there was no harm.
  - RMX5108 rotated its short id to 58296b50 and I2221 rotated to 2966eea9 after the reconnect. That is expected rotation and is not identity loss: own fingerprints are unchanged per `capsule` at 15:38.
- **Correction to the earlier E1 note:** outgoing 596de98a (S3T4) is addressed to recipient 4b8ce00f, which is I2221's OLD identity from before the approved reset. It could never be delivered to the current I2221 (a63bb237), whatever the trust. The real E1 case is a10b5860 (T4B1, recipient a63bb237).
