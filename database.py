import hashlib
import json
import os
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
            connection.commit()
        database_state.update(status="ready", detail=None)
    except Exception as exc:
        database_state.update(status="error", detail=exc.__class__.__name__)
