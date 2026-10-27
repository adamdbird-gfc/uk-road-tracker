# Firebase Hosting bandwidth investigation — 27 October 2026

## Follow-up review

[PR #222 final review](pr-222-final-review.md) supersedes the initial implementation
and test status below. Reference preparation is now entirely local-only (the
remote opt-in was removed); builds restore only, explicit verified seeding is
provided, historical-source controls are checked, and 57 offline checks pass.
Full Android verification and real reference-cache bootstrap remain blockers.

## Outcome and evidence limits

Source fixes are based on delivered private **0.25.218 (264)**, exact signed source
`60fe5e5408b4ca03f04dff03e9926d9ea225bd71`, branch documentation tip `42f1c8d`.
Main `98b503f` explicitly directs development to this newer Android lineage.
No production configuration, billing, Hosting content or installed APK changed.
No Firebase request, deployment, app launch, integration test or APK build was made.
Only GitHub source, committed metadata and synthetic fixtures were used.

The supplied console figures (9.77 GB / 10 GB downloaded; 491.63 MB stored) are
user-provided observations. They do not attribute traffic to individual assets.
Storage is not download volume, nor proof of the size of one deployment.
Historical request counts and actual per-file billing remain unknown.

## Findings

1. **Confirmed repeated development download mechanism, high impact.** Both
   `android-prototype-build.yml` (checks) and `build-android-prototype.yml`
   (packaging) call `prepare_ncn_reference.py`. It previously downloaded the full
   pinned national database whenever `.cache/ncn/catalogue.db` was absent. Neither
   workflow restored this dataset across fresh runners. The county preparation
   shell also unconditionally fetched the same pinned boundary binary per job.
   Checks plus packaging can each incur the complete cost for one release.
   These mechanisms are demonstrated by source, not a reconstruction of all runs.
2. **Confirmed uncached Firebase fallback.** Each settlement detail/map request
   called Render for a boundary, then Firebase after failure. There was no disk
   cache for that response. Repeated focused navigation during Render failure
   could repeatedly transfer the same boundary. Frequency/total bytes unknown.
3. **NCN is already persistent on devices.** `ncn-reference-v2` under `filesDir`
   retains checksum-verified active and previous generations, resumes partial
   compressed downloads, validates SQLite, and switches pointers atomically.
   Catalogue loading is already synchronized and install rechecks under a lock;
   ordinary screen navigation does **not** demonstrate repeated national payload
   downloads. Journey assessments use separate snapshot/per-source files.
   Refresh discovery previously ran before checking for an installed catalogue,
   allowing an unnecessary manifest attempt during first installation.
4. **Venue caching is already bounded and version-aware.** Wetherspoons and
   services ship with the APK, retain atomic update files, check manifests at
   most daily on successful refresh / hourly after failure, and serialize updates.
   Unchanged revisions do not fetch full payloads. No evidence justifies blaming
   normal venue-layer changes for national-scale traffic.
5. **Publication verification deliberately downloads assets.**
   `verify_firebase_reference_data.verify` reads every file in its supplied staging
   report. `publish_ncn_reference.served` downloads 142,796,162 bytes at preview
   verification and again after promotion. A successful preview+live NCN release
   therefore reads roughly 285.6 MB of catalogue bodies. This is a confirmed code
   path; historical invocations and total contribution are unknown. Publication
   tools and their production safety gates were not executed or weakened.

Relevant introduction history: `ab67a9f` (Android permanent Firebase origin),
`304b11f` / `741b9b0` (pinned county build delivery), `00fb8a6` (national NCN,
Android installation and external national test reference). Current handover and
`docs/ncn-national-integration.md` retain the original delivery evidence.

## Dependency inventory

All Hosting paths below are relative to `PublicReferenceData.BASE_URL`.
Sizes describe committed metadata, not a fresh production measurement.

| Dataset | Purpose / Hosting path | Known size | Consumer, trigger and cache |
|---|---|---|---|
| NCN manifest | `collections/published/ncn/manifest-v2.json` | Remote unknown; client limit 32 KiB | `NcnCatalogueFiles`: initial install discovery; weekly installed-catalogue refresh, hourly failure backoff. Bundled seed and active/previous manifests persist. No per-screen refresh bypass. |
| NCN national catalogue | `collections/published/ncn/<sha256>/catalogue.db.gz` | **142,796,162 compressed bytes**, **243,724,288 decoded bytes**, revision 1 in bundled manifest | Android: collection enabled and no valid cached DB, or higher published revision. Hash, length and SQLite checks; resumable `.part`; active/previous generations. CI: external test DB, formerly fetched per fresh runner. |
| Historic county boundaries | `build-references/historic-counties/b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850/boundaries.bin` | **8,992,277 bytes** inferred from maximum committed county offset+length | Build scripts, not runtime Firebase loading. Bundled in APK after pinned checksum validation. Formerly unconditional curl in checks and packaging. |
| Wetherspoons manifest/catalogue | `collections/published/wetherspoons/manifest-v1.json`, `catalogue-<revision>.json` | Git source: manifest 280 bytes, newest published file on this branch 389,467 bytes; live version unknown | `VenueCatalogueUpdates`: installed collection refresh on Map entry; daily manifest gate, hourly failure backoff. Bundled seed + atomic `wetherspoons-map-cache-v1.json`; full payload only for a newer revision; SHA/length/content validation. |
| Services manifest/catalogue | `collections/published/services/manifest-v1.json`, `catalogue-<revision>.json` | Git source: 253 / 57,124 bytes; live version unknown | Same venue client/refresh gate; bundled seed + atomic `services-map-cache-v1.json`; unchanged version reused. |
| Settlement boundary | `settlement-inventories-v1/<code>-boundary.geojson` | Per-boundary sizes unknown | `LocalRoadSettlementMatcher`: Render fallback for focused settlement maps. Previously uncached. Now persistent `settlement-boundaries-v1/<code>.ref`, checksum and geometry validation, atomic replacement, serialized misses, persistent hourly failure cooldown. |
| Settlement inventory/manifest and other published reference JSON | `settlement-inventory-manifest-v1.json`, `settlement-inventories-v1/<code>.json`, other staged JSON | Current complete live size unknown | Publication/protection/reconciliation tools can fetch these. No Android direct national inventory fetch found in the Firebase client paths; runtime inventory counts use Render + persistent preferences. |
| Motorway/A-road reference geometry | APK canonical assets; per-road fallback is Overpass | Firebase size: not applicable to examined runtime paths | `MotorwayProgressCalculator` / `ARoadProgressCalculator`: bundled references and on-device reference files. Not evidence of Firebase national road downloads on each map opening. |

Force-stop, process restart, ordinary background return, permission changes and
in-place app upgrades do not delete `filesDir` reference generations in the
examined paths. Uninstall/clear-app-data necessarily loses them. Removing NCN
explicitly keeps its downloaded catalogue. Journey revision changes invalidate
local calculation results, not national reference files. No new dependency on
journey revisions or application version was introduced into reference identity.

## Implemented changes

- Both active Android build workflows restore a cache keyed by the exact NCN and
  county manifests, shared between checks and packaging. Pin checks still run.
- NCN test preparation is **offline by default**. Missing/corrupt pins fail before
  any URL request. A separate `--allow-firebase-download` flag exists for a future,
  explicitly authorized operation; neither workflow supplies it.
- County preparation uses an existing verified asset or explicit local
  `ROADPRINTS_COUNTY_REFERENCE_FILE`. It no longer contains a download command.
- Settlement maps share a persistent, version-keyed, integrity-checked cache.
  Failed or interrupted updates retain the last valid version; concurrent callers
  recheck the committed file. Missing/corrupt data has one attempt per hour, with
  no automatic retry loop. Geometry may be Polygon/MultiPolygon or a GeoJSON
  Feature containing either. Current boundary epoch is `published-v1`: change
  this deliberately when replacing the published boundary generation. No remote
  boundary version manifest was invented.
- NCN cold discovery and installation share the existing install lock. Failed
  initial installs have a persistent one-hour retry gate; explicit collection
  Retry clears it. Background refresh first requires a valid cached catalogue;
  successful initial install defers the next manifest check for a week.
- All three Firebase runtime transports now count consumed response-body bytes,
  durations, outcomes and request triggers. Local diagnostics include instrumented
  cache hits/misses, versions where available, invalidation reasons, per-dataset
  request/byte counts, repeated versions, consecutive-failure retry counts and
  trigger aggregates. Performance diagnostics export includes this report.
  Manifests have separate counters from large payloads. Persistence is coalesced
  on a daemon worker, retaining 64 recent events plus aggregate counters. No
  coordinates, settlement codes or URLs are recorded; no telemetry is uploaded.

## Offline checks and limits

Run `bash scripts/check_reference_bandwidth_offline.sh` with Java 17 and Python.
The Java test transport rejects every URL except an explicit in-memory fixture;
Python blocks socket connection and unexpected `urlopen`. There is no real HTTP
server or remote reference prerequisite.

**20 Java checks + 5 Python checks passed.** Existing distribution-control tests
(**4**) and county recovery tests (**6**) also passed. Shell syntax, Python syntax
and `git diff --check` passed. Three production helper classes were compiled by
the JDK compiler module. No Gradle/Android SDK is available here.

The Java checks exercise the actual new cache/transport/diagnostic classes with
synthetic bytes: first install, fresh instance restart, unchanged-version reuse,
version replacement, interrupted update fallback, corruption, offline reuse,
16 concurrent callers (one loader call), bounded failures, cancellation,
validation failure, byte accounting, HTTP failure and bounded retention.
Map/Progress/Journeys, focused-map, NCN navigation, layer switching, app upgrade
and journey recalculation are **cache-contract simulations with unchanged pins**,
not executions of Android activities. Python source checks verify caller wiring,
transport instrumentation and build safeguards. Full Android compilation,
existing Robolectric regressions, NCN SQLite installer/resume integration, actual
screen navigation and physical Pixel/Samsung acceptance remain unverified.
Do not interpret these 35 checks as full Android or device acceptance.

## Bandwidth model (decimal GB; excludes unknown manifests/boundaries/overhead)

Let N = 142,796,162 bytes; C = 8,992,277 inferred county bytes.

| Scenario | Calculated traffic |
|---|---:|
| One NCN-enabled new device, unchanged catalogue | N = 142.8 MB once |
| Existing valid device, unchanged payloads, restart/navigation/app update | 0 full reference-payload bytes; due manifests still possible |
| Fresh check or package job before fixes | N+C = 151.8 MB |
| Check plus package for one release before fixes | 303.6 MB |
| 20 / 60 fresh jobs before fixes | 3.036 / 9.107 GB |
| Same jobs after fixes with valid cached pins | 0 Firebase reference bytes |
| Same jobs after fixes with missing pins | 0 Firebase bytes; fail closed |
| Five new NCN-enabled beta devices | 0.714 GB |
| 100 / 1,000 new NCN-enabled devices | 14.280 / 142.796 GB |
| One unchanged full boundary revisited k times during Render outage | Before: up to k×B; after: B once, then local reuse; B unknown |

Monthly steady-state assumption: unchanged catalogue, retained app data, five
existing devices, no production publication verification. Full NCN payload use
should be zero; weekly NCN and daily installed-venue manifests remain small but
actual live sizes are unknown. Five first installs plus 60 old fresh CI jobs
would be about **9.821 GB**, close to the reported consumption; this is an
illustration, **not historical attribution**. Each national catalogue update adds
N per participating device; interrupted downloads may add more if Range is
ignored. No delta format exists; introducing one is outside this targeted fix.

A 10 GB allowance can plausibly support five beta devices with retained data and
no repeated CI downloads, but cannot support 100 fresh NCN-enabled users in a
month. At most about 70 initial NCN downloads fit 10 decimal GB before all other
traffic. This is a capacity calculation, not a recommendation to change billing.

## Delivery / remaining protection

No APK or distribution requested; Android version remains 218. Source changes
must reach the active checks/packaging lineage before protecting future builds.
Existing historical reusable workflows pinned to old SHAs retain old behaviour.
The currently installed beta APKs also retain their old behaviour until a later
reviewed APK is installed. Do not run those historical workflows during this
quota incident.

**Cold build cache misses deliberately stop builds.** GitHub cache restoration
cannot create the first verified seed, and caches can expire/be evicted. Provision
existing verified files from an approved local/archive source before packaging;
this task neither fetched such assets nor bypassed national reference tests.
No CI run, publication workflow, merge or production action is part of delivery.
Historical usage attribution, real current live manifests, precise origin of all
9.77 GB and deployment-size history require later evidence. Trigger diagnostics
identify the responsible data operation; NCN's common callers remain grouped
rather than distinguishing every startup/navigation entrypoint. Byte counts are
application-consumed bodies, excluding HTTP/TLS and unread data, not billed bytes.
