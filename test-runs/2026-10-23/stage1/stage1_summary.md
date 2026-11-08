# Stage 1 - every screen opens (no radio) - 2026-10-23

Build on all phones: commit 4729bd2 (APK MD5 627c9132...). Walked via adb (uiautomator + screenshots), per phone:
Home, Composer, Logs, Hands-free, SOS (category picker), Language sheet, Settings (top + bottom), Add contact / QR scan,
Report a problem, How this works (onboarding cards 1-3), Developer row, Message detail (RMX5070 only: needs an existing message).
Files: stage1\<PHONE>\*.png (screenshots), *.txt (screen text), walk_log.txt / walk2_log.txt, logcat_stage1.txt.

Result: every screen opened on all 4 phones. No crash, no blank screen. logcat: no FATAL EXCEPTION / ANR / fatal signal for the app on any phone.

Not exercised: Message detail on I2221, RMX3392, RMX5108 (no messages yet; will happen in Stage 2). Onboarding card 4 not viewed
(card 3 offers ADD A CONTACT / LATER, my script looked for NEXT). QR camera preview was black on RMX3392 (dark room?) - UNCLEAR.

UI findings
1. "App: 1.0 (debug)" is printed on Report a problem (debug build).
2. Settings has a "Developer" row (debug builds). It opens the OLD legacy screen: "HOLD TO TALK / Idle / PLAY LAST RECORDING /
   Legacy RFCOMM demo (paired phones, not end-to-end)", and on I2221 a "Test TTS (temporary)" button. Looks like a dev tool, not the new UI.
3. RMX5070 Logs holds old test messages from 25 Sept (PHONE_A, DTN1-001..005 with 100+ x's), unread badge 160 on Home.
4. Names differ per phone: RMX3392 "Vivek Tiwary", RMX5070 "Yash Mathur", RMX5108 "Utkarsh Keshri", I2221 "iTantra user".
5. RMX3392 also carries the old demo app (com.chmod777.itantra.demo.vivek, icon "iTantra"). The test build icon is "SIH_iTantra".

Process notes (my own mistakes, not app bugs)
- The first walk of RMX5070 and I2221 ran twice at once and was invalid; kept as walk_log_FIRST_RUN_INVALID_overlapping_scripts.txt; rerun cleanly.
- Hands-free was opened on every phone for ~5-9 s and left with Back. It showed "Listening..." with a timer. RMX3392's mic permission was
  not granted at the Stage 0 check; I did not look into whether Hands-free really recorded there.
