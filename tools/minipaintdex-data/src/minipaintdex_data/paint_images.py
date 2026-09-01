"""Download and validate official paint packshots into the local media cache."""

from __future__ import annotations

import hashlib
import io
import mimetypes
import tempfile
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from datetime import date
from pathlib import Path
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from .changesets import validate_changeset
from .official_sources.common import slug


FetchImage = Callable[[str, int], tuple[bytes, str, str]]
ALLOWED_IMAGE_HOSTS = {
    "Prince August": {"prince-august.net", "www.prince-august.net"},
    "The Army Painter": {"cdn.shopify.com", "thearmypainter.com", "www.thearmypainter.com"},
    "Vallejo": {"acrylicosvallejo.com", "www.acrylicosvallejo.com"},
    "Warhammer Colour": {"warhammer.com", "www.warhammer.com"},
}


def _allowed_url(brand: str, url: str) -> bool:
    parsed = urlparse(url)
    return parsed.scheme == "https" and (parsed.hostname or "").casefold() in ALLOWED_IMAGE_HOSTS.get(brand, set())


def _download(url: str, max_bytes: int) -> tuple[bytes, str, str]:
    request = Request(url, headers={"User-Agent": "MiniPaintDex/0.2 official-image-cache"})
    retryable_statuses = {408, 429, 500, 502, 503, 504}
    for attempt in range(4):
        try:
            with urlopen(request, timeout=60) as response:  # noqa: S310 - host is validated before and after redirects
                final_url = response.geturl()
                content_type = response.headers.get_content_type()
                declared_length = int(response.headers.get("Content-Length") or 0)
                if declared_length > max_bytes:
                    raise ValueError(f"image exceeds {max_bytes} bytes")
                content = response.read(max_bytes + 1)
            if len(content) > max_bytes:
                raise ValueError(f"image exceeds {max_bytes} bytes")
            return content, content_type, final_url
        except HTTPError as error:
            if error.code not in retryable_statuses or attempt == 3:
                raise
        except URLError:
            if attempt == 3:
                raise
        time.sleep(0.75 * (2 ** attempt))
    raise OSError("official image download exhausted its retry policy")  # pragma: no cover


def _write_webp(content: bytes, target: Path, *, min_width: int, min_height: int, max_edge: int) -> dict[str, Any]:
    try:
        from PIL import Image, ImageOps, UnidentifiedImageError
    except ImportError as error:  # pragma: no cover - operator dependency
        raise ValueError("Pillow is required to cache paint images.") from error
    try:
        with Image.open(io.BytesIO(content)) as source:
            source.load()
            image = ImageOps.exif_transpose(source)
            original_width, original_height = image.size
            if original_width < min_width or original_height < min_height:
                raise ValueError(f"image is too small: {original_width}x{original_height}")
            image.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)
            converted = image.convert("RGBA" if "A" in image.getbands() else "RGB")
            target.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(dir=target.parent, suffix=".webp.tmp", delete=False) as handle:
                temporary = Path(handle.name)
            try:
                converted.save(temporary, format="WEBP", quality=82, method=6)
                temporary.replace(target)
            finally:
                if temporary.exists():
                    temporary.unlink()
    except (OSError, UnidentifiedImageError) as error:
        raise ValueError("source is not a readable raster image") from error
    return {
        "width": converted.width,
        "height": converted.height,
        "original_width": original_width,
        "original_height": original_height,
        "bytes": target.stat().st_size,
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
    }


def _write_svg(content: bytes, target: Path, *, max_edge: int) -> dict[str, Any]:
    lowered = content.lower()
    if b"<!doctype" in lowered or b"<!entity" in lowered:
        raise ValueError("SVG declarations are not allowed")
    try:
        root = ET.fromstring(content)
    except ET.ParseError as error:
        raise ValueError("source is not a readable SVG image") from error
    if root.tag.rsplit("}", 1)[-1].casefold() != "svg":
        raise ValueError("source is not an SVG image")
    forbidden = {"script", "foreignobject", "iframe", "object", "embed"}
    for element in root.iter():
        if element.tag.rsplit("}", 1)[-1].casefold() in forbidden:
            raise ValueError("SVG contains active content")
        for name, value in element.attrib.items():
            local_name = name.rsplit("}", 1)[-1].casefold()
            normalized = value.strip().casefold()
            if local_name.startswith("on") or "url(http" in normalized:
                raise ValueError("SVG contains an external or active reference")
            if local_name == "href" and normalized and not normalized.startswith("#"):
                raise ValueError("SVG contains an external reference")
    view_box = str(root.attrib.get("viewBox", "")).replace(",", " ").split()
    if len(view_box) != 4:
        raise ValueError("SVG requires a four-value viewBox")
    try:
        original_width, original_height = float(view_box[2]), float(view_box[3])
    except ValueError as error:
        raise ValueError("SVG viewBox dimensions are invalid") from error
    if original_width <= 0 or original_height <= 0:
        raise ValueError("SVG viewBox dimensions must be positive")
    scale = max_edge / max(original_width, original_height)
    width = max(1, round(original_width * scale))
    height = max(1, round(original_height * scale))
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, suffix=".svg.tmp", delete=False) as handle:
        temporary = Path(handle.name)
        handle.write(content)
    try:
        temporary.replace(target)
    finally:
        if temporary.exists():
            temporary.unlink()
    return {
        "width": width,
        "height": height,
        "original_width": original_width,
        "original_height": original_height,
        "bytes": target.stat().st_size,
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "format": "svg",
    }


def _cache_one(
    paint: dict[str, Any], media_root: Path, *, min_width: int, min_height: int,
    max_edge: int, max_bytes: int, overwrite: bool, fetch_image: FetchImage,
) -> tuple[dict[str, Any] | None, dict[str, Any]]:
    identifier = str(paint.get("id", ""))
    brand = str(paint.get("brand", ""))
    image = paint.get("manufacturer_image") if isinstance(paint.get("manufacturer_image"), dict) else {}
    source_url = str(image.get("source_url", "")).strip()
    local_path = str(image.get("path", "")).strip()
    if local_path and not overwrite and (not source_url or not _allowed_url(brand, source_url)):
        return None, {"id": identifier, "brand": brand, "status": "skipped_local", "path": local_path}
    if not source_url:
        return None, {"id": identifier, "brand": brand, "status": "missing_source"}
    if not _allowed_url(brand, source_url):
        return None, {"id": identifier, "brand": brand, "status": "rejected_host", "source_url": source_url}
    is_svg = urlparse(source_url).path.casefold().endswith(".svg")
    extension = ".svg" if is_svg else ".webp"
    relative = Path("market") / "paints" / slug(brand) / f"{identifier}{extension}"
    target = (media_root / relative).resolve()
    if not target.is_relative_to(media_root.resolve()):
        raise ValueError(f"Image cache target escapes media root: {identifier}")
    public_path = "/media/" + relative.as_posix()
    if local_path == public_path and target.exists() and not overwrite:
        return None, {"id": identifier, "brand": brand, "status": "skipped_local", "path": public_path}
    try:
        if target.exists() and not overwrite:
            metadata = (_write_svg(target.read_bytes(), target, max_edge=max_edge) if is_svg else
                        _write_webp(target.read_bytes(), target, min_width=min_width, min_height=min_height, max_edge=max_edge))
            status = "reused"
            final_url = source_url
        else:
            content, content_type, final_url = fetch_image(source_url, max_bytes)
            if not _allowed_url(brand, final_url):
                raise ValueError("redirected to a non-official host")
            if not content_type.startswith("image/") and not mimetypes.guess_type(final_url)[0]:
                raise ValueError(f"unexpected content type: {content_type}")
            metadata = (_write_svg(content, target, max_edge=max_edge) if is_svg else
                        _write_webp(content, target, min_width=min_width, min_height=min_height, max_edge=max_edge))
            status = "cached"
        updated = deepcopy(paint)
        updated.setdefault("manufacturer_image", {})["path"] = public_path
        return updated, {
            "id": identifier, "brand": brand, "status": status, "path": public_path,
            "source_url": source_url, "final_url": final_url, **metadata,
        }
    except (OSError, ValueError) as error:
        return None, {
            "id": identifier, "brand": brand, "status": "failed", "source_url": source_url,
            "error": str(error),
        }


def build_image_cache_changeset(
    catalog: dict[str, Any], media_root: Path, *, brands: list[str] | None = None,
    min_width: int = 300, min_height: int = 300, max_edge: int = 800,
    max_bytes: int = 10 * 1024 * 1024, overwrite: bool = False, limit: int = 0,
    workers: int = 4, verified_at: str | None = None, fetch_image: FetchImage = _download,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if min_width <= 0 or min_height <= 0 or max_edge < max(min_width, min_height):
        raise ValueError("Image dimensions must be positive and max_edge must cover the minimum dimensions.")
    if max_bytes <= 0 or workers <= 0 or workers > 16 or limit < 0:
        raise ValueError("Invalid image cache limits.")
    selected = {brand.casefold() for brand in (brands or ["all"])}
    paints = [
        paint for paint in catalog.get("paints", []) if isinstance(paint, dict)
        and ("all" in selected or str(paint.get("brand", "")).casefold() in selected)
    ]
    known_brands = {str(paint.get("brand", "")).casefold() for paint in catalog.get("paints", []) if isinstance(paint, dict)}
    unknown = sorted(selected - {"all"} - known_brands)
    if unknown:
        raise ValueError(f"Unknown paint brand(s): {', '.join(unknown)}")
    paints.sort(key=lambda paint: (str(paint.get("brand", "")), str(paint.get("id", ""))))
    if limit:
        paints = paints[:limit]
    results: list[tuple[dict[str, Any] | None, dict[str, Any]]] = []
    with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="paint-image-cache") as executor:
        futures = {
            executor.submit(
                _cache_one, paint, media_root, min_width=min_width, min_height=min_height,
                max_edge=max_edge, max_bytes=max_bytes, overwrite=overwrite, fetch_image=fetch_image,
            ): paint
            for paint in paints
        }
        for future in as_completed(futures):
            results.append(future.result())
    results.sort(key=lambda item: (item[1]["brand"], item[1]["id"]))
    operations = [
        {"action": "upsert", "record": record, "workshop_quantity_delta": 0, "confirmed_removal": False}
        for record, _ in results if record is not None
    ]
    verification_date = verified_at or date.today().isoformat()
    counts: dict[str, int] = {}
    for _, item in results:
        counts[item["status"]] = counts.get(item["status"], 0) + 1
    report = {
        "schema_version": 1,
        "kind": "paint_image_cache_audit",
        "generated_at": verification_date,
        "media_root": media_root.as_posix(),
        "minimum_dimensions": {"width": min_width, "height": min_height},
        "max_edge": max_edge,
        "counts": dict(sorted(counts.items())),
        "items": [item for _, item in results],
    }
    changeset = {
        "schema_version": 1,
        "kind": "market_paints",
        "source": {"kind": "official_paint_image_cache", "verified_at": verification_date},
        "asset_cache": {key: value for key, value in report.items() if key != "items"},
        "operations": operations,
    }
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid image cache change set: " + "; ".join(errors))
    return changeset, report


def build_image_source_changeset(
    catalog: dict[str, Any], manifest: dict[str, Any], *, verified_at: str | None = None,
    allow_unmatched: bool = False,
) -> dict[str, Any]:
    brand = str(manifest.get("brand", "")).strip()
    items = manifest.get("items")
    if brand not in ALLOWED_IMAGE_HOSTS or not isinstance(items, list):
        raise ValueError("Image source manifest requires a supported brand and an items list.")
    by_reference = {
        str(paint.get("reference", "")).replace(" ", "").upper(): paint
        for paint in catalog.get("paints", [])
        if isinstance(paint, dict) and paint.get("brand") == brand and paint.get("reference")
    }
    seen: set[str] = set()
    unmatched: list[str] = []
    operations: list[dict[str, Any]] = []
    for item in items:
        if not isinstance(item, dict):
            raise ValueError("Image source manifest items must be objects.")
        reference_code = str(item.get("reference", "")).replace(" ", "").upper()
        image_url = str(item.get("image_url", "")).strip()
        page_url = str(item.get("page_url", "")).strip()
        if not reference_code or reference_code in seen:
            raise ValueError(f"Duplicate or missing image source reference: {reference_code or '<missing>'}")
        seen.add(reference_code)
        if reference_code not in by_reference:
            if allow_unmatched:
                unmatched.append(reference_code)
                continue
            raise ValueError(f"Unknown {brand} image source reference: {reference_code}")
        if not _allowed_url(brand, image_url) or not _allowed_url(brand, page_url):
            raise ValueError(f"Non-official image source URL for {brand} {reference_code}")
        current = by_reference[reference_code]
        updated = deepcopy(current)
        updated.setdefault("manufacturer_image", {})["source_url"] = image_url
        updated["manufacturer_image"]["credit"] = f"Official {updated.get('manufacturer', brand)} catalogue"
        updated["manufacturer_page"] = page_url
        snapshots = [
            snapshot for snapshot in updated.get("source_snapshots", [])
            if not (isinstance(snapshot, dict) and snapshot.get("provider") == "official_image_manifest")
        ]
        snapshots.append({
            "provider": "official_image_manifest",
            "url": page_url,
            "payload": {
                "reference": reference_code,
                "name": str(item.get("name", "")),
                "image_url": image_url,
            },
        })
        updated["source_snapshots"] = snapshots
        if updated != current:
            operations.append({
                "action": "upsert", "record": updated,
                "workshop_quantity_delta": 0, "confirmed_removal": False,
            })
    changeset = {
        "schema_version": 1,
        "kind": "market_paints",
        "source": {
            "kind": "official_paint_image_manifest",
            "brand": brand,
            "verified_at": verified_at or date.today().isoformat(),
            "source_url": str(manifest.get("source_url", "")),
            "unmatched_references": sorted(unmatched),
        },
        "operations": sorted(operations, key=lambda operation: operation["record"]["id"]),
    }
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid image source change set: " + "; ".join(errors))
    return changeset
