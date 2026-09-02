"""Build and validate transport-neutral MiniPaintDex change sets."""

from __future__ import annotations

import json
import re
from datetime import date
from pathlib import Path
from typing import Any

from .paint_identity import market_paint_deduplication_key, market_paint_id
from .image_quality import IMAGE_QUALITY_LIMITATION_CODES, IMAGE_QUALITY_RANKS, quality_limitation
from .paint_model import canonical_profile, source_observation, validate_profile


ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
PAINT_REQUIRED_FIELDS = ("id", "brand", "manufacturer", "range", "profile", "name")
CHANGESET_KINDS = {"market_paints", "market_product"}
PAINTABLE_KINDS = {"hero", "enemy", "scenery", "vehicle", "creature", "accessory"}
INSTRUCTION_ROLES = {"technical_effect", "primer", "wash", "ink", "varnish", "medium", "auxiliary", "pigment"}


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
    brand = _text(record.get("brand_canonical") or record.get("brand"))
    manufacturer_reference = _text(record.get("reference"))
    identifier = _text(record.get("id")) or market_paint_id(brand, manufacturer_reference)
    color_hex = _text(_first(record, enrichment, "color_hex", "couleur_hex"))
    color_family = _text(_first(record, enrichment, "color_family", "famille_couleur"))
    existing_manufacturer_image = record.get("manufacturer_image") if isinstance(record.get("manufacturer_image"), dict) else {}
    verification_date = verified_at or _text(_first(record, enrichment, "verified_on", "verified_at")) or date.today().isoformat()
    local_image = _text(_first(record, enrichment, "local_image", default=existing_manufacturer_image.get("path", "")))
    image_quality = _text(existing_manufacturer_image.get("image_quality")) or ("owned_photo" if local_image else "none")
    manufacturer_image = {
        "path": local_image,
        "source_url": _text(_first(record, enrichment, "image_source_url")),
        "credit": _text(_first(record, enrichment, "image_credit")),
        "license": _text(existing_manufacturer_image.get("license")),
        "reference_url": _text(existing_manufacturer_image.get("reference_url")),
        "image_quality": image_quality,
        "quality_verified_at": _text(existing_manufacturer_image.get("quality_verified_at")) or (
            verification_date if image_quality != "none" else ""
        ),
    }
    if image_quality != "official_photo":
        existing_limitation = existing_manufacturer_image.get("quality_limitation")
        manufacturer_image["quality_limitation"] = existing_limitation if isinstance(existing_limitation, dict) else quality_limitation(
            "manually-provided" if image_quality == "owned_photo" else "better-source-not-found",
            "The product photo was supplied manually." if image_quality == "owned_photo"
            else "The import did not provide a better usable product image.",
            verification_date,
        )
    result_image = {
        "path": _text(_first(record, enrichment, "result_image")),
        "source_url": _text(_first(record, enrichment, "result_source_url")),
        "credit": _text(_first(record, enrichment, "result_credit")),
        "license": _text(_first(record, enrichment, "result_license")),
        "reference_url": _text(_first(record, enrichment, "result_reference_url")),
    }
    source_hash = _text(record.get("source_hash"))
    profile, mapping_report = canonical_profile(record)
    paint: dict[str, Any] = {
        "schema_version": 1,
        "id": identifier,
        "observed_brand": _text(record.get("brand_observed")),
        "brand": brand,
        "brand_aliases": _values(record.get("brand_aliases")),
        "manufacturer": _text(record.get("manufacturer")),
        "observed_range": _text(record.get("range_observed")),
        "range": _text(record.get("range_canonical") or record.get("range")),
        "profile": profile,
        "reference": manufacturer_reference,
        "name": _text(record.get("name") or record.get("name_observed")),
        "confidence": record.get("confidence", 0),
        "data_status": _status(record.get("status")),
        "lifecycle_status": _text(_first(record, enrichment, "lifecycle_status", default="unknown")),
        "warnings": _values(record.get("warnings")),
        "color": {"hex": color_hex, "family": color_family},
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
        "verified_at": verification_date,
        "notes": _text(_first(record, enrichment, "notes")),
        "deduplication_key": _text(record.get("dedupe_key")),
        "source_observation": source_observation(record),
        "mapping_report": mapping_report,
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
            if not isinstance(operation, dict) or operation.get("action") not in {"upsert", "retire", "delete", "rekey"}:
                errors.append(f"{location}.action must be upsert, retire, delete or rekey")
                continue
            quantity_delta = operation.get("workshop_quantity_delta", 0)
            if not isinstance(quantity_delta, int) or quantity_delta < 0:
                errors.append(f"{location}.workshop_quantity_delta must be a non-negative integer")
            record = operation.get("record")
            if not isinstance(record, dict):
                errors.append(f"{location}.record must be an object")
                continue
            required_fields = PAINT_REQUIRED_FIELDS if operation.get("action") in {"upsert", "rekey"} else ("id",)
            if operation.get("action") in {"upsert", "rekey"} and record.get("schema_version") != 1:
                errors.append(f"{location}.record.schema_version must be 1")
            for field in required_fields:
                if field == "profile":
                    if not isinstance(record.get(field), dict):
                        errors.append(f"{location}.record.profile is required")
                    continue
                if not _text(record.get(field)):
                    errors.append(f"{location}.record.{field} is required")
            if operation.get("action") == "delete" and operation.get("confirmed_removal") is not True:
                errors.append(f"{location}.confirmed_removal must be true for deletion")
            if operation.get("action") == "rekey":
                previous_id = _text(operation.get("previous_id"))
                if not previous_id:
                    errors.append(f"{location}.previous_id is required for rekey")
                elif not ID_PATTERN.fullmatch(previous_id):
                    errors.append(f"{location}.previous_id must be lowercase kebab-case")
                elif previous_id == _text(record.get("id")):
                    errors.append(f"{location}.previous_id must differ from record.id")
            if operation.get("action") != "upsert" and quantity_delta != 0:
                errors.append(f"{location}.workshop_quantity_delta must be zero unless action is upsert")
            profile = record.get("profile")
            if operation.get("action") in {"upsert", "rekey"} and isinstance(profile, dict):
                try:
                    validate_profile(profile, f"{location}.record.profile")
                except ValueError as error:
                    errors.append(str(error))
            roles = set(profile.get("roles", [])) if isinstance(profile, dict) else set()
            if operation.get("action") in {"upsert", "rekey"} and roles.intersection(INSTRUCTION_ROLES):
                instructions = record.get("usage_instructions")
                if not isinstance(instructions, dict) or not _text(instructions.get("summary")):
                    errors.append(f"{location}.record.usage_instructions.summary is required for technical paint")
                if not isinstance(instructions, dict) or not isinstance(instructions.get("steps"), list) or not instructions.get("steps"):
                    errors.append(f"{location}.record.usage_instructions.steps must explain technical paint usage")
            identifier = _text(record.get("id"))
            if identifier and not ID_PATTERN.fullmatch(identifier):
                errors.append(f"{location}.record.id must be lowercase kebab-case")
            if operation.get("action") in {"upsert", "rekey"} and identifier:
                try:
                    expected_id = market_paint_id(record.get("brand"), record.get("reference"))
                except ValueError:
                    expected_id = identifier
                if identifier != expected_id:
                    errors.append(f"{location}.record.id must be {expected_id} for its brand and reference")
                _validate_image_reference(
                    record.get("manufacturer_image"), f"{location}.record.manufacturer_image", errors,
                    product_visual=True,
                )
                _validate_image_reference(
                    record.get("result_image"), f"{location}.record.result_image", errors,
                    product_visual=False,
                )
                _validate_source_evidence(record, location, errors)
            if identifier in seen:
                errors.append(f"duplicate paint id: {identifier}")
            seen.add(identifier)
    if kind == "market_product":
        product = changeset.get("product")
        if not isinstance(product, dict):
            errors.append("product must be an object")
            return errors
        if product.get("schema_version") != 1:
            errors.append("product.schema_version must be 1")
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


def _validate_image_reference(
    image: Any, location: str, errors: list[str], *, product_visual: bool,
) -> None:
    if image is None:
        return
    if not isinstance(image, dict):
        errors.append(f"{location} must be an object")
        return
    for field in ("source_url", "reference_url"):
        value = _text(image.get(field))
        if value and not value.startswith("https://"):
            errors.append(f"{location}.{field} must use HTTPS")
    if not product_visual:
        return
    quality = _text(image.get("image_quality")) or "none"
    if quality not in IMAGE_QUALITY_RANKS:
        errors.append(f"{location}.image_quality is unsupported: {quality}")
        return
    has_visual = bool(_text(image.get("path")) or _text(image.get("source_url")))
    if quality in {"official_photo", "retailer_photo", "owned_photo", "generic_visual"} and not has_visual:
        errors.append(f"{location} requires a path or source_url for {quality}")
    if quality != "none" and not _text(image.get("quality_verified_at")):
        errors.append(f"{location}.quality_verified_at is required for {quality}")
    if quality == "retailer_photo":
        if not _text(image.get("credit")):
            errors.append(f"{location}.credit is required for retailer_photo")
        if not _text(image.get("reference_url")):
            errors.append(f"{location}.reference_url is required for retailer_photo")
    limitation = image.get("quality_limitation")
    if quality == "official_photo":
        if limitation is not None:
            errors.append(f"{location}.quality_limitation must be absent for official_photo")
    elif not isinstance(limitation, dict):
        errors.append(f"{location}.quality_limitation is required for {quality}")
    else:
        code = _text(limitation.get("code"))
        if code not in IMAGE_QUALITY_LIMITATION_CODES:
            errors.append(f"{location}.quality_limitation.code is unsupported: {code}")
        if not _text(limitation.get("detail")):
            errors.append(f"{location}.quality_limitation.detail is required")
        observed_at = _text(limitation.get("observed_at"))
        try:
            date.fromisoformat(observed_at)
        except ValueError:
            errors.append(f"{location}.quality_limitation.observed_at must be an ISO-8601 date")


def _validate_source_evidence(record: dict[str, Any], location: str, errors: list[str]) -> None:
    snapshots = record.get("source_snapshots")
    if snapshots is not None:
        if not isinstance(snapshots, list):
            errors.append(f"{location}.record.source_snapshots must be a list")
        else:
            for index, snapshot in enumerate(snapshots):
                current = f"{location}.record.source_snapshots[{index}]"
                if not isinstance(snapshot, dict):
                    errors.append(f"{current} must be an object")
                    continue
                if not _text(snapshot.get("provider")):
                    errors.append(f"{current}.provider is required")
                url = _text(snapshot.get("url"))
                if not url.startswith("https://"):
                    errors.append(f"{current}.url must use HTTPS")
                if not isinstance(snapshot.get("payload"), dict):
                    errors.append(f"{current}.payload must be an object")
    report = record.get("mapping_report")
    if report is not None:
        if not isinstance(report, dict):
            errors.append(f"{location}.record.mapping_report must be an object")
        elif report.get("mapping_version") != 1:
            errors.append(f"{location}.record.mapping_report.mapping_version must be 1")
