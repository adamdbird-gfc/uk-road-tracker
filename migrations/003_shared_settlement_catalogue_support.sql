-- Shared catalogue support for Step 5.
-- Stores public settlement centres separately from on-demand boundary geometry.
ALTER TABLE settlements
  ADD COLUMN IF NOT EXISTS centre GEOMETRY(POINT, 4326);

CREATE INDEX IF NOT EXISTS settlements_centre_idx
  ON settlements USING GIST (centre);

CREATE TABLE IF NOT EXISTS reference_data_loads (
  source_id UUID NOT NULL REFERENCES reference_sources(id),
  dataset_key TEXT NOT NULL,
  checksum TEXT NOT NULL,
  row_count INTEGER NOT NULL CHECK (row_count >= 0),
  loaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (source_id, dataset_key)
);

COMMENT ON COLUMN settlements.centre IS
  'Public settlement reference centre. No user route or location history.';
COMMENT ON TABLE reference_data_loads IS
  'Idempotency and provenance state for shared reference catalogue loaders.';
