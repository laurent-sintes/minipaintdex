"""Deterministic brand mappings to the canonical Mini Paint Dex paint profile."""

from __future__ import annotations

from copy import deepcopy
from functools import lru_cache
from pathlib import Path
import re
from typing import Any

import yaml


DEFAULT_MAPPING_DIRECTORY = Path(__file__).resolve().parents[2] / "mappings"
BRAND_CODE_PATTERN = re.compile(r"^[a-z][a-z0-9]{2,4}$")

ROLE_VALUES = {
    "color_paint", "primer", "wash", "ink", "varnish", "medium", "auxiliary",
    "technical_effect", "pigment",
}
AUXILIARY_TONE_ROLES = {"varnish", "medium", "auxiliary"}
APPLICATION_METHOD_VALUES = {"brush", "airbrush", "spray", "marker"}
APPLICATION_SYSTEM_VALUES = {
    "conventional_layering", "one_coat_shading", "washing", "priming",
    "effect_application", "unknown",
}
COVERAGE_VALUES = {"opaque", "semi_opaque", "translucent", "transparent", "unknown"}
FINISH_VALUES = {"matte", "satin", "gloss", "unknown"}
EFFECT_VALUES = {"metallic", "fluorescent", "pearlescent"}
UNDERCOAT_VALUES = {"light", "dark", "any", "unknown"}
MEDIUM_VALUES = {"water_based_acrylic", "acrylic", "alcohol_based", "oil", "enamel", "unknown"}
PROFILE_SOURCE_FIELDS = {"functional_class", "functional_type", "finish", "medium", "opacity", "range"}
KNOWN_INPUT_FIELDS = {
    "schema_version", "id", "name", "name_observed", "brand", "brand_observed", "brand_canonical",
    "brand_aliases", "manufacturer", "range", "range_observed", "range_canonical", "profile",
    "reference", "confidence", "status", "data_status", "lifecycle_status", "warnings", "color",
    "color_hex", "couleur_hex", "color_family", "famille_couleur", "volume_ml", "tags",
    "recommended_uses", "usages_conseilles", "usage_instructions", "manufacturer_url",
    "manufacturer_page", "manufacturer_image", "local_image", "image_source_url", "image_credit",
    "result_image", "result_source_url", "result_credit", "result_license", "result_reference_url",
    "provenance", "source_photo", "source_hash", "verified_on", "verified_at", "notes", "dedupe_key",
    "deduplication_key", "quantity", "warnings", "source_observation", "mapping_report",
    "source_snapshots",
} | PROFILE_SOURCE_FIELDS


@lru_cache(maxsize=4)
def load_mappings(directory: str | Path = DEFAULT_MAPPING_DIRECTORY) -> dict[str, dict[str, Any]]:
    root = Path(directory)
    result: dict[str, dict[str, Any]] = {}
    brand_codes: set[str] = set()
    for path in sorted(root.glob("*.yaml")):
        with path.open("r", encoding="utf-8-sig") as handle:
            mapping = yaml.safe_load(handle)
        if not isinstance(mapping, dict) or mapping.get("schema_version") != 1:
            raise ValueError(f"Invalid paint mapping: {path}")
        brand = str(mapping.get("brand", "")).strip()
        if not brand:
            raise ValueError(f"Paint mapping has no brand: {path}")
        brand_code = str(mapping.get("brand_code", "")).strip()
        if not BRAND_CODE_PATTERN.fullmatch(brand_code):
            raise ValueError(f"Paint mapping has an invalid brand_code: {path}")
        if brand_code in brand_codes:
            raise ValueError(f"Paint mapping brand_code must be unique: {brand_code}")
        brand_codes.add(brand_code)
        validate_profile(mapping.get("default_profile"), f"{path}: default_profile")
        result[brand.casefold()] = mapping
    return result


def validate_profile(profile: Any, location: str = "profile") -> None:
    if not isinstance(profile, dict):
        raise ValueError(f"{location} must be an object")
    _list_values(profile, "roles", ROLE_VALUES, location, required=True)
    _list_values(profile, "application_methods", APPLICATION_METHOD_VALUES, location, required=True)
    _scalar(profile, "application_system", APPLICATION_SYSTEM_VALUES, location, required=True)
    _scalar(profile, "coverage", COVERAGE_VALUES, location, required=True)
    _scalar(profile, "finish", FINISH_VALUES, location, required=True)
    _list_values(profile, "effects", EFFECT_VALUES, location, required=True)
    _scalar(profile, "medium", MEDIUM_VALUES, location, required=True)
    undercoat = profile.get("undercoat")
    if not isinstance(undercoat, dict):
        raise ValueError(f"{location}.undercoat must be an object")
    _scalar(undercoat, "tone", UNDERCOAT_VALUES, f"{location}.undercoat", required=True)
    if not isinstance(undercoat.get("pre_highlighted_surface_recommended"), bool):
        raise ValueError(f"{location}.undercoat.pre_highlighted_surface_recommended must be boolean")


def canonical_color_family(profile: dict[str, Any], observed_family: Any) -> str:
    """Return the observed family, or the functional auxiliary tone when applicable."""
    family = str(observed_family or "").strip()
    roles = set(profile.get("roles", []))
    if not family and roles and roles.issubset(AUXILIARY_TONE_ROLES):
        return "auxiliary"
    return family


def canonical_profile(record: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    existing = record.get("profile")
    if isinstance(existing, dict):
        validate_profile(existing)
        mapping = _mapping_for(record.get("brand"))
        return deepcopy(existing), _report(mapping, [], _unmapped_input_fields(record))

    mapping = _mapping_for(record.get("brand_canonical") or record.get("brand"))
    profile = deepcopy(mapping["default_profile"])
    range_name = str(record.get("range_canonical") or record.get("range") or "").strip()
    range_profile = (mapping.get("ranges") or {}).get(range_name)
    if isinstance(range_profile, dict):
        profile = _merge(profile, range_profile)

    source_type = str(record.get("functional_class") or record.get("functional_type") or "").strip()
    _apply_source_type(profile, source_type)
    _apply_observed_values(profile, record)
    validate_profile(profile)
    mapped = [
        field for field in ("functional_class", "functional_type", "finish", "medium", "opacity", "range")
        if field in record
    ]
    return profile, _report(mapping, mapped, _unmapped_input_fields(record))


def source_observation(record: dict[str, Any]) -> dict[str, Any]:
    fields = []
    observed_keys = set(PROFILE_SOURCE_FIELDS - {"range"}) | set(_unmapped_input_fields(record))
    if isinstance(record.get("enrichment"), dict):
        observed_keys.add("enrichment")
    for key in sorted(observed_keys):
        value = record.get(key)
        if value not in (None, "", []):
            fields.append({"name": key, "value": deepcopy(value)})
    return {"adapter": "brand_mapping", "fields": fields} if fields else {}


def _unmapped_input_fields(record: dict[str, Any]) -> list[str]:
    return sorted(key for key in record if key not in KNOWN_INPUT_FIELDS and key != "enrichment")


def _mapping_for(brand: Any) -> dict[str, Any]:
    mappings = load_mappings()
    key = str(brand or "").strip().casefold()
    if key not in mappings:
        raise ValueError(f"No canonical paint mapping for brand: {brand}")
    return mappings[key]


def _apply_source_type(profile: dict[str, Any], source_type: str) -> None:
    if source_type in {"opaque_standard", "one_coat_contrast", "metallic", "airbrush", "fluorescent"}:
        profile["roles"] = ["color_paint"]
    elif source_type == "primer":
        profile.update({"roles": ["primer"], "application_system": "priming"})
    elif source_type == "wash_shade":
        profile.update({"roles": ["wash"], "application_system": "washing", "coverage": "transparent"})
    elif source_type == "ink":
        profile.update({"roles": ["ink"], "application_system": "washing", "coverage": "transparent"})
    elif source_type == "technical_effect":
        profile.update({"roles": ["technical_effect"], "application_system": "effect_application"})
    elif source_type == "auxiliary":
        profile.update({"roles": ["auxiliary"], "application_system": "effect_application"})
    elif source_type == "varnish":
        profile.update({"roles": ["varnish"], "application_system": "effect_application"})
    elif source_type == "medium":
        profile.update({"roles": ["medium"], "application_system": "effect_application"})
    elif source_type == "pigment":
        profile.update({"roles": ["pigment"], "application_system": "effect_application"})
    if source_type == "one_coat_contrast":
        profile.update({
            "application_system": "one_coat_shading", "coverage": "transparent",
            "undercoat": {"tone": "light", "pre_highlighted_surface_recommended": True},
        })
    if source_type == "airbrush":
        profile["application_methods"] = ["airbrush"]
    if source_type == "metallic" and "metallic" not in profile["effects"]:
        profile["effects"].append("metallic")
    if source_type == "fluorescent" and "fluorescent" not in profile["effects"]:
        profile["effects"].append("fluorescent")


def _apply_observed_values(profile: dict[str, Any], record: dict[str, Any]) -> None:
    coverage = str(record.get("opacity") or "").strip().casefold().replace("-", "_")
    if coverage in COVERAGE_VALUES:
        profile["coverage"] = coverage
    finish = str(record.get("finish") or "").strip().casefold()
    if "matt" in finish or "matte" in finish:
        profile["finish"] = "matte"
    elif "satin" in finish:
        profile["finish"] = "satin"
    elif "gloss" in finish or "brillant" in finish:
        profile["finish"] = "gloss"
    if "metal" in finish and "metallic" not in profile["effects"]:
        profile["effects"].append("metallic")
    name_range = f"{record.get('name', '')} {record.get('range', '')}".casefold()
    if any(word in name_range for word in ("fluorescent", "fluo")) and "fluorescent" not in profile["effects"]:
        profile["effects"].append("fluorescent")
    medium = str(record.get("medium") or "").strip().casefold()
    if "water" in medium or "eau" in medium:
        profile["medium"] = "water_based_acrylic"
    elif "isopropyl" in medium or "alcohol" in medium:
        profile["medium"] = "alcohol_based"
    elif "acryl" in medium:
        profile["medium"] = "acrylic"
    elif "oil" in medium or "huile" in medium:
        profile["medium"] = "oil"
    elif "enamel" in medium:
        profile["medium"] = "enamel"


def _merge(base: dict[str, Any], override: dict[str, Any]) -> dict[str, Any]:
    result = deepcopy(base)
    for key, value in override.items():
        result[key] = deepcopy(value)
    return result


def _report(mapping: dict[str, Any], mapped: list[str], unmapped: list[str]) -> dict[str, Any]:
    identity = mapping["mapping"]
    return {
        "mapping": identity["id"],
        "mapping_version": identity["version"],
        "mapped_fields": sorted(mapped),
        "unmapped_fields": sorted(unmapped),
        "ignored_fields": [],
    }


def _scalar(profile: dict[str, Any], key: str, allowed: set[str], location: str, *, required: bool) -> None:
    value = profile.get(key)
    if value is None and not required:
        return
    if value not in allowed:
        raise ValueError(f"{location}.{key} has unsupported value: {value}")


def _list_values(
    profile: dict[str, Any], key: str, allowed: set[str], location: str, *, required: bool,
) -> None:
    values = profile.get(key)
    if values is None and not required:
        return
    if not isinstance(values, list) or (required and not values and key != "effects"):
        raise ValueError(f"{location}.{key} must be a list")
    unknown = [value for value in values if value not in allowed]
    if unknown:
        raise ValueError(f"{location}.{key} has unsupported values: {unknown}")
