param(
    [string]$RepoRoot = (Get-Location).Path,
    [switch]$Force,
    # Bengali (Meta MMS-TTS, CC BY-NC 4.0) has no official prebuilt sherpa-onnx package. Point this at the
    # locally converted model with its decoder in fp16 (tts-research/mms-ben/MMS_BN_CONVERSION.md, then
    # tts-research/mms-ben/speed/SPEED_REPORT.md); it is hash-checked.
    [string]$MmsBengaliModel = "",
    # Odia (Meta MMS-TTS, CC BY-NC 4.0) has no official prebuilt sherpa-onnx package either. Point this at
    # the locally converted model with its decoder in fp16 (tts-research/mms-ory/MMS_ORY_CONVERSION.md); it
    # is hash-checked.
    [string]$MmsOdiaModel = "",
    # Gujarati (Meta MMS-TTS, CC BY-NC 4.0), same situation: locally converted, decoder in fp16
    # (tts-research/mms-guj); hash-checked.
    [string]$MmsGujaratiModel = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Format-Size([long]$Bytes) {
    if ($Bytes -ge 1GB) { return "{0:N2} GB" -f ($Bytes / 1GB) }
    if ($Bytes -ge 1MB) { return "{0:N2} MB" -f ($Bytes / 1MB) }
    if ($Bytes -ge 1KB) { return "{0:N2} KB" -f ($Bytes / 1KB) }
    return "$Bytes B"
}

function Assert-MinSize([string]$Path, [long]$MinBytes, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label is missing: $Path"
    }

    $size = (Get-Item -LiteralPath $Path).Length
    if ($size -lt $MinBytes) {
        throw "$Label looks too small ($(Format-Size $size)). Expected at least $(Format-Size $MinBytes)."
    }

    return $size
}

function Assert-Sha256([string]$Path, [string]$Expected, [string]$Label) {
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $Expected.ToLowerInvariant()) {
        throw @"
SHA-256 mismatch for $Label.
Expected: $Expected
Actual:   $actual
File:     $Path

The file may be incomplete, corrupted, or a different model revision.
Delete it (or rerun with -Force) and try again.
"@
    }
}

function Download-File(
    [string]$Url,
    [string]$Destination,
    [string]$Label,
    [long]$MinBytes,
    [string]$Sha256 = ""
) {
    if ((Test-Path -LiteralPath $Destination -PathType Leaf) -and -not $Force) {
        $existingSize = Assert-MinSize $Destination $MinBytes $Label

        if ($Sha256) {
            Write-Host "[CHECK] $Label already exists; verifying SHA-256..."
            Assert-Sha256 $Destination $Sha256 $Label
        }

        Write-Ok "$Label already present ($(Format-Size $existingSize)); skipping download."
        return
    }

    $parent = Split-Path -Parent $Destination
    New-Item -ItemType Directory -Force -Path $parent | Out-Null

    $partial = "$Destination.partial"
    Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue

    if ($Force -and (Test-Path -LiteralPath $Destination)) {
        Remove-Item -LiteralPath $Destination -Force
    }

    Write-Host "[GET] $Label"
    Write-Host "      $Url"

    $success = $false
    $lastError = $null

    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            Invoke-WebRequest `
                -Uri $Url `
                -OutFile $partial `
                -UseBasicParsing `
                -MaximumRedirection 10

            $size = Assert-MinSize $partial $MinBytes $Label

            if ($Sha256) {
                Write-Host "[CHECK] SHA-256..."
                Assert-Sha256 $partial $Sha256 $Label
            }

            Move-Item -LiteralPath $partial -Destination $Destination -Force
            Write-Ok "$Label downloaded ($(Format-Size $size))."
            $success = $true
            break
        }
        catch {
            $lastError = $_
            Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
            if ($attempt -lt 3) {
                Write-Warn "$Label download failed on attempt $attempt. Retrying..."
                Start-Sleep -Seconds (2 * $attempt)
            }
        }
    }

    if (-not $success) {
        throw "Failed to download $Label after 3 attempts.`n$lastError"
    }
}

function Assert-TtsSupport(
    [string]$Dir,
    [string]$ModelName
) {
    $required = @(
        (Join-Path $Dir "tokens.txt"),
        (Join-Path $Dir "$ModelName.onnx.json"),
        (Join-Path $Dir "espeak-ng-data")
    )

    foreach ($item in $required) {
        if (-not (Test-Path -LiteralPath $item)) {
            throw @"
Expected TTS support file/folder is missing:
$item

This script intentionally adds only the missing ONNX binary and does NOT overwrite
the Speech team's committed TTS support files. Make sure you are on the correct
iTantra working tree/branch before continuing.
"@
        }
    }
}

function Install-ModelFromArchive(
    [string]$Url,
    [string]$ArchiveName,
    [string]$ExpectedFolder,
    [string]$OnnxName,
    [string]$DestinationDir,
    [string]$Label,
    [string]$TempRoot,
    [string]$ArchiveSha256 = "",
    [string]$OnnxSha256 = ""
) {
    $destination = Join-Path $DestinationDir $OnnxName

    if ((Test-Path -LiteralPath $destination -PathType Leaf) -and -not $Force) {
        $size = Assert-MinSize $destination 50MB $Label
        if ($OnnxSha256) {
            Write-Host "[CHECK] $Label already exists; verifying SHA-256..."
            Assert-Sha256 $destination $OnnxSha256 $Label
        }
        Write-Ok "$Label already present ($(Format-Size $size)); skipping download."
        return
    }

    $tar = Get-Command tar.exe -ErrorAction SilentlyContinue
    if (-not $tar) {
        throw @"
Windows tar.exe was not found.
Modern Windows normally includes it. Open a normal PowerShell/Terminal and run:

    tar --version

If that command is unavailable, install/enable a tar-capable tool before rerunning.
"@
    }

    $archivePath = Join-Path $TempRoot $ArchiveName
    $extractRoot = Join-Path $TempRoot ([IO.Path]::GetFileNameWithoutExtension([IO.Path]::GetFileNameWithoutExtension($ArchiveName)))

    New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null

    Download-File `
        -Url $Url `
        -Destination $archivePath `
        -Label "$Label archive" `
        -MinBytes 10MB `
        -Sha256 $ArchiveSha256

    Write-Host "[EXTRACT] $ArchiveName"
    & $tar.Source -xjf $archivePath -C $extractRoot
    if ($LASTEXITCODE -ne 0) {
        throw "tar.exe failed to extract $ArchiveName (exit code $LASTEXITCODE)."
    }

    # Prefer the canonical archive layout, but search recursively as a fallback.
    $candidate = Join-Path (Join-Path $extractRoot $ExpectedFolder) $OnnxName

    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        $matches = @(Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter $OnnxName)
        if ($matches.Count -ne 1) {
            throw "Could not uniquely locate $OnnxName after extracting $ArchiveName."
        }
        $candidate = $matches[0].FullName
    }

    $size = Assert-MinSize $candidate 50MB $Label
    if ($OnnxSha256) {
        Assert-Sha256 $candidate $OnnxSha256 $Label
    }

    if ($Force -and (Test-Path -LiteralPath $destination)) {
        Remove-Item -LiteralPath $destination -Force
    }

    Copy-Item -LiteralPath $candidate -Destination $destination -Force
    $installedSize = Assert-MinSize $destination 50MB $Label
    if ($OnnxSha256) {
        Assert-Sha256 $destination $OnnxSha256 $Label
    }
    Write-Ok "$Label installed ($(Format-Size $installedSize))."
}

try {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Magenta
    Write-Host " iTantra model setup — STT + TTS" -ForegroundColor Magenta
    Write-Host "============================================================" -ForegroundColor Magenta

    $RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
    $Assets = Join-Path $RepoRoot "app\src\main\assets"

    Write-Step "Checking repository"
    if (-not (Test-Path -LiteralPath $Assets -PathType Container)) {
        throw @"
Could not find:
$Assets

Run this script from the ROOT of the iTantra repository, or pass:

    .\setup-models.ps1 -RepoRoot "D:\path\to\Itantra"
"@
    }

    Write-Ok "Repo root: $RepoRoot"
    Write-Ok "Assets:    $Assets"

    # These should already be committed by the Speech team.
    $TinyTokens = Join-Path $Assets "tiny.en-tokens.txt"
    $BaseTokens = Join-Path $Assets "base-tokens.txt"

    if (-not (Test-Path -LiteralPath $TinyTokens -PathType Leaf)) {
        throw "Missing committed STT token file: $TinyTokens"
    }
    if (-not (Test-Path -LiteralPath $BaseTokens -PathType Leaf)) {
        throw "Missing committed STT token file: $BaseTokens"
    }
    Write-Ok "Existing STT token files found."

    $DolphinDir = Join-Path $Assets "dolphin-base-ctc-multi-lang-int8"
    $DolphinTokens = Join-Path $DolphinDir "tokens.txt"
    if (-not (Test-Path -LiteralPath $DolphinTokens -PathType Leaf)) {
        throw "Missing committed Dolphin token file: $DolphinTokens"
    }
    Assert-Sha256 $DolphinTokens "c3788261a51df1899ea4b210b552cd42139204de72c0ad60f6cebb199078872e" "Dolphin tokens"
    Write-Ok "Dolphin token file found and verified."

    $RyanDir = Join-Path $Assets "vits-piper-en_US-ryan-medium"
    $PrathamDir = Join-Path $Assets "vits-piper-hi_IN-pratham-medium"
    $ArjunDir = Join-Path $Assets "vits-piper-ml_IN-arjun-medium"

    Assert-TtsSupport $RyanDir "en_US-ryan-medium"
    Assert-TtsSupport $PrathamDir "hi_IN-pratham-medium"
    Assert-TtsSupport $ArjunDir "ml_IN-arjun-medium"
    Write-Ok "Existing Ryan + Pratham + Arjun TTS support files found."

    # TLS 1.2 helps older Windows PowerShell installations.
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    } catch {}

    Write-Step "Downloading 4 Whisper STT models"

    $sttModels = @(
        @{
            Label = "Whisper tiny.en encoder INT8"
            Url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx?download=true"
            Dest = (Join-Path $Assets "tiny.en-encoder.int8.onnx")
            Min = 10MB
            Sha = "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6"
        },
        @{
            Label = "Whisper tiny.en decoder INT8"
            Url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-decoder.int8.onnx?download=true"
            Dest = (Join-Path $Assets "tiny.en-decoder.int8.onnx")
            Min = 80MB
            Sha = "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527"
        },
        @{
            Label = "Whisper base encoder INT8"
            Url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-encoder.int8.onnx?download=true"
            Dest = (Join-Path $Assets "base-encoder.int8.onnx")
            Min = 25MB
            Sha = "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"
        },
        @{
            Label = "Whisper base decoder INT8"
            Url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx?download=true"
            Dest = (Join-Path $Assets "base-decoder.int8.onnx")
            Min = 120MB
            Sha = "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"
        }
    )

    foreach ($m in $sttModels) {
        Download-File `
            -Url $m.Url `
            -Destination $m.Dest `
            -Label $m.Label `
            -MinBytes $m.Min `
            -Sha256 $m.Sha
    }

    Write-Step "Downloading + extracting Dolphin STT and 3 Piper TTS models"

    $tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("itantra-models-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

    try {
        Install-ModelFromArchive `
            -Url "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2" `
            -ArchiveName "sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02.tar.bz2" `
            -ExpectedFolder "sherpa-onnx-dolphin-base-ctc-multi-lang-int8-2025-04-02" `
            -OnnxName "model.int8.onnx" `
            -DestinationDir $DolphinDir `
            -Label "Dolphin base multilingual CTC INT8" `
            -TempRoot $tempRoot `
            -ArchiveSha256 "6f23da2303c3c2e5fa6445c450fa2a7133cd57e3da070ae5f97ab9e0dfbb4a54" `
            -OnnxSha256 "a3aa46c97f3f60f135ff949793cb05fabe7a0b3c484dc2e3cc699d354ee11b76"

        Install-ModelFromArchive `
            -Url "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium.tar.bz2" `
            -ArchiveName "vits-piper-en_US-ryan-medium.tar.bz2" `
            -ExpectedFolder "vits-piper-en_US-ryan-medium" `
            -OnnxName "en_US-ryan-medium.onnx" `
            -DestinationDir $RyanDir `
            -Label "Piper English Ryan medium" `
            -TempRoot $tempRoot

        Install-ModelFromArchive `
            -Url "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium.tar.bz2" `
            -ArchiveName "vits-piper-hi_IN-pratham-medium.tar.bz2" `
            -ExpectedFolder "vits-piper-hi_IN-pratham-medium" `
            -OnnxName "hi_IN-pratham-medium.onnx" `
            -DestinationDir $PrathamDir `
            -Label "Piper Hindi Pratham medium" `
            -TempRoot $tempRoot

        Install-ModelFromArchive `
            -Url "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ml_IN-arjun-medium.tar.bz2" `
            -ArchiveName "vits-piper-ml_IN-arjun-medium.tar.bz2" `
            -ExpectedFolder "vits-piper-ml_IN-arjun-medium" `
            -OnnxName "ml_IN-arjun-medium.onnx" `
            -DestinationDir $ArjunDir `
            -Label "Piper Malayalam Arjun medium" `
            -TempRoot $tempRoot `
            -ArchiveSha256 "3058d098e8b1ffcdd6069e96b1d492f319333235912a627c309c7c54cea59acf" `
            -OnnxSha256 "33c97f81a1d326e0c524e321940dacf3ac1b48b6b5c486a6afa8bff245695cf7"
    }
    finally {
        if (Test-Path -LiteralPath $tempRoot) {
            Write-Host "[CLEAN] Removing temporary archives/extraction files..."
            Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    }

    Write-Step "Bengali MMS TTS (optional)"
    $MmsBnDir = Join-Path $Assets "vits-mms-ben"
    $MmsBnDest = Join-Path $MmsBnDir "model.onnx"
    # fp16-decoder model derived from the fp32 export f8f7bf4f...4e71 by speed/mms_decoder_fp16.py.
    $MmsBnSha = "8a819bba1b0c424842b71f89d36987e278dde7e4e564e94af0b90a782a2fb90e"
    if (-not (Test-Path -LiteralPath $MmsBnDest -PathType Leaf) -or $Force) {
        if ($MmsBengaliModel -and (Test-Path -LiteralPath $MmsBengaliModel -PathType Leaf)) {
            Assert-Sha256 $MmsBengaliModel $MmsBnSha "Bengali MMS model.onnx"
            Copy-Item -LiteralPath $MmsBengaliModel -Destination $MmsBnDest -Force
        }
        else {
            Write-Warn "Bengali MMS model.onnx not installed. Convert it (tts-research/mms-ben/MMS_BN_CONVERSION.md), derive the fp16-decoder model (tts-research/mms-ben/speed/SPEED_REPORT.md) and rerun with -MmsBengaliModel <path>. Bengali TTS is skipped until then."
        }
    }
    if (Test-Path -LiteralPath $MmsBnDest -PathType Leaf) {
        Assert-Sha256 $MmsBnDest $MmsBnSha "Bengali MMS model.onnx"
        Write-Ok "Bengali MMS TTS present and verified."
    }

    Write-Step "Odia MMS TTS (optional)"
    $MmsOryDir = Join-Path $Assets "vits-mms-ory"
    $MmsOryDest = Join-Path $MmsOryDir "model.onnx"
    # fp16-decoder model derived from the fp32 export 81c08eda...2fb90e by tts-research/mms-ory/mms_decoder_fp16.py.
    $MmsOrySha = "3dddf002b8e760ff75dec0d9fba2e08b1a2279e3fd78edf100da0cad23741f69"
    if (-not (Test-Path -LiteralPath $MmsOryDest -PathType Leaf) -or $Force) {
        if ($MmsOdiaModel -and (Test-Path -LiteralPath $MmsOdiaModel -PathType Leaf)) {
            Assert-Sha256 $MmsOdiaModel $MmsOrySha "Odia MMS model.onnx"
            Copy-Item -LiteralPath $MmsOdiaModel -Destination $MmsOryDest -Force
        }
        else {
            Write-Warn "Odia MMS model.onnx not installed. Convert it (tts-research/mms-ory/MMS_ORY_CONVERSION.md) and rerun with -MmsOdiaModel <path>. Odia TTS is skipped until then."
        }
    }
    if (Test-Path -LiteralPath $MmsOryDest -PathType Leaf) {
        Assert-Sha256 $MmsOryDest $MmsOrySha "Odia MMS model.onnx"
        Write-Ok "Odia MMS TTS present and verified."
    }

    Write-Step "Gujarati MMS TTS (optional)"
    $MmsGuDir = Join-Path $Assets "vits-mms-guj"
    $MmsGuDest = Join-Path $MmsGuDir "model.onnx"
    # fp16-decoder model derived from the fp32 export 593b73e7...76cba by tts-research/mms-guj/mms_decoder_fp16.py.
    $MmsGuSha = "3ce0a3206080915f6541c00bcd26df1f2817d118d6e324fda62011d679583601"
    if (-not (Test-Path -LiteralPath $MmsGuDest -PathType Leaf) -or $Force) {
        if ($MmsGujaratiModel -and (Test-Path -LiteralPath $MmsGujaratiModel -PathType Leaf)) {
            Assert-Sha256 $MmsGujaratiModel $MmsGuSha "Gujarati MMS model.onnx"
            Copy-Item -LiteralPath $MmsGujaratiModel -Destination $MmsGuDest -Force
        }
        else {
            Write-Warn "Gujarati MMS model.onnx not installed. Convert it (tts-research/mms-guj/MMS_GUJ_CONVERSION.md), derive the fp16-decoder model and rerun with -MmsGujaratiModel <path>. Gujarati TTS is skipped until then."
        }
    }
    if (Test-Path -LiteralPath $MmsGuDest -PathType Leaf) {
        Assert-Sha256 $MmsGuDest $MmsGuSha "Gujarati MMS model.onnx"
        Write-Ok "Gujarati MMS TTS present and verified."
    }

    Write-Step "Final verification"

    $final = @(
        @{ Label = "tiny.en encoder"; Path = (Join-Path $Assets "tiny.en-encoder.int8.onnx"); Min = 10MB; Sha = "0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6" },
        @{ Label = "tiny.en decoder"; Path = (Join-Path $Assets "tiny.en-decoder.int8.onnx"); Min = 80MB; Sha = "06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527" },
        @{ Label = "base encoder";    Path = (Join-Path $Assets "base-encoder.int8.onnx");    Min = 25MB; Sha = "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11" },
        @{ Label = "base decoder";    Path = (Join-Path $Assets "base-decoder.int8.onnx");    Min = 120MB; Sha = "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d" },
        @{ Label = "Dolphin STT";      Path = (Join-Path $DolphinDir "model.int8.onnx");       Min = 50MB; Sha = "a3aa46c97f3f60f135ff949793cb05fabe7a0b3c484dc2e3cc699d354ee11b76" },
        @{ Label = "Ryan TTS";        Path = (Join-Path $RyanDir "en_US-ryan-medium.onnx");   Min = 50MB; Sha = "" },
        @{ Label = "Pratham TTS";     Path = (Join-Path $PrathamDir "hi_IN-pratham-medium.onnx"); Min = 50MB; Sha = "" },
        @{ Label = "Arjun TTS (ml)";  Path = (Join-Path $ArjunDir "ml_IN-arjun-medium.onnx"); Min = 50MB; Sha = "33c97f81a1d326e0c524e321940dacf3ac1b48b6b5c486a6afa8bff245695cf7" }
    )

    foreach ($m in $final) {
        $size = Assert-MinSize $m.Path $m.Min $m.Label
        if ($m.Sha) {
            Assert-Sha256 $m.Path $m.Sha $m.Label
        }
        Write-Ok ("{0,-18} {1,10}" -f $m.Label, (Format-Size $size))
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host " ALL 8 MODEL FILES ARE READY" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "STT:"
    Write-Host "  app/src/main/assets/tiny.en-encoder.int8.onnx"
    Write-Host "  app/src/main/assets/tiny.en-decoder.int8.onnx"
    Write-Host "  app/src/main/assets/base-encoder.int8.onnx"
    Write-Host "  app/src/main/assets/base-decoder.int8.onnx"
    Write-Host "  app/src/main/assets/dolphin-base-ctc-multi-lang-int8/model.int8.onnx"
    Write-Host ""
    Write-Host "TTS:"
    Write-Host "  app/src/main/assets/vits-piper-en_US-ryan-medium/en_US-ryan-medium.onnx"
    Write-Host "  app/src/main/assets/vits-piper-hi_IN-pratham-medium/hi_IN-pratham-medium.onnx"
    Write-Host "  app/src/main/assets/vits-piper-ml_IN-arjun-medium/ml_IN-arjun-medium.onnx"
    Write-Host ""
    Write-Host "Model setup complete."
}
catch {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host " MODEL SETUP FAILED" -ForegroundColor Red
    Write-Host "============================================================" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ""
    exit 1
}
