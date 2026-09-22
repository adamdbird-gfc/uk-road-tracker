"""One-off public OSM reference loader for Roadprints.

This is deliberately a deployment/admin process, never a request-time operation.
It stores public Kent transport geometry only; it neither receives nor persists
Timeline files, journeys, user identifiers, or corrections.
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
import tempfile
import urllib.request
from math import asin, cos, radians, sin, sqrt
from pathlib import Path

import osmium

logger = logging.getLogger("roadprints.pedestrian_reference")

KENT_SOURCE_URL = (
    "https://download.geofabrik.de/europe/united-kingdom/england/"
    "kent-latest.osm.pbf"
)
KENT_SOURCE_KEY = "osm_geofabrik_kent"
KENT_DATASET_KEY = "kent_walkable_network_v1"
KENT_AREA_CODE = "GB-KENT"

# These are public ways that can form a walking/running route.  Motorways and
# trunks are intentionally excluded.  The selection retains normal streets,
# because a footway-only network would strand a walker whenever OSM has not
# mapped a separate pavement.
WALKABLE_HIGHWAYS = {
    "bridleway",
    "corridor",
    "cycleway",
    "footway",
    "living_street",
    "path",
    "pedestrian",
    "primary",
    "residential",
    "road",
    "secondary",
    "service",
    "steps",
    "tertiary",
    "track",
    "unclassified",
}


def _length_metres(coords: list[list[float]]) -> float:
    total = 0.0
    for (lon_a, lat_a), (lon_b, lat_b) in zip(coords, coords[1:]):
        lat1, lat2 = radians(lat_a), radians(lat_b)
        d_lat, d_lon = radians(lat_b - lat_a), radians(lon_b - lon_a)
        value = sin(d_lat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(d_lon / 2) ** 2
        total += 6371008.8 * 2 * asin(min(1.0, sqrt(value)))
    return total


class KentWayCollector(osmium.SimpleHandler):
    def __init__(self, cursor, source_id):
        super().__init__()
        self.cursor = cursor
        self.source_id = source_id
        self.rows = []
        self.rows_loaded = 0

    def _flush(self):
        if not self.rows:
            return
        self.cursor.executemany(
            """
            INSERT INTO pedestrian_reference_segments
                (source_id, source_feature_id, area_code, segment_kind, tags,
                 length_m, geometry)
            VALUES (
                %s, %s, %s, %s, %s::jsonb, %s,
                ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)
            )
            ON CONFLICT (source_id, source_feature_id) DO UPDATE
            SET area_code = EXCLUDED.area_code,
                segment_kind = EXCLUDED.segment_kind,
                tags = EXCLUDED.tags,
                length_m = EXCLUDED.length_m,
                geometry = EXCLUDED.geometry
            """,
            self.rows,
        )
        self.rows_loaded += len(self.rows)
        self.rows = []

    def way(self, way):
        kind = way.tags.get("highway")
        if kind not in WALKABLE_HIGHWAYS:
            return
        try:
            coords = [[float(node.location.lon), float(node.location.lat)] for node in way.nodes]
        except osmium.InvalidLocationError:
            return
        compact = [coords[0]] if coords else []
        compact.extend(point for point in coords[1:] if point != compact[-1])
        if len(compact) < 2:
            return
        public_tags = {
            key: way.tags[key]
            for key in ("highway", "name", "ref", "surface", "foot", "access")
            if key in way.tags
        }
        geometry = json.dumps(
            {"type": "LineString", "coordinates": compact}, separators=(",", ":")
        )
        self.rows.append(
            (
                self.source_id,
                str(way.id),
                KENT_AREA_CODE,
                kind,
                json.dumps(public_tags, separators=(",", ":")),
                _length_metres(compact),
                geometry,
            )
        )
        if len(self.rows) >= 1000:
            self._flush()


def load_kent_pedestrian_reference(cursor) -> None:
    """Load Kent's public walkable network once when explicitly enabled.

    A source failure is reported to the application log and leaves the reference
    incomplete.  It never blocks or changes user import behaviour.
    """
    if os.getenv("LOAD_KENT_PEDESTRIAN_REFERENCE", "").lower() not in {"1", "true", "yes"}:
        return

    cursor.execute(
        "SELECT id FROM reference_sources WHERE source_key = %s",
        (KENT_SOURCE_KEY,),
    )
    source = cursor.fetchone()
    if not source:
        raise RuntimeError("Kent pedestrian reference source is not registered")
    source_id = source[0]

    cursor.execute(
        "SELECT row_count FROM reference_data_loads WHERE source_id = %s AND dataset_key = %s",
        (source_id, KENT_DATASET_KEY),
    )
    completed = cursor.fetchone()
    if completed and completed[0] > 0:
        return

    logger.info("starting Kent public pedestrian reference import")
    with tempfile.TemporaryDirectory(prefix="roadprints-kent-") as directory:
        source_path = Path(directory) / "kent-latest.osm.pbf"
        urllib.request.urlretrieve(KENT_SOURCE_URL, source_path)
        checksum = hashlib.sha256(source_path.read_bytes()).hexdigest()
        collector = KentWayCollector(cursor, source_id)
        collector.apply_file(str(source_path), locations=True)
        collector._flush()

    if not collector.rows_loaded:
        raise RuntimeError("Kent pedestrian reference import produced no usable ways")

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
        (source_id, KENT_DATASET_KEY, checksum, collector.rows_loaded),
    )
    logger.info(
        "Kent public pedestrian reference import complete: %s ways",
        collector.rows_loaded,
    )
