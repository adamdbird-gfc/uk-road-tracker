# PR #222 final review and safe build procedure — 27 October 2026

## Artifact ingress proposal — current next step

Run 40555030074 confirmed the read-only runner did not discover the draft even
with corrected inputs/source. The ZIP is still unchanged/unpublished and no
cache was saved. The [artifact bootstrap proposal](pr-222-artifact-bootstrap.md)
provides the reviewed one-file manual staging workflow and exact approval gates.
It uses a temporary GitHub asset URL kept in an Actions secret (an asset-scoped
credential, requiring approval), pinned 79bcc97 local validation, and artifact
upload after ZIP/member/SQLite/county checks. Seven socket-blocked tests pass.
No workflow, credential creation, main/cache/release change or Firebase access
occurred during preparation. The existing cache seed workflow needs no edit;
use source 79bcc97 and artifact inputs only after staging is approved/successful.

## Disposition

**Keep PR #222 in draft. Do not merge yet.** Reviewed the complete initial diff
against `42f1c8d` (delivered Android 218 lineage), including cache code, caller
changes, transport instrumentation, workflows, preparation scripts, tests and
documentation. Corrective changes remain exclusively in this PR. Android version,
release/development requests, matching calculations and map rendering are unchanged.
No Firebase requests, deployments, live tests, Actions dispatches, APK generation,
installation, distribution or production changes were performed.

## Draft-release seed correction — current status

Read [the current approval procedure](pr-222-seed-authorisation.md). The existing
verified ZIP is uploaded to draft release 425479238, asset 681638825. Main's manual
workflow was registered as 69537eb. Approved run 40511948834 failed on the published
tag lookup before asset download/cache publication. The corrected script now uses
authenticated paginated release/asset discovery and requires exact draft/asset
identity, size and SHA-256. Both payload integrity checks remain unchanged.

14 socket-blocked synthetic tests pass, including permission/404 failures and
successful authenticated asset staging. No new Actions run or main edit occurred.
79bcc97 remains immutable; the next run must explicitly approve the corrective
commit SHA to obtain the fixed script. The workflow file itself is unchanged.
Contents-read API support is documented, but actual runner access to the draft
requires later approved verification; there is no silent permission escalation.

Registration unexpectedly triggered a managed GitHub Pages deployment despite
skip markers. Render history showed no new deployments. Earlier statements below
about no uploads/registration/Actions/deployments describe prior investigation
sessions, not the current overall project state. Cache remains unseeded and full
Android compilation/lint/Robolectric remain pending; keep the PR in draft.

## Reference discovery and local bootstrap — completed follow-up

The two exact reference assets have now been recovered **without Firebase access**.
The original Library archive `roadprints-ncn-firebase-publication-2026-10-19.zip`
contains the compressed NCN catalogue; its embedded manifest exactly matches the
committed revision-1 manifest. Existing local APK 218 supplies the county binary;
the APK's catalogue and full APK hash match the delivered baseline.

| Asset | Verified bytes | Verified SHA-256 | Provenance / validation |
|---|---:|---|---|
| NCN compressed input | 142,796,162 | `91c8e25f0d58ced6a1e2c91905fe4190b826dc8a469aafc4aab09b83dee1fd91` | Previously prepared publication ZIP, Library file `libfile_8ab97d8102a88191905627b3c064c7cd`; manifest revision 1 matches exactly. |
| NCN database | 243,724,288 | `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093` | Bounded local decompression; SQLite `quick_check=ok`; 219 routes. |
| County boundary binary | 8,992,277 | `b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850` | Recovered from existing `roadprints-0.25.218.apk`; county version `2026-10-06`; catalogue exactly matches source. |

Source APK SHA-256:
`a514e61d1a7fb6bcec3eedcfc44b237fd452c3b5a9ec6b96e5ac2ae89ffd8863`.
The inspected GitHub packaging run `40209370261` retains the same APK in artifact
`12604584815` (reported expiry 1 November 2026); its other artifact is reports.
Publication run `39330952075` retains a 468-byte publication receipt, not the NCN
binary. No binary was fetched from these Actions artifacts and neither run was
re-executed. This is not an exhaustive claim about every repository artifact.

Local import and bootstrap succeeded:

- `.cache/ncn/catalogue.db` is the verified national database.
- `android-prototype/app/src/main/assets/historic-counties/boundaries.bin` is the
  verified county pin.
- `.cache/reference-bootstrap/` holds both verified source files and a ready-to-seed
  ZIP. These are real pins, not synthetic replacements. All generated reference
  paths are ignored by Git; no dataset is included in the PR.
- Both build workflows' cache restore paths and key expressions exactly equal the
  seed workflow's save configuration. This is a source/configuration comparison,
  **not proof that a shared GitHub cache has been saved or restored**.

A reusable `roadprints-reference-pins-2026-10-27.zip` was prepared from the exact
verified files (plus their manifests and README): **157,393,480 bytes**, SHA-256
`d547847572e574cecd16cdb8e08077bf2e0a1014507c7f876c94c7c88b97853c`.
The prepared bundle is saved as Library file
`libfile_84308ddbbf488191add27b1e27dd0e5e`:
[download the verified bundle](https://chatgpt.com/library?file_id=libfile_84308ddbbf488191add27b1e27dd0e5e&account_id=personal).
The source publication ZIP remains available independently for recovery.

The existing **real national catalogue test passed** with socket connections
rejected. It checks the pinned database, 219 route identities, 62,399 physical
ways, 62,487 route-way rows, held identities, overviews, network length and all
canonical target-to-network mappings. Prior 57 synthetic/control checks were not
repeated during this discovery task; their earlier results remain historical
verification evidence. This one real-data test is not Android integration proof.

One offline-verification correction was required: Gradle daemon `-D` settings
are not automatically inherited by forked Test workers. The Test configuration
now explicitly forwards `robolectric.offline` and `robolectric.dependency.dir`.
The wiring was checked statically; full Gradle execution remains unavailable.

### Build environment inventory

| Prerequisite | Current status |
|---|---|
| Java 17 | OpenJDK 17.0.20 and `jdk.compiler` module available. |
| Gradle | Required workflow distribution 8.10 absent; no local dependency cache found. |
| Android SDK | Platform 35 / `android.jar`, SDK build tools and platform tools absent. |
| Android Gradle plugins | No cached AGP 8.6.1, Google Services 4.4.4 or Crashlytics 3.0.6 dependencies. |
| Robolectric | 4.14.1, required instrumented SDK jars for test API 28/33, JUnit 4.13.2 and org.json 20240303 jars absent. |
| App Maven dependencies | No usable Gradle dependency cache for the existing declared dependencies. |
| Reference assets | Both exact pins now available and locally bootstrapped. |

Search covered repository files, local development/workspace caches, common SDK/
Gradle locations and available Java runtime roots. No SDK, Gradle, Maven or
Robolectric dependencies were downloaded, and no unavailable build was attempted.

### Shared cache remains unseeded

The ready local ZIP is not itself a GitHub Actions artifact. The current seed
workflow takes an **existing same-repository artifact run ID and name**; the
inspected runs above do not provide such a two-pin bundle. The connected GitHub
capabilities used here can inspect/download artifacts but do not upload a local
file as an Actions artifact or directly save a shared cache.

Before CI can resume: arrange an explicitly authorised, reviewed staging step to
make this verified ZIP's extracted files available as a GitHub artifact, then
explicitly authorise the seed workflow on a ref visible to the intended builds
(default-branch scope preferred). Do not supply the APK/report/receipt artifact
IDs as if they were a reference bundle. The workflow must also be available for
manual dispatch; no default-branch file was installed or merged by this task.
No Actions run is authorised merely by preparing these files or documentation.
After an approved seed, confirm both cache restores and full offline verification
on the prepared runner. Until then, cold CI builds must continue to fail closed.

## Review findings resolved

| Finding | Correction |
|---|---|
| Packaging checks out `source_ref`; an older source could execute old download helpers | Both build workflows verify the offline helper contract immediately after checkout and before any reference preparation. Historical/missing helpers fail. NCN preparation has **no network implementation or remote opt-in flag**. |
| Release and NCN callers selected a reusable workflow on another branch | All three Android packaging callers now use the local reusable workflow from their own reviewed workflow revision. Previously pinned external invocations remain unchanged elsewhere in Git history and must not be launched. |
| Cache restoration could not bootstrap a fresh runner | Added local validated import of decoded or compressed NCN files, a two-pin seed command, and a dormant manual GitHub-artifact-only cache seed workflow. No seed operation was executed with real data. |
| Ordinary build jobs could auto-save cache contents after tests | Builds use **restore-only** actions. Only the owner-triggered seed workflow can save the v2 reference key, after hash/size/SQLite validation. No broad restore keys are used. |
| One slow boundary lookup serialized every boundary cache operation | Bounded striped interruptible locks share same-key work while allowing different stripes to proceed. Pre-cancelled requests do not poison subsequent retries. Failure writing backoff metadata does not hide a valid previous generation. |
| Boundary validation accepted empty or malformed nested rings | Validate ring lengths, closed rings and finite coordinate bounds before persisting. Polygon, MultiPolygon, Feature and FeatureCollection are supported, preserving the existing geometry consumer's wrapper support. Invalid Render responses retain the Firebase fallback. |
| NCN cold-open diagnostic context leaked on its worker thread; cancelled requests could start discovery | Cold-open context is cleared in `finally`. Check interruption before setting a retry deadline and again before installation. Retry deadline is persisted synchronously on the background install path. Existing offline generation, resume/hash/SQLite/atomic-pointer rules are retained. |
| Android unit tests could accidentally open a reference HTTP connection | Gradle Test workers set `roadprints.referenceNetworkDisabled=true`; all three Firebase reference clients use the guarded transport, which rejects before `URL.openConnection`. Mock Transport and synthetic URL fixtures remain supported. Runtime APK behaviour defaults to enabled networking. |

The preflight checks protect reviewed/historical repository source versions; they
are not a sandbox for adversarial build scripts. They do not execute imported
reference artifacts. Synthetic test payloads cannot seed a real pinned cache.

## Safely populate and maintain the reference pins

Required real inputs, already held locally or in an existing trusted archive:

- `catalogue.db`: **243,724,288 bytes**, SHA-256
  `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`.
- `boundaries.bin`: inferred **8,992,277 bytes**, SHA-256
  `b81fe3bf42400548c45576750d4e40ca2795975ddbc8903b83a9bb6072c6e850`.

The committed manifests are authoritative if those pins change. Do not retrieve
missing inputs from Firebase. An existing verified CI/local NCN database or
compressed reference is suitable. County boundaries can also be recovered from
an already-held APK with `recover_county_reference.py`; the national NCN DB is
**not** bundled in the APK.

Local bootstrap (no URL arguments or network code):

```sh
python3 scripts/verify_offline_build_controls.py
python3 scripts/seed_reference_pins.py --source-directory /path/to/existing-reference-bundle
python3 scripts/prepare_ncn_reference.py
bash android-prototype/scripts/prepare_county_reference.sh
```

For an existing local compressed NCN pin, first import it using:

```sh
python3 scripts/prepare_ncn_reference.py --source-archive /path/to/existing/catalogue.db.gz
```

This verifies compressed length/hash, bounds decompression, verifies decoded
length/hash/SQLite/route count, then atomically replaces the build reference.
A failed import retains an existing destination. For compressed-only bootstrap,
provide county data separately using `ROADPRINTS_COUNTY_REFERENCE_FILE` when
running county preparation. No catalogue/membership metadata is rewritten.

Future CI bootstrap: use `reference-pin-cache.yml` only when a same-repository
GitHub Actions artifact containing the two exact files already exists. Supply its
run ID and artifact name. The workflow reads only GitHub's artifact service,
verifies both pins, and saves `reference-pins-v2-${runner.os}-${hashFiles(...)}`.
Run it in a ref whose cache scope is visible to the intended builds: the default
branch is preferred; a PR merge-ref cache is not a shared seed. A new manual
workflow must first be made available for dispatch through a separately reviewed
repository change if GitHub does not yet list it. This PR does not perform that
bootstrap or create a reference artifact.

Keep a durable approved copy of the verified bundle outside the evictable cache.
On eviction, repeat the same local/artifact validation, never a Firebase fallback.
On a pin update, use a new matching bundle/key. If a v2 key contains corrupt data,
the build fails; remove that exact GitHub cache entry and reseed under owner
control. Ordinary builds cannot overwrite an existing immutable cache key.

Once all tooling/dependencies are already local, verify **without assembling an
APK**:

```sh
NCN_TEST_DATABASE="$PWD/.cache/ncn/catalogue.db" \
  gradle --offline --no-daemon -p android-prototype \
  :app:compileDebugJavaWithJavac :app:lintDebug :app:testDebugUnitTest \
  -Drobolectric.offline=true \
  -Drobolectric.dependency.dir=/path/to/preinstalled-robolectric-jars
```

Gradle `--offline` alone does not guarantee Robolectric has all SDK artifacts.
Its SDK jars/dependencies must also be pre-provisioned; stop if any are missing.
No signing or assemble task is required. The reference-network test guard is
already set in the Test task. Avoid old source refs and external workflow SHAs.

## Merge and Actions trigger assessment

Assessed all committed workflow trigger filters against this PR's changed paths,
including the review corrections. **Ignoring skip markers**:

| Event | Matching workflows |
|---|---|
| Merge/push to current base `fix/navigation-progress-responsiveness` | None: development request JSON is unchanged. |
| PR open/synchronize against that base | Android checks; Android release-request checks. Both are source checks, with reference preparation guarded/offline and reference networking disabled in Android Test workers. |
| Later merge/push to `main` | Android checks only for these paths. Missing reference pins fail rather than downloading. |

No Hosting publication paths/request files, distribution controls or production
configuration were changed. The new seed workflow is manual only. NCN publication
and approved distribution workflows remain manual/request-driven; their
publication operations are not made generally safe to run by this build review.
The review update commit uses `[skip ci] [skip render]` and no workflow was
requested. Trigger safety is established from source filters and actual changed
paths, rather than relying solely on that skip marker. Reassess after retargeting
or if other changes are added to the merge.

## Verification performed and remaining blockers

**57 offline checks passed**: 24 Java helper checks; 14 Python reference/import/
workflow-contract checks; 4 distribution-control checks; 9 release-request checks;
6 county recovery checks. Java helper classes compile on JDK 17; Python and shell
syntax, all workflow YAML parsing and `git diff --check` pass. Java rejects every
unplanned URL transport; Python reference tests reject socket connections.

New coverage includes verified local SQLite/gzip import, bundle seeding, corruption
rejection without clobbering existing files, rejection of old network helpers,
restore-only cache controls, local reusable workflow callers, interruption,
backoff-write failure fallback and concurrent independent reference keys.

**Remaining blockers to merge-ready status / normal build restoration:**

1. **Full Android compile/lint and Robolectric suite have not run.** This workspace
   has JDK compiler modules but no Android SDK, Gradle installation/dependency cache
   or org.json/Robolectric jars. The real reference pins are now local, but no build
   dependencies were downloaded. Four new Android
   test methods (boundary structure/wrappers and NCN retry/cancellation), plus the
   existing suite, are authored but unexecuted. Helper tests do not prove these
   Android integrations compile or pass.
2. **Shared v2 cache seeding/restoration is still unverified and unauthorised.**
   Both real pins are now verified and locally bootstrapped; asset discovery is
   resolved. A prepared bundle exists, but approved GitHub artifact staging and
   an explicitly authorised seed run in a visible scope are still required.
   Normal cold CI builds intentionally stop until this is completed.

After resolving those blockers, preserve existing app data and later perform
Pixel/Samsung acceptance: settlement map reopen/offline boundaries, NCN initial
failure then explicit Retry, cached NCN restart/navigation, cancellation,
unchanged app upgrade, and unchanged main-map/Progress responsiveness. No APK is
requested by this review; leave device verification for the next authorized build.
