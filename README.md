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


## POC 13 — Canonical M25 Coverage

- Loads OpenStreetMap M25 relation 106164 live through Overpass.
- Separates the two mainline carriageways by their direction around London.
- Uses one carriageway as the fixed canonical road reference.
- Samples that reference into approximately 100 m anchor sections.
- Projects matched M25 geometry from either direction to the nearest canonical
  section within 85 m.
- Each fixed section can only be credited once.
- Completion = credited canonical sections / all canonical sections.
- Display reference length: 188 km / 116.8 miles.

A production version should cache and version the OSM reference server-side
rather than loading Overpass from the browser.


## POC 14 — pause/resume + M25 completion layers

- Easy Import now uses a Pause / Resume toggle instead of a one-way Stop button.
  The current progress is retained while paused and processing resumes from the
  next journey.
- M25 canonical map layers are now labelled Completed / Uncompleted.
- Both use the same line width.
- Completed M25 sections are blue; uncompleted M25 sections are red.


## POC 15 — Canonical motorway network

Generalises the successful M25 fixed-reference model to every motorway ref discovered in matched journeys.

- Includes M-roads and A-road motorways such as A1(M).
- Canonical references are lazy-loaded sequentially from OpenStreetMap/Overpass.
- Both carriageways are sampled every ~100 m and nearby anchors are spatially deduplicated into one physical-road reference.
- Journey geometry from either direction projects to the same canonical anchors.
- Each anchor can be credited only once.
- Completion percentage is calculated independently for every discovered motorway.
- Completed and uncompleted motorway sections can be viewed as separate map layers.

Production should pre-build, cache and version this reference network rather than querying Overpass from each browser.
