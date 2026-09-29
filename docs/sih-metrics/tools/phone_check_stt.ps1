<#
Task 5b phone check: install the current debug build (+ instrumentation APK), push 5 FLEURS clips per language, run each language
through the app's SpeechEngine (am instrument; NOT connectedAndroidTest, which would uninstall the app and delete the pushed model),
stream logcat and meminfo to files, pull the transcripts and compare them with the desktop IndicConformer output.

  .\docs\sih-metrics\tools\phone_check_stt.ps1 -Serial <adb serial> -Languages gu,mr,ta,te,or,bn -Prime hi

Prerequisites: real phone attached and authorised; IC model already pushed to files/indicconformer (docs/STT_INDICCONFORMER.md);
`gradlew assembleDebug assembleDebugAndroidTest` done; clips exist under docs/sih-metrics/raw/task5-wer/dataset/<lang>/ (created by the scorer).
-Prime hi loads Hindi first, so the measured switch is IC -> IC (release + load). Use -Prime en for an English -> IC switch.
#>
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string[]]$Languages = @("gu", "mr", "ta", "te", "or", "bn"),
    [string]$Prime = "hi",
    [switch]$SkipInstall
)
$ErrorActionPreference = "Continue"   # adb writes progress to stderr; do not treat it as fatal
$adb = "C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$out = Join-Path $repo "docs\sih-metrics\raw\task5b-indicconformer\phone"
New-Item -ItemType Directory -Force $out | Out-Null
$pkg = "com.chmod777.itantra"

if (-not $SkipInstall) {
    & $adb -s $Serial install -r (Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk")
    & $adb -s $Serial install -r -t (Join-Path $repo "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk")
}
foreach ($l in $Languages) {
    & $adb -s $Serial shell "mkdir -p /data/local/tmp/stt-parity/$l"
    Get-ChildItem (Join-Path $repo "docs\sih-metrics\raw\task5-wer\dataset\$l\*.wav") | Sort-Object Name | Select-Object -First 5 |
        ForEach-Object { & $adb -s $Serial push $_.FullName "/data/local/tmp/stt-parity/$l/" | Out-Null }
    & $adb -s $Serial shell "run-as $pkg sh -c 'mkdir -p files/stt-parity && rm -rf files/stt-parity/$l && cp -r /data/local/tmp/stt-parity/$l files/stt-parity/$l'"
}
& $adb -s $Serial shell "rm -rf /data/local/tmp/stt-parity"

foreach ($l in $Languages) {
    $name = "$l-prime-$Prime"
    & $adb -s $Serial shell am force-stop $pkg
    Start-Sleep 2
    & $adb -s $Serial logcat -c
    $lc = Start-Process -FilePath $adb -ArgumentList "-s $Serial logcat -v time -s STTPARITY:D ITANTRA_PERF_STT:D" -RedirectStandardOutput (Join-Path $out "$name-logcat.txt") -PassThru -WindowStyle Hidden
    $job = Start-Job -ScriptBlock {
        param($adb, $s, $pkg, $file)
        1..80 | ForEach-Object {
            "$(Get-Date -Format HH:mm:ss) " + ((& $adb -s $s shell dumpsys meminfo $pkg 2>$null | Select-String "TOTAL PSS") -join " ")
            Start-Sleep 1
        } | Set-Content -Encoding utf8 $file
    } -ArgumentList $adb, $Serial, $pkg, (Join-Path $out "$name-meminfo-samples.txt")
    & $adb -s $Serial shell am instrument -w -e class com.chmod777.itantra.SttParityInstrumentedTest -e lang $l -e prime $Prime -e passes 1 -e limit 5 "$pkg.test/androidx.test.runner.AndroidJUnitRunner" |
        Set-Content -Encoding utf8 (Join-Path $out "$name-instrument-output.txt")
    Start-Sleep 1
    Stop-Process -Id $lc.Id -ErrorAction SilentlyContinue
    Stop-Job $job -ErrorAction SilentlyContinue; Remove-Job $job -Force -ErrorAction SilentlyContinue
    $res = Join-Path $out "$l-results.json"
    cmd /c "$adb -s $Serial exec-out run-as $pkg cat files/stt-parity/results-$l.json > $res"
    "== $l done ($name)"
}

$py = Join-Path $repo ".venv-sih-metrics\Scripts\python.exe"
$cmp = @'
import csv, json, sys
repo, langs = sys.argv[1], sys.argv[2].split(",")
for l in langs:
    ph = json.load(open(f"{repo}/docs/sih-metrics/raw/task5b-indicconformer/phone/{l}-results.json", encoding="utf-8"))
    desk = list(csv.DictReader(open(f"{repo}/docs/sih-metrics/raw/task5b-indicconformer/{l}-results.csv", encoding="utf-8")))[:5]
    same = sum(p["text"] == d["hypothesis"] for p, d in zip(ph, desk))
    print(f"{l}: {same}/5 identical to desktop")
    for p, d in zip(ph, desk):
        if p["text"] != d["hypothesis"]:
            print("   DIFF", p["clip"]); print("    phone  :", p["text"]); print("    desktop:", d["hypothesis"])
'@
$env:PYTHONIOENCODING = "utf-8"
$cmp | & $py - $repo ($Languages -join ",")
