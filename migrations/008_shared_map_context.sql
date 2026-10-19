-- Public, rounded map areas only. No user or journey records.
CREATE TABLE map_context_areas (
    area_key TEXT PRIMARY KEY,
    document JSONB NOT NULL,
    refreshed_at BIGINT NOT NULL
);
CREATE INDEX map_context_areas_refreshed ON map_context_areas(refreshed_at);
CREATE TABLE map_context_budget (
    singleton BOOLEAN PRIMARY KEY CHECK (singleton),
    day BIGINT NOT NULL DEFAULT 0,
    requests INTEGER NOT NULL DEFAULT 0,
    failures INTEGER NOT NULL DEFAULT 0,
    next_allowed BIGINT NOT NULL DEFAULT 0
);
INSERT INTO map_context_budget(singleton) VALUES(true);
