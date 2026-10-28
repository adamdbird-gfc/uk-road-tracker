# Review of four divergent legacy PRs

Date: 28 October 2026. Read-only GitHub PR patch, ancestry, file SHA and selected source inspection. No code integration, test execution, PR closure or deployment.

## Decision matrix

| PR | Distinct branch evidence | Current baseline comparison | Disposition |
|---|---|---|---|
| [#172](https://github.com/adamdbird-gfc/uk-road-tracker/pull/172) — early NCN Android trial | Three branch-only commits relative to 0.25.220 build; PR adds opt-in eight-route trial, `NcnTrialActivity`, pinned 206 source-way associations and local checkpoint logic | `NcnTrialActivity.java` exists at #172 head (blob 167eddfa) but is absent at packaged Android SHA and `main`; `NcnCatalogue.java` exists at packaged SHA but differs (PR 193d0abc vs packaged 5ae70a2c). The later NCN implementation has superseded the eight-route trial's scope, but a full behavioural equivalence proof is not done. | Do not cherry-pick. Keep PR open until current NCN capabilities and historical test cases are mapped; then propose superseded closure with evidence. |
| [#163](https://github.com/adamdbird-gfc/uk-road-tracker/pull/163) — place-aware visits | One branch-only commit relative to build; PR changes `CaptureService`, `CaptureMovementEvidence`, `ContextMap`, `PlaceBoundaryEvidence`, arrival dwell and visit buffering. It distinguishes school/site dwell from sustained departure or exercise. | CaptureService blobs differ across PR (7cf97a1a), packaged build (7d981e16), main (7360b580); ContextMap PR (6d917cb7) differs from packaged (a7be14d4), absent on main. Later capture history is extensive; no evidence that the lone branch-only commit is independently required. | Do not cherry-pick. Compare school suppression, campus boundaries, running loops and departure fixtures against 0.25.220 before superseded decision. |
| [#7](https://github.com/adamdbird-gfc/uk-road-tracker/pull/7) — screen caching | One branch-only commit relative to build; historical PR patch also includes rail station/capture work due to its stacked base. Proposed journey-discovery disk cache and bounded map cache. | JourneyListActivity blobs PR 704f69b5, packaged 3eae4928, main 4e48f654; CaptureService PR 3eac085e, packaged 7d981e16, main 7360b580. Later performance work is in packaged build. No equivalence or measured speed comparison established. | Do not cherry-pick. Compare specific cache invalidation and archive-load behaviour against modern performance implementation; test first useful Journeys and Progress. |
| [#8](https://github.com/adamdbird-gfc/uk-road-tracker/pull/8) — router rate limits | Four branch-only commits relative to build; PR changes `api.py`, workflow and adds `tests/test_match_upstream_failures.py`. | `api.py` differs (PR 572421b7, packaged 1836196b, main 936f43b9). **Current main's `osrm_match_chunk` explicitly maps upstream 429 to 503 with Retry-After, upstream 5xx to 502, before NoMatch/TooBig handling.** Historical dedicated test file is absent from packaged build and main. | Do not restore old backend. Assess current test coverage for 429/502 non-recursive behaviour; adapt useful regression cases to current interfaces if missing. |

## Important caveats

- PR patches are against their *own historical base*, so the patch may contain inherited or stacked changes. A differing blob SHA does not mean the old implementation is better or missing.
- These are **source-level inspections**, not full semantic equivalence audits, runtime validation or test execution.
- The NCN trial is distinct from the separate NCN catalogue PR chain #165/#175–187; neither should be automatically merged.
- `main` already contains the essential #8 rate-limit response behaviour. This is a positive code-level finding, but the exact historical regression test is not present. No assertion that all failure modes are covered.

## Next steps

1. For #172: enumerate original eight-route trial guarantees and compare to packaged NCN catalogue, history and correction semantics; preserve relevant regression fixtures, not obsolete UI.
2. For #163: test school/campus stay suppression, departure and parkrun-style closed-loop exercise against packaged Android behaviour.
3. For #7: compare cache keys, invalidation and archive performance; measure rather than restoring old caching wholesale.
4. For #8: inventory modern API tests, add bounded upstream 429/5xx regression cases only if coverage is missing; keep newer production API implementation.
5. Build reviewed Android-only integration changes atop the protected current-main baseline on this integration branch **only after separate owner approval**. Preserve backend, governance, release controls and data history.
6. Keep all four PRs open pending integration/verification; propose closures separately with evidence.

No `main` changes, source merge, APK, deployment, PR closure, branch deletion or database/reference-data mutation performed.
