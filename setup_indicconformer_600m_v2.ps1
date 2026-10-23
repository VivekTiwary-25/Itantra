param(
    [string]$ProjectRoot = "D:\projects\sih\itantra",
    [string]$PythonVersion = "3.10",
    [switch]$SkipDownload
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Info([string]$Message) {
    Write-Host "    $Message"
}

function Fail([string]$Message) {
    Write-Host ""
    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}

function Run-PythonFile {
    param(
        [string]$Name,
        [string]$Code
    )
    $tmpDir = Join-Path $WorkDir "_setup_tmp"
    New-Item -ItemType Directory -Force $tmpDir | Out-Null
    $path = Join-Path $tmpDir $Name
    Set-Content -Path $path -Value $Code -Encoding UTF8
    & $PythonExe $path
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Fail "Python step '$Name' failed with exit code $code."
    }
}

$RepoId = "ai4bharat/indic-conformer-600m-multilingual"

if (-not (Test-Path $ProjectRoot)) {
    Fail "Project root does not exist: $ProjectRoot"
}

Set-Location $ProjectRoot

$VenvDir   = Join-Path $ProjectRoot ".venv-indicconformer600m"
$WorkDir   = Join-Path $ProjectRoot ".indicconformer-600m"
$ModelDir  = Join-Path $WorkDir "model"
$StatusOut = Join-Path $WorkDir "SETUP_STATUS.txt"
$PythonExe = Join-Path $VenvDir "Scripts\python.exe"

New-Item -ItemType Directory -Force $WorkDir | Out-Null

Step "Preflight checks"

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    Fail "uv is not on PATH."
}

Info "Project root: $ProjectRoot"
Info "Virtual environment: $VenvDir"
Info "Model directory: $ModelDir"

$GitDir = Join-Path $ProjectRoot ".git"
if (Test-Path $GitDir) {
    $Exclude = Join-Path $GitDir "info\exclude"
    $ExcludeDir = Split-Path $Exclude -Parent
    New-Item -ItemType Directory -Force $ExcludeDir | Out-Null
    if (-not (Test-Path $Exclude)) {
        New-Item -ItemType File -Force $Exclude | Out-Null
    }

    $existing = @(Get-Content $Exclude -ErrorAction SilentlyContinue)
    foreach ($pattern in @(
        ".venv-indicconformer600m/",
        ".indicconformer-600m/"
    )) {
        if ($existing -notcontains $pattern) {
            Add-Content -Path $Exclude -Value $pattern
        }
    }
    Info "Local model/venv paths excluded through .git/info/exclude."
}

Step "Create/reuse isolated Python environment"

if (-not (Test-Path $PythonExe)) {
    & uv venv --python $PythonVersion $VenvDir
    if ($LASTEXITCODE -ne 0) {
        Fail "uv venv failed."
    }
} else {
    Info "Existing environment found; reusing it."
}

if (-not (Test-Path $PythonExe)) {
    Fail "Virtual environment does not contain $PythonExe"
}

Step "Install/reconcile PyTorch + torchaudio"

& uv pip install `
    --python $PythonExe `
    "torch==2.11.0+cu130" `
    "torchaudio==2.11.0+cu130" `
    --index-url "https://download.pytorch.org/whl/cu130"

if ($LASTEXITCODE -ne 0) {
    Fail "PyTorch/torchaudio install failed."
}

Step "Install IndicConformer runtime/tooling"

# IMPORTANT:
# The AI4Bharat model card currently asks for onnxruntime-gpu==1.20.1,
# but PyPI does not publish that Windows wheel. For this quality bake-off,
# CPU ONNX Runtime 1.20.1 is sufficient and reproducible. Desktop speed is
# NOT the SIH mobile-latency measurement.
#
# If we later need desktop GPU ORT, do that as a separate environment task.
& uv pip install `
    --python $PythonExe `
    "transformers==4.48.3" `
    "huggingface_hub>=0.28,<1.0" `
    "onnx==1.20.1" `
    "onnxruntime==1.20.1" `
    "soundfile>=0.12" `
    "sentencepiece>=0.2" `
    "numpy<3"

if ($LASTEXITCODE -ne 0) {
    Fail "IndicConformer runtime/tooling install failed."
}

Step "Sanity-check Python environment"

$sanity = @'
import sys
import torch
import torchaudio
import transformers
import huggingface_hub
import onnx
import onnxruntime as ort

print("python:", sys.version.split()[0])
print("torch:", torch.__version__)
print("cuda_available:", torch.cuda.is_available())
if torch.cuda.is_available():
    print("cuda_device:", torch.cuda.get_device_name(0))
print("torchaudio:", torchaudio.__version__)
print("transformers:", transformers.__version__)
print("huggingface_hub:", huggingface_hub.__version__)
print("onnx:", onnx.__version__)
print("onnxruntime:", ort.__version__)
print("ort_providers:", ort.get_available_providers())
'@

Run-PythonFile -Name "sanity.py" -Code $sanity

New-Item -ItemType Directory -Force $ModelDir | Out-Null

if (-not $SkipDownload) {
    Step "Check Hugging Face authentication"

    $authProbe = @'
from huggingface_hub import HfApi
import sys
try:
    who = HfApi().whoami()
    print("logged_in_as:", who["name"])
except Exception:
    sys.exit(7)
'@

    $tmpAuth = Join-Path $WorkDir "_setup_tmp\auth_probe.py"
    New-Item -ItemType Directory -Force (Split-Path $tmpAuth -Parent) | Out-Null
    Set-Content -Path $tmpAuth -Value $authProbe -Encoding UTF8
    & $PythonExe $tmpAuth *> $null
    $AuthOk = ($LASTEXITCODE -eq 0)

    if ($AuthOk) {
        Info "Existing Hugging Face login found."
    } elseif ($env:HF_TOKEN) {
        Info "HF_TOKEN environment variable found."
        $AuthOk = $true
    } else {
        Write-Host ""
        Write-Host "The AI4Bharat 600M model is gated on Hugging Face." -ForegroundColor Yellow
        Write-Host "Your account must accept the model access conditions once in the browser." -ForegroundColor Yellow
        Write-Host "If you already accepted them, paste a READ token below." -ForegroundColor Yellow
        Write-Host "The token is used only by this PowerShell process." -ForegroundColor Yellow
        Write-Host ""

        $secure = Read-Host "Hugging Face READ token (hf_...)" -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try {
            $env:HF_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        }

        if ([string]::IsNullOrWhiteSpace($env:HF_TOKEN)) {
            Fail "No Hugging Face token supplied."
        }
    }

    Step "Verify access to the gated AI4Bharat model"

    $env:ITANTRA_HF_REPO = $RepoId
    $verifyAccess = @'
from huggingface_hub import HfApi
import os, sys

repo = os.environ["ITANTRA_HF_REPO"]
token = os.environ.get("HF_TOKEN") or True

try:
    info = HfApi().model_info(repo_id=repo, token=token)
    print("access_ok:", info.id)
except Exception as e:
    print("MODEL ACCESS FAILED")
    print(type(e).__name__ + ":", e)
    print()
    print("Accept the model gate on Hugging Face and make sure the token has read access.")
    sys.exit(2)
'@

    Run-PythonFile -Name "verify_access.py" -Code $verifyAccess

    Step "Download the official 600M multilingual IndicConformer snapshot"

    $env:ITANTRA_INDIC_MODEL_DIR = $ModelDir
    $download = @'
from huggingface_hub import snapshot_download
import os

repo = os.environ["ITANTRA_HF_REPO"]
dst = os.environ["ITANTRA_INDIC_MODEL_DIR"]
token = os.environ.get("HF_TOKEN") or True

path = snapshot_download(
    repo_id=repo,
    local_dir=dst,
    token=token,
)
print("model_snapshot:", path)
'@

    Run-PythonFile -Name "download_model.py" -Code $download
} else {
    Info "SkipDownload supplied; model download skipped."
}

Step "Verify downloaded model structure"

if (-not $SkipDownload) {
    if (-not (Test-Path (Join-Path $ModelDir "config.json"))) {
        Fail "Model snapshot is missing config.json."
    }

    if (-not (Test-Path (Join-Path $ModelDir "model_onnx.py")) -and
        -not (Test-Path (Join-Path $ModelDir "model_ts.py"))) {
        Fail "Model snapshot does not contain the expected AI4Bharat custom model code."
    }

    $modelFiles = Get-ChildItem -Path $ModelDir -Recurse -File
    $totalBytes = ($modelFiles | Measure-Object Length -Sum).Sum
    $totalGB = [Math]::Round($totalBytes / 1GB, 2)

    Info "Downloaded files: $($modelFiles.Count)"
    Info "Snapshot size: $totalGB GB"
}

Step "Write handoff status for Sonnet"

$env:ITANTRA_STATUS_OUT = $StatusOut
$env:ITANTRA_MODEL_DIR = $ModelDir
$env:ITANTRA_PYTHON_EXE = $PythonExe

$statusProbe = @'
import os
import torch
import onnxruntime as ort

lines = []
lines.append("iTantra IndicConformer 600M setup")
lines.append("================================")
lines.append("")
lines.append("Repository:")
lines.append("ai4bharat/indic-conformer-600m-multilingual")
lines.append("")
lines.append("Python:")
lines.append(os.environ["ITANTRA_PYTHON_EXE"])
lines.append("")
lines.append("Local model snapshot:")
lines.append(os.environ["ITANTRA_MODEL_DIR"])
lines.append("")
lines.append("CUDA available to PyTorch:")
lines.append(str(torch.cuda.is_available()))
if torch.cuda.is_available():
    lines.append(torch.cuda.get_device_name(0))
lines.append("")
lines.append("ONNX Runtime providers:")
lines.append(", ".join(ort.get_available_providers()))
lines.append("")
lines.append("NOTE:")
lines.append("This setup intentionally uses CPU ONNX Runtime 1.20.1 for the quality bake-off.")
lines.append("Do not interpret desktop inference time as Android latency.")
lines.append("")
lines.append("NEXT OBJECTIVE FOR SONNET:")
lines.append("Run the SAME existing 10 Hindi WAV recordings and references through")
lines.append("this 600M multilingual IndicConformer with language='hi' using BOTH")
lines.append("CTC and RNNT. Do not quantize or integrate yet.")
lines.append("")
lines.append("Compare against the existing Dolphin and Whisper candidates using:")
lines.append("- WER")
lines.append("- CER")
lines.append("- wrong-script failures")
lines.append("- exact transcripts")
lines.append("- per-clip runtime (context only, not mobile latency)")
lines.append("")
lines.append("Keep the existing scoring normalization consistent.")

with open(os.environ["ITANTRA_STATUS_OUT"], "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")

print("status_written:", os.environ["ITANTRA_STATUS_OUT"])
'@

Run-PythonFile -Name "write_status.py" -Code $statusProbe

Step "Cleanup setup temp files"

$tmpDir = Join-Path $WorkDir "_setup_tmp"
if (Test-Path $tmpDir) {
    Remove-Item -Recurse -Force $tmpDir
}

Step "DONE"

Write-Host ""
Write-Host "READY FOR SONNET" -ForegroundColor Green
Write-Host ""
Write-Host "Environment: $VenvDir"
Write-Host "Model:       $ModelDir"
Write-Host "Handoff:     $StatusOut"
Write-Host ""
Write-Host "Next: point Sonnet at SETUP_STATUS.txt and run the 10-clip CTC/RNNT bake-off."
