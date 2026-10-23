# install-test-phone.ps1 - put the test build on the ONE phone that is plugged in.
#
#   powershell -ExecutionPolicy Bypass -File .\install-test-phone.ps1
#
# It refuses to run if zero or more than one device shows up in adb, so it can never
# pick the wrong phone. It installs the app and the speech models through
# setup-phone.ps1 (which skips an APK and any model file that is already there, so it
# is safe to run again on the same phone), then prints what is on the phone and
# PASS or FAIL. It never uninstalls anything and never wipes app data.
#
# The commit it reports comes from app\build\outputs\apk\debug\BUILD_INFO.txt, written
# right after the build. The installed APK is compared with that APK byte for byte
# (MD5), so "commit X is installed" is checked on the phone, not assumed.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

$Package  = 'com.chmod777.itantra'
$Apk      = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
$Info     = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\BUILD_INFO.txt'
$Setup    = Join-Path $PSScriptRoot 'setup-phone.ps1'

$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $Adb)) { $Adb = 'adb' }

function Stop-Fail([string]$Why) {
    Write-Host ''
    Write-Host $Why -ForegroundColor Red
    Write-Host ''
    Write-Host 'RESULT: FAIL' -ForegroundColor Red
    exit 1
}

function Get-Adb([string[]]$ArgumentList) {
    $global:LASTEXITCODE = 0
    $raw = & $Adb @ArgumentList 2>&1
    $lines = @()
    foreach ($i in $raw) { if ($null -ne $i) { $lines += $i.ToString() } }
    return ($lines -join "`n").Trim()
}

foreach ($p in @($Apk, $Info, $Setup)) {
    if (-not (Test-Path $p)) { Stop-Fail "Missing: $p  (the build has not been made on this PC yet)" }
}

# ---- exactly one phone -----------------------------------------------------------

$null = Get-Adb @('start-server')
$devices = @()
foreach ($line in ((Get-Adb @('devices')) -split "`n")) {
    $t = $line.Trim()
    if ($t -eq '' -or $t -like 'List of devices*' -or $t -like '*daemon*') { continue }
    $parts = $t -split '\s+'
    if ($parts.Count -ge 2) { $devices += [pscustomobject]@{ Serial = $parts[0]; State = $parts[1] } }
}

if ($devices.Count -eq 0) {
    Stop-Fail 'No phone found. Plug one in over USB, unlock it, and accept "Allow USB debugging".'
}
if ($devices.Count -gt 1) {
    Write-Host 'More than one device is connected. I will not pick one for you:' -ForegroundColor Red
    foreach ($d in $devices) { Write-Host ("    {0}  {1}" -f $d.Serial, $d.State) }
    Stop-Fail 'Unplug the others (or turn off wireless debugging on them) and run this again.'
}
$serial = $devices[0].Serial
if ($devices[0].State -ne 'device') {
    Stop-Fail "The phone $serial is '$($devices[0].State)', not ready. Unlock it and accept the USB debugging prompt."
}

# ---- what we are installing ------------------------------------------------------

$build = @{}
foreach ($l in (Get-Content $Info)) { if ($l -match '^(\w+)=(.*)$') { $build[$Matches[1]] = $Matches[2].Trim() } }
$localMd5 = (Get-FileHash -Path $Apk -Algorithm MD5).Hash.ToLower()
if ($build['apk_md5'] -ne $localMd5) {
    Stop-Fail 'The APK on this PC is not the one BUILD_INFO.txt describes (it was rebuilt or replaced). Rebuild, then rewrite BUILD_INFO.txt.'
}

Write-Host ''
Write-Host "Phone   : $serial"
Write-Host "Build   : $($build['commit']) on $($build['branch'])  (versionName $($build['versionName']))"

# ---- install ---------------------------------------------------------------------

& powershell -NoProfile -ExecutionPolicy Bypass -File $Setup -Serial $serial
$setupExit = $LASTEXITCODE

# ---- report what is really on the phone ------------------------------------------

$model   = Get-Adb @('-s', $serial, 'shell', 'getprop ro.product.model')
$brand   = Get-Adb @('-s', $serial, 'shell', 'getprop ro.product.manufacturer')
$android = Get-Adb @('-s', $serial, 'shell', 'getprop ro.build.version.release')
$api     = Get-Adb @('-s', $serial, 'shell', 'getprop ro.build.version.sdk')

$installedName = ''
foreach ($l in ((Get-Adb @('-s', $serial, 'shell', "dumpsys package $Package")) -split "`n")) {
    if ($l -match 'versionName=(\S+)') { $installedName = $Matches[1]; break }
}

$installedMd5 = ''
$pmPath = Get-Adb @('-s', $serial, 'shell', "pm path $Package")
foreach ($l in ($pmPath -split "`n")) {
    if ($l.Trim() -like 'package:*') {
        $remote = $l.Trim().Substring(8)
        $out = Get-Adb @('-s', $serial, 'shell', "md5sum $remote")
        if ($out) { $installedMd5 = (($out -split "`n")[-1].Trim() -split ' ')[0].ToLower() }
        break
    }
}
$sameBuild = ($installedMd5 -ne '' -and $installedMd5 -eq $localMd5)

Write-Host ''
Write-Host '================ INSTALL REPORT ================'
Write-Host ("Phone model     : {0} {1}" -f $brand, $model)
Write-Host ("Android version : {0} (API {1})" -f $android, $api)
Write-Host ("App version     : {0}" -f $installedName)
Write-Host ("Commit installed: {0}" -f $build['commit'])
Write-Host ("APK on phone is the built APK: {0}" -f $(if ($sameBuild) { 'yes (MD5 matches)' } else { 'NO' }))
Write-Host ("Models step     : {0}" -f $(if ($setupExit -eq 0) { 'all files present, right size' } else { "failed (setup-phone exit $setupExit)" }))
Write-Host '================================================'

if ($setupExit -eq 0 -and $sameBuild) {
    Write-Host 'RESULT: PASS' -ForegroundColor Green
    Write-Host 'Now open iTantra on the phone and allow the permission prompts.'
    exit 0
}
Write-Host 'RESULT: FAIL' -ForegroundColor Red
Write-Host 'Run the same command again; it carries on from where it stopped.'
exit 1
