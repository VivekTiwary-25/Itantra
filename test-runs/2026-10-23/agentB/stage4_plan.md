# Stage 4 plan: three phones in a line (S → M → R), Vivek's hands

Roles: **S = RMX5108** (sender, Agent A), **M = RMX3392** (middle, Agent B), **R = I2221** (receiver, Agent A).
**RMX5070 is the fourth phone. It stays with Emergency mode OFF the whole time.**
S and R trust each other. M trusts nobody: it has 0 contacts and its database is empty, which is checked on the
phone. That is correct. The relay code never asks whether the middle phone knows anyone
(`dtn/DtnNode.kt:185-188`, `:263-295`).

Marker word: **KESTREL41**. It has never been used on any phone. Messages: `KESTREL41 one` … `KESTREL41 five`,
then `KESTREL41 carry`.

The foreman gives Vivek **one step at a time** and waits for "done" before giving the next one.

---

## 0. Before Vivek moves anything (agents only, no hands)
- A0. Each agent writes down its phone clocks: `adb -s <id> shell date +%s`. Agent B also copies M's database as a
  "before" copy (read-only, `run-as … cat`). It must show 0 rows in contacts, bundles, delivered and outgoing.
- A1. Agent A confirms that S lists R under Contacts and R lists S. If not, do the RUNBOOK §3 QR swap first, with
  the phones side by side and Emergency mode OFF.
- A2. Check the batteries. **M must be at least 50% or plugged into a power bank.** M has no screen-awake setting
  (see the end of this file), but its screen timeout is 30 minutes and the relay runs as a foreground service, so
  the screen may go dark.
- A3. Check the fourth phone, RMX5070: no iTantra service is running (`dumpsys activity services
  com.chmod777.itantra` is empty).

## 1. Where the phones go (a template, because we do not know the rooms yet)

```
  [S  RMX5108]  ── 12-15 m, at most 1 wall/doorway ──  [M  RMX3392]  ── 12-15 m, at most 1 wall/doorway ──  [R  I2221]
  room/spot A                                           corridor/doorway B                                     room/spot C
                    S ↔ R: 25-30 m and 2+ solid (brick/concrete) walls, or different floors
```
- **Safe default:** S and R are about **25-30 m apart (about 35-40 normal steps)**, with **at least two solid walls**
  between them, or on different floors. M stands about half way, **12-15 m (15-20 steps) from each**, in a doorway
  or at a corridor corner, so it has a clear view or only one wall to each side.
- Each phone lies **screen up on a table or chair about 1 m high**. Not in a pocket and not in a hand.
- Write the real spots here before starting: A = ________, B = ________, C = ________.
- These are a starting point, not a promise. **Step 2 proves the gap.** If S and R can see each other, move them
  further apart or put another wall between them, then repeat step 2.
- Before step 2, the agents check that wireless adb still reaches every spot. If one does not, its evidence stays
  in the phone's JSONL and is pulled later.

## 2. Prove that S and R cannot hear each other (M stays OFF)
One instruction at a time:
1. "Put **I2221** on the table at spot C, screen up."
2. "On **I2221**, tap **START** on the 'Emergency mode off' line." (Wait for "done".)
3. "Walk to spot A. Put **RMX5108** on the table, screen up."
4. "On **RMX5108**, tap **START** on the 'Emergency mode off' line."
5. "Step away from both phones and wait **3 minutes**. Touch nothing."

Agents check, no hands needed:
- Home on S and on R shows "Emergency mode on" with **no "· N nearby"**.
- In the JSONL since the stage clock, there are **no `peer_discovered` and no `link_ready`** events on S or on R.
  The Net Lab Nearby list on R says "No iTantra advertisements seen yet". Agent A opens it by adb.
- **If S and R did see each other, the placement failed, not the app.** Move them further apart and repeat.
  This proves "out of range" from the logs: the outer two phones never discover or link to each other.

## 3. Start the middle
6. "Walk to spot B. Put **RMX3392** on the table, screen up. On **RMX3392**, tap **START** on the
   'Emergency mode off' line."
7. "Step back and wait **60 seconds**."
- PASS: M shows "· 2 nearby", and S and R each show "· 1 nearby". In the JSONL, every `peer_discovered` and
  `link_ready` event on S and on R names M. M has `link_ready` to two different peers.

## 4. Send five marked messages
8. "Walk to spot A. On **RMX5108**: Home → **Write message** → **To / Select trusted contact** → pick
   **R-I2221** → type `KESTREL41 one` → **Send**."
9. "Wait at spot A for **2 minutes**. Touch nothing."
- Expected: S Logs shows `Queued ·`, then `Relayed ··`. R speaks the message once, and S then shows
  `Delivered ✓✓` (anti-entropy runs every 30 s, `protocol/ProtocolConfig.kt:66`).
10. to 13. The same as step 8 with `KESTREL41 two`, `three`, `four`, `five`, **one step each**, about 20 s apart.
14. "Wait **2 minutes**. Touch nothing."

## 5. Check that the middle phone never shows or speaks the text
15. "Walk to spot B. **Only look** at RMX3392. Did it make any sound? Is there a new number on the Logs
    button? Tell me yes or no. Do not tap anything."

Agents do the rest by adb (Agent B, on M):
- **Screen:** take a screenshot of M's Home (no unread badge on Logs, because the baseline is no badge). Open Logs by
  adb (`input tap` on Logs), take a screenshot (it must be empty), then press Back. Open Net Lab by adb
  (`am start -n com.chmod777.itantra/.ui.lab.NetworkLabActivity`) and take a screenshot of "Carried bundles
  (ciphertext only)". It may show RELAY entries with hops=1 while it is carrying, and fewer after the tombstones
  arrive. The Inbox must be empty.
- **Logs:** run `pull_and_scan.ps1 -Phones RMX3392 -Marker KESTREL41 -PullDb`. M must have **0 marker hits** in
  logcat, in the JSONL (bench) and in the database, both `itantra_net.db` and `-wal`. It must also have **0**
  `product_delivered`, **0** `product_tts_start` and **0** `ITANTRA_TTS … speak` lines. Also run an extra check:
  `adb -s <M> logcat -d | findstr /i KESTREL` must give nothing.
- **Database (sealed copy only):** in M's copy, `contacts`, `delivered` and `outgoing` are still **0 rows**. The
  `bundles` table may hold rows only with `origin=RELAY`, and their `immutable_blob` holds no `KESTREL41` bytes
  (a byte search of the whole .db and -wal files). `seen` and `tombstones` rows are allowed. They hold hashes and
  ids only.
- S and R are expected to hold the marker in their own `outgoing` / `delivered` tables. That is normal.

## 6. Carry-and-forward (the receiver is away for a while)
16. "Walk to spot C. On **I2221**, pull down the quick settings and turn **Bluetooth OFF**. Leave the app as it is."
    (The app pauses itself and shows "Bluetooth turned off". It restarts by itself when Bluetooth comes back:
    `service/EmergencyModeService.kt:83-87`.)
17. "Walk to spot A. On **RMX5108**, send `KESTREL41 carry` to **R-I2221**."
18. "Wait at spot A for **1 minute**."
- Expected: S shows `Relayed ··` and **not** Delivered. M's JSONL has `dtn_bundle_rx` RELAY PERSISTED. R has nothing.
19. "Wait **5 more minutes**. Touch nothing." (This is the carry time. M holds only ciphertext. The message's
    lifetime is 24 h, `ProtocolConfig.kt:43`.)
20. "Walk to spot C. On **I2221**, turn **Bluetooth ON**. Put the phone back on the table."
21. "Wait **2 minutes**. Touch nothing."
- Expected: R speaks `KESTREL41 carry` once. S turns `Delivered ✓✓`. M has `dtn_bundle_tx` DESTINATION and then
  `dtn_tombstone_rx` applied. M still has 0 marker hits.
- Optional, closer to a real carry: start with M near S only, out of R's range. Send. Then Vivek carries M to
  spot B or C. This needs one extra hand step: "pick up RMX3392 and walk to spot C".

## 7. End
22. "Leave everything running. Do not touch the phones." The agents pull all three phones (RUNBOOK §2), with
    `-Marker KESTREL41`, and with `-PullDb` on M. Only after the pull does the foreman tell Vivek to tap STOP.

## Notes
- RMX3392's "Stay awake" is not set: the phone refuses `stay_on_while_plugged_in`. The screen timeout is 30 min.
  If the screen should stay on, Vivek can turn it on by hand: Settings → Additional settings → Developer options →
  Stay awake. It only works while the phone is charging.
- If R ever shows a message twice, or M's checks find any marker hit, `product_delivered` or TTS line: **stop and
  report the log lines.** That is an engine or privacy bug, and we do not fix it during the test.
