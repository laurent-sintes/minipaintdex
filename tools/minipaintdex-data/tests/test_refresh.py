import unittest

from minipaintdex_data.refresh import build_refresh_changeset


def paint(identifier: str, brand: str, name: str = "Paint") -> dict:
    return {
        "id": identifier,
        "brand": brand,
        "manufacturer": brand,
        "range": "Warpaints Fanatic" if brand == "The Army Painter" else "Model Color",
        "profile": {
            "roles": ["color_paint"], "application_methods": ["brush"],
            "application_system": "conventional_layering", "coverage": "opaque",
            "finish": "matte", "effects": [],
            "undercoat": {"tone": "any", "pre_highlighted_surface_recommended": False},
            "medium": "acrylic",
        },
        "name": name,
        "data_status": "confirmed",
        "lifecycle_status": "current",
        "verified_at": "2026-01-01",
    }


class RefreshTests(unittest.TestCase):
    def test_all_brands_only_retires_from_complete_coverage(self):
        catalog = {"schema_version": 1, "paints": [paint("a-old", "The Army Painter"), paint("b-old", "Vallejo")]}
        refreshed = {
            "coverage": [
                {"brand": "The Army Painter", "complete": True},
                {"brand": "Vallejo", "complete": False},
            ],
            "paints": [paint("a-new", "The Army Painter", "New")],
        }

        changeset = build_refresh_changeset(catalog, refreshed, brand="all", verified_at="2026-08-30")

        actions = {(operation["action"], operation["record"]["id"]) for operation in changeset["operations"]}
        self.assertIn(("upsert", "a-new"), actions)
        self.assertIn(("retire", "a-old"), actions)
        self.assertNotIn(("retire", "b-old"), actions)
        self.assertTrue(any("b-old" in warning for warning in changeset["refresh"]["warnings"]))
        audit = changeset["refresh"]["audit"]
        self.assertEqual(audit["existing_count"], 2)
        self.assertEqual(audit["incoming_count"], 1)
        self.assertEqual(audit["operation_count"], 2)
        self.assertEqual(audit["operations_by_brand"]["The Army Painter"], {"retire": 1, "upsert": 1})

    def test_explicit_removal_produces_confirmed_delete_operations(self):
        catalog = {"schema_version": 1, "paints": [paint("a-old", "The Army Painter")]}
        refreshed = {"coverage": [{"brand": "The Army Painter", "complete": True}], "paints": []}

        changeset = build_refresh_changeset(
            catalog, refreshed, brand="The Army Painter", verified_at="2026-08-30", remove_missing=True
        )

        operation = changeset["operations"][0]
        self.assertEqual(operation["action"], "delete")
        self.assertTrue(operation["confirmed_removal"])

    def test_technical_paint_requires_usage_instructions(self):
        catalog = {"schema_version": 1, "paints": [paint("a-old", "The Army Painter")]}
        technical = paint("technical", "The Army Painter")
        technical["profile"] = technical["profile"] | {
            "roles": ["technical_effect"], "application_system": "effect_application",
        }
        refreshed = {"coverage": [{"brand": "The Army Painter", "complete": False}], "paints": [technical]}

        with self.assertRaisesRegex(ValueError, "usage_instructions"):
            build_refresh_changeset(catalog, refreshed, brand="The Army Painter")


if __name__ == "__main__":
    unittest.main()
