"""Create and validate portable, deterministic MiniPaintDex datasets."""

from __future__ import annotations

import hashlib
import json
import re
import shutil
from pathlib import Path
from typing import Any

import yaml

from .changesets import write_json


SCHEMA_VERSION = 1
CATEGORY_PATHS = {
    "market.paint-brand": Path("market/paint-brands"),
    "market.paintable-product": Path("market/paintable-products"),
    "workshop.paints": Path("workshop/paints"),
    "workshop.painting-project": Path("workshop/painting-projects"),
}
CATEGORY_KINDS = {
    "market.paint-brand": "market_paints",
    "market.paintable-product": "market_product",
    "workshop.paints": "workshop_paints",
    "workshop.painting-project": "painting_project",
}


def slug(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", value.strip().lower()).strip("-")
    if not normalized:
        raise ValueError("Dataset name must contain at least one letter or digit")
    return normalized


def load_yaml(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8-sig") as handle:
        value = yaml.safe_load(handle) or {}
    if not isinstance(value, dict):
        raise ValueError(f"Expected a YAML object in {path}")
    return value


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(64 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _catalog(root: Path) -> list[dict[str, Any]]:
    paints = load_yaml(root / "data/market/paints/catalog.yaml").get("paints", [])
    if not isinstance(paints, list):
        raise ValueError("Market paint catalog must contain a paints list")
    return [paint for paint in paints if isinstance(paint, dict)]


def _market_brand(root: Path, brand: str | None) -> tuple[dict[str, Any], list[str]]:
    if not brand:
        raise ValueError("--brand is required for category market.paint-brand")
    selected = [paint for paint in _catalog(root) if paint.get("brand") == brand]
    if not selected:
        raise ValueError(f"Unknown or empty market paint brand: {brand}")
    selected.sort(key=lambda paint: str(paint.get("id", "")))
    return {
        "schema_version": 1,
        "kind": "market_paints",
        "operations": [
            {"action": "upsert", "record": paint, "workshop_quantity_delta": 0}
            for paint in selected
        ],
    }, ["data/market/paints/catalog.yaml"]


def _market_product(root: Path, product_id: str | None) -> tuple[dict[str, Any], list[str]]:
    if not product_id:
        raise ValueError("--product is required for category market.paintable-product")
    product_path = root / f"data/market/paintable-products/{product_id}.yaml"
    guide_path = root / f"data/market/painting-guides/{product_id}.yaml"
    product = load_yaml(product_path)
    guides = load_yaml(guide_path).get("painting_guides", []) if guide_path.exists() else []
    if not isinstance(guides, list):
        raise ValueError(f"painting_guides must be a list in {guide_path}")
    sources = [product_path.relative_to(root).as_posix()]
    if guide_path.exists():
        sources.append(guide_path.relative_to(root).as_posix())
    return {
        "schema_version": 1,
        "kind": "market_product",
        "product": product,
        "painting_guides": guides,
    }, sources


def _workshop_paints(root: Path) -> tuple[dict[str, Any], list[str]]:
    inventory_path = root / "data/workshop/paints.yaml"
    inventory = load_yaml(inventory_path).get("paints", [])
    if not isinstance(inventory, list):
        raise ValueError("Workshop paint inventory must contain a paints list")
    market_ids = {str(paint.get("id")) for paint in _catalog(root)}
    normalized: list[dict[str, Any]] = []
    for entry in inventory:
        if not isinstance(entry, dict):
            continue
        paint_id = str(entry.get("paint_id", ""))
        quantity = int(entry.get("quantity", 0))
        if paint_id not in market_ids:
            raise ValueError(f"Workshop inventory references unknown market paint: {paint_id}")
        if quantity < 0:
            raise ValueError(f"Workshop paint quantity cannot be negative: {paint_id}")
        normalized.append({"paint_id": paint_id, "quantity": quantity})
    normalized.sort(key=lambda entry: entry["paint_id"])
    return {
        "schema_version": 1,
        "kind": "workshop_paints",
        "paints": normalized,
    }, ["data/workshop/paints.yaml", "data/market/paints/catalog.yaml"]


def _workshop_project(
    root: Path,
    product_id: str | None,
    project_id: str | None,
    project_name: str | None,
) -> tuple[dict[str, Any], list[str]]:
    if not product_id:
        raise ValueError("--product is required for category workshop.painting-project")
    product_path = root / f"data/market/paintable-products/{product_id}.yaml"
    product = load_yaml(product_path)
    if str(product.get("id", "")) != product_id:
        raise ValueError(f"Paintable product id mismatch in {product_path}")
    resolved_project_id = project_id or product_id
    return {
        "schema_version": 1,
        "kind": "painting_project",
        "painting_project": {
            "id": resolved_project_id,
            "paintable_product_id": product_id,
            "name": project_name or str(product.get("name", product_id)),
        },
    }, [product_path.relative_to(root).as_posix()]


def build_payload(
    application_root: Path,
    category: str,
    *,
    brand: str | None = None,
    product_id: str | None = None,
    project_id: str | None = None,
    project_name: str | None = None,
) -> tuple[dict[str, Any], list[str]]:
    root = application_root.resolve()
    if category == "market.paint-brand":
        return _market_brand(root, brand)
    if category == "market.paintable-product":
        return _market_product(root, product_id)
    if category == "workshop.paints":
        return _workshop_paints(root)
    if category == "workshop.painting-project":
        return _workshop_project(root, product_id, project_id, project_name)
    raise ValueError(f"Unsupported dataset category: {category}")


def create_dataset(
    application_root: Path,
    datasets_root: Path,
    category: str,
    name: str,
    *,
    brand: str | None = None,
    product_id: str | None = None,
    project_id: str | None = None,
    project_name: str | None = None,
    replace: bool = False,
) -> Path:
    if category not in CATEGORY_PATHS:
        raise ValueError(f"Unsupported dataset category: {category}")
    dataset_id = slug(name)
    target = (datasets_root / CATEGORY_PATHS[category] / dataset_id).resolve()
    category_root = (datasets_root / CATEGORY_PATHS[category]).resolve()
    if target.parent != category_root:
        raise ValueError("Dataset target escaped its category directory")
    if target.exists() and not replace:
        raise ValueError(f"Dataset already exists: {target}")

    payload, sources = build_payload(
        application_root, category, brand=brand, product_id=product_id,
        project_id=project_id, project_name=project_name,
    )
    category_root.mkdir(parents=True, exist_ok=True)
    temporary = category_root / f".{dataset_id}.staging"
    if temporary.exists():
        shutil.rmtree(temporary)
    temporary.mkdir()
    try:
        payload_path = temporary / "payload/change-set.json"
        write_json(payload_path, payload)
        manifest = {
            "schema_version": SCHEMA_VERSION,
            "id": dataset_id,
            "name": name.strip(),
            "category": category,
            "mode": "replace" if category == "workshop.paints" else "merge",
            "source_files": sorted(sources),
            "payload": {
                "path": "payload/change-set.json",
                "kind": CATEGORY_KINDS[category],
                "sha256": _sha256(payload_path),
            },
        }
        with (temporary / "dataset.yaml").open("w", encoding="utf-8", newline="\n") as handle:
            yaml.safe_dump(manifest, handle, allow_unicode=True, sort_keys=False)
        errors = validate_dataset(temporary)
        if errors:
            raise ValueError("; ".join(errors))
        if target.exists():
            shutil.rmtree(target)
        temporary.replace(target)
        return target
    except Exception:
        shutil.rmtree(temporary, ignore_errors=True)
        raise


def validate_dataset(path: Path) -> list[str]:
    errors: list[str] = []
    root = path.resolve()
    manifest_path = root / "dataset.yaml"
    if not manifest_path.is_file():
        return ["dataset.yaml is missing"]
    try:
        manifest = load_yaml(manifest_path)
    except (OSError, ValueError, yaml.YAMLError) as error:
        return [str(error)]
    if manifest.get("schema_version") != SCHEMA_VERSION:
        errors.append(f"schema_version must be {SCHEMA_VERSION}")
    category = str(manifest.get("category", ""))
    if category not in CATEGORY_PATHS:
        errors.append(f"unsupported category: {category}")
    payload = manifest.get("payload")
    if not isinstance(payload, dict):
        return errors + ["payload must be an object"]
    relative = Path(str(payload.get("path", "")))
    payload_path = (root / relative).resolve()
    if root not in payload_path.parents:
        errors.append("payload path escapes the dataset directory")
        return errors
    if not payload_path.is_file():
        errors.append(f"payload file is missing: {relative.as_posix()}")
        return errors
    expected_sha = str(payload.get("sha256", ""))
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha):
        errors.append("payload sha256 is invalid")
    elif _sha256(payload_path) != expected_sha:
        errors.append("payload sha256 does not match")
    try:
        with payload_path.open("r", encoding="utf-8") as handle:
            document = json.load(handle)
        expected_kind = CATEGORY_KINDS.get(category)
        if expected_kind and document.get("kind") != expected_kind:
            errors.append(f"payload kind must be {expected_kind}")
        if document.get("schema_version") != SCHEMA_VERSION:
            errors.append(f"payload schema_version must be {SCHEMA_VERSION}")
    except (OSError, json.JSONDecodeError) as error:
        errors.append(str(error))
    return errors


def inspect_dataset(path: Path) -> dict[str, Any]:
    manifest = load_yaml(path / "dataset.yaml")
    errors = validate_dataset(path)
    return {"valid": not errors, "errors": errors, "manifest": manifest}
