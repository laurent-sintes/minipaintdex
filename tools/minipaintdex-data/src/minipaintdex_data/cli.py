"""Unified command-line entry point for deterministic MiniPaintDex data work."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import paint_import
from .assets import audit_assets
from .changesets import (
    build_paint_changeset,
    build_paint_model_migration_changeset,
    load_json,
    validate_changeset,
    write_json,
)
from .datasets import CATEGORY_PATHS, create_dataset, inspect_dataset, validate_dataset
from .official_refresh import collect_official_refresh
from .paint_images import build_image_cache_changeset, build_image_source_changeset
from .refresh import build_refresh_changeset, read_catalog


def _write_result(value: object, output: str | None) -> None:
    if output:
        write_json(Path(output), value)
    else:
        print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)

    changeset = subcommands.add_parser("changeset", help="Build or validate an application change set")
    changeset_commands = changeset.add_subparsers(dest="changeset_command", required=True)
    build_paints = changeset_commands.add_parser("build-paints", help="Build a canonical market-paint change set")
    build_paints.add_argument("input")
    build_paints.add_argument("--source")
    build_paints.add_argument("--verified-at")
    build_paints.add_argument("--no-workshop", action="store_true", help="Do not add imported quantities to the owned inventory")
    build_paints.add_argument("--output", required=True)
    validate = changeset_commands.add_parser("validate", help="Validate a change set without applying it")
    validate.add_argument("input")
    validate.add_argument("--format", choices=("human", "json"), default="human")
    refresh = changeset_commands.add_parser("refresh-paints", help="Compare a complete manufacturer refresh with the local catalog")
    refresh.add_argument("input", help="Verified refreshed JSON containing coverage and paints")
    refresh.add_argument("--catalog", default="data/market/paints")
    refresh.add_argument("--brand", required=True, help="Canonical brand name or 'all'")
    refresh.add_argument("--verified-at")
    refresh.add_argument("--remove-missing", action="store_true", help="Propose explicit deletions instead of retirement")
    refresh.add_argument("--audit-log", help="Write the structured refresh audit as JSON")
    refresh.add_argument("--output", required=True)
    migrate = changeset_commands.add_parser("migrate-paint-model", help="Build a lossless canonical paint-model migration")
    migrate.add_argument("--catalog", default="data/market/paints")
    migrate.add_argument("--retain-legacy-fields", action="store_true")
    migrate.add_argument("--output", required=True)

    catalog = subcommands.add_parser("catalog", help="Collect verified manufacturer catalogue data")
    catalog_commands = catalog.add_subparsers(dest="catalog_command", required=True)
    collect = catalog_commands.add_parser("collect-official-paints", help="Collect one or every registered official paint catalogue")
    collect.add_argument("--catalog", default="data/market/paints")
    collect.add_argument("--vallejo-pdf", required=True, help="Downloaded official Vallejo catalogue PDF")
    collect.add_argument("--verified-at")
    collect.add_argument("--brand", action="append", default=[], help="Canonical brand name; repeat it or use 'all' (default)")
    collect.add_argument("--audit-log", help="Write the structured collection audit as JSON")
    collect.add_argument("--output", required=True)

    assets = subcommands.add_parser("assets", help="Audit local public media")
    assets_commands = assets.add_subparsers(dest="assets_command", required=True)
    audit = assets_commands.add_parser("audit", help="Report missing and orphaned public media")
    audit.add_argument("--root", default=".")
    audit.add_argument("--min-width", type=int, default=300)
    audit.add_argument("--min-height", type=int, default=300)
    audit.add_argument("--output")
    cache_images = assets_commands.add_parser(
        "cache-paint-images", help="Download and validate official paint packshots into the local media cache"
    )
    cache_images.add_argument("--catalog", default="data/market/paints")
    cache_images.add_argument("--media-root", default="media")
    cache_images.add_argument("--brand", action="append", default=[], help="Canonical brand; repeat or use 'all'")
    cache_images.add_argument("--min-width", type=int, default=300)
    cache_images.add_argument("--min-height", type=int, default=300)
    cache_images.add_argument("--max-edge", type=int, default=800)
    cache_images.add_argument("--max-bytes", type=int, default=10 * 1024 * 1024)
    cache_images.add_argument("--workers", type=int, default=4)
    cache_images.add_argument("--limit", type=int, default=0, help="Maximum records to inspect; zero means no limit")
    cache_images.add_argument("--overwrite", action="store_true")
    cache_images.add_argument("--allow-partial", action="store_true", help="Return success even when some downloads fail")
    cache_images.add_argument("--verified-at")
    cache_images.add_argument("--audit-log", required=True)
    cache_images.add_argument("--output", required=True, help="Market-paint change set to validate and apply with the Java CLI")
    import_image_sources = assets_commands.add_parser(
        "import-paint-image-sources", help="Validate an official image manifest and build a market-paint change set"
    )
    import_image_sources.add_argument("--catalog", default="data/market/paints")
    import_image_sources.add_argument("--manifest", required=True)
    import_image_sources.add_argument(
        "--allow-unmatched", action="store_true",
        help="Report official references absent from the catalog instead of rejecting the whole manifest",
    )
    import_image_sources.add_argument("--verified-at")
    import_image_sources.add_argument("--output", required=True)

    dataset = subcommands.add_parser("dataset", help="Create or validate portable application datasets")
    dataset_commands = dataset.add_subparsers(dest="dataset_command", required=True)
    create = dataset_commands.add_parser("create", help="Create a named dataset from application references")
    create.add_argument("--root", default=".", help="MiniPaintDex application root")
    create.add_argument("--datasets-root", default="datasets")
    create.add_argument("--category", required=True, choices=tuple(CATEGORY_PATHS))
    create.add_argument("--name", required=True)
    create.add_argument("--brand")
    create.add_argument("--product")
    create.add_argument("--project-id")
    create.add_argument("--project-name")
    create.add_argument("--replace", action="store_true")
    validate_dataset_parser = dataset_commands.add_parser("validate", help="Validate manifest, payload and checksum")
    validate_dataset_parser.add_argument("input")
    validate_dataset_parser.add_argument("--format", choices=("human", "json"), default="human")
    inspect = dataset_commands.add_parser("inspect", help="Display a dataset manifest and validation result")
    inspect.add_argument("input")
    return parser


def main(argv: list[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    if arguments and arguments[0] == "paint":
        return paint_import.main(arguments[1:])
    try:
        args = build_parser().parse_args(arguments)
        if args.command == "changeset" and args.changeset_command == "build-paints":
            payload = load_json(Path(args.input))
            changeset = build_paint_changeset(
                payload,
                source=args.source or Path(args.input).as_posix(),
                verified_at=args.verified_at,
                include_workshop=not args.no_workshop,
            )
            write_json(Path(args.output), changeset)
            print(f"Change set written to {args.output} ({len(changeset['operations'])} operation(s)).")
            return 0
        if args.command == "changeset" and args.changeset_command == "validate":
            payload = load_json(Path(args.input))
            is_refresh = isinstance(payload, dict) and isinstance(payload.get("refresh"), dict)
            errors = validate_changeset(payload, allow_empty=is_refresh)
            if args.format == "json":
                print(json.dumps({"valid": not errors, "errors": errors}, ensure_ascii=False, sort_keys=True))
            else:
                print("Valid change set." if not errors else "\n".join(f"ERROR {error}" for error in errors))
            return 1 if errors else 0
        if args.command == "changeset" and args.changeset_command == "refresh-paints":
            changeset = build_refresh_changeset(
                read_catalog(Path(args.catalog)),
                load_json(Path(args.input)),
                brand=args.brand,
                verified_at=args.verified_at,
                remove_missing=args.remove_missing,
            )
            write_json(Path(args.output), changeset)
            if args.audit_log:
                write_json(Path(args.audit_log), {
                    "schema_version": 1,
                    "kind": "paint_catalog_refresh_audit",
                    "generated_at": changeset["refresh"]["verified_at"],
                    **changeset["refresh"]["audit"],
                    "warnings": changeset["refresh"]["warnings"],
                })
            print(f"Refresh change set written to {args.output} ({len(changeset['operations'])} operation(s)).")
            audit = changeset["refresh"]["audit"]
            print(
                f"AUDIT existing={audit['existing_count']} incoming={audit['incoming_count']} "
                f"operations={audit['operation_count']}"
            )
            for name, count in audit["changed_top_level_fields"].items():
                print(f"FIELD name={name} changes={count}")
            for warning in changeset["refresh"]["warnings"]:
                print(f"WARNING {warning}")
            return 0
        if args.command == "changeset" and args.changeset_command == "migrate-paint-model":
            changeset = build_paint_model_migration_changeset(
                read_catalog(Path(args.catalog)), source=Path(args.catalog).as_posix(),
                retain_legacy_fields=args.retain_legacy_fields,
            )
            write_json(Path(args.output), changeset)
            print(f"Migration change set written to {args.output} ({len(changeset['operations'])} operation(s)).")
            return 0
        if args.command == "catalog" and args.catalog_command == "collect-official-paints":
            payload = collect_official_refresh(
                Path(args.catalog), Path(args.vallejo_pdf), verified_at=args.verified_at,
                brands=args.brand or ["all"],
            )
            write_json(Path(args.output), payload)
            if args.audit_log:
                write_json(Path(args.audit_log), {
                    "schema_version": 1,
                    "kind": "paint_catalog_collection_audit",
                    "generated_at": payload["source"]["generated_at"],
                    "providers": payload["audit"],
                    "source": payload["source"],
                })
            print(f"Verified catalogue written to {args.output} ({len(payload['paints'])} paint(s)).")
            for provider in payload["audit"]:
                images = provider["images"]
                print(
                    f"PROVIDER brand={provider['brand']} known={provider['known_count']} "
                    f"collected={provider['collected_count']} mode={provider['provider_mode']} "
                    f"coverage_complete={str(provider['coverage_complete']).lower()} "
                    f"source_snapshots={provider['source_snapshots']} "
                    f"images_local={images['local']} images_remote_only={images['remote_only']} "
                    f"images_missing={images['missing']}"
                )
            return 0
        if args.command == "assets" and args.assets_command == "audit":
            if args.min_width <= 0 or args.min_height <= 0:
                raise ValueError("Minimum image dimensions must be positive.")
            _write_result(
                audit_assets(Path(args.root), min_width=args.min_width, min_height=args.min_height),
                args.output,
            )
            return 0
        if args.command == "assets" and args.assets_command == "cache-paint-images":
            changeset, report = build_image_cache_changeset(
                read_catalog(Path(args.catalog)), Path(args.media_root),
                brands=args.brand or ["all"], min_width=args.min_width, min_height=args.min_height,
                max_edge=args.max_edge, max_bytes=args.max_bytes, overwrite=args.overwrite,
                limit=args.limit, workers=args.workers, verified_at=args.verified_at,
            )
            write_json(Path(args.output), changeset)
            write_json(Path(args.audit_log), report)
            counts = report["counts"]
            print(
                f"Paint image cache wrote {len(changeset['operations'])} operation(s): "
                + " ".join(f"{name}={count}" for name, count in counts.items())
            )
            if counts.get("failed", 0) and not args.allow_partial:
                print("ERROR: Some images failed; inspect the audit log before applying the change set.", file=sys.stderr)
                return 2
            return 0
        if args.command == "assets" and args.assets_command == "import-paint-image-sources":
            changeset = build_image_source_changeset(
                read_catalog(Path(args.catalog)), load_json(Path(args.manifest)), verified_at=args.verified_at,
                allow_unmatched=args.allow_unmatched,
            )
            write_json(Path(args.output), changeset)
            unmatched = changeset["source"]["unmatched_references"]
            print(
                f"Paint image source change set written to {args.output} "
                f"({len(changeset['operations'])} operation(s), {len(unmatched)} unmatched reference(s))."
            )
            return 0
        if args.command == "dataset" and args.dataset_command == "create":
            target = create_dataset(
                Path(args.root), Path(args.datasets_root), args.category, args.name,
                brand=args.brand, product_id=args.product, project_id=args.project_id,
                project_name=args.project_name, replace=args.replace,
            )
            print(target.as_posix())
            return 0
        if args.command == "dataset" and args.dataset_command == "validate":
            errors = validate_dataset(Path(args.input))
            if args.format == "json":
                print(json.dumps({"valid": not errors, "errors": errors}, ensure_ascii=False, sort_keys=True))
            else:
                print("Valid dataset." if not errors else "\n".join(f"ERROR {error}" for error in errors))
            return 1 if errors else 0
        if args.command == "dataset" and args.dataset_command == "inspect":
            _write_result(inspect_dataset(Path(args.input)), None)
            return 0
        return 2
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
