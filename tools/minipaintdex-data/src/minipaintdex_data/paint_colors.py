"""Enrich canonical paints from pinned, traceable digital colour datasets."""

from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from collections import Counter, defaultdict
from copy import deepcopy
from datetime import date
from pathlib import Path
from typing import Any

from .changesets import validate_changeset
from .official_sources.common import classify, usage


COLOR_BEARING_ROLES = {"color_paint", "primer", "wash", "ink", "technical_effect", "pigment"}
AUXILIARY_TONE_ROLES = {"varnish", "medium", "auxiliary"}
AUXILIARY_TONE = "auxiliary"
HEX_COLOR = re.compile(r"#[0-9a-fA-F]{6}")


def _normalized(value: Any) -> str:
    ascii_value = unicodedata.normalize("NFKD", str(value or "")).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", " ", ascii_value.casefold()).strip()


def _read_manifest(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("schema_version") != 1:
        raise ValueError(f"Invalid colour-source manifest: {path}")
    if not re.fullmatch(r"(?:[0-9a-f]{40}|[0-9a-f]{64})", str(value.get("revision", ""))):
        raise ValueError("Colour-source manifest revision must be a full lowercase Git commit or SHA-256 hash.")
    if not isinstance(value.get("brands"), dict) or not value["brands"]:
        raise ValueError("Colour-source manifest must declare brands.")
    return value


def _read_source_records(source_root: Path, config: dict[str, Any]) -> list[dict[str, Any]]:
    path = source_root / str(config.get("file", ""))
    if not path.is_file():
        raise ValueError(f"Colour-source file not found: {path}")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != config.get("sha256"):
        raise ValueError(f"Colour-source checksum mismatch for {path}: {digest}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, list) or any(not isinstance(record, dict) for record in value):
        raise ValueError(f"Colour-source file must contain a JSON array of records: {path}")
    invalid = [record.get("id", record.get("name", "unknown")) for record in value
               if not HEX_COLOR.fullmatch(str(record.get("hex", "")))]
    if invalid:
        raise ValueError(f"Colour-source records have invalid hex values: {', '.join(map(str, invalid[:5]))}")
    return value


def _single_color(candidates: list[dict[str, Any]]) -> tuple[dict[str, Any] | None, str]:
    by_color: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for candidate in candidates:
        by_color[str(candidate["hex"]).lower()].append(candidate)
    if not by_color:
        return None, "not-found"
    if len(by_color) > 1:
        return None, "ambiguous-colors"
    records = next(iter(by_color.values()))
    return sorted(records, key=lambda item: str(item.get("id", "")))[0], "matched"


def _match(
    paint: dict[str, Any], records: list[dict[str, Any]], config: dict[str, Any]
) -> tuple[dict[str, Any] | None, str, str]:
    strategy = str(config.get("match", ""))
    candidates: list[dict[str, Any]] = []
    method = strategy
    if strategy == "reference":
        reference = _normalized(paint.get("reference"))
        paint_brand = _normalized(paint.get("brand"))
        candidates = [
            record for record in records
            if _normalized(record.get("code", record.get("reference"))) == reference
            and (not record.get("brand") or _normalized(record.get("brand")) == paint_brand)
        ]
    elif strategy == "prince-august-model-color-reference":
        match = re.fullmatch(r"p(\d{3})", _normalized(paint.get("reference")))
        if match:
            reference = f"70 {match.group(1)}"
            source_range = _normalized(config.get("source_range"))
            candidates = [
                record for record in records
                if _normalized(record.get("code")) == reference
                and _normalized(record.get("range")) == source_range
            ]
    elif strategy == "name-range":
        aliases = config.get("range_aliases", {})
        allowed_ranges = aliases.get(paint.get("range"), [paint.get("range")]) if isinstance(aliases, dict) else [paint.get("range")]
        paint_name = str(paint.get("name", ""))
        for prefix in config.get("strip_name_prefixes", []):
            if paint_name.casefold().startswith(str(prefix).casefold()):
                paint_name = paint_name[len(str(prefix)):].strip()
                method += "-stripped-prefix"
                break
        names = [paint_name]
        configured_aliases = config.get("name_aliases", {})
        if isinstance(configured_aliases, dict):
            alias = configured_aliases.get(paint_name, configured_aliases.get(str(paint.get("name", ""))))
            if alias:
                names = alias if isinstance(alias, list) else [alias]
                method += "-reviewed-alias"
        name_keys = {_normalized(value) for value in names}
        # The configured order expresses source preference. This notably lets a
        # current formula win over a legacy formula without merging both into
        # an artificial ambiguity.
        for allowed_range in allowed_ranges:
            range_key = _normalized(allowed_range)
            range_candidates = [
                record for record in records
                if _normalized(record.get("name")) in name_keys
                and _normalized(record.get("range")) == range_key
            ]
            if range_candidates:
                candidates = range_candidates
                break
    else:
        raise ValueError(f"Unsupported colour-source match strategy: {strategy}")
    candidate, status = _single_color(candidates)
    return candidate, method, status


def _source_snapshot(
    manifest: dict[str, Any], config: dict[str, Any], source: dict[str, Any], method: str
) -> dict[str, Any]:
    filename = str(config["file"])
    revision = str(manifest["revision"])
    repository = str(manifest["repository"]).removesuffix("/")
    record = {
        key: source[key]
        for key in (
            "id", "name", "brand", "range", "type", "hex", "code", "reference",
            "discontinued", "metallic", "source_image_url", "source_image_sha256",
            "source_equivalent",
        )
        if key in source
    }
    source_url = str(source.get("source_url", ""))
    if source_url:
        return {
            "provider": str(manifest["id"]),
            "url": source_url,
            "payload": {
                "revision": revision,
                "license": manifest.get("license", ""),
                "copyright": manifest.get("copyright", ""),
                "accuracy": source.get("accuracy", manifest.get("accuracy", "")),
                "identity_match": method,
                "identity_confidence": 1.0 if method in {"reference", "prince-august-model-color-reference"} else 0.95,
                "source_document_sha256": source.get("source_sha256", ""),
                "source_image_url": source.get("source_image_url", ""),
                "source_image_sha256": source.get("source_image_sha256", ""),
                "source_equivalent": source.get("source_equivalent", ""),
                "source_page": source.get("source_page"),
                "extraction_method": source.get("extraction_method", ""),
                "source_record": record,
            },
        }
    return {
        "provider": str(manifest["id"]),
        "url": f"{repository}/blob/{revision}/data/paints/{filename}",
        "payload": {
            "revision": revision,
            "license": manifest.get("license", ""),
            "copyright": manifest.get("copyright", ""),
            "accuracy": manifest.get("accuracy", ""),
            "identity_match": method,
            "identity_confidence": 1.0 if method in {"reference", "prince-august-model-color-reference"} else 0.95,
            "source_record": record,
        },
    }


def build_color_enrichment_changeset(
    catalog: dict[str, Any],
    *,
    manifest_path: Path,
    source_root: Path,
    brands: list[str] | None = None,
    as_of: str | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Fill missing canonical hex colours without overwriting existing observations."""
    manifest = _read_manifest(manifest_path)
    requested = {_normalized(value) for value in (brands or ["all"])}
    brand_configs = manifest["brands"]
    if "all" not in requested:
        unknown = sorted(requested - {_normalized(value) for value in brand_configs})
        if unknown:
            raise ValueError(f"Unknown colour-source brand(s): {', '.join(unknown)}")
    selected = {
        brand: config for brand, config in brand_configs.items()
        if "all" in requested or _normalized(brand) in requested
    }
    sources_by_file: dict[str, list[dict[str, Any]]] = {}
    for config in selected.values():
        filename = str(config["file"])
        if filename not in sources_by_file:
            sources_by_file[filename] = _read_source_records(source_root, config)

    operations: list[dict[str, Any]] = []
    item_audit: list[dict[str, Any]] = []
    counts: dict[str, Counter[str]] = {brand: Counter() for brand in selected}
    for paint in catalog.get("paints", []):
        brand = str(paint.get("brand", ""))
        if brand not in selected:
            continue
        counter = counts[brand]
        counter["total"] += 1
        roles = set((paint.get("profile") or {}).get("roles", []))
        inferred_functional_role = classify(str(paint.get("name", "")), "")
        if roles.intersection(COLOR_BEARING_ROLES) and inferred_functional_role in AUXILIARY_TONE_ROLES:
            updated = deepcopy(paint)
            updated.setdefault("profile", {})["roles"] = [inferred_functional_role]
            updated.setdefault("color", {})["family"] = AUXILIARY_TONE
            updated["color"]["hex"] = ""
            updated["usage_instructions"] = usage(inferred_functional_role, str(paint.get("name", "")))
            operations.append({
                "action": "upsert", "record": updated,
                "workshop_quantity_delta": 0, "confirmed_removal": False,
            })
            counter["reclassified_auxiliary"] += 1
            item_audit.append({
                "paint_id": paint.get("id"), "brand": brand,
                "status": "reclassified-auxiliary",
                "previous_roles": sorted(roles), "role": inferred_functional_role,
            })
            continue
        if not roles.intersection(COLOR_BEARING_ROLES):
            if not roles or not roles.issubset(AUXILIARY_TONE_ROLES):
                counter["unclassified_functional"] += 1
                continue
            counter["special_auxiliary"] += 1
            existing_family = str((paint.get("color") or {}).get("family", "")).strip()
            if existing_family:
                if existing_family.casefold() != AUXILIARY_TONE:
                    counter["auxiliary_family_conflicts"] += 1
                    item_audit.append({
                        "paint_id": paint.get("id"), "brand": brand,
                        "status": "auxiliary-family-conflict-preserved",
                        "existing_family": existing_family, "roles": sorted(roles),
                    })
                continue
            updated = deepcopy(paint)
            updated.setdefault("color", {})["family"] = AUXILIARY_TONE
            operations.append({
                "action": "upsert", "record": updated,
                "workshop_quantity_delta": 0, "confirmed_removal": False,
            })
            counter["classified_auxiliary"] += 1
            continue
        counter["eligible"] += 1
        config = selected[brand]
        source, method, match_status = _match(paint, sources_by_file[str(config["file"])], config)
        existing_hex = str((paint.get("color") or {}).get("hex", "")).strip()
        if existing_hex:
            counter["existing_hex"] += 1
            if source and existing_hex.casefold() != str(source["hex"]).casefold():
                counter["existing_conflicts"] += 1
                item_audit.append({
                    "paint_id": paint.get("id"), "brand": brand, "status": "existing-conflict-preserved",
                    "existing_hex": existing_hex.lower(), "candidate_hex": str(source["hex"]).lower(),
                    "match": method, "source_id": source.get("id"),
                })
            continue
        if not source:
            counter["ambiguous" if match_status == "ambiguous-colors" else "unmatched"] += 1
            item_audit.append({
                "paint_id": paint.get("id"), "brand": brand, "status": match_status,
                "reference": paint.get("reference", ""), "range": paint.get("range", ""),
                "name": paint.get("name", ""), "match": method,
            })
            continue
        updated = deepcopy(paint)
        updated.setdefault("color", {})["hex"] = str(source["hex"]).lower()
        updated.setdefault("source_snapshots", []).append(_source_snapshot(manifest, config, source, method))
        operations.append({
            "action": "upsert", "record": updated,
            "workshop_quantity_delta": 0, "confirmed_removal": False,
        })
        counter["enriched"] += 1
        counter[f"matched_by_{method.replace('-', '_')}"] += 1

    generated_at = as_of or date.today().isoformat()
    changeset = {
        "schema_version": 1,
        "kind": "market_paints",
        "source": {
            "provider": manifest["id"], "repository": manifest["repository"],
            "revision": manifest["revision"], "license": manifest.get("license", ""),
            "generated_at": generated_at,
        },
        "operations": operations,
    }
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid colour enrichment change set: " + "; ".join(errors))
    brand_audit = {}
    for brand, counter in counts.items():
        for key in (
            "total", "special_auxiliary", "classified_auxiliary", "unclassified_functional",
            "reclassified_auxiliary",
            "auxiliary_family_conflicts", "eligible", "existing_hex",
            "existing_conflicts", "ambiguous", "unmatched", "enriched",
        ):
            counter.setdefault(key, 0)
        values = dict(sorted(counter.items()))
        values["coverage_before"] = counter["existing_hex"]
        values["coverage_after"] = counter["existing_hex"] + counter["enriched"]
        values["eligible_coverage_percent_after"] = round(
            100 * values["coverage_after"] / counter["eligible"], 1
        ) if counter["eligible"] else 100.0
        values["filter_coverage_after"] = (
            values["coverage_after"] + counter["special_auxiliary"] + counter["reclassified_auxiliary"]
        )
        filter_candidates = counter["eligible"] + counter["special_auxiliary"] + counter["reclassified_auxiliary"]
        values["filter_coverage_percent_after"] = round(
            100 * values["filter_coverage_after"] / filter_candidates, 1
        ) if filter_candidates else 100.0
        brand_audit[brand] = values
    audit = {
        "schema_version": 1,
        "kind": "paint_color_enrichment_audit",
        "generated_at": generated_at,
        "source": changeset["source"],
        "brands": brand_audit,
        "operation_count": len(operations),
        "items": item_audit,
    }
    return changeset, audit
