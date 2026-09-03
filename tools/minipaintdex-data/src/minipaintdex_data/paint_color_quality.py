"""Read-only colour qualification and explicitly reviewed Market corrections.

Continuous Lab coordinates mirror PaintMatchEngine (sRGB, D65). They describe
digital swatches, never measured paint or validated perceptual family boundaries.
"""

from __future__ import annotations

import hashlib
import json
import re
from collections import Counter
from copy import deepcopy
from pathlib import Path
from typing import Any

from .changesets import validate_changeset
from .paint_colors import AUXILIARY_TONE_ROLES, COLOR_BEARING_ROLES, HEX_COLOR
from .paint_hsl import classify_hex, load_policy
from .paint_quality_protection import reviewed_path


FAMILIES = frozenset("auxiliary beige black blue brown copper gold green grey orange pink purple red silver skin white yellow".split())
METALLIC_LABEL = re.compile(r"\bmetallic\b|\(metal\.\)", re.IGNORECASE)


def digital_lab(hex_color: str) -> dict[str, float]:
    if not HEX_COLOR.fullmatch(hex_color):
        raise ValueError("Lab requires a six-digit HEX colour")
    rgb = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    r, g, b = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    xyz = [(r * .4124 + g * .3576 + b * .1805) / .95047,
           r * .2126 + g * .7152 + b * .0722,
           (r * .0193 + g * .1192 + b * .9505) / 1.08883]
    x, y, z = [v ** (1 / 3) if v > .008856 else 7.787 * v + 16 / 116 for v in xyz]
    return {"l": round(116 * y - 16, 4), "a": round(500 * (x - y), 4), "b": round(200 * (y - z), 4)}


def audit_color_quality(catalog: dict[str, Any]) -> dict[str, Any]:
    """Report missingness and evidence separately; never assign a quality score."""
    brands: dict[str, Counter] = {}
    hsl_policy, hsl_hash = load_policy()
    items = []
    for paint in sorted(catalog["paints"], key=lambda p: p["id"]):
        count = brands.setdefault(paint["brand"], Counter())
        count["total"] += 1
        profile = paint.get("profile") or {}
        roles = set(profile.get("roles", []))
        auxiliary = bool(roles) and roles <= AUXILIARY_TONE_ROLES
        eligible = bool(roles & COLOR_BEARING_ROLES)
        color = paint.get("color") or {}
        family, hex_color = color.get("family") or "", color.get("hex") or ""
        issues = []
        if auxiliary:
            count["auxiliary"] += 1
            if family != "auxiliary" or hex_color:
                issues.append("invalid-auxiliary-color")
        elif eligible:
            count["color-bearing"] += 1
            if not hex_color:
                issues.append("missing-hex")
        else:
            issues.append("unclassified-role")
        valid_hex = bool(HEX_COLOR.fullmatch(hex_color))
        if hex_color and not valid_hex:
            issues.append("invalid-hex")
        if valid_hex:
            count["valid-hex"] += 1
        if not family:
            issues.append("missing-family")
        elif family not in FAMILIES:
            issues.append("noncanonical-family")
        else:
            count["canonical-family"] += 1
        sources = []
        reviewed_fields = set()
        source_hexes = set()
        for snapshot in paint.get("source_snapshots", []):
            payload = snapshot.get("payload") or {}
            if snapshot.get("provider") == "reviewed-paint-color-quality":
                path = reviewed_path(payload.get("field", ""))
                section, _, key = path.partition(".")
                if key and (paint.get(section) or {}).get(key) == payload.get("after"):
                    reviewed_fields.add(path)
            source_record = payload.get("source_record") or {}
            source_hex = str(source_record.get("hex") or "").lower()
            if HEX_COLOR.fullmatch(source_hex):
                source_hexes.add(source_hex)
                if source_hex == hex_color.lower():
                    sources.append({"provider": snapshot.get("provider"), "url": snapshot.get("url"),
                                    "accuracy": payload.get("accuracy", ""),
                                    "identity_match": payload.get("identity_match", "")})
        if valid_hex and not sources:
            issues.append("hex-without-explicit-color-evidence")
        if valid_hex and source_hexes - {hex_color.lower()}:
            issues.append("hex-source-disagreement")
        if METALLIC_LABEL.search(paint.get("name", "")) and "metallic" not in profile.get("effects", []):
            issues.append("metallic-label-without-effect")
        for field in ("coverage", "finish", "medium", "application_system"):
            value = profile.get(field, "unknown")
            count[f"{field}:{value}"] += 1
            if value == "unknown":
                issues.append(f"unknown-{field}")
            elif f"profile.{field}" not in reviewed_fields:
                count[f"{field}-without-explicit-review"] += 1
        hsl = classify_hex(hex_color, hsl_policy) if valid_hex and not auxiliary else None
        if hsl:
            count["hsl-classified"] += 1
            if hsl["review_reasons"]:
                count["hsl-boundary-review"] += 1
        for issue in issues:
            count[issue] += 1
        items.append({"paint_id": paint["id"], "brand": paint["brand"], "reference": paint.get("reference"),
                      "name": paint.get("name"), "range": paint.get("range"), "color": deepcopy(color),
                      "profile": deepcopy(profile), "issues": issues, "color_evidence": sources,
                      "reviewed_fields": sorted(reviewed_fields), "digital_hsl": hsl,
                      "digital_lab": digital_lab(hex_color) if valid_hex and not auxiliary else None})
    total = Counter()
    for values in brands.values():
        total.update(values)
    return {"schema_version": 1, "kind": "paint_color_quality_audit",
            "hsl_policy": {"id": hsl_policy["id"], "sha256": hsl_hash, "status": hsl_policy["status"]},
            "limitations": ["HEX and Lab describe digital approximations, not dried paint.",
                            "Missing explicit colour evidence does not prove a HEX is wrong.",
                            "Empty effects do not prove the absence of effects.",
                            "HSL swatch groups are proposals independent of existing lexical families.",
                            "No finish, coverage or effect is inferred from HEX, HSL or Lab."],
            "summary": dict(sorted(total.items())),
            "brands": {k: dict(sorted(v.items())) for k, v in sorted(brands.items())}, "items": items}


def build_reviewed_color_corrections(catalog: dict[str, Any], manifest_path: Path) -> tuple[dict, dict]:
    content = manifest_path.read_bytes()
    review = json.loads(content)
    if review.get("schema_version") != 1 or not review.get("id") or not review.get("reviewed_at"):
        raise ValueError("Invalid colour-quality review manifest")
    paints = {paint["id"]: paint for paint in catalog["paints"]}
    if len(paints) != len(catalog["paints"]):
        raise ValueError("Duplicate paint identities")
    updated = {}
    changes = []
    seen = set()
    for section, field in (("family_corrections", "family"), ("metallic_corrections", "effects")):
        for decision in review.get(section, []):
            identifier = decision["id"]
            canonical_path = reviewed_path(field)
            if (identifier, canonical_path) in seen:
                raise ValueError(f"Duplicate review: {identifier}/{field}")
            seen.add((identifier, canonical_path))
            current = paints.get(identifier)
            if current is None or any(current.get(k) != decision[k] for k in ("brand", "reference")):
                raise ValueError(f"Review identity mismatch: {identifier}")
            paint = updated.get(identifier, deepcopy(current))
            if field == "family":
                before, after = decision["before"], decision["after"]
                if after not in FAMILIES - {"auxiliary"} or before in FAMILIES:
                    raise ValueError(f"Not a reviewed free-label normalization: {identifier}")
                target = paint["color"]
                rationale = "Reviewed normalization of an existing descriptive family; not a HEX-derived classification."
                evidence = {"previous_family": before}
                url = paint.get("manufacturer_page", "")
            else:
                if current.get("name") != decision["name"] or not METALLIC_LABEL.search(decision["name"]):
                    raise ValueError(f"Review name mismatch: {identifier}")
                matches = [s for s in current.get("source_snapshots", [])
                           if s.get("provider") == "vallejo_catalogue_pdf"
                           and all(s.get("payload", {}).get(k) == decision[k] for k in ("reference", "name"))]
                if not matches:
                    raise ValueError(f"Missing retained official metallic evidence: {identifier}")
                before, after = [], ["metallic"]
                target = paint["profile"]
                evidence = deepcopy(matches[0])
                url = matches[0]["url"]
                rationale = "Explicit metallic designation in the retained official catalogue; finish and coverage unchanged."
            if target.get(field) == after:
                continue
            if target.get(field) != before:
                raise ValueError(f"Review precondition failed: {identifier}/{field}")
            if not url.startswith("https://"):
                raise ValueError(f"Missing traceable source URL: {identifier}")
            target[field] = after
            payload = {"review_id": review["id"], "reviewed_at": review["reviewed_at"],
                       "manifest_sha256": hashlib.sha256(content).hexdigest(), "field": field,
                       "before": before, "after": after, "rationale": rationale, "evidence": evidence}
            paint.setdefault("source_snapshots", []).append(
                {"provider": "reviewed-paint-color-quality", "url": url, "payload": payload})
            updated[identifier] = paint
            changes.append({"paint_id": identifier, **payload})
    for decision in review.get("field_corrections", []):
        identifier, field = decision["id"], decision["field"]
        allowed = {"color.hex", "color.family", "profile.finish", "profile.coverage", "profile.effects",
                   "profile.roles", "profile.application_system", "profile.application_methods"}
        if field not in allowed or (identifier, field) in seen:
            raise ValueError(f"Unsupported or duplicate field review: {identifier}/{field}")
        seen.add((identifier, field))
        current = paints.get(identifier)
        if current is None or any(current.get(k) != decision[k] for k in ("brand", "reference", "name", "range")):
            raise ValueError(f"Review identity mismatch: {identifier}")
        paint = updated.get(identifier, deepcopy(current))
        section, key = field.split(".")
        evidence = decision["evidence"]
        if not evidence.get("url", "").startswith("https://") or not evidence.get("rationale"):
            raise ValueError(f"Missing review evidence: {identifier}/{field}")
        if field == "color.hex" and (not HEX_COLOR.fullmatch(decision["after"])
                or evidence.get("source_record", {}).get("hex", "").lower() != decision["after"].lower()):
            raise ValueError(f"HEX review requires matching explicit source colour: {identifier}")
        payload = {"review_id": review["id"], "reviewed_at": review["reviewed_at"],
                   "manifest_sha256": hashlib.sha256(content).hexdigest(), "field": field,
                   "before": decision["before"], "after": decision["after"],
                   "rationale": evidence["rationale"], "evidence": deepcopy(evidence)}
        if field == "color.hex":
            payload.update(source_record=deepcopy(evidence["source_record"]),
                           accuracy=evidence.get("accuracy", "Approximate digital swatch; not a paint measurement."),
                           identity_match=evidence.get("identity_match", "reviewed-exact-reference"))
        snapshot = {"provider": "reviewed-paint-color-quality", "url": evidence["url"], "payload": payload}
        target = paint[section]
        same_decision = any(s.get("provider") == snapshot["provider"] and s.get("url") == snapshot["url"]
                            and {k: v for k, v in s.get("payload", {}).items() if k != "manifest_sha256"}
                            == {k: v for k, v in payload.items() if k != "manifest_sha256"}
                            for s in paint.get("source_snapshots", []))
        if target.get(key) == decision["after"] and same_decision:
            continue
        if target.get(key) != decision["before"]:
            raise ValueError(f"Review precondition failed: {identifier}/{field}")
        target[key] = deepcopy(decision["after"])
        paint.setdefault("source_snapshots", []).append(snapshot)
        updated[identifier] = paint
        changes.append({"paint_id": identifier, **payload})
    for assignment in review.get("usage_guide_assignments", []):
        identifier = assignment["id"]
        current = paints.get(identifier)
        if current is None or any(current.get(k) != assignment[k] for k in ("brand", "reference")):
            raise ValueError(f"Guide assignment identity mismatch: {identifier}")
        paint = updated.get(identifier, deepcopy(current))
        if paint.get("usage_guide_ids", []) == assignment["after"]:
            continue
        if paint.get("usage_guide_ids", []) != assignment["before"]:
            raise ValueError(f"Guide assignment precondition failed: {identifier}")
        paint["usage_guide_ids"] = deepcopy(assignment["after"])
        updated[identifier] = paint
    changeset = {"schema_version": 1, "kind": "market_paints", "source": {"provider": review["id"]},
                 "operations": [{"action": "upsert", "record": paint,
                                 "workshop_quantity_delta": 0, "confirmed_removal": False}
                                for _, paint in sorted(updated.items())]}
    existing_guides = {g["id"]: g for g in catalog.get("paint_usage_guides", [])}
    guide_updates = [g for g in review.get("paint_usage_guides", []) if existing_guides.get(g["id"]) != g]
    if guide_updates:
        changeset["paint_usage_guides"] = deepcopy(guide_updates)
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid reviewed corrections: " + "; ".join(errors))
    return changeset, {"schema_version": 1, "kind": "paint_color_correction_audit",
                       "operation_count": len(updated), "items": changes}
