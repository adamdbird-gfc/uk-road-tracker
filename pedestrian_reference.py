"""Preload public OSM pedestrian references for Roadprints.

This is a deployment/admin process, never a request-time operation. It stores
public network geometry only: no Timeline files, journeys, user identifiers or
corrections are received or persisted.
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
import tempfile
import urllib.request
from dataclasses import dataclass
from math import asin, cos, radians, sin, sqrt
from pathlib import Path

import osmium

logger = logging.getLogger("roadprints.pedestrian_reference")


@dataclass(frozen=True)
class ReferenceArea:
    key: str
    area_code: str
    source_key: str
    display_name: str
    source_url: str


# The UK extract is the intended nationwide source. Kent stays as the proven
# pilot and lets the service remain useful while a national load is prepared.
REFERENCE_AREAS = {
    "kent": ReferenceArea(
        key="kent",
        area_code="GB-KENT",
        source_key="osm_geofabrik_kent",
        display_name="OpenStreetMap Kent regional extract via Geofabrik",
        source_url="https://download.geofabrik.de/europe/united-kingdom/england/kent-latest.osm.pbf",
    ),
    "uk": ReferenceArea(
        key="uk",
        area_code="UK",
        source_key="osm_geofabrik_uk",
        display_name="OpenStreetMap United Kingdom extract via Geofabrik",
        source_url="https://download.geofabrik.de/europe/united-kingdom-latest.osm.pbf",
    ),
}

WALKABLE_HIGHWAYS = {
    "bridleway", "corridor", "cycleway", "footway", "living_street", "path",
    "pedestrian", "primary", "residential", "road", "secondary", "service",
    "steps", "tertiary", "track", "unclassified",
}


def _file_checksum(path: Path) -> str:
    """Hash large public extracts without loading them into memory."""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _length_metres(coords: list[list[float]]) -> float:
    total = 0.0
    for (lon_a, lat_a), (lon_b, lat_b) in zip(coords, coords[1:]):
        lat1, lat2 = radians(lat_a), radians(lat_b)
        d_lat, d_lon = radians(lat_b - lat_a), radians(lon_b - lon_a)
        value = sin(d_lat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(d_lon / 2) ** 2
        total += 6371008.8 * 2 * asin(min(1.0, sqrt(value)))
    return total


class WayCollector(osmium.SimpleHandler):
    def __init__(self, cursor, source_id, area: ReferenceArea):
        super().__init__()
        self.cursor = cursor
        self.source_id = source_id
        self.area = area
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
        geometry = json.dumps({"type": "LineString", "coordinates": compact}, separators=(",", ":"))
        self.rows.append((
            self.source_id, str(way.id), self.area.area_code, kind,
            json.dumps(public_tags, separators=(",", ":")), _length_metres(compact), geometry,
        ))
        if len(self.rows) >= 1000:
            self._flush()


def _requested_area_keys() -> list[str]:
    # Keeping the original flag avoids surprising the existing Kent deployment.
    configured = os.getenv("LOAD_PEDESTRIAN_REFERENCE_AREAS", "").strip()
    if configured:
        return [key.strip().lower() for key in configured.split(",") if key.strip()]
    if os.getenv("LOAD_KENT_PEDESTRIAN_REFERENCE", "").lower() in {"1", "true", "yes"}:
        return ["kent"]
    return []


def _ensure_source(cursor, area: ReferenceArea) -> str:
    cursor.execute(
        """
        INSERT INTO reference_sources
          (source_key, display_name, source_version, licence, retrieved_at, notes)
        VALUES (%s, %s, 'latest', 'Open Data Commons Open Database Licence (ODbL) v1.0', NOW(), %s)
        ON CONFLICT (source_key) DO UPDATE
        SET display_name = EXCLUDED.display_name, source_version = EXCLUDED.source_version,
            licence = EXCLUDED.licence, notes = EXCLUDED.notes, retrieved_at = NOW()
        RETURNING id
        """,
        (area.source_key, area.display_name,
         "Public pedestrian reference, loaded before imports. No personal journey data is stored."),
    )
    return cursor.fetchone()[0]


def _load_area(cursor, area: ReferenceArea) -> None:
    source_id = _ensure_source(cursor, area)
    dataset_key = f"{area.key}_walkable_network_v1"
    cursor.execute(
        "SELECT row_count FROM reference_data_loads WHERE source_id = %s AND dataset_key = %s",
        (source_id, dataset_key),
    )
    completed = cursor.fetchone()
    if completed and completed[0] > 0:
        return

    logger.info("starting public pedestrian reference import: %s", area.key)
    with tempfile.TemporaryDirectory(prefix=f"roadprints-{area.key}-") as directory:
        source_path = Path(directory) / f"{area.key}-latest.osm.pbf"
        logger.info("downloading public pedestrian reference: %s", area.key)
        urllib.request.urlretrieve(area.source_url, source_path)
        logger.info(
            "download complete: %s (%.1f MB)",
            area.key,
            source_path.stat().st_size / (1024 * 1024),
        )
        # Re-reading a multi-gigabyte extract solely to calculate a checksum
        # can exceed the memory envelope of a small one-off job through the
        # operating-system file cache. The URL and downloaded byte size are a
        # stable idempotency marker for this administrative UK load.
        if area.key == "uk":
            checksum = hashlib.sha256(
                f"{area.source_url}\n{source_path.stat().st_size}".encode("utf-8")
            ).hexdigest()
        else:
            checksum = _file_checksum(source_path)
        collector = WayCollector(cursor, source_id, area)
        # The national extract has too many OSM nodes for Osmium's default
        # flex_mem cache on the deliberately small one-off job. Keep the
        # transient node index on the job's local disk instead; only the
        # public pedestrian segments are retained in Postgres.
        node_index_path = Path(directory) / f"{area.key}-node-locations.index"
        logger.info("importing pedestrian ways with disk-backed node cache: %s", area.key)
        collector.apply_file(
            str(source_path),
            locations=True,
            idx=f"sparse_file_array,{node_index_path}",
        )
        collector._flush()

    if not collector.rows_loaded:
        raise RuntimeError(f"{area.key} pedestrian reference import produced no usable ways")
    cursor.execute(
        """
        INSERT INTO reference_data_loads (source_id, dataset_key, checksum, row_count)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (source_id, dataset_key) DO UPDATE
        SET checksum = EXCLUDED.checksum, row_count = EXCLUDED.row_count, loaded_at = NOW()
        """,
        (source_id, dataset_key, checksum, collector.rows_loaded),
    )
    logger.info("public pedestrian reference import complete: %s (%s ways)", area.key, collector.rows_loaded)


def load_configured_pedestrian_references(cursor) -> None:
    """Idempotently load explicitly configured public reference areas.

    Unknown keys fail closed; a bad deployment variable cannot silently download
    an unintended region. A source failure is handled by the caller so it never
    changes existing user import behaviour.
    """
    for key in _requested_area_keys():
        area = REFERENCE_AREAS.get(key)
        if not area:
            raise RuntimeError(f"Unknown pedestrian reference area: {key}")
        _load_area(cursor, area)
