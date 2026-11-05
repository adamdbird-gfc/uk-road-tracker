# NCN matching: reuse of road and path infrastructure

29 October 2026. Read-only feasibility investigation. Source baseline:
`cc088b828982a3ff9399e2bd930978ce02ecba4a` (latest main checked against origin).
Android source baseline: **0.25.220 (266)**. PR #240 at
`21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7` is evidence, not an integrated dependency.
PR #238 remains separate. No implementation or catalogue change in this report.

## Recommendation for the owner

**Reuse the existing walking and cycling matchers, as NCN already does. Repair
the link between their identified paths and the NCN route map; do not replace
that link with the motorway coverage estimate.** This is approach C, a hybrid,
and is an incremental improvement to the existing architecture.

Think of a journey as answering “which physical path did I travel?”, and the
NCN catalogue as answering “which parts of NCN 1 does that path represent?”.
Knowing the first does not automatically answer the second. A road and its
adjacent cycle path can be only a few metres apart. A safe answer needs the
correct path identity, permitted activity and an evidenced correspondence.

We are fixing the right layer for the proven segmentation defect. We have not
proved that binding repairs alone will resolve every gap. The missing 7.66%
does **not** mean 7.66% of NCN is absent from the road/path network. The retained
data cannot measure the national road/path split or genuine absence reliably.
There is no evidence here supporting a replacement matcher or a broad rewrite.

## What the current systems actually do

| Responsibility | Roads, including the M11 example | NCN |
| --- | --- | --- |
| Journey matching | Backend routes observed GPS onto a mode-specific graph. Driving, walking and cycling have different processing. | Reuses walking `preloaded_pedestrian_network_v4` evidence and cycling `osrm_bicycle_v1` evidence. `NcnMatcher` is an evidence consumer, not another GPS router. |
| Reference geometry | Motorways have canonical road lines; A roads have canonical references. Other road views may show recorded sections without a full reference denominator. | Published verified-open targets come from a separate publisher dataset; admitted physical paths have OSM way/node identities and geometry hashes. They can be segmented and positioned differently. |
| Coverage accounting | Motorway calculator samples reference lines, marks nearby reference anchors from retained matched road geometry, and estimates unique mileage from the fraction of covered anchors. | Source intervals are translated through catalogue bindings into target intervals. The ledger unions intervals within a route and onto a canonical unique network. |
| Presentation | Focused road model supplies travelled/remaining lines and mileage. | Uses the same focused-road model/activity infrastructure, with NCN-specific ledger/detail/overlay preparation and activity filters. |

The owner's M11 figures are a useful presentation goal, not a replayed
measurement in this investigation. In `MotorwayProgressCalculator`, reference
sampling is 100 m, journey sampling 25 m, match radius 110 m and reference-anchor
deduplication radius 95 m. Mileage is reference length multiplied by anchor
coverage. Road identity and driving/bus filtering restrict candidates first;
this is not unrestricted GPS proximity credit. Nevertheless, copying these
allowances onto NCN could conflate parallel cycle paths, roads or branches.
`ARoadProgressCalculator` also uses sampled coverage, with a 100 m match radius.
This report makes no claim that existing road calculations are defective.

The systems share OSM-derived infrastructure, saved matched journey responses,
correction handling, geometry/hashing utilities and Android map/detail surfaces.
They do **not** share one complete canonical coverage ledger or one reference
graph. Pedestrian routing uses database `pedestrian_reference_segments` joined
to `reference_sources`; cycling uses a dedicated external OSRM bicycle graph.
Motorway/A-road canonical references are another representation. A shared OSM
origin does not guarantee identical snapshot geometry or eligibility.

`NcnMatcher` accepts complete eligible journeys only. Walking/running evidence
must identify the admitted way and source geometry hash; cycling must identify
a unique monotone OSM-node chain and agree with the admitted geometry. Removed
matched segments are excluded, unsupported/changed evidence fails closed, and
driving/bus never earns NCN progress. Legacy walking has a constrained exact
source-geometry route; arbitrary old geometry is not enough. These checks do
not prove perfect real-world GPS selection; they prevent attribution from
inventing stronger evidence than the journey contains.

The compiler's `open_completion.bind_edges` performs a different job: only after
source admission, it maps finite source spans onto publisher target edges using
the existing 10 m/15-degree policy. Ambiguity, unsupported extent and deferred
alternatives stay unbound. PR #239 fixed source-section offsets in that mapping;
it did not change matching, targets or access. NCN's explicit interval union is
needed for partial journeys, shared routes and combined foot/cycle achievements.

## What can be measured now

All distances below use the existing **unique physical-network union**, not the
sum of route lengths. Receipt and limitations:
[machine-readable assessment](data-trials/ncn-shared-matching-feasibility-2026-10-29.json).

| Requested measurement | Evidence-backed result |
| --- | --- |
| Published measurable NCN target union | **17,435.132 km** across 218 measurable routes. |
| Existing assessable target union | **16,099.974 km; 92.342135%**. Supported by admitted source-to-target bindings, not a measurement of exact geometric coincidence with live routing networks. |
| Unassessable target union | **1,335.159 km; 7.657865%**. All 218 routes still contain gaps. |
| Overlap with the existing road network | **Unknown nationally** from the available snapshots; the canonical motorway/A-road files are not a complete road/path network. |
| Overlap with supported walking/cycling paths | **Unknown nationally**, and overlaps road coverage. These are not disjoint classes: walking/cycling can be permitted on roads. |
| Genuinely absent from all existing networks | **Unknown**, not 1,335 km and not the 619 km class below. Absence from admitted NCN evidence does not establish network absence. |
| Present in active network but unassociated with the right NCN route | **Unknown nationally**. Captured known-way spans below are leads, not confirmation of active-graph presence, access or route membership. |

Why an exact overlap percentage is unavailable: the retained NCN checkpoint has
route-specific admitted associations and publisher extents, not the complete
active pedestrian database or bicycle graph. Its association rows omit highway
classification/access tags required for a road/path split. Repository regional
examples, canonical numbered-road references and import code do not establish
complete national content or current versions. Neither a 10 m buffer nor exact
coordinate equality across independently digitised datasets can establish that
two nearby lines are the same eligible infrastructure.

A reliable additional comparison would need a pinned, read-only export of
existing public `pedestrian_reference_segments` plus `reference_sources` and
`reference_data_loads` receipts (way IDs, complete geometry, source hashes,
highway/foot/access tags), and the existing bicycle graph's snapshot/profile
and way/node cross-reference with access policy. Cycling eligibility cannot be
inferred from pedestrian import tags: `pedestrian_reference.py` does not retain
`bicycle` tags. Include complete road classes beyond motorways/A roads. Join
identities and source versions first; review changed geometry separately; apply
the existing target-to-network union only once. No user journeys or fresh
national route discovery are needed. Without a complete pinned export, label
unmatched records **unknown**, never “absent”. No live dataset access is claimed.

## What the remaining 1,335 km tells us

PR #240's exclusive network cause sets give this partition, independently
rechecked against the retained receipt. Tiny overlapping explanation sets and
the explicit projection residual are retained in the JSON, not credited.

| Existing diagnostic cause | Unique-network km | What reuse can establish |
| --- | ---: | --- |
| Ambiguous target correspondence | 109.425 | Admitted source exists, but selecting the correct target is unresolved. |
| Direction gate | 426.477 | Finite nearby source correspondence appears only when the angle test is omitted diagnostically. May include crossings/different paths; not recoverable mileage. |
| Finite endpoint/alignment | 69.767 | Nearby admitted geometry is insufficient under current finite correspondence. No endpoint extrapolation. |
| Deferred alternatives | 9.304 | Existing evidence is deliberately withheld pending alternative-path correspondence; preserve that contract. |
| Unadmitted parts of known source ways | 101.320 | Captured way geometry suggests a review opportunity; mode/membership/extent still need proof. |
| No admitted source correspondence | 618.866 | Could be a missing association, changed source or genuinely missing evidence. Does not prove the path is missing from a routing network. |

The first four classes total **614.973 km (46.060% of the deficit)** of diagnostic
leads involving admitted evidence. Adding known-way leads gives **716.293 km
(53.649%)**. Neither is a safe improvement forecast. No new binding or credited
metre was produced. PR #240's narrower ordinary-seam probe identifies a
**45.631 km unique-network ceiling** using already admitted sources, within the
ambiguity class. It is not an extra class to add to the table. Even that ceiling
would complete no route and raise capacity only to about **92.604%**.

## Comparison of approaches

| Consideration | A: existing binding repairs | B: direct road/path coverage reuse | C: shared evidence plus NCN attribution |
| --- | --- | --- | --- |
| Expected improvement | Proven bounded seam opportunity; other classes require evidence. | No measured gain. Existing route-ID-free road totals cannot identify which NCN intervals were travelled. | Same proven opportunity initially; future exact identity joins could recover associations, amount unknown. |
| Accuracy | Preserves finite correspondence; piecemeal exceptions would be risky. | Reusing road anchor/proximity totals risks adjacent paths/branches and imports driving policy. Exact interval reuse would still require a crosswalk. | Reuse source identities/intervals; retain NCN target mapping, modes and ambiguity holds. |
| Complexity | Smallest immediate change. | Large if replacing ledger, reference definitions, historical recognition and caches; a safe exact implementation converges on C. | Small increment on today's hybrid; avoid an unneeded new universal ledger. |
| Performance/maintenance | Offline catalogue compilation, indexed consumer; preserve existing caches. | Reprocessing large archives or joining every journey to every target is costly. Existing aggregates lose required detail. | Build correspondence once offline; union source intervals before target mapping; preserve incremental/resumable processing. No new performance benchmark claimed. |
| Android 266 | Existing-schema candidate corrections can use current consumer, subject to tests. | Consumer changes likely for a replacement contract; APK cannot be ruled out. | No consumer change anticipated for the next data/compiler correction. Existing PR #239 candidate compatibility is already validated; future candidates need validation. |
| Migration | Version new catalogue; re-evaluate original evidence, preserve edits/history. | High risk of importing unsupported totals, resetting achievements or changing denominators. | Retain IDs, provenance, catalogue-version cache invalidation, original journey dates and combined milestones. |

Dedicated/shared-use/off-road paths are already supported where mode-specific
matcher evidence and admitted source access agree. A neighbouring carriageway
must not substitute. At junctions and crossings, source identity plus finite
monotone attribution must reject branch shortcuts. Overlapping NCN routes may
receive their own route credit, but network and combined-mode totals union the
same physical interval once. Historical journeys use their retained evidence;
missing identity requires supported reassessment, not converting an old road
percentage to NCN progress. Keep corrections and original GPS intact.

For large archives, `NcnProgressStore` already uses a background worker,
catalogue/versioned per-source assessment caches, resumable checkpoints and
metadata-only refresh. `NcnLedger.prepare` unions repeat source evidence before
catalogue access. Preserve these properties; do not add nationwide geometric
search per journey. No new architecture is needed to obtain the requested
travelled/remaining presentation, which NCN already exposes.

## Decision and next task

Proceed with the bounded existing-evidence seam correction described in the
[focused brief](ncn-shared-matching-next-development-brief.md). This architectural
review supports that work; it does not authorise implementation in this task.
The 45.6 km ceiling is modest but demonstrated. There is no measured larger safe
win justifying a rewrite. Keep network-export/identity reconciliation as the
bounded prerequisite for any claim of a larger reuse opportunity, rather than
promising it will fix the other 1,289 km.

No product decision or scope reduction is required. Implementation and any later
publication remain separate approvals. Stage 1.1 remains incomplete.

## Evidence and validation

Architecture entrypoints inspected on main:
`api.py` (`match_cycling_activity`, walking path, router request annotations),
`database.py::match_pedestrian_reference`, `pedestrian_reference.py`,
`matched_path_evidence.py`, `cycling_path_evidence.py`, and Android
`MotorwayProgressCalculator`, `ARoadProgressCalculator`, `RoadDetailModel`,
`NcnMatcher`, `NcnLedger`, `NcnProgressStore` under
`android-prototype/app/src/main/java/com/roadprints/capture/`.
Compiler entrypoints: `scripts/ncn_trial/open_completion.py` and
`scripts/build_ncn_national.py`.

Retained checkpoint summary/input-pin hashes, classified inventory SHA-256,
all 442,910 classified subintervals, route counts and cause distance totals were
checked locally. No national discovery, reference regeneration or runtime test
rerun was necessary for documentation. Prior candidate run
[40894305507](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40894305507)
passed reproduction, mappings and 41 Android tests; this is historical candidate
validation, not a fresh live-device/network-overlap test. PR #240's
[40900508503](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/40900508503)
is its existing NCN validation. Live backend/Firebase versions and the actual
installed phone build were not independently queried in this read-only source
study; 266 is the stipulated baseline.

Delivery: documentation branch from main; leave its PR open/unmerged. PR #240
unchanged/unmerged. No runtime, catalogue, progress, achievement, backend,
Firebase, APK, distribution or issue-status changes. No device acceptance or
screenshot exercise requested.
