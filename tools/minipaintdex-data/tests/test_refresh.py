import unittest

from minipaintdex_data.refresh import build_refresh_changeset


def paint(identifier: str, brand: str, name: str = "Paint") -> dict:
    return {
        "id": identifier,
        "brand": brand,
        "manufacturer": brand,
        "range": "Range",
        "functional_type": "opaque_standard",
        "name": name,
        "data_status": "confirmed",
        "lifecycle_status": "current",
        "verified_at": "2026-01-01",
    }


class RefreshTests(unittest.TestCase):
    def test_all_brands_only_retires_from_complete_coverage(self):
        catalog = {"schema_version": 1, "paints": [paint("a-old", "Brand A"), paint("b-old", "Brand B")]}
        refreshed = {
            "coverage": [
                {"brand": "Brand A", "complete": True},
                {"brand": "Brand B", "complete": False},
            ],
            "paints": [paint("a-new", "Brand A", "New")],
        }

        changeset = build_refresh_changeset(catalog, refreshed, brand="all", verified_at="2026-08-30")

        actions = {(operation["action"], operation["record"]["id"]) for operation in changeset["operations"]}
        self.assertIn(("upsert", "a-new"), actions)
        self.assertIn(("retire", "a-old"), actions)
        self.assertNotIn(("retire", "b-old"), actions)
        self.assertTrue(any("b-old" in warning for warning in changeset["refresh"]["warnings"]))

    def test_explicit_removal_produces_confirmed_delete_operations(self):
        catalog = {"schema_version": 1, "paints": [paint("a-old", "Brand A")]}
        refreshed = {"coverage": [{"brand": "Brand A", "complete": True}], "paints": []}

        changeset = build_refresh_changeset(
            catalog, refreshed, brand="Brand A", verified_at="2026-08-30", remove_missing=True
        )

        operation = changeset["operations"][0]
        self.assertEqual(operation["action"], "delete")
        self.assertTrue(operation["confirmed_removal"])

    def test_technical_paint_requires_usage_instructions(self):
        catalog = {"schema_version": 1, "paints": [paint("a-old", "Brand A")]}
        technical = paint("technical", "Brand A") | {"functional_type": "technical_effect"}
        refreshed = {"coverage": [{"brand": "Brand A", "complete": False}], "paints": [technical]}

        with self.assertRaisesRegex(ValueError, "usage_instructions"):
            build_refresh_changeset(catalog, refreshed, brand="Brand A")


if __name__ == "__main__":
    unittest.main()
