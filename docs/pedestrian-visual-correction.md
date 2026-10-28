# Roadprints pedestrian correction — 28 October 2026

Delivered private Android **0.25.220 (266)** from `67e74e7d9e9873552ae98b23397cd9e265fc3517`, PR225 targeting the existing Android development branch. Backend-only PR226 was squash-merged to production main as `d7f07060ebf15cee1d32fd3f948248d59f454cb2`. Render deployment `dep-dbgun1ivcj2c73dslg60` is **live**, finished 12:34:11 UTC. No Android development was merged into main; no Firebase distribution or reference rebuild occurred.

## Confirmed causes and corrections

Walking preparation dropped recorded GPS accuracy; observations inside their uncertainty were treated as precise deviations. The matcher accepted the first successful six-candidate solution and could force along-edge movement between observation-specific projections. A binary reversal penalty could make a loop preferable to a reversal. Selection6 forwards retained accuracy, evaluates the existing24 prepared alternatives, adds bounded real junction/shared projection candidates and scores accuracy-supported travel continuously. Unknown-accuracy clients retain legacy scoring with expanded candidates. This changes actual matched network geometry, evidence spans and distance together; no display smoothing or artificial cross-network joins.

The 2,001ms reported UI stack is explained by structural hashing of nested mutable JSONArray cache keys. All four geometry caches use weak/strong identity wrappers now. Existing memory budgets remain; explicit same-array republication invalidates prepared paths, and mutable editing bypasses immutable reuse. Manual removed-edge indices are reconciled only to exact physical edges; incompatible rematches keep the previous completed route, evidence and edits with an explicit amber review message. They are never silently assigned to nearby parallel edges.

Changed components: backend `api.py`, `database.py`, `pedestrian_matching.py`, focused tests/replay helper; Android `WalkingJourneyPreparation`, `MapRenderGeometry`, `RoutePreviewView`, `JourneyCorrectionUtils`, `MatchingCoordinator`, `JourneyListActivity`, three test classes, version/release notes and handover/build-request metadata. Existing journey navigation, reference pins, driving and NCN implementations were preserved.

## Actual Charing Cross regression

The private fixture retains the original333 raw capture positions,148 saved positions and117 submitted positions. For the new request only retained per-fix accuracy is added; all148 saved accuracies recovered identically from raw and aligned capture samples. The backend samples82 observations. Both live revision6 replays returned exactly the same279 coordinates as the offline snapshot regression.

| Measure | Live selection5 | Live selection6 |
|---|---:|---:|
| Returned coordinates |409|279|
| Matched distance |2,486.5m|1,986.7m|
| Immediate A–B–A reversals |22|6|
| Repeated exact-edge distance (approximate planar metres) |220.05m|33.71m|
| Original22 flagged terminal edges still bidirectionally retraced |22|3|
| Matcher work |7.868s|17.003s; confirmation16.766s|
| Client elapsed (remote replay) |35.704s|22.139s; confirmation24.761s|

CPU time increased: expanded search costs approximately9s more. The before request includes idle startup, so client timings do not prove a controlled network/phone speedup. Reference20s and overall48s deadlines remain; both live calls succeeded without upstream router calls. Backend phase details and Server-Timing are included in the JSON summaries.

The distance reduction499.8m is material and consistent with removal of repeated travel, but neither shorter distance nor proximity proves the actual route. Validated recorded distance2,409.95m remains a different measurement and was not rewritten. Rejected capture fixes remain excluded from validated distance. Road motorway/A-road result collections are empty for this walk. Pedestrian coverage is derived from the returned matched evidence, not an independently cosmetically corrected display. NCN family/evidence compatibility remains `preloaded_pedestrian_network_v4`; policy is separately identified by `pedestrian_selection_version:6`.

## Visual review and remaining ambiguity

`route-before-after-regions.png` plots actual live coordinates at identical bounds/scale within each before/after column. Network is grey, saved GPS orange, matched network blue, immediate reversals red rings. It covers Charing Cross, Strand, Fleet Street and Blackfriars; `route-before-after-overview.png` shows the full corridor. These are diagnostic coordinate plots, not phone screenshots.

Large junction detours and repeated excursions are visibly reduced and the principal corridor is more coherent. **Full visual acceptance is not claimed.** Six residual terminal reverse edges remain: Charing Cross two (~0.8m and5.3m), Strand junction two (~4.2m and2.0m), Fleet Street one (~2.1m), Blackfriars approach one (~2.4m). A terminal-edge length is not the complete outward-and-return excursion; nested/overlapping paths may span more than one edge. Small hooks and section offsets are still visible, particularly around Charing Cross and Strand. Their nearby GPS uncertainty15–45m cannot reliably distinguish intentional short visits from noise. Detailed coordinates and each of the original22 flag comparisons are retained in the private owner evidence bundle.

The final network endpoint lies about28.6m from the last saved fix, within its37.35m reported accuracy. The mapped turn towards an entrance/possible indoor continuation remains uncertain. No ground-truth path, deliberate crossing or backtrack has been invented or erased simply to produce a straight line. Network segmentation/footway availability still limits exact pavement attribution.

## Returned, persisted and displayed geometry

Before: details already selected successful `processing_result.geojson`; the219 editor selected matched edges with the previously fixed inferred GPS tail suppressed. Remaining artefacts were in the backend coordinates. Raw `route_geometry` was not overriding a successful match.

After: successful rematching replaces `processing_result`, atomically saves through JourneyStore, advances archive/evidence revisions and refreshes preview/progress caches. Details select matched geometry minus explicit saved removals. The editor loads the full current matched route so removed edges can be restored; it omits unsupported GPS-derived extensions. Raw/saved positions remain diagnostics and correction-splicing input. With no removals, both views consume the returned279 positions unchanged. With edits, intentionally different visible segments follow the saved correction contract. Incompatible edits retain the previous completed route instead of accepting a destructive replacement.

`actual-coordinate-selector-proof.json` executes the verbatim extracted production detail/editor selector methods on the actual409/279-position responses after JSON file serialization and reopen; both select exactly the returned coordinates and preserve raw/saved evidence. The unmatched fallback is a throwing stub, never reached. This portable proof is not a physical Android JourneyStore/device observation. Separate full Android tests exercise persistence/rematch/preview/editor contracts. Physical Pixel visual and foreground timing acceptance remains outstanding.

Existing recorded journeys can rematch without recapture. Install220 to forward retained accuracy and receive the identity-cache correction;219 cannot send accuracy and cannot receive the Android stall fix from a backend deployment. Historical records are not automatically rewritten/reassessed. Manual corrections remain authoritative.

## Verification

- Backend106 tests passed in CI including PostgreSQL; driving recovery regression passed. Locally106 tests passed with5 PostgreSQL integrations skipped locally only.
- Private packaging run40690390058: **810 tests /109 suites**, zero failures/errors/skips, compile, lint0errors (310 existing warnings), pinned reference validation, national NCN fixtures and signing verification passed. PR checks40690399277 also passed.
- Focused cases cover precise genuine backtracking, short deliberate spurs, loops/crossings, parallel-path uncertainty, stationary jitter, historical compatibility, manual edit preservation and large rendering caches.
- The old WeakHashMap operation is explicitly shown to invoke a forbidden structural hash; replacement renders30frames of25,000coordinates with **0 structural hash calls**,43.714ms aggregate warm frames in Robolectric. This removes the identified mechanism; it is not a replacement measurement for the phone's2,001ms stall. Cache-budget/same-length mutation/editor tests pass.
- Existing navigation fixture: first12journey bind/draw28.94ms versus full40at127.26ms; cached-road first draw25.40ms; UI path builds0; changed-one history invalidations0. Reference caching/NCN/driving assertions unchanged and pass. Synthetic runner timings are not Pixel performance guarantees.

APK36,623,557bytes; SHA256 `5eb4485327a9646bc95d4f57e3959a9acc944e8feb14b3fe9605e1aa359ae265`. Manifest220/266 and established signing certificate independently extracted from downloaded APK; CI apksigner verified cryptographic signature. Exactly one220APK was built. No tester distribution.

