"""The Army Painter official Shopify adapter."""

from __future__ import annotations

import json
import re
from copy import deepcopy
from pathlib import Path
from typing import Any, Iterable

from .common import base_record, classify, existing_indexes, fetch_text, merge_existing, plain, reference, slug, source_snapshot


COLLECTIONS = {
    "fanatic": "https://thearmypainter.com/en-gb/collections/warpaints-fanatic-singles/products.json",
    "speedpaint": "https://thearmypainter.com/en-gb/collections/speedpaint/products.json",
    "air": "https://thearmypainter.com/en-gb/collections/warpaints-air/products.json",
    "primers": "https://thearmypainter.com/collections/primers/products.json",
}
OFFICIAL_URLS = tuple(COLLECTIONS.values())


def stable_payload(product: dict[str, Any]) -> dict[str, Any]:
    """Remove Shopify request-time stamps while retaining commercial source facts."""
    stable = deepcopy(product)
    stable.pop("updated_at", None)
    for variant in stable.get("variants", []):
        if isinstance(variant, dict):
            variant.pop("updated_at", None)
    return stable


def _products(url: str) -> Iterable[dict[str, Any]]:
    for page_number in range(1, 10):
        payload = json.loads(fetch_text(f"{url}?limit=250&page={page_number}"))
        products = payload.get("products", [])
        if not products:
            return
        yield from products
        if len(products) < 250:
            return


def _keep(collection: str, title: str) -> bool:
    if collection in {"fanatic", "primers"}:
        return True
    if collection == "speedpaint":
        return not re.search(r"\b(Set|Marker|Wargamers)\b", title, re.IGNORECASE)
    return bool(re.match(r"Warpaints Air(?::| Metallics:| Fluorescent:)", title)) or title in {
        "Airbrush Medium, 100 ml", "Airbrush Cleaner, 100 ml",
    }


def _metadata(collection: str, title: str) -> tuple[str, str, int | float, str, str]:
    if collection == "fanatic":
        range_name, default, volume, finish, opacity = "Warpaints Fanatic", "opaque_standard", 18, "matte", "opaque"
    elif collection == "speedpaint":
        range_name, default, volume, finish, opacity = "Speedpaint", "one_coat_contrast", 18, "transparent contrast", "transparent"
    elif collection == "air":
        range_name, default, volume, finish, opacity = "Warpaints Air", "airbrush", 18, "matte", "opaque"
    else:
        range_name, default, volume, finish, opacity = "Colour Primer", "primer", 400, "matte", "opaque"
    if "Metallic" in title or re.search(r"\b(Silver|Gold|Copper|Gun Metal|Bronze|Steel)\b", title, re.IGNORECASE):
        default, finish = "metallic", "metallic"
    if "Effects:" in title:
        default = "technical_effect"
    return range_name, classify(title, default), volume, finish, opacity


def collect(catalog: dict[str, Any], _: Path) -> list[dict[str, Any]]:
    by_reference, _ = existing_indexes(catalog)
    products: dict[str, tuple[str, dict[str, Any]]] = {}
    for collection, url in COLLECTIONS.items():
        for product in _products(url):
            title = str(product.get("title", "")).strip()
            reference_code = reference((product.get("variants") or [{}])[0].get("sku"))
            if reference_code and _keep(collection, title):
                products[reference_code] = (collection, product)
    records: list[dict[str, Any]] = []
    for reference_code, (collection, product) in products.items():
        title = str(product.get("title", "")).strip()
        range_name, functional_type, volume, finish, opacity = _metadata(collection, title)
        name = re.sub(r"^(?:Warpaints Fanatic(?: Effects| Metallic| Wash)?|Warpaints Air(?: Metallics| Fluorescent)?|Speedpaint)[:,]\s*", "", title).strip()
        variant = (product.get("variants") or [{}])[0]
        if variant.get("grams") and not title.endswith("100 ml") and collection != "primers":
            volume = 18
        image = str((product.get("images") or [{}])[0].get("src", ""))
        page = f"https://thearmypainter.com/products/{product.get('handle')}"
        record = base_record(
            identifier=f"the-army-painter-{slug(range_name)}-{slug(reference_code)}", brand="The Army Painter",
            manufacturer="The Army Painter", range_name=range_name, functional_type=functional_type,
            reference_code=reference_code, name=name, page=page, image=image, volume_ml=volume,
            finish=finish, opacity=opacity, summary=plain(str(product.get("body_html", "")))[:500],
        )
        record["source_snapshots"] = source_snapshot("army_painter_shopify_product", page, stable_payload(product))
        records.append(merge_existing(record, by_reference))
    return records
