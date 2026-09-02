import io
import unittest
import uuid
from pathlib import Path

import yaml

from minipaintdex_data.assets import audit_assets
from minipaintdex_data.paint_images import (
    build_image_cache_changeset, build_image_source_changeset, rekey_cached_paint_images,
)
from minipaintdex_data.image_quality import plan_image_rechallenge


def paint(source_url: str) -> dict:
    quality = "official_photo" if source_url else "none"
    return {
        "schema_version": 1,
        "id": "val-72-401",
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
        "manufacturer_image": {
            "path": "", "source_url": source_url, "credit": "Official Vallejo catalogue",
            "image_quality": quality,
            "quality_verified_at": "2026-09-01" if source_url else "",
            **({} if source_url else {"quality_limitation": {
                "code": "historical-reason-not-recorded",
                "detail": "The precise historical reason was not recorded.",
                "observed_at": "2026-09-01",
            }}),
        },
    }


class PaintImageCacheTest(unittest.TestCase):
    @staticmethod
    def temporary_directory():
        root = Path("target") / "python-tests" / uuid.uuid4().hex
        root.mkdir(parents=True, exist_ok=True)
        return root

    def test_caches_valid_official_image_and_builds_changeset(self):
        try:
            from PIL import Image, ImageDraw
        except ImportError:
            self.skipTest("Pillow is not installed")
        source = io.BytesIO()
        image = Image.new("RGB", (600, 500), "white")
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((180, 100, 420, 470), radius=45, fill="#a92222", outline="black", width=8)
        draw.rectangle((220, 45, 380, 140), fill="#202020")
        image.save(source, format="PNG")

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
        self.assertEqual(path, "/media/market/paints/vallejo/val-72-401.webp")
        self.assertTrue((directory / path.removeprefix("/media/")).is_file())
        self.assertEqual((report["items"][0]["width"], report["items"][0]["height"]), (800, 800))
        self.assertEqual(report["items"][0]["presentation_canvas"], "square")

    def test_rekeys_existing_cached_image(self):
        directory = self.temporary_directory()
        source = directory / "market" / "paints" / "vallejo" / "legacy-id.webp"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_bytes(b"packshot")
        record = paint("")
        record["id"] = "val-72-401"
        record["manufacturer_image"]["path"] = "/media/market/paints/vallejo/val-72-401.webp"
        changeset = {
            "schema_version": 1,
            "kind": "market_paints",
            "operations": [{
                "action": "rekey", "previous_id": "legacy-id", "record": record,
                "workshop_quantity_delta": 0,
            }],
        }

        report = rekey_cached_paint_images(changeset, directory)

        self.assertEqual(report["moved_count"], 1)
        self.assertFalse(source.exists())
        self.assertEqual(
            (directory / "market" / "paints" / "vallejo" / "val-72-401.webp").read_bytes(),
            b"packshot",
        )

    def test_keeps_a_packshot_on_a_large_plain_background(self):
        try:
            from PIL import Image, ImageDraw
        except ImportError:
            self.skipTest("Pillow is not installed")
        source = io.BytesIO()
        image = Image.new("RGB", (800, 800), "white")
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((400, 220, 625, 715), radius=35, fill="#e5e5e5", outline="#202020", width=8)
        draw.rectangle((435, 165, 590, 290), fill="#181818")
        draw.rectangle((420, 390, 605, 620), fill="#555555")
        for index in range(24):
            red = 40 + index * 5
            draw.line((435, 405 + index * 7, 590, 405 + index * 7), fill=(red, red + 4, red + 8), width=3)
        image.save(source, format="PNG")

        directory = self.temporary_directory()
        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/plain-background.png")]},
            directory,
            fetch_image=lambda url, maximum: (source.getvalue(), "image/png", url),
            workers=1,
        )

        self.assertEqual(report["counts"], {"cached": 1}, report)
        self.assertEqual(len(changeset["operations"]), 1)

    def test_rejects_flat_colour_artwork_as_a_packshot(self):
        try:
            from PIL import Image, ImageDraw
        except ImportError:
            self.skipTest("Pillow is not installed")
        source = io.BytesIO()
        image = Image.new("RGB", (800, 800), "#efb632")
        draw = ImageDraw.Draw(image)
        draw.rectangle((575, 565, 735, 760), outline="black", width=3)
        image.save(source, format="PNG")

        directory = self.temporary_directory()
        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/swatch.png")]},
            directory,
            fetch_image=lambda url, maximum: (source.getvalue(), "image/png", url),
            workers=1,
        )

        self.assertEqual(report["counts"], {"failed": 1})
        self.assertIn("flat colour artwork", report["items"][0]["error"])
        self.assertEqual(len(changeset["operations"]), 1)
        limitation = changeset["operations"][0]["record"]["manufacturer_image"]["quality_limitation"]
        self.assertEqual(limitation["code"], "official-candidate-rejected")

    def test_rejects_checkerboard_background_as_a_product_photo(self):
        try:
            from PIL import Image, ImageDraw
        except ImportError:
            self.skipTest("Pillow is not installed")
        image = Image.new("RGB", (600, 600), "white")
        draw = ImageDraw.Draw(image)
        size = 50
        for y in range(0, 600, size):
            for x in range(0, 600, size):
                draw.rectangle((x, y, x + size, y + size), fill="#d0d0d0" if (x // size + y // size) % 2 else "#ffffff")
        source = io.BytesIO()
        image.save(source, format="PNG")

        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/checker.png")]},
            self.temporary_directory(),
            fetch_image=lambda url, maximum: (source.getvalue(), "image/png", url), workers=1,
        )

        self.assertEqual(len(changeset["operations"]), 1)
        self.assertEqual(
            changeset["operations"][0]["record"]["manufacturer_image"]["quality_limitation"]["code"],
            "official-candidate-rejected",
        )
        self.assertIn("checkerboard_background", report["items"][0]["error"])

    def test_plans_lower_quality_and_stale_official_images(self):
        lower = paint("")
        lower["manufacturer_image"].update({"image_quality": "color_swatch", "quality_verified_at": "2026-08-01"})
        stale = paint("https://acrylicosvallejo.com/official.png")
        stale["id"] = "val-72-402"
        stale["reference"] = "72.402"
        stale["manufacturer_image"].update({"image_quality": "official_photo", "quality_verified_at": "2025-08-31"})
        fresh = paint("https://acrylicosvallejo.com/fresh.png")
        fresh["id"] = "val-72-403"
        fresh["reference"] = "72.403"
        fresh["manufacturer_image"].update({"image_quality": "official_photo", "quality_verified_at": "2026-08-31"})

        report = plan_image_rechallenge(
            {"paints": [lower, stale, fresh]}, as_of="2026-09-01", official_max_age_days=365,
        )

        self.assertEqual(report["candidate_count"], 2)
        self.assertEqual(
            {item["reason"] for item in report["items"]},
            {"better_quality_available", "official_photo_older_than_policy"},
        )

    def test_asset_audit_reports_rejected_artwork_with_technical_reasons(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is not installed")
        root = self.temporary_directory()
        image_path = root / "media" / "market" / "paints" / "vallejo" / "flat.webp"
        image_path.parent.mkdir(parents=True)
        Image.new("RGB", (600, 600), "#778899").save(image_path, format="WEBP")
        catalog_path = root / "data" / "market" / "paints" / "vallejo.yaml"
        catalog_path.parent.mkdir(parents=True)
        catalog_path.write_text(yaml.safe_dump({
            "paints": [{
                "id": "flat", "brand": "Vallejo",
                "manufacturer_image": {"path": "/media/market/paints/vallejo/flat.webp"},
            }],
        }), encoding="utf-8")

        report = audit_assets(root)

        self.assertEqual(report["paint_images"]["by_brand"]["Vallejo"], {"color_swatch": 1})
        self.assertEqual(report["rejected_artwork"][0]["path"], "/media/market/paints/vallejo/flat.webp")
        self.assertIn("flat_colour_artwork", report["rejected_artwork"][0]["reasons"])

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
        self.assertEqual(len(changeset["operations"]), 1)

    def test_rejects_low_complexity_neutral_pot_silhouette(self):
        from PIL import Image, ImageDraw

        source = Image.new("RGB", (600, 600), "white")
        draw = ImageDraw.Draw(source)
        for x in range(180, 420):
            shade = 20 + (x - 180) // 2
            draw.rectangle((x, 170, x, 520), fill=(shade, shade, shade))
        buffer = io.BytesIO()
        source.save(buffer, format="JPEG")
        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/silhouette.jpg")]},
            self.temporary_directory(),
            fetch_image=lambda url, maximum: (buffer.getvalue(), "image/jpeg", url),
            workers=1,
        )

        self.assertEqual(report["counts"], {"failed": 1})
        self.assertIn("low_complexity_neutral_artwork", report["items"][0]["error"])
        self.assertEqual(len(changeset["operations"]), 1)

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

    def test_accepts_shopify_cdn_for_a_credited_retailer_photo(self):
        changeset = build_image_source_changeset(
            {"paints": [paint("")]},
            {
                "schema_version": 1,
                "brand": "Vallejo",
                "image_quality": "retailer_photo",
                "source_url": "https://example-paints.test/collections/vallejo",
                "items": [{
                    "reference": "72.401",
                    "name": "Templar White",
                    "page_url": "https://example-paints.test/products/templar-white",
                    "image_url": "https://cdn.shopify.com/s/files/example/templar-white.jpg",
                    "credit": "Example Paints",
                }],
            },
            verified_at="2026-09-01",
        )

        image = changeset["operations"][0]["record"]["manufacturer_image"]
        self.assertEqual(image["image_quality"], "retailer_photo")
        self.assertEqual(image["credit"], "Example Paints")
        self.assertEqual(image["quality_limitation"]["code"], "better-source-not-found")

    def test_keeps_a_detailed_monochrome_product_photo(self):
        from PIL import Image, ImageDraw

        source = Image.new("RGB", (800, 800), "white")
        draw = ImageDraw.Draw(source)
        draw.rounded_rectangle((300, 120, 520, 710), radius=45, fill="#ededed", outline="#303030", width=7)
        for y in range(210, 610, 24):
            draw.line((320, y, 500, y), fill="#777777", width=4)
        draw.rectangle((335, 390, 485, 530), fill="white", outline="black", width=5)
        draw.text((365, 440), "P842", fill="black")
        buffer = io.BytesIO()
        source.save(buffer, format="PNG")

        changeset, report = build_image_cache_changeset(
            {"paints": [paint("https://acrylicosvallejo.com/white-product.png")]},
            self.temporary_directory(),
            fetch_image=lambda url, maximum: (buffer.getvalue(), "image/png", url), workers=1,
        )

        self.assertEqual(report["counts"], {"cached": 1})
        self.assertEqual(len(changeset["operations"]), 1)

    def test_normalizes_an_existing_local_raster_without_downloading_it(self):
        try:
            from PIL import Image, ImageDraw
        except ImportError:
            self.skipTest("Pillow is not installed")
        directory = self.temporary_directory()
        local = directory / "market" / "paints" / "vallejo" / "val-72-401.webp"
        local.parent.mkdir(parents=True)
        image = Image.new("RGB", (600, 400), "white")
        draw = ImageDraw.Draw(image)
        draw.rectangle((180, 40, 420, 360), fill="#a92222", outline="black", width=8)
        image.save(local, format="WEBP")
        record = paint("https://acrylicosvallejo.com/official.png")
        record["manufacturer_image"]["path"] = "/media/market/paints/vallejo/val-72-401.webp"

        changeset, report = build_image_cache_changeset(
            {"paints": [record]}, directory, normalize_local=True, workers=1,
            fetch_image=lambda *_: self.fail("The remote source must not be downloaded."),
        )

        self.assertEqual(changeset["operations"], [])
        self.assertEqual(report["counts"], {"normalized_local": 1})
        with Image.open(local) as normalized:
            self.assertEqual(normalized.size, (800, 800))


if __name__ == "__main__":
    unittest.main()
