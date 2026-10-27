# PR #222 — final build-cache integration review

Status: reference restore, Android compilation/lint and test fixtures verified;
eight remaining test failures corrected on the PR, runner confirmation pending. Do not merge yet. This report supersedes earlier draft-release, signed
URL and cache-bootstrap preparation instructions.

## Latest verification — eight failures traced and corrected on the PR

[Run 40584540947](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40584540947)
finished against cd0e649 with **793/801 tests passed, 8 failed**. Reference cache
restore/integrity, Android/unit compilation, lint and all three Robolectric fixture
resolutions passed. The test reports (artifact 12772030710, 359,577 bytes) identify
all eight failures:

- Five NCN tests: the test network-namespace sudo boundary dropped the already
  exported NCN_TEST_DATABASE variable. The database was restored and verified;
  this was environment propagation, not a missing reference asset. Both sudo
  boundaries now explicitly preserve this one local-file path.
- Three diagnostics tests: new bandwidth counters survived Clear diagnostics,
  and an empty report still emitted a heading/newline. This was a regression in
  PR #222's diagnostic integration. Clear now empties the local bandwidth ledger
  as well as timing diagnostics, emits no report for empty data, and serializes
  flush snapshots so an older queued write cannot restore cleared counters.
  It does not clear reference files, retry metadata, journey data or notification
  identities. Existing CrashReporter/notification regression assertions remain
  unchanged, not relaxed.

25 pure Java checks and 16 Python build/reference checks pass after these fixes,
including durable diagnostic clearing without deleting cached references and
preservation of the NCN path through the isolated launcher. Shell parsing passes.
No new workflow, main change, Firebase request or cache/bootstrap action occurred.

The new reviewed PR commit must be used as source_sha for the next full runner
verification because it includes the two diagnostic Java corrections. Reusing
cd0e649 would retest the known failing diagnostic code. Approval is required to
update only the registered verification workflow (NCN environment propagation)
and start one fresh run against that new source. No reseed/secret/APK is needed.
The delivery receipt/PR body identifies the exact new source SHA.

## Android runner result and narrow verification fix

[Run 40582984194](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40582984194)
used application/integration source `cd0e6499ee7959819105c64a30f0f1e852c0679b`.
It restored the exact shared reference cache, validated both datasets, passed
preflight/offline controls, compiled Android and unit-test sources, and passed lint.
`BUILD SUCCESSFUL` for compile/lint is recorded in the log. No reference fallback
or Firebase request occurred. This now establishes Android build preparation with
the GitHub cache, beyond the earlier seed-only proof.

The unit run executed inside the network namespace: **801 tests, 103 passed,
698 failed**. Report artifact `12769329063` (768,708 bytes) was inspected. Every
failure reports a missing local Robolectric instrumented Android jar, not a test
assertion failure: 687 need `android-all-instrumented-9-robolectric-4913185-2-i7.jar`,
7 need `android-all-instrumented-13-robolectric-9030017-i7.jar`, and 4 need
`android-all-instrumented-15-robolectric-12650502-i7.jar`.

The verification setup incorrectly assumed those files were in the restored
Gradle/Maven caches. The correction preloads these exact three Maven test fixtures
before disabling external networking. Each version is resolved in a separate
non-transitive configuration so Gradle does not discard older SDKs through version
conflict resolution. No Android app, reference pin, cache key or bootstrap change
is needed. These are test-library jars from Maven Central, not Firebase assets or
Android SDK installations. Test execution remains network-isolated/offline.

16 offline build-contract tests and shell syntax checks pass after this correction.
The Gradle fixture-preparation task itself needs the next approved runner execution;
no local dependency download or new Actions run was performed here. PR #222 stays
draft until the test suite can execute with those fixtures present. Do not rerun
old attempts: update only the registered verification workflow after approval,
then use a new dispatch with the same already-compiled source SHA `cd0e649...`.
Registration is already complete at main commit b56616c; the remaining request is
an update of that one workflow plus one fresh run, not another bootstrap.

## Established results — do not repeat bootstrap

The owner published `reference-pins-2026-10-27-bootstrap`. Existing ZIP
`roadprints-reference-pins-2026-10-27.zip` is 157,393,480 bytes with SHA-256
`d547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c`.
No replacement, re-upload, new release, temporary URL secret or credential is needed.

- [Offline staging tests 40576749837](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40576749837)
  succeeded (14 tests).
- [Cache seeding 40579277763](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40579277763)
  succeeded using reviewed source `54c2cca24f150d52161788a82a784d4a1d2a2303`.
  Logs show published release/asset identification, ZIP/member validation, both
  local pin imports, cache save, deletion of installed files, exact-key restoration
  and final revalidation. These are existing results, not runs started by this task.

Verified shared default-branch cache key:
`reference-pins-v2-Linux-46a8b52eef4cb8117614b9944797b353027f46e4b30ffa6e3ed582a34a19e024`.
Recorded transferred cache archive: 149,008,870 bytes from GitHub cache storage.

| Restored build path | Bytes | SHA-256 |
|---|---:|---|
| `.cache/ncn/catalogue.db` | 243,724,288 | `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093` |
| `android-prototype/app/src/main/assets/historic-counties/boundaries.bin` | 8,992,277 | `b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850` |

Original source 79bcc97 remains immutable. The later published-release correction
was explicitly used by the successful seed. Routine Android consumers do not call
the release downloader or re-download this ZIP: they restore the shared cache.

## Remaining integration changes

Both `.github/workflows/android-prototype-build.yml` (Android checks) and
`build-android-prototype.yml` (private packaging) already restore that same exact
key and paths, validate both files before Java/Gradle setup and use local-only
preparation helpers. This correction adds explicit `fail-on-cache-miss: true`
to both consumers. No broad restore key, cache save or Firebase fallback exists.
A cache miss now stops at restore with the missing key; corrupt contents stop at
pin validation. Cache eviction is an infrastructure failure requiring intentional
GitHub-only recovery, not permission to retrieve Firebase reference data.

The existing development/release/NCN packaging callers use the local reusable
packager, so the protection is inherited. Source preflight rejects historical
network-capable preparation helpers before either dataset can be prepared.
Reference-data HTTP is disabled in Android unit Test workers by
`roadprints.referenceNetworkDisabled=true`; mocked transports remain usable.

A new manual-only `verify-android-reference-cache.yml` is prepared on this PR,
not registered/executed on main. It restores the same exact pins, validates them,
then compiles Android, runs lint and compiles/runs unit tests without producing an
APK. Owner/default-branch guards and exact source SHA input apply. No signing or
production secrets, cache save, distribution, publication or deployment task exists.

## Minimum verification completed here

- 24 pure Java offline checks: persistent hits, navigation/recalculation reuse,
  interrupted/corrupt updates, cancellation, concurrent deduplication, bounded
  retries, transport blocking and diagnostics.
- 16 Python offline reference/build contracts (previous 14 plus cache equivalence
  and controlled Android verification contracts). Socket connections are rejected;
  synthetic SQLite/ZIP fixtures check missing/corrupt pins and zero network calls.
- 14 existing published-release staging tests: pass, synthetic/socket-blocked.
- 4 existing Android distribution-control tests: pass, source/fixture checks.
- Diff whitespace/Python/YAML structure checks: pass.

Total: **58 offline checks passed** (24 Java + 34 Python). No live reference file
was downloaded and bootstrap was not repeated. The successful seed proves restore
and integrity in the seed job; equality of consumer keys/paths is verified from
source. The subsequent Android verification result above now proves cache restore and
compilation/lint against cd0e649; complete unit-test success remains outstanding.

Local Android validation cannot run: Java is present, but Gradle 8.10, Android SDK
35/build tools and the complete Gradle/Robolectric dependency caches are absent.
No installation or futile compile attempt was made.

## Exact remaining approval and validation

Approve only these concrete actions after reviewing this commit:

1. Update only `.github/workflows/verify-android-reference-cache.yml` on main,
   using `[skip ci] [skip render]`. This does not merge the PR, change website files
   or touch the published ZIP/cache. GitHub's managed Pages build may republish
   existing static content despite `[skip ci]`; approval must cover that side
   effect. Render API and cron follow main, so retain `[skip render]`.
2. Run that workflow once on main, setting `source_sha` to the new reviewed corrective commit identified in the
   delivery receipt/PR body. This includes the diagnostic-clear Java fixes and
   NCN environment propagation described above. No further bootstrap/seed workflow is required.

The verification job uses the runner's existing Java 17/Android SDK 35 and fails
if the SDK is missing; it never invokes sdkmanager or enables SDK downloads.
Gradle setup may obtain Gradle 8.10, and compilation/lint/unit compilation may
resolve missing build dependencies from the configured Gradle Plugin Portal,
Google Maven and Maven Central. Approval must include these ordinary build-tool
transfers, which are separate from Firebase services/Hosting. No such transfer
has occurred in this task.

Actual unit test execution is inside a Linux network namespace with loopback only,
using Gradle offline mode, Robolectric offline mode and the reference transport
guard. The three required instrumented Android test jars are resolved with normal
Gradle dependency handling into the temporary dependency directory before network
isolation. Missing test jars/dependencies still fail clearly; there is no online retry or live Firebase test. The job captures build,
lint and unit reports only, never APKs. The network-namespace procedure has been
reviewed structurally but cannot be runner-tested before approval.

The connected GitHub tools currently cannot dispatch new workflows; after approved
registration the owner can click Run workflow once and supply the reviewed SHA.
Inspect reference restore/validation first, then compile/lint/test results. If a
runner dependency is missing, record its exact name and stop; do not repeatedly
rerun or reseed. A successful run clears the final integration verification gate.
Merge still needs explicit owner approval and separate merge-event automation review.

## Scope and safety

No new app, journey, matching, map, Progress, NCN achievement, archive, website or
reference-content changes were made by this final integration correction. Android
baseline remains 0.25.218 (264), delivered lineage PR #221. Existing app-level
reference-cache fixes in PR #222 are preserved. Reference data contains national
public map data; no personal journeys or account credentials are introduced.

This task made no Firebase request, main change, workflow dispatch, production
change/deployment, secret creation, release publication, reference/cache mutation,
APK or merge. Earlier owner-approved publications/runs are recorded above and
must not be confused with actions performed during this correction.
