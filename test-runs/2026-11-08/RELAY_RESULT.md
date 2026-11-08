# RELAY_RESULT — three-phone test, 2026-11-08

Build `b460de9` (APK MD5 `1f14a7e87d12591bc6298488d6a1a79e`) on all three phones. Nothing is GREEN.

| Name | Phone | Serial | Bluetooth short id during the test |
|---|---|---|---|
| vivek (sender) | realme RMX3392, Android 14 | EILZRCFQLZIZ6H6P | `5c3a5956` |
| pratham (receiver) | vivo V2059, Android 13 | 1370831502000ZH | `d19025c2` |
| umar (middle) | vivo I2221, Android 16 | 10BF480LL50024U | `27ab92cd` |

Logs were cleared on all three at 17:58:51 and pulled at 18:14:31. Files are in `relay/`:
- `<name>.logcat.txt`: `logcat -d`;
- `<name>.events/`: the app's events files;
- `timeline_all.txt`: the three event logs merged, one line per event;
- `BASELINE.txt`.

Times are each phone's own clock. Comparing clocks: pratham runs about 1 s ahead of the other two. The cross-phone
gaps below are therefore about ±1 s; times on the same phone are exact.

## 1. Verdict in one line

**Umar carried every message and could not read any of them. But this was not a clean relay test: vivek and pratham
were still connected to each other directly the whole time.** So the test does not prove delivery when the two
ends cannot hear each other.

## 2. What happened

- 18:02:36: Emergency mode ON on umar. It found and linked to both vivek and pratham within 2 s.
- **The Bluetooth link vivek ↔ pratham from the two-phone test (opened 17:39:15) never dropped.** It carried traffic
  during the test and was still up at 18:14:31:
  - vivek: link `ble-c-3244f9` to `d19025c2`, 48 frames between 18:10 and 18:13;
  - pratham: link `ble-s-89f682` from `5c3a5956`, 47 frames.

  vivek's Home screen should have shown **2 nearby**, not 1. A link that is already open survives at a range where two
  phones would not find each other fresh, so moving pratham away did not break it.
- **Three messages, all vivek → pratham:**

| Message | vivek queued | vivek → umar (sealed relay copy) | umar → pratham | pratham shows it | Second copy, direct from vivek | Receipt back on vivek |
|---|---|---|---|---|---|---|
| `46a4822a`, en, typed | 18:10:35.145 | 18:10:35.397 | 18:10:35.899 | 18:10:37.021, spoken | 18:10:37.557, refused as duplicate (TOMBSTONED) | 18:10:38.177 (3.0 s after sending) |
| `c685e982`, hi, push-to-talk | 18:11:17.676 | 18:11:18.030 | 18:11:18.477 | 18:11:19.596, spoken | 18:11:20.096, refused as duplicate | 18:11:21.108 (3.4 s) |
| `27fdeb5d`, en, short | 18:11:53.134 | 18:11:53.321 | 18:11:53.634 | 18:11:54.643, spoken | not needed | 18:11:59.922 (6.8 s) |

- **The copy pratham accepted came from umar every time.** The frame that carried it arrived on pratham's link from
  `27ab92cd` (umar). For the first two messages, vivek's direct copy came about 1.5 s later and was correctly refused
  as a duplicate, so each message was shown and spoken once.
- Receipts went both ways: pratham gave umar a sealed relay copy of each receipt and also sent it straight to vivek.
  vivek accepted the direct one.
- **pratham → vivek was not sent in this test.** pratham's events have no `product_queued` after 17:58. Its sent
  count is still the 3 messages from the two-phone test.

## 3. Could umar read or show them? No.

- Umar received every message only as `dtn_bundle_rx mode RELAY ack PERSISTED`, meaning "stored for someone else".
  It never asked for one as its own.
- Umar logged no `product_delivered` and no text-to-speech events. Its delivered count is 0 and it has sent nothing.
- Its held relay copies are gone now (bundle store empty). Pratham's receipts reached umar, so umar dropped its copies.
- None of the three message texts (taken from pratham's delivered list) appears anywhere in the three logcats or the
  three events logs.
- What a relay can see is the padded size class: 1195 B for normal messages, 427 B for very short ones such as
  `27fdeb5d`. That is how the padding is designed, not a leak found today.

## 4. Errors and oddities

- **No crashes, no ANRs, no app errors** (`ITANTRA` tags at E level: none). No link failures and no failed sync
  rounds in any events file.
- 18:02:57: two `server_link_unused_detached` on umar. This is the known E2 (an unused second link closes after 20 s).
  Harmless.
- `dtn_tombstone_rx applied=false` on umar a few times: a tombstone it already had. Harmless.
- vivek handed umar a relay copy even though pratham was directly linked. That's how Spray-and-Wait works: a copy
  goes to a relay before the destination asks. The cost is one extra copy; nothing breaks.
- **pratham's logcat is almost empty** (330 lines, all errors from other apps). This vivo hides logs below ERROR, so
  for this phone the app's events file is the only useful record. This matters for the long-logs plan.
- An `AndroidRuntime: VM exiting` line in umar's logcat is from a vivo system process (`com.qti.qcc`), not iTantra.

## 5. What this proves and what it does not

**Proven on phones (this build):**
- A middle phone that trusts both ends stores the message sealed, passes it on to the right recipient, and never shows
  or speaks it.
- The recipient takes the relayed copy, shows and speaks it once, and refuses the second copy.
- The sender reaches Delivered through the receipt in 3–7 s.

**Not proven:**
- Delivery when vivek and pratham cannot hear each other at all.
- The reply direction (pratham → vivek) through umar.

## 6. To make the next run a clean relay test

1. Before spreading out, tap **STOP** on vivek and pratham (Emergency mode off). That closes their direct link.
2. Place pratham far away, then START on pratham. Place vivek, then START on vivek. Wait 2–3 minutes with umar's
   Emergency mode **off**. Both should show **0 nearby**; if either shows 1, they can still hear each other.
3. Only then START umar in the middle and check the counts: vivek 1, umar 2, pratham 1.
4. Send vivek → pratham, then pratham → vivek.
5. Carry test, optional: umar STOP, send, walk umar from vivek to pratham, START near pratham.
