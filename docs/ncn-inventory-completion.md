# Captured NCN source inventory completion — 16 October 2026

The trial now produces fixed completion plans for all 48 strongest candidate
numbered-route keys. These describe the complete captured OSM member inventories,
not verified totals for the entire published or promoted routes. Every plan has
`whole_published_route_ready: false` and a `:source-inventory` scope.

## Accounting and source qualification

The denominator includes the unique physical geometry of all captured member
ways, including mileage that cannot currently earn progress. Unsupported mileage
is never removed to make a route appear complete. NCN228, for example, retains a
nonzero denominator with no eligible associations. Supported coverage capacity in
the reports is a data-quality measure, not recorded user progress.

Consecutive source vertex edges are now assessed independently. A partially
supported way retains its full node identity while only explicit contiguous
vertex spans enter the matching catalogue. An unsupported middle span cannot be
bridged. Existing membership, geometry, access and saved-path evidence checks
remain in force; proximity alone and transport-mode relabelling are insufficient.

Explicitly tagged roundabout/circular member ways are grouped by shared OSM node
IDs. A simple cycle with exactly two network gates receives two gate-to-gate
branches. Either branch earns the same logical connection, whose canonical
length is the shorter arc. Riding both does not double logical completion;
physical travelled distance remains separate. This also works for rings split
across several source ways. A linear member arc does not invent the rest of a
circle. General loops, complex junctions and unresolved branch policies retain
their full inventory requirement.

The physical reference also fixes an accounting bug: endpoint projection onto a
whole closed or repeated line selected its first occurrence and could omit a
closing or returning edge. Mapping each original segment with its cumulative
source offset preserves every occurrence while counting unique physical union
geometry once.

## Results and portable format

- 48 fixed source-inventory completion plans, including the zero-association case.
- 47 bounded matching catalogues; 3,999 distinct eligible ways, up from 3,778.
- 10 reviewed two-gate roundabout groups.
- 10 source inventories have no remaining unmapped capacity in the combined-mode
  accounting. This does not certify the full published routes.
- Remaining holds include 419 ways with unresolved extent portions, 22 with
  unresolved/restricted mode policy and 13 requiring branch policy. Partially
  accepted ways can also appear in the holds; these are not disjoint counts.

`inventory_completion.py` writes a compressed plan and association bundle plus a
manifest with independently pinned uncompressed SHA-256 hashes. The reference
version covers the plan's policy and bindings. `read_completion` verifies hashes,
version, scope and declared denominator before constructing the ledger. Unmapped
logical sections remain at zero in `InventoryLedger`; the generic alternative
ledger's stronger binding requirement is unchanged.

Regenerate with `python -m scripts.ncn_trial.inventory_completion
--research-root RESEARCH --catalogues RESEARCH/confirmed-catalogues-v2
--output RESEARCH/completion-plans` from the repository root. This consumes the
53 pinned full-source snapshots already captured for these 48 candidates.
Every generated plan and association bundle was read back through the consumer.

Validation: 242 NCN checks and 5 shared collector checks pass. Coverage includes
closing/repeated edges, split rings, partial spans, fixed unmapped denominators,
mode corrections, deletion, portable checksum rejection and activation/checkpoint
replay without bulk-import prompts.

## Remaining boundary

Published-route scope, missing sections, unreviewed branches and the remaining
national candidate groups still need reconciliation. Android activation,
map/progress presentation and initial achievements remain integration work.
This checkpoint changes no production database, Firebase release or APK and does
not yet satisfy the agreed app beta gate.

The following day's [official scope review](ncn-official-scope-review.md) locates
exceptions for all 48 plans and identifies eight strongest integration candidates.
It keeps scope diagnostics separate from progress eligibility and readiness.
