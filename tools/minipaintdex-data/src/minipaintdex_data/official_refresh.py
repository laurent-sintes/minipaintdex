"""Orchestrate independent official manufacturer paint-source adapters."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any, Callable, Iterable

from .official_sources import army_painter, prince_august, vallejo, warhammer
from .refresh import read_catalog


Collector = Callable[[dict[str, Any], Path], list[dict[str, Any]]]


@dataclass(frozen=True)
class ProviderSpec:
    brand: str
    provider: str
    mode: str
    scope: str
    official_urls: tuple[str, ...]
    collect: Collector
    minimum_count: int
    minimum_known_ratio: float = 0.80
    coverage_complete: bool = False


OFFICIAL_PROVIDERS = {
    spec.brand: spec
    for spec in (
        ProviderSpec(
            "Prince August", "prince_august", "official_source_collection",
            "Official Classic range; other distributed ranges excluded.",
            prince_august.OFFICIAL_URLS, prince_august.collect, 150,
        ),
        ProviderSpec(
            "The Army Painter", "army_painter", "official_source_collection",
            "Official Fanatic singles, Speedpaint singles, Warpaints Air singles and primers.",
            army_painter.OFFICIAL_URLS, army_painter.collect, 400,
        ),
        ProviderSpec(
            "Vallejo", "vallejo", "official_source_collection",
            "Colour charts parsed from the official 2026 models and miniatures catalogue.",
            vallejo.OFFICIAL_URLS, vallejo.collect, 800,
        ),
        ProviderSpec(
            "Warhammer Colour", "warhammer", "official_store_search",
            "All current paint products from the official Warhammer store search index.",
            warhammer.OFFICIAL_URLS, warhammer.collect, 300, minimum_known_ratio=0.90,
            coverage_complete=True,
        ),
    )
}


def _validate_collection(spec: ProviderSpec, paints: list[dict[str, Any]], known_count: int) -> None:
    count = len(paints)
    if count < spec.minimum_count:
        raise ValueError(
            f"{spec.brand} collection gate failed: expected at least {spec.minimum_count}, received {count}."
        )
    minimum_from_catalog = int(known_count * spec.minimum_known_ratio)
    if known_count and count < minimum_from_catalog:
        raise ValueError(
            f"{spec.brand} collection gate failed: {count} records are below "
            f"{spec.minimum_known_ratio:.0%} of the {known_count} known records."
        )
    identifiers = [str(paint.get("id", "")).strip() for paint in paints]
    duplicates = sorted(identifier for identifier, count in Counter(identifiers).items() if count > 1)
    if not all(identifiers) or duplicates:
        detail = f" duplicate ids: {', '.join(duplicates[:5])}" if duplicates else " missing id"
        raise ValueError(f"{spec.brand} collection gate failed:{detail}.")
    wrong_brands = sorted({str(paint.get("brand", "")) for paint in paints if paint.get("brand") != spec.brand})
    if wrong_brands:
        raise ValueError(f"{spec.brand} adapter returned records for: {', '.join(wrong_brands)}")
    without_snapshot = [paint["id"] for paint in paints if not paint.get("source_snapshots")]
    if without_snapshot:
        raise ValueError(f"{spec.brand} collection gate failed: {len(without_snapshot)} record(s) lack source snapshots.")


def collect_official_refresh(
    catalog_path: Path,
    vallejo_pdf: Path | None,
    *,
    verified_at: str | None = None,
    brands: Iterable[str] | None = None,
) -> dict[str, Any]:
    verification_date = verified_at or date.today().isoformat()
    requested = list(brands or OFFICIAL_PROVIDERS)
    if any(brand.casefold() == "all" for brand in requested):
        requested = list(OFFICIAL_PROVIDERS)
    canonical = {brand.casefold(): brand for brand in OFFICIAL_PROVIDERS}
    unknown = [brand for brand in requested if brand.casefold() not in canonical]
    if unknown:
        raise ValueError(f"No official catalogue provider is registered for: {', '.join(unknown)}")
    selected = list(dict.fromkeys(canonical[brand.casefold()] for brand in requested))
    if "Vallejo" in selected and vallejo_pdf is None:
        raise ValueError("--vallejo-pdf is required when the Vallejo provider is selected.")
    catalog = read_catalog(catalog_path)
    paints: list[dict[str, Any]] = []
    audit: list[dict[str, Any]] = []
    existing_counts = {
        brand: sum(1 for paint in catalog.get("paints", []) if paint.get("brand") == brand)
        for brand in selected
    }
    for brand in selected:
        spec = OFFICIAL_PROVIDERS[brand]
        collected = spec.collect(catalog, vallejo_pdf or Path())
        _validate_collection(spec, collected, existing_counts[brand])
        paints.extend(collected)
        audit.append({
            "brand": brand,
            "provider": spec.provider,
            "provider_mode": spec.mode,
            "known_count": existing_counts[brand],
            "collected_count": len(collected),
            "minimum_count": spec.minimum_count,
            "minimum_known_ratio": spec.minimum_known_ratio,
            "coverage_complete": spec.coverage_complete,
            "images": {
                "local": sum(bool((paint.get("manufacturer_image") or {}).get("path")) for paint in collected),
                "remote_only": sum(
                    not bool((paint.get("manufacturer_image") or {}).get("path"))
                    and bool((paint.get("manufacturer_image") or {}).get("source_url"))
                    for paint in collected
                ),
                "missing": sum(
                    not bool((paint.get("manufacturer_image") or {}).get("path"))
                    and not bool((paint.get("manufacturer_image") or {}).get("source_url"))
                    for paint in collected
                ),
            },
            "source_snapshots": sum(len(paint.get("source_snapshots", [])) for paint in collected),
            "scope": spec.scope,
        })
    unique = {paint["id"]: paint for paint in paints}
    if len(unique) != len(paints):
        raise ValueError("Official providers returned duplicate paint ids across brands.")
    for paint in unique.values():
        paint["verified_at"] = verification_date
    return {
        "coverage": [
            {"brand": brand, "complete": OFFICIAL_PROVIDERS[brand].coverage_complete, "scope": OFFICIAL_PROVIDERS[brand].scope}
            for brand in selected
        ],
        "source": {
            "generated_at": verification_date,
            "official_urls": [url for brand in selected for url in OFFICIAL_PROVIDERS[brand].official_urls],
        },
        "audit": audit,
        "paints": sorted(unique.values(), key=lambda paint: (paint["brand"], paint["range"], paint["name"], paint["id"])),
    }
