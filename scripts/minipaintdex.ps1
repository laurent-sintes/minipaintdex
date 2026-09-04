[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Position = 0)]
    [ValidateSet('build', 'test', 'start', 'restart', 'stop', 'status', 'doctor', 'cli')]
    [string]$Command = 'build',

    [ValidateRange(1024, 65535)]
    [int]$Port = 8080,

    [ValidateRange(5, 300)]
    [int]$TimeoutSeconds = 60,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'local-server.ps1')
$portableJdkRoot = Join-Path $projectRoot '.tools\jdk25'
$portableJdk = if (Test-Path -LiteralPath $portableJdkRoot) {
    Get-ChildItem -LiteralPath $portableJdkRoot -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

if ($portableJdk) {
    $env:JAVA_HOME = $portableJdk.FullName
}

$java = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    (Get-Command java -ErrorAction SilentlyContinue).Source
}

if (-not $java) {
    throw 'Java 25 is required. Place a portable JDK under .tools\jdk25 or define JAVA_HOME.'
}

$mavenRepository = (Join-Path $projectRoot '.tools\m2').Replace('\', '/')
$mavenWrapper = Join-Path $projectRoot 'mvnw.cmd'
$cliJar = Join-Path $projectRoot 'backend\cli\target\minipaintdex-cli-0.2.0-SNAPSHOT.jar'

function Get-JvmMemoryOptions(
    [string]$Profile,
    [string]$DefaultInitial,
    [string]$DefaultMaximum
) {
    $prefix = "MINIPAINTDEX_$($Profile.ToUpperInvariant())"
    $initial = [Environment]::GetEnvironmentVariable("${prefix}_XMS")
    $maximum = [Environment]::GetEnvironmentVariable("${prefix}_XMX")
    if (-not $initial) { $initial = $DefaultInitial }
    if (-not $maximum) { $maximum = $DefaultMaximum }
    foreach ($value in @($initial, $maximum)) {
        if ($value -notmatch '^\d+[kKmMgG]$') {
            throw "Invalid JVM memory size '$value'. Use a value such as 128m or 1g."
        }
    }
    return @("-Xms$initial", "-Xmx$maximum", '-XX:+ExitOnOutOfMemoryError', '--enable-native-access=ALL-UNNAMED')
}

function Invoke-Build([switch]$TestsOnly) {
    $directory = Join-Path $projectRoot '.local-build/server'
    $buildLock = Enter-ServerLock $directory
    try {
        if (Get-OwnedServerProcess (Read-ServerJson (Join-Path $directory 'process.json'))) {
            throw 'Stop the managed server before a full build: its classpath must not change while running.'
        }
        Write-ServerJson (Join-Path $directory 'build.json') @{inputDigest=$null;outputDigest=$null;status='building'}
        $before = Get-ServerInputDigest $projectRoot $java
        if ($TestsOnly) {
            & $mavenWrapper --no-transfer-progress "-Dmaven.repo.local=$mavenRepository" '-Dfrontend.skip=true' test
            if ($LASTEXITCODE -ne 0) { throw 'Maven tests failed.' }
            return
        }
        & $mavenWrapper --no-transfer-progress "-Dmaven.repo.local=$mavenRepository" clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Full Maven verification failed.' }
        if ((Get-ServerInputDigest $projectRoot $java) -ne $before) { throw 'Inputs changed during verification; build not marked reusable.' }
        Write-ServerJson (Join-Path $directory 'build.json') @{inputDigest=$before;outputDigest=(Get-ServerOutputDigest $projectRoot);status='verified';compiledAt=[DateTime]::UtcNow.ToString('o')}
    } finally { $buildLock.Dispose() }
}

Push-Location $projectRoot
try {
    switch ($Command) {
        'build' {
            Invoke-Build
        }
        'test' {
            Invoke-Build -TestsOnly
        }
        { $_ -in @('start', 'restart', 'stop', 'status', 'doctor') } {
            if ($Arguments.Count) { throw 'Managed lifecycle commands accept -Port and -TimeoutSeconds; use Spring configuration/environment for application settings.' }
            $result = Invoke-LocalServer $Command $projectRoot $java $mavenRepository (Get-JvmMemoryOptions 'server' '128m' '512m') $Port $TimeoutSeconds
            $result | ConvertTo-Json -Depth 10
            if ($Command -eq 'doctor' -and ($result.status -ne 'running' -or -not $result.buildFresh -or -not $result.configurationFresh)) { exit 1 }
        }
        'cli' {
            if (-not (Test-Path -LiteralPath $cliJar)) { Invoke-Build }
            $cliJvmOptions = Get-JvmMemoryOptions 'cli' '64m' '512m'
            & $java @cliJvmOptions -jar $cliJar @Arguments
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
    }
} catch {
    if ($Command -in @('start', 'restart', 'stop', 'status', 'doctor')) {
        [pscustomobject]@{status='error';command=$Command;message=$_.Exception.Message} | ConvertTo-Json
        exit 1
    }
    throw
} finally {
    Pop-Location
}
