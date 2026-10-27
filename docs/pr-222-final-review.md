# PR #222 — final build-cache integration review

Status: reference bootstrap complete; Android runner verification still requires
approval. Do not merge yet. This report supersedes earlier draft-release, signed
URL and cache-bootstrap preparation instructions.

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
source. A normal Android workflow's own restore/build has **not** yet been run
against the final integration commit. Do not describe that as completed.

Local Android validation cannot run: Java is present, but Gradle 8.10, Android SDK
35/build tools and the complete Gradle/Robolectric dependency caches are absent.
No installation or futile compile attempt was made.

## Exact remaining approval and validation

Approve only these concrete actions after reviewing this commit:

1. Register only `.github/workflows/verify-android-reference-cache.yml` on main,
   using `[skip ci] [skip render]`. This does not merge the PR, change website files
   or touch the published ZIP/cache. GitHub's managed Pages build may republish
   existing static content despite `[skip ci]`; approval must cover that side
   effect. Render API and cron follow main, so retain `[skip render]`.
2. Run that workflow once on main, setting `source_sha` to the exact reviewed
   integration commit, not main's older Android baseline. The delivery receipt/PR
   body identifies that full SHA. No further bootstrap/seed workflow is required.

The verification job uses the runner's existing Java 17/Android SDK 35 and fails
if the SDK is missing; it never invokes sdkmanager or enables SDK downloads.
Gradle setup may obtain Gradle 8.10, and compilation/lint/unit compilation may
resolve missing build dependencies from the configured Gradle Plugin Portal,
Google Maven and Maven Central. Approval must include these ordinary build-tool
transfers, which are separate from Firebase services/Hosting. No such transfer
has occurred in this task.

Actual unit test execution is inside a Linux network namespace with loopback only,
using Gradle offline mode, Robolectric offline mode and the reference transport
guard. Existing instrumented Android jars are copied from restored Gradle/Maven
caches into a temporary dependency directory. Missing test jars/dependencies fail
clearly; there is no online retry or live Firebase test. The job captures build,
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
