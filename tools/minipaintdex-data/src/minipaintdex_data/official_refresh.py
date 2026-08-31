"""Collect verified paint records from official manufacturer catalogues."""

from __future__ import annotations

import html
import json
import re
import unicodedata
from copy import deepcopy
from datetime import date
from pathlib import Path
from typing import Any, Iterable
from urllib.request import Request, urlopen

import yaml


VALLEJO_CATALOG_URL = "https://acrylicosvallejo.com/wp-content/uploads/2025/10/Catalogo_2026-R02.pdf"
WARHAMMER_RANGE_URL = "https://paint.warhammer.com/the-paint-range/"
PRINCE_AUGUST_CLASSIC_URL = "https://www.prince-august.net/peintures/classic/"
ARMY_PAINTER_COLLECTIONS = {
    "fanatic": "https://thearmypainter.com/en-gb/collections/warpaints-fanatic-singles/products.json",
    "speedpaint": "https://thearmypainter.com/en-gb/collections/speedpaint/products.json",
    "air": "https://thearmypainter.com/en-gb/collections/warpaints-air/products.json",
    "primers": "https://thearmypainter.com/collections/primers/products.json",
}
TECHNICAL_TYPES = {"technical_effect", "primer", "wash_shade", "ink", "auxiliary"}
REFERENCE_LINE = re.compile(r"^(\d{2})\.\s*(\d{3})\s*\*?$")
PRODUCT_CARD = re.compile(
    r'<div class="single-element">.*?<img[^>]+src="([^"]+)".*?'
    r'<h3><a href="([^"]+)">(.*?)</a></h3>\s*<p>(.*?)</p>',
    re.DOTALL | re.IGNORECASE,
)


def _fetch_text(url: str) -> str:
    request = Request(url, headers={"User-Agent": "MiniPaintDex/0.2 official-catalog-refresh"})
    with urlopen(request, timeout=60) as response:  # noqa: S310 - fixed official URLs
        return response.read().decode(response.headers.get_content_charset() or "utf-8", errors="replace")


def _slug(value: str) -> str:
    ascii_value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "-", ascii_value.lower()).strip("-") or "paint"


def _plain(value: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", value))).strip()


def _reference(value: Any) -> str:
    return re.sub(r"\s+", "", str(value or "").strip()).upper()


def _color_family(name: str) -> str:
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


def _usage(functional_type: str, name: str, summary: str = "") -> dict[str, Any]:
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
    if functional_type == "auxiliary":
        return provenance | {
            "summary": summary or f"Auxiliary product ({name}) used to modify, protect or support the painting process.",
            "steps": ["Read the manufacturer directions and shake if required.", "Use a controlled amount for the stated purpose.", "Allow the product to dry or cure before the next operation."],
            "tips": ["Check compatibility on a test surface and follow label precautions."],
        }
    return {"summary": "", "steps": [], "tips": [], "instruction_status": "not_applicable", "review_required": False}


def _classify(name: str, default: str) -> str:
    lowered = name.casefold()
    if any(word in lowered for word in ("varnish", "medium", "thinner", "cleaner", "improver", "softener", "binder", "retarder", "diluant", "vernis", "médium", "medium")):
        return "auxiliary"
    if "primer" in lowered or "imprim" in lowered or "undercoat" in lowered:
        return "primer"
    if "wash" in lowered or "lavis" in lowered or "shade" in lowered:
        return "wash_shade"
    if "ink" in lowered or "encre" in lowered:
        return "ink"
    if any(word in lowered for word in ("texture", "stain", "spill", "soot", "effect", "mastic", "masque")):
        return "technical_effect"
    return default


def _base_record(
    *, identifier: str, brand: str, manufacturer: str, range_name: str, functional_type: str,
    reference: str, name: str, page: str, image: str = "", volume_ml: int | float = 0,
    finish: str = "", opacity: str = "", medium: str = "acrylic", summary: str = "",
) -> dict[str, Any]:
    return {
        "id": identifier,
        "observed_brand": "",
        "brand": brand,
        "brand_aliases": [],
        "manufacturer": manufacturer,
        "observed_range": "",
        "range": range_name,
        "functional_type": functional_type,
        "reference": reference,
        "name": name,
        "confidence": 1.0,
        "data_status": "confirmed",
        "lifecycle_status": "active",
        "warnings": [],
        "color": {"hex": "", "family": _color_family(name)},
        "finish": finish,
        "medium": medium,
        "opacity": opacity,
        "volume_ml": volume_ml,
        "tags": [range_name],
        "recommended_uses": [],
        "usage_instructions": _usage(functional_type, name, summary),
        "manufacturer_page": page,
        "manufacturer_image": {"path": "", "source_url": image, "credit": f"Official {manufacturer} catalogue" if image else ""},
        "result_image": {"path": "", "source_url": "", "credit": "", "license": "", "reference_url": ""},
        "provenance": {"photo": "", "hashes": []},
        "verified_at": date.today().isoformat(),
        "notes": "",
        "deduplication_key": f"{_slug(brand)}|{_slug(range_name)}|ref:{reference}",
    }


def _existing_indexes(catalog: dict[str, Any]) -> tuple[dict[tuple[str, str], dict[str, Any]], dict[str, dict[str, Any]]]:
    paints = [paint for paint in catalog.get("paints", []) if isinstance(paint, dict)]
    by_reference = {
        (str(paint.get("brand", "")).casefold(), _reference(paint.get("reference"))): paint
        for paint in paints if _reference(paint.get("reference"))
    }
    return by_reference, {str(paint.get("id")): paint for paint in paints}


def _merge_existing(record: dict[str, Any], by_reference: dict[tuple[str, str], dict[str, Any]]) -> dict[str, Any]:
    previous = by_reference.get((record["brand"].casefold(), _reference(record.get("reference"))))
    if not previous:
        return record
    merged = deepcopy(previous)
    for key in ("brand", "manufacturer", "range", "functional_type", "reference", "name", "confidence", "data_status", "lifecycle_status", "finish", "medium", "opacity", "volume_ml", "manufacturer_page", "verified_at", "deduplication_key"):
        if record.get(key) not in (None, "", 0, []):
            merged[key] = record[key]
    for key in ("color", "manufacturer_image"):
        target = merged.setdefault(key, {})
        for item_key, value in record.get(key, {}).items():
            if value not in (None, "", 0, []):
                target[item_key] = value
    if record.get("usage_instructions", {}).get("summary"):
        merged["usage_instructions"] = record["usage_instructions"]
    return merged


def parse_prince_august_cards(page: str) -> list[dict[str, str]]:
    records: list[dict[str, str]] = []
    for image, url, title, description in PRODUCT_CARD.findall(page):
        title = _plain(title)
        parts = [part.strip() for part in re.split(r"\s+[–-]\s+", title, maxsplit=2)]
        if not parts or not re.fullmatch(r"P\d+", parts[0], re.IGNORECASE):
            continue
        records.append({
            "reference": parts[0].upper(),
            "name": parts[-1],
            "url": html.unescape(url),
            "image": html.unescape(image),
            "description": _plain(description),
        })
    return records


def collect_prince_august(catalog: dict[str, Any]) -> list[dict[str, Any]]:
    by_reference, _ = _existing_indexes(catalog)
    cards: dict[str, dict[str, str]] = {}
    for page_number in range(1, 16):
        separator = "?" if page_number > 1 else ""
        suffix = f"sf_paged={page_number}" if page_number > 1 else ""
        for card in parse_prince_august_cards(_fetch_text(PRINCE_AUGUST_CLASSIC_URL + separator + suffix)):
            cards[card["reference"]] = card
    records: list[dict[str, Any]] = []
    for card in cards.values():
        if card["reference"] == "P000":
            continue
        functional_type = _classify(card["name"], "opaque_standard")
        record = _base_record(
            identifier=f"prince-august-classic-{_slug(card['reference'])}", brand="Prince August",
            manufacturer="Prince August", range_name="CLASSIC", functional_type=functional_type,
            reference=card["reference"], name=card["name"], page=card["url"], image=card["image"],
            volume_ml=17, finish="matte" if functional_type == "opaque_standard" else "",
            opacity="opaque" if functional_type == "opaque_standard" else "", summary=card["description"],
        )
        records.append(_merge_existing(record, by_reference))
    return records


def _army_painter_products(url: str) -> Iterable[dict[str, Any]]:
    for page_number in range(1, 10):
        payload = json.loads(_fetch_text(f"{url}?limit=250&page={page_number}"))
        products = payload.get("products", [])
        if not products:
            return
        yield from products
        if len(products) < 250:
            return


def _keep_army_painter(collection: str, title: str) -> bool:
    if collection in {"fanatic", "primers"}:
        return True
    if collection == "speedpaint":
        return not re.search(r"\b(Set|Marker|Wargamers)\b", title, re.IGNORECASE)
    return bool(re.match(r"Warpaints Air(?::| Metallics:| Fluorescent:)", title)) or title in {"Airbrush Medium, 100 ml", "Airbrush Cleaner, 100 ml"}


def _army_painter_metadata(collection: str, title: str) -> tuple[str, str, int | float, str, str]:
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
    return range_name, _classify(title, default), volume, finish, opacity


def collect_army_painter(catalog: dict[str, Any]) -> list[dict[str, Any]]:
    by_reference, _ = _existing_indexes(catalog)
    products: dict[str, tuple[str, dict[str, Any]]] = {}
    for collection, url in ARMY_PAINTER_COLLECTIONS.items():
        for product in _army_painter_products(url):
            title = str(product.get("title", "")).strip()
            reference = _reference((product.get("variants") or [{}])[0].get("sku"))
            if reference and _keep_army_painter(collection, title):
                products[reference] = (collection, product)
    records: list[dict[str, Any]] = []
    for reference, (collection, product) in products.items():
        title = str(product.get("title", "")).strip()
        range_name, functional_type, volume, finish, opacity = _army_painter_metadata(collection, title)
        name = re.sub(r"^(?:Warpaints Fanatic(?: Effects| Metallic| Wash)?|Warpaints Air(?: Metallics| Fluorescent)?|Speedpaint)[:,]\s*", "", title).strip()
        variant = (product.get("variants") or [{}])[0]
        if variant.get("grams") and not str(title).endswith("100 ml") and collection != "primers":
            volume = 18
        image = str((product.get("images") or [{}])[0].get("src", ""))
        page = f"https://thearmypainter.com/products/{product.get('handle')}"
        record = _base_record(
            identifier=f"the-army-painter-{_slug(range_name)}-{_slug(reference)}", brand="The Army Painter",
            manufacturer="The Army Painter", range_name=range_name, functional_type=functional_type,
            reference=reference, name=name, page=page, image=image, volume_ml=volume, finish=finish,
            opacity=opacity, summary=_plain(str(product.get("body_html", "")))[:500],
        )
        records.append(_merge_existing(record, by_reference))
    return records


def parse_vallejo_lines(lines: Iterable[str], *, prefix: str | None = None) -> list[tuple[str, str]]:
    values = [re.sub(r"\s+", " ", line).strip() for line in lines]
    records: list[tuple[str, str]] = []
    seen: set[str] = set()
    for index, value in enumerate(values):
        match = REFERENCE_LINE.match(value)
        if not match:
            continue
        reference = f"{match.group(1)}.{match.group(2)}"
        if (prefix and not reference.startswith(prefix)) or reference in seen:
            continue
        name = next((candidate for candidate in values[index + 1:] if candidate), "")
        if name and not REFERENCE_LINE.match(name):
            records.append((reference, name.split(" / ", 1)[0].strip()))
            seen.add(reference)
    return records


def collect_vallejo(catalog: dict[str, Any], pdf_path: Path) -> list[dict[str, Any]]:
    try:
        from pypdf import PdfReader
    except ImportError as error:  # pragma: no cover - depends on the operator environment
        raise ValueError("pypdf is required to read the official Vallejo catalogue PDF.") from error
    by_reference, _ = _existing_indexes(catalog)
    reader = PdfReader(pdf_path)
    specifications = (
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
        ((132,), "Pigment FX", "technical_effect", None, 35, "matte", ""),
        ((138,), "Wash FX", "wash_shade", "76.", 35, "matte", "transparent"),
    )
    records_by_reference: dict[str, dict[str, Any]] = {}
    for pages, range_name, default_type, prefix, volume, finish, opacity in specifications:
        for page_number in pages:
            text = reader.pages[page_number - 1].extract_text() or ""
            for reference, name in parse_vallejo_lines(text.splitlines(), prefix=prefix):
                functional_type = _classify(name, default_type)
                record = _base_record(
                    identifier=f"vallejo-{_slug(range_name)}-{_slug(reference)}", brand="Vallejo",
                    manufacturer="Acrylicos Vallejo", range_name=range_name, functional_type=functional_type,
                    reference=reference, name=name, page=VALLEJO_CATALOG_URL, volume_ml=volume,
                    finish=finish, opacity=opacity, medium="isopropyl alcohol" if range_name == "Liquid Metal" else "water-based acrylic",
                    summary=f"Official Vallejo 2026 catalogue, {range_name} chart, page {page_number}.",
                )
                record["notes"] = f"Verified in the official Vallejo 2026 catalogue, page {page_number}."
                # A product can be repeated in a later, more specific chart (for
                # example Gloss Black Primer after Metal Color). Prefer that chart.
                records_by_reference[reference] = _merge_existing(record, by_reference)
    return list(records_by_reference.values())


def collect_warhammer(catalog: dict[str, Any]) -> list[dict[str, Any]]:
    _, by_id = _existing_indexes(catalog)
    records: list[dict[str, Any]] = []
    for paint in by_id.values():
        if paint.get("brand") != "Warhammer Colour":
            continue
        record = deepcopy(paint)
        page = str(record.get("manufacturer_page", ""))
        if "warhammer.com" not in page and "games-workshop.com" not in page:
            record["manufacturer_page"] = WARHAMMER_RANGE_URL
        if record.get("functional_type") == "one_coat_contrast":
            record["usage_instructions"] = {
                "summary": "Contrast paint formulated to base, shade and highlight over a light undercoat in one application, or to glaze and tint.",
                "steps": ["Shake thoroughly.", "Apply a generous controlled coat over a light undercoat.", "Guide excess away from flat areas and let the coat dry fully."],
                "tips": ["It can also be used as a glaze or tint; avoid uncontrolled pooling."],
                "instruction_status": "official_range_guidance",
                "review_required": False,
            }
        records.append(record)
    return records


OFFICIAL_PROVIDERS = {
    "Prince August": {
        "collector": "prince_august",
        "scope": "Official Classic range; other distributed ranges excluded.",
        "official_urls": [PRINCE_AUGUST_CLASSIC_URL],
    },
    "The Army Painter": {
        "collector": "army_painter",
        "scope": "Official Fanatic singles, Speedpaint singles, Warpaints Air singles and primers.",
        "official_urls": list(ARMY_PAINTER_COLLECTIONS.values()),
    },
    "Vallejo": {
        "collector": "vallejo",
        "scope": "Colour charts parsed from the official 2026 models and miniatures catalogue.",
        "official_urls": [VALLEJO_CATALOG_URL],
    },
    "Warhammer Colour": {
        "collector": "warhammer",
        "scope": "Known local records checked against official range documentation; exhaustive store enumeration is unavailable.",
        "official_urls": [WARHAMMER_RANGE_URL],
    },
}


def collect_official_refresh(
    catalog_path: Path,
    vallejo_pdf: Path,
    *,
    verified_at: str | None = None,
    brands: Iterable[str] | None = None,
) -> dict[str, Any]:
    with catalog_path.open("r", encoding="utf-8-sig") as handle:
        catalog = yaml.safe_load(handle)
    if not isinstance(catalog, dict) or not isinstance(catalog.get("paints"), list):
        raise ValueError(f"Invalid paint catalog: {catalog_path}")
    verification_date = verified_at or date.today().isoformat()
    requested = list(brands or OFFICIAL_PROVIDERS)
    if any(brand.casefold() == "all" for brand in requested):
        requested = list(OFFICIAL_PROVIDERS)
    canonical = {brand.casefold(): brand for brand in OFFICIAL_PROVIDERS}
    unknown = [brand for brand in requested if brand.casefold() not in canonical]
    if unknown:
        raise ValueError(f"No official catalogue provider is registered for: {', '.join(unknown)}")
    selected = [canonical[brand.casefold()] for brand in requested]
    paints: list[dict[str, Any]] = []
    for brand in selected:
        provider = OFFICIAL_PROVIDERS[brand]["collector"]
        if provider == "prince_august":
            paints.extend(collect_prince_august(catalog))
        elif provider == "army_painter":
            paints.extend(collect_army_painter(catalog))
        elif provider == "vallejo":
            paints.extend(collect_vallejo(catalog, vallejo_pdf))
        elif provider == "warhammer":
            paints.extend(collect_warhammer(catalog))
    unique = {paint["id"]: paint for paint in paints}
    for paint in unique.values():
        paint["verified_at"] = verification_date
    return {
        "coverage": [
            {"brand": brand, "complete": False, "scope": OFFICIAL_PROVIDERS[brand]["scope"]}
            for brand in selected
        ],
        "source": {
            "generated_at": verification_date,
            "official_urls": [url for brand in selected for url in OFFICIAL_PROVIDERS[brand]["official_urls"]],
        },
        "paints": sorted(unique.values(), key=lambda paint: (paint["brand"], paint["range"], paint["name"], paint["id"])),
    }
