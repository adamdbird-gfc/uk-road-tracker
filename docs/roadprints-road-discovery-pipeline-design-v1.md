# Roadprints road-discovery pipeline design v1

Status: implementation design for Step 8  
Date: 20 September 2026  
Privacy model: device-local personal evidence; shared UK references only

## Purpose

Move road discovery out of screen rendering and into a durable local derivation stage. A successful match should create reusable road evidence once, then Map, Journeys and Progress should read that evidence without re-parsing every matched route on each render.

## Boundary retained

- Timeline files, Journey geometry, matching results, corrections, progress and achievements remain on the device.
- Render may provide shared UK references and transient matching only. It must not retain personal route history or discovery results.
- The existing matcher, map archive and visible Progress screens remain the fallback while the derived store is proved.

## Derived road evidence

For each successfully matched Journey, store a local record keyed by Journey identity containing:

- Journey identifier, timestamp and travel mode;
- motorway and A-road references, including Great Britain/Northern Ireland identity;
- local/B/other-road discovery labels and matched geometry evidence;
- canonical motorway/A-road coverage contribution identifiers where available;
- derivation version, completed timestamp and any recoverable error.

Local-road evidence remains separate from settlement inventory building. Encountering a local road records the touch; locality/inventory enrichment may run later without delaying matching.

## Processing behaviour

1. The transient matcher returns matched geometry as today.
2. The Journey archive is saved as today.
3. A local derive-road-discovery stage extracts the Journey's road evidence and checkpoints it.
4. Coverage and discovery displays consume derived evidence when present.
5. On restart, any archived Journey without current-version derived evidence is queued for local derivation before its derived totals are shown.
6. A correction invalidates only the evidence derived from the corrected Journey/segments, then recomputes the affected coverage.
7. Failed derivations remain visible as retryable local work; they do not discard the matched Journey.

## First implementation slice

Add a versioned IndexedDB road-discovery store and derive evidence immediately after a successful road match. Keep the current render-time ledger as a comparison/fallback until the new store reproduces motorway, A-road and local-road results for the ten-journey and large-file tests.

Settlement catalogue requests, new matcher behaviour and achievement-rule changes are outside this first slice.

## Acceptance criteria

- A refresh does not require matched Journeys to be re-matched or rediscovered.
- Road, A-road and local-road counts match the current proven screens for the same saved data.
- Repeated Timeline activities retain mileage rules while evidence remains deduplicated.
- A correction recomputes only affected discovery/coverage evidence.
- No personal Journey, route, derivation record or user identity is stored remotely.
