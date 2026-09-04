"""Real Git fixtures; fake Maven keeps receipt/state-machine tests fast and deterministic."""
import importlib.util
import os
import stat
from pathlib import Path
import uuid
import unittest
from unittest.mock import patch

REPOSITORY = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("verification", REPOSITORY / "scripts/verification.py")
verification = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verification)


class VerificationTests(unittest.TestCase):
    def setUp(self):
        directory = REPOSITORY / "target/verification-tests"
        directory.mkdir(parents=True, exist_ok=True)
        # Inherit workspace ACLs; Python 3.14's private 0700 temporary directories can
        # prevent child Git processes under a different sandbox token from reading them.
        self.root = directory / uuid.uuid4().hex
        self.root.mkdir()
        verification.git(self.root, "init", "-q")
        verification.git(self.root, "config", "user.name", "Fixture")
        verification.git(self.root, "config", "user.email", "fixture@example.invalid")
        verification.git(self.root, "config", "core.autocrlf", "true")
        self.put(".gitignore", ".local-build/\n.tools/\ntarget/\n*.lock\n")
        self.put("source.txt", "original\n")
        verification.git(self.root, "add", ".")
        verification.git(self.root, "commit", "-qm", "fixture")
        self.context = patch.object(verification, "context", return_value="environment-1")
        self.context.start()
        self.maven = patch.object(verification, "run_maven", return_value=(0, "fixture.log", 0.1))
        self.run_maven = self.maven.start()

    def tearDown(self):
        self.maven.stop()
        self.context.stop()
        # Git marks loose objects read-only on Windows; allow the next Maven clean to
        # remove these generated fixtures without touching the real repository's Git data.
        for path in self.root.rglob("*"):
            if path.is_file():
                path.chmod(stat.S_IREAD | stat.S_IWRITE)

    def put(self, name, value):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(value.encode("utf-8"))
        return path

    def test_success_is_reused_without_maven(self):
        first = verification.validate(self.root, "worktree")
        second = verification.validate(self.root, "worktree")
        self.assertEqual("validated", first["status"])
        self.assertEqual("reusable", second["status"])
        self.assertEqual(0, second["validationSeconds"])
        self.run_maven.assert_called_once()

    def test_every_source_category_invalidates_even_with_same_timestamp(self):
        for name in ("backend/cli/src/main/Main.java", "backend/domain/src/test/Test.java",
                     "tools/minipaintdex-data/test.py", "data/market/test.yaml",
                     "frontend/src/test.ts", "config/application.yaml", "pom.xml", "docs/note.md"):
            with self.subTest(name=name):
                path = self.put(name, "before")
                verification.validate(self.root, "worktree")
                stamp = path.stat()
                path.write_text("after", encoding="utf-8")
                os.utime(path, ns=(stamp.st_atime_ns, stamp.st_mtime_ns))
                self.assertEqual("validation-required", verification.status(self.root, "worktree")["status"])

    def test_addition_deletion_and_rename_invalidate(self):
        verification.validate(self.root, "worktree")
        path = self.put("new.txt", "new")
        self.assertEqual("validation-required", verification.status(self.root, "worktree")["status"])
        path.unlink()
        self.assertEqual("reusable", verification.status(self.root, "worktree")["status"])
        (self.root / "source.txt").rename(self.root / "renamed.txt")
        self.assertEqual("validation-required", verification.status(self.root, "worktree")["status"])

    def test_staging_and_crlf_do_not_invalidate_equivalent_content(self):
        self.put("accent é space.txt", "hello\r\n")
        verification.validate(self.root, "worktree")
        verification.git(self.root, "add", ".")
        self.assertEqual(verification.snapshot(self.root, "index"), verification.snapshot(self.root, "worktree"))
        self.assertEqual("reusable", verification.status(self.root, "index")["status"])

    def test_partial_commit_cannot_borrow_worktree_validation(self):
        self.put("source.txt", "staged\n")
        verification.git(self.root, "add", "source.txt")
        self.put("source.txt", "unstaged\n")
        verification.validate(self.root, "worktree")
        self.assertEqual("validation-required", verification.status(self.root, "index")["status"])
        with self.assertRaisesRegex(RuntimeError, "not validated"):
            verification.commit(self.root, "not allowed")

    def test_isolated_index_uses_staged_content_and_preserves_worktree(self):
        self.put("source.txt", "staged\n")
        verification.git(self.root, "add", "source.txt")
        self.put("source.txt", "unstaged\n")
        self.put("untracked.txt", "private change")
        with (self.root / ".git/info/exclude").open("a") as exclude:
            exclude.write("\ndata/ledger/events/*.jsonl\n")
        self.put("data/ledger/events/test.jsonl", "local test context")
        index_before = verification.git(self.root, "ls-files", "--stage")
        def build(root, build_root):
            self.assertNotEqual(root, build_root)
            self.assertEqual("staged\n", (build_root / "source.txt").read_text())
            self.assertFalse((build_root / "untracked.txt").exists())
            self.assertEqual("local test context", (build_root / "data/ledger/events/test.jsonl").read_text())
            return 0, "fixture.log", 0.1
        self.run_maven.side_effect = build
        verification.validate(self.root, "index")
        self.assertEqual(index_before, verification.git(self.root, "ls-files", "--stage"))
        self.assertEqual("unstaged\n", (self.root / "source.txt").read_text())
        self.assertTrue((self.root / "untracked.txt").exists())
        self.assertEqual("reusable", verification.status(self.root, "index")["status"])

    def test_toolchain_change_invalidates(self):
        verification.validate(self.root, "worktree")
        verification.context.return_value = "environment-2"
        self.assertEqual("validation-required", verification.status(self.root, "worktree")["status"])

    def test_failed_forced_build_revokes_success(self):
        verification.validate(self.root, "worktree")
        self.run_maven.return_value = (1, "failure.log", 1.2)
        with self.assertRaisesRegex(RuntimeError, "Maven verification failed"):
            verification.validate(self.root, "worktree", force=True)
        self.assertEqual("validation-required", verification.status(self.root, "worktree")["status"])

    def test_change_during_build_cannot_publish_success(self):
        def changing_build(*args):
            self.put("source.txt", "during build")
            return 0, "fixture.log", 0.1
        self.run_maven.side_effect = changing_build
        with self.assertRaisesRegex(RuntimeError, "changed during Maven"):
            verification.validate(self.root, "worktree")
        self.assertEqual("validation-required", verification.status(self.root, "worktree")["status"])

    def test_malformed_or_running_receipt_is_not_reused(self):
        result = verification.status(self.root, "index")
        path = Path(result["receipt"])
        path.parent.mkdir(parents=True)
        path.write_text("invalid", encoding="utf-8")
        self.assertEqual("validation-required", verification.status(self.root, "index")["status"])
        verification.write_json(path, {"status": "running", "identity": result["identity"]})
        self.assertEqual("validation-required", verification.status(self.root, "index")["status"])

    def test_commit_is_timed_and_does_not_validate_again(self):
        self.put("source.txt", "next\n")
        verification.git(self.root, "add", "source.txt")
        verification.validate(self.root, "worktree")
        result = verification.commit(self.root, "Test exact tree")
        self.assertEqual("committed", result["status"])
        self.assertGreaterEqual(result["gitSeconds"], 0)
        self.assertEqual(result["commit"], verification.git(self.root, "rev-parse", "HEAD").decode().strip())
        self.run_maven.assert_called_once()

    def test_empty_index_does_not_create_commit(self):
        with self.assertRaisesRegex(RuntimeError, "Nothing staged"):
            verification.commit(self.root, "Nothing")
        self.run_maven.assert_not_called()

    def test_index_change_during_inspection_is_rejected_before_commit(self):
        self.put("source.txt", "next\n")
        verification.git(self.root, "add", "source.txt")
        verification.validate(self.root, "worktree")
        original_status = verification.status
        before = verification.git(self.root, "rev-parse", "HEAD")
        def changing_status(root, scope):
            result = original_status(root, scope)
            self.put("source.txt", "concurrent\n")
            verification.git(self.root, "add", "source.txt")
            return result
        with patch.object(verification, "status", side_effect=changing_status):
            with self.assertRaisesRegex(RuntimeError, "Index changed"):
                verification.commit(self.root, "Refuse changed index")
        self.assertEqual(before, verification.git(self.root, "rev-parse", "HEAD"))

    def test_environment_ignores_only_transport_and_path_noise(self):
        with patch.dict(os.environ, {"Path": "shell-1", "NPM_CONFIG_OFFLINE": "true", "NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S": "browser-1", "JAVA_TOOL_OPTIONS": "-Xmx512m"}, clear=True):
            before = verification.build_environment()
        with patch.dict(os.environ, {"PATH": "shell-2", "JAVA_TOOL_OPTIONS": "-Xmx512m"}, clear=True):
            self.assertEqual(before, verification.build_environment())
            os.environ["JAVA_TOOL_OPTIONS"] = "-Xmx1g"
            self.assertNotEqual(before, verification.build_environment())

    def test_real_context_detects_ignored_data_and_dependency_content(self):
        self.context.stop()
        self.put(".tools/jdk/release", "25")
        self.put(".tools/jdk/bin/java.exe", "java")
        dependency = self.put(".tools/m2/library.jar", "dependency")
        with patch.dict(os.environ, {"JAVA_HOME": str(self.root / ".tools/jdk")}):
            before = verification.context(self.root)
            dependency.write_text("corrupted")
            changed = verification.context(self.root)
            self.assertNotEqual(before, changed)
            self.put("data/ledger/events/local.jsonl", "event")
            self.assertNotEqual(changed, verification.context(self.root))
        self.context.start()


if __name__ == "__main__":
    unittest.main()
