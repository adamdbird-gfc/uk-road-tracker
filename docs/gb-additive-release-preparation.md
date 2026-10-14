# Additive GB settlement release preparation — 14 October 2026

An isolated release candidate now combines all 8,545 GB inventories/boundaries
while preserving the existing 121 published inventory and boundary files
byte-for-byte. It adds 8,424 new inventories. The 26 existing name-universe
changes from bulk generation are deferred, protecting current cached totals
and historical road-name/progress identity.

This candidate is not deployed or imported into Postgres. It does not add
Northern Ireland coverage or retire the settlement queue.

## Known-zero compatibility

There are 206 zero-eligible inventories under the unchanged four highway-class
rule. These are valid count=0, roads=[] reference records, not missing data and
not a claim that a settlement physically has no roads.

Current app/source contracts support this representation:

- `LocalRoadSettlementMatcher.inventoryCount` accepts ready counts >=0 and
  caches the integer, so ready-zero does not become an unresolved request.
- `ProgressActivity` handles count=0 explicitly as 0% rather than dividing by
  zero or treating an empty total as completed.
- `TownAchievementEvidence` requires inventory>400; zero cannot qualify for
  the existing town percentage achievements.
- `database.request_settlement_inventory` uses ON CONFLICT DO NOTHING; an
  existing ready-zero row stays ready and does not restart the build queue.
- The inventory status endpoint retains the integer zero rather than dropping
  it as a falsey value. PostgreSQL schema permits nonnegative counts.

These are source-contract checks, not new on-device zero-settlement tests.
Reference snapshot completeness/quality assumptions remain explicit. A future
UI label can explain zero eligibility more clearly; no app change is introduced
by release preparation. Adam's Back to the Future achievement idea is recorded
separately as #140, with qualification undecided and missing/pending data excluded.

## Preservation and integrity

`prepare_gb_settlement_release.py` validates candidate hashes/byte counts, the
generation-boundary snapshot hash, stable-code uniqueness and legacy code
coverage. It rejects overwriting the published directory or reusing a populated
output directory. Existing assets are copied unchanged, with hashes checked.
New assets use the generated candidates and valid frozen boundary snapshot.

The legacy Crewe boundary retains its previously published nested-component
geometry because byte preservation is explicit. New boundary files are derived
from the valid candidate snapshot. This does not claim every legacy file has
valid topology or introduce a new geometry into Crewe; review its normalised
equivalent separately before changing live geometry.

Prepared reference root:

- 17,091 public-reference files (two per settlement plus manifest).
- 215,029,561 bytes before collection files.
- 8,545 manifest entries; 121 preserved / 8,424 added / 206 known-zero counts.

Running the existing Firebase allowlist staging code against this root and the
current release collections succeeded with **17,098 staged public files /
216,637,169 bytes**. This is local staging/integrity validation, not remote HTTP
serving verification or confirmation of actual Hosting usage/allowance.

The separately saved reviewable ZIP is 86,029,866 bytes and includes all prepared
reference files, the manifest and preparation report. The large dataset is kept
out of Git history; code/small evidence reports are Git-backed.

## Scottish support

The builder's explicit-code recognition, batch publisher file selection and
published-inventory validator now include S alongside E/W/K. This prevents the
same omission that caused Chester's repeated builds. Two added validator tests
exercise a Scottish inventory and a ready-zero inventory. This does not request
or build Scottish data through the queue merely by merging code support.

## Required publication order

1. Confirm actual Firebase Hosting plan/usage and bounded release retention.
   The full staged candidate is about 217 MB before retained versions. Do not
   infer allowance from payload size alone.
2. Inspect the live Postgres settlement/code mapping and existing inventory
   status/counts read-only. Confirm no duplicate/inactive/missing identities and
   preserve settlement UUIDs; a saved-file catalogue is not proof of DB state.
3. Verify an isolated served preview against the staged expected byte/hash
   manifest, types, cache policies and absent source/configuration paths.
4. Publish and verify the intended live version before marking newly served
   inventories ready. Explicitly insert missing `settlement_inventories` rows;
   the existing reconciliation helper updates incomplete rows only and cannot
   create thousands of previously unrequested rows by itself.
5. Verify API ready/count/version responses including Scottish and zero cases,
   and retain old endpoints/fallback until migration compatibility is accepted.
6. Retire discovery-driven queue polling only after completeness/serving
   acceptance, keeping a controlled refresh/recovery path. NI is still a gap.

The current Render connection has no selected workspace. Its database-list tool
requires an explicit user workspace choice; the listed workspace is named
My Workspace. No database was queried or changed during this preparation.

## Reproduce and checks

Use the saved inventory candidate directory and valid boundary gzip:

```bash
python scripts/prepare_gb_settlement_release.py \
  --published . --candidates /path/to/gb-road-candidates \
  --boundaries /path/to/roadprints-gb-boundaries-2022-valid-candidate.geojson.gz \
  --output /tmp/roadprints-gb-additive-release
python -m unittest discover -s scripts -p test_prepare_gb_settlement_release.py
python -m unittest discover -s scripts -p test_reconcile_published_settlement_inventories.py
```

Five additive-preparation tests cover legacy byte preservation despite changed
candidate names, new Scottish/zero additions, integrity failures, boundary hash
mismatch and protected/fresh output directories. Six inventory-validator tests
cover K/S identities, zero counts, manifest mismatch, missing boundary and path
injection. Changed workflows pass actionlint. CI tests preparation only; no
national download, new APK, live data deployment or paid service is involved.

OSM road data attribution: OpenStreetMap contributors, ODbL 1.0, via Geofabrik.
ONS/Ordnance Survey boundary attribution and licence conditions continue to apply.
