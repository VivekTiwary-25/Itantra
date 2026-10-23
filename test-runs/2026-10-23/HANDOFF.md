# HANDOFF.md - iTantra end-to-end test day, 2026-10-23

Written by the foreman at usage ~72% (not 90%): everything left needs Vivek's hands, so nothing more can run unattended.
Whoever reads this: first VERIFY the state below (read-only) before trusting it, then continue from "EXACT NEXT STEP".

## HARD RULES (word for word - they apply to you and every agent you start)
- Do NOT edit the networking/crypto engine. Engine bugs: write them down with the log lines that show them and report them to the foreman. I decide.
- Do NOT fix anything during a test. Collect UI bugs and report them. If we fix, we rebuild once and reinstall the same commit on every phone, so all phones run the same version.
- Do NOT uninstall the app, clear app data, or wipe anything without asking me first.
- Do NOT mark anything GREEN in STATUS.md. Only I do that.
- Do not guess. If unsure, say "unclear" and show the evidence.
- When something needs my hands (scan a QR, move a phone, tap something), the agent stops and tells the foreman. The foreman tells me ONE clear instruction and waits.
("I"/"me" = Vivek.)
Added later by Vivek: each phone has exactly ONE owner agent at a time and nobody touches a phone they do not own (always `adb -s <id>`); agents do not commit or push; nobody presses SAVE next to "Your name" on any phone (rename bug); the only approved identity reset was I2221's (done); the rename fix is only a proposal (`proposed-fix\`), not applied anywhere; text-to-speech/speech tests are judged from logs only (noisy room, no speaking into phones); before EVERY stage run `foreman\check_models.ps1` (the 19-file `files/indicconformer` folder must be on every phone, tell Vivek at once if any phone lost it, do not start the stage on that phone until fixed). Use Opus for security-critical work and PASS/FAIL decisions, Sonnet for routine jobs.

## Build and places
- Commit on all phones: **4729bd2db56453d5372af085c3e126ba9db7e4f3** (branch test/e2e-oct23, pushed). APK MD5 627c913204d88bdc881b3d2e9f0eadd8 (verified on all 4 phones at ~14:45). Package com.chmod777.itantra; the launcher icon of the test build is "SIH_iTantra" (on RMX3392 the icon "iTantra" is an OLD demo app, package com.chmod777.itantra.demo.vivek - never touch it).
- Repo: D:\projects\sih\itantra (also reachable as D:\projects\sih\iTantra). adb: C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe (not on PATH).
- ALL results: D:\projects\sih\iTantra\test-runs\2026-10-23\ -> stage0\, stage1\ (screens, per-phone), agentA\ (Stage 2/3 evidence, QR test images in agentA\qr\, DB copies, bench events), agentB\ (CHANGES.md, watch.log, stage4_plan.md, olddata_RMX5070\), agentC\ (RUNBOOK.md for Stages 4-6, pull_and_scan.ps1), foreman\ (check_models.ps1, push_models log, i2221_identity_backup\identity_v1.bin = the broken I2221 file, SHA-256 B5419490D5CA04EF1AA15531F0BE41FE573CE7F06E1640717CD190CD9E0334CA), proposed-fix\ (rename_fix.patch + LocalIdentityRenameTest.kt), NEEDS_VIVEK.md.
- The app's networking events are NOT in logcat: they are JSON lines in /sdcard/Android/data/com.chmod777.itantra/files/bench/events_*.jsonl on each phone (link_ready carries MTU, mtu_result, noise_start/noise_done/noise_failed, dtn_bundle_rx, dtn_bundle_tx, session_closed, product_queued, product_delivered). vivo I2221 hides the app's logcat lines; use the events file + dumpsys there.

## Phones, owners, battery (battery as of ~14:45; recheck)
| Phone | adb id | Owner (planned) | Battery | Identity fingerprint now | Notes |
|---|---|---|---|---|---|
| RMX5108 | adb-VCU4AAP7KVN7FQGQ-sbN1ac._adb-tls-connect._tcp | Agent A | 86% | e41c e39b 73d9 dbe5 0033 12cd ff66 0450 ("Utkarsh Keshri") | still holds OLD stale contact "Vimalesh" = old I2221 key 4b06... |
| I2221 (vivo) | adb-10BF3C28T90015K-eEfTYq._adb-tls-connect._tcp | Agent A | 70% | b9bd cc40 2eac acb1 65dd c754 af3f 33b1 (NEW since 14:51, name "iTantra user", no contacts) | volume up (15) |
| RMX3392 | adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp | Agent B | 58% | 1af4 1afd 9629 a378 9623 0831 3029 8f1d ("Vivek Tiwary") | planned MIDDLE phone; trusts nobody, empty DB; no "stay awake"; use ONLY the wireless id |
| RMX5070 | adb-a83e3811-XIQIEi._adb-tls-connect._tcp | Agent B | 41% | 2983 a023 bfdc 3db4 c71f f550 e6aa e76a ("Yash Mathur") | 4th phone, Emergency OFF; old data from 25 Sept (160 recv, 105 sent, contact PHONE_A); not deleted |
Agent ids in the finishing session (may not survive): A a7b1cff3006c050cd, B a7c29ac76efaf069b, C a674189cc586c66d8 - all idle/finished. Watcher script agentB\watch.ps1 (PID 33776, hidden, logs every 10 min to agentB\watch.log); stop with Stop-Process -Id 33776.
Nobody is currently using any phone. Identity check at ~14:55 (after fresh app restarts) showed the other three phones' fingerprints unchanged and none equal to the zero-key fingerprint 91c4 ad49 0157 6d46 7f67 fd42 3e46 5f57. Models: 19 files present on all four phones at ~14:55 (RMX3392's folder had vanished once at ~13:20 for an unknown reason - "no cause found" - and was re-pushed, sizes verified 697,424,531 bytes).

## Results so far
- Stage 0: PASS (all four on 4729bd2; 132 JVM unit tests, 0 failures).
- Stage 1 (every screen opens): PASS on all four, no crash in logcat. UI findings: "App: 1.0 (debug)" text on Report a problem; Settings "Developer" row opens the old legacy screen (incl. "Test TTS (temporary)" on I2221); RMX5070 Logs holds old 25-Sept data; names differ per phone.
- Stage 2 (RMX5108 <-> I2221, typed messages): PASS. QR trust both ways with fingerprint check; BLE link first try ~3.4 s (first GATT connect status 62, auto-retry worked), MTU 517, Noise handshake ~260-280 ms, delivery 0.4-5.4 s (5 s sync round), shown in both Logs, sender shows Delivered. TTS: ran on both (logs); heard by ear UNCLEAR (nobody listened).
- Stage 3: Test 1 (non-iTantra QR refused): PASS (a NON-iTantra image, not another person's real code; QR B bad-signature and QR C valid-impostor still unscanned). Test 3 (send to untrusted): PASS. Test 4 (remove contact, other side sends): the leak did NOT happen - the removed side stored the sealed message as a RELAY copy; never shown/spoken; sender stays "Relayed" (E1 open: does it ever deliver after re-adding?). Test 6 (receiver BT off then on): PASS (delivered 4.5 s after BT back, 81.7 s after send). Test 7 (long name): PASS (cut to 32 chars) with UI bug. Tests 2 and 5, and new Test 4b (in-flight removal, both directions): NOT STARTED, need hands.
- Hands-free "Listening...": mic really records (appops RECORD_AUDIO running; stops on Back). The RMX3392 "Listening... without mic permission" case is UNCLEAR (app does not check that AudioRecord really started).
- Agent B prep: permissions/BT/location/DND/volume/battery-saver exemptions done on RMX5070 and RMX3392 (see agentB\CHANGES.md for each change and how to undo). RMX3392 "Stay awake" needs a manual tap. Agent C: runbook for Stages 4-6 + pull_and_scan.ps1 written (never run on a phone).

## Bugs
Engine / identity (reported, NOT fixed):
- **RENAME BUG (critical):** identity/KeyManager.kt:71-78 `rename()` builds a LocalIdentity from the private-key arrays and then zeroes those same arrays (LocalIdentity does not copy its constructor arrays, LocalIdentity.kt:11-13). After the 1st "Your name" SAVE in a session the in-memory identity signs with a zero key (the QR it shows is invalid); after the 2nd the zero keys are saved to disk and the phone's identity becomes the public all-zero key (fingerprint 91c4 ad49 0157 6d46 7f67 fd42 3e46 5f57). Onboarding's name card also calls rename. Hit I2221 at ~14:02. STATUS: verified by Opus-level code reading and Agent A's evidence (agentA\identity_bug\); fix written as `proposed-fix\rename_fix.patch` (+ `withDisplayName` in LocalIdentity, rename uses it) and `proposed-fix\LocalIdentityRenameTest.kt`; patch checked with `git apply --check` only; the test has NOT been compiled or run; NOT applied to any branch or phone. Vivek decides when (recommendation: after the test run, one rebuild, reinstall same commit everywhere).
- I2221 identity reset (approved by Vivek): backed up the broken file, deleted only no_backup/identity/identity_v1.bin, restarted the app; new identity b9bd cc40 2eac acb1 65dd c754 af3f 33b1. DONE. Consequence: RMX5108 still has the old "Vimalesh" contact (4b06...) and I2221 has no contacts, so two-way trust must be redone by QR (hands).
- E1 (open): a message a recipient stored as a RELAY copy while it did not trust the sender is probably never delivered after trust is re-added (DtnNode.kt:183-185 skips bundles already held/seen). To confirm.
- E2 (minor, harmless): each connection also makes a second unused server link that drops after 20 s ("server_link_unused_detached").
- Debug back door: app/src/debug/.../ValidationReceiver.kt:54-58 `import` adds a trusted contact with no fingerprint check (debug build, adb only). Do not use it while testing QR trust.
- Relayed SOS requests/cancels are forwarded over hops and readable by relays (offer/accept/decline/chat are direct-only); a connected SOS cannot be ended from the main app (SosScreens.kt:263-294, endSession never called). Scanner may stall after ~30 min (BleDiscovery.kt:104-137, unclear).
UI bugs (not fixed): Settings contact list does not refresh after rename (TrustedContact.equals compares nodeId only); unread badge clears only when a message is opened; saved contact shows only 4 of 8 fingerprint groups and there is no full-fingerprint view; composer remembers the last recipient; debug text/Developer row; RMX5070 emergency_enabled=true saved setting although service is off (a service restart could start Emergency mode).

## Half-done / not started
- Re-trust RMX5108 <-> I2221 (needs 2 QR scans by Vivek). Then Test 4b, Test 2 (QR C), Test 5, E1 (needs trust restored; hands).
- Stage 4 (three-phone relay) fully prepared: plan agentB\stage4_plan.md, runbook agentC\RUNBOOK.md, scan script agentC\pull_and_scan.ps1 (use -Phones, -SinceWallMs, -Marker KESTREL41 -PullDb). NOT started: needs Vivek to place phones. Middle = RMX3392 (charge to ~70% first).
- Stages 5 (SOS) and 6 (all four phones, 30-min battery) not started; they start only when Vivek says go.

## NEEDS_VIVEK.md (copy; the file itself is test-runs\2026-10-23\NEEDS_VIVEK.md)
0. Charge RMX3392 (to ~70%) and RMX5070; optional RMX3392 Developer options > Stay awake.
1. TOP: Stage 4 relay test (only after item 2): I2221 spot C START; RMX5108 spot A (25-30 m, 2 walls) START; wait 3 min; RMX3392 middle spot B START; wait 60 s; send KESTREL41 one..five from RMX5108 to the I2221 contact; carry test = I2221 Bluetooth OFF, send `KESTREL41 carry`, wait 5 min, Bluetooth ON, wait 2 min; do not tap STOP until logs are pulled.
2. BEFORE Stage 4: re-trust by QR with fingerprint checks: RMX5108 scans I2221's QR (expect b9bd cc40 2eac acb1 65dd c754 af3f 33b1), I2221 scans RMX5108's QR (expect e41c e39b 73d9 dbe5 0033 12cd ff66 0450); the agent removes the stale "Vimalesh" contact on RMX5108 first.
3. Remaining Stage 3: Test 4b, Test 2 (I2221 scans agentA\qr\C_impostor_valid.png shown on the PC; fingerprint 43c5 9182 4d1b d679 b926 9980 559d f1f5 must NOT match), Test 5 (RMX5108 re-scans I2221), E1.
4. Optional: listen to TTS by ear; decide whether to clear RMX5070's old data; decide when to apply the rename fix.

## EXACT NEXT STEP
1. Verify state (adb devices -l; run foreman\check_models.ps1; confirm APK md5 and fingerprints above; nobody else using the phones).
2. Tell Vivek ONE instruction: the first re-trust scan (item 2 in NEEDS_VIVEK.md). Use Agent A (Opus, owns RMX5108 + I2221) for the trust steps and for the fingerprint decisions; resume it with its transcript if it still exists, otherwise start a fresh Opus agent with the rules above and the contents of agentA\ as context.
3. Then Test 4b, Test 2, Test 5, E1; then Stage 4 with Agent B (RMX3392 + RMX5070) and Agent A together, following agentB\stage4_plan.md.
Do not repeat any test that already has a result above.

## UPDATE 15:05 (foreman, usage ~75%) - Stage 4 pre-flight decisions
- **Logcat buffers:** `adb logcat -G 256M` was accepted on ALL FOUR phones (main/system/crash/kernel now 256 MiB; was 4 MiB on I2221, 256 KiB on the others). It resets if a phone reboots - re-check with `adb -s <id> logcat -g` before each stage. Record: foreman\logcat_buffer_sizes.txt.
- **If wireless adb drops at an outer spot:** the phone keeps running and keeps its logs. (1) Logcat is a ring buffer ON THE PHONE and the app's own events are written to files ON THE PHONE (bench\events_*.jsonl), so an adb drop loses nothing; (2) before each stage write down every phone's clock (`adb shell date +%s`) = stage start; (3) after everyone is back in range pull logcat (`logcat -d -v time`) and the bench folder with agentC\pull_and_scan.ps1 -SinceWallMs <start>; (4) PROOF nothing was lost: the first logcat line must be EARLIER than the stage start (the buffer never wrapped; at 256 MiB it will not), the events file must contain a `runtime_init`/run id from before the stage and its t_ns values must only increase, the last line must be after the stage end, and per-phone event counts must agree across phones (a link_ready on one phone needs the matching one on its peer; a dtn_bundle_tx needs the peer's dtn_bundle_rx). Silent periods can look like gaps, so "no timestamp gap" alone is NOT proof; the first-line-before-start check is.
- **Spot probe:** `foreman\probe_spots.ps1 -Spot A|B|C` (read-only) reports per phone whether adb answers within 8 s, reply time, phone clock and Wi-Fi RSSI/link speed, and appends to foreman\probe_spots.log. Tested at the PC desk: all four OK (RSSI -50 to -63 dBm). The real spot test needs Vivek to carry the phones to spots A, B, C (hands) - NOT yet done. Spots that lose adb are recorded as NO_ANSWER; that spot is still usable for the relay test (logs are pulled later) but the agent cannot watch live there.
- **Decision on who runs Stage 4:** hand Stage 4 (and Stages 5-6) to the WORKER LOGIN with a fresh usage budget; this session finishes the Stage 3 tests (re-trust, Tests 2/5/4b) and keeps this file current. Reason: Stage 4 needs several hours of Opus work reading relay logs and ~3 people on site, and this session is past 75%.

## GROUP LOG (one line per group)
- G1 (15:04): Vivek scanned both ways and compared the 8 fingerprint groups; saved "vimalesh" on RMX5108 (b9bd cc40 2eac acb1 65dd c754 af3f 33b1) and "Utkarsh Keshri" on I2221 (e41c e39b 73d9 dbe5 0033 12cd ff66 0450). PASS - verified by Agent A from both phones' databases (all 8 groups, QR_VERIFIED, own identities unchanged, 1 contact each; agentA\db\*_after_G1). Models 19/19 on both.
