# Determination of six zero-association inventories — 18 October 2026

Follow-up: [NCN228 now has a reviewed catalogue and the NCN626 investigation has identified NCN601 linework](ncn-numbered-alternative-review.md). The original dispositions below are preserved as a dated checkpoint.

Retain NCN228 as a numbered alternative awaiting an explicit completion rule;
retain NCN626 as an NCN route with insufficient official reference geometry.
Defer RCN11, 17, 71 and 82 from activation pending maintained regional evidence.
None of these decisions establishes that a route is closed or no longer exists.
All six remain in the research inventory with their distance totals preserved.

| Inventory | Evidence in the pinned official dataset | Determination and condition for admission |
| --- | --- | --- |
| NCN228 | 15 open features, all Alternative Route; about 5.61 km of official linework | Retain as a numbered alternative. Review its own scope, any parent/shared physical bindings, and its completion rule before admitting verified source spans. Do not silently relabel it as Main Route. |
| NCN626 | One open Temporary Diversion, about 81 m; no main-route features | Retain as an NCN reference-data gap. Obtain authoritative geometry for the published open sections, distinguish proposed connections, and recheck access. The diversion cannot stand in for the whole route. |
| RCN11 | 20 open features with RouteCat=N/A | Defer regional activation. Obtain maintained regional scope and category evidence; resolve source/official extent differences and access/branch holds. |
| RCN17 | 65 open features with RouteCat=N/A | Defer regional activation. Confirm regional scope independently and resolve extent/branch exceptions. Keep RCN17 distinct from NCN17. |
| RCN71 | 67 open features with RouteCat=N/A | Defer regional activation. Obtain maintained regional geometry; the captured source inventory is substantially longer than the official extent. Keep RCN71 distinct from NCN71. |
| RCN82 | 122 open features with RouteCat=N/A; two Main Route features have null OpenStatus | Defer regional activation. Confirm scope, status, geometry, access and branches. Null is not evidence of Open. Keep RCN82 distinct from NCN82. |

The publisher's [field metadata](https://services5.arcgis.com/1ZHcUS1lwPTg4ms0/arcgis/rest/services/National_Cycle_Network_Public/FeatureServer/0?f=pjson)
explicitly distinguishes national and regional networks and states that the
regional network is not maintained/updated by Sustrans. An N/A category is not
evidence that a route is a main route, closed, or declassified. These decisions
apply only to the six reviewed keys; RCN90 and other supported inventories are
outside this review. Any later regional admission needs an explicit evidence
contract rather than a global relaxation of the main-route check.

## Why the two NCN routes stay in the candidate inventory

For NCN228, the Trust's [Sussex and Surrey map description](https://shop.walkwheelcycletrust.org.uk/products/central-sussex-and-south-surrey-cycle-map-7)
lists National Route 228 from Faygate to Crawley. The map edition is May 2021;
this corroborates its identity, not current detailed geometry. The pinned
official dataset identifies the linework as an open alternative. Comparing
all open categories at 10 m leaves about 47 m of official geometry outside the
source corridor and 42 m of source outside the official corridor. This supports
a focused alternative review; neither corridor result grants journey credit.

For NCN626, the Trust's [route page](https://www.walkwheelcycletrust.org.uk/find-a-route-on-the-national-cycle-network/route-626/)
explicitly identifies it as NCN and describes two disconnected open sections
between Oldham and Ashton-under-Lyne, with proposed connections. Its headline
distance is 6.9 km; that rounded figure is not a geometry source or a replacement
denominator. The captured source inventory is about 6.64 km, whereas the pinned
official dataset has only an 81 m diversion. Missing qualifying geometry must
not be interpreted as a nonexistent route or a continuous open connection.

Source way 359405104 remains restricted: its captured tags include
`highway=construction` and a note describing a damaged, closed bridge. The
note/check date is old, so this is a reason to reassess current status, not a
fresh inspection. Permissive bicycle/foot tags do not override that construction
hold. Authoritative open-section geometry and coherent source evidence are
required before the route can acquire supported associations.

## Regional geometry also needs review

Ignoring the category gate would not resolve the four regional inventories.
At a 10 m diagnostic corridor around all officially open linework, the source
geometry outside that corridor is approximately 28.8 km for RCN11, 9.3 km for
RCN17, 75.9 km for RCN71 and 27.6 km for RCN82. These differences may reflect
scope, outdated linework or mapping offsets; they do not identify which roads
are correct. Access, branch and source-geometry holds remain where recorded.

## Accounting and verification

The six existing plan/association pairs were read through the ordinary consumer
with their independently pinned hashes. All have empty associations, zero
supported coverage, and their complete canonical inventory distance retained
as unsupported. The full official snapshot SHA256 was checked before selecting
the six route keys; feature identities, category/status groups, source relation
versions, input hashes and diagnostic results are retained in
[the machine-readable determination](data-trials/ncn-zero-association-determination-2026-10-18.json).

Supported catalogue totals remain **181 keys and 43,043 distinct source ways**.
Those totals are assessment counts, not fully certified published routes or
user journey completion. The Android internal trial remains at eight routes.
This is a documentation and evidence disposition; no matcher, dataset,
denominator, association, application or release was changed.

The complete source capture was not refreshed. Findings refer to the pinned
official/source snapshots, supplemented by the publisher pages checked on
18 October 2026. The reproducible evidence bundle contains the review script,
six input plan/association pairs, official features and the generated report.

Geometry and membership: © OpenStreetMap contributors, ODbL 1.0.
Official extent: Walk Wheel Cycle Trust, OGL 3.0 with the original snapshot
attribution retained in the preceding reconciliation bundle.
