# Infrastructure only: no catalog or workshop mutation. Dot-sourced by the launcher and tests.
function Get-ContentDigest([string[]]$Paths, [string[]]$Values = @()) {
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        # Culture-sensitive Sort-Object orders punctuation differently on .NET Framework/Core.
        $unique = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($path in $Paths) { $null = $unique.Add($path) }
        $ordered = [string[]]::new($unique.Count)
        $unique.CopyTo($ordered)
        [Array]::Sort($ordered, [StringComparer]::Ordinal)
        $entries = @($ordered | ForEach-Object {
            if (Test-Path -LiteralPath $_ -PathType Leaf) {
                $stream = [System.IO.File]::OpenRead($_)
                try { $digest = [BitConverter]::ToString($hasher.ComputeHash($stream)) } finally { $stream.Dispose() }
                "$_=$digest"
            } else { "$_=MISSING" }
        }) + $Values
        return [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes(($entries -join "`n")))).Replace('-', '').ToLowerInvariant()
    } finally { $hasher.Dispose() }
}

function Get-TreeFiles([string]$Directory) {
    if (Test-Path -LiteralPath $Directory) {
        Get-ChildItem -LiteralPath $Directory -File -Recurse |
            Where-Object { $_.FullName -notmatch '[\\/]__pycache__[\\/]' -and $_.Extension -notin @('.pyc', '.pyo') } |
            Select-Object -ExpandProperty FullName
    }
}

function Get-ServerModules([string]$Root) {
    [xml]$pom = Get-Content -LiteralPath (Join-Path $Root 'pom.xml') -Raw
    # The reactor owns module discovery. The standalone CLI is not on the server classpath.
    @($pom.project.modules.module | Where-Object { $_ -ne 'backend/cli' })
}

function Get-ServerInputDigest([string]$Root, [string]$JavaPath) {
    $paths = @((Join-Path $Root 'pom.xml'), (Join-Path $Root 'mvnw.cmd'), $JavaPath,
        (Join-Path (Split-Path (Split-Path $JavaPath)) 'release'))
    foreach ($module in Get-ServerModules $Root) {
        $paths += Join-Path $Root "$module/pom.xml"
        $paths += @(Get-TreeFiles (Join-Path $Root "$module/src/main"))
    }
    foreach ($folder in @('config', 'docs', '.mvn', 'scripts', 'frontend/src', 'frontend/public')) {
        $paths += @(Get-TreeFiles (Join-Path $Root $folder))
    }
    $paths += @(Get-ChildItem -LiteralPath (Join-Path $Root 'frontend') -File |
        Where-Object { $_.Name -notlike '*.tsbuildinfo' } | Select-Object -ExpandProperty FullName)
    Get-ContentDigest $paths
}

function Get-ServerClasspath([string]$Root) {
    $dependencyFile = Join-Path $Root 'backend/server/target/runtime-classpath.txt'
    if (-not (Test-Path -LiteralPath $dependencyFile)) { throw 'Runtime classpath missing; compilation required.' }
    $entries = @(Get-ServerModules $Root | ForEach-Object { Join-Path $Root "$_/target/classes" })
    $entries += (Get-Content -LiteralPath $dependencyFile -Raw).Trim().Split([IO.Path]::PathSeparator)
    return $entries
}

function Get-ServerOutputDigest([string]$Root) {
    $paths = @((Join-Path $Root 'backend/server/target/runtime-classpath.txt'),
        (Join-Path $Root 'backend/server/target/classes/com/minipaintdex/server/MiniPaintDexServer.class'),
        (Join-Path $Root 'backend/server/target/classes/static/index.html'))
    foreach ($entry in Get-ServerClasspath $Root) {
        if (Test-Path -LiteralPath $entry -PathType Container) { $paths += @(Get-TreeFiles $entry) }
        else { $paths += $entry }
    }
    Get-ContentDigest $paths
}

function Read-ServerJson([string]$Path) {
    if (Test-Path -LiteralPath $Path) { [IO.File]::ReadAllText($Path) | ConvertFrom-Json }
}

function Write-ServerJson([string]$Path, $Value) {
    $temporary = "$Path.$([guid]::NewGuid().ToString('N')).tmp"
    [IO.File]::WriteAllText($temporary, ($Value | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}

function Test-ServerBuildFresh([string]$Root, [string]$InputDigest, $Manifest) {
    if (-not $Manifest -or $Manifest.inputDigest -ne $InputDigest) { return $false }
    try {
        foreach ($required in @('backend/server/target/classes/com/minipaintdex/server/MiniPaintDexServer.class',
                'backend/server/target/classes/static/index.html')) {
            if (-not (Test-Path -LiteralPath (Join-Path $Root $required))) { return $false }
        }
        foreach ($entry in Get-ServerClasspath $Root) { if (-not (Test-Path -LiteralPath $entry)) { return $false } }
        return $Manifest.outputDigest -eq (Get-ServerOutputDigest $Root)
    } catch { return $false }
}

function Enter-ServerLock([string]$Directory) {
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    try { return [IO.File]::Open((Join-Path $Directory 'launcher.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'Another launcher/build operation is running for this checkout.' }
}

function Get-OwnedServerProcess($State) {
    if (-not $State) { return $null }
    $process = Get-Process -Id $State.processId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($process.HasExited) { return $null }
    # A recycled PID is not our server; never inspect/stop its unrelated owner.
    if ($process.ProcessName -ne [IO.Path]::GetFileNameWithoutExtension($State.javaPath)) { return $null }
    if (-not $process.StartTime -or -not $process.Path) {
        if ($process.HasExited -or -not (Get-Process -Id $State.processId -ErrorAction SilentlyContinue)) { return $null }
        throw 'Cannot verify process identity; no shutdown attempted.'
    }
    if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne $State.startTimeTicks -or $process.Path -ne $State.javaPath) {
        return $null
    }
    return $process
}

function Test-ServerPort([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $connection = $client.ConnectAsync('127.0.0.1', $Port)
        return $connection.Wait(500) -and $client.Connected
    } catch { return $false } finally { $client.Dispose() }
}

function Invoke-ServerHttp([string]$Url) {
    # No ambient proxy and no redirects: checks must reach this exact local instance.
    $request = [Net.HttpWebRequest]::Create($Url)
    $request.Proxy = $null
    $request.AllowAutoRedirect = $false
    $request.Timeout = 2000
    $request.ReadWriteTimeout = 2000
    $response = $request.GetResponse()
    try {
        $reader = [IO.StreamReader]::new($response.GetResponseStream())
        try { $body = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ([int]$response.StatusCode -ne 200) { throw "HTTP $([int]$response.StatusCode) at $Url" }
        return $body
    } finally { $response.Dispose() }
}

function Test-ServerHealth($State, [switch]$Full) {
    $checks = @()
    foreach ($route in @('/actuator/info', '/actuator/health/readiness') + $(if ($Full) {
                @('/api/v1/site/config', '/api/v1/dashboard', '/api/v1/workshop', '/api/v1/about', '/')
            } else { @() })) {
        $checkTimer = [Diagnostics.Stopwatch]::StartNew()
        try {
            $body = Invoke-ServerHttp ($State.url + $route)
            if ($route -eq '/actuator/info') {
                $info = $body | ConvertFrom-Json
                if ($info.localServer.instance -ne $State.instance -or $info.localServer.buildFingerprint -ne $State.buildFingerprint) {
                    throw 'Running instance/build identity mismatch.'
                }
            } elseif ($route -eq '/actuator/health/readiness') {
                if (($body | ConvertFrom-Json).status -ne 'UP') { throw 'Readiness is not UP.' }
            } elseif ($route -eq '/') {
                if ($body -notmatch '<div id="root"' -or $body -notmatch '/assets/') { throw 'SPA document missing.' }
            } else {
                $null = $body | ConvertFrom-Json
            }
            $checks += [pscustomobject]@{route=$route;ok=$true;error=$null;elapsedMilliseconds=[math]::Round($checkTimer.Elapsed.TotalMilliseconds,2)}
        } catch { $checks += [pscustomobject]@{route=$route;ok=$false;error=$_.Exception.Message;elapsedMilliseconds=[math]::Round($checkTimer.Elapsed.TotalMilliseconds,2)} }
    }
    return $checks
}

function Stop-OwnedServer($State, [string]$JavaPath, [string]$Root, [int]$TimeoutSeconds) {
    $process = Get-OwnedServerProcess $State
    if (-not $process) { return }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    # The helper verifies the random token inside the JVM and Spring before closing the context.
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $helperOutput = & $JavaPath --add-modules jdk.attach (Join-Path $Root 'scripts/LocalServerControl.java') $State.processId $State.instance $TimeoutSeconds 2>&1
        $helperExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    while ((Get-OwnedServerProcess $State) -and $timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) { Start-Sleep -Milliseconds 200 }
    if (Get-OwnedServerProcess $State) {
        throw "Graceful shutdown failed/timed out (helper exit $helperExit). Process preserved; inspect $($State.stderr). $helperOutput"
    }
}

function Remove-ServerClasses([string]$Root) {
    foreach ($module in Get-ServerModules $Root) {
        $target = [IO.Path]::GetFullPath((Join-Path $Root "$module/target/classes"))
        $boundary = [IO.Path]::GetFullPath((Join-Path $Root 'backend')) + [IO.Path]::DirectorySeparatorChar
        if (-not $target.StartsWith($boundary, [StringComparison]::OrdinalIgnoreCase) -or
                -not $target.EndsWith([IO.Path]::Combine('target', 'classes'))) { throw "Unsafe generated-output path: $target" }
        if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    }
}

function Invoke-ServerCompile([string]$Root, [string]$JavaPath, [string]$ManifestPath, [string]$MavenRepository) {
    $before = Get-ServerInputDigest $Root $JavaPath
    # Invalidate BEFORE compilation; partial/failed builds are never reusable.
    Write-ServerJson $ManifestPath @{inputDigest=$null;outputDigest=$null;status='building'}
    Remove-ServerClasses $Root
    $compileLog = Join-Path (Split-Path $ManifestPath) 'compile.log'
    & (Join-Path $Root 'mvnw.cmd') --no-transfer-progress "-Dmaven.repo.local=$MavenRepository" -pl backend/server -am process-classes *> $compileLog
    if ($LASTEXITCODE -ne 0) { throw "Local compilation failed. No server was started; see $compileLog" }
    if ((Get-ServerInputDigest $Root $JavaPath) -ne $before) { throw 'Build inputs changed during compilation; retry start.' }
    $manifest = @{inputDigest=$before;outputDigest=(Get-ServerOutputDigest $Root);compiledAt=[DateTime]::UtcNow.ToString('o');status='compiled'}
    Write-ServerJson $ManifestPath $manifest
    return [pscustomobject]$manifest
}

function Get-LaunchConfigurationDigest([string]$JavaPath, [int]$Port, [string[]]$JvmOptions) {
    $environment = @(Get-ChildItem Env: | Where-Object { $_.Name -match '^(SPRING_|SERVER_|MANAGEMENT_|MINIPAINTDEX_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)' } |
        Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" })
    Get-ContentDigest @() (@($JavaPath, "$Port") + $JvmOptions + $environment)
}

function Invoke-LocalServer([string]$Action, [string]$Root, [string]$JavaPath, [string]$MavenRepository,
        [string[]]$JvmOptions, [int]$Port = 8080, [int]$TimeoutSeconds = 60) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $directory = Join-Path $Root '.local-build/server'
    $lock = Enter-ServerLock $directory
    try {
        $statePath = Join-Path $directory 'process.json'
        $manifestPath = Join-Path $directory 'build.json'
        $lastLaunchPath = Join-Path $directory 'last-launch.json'
        $state = Read-ServerJson $statePath
        $process = Get-OwnedServerProcess $state
        if ($Action -eq 'stop') {
            if (-not $process -and (Test-ServerPort $Port)) { throw "Port $Port belongs to an unmanaged process. No process was stopped." }
            Stop-OwnedServer $state $JavaPath $Root $TimeoutSeconds
            Write-ServerJson $statePath $null
            return [pscustomobject]@{status='stopped';elapsedSeconds=[math]::Round($timer.Elapsed.TotalSeconds,2)}
        }
        $inputs = Get-ServerInputDigest $Root $JavaPath
        $manifest = Read-ServerJson $manifestPath
        $fresh = Test-ServerBuildFresh $Root $inputs $manifest
        $configuration = Get-LaunchConfigurationDigest $JavaPath $Port $JvmOptions
        if ($Action -in @('status', 'doctor')) {
            $checks = @()
            if ($process) { $checks = @(Test-ServerHealth $state -Full:($Action -eq 'doctor')) }
            $status = if ($process) { if (@($checks | Where-Object { -not $_.ok }).Count) { 'unhealthy' } else { 'running' } }
                elseif (Test-ServerPort $Port) { 'unmanaged-port' } else { 'stopped' }
            return [pscustomobject]@{status=$status;buildFresh=$fresh;configurationFresh=($process -and $state.configurationDigest -eq $configuration);
                processId=$(if ($process) {$state.processId} else {$null});url=$(if ($state) {$state.url} else {"http://127.0.0.1:$Port"});
                checks=$checks;stdout=$state.stdout;stderr=$state.stderr;lastLaunch=(Read-ServerJson $lastLaunchPath);elapsedSeconds=[math]::Round($timer.Elapsed.TotalSeconds,2)}
        }
        if ($process -and $Action -eq 'start') {
            if (-not $fresh -or $state.buildFingerprint -ne $manifest.outputDigest -or $state.configurationDigest -ne $configuration) {
                throw 'Running server is stale. Use restart to replace it; start does not interrupt a running instance.'
            }
            $testTimer = [Diagnostics.Stopwatch]::StartNew()
            $checks = @(Test-ServerHealth $state -Full)
            if (@($checks | Where-Object { -not $_.ok }).Count) { throw "Running server is unhealthy. Use doctor; logs: $($state.stderr)" }
            return [pscustomobject]@{status='already-running';processId=$state.processId;url=$state.url;compiled=$false;checks=$checks;startupSeconds=0;
                postStartTestSeconds=[math]::Round($testTimer.Elapsed.TotalSeconds,3);elapsedSeconds=[math]::Round($timer.Elapsed.TotalSeconds,2)}
        }
        $shutdownTimer = [Diagnostics.Stopwatch]::StartNew()
        if ($process) {
            Stop-OwnedServer $state $JavaPath $Root $TimeoutSeconds
            Write-ServerJson $statePath $null
        }
        $shutdownSeconds = $shutdownTimer.Elapsed.TotalSeconds
        if (Test-ServerPort $Port) { throw "Port $Port belongs to an unmanaged process. No process was stopped." }
        if (-not $fresh) { $manifest = Invoke-ServerCompile $Root $JavaPath $manifestPath $MavenRepository }
        if ((Get-ServerInputDigest $Root $JavaPath) -ne $manifest.inputDigest) { throw 'Sources changed before launch; retry.' }
        $instance = [guid]::NewGuid().ToString('N')
        $runs = Join-Path $directory 'runs'
        New-Item -ItemType Directory -Path $runs -Force | Out-Null
        $stdout = Join-Path $runs "$instance.stdout.log"
        $stderr = Join-Path $runs "$instance.stderr.log"
        $classpath = (Get-ServerClasspath $Root) -join [IO.Path]::PathSeparator
        $javaArguments = @($JvmOptions) + @('-Djava.rmi.server.hostname=127.0.0.1', '-Dcom.sun.management.jmxremote.local.only=true', "-Dminipaintdex.launch.instance=$instance", '-cp', $classpath,
            'com.minipaintdex.server.MiniPaintDexServer', "--minipaintdex.root=$Root", '--server.address=127.0.0.1', "--server.port=$Port",
            '--spring.application.admin.enabled=true', '--management.info.env.enabled=true', '--management.endpoints.web.exposure.include=health,info',
            "--info.localServer.instance=$instance", "--info.localServer.buildFingerprint=$($manifest.outputDigest)")
        # Start-Process joins arguments on Windows; quote each Java argument, not a shell command.
        if (@($javaArguments | Where-Object { $_ -match '["\r\n]' }).Count) { throw 'Unsupported quote/newline in launch arguments.' }
        $quoted = @($javaArguments | ForEach-Object { '"' + $_ + '"' })
        $preparationSeconds = $timer.Elapsed.TotalSeconds - $shutdownSeconds
        $startup = [Diagnostics.Stopwatch]::StartNew()
        $process = Start-Process -FilePath $JavaPath -ArgumentList $quoted -WorkingDirectory $Root -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $state = [pscustomobject]@{processId=$process.Id;startTimeTicks=$process.StartTime.ToUniversalTime().Ticks.ToString();javaPath=$JavaPath;
            instance=$instance;buildFingerprint=$manifest.outputDigest;configurationDigest=$configuration;url="http://127.0.0.1:$Port";stdout=$stdout;stderr=$stderr}
        Write-ServerJson $statePath $state
        $lastChecks = @()
        do {
            if (-not (Get-OwnedServerProcess $state)) { throw "Server exited during startup. Logs: $stderr ; $stdout" }
            $lastChecks = @(Test-ServerHealth $state)
            if (-not @($lastChecks | Where-Object { -not $_.ok }).Count) { break }
            Start-Sleep -Milliseconds 300
        } while ($startup.Elapsed.TotalSeconds -lt $TimeoutSeconds)
        $startup.Stop()
        $testTimer = [Diagnostics.Stopwatch]::StartNew()
        $checks = @(Test-ServerHealth $state -Full)
        $testTimer.Stop()
        $result = [pscustomobject]@{status='running';processId=$state.processId;url=$state.url;compiled=(-not $fresh);buildFingerprint=$manifest.outputDigest;
            preparationSeconds=[math]::Round($preparationSeconds,3);shutdownSeconds=[math]::Round($shutdownSeconds,3);
            startupSeconds=[math]::Round($startup.Elapsed.TotalSeconds,3);postStartTestSeconds=[math]::Round($testTimer.Elapsed.TotalSeconds,3);
            elapsedSeconds=[math]::Round($timer.Elapsed.TotalSeconds,3);checks=$checks;stdout=$stdout;stderr=$stderr;compileLog=(Join-Path $directory 'compile.log')}
        if (@($checks | Where-Object { -not $_.ok }).Count) {
            $result.status = 'unhealthy'
            Write-ServerJson $lastLaunchPath $result
            $failed = ($checks | Where-Object { -not $_.ok } | ForEach-Object { "$($_.route): $($_.error)" }) -join '; '
            throw "Startup checks failed: $failed. Instance retained for doctor/stop. Logs: $stderr ; $stdout"
        }
        Write-ServerJson $lastLaunchPath $result
        return $result
    } finally { $lock.Dispose() }
}
