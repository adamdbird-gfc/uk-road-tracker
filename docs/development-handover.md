# Roadprints development handover

Last reviewed: 2026-10-25. This is the **entrypoint**, not a replacement for issue specifications or code review.

## Diagnostic notification lifecycle — delivered 0.25.217 (263)

Continue from `fix/diagnostic-notification-lifecycle`, based on latest #219
`6fa85f85`. See [confirmed defect, incident limits and acceptance](diagnostic-notification-lifecycle.md).
Stored reports no longer become new alerts on process startup; failure event
identity/delivery/acknowledgement survives restarts. Unchanged GPS-quality
rejections are held by input fingerprint/rule version. Transient retry bounds,
raw evidence, journey history and map/NCN incremental work remain intact.
Final implementation `e4c02a1086d2ca9afac8c4a8c35f5583e8a34382` passed full
Android compile/lint, pinned references and unit tests in run 40177903529.
The suite includes actual stale PendingIntent identity, restart/idle/acknowledgement,
concurrent rediscovery, quality holds, repaired evidence and retry-after expiry.
**Delivered private Android 0.25.217 (263), PR #220.** Exact signed source
`5c0a29b0d4a41c627adaae231433539e51a115dd`.
[Successful private packaging 40178241874](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40178241874),
[APK artifact 12592274219](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40178241874/artifacts/12592274219),
[reports 12592413861](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40178241874/artifacts/12592413861).
**787 tests / 105 suites; zero failures/errors/skips**, lint, national references,
distribution controls and the established v2 signing certificate passed.
Downloaded XML confirms totals independently; ZIP and APK hashes match CI.
APK **36,606,617 bytes**, SHA-256
`a4325601d2d8f180b429ca7e8b7e25298174766cdbd3f7d75b65491057aa508d`.
Exact-source independent Android checks 40178244829 also passed.

Synthetic 1,547-driving map preparation: **986.71ms cold / 149.51ms warm /
140.24ms one addition**, identical completion. These are runner measurements,
not phone startup timings. Existing history/map/NCN tests pass; no cache reset.
Install over 216 without clearing data. No Firebase upload/tester distribution,
Play publication, backend change, historical deletion or automatic rematching.
Later documentation-only commits leave this binary unchanged. Original Oct 25 post-versus-OS-delay attribution remains
unprovable without contemporaneous device posting data; the old regeneration
path is confirmed in delivered 213 and 216 source.

## Contextual journey actions — delivered 0.25.216 (262)

Continue from `fix/contextual-journey-actions`, stacked on #218's latest
`0c48f84e`. See [changes, evidence limits and validation](contextual-journey-actions.md).
Evidence-gated ferry review/restore, consistent 8dp action spacing, timed recorded
preview sections without gap connectors, missing-hash NCN metadata protection,
warm incremental assessment and stage-specific slow-frame diagnostics.
No historical rewrite/rematch, cache reset, Firebase distribution or backend change.
Implementation `3606d591a402c7c7e09a13b79ee5113dba783d89` passed full
Android compilation/lint, pinned references and unit tests in run 40134680370.
Ferry/ambiguous-mode/button regressions, exact-duration GPS-gap/restart replay,
missing-input-hash NCN metadata and unfinished foot/cycle retry tests passed.
**Delivered private 0.25.216 (262), PR #219.** Exact signed source
`4555346dcc770be3a41fc1767e523fa3a23c9d7c`.
[Successful private packaging 40135070445](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40135070445),
[APK artifact 12576617872](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40135070445/artifacts/12576617872),
[verification reports 12576922475](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40135070445/artifacts/12576922475).
**771 tests / 103 suites, zero failures/errors/skips**; lint, national references,
distribution controls and established v2 signing certificate passed. Downloaded
XML independently confirms totals; artifact ZIP digests and APK hash match CI.
APK **36,590,025 bytes**, SHA-256
`52cfa666d3d5fa1fe9b1e18ceef6fcab18b9ba2d172b118c51fe769032299265`.
Exact-source independent Android checks 40135074362 also passed.
Install over 215 without clearing data. No Firebase upload/tester distribution,
Play publication, backend change, historical rewrite/rematch or cache reset.

Synthetic 1,547-driving map preparation: **1,148.87ms cold / 258.17ms warm /
176.18ms one addition**, identical completion. Warm reused 1,547 with zero source
reads; adding one reused 1,547 and read/rebuilt one source. Runner measurements
are not phone startup/frame proof. Updated draw-stage telemetry supports later
ordinary-use diagnosis; the original missing-fix/remaining phone-frame causes
are unconfirmed without the new diagnostic export.

## Generalised journey intelligence — delivered 24 October 2026

Continue from `fix/generalised-journey-intelligence`, based on delivered 214
and the validated production backend slice #217. Candidate **0.25.215 (261)**.
See [portable acceptance and behavioural changes](journey-intelligence-portable-acceptance.md).
No return to any original travel location is required. Deterministic portable
regressions are the release acceptance gate; future device journeys are optional
observational feedback. Final implementation `957e7c10402a67a2d831eb801c2fc8624ccfc836` passed full
Android compile/lint, pinned reference and unit tests in run 40128957116.
Database-backed context/walking run 40128957090 and road-recovery run
40128957091 also passed. No tests excluded. **Delivered one private signed 0.25.215 (261), PR #218.** Exact binary source
`d793d8b102ee85f1b1b51f62baa7dfed5ed19608`.
[Successful packaging 40129351156](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40129351156),
[APK artifact 12574201664](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40129351156/artifacts/12574201664),
[reports 12574241629](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40129351156/artifacts/12574241629).
**759 tests / 102 suites; zero failures/errors/skips.** Lint, national references,
distribution controls and established v2 signing certificate passed. Downloaded
XML independently confirms totals. APK 36,589,905 bytes, SHA-256
`a5a92be337bc0345dbe4722c2bcb46fd883bcb4659fd10611efc4fa8144f32be`;
downloaded ZIP and APK hashes match build output. Exact-source independent
Android CI 40129355413, context/walking CI 40129355394 and road-recovery
CI 40129355386 passed.

Synthetic 1,547-driving preparation: 727.54ms first / 187.03ms warm /
126.33ms one addition, identical completion. These are runner measurements,
not phone startup results. Existing map/NCN cache and history regressions pass.
Install over 214 without clearing data. No historical migration/rematch, global
cache invalidation, Firebase upload/tester notification or Play publication.
Later documentation-only commits leave the delivered binary unchanged.

Backend merge `7dd2c58c628ec819ace2b645d18aa296ddb238b4` is live on Render,
deploy `dep-dbegppdg1s2s739dhv30`, service `uk-road-tracker-api` in the owner-
selected My Workspace. Health confirms policy version 2 and database ready.
Live public walking references matched 6/6 Kent points (30.7m) and 8/8 Scottish
points (34.3m); overseas fallback returned real geometry for 3/4 points (42.3m),
one upstream call and explicit unmatched evidence. Runtime counters corroborate.
No personal GPS or journeys were submitted. Exact historical timeout bottleneck
remains unknown; logs establish the 55-second application deadline only.
Issue #216 records portable acceptance and optional future observations.

## Disneyland corrective candidate — 24 October 2026

Continue from `fix/disneyland-gps-integrity`, based on private 213's actual
application lineage. Candidate **0.25.214 (260)**. See
[confirmed evidence, policy, validation and limits](disneyland-stabilisation.md).
Offline mapped resort recognition, confirmed boundary departure, raw internal
capture preservation, GPS freshness/ordering and moving-gap protection,
bounded pedestrian simplification and backend recursive-search budget.
No historical deletion/rematch, map/NCN cache reset or tester distribution.
Final implementation source **f786fde2df32e05edaa98be7273595fcf35b0bf2** passed
[Android compile, lint, national reference and complete unit tests](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40055191072).
Database-backed context/walking CI 40055191068 and road recovery CI 40055191048
also passed. Local backend suite: 87 tests, five DB-dependent skips (covered
in database-backed CI). Earlier failures were missing 214 release notes and an
obsolete uniform-sampling expectation; both corrected, no tests excluded.
**Delivered private 0.25.214 (260), PR #215.** Exact signed source:
`a0467b6ca788cf38e3df450289ae78be9c66b006`.
[Successful private packaging 40055485610](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40055485610),
[APK artifact 12544289738](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40055485610/artifacts/12544289738),
[verification reports 12545137018](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40055485610/artifacts/12545137018).
**746 Android tests / 101 suites; zero failures, errors or skips; lint passed.**
Downloaded XML independently confirms totals. National reference verification,
private-distribution controls and established v2 signing certificate
`5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`
passed. APK **36,589,685 bytes**, SHA-256
`d29eae2db9df5d1e108ab94d5b7026091ff8ed755cf0899360f09872951ba8a2`.
Downloaded artifact ZIP digest and APK checksum match build output. The exact
packaged source also passed independent Android CI 40055489684, database-backed
context/walking CI 40055489573 and road recovery CI 40055489645.

Synthetic 1,547-driving runner preparation: **1,321.98ms first, 355.69ms warm,
185.97ms after one addition**, identical completion. Warm reused 1,547 records
with zero source reads; one addition reused 1,547 and rebuilt/read one source.
These are runner timings, not Pixel measurements or proof of device latency.
Existing NCN/cache and history tests pass; no global invalidation/migration.

Install over 213 without clearing data. **One signed APK built; no Firebase
upload, distribution/notification, Play publication or backend deployment.**
Outstanding hotel footprint scope, live backend rollout/log access and field
acceptance: [#216](https://github.com/adamdbird-gfc/uk-road-tracker/issues/216).
The offline mapped boundary is not a claim to cover every hotel/connecting road;
the eastern evening walk crosses it. Production matcher timeout root cause
remains unverified without logs. Final documentation-only commits do not change
the delivered binary.

## Current multimodal corrective candidate — 21 October 2026

**0.25.213 (259), PR #213**, stacked on #212. Preserve 212's device-verified
New Journeys and map/NCN caching improvements. Exact original baseline
`b9b65eb58e8b4ed4d0c6f5c06fe972c5368bb861`.

See [evidence, implementation limits, tests and next device checks](multimodal-ferry-stabilisation.md).
The 09:00 UTC recording starts near Calais and has no supported crossing interval:
**do not invent a Dover ferry split**. Movement logs identify the unhinted cycling
place-departure heuristic. Changes add conservative Dover–Calais terminal/marine
handoffs, recorded ferry map display, explicit journalled split/restore review,
bounded transport/matching diagnostics and retries, negative overseas road gating,
and NCN stability for ineligible transport matching-state changes.

**Delivered private 0.25.213 (259).** Exact signed APK source:
`5e9f1d56845584559fa8444715d9df0e4f650466`.
[Successful private packaging 39676918329](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39676918329);
[APK artifact 12381562978](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39676918329/artifacts/12381562978);
[reports 12381488029](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39676918329/artifacts/12381488029).
Exact-source [independent checks 39676924222](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39676924222) also passed.
**732 Android tests, 100 suites, zero failures/errors/skips; lint passed.**
National reference and private-distribution controls passed. Downloaded report XML
independently confirms totals. APK: **36,584,107 bytes**, SHA-256
`06d05a607da397531185565b9fa207ecbd10fb28ea2645f148fe31208b23e6da`.
Downloaded artifact ZIP digest and APK checksum both match GitHub/build output.
APK v2 signature and content digest independently verified with the established
certificate `5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`.
Binary manifest independently confirms `com.roadprints.capture`, 0.25.213/code 259.
Install over the existing app without clearing data. One signed APK was built.

Synthetic runner preparation (1,547 M2/A2 driving journeys): first **1,358.51 ms**,
warm **132.81 ms**, one addition **112.23 ms**, identical completion. Warm reused
all 1,547 records with zero source reads; one addition reused 1,547 and read/rebuilt
only one source. These are runner preparation timings, not phone startup or a
claim of improved first-materialisation speed. Device map and NCN timing acceptance
remains outstanding; preserve the owner's 212 measurements as baseline.

Initial checks found helper access errors, missing ferry display geometry, a
missed A-road scope guard, and a too-short activity-hint expiry for sparse running;
all were corrected, with no excluded tests. The stale-result expectation now keeps
a corrected bus pending, and the running fixture explicitly supplies a running
hint. An additional expired-bicycle-hint coach regression passes. Initial mode and
classification timestamp survive checkpoints; older missing evidence exports null.
No actual device verification, Firebase upload/distribution/notification, Google
Play publication or backend deployment. #214 tracks missing crossing evidence,
router/integrity follow-up and field acceptance. Later documentation-only commits
do not change the delivered APK; do not build again merely to resume this handover.


## Current P0 map performance candidate — 20 October 2026

Delivered private **0.25.212 (258)**, [PR #212](https://github.com/adamdbird-gfc/uk-road-tracker/pull/212),
`fix/android-map-contributions`, based on 211's documented head
`692526f285c3495a578f0cb29e3239f929c920b2`.
**Owner acceptance update:** 211's New Journeys flow is FIXED/device-verified;
incremental NCN assessment is typically 1.5–2 s. Main Map still takes 46–62 s,
including 19–38 s evidence and 21–22 s coverage addition.

212 caches compact per-source filter headers, prepared display geometry and exact
motorway/A-road covered-index contributions. Reuse validates actual reference
anchor fingerprints. Source changes, deletion, corruption and explicit Retry
remain correct. Cold spatial matching avoids neighbour-string allocations while
preserving the original sampling, radius and nearest/tie calculations. New binary
snapshot headers reject stale revisions before inflation and avoid checksum
re-serialisation. Main-map publication batches camera fitting. NCN evidence and
metadata revision handling are separated; identical saves do not bump revisions.
**New Journeys presentation/acknowledgement code is unchanged.**

**Delivered private 0.25.212 (258).** Exact signed/tested source
`5d5febdf35f8ac451565b110fbab1e7531fa8116`;
[successful private packaging 39509571145](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509571145);
[APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509571145/artifacts/12305051296);
[verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509571145/artifacts/12305161122).
**711 Android tests passed; zero failures/errors/skips.** Lint, pinned national
reference verification, distribution controls and stable v2 signing passed.
[Independent exact-source checks 39509577110](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509577110)
also passed. APK downloaded and independently SHA-256 verified:
`b0b40ea84ba636406eab4a2903361aa64bc5a5b98de65667f96b7dadb386d39f`, **36,567,523 bytes**.
Established signing certificate:
`5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`.
One signed APK, no Firebase upload/distribution/notification or Play publication.

### Exact-source automated measurements

Synthetic 1,547 driving journeys using real bundled M2/A2 geometry, **not the
owner's Pixel archive**. These are preparation timings, not Android startup/frame
measurements. Cold means the first compact-record materialisation on the runner;
warm reopens the persisted store; addition reuses those records.

| Runner stage | First materialisation | Warm reopen | One addition |
| --- | --- | --- | --- |
| Total preparation | 896.48 ms | 120.55 ms | 84.33 ms |
| map_evidence | 781 ms | 95 ms | 58 ms |
| map_coverage_add | 82 ms | 8 ms | 6 ms |
| map_coverage_finish | 1 ms | 1 ms | 1 ms |
| map_geometry | 7 ms | <1 ms | <1 ms |
| Original source reads | 1,547 | 0 | 1 |
| Records rebuilt | 1,547 | 0 | 1 |
| Prior records reused | 0 | 1,547 | 1,547 |

Completion percentages, covered canonical indices and displayed sections remain
identical in these tests. New source/row/reference invalidation, deletion,
corruption, interrupted preparation, forced Retry, oversized-row fallback,
metadata-only NCN restoration, duplicate saves and transport defaults passed.

Controlled whole-map snapshot comparison, identical **760,597-byte** payload:

| Runner operation | Legacy gzip-wrapper algorithm | New header/payload algorithm |
| --- | --- | --- |
| Valid snapshot read | 33.46 ms | 22.49 ms |
| Stale snapshot rejection | 12.76 ms | 0.114 ms |

These single-run measurements demonstrate the mechanisms; do not extrapolate
their ratios to a real phone. Full report XML and workflow logs retain raw values.

**Outstanding device measurements:** 212 Pixel map_total/map_render/map_slow_frame,
NCN assessment/metadata-refresh latency, first upgrade compaction, subsequent
process-cold/warm/addition and memory/ANR behaviour. 211 owner baselines remain
46–62 s total, 19–38 s evidence, 21–22 s coverage, 3,691 ms cache, 599 ms render and
455 ms slow frame. The 10-second Pixel target is **unverified**.
First-use or cache-evicted original evidence must still be read. Entries above
1 MiB safely rebuild instead of overflowing CursorWindow; `oversized` diagnostics
make that performance limit visible. No original Pixel dataset or emulator
measurements are claimed. Keep #201/#194/PR #212 open for owner acceptance.


See [root causes, cache/rule maintenance, measurements and acceptance](map-performance-stabilisation.md).
Initial implementation: compile/lint passed; all eight new functional tests
passed in [run 39508236450](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39508236450).
Its only failure was missing 212 release notes, now corrected. Latest integrated
[checks 39508974518](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39508974518)
passed all 710 tests, compilation and lint. Final packaging repeats the complete
gate, including compact metadata and Android CursorWindow size safeguards.
Initial runner benchmark (synthetic 1,547 driving journeys, real M2/A2 geometry):
first preparation 1,483.73 ms; warm 418.03 ms; one addition 268.47 ms; completion
unchanged. These are **JVM preparation, not Pixel app startup**, and will be
superseded by exact packaged-source results.

First-use compaction still requires old source evidence and matching; normal
subsequent restarts/changes reuse records. Cache eviction can require rebuilding.
Actual Pixel cold/warm/addition and frame/ANR acceptance remains outstanding;
the 10-second target is unverified. Do not hide incomplete layers to claim it.
Counts from 211 reconcile 1,547 = 1,355 shown + 192 unprocessed, without establishing
that every unprocessed journey is train/flight. New aggregate mode/status counts
verify that classification without changing visibility.

One private APK delivered after green checks. No Firebase upload/tester distribution,
notification, Play publication, catalogue change or unrelated NCN features.
Keep #201/#194 and the PR open for owner performance acceptance.

## Previous P0 history and journey-review stabilisation — 20 October 2026

Private development **0.25.211 (257)** in `fix/android-history-stabilisation`,
[PR #211](https://github.com/adamdbird-gfc/uk-road-tracker/pull/211), based on
210's documented head `6c0deacedd0e560a73229ab6b289fdff2fccf441`.
This supersedes 210's performance acceptance: the owner's real archive still
reports 68–141 s assessment and 37–55 s initial Map. No device acceptance claimed.

**Private APK delivered:** exact tested/signed source
`290d29c1c506a7b7f2a821abd4452e0e5739dd74`.
[Successful private run 39503115073](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39503115073);
[APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39503115073/artifacts/12302947068);
[verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39503115073/artifacts/12303056971).
**701 Android tests passed, zero failures/errors/skips**, lint and pinned national
reference checks passed, stable APK v2 certificate verified
`5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`.
APK SHA-256:
`7e6f035c0a3936237e08304af256594da47bfd3c893648c2db4afc69b0ca04e3`.
Separate [exact-source Android checks 39503121191](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39503121191)
also passed. The successful package has only `development / build`, no Firebase
upload/distribution job. One coordinated signed APK was built.

Download while signed in to GitHub, extract the artifact ZIP and install
`app-debug.apk` over the existing app without clearing data. Artifact retention
ends 27 October; reports expire 23 October. No device/emulator was attached.

| Performance evidence | Before | Candidate result |
| --- | --- | --- |
| Owner Pixel: NCN assessment | 68–141 s | Requires owner device timing |
| Owner Pixel: initial Map | 37–55 s | Requires owner device timing; 10 s target unverified |
| Synthetic 1,546-journey NCN source decodes | Every fallback scans 1,546 sources | Cold 1,546; unchanged 0; one addition 1 |
| Existing data/coverage correctness | Baseline golden expectations | Full original suite and 12 new stabilisation tests passed |

The synthetic test records `HISTORY_BENCHMARK` cold/warm Map preparation, NCN
assessment and single-addition elapsed times in its XML `system-out` in the
reports artifact. Those runner times are not Pixel startup measurements; they
have not been independently transcribed in this handover. No claim of phone
speed or ANR-free acceptance is made from these tests. Actual phone critical-path
attribution, exact 192-count reconciliation and capture/restart acceptance remain
outstanding. Keep #201/#194/PR #211 open; broader NCN work remains paused.

- **NCN:** every JourneyStore save bumps the global archive revision (including
  matching/status/dwell updates with unchanged journey count). That rejects the
  aggregate checkpoint. The previous fallback streamed and hashed all 1,546
  source files even on per-journey assessment-cache hits. Ledger construction
  also queried/decompressed the same source way for each repeated activity span.
  Assessment now validates per-file atomic-replacement tokens and checksummed
  compact assessment/metadata rows; only missing/changed/rule-invalidated evidence
  is decoded. Repeated source spans are unioned by way/mode before catalogue
  lookup. New ledgers exclude deleted IDs; individual journey assessments remain
  available for attribution. Catalogue/rule changes still force applicable
  reassessment. Existing single-worker/running guard and queued change coalescing
  remain. Atomic aggregate persistence completes on that worker before a newer
  request can overtake it. Changed revisions/cancellation cannot publish stale
  results; failed optional caches fall back to original evidence.
- **Review:** the recap was gated on `mapReady || hasDisplayedMap` and queued on
  the shared screen-data executor. New capture IDs are now persisted at their
  first committed save, with a dedicated inbox reader/presenter independent of
  Map and NCN. The existing Welcome back / VIEW NEW JOURNEYS flow, explicit
  Dismiss/Back/navigation acknowledgement and saved-journey editor remain.
  An acknowledgement set prevents a later historical discovery scan re-enqueuing
  a journey already dismissed from the fast inbox. Presentation alone never
  acknowledges. Imports/rematches are not new captures.
- **Map:** whole-archive revision invalidation previously rebuilt every source
  projection and all coverage. The shared disk cache rejected map payloads over
  12 MiB; optional cache work also queues behind other screens. Per-source
  checksummed map/summary projections now survive unrelated additions. Complete
  Map snapshots use atomic compressed storage with a 48 MiB decoded bound;
  original compatible small snapshots remain readable. Rendering, eligibility,
  geometry budgets, filters, recovery/Retry/Reset and retained UI state remain.
  Archive revision polling no longer acquires the archive-save monitor, avoiding
  foreground waits behind serialization, venue checks and capture awards.
  Exact Pixel bottleneck proportions still require the new stage diagnostics.

Diagnostics: assessment trigger, coalesced requests, restored result, full versus
incremental source reads, matches/reuse and aggregate miss reason; map reference
initialisation/cache/evidence/filter/coverage/geometry/finalisation/render/total;
review creation/presentation/acknowledgement. Existing foreground stall stacks
remain. No coordinates or personal journey IDs are logged.

Visibility rules are unchanged: only complete driving/bus/on-foot/cycling
journeys with matched geometry are counted by the main map. Train/plane/ferry,
unknown, unprocessed and empty-geometry journeys can explain the gap; **192
cannot be reconciled exactly without the phone archive**. `map_filter` now
reports disjoint aggregate exclusion counts. Do not force all history onto Map.

Initial implementation checks passed in [run 39502451966](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39502451966):
compile, lint, pinned reference verification and all 699 Android tests.
Final packaging also validates bounded background projection/snapshot writers and
cancellation-resume regressions added after final diff review. Optional cache
creation cannot gate first-map publication; saturation skips only disposable
projection warm-up, while snapshots retain the newest queued request. Added tests cover incremental
addition/edit/delete, stale/corrupt/rule-invalid caches, restart checkpoint,
durable pending review/interrupted presentation, blocked Map/shared queue,
rapid same-size replacements, large compressed Map snapshots, revision monitor
contention, and a 1,546-journey synthetic cold/warm/addition workload.
Synthetic JVM measurements are not Pixel startup measurements.
Required owner checks: install over 210 without clearing data; cold/warm/offline
restart; repeated Map/Journeys/Progress/Achievements navigation; single/multiple
captures foreground/background; interrupted review and restart; filters,
Cancel/Reset/Retry; unchanged completion, attribution and celebrations.
**Aim for a usable Pixel map within 10 s; this remains a measurement target.**

Private artifact-only packaging completed after green checks. No Firebase upload,
distribution, tester notification, Google Play release or catalogue publication.
The session has no local execution host/device; GitHub Actions performs compile,
lint, tests and signing. Do not close #201/#194 from CI alone.

## Previous private development delivery — responsiveness and NCN achievements

**Private development 0.25.210 (256)** is ready for owner testing, in
[PR #210](https://github.com/adamdbird-gfc/uk-road-tracker/pull/210), stacked on
209's `fix/ncn-ui-private-development` lineage. No Firebase upload, tester
distribution or notifications. No approval for wider promotion.

Exact signed APK source: **`c6f092d9800695bcca6e0839c54fe30f8603ab7c`**;
application implementation `64582b750803c465ec33487f68bf971cdba00f62`.
[Successful private packaging 39396622144](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39396622144),
[APK artifact 12252785264](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39396622144/artifacts/12252785264),
[reports 12251754332](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39396622144/artifacts/12251754332).
One signed APK: **36,550,759 bytes**, SHA-256
`9dbe9dd6ac72d57510bbe62e0bb9b67d3261cb0704c17518052f42652b0e7bfb`.
Downloaded bytes independently match; binary manifest confirms
`com.roadprints.capture / 0.25.210`. The established v2 signing certificate
`5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`
passed the workflow's fail-closed verification. Install over the existing app
without clearing data.

**Responsiveness:** separate NCN installation and UI scheduling monitors;
background-priority assessment/overlay workers; cached route/network totals;
superseded overlay cancellation and activation-epoch cancellation; validated
derived checkpoint restoration on unchanged process restart. Selected NCN map
preparation/failure has an explicit footer status. Core map, archive, matching,
catalogue and established 209 Progress design remain. Bounded local timings,
heap/slow-frame samples and foreground main-thread stall stacks are included in
Debug; routine timings do not trigger debug notifications.

**Achievements:** first eligible discovery; combined 25/50/75/100 families for
each measurable route and the unique physical network. Full-route completion is
the 100% family level. Walking/running and cycling only, including history;
driving/unknown excluded. No additional archive scan or database query for
awards. Persisted recognition dates, current-level correction/deletion and the
existing celebration carousel/seen-level acknowledgement are reused. NCN228 and
source holds remain excluded. Kent/Millennium discoveries and poem/disc/theme
unlocks remain explicitly held for independent location/disc evidence; no
coordinates, artwork or verse associations are fabricated.

**Verification:** full packaging repeated **689 Android tests, zero failures,
errors or skips**, lint zero errors, national reference verification and stable
signing. Downloaded XML independently confirms totals. All **322 Python NCN
tests**, five reference-control tests and four distribution-control tests pass.
Separate final source checks
[39396145306](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39396145306)
and exact request checks
[39396628007](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39396628007)
also pass. Initial new-test failures identified the compressed fixture and a real
floating-point threshold-boundary defect; both were corrected, no tests excluded.
A dependency-resolution failure was retried as a failed check job only.

| Automated runner workload | Before | After |
| --- | --- | --- |
| Eight-route ledger, three modes, 1,000 repeats (24,000 total reads) | Full scans: 15.160 ms | Cached totals: 6.193 ms |
| Refresh scheduling while the installer monitor is deliberately held | Old implementation waits for the long installation's class monitor | 0.363 ms; simulated installer still blocked |

These are synthetic/JVM timings, **not phone cold-load or frame-rate results**.
The monitor dependency is verified from released code and contention tests;
the original device ANR stack and full archive are unavailable. No actual
Pixel/Samsung ANR-free acceptance or before/after phone timings are claimed.
The owner must test cold/warm/offline restart, Map and retained tab returns,
filters/Reset/Retry, continuous routes, road/NCN details, search/activity controls,
historical unlocks and celebration acknowledgement. Capture Debug after any
stall. Keep #201/#194 and this draft open for device acceptance. Changed archives
still stream for fingerprint validation; optional derived checkpoints above
32 MB fall back to resumable assessment without discarding evidence.

See [implementation, migration and acceptance detail](ncn-performance-and-achievements.md).
Later documentation-only commits do not alter the signed APK. Do not rebuild
merely to resume this handover; next step is owner device acceptance.

## Latest private development delivery — NCN badge and collection-control consistency

**Private development 0.25.209 (255)**, based on installed 0.25.208 (254).
Exact tested/signed APK source `a6335f7e1cf9e3ab481f10abf797bb4b0bf60d3a`;
[successful private build 39387092291](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39387092291).
[APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39387092291/artifacts/12247688870);
[verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39387092291/artifacts/12247484583).
APK: **36,534,079 bytes**, SHA-256
`88fc05feb9d50b00b7acfaeb31c94d64bcdac6209014c70eb0a13ddf2e5cb2f0`.
Downloaded bytes independently match that checksum. Binary manifest independently
confirms `com.roadprints.capture`, version 0.25.209, code 255. Established v2
signing certificate verifies unchanged:
`5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`.
Install over the existing app without clearing data.

Changes in [PR #209](https://github.com/adamdbird-gfc/uk-road-tracker/pull/209):

- Reuses a 78 × 32 dp rounded road identifier in compact NCN Progress rows:
  purple/white badge, adjacent 12dp white-track bar and percentage, reference
  distances and visible VIEW MAP affordance on a minimum 64dp row touch target.
  Focused summaries use the same badge in motorway blue, A-road green or NCN
  purple; their main screen heading remains unchanged.
- Reuses the venue collection white/jade search field and 48dp selected/unselected
  activity buttons. Search spans groups and names/number identities, including
  colon/space variants; empty results are explicit. Clear restores browsing group
  expansion. Activity changes preserve the input and update existing summary/rows
  without rebuilding controls. Search/group/mode state survives route navigation.
  Search/Done, Clear, activity selection and onward navigation dismiss input.
- No matching, eligibility, catalogue, accounting, cache, map geometry or existing
  blue/green/purple/orange rendering algorithms changed. B/local recorded-only
  presentation, journey counts and map controls are preserved.

**Validation:** 684 Android tests passed, zero failures/errors/skips; lint zero
errors, stable signature verification, four distribution-control Python tests
passed. Downloaded reports XML independently confirms totals. NcnNationalTest
(two), NcnUiTest (two), NcnPresentationTest (five), existing venue framework (11)
and Progress availability (six) checks passed. New tests cover compact dimensions,
white badge text/colours, mode-specific/physical network values with a retained
query, cross-group search, empty/clear behaviour, expansion restoration, keyboard
dismissal, return state and unchanged catalogue keys/denominator. The initial
compile attempt identified missing callback exception handling and produced no
APK artifact; it was corrected before the successful full gate, without exclusions.

Separate Android checks run 39387101041 initially hit Maven Central HTTP 429 while
resolving dependencies. Only that failed check job was retried; no APK rebuild.
Its merge tree is identical to the successfully packaged source. The single retry passed compilation, lint, verified reference preparation and the
full integrated unit suite. No additional APK was built or distributed.

**Private only:** successful packaging contains only `development / build` and
no Firebase upload/distribution job. Existing testers were not targeted/notified.
Approval-only artifact promotion safeguards remain; no approval to promote 209
has been given. Catalogue serving was reused and checksum-verified, not regenerated
or republished. The original catalogue objects and all user history are preserved.

**Device acceptance pending:** compare M20/A12/NCN/RCN compact rows and focused
M/A/NCN badges; test A627(M)/A1(M), narrow/large-font layout, combined search/mode
changes, clear/empty/group restoration, keyboard dismissal, return position and
long-list scrolling. See [review checklist](ncn-ui-private-acceptance.md). Source/JVM
checks do not establish real-device visual, keyboard or performance acceptance.
Later documentation-only commits do not change the tested APK.

## Previous private development delivery — NCN UI corrective APK

20 October device feedback authorises UI refinement and **private artifact-only**
packaging. Version 0.25.208 (254), based on installed NCN beta 0.25.207 (253),
keeps the published national catalogue and all assessment rules unchanged.
Achievement alignment, bicycle collection cards/removal confirmation, shared
coverage dashboard, purple NCN Progress cards/jump tracking and solid orange
remaining NCN map lines are implemented. **Private APK built successfully** from exact source
`27236e1b143f5225aa964a67b4f27e43122cdae6`,
[run 39382924243](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39382924243).
[APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39382924243/artifacts/12246766274);
[reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39382924243/artifacts/12246157765).
682 Android tests passed, zero failures/errors/skips; lint zero errors. Both national
data classes and new celebration/collection/paint/summary/jump regressions passed.
Four Python distribution-control tests passed. APK v2 signature verifies against
existing certificate `5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`;
package remains `com.roadprints.capture`.
APK: 36,533,787 bytes; SHA-256
`31e40a81174d2c362a919a3815b2bf3debf5ad93029a73c88efd56904341e7f0`.
Downloaded artifact bytes independently matched the build checksum; reports XML
confirmed all test totals and zero lint errors. **No Firebase upload/distribution
job exists in the successful run; existing testers were not targeted or notified.**
The first attempt stopped at two assertions (old summary text and missing test
fixture target), produced no APK artifact and was corrected without bypassing tests.
[PR #209](https://github.com/adamdbird-gfc/uk-road-tracker/pull/209) retains the work;
#208 tracks distribution controls. No real-device acceptance of these refinements
yet; see [owner review checklist](ncn-ui-private-acceptance.md). Later docs/workflow
hardening commits do not represent another APK build or change the tested app.

**Distribution policy supersedes all older automatic release instructions below:**
Do not distribute development APKs to existing Firebase testers. Packaging retains
signed artifacts only; approved beta promotion requires a separate manual owner
approval naming the exact commit and APK SHA-256. No approval has been given for
this corrective build. See [development/distribution controls](android-development-distribution.md).

## Latest delivery — coordinated national NCN beta

**Released 0.25.207 (253)** through Firebase App Distribution to the established
testers. [Install NCN beta](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/7hem35tolspl8).
Exact tested, signed and distributed source:
`02ddf96aba6c403e2e88eab0d764feb61ef27ec4`.
[Successful packaging/distribution run 39331303438](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39331303438);
[APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39331303438/artifacts/12224797445);
[verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39331303438/artifacts/12223649168).

Draft [PR #207](https://github.com/adamdbird-gfc/uk-road-tracker/pull/207),
`integration/ncn-firebase-beta`, retains the implementation and corrected
publication/packaging workflow. Application baseline remains released 0.25.206
lineage `7bc10f4a`; later documentation reconciliation is not a second APK.
No journey archive or earned achievement migration/deletion was introduced.

**Required release checks passed:** 677 Android tests, zero failures/errors/skips;
lint zero errors; stable-key APK signature verification; Firebase confirmed both
release upload and tester distribution. The previously blocked `NcnNationalTest`
(two tests) and `NcnUiTest` (one test) ran successfully against the published
national database. Prior 322 NCN Python tests, national database readback and five
publication invariant tests passed. See [integration details](ncn-national-integration.md)
for hashes, provenance, accounting, test boundaries and remaining limitations.

**Catalogue already live:** publication
[run 39330952075](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39330952075)
verified preview/live serving on existing Hosting site
`https://roadprints-data-822cc.web.app/`, preserving all 18,155 prior files.
The private Drive transfer and existing Firebase authentication both succeeded.
There is no outstanding transfer/authentication blocker. Do not regenerate,
re-upload, or republish the catalogue without evidence of a genuine defect.
The first packaging attempt used an older reusable workflow; the corrected
`package-existing` run reused the live catalogue, prepared checksum-verified
national test data and completed the full signed release without republishing.

Catalogue delivery uses a small versioned manifest, immutable compressed SQLite,
compressed/decoded SHA-256 checks, resumable download and atomic offline cache
with prior-generation fallback. Catalogue-only updates require no APK. First use
downloads about 136 MiB; offline use requires a previously downloaded catalogue.
Rollback is a higher manifest revision referencing retained immutable data;
automated fallback/rollback invariants passed, but live rollback was not exercised.

**Scope and acceptance:** 219 supported identities / 62,399 unique source ways;
218 measurable open-main targets. NCN228 remains browseable but unmeasurable,
34 held identities stay excluded, and Named routes have an explicit empty group.
No planned/unopened sections or unsupported connectors are admitted. Driving
earns no NCN credit; walking/running/cycling and unique physical-network coverage
are covered by automated tests. New achievements remain deferred to #193.
The original 71-object checkpoint `2ee1031218665ea50acd98fb38c7c3e4ae25ba6a`
is preserved; national binaries remain outside the release Git tree.

Focused motorway/A-road/NCN summaries use rounded **white tracks** with blue,
green and purple fills respectively. Full reference geometry (recorded geometry
only for B/local) drives fit after layout; zoom, loading/error/Retry, shared
journey cards and B/local Recorded sections presentation are retained.

**Ready for real-device acceptance; not device-accepted.** Install over the
existing app without clearing data. Verify cold/warm main-map readiness,
Filter/Reset/Retry, retained tabs, M/A white tracks and complete fit, B/local
recorded-section fit and journey counts, NCN first-download/resume/offline,
purple/grey-dashed overlay, mode filters, section journeys and return context.
Realistic-history assessment latency/memory, gestures and Android device visual
contrast remain unverified. Follow the original 33-check acceptance list.

## Previous delivery — Urgent Beta 1 corrective release (#201)

**Released 0.25.206 (252)** on 19 October 2026. [Install corrective 206](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/0hi0t0j0s2d18). PR [#205](https://github.com/adamdbird-gfc/uk-road-tracker/pull/205), based directly on 205's application/release ancestry `04c4287a8a20b1cb9a40ce2993bd7e4a15df4fb9`. **205 failed real-device acceptance** (repeated initial-map timeout, incorrect chart track, B2008 UK-wide view); its prior automated green result did not prove those behaviours. This corrective slice supersedes the broader refinement brief. No NCN or unrelated UI development.

### Exact defects and minimal corrections

- **Initial-map timeout/recovery:** `8bc3d2b207fbf761fbf0b87cac57b963698c4202` added a hard 30-second budget over the whole cold load. Existing optional map-cache JSON serialization/fsync also occurred before geometry publication. A valid rebuild could therefore be cancelled before it became usable, and Retry restarted the same work. Preparation now has a **120-second bounded budget**; usable geometry is published before optional cache work, which runs on the existing background screen queue. Successful publication no longer cancels/interrupts its worker. Retry and failure-Reset force fresh preparation and bypass derived caches; malformed cache data falls back to archive preparation. Existing selected-filter persistence, retained geometry/Cancel/Reset and generation protection remain.
- **Contradictory Ready footer:** the bottom control reflected matching activity independently of the map. A map-local status override now displays **Loading map / Map needs retry / Map ready** from the actual map transition. Active matching detail remains available; other screens retain their previous matching status behaviour.
- **Progress charts:** `086435977337dfd77b3777be484aa37645ebd355` introduced a themed Android ProgressBar for focused cards, and 205 merely tinted its background white. That did not reproduce the Progress screen's actual white weighted track. Both screens now share **RoadCompletionBar**, extracted from the existing Progress implementation: opaque rounded white track, **#005EB8 motorway blue / #25834A A-road green**, same percentage weights. Percentages and travelled/reference distance calculations are untouched; map red/blue/green strokes are untouched.
- **Camera fit:** the older `5a2c43651` setRouteData path marked fitting pending without applying a fit if layout had already happened. The asynchronous focused-road UI exposed that path for B/local sections. Focused screens now explicitly fit **covered plus remaining geometry** once per geometry publication, immediately or at the first valid layout. B/local roads supply recorded sections only. Finite short/single-point results receive useful bounded zoom; malformed/empty bounds do not replace a valid camera. Secondary journey metadata and later redraw/layout do not repeatedly overwrite manual zoom.

The exact original phone runtime/stack was not available. The 30-second cancellation boundary and late-geometry fit defect are deterministically reproduced in regressions; the cold-load runtime on the full device archive still needs acceptance timing.

### Provenance and checks

Production implementation `a63263c311f5202f8c975afcf6221c5b2f2e29fc`; exact final tested/packaged source **`452258f655e23a1cdd3dcddebcd037b0cdb5ac28`** (adds retained evidence to trigger the actual lazily loaded A12 reference in its test). PR #205 merged as `44781c8902e44c413c4a47559477dababe698dee`; immutable release request `1a4d5bcc5edc8c2078df8d8803148dd5eb25126b` pins that exact source. One signed APK request/build; the merge commit skips redundant push checks after successful PR checks..

[Android checks 39306947451](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39306947451) passed unit tests/lint. [Signed packaging/Firebase 39307530852](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39307530852) passed **656 tests; zero failures/errors/skips**, lint and stable-key APK signature verification, then uploaded and distributed Firebase release **`0hi0t0j0s2d18`** successfully to existing testers. [APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39307530852/artifacts/12213684912); [verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39307530852/artifacts/12215275175)..

Ten new focused regressions: valid cold All loading beyond the old cutoff; footer/error/retry consistency; failure-Reset clearing filters; publication while optional cache work is blocked; forced warm Retry even when the displayed revision matches; **native rendered white/blue/green pixels**; late B2008-style geometry; local geometry before layout; short/single/malformed geometry; actual bundled **M20/A12** complete reference fitting. Existing main-map cold/warm/filter/reset/retry/cancel/tab/restart, data-preservation, road/achievement/collection/capture tests remain. Old tint-only assertions now check the shared component and unchanged numerical progress.

### Device acceptance and limits

Install over 205 without clearing data. Verify cold All map loading (allow a large cold rebuild to finish), warm return/restart, foot/date/combined filters, no-result filters, Reset, Retry after error and tab switching. The footer must follow the same map state. Record RoadprintsTiming queue/prepare/render/outcome durations; cache serialization no longer gates readiness. Check **M20 and A12** chart track/colours/values and full-route initial fit; **B2008 and one local road** recorded-section fit, zoom/pan, journey navigation and manual camera retention while metadata arrives. Check the prepared-map welcome overlay still dismisses without reload.

**Ready for real-device acceptance, not yet device-accepted.** There is no attached phone/emulator; native JVM rendering and actual bundled-reference tests do not prove Android 16/17 behaviour on the original archive. A truly stalled or exceptionally slow request still errors after 120 seconds with recovery controls; uninterruptible work cannot be forcibly terminated. Existing B/local full-road metrics and section-tap limitations and the documented legacy settlement-return recomputation limitation are unchanged. No journey archive, tracking state, matching/rematching, collection, achievement or permanent coverage data is migrated/deleted. A1 and A1(M) remain separate.

Historical scope of corrective 206: NCN was paused during that slice. The coordinated NCN beta above now resumes from this released application lineage.


## Previous delivery — 205 failed device acceptance (#201)

**Released 0.25.205 (251)** on 19 October 2026. [Install 205](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/0gmijfgpa0mgo). Based directly on the delivered 0.25.204 app/release ancestry (`f17f2f171b584931e56e5c4a0208b839f15561af`); PR [#204](https://github.com/adamdbird-gfc/uk-road-tracker/pull/204). No NCN implementation, unrelated PR merge, tracking/matching/rematching change, collection change, coverage-calculator change or data migration.

### Filter failure mechanism and repairs

The old in-session refresh submitted map preparation to the unbounded serial archive screen worker used by history, recap and Progress. Applying filters invalidated ready state and hid every recovery control, without cancellation or a finite deadline. Revision observation could supersede pending scans while those scans continued in the queue. Startup restoration used the same date/mode predicates but began with a new queue. The blocked-history regression reproduces this queue-starvation mechanism; the precise backlog and timing on the user's phone remain device evidence to collect, rather than a remotely observed thread trace.

- Main-map preparation now uses one separate cancellable serial worker, preserving the streamed evidence projection and existing geometry budgets/calculators. A pending request is not superseded by background revision observation. Filter changes cancel the old request; generation checks reject stale results. The captured request revision is published, allowing a later relevant-data refresh instead of marking stale results current.
- The last rendered map and camera remain visible and pannable while filtering. Filter-dependent road-summary taps wait for committed results. Normal filter/legend controls stay hidden until ready; a separate **Cancel/Reset** row remains available during an update. Cancel restores the last successfully displayed filter selection and its saved preferences; Reset can immediately reuse the already displayed All view.
- A **30-second deadline** resolves slow/blocked loads to an explicit error with **Retry/Reset**, including initial restored-filter loading. Failure retains the requested selection for Retry and suppresses the automatic observer retry loop. Rendering exceptions use the same recovery path. Pause/resume retains the request; destruction cancels it.
- Retained-map identity includes the filter selection as well as journey/day revision. Queue, preparation, rendering and total success/cancel/failure times are logged under `RoadprintsTiming`, with filter choices but no coordinates. No date-window/mode matching rule or permanent progress value changed.

### Focused UI and preserved welcome overlay

- Motorway/A-road completion cards use a **white untravelled graph track**, blue/green completion, prominent percentage and travelled/total distances. Map strokes remain blue/red and green/red. A1 and A1(M) remain independently attributed and measured.
- Edge-to-edge safe-area handling replaces double top insets; header width and balanced padding keep Close at the content edge. Existing spinners, skeletons, zoom, fit, cancellation/deadline and progressive geometry remain. The counted contributing-journeys action is prominent and retains shared Journey card formatting/navigation/pagination.
- B/local screens omit unsupported statistics cards and show **Recorded sections** with the map, zoom and counted whole-road journey action. **B/local section tapping is deferred and disabled in this release**: instructions/accessibility no longer promise it. Motorway/A-road section selection remains.
- The existing eligible Welcome back overlay, counts/status/actions and prepared-map dismissal remain. During a background refresh it still blocks map touches and hides map controls/recovery controls beneath it; dismissal exposes the current recovery row without reloading geometry.

### Exact provenance and validation

Implementation `8bc3d2b207fbf761fbf0b87cac57b963698c4202`; overlay/evidence refinement `ca3600775fa8c2b0d39cf26543b865b57ed82536`; exact final tested/packaged source **`e85a74ec9c9e6221223986d0a9ae3465a0de4a1c`**. PR #204 merged as `d9a3473175735c1440a037bdeac8335903100ca0`; one immutable pinned release request `a41d253a119c23ec5ccf71d167cf9749dacd142c`. The merge commit skips a redundant push check after successful exact-source PR checks; packaging repeats the required checks before assembly/distribution..

[Android checks 39301868730](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39301868730) passed unit tests and lint. [Signed packaging/Firebase 39302469538](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39302469538) passed **646 tests; zero failures/errors/skips**, lint and stable-key APK signature verification, then uploaded release **`0gmijfgpa0mgo`** and distributed it to existing testers. [Signed APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39302469538/artifacts/12211894019); [verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39302469538/artifacts/12212318780). Only one APK request/build was made..

Sixteen targeted regressions added: eleven filter lifecycle/recovery/summary-gating tests; repeated real route assembly for foot/date/combined/empty/All with unchanged saved-journey evidence; white-track/unchanged-values, A-road colour/selection and B/local map/action tests; and welcome-overlay touch/recovery gating while updating. Existing actual M2 geometry, A1/A1(M) identity, archive projection, navigation, achievement, collection and capture tests remain in the complete suite. One obsolete loading-copy assertion now checks the displayed recovery guidance.

### Device acceptance and limits

Beta 1 is **ready for device acceptance**, not claimed accepted from JVM tests. Install over 204 without clearing data. Verify On foot, Last 7 days, combined On foot + Last 7 days, no results, repeated Reset/Apply, Cancel, tab switches/return, saved-filter restart and failure/timeout Retry/Reset. Capture `RoadprintsTiming` queue/prepare/render/total lines on Pixel and Samsung, especially the original archive. Check M2/A-road card legibility/gestures/section journeys, B/local recorded maps/count/actions, saved-journey navigation, Welcome back dismissal, safe areas/small screens and retained scroll/camera; smoke-test capture, rematching, achievements and collections on the upgrade.

No connected device/emulator was available for this task. For performance comparison, use queue/prepare/total times for uncached filter loads and render/revisit logs for retained views; the cache-hit path has no preparation stage. B/local full reference distances/completion and section tapping remain unavailable. Existing two-road derived cache limits/local-boundary five-minute TTL and Android process-reclamation limits remain. The UI deadline bounds user waiting but cannot forcibly terminate an uninterruptible underlying operation; stale publication is blocked and recovery stays available. This does not erase or rematch any journey.

Code-review follow-up outside the focused road-detail path: a legacy settlement-overview child may recompute on return when the saved main-map selection is non-All, because its retained-selection field is not published by the separate settlement renderer. No journey/coverage loss is involved; verify and align that identity in a later small state-preservation follow-up. Main Map tab retention and the focused RoadDetail cache are covered here.

### NCN resumption handover — implementation remains paused

After this APK passes device acceptance, start app integration from this released source and the latest `fix/onboarding-start-tracking` descendant, preserving the filter recovery and shared UI. Read epic **#79**, implementation/geometry issues **#190–#194** and the existing catalogue/data PRs **#175–#187**; reuse their existing work instead of regenerating/auditing it in this slice. Reconcile the deferred **#172** trial adapter against already-integrated road-detail code; do not blindly restore its older #171 UI base. Next NCN work is the national open-way/source-provenance adapter, shared-way union, alternatives and mode filters described in those issues. Confirm current NCN issue/PR state at that future session; this release has not approved or published NCN data.


## Previous delivery — Beta 1 UI refinement (#201)

**Released 0.25.204 (250)** on 19 October 2026. [Install the UI update](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/6g411l4k07r7o). Built directly above the real-device-tested 0.25.203 stabilisation release. PR [#203](https://github.com/adamdbird-gfc/uk-road-tracker/pull/203) is limited to the six requested UI refinements. Existing map-filter, celebration, road-geometry and retained-tab fixes are preserved. No NCN, tracking, rematching, collection or coverage-calculation change was included; A1 and A1(M) retain independent identities and mileage.

### Exact changes

- **Welcome back:** the eligible discovery recap now overlays the prepared map with part of the map visible. Map controls and touch interactions remain unavailable until both map readiness and recap dismissal. Close, Back and navigation actions acknowledge only the displayed journey IDs, reveal the same prepared map and retain existing eligibility/count/distance/matching/action rules.
- **Close controls:** the shared Close control aligns visible text with the right content inset, retains a minimum 48dp touch height and is used in focused roads and shared dialogs. Focused-road safe-area padding and the achievement Close row are handled explicitly.
- **Progressive focused maps:** the map container appears immediately, with a spinner over the map and skeleton road statistics. Geometry and whole-road statistics appear before final contributing-journey metadata; the map stays visible during section lookups. Existing 30-second deadline, explicit failure/retry, generation checks and cancellation remain. A two-road bounded derived cache reuses unchanged journey revisions without any network I/O during cache lookup.
- **Zoom:** main and focused maps share the existing-style plus/minus controls, retaining initial road fitting, pinch, pan and section selection.
- **Road statistics:** a whole-road completion graph displays travelled/total distance and percentage using existing motorway blue/red and A-road green/red conventions. Selected mapped-section distance, travelled/remaining status and saved-journey frequency are separately labelled. B/local roads without a complete reference explicitly say whole-road completion is unavailable; no inferred coverage is awarded.
- **Contributing journeys:** the main Journeys and focused-road lists share date/time, mode badge and available distance/duration/GPS metadata formatting. Whole-road and section lists open the corresponding saved journey and load 30 visible cards per page without changing attribution.

### Commits and verification

Implementation commits: `086435977337dfd77b3777be484aa37645ebd355`, `ea0600e16a25010459fbdc11d6a7990e221794bd`, and the final cache-freshness correction below. Exact tested/packaged source **`bed7ef24bacb0d57b5caed676fde492563372510`**; PR #203 merged as `1a4dc2ceca87dc607c1a744bd32283bd96f2aa73`. One immutable release request `5804c64d4ad6895c06afa5dc6a08ffeef42357c0` pins that source and requests existing-tester distribution.

[Android checks 39280220062](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39280220062) passed unit tests and lint. [Packaging/distribution 39292861263](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39292861263) passed **630 tests; zero failures/errors/skips**, lint and stable-key APK signature verification, then uploaded and distributed Firebase release **`6g411l4k07r7o`** to existing testers successfully. Signed APK artifact [12206082307](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39292861263/artifacts/12206082307); verification reports [12207465039](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39292861263/artifacts/12207465039). GitHub previously reported the run queued during its Actions/Pages service incident; after recovery it exposes successful execution. The old queued checkpoint is superseded.

Thirteen new regression tests exercise recap dismissal/Back/navigation and captured IDs; map readiness and prepared-map reuse; shared Close positioning/action; immediate loading/skeleton/error-compatible UI; progressive geometry ahead of metadata; completion graph; shared contribution cards and saved-journey navigation; zoom; bounded pagination; separate section details; road/revision cache invalidation, eviction and unchanged reopening. Existing stabilisation regressions remain in the full suite.

### Acceptance and known limits

Keep #201 open for real-device UI acceptance. Install over 0.25.203 without clearing data; check recap dismissal/navigation and readiness, close alignment/safe areas, M2/A-road fit/zoom/section details, contribution navigation/long lists, small-screen layout and Map/Progress revisit state. JVM tests do not substitute for Pixel/Samsung rendering and gesture acceptance.

Numbered-road cache entries depend on exact road identity and saved-journey revision; local-road snapshots expire after five minutes because public boundary responses expose no revision token. At most two derived snapshots are retained; unusually large models are not cached (50,000 map points or 5,000 journeys). Android process reclamation can rebuild derived state. Selected-section distance describes the nearest rendered edge; frequency is explicitly the number of contributing saved journeys near it, not a count of passes within a journey. Existing B/local reference limitations and unavailable-router behaviour remain unchanged. The Work execution/usage timer is not exposed to the agent; reported elapsed time is wall clock.

## Previous delivery — Beta 1 stabilisation (#201)

**Released 0.25.203 (249)** on 19 October 2026. [Install update](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/6odqoda2eki6g). PR [#202](https://github.com/adamdbird-gfc/uk-road-tracker/pull/202) contains only the five reported Beta 1 repairs and targeted tests. Its code starts directly at installed source `5c8deea0a6237f484a865994e7148d51af4f1627`; no NCN or unrelated backlog feature was integrated. Existing journey archives, tracking state, collection ledgers, application ID and signing identity are preserved; no destructive migration or rematch was added.

### Exact changes

- **Map filter/readiness:** filter opening previously queued summary I/O behind archive-heavy screen work with no immediate UI response. It now opens synchronously with the complete set of filter choices, without an archive scan. Filter/reset, legend, zoom and recap controls stay hidden until road geometry is ready; map touch actions are blocked while loading. Failures retain retry and keep dependent controls hidden.
- **Celebrations:** the explicit action was silently gated by unrelated snapshot revision checks. It now reads the durable pending queue even while evidence refresh is queued. Repeated taps reuse the existing dialog; Next/explicit Close acknowledges only the presented milestone. Pause/system cancellation retains unacknowledged events. Automatic presentation retains its freshness gates.
- **Focused roads:** road detail previously shared the long-running background screen queue and had no finite deadline. A separate single interactive lane now loads streamed retained road evidence, with cancellation, generation checks, explicit errors/retry and a **30-second deadline**, including section-to-journey lookup. Revision changes yield retry rather than an endless reload loop. Existing canonical covered/remaining geometry and contributing journeys remain; the actual bundled GB M2 reference is exercised in regression tests.
- **Map loading:** `JourneyStore.forEachMapEvidence` streams displayed matched GeoJSON, motorway/A-road/cycling features, titles, filters and corrections without materialising raw capture arrays or upgrading every archive record. Legacy edge corrections retain their full-evidence fallback. Long summary scans no longer hold the global store monitor during disk I/O. Collection visits refresh pins independently instead of invalidating unchanged road geometry. Existing display budgets are unchanged. Location-free `RoadprintsTiming` logs separate queue, preparation, rendering and reused revisits.
- **Tab persistence:** `RoadprintsNavigation` reorders existing primary activities instead of finishing tabs or clearing those above Map. Child screens still close appropriately. Loaded views, map camera, Progress scroll and expensive process results survive ordinary tab switches; unchanged Progress avoids repeated enrichment setup and resumes its status ticker. Progress jump intents still reach retained screens.

### Commits and verification

- Repair commits: `585651dbceaf81390f3da27aff4b8a3163f4d3ae`, `d6834a76212c1172b526b990d3c9ccda6795fbc0`, and final exact tested/packaged source **`720d49921936c3ba590917ac4e5ae41dae444907`**. The middle commit reconciles only already-released documentation/request ancestry and navigation/deadline tests; no additional app features were imported.
- PR #202 merged to `fix/onboarding-start-tracking` as `200ece081a1df48c16d784bc455dc31cf54d804b`. Immutable release request **`709ecda50d2ed481531f41ca27915013881626e7`** pins the exact tested source and explicitly shares with existing Firebase testers.
- [Android checks 39272662958](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39272662958): complete unit suite and lint passed. [Packaging/distribution 39273170015](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39273170015) repeated **617 tests; zero failures/errors/skips**, lint and stable-key APK signature verification, then uploaded and distributed Firebase release **`6odqoda2eki6g`** successfully. One APK request only.
- Ten new regressions cover actual View clicks, stale-snapshot celebrations, cancellation/duplicate retention, blocked-worker isolation, finite timeout/failure/retry, real M2 covered/remaining geometry and contributing journeys, retained navigation, and exact displayed-geometry/correction fidelity. The synthetic raw-heavy record test verifies more than tenfold smaller projected JSON while retaining the original archive and all displayed geometry. Four inherited UI assertions were updated to the requested readiness/navigation behaviour; no production guard was removed to satisfy them.
- Local release-request tests: 9 passed; existing-tester discovery tests: 3 passed. Workspace has no Android SDK/Gradle/JDK compiler; full Android validation ran in Actions. Signed APK artifact [12196930985](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39273170015/artifacts/12196930985), verification reports [12196780946](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39273170015/artifacts/12196780946).

### Remaining acceptance and limits

Keep **#201 open for device acceptance**, along with #188/#194. No connected Pixel/Samsung was available: actual phone taps and before/after first-load/revisit timings are not claimed from JVM tests. Check filter readiness/apply/reset, both pending celebrations, M2 travelled/remaining strokes and section journeys, then Map → Progress → other tabs → return with camera/scroll retained. Verify upgrade over the existing installation without clearing data. Android may reclaim stopped activities/processes under memory pressure; recreation can rebuild derived results using existing caches/snapshots. B/local full-reference limitations and router availability are unchanged. Exceptionally slow/continually changing archives now offer retry after the bounded road deadline.

The coding and one-APK delivery slice is complete; the next step is testing this build, not another backlog/NCN integration session. The Work app's displayed execution/usage timer is not exposed to the agent; only wall-clock elapsed time can be reported.

## Previous delivery — Beta 1 / Beta 2 split

The user has authorised **Beta 1: core reliability, existing collection fixes and interactive Progress**, followed by **Beta 2: NCN collection and subsequent fixes**. This supersedes the earlier no-build hold, but packaging/distribution must wait for green integrated checks.

Active integration: `integration/beta-1-core`, based on PR #197 at `3dcf99b55925800c4e1417b90f932d3d56c8da11`. See [exact PR disposition, integration work, validation and device checklist](beta-1-integration.md). #163 is already reconciled in #197; no duplicate merge. #164/#168/#173, #169, #166/#171 and #167 are included. #172 and all NCN research/data PRs #165/#175–#187 are deferred. Original branches remain intact.

The combined slice adds independent post-arrival dwell observations for venue collections, replayable interrupted save/leg handoffs, armed checkpoint recovery, null-intent tracking resumption and atomic journey replacement. Existing saved rematching and raw evidence are preserved. Integrated Android unit tests and lint passed at `5c8deea0` ([run 39259997616](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39259997616)); PostgreSQL backend checks passed. Backend-only PR #199 is merged as `b1926f00`, verified live at API 0.12.0 with database ready. Original saved Warwick replay recovers Northbound only. Version 0.25.202 (248) and explicit existing-tester sharing are prepared. **Released Beta 1 0.25.202 (248):** exact source `5c8deea0a6237f484a865994e7148d51af4f1627`, immutable request `7738692e`, app integration merge `6a215c90`. [Packaging and Firebase run 39261229063](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39261229063) passed 607 tests (zero failures/errors/skips), lint and signature verification, then uploaded release `3ln17lg2bp3v8` and shared it with existing testers. [Install Beta 1](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/3ln17lg2bp3v8). No NCN collection or reference-data publication. A private live driving replay was blocked by automatic approval review; offline production preparation and deterministic complete-geometry regressions passed, with device acceptance still outstanding. No NCN data publication is authorised as part of Beta 1.

Next smallest core follow-up: skip linked-archive reads once the bounded arrival-observation window closes, then gather overnight/screen-off Pixel/Samsung evidence. Keep #188/#194 open. For Beta 2, reconcile #172 with the now-integrated road Progress, then implement national open-way/overlap/alternative coverage from #175–#187.

Prior #197 verification: full Android tests/lint passed at `ad3db3e8` in [run 39256415474](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39256415474). That branch-level result does not validate the new combined integration. Keep #188/#194 open for device acceptance.

## Start every ChatGPT / Work development session here

1. Read this document and [release integration issue #194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194) before modifying code or proposing a build.
2. Review current GitHub branch/PR state, CI, merge bases and changes since this document was last updated. Do not assume open PRs are merged, published or deployed.
3. For NCN work, read [epic #79](https://github.com/adamdbird-gfc/uk-road-tracker/issues/79) and child issues [#190](https://github.com/adamdbird-gfc/uk-road-tracker/issues/190), [#191](https://github.com/adamdbird-gfc/uk-road-tracker/issues/191), [#192](https://github.com/adamdbird-gfc/uk-road-tracker/issues/192), [#193](https://github.com/adamdbird-gfc/uk-road-tracker/issues/193) and [#194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194).
4. Check existing components and stacked PRs before implementing. Reuse established Progress, route-detail, journey-list, collection and navigation behaviours. Record decisions, findings, blockers and tests in the appropriate GitHub issue.
5. Classify outstanding PRs **include / defer / superseded** after reviewing their dependencies and overlaps. Do not blindly merge stacked or divergent branches.
6. Complete coherent feature slices and verify integrated tests before preparing **one coordinated beta APK / Firebase release**. No build or deployment merely to demonstrate a small UI decision. Obtain the user's approval for release when appropriate.

## Release scope and dependencies to assess

- **Service-station visit recognition:** [PR #169](https://github.com/adamdbird-gfc/uk-road-tracker/pull/169), mapped service-site footprints, genuine dwell versus passing traffic, saved Warwick Northbound visit recovery, older hosted catalogue compatibility. Real-device verification remains.
- **Place-aware capture:** [#163](https://github.com/adamdbird-gfc/uk-road-tracker/pull/163), [#164](https://github.com/adamdbird-gfc/uk-road-tracker/pull/164), [#170](https://github.com/adamdbird-gfc/uk-road-tracker/pull/170), [#182](https://github.com/adamdbird-gfc/uk-road-tracker/pull/182): stops at schools/businesses/residences versus continuing running/walking, stationary drift and validated arrival duration.
- **Driving geometry and journey quality:** [#173](https://github.com/adamdbird-gfc/uk-road-tracker/pull/173), [#174](https://github.com/adamdbird-gfc/uk-road-tracker/pull/174): correct routing, continuous map strokes and accurate travel mode.
- **Map/history/provenance:** [#166](https://github.com/adamdbird-gfc/uk-road-tracker/pull/166), [#167](https://github.com/adamdbird-gfc/uk-road-tracker/pull/167), [#168](https://github.com/adamdbird-gfc/uk-road-tracker/pull/168).
- **Shared interactive Progress:** [PR #171](https://github.com/adamdbird-gfc/uk-road-tracker/pull/171) contains focused motorway/A-road maps and contributing journey lists. It is open/unmerged as of this review.
- **NCN Android trial:** [PR #172](https://github.com/adamdbird-gfc/uk-road-tracker/pull/172) is an opt-in eight-route trial stacked on #171, **not** the final collection. It already reuses shared route-detail plumbing.
- **NCN national data:** draft stacked [PRs #175–#187](https://github.com/adamdbird-gfc/uk-road-tracker/pulls) culminate in 219 supported route identities, 34 evidence holds and shared physical ways. A production adapter must handle overlaps and alternatives rather than assuming the trial compiler supports them.
- Triage old open PRs #1, #6–#8 as potentially stale; do not auto-merge.

## Agreed NCN product direction (details in #192)

- Only verified **open** route sections count; preserve route identities; hold unsupported or planned sections.
- Walking/running and cycling contribute; driving does not. Combined completion is a **unique physical union**, not the sum of mode totals.
- Collection map: purple completed sections; muted grey dashed remaining eligible sections; temporarily subdue ordinary Roadprints road overlays, restoring prior state on exit.
- Route taps: verified identity, eligible distance, verified endpoints, mode-filtered completion and road-style contributing journeys. For shared geometry, offer every verified numbered route identity.
- Progress: National, Regional and Named route groups, collapsible like existing lists; numerical sort for numbered routes; search, Jump-to entry, route-focused maps and interactive charts.
- **NCN charts use signpost red**, distinct from **purple completed map strokes**. Motorway and A-road charts retain their existing blue/green conventions.
- Retrospective history assessment should be resumable, correction/deletion aware, and not flood users with review prompts. Purchases/paywall remain deferred for beta.

## Release readiness gates

- Review merge dependency graph and overlapping files; explicitly choose include/defer/superseded for each PR.
- For Beta 2 only: integrate and validate the full NCN source-way/provenance adapter, open-only exclusions, shared physical coverage, route alternatives and bounded memory/storage.
- Test service-station genuine visits vs drive-through; place-aware trip end/start; walking duration; continuous motorway geometry; saved journey edits/deletions; road maps and contributing journeys. NCN mode filtering/charts/route maps are Beta 2 gates.
- Check CI, licences/source provenance, versioned catalogue delivery, migrations, rollback and Firebase staging.
- Distribute Beta 1 only after green integrated automated checks, with explicit outstanding device scenarios. Real-device acceptance is a separate gate; do not claim it from automated tests.

## Session handover discipline

At the end of substantial work, update issue #194 with: branch/commit or PR, what changed, test evidence, what remains, blockers, dependencies and the next recommended slice. Update the relevant feature issue as well. Prefer links and verifiable state over claims based only on conversation history. Ordinary Chat and Work can both read GitHub, but new sessions should be explicitly directed to this file.

## Historical reliability checkpoint — superseded by the Beta 1 integration above

PR [#196](https://github.com/adamdbird-gfc/uk-road-tracker/pull/196), branch `fix/capture-mode-transition-evidence`, stacked on #182 (`13084738`). Code checkpoint: `726339649c824d1a69812cbf2f17ca54ffe282a7`. See [the reliability audit](tracking-reliability-audit-2026-10-19.md) for reviewed heads, include/defer decisions, file overlaps and outstanding work.

The new slice fixes early activity callbacks splitting at their timestamp instead of the start of GPS-supported mode movement. Stops/gaps cannot pool stale movement votes; the existing handoff preserves every measured edge with one shared endpoint. It does not resolve all of #188 or repair existing saved geometry.

Validation: 34 local JVM tests passed, including 11 new mode-boundary cases. Full Android unit tests and lint, including the new service-handoff regression, passed for code checkpoint `72633964` in [Actions run 39249993064](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39249993064). No version bump, APK build, Firebase deployment, merge or live-data publication.

Next recommended slice: reconcile #163 place-visit behaviour onto #170 → #174 → #182 plus this mode-boundary fix, with overnight home drift/morning departure and genuine venue-exercise service tests. Integrate #164/#173 backend changes separately and verify full-route geometry; retain #168 source evidence when NCN joins. #169 visits, #167 late saves and the map/Progress/NCN stacks remain release dependencies, not implemented again here. Keep #188/#194 open pending integrated tests and real-device acceptance.

Saved-journey requirement confirmed: completed journeys must remain explicitly rematchable with current preparation/matching, excluding supported drift from derived walking maps/distance while retaining raw evidence. See [saved-journey remapping](saved-journey-remapping.md) and the completed-old-match/failure-retention regressions in WalkingMatchingIntegrationTest. Automatic bulk migration and historical journey re-segmentation are not implemented; original route/user-edit and leg boundaries must be respected.

Saved-remapping follow-up validation: all Android unit tests and lint passed at `e3a752fd` in [Actions run 39252410302](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39252410302), including three new completed-walk/drift, failure-retention and completed-drive regressions. This verifies existing remapping behaviour; no additional runtime change, APK build or deployment.
