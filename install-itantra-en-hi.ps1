$adb = "C:\Users\Arena\AppData\Local\Android\Sdk\platform-tools\adb.exe"

$apk = "D:\projects\sih\itantra-apks\itantra-en-hi-full-tts-checkpoint.apk"

$expectedHash = "3FF01DF4B47B6A87B6B7E2098069B8DFE0F551994D59F8BE2DCC2D13BDB71D37"

Write-Host "`n[1/4] Verifying APK..."

if (!(Test-Path $apk)) {
    throw "APK not found: $apk"
}

$actualHash = (Get-FileHash $apk -Algorithm SHA256).Hash

Write-Host "Expected: $expectedHash"
Write-Host "Actual:   $actualHash"

if ($actualHash -ne $expectedHash) {
    throw "STOP: APK hash mismatch."
}

Write-Host "`n[2/4] Detecting phone..."

$devices = & $adb devices |
    Select-String "`tdevice$" |
    ForEach-Object { ($_ -split "`t")[0] }

if ($devices.Count -eq 0) {
    throw "No authorized Android phone detected."
}

if ($devices.Count -gt 1) {
    throw "More than one phone detected. Disconnect all except the phone you want to install on."
}

$serial = $devices[0]

Write-Host "Using device: $serial"

Write-Host "`n[3/4] Installing iTantra..."

$result = & $adb -s $serial install -r $apk 2>&1
$result | ForEach-Object { Write-Host $_ }

if (($result -join "`n") -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {

    Write-Host "`nSignature mismatch detected."
    Write-Host "Removing old iTantra..."

    & $adb -s $serial uninstall com.chmod777.itantra

    Write-Host "Installing fresh copy..."

    & $adb -s $serial install $apk
}

Write-Host "`n[4/4] Launching iTantra..."

& $adb -s $serial shell monkey `
    -p com.chmod777.itantra `
    -c android.intent.category.LAUNCHER 1 | Out-Null

Write-Host "`nDONE."
Write-Host "Device: $serial"
Write-Host "APK SHA-256:"
Write-Host $expectedHash