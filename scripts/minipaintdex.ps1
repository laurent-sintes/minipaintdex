param(
    [ValidateSet('build', 'test', 'server', 'cli')]
    [string]$Command = 'build',

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
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
$serverJar = Join-Path $projectRoot 'backend\server\target\minipaintdex-server-0.2.0-SNAPSHOT.jar'
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
    return @("-Xms$initial", "-Xmx$maximum", '-XX:+ExitOnOutOfMemoryError')
}

function Invoke-Build {
    & $mavenWrapper "-Dmaven.repo.local=$mavenRepository" clean verify
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Push-Location $projectRoot
try {
    switch ($Command) {
        'build' {
            Invoke-Build
        }
        'test' {
            & $mavenWrapper "-Dmaven.repo.local=$mavenRepository" '-Dfrontend.skip=true' test
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        'server' {
            if (-not (Test-Path -LiteralPath $serverJar)) { Invoke-Build }
            $serverArguments = @($Arguments)
            $hasConfiguredRoot = $env:MINIPAINTDEX_ROOT -or ($serverArguments | Where-Object { $_ -like '--minipaintdex.root=*' })
            if (-not $hasConfiguredRoot) {
                $serverArguments = @("--minipaintdex.root=$projectRoot") + $serverArguments
            }
            $serverJvmOptions = Get-JvmMemoryOptions 'server' '128m' '512m'
            & $java @serverJvmOptions -jar $serverJar @serverArguments
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
        'cli' {
            if (-not (Test-Path -LiteralPath $cliJar)) { Invoke-Build }
            $cliJvmOptions = Get-JvmMemoryOptions 'cli' '32m' '192m'
            & $java @cliJvmOptions -jar $cliJar @Arguments
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
    }
} finally {
    Pop-Location
}
