#!/usr/bin/env python3
"""Assign shared settlement county/unitary-area metadata from public ONS boundaries.

This loader handles reference geography only. It never reads or writes Roadprints
journeys, routes, users, corrections or other personal data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import urllib.parse
import urllib.request
from datetime import datetime, timezone

import psycopg
from shapely.geometry import shape
from shapely.strtree import STRtree

SOURCE_KEY = "ons_counties_unitary_areas"
SOURCE_NAME = "ONS Counties and Unitary Authorities (December 2022) Boundaries UK"
SOURCE_LICENCE = "Open Government Licence v3.0"
SOURCE_URL = (
    "https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/"
    "Counties_and_Unitary_Authorities_December_2022_UK_BGC/FeatureServer/0/query"
)


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-version", required=True)
    return parser.parse_args()


def fetch_features() -> list[dict]:
    features: list[dict] = []
    offset = 0
    while True:
        query = urllib.parse.urlencode(
            {
                "where": "1=1",
                "outFields": "CTYUA22CD,CTYUA22NM",
                "returnGeometry": "true",
                "resultOffset": offset,
                "resultRecordCount": 500,
                "f": "geojson",
            }
        )
        with urllib.request.urlopen(SOURCE_URL + "?" + query, timeout=90) as response:
            page = json.load(response)
        page_features = page.get("features", [])
        features.extend(page_features)
        if len(page_features) < 500:
            return features
        offset += len(page_features)


def load_settlements(cursor) -> list[tuple[str, object]]:
    cursor.execute(
        """
        SELECT id::text, ST_AsGeoJSON(centre)
        FROM settlements
        WHERE is_active AND centre IS NOT NULL
        """
    )
    return [(settlement_id, shape(json.loads(centre))) for settlement_id, centre in cursor.fetchall()]


def source_id(cursor, source_version: str) -> str:
    cursor.execute(
        """
        INSERT INTO reference_sources
          (source_key, display_name, source_version, licence, retrieved_at, notes)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (source_key) DO UPDATE
        SET display_name = EXCLUDED.display_name,
            source_version = EXCLUDED.source_version,
            licence = EXCLUDED.licence,
            retrieved_at = EXCLUDED.retrieved_at,
            notes = EXCLUDED.notes
        RETURNING id
        """,
        (
            SOURCE_KEY,
            SOURCE_NAME,
            source_version,
            SOURCE_LICENCE,
            datetime.now(timezone.utc),
            "County/unitary-area assignment for shared settlement display grouping.",
        ),
    )
    return str(cursor.fetchone()[0])


def main() -> None:
    options = arguments()
    database_url = os.environ.get("DATABASE_URL")
    if not database_url:
        raise SystemExit("DATABASE_URL is required")

    features = fetch_features()
    areas: list[tuple[str, object]] = []
    for feature in features:
        properties = feature.get("properties") or {}
        name = str(properties.get("CTYUA22NM") or "").strip()
        geometry = feature.get("geometry")
        if name and isinstance(geometry, dict):
            candidate = shape(geometry)
            if not candidate.is_empty:
                areas.append((name, candidate))
    if not areas:
        raise SystemExit("No county/unitary-area geometries were returned")

    with psycopg.connect(database_url) as connection:
        with connection.cursor() as cursor:
            settlements = load_settlements(cursor)
            names = [name for name, _ in areas]
            tree = STRtree([geometry for _, geometry in areas])
            assignments: list[tuple[str, str]] = []
            for settlement_id, centre in settlements:
                candidates = [
                    int(position)
                    for position in tree.query(centre, predicate="intersects")
                ]
                if not candidates:
                    continue
                selected = min(candidates, key=lambda position: areas[position][1].area)
                assignments.append((names[selected], settlement_id))

            source = source_id(cursor, options.source_version)
            cursor.executemany(
                "UPDATE settlements SET county = %s WHERE id = %s",
                assignments,
            )
            checksum = hashlib.sha256(
                json.dumps(assignments, separators=(",", ":"), sort_keys=True).encode()
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
                (source, "settlement_county_assignment", checksum, len(assignments)),
            )
        connection.commit()

    print(json.dumps({
        "status": "ready",
        "areas": len(areas),
        "settlements_assigned": len(assignments),
        "settlements_unassigned": len(settlements) - len(assignments),
    }, sort_keys=True))


if __name__ == "__main__":
    main()
