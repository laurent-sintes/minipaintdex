"""Vallejo official catalogue-PDF adapter."""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Iterable

from .common import base_record, classify, existing_indexes, merge_existing, slug, source_snapshot


CATALOG_URL = "https://acrylicosvallejo.com/wp-content/uploads/2025/10/Catalogo_2026-R02.pdf"
OFFICIAL_URLS = (CATALOG_URL,)
REFERENCE_LINE = re.compile(r"^(\d{2})\.\s*(\d{3})\s*\*?$")
SPECIFICATIONS = (
    ((12, 13), "Model Color", "opaque_standard", "70.", 18, "matte", "opaque"),
    ((25,), "Liquid Metal", "metallic", "70.", 35, "high gloss metallic", "opaque"),
    ((28, 29), "Model Air", "airbrush", "71.", 18, "matte", "opaque"),
    ((44,), "Metal Color", "metallic", "77.", 32, "metallic", "opaque"),
    ((48,), "Primers", "primer", None, 18, "matte", "opaque"),
    ((52, 53), "Game Color", "opaque_standard", "72.", 18, "matte", "opaque"),
    ((68,), "Xpress Color", "one_coat_contrast", "72.", 18, "transparent contrast", "transparent"),
    ((72,), "Game Air", "airbrush", "76.", 18, "matte", "opaque"),
    ((76,), "True Metallic Metal", "metallic", "77.", 18, "metallic", "opaque"),
    ((82,), "Mecha Color", "opaque_standard", "69.", 18, "", "opaque"),
    ((90,), "Hobby Paint", "primer", "28.", 400, "", "opaque"),
    ((132,), "Pigment FX", "pigment", None, 35, "matte", ""),
    ((138,), "Wash FX", "wash_shade", "76.", 35, "matte", "transparent"),
)


def parse_lines(lines: Iterable[str], *, prefix: str | None = None) -> list[tuple[str, str]]:
    values = [re.sub(r"\s+", " ", line).strip() for line in lines]
    records: list[tuple[str, str]] = []
    seen: set[str] = set()
    for index, value in enumerate(values):
        match = REFERENCE_LINE.match(value)
        if not match:
            continue
        reference_code = f"{match.group(1)}.{match.group(2)}"
        if (prefix and not reference_code.startswith(prefix)) or reference_code in seen:
            continue
        name = next((candidate for candidate in values[index + 1:] if candidate), "")
        if name and not REFERENCE_LINE.match(name):
            records.append((reference_code, name.split(" / ", 1)[0].strip()))
            seen.add(reference_code)
    return records


def collect(catalog: dict[str, Any], pdf_path: Path) -> list[dict[str, Any]]:
    try:
        from pypdf import PdfReader
    except ImportError as error:  # pragma: no cover - depends on the operator environment
        raise ValueError("pypdf is required to read the official Vallejo catalogue PDF.") from error
    by_reference, _ = existing_indexes(catalog)
    reader = PdfReader(pdf_path)
    records_by_reference: dict[str, dict[str, Any]] = {}
    for pages, range_name, default_type, prefix, volume, finish, opacity in SPECIFICATIONS:
        for page_number in pages:
            text = reader.pages[page_number - 1].extract_text() or ""
            for reference_code, name in parse_lines(text.splitlines(), prefix=prefix):
                functional_type = classify(name, default_type)
                record = base_record(
                    identifier=f"vallejo-{slug(range_name)}-{slug(reference_code)}", brand="Vallejo",
                    manufacturer="Acrylicos Vallejo", range_name=range_name, functional_type=functional_type,
                    reference_code=reference_code, name=name, page=CATALOG_URL, volume_ml=volume,
                    finish=finish, opacity=opacity,
                    medium="isopropyl alcohol" if range_name == "Liquid Metal" else "water-based acrylic",
                    summary=f"Official Vallejo 2026 catalogue, {range_name} chart, page {page_number}.",
                )
                record["notes"] = f"Verified in the official Vallejo 2026 catalogue, page {page_number}."
                record["source_snapshots"] = source_snapshot(
                    "vallejo_catalogue_pdf", CATALOG_URL,
                    {"page": page_number, "range": range_name, "reference": reference_code, "name": name},
                )
                records_by_reference[reference_code] = merge_existing(record, by_reference)
    return list(records_by_reference.values())
