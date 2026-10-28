# Complete Git tree comparison — Android 0.25.220 versus main

Date: 28 October 2026. **Complete, non-truncated tree comparison; no code integration or tests performed.**

## Immutable inputs

- Android packaged source: `67e74e7d9e9873552ae98b23397cd9e265fc3517` (0.25.220 / 266).
- Production-main baseline: `35dca36a068a45316d7a8da104390f9220722e2d` (includes governance PR #231).
- Source: GitHub REST `GET /repos/adamdbird-gfc/uk-road-tracker/git/trees/{ref}?recursive=1` via connected GitHub access. Both results returned `truncated=false`.
- Counted **blob paths** (files), not directory tree entries; equality determined by exact Git blob SHA. This is a complete path/blob comparison at these pinned SHAs, not a semantic review of all changed code.

## Results

- Android source: **4,698** files (4,777 tree entries including directories).
- Main: **4,121** files (4,165 entries including directories).
- Identical paths and blob SHAs: **4,079**.
- Changed union: **629** paths: **587 Android-only**, **10 main-only**, **32 both-present but differing**.

| Domain | Android-only | Main-only | Different blob |
|---|---:|---:|---:|
| `android-prototype/**` | 361 | 0 | 16 |
| Backend Python (top-level `*.py`) | 0 | 1 | 3 |
| `tests/**` | 20 | 2 | 3 |
| `scripts/**` | 121 | 1 | 2 |
| `.github/**` | 15 | 0 | 4 |
| `docs/**` | 53 | 5 | 3 |
| Other | 17 | 1 | 1 |
| **Total** | **587** | **10** | **32** |

## Integration implications

1. **Android**: all 361 Android-only paths and 16 differing Android paths are candidates for **selective transfer**, not a whole-repo merge. Review the 16 shared Android files (including `CaptureService`, `JourneyStore`, `JourneyListActivity`, `MapActivity`, build configuration and manifest) and dependencies, tests and asset pins.
2. **Backend**: retain `main` versions of `api.py`, `database.py`, `pedestrian_matching.py`; retain main-only `road_distance_evidence.py`. Resolve any client/backend contracts explicitly. Do not revert recent Dover, walking continuity or workload changes.
3. **Tests**: Android-side 20 additional test/fixture paths and main-side 2 distinct test paths must be evaluated together; neither suite alone is a full regression baseline.
4. **Scripts**: 121 Android-only paths include extensive NCN trial/catalogue scripts. They are **not all necessary to build the app**; do not automatically promote unfinished catalogue work into main.
5. **Workflows**: preserve main release controls, bandwidth protection, backend CI and governance. Review four differing workflows; do not introduce auto-distribution.
6. **Documentation**: retain main governance and backend handover; carry forward relevant Android release evidence and documentation, not contradictory historical status statements.
7. **Assets**: preserve signing and reference pin integrity; do not publish or overwrite Firebase datasets.

## Proposed next action

The prior **300-file comparison blocker is resolved**. Prepare a candidate integration on a new, isolated branch from the pinned main SHA, transferring reviewed `android-prototype/**` files from the exact packaged source (including dependencies and tests). Keep main backend, database, scripts, workflow and governance unless an individually justified change is needed. First generate a manifest of all 377 Android changed paths and examine the 16 conflicting files and build/dependency contracts; then create candidate commits, run Android compile/lint/unit and offline-reference checks and backend tests. Record exact CI results and risks before seeking merge approval. No APK or deployment without separate approval.

## Work performed

Read-only full-tree inspection and this documentation commit. No Android code copied, no merge to main, no CI/test run, no APK, no production deployment, no PR closures or branch deletions.
