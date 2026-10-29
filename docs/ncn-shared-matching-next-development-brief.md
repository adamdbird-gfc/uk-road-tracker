# Next development: preserve shared matchers, correct ordinary NCN seams

29 October 2026. Proposed implementation task, **not implemented by this
investigation**. Baseline: latest validated main including `cc088b8`.
Android baseline: 0.25.220 (266). Target main with an open implementation PR.

## Purpose

Correct a bounded translation failure where an already identified physical path
continues across two ordinary NCN target edges. Keep the existing walking and
cycling journey matchers and interval ledger. The
[architectural assessment](ncn-shared-matching-feasibility.md) supports this
incremental hybrid approach; it does not establish a benefit from replacing NCN
accounting with sampled road percentages.

Reference PR #240's `docs/ncn-next-binding-correction-brief.md`, refined diagnostic
receipt and pinned fixtures at `21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7`.
Do not merge that PR implicitly or depend on its analysis code in runtime.
Reuse its fixtures as needed in the implementation PR with explicit provenance.

## Scope and implementation files

Change only the finite source-to-target compiler correspondence needed to
partition a proven ordinary continuation. `scripts/ncn_trial/open_completion.py`
(`bind_edges`, `edge_interval`) is the primary entrypoint. Preserve guards in
`alternatives.py` and `inventory_completion.py`; version receipts through
`completion_checkpoint.py`. Retain `build_ncn_national.py::compile_path_bindings`
full-way offsets. Generate a reproducible existing-schema candidate correction
through `scripts/ncn_binding_attainability.py`; never overwrite published data.
No changes anticipated to Android `NcnMatcher`, `NcnLedger` or `NcnAchievements`.

## Proof before national application

1. Reproduce NCN9 `59306268:4:27` and NCN8 `31252441:47:55` ordinary seams.
   Retain NCN81 `44807950` genuine-hairpin negative. Check source hashes/offsets.
2. Require exactly two continuing edges, degree two in the complete target
   graph, compatible outward directions and a source-supported interior seam
   anchor on both sides. Retain 10 m/15-degree gates and deferred masks.
3. Partition source evidence monotonically. Partial evidence may earn only its
   supported target portion. Never duplicate a whole source window across both
   targets, extrapolate endpoints or credit an untravelled connector/branch.
4. If these conditions cannot be demonstrated, retain the gap and document the
   specific blocker. Do not widen the package to other cause classes.

## Expected effect and protections

Diagnostic ceiling: 45,631.463 unique-network metres across 217 routes, at most
0.261721 percentage points (92.342135% to approximately 92.603857%). Actual gain
must be measured, can be smaller, and completes no route even at the ceiling.
RCN90 is an unrelated evidence-gap control; its seam ceiling is only 0.096 m.
NCN1 supplies a long/disconnected aggregate control (4,004.221 m route ceiling).

Risks: parallel road/path credit, branch or hairpin shortcuts, source ownership
overlap, partial-journey overcredit and stale catalogue caches. Preserve valid
geometry, access, route IDs/hashes, original evidence/corrections, history,
canonical network deduplication and combined 25/50/75/100 milestones. No road
calculator reuse, new matching tolerances or universal ledger migration.

## Validation and definition of done

Extend `test_open_completion.py` and existing offset/attainability tests with
positive finite continuations and negative branches, crossings, parallel paths,
hairpins, endpoints, deferred alternatives, changed hashes and partial/reverse
journeys. Prove old supported target intervals remain supported.

Run NCN research/reference and cycling-evidence suites; reproduce the **candidate**
and validate every target/network mapping. Recalculate all 218 route capacities
and the unique union; report actual additions separately from the ceiling.
Run the candidate-selected Android NCN/celebration suite, including foot/run/cycle,
driving exclusion, historical edits, deletion, repeats, thresholds, persistence
and cache invalidation. Record commands, hashes, counts and performance evidence;
do not infer success from the published-reference tests alone.

Done: bounded correction proven or precise failed cases retained, candidate and
regression evidence reviewable in an unmerged PR. No APK anticipated if the
existing schema passes the consumer suite. No publication/deployment/APK/device
acceptance is included; no screenshot collection needed. Stage 1.1 remains
partial. Do not begin other stages or a national catalogue rebuild.
