import unittest
from pathlib import Path
from unittest.mock import patch

from minipaintdex_data.official_refresh import ProviderSpec, _validate_collection, collect_official_refresh
from minipaintdex_data.official_sources.army_painter import stable_payload
from minipaintdex_data.official_sources.common import classify, merge_previous, usage
from minipaintdex_data.official_sources.prince_august import parse_cards, parse_packshot
from minipaintdex_data.official_sources.vallejo import parse_lines
from minipaintdex_data.official_sources.warhammer import collect as collect_warhammer


class OfficialRefreshTest(unittest.TestCase):
    def test_parses_prince_august_product_card(self):
        page = """
        <div class="single-element"><img src="https://example.test/P830.jpg">
        <h3><a href="https://www.prince-august.net/peintures/classic/102-vert">P830 &#8211; 102 &#8211; Vert Allemand WWII</a></h3>
        <p>Peinture acrylique mate.</p></div>
        """

        self.assertEqual(
            parse_cards(page),
            [{
                "reference": "P830",
                "name": "Vert Allemand WWII",
                "url": "https://www.prince-august.net/peintures/classic/102-vert",
                "image": "https://example.test/P830.jpg",
                "description": "Peinture acrylique mate.",
            }],
        )

    def test_selects_prince_august_packshot_instead_of_colour_swatch(self):
        page = """
        <a href="https://www.prince-august.net/wp-content/uploads/2019/06/PMP951_0000.png"></a>
        <a href="https://www.prince-august.net/wp-content/uploads/2019/05/P951-1.jpg"></a>
        """

        self.assertEqual(
            parse_packshot(page, "P951", "https://www.prince-august.net/peintures/classic/1-blanc"),
            "https://www.prince-august.net/wp-content/uploads/2019/06/PMP951_0000.png",
        )

    def test_parses_vallejo_reference_and_english_name(self):
        lines = ["Game Color chart", "72. 401", "Templar White", "Blanco Templario", "72.402", "Dwarf Skin"]

        self.assertEqual(
            parse_lines(lines, prefix="72."),
            [("72.401", "Templar White"), ("72.402", "Dwarf Skin")],
        )

    def test_ignores_repeated_vallejo_references_in_safety_notices(self):
        lines = ["70.790", "Silver", "Plata", "70.790", "Danger. Flammable liquid."]

        self.assertEqual(
            parse_lines(lines, prefix="70."),
            [("70.790", "Silver")],
        )

    def test_generic_technical_guidance_is_explicitly_marked_for_review(self):
        result = usage("technical_effect", "Mud Effect")

        self.assertEqual(result["instruction_status"], "generic_template")
        self.assertTrue(result["review_required"])

    def test_classification_uses_words_instead_of_substrings(self):
        self.assertEqual(classify("Squid Pink", "opaque_standard"), "opaque_standard")
        self.assertEqual(classify("Medium Grey", "opaque_standard"), "opaque_standard")
        self.assertEqual(classify("Stainless Steel", "metallic"), "metallic")
        self.assertEqual(classify("Mahogany Ink", "opaque_standard"), "ink")
        self.assertEqual(classify("Gloss Varnish", "opaque_standard"), "varnish")
        self.assertEqual(classify("Xpress Medium", "one_coat_contrast"), "medium")

    def test_army_painter_snapshot_ignores_shopify_request_timestamps(self):
        product = {
            "title": "Stable paint",
            "updated_at": "request-time",
            "variants": [{"sku": "WP0001", "price": "3.89", "updated_at": "request-time"}],
            "images": [{"src": "image.jpg", "updated_at": "real-image-update"}],
        }

        stable = stable_payload(product)

        self.assertNotIn("updated_at", stable)
        self.assertNotIn("updated_at", stable["variants"][0])
        self.assertEqual(stable["variants"][0]["price"], "3.89")
        self.assertEqual(stable["images"][0]["updated_at"], "real-image-update")
        self.assertIn("updated_at", product)

    def test_merge_preserves_original_mapping_provenance(self):
        previous = {
            "id": "paint", "brand": "Vallejo", "reference": "70.001", "name": "Old",
            "source_observation": {"adapter": "source", "fields": [{"name": "vendor", "value": "kept"}]},
            "mapping_report": {"mapping": "vallejo", "mapping_version": 1, "unmapped_fields": ["vendor"]},
        }
        incoming = {
            "id": "new-id", "brand": "Vallejo", "reference": "70.001", "name": "New",
            "source_observation": {"adapter": "generated", "fields": []},
            "mapping_report": {"mapping": "vallejo", "mapping_version": 1, "unmapped_fields": []},
        }

        merged = merge_previous(incoming, previous)

        self.assertEqual(merged["name"], "New")
        self.assertEqual(merged["source_observation"], previous["source_observation"])
        self.assertEqual(merged["mapping_report"], previous["mapping_report"])

    def test_merge_never_downgrades_an_owned_photo(self):
        previous = {
            "id": "paint", "brand": "Vallejo", "reference": "70.001", "name": "Paint",
            "manufacturer_image": {
                "path": "/media/owned.webp", "image_quality": "owned_photo",
                "quality_verified_at": "2026-08-30",
            },
        }
        incoming = {
            "id": "paint", "brand": "Vallejo", "reference": "70.001", "name": "Paint",
            "manufacturer_image": {
                "reference_url": "https://example.test/generic", "image_quality": "generic_visual",
                "quality_verified_at": "2026-09-01",
            },
        }

        merged = merge_previous(incoming, previous)

        self.assertEqual(merged["manufacturer_image"]["image_quality"], "owned_photo")
        self.assertEqual(merged["manufacturer_image"]["path"], "/media/owned.webp")

    def test_merge_upgrades_a_retailer_photo_with_an_official_photo(self):
        previous = {
            "id": "paint", "brand": "Vallejo", "reference": "70.001", "name": "Paint",
            "manufacturer_image": {
                "source_url": "https://retailer.test/paint.jpg", "image_quality": "retailer_photo",
                "quality_verified_at": "2026-08-30",
            },
        }
        incoming = {
            "id": "paint", "brand": "Vallejo", "reference": "70.001", "name": "Paint",
            "manufacturer_image": {
                "source_url": "https://acrylicosvallejo.com/paint.jpg", "image_quality": "official_photo",
                "quality_verified_at": "2026-09-01",
            },
        }

        merged = merge_previous(incoming, previous)

        self.assertEqual(merged["manufacturer_image"]["image_quality"], "official_photo")
        self.assertIn("acrylicosvallejo.com", merged["manufacturer_image"]["source_url"])

    @patch("minipaintdex_data.official_sources.warhammer._hits")
    def test_collects_complete_warhammer_store_records_and_preserves_existing_id(self, hits):
        hits.return_value = [{
            "name": "Contrast: Aggaros Dunes", "slug": "Aggaros-Dunes-2019",
            "sku": "prod-test-123", "images": ["/image.svg"],
            "description": ".", "paintType": ["Contrast"], "paintColourRange": "Brown",
        }]
        catalog = {"paints": [{
            "id": "cit-contrast-aggaros-dunes", "brand": "Warhammer Colour",
            "manufacturer": "Games Workshop", "range": "Contrast", "name": "Aggaros Dunes",
            "deduplication_key": "warhammer-colour|contrast|name:aggaros-dunes",
        }]}

        records = collect_warhammer(catalog, Path("unused.pdf"))

        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["id"], "cit-contrast-aggaros-dunes")
        self.assertEqual(records[0]["reference"], "PROD-TEST-123")
        self.assertEqual(records[0]["manufacturer_image"]["source_url"], "")
        self.assertIn("colour swatch", records[0]["warnings"][0])
        self.assertEqual(records[0]["profile"]["application_system"], "one_coat_shading")
        self.assertEqual(records[0]["source_snapshots"][0]["provider"], "warhammer_store_search")
        self.assertEqual(records[0]["source_snapshots"][0]["payload"]["sku"], "prod-test-123")

    @patch("minipaintdex_data.official_sources.warhammer._hits")
    def test_warhammer_reference_drift_requires_explicit_identity_reconciliation(self, hits):
        hits.return_value = [{
            "id": "stable-provider-id", "name": "Layer: Tallarn Sand", "slug": "Layer-Tallarn-Sand-2019",
            "sku": "prod4210363-99189951332", "images": [
                "/app/resources/catalog/product/920x950/99189951332_LAYER_TALLARN_SAND.jpg",
            ],
            "description": ".", "paintType": ["Layer"], "paintColourRange": "Brown",
        }]
        catalog = {"paints": [{
            "schema_version": 1, "id": "cit-prod4210363-99189951239", "brand": "Warhammer Colour",
            "manufacturer": "Games Workshop", "range": "Layer", "reference": "PROD4210363-99189951239",
            "name": "Tallarn Sand", "deduplication_key": "cit|ref:prod4210363-99189951239",
        }]}

        record = collect_warhammer(catalog, Path("unused.pdf"))[0]

        self.assertEqual(record["id"], "cit-prod4210363-99189951239")
        self.assertEqual(record["reference"], "PROD4210363-99189951239")
        self.assertTrue(any("explicit identity reconciliation" in warning for warning in record["warnings"]))
        self.assertEqual(record["manufacturer_image"]["source_url"], "")
        self.assertIn("99189951332", record["source_snapshots"][0]["payload"]["images"][0])

    def test_provider_gate_rejects_a_large_volume_drop(self):
        spec = ProviderSpec("Brand", "provider", "mode", "scope", (), lambda *_: [], 1, 0.80)
        paints = [{"id": f"paint-{index}", "brand": "Brand", "source_snapshots": [{}]} for index in range(79)]

        with self.assertRaisesRegex(ValueError, "below 80%"):
            _validate_collection(spec, paints, 100)

    def test_vallejo_pdf_is_required_only_when_vallejo_is_selected(self):
        with self.assertRaisesRegex(ValueError, "--vallejo-pdf"):
            collect_official_refresh(Path("unused"), None, brands=["Vallejo"])


if __name__ == "__main__":
    unittest.main()
