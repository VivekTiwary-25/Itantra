#!/usr/bin/env bash
# Two-phone validation helpers. Every adb call names its device explicitly (-s).
# The laptop sends control commands and reads results only; it never carries
# application data between the phones (that travels over BLE).
export MSYS_NO_PATHCONV=1
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
A=VCU4AAP7KVN7FQGQ   # PHONE_A realme RMX5108, Android 16
B=a83e3811           # PHONE_B realme RMX5070, Android 16
PKG=com.chmod777.itantra

# cmd <serial> <validation-cmd> [extra am args...]
cmd() { local s=$1; shift; local c=$1; shift; "$ADB" -s "$s" shell am broadcast -n $PKG/.debug.ValidationReceiver --es cmd "$c" "$@" >/dev/null; }
# val <serial>: print the phone's validation log
val() { "$ADB" -s "$1" shell run-as $PKG cat files/validation/val.jsonl 2>/dev/null | tr -d '\r'; }
# last <serial> <event>: last line of a given event
last() { val "$1" | grep "\"ev\":\"$2\"" | tail -1; }
# lab <serial> [autostart]: open Net Lab (initialises the runtime; optional Emergency mode start)
lab() { "$ADB" -s "$1" shell am start ${2:+-S} -n $PKG/.ui.lab.NetworkLabActivity ${2:+--ez autostart true} >/dev/null; }
# events <serial>: newest metrics JSONL on the phone
events() { local d=/sdcard/Android/data/$PKG/files/bench; local f; f=$("$ADB" -s "$1" shell ls -t $d | grep events | head -1 | tr -d '\r'); "$ADB" -s "$1" shell cat "$d/$f" | tr -d '\r'; }
# wait_sessions [max_polls]: until both phones report a BLE_GATT session
wait_sessions() { local n=0; until [ $n -ge ${1:-40} ]; do cmd $A state; cmd $B state; sleep 2; if last $A state | grep -q BLE_GATT && last $B state | grep -q BLE_GATT; then return 0; fi; n=$((n+1)); done; return 1; }
# probe <serial> <trials> <payload>: run a bench from that phone and print its summary
probe() { local s=$1; local before; before=$(val $s | grep -c '"ev":"bench"'); cmd $s bench --ei trials ${2:-5} --ei payload ${3:-200} --ez echo true --ei timeout_ms 8000; local n=0; until [ $(val $s | grep -c '"ev":"bench"') -gt $before ] || [ $n -ge 150 ]; do sleep 1; n=$((n+1)); done; last $s bench | sed -E 's/"csv":"[^"]*",//; s/"label":"[^"]*",//'; }
# sessions: one-line session summary for both phones
sessions() { cmd $A state; cmd $B state; sleep 2; echo "A: $(last $A state | sed -E 's/.*"sessions":"([^"]*)".*/\1/')  |  B: $(last $B state | sed -E 's/.*"sessions":"([^"]*)".*/\1/')"; }
