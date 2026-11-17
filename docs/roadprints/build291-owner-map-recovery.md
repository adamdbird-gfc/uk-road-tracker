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
- Rejects malformed cached interval arrays and oversized per-journey calculations instead of quietly accepting apparent no-coverage rows.
- Avoids incrementing successful `unresolvedMm/excludedMm` counters before decoding and validating a derived row.
- `MapActivity` treats total contribution-budget exhaustion as degraded coverage and prevents incomplete Map snapshots being cached.
- Adds invalid/truncated/out-of-range cached interval regression cases.

## Next Android pass (do not start from scratch)
1. Validate PR #326 atop latest PR #325. Run full Android JVM test suite and use current pinned reference asset; then replay real archive/seed fixture with diagnostic `hold_reasons`.
2. Capture the dominant failure signatures from `read_or_compute` vs `decode_contribution` and fix root cause rather than weakening saved matched-route acceptance or suppressing errors.
3. For a known successful accepted matched minor-road journey, prove `sections>0`, `lines>0`, `unique_mm>0` on the full pipeline, and prove no duplicate legacy line over a covered section.
4. Profile peak heap and archive-load responsiveness on near-production volumes. Do not introduce synchronous full-archive processing on UI.
5. Preserve useful **Road Summary** presentation, with per-journey contributors and correction actions. No user-facing technical section-ID dialogue.
6. Only after these checks, create signed same-app-ID in-place build 292 and direct GitHub Releases APK.

No new private owner exports required to understand these measurements. The 1,600+ archive on physical Pixel 10 remains the final acceptance test.
