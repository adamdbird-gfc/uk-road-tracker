# PR #222 final review and safe build procedure — 27 October 2026

## Disposition

**Keep PR #222 in draft. Do not merge yet.** Reviewed the complete initial diff
against `42f1c8d` (delivered Android 218 lineage), including cache code, caller
changes, transport instrumentation, workflows, preparation scripts, tests and
documentation. Corrective changes remain exclusively in this PR. Android version,
release/development requests, matching calculations and map rendering are unchanged.
No Firebase requests, deployments, live tests, Actions dispatches, APK generation,
installation, distribution or production changes were performed.

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
  -Drobolectric.offline=true
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
   or org.json/Robolectric jars. No dependencies were downloaded. Four new Android
   test methods (boundary structure/wrappers and NCN retry/cancellation), plus the
   existing suite, are authored but unexecuted. Helper tests do not prove these
   Android integrations compile or pass.
2. **Real pinned references are not available locally and no shared v2 cache has
   been seeded.** The import/seed logic was verified using synthetic fixtures only.
   Identify an existing verified bundle and arrange an approved seed in a visible
   cache scope. Normal builds intentionally stop on a cold/invalid pin until then.

After resolving those blockers, preserve existing app data and later perform
Pixel/Samsung acceptance: settlement map reopen/offline boundaries, NCN initial
failure then explicit Retry, cached NCN restart/navigation, cancellation,
unchanged app upgrade, and unchanged main-map/Progress responsiveness. No APK is
requested by this review; leave device verification for the next authorized build.
