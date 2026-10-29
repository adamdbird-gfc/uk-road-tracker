# NCN next correction — bracketed ordinary target seams

Date: 29 October 2026. Type: **next focused development brief; no fix implemented here**.
Baseline: latest validated main, including PR #239 integration
`cc088b828982a3ff9399e2bd930978ce02ecba4a`. Android: **0.25.220 (266)**.
Planning reference: PR #238, still separate. Scope remains Stage 1.1 only.

## Objective and evidence

Resolve false ambiguity caused by segmentation at an ordinary target vertex,
using already admitted source evidence. Preserve genuine alternative paths.
The production binder currently rejects every source interval with more than one
candidate target edge. That includes some two-edge continuations, even where
source evidence passes across their shared vertex and both candidates pass the
existing distance/direction gates.

The [national diagnostic receipt](data-trials/ncn-ordinary-seam-opportunities-2026-10-29.json)
finds **159,378 target intervals across 217 routes**, potentially affecting
**45,611.814 route-local metres / 45,631.463 unique-network metres** after excluding
branches, hairpins, parallel returns, deferred paths and unbracketed source fixes.
These are **upper bounds**, not approved extra credit or a promised outcome.
Even the ceiling would raise network assessability only from 92.342135% to about
92.603857%; **no route would become fully assessable from this package alone**.
NCN1's ceiling is 4,004.221 m; RCN:90's is only 0.096 m, leaving its main evidence
gap untouched. Do not describe this as completion of Stage 1.1.

This is the best bounded existing-evidence correction opportunity, rather than
the largest missing-data class. The latter has no admitted correspondence and
requires additional exact source evidence.

## Implementation entrypoints

- `scripts/ncn_trial/open_completion.py`: `bind_edges`, finite `edge_interval`
  policy and explicit deferred masks. Extend correspondence at a proven ordinary
  seam; do not globally change the 10 m / 15-degree gates.
- `scripts/ncn_trial/alternatives.py` and `inventory_completion.py`: preserve
  source-interval ownership, partial evidence, mode permissions and overlap guards.
- `scripts/ncn_trial/completion_checkpoint.py`: immutable admitted inputs and
  versioned binding receipts. Preserve old pinned readers and checkpoint hashes.
- `scripts/build_ncn_national.py`: retain the integrated full-way source-offset
  correction and exact source occurrence checks.
- `scripts/ncn_binding_attainability.py`: apply/reproduce a candidate correction
  artifact and measure route/network capacity; do not overwrite published data.
- Existing Android consumer `NcnMatcher`, `NcnLedger`, `NcnAchievements` should
  remain unchanged. An existing-schema data correction is anticipated; confirm
  through the candidate consumer suite rather than assuming compatibility.

## Safe sequence

1. Reproduce one pinned ordinary witness, first in a controlled fixture and then
   with its actual source section. Establish the source-local/full-way offsets,
   two target edges, shared vertex and the currently rejected interval.
2. Design and test an interior seam correspondence supported by the actual
   admitted source interval on both sides. Only two continuing edges may qualify;
   their common vertex must have degree two in the complete target graph, opposite
   outward directions, and an interior source projection within the existing
   corridor. Preserve original target geometry and access provenance.
3. The new mapping must partition source evidence monotonically. **Do not map the
   whole physical window to both target edges**, infer a visit to an untravelled
   branch, extrapolate source endpoints, or relax the existing source-overlap
   guard. A partial journey on either side must credit only its supported part.
   If an anchor cannot be justified under those rules, keep that interval unbound.
4. Keep deferred target/source masks authoritative. Produce a versioned additive
   binding correction artifact from immutable inputs; retain original manifests
   and give the candidate new hashes. Do not rebuild route discovery or silently
   weaken old checkpoint consumers to accept revised plans.
5. Validate every changed binding and recalculate all 218 route capacities and the
   unique network. Existing supported target intervals must remain supported;
   distinguish credited source distance from logical target distance. Report actual
   gains separately from the diagnostic ceiling and every unresolved case.

## Representative fixtures

`tests/fixtures/ncn-gap-seams/diagnostic-witnesses.json` contains pinned public
reference geometry and source hashes, with no personal journey data:

- NCN:9, section `59306268:4:27`, demonstrates an ordinary seam on a partial way;
  protects the integrated source-offset correction.
- NCN:8, section `31252441:47:55`, provides another orientation/segmentation case.
- NCN:81, section `44807950`, is a **negative**: two degree-two edges run back
  alongside one another. Treating every degree-two vertex as harmless would
  falsely merge this hairpin. It must remain ambiguous.
- NCN1 provides the long/disconnected aggregate regression; RCN:90 is an explicit
  negative control for unrelated missing evidence. Preserve repeated/reversed
  source occurrences and exact identity/mode requirements.

The receipt's target-level missing intervals can aggregate multiple witnesses;
`witness_projection_interval_m` identifies the span supported by the selected
example. Never infer that a single example explains all gaps on that target.

## Required automated validation

Extend `test_open_completion.py` with ordinary-seam positive tests and negatives
for parallel roads, hairpins, branches, isolated crossings, deferred paths,
source endpoints, sparse/partial evidence and changed source hashes. Preserve
`test_target_endpoint_is_not_extrapolated`, mode/union/edit tests and the exact
source-offset regression. Test foot/run/cycle, driving exclusion, repeats,
corrections, deletion and genuine returns without manufacturing journey evidence.

Run the NCN research/reference suites and classify/capacity tools against the
**new candidate**, not just the shipped DB. Verify physical-network mappings,
combined 25/50/75/100 threshold semantics and source interval non-overlap. Then
run the existing candidate Android workflow (41-test baseline) with the candidate
hash/active version assertion and no skips. Preserve offline/resume/cache and
presentation regressions; no general UI or performance rewrite. Record compiler
work and candidate size, without inventing phone performance figures.

No new field journeys, exhaustive screen review or screenshot collection is
needed for this data/compiler package. Real-device rollout acceptance remains a
separate step if a publication is subsequently authorised.

## Boundaries and completion

Create a short-lived branch directly from latest validated main; target main and
leave the implementation PR open. No Firebase publication, backend deployment,
APK build/distribution, tester release, Stage 1.2/1.3 or mileposts. No APK is
anticipated if the current schema/consumer tests pass.

Done means a reproducible, evidence-backed seam correction with preserved
negatives and measured national before/after results—not forced recovery of the
entire ceiling. If it cannot satisfy finite evidence and non-overlap requirements,
return the exact witness and stop that fix; do not introduce a speculative
fallback. No owner product decision is required to start. Any proposed valid-scope
reduction or tolerance expansion requires a separate concrete owner decision and
is not authorised by this brief.
