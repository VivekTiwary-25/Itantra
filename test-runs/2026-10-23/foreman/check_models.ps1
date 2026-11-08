# check_models.ps1 - read-only. Run before every stage. Say right away if any phone lost its models.
# Usage: .\check_models.ps1                      (all four phones)
#        .\check_models.ps1 -Phones RMX5108,I2221 (only some)
param([string[]]$Phones = @('I2221','RMX3392','RMX5070','RMX5108'))

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$ids = @{
    I2221   = 'adb-10BF3C28T90015K-eEfTYq._adb-tls-connect._tcp'
    RMX3392 = 'adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp'
    RMX5070 = 'adb-a83e3811-XIQIEi._adb-tls-connect._tcp'
    RMX5108 = 'adb-VCU4AAP7KVN7FQGQ-sbN1ac._adb-tls-connect._tcp'
}
$expected = 19
$bad = 0
foreach ($p in $Phones) {
    $out = (& $adb -s $ids[$p] shell "run-as com.chmod777.itantra sh -c 'cd files/indicconformer 2>/dev/null && find . -type f | wc -l || echo MISSING'" 2>&1 | Out-String).Trim()
    $ok = ($out -eq "$expected")
    if (-not $ok) { $bad++ }
    '{0,-8} models: {1,-8} {2}' -f $p, $out, $(if ($ok) { 'OK' } else { '*** LOST OR INCOMPLETE - do not start the stage on this phone ***' })
}
if ($bad -gt 0) { exit 1 } else { exit 0 }
