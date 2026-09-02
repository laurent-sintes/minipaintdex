"""Audit public media references without mutating the repository."""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any

import yaml

from .paint_images import MAX_DOMINANT_COLOR_RATIO, assess_raster_artwork


PUBLIC_PATH = re.compile(
    r"(?<![A-Za-z0-9_:/.-])(?P<path>/[A-Za-z0-9_./-]+\.(?:png|jpe?g|webp|svg))",
    re.IGNORECASE,
)


def _referenced_paths(value: Any) -> set[str]:
    references: set[str] = set()
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "source_snapshots":
                continue
            references.update(_referenced_paths(child))
    elif isinstance(value, list):
        for child in value:
            references.update(_referenced_paths(child))
    elif isinstance(value, str):
        references.update(match.group("path").replace("\\", "/") for match in PUBLIC_PATH.finditer(value))
    return references


def audit_assets(root: Path, *, min_width: int = 300, min_height: int = 300) -> dict[str, Any]:
    root = root.resolve()
    frontend_public = root / "frontend" / "public"
    public = frontend_public if frontend_public.is_dir() else root / "public"
    media = root / "media"
    data = root / "data"
    references: set[str] = set()
    documents: dict[Path, Any] = {}
    for path in sorted(data.rglob("*")):
        if path.is_file() and path.suffix.lower() in {".yaml", ".yml", ".json", ".jsonl"}:
            text = path.read_text(encoding="utf-8-sig")
            value = (
                [yaml.safe_load(line) for line in text.splitlines() if line.strip()]
                if path.suffix.lower() == ".jsonl" else yaml.safe_load(text)
            )
            documents[path] = value
            references.update(_referenced_paths(value))
    files = {
        "/" + path.relative_to(public).as_posix()
        for path in public.rglob("*")
        if path.is_file()
    }
    files.update(
        "/media/" + path.relative_to(media).as_posix()
        for path in media.rglob("*")
        if path.is_file()
    )

    def local_path(public_path: str) -> Path:
        return (
            media / public_path.removeprefix("/media/")
            if public_path.startswith("/media/")
            else public / public_path.lstrip("/")
        )
    too_small: list[dict[str, Any]] = []
    rejected_artwork: list[dict[str, Any]] = []
    unreadable: list[str] = []
    dimensions: dict[str, tuple[int, int]] = {}
    try:
        from PIL import Image, UnidentifiedImageError
    except ImportError:  # pragma: no cover - optional image tooling
        Image = None
        UnidentifiedImageError = OSError
    if Image is not None:
        for public_path in sorted(files & references):
            path = local_path(public_path)
            if path.suffix.casefold() == ".svg":
                continue
            try:
                with Image.open(path) as image:
                    width, height = image.size
                    assessment = assess_raster_artwork(
                        image, max_dominant_color_ratio=MAX_DOMINANT_COLOR_RATIO,
                    )
                dimensions[public_path] = (width, height)
                if width < min_width or height < min_height:
                    too_small.append({"path": public_path, "width": width, "height": height})
                elif not assessment["accepted_as_photo"]:
                    rejected_artwork.append({
                        "path": public_path,
                        **assessment,
                    })
            except (OSError, UnidentifiedImageError):
                unreadable.append(public_path)
    paint_image_records: list[dict[str, Any]] = []
    rejected_by_path = {item["path"]: item for item in rejected_artwork}
    paints_root = data / "market" / "paints"
    for catalog_path in sorted(paints_root.glob("*.yaml")) if paints_root.is_dir() else []:
        value = documents.get(catalog_path)
        for paint in value.get("paints", []) if isinstance(value, dict) else []:
            if not isinstance(paint, dict):
                continue
            result_image = paint.get("result_image") if isinstance(paint.get("result_image"), dict) else {}
            manufacturer_image = paint.get("manufacturer_image") if isinstance(paint.get("manufacturer_image"), dict) else {}
            display_path = str(result_image.get("path") or manufacturer_image.get("path") or "")
            source_url = str(result_image.get("source_url") or manufacturer_image.get("source_url") or "")
            width, height = dimensions.get(display_path, (None, None))
            if display_path and display_path in files:
                if width is not None and (width < min_width or height < min_height):
                    status = "too_small"
                elif display_path in rejected_by_path:
                    status = rejected_by_path[display_path]["classification"]
                else:
                    status = "local"
            elif display_path:
                status = "broken_local_reference"
            elif source_url:
                status = "remote_only"
            else:
                status = "missing"
            paint_image_records.append({
                "id": str(paint.get("id", "")), "brand": str(paint.get("brand", "")),
                "status": status, "path": display_path, "source_url": source_url,
                "width": width, "height": height,
            })
    paint_image_counts: dict[str, dict[str, int]] = {}
    for item in paint_image_records:
        counts = paint_image_counts.setdefault(item["brand"], {})
        counts[item["status"]] = counts.get(item["status"], 0) + 1
    allowed_unreferenced = {"/favicon.svg"}
    return {
        "public_files": len(files),
        "asset_roots": [public.as_posix(), media.as_posix()],
        "referenced_files": len(files & references),
        "missing": sorted(references - files),
        "orphaned": sorted(files - references - allowed_unreferenced),
        "minimum_dimensions": {"width": min_width, "height": min_height},
        "too_small": too_small,
        "rejected_artwork": rejected_artwork,
        "unreadable": unreadable,
        "paint_images": {
            "records": len(paint_image_records),
            "by_brand": {brand: dict(sorted(counts.items())) for brand, counts in sorted(paint_image_counts.items())},
            "issues": [item for item in paint_image_records if item["status"] != "local"],
        },
    }
