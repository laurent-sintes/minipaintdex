import json
from pathlib import Path
import uuid
import unittest
import yaml
from minipaintdex_data.paint_usage_guides import extract_guides, plain_text, translate, validate_guides


class PaintUsageGuidesTest(unittest.TestCase):
    def test_extracts_exact_shared_text_preserving_identity_sources_and_supplements(self):
        root = Path("tools/minipaintdex-data/target/test-usage-guides") / str(uuid.uuid4())
        root.mkdir(parents=True)
        if root.is_dir():
            original = {"summary": "<p>Summary</p>", "steps": ["Shake"], "tips": ["Care"], "instruction_status": "generic_template", "review_required": True}
            paints = [{"id": f"brand-{i}", "brand": "Brand", "range": "Range", "name": str(i), "usage_instructions": original,
                       "manufacturer_page": f"https://example.com/{i}", "notes": "specific"} for i in range(2)]
            (root / "brand.yaml").write_text(yaml.safe_dump({"paints": paints}), encoding="utf-8")
            translations = {"exact": {"Summary": "Résumé", "Shake": "Agiter", "Care": "Prudence"}}
            result = extract_guides(root, translations)
            self.assertEqual(1, len(result["paint_usage_guides"]))
            guide = result["paint_usage_guides"][0]
            self.assertEqual(2, len(guide["source_urls"]))
            self.assertEqual("Prudence", guide["translations"][0]["content"]["tips"][0])
            self.assertEqual(original, guide["source_snapshots"][0]["payload"])
            self.assertTrue(guide["review_required"])
            self.assertEqual(["brand-0", "brand-1"], [o["record"]["id"] for o in result["operations"]])
            self.assertTrue(all(o["record"]["notes"] == "specific" and "usage_instructions" not in o["record"] for o in result["operations"]))
            (root / "brand.yaml").write_text(yaml.safe_dump({"paints": [o["record"] for o in result["operations"]], "paint_usage_guides": [guide]}), encoding="utf-8")
            self.assertEqual([], extract_guides(root, translations)["operations"])
            self.assertEqual([], validate_guides([guide]))

    def test_translation_is_exact_and_does_not_guess_unknown_sources(self):
        rules = {"templates": [{"source": "Product {name}.", "french": "Produit {name}."}]}
        self.assertEqual("Produit A.", translate("Product A.", rules))
        self.assertIsNone(translate("Unknown instruction", rules))
        self.assertEqual("", plain_text("</br>"))
        self.assertEqual("A & B", plain_text("<p>A &amp; B</p>"))

    def test_curated_translation_manifest_is_explicitly_unreviewed(self):
        path = Path(__file__).resolve().parents[1] / "resources/paint-usage-translations-fr.json"
        manifest = json.loads(path.read_text(encoding="utf-8"))
        self.assertEqual("machine", manifest["method"])
        self.assertTrue(manifest["review_required"])
        self.assertEqual("Agiter soigneusement.", translate("Shake thoroughly.", manifest))
