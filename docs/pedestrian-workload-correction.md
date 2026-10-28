# Pedestrian workload correction — 28 October 2026

Successful normal walking matches within the existing workload budget are an
additional acceptance condition for pedestrian continuity work. Selection 7's
geometry policy remains unchanged; the optimised execution is identified by
`pedestrian_search_version: 2`.

## Confirmed failure

Production logs show a 97-observation / 12,137-row reference match reaching its
20-second deadline at layer 81. Router fallback then exhausted the overall
48-second walking budget and returned HTTP 503. This is deadline exhaustion,
not proof of disconnected pedestrian geometry.

The owner's earlier private export for the reported journey has 188 saved
positions. Android validation removes one fix; simplification submits 133.
Reconstructed validated distance matches its recorded preparation, and the
accuracy-bearing request is 9,515 bytes, matching the failed HTTP log.
An ordinary live replay sometimes succeeds but consumed 19.917 seconds of
reference work: inadequate operational headroom.

Selection 6 and 7 use the same 1,303 searches / 206,263 settled nodes on this
trace. The continuity policy did not add candidate expansion. Its predecessor
already ran near the deadline; repeated candidate/path scoring and crowded
spatial queries left the larger trace vulnerable to load variation.

## Exact work reduction

- Reuse one projection frame and vertex-deviation cache per observation pair.
  The cache is discarded after the pair, and does not grow with journey length.
- Visit cheaper arrivals first, but prune only mathematically dominated
  transitions to the same merged state. Distance/shape costs are nonnegative;
  great-circle separation lower-bounds network travel. Strict comparisons keep
  equal-cost alternatives available. Unknown-accuracy direction-dependent
  states remain exhaustive. No beam, candidate count/radius reduction or
  endpoint-only routing is introduced.
- Query finer edge-index cells and reject bounding boxes outside the existing
  exact spherical search cap before projection. Final candidate ranking and
  real network connectivity are unchanged.

The reference deadline remains20s, request budget48s, router-call cap96,
per-call cap10s, and Dijkstra/cache limits are unchanged. No timeout, resource
plan, schema, reference-data or Android changes are required.

## Private fixture acceptance

The existing replay tool accepts `--request` for an exact prepared request,
`--expected-points 133`, and `--expected-result` for geometry/distance/evidence
equality. Every prepared point must be an ordered saved position with its
recorded accuracy. A failed current match now fails the regression command
instead of exiting successfully after printing "no match". Baseline replay
uses the baseline's graph/candidate implementations as well as its selector.

For the reported fixture, output remains exactly320coordinates /2103.9m;
searches1303→1138, settled nodes206263→173613, fully scored transitions
67141→13381. Three-run local medians3.074→2.183s; trajectory phase1.613→0.759s.
These are development measurements, not production speed guarantees.

A controlled8×monotonic-clock stress replay reproduces the original reference
deadline failure without changing its20ssetting. The optimised matcher passes
at the same scale with identical geometry/evidence. This emulates a slower CPU;
it is not a claim that a new live HTTP request always reproduces the503.

The original Charing Cross fixture remains exactly262coordinates /1860.0m,
with zero reversals/repeated edges and all five private region checks passing.
Its local median2.466→1.697s. Genuine movement, crossing, driving/cycling and
NCN controls remain required. Added public controls check dominated-source
work, preservation of a more costly but globally supported turn, exhaustive
unknown-accuracy arrivals and pair-cache isolation/bounds.

Production deployment and repeated live-budget acceptance are recorded in the
development handover after verification. Raw journey/network fixtures remain
private. Android0.25.220 can explicitly rematch existing saved journeys; no APK
or recapture is required.
