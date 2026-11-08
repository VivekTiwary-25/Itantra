# probe_spots.ps1 - read-only. Run once at each spot while the phones are there:
#   .\probe_spots.ps1 -Spot A
# For every phone: does wireless adb answer within 8 s, how fast, the phone's clock, and the Wi-Fi signal
# (RSSI, link speed). Appends to probe_spots.log in this folder.
param([Parameter(Mandatory = $true)][string]$Spot, [string[]]$Phones = @('I2221', 'RMX3392', 'RMX5070', 'RMX5108'))

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$ids = @{
    I2221   = 'adb-10BF3C28T90015K-eEfTYq._adb-tls-connect._tcp'
    RMX3392 = 'adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp'
    RMX5070 = 'adb-a83e3811-XIQIEi._adb-tls-connect._tcp'
    RMX5108 = 'adb-VCU4AAP7KVN7FQGQ-sbN1ac._adb-tls-connect._tcp'
}
$log = Join-Path $PSScriptRoot 'probe_spots.log'
$stamp = Get-Date -Format 'yyyy-MM-ddTHH:mm:ss'

function Invoke-Timed([string]$id, [string]$cmd, [int]$seconds) {
    $job = Start-Job -ScriptBlock { param($a, $i, $c) & $a -s $i shell $c 2>&1 | Out-String } -ArgumentList $adb, $id, $cmd
    if (Wait-Job $job -Timeout $seconds) { $r = (Receive-Job $job).Trim() } else { $r = $null; Stop-Job $job }
    Remove-Job $job -Force
    return $r
}

foreach ($p in $Phones) {
    $t0 = Get-Date
    $echo = Invoke-Timed $ids[$p] 'echo ok; date +%s' 8
    $ms = [int]((Get-Date) - $t0).TotalMilliseconds
    if ($echo -and $echo -match '^ok') {
        $epoch = ($echo -split "`n")[-1].Trim()
        $wifi = Invoke-Timed $ids[$p] "dumpsys wifi | grep -m1 'mWifiInfo'" 8
        $rssi = if ($wifi -match 'RSSI: (-?\d+)') { $Matches[1] } else { '?' }
        $link = if ($wifi -match 'Link speed: (\d+Mbps)') { $Matches[1] } else { '?' }
        $line = "$stamp spot=$Spot phone=$p adb=OK reply_ms=$ms phone_epoch=$epoch rssi_dbm=$rssi link=$link"
    } else {
        $line = "$stamp spot=$Spot phone=$p adb=NO_ANSWER_in_8s (phone keeps its own logs; pull later)"
    }
    $line
    Add-Content -Path $log -Value $line
}
