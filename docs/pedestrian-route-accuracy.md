# Pedestrian route stabilisation — 28 October 2026

Base: `3c62cb86626a814ddbff714df2a09aaac138fdf6`, latest PR221 navigation/performance branch, with merged bandwidth protection PR222. Delivered baseline is 0.25.218 (264); main's Android app is older. Existing edge projections and reversal costs were already present in pedestrian matcher v4. This slice preserves those and concurrent screen/reference work.

## Confirmed findings and contracts

- Journey details already use `processing_result.geojson` when it contains matched lines, applying saved removed-edge indices. They do not overlay `route_geometry`. No blanket replacement or historical rewrite is appropriate.
- The editor independently loads unfiltered matched lines, including removed lines for restoration, plus `route_geometry` for markers/camera and inferred endpoint extensions. Walking now omits that GPS overlay/extension. The same matched lines/index order remains editable; saved removals remain authoritative. Correction drawing still splices preserved `route_geometry`, saves raw originals and explicitly rematches using the existing contract. Driving/legacy editor source behaviour stays unchanged.
- Matcher v4 collapsed every arrival into one state per candidate, despite its next transition cost depending on arrival direction. This violates the dynamic-programming state contract: a locally cheaper arrival can cause a more expensive unsupported reversal. A stationary snap also lost the moving arrival. The revised state retains last moving edge and observed origin, with separate competing arrivals. Costs select existing connected paths; no post-hoc deletion, smoothing, invented junction or backtrack veto is used. Matcher identity becomes v5.
- Source/raw samples, capture preparation and validated distance rules are unchanged. Display changes do not alter matched evidence, road progress or NCN attribution. Existing histories are not automatically rematched/reassessed.

## Private regression and uncertainty

The supplied diagnostic stays outside the public repository. Run:

`python3 scripts/replay_pedestrian_diagnostic.py /private/roadprints-journey-20261026-223410.json [/private/baseline_pedestrian_matching.py]`

The script reproduces Android's simplification from 148 validated coordinates to 117 submitted positions. It uses the 112 exported matched source ways through the production graph/evidence builder with a mocked database boundary. **These are only previously used ways, not the complete candidate network.** It cannot reproduce the original full-network candidate decision or establish the actual pavement/crossings taken.

Original: 333 raw coordinates; 148 saved; 416 matched; 2,409.95m validated; 2,422.7m matched. Raw sample distance 11,439m and original captured distance 7,557m describe different stages. Neither is substituted for validated distance.

Subset replay on this host: old selection 2,313.0m /408 coordinates /15 immediate exact retraces; revised selection 2,306.1m /406 coordinates /16 immediate exact retraces. Example runtime 77ms →92ms. This demonstrates a changed connected/evidence-aligned solution, **not a proven overall reduction in artefacts or latency**. Counting repeated segments cannot distinguish deliberate retracing from noisy fixes. Remaining short reversals and uncertain crossings require full-network replay and visual review, not deletion to make the line look cleaner.

## Timing

Original attempt: 51,307.5ms (request-start timestamp to response-finished timestamp); matcher_work 14,493ms; unallocated 36,814.5ms. No timing evidence identifies that difference as transport, cold wakeup, retry, preparation or storage. Request body 4,760 bytes alone does not explain it.

New Android diagnostics separate preparation, each HTTP attempt/retry, JSON encoding, connection, upload, header wait, response read, parsing and processing-to-save. Final persistence duration is logged under RoadprintsTiming without coordinates. Fixed-length streaming reuses one encoded request body. Backend records lock wait, reference execution, database fetch, graph/candidates, trajectory and geometry/evidence. ASGI Server-Timing covers request handling through serialized response headers; logs cover request receive bytes/time and response send bytes/backpressure. ASGI send is not a measurement of end-to-end transmission; Android header/read timings supply that other side. Delay before ASGI admission remains outside server timings.

No speedup is claimed from instrumentation or the subset replay. Backend changes need a separate deployment; an APK alone cannot install matcher v5. Retrying an existing journey remains explicit.

## Verification status

Local backend: 96 tests, zero failures/errors, five PostgreSQL integration tests skipped because no local test database is configured. New selection tests reproduce competing arrivals, stationary direction retention and GPS-supported return. Existing edge/crossing/connectivity, real out-and-back, graph search and evidence tests pass. ASGI tests preserve response bytes/headers and exclude private payload from logs.

Android tests add walking detail/editor selection and persisted reopen/removal/evidence preservation plus legacy/driving compatibility. Full Android and PostgreSQL CI results and APK provenance will be recorded after completion. No APK has been built at this checkpoint. No Firebase distribution or backend deployment.
