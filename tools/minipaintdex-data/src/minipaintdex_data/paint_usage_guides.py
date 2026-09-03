"""Deterministic extraction of reviewed source text into shared Market documents.

Never writes active storage. Translation wording is supplied separately by an operator;
this module only matches exact text/templates, validates and prepares application change sets.
"""
from __future__ import annotations

from copy import deepcopy
import hashlib
import html
import json
from pathlib import Path
import re
from typing import Any



def plain_text(value: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]*>", " ", (value or "").replace("\ufeff", "")))).strip()


def translate(text: str, translations: dict[str, Any]) -> str | None:
    if not text:
        return ""
    if text in translations.get("already_french", []):
        return text
    if text in translations.get("exact", {}):
        return translations["exact"][text]
    for rule in translations.get("templates", []):
        tokens = re.split(r"(\{[a-z]+\})", rule["source"])
        pattern = "".join("(?P<" + token[1:-1] + ">.+?)" if token.startswith("{") else re.escape(token) for token in tokens)
        match = re.fullmatch(pattern, text)
        if match:
            return rule["french"].format(**match.groupdict())
    for rule in translations.get("suffixes", []):
        if text.endswith(rule["source"]):
            beginning = translate(text[:-len(rule["source"])].strip(), translations)
            if beginning is not None:
                return beginning + " " + rule["french"]
    return None


def extract_guides(catalog: Path, translations: dict[str, Any]) -> dict[str, Any]:
    from .datasets import load_yaml
    catalogs = [load_yaml(path) for path in sorted(catalog.glob("*.yaml"))]
    paints = [paint for document in catalogs for paint in document.get("paints", [])]
    known = {g["id"]: g for document in catalogs for g in document.get("paint_usage_guides", [])}
    groups: dict[str, dict[str, Any]] = {}
    operations = []
    missing = set()
    for paint in sorted(paints, key=lambda p: p["id"]):
        source = deepcopy(paint.get("usage_instructions") or {})
        content = {"summary": plain_text(source.get("summary", "")),
                   "steps": [plain_text(x) for x in source.get("steps", [])],
                   "tips": [plain_text(x) for x in source.get("tips", [])]}
        if not any([content["summary"], content["steps"], content["tips"]]):
            continue
        # Exact content AND source status determine sharing; no name/range inference.
        signature = json.dumps([paint["brand"], content, source.get("instruction_status"), source.get("review_required")], sort_keys=True, ensure_ascii=False)
        identifier = paint["id"].split("-")[0] + "-usage-" + hashlib.sha256(signature.encode()).hexdigest()[:16]
        if identifier in groups:
            guide = groups[identifier]
        elif identifier in known:
            guide = deepcopy(known[identifier])
        else:
            translated = {"summary": translate(content["summary"], translations),
                          "steps": [translate(x, translations) for x in content["steps"]],
                          "tips": [translate(x, translations) for x in content["tips"]]}
            for original, result in zip([content["summary"], *content["steps"], *content["tips"]],
                                        [translated["summary"], *translated["steps"], *translated["tips"]]):
                if result is None:
                    missing.add(original)
            all_text = [content["summary"], *content["steps"], *content["tips"]]
            french_count = sum(x in translations.get("already_french", []) for x in all_text if x)
            original_language = "fr" if french_count == len([x for x in all_text if x]) else "mul" if french_count else "en"
            generic = source.get("instruction_status") in {"generic_template", "manufacturer_summary_with_generic_steps"}
            guide = {
                "schema_version": 1, "id": identifier, "brand": paint["brand"],
                "title": paint["range"], "revision": 1, "ranges": [], "original_language": original_language,
                "original": content, "knowledge_status": "generic-template" if generic else "unverified",
                "review_required": True, "source_urls": [], "translations": [] if original_language == "fr" else [{
                    "language": "fr", "source_revision": 1, "method": "machine", "review_required": True, "content": translated}],
                "source_snapshots": [{"provider": "paint-usage-extraction", "payload": source}],
            }
        if paint["range"] not in guide["ranges"]:
            guide["ranges"].append(paint["range"])
            guide["ranges"].sort()
        urls = [paint.get("manufacturer_page"), *(s.get("url") for s in paint.get("sources", []) if isinstance(s, dict))]
        guide["source_urls"] = sorted(set(guide["source_urls"]) | {url for url in urls if isinstance(url, str) and url.startswith(("https://", "http://"))})
        groups[identifier] = guide
        replacement = deepcopy(paint)
        replacement.pop("usage_instructions", None)
        replacement["usage_guide_ids"] = list(dict.fromkeys([*paint.get("usage_guide_ids", []), identifier]))
        operations.append({"action": "upsert", "record": replacement, "workshop_quantity_delta": 0})
    if missing:
        raise ValueError("Missing operator translations: " + json.dumps(sorted(missing), ensure_ascii=False))
    # Existing documents can acquire explicit scope/source links without losing previous translations.
    for identifier, guide in groups.items():
        previous = known.get(identifier)
        if previous and any(guide[key] != previous[key] for key in ("ranges", "source_urls")):
            guide["revision"] = previous["revision"] + 1
            # Text is unchanged; rebinding an identical translation is deterministic and explicit.
            for translation in guide["translations"]:
                if translation["source_revision"] == previous["revision"]:
                    translation["source_revision"] = guide["revision"]
    return {"schema_version": 1, "kind": "market_paints", "source": {"kind": "paint-usage-extraction"},
            "operations": operations, "paint_usage_guides": sorted(groups.values(), key=lambda g: g["id"])}


def validate_guides(guides: Any) -> list[str]:
    if not isinstance(guides, list):
        return ["paint_usage_guides must be a list"]
    errors = []
    seen = set()
    for guide in guides:
        if not isinstance(guide, dict):
            errors.append("A usage guide must be an object")
            continue
        identifier = guide.get("id", "")
        if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", identifier) or identifier in seen:
            errors.append("Invalid or duplicate usage guide ID")
        seen.add(str(identifier))
        if guide.get("schema_version") != 1 or not isinstance(guide.get("revision"), int) or guide.get("revision", 0) < 1:
            errors.append("Invalid usage guide schema/revision")
        for field in ("brand", "title", "ranges", "original"):
            if not guide.get(field):
                errors.append(f"Usage guide {identifier}: {field} required")
        if guide.get("knowledge_status") not in {"manufacturer", "sourced-summary", "generic-template", "unverified"}:
            errors.append("Invalid usage guide knowledge status")
        if guide.get("knowledge_status") in {"generic-template", "unverified"} and guide.get("review_required") is not True:
            errors.append("Unverified guide requires review")
        if guide.get("original_language") not in {"en", "fr", "mul"}:
            errors.append("Invalid original language")
    return errors
