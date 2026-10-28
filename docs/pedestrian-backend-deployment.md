# Pedestrian backend deployment — 28 October 2026

## Deployment completed

Backend-only [PR224](https://github.com/adamdbird-gfc/uk-road-tracker/pull/224) was squash-merged into main as **610a8011a26e8c13077660b02bc98408156de435**. It contains ten pedestrian backend, test, replay and documentation files. No Android files, APK requests, migrations, credentials or NCN reference changes were merged. The wider PR223 Android branch remains separate.

Render deployment **dep-dbgtg329lscs7393fiv0** became **live at 11:11:14 UTC**. It was automatically triggered by that exact main commit. The previous live runtime was 7dd2c58c628ec819ace2b645d18aa296ddb238b4. Post-deployment /health returned HTTP200 with the database ready.

The live /match-walking endpoint returned HTTP200 and **pedestrian_selection_version=5** on two requests. The matcher family deliberately remains **preloaded_pedestrian_network_v4** so existing installed apps and NCN evidence readers continue to accept the unchanged evidence schema. It also returned the new matcher_profile and request_profile fields. The confirmation response included Server-Timing: request;dur=8190.84, receive;dur=0.29. Runtime logs confirm coordinate-free walking_http and walking_json instrumentation.

## Actual live regression comparison

The same original 117-point Charing Cross payload was posted directly to the production endpoint immediately before deployment and twice afterward. The before geometry equals the supplied 26 October export exactly. Both after responses have identical geometry and evidence hash, and equal the tested PR223 selector geometry exactly. Raw diagnostic coordinates and response bodies remain in a private evidence bundle, not this public repository.

| Measurement | Live before | Live after |
| --- | ---: | ---: |
| Submitted / reported matched tracepoints | 117 /117 | 117 /117 |
| Matched coordinates | 416 | 409 |
| Matched distance | 2,422.7 m | 2,486.5 m |
| Immediate A-B-A reversals | 23 | 22 |
| Exact edges traversed in both directions | 51 | 38 |
| Repeated undirected exact-edge traversals beyond the first | 67 | 54 |
| Distance of those repeated exact-edge traversals | 225.37 m | 219.80 m |
| Source ways occurring in multiple evidence spans | 24 | 16 |
| Total source references | 112 | 124 |
| Client elapsed time for this replay | 16.904 s | 12.690 s; confirmation 12.989 s |
| Reported matcher work | 11.285 s | 8.090 s; confirmation 8.179 s |

Exact-edge metrics compare identical coordinate pairs. Partially overlapping edges with different split positions are not counted as identical. A repeated traversal is not automatically a software error: it may be genuine retracing. Repeated source references may also be necessary transitions. These counts are measurable comparisons, not a ground-truth route judgement.

The route is **63.8 m longer (+2.63%)** after selection changes. It remains within the installed client's existing validated-distance guard, but this is not proof that the longer alternatives are the actual path taken. Visual improvement is modest: most of the short hooks/reversals remain, including 22 immediate reversals and roughly 220 m of repeated exact edges. The deployment is complete; the original accuracy problem is not claimed fully resolved. No smoothing or deletion of uncertain sections was performed.

These timings are individual live requests from the development environment, not phone measurements or a controlled benchmark. They do not guarantee that Render cold startup is eliminated. Response sizes were 86,240 bytes before and 94,696 bytes after. The confirmation server logs measured JSON serialization at 1.90 ms and response send/backpressure at 2.51 ms.

## Existing APK compatibility

**Android 0.25.219 (265) can explicitly rematch existing saved journeys without a new release.** MatchingCoordinator.rematch selects already-complete records for a fresh /match-walking request, prepares retained evidence, validates the response and replaces processing_result. Journey details and the editor read the new matched GeoJSON; raw capture and route_geometry remain separate. The matcher family, geometry and evidence formats are compatible with the installed app. Walking responses still return empty motorway and A-road collections, and NCN mode/hash/span rules are unchanged.

Previously passing Android regressions include completedOldMatchIsRepreparedAndDriftRemovedUsingCurrentLogic, failedUpgradeRematchRetainsPreviousMapDistanceAndRawRecording, persisted walking preview/removals and actual editor opening. The installed APK's 804 tests /107 suites and lint had zero failures/errors/skips. No new Android build was made for deployment.

Use explicit **Rematch** after this deployment. Merely reopening a journey retains its existing saved result. Debug Journey should show processing_result.pedestrian_selection_version=5 after a successful new match. A comparable walk can still select the same geometry; the version marker distinguishes that from calling the old backend. Physical operation/rendering on the user's phone is not remotely verified.

## Checks and provenance

The isolated backend branch passed all **100 backend/PostgreSQL tests**, zero failures/errors/skips, in [40677705213](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40677705213). Driving recovery also passed in 40677705221. Tested isolated head: f89d95d12bddf006f896e042ea33ebe37a97cf1b; deployed squash commit: 610a8011a26e8c13077660b02bc98408156de435.

The private comparison bundle contains both actual live responses, confirmation response, summaries, exact reversal indices, replay payload, geometry comparison image and scripts. These are transient read-only matching requests to Roadprints; no saved user journey was modified on the server and no historical reassessment was triggered.
