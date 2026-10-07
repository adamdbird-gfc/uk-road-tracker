# Historic County Collector reference

Roadprints made use of data provided by the Historic County Borders Project:
https://county-borders.co.uk/

The project permits personal, educational, non-commercial and commercial use.
Downloaded 7 October 2026, website dataset date 6 October 2026.

Use Historic Counties Standard Definition A, full-resolution WGS84 data. Detached
parts belong to their host county under Definition A. All 92 HCS counties remain
separate, including Ross-shire and Cromartyshire. Yorkshire is one county;
London and modern administrative county groupings are not additional counties.

`catalogue.json` versions and identifies the source archive by SHA-256. The build
runs `android-prototype/scripts/prepare_county_reference.sh` to download that
exact source, validate it, and generate the offline reference before tests and
APK assembly. A changed upstream source fails the hash check until deliberately
reviewed and versioned. No runtime network request is made for county lookup.

`boundaries.bin` contains independent gzip members at catalogue offsets. Each
member stores ring counts, vertex counts and zigzag varint coordinate deltas.
Coordinates are rounded to 1e-6 degrees (at most ~0.08 m displacement). Duplicate
rounded vertices are removed; there is no polygon simplification. Islands and
holes are retained. Only a positive matched road stretch within the polygon
qualifies; touching the boundary alone does not award a county. Actual matching
accuracy remains separate from reference precision.

A four-megabyte cache bounds retained boundary arrays. Data are loaded during the
background evidence scan, never to construct the initial achievement cards.

`settlements.json.gz` is the offline ONS settlement-boundary membership index
added in 0.25.163. All 8,545 existing GB settlement IDs are mapped; 537 have
positive overlap with multiple historic counties. Ordered county codes put the
largest footprint first for a single Progress group. Eight invalid ONS polygons
were repaired using `make_valid`, with their IDs recorded for review. The index
pins the same county source hash/version and separately records settlement
source hashes. It does not award travel achievements or change town identity.
See `docs/settlement-historic-county-alignment.md` for scope, attribution and
rebuild instructions, including the outstanding NI settlement source gap.
