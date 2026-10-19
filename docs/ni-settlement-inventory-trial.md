# Northern Ireland settlement inventory trial — 15 October 2026

This stage prepares candidate boundaries for all 527 NISRA settlements and new
road inventories for Belfast City, Derry City and Newry. It does not publish
reference files, register Postgres rows, change the app or build an APK.

## Sources and identity

Use NISRA's official SDL15 shapefile ZIP:
https://www.nisra.gov.uk/files/nisra/publications/SDL15-ESRI_format.zip

The reviewed SHA-256 is
`fa880d3effb0e05923a7a27d2ae44191394737a926af0b61ed1e88c30f4b107c`.
OpenDataNI lists this dataset under the UK Open Government Licence:
https://admin.opendatani.gov.uk/dataset/settlement-development-limits-2015
Attribute NISRA Settlement Development Limits 2015 in downstream data credits.
Keep OSM attribution/ODbL requirements with the derived road inventories.

The 527 official `N11......` codes are unique, with non-empty unique source
names. Names remain verbatim, including Belfast City and the separate
Metropolitan settlements; this trial does not merge their identities. NISRA's
2021 Census methodology says it reused the 2015 settlement boundaries:
https://www.nisra.gov.uk/system/files/statistics/settlement-methodology-for-2021-census.pdf
This is a statistical settlement baseline, not a current developed-land survey.
NISRA describes its inclusion threshold as 20 households and 50 residents.
The ZIP's internal filename contains `above-500-threshold`; this wording is
not used as an inclusion filter, and all 527 records are retained.

The alternative OpenDataNI GeoJSON was also downloaded. It contains projected
Irish Grid coordinates without a CRS declaration, has 18 invalid geometries,
and differs geometrically from the shapefile in places (maximum measured
Hausdorff distance about 11.94 m). Do not combine its geometries with this
candidate snapshot. Prefer the shapefile's declared EPSG:29902 definition.

Road input: pinned Geofabrik Ireland and Northern Ireland extract dated
2026-10-14, 415,543,841 bytes. Its supplier MD5 and local SHA-256 both passed:
https://download.geofabrik.de/europe/ireland-and-northern-ireland-261014.osm.pbf
The audit records its hash and OSM timestamp. The larger extract covers the
Republic of Ireland too; membership in the selected NI settlement polygons
controls which eligible names are counted.

## Boundary validation

The original ZIP remains unchanged. A separate candidate repairs nine invalid
source geometries using GEOS `make_valid`, gated on polygon-only output,
validity, relative area change at most 1e-8 and Hausdorff distance at most
1e-6 source metres. The largest measured relative area change was about
2.97e-15. Conversion to WGS84 uses the available non-ballpark TM65 operation
with stated 1 m accuracy. All 527 resulting geometries are non-empty and valid.

Candidate GeoJSON is 11,476,250 bytes, or 4,380,051 bytes compressed. Its
uncompressed SHA-256 is
`2320848e0ccfe9f13e4c0c2b25ae047d5217ceff10d27ee3d74ac07badb344c0`.
The gzip hash is runtime-dependent; use the canonical uncompressed hash when
checking reproduction across Python versions.

Four original source overlaps exceed one square metre, all involving Belfast:

| Neighbour | Overlap, square metres |
|---|---:|
| Metropolitan Lisburn | 17,896.78 |
| Metropolitan Castlereagh | 7,385.67 |
| Metropolitan North Down | 52.77 |
| Metropolitan Newtownabbey | 27.01 |

These are not silently trimmed, merged or reassigned. Their significance for
road membership needs review before national acceptance. The trial uses the
existing GB rule: an eligible way intersecting a polygon contributes its name.

## Trial result

| Official code | Settlement | Candidate distinct road names |
|---|---|---:|
| N11000151 | Belfast City | 3,594 |
| N11000243 | Derry City | 769 |
| N11000537 | Newry | 352 |

OSM processing took 32.424 seconds, using a measured peak RSS of
1,122,048 KiB (about 1.07 GiB). The disk-backed node index peaked at
855,638,816 bytes and was removed afterwards. The three inventory files total
82,273 bytes. Download time was 104.408 seconds in this runtime.

These are new candidates without previous NI inventories for comparison.
The existing GB accumulator is reused unchanged: named residential,
unclassified, tertiary and living_street ways; exact stripped distinct names;
way/polygon intersection. Snapshot/tagging quality still applies. For example,
one Derry name resembles a house-address label (`53A & 53B Limavady Road`);
review such source names before acceptance rather than changing rules ad hoc.
The trial is not a benchmark of full 527-settlement processing.

## Reproduce and advance

Install `scripts/requirements-ni-settlement-trial.txt` in an isolated environment.
Run `prepare_ni_boundaries.py --source-zip SOURCE.zip --output BOUNDARIES`.
Download the pinned PBF, verify the supplier MD5, and retain the audit's
`downloads.json` plus the checked extraction `.poly` beside it. Then run:

```bash
python scripts/ni_settlement_inventory_trial.py \
  --boundary-dir BOUNDARIES --source-dir SOURCES --output CANDIDATES
python -m unittest discover -s scripts -p test_ni_settlement_trial.py -v
```

The saved checkpoint `roadprints-ni-boundaries-and-trial-2026-10-15.zip`
contains the original NISRA ZIP, all boundary candidates, candidate catalogue,
three inventory files, audits, extraction polygon and reusable tooling. It
excludes the large raw OSM PBF and temporary node index; source checksums and
the dated download URL are retained instead.

Before publication: review source overlaps/name quality, expand generation and
validation to all 527, add/review `N` code support across publisher/API/app,
reconcile aliases and historic county alignment, check coverage semantics,
then stage and coordinate Firebase delivery with Postgres registration.
The existing live GB dataset remains unchanged.
