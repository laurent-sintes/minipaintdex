"""Warhammer Colour official store-search adapter."""

from __future__ import annotations

import re
from datetime import date
from pathlib import Path
from typing import Any
from urllib.parse import urlencode

from ..paint_identity import market_paint_id
from .common import base_record, classify, color_family, existing_indexes, fetch_json, merge_previous, reference, slug, source_snapshot
from ..image_quality import prefer_image, quality_limitation


RANGE_URL = "https://paint.warhammer.com/the-paint-range/"
STORE_URL = "https://www.warhammer.com"
SEARCH_URL = "https://M5ZIQZNQ2H-dsn.algolia.net/1/indexes/prod-lazarus-product-en-gb/query"
SEARCH_HEADERS = {
    "X-Algolia-Application-Id": "M5ZIQZNQ2H",
    # Public search-only key shipped to every visitor of warhammer.com.
    "X-Algolia-API-Key": "92c6a8254f9d34362df8e6d96475e5d8",
}
OFFICIAL_URLS = (RANGE_URL, STORE_URL + "/en-GB/paint")
COLOUR_SWATCH_WARNING = "Official store artwork is a colour swatch, not a product packshot."


def _hits() -> list[dict[str, Any]]:
    payload = fetch_json(
        SEARCH_URL,
        {"params": urlencode({"hitsPerPage": 1000, "facetFilters": "productType:paint"})},
        headers=SEARCH_HEADERS,
    )
    hits = payload.get("hits")
    if not isinstance(hits, list) or payload.get("nbHits") != len(hits):
        raise ValueError(
            "Warhammer paint search is incomplete: "
            f"expected {payload.get('nbHits')}, received {len(hits) if isinstance(hits, list) else 0}."
        )
    return [hit for hit in hits if isinstance(hit, dict)]


def _metadata(paint_type: str, color_range: str, name: str) -> tuple[str, int | float, str, str]:
    defaults = {
        "Base": ("opaque_standard", 12, "matte", "opaque"),
        "Layer": ("opaque_standard", 12, "matte", "opaque"),
        "Shade": ("wash_shade", 18, "matte", "transparent"),
        "Dry": ("opaque_standard", 12, "matte", "opaque"),
        "Contrast": ("one_coat_contrast", 18, "", "transparent"),
        "Technical": ("technical_effect", 0, "", ""),
        "Spray": ("primer", 400, "matte", "opaque"),
        "Air": ("airbrush", 24, "matte", "opaque"),
    }
    functional_type, volume, finish, opacity = defaults.get(paint_type, ("opaque_standard", 0, "", ""))
    if color_range.casefold() in {"gold", "silver", "brass", "bronze", "copper"}:
        finish = "metallic"
        if paint_type in {"Base", "Layer", "Dry", "Air"}:
            functional_type = "metallic"
    return classify(name, functional_type), volume, finish, opacity


def collect(catalog: dict[str, Any], _: Path) -> list[dict[str, Any]]:
    by_reference, _ = existing_indexes(catalog)
    by_name_range = {
        (str(paint.get("range", "")).casefold(), str(paint.get("name", "")).casefold()): paint
        for paint in catalog.get("paints", [])
        if isinstance(paint, dict) and paint.get("brand") == "Warhammer Colour"
    }
    records: list[dict[str, Any]] = []
    for hit in _hits():
        paint_types = hit.get("paintType") or []
        if not isinstance(paint_types, list) or len(paint_types) != 1:
            continue
        paint_type = str(paint_types[0]).strip()
        name = re.sub(rf"^{re.escape(paint_type)}\s*:\s*", "", str(hit.get("name", "")).strip(), flags=re.IGNORECASE)
        reference_code = reference(hit.get("sku"))
        if not name or not reference_code:
            continue
        color_range = str(hit.get("paintColourRange", "")).strip()
        functional_type, volume, finish, opacity = _metadata(paint_type, color_range, name)
        product_slug = str(hit.get("slug", "")).strip()
        record = base_record(
            identifier=market_paint_id("Warhammer Colour", reference_code), brand="Warhammer Colour",
            manufacturer="Games Workshop", range_name=paint_type, functional_type=functional_type,
            reference_code=reference_code, name=name,
            page=f"{STORE_URL}/en-GB/shop/{product_slug}" if product_slug else RANGE_URL,
            image="", volume_ml=volume, finish=finish, opacity=opacity,
            summary=str(hit.get("description", "")).strip(" ."),
        )
        record["source_snapshots"] = source_snapshot(
            "warhammer_store_search", record["manufacturer_page"],
            {key: value for key, value in hit.items() if key != "_highlightResult"},
        )
        if color_range:
            record["color"]["family"] = color_family(color_range)
        previous = by_reference.get(("warhammer colour", reference_code)) or by_name_range.get(
            (paint_type.casefold(), name.casefold())
        )
        if previous:
            previous_reference = reference(previous.get("reference"))
            record["id"] = previous["id"]
            record["deduplication_key"] = previous.get("deduplication_key", record["deduplication_key"])
            record = merge_previous(record, previous)
            if previous_reference and previous_reference != reference_code:
                # An id is immutable after first import. Keep the prior identity basis until an
                # explicit rekey can migrate every mutable reference atomically.
                record["reference"] = previous_reference
                record["deduplication_key"] = previous.get("deduplication_key", record["deduplication_key"])
                record["warnings"] = sorted(set(record.get("warnings", [])) | {
                    f"Official store reference changed from {previous_reference} to {reference_code}; "
                    "explicit identity reconciliation is required."
                })
        record["manufacturer_image"] = prefer_image(record.get("manufacturer_image", {}), {
            "path": "", "source_url": "", "credit": "Official Games Workshop colour swatch",
            "license": "", "reference_url": record["manufacturer_page"],
            "image_quality": "color_swatch", "quality_verified_at": date.today().isoformat(),
            "quality_limitation": quality_limitation(
                "official-photo-not-published",
                "The official Games Workshop catalog publishes a color swatch rather than a usable product photo.",
                date.today().isoformat(),
            ),
        })
        record["warnings"] = sorted(set(record.get("warnings", [])) | {COLOUR_SWATCH_WARNING})
        records.append(record)
    return records
