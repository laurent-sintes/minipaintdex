"""Build and validate transport-neutral MiniPaintDex change sets."""

from __future__ import annotations

import json
import re
from datetime import date
from pathlib import Path
from typing import Any


ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
PAINT_REQUIRED_FIELDS = ("id", "brand", "manufacturer", "range", "functional_type", "name")
CHANGESET_KINDS = {"market_paints", "market_product"}
PAINTABLE_KINDS = {"hero", "enemy", "scenery", "vehicle", "creature", "accessory"}
TECHNICAL_TYPES = {"technical_effect", "primer", "wash_shade", "ink", "auxiliary"}


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")


def _text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def _values(value: Any) -> list[str]:
    if isinstance(value, list):
        return [_text(item) for item in value if _text(item)]
    return [_text(item) for item in _text(value).split("|") if _text(item)]


def _first(record: dict[str, Any], enrichment: dict[str, Any], *keys: str, default: Any = "") -> Any:
    for source in (record, enrichment):
        for key in keys:
            value = source.get(key)
            if value is not None and value != "" and value != []:
                return value
    return default


def _status(value: Any) -> str:
    normalized = _text(value).lower()
    return {
        "confirme": "confirmed",
        "confirmed": "confirmed",
        "a_verifier": "review",
        "à vérifier": "review",
        "review": "review",
    }.get(normalized, normalized or "review")


def _quantity(value: Any) -> int:
    try:
        return max(0, int(float(str(value))))
    except (TypeError, ValueError):
        return 0


def canonical_paint(record: dict[str, Any], *, verified_at: str | None = None) -> dict[str, Any]:
    """Convert one normalized/enriched observation to the canonical market schema."""
    enrichment = record.get("enrichment") if isinstance(record.get("enrichment"), dict) else {}
    identifier = _text(record.get("id"))
    color_hex = _text(_first(record, enrichment, "color_hex", "couleur_hex"))
    color_family = _text(_first(record, enrichment, "color_family", "famille_couleur"))
    manufacturer_image = {
        "path": _text(_first(record, enrichment, "local_image", "manufacturer_image")),
        "source_url": _text(_first(record, enrichment, "image_source_url")),
        "credit": _text(_first(record, enrichment, "image_credit")),
    }
    result_image = {
        "path": _text(_first(record, enrichment, "result_image")),
        "source_url": _text(_first(record, enrichment, "result_source_url")),
        "credit": _text(_first(record, enrichment, "result_credit")),
        "license": _text(_first(record, enrichment, "result_license")),
        "reference_url": _text(_first(record, enrichment, "result_reference_url")),
    }
    source_hash = _text(record.get("source_hash"))
    paint: dict[str, Any] = {
        "id": identifier,
        "observed_brand": _text(record.get("brand_observed")),
        "brand": _text(record.get("brand_canonical") or record.get("brand")),
        "brand_aliases": _values(record.get("brand_aliases")),
        "manufacturer": _text(record.get("manufacturer")),
        "observed_range": _text(record.get("range_observed")),
        "range": _text(record.get("range_canonical") or record.get("range")),
        "functional_type": _text(record.get("functional_class") or record.get("functional_type")),
        "reference": _text(record.get("reference")),
        "name": _text(record.get("name") or record.get("name_observed")),
        "confidence": record.get("confidence", 0),
        "data_status": _status(record.get("status")),
        "lifecycle_status": _text(_first(record, enrichment, "lifecycle_status", default="unknown")),
        "warnings": _values(record.get("warnings")),
        "color": {"hex": color_hex, "family": color_family},
        "finish": _text(_first(record, enrichment, "finish", "fini")),
        "medium": _text(_first(record, enrichment, "medium")),
        "opacity": _text(_first(record, enrichment, "opacity")),
        "volume_ml": _first(record, enrichment, "volume_ml", default=0),
        "tags": _values(_first(record, enrichment, "tags", default=[])),
        "recommended_uses": _values(_first(record, enrichment, "recommended_uses", "usages_conseilles", default=[])),
        "usage_instructions": _first(
            record,
            enrichment,
            "usage_instructions",
            default={"summary": "", "steps": [], "tips": []},
        ),
        "manufacturer_page": _text(_first(record, enrichment, "manufacturer_url", "manufacturer_page")),
        "manufacturer_image": manufacturer_image,
        "result_image": result_image,
        "provenance": {
            "photo": _text(record.get("source_photo")),
            "hashes": [source_hash] if source_hash else [],
        },
        "verified_at": verified_at or _text(_first(record, enrichment, "verified_on", "verified_at")) or date.today().isoformat(),
        "notes": _text(_first(record, enrichment, "notes")),
        "deduplication_key": _text(record.get("dedupe_key")),
    }
    return paint


def build_paint_changeset(
    payload: Any,
    *,
    source: str,
    verified_at: str | None = None,
    include_workshop: bool = True,
) -> dict[str, Any]:
    records = payload.get("paints", payload) if isinstance(payload, dict) else payload
    if not isinstance(records, list):
        raise ValueError("The paint input must be a list or an object containing a paints list.")
    operations = [
        {
            "action": "upsert",
            "record": canonical_paint(record, verified_at=verified_at),
            "workshop_quantity_delta": _quantity(record.get("quantity", 1)) if include_workshop else 0,
        }
        for record in records
    ]
    changeset = {
        "schema_version": 1,
        "kind": "market_paints",
        "source": {"path": source, "generated_at": date.today().isoformat()},
        "operations": operations,
    }
    errors = validate_changeset(changeset)
    if errors:
        raise ValueError("Invalid generated change set: " + "; ".join(errors))
    return changeset


def validate_changeset(changeset: Any, *, allow_empty: bool = False) -> list[str]:
    errors: list[str] = []
    if not isinstance(changeset, dict):
        return ["change set must be a JSON object"]
    if changeset.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    kind = changeset.get("kind")
    if kind not in CHANGESET_KINDS:
        errors.append(f"unsupported kind: {kind}")
        return errors
    if kind == "market_paints":
        operations = changeset.get("operations")
        if not isinstance(operations, list) or (not operations and not allow_empty):
            errors.append("operations must be a non-empty list")
            return errors
        seen: set[str] = set()
        for index, operation in enumerate(operations):
            location = f"operations[{index}]"
            if not isinstance(operation, dict) or operation.get("action") not in {"upsert", "retire", "delete"}:
                errors.append(f"{location}.action must be upsert, retire or delete")
                continue
            quantity_delta = operation.get("workshop_quantity_delta", 0)
            if not isinstance(quantity_delta, int) or quantity_delta < 0:
                errors.append(f"{location}.workshop_quantity_delta must be a non-negative integer")
            record = operation.get("record")
            if not isinstance(record, dict):
                errors.append(f"{location}.record must be an object")
                continue
            required_fields = PAINT_REQUIRED_FIELDS if operation.get("action") == "upsert" else ("id",)
            for field in required_fields:
                if not _text(record.get(field)):
                    errors.append(f"{location}.record.{field} is required")
            if operation.get("action") == "delete" and operation.get("confirmed_removal") is not True:
                errors.append(f"{location}.confirmed_removal must be true for deletion")
            if operation.get("action") != "upsert" and quantity_delta != 0:
                errors.append(f"{location}.workshop_quantity_delta must be zero unless action is upsert")
            if operation.get("action") == "upsert" and record.get("functional_type") in TECHNICAL_TYPES:
                instructions = record.get("usage_instructions")
                if not isinstance(instructions, dict) or not _text(instructions.get("summary")):
                    errors.append(f"{location}.record.usage_instructions.summary is required for technical paint")
                if not isinstance(instructions, dict) or not isinstance(instructions.get("steps"), list) or not instructions.get("steps"):
                    errors.append(f"{location}.record.usage_instructions.steps must explain technical paint usage")
            identifier = _text(record.get("id"))
            if identifier and not ID_PATTERN.fullmatch(identifier):
                errors.append(f"{location}.record.id must be lowercase kebab-case")
            if identifier in seen:
                errors.append(f"duplicate paint id: {identifier}")
            seen.add(identifier)
    if kind == "market_product":
        product = changeset.get("product")
        if not isinstance(product, dict):
            errors.append("product must be an object")
            return errors
        for field in ("id", "name", "line", "product_type", "scope", "catalog_items"):
            if product.get(field) in (None, "", []):
                errors.append(f"product.{field} is required")
        identifier = _text(product.get("id"))
        if identifier and not ID_PATTERN.fullmatch(identifier):
            errors.append("product.id must be lowercase kebab-case")
        items = product.get("catalog_items", [])
        catalog_item_ids: set[str] = set()
        total_quantity = 0
        if isinstance(items, list):
            seen_items: set[str] = set()
            for index, item in enumerate(items):
                if not isinstance(item, dict):
                    errors.append(f"product.catalog_items[{index}] must be an object")
                    continue
                for field in ("id", "product_id", "name", "kind", "quantity"):
                    if not _text(item.get(field)):
                        errors.append(f"product.catalog_items[{index}].{field} is required")
                item_id = _text(item.get("id"))
                if item_id in seen_items:
                    errors.append(f"duplicate catalog item id: {item_id}")
                seen_items.add(item_id)
                catalog_item_ids.add(item_id)
                if _text(item.get("product_id")) != identifier:
                    errors.append(f"product.catalog_items[{index}].product_id must reference {identifier}")
                if _text(item.get("kind")) not in PAINTABLE_KINDS:
                    errors.append(f"product.catalog_items[{index}].kind is invalid")
                quantity = item.get("quantity")
                if not isinstance(quantity, int) or quantity < 1:
                    errors.append(f"product.catalog_items[{index}].quantity must be a positive integer")
                else:
                    total_quantity += quantity
        guides = changeset.get("painting_guides")
        if not isinstance(guides, list):
            errors.append("painting_guides must be a list")
            guides = []
        seen_guides: set[str] = set()
        for index, guide in enumerate(guides):
            if not isinstance(guide, dict):
                errors.append(f"painting_guides[{index}] must be an object")
                continue
            guide_id = _text(guide.get("id"))
            catalog_item_id = _text(guide.get("catalog_item_id"))
            if not guide_id:
                errors.append(f"painting_guides[{index}].id is required")
            if guide_id in seen_guides:
                errors.append(f"duplicate painting guide id: {guide_id}")
            seen_guides.add(guide_id)
            if catalog_item_id not in catalog_item_ids:
                errors.append(f"painting_guides[{index}].catalog_item_id references an unknown catalog item")
            if not isinstance(guide.get("version"), int) or guide.get("version", 0) < 1:
                errors.append(f"painting_guides[{index}].version must be a positive integer")
            if _text(guide.get("knowledge_status")) not in {"documented", "observed", "inferred"}:
                errors.append(f"painting_guides[{index}].knowledge_status is invalid")
            for field in ("slots", "preparation", "painting"):
                if not isinstance(guide.get(field), list):
                    errors.append(f"painting_guides[{index}].{field} must be a list")
            for slot_index, slot in enumerate(guide.get("slots", [])):
                if not isinstance(slot, dict) or not _text(slot.get("id")):
                    errors.append(f"painting_guides[{index}].slots[{slot_index}].id is required")
                if not _text(slot.get("market_paint_id")) and slot.get("pending_import") is not True:
                    errors.append(f"painting_guides[{index}].slots[{slot_index}] needs market_paint_id or pending_import")
        expected = product.get("expected_paintable_count")
        if not isinstance(expected, int) or expected < 1:
            errors.append("product.expected_paintable_count must be a positive integer")
        elif expected != total_quantity:
            errors.append(
                f"catalog item quantities total {total_quantity} but expected_paintable_count is {expected}"
            )
        if "workshop_items" in changeset:
            errors.append("workshop_items do not belong to a market_product change set")
    return errors
