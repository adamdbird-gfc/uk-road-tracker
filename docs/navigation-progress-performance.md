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

Tests/build measurements pending. One coordinated private 218/264 APK after green
checks. No Firebase distribution, backend changes, historical rewrite/rematch,
cache purge or disabling necessary collection assessments.
