# UK Road Tracker — POC 4 map fix

This build keeps the successful Timeline diagnostic parser and focuses on map reliability.

Changes:
- removes Subresource Integrity attributes from Leaflet CDN files;
- adds a second Leaflet CDN fallback;
- explicitly invalidates Leaflet size after the hidden map panel becomes visible;
- adds visible map diagnostics;
- reports tile-loading failures separately from map-library failures;
- draws traces non-interactively for lighter mobile rendering.


## POC 6
Adds live single-journey road matching via the Render API.


## POC 7 — cumulative credited roads

Adds:
- a cumulative credited-road map layer;
- geometry-segment deduplication so repeated matched travel is drawn once;
- map layers for credited roads, matched journeys and raw Timeline traces;
- simple HIGH / REVIEW / LOW match quality labels based on point coverage and OSRM confidence;
- a clear-matches control.

Important: this is still geometry-level deduplication, not canonical OpenStreetMap
edge-ID deduplication. True OSM road-edge credit belongs in the later v9-style
matching backend.


## POC 8 — easy vs detailed import
- Easy import automatically matches every usable journey sequentially.
- Detailed import preserves journey-by-journey matching.
- Journeys with fewer than 2 Timeline points are excluded from matching and road credit, but remain visible in an ignored-journeys audit.


## POC 9 — Motorway Progress
Backend v0.5 requests OSRM route steps and returns M-numbered motorway refs plus geometry.
Frontend progressively builds a motorway dashboard from unique matched geometry.
Percentages are POC estimates: unique returned geometry divided by a route-length reference denominator.


## POC 10 — Corridor-based motorway coverage

Motorway completion no longer deduplicates only identical OSRM geometry.

Matched motorway geometry is sampled every 25 m and collapsed into 100 m
Web-Mercator corridor cells. This means:
- repeat journeys on the same motorway section count once;
- clockwise/anticlockwise carriageways usually collapse into the same corridor;
- small variations in OSRM returned geometry do not create duplicate mileage.

The numerator is therefore an estimate of physical motorway corridor covered.
Canonical OSM section IDs remain the preferred production implementation.


## POC 11 — conservative motorway stats

Motorway completion percentages are temporarily removed.

The dashboard now shows matched motorway distance by road reference only. This
avoids presenting a misleading completion percentage while canonical motorway
section IDs are being designed.

Next acceptance test:
- M25 outbound journey credits motorway sections.
- Reverse journey over the same physical M25 corridor should add little or no
  new canonical section coverage once the section model is implemented.


## POC 12 — miles / km toggle

Motorway distance defaults to miles for UK users. A Miles / km toggle above
the motorway list switches the displayed matched distances instantly without
re-running Timeline processing or road matching.
