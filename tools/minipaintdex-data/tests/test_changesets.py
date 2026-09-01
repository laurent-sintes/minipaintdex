import json
import unittest
from pathlib import Path

from minipaintdex_data.assets import audit_assets
from minipaintdex_data.changesets import (
    build_paint_changeset, build_paint_model_migration_changeset, validate_changeset,
)


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
        self.assertEqual(record["profile"]["application_system"], "one_coat_shading")
        self.assertNotIn("functional_type", record)
        observations = {item["name"]: item["value"] for item in record["source_observation"]["fields"]}
        self.assertEqual(observations["functional_class"], "one_coat_contrast")
        self.assertIn("enrichment", observations)
        self.assertEqual(changeset["operations"][0]["workshop_quantity_delta"], 2)

    def test_migration_preserves_source_specific_information(self):
        source = {
            "paints": [{
                "id": "cit-contrast-example", "brand": "Warhammer Colour",
                "manufacturer": "Games Workshop", "range": "Contrast", "name": "Example",
                "functional_type": "one_coat_contrast", "finish": "transparent",
                "vendor_application_note": {"label": "Contrast", "layers": 1},
            }]
        }
        changeset = build_paint_model_migration_changeset(source, source="catalog.yaml")
        record = changeset["operations"][0]["record"]
        self.assertEqual(record["vendor_application_note"], {"label": "Contrast", "layers": 1})
        observations = {item["name"]: item["value"] for item in record["source_observation"]["fields"]}
        self.assertEqual(observations["functional_type"], "one_coat_contrast")
        self.assertEqual(observations["vendor_application_note"], {"label": "Contrast", "layers": 1})
        self.assertEqual(record["mapping_report"]["unmapped_fields"], ["vendor_application_note"])

    def test_rejects_duplicate_paint_ids(self):
        operation = {
            "action": "upsert",
            "workshop_quantity_delta": 0,
            "record": {
                "id": "paint-id",
                "brand": "Brand",
                "manufacturer": "Maker",
                "range": "Range",
                "profile": {
                    "roles": ["color_paint"], "application_methods": ["brush"],
                    "application_system": "conventional_layering", "coverage": "opaque",
                    "finish": "matte", "effects": [],
                    "undercoat": {"tone": "any", "pre_highlighted_surface_recommended": False},
                    "medium": "acrylic",
                },
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

    def test_accepts_market_painting_guides_in_paintable_product_changeset(self):
        changeset = {
            "schema_version": 1,
            "kind": "market_product",
            "product": {
                "id": "game", "name": "Game", "line": "Game", "product_type": "board_game", "scope": "core",
                "expected_paintable_count": 1,
                "catalog_items": [{"id": "game-hero", "product_id": "game", "name": "Hero", "kind": "hero", "quantity": 1}],
            },
            "painting_guides": [{
                "id": "game-hero-guide", "version": 1, "knowledge_status": "documented",
                "catalog_item_id": "game-hero",
                "slots": [{"id": "game-hero-guide-slot-01", "market_paint_id": "paint"}],
                "preparation": [], "painting": [],
            }],
        }
        self.assertEqual(validate_changeset(changeset), [])


if __name__ == "__main__":
    unittest.main()
