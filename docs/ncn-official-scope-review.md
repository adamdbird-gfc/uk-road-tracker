# Official scope exception review — 17 October 2026

All 48 captured source-inventory plans have now been compared with every feature
of their corresponding numbered key in the pinned official snapshot. This is a
scope review, not a change to matching rules or published-route certification.
The snapshots remain those captured on 16 October, not newly fetched live data.

Eight inventories have no outstanding matching capacity and no geometry outside
the other's 10m corridor: NCN167, 270, 344, 426, 440, 476, 548 and 755. They form
the strongest next integration-test shortlist. Their official features are open
main-route sections. Even these retain `whole_published_route_ready: false`:
corridor agreement cannot independently establish the full promoted itinerary.

Two other fully bound inventories have small discrepancies:

| Inventory | Official geometry outside source's 10m corridor | Outside 25m corridor |
| --- | ---: | ---: |
| NCN166 | 45.41m | 0m |
| NCN339 | 12.24m | 0m |

These are offset-sensitive discrepancies, not proven missing roads. Widening a
diagnostic corridor grants no journey credit and changes no denominator.

Across the 48 keys, overlapping review categories are:

| Review finding | Keys |
| --- | ---: |
| Inventory still has unsupported matching capacity | 38 |
| Official geometry outside source's 10m corridor (over 1m) | 34 |
| Of those, residual remains over 1m at 25m | 15 |
| Source geometry outside official open main 10m corridor (over 1m) | 38 |
| Of those, residual remains over 1m at 25m | 24 |
| Official key also contains nonqualifying statuses/categories | 11 |
| No official open main extent | 1 |

The 1m threshold only prioritizes the queue. Every positive line residual is
retained in the detailed results, including smaller fragments. Different
categories, modes, feature records and route identities can overlap; their
counts and lengths must not be summed as national mileage or distinct gaps.

## Walk/cycle separation and exception locations

Each route has 3m, 10m and 25m bidirectional geometry diagnostics. Official open
main geometry is also compared against walking, cycling and combined eligible
source spans separately. NCN167 and NCN270 demonstrate why this matters: both
have full combined inventory capacity, while approximately 333m and 367m of
official geometry respectively lack walking-eligible spans within 10m. Cycling
has no corresponding residual. This is current static eligibility evidence,
not a claim about historical access or a geometric walking shortcut.

Compressed WGS84 GeoJSON exception files identify official FID/SegmentID/GlobalID
and source way IDs where applicable. They include official geometry outside the
source corridor, official geometry without an eligible binding, source geometry
outside the official open main corridor, unsupported source spans and official
features outside the qualifying open main scope. These preserve locations for
targeted review instead of hiding all exceptions behind one percentage.

`scripts.ncn_trial.scope_review` first validates every plan/association checksum
and reference version through the existing consumer. It rejects an incomplete
plan set, changed official snapshot or mismatched route folder. Results contain
input hashes and never modify associations, inventory lengths or readiness.
Union lengths avoid duplicate official feature inflation; per-feature exception
linework preserves provenance and is explicitly non-additive.

Reproduce using `python -m scripts.ncn_trial.scope_review --plans
RESEARCH/completion-plans --network OFFICIAL.geojson.gz
--expected-network-sha256
331023ba1d8ca175471700b1bdb5d7828984d535792c0c54e7a4b465d48cf3f2
--output RESEARCH/scope-review` from the repository root.

Validation: 252 NCN checks and 5 shared collector checks pass. Ten new tests cover
false completeness from short fragments, offset sensitivity, mode separation,
empty binding sets, closed official features, duplicate geometry, retained
identity, Scottish coordinate round trips and incomplete-batch rejection.

The next implementation boundary is a bounded Android consumer/integration trial
using the eight strongest source inventories, preserving retrospective assessment
on activation and the independent walking/cycling totals. Published-scope review
and the remaining national exception queues stay open. Map, progress and initial
achievements are still required before the agreed beta. No APK, Firebase release
or production database change is part of this checkpoint.
