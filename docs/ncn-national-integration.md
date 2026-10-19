# National NCN Android integration — 19 October 2026

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

### Publication workflow and current blocker

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

The local payload has been checked, **not published**:

- Database: 243,724,288 bytes;
  SHA-256 `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`.
- Compressed: 142,796,162 bytes;
  SHA-256 `91c8e25f0d58ced6a1e2c91905fe4190b826dc8a469aafc4aab09b83dee1fd91`.
- Prepared bundle: `roadprints-ncn-firebase-publication-2026-10-19.zip`.

This session has neither Firebase credentials nor an authorised route to deliver
that local payload to the existing credentialed Actions runner. The Firebase
plugin is available but unconnected. Firebase's file-size limit accommodates this
payload; actual project quota/billing and serving headers still require the
credentialed publication/preview check. **Do not claim publication or package a
beta whose initial reference URL has not passed that check.**

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
| 322 inherited Python NCN tests | Passed again after Firebase delivery changes. |
| National database readback | Passed: identity/source counts, 34 holds, 218 measurable routes, all 335,007 measured target mappings, 333,497 unique network edges. |
| 5 external-reference/publisher invariant tests | Passed: manifest bounds, monotonic rollback, preservation, config rules, corruption. |
| Android Java syntax and Python compilation; git whitespace | Passed; syntax parsing is not Java type compilation. |
| Android compilation | Passed in CI run 39325156149. |
| Lint / offline Robolectric regressions | First lint run found four orientation constants; fixed and rerun pending. Offline regressions can execute even while publication is unavailable. |
| National Robolectric tests | Pending: CI needs the published immutable reference. |
| Firebase preview serving/integrity/rollback and project capacity | Not verified; blocked on authenticated publication/payload transport. |
| APK 0.25.207 (253) | Version reserved in source only; no successful build or distribution yet. |
| Real-device acceptance | Not performed. |

The existing Android checks/package workflows download the immutable NCN pin into
an ignored CI directory via `prepare_ncn_reference.py`, verify hashes/SQLite,
export `NCN_TEST_DATABASE` for Robolectric and run national readback. The payload
is **not** copied into APK assets. Missing/corrupt reference makes checks fail
explicitly. After publication, run integrated unit tests/lint, then one coordinated
APK through the existing pinned release-request/Firebase App Distribution flow.

Device acceptance must cover all 33 checks in the development brief. Checks 1–10
have shared UI/fit/loading regressions and new NCN geometry tests in the JVM suite,
but gestures, pixel contrast and Retry must also be checked on device. Checks
11–26 have data/matcher/ledger/UI/cache tests, with live overlay, section taps,
large-history resume and retained navigation still requiring device validation.
Checks 27–33 retain the main-map/filter/editing/collection regression tests;
realistic-history memory, latency, tab switching and process restart are unverified.
No source or JVM check substitutes for device acceptance.
