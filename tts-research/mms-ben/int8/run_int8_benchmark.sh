#!/usr/bin/env bash
# Single-driver INT8 Bengali benchmark (same sentences / order / thread setting as the fp32 run). Needs a live logcat capture into $LOG.
cd /d/projects/sih/itantra-tts
export ADB="/c/Users/Arena/AppData/Local/Android/Sdk/platform-tools/adb.exe"; export LOG=tts-research/mms-ben/int8/int8_logcat.log
source tts-research/mms-ben/android/drive.sh
mapfile -t BN < <(tr -d '\r' < tts-research/inputs/bn.txt)
speak bn "${BN[0]}"
"$ADB" shell dumpsys meminfo com.chmod777.itantra | grep -E "TOTAL PSS|Native Heap:|Java Heap:" > tts-research/mms-ben/int8/meminfo_bn_only.txt
for p in 1 2; do for t in "${BN[@]}"; do speak bn "$t"; done; done
speak en "this is a test message"; speak hi "यह एक परीक्षण संदेश है"; speak ml "$(head -1 tts-research/inputs/ml.txt)"
speak en "Water is rising near the school. Move to safe ground."; speak hi "स्कूल के पास पानी बढ़ रहा है"; speak ml "$(sed -n 3p tts-research/inputs/ml.txt)"
speak bn "${BN[3]}"
"$ADB" shell dumpsys meminfo com.chmod777.itantra | grep -E "TOTAL PSS|Native Heap:|Java Heap:" > tts-research/mms-ben/int8/meminfo_all_voices.txt
echo FINISHED > tts-research/mms-ben/int8/driver_done.flag
