# Great Britain boundary snapshot — 14 October 2026

A complete candidate snapshot now covers all 8,545 entries in the existing
Great Britain catalogue. No codes or names are missing, additional or renamed.
This closes the missing-saved-boundary prerequisite for GB bulk generation;
it does not publish data or establish complete UK coverage.

## Provenance and validation

The official ONS BUA 2022 GB ArcGIS layer returned 8,545 object IDs. These were
frozen first, then fetched in 86 batches of at most 100, with two concurrent
requests. Requests explicitly returned longitude/latitude geometry (EPSG:4326).
Every batch was checked for truncation, missing/unexpected/duplicate object IDs,
duplicate/invalid stable codes, polygon geometry and coordinate range.
Global IDs and names were reconciled to the existing catalogue.

The source last-edit timestamp stayed unchanged before and after download:
1674828904192. Page, source-metadata and source-ID hashes are retained in the
audit. An initial 500-record request attempt returned HTTP404; smaller requests
worked, so the generator defaults to 100. Fetch and complete audit/output took
307.289 seconds. This is a local duration, not a billed runner figure.

## Measured size

| Measure | Bytes |
| --- | ---: |
| Individual public-format boundary candidates | 201,124,487 |
| Combined original GeoJSON | 201,133,073 |
| Gzip original snapshot | 75,367,333 |
| Gzip valid-component-union candidate | 75,366,163 |

This covers boundaries only; road-name inventories and collection files are
additional. The uncompressed boundary payload is about 201 MB: useful evidence
for capacity planning, not proof of the actual Firebase project's remaining
storage/transfer allowance. Retained Hosting releases and verification/client
downloads also matter. No Hosting space or transfer was used to publish this
snapshot, and no paid service or plan change was introduced.

## Eight invalid source geometries

The source MultiPolygons for Stanley (County Durham), Crewe, Bewdley, Nailsworth,
Hillingdon, Weybridge, Hightown (New Forest) and Dawlish had nested/overlapping
shells. All individual polygon components were valid.

Keep the original snapshot untouched. A separate candidate applies the union of
those already-valid polygon components, producing valid polygons with unchanged
bounds and preserving their combined point membership. It removes double-counted
overlaps. A generic parity-based repair was deliberately not used: it could turn
nested duplicate components into holes and remove intended settlement territory.
Invalid individual polygons cause an explicit failure rather than automatic repair.

The candidate contains all 8,545 boundaries, with these eight component-union
normalisations. Source/candidate hashes, reasons and area accounting are retained
in `data-trials/gb-boundary-snapshot-2026-10-14/normalisation-audit.json`.
Areas are longitude/latitude coordinate-area diagnostics, not square metres.
Existing published geometry has not changed. Named-road comparisons are still
required before adopting the candidate, particularly for Crewe's saved inventory.

## Existing boundary comparison

118 of 121 saved boundaries match the source exactly or geometrically. Three
have tiny differences, with identical bounds:

| Settlement | Stable code | Symmetric-difference / union area | Hausdorff distance, degrees |
| --- | --- | ---: | ---: |
| Haverhill | E63003709 | 0.000000344748 | 0.0000000236507 |
| Gravesend | E63005058 | 0.000000385854 | 0.0000000447255 |
| Gillingham (Medway) | E63005204 | 0.000000192040 | 0.0000000269482 |

The largest displacement is approximately five millimetres using a conservative
degrees-to-distance conversion. These are geometric differences measured from
the source files, not an independently established explanation of how the older
representations were produced. Preserve existing valid boundaries during later
publication unless changes are explicitly reviewed; do not silently swap them
solely for coordinate representation.

## Checkpoint and next work

A separate downloadable checkpoint contains original/candidate compressed
GeoJSONs, source metadata/IDs and audit reports. The large geometry datasets are
kept out of Git source history; code and small audit reports are Git-backed.
The archive is 150,806,197 bytes. It has not been deployed or imported to Postgres.

Next: generate road-name inventories against a memory-bounded set of frozen
boundaries and compare the existing 121 inventories; quantify all outputs and
road-name changes before coordinated Firebase publication/Postgres readiness
updates. Scottish S-code support and Northern Ireland source integration remain
separate production gates. Keep queue servicing until completeness/acceptance.

Northern Ireland is absent from this GB source and catalogue. The previously
identified NI dataset remains an unresolved source-access/schema/licence task;
this snapshot must not be described as all UK settlement data.

## Reproduce

Use Python 3.12 and `scripts/requirements-bulk-settlement-trial.txt`:

```bash
python scripts/freeze_gb_settlement_boundaries.py --output /tmp/roadprints-gb-boundaries
python scripts/normalise_gb_boundary_snapshot.py \
  --source /tmp/roadprints-gb-boundaries/boundaries.geojson.gz \
  --output /tmp/roadprints-gb-boundaries/roadprints-gb-boundaries-2022-valid-candidate.geojson.gz
python -m unittest discover -s scripts -p test_freeze_gb_settlement_boundaries.py
```

Eight tests cover Scottish/cross-border identities, missing/truncated/duplicate
records, wrong coordinates, protected publication paths, valid geometry retention
and nested-component repair that preserves membership without creating holes.
CI runs tests only; it does not download or publish the full snapshot.

Source: [ONS Built Up Areas (December 2022) Boundaries GB BGG](https://www.data.gov.uk/dataset/46fff29c-7836-4082-9495-99e87476d402/built-up-areas-december-2022-boundaries-gb-bgg),
via [official ArcGIS service](https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0).
ONS/Ordnance Survey attribution and licence conditions carry forward to any
derived data publication.
