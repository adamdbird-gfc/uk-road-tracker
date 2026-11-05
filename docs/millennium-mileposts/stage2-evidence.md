# Stage 2 — discovery evidence and proximity

5 November 2026. Scope: implement and validate evidence only; **not integrated or activated**.
Starting main: `870edf535bceb5399ab694aafd13621c882c0328`; PR #280 is merged at that commit.
Authority: [specification](../millennium-mileposts-specification.md), [Stage 1 contract](../../reference-data/millennium-mileposts/README.md).
Final head, CI receipts and ending main are recorded in the Stage 2 PR, avoiding a self-referential commit receipt.

## Components and trust boundaries

`MilepostProximity` is a package-private, Android-independent evaluator. It reads a single eligible physical marker, immutable journey metadata and an iterable of already supported finite segments. It emits evidence only. It has no network, storage, ledger, achievement, NCN, map or presentation calls. `MilepostJourneyEvidence` adapts the current native archive conservatively. Neither has a runtime caller; no existing calculation or archive observer changes.

A trusted archive reader supplies one original record at a time. The adapter does not accept user-created JSON as authentication: field names, source labels, a hash or a `complete` flag cannot establish that a record came from the app. Local app-private archive provenance is a caller trust boundary; a future import ingress must not permit fabricated native records. Hashes identify evidence changes, not its author. The generic `Evidence` interface is a validator capability, not a wire schema: there is deliberately no `validated=true` deserializer. An independent historical validator must prove the local section and mode before implementing it.

| Existing source | Exact contract used or disposition |
| --- | --- |
| `CaptureService` persisted native JSON | `source.type=android_activity_capture`, `capture_decisions.mode_at_save/mode_source`, `mode`, `started_at/ended_at`, aligned `capture_route_samples` `[lon,lat,accuracy,time_ms,speed]`, `route_geometry` LineString; retained activity transitions for cycling |
| `MatchingCoordinator` result | Require `processing_status=complete`, status `ok`, actual LineString, input/matched point accounting equal and >=2; reject partial flags and failed sections. Cycling additionally requires `matching_mode=cycling` and deduplicated distance flag. No geometry is taken from road footprints. MultiLineString-only matches currently remain unresolved rather than flattening parts. |
| `CaptureQualityValidator.inspect/reliable` | Recomputed accuracy, chronology, coordinate, plausibility and moving-gap checks; 50m foot / 100m non-foot fix thresholds unchanged. Any >120s observation silence is unsupported, including low-displacement silence. A saved unresolved recording-quality result also blocks. |
| `WalkingCaptureEvidence.prepare` | Existing stillness/trace cleanup is rerun. Unresolved or removed fixes/stationary samples are held; this adapter never reconnects across a removed observation. Existing preparation diagnostics indicating GPS removal/station/vehicle/stationary trimming are held until exact accepted-section mapping exists. |
| `CaptureMovementEvidence.hasJourneyMovement` | Each edge needs a local three-observation movement witness. Foot retains existing pedestrian motion evidence; local reported vehicle-speed observations (>=6m/s) are excluded conservatively. Cycling retains the existing mode-transition speed envelope .8–12m/s, requires a covering cycling activity entry, and verifies observed displacement <=12m/s plus the existing <=50m plausibility allowance. These are evidence-quality checks, never radius deductions. |
| `JourneyStore.updateMode` | Compare effective mode to mode at capture save; hold any retained transport-change entry from/to another mode. A manual label cannot convert driving into cycle evidence. Missing original classification is unresolved. |
| `JourneyCorrectionUtils` / `TravelAchievementRoutes.matched` | Matched-edge and road corrections refer to different geometry indices from the original observations. Hold edited journeys rather than ignoring edits or guessing their mapping. `TravelAchievementRoutes.eligible` accepts vehicles; not a discovery gate. |
| `CaptureQualityValidator.displaySections` | Display-only; never used to award/validate proximity. |
| NCN matcher/ledger | Entirely unchanged. No source-identity relaxation, NCN membership requirement or coverage credit. |

Accepted native geometry is **consecutive supported original observation edges**, not arbitrary full matcher geometry. Requiring a complete accepted match is an additional validation prerequisite; its drawn line is not proof of the space between unsupported observations. No endpoint extension, road footprint, bounding-box proximity, sample-cell credit or cross-part connector is used. Sparse observations can qualify only when their local movement/continuity evidence is sufficient. Single-point fixes and stationary drift cannot stand in for a moving section. Conservative holds can miss valid journeys; they never manufacture a pass. Retry after a documented evidence upgrade, preserving the original archive.

## Geometry and outcomes

Distances use GeographicLib-Java **2.1**, WGS84 ellipsoidal inverse/direct geodesics ([upstream](https://github.com/geographiclib/geographiclib-java), MIT/X11). Maven dependency is pinned in the existing Android Gradle file. This avoids latitude/longitude planar metres and a spherical radius bias at the 100m boundary.

For a supported segment <=10km, minimize marker-to-point ellipsoidal distance over its finite geodesic by 58 deterministic ternary iterations; include both endpoints explicitly. Zero-length edges and segments longer than the supported 10km numerical envelope return insufficient geometry. Upstream validators must subdivide a longer genuinely supported curved path; subdivision must never invent support across a gap. A triangle-inequality lower bound skips minimization only when an edge cannot improve the measured best distance. It is not an eligibility approximation.

Rule: measured distance <=100.0m, with **0.00001m (10 micrometres)** arithmetic tolerance. This tolerance accommodates numerical minimization only; no GPS accuracy is subtracted from or added to the radius. There is no dwell, stop, photograph, visibility, vegetation, physical-touch, manual-check-in or designated-NCN-route input. A valid parallel path within the same radius qualifies. Vegetation is tested by the identical geometry input, without inventing visibility detection.

| Outcome | Meaning |
| --- | --- |
| `QUALIFIED` | Supported finite geometry is within the fixed radius |
| `OUTSIDE_RADIUS` | Supported finite geometry exists, measured approach is outside |
| `INELIGIBLE_MODE` | Vehicle/unknown mode; only walking/running/cycling accepted (archive aliases normalized) |
| `INSUFFICIENT_GEOMETRY` | Missing validation, timing, movement or safe correction mapping, unsupported schema/size/segment envelope |
| `UNSUPPORTED_GAP` | Observation silence cannot support interpolation |
| `UNRELIABLE_LOCATION` | Invalid/misaligned fixes, implausible movement, unresolved quality or removed/drift observations |
| `UNVALIDATED_MATCH` | Missing, rejected, failed, unfinished or partially supported match |
| `UNTRUSTED_SOURCE` | Unsupported origin or mode provenance |
| `MARKER_INELIGIBLE` | Research-only record; no eligible physical marker |
| `INVALID_MARKER` | Missing/invalid reference, physical ID or catalogue revision |
| `CANCELLED` | Interrupted assessment; no partial qualifying result |

Every qualifying result carries physical marker ID, journey ID/start date, journey date precision (not a fabricated pass timestamp), effective mode, closest coordinate/distance, section and original sample range, evidence source, validation status/revision, SHA-256 evidence token, catalogue revision, discovery-policy revision and method. Token hashing traverses JSON incrementally with sorted object keys and bounded recursion/scalars; it does not serialize the entire matcher tree. Reordered JSON keys retain the same token. Recognition time and earliest-proof ordering belong to Stage 3.

The generic validator must resolve a marker's dated location/presence and eligible physical identity for the particular journey **before** assessment. This stage does not invent historical validity intervals. The Stage 1 adapter always creates an ineligible research marker, even if `eligible` in an input row is tampered with. All 889 real rows are tested; the inventory bytes/schema remain unchanged. Qualifying fixtures use explicit `test-only:` physical IDs and catalogue revisions. There is no award/persistence API.

## Historical imports: observed limitation, not a product-policy change

Pinned implementation: `TimelineImportActivity.journeyFromSegment`, `modeFor`, `roadMode` and `TimelineRoadPreparation.prepare` at starting main. Semantic and legacy imports retain simplified `route_geometry`, file fingerprint, activity interval, mode and `capture_quality.source_route_points`. `timeline_match_evidence` version 1 (aligned `point_times_ms`, source transport and appended-end/parking information) is emitted only for `roadMode`, currently driving/bus. Walking/running/cycling imports lose this local timing/quality context; RUNNING also normalizes to walking. Place visits, activity endpoints and generic simplified lines are insufficient.

`MilepostTimelineImportTest` invokes the **real importer**, with synthetic original semantic WALKING/RUNNING/CYCLING equivalents, and pins absence of retained local timing/capture evidence. Marking those results complete does not make them valid. The native adapter returns insufficient evidence for Timeline, including a pasted driving-style version-1 timing object; no old import is rewritten or rematched. `TimelineImportEvidenceTest` remains a regression control for driving/bus preservation.

Sufficient independently validated historical sections **can qualify** through `Evidence` without live GPS timestamps; synthetic positive equivalents exercise this boundary. This does **not** claim an existing production historical validator or private supported archive was recovered. No private user archive is available in this session. A future evidence adapter must recover original Timeline local observations, effective transport, precision and section validation, resolve gaps/corrections and pin actual supported schema fixtures. Where this cannot be established, retain unresolved status. This is an engineering dependency, not a change to retroactive eligibility policy or a request to revisit it.

## Scalability, recovery and future indexed reader

The evaluator retains one best edge/result, consumes a single-use iterable once and checks cancellation at every edge. It never loads the catalogue or another journey. The native adapter snapshots only the first five scalar values of <=10,000 aligned samples; over-limit journeys remain unresolved. Foot preparation has bounded per-journey lists. The adapter receives an already parsed JSON record; it is **not** itself an archive streaming parser, and cannot retroactively cap its caller's allocation of oversized input. A projected size-limited reader is therefore an explicit production dependency.

`JourneyStore.forEachAchievementEvidence` currently projects general achievement fields and omits native sample/mode-history details required here. Do not add a second archive-wide geometry tree to `AchievementStore`. Follow `NcnProgressStore`'s worker, per-file token, cancellation/activation epoch and atomic checkpoint pattern in a separate milepost worker/store. The existing achievement memory correction remains untouched.

Before Android activation implement:

1. Stream verified catalogue rows off the UI thread into a SQLite point R-tree with unique physical IDs and validated aliases. The Stage 1 JSONL lookup is O(N) and is unsuitable per GPS point. Do not implement a full-catalogue scan per sample.
2. Stream bounded validated journey edges once; query the marker index by a conservative WGS84 100m-expanded edge envelope, then run this exact evaluator on candidate supported sections. Split dateline envelopes; retain inclusive numerical tolerance. Index bounding boxes are only candidate selection, never discovery proof. Exhaustive-vs-indexed fixtures must prove no boundary false negatives.
3. Store compact validation tokens/section references, not cloned matcher trees. A bounded projected reader should admit only original evidence needed by this adapter; pin read byte/vertex budgets before retaining a JSON record. Cache validated per-journey sections by source token + validation revision; invalidate after corrections/deletion/mode changes.
4. Process one archive file or bounded batch at a time. Persist a durable cursor only after atomically committing a completed batch against unchanged archive/catalogue/policy tokens. Cancellation/crash discards unfinished results and resumes the last committed cursor. The pure evaluator can be deterministically rerun; it performs no partial persistence.
5. Key future assessment by journey token, catalogue hash/revision and policy/validator revision. Before publication recheck all revisions; a concurrent capture/import/correction invalidates staged results. Keep prior complete snapshot while updating; yield to capture.

No R-tree installer, historical worker, durable cursor, ledger, background job or discovery cache is activated by this PR. Their device budgets, cancellation during archive parsing, and stale-index invalidation require Stage 3/6 validation. Current local timing/RSS/heap measurements are recorded in the PR; they are pure JVM synthetic probes, not Android/ART or an indexed national scan benchmark.

## Validation and integration boundaries

Fixtures cover nine distance boundaries; finite interior/endpoint and disconnected parts; UK latitude extremes, independent Python GeographicLib coordinate oracles and reverse direction; foot/cycle/vehicle modes; no-stop/vegetation/parallel-path policy; sparse samples; drift/gaps; partial/rejected matches; provenance/corrections; sufficient typed historical sections vs insufficient real importer schemas; multiple journeys/distinct posts; repeatability, immutable observations, sorted-key tokens; cancellation; and all 889 ineligible candidates. A 64MiB JVM probe streams 1,605 journeys / 802,500 edges and repeats native adaptation. The new read-only CI compiles/lints Android and runs focused capture, Timeline, catalogue and existing achievement-volume regressions; no packaging or deployment task is requested. Local pure-JVM compilation uses a test-only `MatchingCoordinator.isFoot` link shim for the unused `CaptureQualityValidator.displaySections`; hosted Android compilation/tests use actual application classes without a shim.

| Next stage | Boundary |
| --- | --- |
| 3 — ledger/idempotent history | Consume evidence results only after verified physical-marker registry, historical location eligibility, projected reader/index and trustworthy historical section adapter exist. Persist/retract compact proofs atomically; deduplicate by physical ID, never OSM candidate or proximity. |
| 4 — six individuals and In Honour of Time | Derive from the separate durable discovery snapshot; Grimshaw consumes existing true NCN completion. No current achievement calculation changes here. |
| 5 — summary/map/silhouettes/Harrow | Presentation reads committed state only; never awards or validates. Preserve seven-stage reveal and capture guards from the authoritative specification. |
| 6 — integrated validation | Real Android/ART memory, indexed candidate completeness, archive concurrency/crash recovery, backend compatibility, performance and accessibility. Existing device version/backend provenance remain unknown; source version is 0.25.223 (269). |

Physical verification, immutable physical-ID/alias registry, dated positions/presence, UK scope and reviewed eligible reference schema must precede production activation. Stage 1 has **zero** approved targets; this stage does not approve any. No unresolved owner product decision is introduced. Remaining questions are evidence availability, projected-reader/index budgets and implementation of the later stages.

No main merge, live discovery, achievement/entitlement write, reference-data publication, backend deployment, Android build/release or tester distribution is part of this delivery.
