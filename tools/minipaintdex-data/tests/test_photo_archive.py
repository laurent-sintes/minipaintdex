import json
import uuid
from minipaintdex_data.paint_pots import ledger_snapshot
import shutil
import unittest
from pathlib import Path
from minipaintdex_data.photo_archive import archive_batch, digest


class PhotoArchiveTests(unittest.TestCase):
    def setUp(self):
        parent = Path("tools/minipaintdex-data/target/test-photo-archive")
        parent.mkdir(parents=True, exist_ok=True)
        self.root = (parent / uuid.uuid4().hex).resolve()
        self.root.mkdir()
        self.addCleanup(shutil.rmtree, self.root)
        stock = self.root / "data/ledger/events/2026-09.jsonl"
        stock.parent.mkdir(parents=True)
        stock.write_text(json.dumps({"event_type": "paint_pot.registered", "aggregate_id": "pot-1", "payload": {"paint_product_id": "paint-1"}}) + "\n", encoding="utf-8")
        self.photo = self.root / "imports/workshop-paints/photos/test.jpeg"
        self.photo.parent.mkdir(parents=True)
        self.photo.write_bytes(b"test photo contents")
        self.manifest = {"schema_version": 1, "target": "workshop.paint-pots", "import_id": "test-import",
                         "archive_date": "2026-09-03", "verified_ledger_sha256": ledger_snapshot(self.root)["ledgerSha256"],
                         "photos": [{"path": "test.jpeg", "sha256": digest(self.photo), "outcome": "imported", "paint_pot_ids": ["pot-1"]}]}

    def test_dry_run_then_idempotent_archive_and_pending_photos(self):
        pending = self.photo.with_name("pending.jpeg")
        pending.write_bytes(b"unresolved photo")
        self.manifest["photos"].append({"path": "pending.jpeg", "outcome": "pending"})
        result = archive_batch(self.root, self.manifest)
        self.assertFalse(result["applied"])
        self.assertTrue(self.photo.exists())
        result = archive_batch(self.root, self.manifest, apply=True)
        self.assertFalse(self.photo.exists())
        self.assertTrue(pending.exists())
        self.assertEqual(digest(self.root / result["moves"][0]["destination"]), self.manifest["photos"][0]["sha256"])
        self.assertEqual(result, archive_batch(self.root, self.manifest, apply=True))

    def test_rejects_wrong_target_changed_stock_path_escape_and_overwrite(self):
        for override in [{"target": "market.paints"}, {"verified_ledger_sha256": "wrong"}, {"import_id": "../escape"}]:
            with self.subTest(override=override), self.assertRaises(ValueError):
                archive_batch(self.root, self.manifest | override, apply=True)
        self.assertTrue(self.photo.exists())
        destination = self.root / archive_batch(self.root, self.manifest)["moves"][0]["destination"]
        destination.parent.mkdir(parents=True)
        destination.write_bytes(b"must not be overwritten")
        with self.assertRaises(ValueError):
            archive_batch(self.root, self.manifest, apply=True)
        self.assertEqual(destination.read_bytes(), b"must not be overwritten")

    def test_duplicate_must_match_archived_original(self):
        original = self.root / "imports/workshop-paints/archive/2026-09-02/earlier/test.jpeg"
        original.parent.mkdir(parents=True)
        original.write_bytes(self.photo.read_bytes())
        self.manifest["photos"][0].update(outcome="duplicate", duplicate_of=original.relative_to(self.root).as_posix())
        result = archive_batch(self.root, self.manifest, apply=True)
        self.assertIn("/duplicates/", result["moves"][0]["destination"])
        self.assertTrue(original.exists())
