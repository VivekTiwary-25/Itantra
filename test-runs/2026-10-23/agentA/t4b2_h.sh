# source me. Agent A helper for t4b2 (read-only helpers + snap)
export MSYS_NO_PATHCONV=1
ADB="/c/Users/Arena/AppData/Local/Android/Sdk/platform-tools/adb.exe"
I=adb-10BF3C28T90015K-eEfTYq._adb-tls-connect._tcp
R=adb-VCU4AAP7KVN7FQGQ-sbN1ac._adb-tls-connect._tcp
A=/d/projects/sih/itantra/test-runs/2026-10-23/agentA
T=$A/${TDIR:-t4b2}
id_of(){ [ "$1" = I2221 ] && echo $I || echo $R; }
snap(){ mkdir -p $T/$1; $ADB -s $(id_of $1) exec-out screencap -p > $T/$1/$2.png; }
st(){ local id=$(id_of $1); $ADB -s $id shell am broadcast -n com.chmod777.itantra/.debug.ValidationReceiver --es cmd state >/dev/null; sleep 2; echo "$1 $($ADB -s $id shell run-as com.chmod777.itantra tail -1 files/validation/val.jsonl)" | tee -a $T/state_poll.jsonl; }
dbcp(){ local d=$A/db/$1_$2; mkdir -p $d; for f in itantra_net.db itantra_net.db-wal; do $ADB -s $(id_of $1) exec-out run-as com.chmod777.itantra cat databases/$f > $d/$f; done; ls -la $d | tail -2; }
export PYTHONIOENCODING=utf-8
ui(){ local id=$(id_of $1); $ADB -s $id shell uiautomator dump /sdcard/agentA_ui.xml >/dev/null 2>&1; $ADB -s $id exec-out cat /sdcard/agentA_ui.xml > $T/ui_$1.xml; python -I D:/projects/sih/itantra/test-runs/2026-10-23/agentA/t4b2_ui.py D:/projects/sih/itantra/test-runs/2026-10-23/agentA/t4b2/ui_$1.xml; }
tap(){ $ADB -s $(id_of $1) shell input tap $2 $3; }
