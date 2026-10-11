#!/usr/bin/env python3
"""Offline validation of the published audit snapshot; never marks it app-ready."""
import hashlib
import json
from pathlib import Path

from build_wetherspoons_catalogue import NAME, KEY, validate_geometry


def validate(root):
    catalogue = json.loads((root / "uk-wetherspoons-candidates-v1.json").read_text())
    audit = json.loads((root / "uk-wetherspoons-audit-v1.json").read_text())
    source = root / "wetherspoons-osm-source-v1.json"
    assert hashlib.sha256(source.read_bytes()).hexdigest() == catalogue["source"]["sha256"]
    assert catalogue["display_name"] == audit["display_name"] == NAME
    assert catalogue["collection_id"] == KEY and catalogue["scope"] == "UK"
    assert catalogue["release_status"] == "audit_only" and catalogue["ready_for_app"] is False
    assert catalogue["completion_target"] is None and audit["ready_for_app"] is False
    assert catalogue["source"]["licence"] == "ODbL-1.0"
    registry = json.loads((root / "wetherspoons-identities-v1.json").read_text())
    assert hashlib.sha256(json.dumps(registry, sort_keys=True).encode()).hexdigest() == catalogue["identity_registry_sha256"]
    assert catalogue["catalogue_version"] == audit["catalogue_version"]
    ids, aliases = set(), set()
    candidates = []
    for venue in catalogue["venues"]:
        assert venue["id"] not in ids
        ids.add(venue["id"])
        assert venue["operating_status"] == "unverified"
        if venue["status"] == "candidate":
            candidates.append(venue)
        assert set(venue["osm_aliases"]) == {s["osm_id"] for s in venue["sources"]}
        for entry in venue["sources"]:
            assert entry["osm_id"] not in aliases
            aliases.add(entry["osm_id"])
            assert validate_geometry(entry["geometry"]) == entry["geometry_quality"]
    assert len(candidates) == audit["candidate_venues"]
    assert audit["point_only"] + audit["mapped_footprints"] == len(candidates)
    assert audit["county_unassigned"] == [v["id"] for v in candidates if not v["county"]]
    assert len(json.loads(source.read_text())["features"]) == audit["raw_objects"]
    print(f"Validated {len(candidates)} candidates; catalogue remains audit-only")


if __name__ == "__main__":
    validate(Path(__file__).resolve().parent.parent / "collections")
