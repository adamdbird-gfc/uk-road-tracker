# Complete GB road inventory candidates — 14 October 2026

All 8,545 catalogue settlements now have independently generated candidate
road-name inventories. They are frozen-data candidates, not a live release.
No existing app data, Hosting files, Postgres rows or queue schedule changed.

## Measured generation

| Measure | Result |
| --- | ---: |
| Settlement inventories | 8,545 |
| Distinct road-name entries summed across settlements | 748,429 |
| Candidate JSON inventory bytes | 13,249,631 |
| Qualifying OSM ways inspected | 1,560,527 |
| Existing inventories matching exactly | 95 |
| Existing inventories differing | 26 |
| Settlements with zero eligible named roads | 206 |
| Incomplete approximate extraction coverage | 0 |
| OSM processing | 275.157 seconds |
| Total generation, including validation/hash/output | 288.265 seconds |
| Peak process RSS | 4,865,632 KiB (about 4.64 GiB) |
| Temporary sparse node-index file | 3,758,099,952 bytes |

The node index was removed after successful generation. Use a disk-backed
`sparse_file_array` location cache to avoid retaining all node locations in RAM.
These are local measured resources, not billed Actions minutes or a guarantee
that another runner will perform identically.

The Geofabrik Great Britain extract was 2,186,375,537 bytes, downloaded in
214.549 seconds. Supplier MD5 passed; SHA-256 is retained in the report. Its
OSM timestamp is 2026-10-13T20:21:31Z. The valid, component-union boundary
snapshot SHA-256 is recorded separately. All catalogue IDs exactly match the
boundary snapshot before processing.

Input source: [Geofabrik Great Britain](https://download.geofabrik.de/europe/great-britain.html).
Location-cache reference: [Osmium index types](https://docs.osmcode.org/osmium/latest/osmium-index-types.html).

## Existing inventory differences

The generator uses the current rule: distinct stripped names on residential,
unclassified, tertiary or living_street ways intersecting the frozen boundary.
It retains case rather than silently merging or renaming entries.

Gravesend matches all 488 names. Chester retains its 1,239 names and adds
Bachelors Court. Cardiff adds Ash Place/Hazel Place and changes Clas ISan to
Clas Isan. Crewe retains all 915 published names and adds 12 names after using
the valid union boundary/source snapshot. Derby changes eight Saint spellings
to St spellings while keeping the same total. These are candidate name changes,
not approved changes to user progress identity.

Some differences include removals: Burton End's three terminal/roundabout names
and other town entries need source classification/identity review. Same total
does not imply the same road universe. Retain the current 121 live inventories
until comparisons and progress/cache version semantics are accepted. The full
diff for all 26 is recorded in
`data-trials/gb-road-inventories-2026-10-14/generation-audit.json`.

## Zero eligible counts

Zero here means no *named ways in the four eligible local-road classes*, not
that a settlement contains no roads or that the download failed. The full
extract polygon covers every selected boundary to the configured tolerance;
missing node coordinates abort generation rather than being silently skipped.

An independent regional pass examined all highway classes for Wallend and
Dungeness in the same dated Kent snapshot. Wallend's named ways were primary,
secondary and track; its local-class ways were unnamed. Dungeness's named ways
were service roads (including industrial-site streets); unclassified ways were
unnamed. Neither qualifies under the unchanged rules. Evidence is in
`data-trials/gb-road-inventories-2026-10-14/zero-count-evidence.json`.

These two examples do not establish individual acceptance for all 206 zero-count
settlements. Before publication, define how known-empty eligibility is represented
in the API/app/queue so zero does not imply instant completion or endless retries.
Do not broaden highway eligibility merely to manufacture nonzero totals.

## Publication size and gates

Combined candidate inventory files (13,249,631 bytes) and previously measured
boundary files (201,124,487 bytes) are **214,374,118 bytes**, about 214 MB,
before collection/catalogue/manifest files and retained Hosting releases.
Transfer depends on actual verification/download frequency and client caching.
This is manageable payload evidence, not confirmation of the project's billing
plan or remaining allowance; actual Hosting usage still needs verification.

The downloadable inventory-only checkpoint is 6,921,375 bytes and contains
8,545 candidate JSON files, per-file hashes, generation audit and sampled zero
evidence. Raw road extracts and temporary node indexes are excluded.
It is separate from the source/boundary checkpoint and is not in Git history.

Remaining release gates:

1. Preserve existing live inventories/geometry unless their updates are accepted;
   handle road-name aliases and existing cached counts/version semantics.
2. Decide zero-eligible status semantics and verify samples beyond Kent.
3. Extend publication, reconciliation and explicit-code support to Scottish S
   codes; stage-only verification must precede any live release.
4. Import/reconcile corresponding Postgres readiness/count/version rows explicitly,
   creating missing inventory rows while preserving settlement UUIDs. Firebase
   publication does not automatically update Postgres.
5. Confirm actual Hosting plan/usage and bounded release retention; minimise
   full-dataset verification/deployment duplication.
6. Resolve Northern Ireland source access, schema/licence, stable IDs and boundaries.
   This GB snapshot is not complete UK coverage.
7. Retire discovery-driven polling only after serving/completeness acceptance,
   retaining controlled refresh and recovery paths.

The current JSON carries road names/counts, not per-name road classes/OSM IDs.
It supports the current batch count/public-file contract; do not pretend it has
populated the database's detailed `settlement_inventory_roads` table. A richer
feature import is a separate concern if that table is needed.

## Reproduce

Use Python 3.12 and `scripts/requirements-bulk-settlement-trial.txt`. Supply
the frozen valid GB boundary snapshot and a checksum-recorded Great Britain
PBF plus extraction polygon and `downloads.json` from the existing downloader.

```bash
python scripts/download_bulk_settlement_trial.py \
  --regions great-britain --output /path/to/gb-road-inputs
python scripts/generate_gb_road_inventory_candidates.py \
  --boundaries /path/to/roadprints-gb-boundaries-2022-valid-candidate.geojson.gz \
  --source-dir /path/to/gb-road-inputs --output /tmp/roadprints-gb-inventories
python -m unittest discover -s scripts -p test_generate_gb_road_inventory_candidates.py
```

Three tests cover name diffs, protected publication directories and a real tiny
PBF fixture processed through the disk-backed index, with eligible-class filtering,
candidate output and index cleanup. CI tests only; no national download or release.

Road data: © OpenStreetMap contributors, ODbL 1.0, supplied by Geofabrik.
Existing ONS/Ordnance Survey boundary attribution/licence conditions continue
to apply to any derived publication.
