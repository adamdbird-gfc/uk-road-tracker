# Android 0.25.220 (266): integrated baseline and provenance

Permanent record, 29 October 2026. This supersedes the pre-integration status,
proposals and incomplete-test statements on historical PR #232
(`integration/android-025220-reconciliation`, head `00355b20`). It records completed
integration rather than asking maintainers to repeat the investigation.

## Source and integration

- Known working Android version: **0.25.220 (266)**.
- Exact packaged application source: `67e74e7d9e9873552ae98b23397cd9e265fc3517`.
- PR #225 head `c5fdaa5dc9e681493ed40fca48c497c28aa415e1` is a documentation-only
  descendant of that source. Its final `docs/pedestrian-visual-correction.md` is
  already byte-identical to current main. Its old handover must not replace the
  later selection7, workload and Dover correction records.
- Pre-integration production main: `35dca36a068a45316d7a8da104390f9220722e2d`.
- Initial isolated candidate: `3a5df5d4be5f25450581233afa1c0dd0998e0849`.
- Final tested #233 head: `7f5f3b6fb41f17b8f64a7e5c2af306714beca3ea`.
- #233 merged as `51d693f2b4cef4ee5ce9dc968c4d068deb818c79`.
- #234 source `33e80e639ac40c6257826b688c20226fe0206c54` adds only four router
  regression tests, comparison documentation and a PR-only validation workflow.
  Authorised merge: `89ae65dc972d0c7e426e8e87cc74b5c2944ac63b`, with
  `[skip render]` in the title/body to prevent an automatic backend deployment.

## Complete Git tree comparison

The complete path/blob comparison is reproducible with `git ls-tree -r REF`;
it does not rely on GitHub's capped 300-file comparison view. Count blob paths,
then compare the exact Git blob identity for every path in the union.

Recomputed on 29 October from the exact pinned source and original main:

| Result | Blob paths |
| --- | ---: |
| Packaged source repository | 4,698 |
| Original main repository | 4,121 |
| Identical at both refs | 4,079 |
| Source-only | 587 |
| Main-only | 10 |
| Present at both with different blobs | 32 |
| Changed path union | 629 |

The Android-only integration transferred 377 changed blobs beneath
`android-prototype/**`: 361 added paths, 16 modified paths, no deletions.
Recomputed invariants for the initial candidate:

- **Zero Android path/blob mismatches** against packaged source.
- **Zero non-Android path/blob mismatches** against original production main.

Final main after the preservation merges still has **zero Android path/blob mismatches** against
packaged source, and **zero top-level backend Python blob changes** against the
pre-integration production main. Later candidate CI commits added only the
validation workflow needed to restore/verify offline reference prerequisites.
#234 adds no application or API runtime code.

## Backend and reference preservation

The integration deliberately retained main's `api.py`, `database.py`,
`pedestrian_matching.py`, `road_distance_evidence.py`, response/evidence contracts,
production tests, reference-cache/release controls and governance. This protects
the later pedestrian continuity/workload fixes and Dover ferry-terminal distance
evidence correction. Do not merge an old Android branch wholesale to recover
missing documentation, NCN tools or fixtures.

Android's national NCN pin remains schema2, database
`973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`, 219 identities,
62,399 unique ways, 34 held identities and checkpoint
`e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b`.
The immutable default-branch GitHub cache supplies the pinned NCN SQLite database
and historic county boundaries. Both are checksum-verified before Android tests;
normal validation does not republish or repeatedly download Firebase data.

## Validation evidence

| Source/run | Result |
| --- | --- |
| [#233 integration CI](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40834735209) | Compilation, lint, all 810 Android unit tests passed; offline data restored and verified. |
| [#234 Android CI](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40840224430) | 810 tests / 109 suites; zero failures, errors or skips; compilation and lint passed, zero lint errors. |
| [#234 backend/PostgreSQL CI](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40840224253) | 130 passed, zero failures/errors/skips, including the four preserved router regressions. |

Downloaded #234 XML independently confirmed the totals. Report artifact
`12882098539`, ZIP SHA-256
`bb8685701111c3f8856deb94d3f15cfcd7737b399ec426ab3dedf625d62ba7eb`.
Tests establish source compatibility and regression coverage; they do not invent
new physical-device performance measurements or full pedestrian visual acceptance.
Existing [pedestrian visual evidence](pedestrian-visual-correction.md),
[continuity record](pedestrian-continuity-selection7.md),
[workload record](pedestrian-workload-correction.md) and
[Dover correction](dover-road-matching.md) retain their separate evidence/limits.

## Historical reconciliation decisions

Twenty confirmed incorporated PRs were closed without merging: #223, #221, #220,
#219, #218, #215, #213, #212, #211, #210, #209, #207, #197, #196, #182, #174,
#171, #168, #6 and #1. Their heads were ancestors of the packaged source.

Four divergent PRs (#172, #163, #7, #8) were separately compared by functionality
and tests. Existing features were retained/extended; #8's four useful lower-level
router tests were preserved and are now integrated through #234. See the
[detailed four-PR record](historical-pr-reconciliation.md). Its statement that
#234 awaits merge is historical; the authorised merge is recorded above.

#225's useful visual record is already on main. #232's six investigation documents
are retained on its historical branch; this curated record preserves their durable
baseline, complete comparison, safeguards, validation and disposition findings.
Its old “do not merge #233”, no-CI and pre-integration warnings are superseded.
NCN research/catalogue tooling is integrated separately through #235 at
`8c1e50ca8a3713d096c4efd02a15a549039bbce0`, from reviewed source `a76f0bcd`.
Its 333 NCN/reference/cycling tests passed with zero failures/errors/skips in
[CI40842500420](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40842500420)
and the final local review. The new provenance verifier also checks the complete
pinned archive and exact published database. No active catalogue or application
code changed. See [NCN preservation and outstanding research](ncn-historical-reconciliation.md).

PR #236 integrates this permanent record and current handover. Normal development
resumes from main containing #233, #234, #235 and #236; historical branches are
retained, not individually merged. The final #236/main commit is recorded by its
GitHub merge event and sign-off comment, avoiding a self-referential document hash.

No branch deletion, backend/Firebase deployment, APK build/distribution or new
production-data publication is part of this documentation preservation.
