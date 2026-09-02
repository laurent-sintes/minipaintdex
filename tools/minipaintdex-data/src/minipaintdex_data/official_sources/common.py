"""Shared normalization helpers for official paint-source adapters."""

from __future__ import annotations

import html
import json
import re
import unicodedata
from copy import deepcopy
from datetime import date
from typing import Any
from urllib.request import Request, urlopen

from ..paint_identity import market_paint_deduplication_key
from ..image_quality import normalize_image_quality, prefer_image, quality_limitation
from ..paint_model import canonical_color_family, canonical_profile, source_observation


TECHNICAL_TYPES = {"technical_effect", "primer", "wash_shade", "ink", "varnish", "medium", "auxiliary", "pigment"}


def fetch_text(url: str) -> str:
    request = Request(url, headers={"User-Agent": "MiniPaintDex/0.2 official-catalog-refresh"})
    with urlopen(request, timeout=60) as response:  # noqa: S310 - provider adapters use fixed official URLs
        return response.read().decode(response.headers.get_content_charset() or "utf-8", errors="replace")


def fetch_json(url: str, payload: dict[str, Any], *, headers: dict[str, str] | None = None) -> dict[str, Any]:
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    request = Request(
        url,
        data=body,
        headers={
            "User-Agent": "MiniPaintDex/0.2 official-catalog-refresh",
            "Content-Type": "application/json",
            **(headers or {}),
        },
        method="POST",
    )
    with urlopen(request, timeout=60) as response:  # noqa: S310 - provider adapters use fixed official URLs
        value = json.loads(response.read().decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"Official catalogue endpoint returned an invalid document: {url}")
    return value


def slug(value: str) -> str:
    ascii_value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", ascii_value.lower()).strip("-") or "paint"


def plain(value: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", value))).strip()


def reference(value: Any) -> str:
    return re.sub(r"\s+", "", str(value or "").strip()).upper()


def source_snapshot(provider: str, url: str, payload: dict[str, Any]) -> list[dict[str, Any]]:
    return [{"provider": provider, "url": url, "payload": deepcopy(payload)}]


def color_family(name: str) -> str:
    lowered = name.casefold()
    families = (
        ("white", ("white", "ivory", "bone")),
        ("black", ("black",)),
        ("grey", ("grey", "gray", "slate")),
        ("red", ("red", "scarlet", "carmine", "vermilion", "blood", "rust")),
        ("orange", ("orange",)),
        ("yellow", ("yellow", "ochre")),
        ("green", ("green", "olive", "emerald")),
        ("blue", ("blue", "azure", "turquoise")),
        ("purple", ("purple", "violet", "magenta")),
        ("pink", ("pink", "rose")),
        ("brown", ("brown", "umber", "sienna", "leather", "earth", "mud")),
        ("skin", ("skin", "flesh")),
        ("beige", ("beige", "sand", "khaki", "buff")),
        ("gold", ("gold",)),
        ("silver", ("silver", "aluminium", "steel", "gunmetal")),
        ("copper", ("copper", "bronze", "brass")),
    )
    return next((family for family, words in families if any(word in lowered for word in words)), "")


def usage(functional_type: str, name: str, summary: str = "") -> dict[str, Any]:
    provenance = {
        "instruction_status": "manufacturer_summary_with_generic_steps" if summary else "generic_template",
        "review_required": True,
    }
    if functional_type == "primer":
        return provenance | {
            "summary": summary or "Primer intended to provide an even surface for subsequent paint layers.",
            "steps": ["Shake thoroughly.", "Apply thin, even coats to a clean model.", "Allow to dry fully before painting."],
            "tips": ["Keep surface detail visible and follow the safety directions on the label."],
        }
    if functional_type == "wash_shade":
        return provenance | {
            "summary": summary or "Low-viscosity wash intended to collect in recesses and reinforce shadows or weathering.",
            "steps": ["Shake thoroughly.", "Apply selectively to recesses or the target area.", "Remove unwanted pooling and let the layer dry."],
            "tips": ["Build several controlled layers instead of flooding fine details."],
        }
    if functional_type == "technical_effect":
        return provenance | {
            "summary": summary or f"Special-effect product ({name}) for controlled finishing and weathering work.",
            "steps": ["Mix or shake the product as directed on its label.", "Test the effect, then apply it to the intended area.", "Let the effect set before adding another layer."],
            "tips": ["Use dedicated tools when required and consult the manufacturer page for product-specific precautions."],
        }
    if functional_type == "ink":
        return provenance | {
            "summary": summary or "Highly fluid, intense colour for tinting, glazing and controlled shading.",
            "steps": ["Shake thoroughly.", "Apply a thin controlled layer.", "Let it dry before evaluating colour intensity."],
            "tips": ["Reduce or dilute only with a compatible medium."],
        }
    if functional_type in {"auxiliary", "varnish", "medium"}:
        return provenance | {
            "summary": summary or f"Auxiliary product ({name}) used to modify, protect or support the painting process.",
            "steps": ["Read the manufacturer directions and shake if required.", "Use a controlled amount for the stated purpose.", "Allow the product to dry or cure before the next operation."],
            "tips": ["Check compatibility on a test surface and follow label precautions."],
        }
    return {"summary": "", "steps": [], "tips": [], "instruction_status": "not_applicable", "review_required": False}


def classify(name: str, default: str) -> str:
    lowered = name.casefold()
    if re.search(r"\b(?:varnish|vernis|ardcoat|stormshield)\b", lowered):
        return "varnish"
    if re.search(r"\b(?:medium|médium)(?:,?\s+\d+\s*ml)?$", lowered):
        return "medium"
    if re.search(r"\b(?:thinner|cleaner|improver|softener|binder|retarder|diluant|stabilizer)\b", lowered):
        return "auxiliary"
    if re.search(r"\b(?:primer|undercoat|imprim\w*)\b", lowered):
        return "primer"
    if re.search(r"\b(?:wash|lavis|shade)\b", lowered):
        return "wash_shade"
    if re.search(r"\b(?:ink|encre)\b", lowered):
        return "ink"
    if re.search(r"\b(?:texture|stains?|spills?|soot|effects?|mastic|masque)\b", lowered):
        return "technical_effect"
    return default


def base_record(
    *, identifier: str, brand: str, manufacturer: str, range_name: str, functional_type: str,
    reference_code: str, name: str, page: str, image: str = "", volume_ml: int | float = 0,
    finish: str = "", opacity: str = "", medium: str = "acrylic", summary: str = "",
) -> dict[str, Any]:
    record = {
        "schema_version": 1,
        "id": identifier,
        "observed_brand": "",
        "brand": brand,
        "brand_aliases": [],
        "manufacturer": manufacturer,
        "observed_range": "",
        "range": range_name,
        "functional_type": functional_type,
        "reference": reference_code,
        "name": name,
        "confidence": 1.0,
        "data_status": "confirmed",
        "lifecycle_status": "active",
        "warnings": [],
        "color": {"hex": "", "family": canonical_color_family(
            {"roles": ["auxiliary" if functional_type in {"auxiliary", "varnish", "medium"} else "color_paint"]},
            "" if functional_type in {"auxiliary", "varnish", "medium"} else color_family(name),
        )},
        "finish": finish,
        "medium": medium,
        "opacity": opacity,
        "volume_ml": volume_ml,
        "tags": [range_name],
        "recommended_uses": [],
        "usage_instructions": usage(functional_type, name, summary),
        "manufacturer_page": page,
        "manufacturer_image": {
            "path": "", "source_url": image,
            "credit": f"Official {manufacturer} catalogue" if image else "",
            "image_quality": "official_photo" if image else "none",
            "quality_verified_at": date.today().isoformat() if image else "",
            **({} if image else {"quality_limitation": quality_limitation(
                "official-source-unavailable",
                "The official catalog record did not expose a usable product photo.",
                date.today().isoformat(),
            )}),
        },
        "result_image": {"path": "", "source_url": "", "credit": "", "license": "", "reference_url": ""},
        "provenance": {"photo": "", "hashes": []},
        "verified_at": date.today().isoformat(),
        "notes": "",
        "deduplication_key": market_paint_deduplication_key(brand, reference_code),
    }
    record["profile"], record["mapping_report"] = canonical_profile(record)
    record["source_observation"] = source_observation(record)
    for field in ("functional_type", "finish", "medium", "opacity"):
        record.pop(field, None)
    return record


def existing_indexes(catalog: dict[str, Any]) -> tuple[dict[tuple[str, str], dict[str, Any]], dict[str, dict[str, Any]]]:
    paints = [paint for paint in catalog.get("paints", []) if isinstance(paint, dict)]
    by_reference = {
        (str(paint.get("brand", "")).casefold(), reference(paint.get("reference"))): paint
        for paint in paints if reference(paint.get("reference"))
    }
    return by_reference, {str(paint.get("id")): paint for paint in paints}


def merge_existing(record: dict[str, Any], by_reference: dict[tuple[str, str], dict[str, Any]]) -> dict[str, Any]:
    previous = by_reference.get((record["brand"].casefold(), reference(record.get("reference"))))
    return merge_previous(record, previous) if previous else record


def merge_previous(record: dict[str, Any], previous: dict[str, Any]) -> dict[str, Any]:
    record = normalize_image_quality(record)
    previous = normalize_image_quality(previous)
    merged = deepcopy(previous)
    for key in ("schema_version", "brand", "manufacturer", "range", "profile", "reference", "name", "confidence", "data_status", "lifecycle_status", "volume_ml", "manufacturer_page", "verified_at", "deduplication_key"):
        if record.get(key) not in (None, "", 0, []):
            merged[key] = record[key]
    for key in ("color",):
        target = merged.setdefault(key, {})
        for item_key, value in record.get(key, {}).items():
            if value not in (None, "", 0, []):
                target[item_key] = value
    merged["manufacturer_image"] = prefer_image(
        previous.get("manufacturer_image", {}), record.get("manufacturer_image", {}),
    )
    if record.get("usage_instructions", {}).get("summary"):
        merged["usage_instructions"] = record["usage_instructions"]
    if record.get("source_snapshots"):
        merged["source_snapshots"] = deepcopy(record["source_snapshots"])
    return merged
