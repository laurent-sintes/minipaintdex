# Reusable validation and commit delivery

This is developer infrastructure, with no Market/Workshop command, aggregate or event changes.
Maven remains the sole product verification entry point. A receipt proves a successful root
`clean verify`; it does not replace human diff review or task-specific deep acceptance controls.

## Commands

- `scripts/minipaintdex.ps1 verify`: reuse an identical successful validation, otherwise run
  the full build. Only on a cache miss, stop the managed server and restore it after success.
  A failed build leaves it stopped with diagnostics; never serve partially replaced classes.
- `verification-status`: inspect worktree evidence without Maven or process control.
- `build`: force a full `clean verify`, with the managed server already stopped.
- `prepare-commit`: inspect the exact Git index and reuse matching evidence. On a miss,
  materialize only staged files under `.local-build/verification/snapshots/<id>/` and run
  root Maven there. The working directory, index and running server remain untouched.
- `commit -Message "..."`: only on explicit user authorization. Require a current index
  receipt and a nonempty, whitespace-checked staged diff; run Git without staging, rebuilding,
  pushing or disabling hooks. Check the committed tree afterward. If a hook/concurrent editor
  changes it, report that a commit exists but differs; never silently reset or push it.

The PowerShell entry point serializes these commands with the existing checkout launcher lock.
`verification.py` is its internal implementation, not an independent release/test workflow.
Do not run concurrent Git/index editing operations during preparation or commit. Normal Git
locking still applies; the checkout launcher lock does not lock external editors.

## Receipt identity

The source identity includes all tracked files and nonignored untracked files: all Java modules
(including CLI), tests, Python tools/resources, frontend, data, configuration, scripts, instructions
and documentation. Additions, deletions, renames and content changes invalidate it. Hashes are
Git blob identities with Git's clean/EOL normalization, not modification dates. An index identity
uses its full stage-zero file table and modes, not only the staged diff. Conflicts, submodules and
symbolic links fail closed in this initial local Windows workflow.

The context separately fingerprints the validator, Java/Python executables, JDK release,
installed Python package names/versions, resolved Maven JAR/POM contents, frontend tool files,
photo models, Maven user settings/toolchains, content-affecting Git configuration, relevant build/runtime environment,
and local data/workshop media that integration tests can observe. Environment values are hashed,
not written into receipts. Receipts contain digests, outcome, duration and log path, not credentials.
Dependency or tool installation during a build changes the context: no reusable proof is granted
until a subsequent stable verification succeeds. Changes to arbitrary external configuration or
in-place Python package files without a version change require an explicit forced `build`.
Shell PATH strings are represented by the resolved executable paths/contents. Environment key
casing, browser-agent variables and npm offline cache preference do not invalidate a proof;
these permissions/settings remain unchanged during execution. No network restriction is bypassed.

This deliberately conservative scope may reject reuse for unrelated source or local data changes.
Ignored generated outputs are not validation inputs. Deleting classes invalidates the independent
runtime manifest but does not erase historical evidence that a source snapshot passed its tests.
The server's `build.json` alone is never commit evidence. Partial test runs do not issue receipts.

Before a full run, the matching receipt is marked running; failure or input/context drift marks
it failed. Only a successful, stable `clean verify` grants reuse. Corrupt/incomplete receipts are
cache misses. Receipts are a trusted local convenience, not a signed CI/security attestation.

## Partial commits and timing

A worktree proof is reusable for a staged tree only when the complete source identities match.
Unstaged fixes cannot validate an older staged version of the same file. An isolated index run
uses staged reference data, its own node_modules and generated outputs and may share downloaded Maven/frontend tools
and the content-addressed pnpm download store, never
the live classpath. Snapshot directories and logs are retained for diagnosis under `.local-build`.
Existing integration tests also read the local owner's ledger. Ignored files under `data` and
`media/workshop` (excluding lock files) are copied as a fingerprinted test context into the snapshot;
they are never staged, never shared for writing and never confused with source identity. Unstaged
tracked files and nonignored untracked files remain excluded. A receipt is therefore local to the
recorded test context, not a claim of reproducibility from Git alone on an empty workshop.

JSON results include `inspectionSeconds`, `validationSeconds`, `serverSeconds`, `gitSeconds`
where applicable, and `elapsedSeconds`. A reused proof reports zero validation time; commit
records Git's actual duration, including hooks. The last result is saved to
`.local-build/verification/last-operation.json`; full Maven output is in `runs/<id>.log`.
Agent conversation time, human approval waits and manual diff review are outside these timers.
There is no separate Python preflight test run before the same tests run in root Maven.
