"""Deterministic, resumable archival of verified workshop-paint photo batches."""
from datetime import date
from pathlib import Path
import hashlib
import json
import re

from .paint_pots import ledger_snapshot
from .changesets import load_json, write_json


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def same_photo(left, right):
    if digest(left) == digest(right):
        return True
    from PIL import Image
    with Image.open(left) as a, Image.open(right) as b:
        return a.size == b.size and a.convert("RGB").tobytes() == b.convert("RGB").tobytes()


def plan_archive(root, manifest):
    root = Path(root).resolve()
    if manifest.get("schema_version") != 1 or manifest.get("target") != "workshop.paint-pots":
        raise ValueError("Archive manifest must target workshop.paint-pots, schema_version 1")
    identifier = manifest.get("import_id", "")
    if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", identifier):
        raise ValueError("Invalid import_id")
    archive_date = date.fromisoformat(manifest["archive_date"]).isoformat()
    snapshot = ledger_snapshot(root)
    if snapshot["ledgerSha256"] != manifest.get("verified_ledger_sha256"):
        raise ValueError("Ledger is not the verified generation; verify the import before archiving")
    base = (root / "imports/workshop-paints").resolve()
    if not base.is_relative_to(root):
        raise ValueError("Import directory escapes application root")
    intake = (base / "photos").resolve()
    archive = (base / "archive").resolve()
    if not intake.is_relative_to(base) or not archive.is_relative_to(base):
        raise ValueError("Photo directories escape the import root")
    moves, pending, seen = [], [], set()
    if not isinstance(manifest.get("photos"), list):
        raise ValueError("photos must be a list")
    for photo in manifest["photos"]:
        name = photo["path"]
        if not isinstance(name, str) or "/" in name or "\\" in name or name in {"", ".", ".."} or name in seen:
            raise ValueError("Photo paths must be unique filenames")
        seen.add(name)
        outcome = photo.get("outcome")
        if outcome not in {"imported", "duplicate", "pending"}:
            raise ValueError("Each photo needs an explicit imported, duplicate or pending outcome")
        if outcome == "pending":
            pending.append(name)
            continue
        if outcome == "imported":
            ids = photo.get("paint_pot_ids", [])
            known_ids = {pot["paintPotId"] for pot in snapshot["pots"]}
            if not ids or not set(ids).issubset(known_ids):
                raise ValueError("Imported photos must reference verified paint pot identities")
        sha = photo.get("sha256", "")
        if not isinstance(sha, str) or not re.fullmatch(r"[a-f0-9]{64}", sha):
            raise ValueError("Missing source SHA-256")
        source = (intake / name).resolve()
        folder = archive / archive_date / identifier
        if outcome == "duplicate":
            folder /= "duplicates"
        destination = (folder / name).resolve()
        if not source.is_relative_to(intake) or not destination.is_relative_to(archive):
            raise ValueError("Photo path escapes intake/archive")
        if source.exists() and destination.exists():
            raise ValueError("Archive collision: existing files are never overwritten")
        actual = source if source.exists() else destination
        if not actual.is_file() or digest(actual) != sha:
            raise ValueError("Missing or changed photo: " + name)
        if outcome == "duplicate":
            original = (root / photo.get("duplicate_of", "")).resolve()
            if not original.is_relative_to(archive) or original == actual or not original.is_file() or not same_photo(actual, original):
                raise ValueError("Duplicate must match a different archived photo")
        moves.append({"source": source.relative_to(root).as_posix(), "destination": destination.relative_to(root).as_posix(),
                      "sha256": sha, "photo_id": "photo-" + sha, "outcome": outcome})
    return {"schema_version": 1, "target": "workshop.paint-pots", "import_id": identifier,
            "archive_date": archive_date, "moves": moves, "pending": pending}


def archive_batch(root, manifest, apply=False):
    root = Path(root).resolve()
    plan = plan_archive(root, manifest)
    if not apply:
        return {**plan, "applied": False}
    report = (root / "imports/workshop-paints/runs" / plan["import_id"] / "archive-manifest.json").resolve()
    if not report.is_relative_to((root / "imports/workshop-paints").resolve()):
        raise ValueError("Archive report escapes the import root")
    if report.exists():
        previous = load_json(report)
        if any(previous.get(key) != value for key, value in plan.items()):
            raise ValueError("Import ID already belongs to another archive plan")
    report.parent.mkdir(parents=True, exist_ok=True)
    write_json(report, {**plan, "status": "archiving"})
    for move in plan["moves"]:
        source, destination = root / move["source"], root / move["destination"]
        if source.exists():
            destination.parent.mkdir(parents=True, exist_ok=True)
            if destination.exists() or digest(source) != move["sha256"]:
                raise ValueError("Photo/archive changed during archival")
            source.rename(destination)
        if digest(destination) != move["sha256"]:
            raise ValueError("Archive hash verification failed")
    result = {**plan, "status": "archived", "applied": True}
    write_json(report, result)
    return result


def command_archive_batch(args):
    result = archive_batch(args.root, load_json(Path(args.manifest)), args.apply)
    print(json.dumps(result, ensure_ascii=False))
    return 0
