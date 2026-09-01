"""Compare verified manufacturer data with the canonical paint catalog."""

from __future__ import annotations

from datetime import date
from pathlib import Path
from typing import Any
from collections import Counter, defaultdict

import yaml

from .changesets import canonical_paint, validate_changeset


INSTRUCTION_ROLES = {"technical_effect", "primer", "wash", "ink", "varnish", "medium", "auxiliary", "pigment"}


def read_catalog(path: Path) -> dict[str, Any]:
    paths = sorted(path.glob("*.yaml")) if path.is_dir() else [path]
    paints: list[dict[str, Any]] = []
    for source in paths:
        with source.open("r", encoding="utf-8-sig") as handle:
            value = yaml.safe_load(handle)
        if not isinstance(value, dict) or not isinstance(value.get("paints"), list):
            raise ValueError(f"Invalid paint catalog: {source}")
        paints.extend(paint for paint in value["paints"] if isinstance(paint, dict))
    if not paths:
        raise ValueError(f"No paint brand catalog found in: {path}")
    return {"schema_version": 2, "paints": paints}


def _casefold(value: Any) -> str:
    return str(value or "").strip().casefold()


def _canonical_record(record: dict[str, Any], verified_at: str) -> dict[str, Any]:
    if record.get("profile") and record.get("brand") and record.get("id"):
        result = dict(record)
        result["verified_at"] = verified_at
    else:
        result = canonical_paint(record, verified_at=verified_at)
    roles = set((result.get("profile") or {}).get("roles", []))
    if roles.intersection(INSTRUCTION_ROLES):
        instructions = result.get("usage_instructions")
        if not isinstance(instructions, dict) or not instructions.get("summary") or not instructions.get("steps"):
            raise ValueError(f"Technical paint {result.get('id')} requires explicit usage_instructions.summary and steps.")
    return result


def build_refresh_changeset(
    catalog: dict[str, Any],
    refreshed: dict[str, Any],
    *,
    brand: str,
    verified_at: str | None = None,
    remove_missing: bool = False,
) -> dict[str, Any]:
    verification_date = verified_at or date.today().isoformat()
    current = catalog.get("paints", [])
    known_by_key = {_casefold(paint.get("brand")): paint.get("brand") for paint in current}
    if _casefold(brand) == "all":
        selected = set(known_by_key)
    else:
        key = _casefold(brand)
        if key not in known_by_key:
            raise ValueError(f"Unknown brand: {brand}. Use a canonical brand from the local catalog or 'all'.")
        selected = {key}

    coverage_entries = refreshed.get("coverage", [])
    coverage = {
        _casefold(entry.get("brand")): bool(entry.get("complete"))
        for entry in coverage_entries
        if isinstance(entry, dict) and entry.get("brand")
    }
    incoming_records = refreshed.get("paints", [])
    if not isinstance(incoming_records, list):
        raise ValueError("Refreshed data must contain a paints list.")
    incoming = {
        record["id"]: record
        for candidate in incoming_records
        if isinstance(candidate, dict)
        for record in [_canonical_record(candidate, verification_date)]
        if _casefold(record.get("brand")) in selected
    }
    existing = {
        paint["id"]: paint
        for paint in current
        if isinstance(paint, dict) and _casefold(paint.get("brand")) in selected
    }

    operations: list[dict[str, Any]] = []
    changed_fields: Counter[str] = Counter()
    for identifier in sorted(incoming):
        record = incoming[identifier]
        if existing.get(identifier) != record:
            previous = existing.get(identifier, {})
            changed_fields.update(
                key for key in set(previous) | set(record)
                if previous.get(key) != record.get(key)
            )
            operations.append({
                "action": "upsert",
                "record": record,
                "workshop_quantity_delta": 0,
                "confirmed_removal": False,
            })

    warnings: list[str] = []
    for identifier in sorted(set(existing) - set(incoming)):
        brand_key = _casefold(existing[identifier].get("brand"))
        if not coverage.get(brand_key, False):
            warnings.append(f"{identifier}: missing from an incomplete refresh; no retirement proposed")
            continue
        action = "delete" if remove_missing else "retire"
        operations.append({
            "action": action,
            "record": {
                "id": identifier,
                "lifecycle_status": "discontinued",
                "verified_at": verification_date,
                "removal_reason": "Missing from a manufacturer range refresh declared complete",
            },
            "workshop_quantity_delta": 0,
            "confirmed_removal": remove_missing,
        })

    operations_by_brand: dict[str, Counter[str]] = defaultdict(Counter)
    for operation in operations:
        record = operation["record"]
        identifier = record.get("id")
        operation_brand = record.get("brand") or (existing.get(identifier, {}) if identifier else {}).get("brand") or "unknown"
        operations_by_brand[str(operation_brand)][operation["action"]] += 1

    audit = {
        "existing_count": len(existing),
        "incoming_count": len(incoming),
        "operation_count": len(operations),
        "operations_by_brand": {
            name: dict(sorted(counts.items())) for name, counts in sorted(operations_by_brand.items())
        },
        "changed_top_level_fields": dict(sorted(changed_fields.items(), key=lambda item: (-item[1], item[0]))),
        "providers": refreshed.get("audit", []),
    }
    changeset = {
        "schema_version": 1,
        "kind": "market_paints",
        "source": refreshed.get("source", {}),
        "refresh": {
            "brand": brand,
            "known_brands": sorted(known_by_key.values()),
            "verified_at": verification_date,
            "warnings": warnings,
            "audit": audit,
        },
        "operations": operations,
    }
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid refresh change set: " + "; ".join(errors))
    return changeset
