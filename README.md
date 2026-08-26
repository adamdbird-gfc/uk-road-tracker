# UK Road Tracker — POC 1

This is the first deliberately small proof of concept for the UK Road Tracker.

## What it does

- Opens as a mobile-friendly web page.
- Lets a user choose a Google Timeline JSON export.
- Processes the JSON locally in the browser.
- Looks for passenger-vehicle journeys.
- Lets the user import all journeys or select them trip-by-trip.
- Draws the selected raw traces on an OpenStreetMap map.

## What it does not do yet

It does **not** yet map-match the GPS traces to the road network or calculate a UK road-completion percentage. That is POC 2.

## Why start here

Before we build backend infrastructure, we want to prove that the real Google Timeline export can be consumed through a simple non-technical mobile interface.

## Running it

Because the app is static, the easiest deployment targets are GitHub Pages, Cloudflare Pages, Netlify or Vercel. For local testing, open `index.html` in a browser. Internet access is required for the Leaflet/OpenStreetMap libraries and map tiles.

## Planned next step

1. Accept selected journeys.
2. Send selected traces to a backend map-matching service.
3. Match each journey to OpenStreetMap road edges.
4. Deduplicate matched edges into a permanent 'roads driven' set.
5. Calculate completion by road class (motorway, A-road, etc.).
6. Render driven and undriven roads distinctly.

## Privacy note

In this POC the selected Timeline JSON never leaves the browser. A future backend version will need a clear privacy/data-retention design before accepting personal location history.
