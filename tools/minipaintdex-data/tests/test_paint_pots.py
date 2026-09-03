import json
import unittest
from pathlib import Path
import shutil
import uuid
from minipaintdex_data.paint_pots import build_import, ledger_snapshot


class PaintPotToolsTests(unittest.TestCase):
    def test_requires_explicit_identity_and_never_infers_photo_quantities(self):
        with self.assertRaises(ValueError):
            build_import({"paints": [{"paint_id": "paint-red", "quantity": 2}]})
        pot = {"paintPotId": "pot-one", "paintProductId": "paint-red"}
        with self.assertRaises(ValueError):
            build_import({"pots": [pot, pot]})
        payload = build_import({"pots": [pot]})
        self.assertEqual(payload["kind"], "workshop_paint_pots")
        self.assertEqual(payload["pots"][0]["acquiredAt"], None)

    def test_ledger_order_and_removed_identity_remain_explicit(self):
        parent = Path("tools/minipaintdex-data/target")
        parent.mkdir(parents=True, exist_ok=True)
        root = (parent / ('test-pots-' + uuid.uuid4().hex)).resolve()
        root.mkdir()
        self.addCleanup(shutil.rmtree, root)
        directory = root / "data/ledger/events"
        directory.mkdir(parents=True)
        def event(kind, payload):
            return json.dumps({"aggregate_id": "pot-one", "event_type": kind, "payload": payload}) + "\n"
        (directory / "2026-09.jsonl").write_text(event("paint_pot.possession_changed", {"possession": "discarded"}), encoding="utf-8")
        (directory / "2026-08.jsonl").write_text(event("paint_pot.registered", {"paint_product_id": "paint-red"}), encoding="utf-8")
        snapshot = ledger_snapshot(root)
        self.assertEqual(snapshot["pots"][0]["possession"], "discarded")
        self.assertEqual(len(snapshot["ledgerSha256"]), 64)
        self.assertEqual(snapshot, ledger_snapshot(root))
