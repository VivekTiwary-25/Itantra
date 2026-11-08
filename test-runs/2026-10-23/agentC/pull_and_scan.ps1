<#
pull_and_scan.ps1 - pull evidence from iTantra test phones and count the key events.

WHAT IT DOES (read-only on the phones)
  For each phone you name:
    1. adb -s <id> logcat -d -v time            -> <OutDir>\<Phone>\logcat.txt
    2. adb -s <id> pull .../files/bench          -> <OutDir>\<Phone>\bench\   (the app's JSONL metrics)
    3. (only with -PullDb) run-as cat of the networking database + its -wal file
       -> <OutDir>\<Phone>\db\                   (read-only copy, used for the plaintext check)
  Then it searches every pulled text file and prints:
    - a per-phone count table for each key event
    - the first matching line of each event, per phone
    - the distinct BLE short IDs each phone discovered / linked to (for the "outer phones never met" proof)
    - with -Marker "WORD": how many times that exact text appears in each phone's files

  It never installs, uninstalls, clears, starts, stops or taps anything.

WHY BOTH LOGCAT AND JSONL
  The networking engine writes its events (link_ready, noise_done, dtn_bundle_rx, sos_offer_rx ...)
  ONLY to the JSONL file in files/bench/, through JsonlMetricsRecorder. It does NOT write them to logcat.
  logcat only has the app's ITANTRA_TTS / ITANTRA_LATENCY / ITANTRA_TRANSPORT tags plus Android's own
  Bluetooth and crash lines. So a logcat-only scan would show zero networking events even on a perfect run.

USAGE
  # Agent A, its own two phones:
  .\pull_and_scan.ps1 -OutDir ..\agentA\stage4_pull -Phones RMX5108,I2221
  # Agent B, its own two phones, with the privacy check:
  .\pull_and_scan.ps1 -OutDir ..\agentB\stage4_pull -Phones RMX5070,RMX3392 -Marker "KESTREL41" -PullDb
  # Re-scan a folder that was already pulled (no adb at all):
  .\pull_and_scan.ps1 -OutDir .\fake -Phones RMX5108,RMX5070 -SkipPull

  -Phones is required on purpose, so an agent only touches the phones it owns.
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory = $true)][string]$OutDir,
    [Parameter(Mandatory = $true)][string[]]$Phones,
    [string]$Marker = '',
    [switch]$PullDb,
    [switch]$SkipPull,
    [int]$FirstLineChars = 220,
    # Only count JSONL lines with wall_ms >= this (the phone's wall clock, ms since 1970). 0 = everything.
    # Logcat lines carry no wall_ms and are always counted.
    [long]$SinceWallMs = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Continue'

# ---- the four test phones (wireless adb ids) -------------------------------------
$PhoneIds = [ordered]@{
    'RMX5108' = 'adb-VCU4AAP7KVN7FQGQ-sbN1ac._adb-tls-connect._tcp'
    'I2221'   = 'adb-10BF3C28T90015K-eEfTYq._adb-tls-connect._tcp'
    'RMX5070' = 'adb-a83e3811-XIQIEi._adb-tls-connect._tcp'
    'RMX3392' = 'adb-EILZRCFQLZIZ6H6P-7Kxnz9._adb-tls-connect._tcp'
}

# "powershell -File ... -Phones A,B" passes one string "A,B": split it.
$Phones = @($Phones | ForEach-Object { $_ -split '[,\s]+' } | Where-Object { $_ -ne '' })

$Package  = 'com.chmod777.itantra'
$BenchDir = "/sdcard/Android/data/$Package/files/bench"

$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $Adb)) { $Adb = 'adb' }

# ---- key events: real names from the source at 4729bd2 ---------------------------
# Group, Label, Regex. JSONL lines look like {"t_ns":..,"wall_ms":..,"dev":..,"run":..,"ev":"<name>",<fields in code order>}
$Events = @(
    # discovery / connect / MTU  (transport/ble/*)
    @('connect',   'peer_discovered',            '"ev":"peer_discovered"'),
    @('connect',   'connect_attempt',            '"ev":"connect_attempt"'),
    @('connect',   'gatt_connected (client)',    '"ev":"gatt_connected"'),
    @('connect',   'gatt_server_central_conn',   '"ev":"gatt_server_central_connected"'),
    @('connect',   'mtu_result',                 '"ev":"mtu_result"'),
    @('connect',   'link_ready',                 '"ev":"link_ready"'),
    @('connect',   'link_duplicate_resolved',    '"ev":"link_duplicate_resolved"'),
    @('connect',   'link_disconnected',          '"ev":"link_disconnected"'),
    @('connect',   'link_failed',                '"ev":"link_failed"'),
    @('connect',   'connect_failed',             '"ev":"(connect_failed|gatt_connect_failed)"'),
    # Noise handshake  (session/NetworkingCore.kt)
    @('handshake', 'noise_done',                 '"ev":"noise_done"'),
    @('handshake', 'noise_failed',               '"ev":"noise_failed"'),
    @('handshake', 'noise_rate_limited',         '"ev":"noise_rate_limited"'),
    @('handshake', 'session_closed',             '"ev":"session_closed"'),
    # trusted message / relay / receipt  (network/ItantraNetwork.kt, dtn/DtnEncounter.kt)
    @('relay',     'product_queued (sender)',    '"ev":"product_queued"'),
    @('relay',     'bundle_tx RELAY (give copy)','"ev":"dtn_bundle_tx","mode":"RELAY"'),
    @('relay',     'bundle_rx RELAY PERSISTED',  '"ev":"dtn_bundle_rx","mode":"RELAY","bytes":\d+,"ack":"PERSISTED"'),
    @('relay',     'bundle_tx DESTINATION',      '"ev":"dtn_bundle_tx","mode":"DESTINATION"'),
    @('relay',     'bundle_rx DEST accepted',    '"ev":"dtn_bundle_rx","mode":"DESTINATION","bytes":\d+,"ack":"ACCEPTED_AS_DESTINATION_ATTEMPT"'),
    @('relay',     'bundle_rx REJECTED',         '"ev":"dtn_bundle_rx".*"ack":"REJECTED"'),
    @('relay',     'tombstone_rx applied',       '"ev":"dtn_tombstone_rx","applied":true'),
    @('relay',     'probe_relay_forward',        '"ev":"probe_relay_forward"'),
    @('delivered', 'product_delivered (recip.)', '"ev":"product_delivered"'),
    @('delivered', 'product_tts_start',          '"ev":"product_tts_start"'),
    @('delivered', 'logcat TTS speak',           'ITANTRA_TTS.*speak lang='),
    @('delivered', 'probe_result success',       '"ev":"probe_result".*"success":true'),
    # SOS  (sos/SosManager.kt)
    @('sos',       'sos_start',                  '"ev":"sos_start"'),
    @('sos',       'sos_offer_tx',               '"ev":"sos_offer_tx"'),
    @('sos',       'sos_offer_rx',               '"ev":"sos_offer_rx"'),
    @('sos',       'sos_accept_tx',              '"ev":"sos_accept_tx"'),
    @('sos',       'sos_decline_tx',             '"ev":"sos_decline_tx"'),
    @('sos',       'sos_accept_rx CONFIRM',      '"ev":"sos_accept_rx","reply":"SOS_CONFIRM"'),
    @('sos',       'sos_accept_rx BUSY',         '"ev":"sos_accept_rx","reply":"SOS_BUSY"'),
    @('sos',       'sos_request_tx (flood)',     '"ev":"sos_request_tx"'),
    @('sos',       'sos_request_rx',             '"ev":"sos_request_rx"'),
    @('sos',       'sos_request_forward',        '"ev":"sos_request_forward"'),
    @('sos',       'sos_end',                    '"ev":"sos_end"'),
    # errors
    @('error',     'uncaught_coroutine_exc',     '"ev":"uncaught_coroutine_exception"'),
    @('error',     'round_send_failed',          '"ev":"round_send_failed"'),
    @('error',     'frame_malformed/strike',     '"ev":"(frame_malformed|dtn_peer_strike|fragment_rejected|hello_malformed)"'),
    @('error',     'scan/adv/server failed',     '"ev":"(scan_failed|gatt_server_failed)"|"ev":"adv_status","status":"Failed'),
    @('error',     'bad signature',              '"ev":"sos_(offer|request)_bad_signature"'),
    @('error',     'logcat FATAL/ANR',           'FATAL EXCEPTION|ANR in com\.chmod777\.itantra|Fatal signal'),
    @('error',     'logcat app E/ lines',        ' E/(ITANTRA_[A-Z]+|AndroidRuntime)\b'),
    @('error',     'logcat app exception',       'com\.chmod777\.itantra.*(Exception|Error)\b|(Exception|Error)\b.*com\.chmod777\.itantra')
)

function Invoke-Cmd([string]$CommandLine) {
    # cmd /c keeps the redirected bytes exactly as adb wrote them.
    & cmd.exe /c $CommandLine
    return $LASTEXITCODE
}

function Get-PhoneDir([string]$Phone) { Join-Path $OutDir $Phone }

# ---- 1. pull -----------------------------------------------------------------------
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Status = @{}
foreach ($phone in $Phones) {
    if (-not $PhoneIds.Contains($phone)) {
        Write-Host "Unknown phone '$phone'. Known: $($PhoneIds.Keys -join ', ')" -ForegroundColor Red
        $Status[$phone] = 'UNKNOWN NAME'
        continue
    }
    $dir = Get-PhoneDir $phone
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    if ($SkipPull) { $Status[$phone] = 'scan only'; continue }

    $id = $PhoneIds[$phone]
    $state = (& $Adb -s $id get-state 2>$null | Out-String).Trim()
    if ($state -ne 'device') {
        Write-Host "[$phone] not reachable over adb (state='$state'). Skipped." -ForegroundColor Yellow
        $Status[$phone] = 'OFFLINE'
        continue
    }
    $stamp = Get-Date -Format 'HHmmss'
    $logFile = Join-Path $dir "logcat_$stamp.txt"
    $rc = Invoke-Cmd "`"$Adb`" -s $id logcat -d -v time > `"$logFile`""
    $bench = Join-Path $dir 'bench'
    New-Item -ItemType Directory -Force -Path $bench | Out-Null
    & $Adb -s $id pull "$BenchDir/." "$bench" 2>&1 | Out-Null
    $jsonl = @(Get-ChildItem -Path $bench -Filter 'events_*.jsonl' -ErrorAction SilentlyContinue)
    $note = "logcat rc=$rc, jsonl files=$($jsonl.Count)"
    if ($PullDb) {
        $db = Join-Path $dir 'db'
        New-Item -ItemType Directory -Force -Path $db | Out-Null
        foreach ($f in @('itantra_net.db', 'itantra_net.db-wal')) {
            $null = Invoke-Cmd "`"$Adb`" -s $id exec-out run-as $Package cat databases/$f > `"$(Join-Path $db $f)`""
        }
        $note += ', db pulled'
    }
    $Status[$phone] = $note
    Write-Host "[$phone] pulled: $note"
}

# ---- 2. scan -----------------------------------------------------------------------
function Get-TextFiles([string]$Phone) {
    $dir = Get-PhoneDir $Phone
    if (-not (Test-Path $dir)) { return @() }
    $files = @()
    $files += @(Get-ChildItem -Path $dir -Filter 'logcat*.txt' -File -ErrorAction SilentlyContinue)
    $benchDir = Join-Path $dir 'bench'
    if (Test-Path $benchDir) { $files += @(Get-ChildItem -Path $benchDir -Filter '*.jsonl' -File -ErrorAction SilentlyContinue) }
    return @($files | ForEach-Object { $_.FullName })
}

function Test-Since([string]$Line) {
    # JSONL lines are kept only from -SinceWallMs on; logcat lines (no wall_ms) are always kept.
    $m = [regex]::Match($Line, '"wall_ms":(\d+)')
    if (-not $m.Success) { return $true }
    return ([long]$m.Groups[1].Value -ge $SinceWallMs)
}

$scanned = @($Phones | Where-Object { $PhoneIds.Contains($_) })
$counts = @{}
$firsts = @{}
foreach ($phone in $scanned) {
    $files = Get-TextFiles $phone
    foreach ($ev in $Events) {
        $key = "$phone|$($ev[1])"
        if ($files.Count -eq 0) { $counts[$key] = '-'; continue }
        $hits = @(Select-String -Path $files -Pattern $ev[2] -CaseSensitive -ErrorAction SilentlyContinue)
        if ($SinceWallMs -gt 0) { $hits = @($hits | Where-Object { Test-Since $_.Line }) }
        $counts[$key] = $hits.Count
        if ($hits.Count -gt 0) {
            $line = $hits[0].Line
            if ($line.Length -gt $FirstLineChars) { $line = $line.Substring(0, $FirstLineChars) + '...' }
            $firsts[$key] = "$(Split-Path $hits[0].Path -Leaf):$($hits[0].LineNumber)  $line"
        }
    }
}

Write-Host ''
Write-Host "=== Event counts (OutDir: $OutDir) ===" -ForegroundColor Cyan
$header = '{0,-10} {1,-28}' -f 'group', 'event'
foreach ($p in $scanned) { $header += ('{0,9}' -f $p) }
Write-Host $header
foreach ($ev in $Events) {
    $row = '{0,-10} {1,-28}' -f $ev[0], $ev[1]
    foreach ($p in $scanned) { $row += ('{0,9}' -f $counts["$p|$($ev[1])"]) }
    Write-Host $row
}

Write-Host ''
Write-Host '=== First matching line per phone and event ===' -ForegroundColor Cyan
foreach ($p in $scanned) {
    Write-Host "--- $p ($($Status[$p])) ---" -ForegroundColor Yellow
    foreach ($ev in $Events) {
        $k = "$p|$($ev[1])"
        if ($firsts.ContainsKey($k)) { Write-Host ('  {0,-28} {1}' -f $ev[1], $firsts[$k]) }
    }
}

# ---- 3. which BLE short IDs did each phone see / link to --------------------------
Write-Host ''
Write-Host '=== BLE short IDs (first 8 hex) per phone ===' -ForegroundColor Cyan
Write-Host '  Stage 4 proof: each OUTER phone must list only the MIDDLE phone. The middle phone lists both outer phones.'
foreach ($p in $scanned) {
    $files = Get-TextFiles $p
    if ($files.Count -eq 0) { Write-Host "  $p  (no files)"; continue }
    # Uses -SinceWallMs too: earlier stages may have linked the same two phones directly.
    $seen = @(Select-String -Path $files -Pattern '"ev":"peer_discovered","peer":"([0-9a-f]+)"' |
        Where-Object { Test-Since $_.Line } | ForEach-Object { $_.Matches[0].Groups[1].Value } | Sort-Object -Unique)
    $linked = @(Select-String -Path $files -Pattern '"ev":"link_ready".*"peer":"([0-9a-f]+)"' |
        Where-Object { Test-Since $_.Line } | ForEach-Object { $_.Matches[0].Groups[1].Value } | Sort-Object -Unique)
    Write-Host ('  {0,-8} discovered: {1}' -f $p, ($(if ($seen.Count) { $seen -join ', ' } else { '(none)' })))
    Write-Host ('  {0,-8} linked:     {1}' -f '', ($(if ($linked.Count) { $linked -join ', ' } else { '(none)' })))
}

# ---- 4. plaintext marker check -------------------------------------------------------
if ($Marker -ne '') {
    Write-Host ''
    Write-Host "=== Plaintext check for marker '$Marker' ===" -ForegroundColor Cyan
    Write-Host '  Expected: 0 in every logcat and every JSONL on every phone; 0 in the RELAY phone''s database.'
    Write-Host '  (The sender''s and recipient''s own databases DO hold it: outgoing / delivered tables.)'
    $latin1 = [System.Text.Encoding]::GetEncoding(28591)
    foreach ($p in $scanned) {
        $files = Get-TextFiles $p
        $textHits = 0
        if ($files.Count) { $textHits = @(Select-String -Path $files -Pattern $Marker -SimpleMatch).Count }
        $dbHits = 'not pulled'
        $dbDir = Join-Path (Get-PhoneDir $p) 'db'
        if (Test-Path $dbDir) {
            $n = 0
            foreach ($f in Get-ChildItem -Path $dbDir -File) {
                $s = $latin1.GetString([System.IO.File]::ReadAllBytes($f.FullName))
                $i = $s.IndexOf($Marker, [System.StringComparison]::Ordinal)
                while ($i -ge 0) { $n++; $i = $s.IndexOf($Marker, $i + 1, [System.StringComparison]::Ordinal) }
            }
            $dbHits = $n
        }
        Write-Host ('  {0,-8} logcat+jsonl hits: {1,4}    database hits: {2}' -f $p, $textHits, $dbHits)
    }
}
Write-Host ''
Write-Host 'Done. Counts are over the whole pulled files: logcat -d is the ring buffer, JSONL holds every run since install.'
Write-Host 'To judge one stage, compare against wall_ms / the logcat time window of that stage.'
