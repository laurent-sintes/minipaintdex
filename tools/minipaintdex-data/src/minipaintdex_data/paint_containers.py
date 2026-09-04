"""Collect packaging evidence alongside paint records without inventing geometry."""
from copy import deepcopy
import re


def associate_containers(paints, previous=(), evidence=None):
    """Return paints and format upserts. Evidence is keyed by exact commercial paint ID.

    An unidentified format is specific to the commercial reference: it must never make
    unrelated paints compatible merely because their volumes or brands match.
    """
    known = {paint["id"]: paint for paint in previous}
    formats = {}
    result = []
    for original in paints:
        paint = deepcopy(original)
        inline = paint.pop("container_format", None)
        observed = (evidence or {}).get(paint["id"]) or inline
        if observed:
            validate_format(observed)
            identifier = observed["id"]
            if identifier in formats and formats[identifier] != observed:
                raise ValueError(f"Conflicting container geometry: {identifier}")
            formats[identifier] = deepcopy(observed)
            paint["container_format_id"] = identifier
        elif paint.get("container_format_id") or known.get(paint["id"], {}).get("container_format_id"):
            paint["container_format_id"] = paint.get("container_format_id") or known[paint["id"]]["container_format_id"]
        else:
            identifier = "unidentified-" + paint["id"]
            paint["container_format_id"] = identifier
            page = paint.get("manufacturer_page")
            formats[identifier] = {
                "schema_version": 1, "id": identifier, "name": "Unidentified packaging — " + paint["id"],
                "brand": paint["brand"], "family": "unidentified", "volume_ml": paint.get("volume_ml") or None,
                "dimensions": {"width_mm": None, "depth_mm": None, "height_mm": None},
                "evidence_status": "unknown", "sources": [page] if page and page.startswith("https://") else [],
                "notes": "Commercial reference recorded; packaging geometry is not documented. Not eligible for automatic placement.",
            }
        result.append(paint)
    return result, [formats[key] for key in sorted(formats)]


def validate_format(value):
    if value.get("schema_version") != 1 or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", value.get("id", "")):
        raise ValueError("Invalid container identity or schema")
    if not value.get("sources") or any(not url.startswith("https://") for url in value["sources"]):
        raise ValueError("Observed packaging needs HTTPS source evidence")
    if value.get("evidence_status") not in ("unknown", "estimated", "confirmed"):
        raise ValueError("Invalid container evidence status")
    for field in ("width_mm", "depth_mm", "height_mm"):
        dimension = value.get("dimensions", {}).get(field)
        if dimension is not None and (not isinstance(dimension, (int, float)) or isinstance(dimension, bool) or not 0 < dimension < float("inf")):
            raise ValueError("Container dimensions must be positive millimetres or null")
