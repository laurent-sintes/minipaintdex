"""Local validation receipts and exact-index delivery; Maven remains the test entry point."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone


def git(root, *args, input=None):
    result = subprocess.run(["git", "-C", str(root), *args], input=input, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True).encode()).hexdigest()


def file_hash(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def build_environment():
    prefixes = ("JAVA", "JDK", "_JAVA", "MAVEN", "M2", "SPRING", "SERVER", "MANAGEMENT", "MINIPAINTDEX", "PYTHON", "PNPM", "NPM", "GIT_CONFIG")
    selected = ("CI", "NODE_OPTIONS", "NODE_PATH", "NODE_EXTRA_CA_CERTS")
    # Network availability is an execution permission, not a different tested product.
    # Do not modify these settings; only omit cache-policy-only differences from identity.
    cache_policy = ("NPM_CONFIG_OFFLINE", "NPM_CONFIG_PREFER_OFFLINE")
    return {k.upper(): v for k, v in os.environ.items()
            if (k.upper().startswith(prefixes) or k.upper() in selected) and k.upper() not in cache_policy}


def snapshot(root, scope):
    entries = {}
    for record in git(root, "ls-files", "--stage", "-z").split(b"\0"):
        if not record:
            continue
        metadata, name = record.split(b"\t", 1)
        mode, oid, stage = metadata.decode().split()
        if stage != "0" or mode not in ("100644", "100755"):
            raise RuntimeError("Resolve conflicts, submodules or symlinks before automated validation.")
        entries[os.fsdecode(name)] = [mode, oid]
    if scope == "worktree":
        names = set(entries)
        names.update(os.fsdecode(p) for p in git(root, "ls-files", "--others", "--exclude-standard", "-z").split(b"\0") if p)
        names = sorted(p for p in names if (root / p).exists())
        for name in names:
            if (root / name).is_symlink() or any(c in name for c in '\r\n"'):
                raise RuntimeError(f"Unsupported source path: {name!r}")
        # Git applies the same clean/EOL rules as staging, without writing objects or the index.
        payload = "".join('"' + name.replace("\\", "\\\\") + '"\n' for name in names).encode("utf-8")
        hashes = git(root, "hash-object", "--stdin-paths", input=payload).decode().splitlines() if names else []
        if len(hashes) != len(names):
            raise RuntimeError("Incomplete worktree fingerprint.")
        entries = {name: [entries.get(name, ["100644"])[0], oid] for name, oid in zip(names, hashes)}
    return {"digest": digest(entries), "files": len(entries)}


def context(root):
    paths = []
    java_home = Path(os.environ["JAVA_HOME"])
    paths.extend(java_home / p for p in ("release", "bin/java.exe"))
    paths.append(Path(sys.executable))
    paths.append(Path(__file__).resolve())
    # PATH strings differ between shells/sandbox tokens. Identify the actual executables,
    # not incidental directory ordering/casing; Python itself is sys.executable above.
    executables = {name: shutil.which(name) for name in ("git", "powershell", "node", "pnpm")}
    paths.extend(Path(p) for p in executables.values() if p)
    # Hash resolved build dependencies, not just their timestamps or the runtime classpath.
    for folder in (root / ".tools/m2", root / ".tools/frontend/node", root / ".tools/models"):
        if folder.exists():
            paths.extend(p for p in folder.rglob("*") if p.is_file() and p.suffix in (".jar", ".pom", ".exe", ".cjs", ".json", ".onnx"))
    for path in (Path.home() / ".m2/settings.xml", Path.home() / ".m2/toolchains.xml"):
        if path.exists():
            paths.append(path)
    import importlib.metadata
    packages = sorted((d.metadata["Name"], d.version) for d in importlib.metadata.distributions())
    environment = build_environment()
    git_configuration = [line for line in git(root, "config", "--list").decode("utf-8", errors="replace").splitlines()
                         if line.startswith(("filter.", "core.autocrlf=", "core.eol=", "core.attributesfile=", "core.filemode=", "core.symlinks="))]
    # Ignored local data can influence integration tests, but is never part of the commit.
    extras = []
    for folder in ("data", "media/workshop"):
        base = root / folder
        if base.exists():
            extras.extend(p for p in base.rglob("*") if p.is_file() and not p.name.endswith(".lock"))
    return digest({"policy": 1, "pythonPackages": packages, "environment": environment, "executables": executables,
                   "gitConfiguration": git_configuration,
                   "tools": [(str(p), file_hash(p)) for p in sorted(set(paths))],
                   "localData": [(str(p.relative_to(root)), file_hash(p)) for p in sorted(extras)]})


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    temporary.write_text(json.dumps(value, indent=2), encoding="utf-8")
    os.replace(temporary, path)


def identity(root, scope):
    return {"snapshot": snapshot(root, scope)["digest"], "context": context(root)}


def receipt_path(root, key):
    return root / ".local-build/verification/receipts" / (digest(key) + ".json")


def status(root, scope):
    started = time.perf_counter()
    key = identity(root, scope)
    path = receipt_path(root, key)
    try:
        receipt = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        receipt = {}
    reusable = isinstance(receipt, dict) and receipt.get("status") == "passed" and receipt.get("identity") == key and receipt.get("command") == "clean verify"
    if not isinstance(receipt, dict):
        receipt = {}
    return {"status": "reusable" if reusable else "validation-required", "scope": scope,
            "identity": key, "receipt": str(path), "validatedAt": receipt.get("validatedAt"),
            "validationSeconds": 0, "inspectionSeconds": round(time.perf_counter() - started, 3)}


def run_maven(root, build_root):
    directory = root / ".local-build/verification/runs"
    directory.mkdir(parents=True, exist_ok=True)
    log = directory / (uuid.uuid4().hex + ".log")
    command = [str(build_root / "mvnw.cmd"), "--no-transfer-progress",
               f"-Dmaven.repo.local={(root / '.tools/m2').as_posix()}",
               f"-Dfrontend.install.directory={(root / '.tools/frontend').as_posix()}",
               "-DskipTests=false", "-Dmaven.test.skip=false", "-DskipITs=false",
               "-Dfrontend.skip=false", "-Ddata-tools.skip=false", "-Dexec.skip=false",
               "-Denforcer.skip=false", "-Djacoco.skip=false", "clean", "verify"]
    started = time.perf_counter()
    environment = os.environ.copy()
    # Isolated trees keep their own node_modules but share the content-addressed download store.
    modules_manifest = root / "frontend/node_modules/.modules.yaml"
    if modules_manifest.exists():
        import yaml
        installed = yaml.safe_load(modules_manifest.read_text(encoding="utf-8"))
        if isinstance(installed, dict) and installed.get("storeDir"):
            environment["PNPM_CONFIG_STORE_DIR"] = installed["storeDir"]
    with log.open("wb") as output:
        result = subprocess.run(command, cwd=build_root, env=environment, stdout=output, stderr=subprocess.STDOUT)
    return result.returncode, str(log), round(time.perf_counter() - started, 3)


def validate(root, scope, force=False):
    started = time.perf_counter()
    previous = status(root, scope)
    if not force and previous["status"] == "reusable":
        return previous
    key = previous["identity"]
    receipt = {"status": "running", "identity": key, "command": "clean verify", "scope": scope}
    write_json(receipt_path(root, key), receipt)
    build_root = root
    try:
        if scope == "index":
            build_root = root / ".local-build/verification/snapshots" / uuid.uuid4().hex
            build_root.mkdir(parents=True)
            # Materialize the index only. Never stash/reset the real worktree or index.
            git(root, "checkout-index", "--all", "--prefix=" + build_root.as_posix() + "/")
            # Some integration tests consume local ledger state. It is a fingerprinted
            # test context, not staged source: copy ignored inputs only, never unstaged fixes.
            for name in git(root, "ls-files", "--others", "--ignored", "--exclude-standard", "-z", "--", "data", "media/workshop").split(b"\0"):
                if not name:
                    continue
                relative = Path(os.fsdecode(name))
                source = root / relative
                if source.name.endswith(".lock") or not source.is_file():
                    continue
                if source.is_symlink() or not source.resolve().is_relative_to(root):
                    raise RuntimeError("External/symlink local test data cannot be copied into a validation snapshot.")
                destination = build_root / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, destination)
            model = root / ".tools/models"
            if model.exists():
                shutil.copytree(model, build_root / ".tools/models")
            if snapshot(root, "index")["digest"] != key["snapshot"]:
                raise RuntimeError("Index changed during snapshot extraction; retry preparation.")
        code, log, seconds = run_maven(root, build_root)
        receipt.update(log=log, validationSeconds=seconds)
        if code:
            raise RuntimeError(f"Maven verification failed (exit {code}); see {log}")
        if identity(root, scope) != key:
            raise RuntimeError("Validation inputs/toolchain changed during Maven; no reusable receipt. Retry validation.")
        receipt.update(status="passed", validatedAt=datetime.now(timezone.utc).isoformat())
        write_json(receipt_path(root, key), receipt)
    except Exception as error:
        receipt.update(status="failed", error=str(error))
        write_json(receipt_path(root, key), receipt)
        raise
    return {**previous, "status": "validated", "validatedAt": receipt["validatedAt"],
            "validationSeconds": seconds, "log": log, "elapsedSeconds": round(time.perf_counter() - started, 3)}


def commit(root, message):
    started = time.perf_counter()
    if not message or not message.strip():
        raise RuntimeError("A commit message is required.")
    if not git(root, "diff", "--cached", "--name-only").strip():
        raise RuntimeError("Nothing staged; no commit created.")
    git(root, "diff", "--cached", "--check")
    prepared = status(root, "index")
    if prepared["status"] != "reusable":
        raise RuntimeError("Index is not validated. Run prepare-commit first; no commit created.")
    if snapshot(root, "index")["digest"] != prepared["identity"]["snapshot"]:
        raise RuntimeError("Index changed during receipt inspection; no commit created.")
    git_started = time.perf_counter()
    output = git(root, "commit", "-m", message).decode("utf-8", errors="replace")
    git_seconds = round(time.perf_counter() - git_started, 3)
    # Hooks/concurrent editors must not silently change the tree that was validated.
    committed = {}
    for record in git(root, "ls-tree", "-r", "-z", "HEAD").split(b"\0"):
        if record:
            meta, name = record.split(b"\t", 1)
            mode, _, oid = meta.decode().split()
            committed[os.fsdecode(name)] = [mode, oid]
    if digest(committed) != prepared["identity"]["snapshot"]:
        raise RuntimeError("Commit exists but a hook/concurrent operation changed its content. Inspect HEAD; no automatic rollback or push.")
    return {**prepared, "status": "committed", "commit": git(root, "rev-parse", "HEAD").decode().strip(),
            "gitSeconds": git_seconds, "output": output, "elapsedSeconds": round(time.perf_counter() - started, 3)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("status", "run-build", "prepare-commit", "commit"))
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--scope", choices=("worktree", "index"), default="worktree")
    parser.add_argument("--message")
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        if args.action == "status":
            result = status(root, args.scope)
        elif args.action == "run-build":
            result = validate(root, "worktree", force=True)
        elif args.action == "prepare-commit":
            result = validate(root, "index")
        else:
            result = commit(root, args.message)
        write_json(root / ".local-build/verification/last-operation.json", result)
        print(json.dumps(result))
    except Exception as error:
        result = {"status": "error", "action": args.action, "message": str(error)}
        write_json(root / ".local-build/verification/last-operation.json", result)
        print(json.dumps(result))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
