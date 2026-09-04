import unittest
from minipaintdex_data.paint_containers import associate_containers
from minipaintdex_data.refresh import build_refresh_changeset


class PaintContainersTest(unittest.TestCase):
    def test_refresh_includes_container_evidence_and_preserves_an_existing_link(self):
        from test_refresh import paint
        existing = paint("a", "Vallejo")
        incoming = dict(existing)
        incoming.pop("container_format_id")
        result = build_refresh_changeset({"paints": [existing]}, {"paints": [incoming]}, brand="all", verified_at="2026-01-01")
        self.assertEqual(result["operations"], [])
        new = dict(incoming, id="b")
        result = build_refresh_changeset({"paints": [existing]}, {"paints": [new]}, brand="Vallejo", verified_at="2026-01-01")
        self.assertEqual(result["operations"][0]["record"]["container_format_id"], "unidentified-b")
        self.assertEqual(result["container_formats"][0]["id"], "unidentified-b")

    def test_unidentified_is_not_a_guessed_standard(self):
        paints, formats = associate_containers([{"id": "a", "brand": "Vallejo", "volume_ml": 18},
                                                {"id": "b", "brand": "Vallejo", "volume_ml": 18}])
        self.assertNotEqual(paints[0]["container_format_id"], paints[1]["container_format_id"])
        self.assertEqual(formats[0]["dimensions"]["width_mm"], None)
        self.assertEqual(formats[0]["evidence_status"], "unknown")

    def test_explicit_evidence_shared_by_products_and_replayed_without_downgrade(self):
        format = {"schema_version": 1, "id": "dropper-18", "brand": "Test", "name": "Bottle",
                  "family": "dropper", "volume_ml": 18, "dimensions": {"width_mm": 25, "depth_mm": 25, "height_mm": 80},
                  "evidence_status": "confirmed", "sources": ["https://example.org/bottle"]}
        raw = [{"id": "a", "brand": "Test"}, {"id": "b", "brand": "Test"}]
        paints, formats = associate_containers(raw, evidence={"a": format, "b": format})
        self.assertEqual(len(formats), 1)
        self.assertTrue(all(p["container_format_id"] == "dropper-18" for p in paints))
        repeated, updates = associate_containers(raw, paints)
        self.assertEqual(paints, repeated)
        self.assertEqual(updates, [])
        with self.assertRaises(ValueError):
            associate_containers(raw, evidence={"a": {**format, "dimensions": {"width_mm": -1}}})
