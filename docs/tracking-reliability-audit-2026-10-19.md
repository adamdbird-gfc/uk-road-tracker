# Tracking reliability audit — 19 October 2026

Reviewed the default-branch development handover, issue #194, all 29 open PR summaries, and file-level changes for every Android/backend reliability dependency and the older #1/#6–#8 candidates. The implementation base is #182 at `13084738b802a1e5c5815daaaced43e8d370232e`; it includes #174 and #170, but not the separate place-visit branch #163. No branch has been merged or deployed by this session. The new defect is tracked in #195.

## Existing fixes and release disposition

| PR | Reviewed head / base | Disposition | Existing behaviour and remaining verification |
| --- | --- | --- | --- |
| #170 | `707fa749` / `fix/onboarding-start-tracking` | Include | Pedestrian trajectory corroboration, dwell and drift cleanup; retained departure fixes. Already inherited by #174/#182. |
| #174 | `d475425f` / #170 | Include | Paint outlines before centrelines; preserve prepared driving observations across 500-point requests; reject isolated driving arrival spikes; distinguish through-road queues from local parking. Already inherited by #182. Genuine missing geometry remains a matcher/evidence problem. |
| #182 | `13084738` / #174 | Include | Repair native pedestrian end times from validated stationary tails after successful rematch/save. Base of this new slice. |
| #173 | `f1e752cd` / main | Include | Ten-point captured-road router windows, shared endpoints and deduplicated distance. Selected M40 excerpt verified, not the full journey or saved-history repair. Backend integration still required. |
| #163 | `26bd7d6f` / `fix/onboarding-start-tracking` | Include after explicit conflict review | Supported place stays, sustained departure and exercise within venues. Not contained in #170/#182. Overlaps CaptureService, CaptureMovementEvidence and WalkingStillnessDetector; reconcile behaviour rather than select one complete file. |
| #164 | `f36c456b` / main | Include with #163 | Complete site boundaries, holes and cached context revision. Backend deployment remains separate. |
| #169 | `34a96fbf` / `fix/onboarding-start-tracking` | Include in coordinated release, defer from this code slice | Service footprints/visits. Keep visit dwell distinct from trip segmentation; saved Warwick recovery and drive-through/device acceptance remain. |
| #167 | `57fdb2e7` / `fix/onboarding-start-tracking` | Include in coordinated release, defer from this code slice | Late-save recap/list visibility; not a cure for capture loss. Overlaps #166 JourneyListActivity/MapActivity. |
| #166 / #171 / #172 | stacked map → Progress → eight-route NCN | Defer from this reliability PR | Preserve their shared UI; #172 also changes RoutePreviewView, so retain #174 stroke ordering when integrating. No full NCN activation claim. |
| #168 | `2cde46de` / #164 | Defer from this reliability PR; include with NCN | Saved source-way/bicycle graph evidence. Overlaps #173 api.py and database backend work; retain evidence while reconciling road assembly. |
| #165 / #175–#187 | stacked NCN research and data | Defer from this reliability PR | Data/compiler trials; wider Android adapter and production integration remain outstanding. |
| #1 / #6 / #7 | old divergent Android branches | Defer as stale merge candidates | Current code already has matching coordination, candidate gates, diagnostics and caches. Their old version bumps and complete CaptureService files must not overwrite current work. Formal closure/supersession requires a separate history audit. |
| #8 | old upstream-router failure branch | Defer as stale merge candidate | Inspect present upstream failure handling before any cherry-pick. Do not roll back api.py to this older branch. |

Head-specific GitHub checks for #163/#164/#168/#169/#170/#173/#174/#182 were successful at audit time (deployment jobs skipped on backend PRs). These are separate branch checks, not integrated acceptance.

## New smallest remaining slice: measured activity-mode boundaries

An early activity callback could be confirmed by later GPS movement, yet CaptureService split at the callback timestamp. That reassigned intervening walking to the vehicle leg. The old boolean confirmer also pooled compatible speeds around stops, and one early vehicle-speed fix vetoed later genuine walking for the entire pending window.

`CaptureMovementEvidence.modeChangeStart` now returns the original index at the start of the latest fresh, continuous, compatible-speed run. It retains the existing three-fix, 20-second and distance thresholds. Stops/incompatible measured speeds reset that run; sampling gaps and implausible jumps cannot pool support. Duplicate/out-of-order callbacks do not add votes, and future/stale fixes cannot confirm a transition. Walking uses the existing pedestrian plausibility test.

CaptureService uses that index in the existing split/handoff, with one shared measured endpoint. Original samples, order, coordinates and timestamps are retained. IN_VEHICLE still becomes Unknown unless the existing mode policy already knows the transport. Station-owned transitions, provisional-mode promotion and GPS-dwell guards remain on their existing paths. Diagnostics record the measured boundary.

This is a synthetic/code-confirmed segmentation defect, not a claim that it caused every supplied journey failure. A measured boundary is still an estimate; it does not reconstruct a missed departure before the first supporting observation.

## Validation

- 34 local JVM tests passed: CaptureModeBoundaryTest (11 new), CaptureMovementEvidenceTest, PedestrianPlaceStayTest and FootTraceValidatorTest, compiled from production Java using Eclipse ECJ 3.37 / Java 17 with JUnit 4.13.2.
- New Android service-handoff test verifies every original edge is retained across the measured split, with one shared endpoint and copied continuation samples. Full Android unit tests and lint passed for PR #196 code checkpoint `726339649c824d1a69812cbf2f17ca54ffe282a7` in [Actions run 39249993064](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39249993064). Local Android SDK/Gradle are unavailable; the full runtime checks were performed in CI.
- Fixtures use invented relative geometry, with no private exports.
- No version change, release request, APK packaging, Firebase publication or deployment.

## Outstanding and next slice

1. Reconcile #163 place-visit behaviour onto the #170 → #174 → #182 → measured-mode-boundary stack, explicitly resolving CaptureService/CaptureMovementEvidence/WalkingStillnessDetector overlaps. Add service-level tests for home drift overnight followed by genuine morning departure, school/campus incidental movement versus running laps, and stationary arrival followed by walking to a car. Issue #188 remains open; do not claim this PR resolves overnight capture.
2. Integrate backend #164/#173 (and #168 provenance when NCN is integrated). Verify full driving routes and all chunk joins, not only the previously replayed M40 excerpt. Rematching old history requires the coordinated release.
3. Preserve #169 services evidence, #167 late saves and #166/#171/#172 map/Progress changes during integration; validate edits, deletions and source evidence.
4. Finish integrated tests and field acceptance before one separately approved beta release. Do not trigger an APK build or Firebase deployment from this work.
