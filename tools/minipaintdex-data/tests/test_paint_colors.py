from __future__ import annotations

import unittest
from pathlib import Path
from unittest.mock import patch

from minipaintdex_data.paint_colors import build_color_enrichment_changeset
from minipaintdex_data.paint_identity import market_paint_id


def paint(brand: str, reference: str, name: str, range_name: str, *, role: str = "color_paint", color: str = "") -> dict:
    return {
        "schema_version": 1,
        "id": market_paint_id(brand, reference),
        "brand": brand,
        "manufacturer": brand,
        "brand_aliases": [],
        "range": range_name,
        "profile": {
            "roles": [role],
            "application_methods": ["brush"],
            "application_system": "conventional_layering",
            "coverage": "opaque",
            "finish": "matte",
            "effects": [],
            "undercoat": {"tone": "any", "pre_highlighted_surface_recommended": False},
            "medium": "acrylic",
        },
        "reference": reference,
        "name": name,
        "color": {"family": "", "hex": color},
        "lifecycle_status": "active",
        "data_status": "confirmed",
        "warnings": [],
        "tags": [],
        "notes": "",
        "manufacturer_page": "",
        "manufacturer_image": {
            "path": "",
            "source_url": "",
            "credit": "",
            "license": "",
            "reference_url": "",
            "image_quality": "none",
            "quality_limitation": {
                "code": "better-source-not-found",
                "detail": "No product image is available in this test fixture.",
                "observed_at": "2026-09-02",
            },
        },
        "volume_ml": 18,
        "recommended_uses": [],
        "usage_instructions": {
            "summary": "" if role == "color_paint" else "Test instructions.",
            "steps": [] if role == "color_paint" else ["Use as directed."],
            "tips": [], "review_required": False,
        },
        "verified_at": "2026-09-01",
        "result_image": {},
        "confidence": 1.0,
        "deduplication_key": f"test|{reference}",
        "provenance": {},
        "mapping_report": {"mapping": "test", "mapping_version": 1, "mapped_fields": [], "unmapped_fields": [], "ignored_fields": []},
        "source_observation": {"adapter": "test", "fields": []},
        "source_snapshots": [],
        "observed_brand": "",
        "observed_range": "",
    }


class PaintColorEnrichmentTest(unittest.TestCase):
    def build(self, catalog_paint: dict, records: list[dict], config: dict):
        brand = catalog_paint["brand"]
        manifest = {
            "schema_version": 1,
            "id": "test-colors",
            "repository": "https://example.test/colors",
            "revision": "a" * 40,
            "license": "MIT",
            "copyright": "Test",
            "accuracy": "Approximate test colours.",
            "brands": {
                brand: {
                    "file": "colors.json",
                    "sha256": "0" * 64,
                    **config,
                }
            },
        }
        with (
            patch("minipaintdex_data.paint_colors._read_manifest", return_value=manifest),
            patch("minipaintdex_data.paint_colors._read_source_records", return_value=records),
        ):
            return build_color_enrichment_changeset(
                {"schema_version": 1, "paints": [catalog_paint]},
                manifest_path=Path("manifest.json"),
                source_root=Path("."),
                as_of="2026-09-02",
            )

    def test_enriches_an_exact_name_and_range_with_traceable_source(self):
        changeset, audit = self.build(
            paint("Warhammer Colour", "PROD1", "Abaddon Black", "Base"),
            [{"id": "citadel-abaddon-black", "name": "Abaddon Black", "range": "Base", "hex": "#010203"}],
            {"match": "name-range"},
        )
        self.assertEqual(1, len(changeset["operations"]))
        record = changeset["operations"][0]["record"]
        self.assertEqual("#010203", record["color"]["hex"])
        snapshot = record["source_snapshots"][-1]
        self.assertEqual("test-colors", snapshot["provider"])
        self.assertEqual("name-range", snapshot["payload"]["identity_match"])
        self.assertEqual(1, audit["brands"]["Warhammer Colour"]["enriched"])

    def test_matches_configured_army_painter_range_alias(self):
        changeset, _ = self.build(
            paint("The Army Painter", "WP2001P", "Sand Golem", "Speedpaint"),
            [{"id": "army-sand-golem", "name": "Sand Golem", "range": "Speedpaint Set 2.0", "hex": "#9A7544"}],
            {"match": "name-range", "range_aliases": {"Speedpaint": ["Speedpaint Set 2.0"]}},
        )
        self.assertEqual("#9a7544", changeset["operations"][0]["record"]["color"]["hex"])

    def test_uses_ordered_range_preference_and_reviewed_name_transforms(self):
        changeset, audit = self.build(
            paint("The Army Painter", "CP3019S", "Colour Primer: Alien Purple", "Colour Primer", role="primer"),
            [
                {"id": "fanatic", "name": "Alien Purple", "range": "Warpaints Fanatic", "hex": "#6154A3"},
                {"id": "primer", "name": "Alien Purple", "range": "Warpaints Primer", "hex": "#492B79"},
            ],
            {
                "match": "name-range",
                "strip_name_prefixes": ["Colour Primer:"],
                "range_aliases": {"Colour Primer": ["Warpaints Primer", "Warpaints Fanatic"]},
            },
        )
        record = changeset["operations"][0]["record"]
        self.assertEqual("#492b79", record["color"]["hex"])
        self.assertEqual("name-range-stripped-prefix", record["source_snapshots"][-1]["payload"]["identity_match"])
        self.assertEqual(1, audit["brands"]["The Army Painter"]["enriched"])

        aliased, _ = self.build(
            paint("The Army Painter", "WP3089P", "Bony Spikes", "Warpaints Fanatic"),
            [{"id": "fanatic", "name": "Boney Spikes", "range": "Warpaints Fanatic", "hex": "#E6D4C1"}],
            {"match": "name-range", "name_aliases": {"Bony Spikes": "Boney Spikes"}},
        )
        self.assertEqual("#e6d4c1", aliased["operations"][0]["record"]["color"]["hex"])

    def test_matches_vallejo_and_prince_august_by_reference(self):
        source = [{"id": "vallejo-70860", "name": "Medium Fleshtone", "range": "Model Color", "code": "70.860", "hex": "#C08060"}]
        vallejo, _ = self.build(
            paint("Vallejo", "70.860", "Medium Fleshtone", "Model Color"), source,
            {"match": "reference"},
        )
        prince, _ = self.build(
            paint("Prince August", "P860", "Chair Moyen", "CLASSIC"), source,
            {"match": "prince-august-model-color-reference", "source_range": "Model Color"},
        )
        self.assertEqual("#c08060", vallejo["operations"][0]["record"]["color"]["hex"])
        self.assertEqual("#c08060", prince["operations"][0]["record"]["color"]["hex"])

    def test_preserves_official_chart_extraction_provenance(self):
        changeset, _ = self.build(
            paint("Vallejo", "77.101", "Sterling Silver", "True Metallic Metal"),
            [{
                "brand": "Vallejo", "reference": "77.101", "name": "Sterling Silver",
                "range": "True Metallic Metal", "hex": "#EFF0EF",
                "source_url": "https://example.test/catalog.pdf",
                "source_sha256": "b" * 64,
                "source_image_url": "https://example.test/paint.png",
                "source_image_sha256": "c" * 64,
                "source_equivalent": "Official equivalent paint",
                "source_page": 76,
                "extraction_method": "median-upper-left-metallic-swatch",
                "accuracy": "Approximate digital swatch.",
            }],
            {"match": "reference"},
        )
        snapshot = changeset["operations"][0]["record"]["source_snapshots"][-1]
        self.assertEqual("https://example.test/catalog.pdf", snapshot["url"])
        self.assertEqual("b" * 64, snapshot["payload"]["source_document_sha256"])
        self.assertEqual("https://example.test/paint.png", snapshot["payload"]["source_image_url"])
        self.assertEqual("c" * 64, snapshot["payload"]["source_image_sha256"])
        self.assertEqual("Official equivalent paint", snapshot["payload"]["source_equivalent"])
        self.assertEqual(76, snapshot["payload"]["source_page"])
        self.assertEqual("median-upper-left-metallic-swatch", snapshot["payload"]["extraction_method"])

    def test_preserves_an_existing_color_and_reports_a_conflict(self):
        changeset, audit = self.build(
            paint("Vallejo", "70.860", "Medium Fleshtone", "Model Color", color="#111111"),
            [{"id": "vallejo-70860", "name": "Medium Fleshtone", "range": "Model Color", "code": "70.860", "hex": "#C08060"}],
            {"match": "reference"},
        )
        self.assertEqual([], changeset["operations"])
        self.assertEqual(1, audit["brands"]["Vallejo"]["existing_conflicts"])
        self.assertEqual("existing-conflict-preserved", audit["items"][0]["status"])

    def test_is_idempotent_when_the_existing_color_matches(self):
        changeset, audit = self.build(
            paint("Vallejo", "70.860", "Medium Fleshtone", "Model Color", color="#c08060"),
            [{"id": "vallejo-70860", "name": "Medium Fleshtone", "range": "Model Color", "code": "70.860", "hex": "#C08060"}],
            {"match": "reference"},
        )
        self.assertEqual([], changeset["operations"])
        self.assertEqual(0, audit["brands"]["Vallejo"]["enriched"])
        self.assertEqual(1, audit["brands"]["Vallejo"]["coverage_after"])

    def test_rejects_ambiguous_colors_and_classifies_auxiliary_products(self):
        ambiguous, audit = self.build(
            paint("Warhammer Colour", "PROD1", "Test Red", "Base"),
            [
                {"id": "red-a", "name": "Test Red", "range": "Base", "hex": "#AA0000"},
                {"id": "red-b", "name": "Test Red", "range": "Base", "hex": "#BB0000"},
            ],
            {"match": "name-range"},
        )
        skipped, skipped_audit = self.build(
            paint("Vallejo", "70.001", "Clear Medium", "Model Color", role="medium"),
            [{"id": "medium", "name": "Clear Medium", "range": "Model Color", "code": "70.001", "hex": "#FFFFFF"}],
            {"match": "reference"},
        )
        self.assertEqual([], ambiguous["operations"])
        self.assertEqual(1, audit["brands"]["Warhammer Colour"]["ambiguous"])
        self.assertEqual("auxiliary", skipped["operations"][0]["record"]["color"]["family"])
        self.assertEqual("", skipped["operations"][0]["record"]["color"]["hex"])
        self.assertEqual(1, skipped_audit["brands"]["Vallejo"]["special_auxiliary"])
        self.assertEqual(1, skipped_audit["brands"]["Vallejo"]["filter_coverage_after"])

    def test_reclassifies_a_functional_product_mistaken_for_paint(self):
        changeset, audit = self.build(
            paint("Prince August", "P596", "Médium Glacis", "CLASSIC"),
            [],
            {"match": "reference"},
        )
        record = changeset["operations"][0]["record"]
        self.assertEqual(["medium"], record["profile"]["roles"])
        self.assertEqual({"family": "auxiliary", "hex": ""}, record["color"])
        self.assertEqual(1, audit["brands"]["Prince August"]["reclassified_auxiliary"])


if __name__ == "__main__":
    unittest.main()
