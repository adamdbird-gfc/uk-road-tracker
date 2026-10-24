# Contextual journey actions and diagnostics — 24 October 2026

Baseline: latest fetched Android development head `0c48f84e` on
`fix/generalised-journey-intelligence` (PR #218, delivered 0.25.215/261).
Main's latest commit is documentation-only `bdb26305`; production walking
backend remains the already deployed #217 slice. No backend changes here.

## Confirmed causes and changes

- Journey detail always added Review Ferry Split. Visibility now requires the
  existing sustained, precise marine movement/transition evidence, or a valid
  preserved split journal for Restore Original Recording. Neither mode, place
  name, terminal proximity nor a stored confidence label alone qualifies.
  Supported partial marine recordings remain reviewable without enabling an
  unsupported split. Existing detector scope (verified Dover–Calais corridor)
  is unchanged; this fix does not claim global ferry recognition.
- All primary journey action buttons use the same 8dp top margin, including
  ferry review/restore. No button reserves space when absent.
- An unmatched recorded preview could draw across a gap even though matching
  was blocked. Aligned timing now produces separate supported display sections,
  omitting intervals over two minutes, invalid fixes and reversed timestamps.
  Original arrays, history and GPS metadata are untouched. Recorded sections
  do not enable discovery replay or imply a successful road match.
- Shared capture already retains raw samples, reports moving gaps, sets saved
  unresolved distance to zero and blocks native full matching. The reported
  888,224ms interval (14m48.224s) is reproduced deterministically for validator,
  preview and process-recovery tests. No new matching exception is introduced.
- Warm NCN evidence changes now go straight to per-source incremental cache
  assessment instead of attempting a checkpoint already known stale. Cold
  checkpoint misses report revision values and incremental recovery explicitly.
  Missing input-hash metadata now compares the existing source evidence before
  replacement, preventing label-only saves after eviction/upgrade from
  unnecessarily changing NCN evidence revision. Unfinished foot/cycle retry
  states share the same zero-coverage identity; pending/processing/failed and
  retry telemetry no longer trigger reassessment. Completed-result transitions,
  corrections and real source changes still invalidate. Existing input hashes
  are lazily refreshed on a later individual save, with no global migration;
  source tokens/fingerprints/checksums and per-row caches remain.
- Slow-frame reports now separate tile, road, NCN and other draw time and count
  road sections. Empty route views avoid an unnecessary offscreen alpha layer.
  Projection limits, incremental map contributions, NCN caches and camera state
  remain unchanged. No speculative blanket geometry/cache rewrite.

## Evidence limits

The newly referenced movement/performance export was not attached in this turn
and no newer diagnostic JSON/text appeared in the connected recent file list.
The reported duration is therefore a synthetic exact-duration regression, not
an attributed provider/root-cause analysis of the original missing interval.
Existing original Disneyland fixtures remain unchanged. Updated draw counters
support diagnosis on ordinary future map use; CI timing is not phone frame proof.

## Validation and delivery

Final implementation `3606d591a402c7c7e09a13b79ee5113dba783d89` passed
complete Android compilation/lint, national references and all unit regressions
in [run 40134680370](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40134680370).
No tests excluded. **Delivered private 0.25.216 (262), PR #219.** Exact signed source
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
