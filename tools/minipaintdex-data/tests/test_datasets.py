from __future__ import annotations

import json
import os
import shutil
import unittest
from pathlib import Path

import yaml

from minipaintdex_data.datasets import create_dataset, validate_dataset


class DatasetTests(unittest.TestCase):
    def test_brand_dataset_preserves_commercial_editions(self):
        from minipaintdex_data.datasets import build_payload
        edition = {"schema_version": 1, "id": "brand-a-2019", "brand": "Brand A", "title": "Catalogue",
                   "edition_label": "2019", "ranges": ["Range"], "source_urls": ["https://example.com/catalog"]}
        guide = {"id": "brand-a-usage", "brand": "Brand A"}
        self._yaml("data/market/paints/brand-a.yaml", {"schema_version": 1, "brand": "Brand A", "catalog_editions": [edition], "paint_usage_guides": [guide],
                   "paints": [{"schema_version": 1, "id": "brand-a-red", "brand": "Brand A", "name": "Red"}]})
        payload, _ = build_payload(self.root, "market.paint-brand", brand="Brand A")
        self.assertEqual(payload["catalog_editions"], [edition])
        self.assertEqual(payload["paint_usage_guides"], [guide])

    def setUp(self) -> None:
        run_id = f"{os.getpid()}-{self._testMethodName}"
        self.root = Path("tools/minipaintdex-data/target/test-datasets") / run_id
        self.root = self.root.resolve()
        shutil.rmtree(self.root, ignore_errors=True)
        self.root.mkdir(parents=True, exist_ok=True)
        self.datasets = self.root / "datasets"
        self._yaml(
            "data/market/paints/brand-a.yaml",
            {"schema_version": 1, "brand": "Brand A", "paints": [
                {"schema_version": 1, "id": "brand-a-red", "brand": "Brand A", "name": "Red"},
            ]},
        )
        self._yaml(
            "data/market/paints/brand-b.yaml",
            {"schema_version": 1, "brand": "Brand B", "paints": [
                {"schema_version": 1, "id": "brand-b-blue", "brand": "Brand B", "name": "Blue"},
            ]},
        )
        ledger = self.root / "data/ledger/events/2026-09.jsonl"
        ledger.parent.mkdir(parents=True)
        ledger.write_text("\n".join(json.dumps({"event_type": "paint_pot.registered", "aggregate_id": f"pot-{i}",
            "payload": {"paint_product_id": "brand-a-red"}}) for i in (1, 2)) + "\n", encoding="utf-8")
        self._yaml(
            "data/market/paintable-products/game.yaml",
            {"schema_version": 1, "id": "game", "name": "Game", "catalog_items": []},
        )
        self._yaml(
            "data/market/painting-guides/game.yaml",
            {"schema_version": 1, "painting_guides": [{"id": "game-guide"}]},
        )

    def tearDown(self) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    def test_creates_each_supported_dataset_category(self) -> None:
        created = [
            create_dataset(self.root, self.datasets, "market.paint-brand", "Brand A", brand="Brand A"),
            create_dataset(self.root, self.datasets, "market.paintable-product", "Game", product_id="game"),
            create_dataset(self.root, self.datasets, "workshop.paint-pots", "My paints"),
            create_dataset(
                self.root, self.datasets, "workshop.painting-project", "Game project",
                product_id="game", project_id="paint-game", project_name="Paint Game",
            ),
        ]
        for dataset in created:
            self.assertEqual([], validate_dataset(dataset))

        payload = json.loads((created[0] / "payload/change-set.json").read_text(encoding="utf-8"))
        self.assertEqual(["brand-a-red"], [operation["record"]["id"] for operation in payload["operations"]])

    def test_detects_payload_tampering(self) -> None:
        dataset = create_dataset(self.root, self.datasets, "workshop.paint-pots", "My paints")
        payload = dataset / "payload/change-set.json"
        payload.write_text(payload.read_text(encoding="utf-8") + " ", encoding="utf-8")

        self.assertIn("payload sha256 does not match", validate_dataset(dataset))

    def test_refuses_to_overwrite_without_explicit_replace(self) -> None:
        create_dataset(self.root, self.datasets, "workshop.paint-pots", "My paints")
        with self.assertRaisesRegex(ValueError, "already exists"):
            create_dataset(self.root, self.datasets, "workshop.paint-pots", "My paints")

    def _yaml(self, relative: str, value: object) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
