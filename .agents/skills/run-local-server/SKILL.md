---
name: run-local-server
description: Build, start, and smoke-test the local MiniPaintDex Spring Boot application. Use when the user asks to launch, restart, run, or test the local site; use the self-contained server by default, not the Vite development server.
---

# Run the local server

Run the current repository state as the self-contained Spring Boot application and leave the process available for the user to test.

## Workflow

1. Resolve the Git root and work from it. Respect the repository `AGENTS.md`.
2. Check whether `127.0.0.1:8080` is already occupied. Reuse a healthy MiniPaintDex process only when the user did not request a restart and it is known to serve the current build. Never terminate an unknown process merely because it owns the port.
3. Run `.\scripts\minipaintdex.ps1 build` before starting. If the build fails, stop and report the failure; do not fall back to a stale JAR.
4. Start `.\scripts\minipaintdex.ps1 server` in a persistent PTY session. Wait until Spring reports that the application started or returns a concrete failure.
5. Smoke-test all three surfaces:
   - `GET http://127.0.0.1:8080/api/v1/health` must report the MiniPaintDex service as healthy.
   - `GET http://127.0.0.1:8080/api/v1/bootstrap` must load repository data successfully; this catches an invalid project-root configuration that the health endpoint alone cannot detect.
   - `GET http://127.0.0.1:8080/` must return the packaged frontend.
6. Leave the server session running and report the test URL. Include any warning that affects actual use.

Use the Vite development server on port `5173` only when the user explicitly asks for frontend hot reload. The default user-facing server is the Spring Boot application on port `8080`.

The repository script supplies the project root through standard Spring configuration. Do not hard-code a machine-specific path, install global build tools, write domain data directly, commit, or push as part of this skill.

When stopping or restarting, interrupt the PTY session created for this task. If that session is unavailable, identify the exact MiniPaintDex JAR process before stopping it; otherwise ask the user to resolve the port conflict.
