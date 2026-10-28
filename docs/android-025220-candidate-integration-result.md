# Candidate integration result — Android 0.25.220

Date: 28 October 2026.

## Created

- Candidate branch: `integration/android-025220-candidate`.
- Draft review: https://github.com/adamdbird-gfc/uk-road-tracker/pull/233 (**DO NOT MERGE**).
- Candidate commit: `3a5df5d4be5f25450581233afa1c0dd0998e0849`.
- Parent `main` baseline: `35dca36a068a45316d7a8da104390f9220722e2d`.
- Packaged Android source: `67e74e7d9e9873552ae98b23397cd9e265fc3517`.

## Implementation

Created a Git tree using the pinned main baseline as the base tree, and applied **377 changed blobs** exclusively beneath `android-prototype/**` from the packaged Android source: **361 added paths and 16 modified paths**. No deletes were required in that subtree relative to main. No other source, workflow, documentation or dataset paths were transferred.

## Verification performed

Retrieved the complete recursive Git trees for candidate, packaged Android and pinned main; all three returned `truncated=false`.

- Candidate contains **4,482 files**.
- **0 Android path/blob mismatches** between candidate and packaged Android source.
- **0 non-Android path/blob mismatches** between candidate and pinned main.
- Candidate therefore preserves the exact packaged Android subtree while retaining the exact pinned main backend, workflows, scripts, governance, docs and data elsewhere.
- At initial inspection, commit status checks: **none**; PR-associated workflow runs: **none**. Draft PR was then opened to permit review and potentially trigger configured PR checks. **No green CI claim.**

## Risks / validation still required

1. **Buildability unproven:** run Android Gradle compile, unit tests and lint; verify signing, release configuration and pinned reference assets without distributing APKs.
2. **Backend compatibility unproven:** check Android request/response contracts with the later main backend; run current backend tests and walking 503 workload, Charing Cross pedestrian geometry, Dover drive evidence and 429/502 upstream regressions.
3. **Behaviour unproven:** school/site visit suppression, sustained exercise and ferry capture; map/NCN responsiveness and journey loading against installed 0.25.220 baseline.
4. **CI compatibility:** main's retained workflows may need Android-specific build/test updates. Do not overwrite main's reference-cache or release safety protections.
5. **Main may advance:** before any proposed merge, recompare candidate against updated main and reconcile changes.
6. **Not a deployment:** no APK, tester distribution, Render/Firebase deployment, PR closure, branch deletion or main merge.

## Recommended next action

Review PR #233's checks and run explicit Android compile/lint/unit + offline-reference checks and backend regression suite in a suitable CI/workspace. Record concrete outcomes and fixes on the isolated candidate branch. Seek owner approval for any eventual merge/release separately.
