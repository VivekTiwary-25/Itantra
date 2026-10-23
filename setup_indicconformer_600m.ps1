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

Step "Preflight checks"

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
    Fail "uv is not on PATH. This script intentionally uses the same uv-style isolated setup as the Dolphin environment."
}

Info "Project root: $ProjectRoot"
Info "Virtual environment: $VenvDir"
Info "Model directory: $ModelDir"

# Keep local experiment/model folders out of Git without changing .gitignore.
$GitDir = Join-Path $ProjectRoot ".git"
if (Test-Path $GitDir) {
    $Exclude = Join-Path $GitDir "info\exclude"
    $ExcludeDir = Split-Path $Exclude -Parent
    New-Item -ItemType Directory -Force $ExcludeDir | Out-Null
    if (-not (Test-Path $Exclude)) {
        New-Item -ItemType File -Force $Exclude | Out-Null
    }

    $existing = Get-Content $Exclude -ErrorAction SilentlyContinue
    foreach ($pattern in @(
        ".venv-indicconformer600m/",
        ".indicconformer-600m/"
    )) {
        if ($existing -notcontains $pattern) {
            Add-Content -Path $Exclude -Value $pattern
        }
    }
    Info "Added local-only model/venv exclusions to .git/info/exclude (no tracked file changed)."
}

Step "Create isolated Python environment"

if (-not (Test-Path $PythonExe)) {
    & uv venv --python $PythonVersion $VenvDir
} else {
    Info "Existing environment found; reusing it."
}

if (-not (Test-Path $PythonExe)) {
    Fail "Virtual environment creation did not produce $PythonExe"
}

Step "Install PyTorch + torchaudio (CUDA 13.0, matching the working Dolphin machine setup)"

& uv pip install `
    --python $PythonExe `
    "torch==2.11.0+cu130" `
    "torchaudio==2.11.0+cu130" `
    --index-url "https://download.pytorch.org/whl/cu130"

Step "Install IndicConformer runtime/tooling"

# AI4Bharat's current model card requires Transformers, torchaudio and ONNX Runtime.
# We install the GPU ONNX Runtime distribution only (it provides the `onnxruntime`
# Python module itself) rather than installing CPU+GPU distributions on top of each other.
& uv pip install `
    --python $PythonExe `
    "transformers==4.48.3" `
    "huggingface_hub>=0.28,<1.0" `
    "onnx==1.20.1" `
    "onnxruntime-gpu==1.20.1" `
    "soundfile>=0.12" `
    "sentencepiece>=0.2" `
    "numpy<3"

Step "Sanity-check Python environment"

& $PythonExe -c @'
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

if ($LASTEXITCODE -ne 0) {
    Fail "Python/runtime sanity check failed."
}

New-Item -ItemType Directory -Force $WorkDir | Out-Null
New-Item -ItemType Directory -Force $ModelDir | Out-Null

if (-not $SkipDownload) {
    Step "Check Hugging Face authentication"

    # The official model is gated. The user must accept the model's access
    # conditions in the browser once. This script cannot accept those terms.
    # Authentication options:
    #   1) existing Hugging Face login
    #   2) HF_TOKEN environment variable
    #   3) secure prompt below (used only in this PowerShell process)

    $AuthOk = $false

    try {
        & $PythonExe -c "from huggingface_hub import HfApi; print(HfApi().whoami()['name'])" *> $null
        if ($LASTEXITCODE -eq 0) {
            $AuthOk = $true
            Info "Existing Hugging Face login found."
        }
    } catch {
        $AuthOk = $false
    }

    if (-not $AuthOk -and $env:HF_TOKEN) {
        Info "HF_TOKEN environment variable found."
        $AuthOk = $true
    }

    if (-not $AuthOk) {
        Write-Host ""
        Write-Host "The AI4Bharat 600M model is gated on Hugging Face." -ForegroundColor Yellow
        Write-Host "Before this can download, your HF account must have accepted the model access conditions." -ForegroundColor Yellow
        Write-Host "If you have already accepted them, paste a READ token now." -ForegroundColor Yellow
        Write-Host "The token is used only in this PowerShell process and is not written into this script." -ForegroundColor Yellow
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

    & $PythonExe -c @"
from huggingface_hub import HfApi
import os, sys

repo = "$RepoId"
token = os.environ.get("HF_TOKEN") or True

try:
    info = HfApi().model_info(repo_id=repo, token=token)
    print("access_ok:", info.id)
except Exception as e:
    print("MODEL ACCESS FAILED")
    print(type(e).__name__ + ":", e)
    print()
    print("You probably still need to accept the model's access conditions on Hugging Face,")
    print("or the token does not have read access.")
    sys.exit(2)
"@

    if ($LASTEXITCODE -ne 0) {
        Fail "Could not access $RepoId. Accept the model gate in your Hugging Face account, then rerun this script."
    }

    Step "Download the official 600M multilingual IndicConformer snapshot"

    # snapshot_download resumes interrupted downloads and reuses completed files.
    $env:ITANTRA_INDIC_MODEL_DIR = $ModelDir
    & $PythonExe -c @"
from huggingface_hub import snapshot_download
import os

repo = "$RepoId"
dst = os.environ["ITANTRA_INDIC_MODEL_DIR"]
token = os.environ.get("HF_TOKEN") or True

path = snapshot_download(
    repo_id=repo,
    local_dir=dst,
    token=token,
)
print("model_snapshot:", path)
"@

    if ($LASTEXITCODE -ne 0) {
        Fail "Model download failed. Rerun after fixing the reported access/network issue; completed files will be reused."
    }
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

$cudaInfo = & $PythonExe -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
$ortInfo  = & $PythonExe -c "import onnxruntime as ort; print(','.join(ort.get_available_providers()))"

$status = @"
iTantra IndicConformer 600M setup
================================

Repository:
$RepoId

Project:
$ProjectRoot

Python:
$PythonExe

Virtual environment:
$VenvDir

Local model snapshot:
$ModelDir

CUDA check:
$($cudaInfo -join "`r`n")

ONNX Runtime providers:
$ortInfo

Current objective:
Run the SAME existing 10 Hindi WAV recordings and matching reference transcripts
through this official 600M multilingual IndicConformer, with language explicitly
set to hi, using BOTH CTC and RNNT decoding.

Do not quantize.
Do not port to Android.
Do not alter the Bluetooth branch.
Do not replace the existing Dolphin baseline.

Compare against the existing Dolphin results using the same scoring normalization:
- WER
- CER
- wrong-script failures
- per-clip inference time

The official model API is expected to support:
    model(wav, "hi", "ctc")
    model(wav, "hi", "rnnt")

Load from the LOCAL model snapshot where practical so the benchmark is reproducible.
"@

Set-Content -Path $StatusOut -Value $status -Encoding UTF8

Step "DONE"

Write-Host ""
Write-Host "READY FOR SONNET" -ForegroundColor Green
Write-Host ""
Write-Host "Environment: $VenvDir"
Write-Host "Model:       $ModelDir"
Write-Host "Handoff:     $StatusOut"
Write-Host ""
Write-Host "Next: point Sonnet at SETUP_STATUS.txt and ask it to run the 10-clip CTC/RNNT bake-off."
