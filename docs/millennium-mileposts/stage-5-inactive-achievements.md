# Stage 5 — inactive achievement derivation

Date: 6 November 2026. Related #288 and #284; #283 Timeline recovery and #286 Utilities portability remain deferred.

Starting main: `064b076978ddbe964c9bf87ea965b744206b2d97`, independently verified merged PR #287. Branch: `feat/milepost-inactive-achievements-20261106`, targeting main in PR #289. Final head, ending main, CI totals and final performance receipt are recorded in the PR. No merge is authorised in this session.

## Scope and architecture

`MilepostAchievementDeriver` is an explicit worker-only coordinator; no existing job, activity, lifecycle, achievement, map or celebration calls it. It reuses `MilepostReconciler`, the Stage 3/4 SQLite database and the already-published `NcnProgressStore` snapshot. It never calls the general achievement calculator, retains journey geometry, starts NCN processing, approves candidates, or writes existing entitlement preferences. There is no new service, scheduler, backend, screen or production migration.

`MilepostAchievements` derives exactly seven compact states. Stage 5 adds no definitions to `AchievementStore`; its definition registry was checked for all seven IDs without collision. Existing NCN/venue ID namespaces also differ. All existing achievement publication remains independent.

| Stable ID | Qualification |
| --- | --- |
| millennium-jon-mills | One eligible canonical physical discovery with verified Mills attribution |
| millennium-andrew-rowe | One eligible canonical physical discovery with verified Rowe attribution |
| millennium-iain-mccoll | One eligible canonical physical discovery with verified McColl attribution |
| millennium-david-dudgeon | One eligible canonical physical discovery with verified Dudgeon attribution |
| millennium-john-grimshaw | One positive-length measurable `NCN:` route at true level 4 under existing combined coverage |
| millennium-charlie-harrow | Seven distinct eligible canonical physical discoveries anywhere in the UK |
| millennium-in-honour-of-time | All six individual states currently earned |

No regions, quotas, personality geography or extra achievements. A verified designer post contributes to both its designer and Harrow. Unknown/unverified design contributes only to Harrow when physical eligibility is independently approved. Alias ownership remains in the Stage 3 identity registry. All 889 actual candidate records remain unchanged and ineligible; only isolated test fixtures receive synthetic approval.

## Trust and consistency

A new coordinator starts both archive keysets from the beginning and records `JourneyStore.dataRevision`. It runs bounded Stage 4 slices, including deletion detection, until a complete pass and assessment queue are finished. Changing archive revision restarts the freshness pass. Restarted processes repeat the compact metadata sweep while retaining completed proof pages; no previous partial cursor is accepted as a whole-archive freshness certificate.

Pending/insufficient evidence is a completed non-qualifying assessment, not a reason to invent a proof or block unrelated trustworthy discoveries indefinitely. Queued source pages cannot publish an achievement result. Positive facts must match the current source token/version, nondeleted completed source, current approved physical catalogue, evaluator, qualifying reason, supported mode and nonnull provenance/validator/event fields. Alternate proofs stay in SQLite. Selected facts are earliest supported event, journey ID, section and physical ID order.

Schema 3 adds verified designer fields (unknown/unverified by default), `derivation_input`, `achievement_state`, and `achievement_snapshot`. SQL triggers advance a monotonic generation for changes to catalogue, marker eligibility/attribution, source versions, proofs, archive stamps and evaluator. A prepare/commit generation check rejects mixed input revisions. Catalogue fingerprints include attribution, preventing an unchanged pin from changing designer credit. A schema-1/2 fingerprint predating attribution requires a newer reviewed catalogue revision for a subsequent install; old proofs/recognition survive the additive upgrade. No real user database was opened or migrated.

The existing NCN publisher is the authority for completed snapshot membership. Derivation requires NCN enabled, a published current snapshot, and matching archive and NCN-evidence revisions. It reads only existing combined route totals via `NcnAchievements.percent`, including its unchanged arithmetic-noise tolerance. Rounded display percentages, RCN routes, nonpositive and unmeasurable routes do not qualify. No denominator, source binding, coverage, reference data or NCN history changes.

Before publishing, Stage 5 obtains the SQLite writer, then NCN publication monitor, then archive monitor. It rechecks archive revision, NCN snapshot object/revision/enabled state, NCN evidence revision, ledger generation and catalogue pin, and commits all seven states plus input receipt together. SQLite acquisition never waits while holding the capture archive monitor. Cancellation before/during publication rolls back all rows. No geometry or NCN assessment runs under these monitors. The previous complete result remains on disk while updating; the diagnostic reader returns `current=false/status=updating` for changed inputs. These diagnostic rows are not live entitlements.

## Recognition and historical dates

Current eligibility and first recognition are separate. Every state retains its original first-recognition time, ordinal, first event/precision and current selected compact support references. First recognition ties in one batch use fixed ID order, with parent last. Reprocessing cannot create duplicate rows or change those recognition ordinals. Corrections, deletions and catalogue/evaluator changes withdraw current qualification on the next complete stable derivation; alternate valid proofs can preserve it. Reinstatement retains first recognition. Later historical evidence updates supported current event dates without reshuffling recognition.

Designer dates are the earliest supported discovery date. Harrow's date is the seventh earliest distinct qualifying physical discovery. Current native ledger precision is `journey`; this never fabricates an exact passing time. The NCN snapshot has no independently established historical completion event date. Grimshaw therefore records `event_date=null/precision=unknown`, with route, catalogue and snapshot revisions as proof. The parent date is unknown when any individual date is unknown. Recognition time is never substituted for an unknown historical completion date. Future historically validated adapters can improve date precision separately.

## Memory investigation and limits

Stage 4's approximately 218 MiB sampled process peak is a live risk, not a demonstrated retained geometry leak. Its original pre-run/after-GC values were approximately 80.5/80.8 MiB; the first Stage 5 CI rerun again peaked around 219 MiB in the Stage 4 volume case and retained around 80.8 MiB. This supports substantial transient allocation/framework overhead in that workload; sampled heap alone does not identify the allocating classes or establish ART safety. No Stage 4 peak-memory fix or physical-device acceptance is claimed.

Stage 5 pages at most 16 compact facts, keeps four designer proofs and seven earliest physical proofs, and writes seven states with bounded references. Its largest supported catalogue is 4,096 markers. It never calls `JourneyStore.all`, copies the NCN activity ledger, or retains archive-wide source/geometry graphs. The workload creates 4,096 synthetic eligible discoveries, 1,605 source identities and 8,192 compact alternative proofs, then measures five warm derivations. Peak and after-GC retained memory are reported separately. Validated implementation head `e1f2f8ed9c190c068ac0f7f27c37090726b38e0d` measured 15,133,632 bytes incremental sampled peak and 60,016 bytes retained growth in 240 ms for five derivations. Pre-run/peak/after-GC heap were 84,134,696 / 99,268,328 / 84,194,712 bytes. SQLite size was 4,702,208 bytes. Final delivery-head measurements in the PR supersede this run if different.

The 2 ms sampler measures whole JVM heap including Robolectric/SQLite/framework/test caches and gives only a lower bound on peak. Warm workload totals include compact queries and publication, excluding fixture generation, source reconciliation and NCN assessment. One initial preparation precedes measurement to separate classloading/setup. This is not a cold phone benchmark, allocation attribution, native SQLite/PSS measurement or battery result. Workstation regression budgets are <64 MiB incremental sampled peak and <8 MiB retained growth, not promised device headroom.

The coordinator rejects UI-thread calls, sets background thread priority, polls a caller cancellation/deadline supplier and latches capture starts with the existing guard. No scheduling is activated. Synchronous SQLite/individual query work remains cooperatively cancellable rather than hard-preemptible. Device/ART heap and PSS, repeated jobs alongside Achievements/map, capture latency and power-loss durability remain acceptance gates. A deadline-limited invocation may retry the compact preparation after cancellation; proof pages survive.

## Validation and delivery boundaries

The focused no-APK workflow compiles app/unit tests, runs lint, isolates Android tests from external networking under a 256 MiB test heap, runs the retained separate 64 MiB streaming probe, and validates the 27 catalogue/cache tests. Stage 5 tests exercise all rules, synthetic UK regions, aliases/duplicates, rounded versus true NCN completion and combined modes, unknown attribution, real candidate ineligibility, repeat processing, backfill, correction/deletion/fallback/reinstatement, stale NCN, incomplete ledger, input-generation rejection, rollback/restart, concurrent real archive save, NCN replacement during preparation, partial scans and capture priority. Existing schema, ledger, NCN, achievement volume, Timeline, walking, train, ferry and transition regressions remain included.

An initial run passed compilation/lint and 251/252 tests; the sole failure was a legacy migration fixture that relabelled schema 3 as schema 1 without removing Stage 5 columns. The fixture now reconstructs actual schema 1 and schema 2 shapes before checking additive upgrades and preserved recognition. Run [42190496539](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/42190496539) passed compilation/lint, **257 Android tests (zero failures/errors/skips)** and **27 catalogue tests**. The separate streaming probe processed 1,605 journeys/802,500 edges in 2.905 seconds with 1,458,208 retained bytes under a 64 MiB heap. Its report ZIP SHA-256 was `f166a59e0d371ddd316718383e2ec363cfb285f8c3066c2981890dce3f4022a3`. Three unrelated historical branch-gated workflows skipped as configured; these are not skipped tests. Final delivery-head CI and report totals are authoritative in the PR.

Android source stays 0.25.223 (269). Installed APK/source provenance, live backend SHA and device-active reference pins are unknown in this session. No APK packaging/distribution, backend/Firebase deployment, live reference publication, production migration, UI change, entitlement activation or issue closure.

## Recommended Stage 6

Build inactive presentation/readiness adapters over the versioned seven-state receipt: spoiler-safe hidden states, consolidated bulk summaries, separate presentation acknowledgement/replay state, stable recognition order and historical-date precision labels. Exercise capture interruption, restart and reduced-motion/accessibility behaviour with placeholders. Prepare the six biographies/art rights and silhouette/reveal assets as a separate reviewed deliverable. Keep reference approval/publication, production scheduling, device performance acceptance and activation explicitly gated; 889 candidates still provide zero approved opportunities. Do not fold #283 or #286 into this stage. No live entitlement switch is justified by workstation Stage 5 results alone.
