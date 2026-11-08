# setup-phone.ps1 - install iTantra and push the IndicConformer models to one phone.
#
# Run from the repository root, once per phone:
#
#   .\setup-phone.ps1 -Serial <serial>            # install + push the models
#   .\setup-phone.ps1 -Serial <serial> -Clean     # wipe the app first (new identity!)
#
# Get serials with:  adb devices -l
#
# -Clean uninstalls the app, which deletes its data: the trusted contacts and this
# phone's identity key are lost and every contact must re-scan its QR code. Without
# -Clean the install keeps app data and only the missing or wrong-sized model files
# are copied again.
#
# The script is safe to re-run. It copies one file at a time, skips every file that
# is already the right size on the phone, retries a file that fails, and if the USB
# connection drops it stops and tells you exactly what is left. Re-run the same
# command (without -Clean) to carry on from where it stopped.
#
# Grant the permission prompts on the phone yourself after the install.

param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [switch]$Clean,
    [int]$WaitSeconds = 120,
    [int]$Retries = 3
)

Set-StrictMode -Version Latest

# Deliberately NOT "Stop". adb writes its transfer progress to stderr, and under
# $ErrorActionPreference = "Stop" PowerShell 5.1 turns that into a terminating
# NativeCommandError even when adb exited 0 - which is what used to kill this
# script immediately after a successful 700 MB push. Success is decided from the
# exit code and from a size check on the phone, never from stderr.
$ErrorActionPreference = 'Continue'
$global:LASTEXITCODE = 0

$Package = 'com.chmod777.itantra'
$Apk     = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
$Models  = Join-Path $PSScriptRoot 'model-staging\indicconformer'
$TmpDir  = '/data/local/tmp/indicconformer'
$AppDir  = 'files/indicconformer'

$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $Adb)) { $Adb = 'adb' }

# Progress, so that a stop can say what is done and what is left.
$script:Total   = 0
$script:Copied  = 0
$script:Skipped = 0
$script:Pending = @()

foreach ($p in @($Apk, $Models)) {
    if (-not (Test-Path $p)) {
        Write-Host "Missing: $p" -ForegroundColor Red
        Write-Host 'Run .\gradlew.bat assembleDebug and .\setup-models.ps1 -IndicConformerModel <pkg> first.'
        exit 1
    }
}

# ---- adb plumbing ---------------------------------------------------------------

# Runs adb and hands back its exit code plus everything it printed, with stdout and
# stderr merged into one string. Nothing in here ever fails on stderr alone.
function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$ArgumentList)

    $ErrorActionPreference = 'Continue'
    $global:LASTEXITCODE = 0
    $raw  = & $Adb @ArgumentList 2>&1
    $code = $LASTEXITCODE
    $lines = @()
    foreach ($item in $raw) {
        if ($null -ne $item) { $lines += $item.ToString() }
    }
    [pscustomobject]@{
        ExitCode = $code
        Output   = ($lines -join "`n").Trim()
    }
}

function Invoke-Device {
    param([Parameter(Mandatory = $true)][string[]]$ArgumentList)
    Invoke-Adb (@('-s', $Serial) + $ArgumentList)
}

function Get-LastLine {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return '' }
    return (($Text -split "`n")[-1]).Trim()
}

# Reads one number out of adb's output, or -1 when there isn't one.
function Get-SizeFrom {
    param([string]$Text)
    $line = Get-LastLine -Text $Text
    if ($line -match '^\d+$') { return [int64]$line }
    return [int64](-1)
}

# KB below a megabyte, so a small file does not read as "0 MB".
function Format-Size {
    param([int64]$Bytes)
    if ($Bytes -lt 1MB) { return ('{0:N1} KB' -f ($Bytes / 1KB)) }
    return ('{0:N1} MB' -f ($Bytes / 1MB))
}

# True when the phone already holds this exact APK, so the install can be skipped.
#
# Size alone is not enough. This APK is ~875 MB of model files wrapped around a few hundred KB of
# code, so two different builds land on the same byte count easily - the first version of this
# check skipped a real code change for exactly that reason, and the phone quietly kept running the
# old build. Size is kept as a free prefilter; the decision is made on a hash. Both hashes
# together cost about 4 s against ~45 s for a reinstall that was not needed.
function Test-ApkAlreadyInstalled {
    param([Parameter(Mandatory = $true)][string]$LocalApk)

    $r = Invoke-Device @('shell', "pm path $Package")
    if ($r.ExitCode -ne 0) { return $false }
    $apkPath = ''
    foreach ($l in ($r.Output -split "`n")) {
        $t = $l.Trim()
        if ($t -like 'package:*') { $apkPath = $t.Substring(8); break }
    }
    if ($apkPath -eq '') { return $false }

    $localSize = (Get-Item $LocalApk).Length
    if ((Get-SizeFrom (Invoke-Device @('shell', "stat -c %s $apkPath")).Output) -ne $localSize) {
        return $false
    }

    $deviceLine = Get-LastLine -Text (Invoke-Device @('shell', "md5sum $apkPath")).Output
    if ($deviceLine -eq '') { return $false }
    $deviceHash = $deviceLine.Split(' ')[0].Trim().ToLower()
    $localHash = (Get-FileHash -Path $LocalApk -Algorithm MD5).Hash.ToLower()
    if ($deviceHash.Length -ne 32) { return $false }
    return ($deviceHash -eq $localHash)
}

function Get-DeviceState {
    $r = Invoke-Adb @('-s', $Serial, 'get-state')
    if ($r.ExitCode -ne 0) { return 'missing' }
    $state = Get-LastLine -Text $r.Output
    if ($state -eq '') { return 'missing' }
    return $state
}

# True once the phone answers as "device". It waits, because the USB link on this
# rig drops often and usually comes back by itself after a reconnect.
function Wait-Device {
    param([string]$Step)

    if ((Get-DeviceState) -eq 'device') { return $true }

    Write-Host "    the phone is not answering before: $Step" -ForegroundColor Yellow
    Write-Host "    waiting up to $WaitSeconds s - re-seat the cable, keep the screen unlocked" -ForegroundColor Yellow
    Invoke-Adb @('reconnect', 'offline') | Out-Null

    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if ((Get-DeviceState) -eq 'device') {
            Write-Host '    the phone is back.' -ForegroundColor Green
            return $true
        }
    }
    return $false
}

function Stop-Incomplete {
    param(
        [string]$Step,
        [string]$Reason = 'the phone stopped answering'
    )

    $done = $script:Copied + $script:Skipped
    Write-Host ''
    Write-Host "STOPPED during: $Step" -ForegroundColor Red
    Write-Host "Reason: $Reason" -ForegroundColor Red
    Write-Host ''
    if ($script:Total -gt 0) {
        Write-Host "  model files already on the phone : $done of $script:Total"
        Write-Host "  copied by this run               : $script:Copied"
        Write-Host "  still to copy                    : $($script:Pending.Count)"
        foreach ($rel in $script:Pending) { Write-Host "      $rel" }
    } else {
        Write-Host '  the models were not reached yet, so nothing was copied.'
    }
    Write-Host ''
    Write-Host 'Nothing is lost. Re-plug the phone (a different cable usually fixes it) and run'
    Write-Host 'the same command again WITHOUT -Clean. It skips whatever is already there:'
    Write-Host "    .\setup-phone.ps1 -Serial $Serial" -ForegroundColor Cyan
    exit 2
}

# Every step goes through this first, so a dropped cable stops the run cleanly
# instead of half-writing a file.
function Assert-Device {
    param([string]$Step)
    if (-not (Wait-Device -Step $Step)) { Stop-Incomplete -Step $Step }
}

# Every model file the app already holds, as a map of relative path -> size.
function Get-PhoneSizes {
    $sizes = @{}
    $cmd = "run-as $Package sh -c 'cd $AppDir 2>/dev/null && find . -type f -exec stat -c %s=%n {} + 2>/dev/null'"
    $r = Invoke-Device @('shell', $cmd)
    if ($r.ExitCode -ne 0) { return $sizes }
    foreach ($line in ($r.Output -split "`n")) {
        $t = $line.Trim()
        if ($t -notmatch '^\d+=\./.') { continue }
        $parts = $t.Split('=', 2)
        $sizes[$parts[1].Substring(2)] = [int64]$parts[0]
    }
    return $sizes
}

# Copies one file: PC -> /data/local/tmp -> the app's private folder, checking the
# size at both hops. The push names the exact destination file, so it can never
# nest the tree one folder deeper the way a directory-to-directory push can.
function Copy-ModelFile {
    param(
        [Parameter(Mandatory = $true)][string]$Rel,
        [Parameter(Mandatory = $true)][string]$LocalPath,
        [Parameter(Mandatory = $true)][int64]$Size
    )

    $devTmp = "$TmpDir/$Rel"
    $subDir = ''
    $cut = $Rel.LastIndexOf('/')
    if ($cut -gt 0) { $subDir = '/' + $Rel.Substring(0, $cut) }

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        Assert-Device -Step "copying $Rel"

        Invoke-Device @('shell', "mkdir -p $TmpDir$subDir") | Out-Null
        $push = Invoke-Device @('push', $LocalPath, $devTmp)

        $onTmp = Get-SizeFrom (Invoke-Device @('shell', "stat -c %s $devTmp")).Output
        if ($onTmp -ne $Size) {
            Write-Host ("        attempt {0}/{1}: the phone got {2} of {3} bytes" -f $attempt, $Retries, $onTmp, $Size) -ForegroundColor Yellow
            if ($push.ExitCode -ne 0) {
                Write-Host "        adb exit $($push.ExitCode): $(Get-LastLine -Text $push.Output)" -ForegroundColor DarkGray
            }
            Invoke-Device @('shell', "rm -f $devTmp") | Out-Null
            continue
        }

        $copy = Invoke-Device @('shell', "run-as $Package sh -c 'mkdir -p $AppDir$subDir && cp $devTmp $AppDir/$Rel'")
        Invoke-Device @('shell', "rm -f $devTmp") | Out-Null

        $inApp = Get-SizeFrom (Invoke-Device @('shell', "run-as $Package sh -c 'stat -c %s $AppDir/$Rel'")).Output
        if ($inApp -eq $Size) { return $true }

        Write-Host ("        attempt {0}/{1}: the app folder got {2} of {3} bytes" -f $attempt, $Retries, $inApp, $Size) -ForegroundColor Yellow
        if ($copy.ExitCode -ne 0) {
            Write-Host "        run-as exit $($copy.ExitCode): $(Get-LastLine -Text $copy.Output)" -ForegroundColor DarkGray
        }
    }
    return $false
}

# ---- Step 1: install ------------------------------------------------------------

Write-Host ''
Write-Host "==> Phone $Serial" -ForegroundColor Cyan
Assert-Device -Step 'the first adb call'

Write-Host ''
Write-Host '==> Installing the app' -ForegroundColor Cyan
if ($Clean) {
    Write-Host '    -Clean: uninstalling first, which deletes the identity and the contacts' -ForegroundColor Yellow
    Invoke-Device @('uninstall', $Package) | Out-Null
    Assert-Device -Step 'the uninstall'
}

$apkBytes = (Get-Item $Apk).Length
Write-Host "    $Apk ($apkBytes bytes)"

# On a re-run the phone usually already holds this exact APK. Re-sending it is
# the single biggest transfer in the script and the one most likely to be cut in
# half by a flaky cable, so skip it when the installed APK really is the same one.
# -Clean has just uninstalled, so there it never matches and the install runs.
Assert-Device -Step 'checking which APK the phone has'
if (Test-ApkAlreadyInstalled -LocalApk $Apk) {
    Write-Host '    the phone already has this exact APK, skipping the install.' -ForegroundColor Green
} else {
    $install = Invoke-Device @('install', '-r', $Apk)
    if ($install.ExitCode -ne 0) {
        Assert-Device -Step 'the install'
        Write-Host ''
        Write-Host "The install failed (adb exit $($install.ExitCode)):" -ForegroundColor Red
        Write-Host $install.Output
        exit 1
    }

    $path = Invoke-Device @('shell', "pm path $Package")
    if ($path.Output -notmatch 'package:') {
        Write-Host ''
        Write-Host "adb reported success but pm cannot find $Package for this user." -ForegroundColor Red
        Write-Host $path.Output
        exit 1
    }
    Write-Host '    installed.' -ForegroundColor Green
}

# ---- Step 2: the models ---------------------------------------------------------

Write-Host ''
Write-Host '==> Models' -ForegroundColor Cyan

$local = @()
foreach ($f in (Get-ChildItem -Path $Models -Recurse -File)) {
    $local += [pscustomobject]@{
        Rel  = ($f.FullName.Substring($Models.Length + 1) -replace '\\', '/')
        Path = $f.FullName
        Size = [int64]$f.Length
    }
}
$local = @($local | Sort-Object Rel)
if ($local.Count -eq 0) {
    Write-Host "No files staged under $Models." -ForegroundColor Red
    exit 1
}
$script:Total  = $local.Count
$expectedBytes = [int64](($local | Measure-Object -Property Size -Sum).Sum)
Write-Host "    staged on this PC: $script:Total files, $expectedBytes bytes"

# A leftover staging folder from an interrupted run is what makes adb nest the
# whole tree one level deeper (files/indicconformer/indicconformer/...), so it
# goes before anything is pushed.
Assert-Device -Step 'clearing the staging folder'
Invoke-Device @('shell', "rm -rf $TmpDir") | Out-Null

Assert-Device -Step 'checking what the phone already has'
$onPhone = Get-PhoneSizes
foreach ($item in $local) {
    if ($onPhone.ContainsKey($item.Rel) -and $onPhone[$item.Rel] -eq $item.Size) {
        $script:Skipped++
    } else {
        $script:Pending += $item.Rel
    }
}

if ($script:Skipped -gt 0) {
    Write-Host "    already the right size on the phone: $script:Skipped files, skipping them" -ForegroundColor Green
}

if ($script:Pending.Count -eq 0) {
    Write-Host '    nothing to copy.' -ForegroundColor Green
} else {
    Write-Host "    to copy: $($script:Pending.Count) files"
    $todo = @($script:Pending)
    $n = 0
    foreach ($rel in $todo) {
        $item = $local | Where-Object { $_.Rel -eq $rel } | Select-Object -First 1
        $n++
        Write-Host ("    [{0}/{1}] {2}  ({3})" -f $n, $todo.Count, $rel, (Format-Size $item.Size))

        $sw = [Diagnostics.Stopwatch]::StartNew()
        $ok = Copy-ModelFile -Rel $rel -LocalPath $item.Path -Size $item.Size
        $sw.Stop()

        if (-not $ok) {
            Assert-Device -Step "copying $rel"
            Write-Host ''
            Write-Host "Gave up on $rel after $Retries attempts while the phone was still" -ForegroundColor Red
            Write-Host 'connected, so this is not the cable. Check free space on the phone:' -ForegroundColor Red
            Write-Host "    adb -s $Serial shell df -h /data"
            Stop-Incomplete -Step "copying $rel" -Reason "$rel would not copy in $Retries attempts"
        }

        $script:Copied++
        $script:Pending = @($script:Pending | Where-Object { $_ -ne $rel })
        Write-Host ("        ok, {0:N1} s" -f $sw.Elapsed.TotalSeconds) -ForegroundColor Green
    }
}

Assert-Device -Step 'the final check'
Invoke-Device @('shell', "rm -rf $TmpDir") | Out-Null

# ---- Step 3: check every single file --------------------------------------------

Write-Host ''
Write-Host '==> Checking every file against model-staging\indicconformer' -ForegroundColor Cyan
$onPhone = Get-PhoneSizes

$bad = @()
$phoneBytes = [int64]0
foreach ($item in $local) {
    $have = [int64](-1)
    if ($onPhone.ContainsKey($item.Rel)) {
        $have = $onPhone[$item.Rel]
        $phoneBytes += $have
    }
    if ($have -ne $item.Size) {
        $bad += [pscustomobject]@{ File = $item.Rel; Staged = $item.Size; OnPhone = $have }
    }
}

$extra = @()
foreach ($key in $onPhone.Keys) {
    if ($local.Rel -notcontains $key) { $extra += $key }
}

Write-Host ("    files : {0} of {1} present and the right size" -f ($script:Total - $bad.Count), $script:Total)
Write-Host ("    bytes : {0} on the phone, {1} staged" -f $phoneBytes, $expectedBytes)

if ($bad.Count -eq 0 -and $extra.Count -eq 0) {
    Write-Host ''
    Write-Host "[PASS] $Serial is ready. Open iTantra and allow the permission prompts." -ForegroundColor Green
    exit 0
}

Write-Host ''
if ($bad.Count -gt 0) {
    Write-Host 'Wrong or missing files (-1 means not on the phone at all):' -ForegroundColor Red
    ($bad | Format-Table -AutoSize | Out-String -Width 200).TrimEnd() | Write-Host
}
if ($extra.Count -gt 0) {
    Write-Host 'Files in the app folder that are not in the staged set - an old nested copy?' -ForegroundColor Red
    foreach ($e in $extra) { Write-Host "    $e" }
    Write-Host ''
    Write-Host 'Clear the folder and re-run to get one clean copy:'
    Write-Host "    adb -s $Serial shell run-as $Package rm -rf $AppDir" -ForegroundColor Cyan
}
Write-Host ''
Write-Host "[FAIL] $Serial is NOT ready. Re-run the same command to retry what is missing." -ForegroundColor Red
exit 3
