from copy import deepcopy
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from minipaintdex_data.paint_hsl import classify_hex, load_policy
from minipaintdex_data.paint_quality_protection import preserve_qualified_data
from minipaintdex_data.official_sources.common import merge_previous
from minipaintdex_data.refresh import build_refresh_changeset
from test_paint_colors import paint


class PaintHslTest(unittest.TestCase):
    def test_reference_points_and_achromatic_hue(self):
        policy, digest = load_policy()
        self.assertEqual(64, len(digest))
        for hex_color, family, hue in (("#ff0000", "red", 0), ("#ffff00", "yellow", 60),
                                       ("#00ff00", "green", 120), ("#00ffff", "cyan", 180),
                                       ("#0000ff", "blue", 240), ("#ff00ff", "magenta", 300)):
            result = classify_hex(hex_color, policy)
            self.assertEqual(family, result["hue_group"])
            self.assertEqual(hue, result["h"])
            self.assertEqual(1, result["s"])
            self.assertEqual(.5, result["l"])
        for value in ("#000000", "#ffffff", "#808080"):
            result = classify_hex(value, policy)
            self.assertEqual("neutral", result["hue_group"])
            self.assertIsNone(result["h"])
        for value in ("", "#fff", "#gggggg", None):
            with self.assertRaises(ValueError):
                classify_hex(value, policy)

    def test_whole_rgb_cube_sample_has_one_stable_swatch_and_near_boundary_flag(self):
        policy, _ = load_policy()
        for r in range(0, 256, 17):
            for g in range(0, 256, 17):
                for b in range(0, 256, 17):
                    result = classify_hex(f"#{r:02x}{g:02x}{b:02x}", policy)
                    self.assertRegex(result["swatch_hex"], r"^#[0-9a-f]{6}$")
        self.assertIn("hue-boundary", classify_hex("#ff4000", policy)["review_reasons"])
        self.assertIn("lightness-boundary", classify_hex("#666666", policy)["review_reasons"])

    def test_policy_rejects_gaps_overlaps_and_invalid_centers(self):
        policy, _ = load_policy()
        for modify in (lambda p: p["hue_bins"][0].update(end=16),
                       lambda p: p["hue_bins"][0].update(end=14),
                       lambda p: p.update(lightness_centers=[.5]),
                       lambda p: p.update(saturation_edges=[.7, .3]),
                       lambda p: p.update(neutral_saturation_max=.9)):
            broken = deepcopy(policy)
            modify(broken)
            with patch.object(Path, "read_bytes", return_value=json.dumps(broken).encode()), self.assertRaises(ValueError):
                load_policy()

    def test_refresh_preserves_qualified_values_all_sources_and_identity(self):
        previous = paint("Vallejo", "1", "Metallic Red", "Test", color="#112233")
        previous["profile"]["effects"] = ["metallic"]
        previous["source_snapshots"] = [{"provider": "reviewed-paint-color-quality", "url": "https://example.test/review", "payload": {"field": "effects", "after": ["metallic"]}}]
        incoming = deepcopy(previous)
        incoming["color"]["hex"] = "#ffffff"
        incoming["profile"]["effects"] = []
        incoming["source_snapshots"] = [{"provider": "new-official-observation", "url": "https://example.test/new", "payload": {"name": "Metallic Red"}}]
        original = deepcopy(incoming)
        for result in (preserve_qualified_data(incoming, previous), merge_previous(incoming, previous),
                       build_refresh_changeset({"paints": [previous]}, {"paints": [incoming]},
                                               brand="Vallejo", verified_at="2026-09-03")["operations"][0]["record"]):
            self.assertEqual("#112233", result["color"]["hex"])
            self.assertEqual(["metallic"], result["profile"]["effects"])
            self.assertEqual(2, len(result["source_snapshots"]))
            self.assertEqual(result, preserve_qualified_data(result, previous))
        self.assertEqual(original, incoming)
