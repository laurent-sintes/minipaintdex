#!/usr/bin/env python3
"""Run the MiniPaintDex data tools directly from the repository checkout."""

from __future__ import annotations

import sys
from pathlib import Path


PACKAGE_ROOT = Path(__file__).resolve().parent / "src"
sys.path.insert(0, str(PACKAGE_ROOT))

from minipaintdex_data.cli import main  # noqa: E402


if __name__ == "__main__":
    raise SystemExit(main())
