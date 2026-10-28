# Android 0.25.220 integration reconciliation

Status: **planning only — no Android source integration performed**.
Date: 28 October 2026.
Integration branch: `integration/android-025220-reconciliation`, created from then-current `main`.
Owner approval: branch creation and documentation only; integration, merges, APKs and deployments require separate explicit approval.

## Objective

Preserve the working Android 0.25.220 (266) experience and the production backend history while consolidating the accumulated stacked development branches into a reproducible, testable source baseline. An installed APK is the *functional reference*, not a substitute for buildable source.

## Verified source and delivery evidence

- Recorded private Android 0.25.220 (266) packaging source: `67e74e7d9e9873552ae98b23397cd9e265fc3517`, in the PR #225 Android development lineage.
- Packaging workflow: https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40690390058 . The development handover reports 810 tests across 109 suites, no failures/errors/skips, lint without errors, pinned reference/NCN checks and established signing certificate.
- Reported APK: 36,623,557 bytes; SHA-256 `5eb4485327a9646bc95d4f57e3959a9acc944e8feb14b3fe9605e1aa359ae265`. This is **recorded artifact evidence**, not an independent hash of the APK currently installed on the owner's phone.
- PR #225 head inspected: `c5fdaa5dc9e681493ed40fca48c497c28aa415e1`, exactly **one descendant commit** after the packaged source. That commit only modifies `docs/development-handover.md` and `docs/pedestrian-visual-correction.md`; no Android code changes.
- At comparison time, packaged Android source versus current `main` had diverged: Android side 657 commits ahead; `main` 21 commits ahead. These counts reflect cumulative stacked history, **not** 657 distinct features to merge. GitHub comparison file lists can be truncated at 300.
- PR #225 remains a draft based on `fix/pedestrian-route-accuracy`, not `main`. The chain includes PR #223, #221, #220, #219, #218, #215, #213, #212, #211, #210, #209 and #207. Older chains also exist.
- Backend-only pedestrian PR #226 was recorded as squash-merged to `main` at `d7f07060ebf15cee1d32fd3f948248d59f454cb2` and deployed to Render as `dep-dbgun1ivcj2c73dslg60`. This is historical deployment evidence, **not a fresh verification of the currently live deployment**.
- Since the Android build, `main` includes backend pedestrian/road matching corrections, tests, workflow/reference-cache changes and governance PR #231. It must not be overwritten by a branch reset.
- Existing open NCN data PRs #175–187 are a separate catalogue lineage and must not be automatically incorporated or discarded.
- As of the read-only inventory after PR #231 was opened, there were 39 open PRs. This is a dated count, not an evergreen total.

## Risks and open questions

1. **Build identity:** verify installed APK version/code, package/signature and, where accessible, APK SHA against the recorded artifact. Do not assert exact device/source identity solely from PR text.
2. **Source reproducibility:** verify build from pinned source SHA and dependency/reference pins in a controlled CI run; no APK distribution.
3. **Backend coexistence:** inspect differences in shared files (e.g. `api.py`, `pedestrian_matching.py`, `database.py`, road evidence modules) and Android clients; preserve current backend bug fixes and deployed behaviour.
4. **Stacked history:** distinguish PRs already contained in the Android source from incomplete/superseded changes. Do not bulk-close or delete branches based only on ancestry.
5. **User acceptance:** Dover ferry-terminal recovery, Charing Cross walking accuracy and 503 workload handling require regression evidence; known pedestrian visual uncertainty remains.
6. **Release safety:** no automatic Firebase tester distribution, no unnecessary APK, no production database mutation, no changes to production deployment.

## Proposed integration sequence — requires separate approval

1. Pin the exact `main` SHA used for this integration branch and preserve the Android build source SHA and PR #225 documentation head as immutable references.
2. Produce a per-file and per-PR integration matrix, separating Android-only, backend-only, workflow/reference, documentation and NCN catalogue changes; identify conflicts and provenance. Confirm actual source containment rather than guessing from titles.
3. Prepare an isolated integration commit or PR **onto this branch**, retaining the current `main` backend, tests and governance. Do not force-push `main`, reset it to the Android branch, or merge the full historical PR chain blindly.
4. Run Android compile/lint/unit tests, pinned offline-reference and NCN checks; backend/PostgreSQL tests; pedestrian and driving matching regressions; build provenance and performance budgets. Record failures and any differences from the installed APK.
5. Request owner review of the exact diff, checks, rollback and release implications. Only then propose integration into `main`. No APK or deployment without separate authorisation.
6. Once integrated and verified, classify each historical PR as incorporated, active, superseded or deferred with evidence; propose closure and branch deletion separately. Keep NCN catalogue reconciliation independent.

## Current action log

- Created `integration/android-025220-reconciliation` from `main`.
- Added this reconciliation plan only.
- **No Android code merged, no PR closed, no APK built or distributed, no backend/Firebase deployed, no database data changed.**
