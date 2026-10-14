# Bulk settlement inventory trial — 14 October 2026

The initial bulk-file trial is promising: it reproduces Gravesend exactly and
finds only small, evidenced differences for Cardiff and cross-border Chester.
Production inventories, queue behaviour and the Android app are unchanged.
Candidate data is not approved for publication by this trial.

## Measured results

| Settlement | Published names | Bulk names | Comparison |
| --- | ---: | ---: | --- |
| Gravesend, E63005058 | 488 | 488 | All 488 names identical |
| Cardiff, W45000596 | 3,313 | 3,315 | Ash Place and Hazel Place added; Clas ISan becomes Clas Isan |
| Chester, K08000001 | 1,239 | 1,240 | Bachelors Court added; all 1,239 existing names retained |

Three Geofabrik extracts (Kent, Cheshire, Wales) downloaded concurrently in
44.104 seconds. Combined input size was 241,548,352 bytes (230.36 MiB).
Supplier MD5 checks passed; SHA-256 hashes are retained in the report.
All extracts identify the same OSM timestamp, 2026-10-13T20:21:31Z.

Processing, input hashing and output generation took 16.315 seconds in this
workspace. Peak process RSS was 705,812 KiB (689.27 MiB). The three candidate
JSON inventories total 83,182 bytes. This excludes boundaries, which remain
the identical saved files. These are local measurements, not GitHub billed
minutes, Hosting usage or a UK-wide forecast.

The combined extraction polygons cover the three saved boundaries to
floating-point precision. Region polygons are approximate checks, not proof
that every relevant OSM object exists. Missing node locations abort processing.

## Comparison method

Use unchanged settlement boundaries and codes; include only named residential,
unclassified, tertiary and living_street ways intersecting a boundary. Strip
outer whitespace, retain case and count distinct names, matching the current
builder's declared rules. A spatial index dispatches qualifying ways to
settlements; each extract is read once. Sets deduplicate repeated ways/names
from overlapping extracts and combine both sides of Chester's national border.

The changed road names have concrete OSM way evidence in
`data-trials/bulk-settlement-2026-10-14/difference-evidence.json`. The three added
roads and corrected name occur on residential ways wholly inside the matching
saved boundary. This confirms inclusion under the trial rules. It does not
establish why an earlier live Overpass inventory differed: object/snapshot
changes and query completeness need distinction before adopting refreshed data.
Case-only renames also require checking progress identity handling before rollout.

Machine-readable measurements, snapshot hashes, complete name differences and
baseline/boundary/candidate hashes are in
`data-trials/bulk-settlement-2026-10-14/comparison.json`.

## Coverage and national expansion gates

The existing settlement catalogue contains 8,545 entries: 7,091 E, 622 W,
822 S and 10 K codes. There are no Northern Ireland entries. It is therefore a
Great Britain catalogue, not a complete UK settlement catalogue.

The queue publisher and explicit-code parser currently support E/W/K only;
Scottish S codes need support before national production generation. Boundary
availability/validity must be audited for all catalogue entries, and compatible
Northern Ireland settlement identities/boundaries must be sourced separately.
The road-data download itself can cover the whole UK, including Northern Ireland.

Next controlled slice:

1. Freeze a catalogue/boundary snapshot, verify stable IDs and all nation coverage.
2. Extend bulk processing to more existing inventories, including Scotland and
   dense/rural settlements; compare counts and name changes.
3. Benchmark a full UK road snapshot and many boundaries together. Measure
   memory, runtime, outputs and download size; do not extrapolate these three
   settlements into a promised national price or duration.
4. Prepare versioned candidate publication, identity/progress checks and rollback;
   measure storage/transfer against the actual Firebase plan before deployment.
5. Retire discovery-driven queue polling only after complete coverage and serving
   verification. Retain a manual recovery route and choose an occasional refresh
   cadence separately; OSM/reference data will continue to change.

No new paid service, full-national download, live publication, queue retirement,
APK or app change was made for this trial.

## Reproduce

Use Python 3.12 and an isolated environment. Keep downloads and candidate outputs
outside the published `settlement-inventories-v1` directory; the generator rejects
that directory as output. `latest` downloads change over time, so compare retained
SHA-256 and OSM timestamps when reproducing the measured snapshot.

```bash
python -m pip install -r scripts/requirements-bulk-settlement-trial.txt
python scripts/download_bulk_settlement_trial.py --output /tmp/roadprints-bulk-inputs
python -m unittest discover -s scripts -p test_bulk_settlement_inventory_trial.py
python scripts/bulk_settlement_inventory_trial.py \
  --inputs /tmp/roadprints-bulk-inputs --output /tmp/roadprints-bulk-candidates
```

The six unit tests check highway/name rules, outside/unnamed ways, geometric
crossings, overlap deduplication, polygon holes and publication-directory safety.
CI runs these tests only; it does not download regional data or publish anything.

## Data and implementation sources

- [Geofabrik Kent](https://download.geofabrik.de/europe/united-kingdom/england/kent.html)
- [Geofabrik Cheshire](https://download.geofabrik.de/europe/united-kingdom/england/cheshire.html)
- [Geofabrik Wales](https://download.geofabrik.de/europe/united-kingdom/wales.html)
- [Geofabrik United Kingdom](https://download.geofabrik.de/europe/united-kingdom.html)
- [Pyosmium handler/location API](https://docs.osmcode.org/pyosmium/latest/reference/Handler-Processing/)

OSM road data: © OpenStreetMap contributors, ODbL 1.0; supplied by Geofabrik.
Existing ONS/Ordnance Survey boundary attribution remains applicable to saved
boundaries. Licence/attribution and stable-ID requirements carry forward to
any national publication.
