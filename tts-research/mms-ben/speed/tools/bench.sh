#!/usr/bin/env bash
# Single-driver on-device TTS benchmark via DebugTtsBenchReceiver.
# Usage: bench.sh <logname> <tag> <dir-under-files/bench> <model> <threads> <provider> <passes> [data_dir] [wavprefix]
# Runs: 1 cold/first sentence (sentence 1, excluded from warm stats) + <passes> x 10 sentences in fixed order.
set -u
export MSYS_NO_PATHCONV=1
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
HERE=$(cd "$(dirname "$0")" && pwd -W)
LOGNAME=$1 TAG=$2 DIR=/data/user/0/com.chmod777.itantra/files/bench/$3 MODEL=$4 THREADS=$5 PROVIDER=$6 PASSES=$7 DATADIR=${8:-} WAVP=${9:-}
LOG=$HERE/logs/$LOGNAME.log; mkdir -p $HERE/logs
WANT=${BENCH_COND:-top-app}   # top-app (app visible) or background (app not visible)
cpuset() { local p; p=$("$ADB" shell pidof com.chmod777.itantra | tr -d ''); [ -z "$p" ] && echo none || "$ADB" shell cat /proc/$p/cpuset | tr -d '/'; }
C0=$(cpuset)
if [ "$WANT" = top-app ] && [ "$C0" != top-app ]; then "$ADB" shell monkey -p com.chmod777.itantra -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3; C0=$(cpuset); fi
if [ "$C0" != "$WANT" ]; then echo "ABORT $LOGNAME: app cpuset '$C0' != wanted '$WANT'"; exit 1; fi
"$ADB" logcat -c
"$ADB" logcat -v time -s ITANTRA_BENCH:* > "$LOG" &
LC=$!
sleep 1
send() { # idx text wav
  local b n0 c extra="${BENCH_EXTRA:-}"
  b=$(python -c "import sys,base64;print(base64.b64encode(sys.argv[1].encode('utf-8')).decode())" "$2")
  [ -n "$DATADIR" ] && extra="$extra --es data_dir $DATADIR"
  [ -n "$3" ] && extra="$extra --es wav $3"
  n0=$(grep -c "bench done" "$LOG")
  "$ADB" shell "am broadcast -n com.chmod777.itantra/.DebugTtsBenchReceiver --es dir $DIR --es model $MODEL --ei threads $THREADS --es provider '$PROVIDER' --es tag $TAG --es text_b64 '$b' $extra" >/dev/null
  for i in $(seq 1 600); do c=$(grep -c "bench done" "$LOG"); [ "$c" -gt "$n0" ] && return 0; sleep 0.5; done
  echo "TIMEOUT $1"; return 1
}
mapfile -t LINES < <(grep -v '^\s*$' "$HERE/${BENCH_INPUT:-bn_input.txt}" | tr -d '\r')
send cold "${LINES[0]}" ""
for p in $(seq 1 $PASSES); do
  for i in "${!LINES[@]}"; do
    w=""; [ -n "$WAVP" ] && [ "$p" = 1 ] && w=$(printf "%s_%02d" "$WAVP" $((i+1)))
    send "p$p-$i" "${LINES[$i]}" "$w"
  done
done
"$ADB" shell "am broadcast -n com.chmod777.itantra/.DebugTtsBenchReceiver --es action release" >/dev/null; sleep 2
kill $LC
C1=$(cpuset); echo "cond start=$C0 end=$C1" >> "$LOG"; [ "$C1" != "$WANT" ] && echo "WARNING $LOGNAME: cpuset changed to $C1 during run - CONTAMINATED"
python "$HERE/bstats.py" "$LOG"
