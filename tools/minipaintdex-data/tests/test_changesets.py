import json
import unittest
from pathlib import Path

from minipaintdex_data.assets import audit_assets
from minipaintdex_data.changesets import build_paint_changeset, validate_changeset


class ChangeSetTests(unittest.TestCase):
    def test_builds_canonical_market_paint_operation(self):
        changeset = build_paint_changeset(
            {
                "paints": [
                    {
                        "id": "warhammer-colour-contrast-apothecary-white",
                        "brand_observed": "CITADEL COLOUR",
                        "brand_canonical": "Warhammer Colour",
                        "brand_aliases": ["Citadel Colour"],
                        "manufacturer": "Games Workshop",
                        "range_observed": "CONTRAST",
                        "range_canonical": "Contrast",
                        "functional_class": "one_coat_contrast",
                        "name": "Apothecary White",
                        "status": "confirme",
                        "quantity": 2,
                        "enrichment": {"color_hex": "#D9DEDA", "volume_ml": 18},
                    }
                ]
            },
            source="imports/run.json",
            verified_at="2026-08-30",
        )
        self.assertEqual(validate_changeset(changeset), [])
        record = changeset["operations"][0]["record"]
        self.assertEqual(record["data_status"], "confirmed")
        self.assertEqual(record["color"]["hex"], "#D9DEDA")
        self.assertEqual(record["volume_ml"], 18)
        self.assertEqual(changeset["operations"][0]["workshop_quantity_delta"], 2)

    def test_rejects_duplicate_paint_ids(self):
        operation = {
            "action": "upsert",
            "workshop_quantity_delta": 0,
            "record": {
                "id": "paint-id",
                "brand": "Brand",
                "manufacturer": "Maker",
                "range": "Range",
                "functional_type": "opaque_standard",
                "name": "Paint",
            },
        }
        errors = validate_changeset(
            {"schema_version": 1, "kind": "market_paints", "operations": [operation, operation]}
        )
        self.assertIn("duplicate paint id: paint-id", errors)

    def test_asset_audit_reports_missing_and_orphaned_files(self):
        root = Path(__file__).parent / "fixtures" / "assets-repository"
        report = audit_assets(root)
        self.assertEqual(report["orphaned"], ["/orphan.png"])
        self.assertEqual(report["missing"], ["/missing.jpg"])

    def test_accepts_market_painting_guides_in_project_changeset(self):
        changeset = {
            "schema_version": 1,
            "kind": "miniature_project",
            "project": {
                "id": "game", "name": "Game", "game": "Game", "scope": "core",
                "expected_paintable_count": 1,
                "catalog_items": [{"id": "game-hero", "game_id": "game", "name": "Hero", "kind": "miniature"}],
            },
            "painting_guides": [{
                "id": "game-hero-guide", "version": 1, "knowledge_status": "documented",
                "catalog_item_id": "game-hero",
                "slots": [{"id": "game-hero-guide-slot-01", "market_paint_id": "paint"}],
                "preparation": [], "painting": [],
            }],
            "workshop_items": [{
                "id": "ws-game-hero-001", "catalog_item_id": "game-hero",
                "project_id": "game", "display_name": "Hero #1",
            }],
        }
        self.assertEqual(validate_changeset(changeset), [])


if __name__ == "__main__":
    unittest.main()
