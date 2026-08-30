$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$toolSource = Join-Path $projectRoot 'tools\minipaintdex-data\src'
$tests = Join-Path $projectRoot 'tools\minipaintdex-data\tests'
$python = (Get-Command python -ErrorAction SilentlyContinue).Source

if (-not $python) {
    throw 'Python 3.12 or newer is required to test the data tools.'
}

$previousPythonPath = $env:PYTHONPATH
try {
    $env:PYTHONPATH = $toolSource
    & $python -m unittest discover -s $tests -v
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    $env:PYTHONPATH = $previousPythonPath
}
