# Agent B - changes made on RMX5070 and RMX3392 (2026-10-23, ~13:56-14:01)

adb = C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe
RMX5070 = adb-a83e3811-XIQIEi._adb-tls-connect._tcp
RMX3392 = adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp
Evidence: before_*.txt, after_*.txt, shots\<phone>\*.png

## Changes that took effect

| # | Phone | What | Exact command / action | Before | After | How to revert |
|---|---|---|---|---|---|---|
| 1 | RMX5070 | Bluetooth on | `adb -s RMX5070 shell svc bluetooth enable` | bluetooth_on=0 (state BLE_ON) | bluetooth_on=1 (state ON) | `svc bluetooth disable` |
| 2 | RMX5070 | Doze whitelist | `adb -s RMX5070 shell dumpsys deviceidle whitelist +com.chmod777.itantra` | not in list, standby bucket 10 | `user,com.chmod777.itantra,10418`, bucket 5 (exempt) | `dumpsys deviceidle whitelist -com.chmod777.itantra` |
| 3 | RMX3392 | Doze whitelist | `adb -s RMX3392 shell dumpsys deviceidle whitelist +com.chmod777.itantra` | not in list, bucket 10 | `user,com.chmod777.itantra,10513`, bucket 5 | `dumpsys deviceidle whitelist -com.chmod777.itantra` |
| 4 | RMX5070 | Screen stays awake while charging | `adb -s RMX5070 shell settings put global stay_on_while_plugged_in 7` | 0 | 7 | `settings put global stay_on_while_plugged_in 0` |
| 5 | RMX3392 | Media volume (STREAM_MUSIC, speaker) | `cmd media_session volume --stream 3 --set 13` was silently ignored (stayed 5), so pressed volume-up: `adb -s RMX3392 shell input keyevent 24` x8 (focus was the launcher) | 5 / 16 | 13 / 16 | `input keyevent 25` x8 (back to 5) |
| 6 | RMX3392 | ColorOS battery: "Allow background activity" | Opened `am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.chmod777.itantra` -> Battery usage, tapped the toggle (927,847), then "Allow" in the confirm dialog (786,2106) | Off (Allow foreground activity was already On) | On | Same screen, switch "Allow background activity" Off |
| 7 | RMX5070 | ColorOS battery: power consumption control | App info -> Battery usage, tapped radio "Allow background activity" (948,546), then "Allow" in dialog (786,2193) | "Smart mode (Recommended)" | "Allow background activity" | Same screen, choose "Smart mode (Recommended)" |

Not a setting, just noted: on RMX5070 the notification shade was found pulled down over the battery screen at 13:58 (not by me - my tap helper had returned "not found" and tapped nothing); I closed it with `cmd statusbar collapse`.

## Tried but refused by the phone (no change happened)

| Phone | Command | Result |
|---|---|---|
| both | `cmd appops set com.chmod777.itantra RUN_ANY_IN_BACKGROUND allow` and `... RUN_IN_BACKGROUND allow` | SecurityException: shell lacks MANAGE_APP_OPS_MODES. Not needed anyway: both ops already "Default mode: allow". |
| RMX3392 | `settings put global stay_on_while_plugged_in 7` | SecurityException: needs WRITE_SECURE_SETTINGS (ColorOS blocks it unless Developer options -> "Disable permission monitoring" is on). Still 0. |

## 14:02 and 14:35 (models recheck and Stage 4 prep): no changes
Read-only only: `ls`, `stat`, `du`, `dumpsys`, `pm list users`, `logcat -d`, DB copies by `run-as … cat` into agentB\stage4prep\.

## Not changed (already fine)
Permissions (mic, camera, notifications, BT scan/advertise/connect) granted on both - no `pm grant` used. Location mode 3 on both. zen_mode 0 on both. RMX5070 media volume already 160/160 (max). RMX3392 Bluetooth already on.
