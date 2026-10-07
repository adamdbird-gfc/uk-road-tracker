# UK Road Tracker backend POC

This API receives selected journey coordinates, sends a reduced trace to OSRM map matching, and returns road-following GeoJSON.

The full Google Timeline JSON stays on the phone. Only coordinates for selected journeys are sent.

The default matcher is the public OSRM demo service, suitable for an end-to-end POC rather than production.

## Cycling matcher (first slice)

`POST /match-cycling` accepts the same 2–500 GPS points as the existing matcher.
`CYCLE_OSRM_BASE_URL` selects a separately prepared bicycle graph (default:
`https://routing.openstreetmap.de/routed-bike`). The URL's `driving` alias does
not change the server's graph. Requests are paced independently of walking,
respect upstream rate limits and have a 55-second overall deadline.

Cycling returns matched route geometry, deduplicated observed-leg distance,
`matching_mode: cycling` and `coverage_attribution: cycle_geometry_only`.
Step geometry is retained as `cycling_geojson`; `road_geojson`, motorway and
A-road attribution remain empty because public OSRM steps do not identify
carriageway versus parallel cycleway. GPS data is transient and not stored.

Run `python -m unittest discover -s tests -p 'test_*.py'` before deployment.
The 0.25.167 Android build requires the endpoint to be deployed first. Its
bicycle trace reduction preserves bends, endpoints and intermediate evidence;
real device routes are still required to validate capture and matching accuracy.
