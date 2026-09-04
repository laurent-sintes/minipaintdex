$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../local-server.ps1')
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$fixture = Join-Path $repository "target/launcher-tests/$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Path $fixture -Force | Out-Null
$script:passed = 0
function Assert-True($Condition, [string]$Message) {
    if (-not $Condition) { throw "FAILED: $Message" }
    $script:passed++
}
function Put-Fixture([string]$Name, [string]$Value) {
    $path = Join-Path $fixture $Name
    New-Item -ItemType Directory -Path (Split-Path $path) -Force | Out-Null
    [IO.File]::WriteAllText($path, $Value)
    return $path
}
$null = Put-Fixture 'pom.xml' '<project><modules><module>backend/domain</module><module>backend/server</module><module>backend/cli</module></modules></project>'
$null = Put-Fixture 'backend/domain/pom.xml' 'domain'
$null = Put-Fixture 'backend/server/pom.xml' 'server'
$null = Put-Fixture 'frontend/package.json' '{}'
$null = Put-Fixture 'frontend/pnpm-lock.yaml' 'lock'
$source = Put-Fixture 'backend/domain/src/main/java/Example.java' 'original'
$null = Put-Fixture 'docs/admin/example.md' 'documentation'
$null = Put-Fixture 'config/application.yaml' 'port: 8080'
$fakeJava = Put-Fixture 'jdk/bin/java.exe' 'jdk'
$null = Put-Fixture 'jdk/release' 'java25'
$first = Get-ServerInputDigest $fixture $fakeJava
Assert-True ($first -eq (Get-ServerInputDigest $fixture $fakeJava)) 'stable input fingerprint'
$dash = Put-Fixture 'ordering/paint-app.ts' 'dash'
$dot = Put-Fixture 'ordering/paint.ts' 'dot'
$hash = [Security.Cryptography.SHA256]::Create()
try {
    $canonical = @($dash, $dot) | ForEach-Object {
        "$_=$([BitConverter]::ToString($hash.ComputeHash([IO.File]::ReadAllBytes($_))))"
    }
    $expected = [BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes(($canonical -join "`n")))).Replace('-', '').ToLowerInvariant()
    Assert-True ($expected -eq (Get-ContentDigest @($dot, $dash, $dot))) 'ordinal punctuation order and duplicates are independent of PowerShell/.NET version'
} finally { $hash.Dispose() }
$originalTime = (Get-Item -LiteralPath $source).LastWriteTimeUtc
[IO.File]::WriteAllText($source, 'modified')
(Get-Item -LiteralPath $source).LastWriteTimeUtc = $originalTime
Assert-True ($first -ne (Get-ServerInputDigest $fixture $fakeJava)) 'content change with unchanged timestamp'
[IO.File]::WriteAllText($source, 'original')
$added = Put-Fixture 'backend/domain/src/main/java/Added.java' 'added'
Assert-True ($first -ne (Get-ServerInputDigest $fixture $fakeJava)) 'untracked source addition'
Remove-Item -LiteralPath $added
Assert-True ($first -eq (Get-ServerInputDigest $fixture $fakeJava)) 'source deletion restored fingerprint'
$null = Put-Fixture 'data/market/paints/test.yaml' 'changed data'
$null = Put-Fixture 'media/workshop/test.png' 'changed media'
$null = Put-Fixture 'backend/domain/src/test/java/Test.java' 'test'
$null = Put-Fixture 'frontend/tsconfig.tsbuildinfo' 'generated compiler cache'
Assert-True ($first -eq (Get-ServerInputDigest $fixture $fakeJava)) 'data/media/backend tests do not require runtime compilation'
$null = Put-Fixture 'frontend/pnpm-lock.yaml' 'new dependency'
Assert-True ($first -ne (Get-ServerInputDigest $fixture $fakeJava)) 'dependency lock invalidates build'
$null = Put-Fixture 'frontend/pnpm-lock.yaml' 'lock'
$null = Put-Fixture 'config/application.yaml' 'port: 8081'
Assert-True ($first -ne (Get-ServerInputDigest $fixture $fakeJava)) 'configuration invalidates prepared resources'
$null = Put-Fixture 'config/application.yaml' 'port: 8080'
$class = Put-Fixture 'backend/server/target/classes/com/minipaintdex/server/MiniPaintDexServer.class' 'compiled'
$null = Put-Fixture 'backend/domain/target/classes/Example.class' 'compiled'
$null = Put-Fixture 'backend/server/target/classes/static/index.html' '<div id="root"></div>'
$dependency = Put-Fixture 'dependencies/library.jar' 'dependency'
$null = Put-Fixture 'backend/server/target/runtime-classpath.txt' $dependency
$manifest = [pscustomobject]@{inputDigest=$first;outputDigest=(Get-ServerOutputDigest $fixture)}
Assert-True (Test-ServerBuildFresh $fixture $first $manifest) 'matching inputs and outputs are reusable'
$null = Put-Fixture 'dependencies/library.jar' 'corrupted'
Assert-True (-not (Test-ServerBuildFresh $fixture $first $manifest)) 'dependency replacement invalidates output'
$null = Put-Fixture 'dependencies/library.jar' 'dependency'
Remove-Item -LiteralPath $class
Assert-True (-not (Test-ServerBuildFresh $fixture $first $manifest)) 'missing compiled entry point invalidates build'
$null = Put-Fixture 'backend/server/target/classes/com/minipaintdex/server/MiniPaintDexServer.class' 'compiled'
$lockDirectory = Join-Path $fixture 'runtime'
$firstLock = Enter-ServerLock $lockDirectory
try {
    $rejected = $false
    try { $secondLock = Enter-ServerLock $lockDirectory; $secondLock.Dispose() } catch { $rejected = $true }
    Assert-True $rejected 'concurrent launcher rejected'
} finally { $firstLock.Dispose() }
$nextLock = Enter-ServerLock $lockDirectory
$nextLock.Dispose()
Assert-True $true 'lock released after operation'
$state = [pscustomobject]@{processId=$PID;startTimeTicks='0';javaPath='not-java'}
Assert-True ($null -eq (Get-OwnedServerProcess $state)) 'PID reuse is not treated as the owned server'
$state.javaPath = (Get-Process -Id $PID).Path
Assert-True ($null -eq (Get-OwnedServerProcess $state)) 'same executable with a different start time is not the owned process'

# Mock only HTTP transport; exercise real identity, readiness and SPA checks.
function Invoke-ServerHttp([string]$Url) {
    if ($Url -like '*/info') { return '{"localServer":{"instance":"owned","buildFingerprint":"build"}}' }
    if ($Url -like '*readiness') { return '{"status":"UP"}' }
    if ($Url.EndsWith('/')) { return '<div id="root"></div><script src="/assets/main.js"></script>' }
    return '{}'
}
$state = [pscustomobject]@{url='http://127.0.0.1:8080';instance='owned';buildFingerprint='build'}
$checks = @(Test-ServerHealth $state -Full)
Assert-True ($checks.Count -eq 7 -and -not @($checks | Where-Object {-not $_.ok}).Count) 'seven startup checks pass'
Assert-True (@($checks | Where-Object { $null -eq $_.elapsedMilliseconds -or $_.elapsedMilliseconds -lt 0 }).Count -eq 0) 'each endpoint has a nonnegative measured duration'
$state.instance = 'another-instance'
Assert-True (@(Test-ServerHealth $state | Where-Object {-not $_.ok}).Count -eq 1) 'HTTP 200 from wrong instance is rejected'
function Invoke-ServerHttp([string]$Url) { throw 'connection refused' }
Assert-True (@(Test-ServerHealth $state | Where-Object {-not $_.ok}).Count -eq 2) 'unavailable server reported, not accepted'

$manifestPath = Join-Path $lockDirectory 'build.json'
Write-ServerJson $manifestPath $manifest
$null = Put-Fixture 'mvnw.cmd' "@exit /b 1`r`n"
$rejected = $false
try { $null = Invoke-ServerCompile $fixture $fakeJava $manifestPath 'unused' } catch { $rejected = $true }
Assert-True $rejected 'failed compilation propagates failure'
Assert-True (-not (Read-ServerJson $manifestPath).inputDigest) 'failed compilation invalidates prior success'
Assert-True (-not (Test-Path -LiteralPath $class)) 'stale classes removed before compilation'

# Exercise the real public parameter contract without running Java or touching a server.
$parseErrors = $null
$tokens = $null
$launcherAst = [Management.Automation.Language.Parser]::ParseFile((Join-Path $repository 'scripts/minipaintdex.ps1'), [ref]$tokens, [ref]$parseErrors)
Assert-True (-not $parseErrors) 'launcher parses'
$binding = [scriptblock]::Create($launcherAst.ParamBlock.Extent.Text + "`n" + '[pscustomobject]@{command=$Command;port=$Port;arguments=$Arguments}')
$bound = & $binding cli --help
Assert-True ($bound.command -eq 'cli' -and $bound.arguments[0] -eq '--help' -and $bound.port -eq 8080) 'CLI arguments are forwarded rather than bound to the port'
$bound = & $binding start -Port 8181
Assert-True ($bound.command -eq 'start' -and $bound.port -eq 8181) 'named launcher port is supported'
function Get-Process { [pscustomobject]@{ProcessName='java';HasExited=$true;StartTime=$null;Path=$null} }
Assert-True ($null -eq (Get-OwnedServerProcess ([pscustomobject]@{processId=42;javaPath='java.exe';startTimeTicks='1'}))) 'process exiting during shutdown polling is considered stopped'
Write-Output "Local server launcher: $script:passed assertions passed. Fixtures: $fixture"
