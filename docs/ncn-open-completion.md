# Captured open-section completion trial — 19 October 2026

The 25 bounded catalogues from the remaining-54 review now have portable
completion plans and a saved-path activation bridge. A journey's verified
source spans can contribute to the corresponding published open target while
its actual physical geometry remains unchanged. **This is an isolated Python
integration trial, not Android activation or an APK release.**

The existing checkpoint remains 219 supported identities / 62,399 distinct ways.
This slice admits no new source paths and resolves none of the 29 evidence holds.
Five older holds also remain outside that queue. The bounded additional identity
lookup did not establish a new numbered source: its Overpass request returned
HTTP 500. That is a failed lookup, not evidence that a route is absent or closed.

## Fixed open targets

Each plan uses the full union of the catalogue's pinned Open Main Route features
classified OnRoad or TrafficFree. Duplicate official linework counts once. Every
unbound target edge remains in the denominator at zero contribution. No source
inventory is replaced, no missing link is drawn, and no per-route distances are
summed into a national total. Overlapping numbered routes need shared physical
accounting at the collection level.

The scope is explicitly `NCN:number:captured-open-main` (or the unchanged regional
prefix). A fraction describes that captured open target only; it does not certify
an entire promoted itinerary. Planned, closed, unknown, alternative, ferry and
N/A features were excluded by the preceding strict catalogue review. Its original
source and extent receipts remain authoritative and unchanged.

## Conservative correspondence

`open_completion.py` first loads a hash-pinned catalogue through the existing
strict reader. Only previously admitted, exact source spans and their permitted
modes reach the binding stage. The new stage cannot add route membership or
match a GPS trace. In particular, none of the 29 held route keys has a plan.

The policy `unique_monotone_open_edge_correspondence_v1` compares straight source
and target edges in the catalogue's metric projection. A physical interval needs
one unique target correspondence, no more than 10 m perpendicular offset, and
direction agreement within 15 degrees (either travel direction). Projection is
clipped to both finite edges, with no endpoint extrapolation. Intervals with
multiple candidates, excessive offsets, sharp direction differences or no
candidate stay unbound. Adjacent target edges are assessed separately; gaps are
never filled by joining endpoints. Micron-scale numeric normalization only
prevents floating-point endpoints escaping their declared bounds.

The 10 m / 15 degree values are explicit, conservative development parameters,
not legal-access rules or a new saved-journey snap tolerance. This straight-edge
policy leaves some bends and junctions unresolved even where the earlier 10 m
proximity diagnostic found nearby geometry. Those remaining portions require
reviewed multi-edge or alternative-path correspondence; they do not prove a
missing road. A supported catalogue is therefore not synonymous with a fully
bound completion target. The per-route summary records both bound capacity by
mode and every metre of unbound target. These are data capacity, not user travel.

The generated batch contains 206,815 binding intervals across all 25 plans. For
example, NCN1 retains 141.713 km of unbound target under this policy, compared
with 77.460 km outside the earlier source proximity corridor. The difference is
additional conservative correspondence uncertainty, not newly discovered closure.
RCN70 and RCN92 keep their regional identities and bounded Open Main scope.

## Saved history and versioning

`activation_trial` wires a verified portable plan into the existing resumable
activation worker and `InventoryLedger`. The existing walking/cycling source
contracts still decide which physical spans a saved journey supports. The new
logical ledger translates only those supported intervals. It never colours an
offset publisher line as physically travelled or turns an unsupported import
into a match. Driving contributes no collection progress.

Plans have a new content-derived `open-completion:` reference version. The
association bundles are rebound to that version; their path rows, node chains,
physical reference digest, geometry, permitted modes, and provenance are exactly
unchanged for every real catalogue. The activation accounting key also pins the
complete plan hash. An old or differently accounted checkpoint requires explicit
reconciliation. Initial activation assesses stored evidence rather than issuing
new matching requests or creating historical review prompts. Repeats, mode
corrections, deletion and interrupted-work restore use the existing ledger rules.

## Validation and reproduction

317 NCN tests and 5 shared cycling-evidence tests pass. Eleven new completion
tests include offsets, reversed travel, finite endpoints, parallel ambiguity,
isolated crossings, unsupported detours, mode policy, repeated geometry, numeric
bounds and an admitted fixture through portable readback, stored source evidence,
activation, checkpoint restore and archive deletion. The fixture is synthetic;
it is not field acceptance or Android performance evidence.

All 25 real plans and bundles passed the portable consumer. A separate comparison
verified their path rows and physical reference digests against all 25 original
catalogues. The original evidence outputs and catalogue summary are unchanged.
The compact JSON index pins the new 76-file plan output and its prerequisite.

From a checkout of this change, with the preceding remaining-54 evidence folder
beside it:

```sh
python -m scripts.ncn_trial.open_completion \
  --catalogues ../ncn-remainder-54/output \
  --expected-summary-sha256 53781739c89bed4ab0f616f2ee3155c8149df4cabd7dc7ebc416981768fbafe6 \
  --output ../ncn-open-completion-replay
```

The output directory must be fresh. Each route has a compressed plan, compressed
association bundle and manifest; the batch summary is written only after every
selected catalogue succeeds. Distribution consumers must obtain the expected
plan and association hashes from a trusted manifest. Hash consistency is not a
signature or independent route approval.

## Remaining integration

The other accepted catalogues still need equivalent completion preparation;
this first new batch covers the latest 25 only. Next comes a bounded Android
consumer trial with persistent per-journey evidence, background activation and
map/progress display, followed by broader catalogue integration. Whole-route
completion claims, achievements and production activation remain gated on their
own scope and product checks. The internal Android trial remains eight routes.
No Firebase write, app release, catalogue promotion or historical credit transfer
is part of this change.
