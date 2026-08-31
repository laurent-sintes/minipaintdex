"""Audit public media references without mutating the repository."""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any


PUBLIC_PATH = re.compile(
    r"(?<![A-Za-z0-9_:/.-])(?P<path>/[A-Za-z0-9_./-]+\.(?:png|jpe?g|webp|svg))",
    re.IGNORECASE,
)


def audit_assets(root: Path) -> dict[str, Any]:
    root = root.resolve()
    frontend_public = root / "frontend" / "public"
    public = frontend_public if frontend_public.is_dir() else root / "public"
    data = root / "data"
    references: set[str] = set()
    for path in sorted(data.rglob("*")):
        if path.is_file() and path.suffix.lower() in {".yaml", ".yml", ".json", ".jsonl"}:
            for match in PUBLIC_PATH.finditer(path.read_text(encoding="utf-8-sig")):
                references.add(match.group("path").replace("\\", "/"))
    files = {
        "/" + path.relative_to(public).as_posix()
        for path in public.rglob("*")
        if path.is_file()
    }
    allowed_unreferenced = {"/favicon.svg"}
    return {
        "public_files": len(files),
        "referenced_files": len(files & references),
        "missing": sorted(references - files),
        "orphaned": sorted(files - references - allowed_unreferenced),
    }
