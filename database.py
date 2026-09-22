import hashlib
import json
import os
import urllib.parse
import urllib.request
import logging
from pathlib import Path

import psycopg

from pedestrian_reference import load_configured_pedestrian_references

DATABASE_URL = os.getenv("DATABASE_URL")
MIGRATIONS_DIR = Path(__file__).with_name("migrations")
SETTLEMENT_CATALOGUE_PATH = Path(__file__).with_name("settlement-catalogue-v1.json")
CATALOGUE_DATASET_KEY = "settlement_catalogue_v1"
database_state = {"configured": bool(DATABASE_URL), "status": "not_configured", "detail": None}
logger = logging.getLogger("roadprints.database")


def load_settlement_catalogue(cursor) -> None:
    """Load public GB settlement names, aliases and centres idempotently."""
    if not SETTLEMENT_CATALOGUE_PATH.exists():
        return

    raw_catalogue = SETTLEMENT_CATALOGUE_PATH.read_bytes()
    checksum = hashlib.sha256(raw_catalogue).hexdigest()
    catalogue = json.loads(raw_catalogue)
    settlements = catalogue.get("settlements", [])
    cursor.execute(
        "SELECT id FROM reference_sources WHERE source_key = %s",
        ("ons_bua_2022_gb",),
    )
    source = cursor.fetchone()
    if not source:
        raise RuntimeError("Missing ONS settlement reference source")
    source_id = source[0]

    cursor.execute(
        "SELECT checksum, row_count FROM reference_data_loads "
        "WHERE source_id = %s AND dataset_key = %s",
        (source_id, CATALOGUE_DATASET_KEY),
    )
    existing = cursor.fetchone()
    if existing and existing[0] == checksum and existing[1] == len(settlements):
        return

    settlement_rows = []
    alias_rows = []
    for settlement in settlements:
        centre = settlement.get("centre")
        if not isinstance(centre, list) or len(centre) != 2:
            raise RuntimeError(f"Invalid settlement centre: {settlement.get('code')}")
        latitude, longitude = centre
        settlement_rows.append(
            (source_id, settlement["code"], settlement["name"], longitude, latitude)
        )
        alias_rows.extend(
            (source_id, settlement["code"], alias)
            for alias in settlement.get("aliases", [])
        )

    cursor.executemany(
        """
        INSERT INTO settlements (source_id, external_code, name, is_active, centre)
        VALUES (
          %s, %s, %s, TRUE,
          ST_SetSRID(ST_MakePoint(%s, %s), 4326)
        )
        ON CONFLICT (source_id, external_code) DO UPDATE
        SET name = EXCLUDED.name,
            is_active = TRUE,
            centre = EXCLUDED.centre
        """,
        settlement_rows,
    )
    if alias_rows:
        cursor.executemany(
            """
            INSERT INTO settlement_aliases (settlement_id, alias)
            SELECT s.id, %s
            FROM settlements s
            WHERE s.source_id = %s AND s.external_code = %s
            ON CONFLICT (settlement_id, alias) DO NOTHING
            """,
            [(alias, source_id, code) for source_id, code, alias in alias_rows],
        )
    cursor.execute(
        """
        INSERT INTO reference_data_loads
          (source_id, dataset_key, checksum, row_count)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (source_id, dataset_key) DO UPDATE
        SET checksum = EXCLUDED.checksum,
            row_count = EXCLUDED.row_count,
            loaded_at = NOW()
        """,
        (source_id, CATALOGUE_DATASET_KEY, checksum, len(settlements)),
    )



def reference_catalogue_status() -> dict:
    """Return only shared-reference readiness; never query personal data."""
    unavailable = {
        "status": database_state["status"],
        "settlements": 0,
        "settlement_boundaries": 0,
        "gb_a_roads": 0,
        "pedestrian_segments": 0,
        "sources": 0,
    }
    if not DATABASE_URL or database_state["status"] != "ready":
        return unavailable
    try:
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT
                      (SELECT COUNT(*) FROM settlements WHERE is_active),
                      (SELECT COUNT(*) FROM settlement_boundaries),
                      (SELECT COUNT(*) FROM road_references
                       WHERE road_kind = 'a_road' AND network_region = 'GB'),
                      (SELECT COUNT(*) FROM pedestrian_reference_segments),
                      (SELECT COUNT(*) FROM reference_sources)
                    """
                )
                settlements, boundaries, a_roads, pedestrian_segments, sources = cursor.fetchone()
        return {
            "status": "ready",
            "settlements": settlements,
            "settlement_boundaries": boundaries,
            "gb_a_roads": a_roads,
            "pedestrian_segments": pedestrian_segments,
            "sources": sources,
        }
    except Exception:
        return unavailable


# This deliberately small next batch consists of settlements that Roadprints
# has already encountered. It is not a national geometry import.
SETTLEMENT_BOUNDARY_SEED_CODES = (
    "E63000498",  # Dalton-in-Furness
    "E63004165",  # Great Dunmow
    "E63004964",  # Allhallows
    "E63005030",  # Dartford
    "E63005041",  # Ebbsfleet Valley
    "E63005055",  # Lower Higham
    "E63005084",  # Darenth
    "E63005039",  # Northfleet
    "E63005212",  # Chatham
    "E63005234",  # Ash (Sevenoaks)
    "E63005283",  # Blue Bell Hill
    "E63005466",  # Maidstone
    "E63005580",  # Reigate
)
ONS_BUA_FEATURE_SERVICE = (
    "https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/"
    "BUA_2022_GB/FeatureServer/0/query"
)
SETTLEMENT_BOUNDARY_SEED_DATASET_KEY = "settlement_boundaries_seed_v1"


def load_seed_settlement_boundaries(cursor) -> None:
    """Load a small, idempotent set of official GB settlement boundaries.

    The data is shared reference geometry only. This loader is intentionally
    bounded; it neither accepts nor queries Roadprints Journey data.
    """
    cursor.execute(
        "SELECT id FROM reference_sources WHERE source_key = %s",
        ("ons_bua_2022_gb",),
    )
    source = cursor.fetchone()
    if not source:
        return
    source_id = source[0]

    cursor.execute(
        """
        SELECT s.external_code
        FROM settlement_boundaries AS boundary
        JOIN settlements AS s ON s.id = boundary.settlement_id
        WHERE s.source_id = %s
          AND s.external_code = ANY(%s)
        """,
        (source_id, list(SETTLEMENT_BOUNDARY_SEED_CODES)),
    )
    existing_codes = {row[0] for row in cursor.fetchall()}
    missing_codes = [
        code for code in SETTLEMENT_BOUNDARY_SEED_CODES if code not in existing_codes
    ]
    if not missing_codes:
        return

    where = "BUA22CD IN (" + ",".join(
        "'" + code.replace("'", "''") + "'" for code in missing_codes
    ) + ")"
    query = urllib.parse.urlencode(
        {
            "where": where,
            "outFields": "BUA22CD,BUA22NM",
            "returnGeometry": "true",
            "outSR": "4326",
            "f": "geojson",
        }
    )
    try:
        with urllib.request.urlopen(
            ONS_BUA_FEATURE_SERVICE + "?" + query, timeout=30
        ) as response:
            payload = json.load(response)
    except Exception:
        # The live fallback remains available. A temporary public-source
        # outage must never make the application or its database unavailable.
        return

    loaded_codes = []
    geometry_fingerprints = []
    for feature in payload.get("features", []):
        attributes = feature.get("properties") or {}
        code = attributes.get("BUA22CD")
        geometry = feature.get("geometry")
        if code not in missing_codes or not isinstance(geometry, dict):
            continue
        geometry_json = json.dumps(geometry, separators=(",", ":"), sort_keys=True)
        cursor.execute(
            """
            INSERT INTO settlement_boundaries (settlement_id, source_id, geometry)
            SELECT s.id, %s, ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)
            FROM settlements AS s
            WHERE s.source_id = %s AND s.external_code = %s
            ON CONFLICT (settlement_id) DO UPDATE
              SET source_id = EXCLUDED.source_id,
                  geometry = EXCLUDED.geometry,
                  updated_at = NOW()
            """,
            (source_id, geometry_json, source_id, code),
        )
        if cursor.rowcount:
            loaded_codes.append(code)
            geometry_fingerprints.append(code + ":" + geometry_json)

    if not loaded_codes:
        return
    checksum = hashlib.sha256(
        "\n".join(sorted(geometry_fingerprints)).encode("utf-8")
    ).hexdigest()
    cursor.execute(
        """
        INSERT INTO reference_data_loads
          (source_id, dataset_key, checksum, row_count)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (source_id, dataset_key) DO UPDATE
        SET checksum = EXCLUDED.checksum,
            row_count = EXCLUDED.row_count,
            loaded_at = NOW()
        """,
        (source_id, SETTLEMENT_BOUNDARY_SEED_DATASET_KEY, checksum, len(loaded_codes)),
    )



FULL_SETTLEMENT_BOUNDARY_DATASET_KEY = "settlement_boundaries_gb_bgg_v1"


def load_all_settlement_boundaries(cursor) -> None:
    """Load the complete public GB built-up-area boundary catalogue.

    This is an explicit administrative load for the one-off reference job.
    It never receives, reads or stores Roadprints Journey data. The source is
    paged so the full national layer is streamed in bounded memory.
    """
    if os.getenv("LOAD_ALL_SETTLEMENT_BOUNDARIES", "").lower() not in {"1", "true", "yes"}:
        return
    cursor.execute(
        "SELECT id FROM reference_sources WHERE source_key = %s",
        ("ons_bua_2022_gb",),
    )
    source = cursor.fetchone()
    if not source:
        raise RuntimeError("Missing ONS settlement reference source")
    source_id = source[0]
    cursor.execute(
        "SELECT row_count FROM reference_data_loads WHERE source_id = %s AND dataset_key = %s",
        (source_id, FULL_SETTLEMENT_BOUNDARY_DATASET_KEY),
    )
    completed = cursor.fetchone()
    if completed and completed[0] > 0:
        return

    page_size = 200
    offset = 0
    rows_loaded = 0
    digest = hashlib.sha256()
    while True:
        query = urllib.parse.urlencode({
            "where": "1=1",
            "outFields": "BUA22CD,BUA22NM",
            "returnGeometry": "true",
            "outSR": "4326",
            "orderByFields": "BUA22CD",
            "resultOffset": offset,
            "resultRecordCount": page_size,
            "f": "geojson",
        })
        with urllib.request.urlopen(ONS_BUA_FEATURE_SERVICE + "?" + query, timeout=90) as response:
            payload = json.load(response)
        features = payload.get("features") or []
        if not features:
            break
        for feature in features:
            properties = feature.get("properties") or {}
            code = properties.get("BUA22CD")
            geometry = feature.get("geometry")
            if not code or not isinstance(geometry, dict):
                continue
            geometry_json = json.dumps(geometry, separators=(",", ":"), sort_keys=True)
            cursor.execute(
                """
                INSERT INTO settlement_boundaries (settlement_id, source_id, geometry)
                SELECT s.id, %s, ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)
                FROM settlements AS s
                WHERE s.source_id = %s AND s.external_code = %s
                ON CONFLICT (settlement_id) DO UPDATE
                  SET source_id = EXCLUDED.source_id,
                      geometry = EXCLUDED.geometry,
                      updated_at = NOW()
                """,
                (source_id, geometry_json, source_id, code),
            )
            if cursor.rowcount:
                rows_loaded += 1
                digest.update((code + ":" + geometry_json).encode("utf-8"))
        offset += len(features)
        if not payload.get("exceededTransferLimit") and len(features) < page_size:
            break

    if not rows_loaded:
        raise RuntimeError("Full settlement boundary import produced no usable boundaries")
    cursor.execute(
        """
        INSERT INTO reference_data_loads (source_id, dataset_key, checksum, row_count)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (source_id, dataset_key) DO UPDATE
        SET checksum = EXCLUDED.checksum, row_count = EXCLUDED.row_count, loaded_at = NOW()
        """,
        (source_id, FULL_SETTLEMENT_BOUNDARY_DATASET_KEY, digest.hexdigest(), rows_loaded),
    )
    logger.info("full GB settlement boundary import complete: %s boundaries", rows_loaded)


# Initial public inventory requests are deliberately bounded to settlements
# already encountered in Roadprints. They are shared reference work only.
SETTLEMENT_INVENTORY_SEED_CODES = (
    "E63005466",  # Maidstone
    "E63005580",  # Reigate
)


def load_seed_inventory_requests(cursor) -> None:
    cursor.execute(
        """
        INSERT INTO settlement_inventories (settlement_id, status)
        SELECT id, 'pending'
        FROM settlements
        WHERE external_code = ANY(%s)
        ON CONFLICT (settlement_id) DO NOTHING
        """,
        (list(SETTLEMENT_INVENTORY_SEED_CODES),),
    )


def settlement_inventory_status(external_code: str) -> dict | None:
    """Return a public shared inventory status, never Journey information."""
    if not DATABASE_URL or database_state["status"] != "ready":
        return None
    try:
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT s.external_code, s.name, i.status, i.road_count,
                           i.source_key, i.source_version, i.inventory_version,
                           i.requested_at, i.build_started_at, i.completed_at,
                           i.failure_code
                    FROM settlements AS s
                    LEFT JOIN settlement_inventories AS i ON i.settlement_id = s.id
                    WHERE s.external_code = %s AND s.is_active
                    """,
                    (external_code,),
                )
                row = cursor.fetchone()
        if not row:
            return None
        keys = (
            "code", "name", "status", "road_count", "source_key",
            "source_version", "inventory_version", "requested_at",
            "build_started_at", "completed_at", "failure_code",
        )
        result = dict(zip(keys, row))
        result["status"] = result["status"] or "not_requested"
        for key in ("requested_at", "build_started_at", "completed_at"):
            if result[key] is not None:
                result[key] = result[key].isoformat()
        return result
    except Exception:
        return None



def settlement_boundary_geojson(external_code: str) -> dict | None:
    """Return shared public settlement boundary geometry, never Journey data."""
    if not DATABASE_URL or database_state["status"] != "ready":
        return None
    try:
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT s.external_code, s.name, ST_AsGeoJSON(b.geometry)
                    FROM settlements AS s
                    JOIN settlement_boundaries AS b ON b.settlement_id = s.id
                    WHERE s.external_code = %s AND s.is_active
                    """,
                    (external_code,),
                )
                row = cursor.fetchone()
        if not row:
            return None
        return {
            "type": "Feature",
            "properties": {"code": row[0], "name": row[1]},
            "geometry": json.loads(row[2]),
        }
    except Exception:
        return None

def request_settlement_inventory(external_code: str) -> dict | None:
    """Deduplicate a public settlement inventory request without route data."""
    if not DATABASE_URL or database_state["status"] != "ready":
        return None
    try:
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO settlement_inventories (settlement_id, status)
                    SELECT id, 'pending'
                    FROM settlements
                    WHERE external_code = %s AND is_active
                    ON CONFLICT (settlement_id) DO NOTHING
                    RETURNING settlement_id
                    """,
                    (external_code,),
                )
                connection.commit()
        return settlement_inventory_status(external_code)
    except Exception:
        return None


def settlement_metadata(names: list[str]) -> list[dict] | None:
    """Return shared settlement display metadata, never Journey information."""
    if not DATABASE_URL or database_state["status"] != "ready":
        return None
    clean_names = sorted({name.strip() for name in names if name and name.strip()})
    if not clean_names:
        return []
    try:
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT external_code, name, nation, region, county
                    FROM settlements
                    WHERE is_active AND name = ANY(%s)
                    ORDER BY name, external_code
                    """,
                    (clean_names,),
                )
                return [
                    {
                        "code": external_code,
                        "name": name,
                        "nation": nation,
                        "region": region,
                        "county": county,
                    }
                    for external_code, name, nation, region, county in cursor.fetchall()
                ]
    except Exception:
        return None

def find_settlements_for_geometry(geometry: dict) -> list[dict] | None:
    """Find shared settlement boundaries intersecting transient route geometry.

    This reads only public reference data.  Callers must not persist or log the
    supplied route geometry; an empty result means the caller may use its
    existing fallback while the national boundary catalogue is still growing.
    """
    if not DATABASE_URL or database_state["status"] != "ready":
        return None
    try:
        geometry_json = json.dumps(geometry, separators=(",", ":"))
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT s.external_code, s.name, s.nation, s.region, s.county
                    FROM settlement_boundaries AS boundary
                    JOIN settlements AS s ON s.id = boundary.settlement_id
                    WHERE s.is_active
                      AND ST_Intersects(
                        boundary.geometry,
                        ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)
                      )
                    ORDER BY s.name
                    """,
                    (geometry_json,),
                )
                return [
                    {"code": external_code, "name": name, "nation": nation, "region": region, "county": county}
                    for external_code, name, nation, region, county in cursor.fetchall()
                ]
    except Exception:
        # Reference lookup must remain an optional acceleration while coverage
        # is partial.  The existing boundary service remains the safe fallback.
        return None


def match_pedestrian_reference(points: list[dict]) -> dict | None:
    """Match a trace through the connected preloaded pedestrian network.

    The database is the sole reference source. The transient points are used
    only for this request and are never written or logged as a Journey.
    """
    if not DATABASE_URL or database_state["status"] != "ready" or len(points) < 2:
        return None
    try:
        trace = [(float(point["lng"]), float(point["lat"])) for point in points]
    except (KeyError, TypeError, ValueError):
        return None

    # Build a compact, route-local graph from the prepared public reference.
    # A generous margin allows a sensible detour around parks, one-way streets
    # or barriers without loading the whole county into application memory.
    west = min(point[0] for point in trace) - 0.008
    east = max(point[0] for point in trace) + 0.008
    south = min(point[1] for point in trace) - 0.006
    north = max(point[1] for point in trace) + 0.006
    try:
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT source_feature_id, segment_kind, tags,
                           ST_AsGeoJSON(geometry)
                    FROM pedestrian_reference_segments
                    WHERE geometry && ST_MakeEnvelope(%s, %s, %s, %s, 4326)
                    """,
                    (west, south, east, north),
                )
                rows = cursor.fetchall()
    except Exception:
        return None
    if not rows:
        return None

    from heapq import heappop, heappush
    from math import asin, cos, radians, sin, sqrt

    def vertex_key(coord: list[float] | tuple[float, float]) -> tuple[int, int]:
        # OSM node coordinates are shared exactly between adjoining ways; this
        # stable key preserves those joins without an additional node table.
        return (round(float(coord[0]) * 10_000_000), round(float(coord[1]) * 10_000_000))

    def length_metres(a, b) -> float:
        lat1, lat2 = radians(a[1]), radians(b[1])
        delta_lat, delta_lng = radians(b[1] - a[1]), radians(b[0] - a[0])
        term = sin(delta_lat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(delta_lng / 2) ** 2
        return 6371008.8 * 2 * asin(min(1.0, sqrt(term)))

    vertices: dict[tuple[int, int], list[float]] = {}
    adjacency: dict[tuple[int, int], list[tuple[tuple[int, int], float, str]]] = {}
    features: dict[str, dict] = {}
    for feature_id, kind, tags, geometry_json in rows:
        geometry = json.loads(geometry_json)
        coords = geometry.get("coordinates") or []
        if len(coords) < 2:
            continue
        features[feature_id] = {
            "type": "Feature",
            "properties": {
                "road_ref": (tags or {}).get("ref", ""),
                "name": (tags or {}).get("name", ""),
                "distance_m": 0.0,
                "reference_kind": kind,
            },
            "geometry": geometry,
        }
        for raw_a, raw_b in zip(coords, coords[1:]):
            a, b = vertex_key(raw_a), vertex_key(raw_b)
            if a == b:
                continue
            vertices.setdefault(a, [float(raw_a[0]), float(raw_a[1])])
            vertices.setdefault(b, [float(raw_b[0]), float(raw_b[1])])
            distance = length_metres(vertices[a], vertices[b])
            adjacency.setdefault(a, []).append((b, distance, feature_id))
            adjacency.setdefault(b, []).append((a, distance, feature_id))

    if not vertices:
        return None

    def nearest_vertex(point):
        # The route-local graph keeps this direct scan small and avoids adding
        # a second personal-location index to shared storage.
        nearest = None
        nearest_distance = float("inf")
        for key, candidate in vertices.items():
            distance = length_metres(point, candidate)
            if distance < nearest_distance:
                nearest, nearest_distance = key, distance
        return nearest, nearest_distance

    snapped_vertices = []
    for point in trace:
        key, distance = nearest_vertex(point)
        if key is None or distance > 120:
            return None
        snapped_vertices.append(key)

    def shortest_path(start_key, end_key):
        if start_key == end_key:
            return [start_key], []
        distances = {start_key: 0.0}
        previous = {}
        queue = [(0.0, start_key)]
        visited = 0
        while queue and visited < 150_000:
            current_distance, current = heappop(queue)
            if current_distance != distances.get(current):
                continue
            if current == end_key:
                break
            visited += 1
            for neighbour, cost, feature_id in adjacency.get(current, []):
                candidate = current_distance + cost
                if candidate < distances.get(neighbour, float("inf")):
                    distances[neighbour] = candidate
                    previous[neighbour] = (current, feature_id)
                    heappush(queue, (candidate, neighbour))
        if end_key not in previous:
            return None
        path = [end_key]
        feature_ids = []
        current = end_key
        while current != start_key:
            prior, feature_id = previous[current]
            path.append(prior)
            feature_ids.append(feature_id)
            current = prior
        path.reverse()
        feature_ids.reverse()
        return path, feature_ids

    route_keys = [snapped_vertices[0]]
    used_feature_ids = []
    for start_key, end_key in zip(snapped_vertices, snapped_vertices[1:]):
        result = shortest_path(start_key, end_key)
        if result is None:
            return None
        path, feature_ids = result
        route_keys.extend(path[1:])
        used_feature_ids.extend(feature_ids)

    coordinates = [vertices[key] for key in route_keys]
    if len(coordinates) < 2:
        return None
    matched_distance = sum(
        length_metres(a, b) for a, b in zip(coordinates, coordinates[1:])
    )
    unique_feature_ids = list(dict.fromkeys(used_feature_ids))
    route_feature = {
        "type": "Feature",
        "properties": {
            "reference_area": "preloaded_public_network",
            "matcher": "preloaded_pedestrian_network_v3",
            "confidence": 1.0,
        },
        "geometry": {"type": "LineString", "coordinates": coordinates},
    }
    return {
        "status": "ok",
        "matcher": "preloaded_pedestrian_network_v3",
        "input_points": len(points),
        "chunks_used": 1,
        "points_sent_to_matcher": len(points),
        "matched_tracepoints": len(points),
        "matched_distance_m": round(matched_distance, 1),
        "geojson": {"type": "FeatureCollection", "features": [route_feature]},
        "motorway_geojson": {"type": "FeatureCollection", "features": []},
        "a_road_geojson": {"type": "FeatureCollection", "features": []},
        "road_geojson": {
            "type": "FeatureCollection",
            "features": [features[feature_id] for feature_id in unique_feature_ids if feature_id in features],
        },
        "other_road_distance_m": 0.0,
    }


def initialise_database() -> None:
    if not DATABASE_URL:
        return
    try:
        with psycopg.connect(DATABASE_URL) as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    "CREATE TABLE IF NOT EXISTS schema_migrations "
                    "(version TEXT PRIMARY KEY, "
                    "applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), "
                    "checksum TEXT NOT NULL)"
                )
                for migration in sorted(MIGRATIONS_DIR.glob("*.sql")):
                    sql = migration.read_text(encoding="utf-8")
                    checksum = hashlib.sha256(sql.encode("utf-8")).hexdigest()
                    cursor.execute(
                        "SELECT checksum FROM schema_migrations WHERE version = %s",
                        (migration.name,),
                    )
                    existing = cursor.fetchone()
                    if existing:
                        if existing[0] != checksum:
                            raise RuntimeError(
                                f"Migration checksum changed: {migration.name}"
                            )
                        continue
                    cursor.execute(sql)
                    cursor.execute(
                        "INSERT INTO schema_migrations (version, checksum) "
                        "VALUES (%s, %s)",
                        (migration.name, checksum),
                    )
                load_settlement_catalogue(cursor)
                load_seed_settlement_boundaries(cursor)
                load_all_settlement_boundaries(cursor)
                load_seed_inventory_requests(cursor)
                # This is an explicit deployment-time reference build. It is
                # never part of a user import and a public-source outage must
                # not take the API offline.
                try:
                    load_configured_pedestrian_references(cursor)
                except Exception as exc:
                    logger.warning("Pedestrian reference not loaded: %s", exc.__class__.__name__)
            connection.commit()
        database_state.update(status="ready", detail=None)
    except Exception as exc:
        database_state.update(status="error", detail=exc.__class__.__name__)
