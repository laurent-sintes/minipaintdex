"""Import-evidence preservation; never used as a search or domain taxonomy."""

from copy import deepcopy


def reviewed_path(field):
    return {"family": "color.family", "effects": "profile.effects"}.get(field, field)


def protected_paths(paint):
    paths = set()
    if (paint.get("color") or {}).get("hex"):
        paths.add("color.hex")
    for snapshot in paint.get("source_snapshots", []):
        if snapshot.get("provider") == "reviewed-paint-color-quality":
            path = reviewed_path(snapshot.get("payload", {}).get("field", ""))
            if path.startswith(("color.", "profile.")) and len(path.split(".")) == 2:
                paths.add(path)
    return paths


def preserve_qualified_data(incoming, previous):
    """Automatic collectors retain existing HEX and reviewed fields, plus every source.

    Conflicting observations remain in their source snapshots for explicit review.
    Only the separate reviewed-correction workflow may override protected values.
    """
    result = deepcopy(incoming)
    for path in sorted(protected_paths(previous)):
        section, field = path.split(".")
        if field in previous.get(section, {}):
            result.setdefault(section, {})[field] = deepcopy(previous[section][field])
    snapshots = deepcopy(previous.get("source_snapshots", []))
    for snapshot in result.get("source_snapshots", []):
        if snapshot not in snapshots:
            snapshots.append(snapshot)
    if snapshots:
        result["source_snapshots"] = snapshots
    return result
