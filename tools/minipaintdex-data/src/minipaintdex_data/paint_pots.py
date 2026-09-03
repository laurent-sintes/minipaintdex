"""Registration datasets and evidence snapshots for physical paint pots.

Reads committed ledger facts only. Never writes application storage.
"""
from pathlib import Path
import hashlib
import json
import re


def ledger_snapshot(root):
    directory = Path(root) / "data/ledger/events"
    pots = {}
    digest = hashlib.sha256()
    for path in sorted(directory.glob("*.jsonl")):
        content = path.read_bytes()
        digest.update(path.name.encode("utf-8") + b"\0" + content + b"\0")
        for line in content.decode("utf-8").splitlines():
            if not line.strip():
                continue
            event = json.loads(line)
            kind = event.get("event_type", "")
            if not kind.startswith("paint_pot."):
                continue
            identifier = event["aggregate_id"]
            payload = event["payload"]
            if kind == "paint_pot.registered":
                if identifier in pots:
                    raise ValueError("Duplicate pot registration: " + identifier)
                pots[identifier] = {"paintPotId": identifier, "paintProductId": payload["paint_product_id"],
                                    "acquiredAt": payload.get("acquired_at"), "possession": "owned"}
            elif identifier not in pots:
                raise ValueError("Paint pot event before registration: " + identifier)
            elif kind == "paint_pot.possession_changed":
                pots[identifier]["possession"] = payload["possession"]
    return {"ledgerSha256": digest.hexdigest(), "pots": [pots[key] for key in sorted(pots)]}


def build_import(payload):
    if not isinstance(payload, dict) or not isinstance(payload.get("pots"), list):
        raise ValueError("Explicit pots list required; never infer identity from a photo or quantity")
    result, seen = [], set()
    for pot in payload["pots"]:
        if not isinstance(pot, dict):
            raise ValueError("Each pot must be an object")
        for key in ("paintPotId", "paintProductId"):
            if not isinstance(pot.get(key), str) or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", pot[key]):
                raise ValueError(key + " must be a stable lowercase kebab-case identity")
        if pot["paintPotId"] in seen:
            raise ValueError("Duplicate paintPotId: " + pot["paintPotId"])
        seen.add(pot["paintPotId"])
        result.append({key: pot.get(key) for key in ("paintPotId", "paintProductId", "acquiredAt")})
    return {"schemaVersion": 1, "kind": "workshop_paint_pots", "pots": sorted(result, key=lambda pot: pot["paintPotId"])}
