# Stage 1 — versioned candidate inventory and reference readiness

5 November 2026. Status: **implemented and locally validated; awaiting PR review/integration**. No publication, Android feature activation, awards, entitlement change, backend deployment, APK or tester distribution. Branch `feat/millennium-milepost-inventory-20261105`, target `main`.

## Verified baseline

GitHub main and full local clone agree at `cc7ed432b210aeac799820cf0f6f8b156588e5a8`, tree `504e77ad0ea142fc92ddc58774fcf71a03ee511e`. PR #278 is merged there; #276 merge `24278f5256bf367039b9a3adf986d619754ccb15` is its ancestor. All six feature-specification deliverables and development governance were read before implementation. [Baseline receipt](stage1-baseline.json) records source/live/device distinctions. Android source is 0.25.223 (269); installed APK and live backend SHA remain unknown. No changed component requires backend or installed-device access.

Inspected NCN components: `NcnCatalogue`, `NcnCatalogueFiles`, bundled manifest, `ReferenceDataDiagnostics`, `UtilitiesActivity`, existing publication workflow and publisher, controlled authorisation/transfer/rollback process. Existing milepost material consists of research inventories/source assessment and the #278 specification; no Android discovery catalogue/model or approved target list was recovered. The old six-way personality allocation was not used. Existing NCN fallback is bundled revision 1 with retained validated cache/previous generations, distinct from server revision 4.

## Implementation

[Reference contract, source provenance, reproduction and publication/rollback procedure](../../reference-data/millennium-mileposts/README.md) is the implementation entry point.

- `reference-data/millennium-mileposts/source/`: exact original OSM snapshot, archived audit/evidence manifest and explicit source pins. No official restricted inventory rows or images imported.
- `record.schema.json`, `manifest.schema.json`: machine-readable candidate-only wire contracts with strict executable cross-field checks.
- `revision-1/catalogue.jsonl`, `manifest.json`: deterministic, independent milepost revision. Not in `collections/published/`, Android assets or auto-publication triggers.
- `scripts/milepost_catalogue.py`: standard-library offline compiler, integrity validator, streaming reader, 32-row LRU and local immutable-generation cache with atomic active/previous pointer and revision high-water mark.
- `scripts/test_milepost_catalogue.py`: 27 focused tests. `.github/workflows/millennium-milepost-inventory.yml`: read-only PR verification and exact rebuild, no secrets/deploy/build.

All records remain source candidates: `physical_marker_id=null`, identity/location unverified, UK boundary unreviewed, `eligible=false`. Known design-tag attribution is provisional; unsupported tags remain explicit unknown. No schema-1 input can claim a physical marker is validated or eligible. There are no existing approved assignments to migrate; unsupported future approved schemas fail closed. A separate physical-ID/alias and reviewed-assignment migration is required before accepting approved markers. This boundary prevents an OSM retag/refresh from silently becoming a verified designer assignment. No achievement consumer or capture code is called or changed.

## Counts and integrity

Raw OSM independently counted: 889 unique typed IDs, all nodes. Mills **446**, Rowe **265**, McColl **135**, Dudgeon **43**. All original IDs, WGS84 coordinates, design tags and complete original tag maps compare equal. Validated physical markers **0**; achievement-eligible markers **0**; boundary exceptions **0**. Unknown/unsupported source design fixture is retained as unknown, not dropped or approved.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Original feasibility ZIP (recovered, not added to repository) | 17,021,846 | `3bdf0b355a978a628eb4b5d1fb5fcf392edb3a1fcc6f78b16b373eecdebfe6c5` |
| Original OSM response | 385,034 | `4c0cd419196408f1abad27a61bfc84e25bd74e4a2ae9e2b835dcd0688fb39fc6` |
| Original candidate GeoJSON (archive cross-check) | 287,516 | `30dac15dadbb354d2ea23df969e7a0d1f742bdb07fb4ff43c272e7732ce5771f` |
| Original candidate gzip (archive cross-check) | 26,724 | `2378a20f1422d6c61888d100a34d085d830622ebe7bf718b808ce1a298bd070e` |
| New deterministic revision-1 JSONL | 890,504 | `63d9772ede9cf59ad7aa882e838b2032a133cc322a01740d6ff336cfa97e2377` |

Source snapshot is `2026-10-15T17:21:06Z`. Precise UTC extraction time was unavailable and remains null; OSM per-object versions were not returned and remain null. Revision/generation epoch is deterministic and documented, not the time a source marker was last observed. Temporal presence, physical identity, actual designer verification and UK/coastal exceptions remain evidence work.

## Validation and bounded memory

**44 local tests pass, zero failures/errors/skips**: 27 new inventory/cache tests, 7 existing NCN reference/publication contract tests, 4 existing Chapel Lane and 6 existing five-case finite correspondence tests. [Exact test output](stage1-tests.log). An initial broader import failed because the clean runtime lacked Shapely; installing the repository's existing pinned offline requirements resolved it. An earlier subset attempt before the full clone lacked an existing publication helper; the complete checkout resolved it. No test failure was hidden or treated as a pass.

Two independent revision-1 builds compare byte-for-byte for payload and manifest. JSON Schema 2020-12 schema validity and all 889 rows/manifest pass using local jsonschema 4.26.0 (validation-only environment dependency, not added to production). Runtime uses only Python standard library. New CI requires no third-party dependencies. `git diff --check` and Python compilation pass.

Python 3.12 Linux tooling probe, one process, all 889 records, three complete scans: open **20.41 ms**; scans **40.49 / 46.67 / 39.64 ms**. Separate tracemalloc probe of three opens/scans: current **15,944 / 16,806 / 17,471 bytes**, peak **151,037 / 157,350 / 158,040 bytes**; process high-water RSS **12,032 KiB**. Automated reader peak budget <2MiB passes. These are local Python measurements, not Android ART/UI/256MiB-heap or physical-device acceptance. LRU misses scan the catalogue, so a future Android per-GPS-point reader requires a spatial index; this offline adapter does not claim that performance.

Malformed/missing/duplicated/changed source fixtures, unsupported design/schema, forged approvals, changed metadata, size limits, revision reuse/decrease, corrupt-active fallback, interrupted install/pointer write, caller cache mutation and cached-file corruption are covered. Cache failure preserves the last valid pointer; missing all compatible data returns unavailable. No existing NCN loader/fallback code or pins change. Android capture/achievement runtime suites, lint, device tests and full national rebuild were **not rerun** because there is no Android/runtime or geometry change. Source diff isolation establishes no new runtime path; prior #276 Android consumer results remain historical evidence, not fresh execution.

## NCN 2,128.616-metre recovery — separate ownership

| Delivery state | Finding |
| --- | --- |
| Implemented | Opt-in finite Chapel Lane, Bradford and Derry compiler corrections |
| Merged | #275/#276 included in current main; #276 merge `24278f52…` |
| Validated | #276 final-head every-row/canonical audit and 49 Android consumers passed; latest national audit #277 independently reproduces **2,128.616150596179 m** |
| Published | **No**; live revision 4 excludes this extra distance |
| Device uptake / user completion | Pending; not assessed or changed |

Read #277 at exact head `3d4e670c5af098c28714248309cc327b180113da` (unmerged audit branch) including report, receipt and results. Independent subtraction of its published/candidate gaps reproduces the prior candidate delta to <0.0001 m. National denominator remains **17,435,132.494006 m**; assessability **92.608243626372% → 92.620452401678%**; candidate remaining deficit **1,286,633.901226 m**. This reuses the latest independent exact national assessment and checks its arithmetic; it does not claim a new full database/geometry assessment in Stage 1.

A fresh 1,079-byte live manifest read during this session confirms revision **4**, manifest-body SHA `f709361d…`; database `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`, gzip `c40117278069777b38c13a099f319855e99878d24b95128f140f8d2f34a6020d`, unchanged from the published receipt. No national payload was downloaded; this is live metadata/pin confirmation, not a fresh full served-payload checksum. Git diff confirms existing NCN source, bundled manifest, requests and reference files are unchanged.

New narrowly scoped tracking [#279](https://github.com/adamdbird-gfc/uk-road-tracker/issues/279) owns preservation/revalidation of the exact CI candidate, a **new increasing reference revision**, existing guarded publication/rollback and subsequent device uptake. #265 retains corridor/Cumbria policy evidence; #277 recommends parking new broad recovery. Preserved CI artifact 13413944871/run 42028448814 expires **19 November 2026**. Its database `c48ac543…` differs from the local writer's `7853f02b…`; do not substitute regenerated bytes. Publication is pending separate authority, not blocked on another geographical investigation or new Android APK.

## Utilities decision and next stages

No PR B. [#272](https://github.com/adamdbird-gfc/uk-road-tracker/issues/272) is updated with precise model, acceptance fixtures and dependencies. `NcnCatalogue` currently retains loaded hash but not manifest revision. A newly installed cached generation can differ from the loaded consumer. Correct display therefore needs loaded-manifest identity propagation and durable nullable update status, exceeding a small label-only change. The model separates application version, loaded/installed/server revision/hash, source, status, last check and last successful update. Candidate milepost revision 1 must show **not published/not installed**, not active. Nothing hard-codes NCN revision 4.

Before Stage 2, review/integrate this offline contract and pin synthetic positive/negative evaluator fixtures against actual native/import evidence schemas. No verified eligible national post catalogue exists yet. Physical verification, stable canonical physical-ID/alias registry, temporal location/presence and UK/coastal evidence, then a separately reviewed approved schema/reader are required before real awards. Seven eligible posts and all four verified design opportunities gate the complete experience, not this source-inventory foundation. Android indexed consumption/activation and the existing-hosting publication adapter remain future work. Silhouette identity/rights and artwork remain later content gates. No settled product-policy decision was reopened.
