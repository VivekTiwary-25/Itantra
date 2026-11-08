# iTantra test day 2026-10-23: runbook for Stages 4, 5 and 6

Written by Agent C (planning only, no phones). Build under test: commit `4729bd2`, branch
`test/e2e-oct23`, package `com.chmod777.itantra`. Everything below comes from reading that
code. File:line references point at `app/src/main/java/com/chmod777/itantra/` unless a
different path is given.

Hard rules (from Vivek, unchanged): do not edit the networking/crypto engine; do not fix
anything during a test; no uninstall, clear-data or wipe without asking Vivek; never mark
GREEN in STATUS.md; say "unclear" when unsure and show the evidence. When a step needs Vivek's
hands, the agent stops and tells the foreman, and the foreman gives Vivek ONE instruction
and waits.

---

## 0. Read this first: five facts that change how we test

1. **The engine's events are NOT in logcat.** Every networking event (`link_ready`,
   `noise_done`, `dtn_bundle_rx`, `sos_offer_rx`, and the rest) goes only to a JSONL file on the phone:
   `/sdcard/Android/data/com.chmod777.itantra/files/bench/events_<run>.jsonl`
   (`metrics/JsonlMetricsRecorder.kt:16-53`, `service/NetworkingRuntime.kt:178-180`).
   The old two-phone run's 131 MB logcat holds zero `noise_done`/`link_ready` lines.
   Logcat only has `ITANTRA_TTS`, `ITANTRA_LATENCY`, `ITANTRA_TRANSPORT`, Android's Bluetooth
   lines and crashes. **Always pull the bench folder as well as logcat.** `pull_and_scan.ps1` does both.
2. **The JSONL keeps everything since install, across restarts.** Earlier stages (where the
   same phones were side by side) are in the same files. Each agent writes down the phone's clock
   at the start of each stage (`adb -s <id> shell date +%s`, then multiply by 1000) and scans
   with `-SinceWallMs <that number>`. Never compare `t_ns` between two phones.
3. **"Relayed" on the sender does not prove a relay hop.** The sender switches to Relayed on
   the first hop ACK. That includes a direct hand-over to the recipient (`dtn/DtnNode.kt:376-383`).
   The hop is proven only by the middle phone's own log lines (`dtn_bundle_rx … RELAY … PERSISTED`
   then `dtn_bundle_tx … DESTINATION`) together with proof that the outer phones never linked.
4. **Both ends must trust each other. The middle phone needs no trust at all.** The receiver
   only recognises a message as "for me" if the sender is in its own contacts
   (`dtn/DtnNode.kt:185` and `:302`). The sender can only address someone in its contacts (`:97-98`).
   The middle phone takes any bundle as an opaque RELAY copy when it has more than one copy
   token (`dtn/SprayRouter.kt:27`, `DtnNode.kt:188`). It cannot decrypt it.
5. **There are no log events for some things we want to prove:** the sender turning
   Relayed/Delivered, the requester receiving an SOS decline/confirm/busy, SOS chat
   lines, and the 6-digit code. These are proven from the screen (screenshots) only. See §6.

---

## 1. Phones, roles, owners

| Phone | Android | adb id | Owner |
|---|---|---|---|
| RMX5108 | 16 | `adb-VCU4AAP7KVN7FQGQ-sbN1ac._adb-tls-connect._tcp` | Agent A |
| I2221 (vivo) | 16 | `adb-10BF3C28T90015K-eEfTYq._adb-tls-connect._tcp` | Agent A |
| RMX5070 | 16 | `adb-a83e3811-XIQIEi._adb-tls-connect._tcp` | Agent B |
| RMX3392 | 14 | `adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp` | Agent B |

Proposed roles for Stage 4 and 5C (the line):

- **S, the sender, is RMX5108** (Agent A).
- **R, the receiver, is I2221** (Agent A). So Agent A watches both ends.
- **M, the middle, is RMX3392** (Agent B) **if** its Bluetooth permission is granted and its battery is at least 40%.
  At Stage 0 it had no permissions, 25% battery and was never opened. Otherwise use **RMX5070**,
  and Agent B first records RMX5070's contact list, because RMX5070 holds old 25-Sept data
  that may include a contact for RMX5108.
- **The fourth phone must have Emergency mode OFF for all of Stage 4 and 5C.** If it is on,
  it becomes a second relay and the test means nothing.

Known phone state from Stage 0 (`test-runs/2026-10-23/stage0/stage0_summary.md`): I2221 has
no camera permission (needed to scan a QR) and no mic. RMX5070 and RMX3392 were at 23-25%
battery. Both need charging before Stage 6.

Where things are on the phone (main app):

- Home has an Emergency mode line. "Emergency mode on · N nearby" means N live encrypted links
  (`ui/app/HomeScreen.kt:311-321`).
- **Settings** (the "You and your contacts" icon at the top of Home) has the QR code, "Your fingerprint", the "Available to help nearby users"
  switch and Contacts with **ADD**. ADD scans the QR, then "These match", then a name.
- **Net Lab (diagnostic):** Settings → Developer → "Contacts & network", or by adb
  `adb -s <id> shell am start -n com.chmod777.itantra/.ui.lab.NetworkLabActivity`.
  It shows "my short id=…", the Nearby list with RSSI, sessions with `mtu=`, Outbox/Inbox,
  **"Carried bundles (ciphertext only)"** and the SOS cards. It has no QR or trust buttons any
  more (`ui/lab/NetworkLabActivity.kt:215-226`). Contacts are added only in Settings. PHYSICAL_TEST_PLAN.md
  is out of date on this point.

---

## 2. Before every stage (agents, no hands needed)

For each phone it owns, the agent:

1. Checks that the build is still `4729bd2` (same APK MD5 as Stage 0: `627c9132…`).
2. Writes down the phone clock: `adb -s <id> shell date +%s` (seconds; × 1000 = `SinceWallMs`).
3. Takes a screenshot of Home (Emergency line) and of Settings → Contacts.
4. Checks battery: `adb -s <id> shell dumpsys battery` (level, temperature, plugged state).
5. Confirms mobile data is off. The main manifest asks for no INTERNET permission
   (`app/src/main/AndroidManifest.xml`), so the app cannot use the internet anyway.
   Wi-Fi stays on, because wireless adb needs it.

After every stage, each agent pulls its own phones:

```
powershell -ExecutionPolicy Bypass -File D:\projects\sih\iTantra\test-runs\2026-10-23\agentC\pull_and_scan.ps1 `
  -OutDir D:\projects\sih\iTantra\test-runs\2026-10-23\agentA\stage4 -Phones "RMX5108,I2221" -SinceWallMs <ms>
```

Add `-Marker "<marker word>" -PullDb` for the privacy check in Stage 4. `-PullDb` only makes
a read-only copy through `run-as … cat`. Logcat is a ring buffer, so pull right after each stage
or early lines may be gone.

---

## 3. Stage 4: three phones in a line, and the message hops through the middle

### Purpose
Prove that a trusted message S to R travels S → M → R when S and R cannot hear each other,
that the receipt comes back R → M → S, and that M never shows, speaks, logs or stores the text.

### Set up (agents first, then Vivek)
- **Trust:** S must have R in its contacts, and R must have S in its contacts. M must have
  neither. If someone is missing, Vivek does the QR swap (below) **with Emergency mode OFF on
  S and R**, before they are moved apart.
- **Message text:** one marker word that has never been used on any phone, for example
  `KESTREL41`. Use typed text so the marker is exact. RMX5070 already has old plaintext
  test messages in its database.
- **Place:** two rooms that are far apart. Pick it this way: S and R about **25-30 m apart
  with at least two solid walls (brick or concrete) between them**, or on different floors. M
  stands where it can reach both: about 8-12 m from each, with at most one wall in the way (a
  doorway or a corridor corner works well). This is a starting point, not a promise. BLE range
  indoors changes a lot with walls and bodies, so **we prove the gap with the pre-check below,
  we don't assume it.** Put each phone on a table or chair about 1 m high, screen up, not in
  a pocket and not in a hand. Check that wireless adb still reaches every spot. If it doesn't,
  the evidence stays safe in the JSONL on the phone and is pulled later.

### QR swap (only if trust is missing). Vivek's hands, about 3 minutes
1. Hold S and R side by side. On I2221, allow the camera if it asks.
2. On R: Settings → ADD. Point it at S's QR (shown at the top of S's Settings).
3. Read S's "Your fingerprint" aloud. If all 8 groups match, tap **These match**. Name it `S-RMX5108`.
4. The same the other way: on S, Settings → ADD, scan R's QR, compare, **These match**, name it `R-I2221`.

PASS: each phone lists the other under Contacts. Log line: none. Trust is not logged, so take screenshots.

### Steps (Vivek's hands)
1. **Pre-check that S and R cannot hear each other.** M stays OFF.
   - Put R at its far spot. On R tap **START** on the "Emergency mode off" line.
   - Walk to S's spot. On S tap **START** on the "Emergency mode off" line.
   - Step away from both and **wait 3 minutes**. Touch nothing.
   - Agents check (no hands): Home on S and R reads "Emergency mode on" with **no "· N nearby"**.
     The Net Lab Nearby list says "No iTantra advertisements seen yet."
     JSONL: no `peer_discovered` and no `link_ready` on S or R since the stage clock.
   - If S and R see each other: **FAIL of the placement, not of the app.** Move them further
     apart or put another wall between them, then repeat step 1.
2. **Start the middle.** Place M at the middle spot and tap **START** on the "Emergency mode off" line.
   Wait **60 seconds**.
   - PASS: M shows "· 2 nearby". S and R each show "· 1 nearby".
3. **Send.** At S: Home → **Write message**, tap **To / Select trusted contact** and pick **R**, type `KESTREL41 one`, tap **Send**.
   - S → Logs shows the row `Queued ·`, then `Relayed ··`, usually within about 5-30 s.
4. **Wait up to 2 minutes** without moving anything (the anti-entropy round is 30 s:
   `protocol/ProtocolConfig.kt:66`).
   - R speaks the message and its Logs shows it from S, once.
   - Then S's row turns `Delivered ✓✓`.
5. Vivek walks to M and looks only. There is no new row in Logs, no sound, and the unread
   count is unchanged. Optional: open Net Lab on M by adb. "Carried bundles" shows RELAY entries
   with `hops=1`, and the Inbox has nothing new.
6. Repeat step 3 four more times (`KESTREL41 two` … `five`), about 20 s apart. That makes 5 in total.
7. Optional, transport only, no trust needed: on S, Net Lab → Transport benchmark → choose S's
   only session, Trials 30, Hops 2, Payload 200, Run. This measures S → M → R → M → S.
8. Leave everything running. Agents pull (§2) with `-Marker KESTREL41`. Agent B adds `-PullDb` for M.

### PASS looks like
| Point | Screen | JSONL proof (real names) |
|---|---|---|
| S and R never linked | Nearby counts 1 / 2 / 1 | S: every `peer_discovered`/`link_ready` `peer` is M's ID. R: the same. M: `link_ready` to two different peers. The script's "BLE short IDs" block shows this. |
| Links are encrypted | session line `mtu=… noise=…ms` in Net Lab | `mtu_result`, `link_ready` (`mtu`), `noise_done` (`handshake_ns`) on all three |
| S hands a copy to M | S row Relayed | S: `product_queued`, then `dtn_bundle_tx` `"mode":"RELAY"` `"tokens":2` |
| M stores ciphertext | Net Lab Carried bundles: RELAY, tokens=2, hops=1 | M: `dtn_bundle_rx` `"mode":"RELAY"` `"ack":"PERSISTED"` |
| M gives it to R | — | M: `dtn_bundle_tx` `"mode":"DESTINATION"`. R: `dtn_bundle_rx` `"mode":"DESTINATION"` `"ack":"ACCEPTED_AS_DESTINATION_ATTEMPT"`, then `product_delivered` |
| R shows and speaks once | R Logs, audio | R: `product_delivered` ×5, `product_tts_start` ×5. Logcat: `ITANTRA_TTS … speak lang=en textLen=…` |
| M drops its copy | Carried bundles shrinks | M: `dtn_tombstone_rx` `"applied":true` |
| Receipt goes back through M | S row Delivered | M: a second `dtn_bundle_rx` RELAY PERSISTED (the receipt) and `dtn_bundle_tx` DESTINATION. S: `dtn_bundle_rx` `"mode":"DESTINATION"` with **no** `product_delivered` after it (a receipt, not a message). There is no "delivered" event on the sender (fact 5). |
| M never sees plaintext | nothing on M | M: **zero** `product_delivered`, **zero** `product_tts_start`, **zero** `ITANTRA_TTS … speak` / `[TTS]` lines. Marker hits: 0 in M's logcat, 0 in M's JSONL, **0 in M's database** (`itantra_net.db` and `-wal`). Every phone: 0 marker hits in logcat and JSONL. The S and R databases DO hold it in their own `outgoing`/`delivered` tables (`dtn/sqlite/NetDatabase.kt:31-33`). That is expected. |

### FAIL looks like
- Any marker hit in M's logcat, JSONL or database. Any `product_delivered` or TTS line on M. A row in M's Logs. → **Engine/privacy bug.** Stop and report the lines.
- R shows a message twice, or a message from someone it never trusted.
- `dtn_bundle_rx … "ack":"REJECTED"` on M or R with nothing delivered.
- S shows `Delivered` before R ever received it.
- `uncaught_coroutine_exception`, `FATAL EXCEPTION`, `noise_failed`.

### UNCLEAR looks like (write down, don't call it pass or fail)
- S or R discovered a peer ID that is not M's. Maybe it was the other outer phone (placement
  failed), or maybe M's short ID rotated. IDs only rotate after 15 minutes **with no links**
  (`transport/ble/BleLinkManager.kt:256-263`). Look at M's "my short id" in Net Lab.
- Delivered on R, but S stays `Relayed` for more than 5 minutes (the receipt did not come back).
  Pull all three and check M for the second RELAY bundle.
- `link_disconnected` / `session_closed` repeating on M (an unstable middle link).

---

## 4. Stage 5: SOS between phones

What the code does (`sos/SosManager.kt`):

- **Direct only:** offer (`SOS_OFFER`), accept, decline, confirm, busy, chat and end. They go over
  the one encrypted link between the two phones. No code forwards them: `onOffer` 306-334,
  `onSimple` 336-384 and `onChat` 410-422 never pass a frame on.
- The requester offers only to phones that are linked **and** advertise "Available to help" **and**
  have a recent RSSI (`:195-201`): 3 phones in wave 0, up to 8 from 10 s on.
- **Multi-hop is a notice only:** from 25 s (hop limit 2) and 60 s (hop limit 3) the requester
  floods a signed `SOS_REQUEST` (`:220-232`). Every phone forwards it once, whether or not it is
  "available" (`:449-457`). A far phone that is available shows a **"Relayed emergency
  request"** card with **no Accept/Decline** (phase `RELAYED_NOTICE`, `sessionKey = null`,
  `:439-446`; `respond()` refuses it at `:286`). Cancel (`SOS_CANCEL`) is also forwarded (`:469`).
  Relays can read the category and language of a request. That is by design.
- With no helper, the requester gives up after 2 min TTL + 60 s = **180 s** (`:189-191`), and logs `sos_end` with `"reason":"EXPIRED"`.
- The relayed notice **does not show in the main app**. The Home strip counts only OFFERED
  cards (`ui/app/ItantraApp.kt:300`, `ui/app/HomeScreen.kt:202`). Use Net Lab to see it.

### 5A: direct SOS: decline, then accept, code, chat
**Set up.** Requester RMX5108 and helper RMX5070, about 1 m apart, both in Emergency mode. On
RMX5070: Settings → "Available to help nearby users" ON. All other phones stay in Emergency mode OFF.
They do **not** need to be contacts.

**Steps (Vivek).**
1. On RMX5108: SOS → choose a category → send. The screen says it is looking for people.
2. Within about 10 s RMX5070 shows "Someone nearby needs help". Tap it → card → **Decline**.
3. RMX5108 keeps searching, and its screen shows "1 said no". Then on RMX5108 cancel the SOS.
4. On RMX5108 start SOS again. On RMX5070 tap **Accept**.
5. Both screens show a 6-digit code. Vivek reads both aloud. They must be the same. Tap **CODES MATCH IN PERSON** on both.
   The label changes to "Checked in person".
6. Type one chat line on each phone. Each line must appear on the other phone.
7. End the SOS. **The main app has no End or Cancel button once connected.** The chat screen
   has only Back (`ui/app/SosScreens.kt:263-294`, `ui/app/ItantraApp.kt:425-436`), and
   `SosManager.endSession` is never called from the app. So Agent A opens Net Lab on RMX5108 by adb,
   and Vivek taps **Cancel SOS** there. By the code, the helper's card stays ACTIVE after a cancel
   (`sos/SosManager.kt:466`). Write down what both screens show. Report it as a UI/flow gap; it is not a pass or fail of this stage.

**PASS and the proof lines**
| Point | RMX5108 (requester) JSONL | RMX5070 (helper) JSONL | Screen |
|---|---|---|---|
| Start | `sos_start` | — | searching |
| Offer | `sos_offer_tx` | `sos_offer_rx` | card with Accept/Decline, "Encrypted connection — identity not verified" |
| Decline | (no event exists) | `sos_decline_tx` | requester "1 said no"; helper "You declined" |
| Cancel | `sos_end` `"reason":"USER_CANCELLED"` | (no event) | card "Ended" |
| Accept | `sos_accept_rx` `"reply":"SOS_CONFIRM"` | `sos_accept_tx` | both show the conversation |
| Code | (no event) | (no event) | same 6 digits on both. After tapping: "Checked in person" (Net Lab: "Nearby device verified") |
| Chat | (no event) | (no event) | lines appear on both sides |
| End (Net Lab Cancel SOS) | `sos_end` `"reason":"USER_CANCELLED"` | (no event) | requester back to Home. Helper: expected to stay in the conversation (see step 7) |

FAIL: the codes differ; Accept does nothing; chat goes one way only; the requester shows "Connected" after a
decline; `sos_offer_bad_signature`. UNCLEAR: no offer arrives within 30 s while RMX5070 is
"available". Check the RMX5108 JSONL for `sos_offer_tx`. Without it, the helper was not seen as available,
which needs advertising and RSSI samples (`transport/ble/BleLinkManager.kt:133-136`).

### 5B: two helpers accept at the same moment
**Set up.** Requester RMX5108. Helpers RMX5070 and RMX3392, both "Available" ON, all within 2 m. I2221 OFF.
**Steps.** Start SOS on RMX5108. When both helpers show the card, Vivek taps Accept on both phones as close together as he can.
**PASS:** exactly one helper gets the conversation. The other shows "Someone else is helping"
(`TAKEN_BY_OTHER`). RMX5108 JSONL: one `sos_accept_rx` `"reply":"SOS_CONFIRM"` and either
another `sos_accept_rx` `"reply":"SOS_BUSY"` or none (the loser may get BUSY before it
accepts, `:357-362`). Both helpers: `sos_offer_rx` and `sos_accept_tx`.
**FAIL:** both helpers end up in a conversation.

### 5C: SOS does not become interactive across the relay chain
**Set up.** The same line as Stage 4 (S = RMX5108, M, R = I2221), with the same pre-check that S
and R can't hear each other. M: "Available" **OFF**. R: "Available" **ON**. The fourth phone OFF.
**Steps (Vivek).** At S start SOS. Touch nothing for **3½ minutes**. Agent A opens Net Lab on R by adb and takes a screenshot of the SOS section at about 40 s.
**PASS:**
- S: `sos_start`; **no** `sos_offer_tx` (M is not available, R is not linked); `sos_request_tx` twice (`"hop_limit":2`, then `3`); at about 180 s `sos_end` `"reason":"EXPIRED"`. The screen shows "asked 0".
- M: `sos_request_rx` (`"decision":"FORWARD"`, `"hop":0`) and `sos_request_forward`. **No** `sos_offer_rx`.
- R: `sos_request_rx` (`"hop":1`). **No** `sos_offer_rx`, **no** `sos_accept_tx`/`sos_decline_tx`.
  Net Lab card: "Relayed emergency request", "Phase: RELAYED_NOTICE", "No live connection is
  available for relayed requests in v1." There are no ACCEPT/DECLINE buttons.
  Main app Home: no strip (a known UI gap, report it, see §6).
- Optional cancel check: rerun, and cancel on S at about 40 s. R's card turns to `ENDED`. Cancel has no event of its own.
**FAIL:** any `sos_offer_rx` or `sos_accept_tx` on R, or a chat or code on R.
**UNCLEAR:** R shows nothing and has no `sos_request_rx`. Then check M for `sos_request_forward`
(a `RATE_LIMITED` or `DELIVER_ONLY` decision would explain it).

---

## 5. Stage 6: all four phones together

### Set up
- **Charge** RMX5070 and RMX3392 to at least 50% first (by cable; unplug before 6C).
- Trust pairs, each made with the QR swap from §3: RMX5108↔I2221 (from Stage 4) and
  RMX5070↔RMX3392 (new). Optional third pair RMX5108↔RMX5070, so one message crosses agents.
- All four phones on one table, about 1-3 m apart, all in Emergency mode. Wait 60 s.
  PASS: every Home shows "· 3 nearby" (a full mesh is 6 links; the limit is 6 per phone,
  `ProtocolConfig.kt:24`). Two links between the same pair = `link_duplicate_resolved`, which is fine.

### 6A: several messages at once
Vivek types on each phone, one after another and as fast as he comfortably can: 3 messages to its
partner, each with a marker (`ORCA1-a`, `ORCA1-b`, …). 12 messages in about 2 minutes.
- PASS: all 12 reach the right phone **once**, are spoken, and turn `Delivered ✓✓` within **5
  minutes**. JSONL: 12 `product_queued` in total and 12 `product_delivered` in total, each on the right phone.
  Phones that are not the target hold RELAY copies only (`dtn_bundle_rx … RELAY … PERSISTED`).
  Expect it, because Spray-and-Wait sprays copies.
- Not a failure: delays up to a few minutes. Each link allows 16 destination hand-overs per minute,
  receipts included (`ProtocolConfig.kt:54`). The 25-Sept run measured p95 queued→Relayed = 58 s at 15 msgs/min.
- FAIL: a message shown on a phone it was not addressed to; any duplicate; anything never delivered after 10 min.

### 6B: a phone leaves and comes back
1. Vivek picks up **RMX3392**, walks out of range (to the far room from Stage 4) and stays there **3 minutes**.
2. While it is away, at RMX5070, send 2 messages to RMX3392 (`ORCA2-a`, `ORCA2-b`).
   RMX5070's rows stay `Queued` or `Relayed` (other phones may take relay copies).
3. Vivek brings RMX3392 back to the table and waits 2 minutes.
- PASS: after leaving, the others log `link_disconnected` or `session_closed` for it. After it
  returns: new `link_ready` + `noise_done` on RMX3392 within about 60 s, both messages on RMX3392
  once (`product_delivered` ×2), and RMX5070 turns `Delivered`. Its Home is back to "· 3 nearby".
  `peer_discovered` may **not** show again if it was back within 2 minutes (observations are kept
  4×30 s, `BleLinkManager.kt:171`). Use `link_ready`.
- FAIL: no relink after 3 minutes back; messages lost or doubled.
- Optional: Stop and Start Emergency mode on one phone. It comes back with a new short ID, and the others
  should log `link_superseded_same_device` and relink.

### 6C: battery over 30 minutes
There is no battery target in the spec. §54 only says to record "battery/thermal observations".
The flag line below is my proposal for Vivek to approve.

**Hold these the same on all four phones for the whole 30 minutes:**
- Unplugged (check `dumpsys battery`: AC/USB/Wireless powered = false), at least 50% at the start, battery saver OFF.
- Emergency mode ON, all four on the table 1-3 m apart, links up ("· 3 nearby") before the clock starts.
- "Available to help" OFF on all four. No SOS. No messages during the window.
- **Screen OFF (locked) for the whole window. Nobody touches the phones.**
- Bluetooth on, Wi-Fi on and connected (adb), mobile data off. No other apps open. Write down each
  phone's OEM "allow background activity" / battery-optimisation setting. **Don't change it without asking Vivek.**

**Measure (agents, each on its own phones):**
- At t=0, then every **2 minutes**, until t=30 min:
  `adb -s <id> shell dumpsys battery`. Keep `level`, `temperature` (tenths of °C), `voltage`,
  and `Charge counter` (µAh) if the phone prints it.
- At t=0 and t=30: `adb -s <id> shell dumpsys batterystats --charged com.chmod777.itantra > file`
  (a snapshot only). **Do not run `dumpsys batterystats --reset` without asking Vivek.** It wipes the phone's battery history.
- Optional control if time allows: one phone, the same 30 minutes, Emergency mode OFF, everything else the same.

**Report:** per phone, the level drop (points / 30 min), the charge-counter drop in mAh, the highest temperature,
and how many `link_disconnected` happened in the window (from the JSONL with `-SinceWallMs`).
**Proposed flag:** more than 5 points in 30 min on any phone, or temperature over 40 °C, or links that dropped with the screen off.
Expect a high number. The app scans and advertises in LOW_LATENCY mode non-stop
(`transport/ble/BleDiscovery.kt:41-42,117`) and asks for high connection priority on every link
(`transport/ble/BleGattClientLink.kt:161`).

**Last check, right after 6C:** with the scans now running for more than 30 minutes, carry one phone out of range
for 3 minutes and bring it back (6B again). Android may slow down a BLE scan that runs for more than 30 minutes, and
this code starts its scan once and never restarts it unless it fails (`BleDiscovery.kt:104-137`,
`BleLinkManager.kt:174`). Whether that hits these phones is **unclear**. This step answers it:
if the phone relinks only very slowly or never, write down the times and the `scan_start` count.

---

## 6. Things in the code that make a planned check impossible or misleading

| # | What | Where | Effect on the test |
|---|---|---|---|
| 1 | Engine events go only to JSONL, not logcat | `metrics/JsonlMetricsRecorder.kt:33-53` | a "logcat proves it" check would find nothing. Use JSONL |
| 2 | Relayed is set by any first hop ACK, even a direct hand-over to the recipient | `dtn/DtnNode.kt:371-385` | the sender's Relayed does not prove a hop |
| 3 | No event when the sender turns Relayed/Delivered | `DtnNode.kt:347-385`, `network/ItantraNetwork.kt:63-96` (only `product_queued` / `product_delivered`) | Delivered is proven by screen, or by a `dtn_bundle_rx` DESTINATION on the sender with no `product_delivered` after it |
| 4 | No event for SOS decline/confirm/busy received, for chat, or for the code | `sos/SosManager.kt:364-373, 410-422` | screenshots only |
| 5 | A relayed SOS notice is hidden in the main app | `ui/app/ItantraApp.kt:300`, `ui/app/HomeScreen.kt:202` | only Net Lab shows it. Report as a UI gap |
| 6 | SOS requests and cancels do cross hops (by design); offers, answers and chat don't | `SosManager.kt:220-232, 449-457, 469` | "SOS never goes through the relay" is true only for the interactive part |
| 7 | A phone's own short ID is never logged | `BleLinkManager.kt` (only on the Net Lab screen) | match IDs through the middle phone's two `link_ready` peers, or a Net Lab screenshot |
| 8 | The debug ValidationReceiver `import` adds a trusted contact with no fingerprint step | `app/src/debug/java/com/chmod777/itantra/debug/ValidationReceiver.kt:54-58` | the Net Lab path closed in `4729bd2` still exists here (debug only, adb with DUMP permission only). Don't use it if the point is to test QR trust. Report to the foreman |
| 9 | On "nothing heard" the sender logs up to 32 characters of the raw transcript to logcat | `ui/app/CaptureSession.kt:120` | small §54 issue, sender side only. It can give a false marker hit if a marker word is ever spoken and STT returns "nothing heard" |
| 10 | The scanner starts once, LOW_LATENCY, and is never restarted | `transport/ble/BleDiscovery.kt:104-137` | late discovery after 30 min is unclear. Tested at the end of 6C |
| 11 | PHYSICAL_TEST_PLAN.md is out of date: "Net Lab has its own launcher icon", QR trust in Net Lab | `app/src/debug/AndroidManifest.xml:7-13`, `NetworkLabActivity.kt:215-226` | open Net Lab via Settings → Developer, or by adb |
| 12 | A connected SOS cannot be ended from the main app (no End button; `endSession` unused). A cancel leaves the helper ACTIVE | `ui/app/SosScreens.kt:263-294`, `sos/SosManager.kt:386-402, 466` | "End" in 5A has to go through Net Lab, and the helper side does not close |

---

## 7. Open questions for Vivek

1. The middle phone for Stage 4: RMX3392 (trusts nobody, but no BT permission yet and low battery) or RMX5070 (has old 25-Sept contacts and messages)?
2. May the agents ask you to tap "Allow" for missing permissions (camera on I2221 for the QR; Bluetooth and notifications on RMX3392)?
3. Where are the two far-apart rooms (about 25-30 m, two walls)? Does Wi-Fi for wireless adb reach both?
4. Battery: do you accept the proposed flag (more than 5 points in 30 min, or over 40 °C)? We will not run `batterystats --reset` unless you say yes.
5. Typed marker messages for Stage 4 (exact plaintext check), plus one spoken message at the end. Is that OK?
