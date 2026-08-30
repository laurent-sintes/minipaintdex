"""Compare verified manufacturer data with the canonical paint catalog."""

from __future__ import annotations

from datetime import date
from pathlib import Path
from typing import Any

import yaml

from .changesets import canonical_paint, validate_changeset


TECHNICAL_TYPES = {"technical_effect", "primer", "wash_shade", "ink", "auxiliary"}


def read_catalog(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8-sig") as handle:
        value = yaml.safe_load(handle)
    if not isinstance(value, dict) or not isinstance(value.get("paints"), list):
        raise ValueError(f"Invalid paint catalog: {path}")
    return value


def _casefold(value: Any) -> str:
    return str(value or "").strip().casefold()


def _canonical_record(record: dict[str, Any], verified_at: str) -> dict[str, Any]:
    if record.get("functional_type") and record.get("brand") and record.get("id"):
        result = dict(record)
        result["verified_at"] = verified_at
    else:
        result = canonical_paint(record, verified_at=verified_at)
    if result.get("functional_type") in TECHNICAL_TYPES:
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
    for identifier in sorted(incoming):
        record = incoming[identifier]
        if existing.get(identifier) != record:
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

    changeset = {
        "schema_version": 1,
        "kind": "market_paints",
        "source": refreshed.get("source", {}),
        "refresh": {
            "brand": brand,
            "known_brands": sorted(known_by_key.values()),
            "verified_at": verification_date,
            "warnings": warnings,
        },
        "operations": operations,
    }
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid refresh change set: " + "; ".join(errors))
    return changeset
