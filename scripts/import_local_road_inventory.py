#!/usr/bin/env python3
"""Import shared local-road catalogues from a filtered public GeoJSON sequence.

This is reference-data administration, never a Roadprints user-data process.
The input is expected to contain only OSM ways already filtered to the
Roadprints local-road eligibility set.
"""
from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path

import psycopg
from shapely.geometry import shape
from shapely.strtree import STRtree

ELIGIBLE_CLASSES = {"residential", "unclassified", "tertiary", "living_street"}
SOURCE_KEY = "osm_gb_local_roads"
SOURCE_NAME = "OpenStreetMap Great Britain local-road extract"
SOURCE_LICENCE = "Open Database License (ODbL) 1.0"


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--features", required=True, type=Path,
                        help="GeoJSON sequence produced from eligible OSM ways")
    parser.add_argument("--settlement", action="append", required=True,
                        help="Official settlement code; may be supplied more than once")
    parser.add_argument("--source-version", required=True,
                        help="Source date/version, for example 2026-09-20")
    parser.add_argument("--inventory-version", default="local-roads-v1")
    return parser.parse_args()


def feature_properties(feature: dict) -> tuple[str, str, str | None] | None:
    properties = feature.get("properties") or {}
    road_name = str(properties.get("name") or "").strip()
    road_class = str(properties.get("highway") or "").strip().lower()
    feature_id = properties.get("@id") or feature.get("id")
    if not road_name or road_class not in ELIGIBLE_CLASSES:
        return None
    return road_name, road_class, str(feature_id) if feature_id is not None else None


def source_id(cursor, source_version: str) -> str:
    cursor.execute(
        """
        INSERT INTO reference_sources
          (source_key, display_name, source_version, licence, retrieved_at, notes)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_key) DO UPDATE
        SET source_version = EXCLUDED.source_version,
            licence = EXCLUDED.licence,
            retrieved_at = EXCLUDED.retrieved_at,
            notes = EXCLUDED.notes
        RETURNING id
        """,
        (
            SOURCE_KEY, SOURCE_NAME, source_version, SOURCE_LICENCE,
            datetime.now(timezone.utc),
            "Derived from a versioned Great Britain extract; raw source is not retained.",
        ),
    )
    return str(cursor.fetchone()[0])


def load_boundaries(cursor, codes: list[str]) -> dict[str, tuple[str, object]]:
    cursor.execute(
        """
        SELECT s.external_code, s.id::text, ST_AsGeoJSON(b.geometry)
        FROM settlements AS s
        JOIN settlement_boundaries AS b ON b.settlement_id = s.id
        WHERE s.external_code = ANY(%s)
        """,
        (codes,),
    )
    boundaries = {
        code: (settlement_id, shape(json.loads(geometry)))
        for code, settlement_id, geometry in cursor.fetchall()
    }
    missing = sorted(set(codes) - set(boundaries))
    if missing:
        raise RuntimeError("Missing official settlement boundary: " + ", ".join(missing))
    return boundaries


def build_boundary_index(boundaries: dict[str, tuple[str, object]]) -> tuple[list[str], STRtree]:
    """Index settlement boundaries once so a source feature checks only nearby towns."""
    codes = list(boundaries)
    return codes, STRtree([boundaries[code][1] for code in codes])


def mark_building(cursor, codes: list[str]) -> None:
    cursor.execute(
        """
        UPDATE settlement_inventories AS inventory
        SET status = 'building', build_started_at = NOW(), failed_at = NULL,
            failure_code = NULL, updated_at = NOW()
        FROM settlements AS settlement
        WHERE inventory.settlement_id = settlement.id
          AND settlement.external_code = ANY(%s)
        """,
        (codes,),
    )


def import_catalogues(connection, options: argparse.Namespace) -> dict[str, int]:
    codes = list(dict.fromkeys(options.settlement))
    with connection.cursor() as cursor:
        boundaries = load_boundaries(cursor, codes)
        mark_building(cursor, codes)
    connection.commit()

    roads: dict[str, dict[str, tuple[str, str | None]]] = {code: {} for code in codes}
    boundary_codes, boundary_tree = build_boundary_index(boundaries)
    with options.features.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            record = line.strip().lstrip("\x1e").strip()
            if not record:
                continue
            feature = json.loads(record)
            details = feature_properties(feature)
            geometry = feature.get("geometry")
            if not details or not isinstance(geometry, dict):
                continue
            road_name, road_class, feature_id = details
            road_geometry = shape(geometry)
            if road_geometry.is_empty:
                continue
            for position in boundary_tree.query(road_geometry, predicate="intersects"):
                code = boundary_codes[int(position)]
                roads[code][road_name] = (road_class, feature_id)

    with connection.cursor() as cursor:
        source = source_id(cursor, options.source_version)
        for code, (settlement_id, _) in boundaries.items():
            cursor.execute(
                "DELETE FROM settlement_inventory_roads WHERE settlement_id = %s",
                (settlement_id,),
            )
            entries = [
                (settlement_id, name, road_class, feature_id)
                for name, (road_class, feature_id) in sorted(roads[code].items())
            ]
            if entries:
                cursor.executemany(
                    """
                    INSERT INTO settlement_inventory_roads
                      (settlement_id, road_name, road_class, source_feature_id)
                    VALUES (%s, %s, %s, %s)
                    """,
                    entries,
                )
            cursor.execute(
                """
                UPDATE settlement_inventories
                SET status = 'ready', road_count = %s, source_key = %s,
                    source_version = %s, inventory_version = %s,
                    completed_at = NOW(), failed_at = NULL, failure_code = NULL,
                    updated_at = NOW()
                WHERE settlement_id = %s
                """,
                (len(entries), SOURCE_KEY, options.source_version,
                 options.inventory_version, settlement_id),
            )
    connection.commit()
    return {code: len(entries) for code, entries in roads.items()}


def mark_failed(connection, codes: list[str], error: Exception) -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE settlement_inventories AS inventory
            SET status = 'failed', failed_at = NOW(),
                failure_code = %s, updated_at = NOW()
            FROM settlements AS settlement
            WHERE inventory.settlement_id = settlement.id
              AND settlement.external_code = ANY(%s)
            """,
            (error.__class__.__name__, codes),
        )
    connection.commit()


def main() -> None:
    options = arguments()
    database_url = os.environ.get("DATABASE_URL")
    if not database_url:
        raise SystemExit("DATABASE_URL is required")
    try:
        with psycopg.connect(database_url) as connection:
            try:
                counts = import_catalogues(connection, options)
            except Exception as error:
                connection.rollback()
                mark_failed(connection, list(dict.fromkeys(options.settlement)), error)
                raise
    except Exception as error:
        raise SystemExit(f"Inventory import failed: {error.__class__.__name__}: {error}") from error
    print(json.dumps({"status": "ready", "settlements": counts}, sort_keys=True))


if __name__ == "__main__":
    main()