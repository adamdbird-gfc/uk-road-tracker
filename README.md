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
