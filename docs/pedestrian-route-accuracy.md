# Pedestrian route stabilisation — 28 October 2026

Base: `3c62cb86626a814ddbff714df2a09aaac138fdf6`, latest PR221 navigation/performance branch, with merged bandwidth protection PR222. Delivered baseline is 0.25.218 (264); main's Android app is older. Existing edge projections and reversal costs were already present in pedestrian matcher v4. This slice preserves those and concurrent screen/reference work.

## Confirmed findings and contracts

- Journey details already use `processing_result.geojson` when it contains matched lines, applying saved removed-edge indices. They do not overlay `route_geometry`. No blanket replacement or historical rewrite is appropriate.
- The editor independently loads unfiltered matched lines, including removed lines for restoration, plus `route_geometry` for markers/camera and inferred endpoint extensions. In this diagnostic the nearest saved endpoint is index 144 of 148, so the old editor adds a four-point, **36.36m inferred tail** that the detail screen omits. Walking now omits that GPS overlay/extension. The same matched lines/index order remains editable; saved removals remain authoritative. Correction drawing still splices preserved `route_geometry`, saves raw originals and explicitly rematches using the existing contract. Driving/legacy editor source behaviour stays unchanged.
- Matcher v4 collapsed every arrival into one state per candidate, despite its next transition cost depending on arrival direction. This violates the dynamic-programming state contract: a locally cheaper arrival can cause a more expensive unsupported reversal. A stationary snap also lost the moving arrival. The revised state retains last moving edge and observed origin, with separate competing arrivals. Costs select existing connected paths; no post-hoc deletion, smoothing, invented junction or backtrack veto is used. The evidence-compatible matcher family remains v4 (required by existing NCN readers); `pedestrian_selection_version=5` identifies the corrected algorithm without making older apps reject valid evidence.
- Source/raw samples, capture preparation and validated distance rules are unchanged. Display changes do not alter matched evidence, road progress or NCN attribution. Existing histories are not automatically rematched/reassessed.

## Geometry lifecycle

| Stage | Component / contract in this regression |
| --- | --- |
| Original capture | CaptureService keeps 333 raw coordinates/samples, original captured distance and decisions. |
| Validation | WalkingCaptureEvidence, FootTraceValidator and CaptureQualityValidator preserve rejected evidence but exclude it from derived distance; capture reports 45 rejected fixes. |
| Saved route | CaptureService/JourneyStore persist 148 prepared coordinates and aligned capture_route_samples in route_geometry, retaining raw_capture_geometry/raw_capture_samples separately. |
| Request | WalkingJourneyPreparation validates the saved leg; WalkingTraceSimplifier retains 117 points. MatchingCoordinator submits those to /match-walking with walking_recovery. |
| Network match | database.match_pedestrian_reference samples 82 fixes, projects onto real connected edges and selects a path; response geojson and matched_path_evidence share aligned travelled vertices. Raw/submitted positions are not replaced in the archive. |
| Persistence | MatchingCoordinator validates distance/partial-result rules and saves processing_result separately; original route, raw samples and explicit correction contracts remain. |
| Journey details | JourneyListActivity.journeyPreview uses matchedRouteSegmentsForDisplay with saved removed-edge indices; recorded GPS only serves the unmatched fallback. |
| Route editor | JourneyMapEditorActivity uses unfiltered matched edges to permit removal/restoration. Walking now omits inferred GPS tails/markers; correction splicing explicitly reads original route_geometry. |
| Road progress | ProgressActivity keeps separate driving/foot contributions. MotorwayProgressCalculator and ARoadProgressCalculator retain driving/bus eligibility; walking responses still have empty motorway/a_road collections. |
| NCN | NcnMatcher checks eligible mode, matcher family, hashes, travelled spans and removed edges before NcnProgressStore attribution. Matcher family v4 is retained for compatibility; the unchanged evidence schema is not loosened. |

## Private regression and uncertainty

The supplied diagnostic stays outside the public repository. A read-only snapshot of the full production candidate envelope contains **11,734 public source ways**. Replaying the old selection against that snapshot reproduces the original **416 coordinates exactly**, including the matched evidence geometry. No personal trace was sent to the database; only its public reference envelope was read.

Repeat with the private fixture bundle:

`python3 scripts/replay_pedestrian_diagnostic.py /private/roadprints-journey-20261026-223410.json --baseline /private/pedestrian_matching_before.py --baseline-database /private/database_before_lazy.py --network /private/network_snapshot.json --repeat 5`

The script reproduces Android simplification from 148 validated coordinates to 117 submitted positions and calls the production graph/evidence builder at a mocked database boundary. A subset-only mode can use the 112 exported selected ways but is labelled incomplete; it must not be used for full-network acceptance.

Original: 333 raw coordinates; 148 saved; 416 matched; 2,409.95m validated; 2,422.7m matched. Raw sample distance 11,439m and original captured distance 7,557m describe different stages. Neither is substituted for validated distance.

Full replay after state correction: **409 coordinates, 2,486.5m, 22 immediate exact retraces versus 23 originally**. The additional 63.8m (+2.63%) follows the globally selected connected alternatives under the existing path and turn scoring, and remains within the established validated-distance guard. It is not new raw distance or automatic historical credit. Finite geometry and aligned source evidence are checked and the input is unchanged.

The test does not establish which pavement or crossing was actually taken. Remaining retraces may reflect actual movement or noisy fixes; repeated source references alone cannot distinguish those cases. **An overall artefact-free route is not claimed.** Remaining uncertain sections require visual/device review rather than deletion to make the line look cleaner. The synthetic regressions specifically prove the corrected software failure mechanism while preserving a GPS-supported return.

## Timing

Original attempt: 51,307.5ms (request-start timestamp to response-finished timestamp); matcher_work 14,493ms; unallocated 36,814.5ms. Retained production logs now establish a server cold startup during that exact interval: command start 09:13:26.483 UTC, server process start 09:13:38.783, application ready 09:13:39.174, first successful health check 09:13:47.569. The matching work ends at 09:14:02.274 (117 points, 442 searches, 39,223 settled nodes), and request duration is 14,597ms. Inferred handler admission is about 09:13:47.776, **35.4 seconds after Android's attempt timestamp**. Response finish on Android is 09:14:03.680, roughly 1.3 seconds after the server's request completion log. These matching counters and timings strongly support cold startup/readiness as the dominant gap; no request ID spans both logs, and exact client-preparation/DNS/TLS shares remain unknown. No evidence of 51 seconds of matcher CPU or repeated upstream work (router_calls=0). Request body 4,760 bytes alone does not explain it. No paid-plan or keepalive change is made.

New Android diagnostics separate preparation, each HTTP attempt/retry, JSON encoding, connection, upload, header wait, response read, parsing and processing-to-save. Final persistence duration is logged under RoadprintsTiming without coordinates. Fixed-length streaming reuses one encoded request body. Backend records lock wait, reference execution, database fetch, graph/candidates, trajectory and geometry/evidence. ASGI Server-Timing covers request handling through serialized response headers; logs cover request receive bytes/time and response send bytes/backpressure. The walking JSON response separately logs final JSON serialization duration/bytes without its contents. ASGI send is not a measurement of end-to-end transmission; Android header/read timings supply that other side. Delay before ASGI admission remains outside server timings.

Profiling found eager source hashes for all 11,734 candidates, although only 112 ways were used. Hashes are now computed only for selected ways; a regression verifies unused ways are not hashed while source identity stays aligned. Five-run full-snapshot median on this host: **1,478.6ms →1,154.8ms (21.9% less)**, with identical search work (442 searches /39,223 settled nodes). This excludes live database/network/cold-start costs and is not a measured 51-second phone-request improvement. Backend changes need a separate deployment; an APK alone cannot install selection version 5. Retrying an existing journey remains explicit.

## Verification status

Local backend: 100 tests, zero failures/errors, five PostgreSQL integration tests skipped because no local test database is configured. New selection tests reproduce competing arrivals, stationary direction retention and GPS-supported return. Existing edge/crossing/connectivity, real out-and-back, graph search and evidence tests pass. ASGI tests preserve response bytes/headers and exclude private payload from logs.

Android tests add walking detail/editor selection, actual editor opening, persisted reopen/removal/evidence preservation and legacy/driving compatibility. Android compile/lint, pinned references, national NCN fixtures and all **804 tests** passed at `e8c4086d` in [40671037615](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40671037615). The earlier missing release-note failure was fixed by adding the 219 entry; no assertion was removed.

Final PostgreSQL CI [40671803409](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40671803409) and driving-recovery CI [40671803352](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40671803352) passed at `f04f6139`. All 100 backend tests include matcher-family compatibility, genuine loops, parallel paths and a valid connected crossing. Android runtime/test sources are unchanged from the green e8c4086d check. One private signed APK is now requested only after these checks; packaging repeats the Android tests/lint before assembly. No Firebase distribution or backend deployment. The unchanged matcher family preserves the established NCN evidence reader; no NCN implementation is modified.
