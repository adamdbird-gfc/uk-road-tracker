# UK Road Tracker — POC 4 map fix

This build keeps the successful Timeline diagnostic parser and focuses on map reliability.

Changes:
- removes Subresource Integrity attributes from Leaflet CDN files;
- adds a second Leaflet CDN fallback;
- explicitly invalidates Leaflet size after the hidden map panel becomes visible;
- adds visible map diagnostics;
- reports tile-loading failures separately from map-library failures;
- draws traces non-interactively for lighter mobile rendering.
