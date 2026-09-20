import hashlib
import json
import os
import urllib.parse
import urllib.request
from pathlib import Path

import psycopg

DATABASE_URL = os.getenv("DATABASE_URL")
MIGRATIONS_DIR = Path(__file__).with_name("migrations")
SETTLEMENT_CATALOGUE_PATH = Path(__file__).with_name("settlement-catalogue-v1.json")
CATALOGUE_DATASET_KEY = "settlement_catalogue_v1"
database_state = {"configured": bool(DATABASE_URL), "status": "not_configured", "detail": None}


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
                      (SELECT COUNT(*) FROM reference_sources)
                    """
                )
                settlements, boundaries, a_roads, sources = cursor.fetchone()
        return {
            "status": "ready",
            "settlements": settlements,
            "settlement_boundaries": boundaries,
            "gb_a_roads": a_roads,
            "sources": sources,
        }
    except Exception:
        return unavailable


# This deliberately small next batch consists of settlements that Roadprints
# has already encountered. It is not a national geometry import.
SETTLEMENT_BOUNDARY_SEED_CODES = (
    "E63000498",  # Dalton-in-Furness
    "E63004165",  # Great Dunmow
    "E63005055",  # Lower Higham
    "E63005466",  # Maidstone
    "E63005039",  # Northfleet
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
                    SELECT s.external_code, s.name
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
                    {"code": external_code, "name": name}
                    for external_code, name in cursor.fetchall()
                ]
    except Exception:
        # Reference lookup must remain an optional acceleration while coverage
        # is partial.  The existing boundary service remains the safe fallback.
        return None

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
                load_seed_inventory_requests(cursor)
            connection.commit()
        database_state.update(status="ready", detail=None)
    except Exception as exc:
        database_state.update(status="error", detail=exc.__class__.__name__)
