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
