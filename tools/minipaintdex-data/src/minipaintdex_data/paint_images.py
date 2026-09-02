"""Download and validate official paint packshots into the local media cache."""

from __future__ import annotations

import hashlib
import io
import mimetypes
import tempfile
import time
import xml.etree.ElementTree as ET
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from copy import deepcopy
from datetime import date
from pathlib import Path
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from .changesets import validate_changeset
from .image_quality import IMAGE_QUALITY_RANKS, infer_image_quality, prefer_image, quality_limitation
from .official_sources.common import slug


FetchImage = Callable[[str, int], tuple[bytes, str, str]]
MAX_DOMINANT_COLOR_RATIO = 0.90
MAX_FLAT_ARTWORK_COLORS = 48
PRESENTATION_BACKGROUND = (248, 247, 243)
ALLOWED_IMAGE_HOSTS = {
    "Prince August": {"prince-august.net", "www.prince-august.net"},
    "The Army Painter": {"cdn.shopify.com", "thearmypainter.com", "www.thearmypainter.com"},
    "Vallejo": {"acrylicosvallejo.com", "www.acrylicosvallejo.com"},
    "Warhammer Colour": {"warhammer.com", "www.warhammer.com"},
}


def rekey_cached_paint_images(changeset: dict[str, Any], media_root: Path) -> dict[str, Any]:
    """Move generated cache files to the paths declared by validated rekey operations."""
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid paint identity rekey change set: " + "; ".join(errors))
    root = media_root.resolve()
    moved: list[dict[str, str]] = []
    missing: list[dict[str, str]] = []
    for operation in changeset.get("operations", []):
        if operation.get("action") != "rekey":
            continue
        record = operation.get("record") if isinstance(operation.get("record"), dict) else {}
        image = record.get("manufacturer_image") if isinstance(record.get("manufacturer_image"), dict) else {}
        public_path = str(image.get("path", "")).strip()
        if not public_path.startswith("/media/"):
            continue
        new_id = str(record.get("id", "")).strip()
        old_id = str(operation.get("previous_id", "")).strip()
        target = (root / public_path.removeprefix("/media/")).resolve()
        source = target.with_name(old_id + target.suffix)
        if not target.is_relative_to(root) or not source.is_relative_to(root):
            raise ValueError(f"Paint image rekey escapes the media root: {public_path}")
        if target.exists():
            if source.exists() and source != target:
                raise ValueError(f"Both old and new paint image cache files exist: {source} and {target}")
            continue
        if not source.exists():
            missing.append({"id": new_id, "source": source.as_posix(), "target": target.as_posix()})
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        source.replace(target)
        moved.append({"id": new_id, "source": source.as_posix(), "target": target.as_posix()})
    return {"moved": moved, "missing": missing, "moved_count": len(moved), "missing_count": len(missing)}


def _allowed_url(brand: str, url: str) -> bool:
    parsed = urlparse(url)
    return parsed.scheme == "https" and (parsed.hostname or "").casefold() in ALLOWED_IMAGE_HOSTS.get(brand, set())


def _same_https_host(first: str, second: str) -> bool:
    first_url, second_url = urlparse(first), urlparse(second)
    return (
        first_url.scheme == second_url.scheme == "https"
        and bool(first_url.hostname)
        and first_url.hostname.casefold() == (second_url.hostname or "").casefold()
    )


def _allowed_retailer_urls(image_url: str, page_url: str) -> bool:
    image, page = urlparse(image_url), urlparse(page_url)
    if image.scheme != "https" or page.scheme != "https" or not image.hostname or not page.hostname:
        return False
    return (
        image.hostname.casefold() == page.hostname.casefold()
        or image.hostname.casefold() == "cdn.shopify.com"
    )


def _allowed_image_reference(brand: str, image: dict[str, Any]) -> bool:
    quality = str(image.get("image_quality", "official_photo"))
    source_url = str(image.get("source_url", ""))
    if quality == "official_photo":
        return _allowed_url(brand, source_url)
    if quality == "retailer_photo":
        return _allowed_retailer_urls(source_url, str(image.get("reference_url", "")))
    return False


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


def raster_artwork_metrics(image: Any) -> tuple[float, int]:
    sample = image.convert("RGBA")
    sample.thumbnail((64, 64))
    pixel_data = sample.get_flattened_data() if hasattr(sample, "get_flattened_data") else sample.getdata()
    pixels = [
        (red // 16, green // 16, blue // 16)
        for red, green, blue, alpha in pixel_data
        if alpha >= 16
    ]
    if not pixels:
        return 1.0, 0
    colors = Counter(pixels)
    return colors.most_common(1)[0][1] / len(pixels), len(colors)


def raster_is_flat_artwork(image: Any, *, max_dominant_color_ratio: float) -> tuple[bool, float, int]:
    dominant_color_ratio, quantized_color_count = raster_artwork_metrics(image)
    return (
        dominant_color_ratio >= max_dominant_color_ratio
        and quantized_color_count <= MAX_FLAT_ARTWORK_COLORS,
        dominant_color_ratio,
        quantized_color_count,
    )


def assess_raster_artwork(image: Any, *, max_dominant_color_ratio: float) -> dict[str, Any]:
    """Estimate whether a raster is a usable product photo and explain the decision."""
    try:
        from PIL import Image, ImageFilter
    except ImportError as error:  # pragma: no cover - operator dependency
        raise ValueError("Pillow is required to assess paint images.") from error
    dominant_ratio, color_count = raster_artwork_metrics(image)
    sample = image.convert("RGB")
    sample.thumbnail((96, 96))
    edges = sample.convert("L").filter(ImageFilter.FIND_EDGES)
    edge_values = edges.get_flattened_data() if hasattr(edges, "get_flattened_data") else edges.getdata()
    edge_density = sum(value >= 28 for value in edge_values) / max(1, edges.width * edges.height)

    checker_sample = image.convert("RGB")
    checker_sample.thumbnail((48, 48))
    checker_values = (checker_sample.get_flattened_data()
                      if hasattr(checker_sample, "get_flattened_data") else checker_sample.getdata())
    pixels = list(checker_values)
    neutral = [max(pixel) - min(pixel) <= 14 for pixel in pixels]
    luminance = [sum(pixel) / 3 for pixel in pixels]
    transitions = 0
    comparable = 0
    width, height = checker_sample.size
    for y in range(height):
        for x in range(width):
            index = y * width + x
            for neighbor in ((index + 1) if x + 1 < width else None,
                             (index + width) if y + 1 < height else None):
                if neighbor is not None and neutral[index] and neutral[neighbor]:
                    comparable += 1
                    transitions += abs(luminance[index] - luminance[neighbor]) >= 24
    neutral_ratio = sum(neutral) / max(1, len(neutral))
    checker_transition_ratio = transitions / max(1, comparable)

    # Product packshots contain internal structure: lid separation, label boundaries and
    # typography. Warhammer's catalog swatches can have gradients and a tiny pot outline,
    # which makes global color and edge metrics look deceptively photo-like. Measure edges
    # strictly inside the non-background foreground so those outer contours do not count as
    # product detail.
    structure_sample = image.convert("RGBA")
    structure_sample.thumbnail((128, 128))
    canvas = Image.new("RGBA", structure_sample.size, (*PRESENTATION_BACKGROUND, 255))
    canvas.alpha_composite(structure_sample)
    structure_rgb = canvas.convert("RGB")
    structure_values = (structure_rgb.get_flattened_data()
                        if hasattr(structure_rgb, "get_flattened_data") else structure_rgb.getdata())
    structure_pixels = list(structure_values)
    foreground = [
        not (
            red >= 235 and green >= 235 and blue >= 228
            and max(red, green, blue) - min(red, green, blue) <= 20
        )
        for red, green, blue in structure_pixels
    ]
    foreground_indices = [index for index, value in enumerate(foreground) if value]
    foreground_ratio = len(foreground_indices) / max(1, len(foreground))
    foreground_bbox_ratio = 0.0
    internal_edge_density = 0.0
    if foreground_indices:
        structure_width, structure_height = structure_rgb.size
        xs = [index % structure_width for index in foreground_indices]
        ys = [index // structure_width for index in foreground_indices]
        foreground_bbox_ratio = (
            (max(xs) - min(xs) + 1) * (max(ys) - min(ys) + 1)
            / max(1, structure_width * structure_height)
        )
        structure_edges = structure_rgb.convert("L").filter(ImageFilter.FIND_EDGES)
        structure_edge_values = (structure_edges.get_flattened_data()
                                 if hasattr(structure_edges, "get_flattened_data") else structure_edges.getdata())
        structure_edge_pixels = list(structure_edge_values)
        interior_indices = []
        for index in foreground_indices:
            x, y = index % structure_width, index // structure_width
            if 0 < x < structure_width - 1 and 0 < y < structure_height - 1:
                neighbors = (index - 1, index + 1, index - structure_width, index + structure_width)
                if all(foreground[neighbor] for neighbor in neighbors):
                    interior_indices.append(index)
        internal_edge_density = sum(
            structure_edge_pixels[index] >= 28 for index in interior_indices
        ) / max(1, len(interior_indices))
    # A real checkerboard alternates in both directions often enough to remain above this
    # threshold after downsampling. Product layouts with typography and diagonal artwork can
    # approach 0.18, so keep a margin to avoid rejecting official packshots.
    checkerboard = neutral_ratio >= 0.65 and checker_transition_ratio >= 0.22 and color_count <= 80
    flat = (
        dominant_ratio >= max_dominant_color_ratio
        and color_count <= MAX_FLAT_ARTWORK_COLORS
        and neutral_ratio < 0.65
    ) or (dominant_ratio >= 0.72 and color_count <= 24 and neutral_ratio < 0.50)
    # Neutral products (white paint, mediums and varnishes) are legitimate photographs. Only
    # classify the image as a generic silhouette when one neutral tone also dominates the canvas.
    neutral_silhouette = (
        neutral_ratio >= 0.97 and dominant_ratio >= 0.80
        and color_count <= 24 and edge_density < 0.10
    )
    color_card = (
        foreground_ratio >= 0.72 and foreground_bbox_ratio >= 0.75
        and internal_edge_density <= 0.03
    )
    low_detail_silhouette = (
        0.05 <= foreground_ratio < 0.72 and internal_edge_density <= 0.02
    )

    score = 100
    reasons: list[str] = []
    if flat:
        score -= 80
        reasons.append("flat_colour_artwork")
    if checkerboard:
        score -= 65
        reasons.append("checkerboard_background")
    if neutral_silhouette:
        score -= 65
        reasons.append("low_complexity_neutral_artwork")
    if color_card and not flat:
        score -= 80
        reasons.append("color_card_without_product_detail")
    if low_detail_silhouette and not flat:
        score -= 65
        reasons.append("silhouette_without_product_detail")
    if edge_density < 0.01:
        score -= 20
        reasons.append("very_low_detail")
    elif edge_density < 0.025:
        score -= 8
        reasons.append("low_detail")
    score = max(0, min(100, score))
    classification = "packshot_candidate"
    if flat:
        classification = "color_swatch"
    elif color_card:
        classification = "color_swatch"
    elif checkerboard:
        classification = "checkerboard_visual"
    elif neutral_silhouette or low_detail_silhouette:
        classification = "generic_visual"
    elif score < 70:
        classification = "low_detail_visual"
    return {
        "technical_quality_score": score,
        "classification": classification,
        "accepted_as_photo": not flat and not checkerboard and not neutral_silhouette
        and not color_card and not low_detail_silhouette and score >= 70,
        "reasons": reasons,
        "signals": {
            "dominant_color_ratio": round(dominant_ratio, 4),
            "quantized_color_count": color_count,
            "edge_density": round(edge_density, 4),
            "neutral_pixel_ratio": round(neutral_ratio, 4),
            "checker_transition_ratio": round(checker_transition_ratio, 4),
            "foreground_ratio": round(foreground_ratio, 4),
            "foreground_bbox_ratio": round(foreground_bbox_ratio, 4),
            "internal_edge_density": round(internal_edge_density, 4),
        },
    }


def _write_webp(
    content: bytes, target: Path, *, min_width: int, min_height: int, max_edge: int,
    max_dominant_color_ratio: float,
) -> dict[str, Any]:
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
            assessment = assess_raster_artwork(image, max_dominant_color_ratio=max_dominant_color_ratio)
            dominant_color_ratio = assessment["signals"]["dominant_color_ratio"]
            quantized_color_count = assessment["signals"]["quantized_color_count"]
            if not assessment["accepted_as_photo"]:
                reason_text = ", ".join(assessment["reasons"]).replace("flat_colour_artwork", "flat colour artwork")
                raise ValueError(
                    "image is not a product photo: " + reason_text
                )
            image.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)
            converted = image.convert("RGBA" if "A" in image.getbands() else "RGB")
            content_width, content_height = converted.size
            background = (*PRESENTATION_BACKGROUND, 0) if converted.mode == "RGBA" else PRESENTATION_BACKGROUND
            canvas = Image.new(converted.mode, (max_edge, max_edge), background)
            offset = ((max_edge - content_width) // 2, (max_edge - content_height) // 2)
            canvas.paste(converted, offset, converted if converted.mode == "RGBA" else None)
            presentation_sample = canvas
            if canvas.mode == "RGBA":
                presentation_sample = Image.new("RGB", canvas.size, PRESENTATION_BACKGROUND)
                presentation_sample.paste(canvas, (0, 0), canvas)
            presentation_assessment = assess_raster_artwork(
                presentation_sample, max_dominant_color_ratio=max_dominant_color_ratio,
            )
            if not presentation_assessment["accepted_as_photo"]:
                reason_text = ", ".join(presentation_assessment["reasons"])
                raise ValueError("presentation canvas is not a product photo: " + reason_text)
            target.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(dir=target.parent, suffix=".webp.tmp", delete=False) as handle:
                temporary = Path(handle.name)
            try:
                canvas.save(temporary, format="WEBP", quality=82, method=6)
                temporary.replace(target)
            finally:
                if temporary.exists():
                    temporary.unlink()
    except (OSError, UnidentifiedImageError) as error:
        raise ValueError("source is not a readable raster image") from error
    return {
        "width": canvas.width,
        "height": canvas.height,
        "content_width": content_width,
        "content_height": content_height,
        "content_offset": {"x": offset[0], "y": offset[1]},
        "presentation_canvas": "square",
        "original_width": original_width,
        "original_height": original_height,
        "dominant_color_ratio": round(dominant_color_ratio, 4),
        "quantized_color_count": quantized_color_count,
        "technical_assessment": assessment,
        "presentation_assessment": presentation_assessment,
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
    max_edge: int, max_bytes: int, max_dominant_color_ratio: float,
    overwrite: bool, normalize_local: bool, verified_at: str, fetch_image: FetchImage,
    fallback: dict[str, Any] | None,
) -> tuple[dict[str, Any] | None, dict[str, Any]]:
    identifier = str(paint.get("id", ""))
    brand = str(paint.get("brand", ""))
    image = paint.get("manufacturer_image") if isinstance(paint.get("manufacturer_image"), dict) else {}
    source_url = str(image.get("source_url", "")).strip()
    local_path = str(image.get("path", "")).strip()
    if normalize_local and local_path.startswith("/media/"):
        local_target = (media_root.resolve() / local_path.removeprefix("/media/")).resolve()
        if not local_target.is_relative_to(media_root.resolve()):
            raise ValueError(f"Image cache target escapes the media root: {identifier}")
        if local_target.exists() and local_target.suffix.casefold() == ".webp":
            try:
                from PIL import Image
                with Image.open(local_target) as cached:
                    size = cached.size
                if size == (max_edge, max_edge):
                    return None, {
                        "id": identifier, "brand": brand, "status": "already_normalized",
                        "path": local_path, "width": size[0], "height": size[1],
                    }
                metadata = _write_webp(
                    local_target.read_bytes(), local_target, min_width=min_width, min_height=min_height,
                    max_edge=max_edge, max_dominant_color_ratio=max_dominant_color_ratio,
                )
                return None, {
                    "id": identifier, "brand": brand, "status": "normalized_local",
                    "path": local_path, **metadata,
                }
            except (OSError, ValueError) as error:
                return None, {
                    "id": identifier, "brand": brand, "status": "failed",
                    "path": local_path, "error": str(error),
                }
        if local_target.exists():
            return None, {
                "id": identifier, "brand": brand, "status": "vector_unchanged", "path": local_path,
            }
    if local_path and not overwrite and (not source_url or not _allowed_image_reference(brand, image)):
        return None, {"id": identifier, "brand": brand, "status": "skipped_local", "path": local_path}
    if not source_url:
        return None, {"id": identifier, "brand": brand, "status": "missing_source"}
    if not _allowed_image_reference(brand, image):
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
                        _write_webp(
                            target.read_bytes(), target, min_width=min_width, min_height=min_height,
                            max_edge=max_edge, max_dominant_color_ratio=max_dominant_color_ratio,
                        ))
            status = "reused"
            final_url = source_url
        else:
            content, content_type, final_url = fetch_image(source_url, max_bytes)
            redirected = dict(image)
            redirected["source_url"] = final_url
            if not _allowed_image_reference(brand, redirected):
                raise ValueError("redirected to a non-official host")
            if not content_type.startswith("image/") and not mimetypes.guess_type(final_url)[0]:
                raise ValueError(f"unexpected content type: {content_type}")
            metadata = (_write_svg(content, target, max_edge=max_edge) if is_svg else
                        _write_webp(
                            content, target, min_width=min_width, min_height=min_height,
                            max_edge=max_edge, max_dominant_color_ratio=max_dominant_color_ratio,
                        ))
            status = "cached"
        updated = deepcopy(paint)
        updated.setdefault("manufacturer_image", {})["path"] = public_path
        updated["manufacturer_image"]["image_quality"] = infer_image_quality(paint)
        updated["manufacturer_image"]["quality_verified_at"] = verified_at
        return updated, {
            "id": identifier, "brand": brand, "status": status, "path": public_path,
            "source_url": source_url, "final_url": final_url, **metadata,
        }
    except (OSError, ValueError) as error:
        retained = _record_official_candidate_rejection(paint, fallback, str(error), verified_at)
        return retained, {
            "id": identifier, "brand": brand, "status": "failed", "source_url": source_url,
            "error": str(error),
        }


def _record_official_candidate_rejection(
    candidate: dict[str, Any], fallback: dict[str, Any] | None, error: str, observed_at: str,
) -> dict[str, Any] | None:
    candidate_image = candidate.get("manufacturer_image") if isinstance(candidate.get("manufacturer_image"), dict) else {}
    if str(candidate_image.get("image_quality", "")) != "official_photo":
        return None
    retained = deepcopy(candidate)
    fallback_image = fallback.get("manufacturer_image") if isinstance(fallback, dict) and isinstance(
        fallback.get("manufacturer_image"), dict
    ) else {}
    fallback_quality = infer_image_quality(fallback) if isinstance(fallback, dict) else "none"
    if fallback_quality == "official_photo":
        return None
    retained_image = deepcopy(fallback_image) if fallback_image else {
        "path": "", "source_url": "", "credit": "", "license": "", "reference_url": "",
        "image_quality": "none", "quality_verified_at": "",
    }
    retained_image["image_quality"] = fallback_quality
    retained_image["quality_limitation"] = quality_limitation(
        "official-candidate-rejected",
        f"An official image candidate was rejected by the cache quality gate: {error}",
        observed_at,
    )
    retained["manufacturer_image"] = retained_image
    return retained


def build_image_cache_changeset(
    catalog: dict[str, Any], media_root: Path, *, brands: list[str] | None = None,
    min_width: int = 300, min_height: int = 300, max_edge: int = 800,
    max_bytes: int = 10 * 1024 * 1024, overwrite: bool = False, normalize_local: bool = False, limit: int = 0,
    workers: int = 4, verified_at: str | None = None, fetch_image: FetchImage = _download,
    max_dominant_color_ratio: float = MAX_DOMINANT_COLOR_RATIO,
    fallback_catalog: dict[str, Any] | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if min_width <= 0 or min_height <= 0 or max_edge < max(min_width, min_height):
        raise ValueError("Image dimensions must be positive and max_edge must cover the minimum dimensions.")
    if max_bytes <= 0 or workers <= 0 or workers > 16 or limit < 0 or not 0 < max_dominant_color_ratio < 1:
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
    verification_date = verified_at or date.today().isoformat()
    results: list[tuple[dict[str, Any] | None, dict[str, Any]]] = []
    fallback_by_id = {
        str(paint.get("id", "")): paint for paint in (fallback_catalog or {}).get("paints", [])
        if isinstance(paint, dict)
    }
    with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="paint-image-cache") as executor:
        futures = {
            executor.submit(
                _cache_one, paint, media_root, min_width=min_width, min_height=min_height,
                max_edge=max_edge, max_bytes=max_bytes, max_dominant_color_ratio=max_dominant_color_ratio,
                overwrite=overwrite, normalize_local=normalize_local, verified_at=verification_date,
                fetch_image=fetch_image, fallback=fallback_by_id.get(str(paint.get("id", ""))),
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
        "presentation_canvas": "square",
        "max_dominant_color_ratio": max_dominant_color_ratio,
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
    manifest_quality = str(manifest.get("image_quality", "official_photo")).strip()
    if manifest_quality not in {"official_photo", "retailer_photo"}:
        raise ValueError("A remote image manifest must declare official_photo or retailer_photo quality.")
    verification_date = verified_at or date.today().isoformat()
    raw_quality_overrides = manifest.get("quality_overrides", {})
    if not isinstance(raw_quality_overrides, dict):
        raise ValueError("Image source manifest quality_overrides must be an object keyed by reference.")
    quality_overrides = {
        str(reference_code).replace(" ", "").upper(): str(quality).strip()
        for reference_code, quality in raw_quality_overrides.items()
    }
    unsupported_overrides = sorted(
        reference_code for reference_code, quality in quality_overrides.items()
        if quality not in {"generic_visual", "color_swatch"}
    )
    if unsupported_overrides:
        raise ValueError(
            "Image quality overrides must declare generic_visual or color_swatch: "
            + ", ".join(unsupported_overrides)
        )
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
        item_quality = str(item.get("image_quality", manifest_quality)).strip()
        if item_quality not in {"official_photo", "retailer_photo"}:
            raise ValueError(f"Invalid remote image quality for {brand} {reference_code}: {item_quality}")
        reviewed_replacement = item.get("reviewed_replacement", False)
        if not isinstance(reviewed_replacement, bool):
            raise ValueError(f"reviewed_replacement must be a boolean for {brand} {reference_code}")
        if item_quality == "official_photo" and (
            not _allowed_url(brand, image_url) or not _allowed_url(brand, page_url)
        ):
            raise ValueError(f"Non-official image source URL for {brand} {reference_code}")
        if item_quality == "retailer_photo" and not _allowed_retailer_urls(image_url, page_url):
            raise ValueError(f"Retailer image and product page must use the same HTTPS host for {reference_code}")
        credit = str(item.get("credit", "")).strip()
        if item_quality == "retailer_photo" and not credit:
            raise ValueError(f"Retailer image credit is required for {brand} {reference_code}")
        current = by_reference[reference_code]
        updated = deepcopy(current)
        effective_quality = quality_overrides.get(reference_code, item_quality)
        candidate = {
            "path": "",
            "source_url": image_url,
            "credit": credit or f"Official {updated.get('manufacturer', brand)} catalogue",
            "license": str(item.get("license", "")),
            "reference_url": page_url,
            "image_quality": effective_quality,
            "quality_verified_at": verification_date,
        }
        if effective_quality != "official_photo":
            declared_limitation = item.get("quality_limitation")
            if isinstance(declared_limitation, dict):
                candidate["quality_limitation"] = declared_limitation
            elif effective_quality == "color_swatch":
                candidate["quality_limitation"] = quality_limitation(
                    "better-source-not-found",
                    "The audited source asset is a color swatch rather than a product packshot; no better source was retained.",
                    verification_date,
                )
            elif effective_quality == "generic_visual":
                candidate["quality_limitation"] = quality_limitation(
                    "better-source-not-found",
                    "The audited source asset does not show an identifiable product packshot; no better source was retained.",
                    verification_date,
                )
            else:
                candidate["quality_limitation"] = quality_limitation(
                    "better-source-not-found",
                    "No accepted official photo accompanied this refresh; the credited retailer photo was retained.",
                    verification_date,
                )
        current_image = deepcopy(updated.get("manufacturer_image")) if isinstance(updated.get("manufacturer_image"), dict) else {}
        current_image.setdefault("image_quality", infer_image_quality(updated))
        if reviewed_replacement:
            if IMAGE_QUALITY_RANKS[effective_quality] > IMAGE_QUALITY_RANKS.get(
                str(current_image.get("image_quality", "none")), IMAGE_QUALITY_RANKS["none"]
            ):
                raise ValueError(f"Reviewed replacement would downgrade image quality for {brand} {reference_code}")
            if str(current_image.get("source_url", "")) == image_url:
                candidate["path"] = str(current_image.get("path", ""))
            updated["manufacturer_image"] = candidate
        elif reference_code in quality_overrides and str(current_image.get("source_url", "")) == image_url:
            candidate["path"] = str(current_image.get("path", ""))
            updated["manufacturer_image"] = candidate
        else:
            updated["manufacturer_image"] = prefer_image(current_image, candidate)
        updated["manufacturer_page"] = page_url
        snapshots = [
            snapshot for snapshot in updated.get("source_snapshots", [])
            if not (isinstance(snapshot, dict) and snapshot.get("provider") in {
                "official_image_manifest", "retailer_image_manifest",
            })
        ]
        snapshot_payload = {
            "reference": reference_code,
            "name": str(item.get("name", "")),
            "image_url": image_url,
            "image_quality": effective_quality,
            "credit": candidate["credit"],
        }
        if effective_quality != item_quality:
            snapshot_payload["source_quality"] = item_quality
        snapshots.append({
            "provider": "official_image_manifest" if item_quality == "official_photo" else "retailer_image_manifest",
            "url": page_url,
            "payload": snapshot_payload,
        })
        updated["source_snapshots"] = snapshots
        if updated != current:
            operations.append({
                "action": "upsert", "record": updated,
                "workshop_quantity_delta": 0, "confirmed_removal": False,
            })
    for reference_code, effective_quality in quality_overrides.items():
        if reference_code in seen or reference_code not in by_reference:
            continue
        current = by_reference[reference_code]
        updated = deepcopy(current)
        current_image = deepcopy(updated.get("manufacturer_image")) if isinstance(
            updated.get("manufacturer_image"), dict
        ) else {}
        current_image["image_quality"] = effective_quality
        current_image["quality_verified_at"] = verification_date
        current_image["quality_limitation"] = quality_limitation(
            "better-source-not-found",
            (
                "The audited source asset is a color swatch rather than a product packshot; "
                "no better source was retained."
                if effective_quality == "color_swatch"
                else "The audited source asset does not show an identifiable product packshot; "
                     "no better source was retained."
            ),
            verification_date,
        )
        updated["manufacturer_image"] = current_image
        snapshots = [
            snapshot for snapshot in updated.get("source_snapshots", [])
            if not (isinstance(snapshot, dict) and snapshot.get("provider") == "image_quality_review")
        ]
        snapshots.append({
            "provider": "image_quality_review",
            "url": str(current_image.get("reference_url") or current_image.get("source_url")
                       or manifest.get("source_url", "")),
            "payload": {
                "reference": reference_code,
                "name": str(current.get("name", "")),
                "image_url": str(current_image.get("source_url", "")),
                "image_quality": effective_quality,
            },
        })
        updated["source_snapshots"] = snapshots
        if updated != current:
            operations.append({
                "action": "upsert", "record": updated,
                "workshop_quantity_delta": 0, "confirmed_removal": False,
            })
    unknown_overrides = sorted(set(quality_overrides) - set(by_reference))
    if unknown_overrides:
        raise ValueError("Image quality overrides do not match catalog references: " + ", ".join(unknown_overrides))
    changeset = {
        "schema_version": 1,
        "kind": "market_paints",
        "source": {
            "kind": "official_paint_image_manifest",
            "brand": brand,
            "verified_at": verification_date,
            "source_url": str(manifest.get("source_url", "")),
            "unmatched_references": sorted(unmatched),
        },
        "operations": sorted(operations, key=lambda operation: operation["record"]["id"]),
    }
    errors = validate_changeset(changeset, allow_empty=True)
    if errors:
        raise ValueError("Invalid image source change set: " + "; ".join(errors))
    return changeset
