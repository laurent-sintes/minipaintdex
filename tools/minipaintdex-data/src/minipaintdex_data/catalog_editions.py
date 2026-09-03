"""Validate sourced commercial editions independently of scrape timestamps."""
import re
from urllib.parse import urlsplit

ID = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")


def https_url(value):
    return isinstance(value, str) and urlsplit(value).scheme == "https" and bool(urlsplit(value).hostname)


def validate_editions(editions):
    errors, seen = [], set()
    if not isinstance(editions, list):
        return ["catalog_editions must be a list"]
    for edition in editions:
        if not isinstance(edition, dict):
            errors.append("catalog edition must be an object")
            continue
        identifier = edition.get("id", "")
        if not isinstance(identifier, str) or not ID.fullmatch(identifier) or identifier in seen:
            errors.append("catalog edition id must be unique lowercase kebab-case")
        else:
            seen.add(identifier)
        if edition.get("schema_version") != 1:
            errors.append("catalog edition schema_version must be 1")
        for field in ("brand", "title", "edition_label"):
            if not isinstance(edition.get(field), str) or not edition[field].strip():
                errors.append(f"catalog edition {field} is required")
        year = edition.get("publication_year")
        if year is not None and (type(year) is not int or not 1 <= year <= 9999):
            errors.append("catalog edition publication_year is invalid")
        for field in ("ranges", "source_urls"):
            values = edition.get(field)
            if not isinstance(values, list) or not values or any(not isinstance(v, str) or not v.strip() for v in values):
                errors.append(f"catalog edition {field} must be a nonempty string list")
            elif len(values) != len(set(values)):
                errors.append(f"catalog edition {field} must be unique")
            elif field == "source_urls" and not all(https_url(v) for v in values):
                errors.append("catalog edition sources must use absolute HTTPS URLs")
    return errors


def validate_memberships(memberships):
    errors, seen = [], set()
    if not isinstance(memberships, list):
        return ["catalog_memberships must be a list"]
    for membership in memberships:
        if not isinstance(membership, dict):
            errors.append("catalog membership must be an object")
            continue
        identifier = membership.get("catalog_edition_id", "")
        if not isinstance(identifier, str) or not ID.fullmatch(identifier) or identifier in seen:
            errors.append("catalog membership edition id must be valid and unique")
        else:
            seen.add(identifier)
        if not https_url(membership.get("source_url")):
            errors.append("catalog membership source_url must use absolute HTTPS")
        if not isinstance(membership.get("locator"), str) or not membership["locator"].strip():
            errors.append("catalog membership locator is required")
    return errors
