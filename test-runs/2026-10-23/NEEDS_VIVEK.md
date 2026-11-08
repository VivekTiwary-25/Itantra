# NEEDS_VIVEK.md - every step that needs Vivek's hands (kept up to date by the foreman)

**UPDATE 15:25 (usage 91%): G1 and G2 are DONE. NEXT = G3 (below), then G4, then Stage 4. Section 2 and the Test 2/Test 5 items of section 3 are finished.**

**15:33 (new foreman session): state re-verified read-only.** 4 phones connected; app MD5 627c9132... on all 4; models 19/19 on all 4; own fingerprints match the table (none is 91c4...); logcat 256 MiB on all 4; old session idle. RMX5108 on "You" screen with QR, I2221 on camera screen - G3 can go.
Mismatches found: (a) RMX5070 is NOT charging (39%); (b) RMX5070 holds a sealed relay copy of the Test 4 message (bundle 596de98a, received 14:36:54, 2 hops) so its Emergency mode was ON for a while around 14:36 - who/what turned it on is UNCLEAR (likely the known "emergency_enabled=true" saved-setting bug); it is OFF now. Copy of its DB: foreman\rmx5070_db_1530\.

**~15:37: Vivek reports G3 done; RMX5070 plugged in. Agent A (Opus) verifying G3 + watching E1; Agent B (Sonnet) read-only checks on RMX3392/RMX5070. Do not touch any phone until the foreman says.**

**15:40 Agent B (agentB\check_1535.md):** RMX5070 charging (43%), RMX3392 93% now unplugged; Emergency Off on both; models 19/19; logcat 256 MiB. The RMX5070 mystery is EXPLAINED: someone opened the app on RMX5070 by hand at 14:36:49 and Emergency ran for 6 s (14:36:51-14:36:57, started and stopped by a user), so it picked up one sealed relay copy (probably from I2221). Not the saved-setting bug. Who tapped: unclear. RMX3392's very high "load" number looks like a broken counter (phone is idle and healthy); a reboot would clear it - Vivek decides, not needed now. Note: check_models.ps1 with `-File ... -Phones A,B` gives a FALSE "LOST"; run it with no -Phones or one phone at a time.

**16:20: Stage 4 on FLOORS (Suyash upstairs I2221, Vivek middle RMX3392, Utkarsh downstairs RMX5108, Yash spare RMX5070): plan stage4\STAGE4_FLOORS_PLAN.md, helper cards stage4\CARDS.md. NEXT = Vivek's group S4-0 (STOP on RMX5108 and I2221 at the PC).**

## G3 - re-add on I2221 (E1 check) - DONE by Vivek (~15:37), verification running
Phones are set: RMX5108 on Settings with its QR at the top; I2221 on the camera screen; Emergency mode ON on both.
1. Keep I2221's camera pointed away from the PC and from any other QR.
2. I2221: point it ONLY at the QR on RMX5108's screen until it buzzes.
3. I2221, "Check this is really them": READ all 8 groups first: e41c e39b 73d9 dbe5 0033 12cd ff66 0450.
4. Only if all 8 match tap "These match" (else "They don't match" and tell the foreman).
5. I2221, "Name this contact": leave the name, tap "Save contact".
6. Put both phones down, touch nothing for 3 minutes (the agent watches whether the stuck T4B1 message now arrives = E1).
Report back: "G3 done" + any group that did not match.

**15:47: G3 PASS, E1 CONFIRMED (stuck messages never delivered; engine bug DtnNode.kt:183). Next: Test 4b direction 2 by Agent A (agent only), then G4.**

**15:57: Test 4b direction 2 DONE (message still delivered after the sender removed the contact - by design, the dialog warns; sender wrongly stays "Relayed").**

**16:10: G4 PASS - RMX5108 and I2221 trust each other again. Stage 3 COMPLETE. Next = Stage 4 (section 1) - waiting for Vivek: how many people, which spots.**

## G4 - restore trust before Stage 4 - DONE 16:03
1. I2221: Home > tap the profile icon at the top > the "You" screen with the QR code.
2. RMX5108: Settings > Add trusted contact (camera screen).
3. Keep RMX5108's camera away from the PC and any other QR.
4. Point RMX5108 only at the QR on I2221's screen until it buzzes.
5. RMX5108: read all 8 groups first: b9bd cc40 2eac acb1 65dd c754 af3f 33b1.
6. Only if all 8 match tap "These match" (else "They don't match" and tell the foreman).
7. RMX5108: name it vimalesh, tap "Save contact". (NOT the SAVE next to "Your name".)
8. Put both phones down. Report "G4 done".


Last update: foreman, usage ~72%. Build on all phones: commit 4729bd2. Rule for today: NOBODY presses SAVE next to "Your name".

## 0. Charging (do first, no waiting needed)
- Plug in **RMX3392** (middle phone, 58%) until about 70%, or use a power bank at the middle spot.
- Plug in **RMX5070** (41%) before Stage 5.
- Optional on RMX3392 while it charges: Settings > Additional settings > Developer options > turn on **Stay awake**.

## 1. TOP PRIORITY - Stage 4: three phones in a line (relay) - NOT STARTED, needs you
Full plan with expected results: `agentB\stage4_plan.md`. Roles: **S = RMX5108** (sender), **M = RMX3392** (middle),
**R = I2221** (receiver). **RMX5070 stays OFF (Emergency mode off) the whole time.** Message marker word: KESTREL41.
Do this only AFTER section 2 (re-trust of S and R) is done. One instruction at a time; wait for the foreman each time.
1. Put I2221 on a table at spot C, screen up. Tap START on "Emergency mode off".
2. Walk 25-30 m (35-40 steps), with at least two solid walls between (or another floor), to spot A. Put RMX5108 on a table, screen up. Tap START.
3. Step away and wait 3 minutes without touching anything (agents prove from logs that S and R cannot see each other; if they do, move further apart).
4. Walk to the middle spot B (12-15 m / 15-20 steps from each). Put RMX3392 on a table, screen up. Tap START. Wait 60 seconds.
5. Go to spot A. On RMX5108: Write message > Select trusted contact > pick the I2221 contact > type `KESTREL41 one` > Send. Wait 2 minutes. Repeat for two..five, about 20 s apart.
6. Carry test: on I2221 turn Bluetooth OFF. Send `KESTREL41 carry` from RMX5108. Wait 5 minutes. Turn I2221 Bluetooth ON, wait 2 minutes.
7. Do NOT tap STOP on any phone until the foreman says logs are pulled.
Agents check: middle phone never shows/speaks the text (zero marker hits), hop appears in logs, delivery times.

## 2. BEFORE Stage 4 - re-trust (I2221 has a NEW identity since 14:51, so the old trust is void)
I2221's new fingerprint: **b9bd cc40 2eac acb1 65dd c754 af3f 33b1** (old broken one was 91c4...). RMX5108's fingerprint: **e41c e39b 73d9 dbe5 0033 12cd ff66 0450**.
RMX5108 still holds an OLD contact "Vimalesh" (old I2221 key, fingerprint 4b06...). It is stale and will be removed by the test agent on RMX5108 first.
- 2a. Hold **RMX5108** so its camera sees the QR code on **I2221's** Settings screen (profile icon at top of Home). When RMX5108 shows the fingerprint screen, STOP and tell the foreman. The agent checks all 8 groups against b9bd cc40... and says "match" or "no match". You then tap "These match" only after the foreman says match, and type the name Vimalesh.
- 2b. Hold **I2221** so its camera sees the QR code on **RMX5108's** Settings screen. Same fingerprint check (e41c e39b...). Name it Utkarsh.
- Neither phone may press SAVE next to "Your name".

## 3. Remaining Stage 3 tests (need hands; start after section 2)
- **Test 4b** (in-flight removal, both directions): agent does Bluetooth off/on and removing contacts by adb/UI; hands only needed to re-scan after each removal (one instruction each time).
- **Test 2** (fingerprint check must fail): hold I2221 so its camera sees the file `agentA\qr\C_impostor_valid.png` shown on the PC screen. The agent checks the fingerprint 43c5 9182... is NOT e41c... and taps "They don't match"; nothing may be saved.
- **Test 5** (re-scan existing contact): hold RMX5108 so its camera sees I2221's QR again.
- **E1 question:** after trust is restored, does the old "S3T4 after I2221 removed me" message ever get delivered?

## 4. Optional, only if you want
- Listen to text-to-speech by ear on a quiet moment (we judged it from logs only; "heard by ear" is UNCLEAR). I2221 volume is now up.
- Decide whether the old test data on RMX5070 (160 received, 105 sent, contact PHONE_A, 25 Sept) should be cleared. Left alone for now.
- Decide when to apply the rename-bug fix: see `proposed-fix\` (NOT applied anywhere).
