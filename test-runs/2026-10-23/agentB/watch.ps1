# Agent B watcher: every 10 minutes append one line per phone to watch.log. Read-only adb queries.
$adb = 'C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$pkg = 'com.chmod777.itantra'
$log = 'D:\projects\sih\iTantra\test-runs\2026-10-23\agentB\watch.log'
$ph  = [ordered]@{ RMX5070='adb-a83e3811-XIQIEi._adb-tls-connect._tcp'; RMX3392='adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp' }
"$(Get-Date -Format s) watcher started pid=$PID" | Add-Content $log
while ($true) {
  $ts = Get-Date -Format s
  foreach ($n in $ph.Keys) {
    $id = $ph[$n]
    try {
      $st = ((& $adb -s $id get-state 2>&1) -join ' ').Trim()
      if ($st -eq 'device') {
        $out = (& $adb -s $id shell "pm path $pkg >/dev/null 2>&1 && echo yes || echo no; pidof $pkg || echo none; dumpsys battery | grep -E ' level:| status:| AC powered:| USB powered:'; settings get global bluetooth_on" 2>&1)
        $inst = $out[0]; $pidv = $out[1]
        $lvl = ($out | Select-String 'level:' | Select-Object -First 1) -replace '.*level:\s*',''
        $stat = ($out | Select-String 'status:' | Select-Object -First 1) -replace '.*status:\s*',''
        $ac = ($out | Select-String 'AC powered:') -replace '.*:\s*',''
        $usb = ($out | Select-String 'USB powered:') -replace '.*:\s*',''
        $bt = $out[-1]
        $chg = if ($stat -eq '2' -or $stat -eq '5') { 'charging' } else { 'not_charging' }
        $line = "$ts $n state=$st installed=$inst pid=$pidv battery=$lvl% status=$stat($chg) ac=$ac usb=$usb bluetooth_on=$bt"
      } else { $line = "$ts $n state=$st (offline/unreachable)" }
    } catch { $line = "$ts $n ERROR $($_.Exception.Message)" }
    Add-Content $log $line
  }
  Start-Sleep -Seconds 600
}
