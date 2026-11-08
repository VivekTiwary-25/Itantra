# Proposal: 24–36 hours of logs on each phone (design only, not built)

Waiting for Vivek's OK before any code is written.

## 1. Why logcat is not enough

- A 16 MB logcat buffer holds about 30–60 minutes on a busy phone. On RMX3392 it filled with 105,000 lines in
  16 minutes, mostly from other apps.
- **vivo phones hide everything below ERROR.** pratham's V2059 logcat had 330 lines in 16 minutes, none from iTantra.
  So for some phones logcat is useless whatever its size.
- logcat is lost on a reboot, and the phone has to be plugged in to read it.

## 2. What already exists, and the plan: extend it, don't add a second log

The app already writes its own log: `JsonlMetricsRecorder` → `Android/data/com.chmod777.itantra/files/bench/events_<run>.jsonl`.
- One JSON line per event, with the phone's monotonic clock and wall clock. A new file starts each time the app
  process starts.
- It is written off the main thread and flushed every 2 s, so little is lost if the app is killed.
- Its rule is already "callers must not pass plaintext, names, keys or secrets".
- It already logs Bluetooth discovery, connect and link setup, the encrypted session handshake, sync rounds, bundle
  send/receive with the ack result, delivered, queued, Emergency mode on/off, SOS, speech timings, and uncaught
  coroutine exceptions.
- "Report a problem" (`ProblemReport.kt`) already shares this folder through the share sheet.
- Measured today: about **44 bytes/s with two links up**, which is about 4 MB a day. 36 hours fits easily.

**Proposal:** keep this one file format and add what's missing (section 3), plus a size cap and rotation (section 5).
No second logging system.

## 3. What gets added

| Area | Already logged | To add |
|---|---|---|
| Bluetooth | discovery, connect, link ready/failed/disconnected with cause, handshake | adapter on/off, scan start/stop/fail per cycle, a summary of nearby counts every 60 s |
| Sync rounds | inventory tx/rx with counts, round send failures | round duration; peer short id on bundle tx/rx (today only the frame events carry the link) |
| Message states | queued, delivered (recipient), bundle tx/rx | **every sender state change by bundle id**: QUEUED → RELAYED → DELIVERED / EXPIRED. Today these exist only in the database. Plus receipt accepted or rejected, and relay copies stored/dropped/expired. |
| Trust | none | contact added / removed / re-scanned: short node id and the first 2 fingerprint groups only, never the name |
| Speech | timings and transcript-ready | model load ok/failed per language, "model missing", empty result, engine exception type, TTS start/fail per language |
| Crashes | coroutine exceptions | uncaught Java exceptions (exception type, top 5 stack frames of our own code), written synchronously before the process dies. On the next start: the previous run ended normally or not, and Android's exit reason (`ApplicationExitInfo`: crash, ANR, low memory, killed by the battery manager) |
| Phone state | none | battery %, charging, battery saver, Doze, every 5 min; app start/stop; Emergency service start/stop and the reason |
| Build | sdk and key protection at start | build commit and APK MD5 at start, so logs from different builds are never mixed up |

## 4. What must never be logged

- Message text, transcripts, recognised words and TTS input. Only lengths and language codes.
- Contact names, display names and full fingerprints. Only short ids and the first 2 groups.
- Keys, seeds, pair secrets, deletion secrets, signatures and ciphertext bytes.
- Anything a relay phone could use to learn what it carries, or who is talking to whom beyond the short Bluetooth ids
  it already sees. Bundle ids are random and stay as 8-hex prefixes, as now.
- Audio and WAV paths that include user content.

**Enforcement:** a single `safeFields()` gate in the recorder that rejects field names like `text`, `name`,
`transcript` or `key`. Plus a JVM test that drives a full send/relay/deliver and checks that no message text or name
appears in the file. Today's check (no message text in any log, `RELAY_RESULT.md` §3) becomes that test.

## 5. Storage, cap and rotation

- **Where:** the same app-specific external folder (`Android/data/<pkg>/files/bench/`). It needs no storage
  permission, can be pulled with plain `adb pull`, and is deleted only on uninstall (which we never do).
- **Rotation:** start a new file every 4 MB or every 6 hours, whichever comes first, named
  `events_<run>_<part>.jsonl`.
- **Cap:** 64 MB in total. When it is exceeded, delete the oldest *part* files. Never delete the current run's first
  part, so a run's start stays visible. At today's rate 64 MB is about 2 weeks; with the additions, estimate 10–15 MB
  a day, which is still 4+ days.
- **Compression:** gzip a part when it is closed (about 10× smaller). This is optional and can come later.
- **Battery cost:** appending to a file from one background thread, about 1 line/s on average, costs well under 1%
  a day. The phone-state snapshot every 5 min doesn't wake the phone on its own; it runs when the app is already awake.

## 6. Getting the logs off and reading them

- **Pull:** `adb -s <serial> pull /sdcard/Android/data/com.chmod777.itantra/files/bench/ <folder>`. One small script,
  `pull-logs.ps1 -Serial <s> -Name vivek`, would also save the build info and the debug `state`. Without a cable,
  "Report a problem" already shares the folder.
- **Reading across phones:** a PC script (an extension of today's `timeline_all.txt` builder) merges all phones by wall
  clock and lines up the same bundle id across phones (sender → relay → recipient). Clock drift is corrected from
  pairs of matching events, such as a bundle tx on one phone and rx on the next. It reports:
  - each message's path and hop times;
  - stuck messages, with each phone's state at that moment;
  - link drops, crashes and battery-manager kills.

## 7. Does it touch the networking engine? Yes, in two places.

1. **The sender state-change event** (QUEUED → RELAYED → DELIVERED, receipt accepted/rejected, relay copy
   stored/dropped). These come from `DtnNode`. The smallest change would subscribe to the `DtnEvent` stream that
   already exists (`OutgoingStateChanged`, `BundleStored`, `Rejected`) from outside the engine, in `ItantraNetwork`,
   the same way `product_delivered` is done today. That leaves `DtnNode.kt` unedited. If the receipt and drop details
   aren't all on that stream, `DtnNode` would need a few more `emit()` calls. **That is an engine edit, flagged for
   your decision.**
2. **Peer short id on bundle tx/rx and round durations.** These live in `DtnEncounter.kt` and the session layer
   (`NetworkingCore.kt`), which are networking code. Adding fields to existing `metrics.record` calls changes no
   behaviour, but it is still an edit in engine files. **Flagged.**

Everything else (crash handler, exit reasons, phone state, speech, trust events, rotation and cap) is in the app,
speech and metrics layers.

## 8. Suggested order once approved

1. Rotation, cap and the `safeFields()` gate, with the no-plaintext JVM test.
2. Crash handler, exit reasons, phone-state snapshots, build info at start.
3. Speech and trust events.
4. Sender state changes from the existing `DtnEvent` stream (no engine edit).
5. Only if you approve: the engine-side fields in item 7.
6. `pull-logs.ps1` and the cross-phone merge script.
