#!/usr/bin/env python3
"""Deterministic helpers for the miniature-paint photo import workflow.

The script deliberately does not identify products from pixels or browse the web.
It normalizes already-observed data, flags ambiguity, merges confirmed records and
keeps photo/source provenance.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from .paint_identity import market_paint_deduplication_key, market_paint_id


IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".heic", ".heif", ".webp", ".tif", ".tiff"}
FUNCTIONAL_CLASSES = {
    "one_coat_contrast",
    "opaque_standard",
    "wash_shade",
    "ink",
    "metallic",
    "airbrush",
    "primer",
    "technical_effect",
    "fluorescent",
    "auxiliary",
    "unknown",
}

CSV_COLUMNS = [
    "id",
    "marque_observee",
    "marque_canonique",
    "alias_marque",
    "fabricant",
    "gamme_observee",
    "gamme_canonique",
    "classe_fonctionnelle",
    "reference",
    "nom",
    "quantite",
    "confiance",
    "statut",
    "avertissements",
    "source_photo",
    "source_hashes",
    "couleur_hex",
    "famille_couleur",
    "fini",
    "medium",
    "volume_ml",
    "tags",
    "usages_conseilles",
    "fiche_fabricant",
    "image_fabricant",
    "source_image",
    "credit_image",
    "verifie_le",
    "notes",
    "cle_dedoublonnage",
]


def plain(value: Any) -> str:
    return "" if value is None else str(value).strip()


def folded(value: Any) -> str:
    text = unicodedata.normalize("NFKD", plain(value)).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def slug(value: Any) -> str:
    return re.sub(r"[^a-z0-9]+", "-", folded(value)).strip("-")


def as_float(value: Any, default: float = 0.0) -> float:
    try:
        return float(str(value).replace(",", "."))
    except (TypeError, ValueError):
        return default


def as_int(value: Any, default: int = 0) -> int:
    try:
        return int(float(str(value).replace(",", ".")))
    except (TypeError, ValueError):
        return default


def split_values(value: Any) -> list[str]:
    if isinstance(value, list):
        values = [plain(item) for item in value]
    else:
        values = re.split(r"\s*\|\s*", plain(value)) if plain(value) else []
    return [item for item in values if item]


def join_unique(values: Iterable[Any]) -> str:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        for item in split_values(value):
            key = folded(item)
            if key and key not in seen:
                seen.add(key)
                result.append(item)
    return "|".join(result)


def canonical_brand(observed: Any) -> tuple[str, str, str]:
    key = folded(observed)
    if key in {"citadel", "citadel colour", "citadel color", "games workshop", "gw paint", "warhammer", "warhammer colour", "warhammer color"}:
        return "Warhammer Colour", "Citadel|Citadel Colour|Games Workshop", "Games Workshop"
    if key in {"vallejo", "acrylicos vallejo", "acrylicos vallejo s l"}:
        return "Vallejo", "Acrylicos Vallejo", "Acrylicos Vallejo"
    if key in {"army painter", "the army painter"}:
        return "The Army Painter", "Army Painter", "The Army Painter"
    if key in {"prince august", "prince auguste"}:
        return "Prince August", "Prince Auguste", "Prince August"
    return plain(observed), "", ""


def canonical_range(observed: Any) -> tuple[str, list[str]]:
    raw = plain(observed)
    key = folded(raw)
    warnings: list[str] = []
    if key in {"cpress", "cpress color", "xpress", "xpress colour", "xpresscolor"}:
        warnings.append("OCR corrigé en « Xpress Color » ; confirmer sur l’étiquette")
        return "Xpress Color", warnings
    exact = {
        "xpress color": "Xpress Color",
        "xpress color intense": "Xpress Color Intense",
        "contrast": "Contrast",
        "speedpaint": "Speedpaint",
        "speed paint": "Speedpaint",
        "speedpaint 2 0": "Speedpaint 2.0",
        "warpaints fanatic": "Warpaints Fanatic",
        "model color": "Model Color",
        "game color": "Game Color",
        "game color metallic": "Game Color Metallic",
        "model air": "Model Air",
        "game air": "Game Air",
        "mecha color": "Mecha Color",
        "pa air": "PA-Air",
        "special fx": "Special FX",
        "diorama fx": "Diorama FX",
        "xpressbase": "XpressBase",
    }
    return exact.get(key, raw), warnings


def classify_function(range_name: Any, product_name: Any = "") -> str:
    key = f"{folded(range_name)} {folded(product_name)}".strip()
    checks: list[tuple[str, tuple[str, ...]]] = [
        ("one_coat_contrast", ("contrast", "xpress color", "speedpaint")),
        ("primer", ("primer", "undercoat", "sous couche", "xpressbase", "colour primer")),
        ("airbrush", ("model air", "game air", "pa air", "mecha color", "airbrush")),
        ("wash_shade", ("shade", "wash", "lavis")),
        ("ink", ("ink", "encre")),
        ("metallic", ("metal color", "metallic", "metallique", "metal")),
        ("fluorescent", ("fluorescent", "fluo")),
        ("auxiliary", ("medium", "varnish", "vernis", "thinner", "diluent", "auxiliary", "cleaner")),
        ("technical_effect", ("technical", "special fx", "diorama fx", "weathering", "effect", "effet")),
        ("opaque_standard", ("base", "layer", "model color", "game color", "warpaints fanatic", "classic", "games")),
    ]
    for functional_class, needles in checks:
        if any(needle in key for needle in needles):
            return functional_class
    return "unknown"


def normalize_reference(value: Any) -> str:
    return re.sub(r"\s+", "", plain(value)).upper()


def dedupe_key(brand: Any, range_name: Any, reference: Any, name: Any) -> str:
    ref = normalize_reference(reference)
    if ref:
        return market_paint_deduplication_key(brand, ref)
    return f"{slug(brand)}|{slug(range_name)}|name:{slug(name)}"


def looks_like_vallejo_reference(reference: Any) -> bool:
    ref = normalize_reference(reference)
    return bool(re.match(r"^(70|71|72|73|74|76|77)\.\d{3}$", ref))


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def command_hash_photos(args: argparse.Namespace) -> int:
    directory = Path(args.directory).resolve()
    if not directory.is_dir():
        raise ValueError(f"Répertoire introuvable : {directory}")
    manifest = []
    for path in sorted(directory.rglob("*"), key=lambda item: str(item).lower()):
        if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
            stat = path.stat()
            manifest.append(
                {
                    "path": path.relative_to(directory).as_posix(),
                    "sha256": sha256_file(path),
                    "bytes": stat.st_size,
                    "mtime_utc": datetime.fromtimestamp(stat.st_mtime, timezone.utc).isoformat(),
                }
            )
    write_json(Path(args.output), {"root": str(directory), "photos": manifest})
    print(f"{len(manifest)} photo(s) indexée(s) dans {args.output}")
    return 0


def normalize_candidate(record: dict[str, Any], common: dict[str, Any]) -> dict[str, Any]:
    result = dict(record)
    observed_brand = plain(record.get("brand_observed") or record.get("marque_observee") or record.get("brand") or record.get("marque"))
    observed_range = plain(record.get("range_observed") or record.get("gamme_observee") or record.get("range") or record.get("gamme"))
    observed_name = plain(record.get("name_observed") or record.get("nom"))
    observed_ref = plain(record.get("reference_observed") or record.get("reference"))
    canonical, aliases, manufacturer = canonical_brand(observed_brand)
    range_canonical, range_warnings = canonical_range(observed_range)
    warnings = split_values(record.get("warnings") or record.get("avertissements")) + range_warnings
    confidence = as_float(record.get("confidence", record.get("confiance", 0.0)))
    quantity = max(1, as_int(record.get("quantity", record.get("quantite", 1)), 1))
    functional_class = plain(record.get("functional_class") or record.get("classe_fonctionnelle"))
    if not functional_class:
        functional_class = classify_function(range_canonical, observed_name)
    if canonical == "Prince August" and looks_like_vallejo_reference(observed_ref):
        warnings.append("Référence au format Vallejo sous marque observée Prince August : confirmer le fabricant et la gamme")
        manufacturer = "À confirmer : Prince August ou Acrylicos Vallejo"
    if not canonical:
        warnings.append("Marque absente")
    elif not manufacturer:
        warnings.append("Marque non reconnue par la table d’alias")
    if not observed_name:
        warnings.append("Nom du produit absent")
    if functional_class not in FUNCTIONAL_CLASSES:
        warnings.append(f"Classe fonctionnelle inconnue : {functional_class}")
        functional_class = "unknown"
    elif functional_class == "unknown":
        warnings.append("Fonction non classée")
    warnings = split_values(join_unique(warnings))
    needs_review = bool(warnings) or confidence < 0.90 or plain(record.get("needs_review")).lower() in {"true", "1", "yes", "oui"}
    result.update(
        {
            "brand_observed": observed_brand,
            "brand_canonical": canonical,
            "brand_aliases": split_values(aliases),
            "manufacturer": manufacturer,
            "range_observed": observed_range,
            "range_canonical": range_canonical,
            "functional_class": functional_class,
            "reference": normalize_reference(observed_ref),
            "name": observed_name,
            "quantity": quantity,
            "confidence": round(confidence, 3),
            "warnings": warnings,
            "needs_review": needs_review,
            "status": "a_verifier" if needs_review else "confirme",
            "source_photo": plain(record.get("source_photo") or common.get("source_photo")),
            "source_hash": plain(record.get("source_hash") or common.get("source_hash")),
            "dedupe_key": dedupe_key(canonical, range_canonical, observed_ref, observed_name),
        }
    )
    return result


def command_normalize(args: argparse.Namespace) -> int:
    payload = load_json(Path(args.input))
    if isinstance(payload, list):
        common: dict[str, Any] = {}
        records = payload
    elif isinstance(payload, dict):
        common = payload
        records = payload.get("paints", payload.get("records", []))
    else:
        raise ValueError("Le JSON candidat doit être une liste ou un objet contenant 'paints'.")
    normalized = [normalize_candidate(record, common) for record in records]
    output = {
        "source_photo": plain(common.get("source_photo")),
        "source_hash": plain(common.get("source_hash")),
        "normalized_at": datetime.now(timezone.utc).isoformat(),
        "paints": normalized,
        "summary": {
            "total": len(normalized),
            "confirmed": sum(item["status"] == "confirme" for item in normalized),
            "needs_review": sum(item["needs_review"] for item in normalized),
        },
    }
    write_json(Path(args.output), output)
    print(f"{len(normalized)} peinture(s) normalisée(s), {output['summary']['needs_review']} à vérifier")
    return 0


def command_enrich(args: argparse.Namespace) -> int:
    payload = load_json(Path(args.input))
    catalog = load_json(Path(args.catalog))
    records = payload.get("paints", []) if isinstance(payload, dict) else []
    by_reference = catalog.get("by_reference", {}) if isinstance(catalog, dict) else {}
    if not isinstance(by_reference, dict):
        raise ValueError("Le catalogue d’enrichissement doit contenir un objet 'by_reference'.")
    missing: list[str] = []
    for record in records:
        reference = normalize_reference(record.get("reference"))
        enrichment = by_reference.get(reference)
        if not isinstance(enrichment, dict):
            missing.append(reference or plain(record.get("name")))
            continue
        record["enrichment"] = dict(enrichment)
    if missing:
        raise ValueError(f"Enrichissement absent pour : {', '.join(missing)}")
    payload["enriched_at"] = datetime.now(timezone.utc).isoformat()
    write_json(Path(args.output), payload)
    print(f"{len(records)} peinture(s) enrichie(s), 0 source manquante")
    return 0


def command_trim_images(args: argparse.Namespace) -> int:
    try:
        from PIL import Image
    except ImportError as exc:
        raise ValueError("Pillow est requis pour recadrer les images fabricant.") from exc
    changed = 0
    for raw_path in args.images:
        path = Path(raw_path)
        with Image.open(path) as source:
            image = source.convert("RGBA")
            bbox = image.getchannel("A").getbbox()
            if not bbox:
                continue
            left, top, right, bottom = bbox
            padding = max(0, args.padding)
            padded = (
                max(0, left - padding),
                max(0, top - padding),
                min(image.width, right + padding),
                min(image.height, bottom + padding),
            )
            if padded == (0, 0, image.width, image.height):
                continue
            image.crop(padded).save(path)
            changed += 1
    print(f"{changed} image(s) recadrée(s) sur {len(args.images)}")
    return 0


def command_relocate_source(args: argparse.Namespace) -> int:
    rows = [old_row_to_current(row) for row in read_csv(Path(args.inventory))]
    changed = 0
    for row in rows:
        sources = split_values(row.get("source_photo"))
        relocated = [args.new if source == args.old else source for source in sources]
        updated = join_unique(relocated)
        if updated != row.get("source_photo", ""):
            row["source_photo"] = updated
            changed += 1
    write_csv(Path(args.output_csv), rows)
    write_yaml(Path(args.output_yaml), rows)
    print(f"Source relocalisée dans {changed} fiche(s)")
    return 0


def read_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return [dict(row) for row in csv.DictReader(handle)]


def old_row_to_current(row: dict[str, Any]) -> dict[str, str]:
    observed_brand = plain(row.get("marque_observee") or row.get("marque") or row.get("brand_observed"))
    canonical, aliases, manufacturer = canonical_brand(row.get("marque_canonique") or observed_brand)
    observed_range = plain(row.get("gamme_observee") or row.get("gamme") or row.get("range_observed"))
    range_canonical, _ = canonical_range(row.get("gamme_canonique") or observed_range)
    name = plain(row.get("nom") or row.get("name"))
    reference = normalize_reference(row.get("reference"))
    result = {column: "" for column in CSV_COLUMNS}
    translations = {
        "id": "id", "quantite": "quantite", "confiance": "confiance", "statut": "statut",
        "couleur_hex": "couleur_hex", "famille_couleur": "famille_couleur", "fini": "fini",
        "medium": "medium", "volume_ml": "volume_ml", "tags": "tags",
        "usages_conseilles": "usages_conseilles", "fiche_fabricant": "fiche_fabricant",
        "image_fabricant": "image_fabricant", "source_image": "source_image",
        "credit_image": "credit_image", "verifie_le": "verifie_le", "notes": "notes",
        "source_hashes": "source_hashes", "avertissements": "avertissements",
    }
    for source, target in translations.items():
        result[target] = plain(row.get(source))
    result.update(
        {
            "marque_observee": observed_brand,
            "marque_canonique": canonical,
            "alias_marque": plain(row.get("alias_marque")) or aliases,
            "fabricant": plain(row.get("fabricant")) or manufacturer,
            "gamme_observee": observed_range,
            "gamme_canonique": range_canonical,
            "classe_fonctionnelle": plain(row.get("classe_fonctionnelle")) or classify_function(range_canonical, name),
            "reference": reference,
            "nom": name,
            "quantite": plain(row.get("quantite")) or "1",
            "statut": plain(row.get("statut")) or "confirme",
            "source_photo": plain(row.get("source_photo") or row.get("source_inventaire")),
            "cle_dedoublonnage": plain(row.get("cle_dedoublonnage")) or dedupe_key(canonical, range_canonical, reference, name),
        }
    )
    return result


def candidate_to_row(record: dict[str, Any]) -> dict[str, str]:
    extras = record.get("enrichment", {}) if isinstance(record.get("enrichment"), dict) else {}
    get = lambda *keys: next((record[key] for key in keys if plain(record.get(key))), next((extras[key] for key in keys if plain(extras.get(key))), ""))
    name = plain(record.get("name") or record.get("name_observed"))
    brand = plain(record.get("brand_canonical"))
    range_name = plain(record.get("range_canonical"))
    reference = normalize_reference(record.get("reference"))
    identifier = plain(record.get("id")) or (
        market_paint_id(brand, reference) if reference else slug(f"{brand}-{range_name}-{name}")
    )
    return {
        "id": identifier,
        "marque_observee": plain(record.get("brand_observed")),
        "marque_canonique": brand,
        "alias_marque": join_unique(record.get("brand_aliases", [])),
        "fabricant": plain(record.get("manufacturer")),
        "gamme_observee": plain(record.get("range_observed")),
        "gamme_canonique": range_name,
        "classe_fonctionnelle": plain(record.get("functional_class")),
        "reference": reference,
        "nom": name,
        "quantite": str(as_int(record.get("quantity"), 1)),
        "confiance": plain(record.get("confidence")),
        "statut": plain(record.get("status")),
        "avertissements": join_unique(record.get("warnings", [])),
        "source_photo": plain(record.get("source_photo")),
        "source_hashes": plain(record.get("source_hash")),
        "couleur_hex": plain(get("color_hex", "couleur_hex")),
        "famille_couleur": plain(get("color_family", "famille_couleur")),
        "fini": plain(get("finish", "fini")),
        "medium": plain(get("medium")),
        "volume_ml": plain(get("volume_ml")),
        "tags": join_unique(get("tags")),
        "usages_conseilles": join_unique(get("recommended_uses", "usages_conseilles")),
        "fiche_fabricant": plain(get("manufacturer_url", "fiche_fabricant")),
        "image_fabricant": plain(get("local_image", "image_fabricant")),
        "source_image": plain(get("image_source_url", "source_image")),
        "credit_image": plain(get("image_credit", "credit_image")),
        "verifie_le": plain(get("verified_on", "verifie_le")),
        "notes": plain(get("notes")),
        "cle_dedoublonnage": plain(record.get("dedupe_key")) or dedupe_key(brand, range_name, record.get("reference"), name),
    }


def merge_rows(existing: dict[str, str], incoming: dict[str, str]) -> tuple[dict[str, str], bool]:
    incoming_hash = incoming["source_hashes"]
    if incoming_hash and incoming_hash in split_values(existing.get("source_hashes")):
        return existing, False
    merged = dict(existing)
    merged["quantite"] = str(max(0, as_int(existing.get("quantite"))) + max(0, as_int(incoming.get("quantite"))))
    merged["source_hashes"] = join_unique([existing.get("source_hashes"), incoming_hash])
    merged["source_photo"] = join_unique([existing.get("source_photo"), incoming.get("source_photo")])
    merged["avertissements"] = join_unique([existing.get("avertissements"), incoming.get("avertissements")])
    for column in CSV_COLUMNS:
        if column not in {"quantite", "source_hashes", "source_photo", "avertissements"} and not plain(merged.get(column)):
            merged[column] = incoming.get(column, "")
    return merged, True


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def yaml_scalar(value: Any) -> str:
    return json.dumps(plain(value), ensure_ascii=False)


def write_yaml(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("peintures:\n")
        for row in rows:
            handle.write(f"  - id: {yaml_scalar(row['id'])}\n")
            for column in CSV_COLUMNS[1:]:
                handle.write(f"    {column}: {yaml_scalar(row.get(column, ''))}\n")


def command_merge(args: argparse.Namespace) -> int:
    payload = load_json(Path(args.input))
    records = payload.get("paints", payload) if isinstance(payload, dict) else payload
    rows = [old_row_to_current(row) for row in read_csv(Path(args.inventory))]
    by_key = {row["cle_dedoublonnage"]: index for index, row in enumerate(rows)}
    report: dict[str, Any] = {"added": 0, "merged": 0, "skipped_review": 0, "skipped_photo_hash": 0, "warnings": []}
    for record in records:
        if plain(record.get("status")) != "confirme" and not args.include_review:
            report["skipped_review"] += 1
            report["warnings"].append(f"À vérifier : {plain(record.get('name') or record.get('name_observed'))}")
            continue
        incoming = candidate_to_row(record)
        key = incoming["cle_dedoublonnage"]
        if key in by_key:
            merged, changed = merge_rows(rows[by_key[key]], incoming)
            rows[by_key[key]] = merged
            report["merged" if changed else "skipped_photo_hash"] += 1
        else:
            by_key[key] = len(rows)
            rows.append(incoming)
            report["added"] += 1
    rows.sort(key=lambda row: (folded(row["marque_canonique"]), folded(row["gamme_canonique"]), folded(row["nom"])))
    write_csv(Path(args.output_csv), rows)
    write_yaml(Path(args.output_yaml), rows)
    report["total_after_merge"] = len(rows)
    write_json(Path(args.report), report)
    print(f"Fusion : {report['added']} ajout(s), {report['merged']} doublon(s) fusionné(s), {report['skipped_review']} à vérifier")
    return 0


def command_validate(args: argparse.Namespace) -> int:
    rows = [old_row_to_current(row) for row in read_csv(Path(args.inventory))]
    errors: list[str] = []
    warnings: list[str] = []
    seen: dict[str, int] = {}
    for line, row in enumerate(rows, start=2):
        label = row["nom"] or f"ligne {line}"
        if not row["marque_canonique"]:
            errors.append(f"{label}: marque canonique absente")
        if not row["nom"]:
            errors.append(f"ligne {line}: nom absent")
        if as_int(row["quantite"]) <= 0:
            errors.append(f"{label}: quantité non positive")
        if row["classe_fonctionnelle"] not in FUNCTIONAL_CLASSES:
            errors.append(f"{label}: classe fonctionnelle invalide ({row['classe_fonctionnelle']})")
        if row["classe_fonctionnelle"] == "unknown":
            warnings.append(f"{label}: fonction non classée")
        key = row["cle_dedoublonnage"]
        if key in seen:
            errors.append(f"{label}: doublon avec la ligne {seen[key]} ({key})")
        else:
            seen[key] = line
    for message in warnings:
        print(f"AVERTISSEMENT: {message}")
    for message in errors:
        print(f"ERREUR: {message}", file=sys.stderr)
    print(f"Validation : {len(rows)} fiche(s), {len(errors)} erreur(s), {len(warnings)} avertissement(s)")
    return 1 if errors else 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    hash_parser = sub.add_parser("hash-photos", help="Calculer les empreintes SHA-256 des photos")
    hash_parser.add_argument("directory")
    hash_parser.add_argument("--output", required=True)
    hash_parser.set_defaults(func=command_hash_photos)

    normalize_parser = sub.add_parser("normalize", help="Normaliser un fichier candidat JSON")
    normalize_parser.add_argument("input")
    normalize_parser.add_argument("--output", required=True)
    normalize_parser.set_defaults(func=command_normalize)

    enrich_parser = sub.add_parser("enrich", help="Appliquer un catalogue d’enrichissement vérifié par référence")
    enrich_parser.add_argument("input")
    enrich_parser.add_argument("--catalog", required=True)
    enrich_parser.add_argument("--output", required=True)
    enrich_parser.set_defaults(func=command_enrich)

    trim_parser = sub.add_parser("trim-images", help="Recadrer les marges transparentes de packshots PNG")
    trim_parser.add_argument("images", nargs="+")
    trim_parser.add_argument("--padding", type=int, default=24)
    trim_parser.set_defaults(func=command_trim_images)

    relocate_parser = sub.add_parser("relocate-source", help="Mettre à jour le chemin d’une photo archivée sans modifier les quantités")
    relocate_parser.add_argument("--inventory", required=True)
    relocate_parser.add_argument("--old", required=True)
    relocate_parser.add_argument("--new", required=True)
    relocate_parser.add_argument("--output-csv", required=True)
    relocate_parser.add_argument("--output-yaml", required=True)
    relocate_parser.set_defaults(func=command_relocate_source)

    merge_parser = sub.add_parser("merge", help="Fusionner les fiches confirmées au référentiel")
    merge_parser.add_argument("input")
    merge_parser.add_argument("--inventory", required=True)
    merge_parser.add_argument("--output-csv", required=True)
    merge_parser.add_argument("--output-yaml", required=True)
    merge_parser.add_argument("--report", required=True)
    merge_parser.add_argument("--include-review", action="store_true", help="Inclure aussi les fiches à vérifier")
    merge_parser.set_defaults(func=command_merge)

    validate_parser = sub.add_parser("validate", help="Valider un référentiel CSV")
    validate_parser.add_argument("--inventory", required=True)
    validate_parser.set_defaults(func=command_validate)
    return parser


def main(argv: list[str] | None = None) -> int:
    try:
        args = build_parser().parse_args(argv)
        return int(args.func(args))
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ERREUR: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
