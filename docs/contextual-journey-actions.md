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
  unnecessarily changing NCN evidence revision. Genuine evidence changes still
  invalidate; source tokens/fingerprints/checksums and per-row caches remain.
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

Targeted and complete Android CI/lint, national references and private packaging
will be recorded in the handover after verification. One private 0.25.216 (262)
APK is planned after tests pass. No Firebase tester distribution, historical
rewrite/rematch, global cache invalidation or backend deployment.
