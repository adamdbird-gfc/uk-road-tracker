# Shared local-road ingestion v1

## Decision

Roadprints uses two complementary shared reference sources:

- **OS Open Roads** remains the national reference for motorway and A-road work.
- A versioned **OpenStreetMap Great Britain** extract supplies local-road inventories.

OS Open Roads does not retain the detailed `residential`, `tertiary` and `living_street` classifications used by the existing Roadprints local-road experience. Replacing those rules with an OS-only interpretation would change established town counts.

## Boundary and eligibility rules

Each local-road catalogue is built from:

1. an official ONS Built-up Area 2022 boundary;
2. named OSM ways whose `highway` value is one of:
   `residential`, `unclassified`, `tertiary`, or `living_street`;
3. a geometric intersection with that settlement boundary.

The catalogue contains a deduplicated road name, its class, source feature identifier and source/version metadata. It contains no Journey, user, device, route or Timeline data.

## Controlled ingestion

A reference-data administrator downloads the Great Britain extract temporarily, filters it once with `osmium`, and runs:

```bash
osmium tags-filter great-britain-latest.osm.pbf \
  w/highway=residential,unclassified,tertiary,living_street \
  -o eligible-local-roads.osm.pbf

osmium export eligible-local-roads.osm.pbf \
  -o eligible-local-roads.geojsonseq -f geojsonseq

DATABASE_URL=… python scripts/import_local_road_inventory.py \
  --features eligible-local-roads.geojsonseq \
  --settlement E63005466 \
  --settlement E63005580 \
  --source-version YYYY-MM-DD
```

The raw source and intermediate files are discarded after a verified import. The API and browser never download them.

## Safety and readiness

The importer writes each requested settlement in one transaction. It clears and replaces a catalogue only as the import completes, then sets the inventory to `ready` with an exact road count. An unsuccessful run does not claim readiness.

This is shared reference administration, not user background processing. Durable user import queues remain a later, explicitly approved enhancement.
