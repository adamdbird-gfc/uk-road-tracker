# NI integration and temporary UK preview — 15 October 2026

## Identity, credit and metadata

The existing API accepts `[A-Z]` plus eight digits; its PostGIS query returns all
intersecting settlements. The Android matcher retains every stable code, and
TownAchievementEvidence records credit for every returned settlement. No N-code
parser change is required in those components or the public-file stager.

Eight overlap cases were extracted from the actual public OSM ways in the pinned
snapshot. Their polygons and route geometry intersect the expected pairs of
NISRA codes. Android regression checks exercise the existing record/collect path
for all eight cases and require credit in every returned settlement. This
preserves the same non-exclusive intersection rule as GB; official boundaries
are not trimmed or reassigned.

The app branch now contains the offline historic county index for 9,072 IDs.
Every existing GB membership is unchanged. NI adds 527 mappings; 12 span counties.
The 92 county IDs, existing county source version and county geometry are not
updated. The county website's current archive has a different SHA-256; NI uses
the exact pinned County Collector binary asset recovered from a verified APK,
with its complete data hash checked before decoding. Rounded coordinates are
1e-6 degrees, matching the county geometry the app actually uses. Polygon holes
and detached islands are reconstructed and tested. Provenance identifies this
packed-geometry basis separately from the original county source archive.

The index extension and Android regression checks passed unit/lint CI in PR #151
and are merged into the app branch. No release request or APK distribution was
triggered. NI historic county grouping ships in the next APK; existing APKs can
recognise NI codes but do not have those offline county mappings.

Prepared `ni-metadata.json.gz` contains source names, stable codes, official-name
aliases, a representative point within each polygon, nation and separate historic
county memberships. Administrative county/region remain null because the NISRA
source does not supply them. No case-based or ambiguous place-name aliases are
invented. Keep this metadata separate from the ONS GB catalogue: the existing
startup loader assigns that catalogue to the ONS source. Future ingestion must
use a distinct `nisra_sdl_2015` source and must preserve all existing GB identities,
counts and reference rows. No NI database rows are created in this step.

## Additive staging

`stage_ni_reference_additions.py` validates the exact reviewed source hashes,
adds 527 inventory/boundary pairs and replaces only the manifest with 9,072
entries. The original 8,545 GB manifest entries remain identical. It stages
1,055 added/replaced files totalling 12,642,492 bytes. All public JSON file hashes
are fixed by the reviewed additions report:

`8721b48b05b791bdca92b18a65b4da8acb5d72234b078a5e08207a8b6b78c6e0`

The final preview should contain 18,152 public files. Total uncompressed public
content is 228,536,593 bytes. These include collection releases already present
on the verified GB site. No source files, credentials or configuration files are
added to public hosting.

## Expiring preview workflow

A one-off request rebuilds only NI from the pinned NISRA ZIP and dated OSM PBF;
the resulting file/hash report must match exactly before deployment. It does
not download or regenerate all GB inventories.

The existing Hosting service account is used only for the dedicated public-data
site. Firebase's `versions.clone` creates an unfinished server-side clone of the
current live version. Every inherited Hosting file hash and the serving config
must remain identical. Only the 1,055 staged additions/replacement paths are
populated; upload content is gzip-hashed and the upload destination is restricted
to the expected Google Hosting endpoint for that exact new version.

The tool finalises the version and compares the complete file map. Its request
wrapper explicitly rejects live release mutations. A one-day preview channel
`uk-ni-reviewed-20261015` receives the release. All 1,055 new/replaced assets get
exact-byte, MIME, cache and nosniff serving checks in bounded batches. Existing
GB/collection files are proven unchanged through Hosting hashes and config,
with representative serving checks and genuine source/configuration 404 probes.
The live version is checked before and after preview verification and must stay
unchanged. Evidence retains the source/preview versions and content-map hashes.

This avoids another 217 MB client download or full GB upload. It does not change
the billing plan, publish to live, access personal route data or register NI
records in Postgres. The next publication slice must promote the exact verified
preview and atomically ingest NI reference metadata, boundaries and matching
counts under their own source, with appropriate idempotence and preservation
checks. APK timing is separate from public reference-data publication.
