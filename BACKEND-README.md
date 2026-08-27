# UK Road Tracker backend POC

This API receives selected journey coordinates, sends a reduced trace to OSRM map matching, and returns road-following GeoJSON.

The full Google Timeline JSON stays on the phone. Only coordinates for selected journeys are sent.

The default matcher is the public OSRM demo service, suitable for an end-to-end POC rather than production.
