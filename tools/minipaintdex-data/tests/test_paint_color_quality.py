from copy import deepcopy
import hashlib
import json
from pathlib import Path
import unittest
from unittest.mock import patch

from minipaintdex_data.paint_color_quality import audit_color_quality, build_reviewed_color_corrections, digital_lab
from minipaintdex_data.paint_colors import _read_source_records
from test_paint_colors import paint


class PaintColorQualityTest(unittest.TestCase):
    def test_pinned_followup_reviews_are_complete_valid_and_idempotent(self):
        path = Path(__file__).resolve().parents[1] / "resources/paint-quality-followup-review.json"
        review = json.loads(path.read_text(encoding="utf-8"))
        fixtures = {}
        for decision in review["field_corrections"]:
            identifier = decision["id"]
            if identifier not in fixtures:
                fixtures[identifier] = paint(decision["brand"], decision["reference"], decision["name"], decision["range"], color="#112233")
                fixtures[identifier]["id"] = identifier
            section, field = decision["field"].split(".")
            fixtures[identifier][section][field] = deepcopy(decision["before"])
        changeset, audit = build_reviewed_color_corrections({"paints": list(fixtures.values())}, path)
        self.assertEqual(252, len(changeset["operations"]))
        self.assertEqual(339, len(audit["items"]))
        applied = {"paints": [op["record"] for op in changeset["operations"]], "paint_usage_guides": changeset["paint_usage_guides"]}
        self.assertEqual([], build_reviewed_color_corrections(applied, path)[0]["operations"])
        by_id = {p["id"]: p for p in applied["paints"]}
        for number in range(141, 161):
            shade = by_id[f"val-77-{number}"]
            self.assertEqual([], shade["profile"]["effects"])
            self.assertEqual("satin", shade["profile"]["finish"])
            self.assertEqual(["wash"], shade["profile"]["roles"])
            self.assertEqual(["val-tmm-shade-usage"], shade["usage_guide_ids"])
        self.assertNotIn("cit-prod4190289-99189960108", by_id)
        self.assertNotIn("cit-prod4190290-99189960109", by_id)

    def test_continuous_lab_matches_srgb_reference_points(self):
        self.assertEqual({"l": 0.0, "a": 0.0, "b": 0.0}, digital_lab("#000000"))
        self.assertAlmostEqual(100, digital_lab("#ffffff")["l"], places=2)
        red = digital_lab("#ff0000")
        self.assertAlmostEqual(53.23, red["l"], places=2)
        self.assertAlmostEqual(80.11, red["a"], places=2)
        with self.assertRaises(ValueError):
            digital_lab("#fff")

    def test_audit_preserves_inputs_and_distinguishes_evidence_from_completeness(self):
        p = paint("Vallejo", "1", "Metallic Red", "Test", color="#ff0000")
        p["source_snapshots"] = [{"provider": "test", "url": "https://example.test",
                                  "payload": {"source_record": {"hex": "#FF0000"}}}]
        aux = paint("Vallejo", "2", "Medium", "Test", role="medium")
        aux["color"]["family"] = "auxiliary"
        original = deepcopy([p, aux])
        audit = audit_color_quality({"paints": [p, aux]})
        self.assertEqual(original, [p, aux])
        self.assertEqual(1, audit["summary"]["valid-hex"])
        self.assertEqual(1, audit["summary"]["missing-family"])
        self.assertEqual(1, audit["summary"]["metallic-label-without-effect"])
        self.assertNotIn("missing-hex", audit["summary"])
        self.assertNotIn("hex-without-explicit-color-evidence", audit["summary"])
        self.assertIsNone(audit["items"][1]["digital_lab"])
        self.assertEqual(audit, audit_color_quality({"paints": [aux, p]}))

    def test_audit_reports_conflicting_sources_invalid_hex_and_unknowns(self):
        p = paint("Vallejo", "1", "Test", "Test", color="#112233")
        p["profile"]["finish"] = "unknown"
        p["source_snapshots"] = [{"payload": {"source_record": {"hex": "#ffffff"}}}]
        audit = audit_color_quality({"paints": [p]})
        for issue in ("hex-source-disagreement", "hex-without-explicit-color-evidence", "unknown-finish"):
            self.assertEqual(1, audit["summary"][issue])
        p["color"]["hex"] = "invalid"
        self.assertIsNone(audit_color_quality({"paints": [p]})["items"][0]["digital_lab"])

    def build(self, paints, review):
        with patch.object(Path, "read_bytes", return_value=json.dumps(review).encode()):
            return build_reviewed_color_corrections({"paints": paints}, Path("review.json"))

    def fixture(self):
        p = paint("Vallejo", "71.062", "Aluminium (Metallic)", "Model Air", color="#acacb6")
        p["color"]["family"] = "Acier moyen"
        p["manufacturer_page"] = "https://example.test/paint"
        p["source_snapshots"] = [{"provider": "vallejo_catalogue_pdf", "url": "https://example.test/catalog.pdf",
                                  "payload": {"reference": p["reference"], "name": p["name"], "page": 28}}]
        identity = {k: p[k] for k in ("id", "brand", "reference")}
        review = {"schema_version": 1, "id": "review", "reviewed_at": "2026-09-03",
                  "family_corrections": [{**identity, "before": "Acier moyen", "after": "silver"}],
                  "metallic_corrections": [{**identity, "name": p["name"]}]}
        return p, review

    def test_review_merges_fields_preserves_all_other_data_and_replays_without_operations(self):
        p, review = self.fixture()
        original = deepcopy(p)
        changeset, audit = self.build([p], review)
        self.assertEqual(1, audit["operation_count"])
        self.assertEqual(2, len(audit["items"]))
        result = changeset["operations"][0]["record"]
        self.assertEqual("silver", result["color"]["family"])
        self.assertEqual(["metallic"], result["profile"]["effects"])
        self.assertEqual(p, original)
        restored = deepcopy(result)
        restored["color"] = original["color"]
        restored["profile"] = original["profile"]
        restored["source_snapshots"] = original["source_snapshots"]
        self.assertEqual(original, restored)
        self.assertEqual([], self.build([result], review)[0]["operations"])
        with patch.object(Path, "read_bytes", return_value=(json.dumps(review, indent=2) + "\r\n").encode()):
            self.assertEqual([], build_reviewed_color_corrections({"paints": [result]}, Path("review.json"))[0]["operations"])

    def test_review_blocks_stale_or_unproven_decisions(self):
        for change in (lambda p: p.update(reference="wrong"),
                       lambda p: p["color"].update(family="blue"),
                       lambda p: p.update(source_snapshots=[]),
                       lambda p: p["profile"].update(effects=["fluorescent"])):
            p, review = self.fixture()
            change(p)
            with self.assertRaises(ValueError):
                self.build([p], review)

    def test_source_lf_equivalence_requires_explicit_pinned_hash(self):
        content = b'[{"hex":"#123456"}]\n'
        raw_hash = hashlib.sha256(content.replace(b'\n', b'\r\n')).hexdigest()
        config = {"file": "test.json", "sha256": raw_hash}
        with patch.object(Path, "is_file", return_value=True), \
             patch.object(Path, "read_bytes", return_value=content), \
             patch.object(Path, "read_text", return_value=content.decode()):
            with self.assertRaises(ValueError):
                _read_source_records(Path("."), config)
            config["sha256_text_lf"] = hashlib.sha256(content).hexdigest()
            self.assertEqual([{"hex": "#123456"}], _read_source_records(Path("."), config))
            with patch.object(Path, "read_bytes", return_value=content.replace(b'123456', b'654321')):
                with self.assertRaises(ValueError):
                    _read_source_records(Path("."), config)

    def test_explicit_field_review_can_replace_hex_and_qualify_unchanged_values(self):
        p = paint("Vallejo", "1", "Test", "Test", color="#112233")
        identity = {k: p[k] for k in ("id", "brand", "reference", "name", "range")}
        review = {"schema_version": 1, "id": "review", "reviewed_at": "2026-09-03",
                  "field_corrections": [{**identity, "field": "color.hex", "before": "#112233", "after": "#223344",
                                         "evidence": {"url": "https://example.test/chart", "rationale": "Reviewed chart",
                                                      "source_record": {"hex": "#223344"}}}]}
        result = self.build([p], review)[0]["operations"][0]["record"]
        self.assertEqual("#223344", result["color"]["hex"])
        self.assertEqual([], self.build([result], review)[0]["operations"])
        self.assertNotIn("hex-without-explicit-color-evidence", audit_color_quality({"paints": [result]})["summary"])
        review["field_corrections"][0]["before"] = "#223344"
        review["id"] = "confirm"
        result2 = self.build([result], review)[0]["operations"][0]["record"]
        self.assertEqual(2, len(result2["source_snapshots"]))
        review["field_corrections"][0]["after"] = "#ffffff"
        with self.assertRaises(ValueError):
            self.build([result], review)
