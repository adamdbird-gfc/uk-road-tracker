-- Named local-road catalogues are shared UK reference data only.
-- They contain no users, journeys, route geometry or Timeline data.
CREATE TABLE IF NOT EXISTS settlement_inventory_roads (
    settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    road_name TEXT NOT NULL,
    road_class TEXT NOT NULL
        CHECK (road_class IN ('residential', 'unclassified', 'tertiary', 'living_street')),
    source_feature_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (settlement_id, road_name)
);

CREATE INDEX IF NOT EXISTS settlement_inventory_roads_settlement_idx
    ON settlement_inventory_roads(settlement_id);
