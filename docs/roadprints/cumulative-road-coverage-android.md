# Cumulative road coverage in the Android overview

The overview attributes successful saved Driving and Bus matches, including Google Timeline imports. The saved matched legs are processed in editor order. Exact saved edge exclusions and legacy road identifiers retain their existing scope. A canonical interval resolved for an excluded leg is withdrawn from that journey's contribution, while independent journeys keep their credit. Removed legs are never bridged. Failed, pending and malformed matches do not earn coverage. Low match confidence and sparse source observations are not eligibility gates. The original observations remain available in journey diagnostics.

Build 290 (0.25.244), signed with the established owner certificate, came from [PR #320](https://github.com/adamdbird-gfc/uk-road-tracker/pull/320) at `8f852752ad2fe00c899d7a614f62bc415c738f14`. The integration branch combines that source and [PR #324](https://github.com/adamdbird-gfc/uk-road-tracker/pull/324) at `b6583371574fb478954a3eb8ede56f5be323ca8c`, and targets `main` at `c4ed7fc96cc36dab7437cf6c7272594e6432c5ea`. Build 291 bundles the same established assets from build 290. This change requires no live backend or Firebase publication.

## Reference and local cache

The release workflow builds a seekable, read-only reference from the pinned 16 November 2026 Great Britain and Ireland/Northern Ireland extracts. Source and geometry are from [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) under the ODbL via [Geofabrik](https://download.geofabrik.de/europe.html). Each section is an OSM way and its two adjacent node IDs. Direction, class, label and node geometry come from the snapshot. A road edit in OpenStreetMap may change its identity in a later snapshot. The cache namespace contains the complete input fingerprint and attribution algorithm version so intervals from different snapshots cannot be combined.

The reader retains up to eight 0.02-degree tiles. Attribution requires the route's heading, spatial fit and a connected chain across tile or reference edge boundaries. Materially competing alignments stay in the journey overlay without earning canonical credit. A-roads, trunk roads and motorways keep their existing presentation and progression calculations. Black minor-road lines replace only attributable portions of the saved route. The existing Map filters apply before aggregation. Tap a canonical line to open Road Summary for its road. The card includes confirmed contributors at the selected point and the road’s matched journey history.

The Map prepares coverage on its archive worker and commits per-journey derived rows in batches of 25. Atomic journey-file changes, including an owner correction or restoration, invalidate the corresponding row. The versioned Map snapshot also follows the archive geometry revision and APK version. Contributions have processing bounds, and the Map keeps the legacy view if a reference or contribution is unavailable. Diagnostics report unavailable reference, reused rows, held rows, unresolved metres and rendering size. The worker batches the canonical overlay into simple draw calls without allocating a Path for every road section.

## Seven private exports

The executable accepted-route attribution was run against the seven original build 290 exports. The production attribution code was replayed on the 100,103-tile release reference, version `858c943ffe1bbc3295cdbcbbbd8703deb08a4f6e9354723d887cbd901028e3c9`. The result is identical to the Kent extract replay. No raw samples, coordinates or archive IDs are included. “Already represented” compares each journey's own unique minor-road intervals to earlier journeys. “Eligible” is the length of the corrected saved match, which can include multiple traversals within one trip and major or non-creditable roads.

| Journey | Eligible m | New minor-road m | Already represented m | Unique minor-road m | Excluded m | Unresolved m |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 24 Sep evening | 3,895.597 | 3,368.812 | 0 | 3,368.812 | 0 | 0 |
| 1 Oct outward | 4,084.842 | 227.897 | 3,338.862 | 3,596.709 | 0 | 0 |
| 1 Oct return | 2,055.047 | 1,934.105 | 0 | 5,530.814 | 0 | 0 |
| 8 Oct | 3,697.886 | 1,542.419 | 1,111.454 | 7,073.233 | 0 | 0 |
| 15 Oct outward | 3,981.246 | 159.978 | 3,261.826 | 7,233.211 | 46.270 | 0 |
| 15 Oct return | 6,188.930 | 2,269.708 | 725.619 | 9,502.919 | 0 | 0 |
| 5 Nov return | 0 | 0 | 0 | 9,502.919 | 0 | 0 |

The 5 November export was pending and contained no stored successful match. Of the final cumulative coverage, 4,809.711 m have one contributing journey, 1,460.788 m have two, 2,720.287 m have three and 512.133 m have four. The replay measures attribution to the versioned road reference. Whether a specific historic match accurately reflects the drive remains subject to review against original evidence. The separate disputed historical case in [#322](https://github.com/adamdbird-gfc/uk-road-tracker/issues/322) remains open.

## Archive workload measurement

A host JVM projected a 1,600-journey workload formed by repeating the six successful private matches. It calculated 329,593 contribution intervals in 3.517 seconds and aggregated them to the same 9,502.919 unique metres in 0.377 seconds. Android stores each journey's section endpoints in compact integer arrays to retain repeated contributors. The host workload tests attribution and interval union. Android archive I/O, rendering and Pixel 10 heap use have separate checks.

## Owner release validation

[Issue #323](https://github.com/adamdbird-gfc/uk-road-tracker/issues/323) tracks the unified Map and [issue #321](https://github.com/adamdbird-gfc/uk-road-tracker/issues/321) tracks correction precision. The GitHub workflow gates release on Android regressions, exact asset and signing checks, and an API 35 emulator upgrade seeded with accepted native and reverse Timeline routes. The Pixel 10 archive and physical-device responsiveness require owner acceptance.

The build 291 Pixel 10 check showed parallel black lines beside the training venue and returned the legacy Road Summary from the reported taps. A replay of the seven exports found four overlapping contributions to the same Rosherville Way section. A follow-up gives the cumulative line priority within its visible stroke and uses its road identity to open the whole-road summary. Attribution and duplicate rendering on the complete Pixel archive remain under investigation.
