# National NCN Android integration — delivered 20 October 2026

Branch `integration/ncn-firebase-beta`, starting at released 0.25.206 lineage
`7bc10f4a` (contains exact corrective source `452258f6`). No old app branch was
merged over its map readiness, recovery, navigation or white progress bar fixes.

## Dependency disposition

| Work | Disposition |
|---|---|
| #171 | Already reconciled in Beta 1; retain latest shared detail UI. |
| #168 | Already reconciled backend/source provenance; no duplicate merge. |
| #165 | Include existing Python source/evidence readers and tests. |
| #172 | Selectively adapt catalogue, matcher, ledger, assessment and golden fixtures; supersede trial UI, fixed-key production reader and restrictive compiler. |
| #175–#187 | Include consolidated Python readers and the pinned #187 output; no blind stacked merges or new geometry admissions. |
| #201 / #202–#205 | Preserve corrective release lineage and existing regression tests. |
| #193 | Defer all new achievements, mileposts and poem mechanics. |

## Data and accounting

Reviewed checkpoint: 219 supported route identities, 62,399 distinct source
ways, 62,487 route-way associations and 34 held identities. Input pin
`a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641`;
plan summary `e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b`.
See `ncn-completion-checkpoint.md`. No live membership/status research is repeated.

`python -m scripts.build_ncn_national --checkpoint <reviewed-bundle> --output
<external-staging-directory>` validates the pinned plans and writes an indexed
SQLite database, one compressed catalogue and a small manifest.
`scripts/finalize_ncn_android.py` bounds rows and adds display-only overviews.
**Copy only manifest.json into Android assets. Never commit generated databases,
compressed payloads or split national binary parts.** Queries index source ways,
hashes, node pairs and route targets; startup does not parse the full network.

## Existing Firebase architecture and national delivery

- Firebase project `roadprints-822cc`; configured Storage bucket
  `roadprints-822cc.firebasestorage.app`. Existing large reference assets are
  actually served through the dedicated **Hosting** site `roadprints-data-822cc`
  at `https://roadprints-data-822cc.web.app/`, via `PublicReferenceData.BASE_URL`.
- Existing venues use small mutable manifests, immutable revision files, SHA/size
  checks and atomic cached copies. Their 2 MB JSON limit is preserved. NCN uses
  a separate bounded, streamed SQLite cache on the same origin.
- Mutable discovery: `collections/published/ncn/manifest-v2.json`. Immutable file:
  `collections/published/ncn/<database-sha256>/catalogue.db.gz`. Manifest pins
  schema, scope, revision, compressed and decoded lengths/hashes, counts, holds,
  provenance and attribution. The first APK seed is a small fallback manifest.
- `NcnCatalogueFiles` downloads off the UI thread, resumes partial transfers with
  HTTP Range, verifies both hashes and SQLite integrity before atomic activation,
  and retains the prior generation. An already cached reference opens offline
  without a network request; a corrupt active cache falls back to the prior one.
  First use needs a download (~136 MiB). The database is ~232 MiB; downloading an
  update temporarily needs space for old/new generations plus compressed input.
- Weekly background discovery (hourly failure backoff) accepts higher manifest
  revisions. Cache/assessment versions use the database digest. Catalogue changes
  invalidate progress and advance the activation epoch so stale assessment work
  cannot overwrite a newer generation. Catalogue-only updates require no APK.
- Rollback publishes a **higher manifest revision** referring to a retained older
  immutable digest. Never decrease revisions, replace immutable bytes or delete
  the prior assets. The publisher records the source Hosting version for emergency
  server rollback; higher-revision catalogue rollback also reaches cached clients.

### Established publication workflow

`scripts/publish_ncn_reference.py` follows the existing county publisher: use
`FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON`, assert the exact project/site, clone the
live version server-side, preserve all other file hashes/configuration, add only
NCN bytes/manifest and an immutable NCN cache rule, verify actual preview bytes,
recheck that live did not change, then optionally promote that exact version.
No full Firebase deploy or repeated binary-upload retry loop. It reuses already
stored content hashes. `.github/workflows/ncn-reference-publication.yml` serialises
with the existing `firebase-reference-publication` group. It takes an authorised
HTTPS payload location; the payload must match the committed manifest before any
credentialed publication step. Do not commit staging URLs or credentials.

The prepared payload was verified and published (confirmed run and URLs below):

- Database: 243,724,288 bytes;
  SHA-256 `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`.
- Compressed: 142,796,162 bytes;
  SHA-256 `91c8e25f0d58ced6a1e2c91905fe4190b826dc8a469aafc4aab09b83dee1fd91`.
- Prepared bundle: `roadprints-ncn-firebase-publication-2026-10-19.zip`.

### Proven original authentication and current environment difference

Original successful county migration: [run 38740224136](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/38740224136),
source `f43a8686`, `.github/workflows/reference-maintenance.yml`. That Actions
runner downloaded the existing public `android-0.25.167` APK from GitHub Releases,
extracted the exact pinned county binary, then `publish_county_build_reference.py`
used `FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON` and `ROADPRINTS_DATA_HOSTING_SITE`.
`google.oauth2.service_account.Credentials` + `AuthorizedSession` minted the
Hosting OAuth token inside Actions; the private key never entered this workspace.
The publisher cloned live Hosting, uploaded gzip transport content by hash,
verified preview/live serving and preserved every existing reference file.

The original full reference-data workflow stages allowlisted venue/settlement
files from repository checkouts and deploys with Firebase CLI. It writes that
same service-account secret to a private runner temporary file, sets
`GOOGLE_APPLICATION_CREDENTIALS`, and deploys target `reference-data` on project
`roadprints-822cc`. Both are the existing deployment identity, with different
payload sources. App Distribution's separate identity is not a Hosting substitute.

**Existing authentication is available and works.** [Run 39327313703](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39327313703)
and [run 39327496695](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39327496695)
verified that same Hosting secret from Actions, live version
`sites/roadprints-data-822cc/versions/ffae619efea48f69`, all **18,155 files**, and
the original county asset. The identity fingerprint is
`f48e6d9f244a2987bbe9696a7093492f5f4efcaebfe9c2d04d85cab0dd23dd6d`.
No key, bearer token, new account/project or new authentication design was exported.
Verification script/workflow are preserved on `infra/ncn-publication`.

### Confirmed publication and packaging

The payload transport and authentication blockers are resolved. The owner uploaded
the prepared catalogue to private Drive file
`1S9WjK0KUQX-XjUclgO92uter9Wvl3gbX`, authorised reader access only to
`roadprints-hosting@roadprints-822cc.iam.gserviceaccount.com`, and enabled the
Drive API. The Actions runner downloaded it using the existing Hosting service
account, checked its pinned bytes and published through the established Hosting
clone/add mechanism. No new cloud account, project, credential or public Drive
share was introduced. Earlier 403/API and transport notes are historical.

[Publication run 39330952075](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39330952075),
source `dda0aecc71a5e40ff501a32831b43440d559ebb4`, recorded
`live_verified`, preserved all **18,155 existing files** and promoted version
`sites/roadprints-data-822cc/versions/758e86605707677e`.
Prior Hosting version `sites/roadprints-data-822cc/versions/ffae619efea48f69`
was recorded for recovery.

- [Live manifest](https://roadprints-data-822cc.web.app/collections/published/ncn/manifest-v2.json).
- [Immutable catalogue](https://roadprints-data-822cc.web.app/collections/published/ncn/973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093/catalogue.db.gz).
- Decoded database: **243,724,288 bytes**;
  SHA-256 `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`.
- Compressed catalogue: **142,796,162 bytes**;
  SHA-256 `91c8e25f0d58ced6a1e2c91905fe4190b826dc8a469aafc4aab09b83dee1fd91`.

The first packaging job called an older reusable definition lacking national-data
preparation. It failed tests and did not distribute. The corrected publisher
workflow pins `build-android-prototype.yml@dda0aecc71a5e40ff501a32831b43440d559ebb4`.
The `package-existing` request then skipped publication, retrieved the live
immutable reference, verified compressed/decoded hashes and SQLite, exported
`NCN_TEST_DATABASE`, and ran the complete suite. Never bypass these gates with
offline-only exclusions when packaging.

**Released 0.25.207 (253)** from exact source
`02ddf96aba6c403e2e88eab0d764feb61ef27ec4`.
[Run 39331303438](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39331303438) completed tests, lint, signed APK assembly
and Firebase App Distribution successfully, including tester distribution.
[Install release](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/7hem35tolspl8).
[APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39331303438/artifacts/12224797445);
[reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39331303438/artifacts/12223649168).
No repeat build or catalogue publication was needed on 20 October.

Application identity remains `com.roadprints.capture`, using the established
stable signing identity. Verified certificate SHA-256:
`5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`.
Install over the existing app; do not clear user data.

Original remote checkpoint `integration/ncn-national-beta` at
`2ee1031218665ea50acd98fb38c7c3e4ae25ba6a` preserves the first 71 confirmed parts.
Additional confirmed object receipts remain in the local upload checkpoint (79
total recorded). No old blobs were recreated/deleted and no further national
binary uploads are required. The new integration branch starts directly from the
released application lineage and excludes national binary parts from its tree
and ancestry. Processing logic, source definitions, golden fixtures and small
manifests remain in GitHub. The existing 336 KB trial golden fixture is reused.

Route completion preserves original projection offsets and piecewise non-unit
physical/logical bindings. Overall completion converts covered route intervals
onto a separately noded, unique physical union of the open target geometry.
Overlapping route identities and repeated/mixed-mode journeys union intervals;
route totals are never summed into the network total. Source travel is painted
from actual supported source geometry, not an offset publisher line. Complete
open target gaps remain in denominators and remaining geometry. No connectors
are added across gaps; joining requires identical endpoints with degree two.

Assessment accepts validated walking/running and bicycle provenance only.
Driving, bus, rail, flight, unknown and relabelled car matches earn zero. Each
journey has an atomic fingerprinted checkpoint under the catalogue/accounting
version. Geometry edits and corrections invalidate it; deleted records cannot
return via cache. Reading streams only matched geometry/provenance and metadata,
without raw capture arrays, journey upgrades or rematching. Work runs separately
from core map preparation, checkpoints per journey and publishes only on a
stable archive revision/activation epoch. Explicit Retry resumes failed work.

## UI and limitations

- Collections activation, existing Legend collection selection, activity filters,
  subordinate ordinary road overlays, route identity choice, grouped searchable
  Progress, Jump-to, red signpost charts and shared purple/grey-dashed detail maps.
- Focused maps fit verified reference geometry, retain zoom/pan/Close and offer
  eligible section journeys through the established journey cards/editor.
- Activity preference, group/search preferences and retained screen state survive
  navigation. NCN overlay work does not gate ordinary map readiness or change
  road-layer preferences. No user archive or earned achievement migration.
- **218 open-main targets are measurable. NCN228 is a browseable preserved
  alternative inventory; open-target assessment/percentage and aggregate credit
  are held.** Its source-inventory scope is not relabelled as open-main.
- 34 identities remain excluded. The asset manifest is the explicit hold register.
  176 alternative way occurrences across 49 identities remain conservatively
  unbound, as in the reviewed checkpoint. No shortcut or either-arc reinterpretation.
- This checkpoint contains NCN/RCN numbered identities only. Named routes have a
  visible empty group; verified names/termini are not fabricated.
- Zero-coverage national overlay geometry uses a display-only approximately
  10–20 m simplified overview. Focused maps retain reference vertices. Coverage
  assessment, denominators and source lines never use simplified geometry.
- Unsupported old matched evidence receives no credit and no repeated review
  prompts. A normal explicit rematch remains available through existing editing.
- External references are public ODbL/OGL derived data. No journey archives or
  private observations are published. New achievements remain deferred to #193.

## Validation record

| Checks | Status |
|---|---|
| 322 inherited Python NCN tests | Passed after Firebase delivery changes. |
| National database readback | Passed: identity/source counts, 34 holds, 218 measurable routes, all 335,007 measured target mappings, 333,497 unique network edges. |
| 5 external-reference/publisher invariant tests | Passed: manifest bounds, monotonic rollback, preservation, config rules, corruption. |
| Public reference serving and integrity | Passed in publication and corrected packaging; existing Hosting files preserved. No regeneration or republication on resumption. |
| Full Android regression suite | **677 tests; zero failures, errors or skips**, run 39331303438. Includes existing map/filter/reset/retry/navigation/edit/collection/achievement regressions. |
| Previously blocked national classes | **NcnNationalTest: 2 passed; NcnUiTest: 1 passed**, using the verified national SQLite reference. |
| Additional NCN Android classes | NcnAndroidTest: 11; NcnCatalogueFilesTest: 4; NcnGeometryTest: 3, all passed. |
| Lint | Zero errors in corrected full packaging run. |
| Signed APK and app identity | Passed established stable-key verification; com.roadprints.capture, 0.25.207 (253). |
| Firebase App Distribution | Release 7hem35tolspl8 uploaded and distributed successfully to established testers. |
| Offline fallback / rollback | Automated cache corruption, fallback and monotonic manifest invariants passed; production live rollback not exercised. |
| Real-device acceptance | **Not performed.** No claim of device, realistic-history memory or latency acceptance. |

National golden tests cover eligible modes, repeated/mixed-mode physical unions,
vehicle exclusion, conservative evidence, edit/delete checkpoint invalidation and
resumption. UI tests cover grouped search/mode navigation and overlay camera
retention. They do not substitute for real Android gestures, visual acceptance,
actual archive processing time or large-history memory measurements.

Shared Progress road detail retains opaque rounded white tracks with motorway blue
and A-road green fills; focused NCN uses purple. Underlying estimated unique
travelled distance, reference total and percentages are unchanged. Fit uses covered
plus remaining geometry after layout, including disconnected sections; B/local
fit recorded sections only. Zoom controls, skeleton/spinner, bounded loading,
Retry/error/Close, shared contributing-journey cards and section-specific eligible
NCN journey selection remain. B/local omit unsupported totals/percentages and
display Recorded sections plus counted VIEW JOURNEYS; B/local section tapping
remains deferred and is not promised.

Device acceptance must cover all 33 checks in the development brief. Check cold/
warm main-map readiness and recovery, Filter/Reset/Retry and retained tabs; M/A
white tracks, full reference fit, zoom and journey navigation; B/local recorded
fit and accurate counts; national first download/resume/offline, purple and grey
dashed overlay, eligible mode progress and driving exclusion, section journeys
and preserved mode/group/search/scroll context. Realistic-history assessment,
edit/delete/import refresh, process restart, memory and latency remain device
acceptance work. No new achievements or milepost mechanics are included.
