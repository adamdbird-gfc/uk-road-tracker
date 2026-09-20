-- Shared, versioned settlement inventory status. This table contains only
-- public settlement-reference work; it never records journeys, routes or users.
CREATE TABLE IF NOT EXISTS settlement_inventories (
    settlement_id UUID PRIMARY KEY REFERENCES settlements(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'building', 'ready', 'failed')),
    road_count INTEGER CHECK (road_count IS NULL OR road_count >= 0),
    source_key TEXT,
    source_version TEXT,
    inventory_version TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    build_started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS settlement_inventories_status_idx
    ON settlement_inventories(status, requested_at);
