# Shared map context and walking outcomes — release 0.25.180

Implements backlog #64–#67. Real-device validation remains #68.

## Shared map areas

Android requests `GET /map-context/v1/{lat_cell}/{lon_cell}`, where the cells are floor(latitude * 100) and floor(longitude * 50). No exact fix, journey identifier, route or personal place name is submitted. The response has schema_version 1, area_key, bounds, elements, refreshed_at and expires_at (UTC epoch seconds), and OpenStreetMap attribution. Elements contain only context tags, geometry and bounds.

The existing backend/database share areas across users and restarts. A seven-day cache is bounded to 256 areas and 64 MB of JSON payload. A PostgreSQL session advisory lock prevents concurrent upstream fills, and committed reservations persist even if a fill fails. Cache hits do not consume upstream budget. Defaults: 200 upstream requests/day globally, at least 60 seconds between fills, persistent per-provider failure delays starting at 10 minutes and increasing to four hours. A cache miss while constrained returns HTTP 503 with Retry-After; recording continues without unavailable context.

`CONTEXT_INTERPRETER_URL` can select a dedicated interpreter without changing the app. Initial cache fills still use public Overpass by default, via the existing Kumi interpreter, centrally and under the shared limits; this release does not deploy a nationwide self-hosted OpenStreetMap extract. `CONTEXT_DAILY_UPSTREAM_LIMIT` controls the server budget. No new paid service is provisioned. Switching providers retains previous provider cooldowns; global daily counts and minimum spacing are never reset.

Phones cache up to 64 areas, honour server expiry, persist a 48/day request budget and cooldown across capture restarts, and honour Retry-After. Failures progressively back off. Requests are prioritised for slow/stationary fixes, pauses and uncertain mode transitions. Continuous movement recording remains independent of map lookups.

## Consent

Map-assisted context remains opt-in. New users see the choice and explanation during tracking setup. Existing users see one explanation, retaining their current preference until they choose. Utilities > Tracking settings uses the same preference and explanation. Closing the explanation retains the existing choice; turning it off stops further requests and prevents cached evidence being used for context decisions.

## Walking outcomes

Android opts into `walking_recovery` at /match-walking. Two-point traces are retained as insufficient_detail without requesting a speculative route. Known no-match results become non-error recorded outcomes: insufficient_detail for up to three submitted points, otherwise no_route_found. The latter means the matcher found no suitable route; it does not prove that the person was in a field or that no map path exists. Temporary HTTP failures remain retryable errors. Older clients retain their existing matching contract.

Recorded-only walks keep original geometry and distance, contribute to walking history/distance, and have no matched road/path geometry to credit. Automatic matching skips completed recorded-only outcomes; explicit rematch remains available. Changing transport or correcting a trace clears the outcome.

Partial walking routes need real matched geometry, complete non-overlapping input-point accounting and a verified deduplicated distance, with a plausibility bound against journey evidence. Chunked walking recovery shares waypoints without sharing travelled intervals. Original trace and distance remain available, and discovery uses only returned matched road/path features. Existing fully validated native-walk distance correction remains in place, preserving original_distance_meters.

## Validation

Backend regression workflow uses an isolated PostgreSQL service to test shared cache reuse, persistent failure delays/day budgets and concurrent fill suppression. Walking tests distinguish no-match outcomes from outages and preserve the legacy contract. Android checks cover consent, restart-safe budgets, retained trace/distance, queue skipping, partial-result validation and retryable router errors. #68 records real-device scenarios and stays open until those journeys have been checked.


## Provider adapter and object identity (0.25.182)

`CONTEXT_PROVIDER_FORMAT=postpass` enables the fixed, read-only Postpass query; set `CONTEXT_INTERPRETER_URL=https://postpass.geofabrik.de/api/interpreter`. Default format remains `overpass`. Provider selection is explicit: there is no automatic fan-out or budget reset. Both formats use the existing global spacing, daily budget, durable provider backoff, cache size and response bounds.

Postpass requests only a validated UK area envelope and allowed context tags. Geometry is clipped to the buffered area to bound payload size; there is at least 333 m latitude buffer outside the phone's core cell, exceeding the 50 m maximum accuracy used for destination evidence. Separate line components stay separate, and polygon courtyard holes remain holes. A truncated/oversized/invalid response is unavailable, never positive arrival evidence. Public way/relation IDs survive sanitisation; names and addresses are not cached. The phone remains responsible for dwell and confidence.

Provider protocol/schema references: https://github.com/woodpeck/postpass-ops and https://github.com/woodpeck/postpass-ops/blob/main/SCHEMA.md. The public instance is an interim beta source, not a service guarantee; a dedicated source remains a future scaling choice. No new paid infrastructure is provisioned.

Connection failure logs now include a fixed diagnostic category without exception text, query, coordinates or identifiers. Live fill and repeat-hit verification must still be recorded separately from automated checks.
