# usage: . collect.ps1 ; Collect <phone> <stage-tag>
# Saves full logcat + all bench jsonl files for one phone into agentA\<tag>\, prints app-relevant lines.
. D:\projects\sih\iTantra\test-runs\2026-10-23\agentA\h.ps1
function Collect($n, $tag) {
    $d = "$root\$tag"; New-Item -ItemType Directory -Force $d | Out-Null
    $lc = "$d\${n}_$tag.txt"
    cmd /c "`"$adb`" -s $($ph[$n]) logcat -d -v time > `"$lc`""
    $bd = "$d\${n}_bench"; New-Item -ItemType Directory -Force $bd | Out-Null
    $files = (& $adb -s $ph[$n] shell "ls /sdcard/Android/data/com.chmod777.itantra/files/bench/") -split "`n" | % { $_.Trim() } | ? { $_ -like 'events_*' }
    foreach ($f in $files) { cmd /c "`"$adb`" -s $($ph[$n]) exec-out cat /sdcard/Android/data/com.chmod777.itantra/files/bench/$f > `"$bd\$f`"" }
    $lc
}
function AppLog($file) { Select-String -Path $file -Pattern 'ITANTRA_|AndroidRuntime|FATAL' | % { $_.Line } }
function Ev($n, $tag, $sinceMs, $exclude = 'frame_(tx|rx)') {
    $bd = "$root\$tag\${n}_bench"
    Get-ChildItem $bd -Filter 'events_*.jsonl' | % { Get-Content $_.FullName } | ? { $_ -match '"wall_ms":(\d+)' -and [long]$matches[1] -ge $sinceMs -and $_ -notmatch $exclude } | Sort-Object { [regex]::Match($_, '"wall_ms":(\d+)').Groups[1].Value }
}
