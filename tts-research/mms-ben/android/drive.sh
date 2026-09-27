#!/usr/bin/env bash
# Usage: source drive.sh ; needs LOG (live logcat file) and ADB. Sends UTF-8 text via base64 to DebugTtsReceiver.
speak() { # lang text
  local b n0 c
  b=$(python -c "import sys,base64;print(base64.b64encode(sys.argv[1].encode('utf-8')).decode())" "$2")
  n0=$(grep -c "debug speak returned" "$LOG")
  "$ADB" shell "am broadcast -n com.chmod777.itantra/.DebugTtsReceiver --es lang $1 --es text_b64 '$b'" >/dev/null
  for i in $(seq 1 240); do c=$(grep -c "debug speak returned" "$LOG"); [ "$c" -gt "$n0" ] && return 0; sleep 0.5; done
  echo "TIMEOUT lang=$1"; return 1
}
