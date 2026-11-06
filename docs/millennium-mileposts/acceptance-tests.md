# Discovery and achievement acceptance-test matrix

Original planned matrix, not a claim of execution. Stage 10 owner-approved OSM acceptance replaces blanket independent-survey/date prerequisites; source identity/location approval is distinct from designer attribution. Locked cards use question marks, never silhouettes. See stage-10-production.md for actual execution and remaining device gates. Each row is a release acceptance criterion; use synthetic/private-free fixtures and independently calculated expected results. All thresholds apply to actual unrounded measurements.

| ID | Fixture / operation | Expected result | Gate |
| --- | --- | --- | --- |
| E01 | Verified post; continuous validated walking, running, cycling pass at 99.9m, 100.0m, 100.1m | First two qualify; third does not; no accuracy-radius addition | evaluator |
| E02 | Same tests in southern England, NI, northern Scotland/Shetland with oblique segments | Independent geodesic/local metric answer agrees at boundary; no fixed UK-wide longitude scale error | evaluator |
| E03 | Driving, bus, train, ferry, unknown mode; manual vehicle→cycling relabel without corroboration | No discoveries; reason recorded | evaluator |
| E04 | Stationary jitter cloud within 100m; stale/poor accuracy fixes; native stillness trimming | No drift awards even if processing is complete | evaluator |
| E05 | Two valid points separated by unsupported GPS gap crossing a post | No interpolated discovery | evaluator |
| E06 | Parallel whole road contains post but actual retained travel section does not | No discovery from full-road/bounding box | evaluator |
| E07 | User-drawn route, failed match, synthetic connector, removed segment or deleted road | No award from unsupported/removed geometry | evaluator |
| E08 | Supported historical import with locally validated mode/path; coarse Timeline interval/place record | First qualifies without live-only requirements; coarse record is insufficient, not fabricated | historical adapter |
| E09 | Sparse historical record later gains valid evidence | Idempotent re-evaluation creates one legitimate discovery; original archive unchanged | historical adapter |
| E10 | Same post, repeated passes/reimports, two source aliases | One physical discovery; no repeat designer/parent awards | persistence |
| E11 | Two verified physical posts very near each other | Two discoveries; no proximity-only merge | inventory |
| E12 | Verified unknown design; candidate-only post with design tag | First contributes to Harrow only; second contributes nothing | inventory/evaluator |
| E13 | Conflicting/unreliable position, stale missing-post observation | Held with reasons, no award until sufficient spatial/temporal evidence | inventory |
| E14 | UK coastal post outside simplified polygon; unverified exception; Crown Dependency/Republic of Ireland | Explicit verified UK exception allowed at true position; others excluded/held | inventory |
| E15 | Post moved with dated coordinates; old journey against new point | Match only supported location-validity interval; preserve physical identity | inventory |
| A01 | One verified post of each design in any region | Correct four designer individuals; no arbitrary geography assignment | achievement |
| A02 | Six posts; seventh elsewhere; four Wales + three South East | Harrow at seven nationally, no regional or route restriction | achievement |
| A03 | Eight or 100 distinct posts; repeated scans | Harrow once; count remains accurate; presentation stage capped seven | achievement |
| A04 | True eligible measurable NCN route 100%, displayed rounded near-100%, unmeasurable/held route | Only true complete eligible NCN route awards Grimshaw; use existing noise tolerance | NCN regression |
| A05 | No complete route but network cumulative distance high; complete RCN only | No Grimshaw under NCN-route condition | NCN regression |
| A06 | All six earned same batch; only five earned | Parent once in same stable transaction; no parent with five | achievement |
| A07 | First individual is Grimshaw; first post has unknown design and no individual earned | First reveals remaining anonymous question marks/parent; second remains spoiler-safe while discovery saved | UI |
| P01 | Seven unique discoveries in sequence | Exact six fixed themes then silhouette/name/biography; resolved position sets monotonically increase | presentation |
| P02 | Restart, device locale/time change, repeat visit, reordered input files | Same cipher per version; space fixed; duplicates do not advance | presentation |
| P03 | Older history arrives after stages viewed | Proof dates may update; saved ordinal/revealed letters do not shuffle | persistence/UI |
| P04 | Large Timeline import earns many collection milestones and all seven heritage awards | One summary; every earned state immediately available; no mandatory individual queue | bulk UI |
| P05 | Later, Skip, Back, rotation, process death during transition | Resume last completed static stage; skip affects presentation only; no duplicated summary | presentation |
| P06 | Replay completed Harrow repeatedly | No new discovery, achievement, earning date or source mutation | presentation |
| P07 | Active capture before opening; capture starts during reveal; activity resumes mid-capture | No celebration; pause/save immediately; capture continues; summary waits | capture regression |
| P08 | Reduced motion/animators disabled, TalkBack, large text, switch/keyboard navigation | Static equivalent, one stage announcement, no glyph chatter/spoilers, usable skip/replay and 48dp targets | accessibility |
| M01 | Orange undiscovered, purple discovered, mixed cluster | Shape/status supplement colour; mixed count explicit; drawing and hit targets agree | map |
| M02 | Dense national view, maximum zoom cluster, offline details | Bounded rendering; stable list expands; details and attribution available offline | map/performance |
| R01 | Crash before transaction, after transaction before summary, midway through assessment | Last complete snapshot remains; resume work; no partial parent/count or lost entitlement | durability |
| R02 | Journey correction/deletion during scan, catalogue changes mid-scan | Staged result rejected; affected proofs recomputed; alternative valid proof selected | durability |
| R03 | Corrupt/unknown manifest, hash mismatch, rollback, old app reads new schema | Known-good compatible catalogue retained; no destructive migration or silent false awards | compatibility |
| R04 | Disable feature then re-enable, downgrade app, reinstall without backup | Additive data retained on disable; compatible old app ignores it; uninstall recovery is only as good as existing backup, not promised cloud sync | compatibility |
| V01 | Existing 1,605×500-vertex archive; three full evaluations; 10× journey count; long single journey | No OOM/retained growth; bounded batches; same independent discovery results; no archive byte changes | memory |
| V02 | Unchanged 10× archive repeat, one journey updated | Zero unnecessary geometry decode on unchanged run; only changed records assessed; no full matcher rerun | incremental |
| V03 | Recording and map interactions while history scan runs | Meets device budgets; worker yields; no capture/ANR regression | device |
| V04 | Existing NCN thresholds/unions, core/landmark awards, correction tests and capture regressions | Existing outcomes unchanged; no route coverage generated from post proximity | regression |

Mandatory regression sets: `NcnAchievementThresholdTest`, relevant `NcnNationalTest`/ledger and reference transition tests; `AchievementCelebrationsTest`, `AchievementArchiveVolumeTest`, `AchievementGeometryMemoryTest`; existing stationary drift, walking fragmentation, driving recognition, phantom train and ferry/terminal cases. Run tests relevant to each implementation PR, then the integrated no-APK gate. Reference tests must restore exact pins, not redownload national catalogues. Physical-device acceptance on Pixel and Samsung is separate from JVM/CI success.

Proposed performance acceptance budgets (targets to measure in implementation, not observed results): initial cached screen usable <=500ms; p95 main-thread feature work <=8ms per frame on 60Hz device; no >100ms main-thread discovery task; evaluator temporary working set <=16MiB incremental to baseline and no >5% retained-heap rise across three identical warmed runs; current reveal assets <=8MiB decoded; workers cancellation/yield checked at least per journey and at <=100ms work slices for oversized journeys. Record total heap/PSS against 256MiB Java limit, sample counts, latency distribution and capture continuity. If a budget fails, profile and fix or report the measured blocker; do not silently relax it. Total historical scan time depends on archive/device and must be measured at 1,605 and 16,050 journeys rather than promised here.
