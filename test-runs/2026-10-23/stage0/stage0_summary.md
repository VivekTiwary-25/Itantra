# Stage 0 - setup check (2026-10-23)

Build expected: commit 4729bd2db56453d5372af085c3e126ba9db7e4f3, APK MD5 627c913204d88bdc881b3d2e9f0eadd8

| Phone | Android | Battery | APK = built 4729bd2 | Models (19 files) | BT scan/adv/connect | Mic | Notifications | Camera |
|---|---|---|---|---|---|---|---|---|
| I2221 | 16 (API 36) | 80% | yes (MD5 match) | yes, 19 | granted | NOT granted | granted | NOT granted |
| RMX3392 | 14 (API 34) | 25% | yes (MD5 match) | NO - folder files/indicconformer does not exist | NOT granted | NOT granted | NOT granted | NOT granted |
| RMX5108 | 16 (API 36) | 94% | yes (MD5 match) | yes, 19 | granted | granted | granted | granted |
| RMX5070 | 16 (API 36) | 23% | yes (MD5 match) | yes, 19 | granted | granted | NOT granted | granted |

Notes
- "Nearby devices" (NEARBY_WIFI_DEVICES) is not requested by the app; on Android 12+ the Bluetooth trio is the "Nearby devices" permission.
- RMX3392 was last updated 10:59 today and its app data holds only `profileInstalled`: the app was installed but never opened, and the models were never copied.
- The camera permission is needed for the QR scan. Mic is needed for speaking.
- All four phones are on battery (not charging). RMX3392 (25%) and RMX5070 (23%) are low.

Unit tests (gradlew testDebugUnitTest on 4729bd2): BUILD SUCCESSFUL, 132 tests, 0 failures, 0 errors, 0 skipped.

Stage 0 verdict: NOT all phones pass. RMX3392 has no speech models (and no permissions). Stopped before Stage 1 as instructed.
