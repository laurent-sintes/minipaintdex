"""Read-only HSL swatch proposal, independent of lexical families and paint effects."""

import colorsys
import hashlib
import json
import math
import re
from pathlib import Path


DEFAULT_POLICY = Path(__file__).resolve().parents[2] / "resources/paint-hsl-policy.json"


def load_policy(path=DEFAULT_POLICY):
    content = path.read_bytes()
    policy = json.loads(content)
    if policy.get("schema_version") != 1 or not policy.get("id"):
        raise ValueError("Invalid HSL policy")
    for axis in ("saturation", "lightness"):
        edges, centers = policy[f"{axis}_edges"], policy[f"{axis}_centers"]
        if (len(centers) != len(edges) + 1 or edges != sorted(set(edges))
                or any(not math.isfinite(x) or not 0 < x < 1 for x in [*edges, *centers])
                or any(not lo < mid < hi for lo, mid, hi in zip([0, *edges], centers, [*edges, 1]))):
            raise ValueError(f"Invalid HSL {axis} bands")
    bins = policy["hue_bins"]
    if len({b["id"] for b in bins}) != len(bins):
        raise ValueError("Duplicate HSL hue bins")
    for b in bins:
        if not all(math.isfinite(b[k]) and 0 <= b[k] < 360 for k in ("start", "end", "center")) or b["start"] == b["end"]:
            raise ValueError("Invalid HSL hue interval")
    boundaries = sorted({0, 360, *(b[k] for b in bins for k in ("start", "end"))})
    if any(sum(_contains(b, (lo + hi) / 2) for b in bins) != 1 for lo, hi in zip(boundaries, boundaries[1:])):
        raise ValueError("HSL hue intervals must partition the whole circle")
    if any(not _contains(b, b["center"]) for b in bins):
        raise ValueError("HSL hue center outside interval")
    if not 0 <= policy["neutral_saturation_max"] < policy["saturation_edges"][0]:
        raise ValueError("Invalid neutral threshold")
    if not 0 <= policy["boundary_hue_degrees"] < 15 or not 0 <= policy["boundary_sl_margin"] < .1:
        raise ValueError("Invalid boundary margins")
    return policy, hashlib.sha256(content).hexdigest()


def _contains(b, hue):
    return b["start"] <= hue < b["end"] if b["start"] < b["end"] else hue >= b["start"] or hue < b["end"]


def classify_hex(hex_color, policy):
    if not isinstance(hex_color, str) or not re.fullmatch(r"#[0-9a-fA-F]{6}", hex_color):
        raise ValueError("HSL requires a six-digit HEX colour")
    r, g, b = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    hue, lightness, saturation = colorsys.rgb_to_hls(r, g, b)
    hue *= 360
    neutral = saturation <= policy["neutral_saturation_max"]
    hue_bin = None if neutral else next(b for b in policy["hue_bins"] if _contains(b, hue))
    family = "neutral" if neutral else hue_bin["id"]
    si = sum(saturation >= edge for edge in policy["saturation_edges"])
    li = sum(lightness >= edge for edge in policy["lightness_edges"])
    swatch_h = 0 if neutral else hue_bin["center"] / 360
    swatch_s = 0 if neutral else policy["saturation_centers"][si]
    swatch_l = policy["lightness_centers"][li]
    rgb = colorsys.hls_to_rgb(swatch_h, swatch_l, swatch_s)
    swatch = "#" + "".join(f"{round(v * 255):02x}" for v in rgb)
    boundaries = []
    if not neutral and any(min(abs(hue - b["start"]), 360 - abs(hue - b["start"])) <= policy["boundary_hue_degrees"] for b in policy["hue_bins"]):
        boundaries.append("hue-boundary")
    margin = policy["boundary_sl_margin"]
    if abs(saturation - policy["neutral_saturation_max"]) <= margin:
        boundaries.append("neutral-boundary")
    if not neutral and any(abs(saturation - edge) <= margin for edge in policy["saturation_edges"]):
        boundaries.append("saturation-boundary")
    if any(abs(lightness - edge) <= margin for edge in policy["lightness_edges"]):
        boundaries.append("lightness-boundary")
    return {"h": round(hue, 4) if saturation else None, "s": round(saturation, 6), "l": round(lightness, 6),
            "hue_group": family, "swatch_id": f"{family}-s{0 if neutral else si + 1}-l{li + 1}",
            "swatch_hex": swatch, "review_reasons": boundaries}
