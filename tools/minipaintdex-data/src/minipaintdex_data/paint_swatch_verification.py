"""Reproduce reviewed digital swatches from pinned local manufacturer assets."""

import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from PIL import Image, ImageStat


def verify_reviewed_swatches(manifest_path: Path, source_roots: list[Path]) -> dict:
    review = json.loads(manifest_path.read_text(encoding="utf-8"))
    if review.get("schema_version") != 1:
        raise ValueError("Unsupported swatch review schema")
    items = []
    output_root = Path(__file__).resolve().parents[2] / "target"
    output_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="swatch-verification-", dir=output_root) as temporary:
        for decision in review.get("field_corrections", []):
            if decision["field"] != "color.hex":
                continue
            evidence = decision["evidence"]
            name = evidence["source_file"]
            if Path(name).name != name or "\\" in name or "/" in name:
                raise ValueError("Swatch source must be a simple filename")
            candidates = [root / name for root in source_roots if (root / name).is_file()]
            matches = [p for p in candidates if hashlib.sha256(p.read_bytes()).hexdigest() == evidence["source_sha256"]]
            if not matches:
                raise ValueError(f"Missing or changed pinned swatch source: {name}")
            source = matches[0]
            rendered = source
            if source.suffix.lower() == ".pdf":
                page, size = evidence["page"], evidence["render_long_side"]
                if not isinstance(page, int) or page < 1 or not isinstance(size, int) or not 100 <= size <= 10000:
                    raise ValueError("Invalid swatch PDF render parameters")
                rendered = Path(temporary) / f"{evidence['source_sha256']}-{page}-{size}.png"
                if not rendered.exists():
                    subprocess.run(["pdftoppm", "-f", str(page), "-l", str(page), "-scale-to", str(size),
                                    "-png", "-singlefile", str(source), str(rendered.with_suffix(""))],
                                   check=True, capture_output=True, timeout=60)
            with Image.open(rendered) as image:
                if list(image.size) != evidence["image_size"]:
                    raise ValueError(f"Unexpected swatch dimensions: {name}")
                box = evidence["box"]
                if (len(box) != 4 or not all(isinstance(x, int) for x in box)
                        or not 0 <= box[0] < box[2] <= image.width or not 0 <= box[1] < box[3] <= image.height):
                    raise ValueError("Swatch rectangle outside image")
                if evidence["extraction_method"] != "median-rgb":
                    raise ValueError("Unsupported reviewed swatch extraction method")
                rgb = ImageStat.Stat(image.convert("RGB").crop(box)).median
                hex_color = "#" + "".join(f"{round(v):02x}" for v in rgb)
            if hex_color != decision["after"]:
                raise ValueError(f"Swatch reproduction mismatch for {decision['id']}: {hex_color}")
            items.append({"paint_id": decision["id"], "hex": hex_color, "source_sha256": evidence["source_sha256"]})
    return {"schema_version": 1, "kind": "reviewed_swatch_verification", "review_id": review["id"],
            "verified_count": len(items), "items": items}
