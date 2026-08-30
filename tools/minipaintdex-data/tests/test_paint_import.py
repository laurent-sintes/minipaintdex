import json
import unittest
from pathlib import Path

from minipaintdex_data import paint_import as paint_inventory


MODULE_PATH = Path(paint_inventory.__file__).resolve()


class PaintInventoryTests(unittest.TestCase):
    def test_brand_aliases_preserve_current_name(self):
        brand, aliases, manufacturer = paint_inventory.canonical_brand("Citadel Colour")
        self.assertEqual(brand, "Warhammer Colour")
        self.assertIn("Citadel", aliases)
        self.assertEqual(manufacturer, "Games Workshop")

    def test_cpress_is_flagged_not_silently_changed(self):
        record = paint_inventory.normalize_candidate(
            {
                "brand_observed": "Vallejo",
                "range_observed": "Cpress Color",
                "name_observed": "Test Blue",
                "confidence": 0.98,
            },
            {},
        )
        self.assertEqual(record["range_canonical"], "Xpress Color")
        self.assertEqual(record["functional_class"], "one_coat_contrast")
        self.assertTrue(record["needs_review"])
        self.assertTrue(any("OCR corrigé" in item for item in record["warnings"]))

    def test_equivalent_commercial_ranges_share_a_function(self):
        for range_name in ("Contrast", "Xpress Color", "Speedpaint 2.0"):
            self.assertEqual(paint_inventory.classify_function(range_name), "one_coat_contrast")

    def test_army_painter_label_spacing_is_normalized(self):
        canonical, warnings = paint_inventory.canonical_range("SPEED PAINT")
        self.assertEqual(canonical, "Speedpaint")
        self.assertEqual(warnings, [])

    def test_vallejo_subranges_keep_a_stable_canonical_spelling(self):
        self.assertEqual(paint_inventory.canonical_range("GAME COLOR METALLIC")[0], "Game Color Metallic")
        self.assertEqual(paint_inventory.canonical_range("XPRESS COLOR INTENSE")[0], "Xpress Color Intense")

    def test_prince_august_vallejo_style_reference_requires_review(self):
        record = paint_inventory.normalize_candidate(
            {
                "brand_observed": "Prince August",
                "range_observed": "Games",
                "name_observed": "Example",
                "reference_observed": "72.401",
                "confidence": 0.99,
            },
            {},
        )
        self.assertEqual(record["brand_canonical"], "Prince August")
        self.assertTrue(record["needs_review"])
        self.assertIn("Acrylicos Vallejo", record["manufacturer"])

    def test_same_photo_is_not_counted_twice(self):
        existing = {column: "" for column in paint_inventory.CSV_COLUMNS}
        existing.update({"quantite": "1", "source_hashes": "abc", "nom": "Blue"})
        incoming = dict(existing)
        incoming["quantite"] = "2"
        merged, changed = paint_inventory.merge_rows(existing, incoming)
        self.assertFalse(changed)
        self.assertEqual(merged["quantite"], "1")

    def test_enrichment_catalog_is_applied_by_reference(self):
        root = MODULE_PATH.parents[4] / "imports"
        source = root / "_test_normalized.json"
        catalog = root / "_test_catalog.json"
        output = root / "_test_enriched.json"
        try:
            source.write_text(json.dumps({"paints": [{"reference": "72.418", "name": "Lizard Green"}]}), encoding="utf-8")
            catalog.write_text(json.dumps({"by_reference": {"72.418": {"volume_ml": 18}}}), encoding="utf-8")
            args = type("Args", (), {"input": str(source), "catalog": str(catalog), "output": str(output)})()
            self.assertEqual(paint_inventory.command_enrich(args), 0)
            self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["paints"][0]["enrichment"]["volume_ml"], 18)
        finally:
            source.unlink(missing_ok=True)
            catalog.unlink(missing_ok=True)
            output.unlink(missing_ok=True)

    def test_transparent_packshot_is_trimmed_with_padding(self):
        from PIL import Image

        path = MODULE_PATH.parents[4] / "imports" / "_test_packshot.png"
        try:
            image = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
            image.paste((255, 0, 0, 255), (40, 30, 60, 70))
            image.save(path)
            args = type("Args", (), {"images": [str(path)], "padding": 5})()
            self.assertEqual(paint_inventory.command_trim_images(args), 0)
            with Image.open(path) as trimmed:
                self.assertEqual(trimmed.size, (30, 50))
        finally:
            path.unlink(missing_ok=True)

    def test_relocate_source_does_not_change_quantity(self):
        root = MODULE_PATH.parents[4] / "imports"
        source = root / "_test_relocate.csv"
        output_csv = root / "_test_relocated.csv"
        output_yaml = root / "_test_relocated.yaml"
        row = {column: "" for column in paint_inventory.CSV_COLUMNS}
        row.update({
            "id": "paint", "marque_canonique": "Vallejo", "gamme_canonique": "Xpress Color",
            "classe_fonctionnelle": "one_coat_contrast", "nom": "Test", "quantite": "2",
            "source_photo": "imports/photos/test.jpg", "cle_dedoublonnage": "paint",
        })
        try:
            paint_inventory.write_csv(source, [row])
            args = type("Args", (), {
                "inventory": str(source), "old": "imports/photos/test.jpg", "new": "imports/archive/test.jpg",
                "output_csv": str(output_csv), "output_yaml": str(output_yaml),
            })()
            self.assertEqual(paint_inventory.command_relocate_source(args), 0)
            relocated = paint_inventory.read_csv(output_csv)[0]
            self.assertEqual(relocated["source_photo"], "imports/archive/test.jpg")
            self.assertEqual(relocated["quantite"], "2")
        finally:
            source.unlink(missing_ok=True)
            output_csv.unlink(missing_ok=True)
            output_yaml.unlink(missing_ok=True)

    def test_csv_and_yaml_exports_are_written(self):
        row = {column: "" for column in paint_inventory.CSV_COLUMNS}
        row.update(
            {
                "id": "vallejo-xpress-test-blue",
                "marque_canonique": "Vallejo",
                "gamme_canonique": "Xpress Color",
                "classe_fonctionnelle": "one_coat_contrast",
                "nom": "Test Blue",
                "quantite": "1",
            }
        )
        writable_test_root = MODULE_PATH.parents[4] / "imports"
        csv_path = writable_test_root / "_test_paints.csv"
        yaml_path = writable_test_root / "_test_paints.yaml"
        try:
            paint_inventory.write_csv(csv_path, [row])
            paint_inventory.write_yaml(yaml_path, [row])
            loaded = paint_inventory.read_csv(csv_path)
            self.assertEqual(loaded[0]["marque_canonique"], "Vallejo")
            self.assertIn('gamme_canonique: "Xpress Color"', yaml_path.read_text(encoding="utf-8"))
        finally:
            csv_path.unlink(missing_ok=True)
            yaml_path.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
