from __future__ import annotations

import json
import os
import shutil
import unittest
from pathlib import Path

import yaml

from minipaintdex_data.datasets import create_dataset, validate_dataset


class DatasetTests(unittest.TestCase):
    def setUp(self) -> None:
        run_id = f"{os.getpid()}-{self._testMethodName}"
        self.root = Path("tools/minipaintdex-data/target/test-datasets") / run_id
        self.root = self.root.resolve()
        shutil.rmtree(self.root, ignore_errors=True)
        self.root.mkdir(parents=True, exist_ok=True)
        self.datasets = self.root / "datasets"
        self._yaml(
            "data/market/paints/brand-a.yaml",
            {"schema_version": 2, "brand": "Brand A", "paints": [
                {"id": "brand-a-red", "brand": "Brand A", "name": "Red"},
            ]},
        )
        self._yaml(
            "data/market/paints/brand-b.yaml",
            {"schema_version": 2, "brand": "Brand B", "paints": [
                {"id": "brand-b-blue", "brand": "Brand B", "name": "Blue"},
            ]},
        )
        self._yaml(
            "data/workshop/paints.yaml",
            {"schema_version": 1, "paints": [{"paint_id": "brand-a-red", "quantity": 2}]},
        )
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
            create_dataset(self.root, self.datasets, "workshop.paints", "My paints"),
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
        dataset = create_dataset(self.root, self.datasets, "workshop.paints", "My paints")
        payload = dataset / "payload/change-set.json"
        payload.write_text(payload.read_text(encoding="utf-8") + " ", encoding="utf-8")

        self.assertIn("payload sha256 does not match", validate_dataset(dataset))

    def test_refuses_to_overwrite_without_explicit_replace(self) -> None:
        create_dataset(self.root, self.datasets, "workshop.paints", "My paints")
        with self.assertRaisesRegex(ValueError, "already exists"):
            create_dataset(self.root, self.datasets, "workshop.paints", "My paints")

    def _yaml(self, relative: str, value: object) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
