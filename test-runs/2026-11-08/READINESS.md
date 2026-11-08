# READINESS — fix/identity-and-e1, 2026-11-08

Nothing here is GREEN. STATUS.md was not edited. No phone was uninstalled or cleared.

## 1. The build

| | |
|---|---|
| Branch | `fix/identity-and-e1` (pushed) |
| Commit | `b460de906970cee35364d09211bcc20de2247c91` (`b460de9`) |
| APK | `app/build/outputs/apk/debug/app-debug.apk`, clean build (`gradlew clean assembleDebug`) from a tree with no tracked changes |
| APK MD5 | `1f14a7e87d12591bc6298488d6a1a79e` |
| `BUILD_INFO.txt` | rewritten for this APK, so `install-test-phone.ps1 -Serial <serial>` accepts it |
| JVM tests | 138 / 138 pass, 0 skipped (`gradlew test`; the app code is the same as the 7 Nov run, so Gradle reused that result) |

What this build has over the 23 Oct phones (`4729bd2`): the rename fix (`02bce34`), the E1 fix (`80f1cef`) and the
"speech model missing" message (`58fc9d2`). The rest of today's commits are docs, the install script and the test.

## 2. Speech-to-text, checked on RMX3392 by feeding audio files

**How:** 5 Google FLEURS test clips per language (16 kHz, 16-bit, mono; the same clips the September measurements
used; the English ones were downloaded again today). They were fed into the app's real `SpeechEngine` on the phone by
`SttParityInstrumentedTest`. That test lives in the separate test APK (`com.chmod777.itantra.test`), never in the app.
It was installed with `adb install -r -t` and run with `adb shell am instrument`, never with connectedAndroidTest.
The clips were copied into the app's storage for the run and deleted afterwards. They are not in git.

**Afterwards:** the identity file, both settings files and all 19 model files had exactly the same checksums as
before (`evidence/RMX3392_identity_models_*`). The first-install date is still 23 Oct, so nothing was uninstalled.

**Scoring:** character error rate (CER) and word error rate (WER) against the FLEURS transcript. Both sides are
lower-cased, punctuation is removed and spaces are collapsed. Scripts: `stt/tools/score.py`; per-clip results:
`stt/phone/`. All 10 languages loaded their model (`model_present` = true), and every output was 100% in the right
script.

| Language | Model | CER | WER | Time per clip, incl. first load | Verdict |
|---|---|---|---|---|---|
| English (`en`) | Whisper tiny.en, inside the APK | 4.2% | 11.5% | 1.6 s | Good |
| Hindi (`hi`) | IndicConformer | 2.2% | 8.1% | 2.7 s | Good |
| Gujarati (`gu`) | IndicConformer | 7.8% | 18.5% | 2.6 s | OK (numbers come out as words) |
| Marathi (`mr`) | IndicConformer | 5.9% | 17.5% | 2.3 s | OK |
| Kannada (`kn`) | IndicConformer | 3.3% | 8.2% | 2.3 s | Good |
| Malayalam (`ml`) | IndicConformer | 10.7% | 25.4% | 3.4 s | Fair (numbers as words, chillu spellings) |
| Tamil (`ta`) | IndicConformer | 32.0% | 46.8% | 2.9 s | **Flag**, but see below |
| Telugu (`te`) | IndicConformer | 1.1% | 8.7% | 2.1 s | Good |
| Odia (`or`) | IndicConformer | 11.0% | 24.1% | 2.1 s | Fair (numbers and "AD" written out as words) |
| Bengali (`bn`) | IndicConformer | 1.5% | 10.5% | 2.1 s | Good |

**Tamil:** one clip (`003-1666`) has the speaker saying the sentence twice. The model wrote both takes, which
doubles the length; the September desktop run did the same. Without that clip Tamil's CER is **9.8%**. Tamil's WER of
46.8% matches September's phone figure (46.77%), so nothing has got worse. Tamil remains the weakest language for words.

**Samples (expected → got):**
- en: "many people don t think about them as dinosaurs…" → "…as a dinosaur…"
- hi: "टेलीविजन रिपोर्टों में … धुआँ दिखाया गया है" → "टेलीविज़न … धुआं …" (spelling variants only)
- kn: "ಸಹ ಕುಸ್ತಿಪಟುಗಳು ಸಹ ಲೂನಾ ಅವರಿಗೆ ಗೌರವ ಸಲ್ಲಿಸಿದರು" → identical
- gu: "…લગભગ 70 કિલોમીટર…" → "…લગભગ સિત્તેર કિલોમીટર…"
- ta: "…ராஜு கண்டெல்வலும்…" → "…ராஜு கண்டல்வாலும்…"
- or: "…11ଶ 12ଶ ଏବଂ 13ଶ ଶତାବ୍ଦୀରେ…" → "…ଏକାଦଶ ଦ୍ୱାଦଶ ଏବଂ ତ୍ରୟୋଦଶ ଶତାବ୍ଦୀରେ…"

**First English run was invalid, and it was my mistake, not the app's.** The English clips I first made were all
silence (a float-to-16-bit conversion bug in my download script). The engine correctly returned `[BLANK_AUDIO]`. Those
clips were remade and the run repeated; the table shows the repeat.

**Not covered:** speaking into the microphone, and the new "isn't installed" message on screen (that would need a
model file renamed on the phone). Both belong in the field test.

## 3. The phones

| Phone | Build installed | Models | Fingerprint now | Notes |
|---|---|---|---|---|
| RMX3392 | `b460de9`, MD5 `1f14a7e8…` checked on the phone, 8 Nov 16:53 | 19 / 19 | `da61 127a d45e 8808 665e 8e15 8408 e960` | New identity since its app data was cleared on 29 Oct 10:01 (not uninstalled: first install is still 23 Oct). Trusts one contact, "Suyash Gupta" `6f44 c48f adc3 22f1 d046 50bd 983b 5a7e`. Emergency mode off. The test APK `com.chmod777.itantra.test` is also installed; it is harmless and holds no data. |
| I2221 | not checked (not connected) | not checked | 23 Oct value `b9bd cc40 2eac acb1 65dd c754 af3f 33b1`, **probably changed** | "Suyash Gupta" `6f44…` above matches none of the 23 Oct fingerprints. Suyash carried I2221 on 23 Oct, so I2221 was probably reset too. |
| RMX5108 | not checked | not checked | 23 Oct value `e41c e39b 73d9 dbe5 0033 12cd ff66 0450` | |
| RMX5070 | not checked | not checked | 23 Oct value `2983 a023 bfdc 3db4 c71f f550 e6aa e76a` | Has old 25 Sep test data, and the saved-Emergency-setting bug. |

## 4. What the 29 Oct "Queued" test left on RMX3392

Source: `evidence/RMX3392_2026-10-29_events_timeline.txt` (from RMX3392's own events file) and a read-only copy of
its database.
- 13:13:24: RMX3392 trusted "Suyash Gupta" by QR. 13:14:00: it queued a push-to-talk message, "Hello", to that contact.
- 13:17:33: linked to a peer and **sent the message as a relay copy** (`dtn_bundle_tx mode RELAY`). 13:19:21: same
  again with a second peer. A third peer at 13:20:45 asked for nothing.
- **What that means:** the message did leave RMX3392. But every phone it met asked for it as "someone else's message
  to carry", not "a message for me". So, at that moment, none of the phones RMX3392 met had both trusted RMX3392's
  new key and been the recipient. That points to trust or identity being out of step after the 29 Oct reset, not to
  the E1 code:
  - RMX3392 held no relay copies at all, so the E1 re-check had nothing to do.
  - The message was never shown as delivered and expired after 24 hours (the database now says EXPIRED).
- Also in the log: links dropped after 60–90 s with "GATT write failed", and one reconnect failed 3 times (status -3).
  The known unused second link was seen as well (E2).
- **Not settled:** what the other phone's screen and log showed. Only RMX3392's side survives. So the Queued problem
  is **explained in part, not solved**. The two-phone check in the plan below is what settles it.

## 5. Proven and not proven

**Proven on real phones**
- 23 Oct, build `4729bd2`: every screen opens; QR trust works both ways and an impostor is refused; two-phone
  delivery takes 0.4–5.4 s and reaches Delivered; carry-and-deliver works when Bluetooth comes back; break tests 4b.
- 8 Nov: all 10 speech languages load and transcribe on RMX3392 (by file feed, section 2).

**Not proven on a phone yet**
- The rename fix and the E1 fix: no phone has run them in a test.
- Two-phone delivery on this build. The 29 Oct attempt did not deliver (section 4).
- Three-phone relay (Stage 4), SOS (Stage 5), the 30-minute battery run (Stage 6), and speech heard by ear.
- Push-to-talk speech in real use.

**Known open issues (not fixed; engine ones need your decision)**
- With several phones linked, a message that arrives just as trust is restored can stay stuck until the app restarts.
- A connected SOS cannot be ended from the main app. Relayed SOS requests can be read by the relay phones.
- E2: an unused second link drops after 20 s.
- UI bugs from 23 Oct (stale names, unread badge, composer remembers the last recipient). See the 29 Oct REPORT §6d.

## 6. Wireless debugging

**Checked from here:** the laptop is on `NIE-BH` (10.20.184.0/21). Its address table already lists other devices
on that network (3 neighbours), which is a good sign but **does not prove** two clients can reach each other. RMX3392's
Wi-Fi is off, so I couldn't test it end to end.

**Two-minute check on your side:**
1. Put one phone on `NIE-BH`. Settings → Additional settings → Developer options → **Wireless debugging** ON.
2. On that screen, note the **IP address & Port**, e.g. `10.20.x.y:4xxxx`.
3. Tell me the IP. I run `ping 10.20.x.y`. If it answers, tap **Pair device with pairing code** and read me the
   pairing IP:port and the 6-digit code; I run `adb pair` and `adb connect`.
4. If ping times out, or `adb connect` hangs, the network blocks devices from seeing each other. Use the fallback.

**Fallbacks, best first:**
1. **Laptop hotspot** (Settings → Network → Mobile hotspot, band 5 GHz if offered). The phones join it, and wireless
   debugging works the same way. Windows may refuse to start the hotspot when the laptop has no internet connection.
   If it does:
2. **A spare phone's hotspot** (RMX5070 is not needed until the relay test). The laptop and the test phones join it.
   No internet is needed.
3. **USB.** Always works, but it ties the phones to the laptop. Fine for the two-phone test; not for the floors test.

Notes:
- The app talks over Bluetooth, not Wi-Fi. Wi-Fi is only for watching the phones.
- Wireless debugging switches off when a phone leaves the network or restarts, and the port changes each time. If a
  phone drops, I'll ask for the new IP:port.
- A 5 GHz network keeps Wi-Fi traffic off the 2.4 GHz band Bluetooth uses.
- The APK is 875 MB and the models about 700 MB. **Install over USB before going out**, not over Wi-Fi.

## 7. Field-test plan (do the batches in order; reply "done" after each)

Roles in the two-phone part: **S** = sender, **R** = receiver. Which two phones is your call when we get there.
Last time S was RMX5108 and R was I2221.

### Batch 0 — at the laptop, over USB (about 10 min per phone)
1. Plug in one phone. I run `.\install-test-phone.ps1 -Serial <serial>`. It installs with `-r`, copies the speech
   models only if any are missing, checks the APK MD5 on the phone and prints PASS or FAIL. It never uninstalls.
2. I read that phone's fingerprint, contacts and Emergency state with the debug `state` / `capsule` commands (no
   taps), and fill in the table in section 3.
3. Repeat for each phone. RMX3392 already has the build. Charge any phone under 60%.

### Batch 1 — connect the two phones wirelessly
1. Both phones and the laptop on the same network (section 6).
2. On each phone: Developer options → Wireless debugging → Pair device with pairing code. Read me the pairing IP:port
   and the code, then the main IP:port.
3. I confirm both show in `adb devices` and start live logs from both.

### Batch 2 — trust both ways (your hands)
1. **R:** Home → profile icon at the top → the screen with its QR code.
2. **S:** Settings → Add trusted contact. Point the camera **only** at R's screen.
3. **S:** read all 8 groups aloud. I check them against R's fingerprint from Batch 0. Tap "These match" only when I
   say they match. Name the contact, then Save contact. (Not the SAVE next to "Your name".)
4. Swap: R scans S's QR the same way.
5. I check that both contact lists hold the other phone with the right fingerprint.

### Batch 3 — two-phone delivery (the gate for everything after it)
1. Both phones on Home, side by side, 1–2 m apart. Tap **START** next to "Emergency mode off" on both.
2. Wait about 15 s. I watch for `link_ready` and `noise_done`.
3. **S:** Write message → pick R → type `FIELD one` → Send.
4. Expected within about 10 s: on S the message goes Queued → Relayed → **Delivered**. On R it appears in Logs and is
   spoken. I look for `dtn_bundle_tx mode DESTINATION` on S, and `product_delivered` plus a receipt on R.
5. Repeat the other way: **R** sends `FIELD two` to S.
6. I pull both events files (`/sdcard/Android/data/com.chmod777.itantra/files/bench/events_*.jsonl`).
7. **If either message stays Queued or Relayed for more than 2 minutes:** stop here. I diagnose from the logs and the
   two databases (read-only) and report to you. No engine edits. Batches 4 onwards wait.

### Batch 4 — optional quick checks of the two fixes (5 min each)
- Rename (REPORT 2026-10-29 §7 A): on S, change "Your name" twice → SAVE each time. Force-stop and reopen. I confirm
  the fingerprint has not changed and that a message still delivers.
- E1 (REPORT §7 B): R removes S; S sends `E1FIX one` (shows Relayed); R re-adds S by QR; within about 1 minute R
  shows it once and S turns Delivered.

### Batch 5 — three-phone relay (Stage 4)
- Plan: `test-runs/2026-10-23/stage4/STAGE4_FLOORS_PLAN.md`, cards in `stage4/CARDS.md`. S and R on different
  floors, the middle phone M (e.g. RMX3392) in between. M does not need to trust anyone.
1. Tap STOP on S and R before carrying them. Put R upstairs and tap START. Put S downstairs and tap START. Wait
   3 minutes: I confirm from the logs that S and R never link.
2. Put M in the middle and tap START. Wait 60 s.
3. S sends `KESTREL41 one` to R. Expected: S links M, M carries it, M links R, R shows it and S turns Delivered. I check
   that M never shows or speaks the text.
4. Carry test: R Bluetooth off; S sends `KESTREL41 carry`; wait 5 minutes; R Bluetooth on; wait 2 minutes.
- Watching live needs all three phones on Wi-Fi. If the hostel Wi-Fi doesn't reach every floor, run it blind and I
  pull the logs over USB afterwards.

### Batch 6 — speech in real use
1. On S and on R, in turn: tap the language chip on Home, pick your language, hold the talk button, say a short
   sentence, let go.
2. Read the composer text. Is it what you said? Send it to the other phone.
3. The other phone should speak it in that language. Every language has a voice in this build (en, hi and ml use
   Piper; bn, gu, kn, mr, or, ta and te use MMS). Tell me if it sounds right.
4. Try at least English, Hindi, Kannada and one more. Expect Tamil to be the weakest.

### Batch 7 — SOS (Stage 5)
1. S: Home → SOS → pick a category → start. R should get the incoming SOS. I watch for `sos_request_tx` / `rx`.
2. R: accept/help, and send one chat line each way.
3. S: cancel the SOS. Known issue: a *connected* SOS cannot be ended from the main app (`SosScreens.kt:263-294`). If
   it stays on, tell me, and don't force-stop until I've pulled logs.

Afterwards: tap STOP on every phone. I pull events and logs and write `test-runs/<date>/FIELD_REPORT.md`.

## 8. Go / no-go

**Ready for field testing, with one gate.**
- The build is fixed and pushed, and installed and verified on RMX3392.
- Speech works in all 10 languages by file feed.
- Every step above is reversible and never uninstalls.

**The gate:** Batch 3 (two-phone delivery on this build) must pass before the relay, speech or SOS batches. The
29 Oct attempt did not deliver. Section 4 points to trust being out of step rather than a code fault, but only a clean
two-phone run settles it.

Before Batch 3, the other three phones need this build and a model check (Batch 0).
