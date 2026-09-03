import unittest
from minipaintdex_data.catalog_editions import validate_editions, validate_memberships
from minipaintdex_data.changesets import build_paint_changeset, validate_changeset


class CatalogEditionTests(unittest.TestCase):
    def test_edition_does_not_require_a_year_but_requires_sources(self):
        edition = {"schema_version": 1, "id": "brand-summer", "brand": "Brand", "title": "Catalogue",
                   "edition_label": "Summer", "ranges": ["Range"], "source_urls": ["https://example.com/catalog"]}
        self.assertEqual(validate_editions([edition]), [])
        self.assertTrue(validate_editions([edition | {"source_urls": []}]))
        self.assertTrue(validate_editions([edition, edition]))
        self.assertEqual(validate_changeset({"schema_version": 1, "kind": "market_paints",
                         "operations": [], "catalog_editions": [edition]}), [])
        changeset = build_paint_changeset({"paints": [], "catalog_editions": [edition]},
                                         source="publication.json")
        self.assertEqual(changeset["catalog_editions"], [edition])

    def test_membership_needs_identity_source_and_locator(self):
        membership = {"catalog_edition_id": "brand-summer", "source_url": "https://example.com/catalog", "locator": "page 2"}
        self.assertEqual(validate_memberships([membership]), [])
        self.assertTrue(validate_memberships([membership, membership]))
        self.assertTrue(validate_memberships([membership | {"locator": ""}]))
        self.assertTrue(validate_memberships([membership | {"source_url": "file:///private"}]))
