# REPORT — fix/identity-and-e1 (2026-10-29)

Written by Claude (Opus 5.5). PC-only run: no phone was touched and no APK was installed. STATUS.md was not
edited and nothing is marked GREEN.

## 1. Summary in plain words

- **Rename bug: fixed.** Renaming "Your name" used to wipe the phone's secret keys. The app was building the
  new identity on top of a scratch copy of the keys and then clearing that scratch copy. The identity now keeps
  its own copy. A test replays the exact rename steps. Before the fix it failed with the very fingerprint I2221
  ended up with (`91c4 ad49 0157 6d46 7f67 fd42 3e46 5f57`). After the fix it passes.
- **E1: fixed.** A message that arrived while its sender was untrusted was put aside as "someone else's
  message to carry" and never looked at again. Now, whenever a contact is newly trusted (first time, re-added,
  or after an app restart), the phone looks again at the messages it is carrying for others. If one is really
  for it, the phone delivers it, but only after the same full security checks as a normal delivery. The same
  change fixes the T4B2 case, where the receipt was the item stuck as a relay copy.
- **All 138 JVM tests pass. assembleDebug builds.** The branch is pushed.
- **No security problem found.** Notes are in section 5.
- **Main readiness finding:** STATUS.md was last changed on **2026-09-27**. It does not list most of the
  screens and features that will be tested end to end. They were built between 14 and 23 Oct. See section 6.

## 2. Branch and commits

Branch `fix/identity-and-e1`, made from `test/e2e-oct23` at `7c9d44e`. The phones run `4729bd2`; the only
difference between `4729bd2` and `7c9d44e` is docs. Pushed to `origin/fix/identity-and-e1`.

| Commit | What | Files |
|---|---|---|
| `02bce34` | fix(identity): renaming "Your name" no longer zeroes the identity keys | `identity/LocalIdentity.kt`, new test `identity/LocalIdentityKeyCopyTest.kt` |
| `80f1cef` | fix(dtn): deliver relay copies held from a sender who is trusted again (E1) | `dtn/DtnNode.kt`, `dtn/DtnScenarioTest.kt` (+4 tests) |
| (this report) | docs | `test-runs/2026-10-29/` |

Only `LocalIdentity.kt` and `DtnNode.kt` were changed in the app code. The uncommitted test-day files that were
already in the working folder were left alone: they were not staged, committed, changed or deleted.

## 3. Bug 1 — rename wipes the identity keys

**Cause.** `KeyManager.rename()` exported a copy of the private keys and built the renamed `LocalIdentity`
from that copy. It saved the result and then zeroed the copy. `LocalIdentity` stored the arrays it was given,
not copies of them, so zeroing the copy also wiped the renamed identity's keys:
- After one rename the in-memory identity signed with a zero key, so its QR code was invalid. The disk copy
  was still fine.
- After a second rename the zero keys were written to disk. The phone then became the all-zero identity.

**Fix (`LocalIdentity.kt`).** The constructor now keeps its own copies (`copyOf()`) of both key arrays. No
callers changed, so the existing cleanup in `rename()` and `save()` now only wipes the scratch copies, which
was the intent.

**Test `LocalIdentityKeyCopyTest`** (2 tests). It replays rename's steps: export → build → wipe, once and then
twice. It checks that the identity still signs as the original, can still open messages sealed to it, and keeps
the same fingerprint and node id.
- Before the fix, both tests failed. The fingerprint check reported
  `expected:<29c2 6e25 …> but was:<91c4 ad49 0157 6d46 7f67 fd42 3e46 5f57>`, the I2221 zero-key fingerprint.
  Evidence: `evidence/bug1_before_fix.txt`.
- After the fix: 2/2 pass (`evidence/bug1_after_fix.txt`).

**Not covered by the fix.** It cannot repair a phone whose identity file already holds zero keys; only I2221
did, and it was already reset. `KeyManager` itself needs Android Keystore and cannot run as a JVM test. An
instrumented test would use the app's real identity file on the phone, so none was written.

## 4. Bug 2 (E1) — messages held as relay copies never delivered after trust returns

**Cause.** When the receiver does not trust the sender, the destination tag does not match, so the bundle is
stored as a RELAY copy and marked "seen". Two things then stop it from ever being delivered:
- `planEncounter` (`DtnNode.kt`, the old line 183) skips any bundle already held or seen before it asks "is
  this for me?".
- Nothing re-checked held relay copies when a contact was added.

The T4B2 receipt (0059cd96) was the same bug on the sender's side.

**Fix (`DtnNode.kt`, +25 lines).** At the start of `planEncounter`, a new private function
`deliverHeldCopiesForNewContacts()` runs, using the same contact list that round uses:
- It works out which contacts are new since the last check. A contact is identified by node id plus both
  public keys. It counts as new when trusted for the first time, when re-added after removal, when re-scanned
  with new keys, and for every contact after a process restart.
- If nothing is new, it does nothing, so there is no extra cost in normal sync rounds.
- Otherwise, each held RELAY copy that is not expired and not tombstoned is checked against the new contacts
  only. A match goes into the existing `ingestAsDestination()`.

**Tests in `DtnScenarioTest`** (4 new; they run through the real codec and the real `DtnEncounter`):
1. `e1_messageHeldAsRelayCopyIsDeliveredOnceTheSenderIsTrusted` follows the T4B1 order: trusted both ways and
   linked, receiver removes sender, sender sends, they meet (relay copy, sender RELAYED), receiver re-adds,
   they meet. It expects the message delivered once, the relay copy gone and the sender DELIVERED. A third
   meeting must not deliver the message a second time.
2. `e1_receiptHeldAsRelayCopyMarksDeliveredOnceTheRecipientIsTrusted` follows T4B2: the sender removes the
   recipient after sending. The receipt is held as a relay copy, the sender re-adds the recipient, and the
   sender should go to DELIVERED.
3. `e1_trustingTheSenderOfARelayCopyAddressedToSomeoneElseDeliversNothing` is a guard: a relay that starts
   trusting the sender still cannot open a message meant for someone else.
4. `e1_tamperedRelayCopyIsNotDeliveredAfterTrust` is a guard: a held copy with a flipped ciphertext byte is
   refused, and no tombstone or "delivered" record is written.

- Before the fix, tests 1 and 2 failed at the after-trust checks: `expected:<[T4B1 receiver removes sender]>
  but was:<[]>` and `expected:<DELIVERED> but was:<RELAYED>`. Their setup checks passed, so the tests
  reproduce the field behaviour. Tests 3 and 4 are guards and pass both before and after. Evidence:
  `evidence/e1_before_fix.txt`.
- After the fix: 17/17 in `DtnScenarioTest` pass (`evidence/e1_after_fix.txt`).

## 5. Security review (done by Opus, not handed off)

**Identity keys (Bug 1)**
- Private keys are still private fields and are never logged. `exportPrivateMaterial()` still returns copies
  and is still `internal`.
- The constructor parameters are no longer stored as properties, so the identity keeps no reference to the
  caller's arrays.
- The public key, node id and fingerprint are computed from identical bytes, so they are unchanged; the test
  checks this.
- The QR capsule from a renamed identity is now signed with the real key, where before it was signed with a
  zero key.
- Minor hygiene note, not a problem: the arrays `KeyManager.load()` and `generate()` pass in are now left for
  the garbage collector without being zeroed. Before, those same arrays were the live keys. The JVM never
  guaranteed key wiping anyway.

**Trust and held relay copies (E1)**
- A held copy is delivered only through `ingestAsDestination()`, the same function a live delivery uses. It:
  1. re-matches the destination tag against `contacts.all()`. The tag is made from the X25519 pair secret
     between this phone and the contact plus this phone's node id, so only this phone or that contact can
     produce it;
  2. decrypts with this phone's private key (`crypto.open`);
  3. strictly parses the payload;
  4. requires the signer node id to equal the tag-matched contact;
  5. verifies the Ed25519 signature with that contact's **stored** key, the one saved from the fingerprint-
     checked QR;
  6. requires the addressee to be this phone;
  7. requires the bundle id, lifetime, priority and deletion commitment to match their signed copies.

  Seen, tombstone and delivered records are written only after all of that succeeds.
- Receipts also go through `acceptReceipt()`, which requires the receipt to come from the exact contact the
  message was addressed to and to cover the exact inner message.
- Live-path checks the re-check skips, and why that is safe:
  - Solicitation check: the copy is already ours.
  - Per-session byte cap: there is no peer involved; held copies are already bounded by the 512-bundle store.
  - Live age check: replaced by `isExpired` plus a tombstone check.
- A relay peer cannot use this to forge a delivery. That would need the pair secret and the contact's signing
  key, exactly as on the live path. The tampered-copy and wrong-addressee guard tests pin this down.
- A removed contact gets nothing from its held copies until it is re-added by QR. A contact re-scanned with
  new keys only matches bundles made for the new keys. Copies addressed to I2221's pre-reset identity, such as
  596de98a, still never match.
- A copy that fails the checks stays as it was and is not retried every round. It is retried only when the
  contact set changes again, so there is no log spam.
- The fingerprint-check code was not changed. It lives in the QR and contact screens and `ContactQrCodec`.

**Not security problems, but worth knowing**
- **Intended behaviour change:** after you re-add a contact, messages they sent while you did not trust them
  will now be shown and spoken.
- **Delivery timing:** delivery happens at the next sync with any phone, about 5 s while linked, not at the
  moment of re-adding. With no link, it waits for the next link.
- **Rare gap (delivery only, not security):** with two or more peers at once, a bundle can be requested as a
  relay copy before the re-add and arrive just after another peer's round already ran the check. That bundle
  stays undelivered until the contact set changes again or the app restarts. Closing it would need a change in
  `ingestAsRelay`, which this approval did not cover.
- **Not covered:** a relay copy that storage pressure already deleted, while its "seen" record remains, is
  still never fetched again.
- **First sync after app start:** every contact counts as new, so one pass checks all held relay copies, at
  most 512 times the number of contacts in X25519 operations. This happens once per process, under the DTN
  lock. It is not measured on a phone.

## 6. Readiness report for end-to-end testing

The status column quotes **STATUS.md as it is now** (last changed 2026-09-27, header says
`integration/tts-recovery`). "Not listed" means STATUS.md has no row for it. The test-day column is from
`test-runs/2026-10-23/HANDOFF.md`, where agents recorded the evidence; those results were not entered into
STATUS.md.

### 6a. Screens (`ui/app/ItantraApp.kt` routes)

| Screen | STATUS.md | Test day 2026-10-23 (real phones) |
|---|---|---|
| Home (redesigned, built 16 Oct) | Not listed. A0–A3 GREEN were accepted on the old screen | Opens on all 4 (Stage 1) |
| Hold-to-talk / PTT capture → draft | A3–A6 GREEN; "English PTT STT → draft → Send → Bluetooth → Logs" PHYSICALLY VERIFIED at `21e28fd` (RFCOMM era) | Not run as a new STT test (noisy room, logs only) |
| Hands-free "Listening…" (redesigned 16 Oct) | A11 GREEN (old UI) | Mic really records (appops); "Listening without mic permission" UNCLEAR |
| New message / composer (16 Oct) | A9, A12 GREEN (old UI) | Typed messages used in Stage 2/3; composer remembers the last recipient (UI bug) |
| Logs (now read from the DTN database) | A8 GREEN (old UI, fake messages) | Shown in both Logs (Stage 2) |
| Message detail | A13 GREEN (old UI) | Opens; unread badge clears only when a message is opened (UI bug) |
| Settings (17 Oct) | Not listed | Opens; Developer row opens the legacy screen; contact list not refreshed after rename |
| QR scan / add contact | "Identity capsule, QR codec, trusted contacts" YELLOW ("QR scan/paste UI not exercised on a phone") | Stage 2/3 PASS: QR trust both ways, impostor refused (Test 2), non-iTantra QR refused (Test 1), re-scan replaces the row (Test 5) |
| Fingerprint check | Not listed | Used in G1–G4; impostor refused when the codes do not match |
| Add-contact name | Not listed | Long name cut to 32 chars (Test 7) with a UI bug |
| SOS category / SOS active / incoming SOS / helping SOS (17 Oct) | "Direct SOS" YELLOW (JVM); "Multi-hop SOS" YELLOW (JVM); "Relayed interactive SOS session" RED (not built) | **Not run** (Stage 5 not started) |
| Report a problem (17 Oct) | Not listed | Opens; shows "App: 1.0 (debug)" |
| Onboarding (17 Oct) | Not listed | Opens; its name card calls the rename that was buggy (now fixed) |
| Legacy main screen (debug long-press) | "Legacy RFCOMM demo" YELLOW | Opens; holds "Test TTS (temporary)" |
| Net Lab (debug only, `ui/lab/NetworkLabActivity.kt`) | Not listed | Not a test target; can no longer add contacts (`4729bd2`) |

### 6b. Features / engine

| Feature | STATUS.md | Test day 2026-10-23 |
|---|---|---|
| Keystore-wrapped identity | YELLOW (DEVICE-1, TEE) | Survived app restarts on 3 phones; I2221 broken by the rename bug and reset |
| Rename "Your name" | Not listed | BROKEN on 4729bd2 → **fixed on this branch, not yet on any phone** |
| BLE advertise / scan / GATT server | YELLOW | Worked on 2 phones (Stage 2) |
| GATT client link, MTU, CCCD, HELLO | **RED** ("needs two phones") | Link ~3.4 s, MTU 517; first connect status 62, then the auto-retry worked |
| Noise XX hop sessions | YELLOW (JVM, "not over BLE") | Handshake ~260–280 ms over BLE |
| DTN direct delivery + receipts (Delivered state) | YELLOW (JVM) | Stage 2 PASS: 0.4–5.4 s, sender shows Delivered |
| Store-carry-forward (receiver BT off then on) | "Store-and-forward" **RED** | Test 6 PASS: delivered 4.5 s after BT came back |
| Three-phone relay (Spray-and-Wait over a middle phone) | DTN row YELLOW (JVM); "Gates B–F" **RED** | **Not run** (Stage 4 prepared, needs people on site) |
| Untrusted sender → held, then trust restored (E1) | Not listed | BROKEN on 4729bd2 → **fixed on this branch, not yet on any phone** |
| Receive → Logs → TTS only on the end recipient | YELLOW | TTS ran per the logs on both phones; heard by ear UNCLEAR |
| English/Hindi TTS | GREEN | — |
| Malayalam / Bengali TTS | YELLOW | — |
| Tamil TTS | RED (desktop only) | — |
| Hindi Dolphin STT | YELLOW (unreliable) | — |
| Other languages end to end | RED | — |
| EmergencyModeService | YELLOW | Ran on all phones; RMX5070 has emergency_enabled=true saved while the service is off |
| Battery over 30 min, 4 phones | Not listed | **Not run** (Stage 6) |

### 6c. Built but not yet verified on a real phone

1. **Both fixes on this branch** (rename, E1). No phone runs `80f1cef`. The 4 phones run `4729bd2`.
2. **SOS**, all four screens, both direct and multi-hop. Stage 5 was never started.
3. **Three-phone relay** through a middle phone (Stage 4) and **Gates B–F** in `docs/networking/PHYSICAL_TEST_PLAN.md`.
4. **30-minute, four-phone battery run** (Stage 6).
5. **TTS heard by ear** on the receiver. On test day it was only confirmed from the logs.
6. **"Listening…" with the mic permission denied**: the app does not check that AudioRecord really started (UNCLEAR).
7. **Onboarding name card**: it calls the rename path. It was not exercised after the fix (it can't be, until a phone runs the new build).
8. **Language metadata beyond English/Hindi** on two phones (STATUS.md: pending).
9. **The redesigned Home / composer / Logs / detail / Hands-free screens.** Stage 1 showed they open, and Stage 2/3
   used typed messages. But the A2–A13 GREEN acceptance tests were done on the pre-redesign UI and were not
   re-run on these screens.
10. **Scanner stall after ~30 min** (`BleDiscovery.kt:104-137`): not observed or ruled out (UNCLEAR).

**Bookkeeping note (I changed nothing):** STATUS.md still shows "GATT client link" and "Store-and-forward" as
RED, and its header says "No two-phone or three-phone run has happened yet". The 2026-10-23 evidence above
shows two-phone links and store-and-forward working. Only you can update those rows.

### 6d. Other known bugs and TODOs (not fixed — report only)

Engine / security-relevant (from test day plus code reading):
- **E2 (minor):** each connection also makes a second unused server link that drops after 20 s (`server_link_unused_detached`).
- **Debug back door:** `app/src/debug/.../ValidationReceiver.kt:54-58` `import` adds a trusted contact with no fingerprint check. Debug build, adb only. Do not use it during QR-trust tests.
- **Relayed SOS requests and cancels** are readable by relay phones (offer/accept/decline/chat are direct-only).
- **A connected SOS cannot be ended from the main app** (`SosScreens.kt:263-294`; `endSession` is never called).
- **Spray split overcount:** an unconfirmed split keeps its tokens, which "can overcount the budget" (`DtnNode.kt` sweep comment, audit A). Known by design.
- **E1 leftovers** (section 5): the rare multi-peer timing gap, and relay copies already removed by storage pressure.

UI (from test day, not fixed):
- The Settings contact list does not refresh after a rename (`TrustedContact.equals` compares node id only).
- The Settings / Remove dialog shows a stale contact name ("vimalesh" while the database said "iTantra user").
- A re-scan silently overwrites the local nickname with the other phone's own name.
- No warning for duplicate names: an impostor saved under a real contact's name looks identical in lists.
- A saved contact shows only 4 of 8 fingerprint groups, and there is no full-fingerprint view. The Remove dialog shows no fingerprint.
- The unread badge clears only when a message is opened.
- The composer remembers the last recipient.
- Logs show a removed or old-identity sender as raw hex.
- Debug text: "App: 1.0 (debug)", the Settings "Developer" row, and "Test TTS (temporary)" (`LegacyScreens.kt:541`).
- RMX5070: emergency_enabled=true is saved while the service is off, so a service restart could start Emergency mode.
- An overlay with "Close" plus hex text flashed once on RMX5108 (source UNCLEAR).
- The "Listening…" screen does not confirm the recorder started.

TODO / temporary markers in the code (`grep TODO|FIXME|temporary`):
- `speech/SpeechRecognizerManager.kt:82, 101, 141`: "PERF-INSTRUMENTATION (temporary)", to be removed in a perf pass.
- `LegacyModels.kt:51, 67` and `LegacyScreens.kt:541`: the "temporary Test TTS control".
- `TtsHelper.kt:58`: the thread count is not phone-profiled yet.
- There are no `TODO` or `FIXME` comments in `app/src/main`.

## 7. Phone-check checklist (to run later — NOT run)

**Before you start (once)**
- [ ] Install the **same** build on **all four** phones: branch `fix/identity-and-e1` at `80f1cef` (or later, if
      only docs changed). Use `adb -s <id> install -r app-debug.apk`. **Do not uninstall and do not clear data**;
      `-r` keeps the identity, contacts and stored bundles. My PC build's MD5 was
      `459663fde54b421648dc470500f3195a` (`app/build/outputs/apk/debug/app-debug.apk`). Rebuild if you prefer;
      either way, check the MD5 is the same on every phone.
- [ ] Before installing, write down each phone's 8-group fingerprint (Settings → "Your fingerprint"). The
      expected values from 2026-10-23: RMX5108 `e41c e39b 73d9 dbe5 0033 12cd ff66 0450`, I2221 `b9bd cc40 2eac acb1
      65dd c754 af3f 33b1`, RMX3392 `1af4 1afd 9629 a378 9623 0831 3029 8f1d`, RMX5070 `2983 a023 bfdc 3db4 c71f f550 e6aa e76a`.
- [ ] After installing, open the app on each phone and check the fingerprint has not changed.
- [ ] Run `foreman\check_models.ps1`: the 19-file model folder must be on every phone.

**A. Rename keeps the same identity** (use RMX5108 and I2221; they trust each other since G4)
- [ ] On RMX5108, Settings → "Your name": change the name → **SAVE**. Change it again → **SAVE** (the I2221
      sequence was two saves in one session).
- [ ] Settings → "Your fingerprint" still reads `e41c e39b 73d9 dbe5 0033 12cd ff66 0450`, and **not** `91c4 ad49 0157 6d46 7f67 fd42 3e46 5f57`.
- [ ] Force-stop the app (Android Settings → Apps → SIH_iTantra → Force stop) and reopen it. The fingerprint is
      still `e41c …0450` and the new name is shown. (With the old bug, this restart was when the zero identity appeared.)
- [ ] The QR is valid: on I2221, Add trusted contact → scan RMX5108's QR. The fingerprint screen must show
      `e41c e39b 73d9 dbe5 0033 12cd ff66 0450` and the new name, so it is accepted, not refused. Save; the row is
      replaced, not duplicated. (Before the fix, after one rename this QR was signed with a zero key and
      would have been refused.)
- [ ] Messaging still works both ways after the restart: Emergency ON on both, linked. Send a typed message
      RMX5108 → I2221 and I2221 → RMX5108. Each should arrive in Logs, and each sender should go Queued → Relayed →
      **Delivered**.
- [ ] Optional: do the same rename through onboarding (Settings → replay the intro → name card).

**B. Untrusted, then trusted: delivered and the status updates** (RMX5108 = sender S, I2221 = receiver R)
- [ ] Both trust each other, Emergency ON, linked.
- [ ] On R, Settings → remove S's contact ("Utkarsh Keshri").
- [ ] On S, send a typed message to R, for example `E1FIX one`. Wait about 30 s.
      Expected: S shows **Relayed**. R does **not** show it in Logs and does not speak it. R's events file has
      `dtn_bundle_rx` with mode RELAY.
- [ ] On R, re-add S by QR: scan S's QR and read **all 8 groups** (`e41c e39b 73d9 dbe5 0033 12cd ff66 0450`) before tapping "These match". Save.
- [ ] Keep both phones linked and untouched for 1 minute.
      Expected: R shows `E1FIX one` in Logs **once** and speaks it once (judge from logs; see the HANDOFF rules).
      Within one or two more sync rounds (about 5–10 s), S changes from Relayed to **Delivered**.
- [ ] Receipt case (T4B2), with both trusting each other again:
      1. On R, turn Bluetooth OFF.
      2. On S, send `E1FIX two` to R. It shows Queued.
      3. On S, remove R's contact ("vimalesh").
      4. On R, turn Bluetooth ON. Expected: R shows and speaks `E1FIX two` (by design: it was sealed while R was
         trusted). S stays **Relayed**, because R's receipt is held as a relay copy.
      5. On S, re-add R by QR: read all 8 groups, `b9bd cc40 2eac acb1 65dd c754 af3f 33b1`, then Save.
      6. Expected: within about 1 minute S changes to **Delivered**.
- [ ] Pull the events files (`/sdcard/Android/data/com.chmod777.itantra/files/bench/events_*.jsonl`) from both
      phones as evidence. Look for `product_delivered` on R and the receipt on S.

**C. Does the old stuck message a10b5860 deliver after install?**
- Honest expectation: **probably not, and that is correct.** A normal message lives **24 hours**
  (`ProtocolConfig.normalPrivateLifetimeMs`). a10b5860 was sent around 15:20 on 2026-10-23. Its age counts only
  while the phone is on, so if the phones were on for most of the last six days it has expired. The fix
  deliberately skips expired copies.
- [ ] **Before** installing, pull a read-only copy of I2221's and RMX5108's databases, as on test day
      (`agentA\collect.ps1` / `dbq.py`). Check whether bundle `a10b5860…` is still in I2221's bundle table,
      and what state RMX5108's outgoing row shows.
- [ ] If it is **gone** on I2221, or RMX5108 shows **EXPIRED**: it expired. Nothing can deliver it; mark the
      check "not applicable" and rely on test B.
- [ ] If it is **still there**: install, open the app on both phones (I2221 already trusts RMX5108 since G3),
      keep them linked for 1 minute. Expected: I2221 shows "T4B1 receiver removes sender" once, and RMX5108
      turns **Delivered**.
- [ ] The same applies to receipt `0059cd96` (T4B2, held on RMX5108): if it has not expired, RMX5108's
      "T4B2 sender removes receiver" should turn Delivered.
- [ ] `596de98a` (S3T4) can **never** deliver: it was addressed to I2221's old identity from before the reset. That is expected.

## 8. Evidence files

- `test-runs/2026-10-29/evidence/bug1_before_fix.txt`, `bug1_after_fix.txt`
- `test-runs/2026-10-29/evidence/e1_before_fix.txt`, `e1_after_fix.txt`
- `test-runs/2026-10-29/evidence/full_test.txt`: `gradlew.bat cleanTestDebugUnitTest test` → 138 tests, 0
  failures, 0 errors, 0 skipped (132 before this branch, plus 2 + 4 new).
- `test-runs/2026-10-29/evidence/assembleDebug.txt`: `gradlew.bat assembleDebug` → BUILD SUCCESSFUL, APK
  MD5 `459663fde54b421648dc470500f3195a`, no compiler warnings in the changed files.
