# Pedestrian visual correction and rendering cache — 28 October 2026

Baseline: installed private Android0.25.219/code265, PR223; deployed backend selection5, main610a8011/PR224. Previous work failed visual acceptance. This slice preserves navigation, offline Firebase reference pins and NCN UI; it does not rebuild reference data or merge Android into production main.

## Confirmed causes

- Details already select processing_result.geojson (after saved section removals), not route_geometry. The editor edits matched edges and permits restoration of removed edges; source/raw geometry is kept for diagnostics and correction splicing. This is not a raw-GPS-overlaid-on-matched-route defect.
- Native capture retains per-fix accuracy, but WalkingJourneyPreparation omitted it from requests and Point accepted positions only. Several 1–17m reversals occur near fixes whose reported uncertainty is15–45m. Perpendicular emission and shape costs consequently treated noise as precise turns.
- The matcher accepted the first successful six-candidate solution. Valid paths crowded out by closer nearby ways were never considered unless matching failed. Edge projections were specific to each observation, so along-edge pauses/jitter could force movement even when one shared network position was compatible with both fixes.
- A large binary reversal penalty could prefer going around a short loop to paying the turn cost. For observations with accuracy, the revised model uses an uncertainty-aware distance likelihood and travel prior rather than that binary turn cost. Precise deliberate turns, spurs, crossings and out-and-back movements are covered by regressions.
- MapRenderGeometry used JSONArray directly in weak maps and retained linked maps. Both invoke structural hashCode/equals on nested mutable arrays, matching the reported foreground stack. Changing a mutable array's value could also invalidate its hash key. All four caches now use identity keys: weak metadata with a reference queue; bounded strong path retention. Existing8192-entry,200k prepared-vertex and100k per-view path budgets remain. Same-length republished arrays invalidate their previous prepared path; editing bypasses immutable reuse.
- Removed section indices belonged to the old match. A changed match could apply them to different edges. Exact physical-edge reconciliation now transfers compatible edits; otherwise the old route and edits are retained with an explicit failure message instead of silently reassigned.

## Implementation and evidence

Optional accuracy_m is preserved through preparation, simplification, request validation and server sampling. No GPS fix or raw evidence is deleted by this change. The matcher considers the existing24 prepared edge alternatives, plus a bounded set of nearby shared projections/junctions supported by reported accuracy. It never joins crossing or parallel paths artificially. Candidate radii are bounded by available accuracy; unknown-accuracy clients keep their legacy scoring and receive the expanded candidate set.

Returned network geometry is used unchanged for evidence spans and matched distance. There is no display-only smoothing. Matcher family remains preloaded_pedestrian_network_v4 for NCN compatibility; pedestrian_selection_version=6 identifies this policy.

Full11,734-way offline replay with original117 submitted positions and their retained accuracies currently returns279 coordinates,1986.7m and six immediate reversals versus deployed selection5's409 coordinates,2486.5m and22. Remaining terminal retrace edges are about0.8–5.3m. This is not a ground-truth route claim: position uncertainty cannot establish whether every tiny return was intentional. Live deployment, actual before/after timing and equivalent-zoom visuals will be recorded after verification.

The source/raw recording remains333 coordinates; saved route148; request117. Backend sampling retains82 observations. Successful results replace processing_result separately; JourneyStore atomically saves and increments archive/evidence revisions, invalidating history/map/coverage caches. Journey detail and editor consume the new match. User-drawn route_geometry corrections are retained; safely transferable edge removals are remapped by exact geometry, and incompatible edits preserve the previous map. No automatic history rewrite or reassessment is introduced.

## Verification and delivery status

Local backend106 tests pass with five PostgreSQL integrations skipped locally; CI must exercise Postgres. New Android regressions cover zero structural hashes across large warm/cold/cull cache use, same-count republication, path budgets, mutable editing and correction reconciliation. The large-array test deliberately demonstrates that the old WeakHashMap key operation calls the forbidden structural hash; the new cache must complete30 frames without one call. Device-level ANR elimination is not claimed from JVM checks.

Android0.25.220/code266 is prepared. No APK has been requested or built at this checkpoint. One private signed APK will be requested after backend and Android checks pass. Backend changes will be isolated onto main and verified live; no Firebase tester distribution.

## Live revision6 and pre-packaging verification

Backend-only PR226 merged to main as d7f07060ebf15cee1d32fd3f948248d59f454cb2. Render dep-dbgun1ivcj2c73dslg60 became live at12:34:11 UTC. The same117-position request, enriched only with the retained original accuracies, returned selection5 before deployment and selection6 afterward. All148 saved positions recover the same accuracies from raw_capture_samples as the aligned capture_route_samples; no metadata was invented.

Actual live response:279 coordinates,1986.7m,six immediate retraces, exactly equal to the offline correction. Backend matcher work17.003s versus7.868s before; client request22.139s versus35.704s before (the before request includes idle startup, so these are not controlled phone speed measurements). Wider candidate search costs more CPU; the existing20s reference and48s total work budgets remain unchanged. No upstream router calls were made. Source evidence and matched geometry align; motorway/A-road collections remain empty.

Android source05bb071b passed compile/lint, pinned-reference and national NCN checks plus the full unit suite. The identity-cache regression completed30 frames of25,000 coordinates with zero structural JSON hash calls. This demonstrates removal of the specific nested-hash mechanism, not a measured replacement for the phone's2001ms foreground-stall report. Final status messaging additionally identifies held manual edits without presenting an unapplied match as a new success. Final checks and one private APK remain pending.


Final pre-packaging checks passed at85628e6a in Android run40689585723: compile/lint, pinned references, NCN fixtures and full unit suite, including the identity-hash regression and manual-edit preservation. Backend/PostgreSQL106 tests and driving recovery passed. One private0.25.220/code266 APK is now requested; no intermediate APK or Firebase distribution has occurred. Package verification will independently repeat unit tests/lint before assembly and check the established signing identity.
