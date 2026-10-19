# Settlement coverage and Scottish trial — 14 October 2026

The bulk-file method also generates Scottish inventories within a modest local
resource footprint. National publication remains a later, gated step: only
121 of 8,545 catalogue settlements currently have saved reference boundaries
and inventories, and Northern Ireland is absent from the catalogue.

## Saved-file coverage audit

| Code prefix | Catalogue entries | Saved boundaries | Available inventories |
| --- | ---: | ---: | ---: |
| E | 7,091 | 119 | 119 |
| W | 622 | 1 | 1 |
| S | 822 | 0 | 0 |
| K | 10 | 1 | 1 |
| Total | 8,545 | 121 | 121 |

8,424 catalogue entries have no boundary file saved in this repository.
This measures the saved public reference files, not the live Postgres spatial
table: the database may contain additional boundaries. Machine-readable audit:
`data-trials/settlement-coverage-2026-10-14/coverage.json`, source main commit
31596cef96539f9043a04c151f82af581621693e.

## Scottish candidate generation

Official ONS BUA 2022 GB query returned the selected Scottish identities and
valid geometries. The three selected boundaries were checked against the
Scotland extraction polygon and were covered to floating-point precision.

| Settlement | Stable code | Candidate distinct road names | Candidate JSON bytes |
| --- | --- | ---: | ---: |
| Edinburgh | S45000308 | 4,217 | 76,949 |
| Glasgow | S45000364 | 6,088 | 106,360 |
| Pitlochry | S45000635 | 78 | 1,452 |

There are no saved Scottish baseline inventories, so these are first-generation
candidates, not proven matches to published totals. The unchanged bulk rules
include named residential, unclassified, tertiary and living_street ways that
intersect the official boundary, with exact stripped distinct names. They are
not counts of every road segment or walking path.

Scotland's extract was 327,473,479 bytes (312.30 MiB); supplier MD5 verification
passed and SHA-256 is retained. Download took 56.832 seconds. Candidate
generation including input hashing took 26.332 seconds, peak process RSS
1,181,776 KiB (1.127 GiB). Outputs total 184,761 bytes, excluding boundary
files. These are local measurements, not a national performance projection,
Hosting usage or billed runner minutes.

Snapshot/boundary hashes, timestamp, counts and output hashes are in
`data-trials/settlement-coverage-2026-10-14/scottish-trial.json`.

## Northern Ireland dependency

An official Open Data NI listing provides Settlement Development Limits 2015
and a GeoJSON resource, linked to NISRA's settlement classification/documentation.
This is a candidate compatible national source, not yet an imported dataset.
The listing fetch returned HTTP 429 and the public resource-metadata API returned
HTTP 403 in this environment. No boundary download, schema/geometry audit,
licence confirmation or completeness claim was made.

References:

- [Settlement Development Limits 2015](https://admin.opendatani.gov.uk/dataset/settlement-development-limits-2015)
- [Listed GeoJSON resource](https://admin.opendatani.gov.uk/dataset/settlement-development-limits-2015/resource/a1daf6e3-83da-4729-b444-84c7cfca18cc)
- [NISRA settlement documentation](https://www.nisra.gov.uk/publications/settlement-2015-documentation)
- [NISRA urban/rural geography](https://www.nisra.gov.uk/support/geography/urban-rural-classification)

Before adoption, verify accessible source bytes, settlement names and N codes,
CRS, geometry validity, duplicates, licensing and version lineage. Preserve
existing GB IDs; Northern Ireland requires a separate source in the catalogue
and database rather than an ONS GB endpoint lookup. Statistical boundaries
from different years/sources need explicit provenance and consistent treatment.

## Next controlled work

1. Freeze the full GB boundary snapshot using paginated/bulk source retrieval,
   validate all identities and measure compressed/publication size.
2. Resolve the Northern Ireland source dependency and audit its schema/coverage.
3. Extend generation/publication support to S and the selected NI code scheme;
   compare dense, small and cross-border examples, preserve user progress.
4. Measure a national build and actual Firebase plan/usage; only then publish a
   versioned snapshot and reconcile the corresponding Postgres inventory states.
5. Retire regular discovery-driven polling after completeness/serving acceptance,
   retaining controlled refresh/recovery paths.

No production database, Hosting release, queue schedule, Android source or APK
was changed for this audit and trial.

## Reproduce the Scottish experiment

Install `scripts/requirements-bulk-settlement-trial.txt`. Download only Scotland:

```bash
python scripts/download_bulk_settlement_trial.py \
  --regions scotland --output /tmp/roadprints-scottish-inputs
```

Save the official ONS service's GeoJSON query response as
`/tmp/roadprints-scottish-inputs/boundaries.geojson`, using:

- Service: `https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/BUA_2022_GB/FeatureServer/0/query`
- where: `BUA22CD IN ('S45000364','S45000308','S45000635')`
- outFields: `BUA22CD,BUA22NM`; returnGeometry: `true`; f: `geojson`.

```bash
python scripts/scottish_bulk_settlement_trial.py \
  --inputs /tmp/roadprints-scottish-inputs --output /tmp/roadprints-scottish-candidates
```

The generator validates selected identities, valid geometries and the snapshot
hash before processing, and rejects the repository's published inventory
directory as output. Seven local bulk-rule/safety tests passed. CI validates
the tools only; it does not fetch or publish the regional dataset.

Data: © OpenStreetMap contributors, ODbL 1.0, supplied by
[Geofabrik Scotland](https://download.geofabrik.de/europe/united-kingdom/scotland.html).
ONS/Ordnance Survey boundary attribution and licence conditions remain applicable.
