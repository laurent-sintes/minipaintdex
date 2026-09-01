"""Stable identities for canonical market paints."""

from __future__ import annotations

import re
import unicodedata
from typing import Any

from .paint_model import load_mappings


BRAND_CODE_PATTERN = re.compile(r"^[a-z][a-z0-9]{2,4}$")


def identity_segment(value: Any) -> str:
    ascii_value = unicodedata.normalize("NFKD", str(value or "")).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", ascii_value.lower()).strip("-")


def brand_code(brand: Any) -> str:
    key = str(brand or "").strip().casefold()
    mapping = load_mappings().get(key)
    if mapping is None:
        raise ValueError(f"No canonical paint mapping for brand: {brand}")
    code = str(mapping.get("brand_code", "")).strip()
    if not BRAND_CODE_PATTERN.fullmatch(code):
        raise ValueError(f"Invalid paint brand code for {mapping.get('brand')}: {code}")
    return code


def market_paint_id(brand: Any, manufacturer_reference: Any) -> str:
    normalized_reference = identity_segment(manufacturer_reference)
    if not normalized_reference:
        raise ValueError(f"A manufacturer reference is required to generate a paint id for {brand}.")
    return f"{brand_code(brand)}-{normalized_reference}"


def market_paint_deduplication_key(brand: Any, manufacturer_reference: Any) -> str:
    normalized_reference = identity_segment(manufacturer_reference)
    if not normalized_reference:
        raise ValueError(f"A manufacturer reference is required to deduplicate a paint for {brand}.")
    return f"{brand_code(brand)}|ref:{normalized_reference}"
