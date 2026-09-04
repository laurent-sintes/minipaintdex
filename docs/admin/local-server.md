# Local server lifecycle

The PowerShell launcher owns development process control, not Market or Workshop behavior.
No aggregate, command, domain event or REST contract changes are needed. Spring Boot still
serves the SPA and REST API from the same process on loopback. Production remains one executable JAR.

## Commands

`scripts/minipaintdex.ps1 start|restart|stop|status|doctor` returns JSON with deterministic
exit codes (0 success, 1 error). `doctor` also returns 1 for a stopped, unhealthy or stale
instance. `status` reports observed state without restarting, recompiling or attaching JMX.
`start` is idempotent for a healthy current instance; use `restart` to replace a stale one.
`-Port` defaults to 8080 and `-TimeoutSeconds` to 60. One managed instance exists per checkout.
The recorded instance, not an arbitrary PID found on the requested port, is the target of `stop`.

Spring settings use the usual configuration files/environment; the managed launcher fixes
the root to this checkout, the bind address to 127.0.0.1, the chosen port and its local
management identity. It does not install the optional photo model. A missing required model
or invalid catalog is a real startup failure, not permission to bypass validation.

## Compilation and freshness

Maven is the only build entry point. `process-classes` on the server reactor prepares frontend
assets, Java classes, configuration, embedded documentation and the runtime dependency classpath.
It neither packages the executable JAR nor runs the full verification suites. Frontend generation
is bound before resource copying; validation stays in `verify`. The Node/pnpm installation cache
lives in `.tools/frontend`, outside Maven clean outputs.

SHA-256 fingerprints include runtime sources (including untracked additions and deletions), POMs,
frontend sources and lockfile, config, embedded documentation, launcher scripts and the JDK.
Runtime class/resource files and resolved dependency JAR contents have a separate fingerprint.
Data, media, Git HEAD and backend test changes do not force a runtime compilation. Spring validates
the active data on startup. Relevant Spring/JVM environment and launch options also determine
whether an already-running instance is current. External configuration imported from locations
outside the checkout is not recursively fingerprinted: explicitly restart after changing it.

Missing or changed outputs trigger preparation. A stale preparation conservatively clears only
the known server modules' generated `target/classes` directories before compiling, so removed Java
sources/resources cannot survive. It does not delete data, media, dependency caches or the full workspace.
The manifest is invalidated before compilation and published only after success and a second input
check. No fallback to an old JAR is permitted. This is development readiness, not a claim that tests passed.

`scripts/minipaintdex.ps1 build` runs `clean verify`, including Java, frontend, Python and Windows
launcher tests, and records a verified manifest. Stop the managed server first. The wrapper serializes
build/test/lifecycle commands with one checkout-local lock; direct Maven invocations must obey the
same stopped-server rule because classes on a live classpath must not be replaced.

## Ownership, shutdown and failures

`.local-build/server/process.json` records PID, process start time, Java executable, random
instance identity, build fingerprint and log paths. PID reuse is rejected. The controller never
kills an unknown port owner, never uses forced termination, and never adopts an unmanaged instance.

Shutdown uses the standard Spring application admin MBean through the JDK's same-user local Attach
and JMX facilities. No remote JMX port or HTTP shutdown endpoint is enabled. The helper verifies
the random instance token in both JVM properties and Spring before requesting context closure.
Spring's graceful HTTP shutdown, event-pipeline drain and resource closure remain authoritative.
A connection closing during shutdown is accepted only once the recorded process has actually exited.
If local Attach is forbidden, the command fails without a kill fallback. An old manually started
server must be stopped through its known launching session before the first managed start.

Failed startup retains process identity and logs for `doctor` and `stop`. Retrying `start` does not
silently replace an unhealthy running process. Operators can inspect the logs and explicitly restart.
The launcher performs bounded readiness polling, validates `/actuator/info` instance/build identity,
then checks readiness, site configuration, dashboard, workshop, about and the SPA document.
These are smoke tests, not a browser interaction test or a replacement for `verify`.

## Timing and logs

Each successful launch records `.local-build/server/last-launch.json`:

- `preparationSeconds`: freshness checks, optional compilation and launch preparation;
- `shutdownSeconds`: graceful shutdown of the previous instance, if present;
- `startupSeconds`: process creation through identity-verified readiness (includes polling overhead);
- `postStartTestSeconds`: the subsequent HTTP smoke-test batch;
- `checks[].elapsedMilliseconds`: one duration per endpoint, including failures;
- `elapsedSeconds`: total launcher duration.

`status` and `doctor` return the saved launch report alongside current checks. An idempotent `start`
reports zero startup time and the duration of its fresh checks. Maven preparation output is saved to
`compile.log`; each instance has separate stdout/stderr logs under `runs/`. Logs are retained for
diagnosis and are never written into application data or returned by public business APIs.
