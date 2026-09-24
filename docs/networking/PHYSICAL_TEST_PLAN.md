# iTantra v1 networking — physical test plan

Every gate below is **not yet demonstrated** until a person runs it on real phones.
A green build or a passing JVM test does not count. Vivek assigns GREEN.

## Setup (every phone)

1. Install the debug build: `.\gradlew.bat assembleDebug`, then
   `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
   `-r` keeps existing app data. Do **not** use `connectedAndroidTest`: it
   uninstalls the app afterwards.
2. Open **iTantra Net Lab**. It has its own launcher icon in debug builds.
3. Tap **Permissions** and allow Bluetooth (Nearby devices) and notifications.
4. Tap **Start**. Expect the state `ON`, `adv=advertising scan=true gattServer=true`,
   and the "iTantra Emergency mode is ON" notification.
5. Turn mobile data **off** and make sure no Wi-Fi network has Internet (spec §55).
   Check that a browser page fails to load.

To run Emergency mode without taps:
`adb shell am start -n com.chmod777.itantra/.ui.lab.NetworkLabActivity --ez autostart true`

Evidence files live on each phone under
`/sdcard/Android/data/com.chmod777.itantra/files/bench/`. Pull them with Git
Bash, setting `MSYS_NO_PATHCONV=1` first so the device path is not rewritten:

```
MSYS_NO_PATHCONV=1 adb -s <serial> pull /sdcard/Android/data/com.chmod777.itantra/files/bench/ ./bench-<phone>/
```

`t_ns` in the JSONL is that phone's `elapsedRealtimeNanos`. **Never subtract `t_ns`
values from two different phones.**

## Gate B — two-phone BLE link (phones A, B; never paired)

1. Start Emergency mode on A and B, about 1 m apart.
2. Within about 10 s each phone lists the other under **Nearby** with an RSSI.
   Within about 30 s each shows exactly one `session … BLE_GATT mtu=… noise=…ms`.
   - Record the MTU. 517 is requested; anything from 23 upward is valid.
   - Only one session per pair is expected (collision rule). Two sessions means
     the duplicate-link resolution failed: pull both logs.
3. Bidirectional frames: on A pick the session and run the benchmark with Trials 20,
   Hops 1, Payload 200. Expect 20/20 successes. Repeat from B towards A.
4. Reconnect: Stop on B, wait for A's session to disappear, then Start on B again.
   Expect a new session within about 30 s and another 20/20 benchmark.
5. Evidence: `link_ready`, `noise_done`, `mtu_result`, `probe_result` lines on both phones.

## Gate C — three-phone relay (A, B, C in a line)

Place A and C out of each other's radio range. Walls or distance both work; confirm
that A does not list C under Nearby. B sits between them.

1. Start all three. B must show two sessions. A and C show one each.
2. On A, pick its session and run the benchmark with Trials 30, Hops 2, Payload 200.
   A success means the probe went A→B→C and the echo came back C→B→A before the timeout.
   `relay_processing_ms` is B's own decode-to-handoff time.
3. Trusted message relay (see Gate D setup for trust): A queues a message to C. It shows
   **Relayed** after B accepts, C's Inbox shows it once, and later A shows **Delivered**.
   B's Inbox must stay empty.
4. Repeat step 2 five times across the session and keep every CSV. Failures stay in.

## Gate D — DTN store-carry-forward (spec Test B, C, E, H)

Trust setup: on A tap **Show my QR**; on C tap **Scan QR**, compare the fingerprint
aloud, then **Fingerprint matches — trust**. Repeat in the other direction.

1. **Destination absent.** Keep C far away or Stopped. A queues "test D1" to C.
   Outbox = `Queued`.
2. A meets relay R (Start both, near each other). Outbox = `Relayed`, and R's
   **Carried bundles** shows one RELAY bundle with `tokens=2`. A still holds its own
   copy (`tokens=2`).
3. Stop A (or take it away). Kill R's process (`adb shell am force-stop com.chmod777.itantra`),
   reopen Net Lab and Start. The bundle is still listed (spec Test H).
4. Bring R to C. C's Inbox shows "test D1" exactly once. R's copy disappears
   (tombstone). R now carries C's receipt bundle.
5. Bring R back to A. A's Outbox = **Delivered**. It must never have shown
   Delivered before this step.
6. Duplicate route (Test C): queue "test D2", let A meet R1 and R2 separately, then R1
   and R2 each meet C. C shows it once.
7. Expiry (Test E): queue an **Urgent** message to an absent contact and wait 6 h, or
   build with a shorter `urgentPrivateLifetimeMs`. Outbox = `Expired` and no phone
   still carries it.

## Gate E — security boundary

1. After Gate D, pull R's database. The phone's run-as access works because the
   build is debuggable:
   `adb exec-out run-as com.chmod777.itantra cat databases/itantra_net.db > r.db`
   (use `cmd /c` for binary-safe redirection, see AGENTS.md).
   `strings r.db | findstr "test D1"` must find nothing.
2. A phone without the trust relationship never shows the message in its Inbox.
3. The JSONL contains no message text, names or keys:
   `findstr /i "test D1" events_*.jsonl` finds nothing.

## Gate F — SOS stranger path (spec Test F, G)

Two phones that have never exchanged QR codes.

1. On B, enable **Available to help nearby users**.
2. On A, choose a category and language, then tap **I NEED HELP**. B shows "Someone
   nearby is requesting help" with `Encrypted connection — identity not verified`.
3. Decline on B. A keeps searching (`declined=1`, state "Looking for nearby people…").
4. Start again and Accept on B. Both show the same 6-digit code, then chat both ways.
   After comparing codes, "Codes match in person" shows "Nearby device verified".
5. With a third phone C also available, accept on both B and C at nearly the same
   time. Exactly one becomes ACTIVE; the other shows TAKEN_BY_OTHER.

## Screen-off / background (Realme / OEM)

With Emergency mode on, lock A for 10 minutes, then run Gate C step 2 from the other
end. Record whether A kept relaying. Realme battery optimisation may need
"Allow background activity" for iTantra. Record the setting used; do not assume it.

## Product tests (Batch 2, main iTantra screen)

**Product test A — speech to a trusted person through a relay.** Phones A (sender),
B (relay), C (recipient). A and C trust each other through Contacts & network (QR);
B trusts nobody.
1. On all three tap **START EMERGENCY MODE**.
2. On A pick English, hold to talk, release, check the draft, pick C in the recipient
   row and Send. A's Logs shows `↑ Sent • to C • Queued`, then `Relayed`.
3. C's Logs shows `↓ Received • from A` once, and C speaks it. **B shows nothing
   new in Logs and speaks nothing.**
4. A's row turns `Delivered` only after C's receipt comes back.
5. Repeat with Hindi. Only the language metadata and TTS are expected to work; Hindi
   STT accuracy is a known separate issue.

**Product test B — timing hooks.** After test A pull `bench/events_*.jsonl` from A and C.
Check that A has `product_speech_end`, `product_transcript_ready`, `product_queued` and
C has `product_delivered`, `product_tts_start`. Do not subtract A's times from C's.

**Product test C — legacy regression.** Turn on the "Legacy RFCOMM demo" switch on two
paired phones, use LISTEN / CONNECT as before, and send. The receiver shows and speaks
the message. The sender shows `Sent (legacy RFCOMM)` then `Next phone received
(legacy)`, never `Delivered`.

## Reporting

For each gate record the date, phone models, Android versions, topology, attempts,
successes, failures and the pulled file names. Report measurements only from the
CSV/JSONL, labelled with the transport the session reported (`BLE_GATT` or `RFCOMM`).
