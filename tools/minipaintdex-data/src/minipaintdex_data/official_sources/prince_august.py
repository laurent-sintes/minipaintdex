"""Prince August official Classic-range adapter."""

from __future__ import annotations

import html
import re
from pathlib import Path
from typing import Any

from .common import base_record, classify, existing_indexes, fetch_text, merge_existing, plain, slug, source_snapshot


CLASSIC_URL = "https://www.prince-august.net/peintures/classic/"
OFFICIAL_URLS = (CLASSIC_URL,)
PRODUCT_CARD = re.compile(
    r'<div class="single-element">.*?<img[^>]+src="([^"]+)".*?'
    r'<h3><a href="([^"]+)">(.*?)</a></h3>\s*<p>(.*?)</p>',
    re.DOTALL | re.IGNORECASE,
)


def parse_cards(page: str) -> list[dict[str, str]]:
    records: list[dict[str, str]] = []
    for image, url, title, description in PRODUCT_CARD.findall(page):
        title = plain(title)
        parts = [part.strip() for part in re.split(r"\s+[–-]\s+", title, maxsplit=2)]
        if not parts or not re.fullmatch(r"P\d+", parts[0], re.IGNORECASE):
            continue
        records.append({
            "reference": parts[0].upper(),
            "name": parts[-1],
            "url": html.unescape(url),
            "image": html.unescape(image),
            "description": plain(description),
        })
    return records

def collect(catalog: dict[str, Any], _: Path) -> list[dict[str, Any]]:
    by_reference, _ = existing_indexes(catalog)
    cards: dict[str, dict[str, str]] = {}
    for page_number in range(1, 16):
        separator = "?" if page_number > 1 else ""
        suffix = f"sf_paged={page_number}" if page_number > 1 else ""
        for card in parse_cards(fetch_text(CLASSIC_URL + separator + suffix)):
            cards[card["reference"]] = card
    records: list[dict[str, Any]] = []
    for card in cards.values():
        if card["reference"] == "P000":
            continue
        functional_type = classify(card["name"], "opaque_standard")
        record = base_record(
            identifier=f"prince-august-classic-{slug(card['reference'])}", brand="Prince August",
            manufacturer="Prince August", range_name="CLASSIC", functional_type=functional_type,
            reference_code=card["reference"], name=card["name"], page=card["url"], image=card["image"],
            volume_ml=17, finish="matte" if functional_type == "opaque_standard" else "",
            opacity="opaque" if functional_type == "opaque_standard" else "", summary=card["description"],
        )
        record["source_snapshots"] = source_snapshot("prince_august_product_card", card["url"], card)
        records.append(merge_existing(record, by_reference))
    return records
