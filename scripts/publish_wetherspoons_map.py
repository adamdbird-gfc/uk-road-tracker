#!/usr/bin/env python3
"""Publish an explicitly approved map release; never activate candidate data."""
import argparse
import hashlib
import json
import re
from datetime import date
from pathlib import Path

MAX_BYTES = 2 * 1024 * 1024


def validate(data):
    required = {"schema_version": 1, "collection_id": "wetherspoons", "scope": "UK",
                "release_status": "approved", "approval_scope": "map_only_partial_beta",
                "ready_for_app": True, "coverage_complete": False, "visit_enabled": False,
                "completion_target": None}
    if any(k not in data or data[k] != value or type(data[k]) is not type(value)
           for k, value in required.items()):
        raise ValueError("Only reviewed map releases can be published")
    if not data.get("catalogue_version") or not data.get("attribution"):
        raise ValueError("Version and attribution are required")
    venues = data["venues"]
    if len(venues) > 5000 or data["review_summary"]["map_venues"] != len(venues):
        raise ValueError("Review count differs from map")
    ids, aliases = set(), set()
    for row in venues:
        if (not re.fullmatch(r"spoons-(osm-[nwr][0-9]+|curated-[a-z0-9-]+)", row["id"])
                or len(row["id"]) > 200 or row["id"] in ids or row["status"] != "active"
                or row["operating_status"] != "reviewed_current"
                or row.get("visit_enabled") is not False or not row.get("map_reviewed_at")
                or not row.get("name") or not isinstance(row.get("town"), str)
                or len(row["name"]) > 200 or len(row["town"]) > 200
                or date.fromisoformat(row["map_reviewed_at"]) > date.today()
                or not 49 <= row["lat"] <= 61 or not -9 <= row["lon"] <= 2):
            raise ValueError("Unreviewed or duplicate pub")
        ids.add(row["id"])
        if row["id"].startswith("spoons-curated-") and (
                row.get("confirmation_required") is not True
                or not row.get("location_label") or not row.get("attribution")):
            raise ValueError("Curated location labels are required")
        for alias in row["osm_aliases"]:
            if not re.fullmatch(r"(node|way|relation)/[0-9]+", alias) or alias in aliases:
                raise ValueError("Duplicate source identity")
            aliases.add(alias)


def publish(catalogue, revision, directory):
    if not 2026101201 <= revision <= 9999999999:
        raise ValueError("A monotonic ten-digit release revision is required")
    payload = Path(catalogue).read_bytes()
    if not 2 <= len(payload) <= MAX_BYTES:
        raise ValueError("Catalogue exceeds app limit")
    data = json.loads(payload)
    validate(data)
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    filename = f"catalogue-{revision}.json"
    target = directory / filename
    manifest_path = directory / "manifest-v1.json"
    manifest = {"schema_version": 1, "collection_id": "wetherspoons", "revision": revision,
                "catalogue_version": data["catalogue_version"], "filename": filename,
                "bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}
    if target.exists() and target.read_bytes() != payload:
        raise ValueError("Published catalogue files are immutable")
    if manifest_path.exists():
        previous = json.loads(manifest_path.read_text())
        if previous["revision"] > revision or (previous["revision"] == revision and previous != manifest):
            raise ValueError("Publication cannot downgrade or reuse a revision")
    # Commit both files together after running CI. Readers verify the payload hash.
    target.write_bytes(payload)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


def verify(directory):
    directory = Path(directory)
    manifest = json.loads((directory / "manifest-v1.json").read_text())
    expected = f"catalogue-{manifest['revision']}.json"
    if (manifest["schema_version"] != 1 or manifest["collection_id"] != "wetherspoons"
            or not 2026101201 <= manifest["revision"] <= 9999999999
            or not 2 <= manifest["bytes"] <= MAX_BYTES
            or not re.fullmatch(r"[0-9a-f]{64}", manifest["sha256"])
            or manifest["filename"] != expected):
        raise ValueError("Unexpected publication path")
    payload = (directory / expected).read_bytes()
    if len(payload) != manifest["bytes"] or hashlib.sha256(payload).hexdigest() != manifest["sha256"]:
        raise ValueError("Published data does not match manifest")
    data = json.loads(payload)
    validate(data)
    if manifest["catalogue_version"] != data["catalogue_version"]:
        raise ValueError("Published catalogue version differs")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--catalogue")
    parser.add_argument("--revision", type=int)
    parser.add_argument("--directory", required=True)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if args.verify:
        print(json.dumps(verify(args.directory)))
    else:
        if args.catalogue is None or args.revision is None:
            parser.error("--catalogue and --revision are required to publish")
        print(json.dumps(publish(args.catalogue, args.revision, args.directory)))
