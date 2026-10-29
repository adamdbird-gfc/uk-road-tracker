# Four divergent historical PRs: reconciliation

Baseline: `main` at `51d693f2b4cef4ee5ce9dc968c4d068deb818c79`, the merge of #233.
Android remains 0.25.220 (266); #233 recorded compilation, lint and 810/810 unit tests.
This review does not change application/backend source, versions, reference data or
production configuration. Live backend deployment identity was not queried because
no deployment is part of this task. Device acceptance is not inferred from tests.

## #172 — early NCN Android integration

Head `c0485d0d7f86dbeb7897d583cede582a62fc49a3`; commits unique to its original
PR base: `e4aedd73`, `230c68ba`, `c0485d0d`. Reviewed all 21 changed paths.

The eight-inventory adapter, pinned opaque binary asset, Python-compatible hashes,
walking/source and bicycle/node evidence, interval union, mode navigation, correction
exclusion and opt-in trial are present or extended in current Android. The trial
manifest/binary and golden fixture are byte-identical to this head. The national
catalogue uses indexed SQLite and adds a deduplicated physical-network union; it must
not be replaced with the early in-memory adapter. Trial loader tests explicitly load
the trial instead of relying on the now-national default loader.

All original NCN test scenarios remain: `NcnAndroidTest` retains the golden results,
canonical hashes, invalid metadata, exclusions and navigation. Its removed
`checkpointSurvivesRestartAndDeletedHistoryCannotReturn` is superseded by
`NcnNationalTest.nationalCheckpointsResumeDeleteAndKeepModeNavigation` and
`HistoryStabilisationTest.newEditedAndDeletedJourneysReuseUnchangedEvidence`:
checkpoint reuse, corrupt/stale rejection, edit/delete and removal of stale credit.
The original compiler `scripts/build_ncn_android_trial.py` is absent from main; it is
an obsolete producer requiring the separate trial checkout, not missing app behaviour.
Its source remains on the historical branch. No catalogue-chain changes are needed.
Disposition: fully superseded by #233's Android integration; close without merge.

## #163 — place-aware visits and suppression

Head/sole unique commit `26bd7d6fe8ecc7f8da73ffd6b4bb33549e04be9d`; reviewed
all 11 changed paths and the later `0ecbe416` reconciliation carried by #197.

Current `CaptureService` retains the local visit buffer, checkpoint/restore, departure
seeding, disarming cleanup and station/manual bypass. `PlaceVisitEvidence`,
`PlaceGeometry`, `DestinationPolicy`, `ContextMap`, `PlaceBoundaryEvidence` and
`WalkingStillnessDetector` preserve mapped boundaries/holes, campus preference,
offline stay estimates, mixed-provider dwell and sustained exercise/departure.
Later code adds fresh evidence checks, multi-component destinations and preservation
of walking prefixes. Replacing it with this branch would discard those safeguards.

All 12 original `PlaceVisitEvidenceTest` cases remain; the running loop now provides
an explicit running hint. This intentionally avoids inferring running from incidental
speed alone. Both added `ContextMapTest` campus/boundary scenarios remain. Additional
`PlaceVisitReliabilityTest`, `PlaceVisitCaptureServiceTest`, `PedestrianPlaceStayTest`,
`DisneylandRegressionTest` and `PortableJourneyIntelligenceTest` cover later policies.
No missing functionality or useful unpreserved test found.
Disposition: fully superseded by #233; close without merge.

## #7 — screen caches, plus station handoffs and diagnostic export

Head `4b563c2b2c91740d0a449a34037d69aaab311648`; six unique commits:
`5e6bbc34` (map cache), `58bb688f` (highlights), `e47eaefa` (version),
`28094c8d` (home copy), `1090907e` (version), `4b563c2b` (station/export work).
Reviewed both cache patches, the copy/version patches and the station/export changes.

`MapActivity` retains bounded revision-keyed route serialization, free-heap guards
and service/journey invalidation. `MapSnapshotCache` now compresses/checksums the
snapshot and rejects stale revisions before inflation. Optional writes occur after
useful display on the cache lane. `JourneyListActivity.readDiscoveryCache` and
`writeDiscoveryCache` retain discovery highlights across restarts; later compact
journey projections avoid full history reads. The old home Capture introduction
remains removed; recording controls now live in tracking settings. Historical version
bumps must not replace 0.25.220 (266).

The unexpectedly included station/export commit is also accounted for:
`MovementDiagnosticsExportTest` and `RailStationCatalogTest` are byte-identical;
`StationCaptureHandoffTest` retains the original tests plus measured-boundary coverage;
`StationJourneyModelTest` preserves boarding/alighting/checkpoint/drift safeguards
with later GPS-only and unknown-wait boundary improvements. `HistoryStabilisationTest`,
`MapPerformanceTest` and `NavigationPerformanceTest` cover the cache replacements.
No missing functionality or useful unpreserved test found.
Disposition: fully superseded by #233; close without merge.

## #8 — router rate limits and bounded walking fallback

Head `40652e28985e17286151255060faf32a58ce33aa`; four unique commits:
`405b2dee` (error propagation/timeouts), `610079ad` (regressions), `3b2675de`
(API workflow), `40652e28` (supported Python). Reviewed the full patch.

Current `router_get` adds shared pacing and cooldown. `osrm_match_chunk` propagates
429 as 503 with Retry-After, upstream 5xx/invalid responses as 502;
`match_chunk_resiliently` subdivides only genuine 422 no-match. The exact old change
to two transport attempts and per-socket 8/30/10/8 timeouts is not present. Its walking
purpose is superseded: recovery requests have a request-local 48-second/96-call
budget with 10-second per-call cancellation, and fallback for both legacy/recovery
clients has a 55-second outer deadline. Keep current retry policy and working
production pedestrian selection unchanged. Existing `context-walking-regression.yml`
provides Python 3.12 CI with PostgreSQL, superseding the old API-only workflow.

The two original tests were not retained verbatim; newer outcome tests mostly mock
above `request_match`. Preserve their useful lower-level protection in
`tests/test_historical_router_failures.py`: actual error parsing/pacing/split path,
one upstream call on 429 or 5xx (including non-JSON bodies), Retry-After preservation,
a second request blocked by cooldown and real cancellation of a stalled legacy
fallback. No public routing requests occur. Four new test methods pass locally.
Disposition: runtime fully superseded; additional regression coverage preserved in
the focused reconciliation PR, awaiting owner review/merge. Close the old PR with
that distinction explicit, retaining its branch.

## Validation and delivery

Local full Python discovery: 130 tests, 125 passed and five PostgreSQL-dependent cases skipped (no local PostgreSQL). The four new lower-level regression tests passed. PostgreSQL cases run in CI.
A PR-only validation workflow runs compilation, lint and the complete Android suite
using checksum-verified offline NCN/county cache pins, without test exclusions. It
uploads test/lint reports; it does not assemble/sign an APK, access deployment
secrets, publish data or deploy services. Existing Python CI runs for the added tests.
Final run URLs and outcomes are recorded in the PR description and closure comments.

No historical branch is deleted. No new PR is merged. No backend/Firebase deployment,
APK packaging/distribution, production data change or catalogue-chain modification
is authorised or performed. No issues are closed by this reconciliation. Only the
new regression coverage/workflow/documentation await integration; current runtime
behaviour is already on main.
