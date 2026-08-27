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
