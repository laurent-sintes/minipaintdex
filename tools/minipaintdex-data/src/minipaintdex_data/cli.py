"""Unified command-line entry point for deterministic MiniPaintDex data work."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import paint_import
from .assets import audit_assets
from .changesets import build_paint_changeset, load_json, validate_changeset, write_json
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
    refresh.add_argument("--catalog", default="data/market/paints/catalog.yaml")
    refresh.add_argument("--brand", required=True, help="Canonical brand name or 'all'")
    refresh.add_argument("--verified-at")
    refresh.add_argument("--remove-missing", action="store_true", help="Propose explicit deletions instead of retirement")
    refresh.add_argument("--output", required=True)

    assets = subcommands.add_parser("assets", help="Audit local public media")
    assets_commands = assets.add_subparsers(dest="assets_command", required=True)
    audit = assets_commands.add_parser("audit", help="Report missing and orphaned public media")
    audit.add_argument("--root", default=".")
    audit.add_argument("--output")
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
            print(f"Refresh change set written to {args.output} ({len(changeset['operations'])} operation(s)).")
            for warning in changeset["refresh"]["warnings"]:
                print(f"WARNING {warning}")
            return 0
        if args.command == "assets" and args.assets_command == "audit":
            _write_result(audit_assets(Path(args.root)), args.output)
            return 0
        return 2
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
