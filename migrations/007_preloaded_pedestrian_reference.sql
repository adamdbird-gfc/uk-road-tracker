-- Shared, preloaded pedestrian reference segments.
-- This table holds public network data only. It intentionally has no Journey,
-- user, import, correction or request columns.
CREATE TABLE IF NOT EXISTS pedestrian_reference_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES reference_sources(id),
    source_feature_id TEXT NOT NULL,
    area_code TEXT NOT NULL,
    segment_kind TEXT NOT NULL,
    tags JSONB NOT NULL DEFAULT '{}'::jsonb,
    length_m DOUBLE PRECISION NOT NULL CHECK (length_m >= 0),
    geometry geometry(LineString, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_id, source_feature_id)
);

CREATE INDEX IF NOT EXISTS pedestrian_reference_segments_geometry_gix
    ON pedestrian_reference_segments USING GIST (geometry);
CREATE INDEX IF NOT EXISTS pedestrian_reference_segments_area_idx
    ON pedestrian_reference_segments (area_code, segment_kind);

INSERT INTO reference_sources (
    source_key,
    display_name,
    source_version,
    licence,
    retrieved_at,
    notes
)
VALUES (
    'osm_geofabrik_kent',
    'OpenStreetMap Kent regional extract via Geofabrik',
    'kent-latest',
    'Open Data Commons Open Database Licence (ODbL) v1.0',
    NOW(),
    'Public pedestrian reference pilot. Imported, simplified and indexed before user imports; no personal journey data is stored.'
)
ON CONFLICT (source_key) DO UPDATE
SET display_name = EXCLUDED.display_name,
    source_version = EXCLUDED.source_version,
    licence = EXCLUDED.licence,
    notes = EXCLUDED.notes;