param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$modelDirectory = Join-Path $projectRoot '.tools/models'
$modelPath = Join-Path $modelDirectory 'u2netp.onnx'
$expectedHash = '309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8'
New-Item -ItemType Directory -Force -Path $modelDirectory | Out-Null
if (Test-Path -LiteralPath $modelPath) {
    if ((Get-FileHash -LiteralPath $modelPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
        throw "An unexpected model already exists at $modelPath. Inspect it before replacing it."
    }
    Write-Output "Local photo model already installed and verified: $modelPath"
    return
}
$downloadPath = Join-Path $modelDirectory ('u2netp-' + [Guid]::NewGuid().ToString() + '.download')
try {
    Invoke-WebRequest -Uri 'https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx' -OutFile $downloadPath
    if ((Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
        throw 'Downloaded model checksum mismatch.'
    }
    Move-Item -LiteralPath $downloadPath -Destination $modelPath
    Write-Output "Installed verified local photo model: $modelPath"
} finally {
    if (Test-Path -LiteralPath $downloadPath) { Remove-Item -LiteralPath $downloadPath }
}
