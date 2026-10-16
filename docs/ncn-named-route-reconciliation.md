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
