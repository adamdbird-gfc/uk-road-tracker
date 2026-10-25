# User-visible navigation performance — 25 October 2026

Baseline: delivered private 217/263, #220, latest Android head `4f6ef175`.
The supplied phone trace identifies road drawing (456/466ms, 3,565 sections),
not NCN drawing (0ms). Wetherspoons selection does not imply that background NCN
assessment is disabled; the existing hidden-overlay early return remains intact.

## Targeted changes

- Journeys: first 12 reliable recent-saved summaries can appear before full
  chronological preparation. This provisional list is explicitly labelled;
  full counts and ordering follow. Compact per-source SQLite rows replace many
  gzip reads/writes after archive changes. Optional full-snapshot fsync no longer
  precedes publication. Summary work has its own executor. Visible cards retain
  identity; unchanged returns skip rendering, and sort timestamps are reused.
- Progress: per-source compact contributions retain exact coverage unions,
  canonical road reference fingerprints, road discovery and summary totals.
  Changed/deleted sources affect their derived rows, never a full-history reset.
  No raw GPS arrays are materialised by routine Progress preparation.
  Road groups bind an initial 30 rows, with Show more retaining access to all.
  Closed counties do not instantiate all town cards. Unopened Local roads are
  not repeatedly rebound by secondary settlement enrichment callbacks.
- Focused maps: current Progress reference/coverage geometry can be displayed
  before contributor lookup. Motorways, A roads and recorded B roads share the
  prepared evidence; Local road boundaries still require verified clipping.
  Map containers/zoom controls are immediate; useful geometry and final readiness
  are separate milestones. Focused evidence and eight bounded model snapshots
  are reused; navigation back retains the viewport.
- Main map: immutable world paths are projected on the map worker, reused across
  redraws/camera changes, transformed to screen coordinates and viewport-culled.
  Bounded caches retain original section boundaries and paint colours. Mutable
  editing paths bypass reuse. Coverage calculations and matching rules remain.
- Diagnostics: `nav_journeys_*`, `nav_progress_*`, `nav_road_type_*`,
  `nav_focused_*`, journey-detail/return timings and archive/bind/cache stages.
  First useful content is measured on draw, independently from container/skeleton
  and full ready. Cold/warm/back labels and existing UI-stall reporting remain.

## Validation and limits

A 1,577-journey synthetic archive exercises first summary display, real list
binding/drawing, full-vs-paged road list construction, Progress cold/warm/update
and focused geometry before contributor readiness. It retains raw evidence and
asserts progress equality. JVM/Robolectric measurements are not Pixel timing.
The full-inflation and 40-card binding baselines reproduce old display workload;
they are not a physical before/after device run. Actual device navigation-to-draw
milestones are included for natural future comparisons. No acceptance claim
based solely on internal map preparation.

Implementation `9f1d8934ab08f9efb8c6f3c328962761f8645f8d` passed complete
Android compile/lint, national references and unit tests in
[run 40208792527](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40208792527).
The preceding bounded-rendering run 40208365345 also passed. Initial tests caught
a missing-distance NaN fallback; fixed in shared processing. Existing zoom/cache
and queue-drain tests were updated for intended controls/capacity/executor
behaviour; no tests were excluded. Additional tests protect first-bind badges,
hidden-overlay isolation and summary queue independence.

## Delivered private 0.25.218 (264)

Signed source `60fe5e5408b4ca03f04dff03e9926d9ea225bd71`, PR #221.
[Private packaging 40209370261](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40209370261)
and independent exact-source Android checks 40209376378 succeeded.
Downloaded XML: **797 tests / 106 suites, zero failures, errors or skips**.
Lint, pinned national references, distribution controls and established v2
certificate passed. APK and ZIP hashes independently match CI.
APK: 36,606,773 bytes, SHA-256
`a514e61d1a7fb6bcec3eedcfc44b237fd452c3b5a9ec6b96e5ac2ae89ffd8863`.
[APK artifact 12604584815](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40209370261/artifacts/12604584815),
[verification reports 12604879417](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40209370261/artifacts/12604879417).
Private owner download provided separately. Install over 217 without clearing data.

### Measured display workloads

Same Robolectric runner, 1,577 synthetic journeys. These measure real view
binding/drawing and the former workload reproduced in current widgets; they
are not an old-APK versus new-APK device experiment. First useful content can
be a smaller list or geometry before contributor enrichment.

| Workload | Full/previous workload | First useful workload |
| --- | ---: | ---: |
| Journeys cards: bind + draw | 40 cards: 235.94 ms | 12 cards: 69.23 ms |
| Road rows: bind + draw | 1,577 rows: 1,649.00 ms | 30 rows: 20.40 ms |
| Focused map | Full contributor readiness: 1,168.48 ms | Prepared Progress geometry + draw: 7.49 ms |
| Road rendering: 3,565 sections | Legacy projection + draw: 1,621.50 ms | First cached draw: 135.30 ms; warm: 112.56 ms |

Recent-12 summaries read: 72.82 ms; all summaries: 490.88 ms. Progress cold:
1,653.17 ms, warm: 351.18 ms, one addition: 422.08 ms. The update assertion
confirms one source rebuilt / 1,576 reused; zero full-history invalidations.
Road preparation took 13.70 ms on the worker, with zero UI Path builds and
3,505 offscreen sections culled per draw. Spatial/Path caches are bounded.
The first-useful focused-map measurement assumes current Progress geometry;
Local roads retain verified-boundary requirements. It is not a claim that every
focused map or every road type opens in 7.49 ms.

The supplied phone baseline (5–8 s main-map load, 456 ms road drawing in a
466 ms frame) is not directly comparable with JVM results. New cold/warm/back,
first-content/draw/ready and CPU/cache diagnostics enable phone comparisons
through ordinary use. No physical-device before/after timings were available
in this environment; natural observation remains necessary. Existing history,
map interaction, UK coverage, collection switching and incremental NCN tests
pass. NCN-hidden/Wetherspoons-selected isolation is explicitly tested.

No Firebase distribution, backend change, historical rewrite/rematch, cache
purge or disabling necessary collection assessments. Later documentation-only
commits do not change this APK.
