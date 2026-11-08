#!/usr/bin/env bash
# Single-driver in-app benchmark of Bengali MMS (fp16 decoder) through TtsHelper (synthesis + AudioTrack
# playback), same sentences/order as the fp32 and INT8 runs. Run right after `adb install -r` so the Bengali
# run is the first-ever use of the new assets (cold). Nothing else may drive the phone meanwhile.
#
# Phase fg: app visible (process cpuset top-app, cpu0-7).  Phase bg: HOME pressed (cpuset background, cpu0-3).
# Each phase checks the process cpuset before and after and records it; a changed cpuset marks the phase
# CONTAMINATED. Usage: run_speed_benchmark.sh <out-dir>
set -u
export MSYS_NO_PATHCONV=1
cd /d/projects/sih/itantra-tts
export ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
OUT=$1; mkdir -p "$OUT"
source tts-research/mms-ben/android/drive.sh
mapfile -t BN < <(tr -d '\r' < tts-research/inputs/bn.txt)
mapfile -t ML < <(tr -d '\r' < tts-research/inputs/ml.txt)
EN=("this is a test message" "Water is rising near the school. Move to safe ground." "Please send a doctor to the north side of the village.")
HI=("यह एक परीक्षण संदेश है" "स्कूल के पास पानी बढ़ रहा है" "कृपया गाँव के उत्तर की ओर एक डॉक्टर भेजें")

cpuset() { local p; p=$("$ADB" shell pidof com.chmod777.itantra | tr -d '\r'); [ -z "$p" ] && echo none || "$ADB" shell cat /proc/$p/cpuset | tr -d '\r/'; }
meminfo() { "$ADB" shell dumpsys meminfo com.chmod777.itantra | grep -E "TOTAL PSS|Native Heap:|Java Heap:" > "$OUT/$1"; }
start_log() { "$ADB" logcat -c; "$ADB" logcat -v time -s ITANTRA_TTS:* DebugTtsReceiver:* AndroidRuntime:E libc:F DEBUG:F > "$LOG" & LC=$!; sleep 1; }
stop_log() { sleep 1; kill $LC; }
check() { local c; c=$(cpuset); echo "cond $1 cpuset=$c" | tee -a "$OUT/conditions.txt"; }

# ---- Phase fg ----
"$ADB" shell monkey -p com.chmod777.itantra -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
export LOG=$OUT/fg_logcat.log; start_log
check fg-start
speak bn "${BN[0]}"
meminfo meminfo_fg_bn_only.txt
for p in 1 2; do for t in "${BN[@]}"; do speak bn "$t"; done; done
for t in "${EN[@]}"; do speak en "$t"; done
for t in "${HI[@]}"; do speak hi "$t"; done
for t in "${ML[@]:0:3}"; do speak ml "$t"; done
speak bn "${BN[3]}"
meminfo meminfo_fg_all_voices.txt
check fg-end
stop_log

# ---- Phase bg ----
"$ADB" shell input keyevent KEYCODE_HOME; sleep 4
export LOG=$OUT/bg_logcat.log; start_log
check bg-start
for t in "${BN[@]}"; do speak bn "$t"; done
for t in "${EN[@]}"; do speak en "$t"; done
for t in "${HI[@]}"; do speak hi "$t"; done
for t in "${ML[@]:0:3}"; do speak ml "$t"; done
check bg-end
stop_log
echo FINISHED
