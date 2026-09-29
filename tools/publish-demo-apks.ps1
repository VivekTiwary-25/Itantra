# Builds the three film-demo APKs and publishes them to the rolling GitHub
# release `demo-latest` (assets are replaced in place). Run from the repo root:
#   powershell -ExecutionPolicy Bypass -File tools\publish-demo-apks.ps1
# Nothing is uploaded unless all three APKs build.
# Exit codes are checked explicitly: under 'Stop', PowerShell 5.1 turns native stderr into terminating errors.
$ErrorActionPreference = 'Continue'
Set-Location (Split-Path $PSScriptRoot -Parent)

$actors = 'Vachana', 'Yash', 'Vivek'
$tasks = $actors | ForEach-Object { "assembleDemo${_}Release" }
& .\gradlew.bat @tasks --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed; nothing published." }

$dist = Join-Path 'build' 'demo-dist'
New-Item -ItemType Directory -Force $dist | Out-Null
$commit = (git rev-parse HEAD).Trim()
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
$stamp = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$lines = @("iTantra SIH film demo APKs", "built: $stamp", "commit: $commit ($branch)", "")
$assets = @()

foreach ($a in $actors) {
    $src = "app\build\outputs\apk\demo$a\release\app-demo$a-release.apk"
    if (-not (Test-Path $src)) { throw "Missing $src; nothing published." }
    $dst = Join-Path $dist "iTantra-$a-latest.apk"
    Copy-Item $src $dst -Force
    $sha = (Get-FileHash $dst -Algorithm SHA256).Hash.ToLower()
    $lines += "iTantra-$a-latest.apk  variant=demo${a}Release  package=com.chmod777.itantra.demo.$($a.ToLower())  sha256=$sha"
    $assets += $dst
}

$manifest = Join-Path $dist 'iTantra-demo-manifest.txt'
$lines | Set-Content -Encoding utf8 $manifest
$assets += $manifest
$lines | ForEach-Object { Write-Host $_ }

# Title built from a code point: Windows PowerShell 5.1 misreads non-ASCII in BOM-less scripts.
$tag = 'demo-latest'
gh release view $tag *> $null
if ($LASTEXITCODE -ne 0) {
    gh release create $tag --target $commit --title "iTantra SIH Demo $([char]0x2014) Latest" `
        --notes "Rolling film-demo builds (one APK per actor). See iTantra-demo-manifest.txt for the commit and SHA-256 of each APK." `
        --prerelease
    if ($LASTEXITCODE -ne 0) { throw "gh release create failed." }
}
gh release upload $tag @assets --clobber
if ($LASTEXITCODE -ne 0) { throw "gh release upload failed." }
Write-Host "Published to release '$tag'."
