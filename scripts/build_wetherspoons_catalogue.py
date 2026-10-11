#!/usr/bin/env python3
"""Build a review-only UK pub catalogue from public OSM data, never journeys.

No third-party Python dependencies. County data are the exact versioned
reference already packaged in a Roadprints APK. This is not an app asset.
"""
import argparse
from datetime import datetime
import gzip
import hashlib
import json
import math
from pathlib import Path
import subprocess
import urllib.parse
import zipfile

NAME = "Wetherspoons Collection"
KEY = "wetherspoons"
PROVIDER = "https://postpass.geofabrik.de/api/interpreter"
MAX_OBJECTS = 2000
MAX_BYTES = 8_000_000
TAGS = ("name", "brand", "brand:wikidata", "operator", "amenity", "tourism",
        "addr:city", "addr:town", "addr:postcode", "addr:street", "addr:housenumber",
        "website", "disused", "abandoned", "closed", "disused:amenity",
        "abandoned:amenity", "demolished:amenity", "was:amenity", "old_name")


def query():
    keys = ",".join("'" + tag + "'" for tag in TAGS)
    return (
        "SELECT p.osm_type,p.osm_id,ST_X(ST_PointOnSurface(p.geom)) AS lon,"
        "ST_Y(ST_PointOnSurface(p.geom)) AS lat,"
        f"(SELECT jsonb_object_agg(key,value) FROM jsonb_each(p.tags) WHERE key IN ({keys})) AS tags,"
        "p.geom FROM postpass_pointpolygon p WHERE "
        "(p.tags @> '{\"brand:wikidata\":\"Q6109362\"}' OR "
        "(p.tags @> '{\"amenity\":\"pub\"}' AND "
        "(p.tags->>'brand' ILIKE '%wetherspoon%' OR "
        "p.tags->>'operator' ILIKE '%wetherspoon%' OR p.tags->>'name' ILIKE '%wetherspoon%'))) "
        "AND EXISTS (SELECT 1 FROM postpass_polygon c WHERE "
        "c.tags @> '{\"ISO3166-1\":\"GB\",\"admin_level\":\"2\",\"boundary\":\"administrative\"}' "
        "AND ST_Covers(c.geom,ST_PointOnSurface(p.geom))) "
        "ORDER BY p.osm_type,p.osm_id LIMIT 2001"
    )


def fetch(target):
    url = PROVIDER + "?" + urllib.parse.urlencode({"data": query(), "cache_for": "86400"})
    # One bounded request. No automatic retry/fallback fan-out or user locations.
    subprocess.run(["curl", "--fail", "--silent", "--show-error", "--max-time", "55",
                    "--max-filesize", str(MAX_BYTES), url, "-o", str(target)], check=True)


def load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, document):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def point_valid(x, y):
    return (isinstance(x, (float, int)) and not isinstance(x, bool)
            and isinstance(y, (float, int)) and not isinstance(y, bool)
            and math.isfinite(x) and math.isfinite(y) and -180 <= x <= 180 and -90 <= y <= 90)


def validate_geometry(geometry):
    kind = geometry.get("type")
    coordinates = geometry.get("coordinates")
    if kind == "Point":
        if not isinstance(coordinates, list) or len(coordinates) != 2 or not point_valid(*coordinates):
            raise ValueError("Invalid venue point")
        return "point_only"
    if kind not in ("Polygon", "MultiPolygon"):
        raise ValueError("Unsupported venue geometry")
    polygons = [coordinates] if kind == "Polygon" else coordinates
    if not isinstance(polygons, list) or not polygons:
        raise ValueError("Empty venue geometry")
    for polygon in polygons:
        if not isinstance(polygon, list) or not polygon:
            raise ValueError("Empty venue polygon")
        for ring in polygon:
            if (not isinstance(ring, list) or len(ring) < 4 or ring[0] != ring[-1]
                    or any(not isinstance(p, list) or len(p) != 2 or not point_valid(*p) for p in ring)):
                raise ValueError("Invalid venue ring")
    return "mapped_footprint_unverified"


def uint(data, offset):
    value = 0
    for shift in range(0, 29, 7):
        if offset >= len(data):
            raise ValueError("Truncated county data")
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if not byte & 128:
            return value, offset
    raise ValueError("Invalid county integer")


def decode_rings(compressed):
    data = gzip.decompress(compressed)
    count, offset = uint(data, 0)
    if not 1 <= count <= 20000:
        raise ValueError("Invalid county rings")
    rings, total = [], 0
    for _ in range(count):
        n, offset = uint(data, offset)
        total += n
        if n < 3 or total > 2_000_000:
            raise ValueError("Invalid county vertices")
        ring, x, y = [], 0, 0
        for _ in range(n):
            dx, offset = uint(data, offset)
            dy, offset = uint(data, offset)
            x += (dx >> 1) ^ -(dx & 1)
            y += (dy >> 1) ^ -(dy & 1)
            ring.append((x / 1_000_000, y / 1_000_000))
        rings.append(ring)
    if offset != len(data):
        raise ValueError("Unexpected county bytes")
    return rings


def contains(rings, x, y):
    # Same even/odd rule as HistoricCountyGeometry, including holes/islands.
    # Border points remain unassigned for review rather than guessing.
    inside = False
    for ring in rings:
        ax, ay = ring[-1]
        for bx, by in ring:
            cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax)
            if (abs(cross) < 1e-14 and min(ax, bx) - 1e-10 <= x <= max(ax, bx) + 1e-10
                    and min(ay, by) - 1e-10 <= y <= max(ay, by) + 1e-10):
                return False
            if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
                inside = not inside
            ax, ay = bx, by
    return inside


class Counties:
    def __init__(self, apk):
        with zipfile.ZipFile(apk) as archive:
            root = "assets/historic-counties/"
            self.manifest = json.loads(archive.read(root + "catalogue.json"))
            self.data = archive.read(root + "boundaries.bin")
        if (self.manifest.get("format") != 1 or self.manifest.get("coordinate_scale") != 1_000_000
                or len(self.manifest["counties"]) != 92
                or hashlib.sha256(self.data).hexdigest() != self.manifest["data_sha256"]):
            raise ValueError("County reference failed validation")
        self.cache = {}

    def assign(self, x, y):
        matches = []
        for county in self.manifest["counties"]:
            west, south, east, north = county["bbox"]
            if not west <= x <= east or not south <= y <= north:
                continue
            code = county["code"]
            if code not in self.cache:
                start, length = county["offset"], county["length"]
                self.cache[code] = decode_rings(self.data[start:start + length])
            if contains(self.cache[code], x, y):
                matches.append({"code": code, "name": county["name"], "nation": county["nation"]})
        return matches[0] if len(matches) == 1 else None


def metres(a, b):
    x1, y1, x2, y2 = a["lon"], a["lat"], b["lon"], b["lat"]
    r = math.pi / 180
    h = math.sin((y2 - y1) * r / 2) ** 2 + math.cos(y1 * r) * math.cos(y2 * r) * math.sin((x2 - x1) * r / 2) ** 2
    return 6_371_000 * 2 * math.asin(math.sqrt(min(1, max(0, h))))


def build(document, *, retrieved_at, source_sha256, counties=None, registry=None, previous=None):
    features = document.get("features")
    if (document.get("type") != "FeatureCollection" or not isinstance(features, list)
            or not features or len(features) > MAX_OBJECTS or document.get("error") or document.get("remark")):
        raise ValueError("Empty, truncated or invalid OSM extract")
    registry = registry or {"venues": []}
    if registry.get("format", 1) != 1 or registry.get("collection_id", KEY) != KEY:
        raise ValueError("Unsupported identity registry")
    aliases, registry_ids = {}, set()
    for row in registry["venues"]:
        ident = row["id"]
        if not ident.startswith("spoons-") or ident in registry_ids or not row.get("osm_aliases"):
            raise ValueError("Invalid/duplicate registry identity")
        registry_ids.add(ident)
        for alias in row["osm_aliases"]:
            if alias in aliases:
                raise ValueError("OSM alias assigned twice")
            aliases[alias] = ident
    old = {v["id"]: v for v in (previous or {}).get("venues", [])}
    if len(old) != len((previous or {}).get("venues", [])):
        raise ValueError("Duplicate prior venue identity")
    if previous and previous.get("collection_id") != KEY:
        raise ValueError("Wrong prior catalogue")
    for v in old.values():
        for alias in v["osm_aliases"]:
            if alias in aliases and aliases[alias] != v["id"]:
                raise ValueError("Registry would replace an existing stable identity")
            aliases[alias] = v["id"]
    venues, excluded, seen = {}, [], set()
    for feature in features:
        properties = feature.get("properties") or {}
        kind, number = properties.get("osm_type"), properties.get("osm_id")
        if kind not in ("N", "W", "R") or not isinstance(number, int) or isinstance(number, bool) or number <= 0:
            raise ValueError("Invalid public OSM identity")
        alias = {"N": "node", "W": "way", "R": "relation"}[kind] + "/" + str(number)
        if alias in seen:
            raise ValueError("Duplicate OSM object")
        seen.add(alias)
        tags = properties.get("tags") or {}
        # Hotels are not a feature, but a pub that also has rooms is still eligible.
        lifecycle = any(tags.get(key) not in (None, "", "no") for key in
                        ("disused", "abandoned", "closed", "disused:amenity", "abandoned:amenity", "demolished:amenity"))
        if tags.get("amenity") != "pub" or lifecycle:
            excluded.append({"osm_id": alias, "name": tags.get("name", ""),
                             "reason": "not_tagged_current_pub" if not lifecycle else "lifecycle_flag"})
            continue
        name = tags.get("name", "").strip()
        if not name or not point_valid(properties.get("lon"), properties.get("lat")):
            raise ValueError("Pub missing name or valid label point")
        geometry = feature.get("geometry") or {}
        quality = validate_geometry(geometry)
        ident = aliases.get(alias, "spoons-osm-" + kind.lower() + str(number))
        county = counties.assign(properties["lon"], properties["lat"]) if counties else None
        source = {"osm_id": alias, "geometry": geometry, "geometry_quality": quality}
        if ident in venues:
            venues[ident]["osm_aliases"].append(alias)
            venues[ident]["sources"].append(source)
            if quality != "point_only":
                flags = venues[ident]["review_flags"]
                if "point_only" in flags:
                    flags.remove("point_only")
                if "footprint_unverified" not in flags:
                    flags.append("footprint_unverified")
            continue
        venues[ident] = {
            "id": ident, "name": name, "osm_aliases": [alias], "sources": [source],
            "lat": properties["lat"], "lon": properties["lon"],
            "town": tags.get("addr:town") or tags.get("addr:city") or "",
            "postcode": tags.get("addr:postcode", ""), "county": county,
            "status": "candidate", "operating_status": "unverified",
            "visit_rule": "supported_stop_and_verified_venue; ambiguous_shared_buildings_require_confirmation",
            "review_flags": (["point_only"] if quality == "point_only" else ["footprint_unverified"])
                            + ([] if county else ["county_unassigned"]),
        }
    # Missing source objects are not proof of closure; keep stable history for review.
    missing = []
    for ident, v in old.items():
        if ident not in venues:
            copy = dict(v)
            copy["status"] = "source_missing_review"
            copy["review_flags"] = sorted(set(v.get("review_flags", []) + ["source_missing"]))
            venues[ident] = copy
            missing.append(ident)
    rows = sorted(venues.values(), key=lambda v: v["id"])
    nearby = []
    current = [v for v in rows if v["status"] == "candidate"]
    for i, a in enumerate(current):
        for b in current[i + 1:]:
            distance = metres(a, b)
            if distance < 75:
                nearby.append({"ids": [a["id"], b["id"]], "names": [a["name"], b["name"]],
                               "distance_metres": round(distance), "action": "review; never merge by proximity alone"})
    identity_hash = hashlib.sha256(json.dumps(registry, sort_keys=True).encode()).hexdigest()
    content_hash = hashlib.sha256(json.dumps(rows, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    version_hash = hashlib.sha256((source_sha256 + identity_hash + content_hash).encode()).hexdigest()
    catalogue = {
        "schema_version": 1, "collection_id": KEY, "display_name": NAME,
        "catalogue_version": retrieved_at[:10] + "." + version_hash[:12], "scope": "UK",
        "release_status": "audit_only", "ready_for_app": False, "completion_target": None,
        "source": {"dataset": "OpenStreetMap", "provider": PROVIDER, "retrieved_at": retrieved_at,
                   "sha256": source_sha256, "query_sha256": hashlib.sha256(query().encode()).hexdigest(),
                   "licence": "ODbL-1.0", "licence_url": "https://opendatacommons.org/licenses/odbl/1-0/",
                   "attribution": "© OpenStreetMap contributors"},
        "county_reference": ({k: counties.manifest[k] for k in
                              ("version", "source_sha256", "data_sha256")} if counties else None),
        "identity_registry_sha256": identity_hash,
        "venues": rows,
    }
    audit = {
        "display_name": NAME, "catalogue_version": catalogue["catalogue_version"],
        "raw_objects": len(features), "candidate_venues": len(current), "excluded_objects": excluded,
        "point_only": sum(all(s["geometry_quality"] == "point_only" for s in v["sources"]) for v in current),
        "mapped_footprints": sum(any(s["geometry_quality"] != "point_only" for s in v["sources"]) for v in current),
        "county_unassigned": [v["id"] for v in current if not v["county"]],
        "missing_postcode": sum(not v["postcode"] for v in current),
        "missing_town": sum(not v["town"] for v in current), "nearby_pairs": nearby,
        "source_missing": missing, "all_operating_statuses_unverified": True,
        "ready_for_app": False,
        "remaining_gates": ["Verify current operation and source coverage; counts alone do not establish completeness",
                            "Review node/building aliases and source-missing identities",
                            "Review venue footprints, point-only and shared-building cases",
                            "Resolve county gaps before county completion targets",
                            "Define an approved active catalogue before app packaging"],
    }
    return catalogue, audit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Cached GeoJSON extract path")
    parser.add_argument("--fetch", action="store_true", help="Explicitly fetch one UK public-area extract")
    parser.add_argument("--county-apk", type=Path)
    parser.add_argument("--registry", type=Path)
    parser.add_argument("--previous", type=Path)
    parser.add_argument("--retrieved-at", required=True, help="UTC source retrieval timestamp, including Z")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()
    timestamp = datetime.fromisoformat(args.retrieved_at.replace("Z", "+00:00"))
    if timestamp.tzinfo is None or timestamp.utcoffset().total_seconds() != 0:
        raise ValueError("Retrieval timestamp must be UTC")
    if args.fetch:
        args.source.parent.mkdir(parents=True, exist_ok=True)
        fetch(args.source)
    raw = args.source.read_bytes()
    if len(raw) > MAX_BYTES:
        raise ValueError("Extract exceeds byte limit")
    catalogue, audit = build(json.loads(raw), retrieved_at=args.retrieved_at,
                             source_sha256=hashlib.sha256(raw).hexdigest(),
                             counties=Counties(args.county_apk) if args.county_apk else None,
                             registry=load_json(args.registry) if args.registry else None,
                             previous=load_json(args.previous) if args.previous else None)
    write_json(args.output, catalogue)
    write_json(args.audit, audit)
    print(json.dumps({key: audit[key] for key in ("raw_objects", "candidate_venues", "mapped_footprints",
                                                 "point_only", "missing_postcode", "missing_town", "ready_for_app")}))


if __name__ == "__main__":
    main()
