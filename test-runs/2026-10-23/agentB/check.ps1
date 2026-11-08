param([string]$Id, [string]$Name)
$adb = 'C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$pkg = 'com.chmod777.itantra'
function S([string]$c) { (& $adb -s $Id shell $c) -join "`n" }
"=== $Name ($Id) $(Get-Date -Format s) ==="
"state: " + ((& $adb -s $Id get-state) -join '')
"pm path: " + (S "pm path $pkg")
"md5: " + (S "md5sum `$(pm path $pkg | head -1 | cut -c9-)")
"versionName/lastUpdate: " + (S "dumpsys package $pkg | grep -E 'versionName|lastUpdateTime|firstInstallTime'")
"models: " + (S "run-as $pkg sh -c 'cd files/indicconformer && find . -type f | wc -l'")
"bluetooth_on: " + (S "settings get global bluetooth_on")
"bt state: " + (S "dumpsys bluetooth_manager | grep -iE '^ *(enabled|state):' | head -3")
"location_mode: " + (S "settings get secure location_mode")
"perms:"
S "dumpsys package $pkg | grep -E 'permission\.(RECORD_AUDIO|CAMERA|POST_NOTIFICATIONS|BLUETOOTH_SCAN|BLUETOOTH_ADVERTISE|BLUETOOTH_CONNECT|ACCESS_FINE_LOCATION|ACCESS_COARSE_LOCATION|NEARBY_WIFI_DEVICES)'"
"appops POST_NOTIFICATION: " + (S "cmd appops get $pkg POST_NOTIFICATION")
"notifications enabled (cmd notification): " + (S "dumpsys notification | grep -A2 'AppSettings: $pkg' | head -3")
"battery:"
S "dumpsys battery | grep -E 'level|status|AC powered|USB powered|Wireless powered'"
"RUN_ANY_IN_BACKGROUND: " + (S "cmd appops get $pkg RUN_ANY_IN_BACKGROUND")
"RUN_IN_BACKGROUND: " + (S "cmd appops get $pkg RUN_IN_BACKGROUND")
"deviceidle whitelist has pkg: " + (S "dumpsys deviceidle whitelist | grep $pkg")
"standby bucket: " + (S "am get-standby-bucket $pkg")
"stay_on_while_plugged_in: " + (S "settings get global stay_on_while_plugged_in")
"screen_off_timeout: " + (S "settings get system screen_off_timeout")
"zen_mode: " + (S "settings get global zen_mode")
"music volume:"
S "dumpsys audio | grep -A6 '^- STREAM_MUSIC' | head -7"
"pid: " + (S "pidof $pkg")
