# Build 291 owner acceptance: cumulative minor-road layer failed on Pixel 10

## Device evidence, 17 November 2026
Owner installed Roadprints 0.25.245 (291), then observed overlapping black legacy routes around Crete Hall Road / Rosherville Way. Tapping those traces opened the older Road Summary, with journey lists. **This does not demonstrate that new canonical black section taps work.**

User-shared debug report (build 291):
```
map_local_roads ms=4728 calculated=0 reused=2 held=648 budget_holds=0
  unresolved_mm=10478287 excluded_mm=0 sections=0 lines=0 unique_mm=0
map_total ms=8097
map_navigation ms=8076 source=archive
map_records ms=6047 reused=650 rebuilt=0
map_geometry ms=4733
map_coverage_finish ms=1640 sampled_heap_peak_mib=251 heap_limit_mib=256
map_render ms=19
map_filter total=1674 filtered=1011 unprocessed=13 shown=650
```

## Interpretation
- Of 650 displayed map records, 648 local-road contribution attempts were held, with only two reused rows. `budget_holds=0` means the explicit contribution-budget gate did not explain the holds.
- No canonical sections/lines emerged. The legacy black geometry remains visible; users perceive duplicate/fragmented lines.
- 251/256 MiB sampled heap peak is dangerously close to the limit and warrants controlled profiling, but **it does not prove OOM caused the holds**.
- `map_render=19 ms` shows the bulk of observed 8.1-second Map time was preprocessing, not final painting.
- Device diagnostics predate the post-release diagnostics/hold failure-breakdown patches on PR #325; the original exception signatures cannot be recovered from these aggregate counters.

## Recovery code (PR #326 stacked on PR #325)
- `LocalRoadCoverage` distinguishes `read_or_compute` failures from `decode_contribution` failures, grouping exception class and safe top package frame (without private paths/positions).
- Rejects malformed cached interval arrays and oversized per-journey calculations instead of quietly accepting apparent no-coverage rows. Malformed disposable contribution cache rows are invalidated, so the next refresh can recompute from the original journey.
- Avoids incrementing successful `unresolvedMm/excludedMm` counters before decoding and validating a derived row.
- `MapActivity` treats total contribution-budget exhaustion as degraded coverage and prevents incomplete Map snapshots being cached.
- Adds invalid/truncated/out-of-range cached interval regression cases.

## Overview presentation decision — implemented in recovery branch

At the owner's request, the existing overview Map is **network-only by default** while cumulative coverage is validated:

- Existing canonical covered/uncovered motorway and A-road layers remain present with their current colours and calculators.
- Only resolved cumulative minor-road sections render black. No individual historical journey, raw motorway trace, or ferry path is supplied to the overview renderer as a visual fallback.
- Individual journey detail screens, ferry recordings, corrections, archive and route data are untouched.
- Legacy per-journey polyline preparation and hidden geometry bounds preparation are bypassed in the overview worker. Performance improvement is expected, but **not yet benchmarked**.
- When attribution is held/degraded, subtitle explicitly states cumulative coverage is incomplete; when no minor sections are available it says so without claiming no travel occurred.
- The local-road tap continues to open the existing **Road Summary**, including journey contributors, rather than exposing a technical section screen. Existing A-road/motorway summary callbacks remain.
- Disk snapshot revision is bumped to avoid accidentally reusing the prior legacy geometry snapshot.
- New focused `MapActivityOverviewCoverageTest` checks status wording and degraded-cache protection.

**Important:** a nearly empty network-only Map on build 292 is a valid diagnostic of the still-unfixed matching problem, but **not acceptable as a completed feature**. Work must fix and validate the original `held=648` issue before declaring release success.

## Next Android pass (do not start from scratch)
1. Validate PR #326 atop latest PR #325. Run full Android JVM test suite and use current pinned reference asset; then replay real archive/seed fixture with diagnostic `hold_reasons`.
2. Capture the dominant failure signatures from `read_or_compute` vs `decode_contribution` and fix root cause rather than weakening saved matched-route acceptance or suppressing errors.
3. For a known successful accepted matched minor-road journey, prove `sections>0`, `lines>0`, `unique_mm>0` on the full pipeline, and prove no duplicate legacy line over a covered section.
4. Profile peak heap and archive-load responsiveness on near-production volumes. Do not introduce synchronous full-archive processing on UI.
5. Preserve useful **Road Summary** presentation, with per-journey contributors and correction actions. No user-facing technical section-ID dialogue.
6. Only after these checks, create signed same-app-ID in-place build 292 and direct GitHub Releases APK.

No new private owner exports required to understand these measurements. The 1,600+ archive on physical Pixel 10 remains the final acceptance test.

## Work recovery checkpoint — 17 November 2026

The remote recovery head was verified as 5f9ef9492643f05f4b1c19cec3d13bbf69041c79, with PR #325 head 82da5d37e20d85d5e610cf062c79c8b84494404d as an ancestor.

Before the workspace reset, the seven original exports were recovered privately and replayed through the compiled accepted-route projection against the full reference extracted from the published build 291 APK. Six completed matches produced a union of 752 physical sections and 9,502,919 mm. The 5 November pending journey contributed nothing. This reproduces the reported 9.503 km, with original saved corrections applied. It exercises attribution directly on the JVM. Android archive ingestion, rendering, contributor interaction and the 648 device holds remain unverified. Private exports and coordinates are excluded from this commit.

The downloaded build 291 APK had SHA-256 f2220aed2421f055300d33181d354731a5fca2edf7b2004a992c3a6c715e2960. Its signing certificate SHA-256 was verified as 5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031. The packaged road reference was 739,770,815 bytes.

A confirmed cache compatibility defect was found in the recovery implementation. MapActivity now writes only motorway and a_road prepared arrays, while MapJourneyStore still required the removed roads and motorways arrays when reading them. Every new network-only prepared row therefore rebuilt on a subsequent archive pass. The reader now validates the two arrays it consumes. Existing rows containing legacy arrays remain readable. A regression verifies warm reuse without source decoding or legacy arrays.

Overview expectations in LocalRoadCoverageTest and MultimodalFerryTest now follow the approved presentation. The latter also verifies that the saved ferry geometry remains available. These source changes require fresh execution of the Android JVM suite.

The interrupted environment lost its Gradle logs and Android runtime. No full-suite pass, integrated Android result, performance measurement or build 292 release is claimed. The cause of the original 648 held contributions remains open. No backend or Firebase changes, main integration or issue closures occurred.

The first durable GitHub run 44126507697 executed 1,087 JVM tests with six failures and 11 skips. All six failures were the NCN national/UI setup assertions requiring NCN_TEST_DATABASE. Its exact cache key was unavailable. Road and Map tests, including the new cache regression, passed in that run. The validation workflow now retrieves and verifies the content-addressed database from the existing published reference when the cache misses. This changes test preparation only.

The prepared Map reader also streams only motorway and a_road arrays from existing derived rows. It skips the legacy road geometry arrays before JSON object allocation, retaining existing headers and major-road contribution reuse. A regression checks this upgrade path. Fresh validation of this additional change is pending.

## Integrated runtime checkpoint and stream lifetime correction

Run 44128154805 passed 1,089 JVM tests with zero failures/errors and 11 skips. Its API 35 runtime processed 1,600 small synthetic journeys, producing one 17,603 mm section from all 1,599 driving contributors with no holds. Cold Map was 5,096 ms, warm disk-snapshot reopen 551 ms. Sampled heaps were 38 MiB cold and 11 MiB warm under a 192 MiB limit. These small repeated-route fixtures contain no major-road contributions and do not establish production archive performance or the original failure cause.

The prepared stream ownership correction is f42cc75dc6d618eba8cb3fb7f8256260b8f88825. Android's delegating AssetFileDescriptor.AutoCloseInputStream can become unreachable while its delegate channel remains in use. FileInputStream.finalize() calls its close override, closing the delegate. LocalRoadCoverage now retains and explicitly closes the outer stream. This mechanism requires native Android GC validation because Robolectric uses the host JVM's resource handling.

Build 292 validation expands the synthetic native journeys to 256 accepted matched vertices and 512 raw sample rows each, adds established M2 and A2 contribution fixtures, and sets the emulator Java heap to 256 MiB. A controlled signed comparison changes only LocalRoadCoverage back to 12d0b98 for the first runtime pass, requiring held contributions with a channel/read exception before testing the corrected owner upgrade. The validation then requires positive canonical coverage, all major reference categories, zero historical overview traces, archive preservation, warm reuse, Road Summary contributors and individual journey navigation. Its publication step runs only after all these checks pass. This candidate is not yet released.
