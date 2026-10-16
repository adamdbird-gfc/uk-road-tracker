# Named-route reference reconciliation — 16 October 2026

This development step screens four OSM named-route candidates against the
checksummed national NCN snapshot. It does not approve associations, award journey
coverage, change Postgres or deploy an Android/Firebase release.

## Measured screening results

| Candidate | Approximate mapped geometry near open NCN at 10m |
| --- | ---: |
| Way of the Roses | 94.5% |
| Two Tunnels Greenway | 99.9% |
| Bath Two Tunnels Circuit | 99.3% |
| Caledonia Way, Oban–Campbeltown section only | 5.3% |

These are approximate geometry-corridor diagnostics, not current membership
percentages or route-completion denominators. A nearby parallel path can pass
this screen; it never earns journey credit. Direction, accessibility, reference
identity, alternatives and source disagreement still require reconciliation.
The code exposes 3m, 10m and 25m sensitivity rather than selecting a larger snap
radius for the app. Crossings contribute only the short length inside a corridor.

A separate bounded download of 18 official Reclassified Routes features shows
94.4% of the southern Caledonia candidate within its 10m corridor. This strongly
supports reclassification as the reason for most of its absence from current
NCN geometry; it does not independently verify every source-way association.
Open NCN and reclassified percentages are separately measured and must not be
added as disjoint progress totals. The geometry is the southern section only,
not the full Caledonia Way. Do not mark the whole promoted itinerary complete
from the NCN-only subset. Product behaviour for full named-route coverage remains
to be agreed before shipping completion cards.

## Repeatable pipeline

`python -m scripts.ncn_trial.reference_reconciliation --help` describes the inputs:
network GeoJSON (gzip accepted), expected uncompressed network SHA256, candidate
member-way GeoJSON, candidate catalogue and output path. The pipeline:

- refuses a mismatched network snapshot and incomplete/duplicate way extracts;
- selects the bounded network geometry and uses an STRtree for spatial queries;
- measures a union of candidate geometry so duplicate geometry cannot inflate
  the screening total;
- separates exclusive 10m corridor lengths for open NCN, nonqualifying NCN
  (closure, ferry or unknown status), regional, link, unclassified and outside;
- preserves candidate section IDs and input hashes for review;
- always emits association_approved=false and completion_ready=false.

The local equirectangular metric projection is appropriate for approximate
regional triage, not publication of exact nationwide distances. Unknown network
status is not labelled as a confirmed closure. The screening does not infer
walking rights from bicycle relation membership. No generated output is consumed
by the saved-path assessor, activation ledger or Android.

110 NCN tests pass locally: the eight screening checks cover duplicates, parallel
paths, absent reference, nonqualifying categories, conservation of measured
length, source classification, the catalogue pipeline and incomplete extracts.
The existing 102 checks still pass. CI now includes the new checks.

## Source evidence

Network snapshot SHA256:
331023ba1d8ca175471700b1bdb5d7828984d535792c0c54e7a4b465d48cf3f2

Current OSM relations (ODbL, OpenStreetMap contributors):
1761952 (Way of the Roses), 2831180 (Two Tunnels Greenway),
4137412 (Bath Two Tunnels Circuit), 20306454 (Caledonia southern section).
The separate catalogue assessment preserves relation versions and source hashes.
Do not replace these with the successful but stale July Overpass replica result.

Official reference layers:
- https://services5.arcgis.com/1ZHcUS1lwPTg4ms0/arcgis/rest/services/National_Cycle_Network_Public/FeatureServer/0
- https://services5.arcgis.com/1ZHcUS1lwPTg4ms0/arcgis/rest/services/Reclassified_Routes_Public/FeatureServer/0

Retain the existing per-source licence/attribution audit; webpage permissions and
GIS licences are distinct. The publisher's differences map is for large-scale QA,
not a path matching oracle. No website descriptions, photos or GPX products have
been copied into app assets.

Next: review source-way correspondences, define full-itinerary versus NCN-only
named-route targets, then expand discovery regionally. National correspondence,
cycling provenance and Android runtime integration remain outstanding.

## Wales/NI expansion and nested catalogue extraction

The bounded source catalogue now contains nine source profiles for eight named
route extents, with examples in all four UK nations. This is not a complete UK
named-route inventory. Added sources and approximate geometric screening:

| Source profile | Unique ways | Components | Near open NCN at 10m |
| --- | ---: | ---: | ---: |
| Taff Trail (hiking relation 3000677) | 338 | 6 | 66.9% |
| Lôn Las Cymru (parent 5487294 and all three children) | 1260 | 3 | 97.1% |
| Lagan Towpath (hiking relation 1116068) | 76 | 3 | 95.4% |
| Comber Greenway (bicycle relation 8604523) | 47 | 1 | 87.8% |
| Comber Greenway (foot relation 3018630) | 41 | 1 | 98.7% |

The Comber source profiles share exactly 41 way identities; six further ways
appear only in the bicycle profile. This does not prove those six ways prohibit
walking. Do not collapse the profiles into one denominator or infer permissions
from their names. The Taff walking relation shares 222 ways with Lôn Las South,
while 116 do not share that identity; the southern cycle route covers a larger
extent, so these are not equivalent itineraries. The source topology and mode
access need investigation before a Taff or Lagan completion card can ship.

All direct member geometry has been recovered for these source profiles. That
means no missing source way/node, not proof of a gap-free real-world route.
Lôn Las Cymru's three child relations (5487292 North, 5487291 Central, 5487293
South) have been expanded without assuming they connect. Its combined graph
has three components. Taff has six; Lagan three. No gap is bridged by proximity.
The southern Caledonia candidate remains only a section of that named itinerary.

`catalogue_extract.extract_route` reads bounded JSON source snapshots, recursively
preserves relation membership and role chains, and deduplicates physical way
geometry without losing repeated memberships. It rejects conflicting source
element versions, preserves explicit public mode/access tags and route type,
flags missing relations/ways/nodes and cycles, excludes guideposts from edge
counts, and never turns a missing midpoint into a new straight edge. An empty
route is not complete. No mode permission or current NCN membership is inferred.
It emits candidates only; completion_ready remains false.

118 local NCN checks now pass, including eight extraction checks. CI includes
the extraction suite. Source snapshots and diagnostic outputs are retained in
the separate assessment; no personal journeys or national geometry are added to
Postgres, and no APK or Firebase release is made.

Owner cross-checks:
- https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/taff-trail-cardiff-to-brecon/
- https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/belfast-to-lisburn-lagan-towpath-route-9/
- https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/comber-greenway-route-99/

Next: resolve mode-specific extent and topology flags, validate source-way links,
and grow the named catalogue beyond these targeted cases. The existing broad
beta gate (map, progress, achievements and reliable mode matching) is unchanged.

## Kent checklist pass and pedestrian-area safeguard

Three more source records have been recovered: Heron Trail (relation 21894,
60 unique ways), Darent Valley Path (146747, 211 ways), and RCN15 Kent (8824,
352 line ways plus an unsupported pedestrian-area relation). These extend the
research to 12 source profiles associated with 11 named-route leads, not 11
approved completion assets. RCN15's Viking association remains extent-unresolved.

- Heron: 98.0% of the mapped line geometry is within a 10m corridor of open NCN
  geometry. Its extent still needs reconciliation with the promoted circuit.
- Darent Valley Path: only 20 way IDs overlap the 79 unique way IDs of NCN125;
  the walking relation contains 211 ways. Do not award the whole path from a
  shorter numbered route. The mapped geometry is 4.6% near open NCN at 10m.
- Viking: the owner identifies the trail with RCN15, but the recovered OSM record
  extends much further south than the advertised Thanet itinerary and measures
  roughly 88km of linework. Its entire extent must not be aliased to Viking.
  Screening reports 61.4% near regional reference and 1.2% near open NCN; those
  remain geometric diagnostics, not membership or completion totals.
- Sheerness Way: a current owner page is located; a bounded map probe found
  NCN174 but no separately named route. This is unresolved discovery, not proof
  that no mapped source exists. Do not substitute the whole of NCN174 for it.

The RCN15 route contains relation 12366413, a pedestrian multipolygon for
Broadstairs Seafront. The extractor now reports unsupported nested relations
and retains valid line members instead of crashing or treating an area perimeter
as route mileage. The candidate is explicitly geometry-incomplete. Comparison
reports carry source completeness and issues forward without approving links.
A dedicated regression check proves an area boundary contributes no route edges.
119 local NCN tests pass. No runtime, production progress or deployment changes.

Owner extent checks:
- https://explorekent.org/activities/heron-cycle-trail-the-hoo-peninsula/
- https://explorekent.org/activities/viking-coastal-trail/
- https://explorekent.org/activities/sheerness-way-cycle-ships-and-chips/
- https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/route-125/

The separately maintained 25-route checklist records these source discoveries
and unresolved extents. No copied owner GPX or prose is shipped as app data.


## Heron composite extent review — 16 October 2026

Heron's mapped source relation 21894 supplies 20,104.57m. Its two open
endpoints are exact existing nodes 33130417 and 33129969 in NCN1 relation
13512082. Selecting the 4,694.15m connection within that independently mapped
relation yields 24,798.72m of unique physical edges, consistent with Explore
Kent's published 24.8km circuit. The owner map corroborates the main trail plus
NCN1 closure; its historical main-route label is regional18. Geometry comes
only from OSM, not digitisation of the published map.

The explicit composite contains 1,242 unique physical edges, one connected
component, zero degree-one endpoints and two branch nodes. It records source
hashes, versions and exact vertex spans. Only selected edges of the connecting
ways are included. Physical coverage is deduplicated across source memberships.
This resolves the source composition; it does not yet approve activity access,
directional/alternative branch completion policy, or runtime matching. The
artifact therefore retains completion_ready=false.

Viking remains unresolved: RCN15 includes linework beyond the promoted Thanet
circuit and a nested pedestrian-area relation. The owner map confirms an inland
closure as well as the coast. A bounded inland probe identifies NCN1 relation
75880 (Dover to Canterbury), a further source lead rather than proof of Viking
membership. Neither the whole RCN15 nor an arbitrary geographic crop is accepted.

A reusable span-composition helper and six regression tests enforce source-way
membership, exact partial spans, shared-edge deduplication, coordinate consistency
and disconnected-node preservation. All 125 local NCN tests pass. This is reference
data preparation; no app deployment, database migration or user progress change.

Sources reviewed:
- https://explorekent.org/activities/heron-cycle-trail-the-hoo-peninsula/
- https://explorekent.org/wp-content/uploads/2015/03/the-heron-trail-higham-to-upnor-on-the-hoo-peninsula.pdf
- https://explorekent.org/activities/viking-coastal-trail/
- https://explorekent.org/wp-content/uploads/2015/03/Viking-Coastal-Trail.pdf
- https://api.openstreetmap.org/api/0.6/relation/21894/full.json
- https://api.openstreetmap.org/api/0.6/relation/13512082/full.json

OSM geometry © OpenStreetMap contributors, ODbL 1.0. Owner PDFs are reviewed
references and are not redistributed in the data package.


## Viking: distinguish the circuit from the coastal itinerary — 16 October 2026

Two published scopes have been reconciled separately. Explore Kent describes a
Thanet circuit; the network operator describes a shorter Cliffsend–Reculver
coastal itinerary. The whole RCN15 relation is neither scope. Explicit selections
from its existing line members now isolate both candidates, using exact junction
nodes at Reculver (28479614) and Cliffsend (29783125), without clipping geometry
or inventing connectors. This supersedes the earlier unresolved extent finding.

| Candidate | Selected source ways | Unique physical linework | Topology |
| --- | ---: | ---: | --- |
| Thanet circuit | 197 | 51.137 km | One component, no loose endpoints |
| Coastal itinerary | 143 | 34.046 km | One component, two endpoints |

These inventory lengths include parallel physical paths. Taking one chain per
shared-endpoint group gives 50.478–50.535 km for the circuit, and 33.435–33.479 km
for the coast. These are undirected length diagnostics, not routing instructions
or approved completion denominators. The coast agrees closely with the operator's
33.4km description. Five candidate alternative groups in the circuit (three on
the coast) require access and credit-policy checks; topology alone does not make
paths interchangeable. In particular, a roundabout or alternate promenade must
not accidentally force users to cover every physical branch for completion.

The Explore Kent page's embedded map attributes describe a third scope,
Sandwich–Reculver, unlike its circuit text/PDF. This discrepancy is recorded;
embedded map coordinates are not reused. Both candidates use OSM geometry only.
The Broadstairs Seafront multipolygon remains excluded, not converted into route
mileage. The selected linework is connected without its perimeter; this does not
approve all pedestrian-area interpretations or walking/cycling access.

Each candidate now has a checksum-pinned replay manifest with exact source-way
vertex spans, a compiled physical-edge inventory and a separate scope review.
The composition CLI refuses changed source bytes before producing output. Source
snapshots and diagnostic chain identities are retained in the assessment package.
Neither variant is completion-ready or changes user progress. The next steps are
alternative/access policy, reference identity reconciliation, then app integration.
128 local NCN tests pass, including pinned-source replay, source-change refusal,
and exclusion of nested pedestrian-area boundaries.

Replay from a source checkout (after extracting the assessment package):

```sh
python -m scripts.ncn_trial.extent_composition --manifest /path/to/viking-thanet-circuit-manifest.json --output /tmp/viking-extent.json
```

The manifest resolves its source JSON relative to itself and checks its SHA-256.
The separate review records the owner evidence; the CLI reproduces physical
selections and does not infer route membership, access or alternative policy.

Sources:
- https://explorekent.org/activities/viking-coastal-trail/
- https://explorekent.org/wp-content/uploads/2015/03/Viking-Coastal-Trail.pdf
- https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/viking-coastal-trail/
- https://api.openstreetmap.org/api/0.6/relation/8824/full.json

Geometry © OpenStreetMap contributors, ODbL 1.0. Owner maps are corroborating
references and are not redistributed or digitised into the candidates.


## Kent access evidence and completion contract — 16 October 2026

The selected-span access audit is now complete as an evidence inventory, not a
certification of present or historic rights. It replays exact source hashes,
geometry and spans before inspecting only selected ways/nodes. No journey data
or application routing is changed. All observations preserve their source tags.

| Scope | Source ways | Explicit positive foot tags | Foot not explicit | Foot context | Positive bicycle tags | Bicycle not explicit | Dismount tags |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Heron circuit | 68 | 10 | 58 | 0 | 11 | 57 | 0 |
| Viking Thanet circuit | 197 | 77 | 119 | 1 | 69 | 123 | 5 |
| Viking coastal itinerary | 143 | 71 | 71 | 1 | 64 | 74 | 5 |

Viking has four ways with general private access but explicit permissive bicycle
tags. Three also have permissive foot tags; way 4711579 lacks that specific foot
override and remains context-dependent. More specific mode tags are retained;
motor-vehicle restrictions are not applied to pedestrians/cyclists. Missing tags
do not establish prohibition or permission, and gates/bollards are observations,
not automatic route closures. OSM's dismount value is kept distinct from bicycle
permission and from foot prohibition. Five ways have that value; way 482774859
has a cycling condition. No conditional expression is evaluated.

The council's current coast guidance refers to local signage. The single mapped
seasonal condition cannot be treated as a complete coast-wide calendar rule or
historic compliance test. A current map tag does not establish what happened on
a past journey. Access evidence must remain separate from confirmed path identity;
a cycling activity does not prove riding every metre, and a dismount tag does not
prove that the user walked a particular segment. Preserve recorded mode and raw
journey evidence rather than inventing segment-mode changes or deleting journeys.

Heron exposes a further accounting trap: the same two junction nodes join a
24.331km circuit core and two local branches of 263.26m and 204.83m. Automatically
calling every same-endpoint path interchangeable would let a tiny branch replace
the entire circuit. The core is mandatory in the proposed completion partition;
only the two explicitly reviewed local branches are alternative candidates.
Viking's five candidate alternative groups remain explicitly enumerated. No
alternative mapping is approved merely from topology.

Completion implementation contract: a reviewed alternative may satisfy its local
logical section without requiring every branch. Keep that logical target separate
from actual unique physical distance. Count supported walking/cycling saved paths,
exclude driving, and union shared segments for combined progress. Current access
metadata must not silently erase historical evidence. Bulk history remains an
automatic activation task without a confirmation queue; targeted confirmation is
reserved for genuinely ambiguous new recorded path identity. This contract is
documented for implementation; no runtime credit rule is changed in this commit.

137 NCN tests pass. Nine added checks cover mode-specific overrides, motor/vehicle
inheritance, absent tags, dismount and conditional preservation, selected-node
bounds, barriers, tampered extents, source-tag refresh and directional member-role
preservation without inferring pedestrian prohibition. Next: reconcile selected
source identities against the versioned NCN reference and exercise saved-path
matching. Route-section credit bindings and app integration remain unimplemented;
this evidence pass is not an additional APK or database deployment.

Sources:
- https://wiki.openstreetmap.org/wiki/Key:access
- https://wiki.openstreetmap.org/wiki/Tag:bicycle=dismount
- https://wiki.openstreetmap.org/wiki/Conditional_restrictions
- https://www.thanet.gov.uk/info-pages/thanet-beaches-and-coast-public-space-protection-order-pspo-2024/


## Snapshot-bound named extents and saved-path matching — 16 October 2026

The three selected candidates (Heron circuit, Viking Thanet circuit and Viking
coastal itinerary) now compile into a portable walking-trial reference: 265
source ways, 371 selected-span sections and 2,990 unique physical edges. The
association JSON compresses to 40,770 bytes, and its projected reference geometry
to 63,571 bytes. Neither is deployed. No Postgres storage or migration is added.

The reference version binds the exact selected extents, projection and pinned
national snapshot SHA-256. This is contextual version binding, not an inferred
OSM-to-official-FID association. Named-extent sections remain a distinct scope;
full itineraries outside the national snapshot are not silently added to official
network totals. Existing 10m geometric screening remains diagnostic only. The
selected-extent screen finds most Heron linework near open NCN, Viking's coast
near the regional layer, and much of Viking's inland circuit outside the mapped
corridors. These observations never supply membership or journey credit.

`build_extent_reference` replays each selected extent against source hashes,
creates exact source-span target geometry, retains full source-way hashes for
saved-result identity checks and deduplicates shared physical linework. Its
walking-only associations round-trip through the portable bundle reader. The
reader verifies that exact-span targets actually lie on the declared source
way. Legacy association rows retain their existing reviewed-reference contract.

The saved-path assessor now distinguishes reviewed GIS targets from exact
selected source spans. For the latter, it clips already identified travelled
geometry to the selected extent, using only micron-scale numerical tolerance.
A long matched source edge crossing an extent boundary earns only the travelled
part inside it. The existing 3m GIS correspondence check is not used as a named
membership search. Unmatched, parallel, removed or unsupported-mode segments
earn nothing. Walking results cannot acquire cycling provenance by relabelling.

Eight new regression checks exercise boundary clipping, exact legacy-way
compatibility without raw GPS, nearby parallel paths, user removals, overlapping
named scopes, portable round-trip, wrong source targets, changed snapshot/extent
and driving/cycling exclusions. Existing reader checks also cover invalid target
contracts and older rows without the new field. All 145 NCN tests pass.

Public-source synthetic replays recover 4.181m of a partial Heron source edge in
either direction; a 7.764m Viking edge credits both named variants while remaining
7.764m in combined unique distance. A duplicate activity adds no distance. Removed
edges, nearby parallel geometry, driving and a cycling label on pedestrian output
all yield zero. These are manufactured diagnostic inputs on actual public source
geometry, not claimed real trips or access certification.

The three available private exports yield zero Heron/Viking credit. They are
negative controls only. The existing bounded Route 1 registry still gives
19.092m of combined unique coverage. No private coordinates or journey records
are included in this public assessment, only aggregate regression results.

The trial reference's physical distance is not yet a logical route-completion
denominator: alternate sections still need explicit credit bindings. Cycling
matcher provenance, nationwide source reconciliation, Android activation, map,
progress and achievements remain outstanding before the agreed beta gate.

Reproduction API: `scripts.ncn_trial.extent_reference.build_extent_reference`
takes `(scope, extent)` pairs, raw OSM snapshots keyed by relation ID, the
uncompressed national snapshot bytes and its independently pinned SHA-256. The
outputs are `TrialReference`, metadata and exact-span `KnownPath` associations;
existing bundle readers and `assess_saved_paths` consume them. Source way and
extent hashes are in the assessment manifests. No network fetch is required.


## Bicycle provenance capture and saved-node replay — 16 October 2026

Cycling now has its own bounded source-identity path. The backend preparation in
PR #168 requests node annotations only from the dedicated bicycle graph, labels
responses `osrm_bicycle_v1`, and adds `cycling_path_evidence`. Returned geometry,
normal journey distance and exclusion from ordinary road-completion attribution
remain unchanged. The request count and database schema are unchanged.

The shared collector records node chains against uniquely aligned leg spans in
existing overview geometry, with geometry/content checksums and data version
when supplied. Annotation count is never assumed to equal vertex count. Missing
annotations, discontinuous steps or repeated overview occurrences are held while
usable journey geometry remains available. It stores no duplicate coordinates.
The live probes did not supply data_version; no historic snapshot is invented.

The NCN registry's v2 contract includes the independently sourced OSM node chain
alongside each full source way and selected-span targets. Both walking and cycling
are supported in this bounded trial. Its reference version changes with the new
contract. Old walking-only bundle files are retained as historical evidence;
production release manifests would pin the intended version rather than mixing
registry/reference generations.

Cycling assessment requires the bicycle matcher contract and a unique ordered
node chain in exactly one registered source way. Unknown chains and chains shared
by multiple ways stay uncredited; geometry never selects between them. Only after
identity is resolved is geometry checked within 0.12m for observed six-decimal
router-coordinate quantisation, projected onto the identified source subchain,
and clipped to the selected named extent. This is not a nearest-path search.
Removed global geometry edges remain removed. Repeated activities/chunks union
on the physical basis; walking and cycling totals stay separate and their combined
coverage deduplicates the same physical travel.

Live tests sent only public OSM coordinates as synthetic observations. With the
backend request options, the Viking bicycle-router probe credits 500.434m against
both named variants and 500.434m once in combined unique coverage. The Heron probe
retains four uniquely aligned legs, holds three unaligned legs and credits only
0.954m after node identity checks; other chains remain unknown/ambiguous. These
are source/contract trials, not evidence that a person actually cycled the routes
or that the full network has been reconciled.

The v2 265-way associations compress to 53,011 bytes and the reference to 63,630
bytes. No Postgres reference rows or personal journey records are added. Existing
Android code retains the full matching response in processing_result, so no APK
is needed solely to preserve the new metadata when the backend is released.
Older cycling results lacking node evidence remain intact but earn no trial NCN
credit through a geometry-only fallback; a rematch may supply the missing evidence.
There is no new bulk confirmation queue. Current access tags do not prove what
happened within a recorded cycling activity or automatically reclassify segments.

Validation: 154 NCN suite tests plus five shared collector tests pass (159 total).
The added cycling checks cover partial spans, edits/missing evidence, absent or
changed source chains, ambiguous ways, removals, driving exclusion, reverse travel,
bounded quantisation, separate mode totals/combined union and bulk activation
without a review queue. Backend #168 has 80 checks: 75 pass locally with five
Postgres integration checks deferred to CI; both backend workflows are green.
No production deployment or APK is made in this step.

Still outstanding: nationwide identity reconciliation, explicit logical alternative
credit bindings, Android collection activation and the agreed map/progress/initial
achievements beta gate. The bounded cycle proof is not national completion-ready.

OSRM annotation contract: https://project-osrm.org/docs/v5.24.0/api/#annotation-object
Public geometry attribution remains OpenStreetMap contributors, ODbL 1.0.


## National discovery and member-ID census — 16 October 2026

This is a source-discovery and membership-identity inventory, not a production
network release or a verified physical crosswalk to the published GIS.

| Measure | Result |
| --- | ---: |
| Published numbered keys (227 NCN + 26 RCN) | 253 |
| Keys with same-number, same-network source candidates | 240 |
| Keys with explicit UK namespace/operator metadata | 205 |
| Distinct candidate way IDs | 77,313 |
| Distinct way IDs under UK-metadata-anchored profiles | 65,694 |
| Root profiles checked against current OSM | 408 |
| Current OSM relation records including children | 603 |
| Geographic discovery queries | 221 |

Counts of keys are presence/candidate counts, not percentages of network distance,
full-route completeness or app-supported coverage. A short source profile can
supply a candidate number without defining the entire promoted route. Candidate
roots overlap, so way identities are unioned rather than summed.

The geographic queries cover the published snapshot's bounds, including adjacent
areas of Ireland and France. Their 5,202 top-level catalogue objects include local
and neighbouring-country routes; they are not 5,202 UK NCN routes. Three dense
cells still reach the configured response cap. The pass therefore does not claim
exhaustive discovery. Numeric global searches for 323, 601 and 689 are also capped;
absence from these results is not evidence of absence from OSM.

The source index keeps original relation tags, versions, timestamps and membership
role paths. The inventory contains way identities; source geometry and node chains
are not part of this extract. Member-list completeness is explicitly separate from geometry,
access, country scope and correspondence with official GIS. No extra Postgres
rows, personal journeys, Firebase publication or APK are involved.

Foreign searches cannot fill UK gaps merely by sharing a number: the returned
source NCN410 explicitly belongs to US:US. The unanchored global candidate named
Part of Bay Cycleway is a lead for 700, not an approved full-route substitute.
The source for 820 is classified lcn and cannot silently become NCN820. Likewise,
LCN89 records do not substitute for published RCN89. The reconciliation retains
these disagreements instead of overwriting source classifications.

Thirteen keys remain unresolved at this stage:
NCN207, 253, 274, 323, 410, 422, 524, 601, 689, 700, 776, 820; RCN89.
Thirty-five candidate keys have no explicit UK namespace/operator marker. That is
missing metadata, not a claim that those routes are outside the UK; Lôn Las Cymru
is one example already examined separately in the named-route assessment.

Three fetched profiles expose structural issues. RCN15 contains the previously
identified Broadstairs Seafront area relation, the global/locally classified
LCN+89 search lead contains another area relation, and Route1 includes an empty
link relation for Erith station. The extractor now flags empty relations even
when the OSM response omits the members field. Area perimeters never become route
mileage. Current source member lists otherwise remain useful as bounded facts.

The new code comprises adaptive capped-response discovery, reusable batch
membership extraction and semantic numbered-route lead reconciliation. Sixteen
regressions cover truncation, budgets, source errors/conflicts, unsupported object
types, role-path preservation, empty/missing/cyclic/area children, foreign-number
collisions, explicit network classification and no automatic physical approval.
All 175 trial checks pass locally (170 NCN suite plus five shared collector tests).

Reproduction: load the current-relations files listed in
current-members-fetch-manifest.json, pass their bytes and seed_relation_ids to
scripts.ncn_trial.catalogue_extract.extract_membership_catalogue, then pass the
published numbered inventory, extracted profiles and geographically discovered
root IDs to scripts.ncn_trial.numbered_reconciliation.numbered_candidates.
Raw discovery responses, request URLs, hashes and provider status observations
are retained. The comparison is reproducible without querying a live database.

Next: resolve the held UK/numbered-source leads and establish the physical
source-to-official-GIS correspondence. Only then can nationwide saved-path links
become eligible for the activation consumer. Existing bounded walking/cycling
trial coverage remains unchanged; map/progress/achievements integration is pending.

Sources: Waymarked Trails cycling API (https://cycling.waymarkedtrails.org/api/v1),
OpenStreetMap API (https://api.openstreetmap.org/api/0.6), and the previously pinned
National Cycle Network Public GIS snapshot. Geometry/membership source attribution:
© OpenStreetMap contributors, ODbL 1.0. No Waymarked Trails server code is included.

## Bidirectional physical pilot: NCN177 — 16 October 2026

The first route-scoped national crosswalk pilot compares current OSM relation
3671326 with the pinned official NCN177 geometry. It retains the source's explicit
network/ref, requires the official snapshot checksum and uses one local approximate
metric projection. Different route numbers are reported as nearby competing
sections and cannot fill gaps for the selected number. Physical lengths are
unions, so overlapping source ways and duplicate linework are not summed.

| Diagnostic | Result |
| --- | ---: |
| Source ways, complete member geometry | 66 |
| Source physical union | 13.124 km |
| Official NCN177 sections | 33 |
| Official physical union | 15.147 km |
| Source within 3m / 10m / 25m of official route | 70.3% / 98.2% / 99.5% |
| Official route within 3m / 10m / 25m of source | 60.8% / 85.1% / 95.8% |

These are corridor diagnostics, not matched progress, positional accuracy claims,
or a completion denominator. In particular, widening tolerance to 25m conceals
much of the discrepancy and cannot justify an identity association across nearby
parallel paths. At 10m, approximately 2.252 km of official geometry is outside the
source corridor. FID7345 (964m) and FID6680 (882m, about 870m outside) account for
most of that gap; both are published open, traffic-free Main Route sections.
Their cause remains unresolved: no source edits, substitutions or approvals have
been made. Conversely about 233m of the source lies outside the official corridor.

The source geometry is complete for its relation but does not establish complete
correspondence to the official route. No association is approved, no access is
inferred, and no app coverage changes. This validates why the next stage needs
both directional measurements and explicit review of disagreeing extents.

Reproduce using scripts.ncn_trial.physical_crosswalk, the existing pinned network,
and https://api.openstreetmap.org/api/0.6/relation/3671326/full.json. The fetched
source SHA256 is 3a6babac34ec236112fa55293db3b39937687287a6a84802f618e6203cb56fc0;
a later live response may differ and should be treated as a new snapshot. The
comparison output records source and network hashes, relation version/timestamp,
projection origin, per-way candidates and per-official-section corridor lengths.
Six additional regressions cover partial routes, different-number collisions,
parallel paths, physical union, duplicate identities and missing/invalid geometry.

### Investigation outcome: parallel northern/southern alignments

A bounded current OSM map extract covering 0.453,51.395–0.484,51.401 resolves the
nature of the two largest discrepancies. These sections are at the Watling Street
approach to Strood, not evidence that the entire Route177 source is incomplete.

| Official feature | Nearby cycleway matching its northern alignment | Relation3671326 uses |
| --- | --- | --- |
| FID7345 | way44332298 | southern ways94920359 and adjoining cycleways |
| FID6680 | ways1302385780 and1251416153 | southern way62216492 and adjoining cycleways |

All four named northern ways in the public fixture (three cycleways plus separate
footway1460076188) are absent from the fetched NCN177 relation's members. At 10m,
the member alignment represents less than 1% of these two official sections;
the three northern cycleways represent more than 94%. Thus this is not simply
missing physical paths in OSM. It is an explicit alignment disagreement between
the official GIS and the route relation. The northern footway and cycleway are
separate mapped objects yet both fit FID7345's 10m corridor over 94% of its length.
Neither proximity nor their shared corridor can distinguish activity identities.

Disposition: **alignment disagreement explained; source-to-official association
held**. The southern relation membership remains a source fact; the northern
published line remains an official reference fact. Neither replaces the other.
We have not established that both sides carry NCN signs, a directional route
policy, or that one source is outdated. No OSM edits are made, no northern ways
are silently inserted into the relation, and the southern alignment receives no
credit for these official extents. Walking on the separate northern footway is
also not automatically treated as cycling on its neighbouring cycleway.

The route custodian's Route177 page corroborates a signed mixed on-road and
traffic-free route along Watling Street but does not specify these individual
side-of-road alignments. It also describes two separate open sections, so the
local 13.124km source /15.147km GIS figures must not be presented as a verified
whole promoted-route length (the page lists18.7km).
https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/route-177/

Reproducible evidence is pinned in tests/fixtures/ncn-177-discrepancy/public-snapshot.json:
official geometries, all66 current relation source ways with node IDs/coordinates,
relation version/member list, three northern cycleways and the distinct footway.
The bounded area response SHA256 is
4f95ac2d3ed8775dab5445c226c151100eda39a3f7e7d254ab9d45a9ede022fc.
This fixture contains public map evidence only, no user journeys or personal GPS.
Four real-data regressions preserve the disagreement and prohibit a misleading
claim that nearby linework establishes relation membership or association approval.
All185 checks pass locally (180 NCN +5 shared collector), including existing
walking/cycling source identity, clipping, repeated coverage and driving exclusions.

Next: retain the hold while reviewing extent-specific independent evidence, and
continue the same bidirectional audit on the remaining numbered inventory. The
result is a classified, reproducible exception, not a reason to increase tolerance
or block all other route reconciliation. Whole-route completion remains unavailable
until route scope and these held associations are resolved.

## Explicit alternative accounting — 16 October 2026

Product decision: accept both explicitly identified north/south alignments as
alternatives for equivalent Route177 extents. This supersedes the previous policy
hold for the bounded common interiors below; it does not assert that the OSM
relation contains northern ways or that every nearby road qualifies. Endpoint
joins and remaining official geometry still need separate span associations.

The new AlternativeLedger consumes the same snapshot-versioned Evidence produced
by the existing saved-path identity matchers. Physical spans map via explicit
anchor pairs onto logical sections. This happens after identity matching; it is
not a wider corridor matcher. Mode-specific logical intervals and their combined
union count each extent once. Different branch lengths remain distinct in the
physical-distance summary, while completion uses one declared canonical logical
length. Partial travel is interpolated within that local span, including reverse
orientation, rather than turning an entire branch or route into an all-or-nothing
achievement. Equivalent partial intervals are unioned across alternatives.

A pinned Route177 pilot binds 1,203.560m of common interiors:

| Official extent | Longitude anchors | Cycling paths | Walking paths |
| --- | --- | --- | --- |
| FID7345 interior | 0.4610–0.4690 | 44332298 north /94920359 south | 1460076188 north footway /94920359 south |
| FID6680 interior | 0.4704–0.4795 | 1302385780 north /62216492 south | 1302385780 north /62216492 south |

These fixed longitudinal cross-sections define unique source-line intersections
in the pinned fixture. Their along-path physical positions and canonical official
lengths are persisted in alternative-bindings.json; they are not selected from
a user's GPS or generated by a proximity search at activation. Full source
geometry hashes and the public snapshot hash bind the manifest, whose hash also
versions the trial reference. The distinct northern footway is walking-only;
way44332298 is cycling-only in this registry because its pedestrian access has
not been separately established. This does not restrict walking on the nearby
explicit footway or imply walking is excluded from NCN generally.

The route identity is deliberately NCN:177:reviewed-pilot, not a full-route
completion denominator. Full physical source-way identity is retained for saved
matching, then only the bounded intervals contribute logical coverage. The
1.204km fixture does not claim that the remaining two feature tails or joins
have been approved. No source data was rewritten to manufacture membership.

Thirteen new checks cover either-side equivalence, reverse partial unions,
repeat imports, mode correction/removal, driving exclusion, unbound roads,
mode-only spans, bad/overlapping bindings and reference-version changes. Six of
these run synthetic saved walking/cycling exports against the real pinned OSM
geometry through the existing assess_saved_paths consumer. They verify full and
partial travel, both second-section alignments, distinct footway/cycleway credit,
and correction removal. These are synthetic matcher-output contracts, not claims
that a live router or a real recorded trip selected those ways. No timestamps or
GPS accuracy have been fabricated.

All198 checks pass locally (193 NCN +5 shared collector). Application integration,
activation cache/version migration for this logical model and national inventory
bindings remain pending. Nothing is deployed and no user progress is changed.

## Full disputed-feature spans and activation checkpoint — 16 October 2026

The previous common-interior fixture is superseded by explicit end-to-end source
chains across both disputed official features. The policy remains permissive for
both reviewed sides, with no invented connection beyond a mapped source endpoint.

| Logical section | Canonical length | Eligible alternatives |
| --- | ---: | --- |
| Full FID7345 | 964.408m | Connected northern cycling chain /northern pedestrian chain /southern shared chain |
| Shared FID6680 extent | 673.313m | Northern shared cycleway /southern shared cycleway |
| Remaining FID6680 northern extension | 209.391m | Northern shared cycleway only |

Together these account for 1,847.113m, the full physical lengths of these two
published features. This is still NCN:177:reviewed-pilot, not the full Route177 or
the intervening published features. Travelling the southern route alone cannot
complete the northern extension. The southern terminal node248536946 meets A2
road ways23158220,23158221 and106179151; those roads are retained as context only,
not silently promoted into NCN links by adjacency. The northern terminal is the
observed node11632889922, approximately1.018m from the official feature endpoint.
This terminal is an explicit reviewed endpoint correspondence; no coordinates
are extrapolated and no metre of synthetic connecting path is created.

The public fixture now retains the additional northern crossing/connector ways
and the separate pedestrian approaches. build_route177_alternatives enumerates
signed way chains, verifies identical shared node IDs and coordinates, clips the
fixed reviewed endpoint crossings and persists physical/logical anchors. Even two
identical coordinates with different node IDs cannot manufacture a chain join.
Physical anchors from reverse ways are clamped only for floating-point roundoff.
Branch distance is normalized locally to its canonical section length; it never
changes the physical travel summary. The27 bindings reference21 distinct physical
ways. Adjacent source spans cover each logical itinerary continuously without
logical overlaps or gaps. The explicit mode registry avoids assuming pedestrian
access to cycleway44332298: walking uses footway1460076188 and its separate mapped
approaches instead. No national membership claims are rewritten by this policy.

ActivationTrial now accepts an explicitly identified accounting factory. Its
default physical ledger behavior and existing callers remain unchanged. The
Route177 factory supplies AlternativeLedger, retaining on-activation historical
assessment, bounded batching, activity replacement and correction/deletion rules.
Checkpoint records retain an accounting key as well as the reference version;
old physical-only checkpoints remain readable with the default ledger, while an
alternative checkpoint cannot silently restore into that ledger. The manifest
hash versions the reference, so a policy/binding change rejects the stale
checkpoint and requires a fresh archive assessment. Deactivation/reactivation of
unchanged history retains completed rows without reassessing or generating a
bulk-review queue. There are no production scheduler, APK or database changes.

Validation: all210 local checks pass (205 NCN +5 shared collector). New coverage
includes deterministic manifest regeneration, real-node join integrity, canonical
interval continuity, missing eastern-tail protection, full multiway walking and
cycling alternatives, paused checkpoint resume, retrospective activation with
zero prompts, archive deletion, mode correction, stale policy re-assessment and
rejection of a mismatched checkpoint accounting model. Real-map tests continue to
use synthetic saved matcher outputs, not claimed live trips.

Next implementation boundary: generalize these explicit alternative bindings
across the nationwide route inventory, retain unreviewed gaps, then connect the
versioned consumer to Android activation, map/progress and initial achievements.
This trial has not yet satisfied the agreed app beta gate.

## Nationwide physical pass completed — 16 October 2026

The next national checkpoint is recorded in
[ncn-national-physical-reconciliation.md](ncn-national-physical-reconciliation.md).
All 305 source detail downloads and all 253 numbered-key reports are complete,
without request, parse or comparison failures. There are 48 close bidirectional
candidates, 79 extent differences, 60 offset-sensitive cases, 23 partial-geometry
keys, 30 scope-review keys and 13 missing-source keys. These are diagnostic states,
not national progress approval. All 224 local checks pass. Explicit reviewed
Route177 alternatives are preserved; no database or app deployment has occurred.

## First current node-backed source-span catalogues — 16 October 2026

The 48 strongest national candidates now have current full OSM identity snapshots.
47 keys produce bounded catalogues for 3,778 distinct ways; 469 ways remain held.
These support exact source-span matching, not full-route completion. Details and
mode/extent policy are in [ncn-confirmed-source-spans.md](ncn-confirmed-source-spans.md).
All 233 local checks pass. Production publication and Android integration remain
pending; reviewed Route177 logical alternatives are retained separately.

## Fixed captured-inventory totals — 16 October 2026

[ncn-inventory-completion.md](ncn-inventory-completion.md) records the next pass:
48 fixed source-inventory plans, 3,999 eligible ways and 10 reviewed two-gate
roundabout groups. Unresolved mileage stays in the denominator. Closing and
repeated source edges are now accounted for correctly. All 247 local checks pass.
These totals cover the captured member inventories; all published-route readiness
flags remain false and Android integration is still pending.
