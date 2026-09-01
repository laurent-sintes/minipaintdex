import json
import unittest
from pathlib import Path

from minipaintdex_data.assets import audit_assets
from minipaintdex_data.changesets import (
    build_paint_changeset, validate_changeset,
)
from minipaintdex_data.paint_identity import market_paint_id


class ChangeSetTests(unittest.TestCase):
    def test_generates_brand_code_reference_identities(self):
        self.assertEqual(market_paint_id("Prince August", "P951"), "pau-p951")
        self.assertEqual(market_paint_id("The Army Painter", "WP2007P"), "tap-wp2007p")
        self.assertEqual(market_paint_id("Vallejo", "72.483"), "val-72-483")
        self.assertEqual(
            market_paint_id("Warhammer Colour", "PROD4190213-99189958145"),
            "cit-prod4190213-99189958145",
        )

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

    def test_manual_pot_photo_is_classified_as_owned_photo(self):
        changeset = build_paint_changeset({"paints": [{
            "brand_canonical": "Vallejo", "manufacturer": "Acrylicos Vallejo",
            "range_canonical": "Model Color", "reference": "70.001", "name": "White",
            "local_image": "/media/market/paints/vallejo/val-70-001.webp",
        }]}, source="imports/manual-photo.json", verified_at="2026-09-01", include_workshop=False)

        image = changeset["operations"][0]["record"]["manufacturer_image"]
        self.assertEqual(image["image_quality"], "owned_photo")
        self.assertEqual(image["quality_verified_at"], "2026-09-01")

    def test_rejects_duplicate_paint_ids(self):
        operation = {
            "action": "upsert",
            "workshop_quantity_delta": 0,
            "record": {
                "schema_version": 1,
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

    def test_rejects_non_v1_paint_records(self):
        changeset = build_paint_changeset({"paints": [{
            "brand_canonical": "Vallejo", "manufacturer": "Acrylicos Vallejo",
            "range_canonical": "Model Color", "reference": "70.001", "name": "White",
        }]}, source="test", include_workshop=False)
        changeset["operations"][0]["record"]["schema_version"] = 2

        self.assertIn("operations[0].record.schema_version must be 1", validate_changeset(changeset))

    def test_rejects_untraceable_retailer_photo(self):
        changeset = build_paint_changeset({"paints": [{
            "brand_canonical": "Vallejo", "manufacturer": "Acrylicos Vallejo",
            "range_canonical": "Model Color", "reference": "70.001", "name": "White",
        }]}, source="test", include_workshop=False)
        changeset["operations"][0]["record"]["manufacturer_image"] = {
            "source_url": "https://retailer.test/paint.webp",
            "image_quality": "retailer_photo",
            "quality_verified_at": "2026-09-01",
        }

        errors = validate_changeset(changeset)

        self.assertIn("operations[0].record.manufacturer_image.credit is required for retailer_photo", errors)
        self.assertIn("operations[0].record.manufacturer_image.reference_url is required for retailer_photo", errors)

    def test_rejects_incomplete_source_snapshot(self):
        changeset = build_paint_changeset({"paints": [{
            "brand_canonical": "Vallejo", "manufacturer": "Acrylicos Vallejo",
            "range_canonical": "Model Color", "reference": "70.001", "name": "White",
        }]}, source="test", include_workshop=False)
        changeset["operations"][0]["record"]["source_snapshots"] = [{"provider": "official", "url": "", "payload": []}]

        errors = validate_changeset(changeset)

        self.assertIn("operations[0].record.source_snapshots[0].url must use HTTPS", errors)
        self.assertIn("operations[0].record.source_snapshots[0].payload must be an object", errors)

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
                "schema_version": 1, "id": "game", "name": "Game", "line": "Game",
                "product_type": "board_game", "scope": "core",
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
