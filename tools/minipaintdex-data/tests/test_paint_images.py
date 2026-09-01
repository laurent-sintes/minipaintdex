import io
import unittest
import uuid
from pathlib import Path

from minipaintdex_data.paint_images import build_image_cache_changeset, build_image_source_changeset


def paint(source_url: str) -> dict:
    return {
        "schema_version": 2,
        "id": "vallejo-xpress-color-72-401",
        "brand": "Vallejo",
        "manufacturer": "Acrylicos Vallejo",
        "range": "Xpress Color",
        "reference": "72.401",
        "name": "Templar White",
        "profile": {
            "roles": ["color_paint"],
            "application_methods": ["brush"],
            "application_system": "one_coat_shading",
            "coverage": "transparent",
            "finish": "matte",
            "effects": [],
            "undercoat": {"tone": "light", "pre_highlighted_surface_recommended": True},
            "medium": "water_based_acrylic",
        },
        "manufacturer_image": {"path": "", "source_url": source_url, "credit": "Official Vallejo catalogue"},
    }


class PaintImageCacheTest(unittest.TestCase):
    @staticmethod
    def temporary_directory():
        root = Path("target") / "python-tests" / uuid.uuid4().hex
        root.mkdir(parents=True, exist_ok=True)
        return root

    def test_caches_valid_official_image_and_builds_changeset(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is not installed")
        source = io.BytesIO()
        Image.new("RGB", (600, 500), "white").save(source, format="PNG")

        directory = self.temporary_directory()
        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/official.png")]},
            directory,
            fetch_image=lambda url, maximum: (source.getvalue(), "image/png", url),
            workers=1,
        )

        self.assertEqual(report["counts"], {"cached": 1}, report)
        self.assertEqual(len(changeset["operations"]), 1)
        path = changeset["operations"][0]["record"]["manufacturer_image"]["path"]
        self.assertEqual(path, "/media/market/paints/vallejo/vallejo-xpress-color-72-401.webp")
        self.assertTrue((directory / path.removeprefix("/media/")).is_file())

    def test_rejects_non_official_image_host(self):
        directory = self.temporary_directory()
        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://example.test/untrusted.png")]},
            directory, workers=1,
        )

        self.assertEqual(report["counts"], {"rejected_host": 1})
        self.assertEqual(changeset["operations"], [])

    def test_caches_sanitized_official_svg(self):
        source = b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 50 100"><path d="M0 0h50v100H0z"/></svg>'
        directory = self.temporary_directory()
        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/official.svg")]},
            directory,
            fetch_image=lambda url, maximum: (source, "image/svg+xml", url),
            workers=1,
        )

        self.assertEqual(report["counts"], {"cached": 1})
        path = changeset["operations"][0]["record"]["manufacturer_image"]["path"]
        self.assertTrue(path.endswith(".svg"))
        self.assertEqual(report["items"][0]["format"], "svg")

    def test_rejects_active_svg(self):
        source = b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 50 100"><script>alert(1)</script></svg>'
        directory = self.temporary_directory()
        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/official.svg")]},
            directory,
            fetch_image=lambda url, maximum: (source, "image/svg+xml", url),
            workers=1,
        )

        self.assertEqual(report["counts"], {"failed": 1})
        self.assertEqual(changeset["operations"], [])

    def test_imports_verified_official_image_source(self):
        changeset = build_image_source_changeset(
            {"paints": [paint("")]},
            {
                "schema_version": 1,
                "brand": "Vallejo",
                "source_url": "https://acrylicosvallejo.com/en/category/hobby/xpress-color-en/",
                "items": [{
                    "reference": "72.401",
                    "name": "Templar White",
                    "page_url": "https://acrylicosvallejo.com/en/product/hobby/xpress-color-en/templar-white-72401/",
                    "image_url": "https://acrylicosvallejo.com/wp-content/uploads/2024/04/vallejo-xpress-color-72401-NewIC-600x600.jpg",
                }],
            },
            verified_at="2026-09-01",
        )

        record = changeset["operations"][0]["record"]
        self.assertIn("72401-NewIC", record["manufacturer_image"]["source_url"])
        self.assertEqual(record["source_snapshots"][-1]["provider"], "official_image_manifest")

    def test_reports_unmatched_official_references_when_allowed(self):
        changeset = build_image_source_changeset(
            {"paints": [paint("")]},
            {
                "schema_version": 1,
                "brand": "Vallejo",
                "source_url": "https://acrylicosvallejo.com/en/category/hobby/xpress-color-en/",
                "items": [{
                    "reference": "72.999",
                    "name": "Future Color",
                    "page_url": "https://acrylicosvallejo.com/en/product/hobby/future-color-72999/",
                    "image_url": "https://acrylicosvallejo.com/wp-content/uploads/future-color-72999.jpg",
                }],
            },
            allow_unmatched=True,
        )

        self.assertEqual(changeset["operations"], [])
        self.assertEqual(changeset["source"]["unmatched_references"], ["72.999"])


if __name__ == "__main__":
    unittest.main()
