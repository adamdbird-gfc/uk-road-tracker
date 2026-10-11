-- Preserve the existing provider's cooldown while keeping daily budgets global.
CREATE TABLE map_context_provider_budget (
    provider TEXT PRIMARY KEY,
    failures INTEGER NOT NULL DEFAULT 0,
    next_allowed BIGINT NOT NULL DEFAULT 0
);
INSERT INTO map_context_provider_budget(provider,failures,next_allowed)
SELECT 'https://overpass-api.de/api/interpreter',failures,next_allowed FROM map_context_budget;
-- Recover the minimum global spacing from the committed previous attempt.
-- The full failure cooldown above remains attached to its provider.
UPDATE map_context_budget SET next_allowed=CASE WHEN failures>0 THEN
    next_allowed-LEAST(14400,600*POWER(2,LEAST(failures-1,5)))::BIGINT+60
    ELSE next_allowed END;

ALTER TABLE map_context_budget DROP COLUMN failures;
