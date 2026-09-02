"""Canonical image-source quality policy shared by every paint brand adapter."""

from __future__ import annotations

from copy import deepcopy
from datetime import date, timedelta
from typing import Any


IMAGE_QUALITY_RANKS = {
    "official_photo": 1,
    "retailer_photo": 2,
    "owned_photo": 3,
    "generic_visual": 4,
    "color_swatch": 5,
    "none": 6,
}

IMAGE_QUALITY_LIMITATION_CODES = {
    "official-photo-not-published",
    "official-source-unavailable",
    "official-candidate-rejected",
    "official-reference-unmatched",
    "better-source-not-found",
    "manually-provided",
    "historical-reason-not-recorded",
}


def quality_limitation(code: str, detail: str, observed_at: str) -> dict[str, str]:
    """Build one controlled, human-readable explanation for a non-official image."""
    if code not in IMAGE_QUALITY_LIMITATION_CODES:
        raise ValueError(f"Unsupported image quality limitation: {code}")
    if not str(detail).strip():
        raise ValueError("Image quality limitation detail is required.")
    date.fromisoformat(str(observed_at))
    return {"code": code, "detail": str(detail).strip(), "observed_at": str(observed_at)}


def quality_rank(value: object) -> int:
    """Return the canonical rank; an absent image has the lowest quality."""
    return IMAGE_QUALITY_RANKS.get(str(value or "").strip(), 6)


def infer_image_quality(record: dict[str, Any]) -> str:
    """Infer a conservative quality for an unqualified source observation."""
    image = record.get("manufacturer_image") if isinstance(record.get("manufacturer_image"), dict) else {}
    declared = str(image.get("image_quality", "")).strip()
    if declared in IMAGE_QUALITY_RANKS:
        return declared
    if image.get("path") or image.get("source_url"):
        credit = str(image.get("credit", "")).casefold()
        return "official_photo" if "official" in credit else "generic_visual"
    warnings = " ".join(str(value) for value in record.get("warnings", [])).casefold()
    snapshots = record.get("source_snapshots") if isinstance(record.get("source_snapshots"), list) else []
    has_source_artwork = any(
        isinstance(snapshot, dict)
        and isinstance(snapshot.get("payload"), dict)
        and bool(snapshot["payload"].get("images"))
        for snapshot in snapshots
    )
    if "colour swatch" in warnings or "color swatch" in warnings or has_source_artwork:
        return "color_swatch"
    color = record.get("color") if isinstance(record.get("color"), dict) else {}
    return "color_swatch" if str(color.get("hex", "")).strip() else "none"


def normalize_image_quality(record: dict[str, Any], *, verified_at: str | None = None) -> dict[str, Any]:
    """Add canonical quality metadata to one paint record."""
    normalized = deepcopy(record)
    image = normalized.setdefault("manufacturer_image", {})
    image["image_quality"] = infer_image_quality(normalized)
    if image["image_quality"] != "none" and not image.get("quality_verified_at"):
        image["quality_verified_at"] = verified_at or str(normalized.get("verified_at", "")) or date.today().isoformat()
    if image["image_quality"] == "official_photo":
        image.pop("quality_limitation", None)
    elif not isinstance(image.get("quality_limitation"), dict):
        observed_at = verified_at or str(normalized.get("verified_at", "")) or date.today().isoformat()
        warnings = " ".join(str(value) for value in normalized.get("warnings", [])).casefold()
        if "colour swatch" in warnings or "color swatch" in warnings:
            limitation = quality_limitation(
                "official-photo-not-published",
                "The official catalog publishes a color swatch rather than a usable product photo.",
                observed_at,
            )
        elif image["image_quality"] == "owned_photo":
            limitation = quality_limitation(
                "manually-provided", "The retained product photo was supplied manually.", observed_at,
            )
        else:
            limitation = quality_limitation(
                "historical-reason-not-recorded",
                "This non-optimal quality predates structured limitation tracking; the precise reason was not recorded.",
                observed_at,
            )
        image["quality_limitation"] = limitation
    return normalized


def prefer_image(current: dict[str, Any], candidate: dict[str, Any]) -> dict[str, Any]:
    """Choose the best sourced image and never downgrade a catalog during refresh."""
    current_quality = str(current.get("image_quality", "none"))
    candidate_quality = str(candidate.get("image_quality", "none"))
    if quality_rank(candidate_quality) < quality_rank(current_quality):
        return deepcopy(candidate)
    if quality_rank(candidate_quality) > quality_rank(current_quality):
        return deepcopy(current)
    # At equal quality, keep the newest verified observation, otherwise retain the stable current value.
    if str(candidate.get("quality_verified_at", "")) > str(current.get("quality_verified_at", "")):
        return deepcopy(candidate)
    return deepcopy(current)


def plan_image_rechallenge(
    catalog: dict[str, Any], *, brands: list[str] | None = None,
    as_of: str | None = None, official_max_age_days: int = 365,
) -> dict[str, Any]:
    """Estimate which paint images should be challenged; this is read-only and deterministic."""
    if official_max_age_days <= 0:
        raise ValueError("official_max_age_days must be positive.")
    today = date.fromisoformat(as_of) if as_of else date.today()
    selected = {brand.casefold() for brand in (brands or ["all"])}
    paints = [paint for paint in catalog.get("paints", []) if isinstance(paint, dict)]
    known = {str(paint.get("brand", "")).casefold() for paint in paints}
    unknown = sorted(selected - {"all"} - known)
    if unknown:
        raise ValueError(f"Unknown paint brand(s): {', '.join(unknown)}")
    cutoff = today - timedelta(days=official_max_age_days)
    items: list[dict[str, Any]] = []
    quality_counts = {quality: 0 for quality in IMAGE_QUALITY_RANKS}
    for paint in paints:
        if "all" not in selected and str(paint.get("brand", "")).casefold() not in selected:
            continue
        quality = infer_image_quality(paint)
        quality_counts[quality] += 1
        image = paint.get("manufacturer_image") if isinstance(paint.get("manufacturer_image"), dict) else {}
        raw_verified = str(image.get("quality_verified_at", "")).strip()
        reason = ""
        if quality != "official_photo":
            reason = "better_quality_available"
        elif not raw_verified:
            reason = "official_quality_verification_missing"
        else:
            try:
                if date.fromisoformat(raw_verified) <= cutoff:
                    reason = "official_photo_older_than_policy"
            except ValueError:
                reason = "invalid_quality_verification_date"
        if reason:
            items.append({
                "id": str(paint.get("id", "")),
                "brand": str(paint.get("brand", "")),
                "reference": str(paint.get("reference", "")),
                "name": str(paint.get("name", "")),
                "current_quality": quality,
                "current_quality_rank": quality_rank(quality),
                "quality_verified_at": raw_verified,
                "reason": reason,
                "best_target_quality": "official_photo",
                "best_target_quality_rank": 1,
            })
    items.sort(key=lambda item: (item["current_quality_rank"], item["brand"], item["id"]))
    return {
        "schema_version": 1,
        "kind": "paint_image_rechallenge_plan",
        "as_of": today.isoformat(),
        "official_max_age_days": official_max_age_days,
        "inspected_count": sum(quality_counts.values()),
        "candidate_count": len(items),
        "quality_counts": quality_counts,
        "items": items,
    }
