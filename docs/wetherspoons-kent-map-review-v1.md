# Regional map review — 0.25.186 (232)

Reviewed on 11 October 2026 against current operator pub pages, with individual
source links and decisions recorded in collections/wetherspoons-map-reviews-v1.json.
The historic Kent candidate group follows the existing county definition A,
including relevant southeast London boroughs. This is a review of existing
candidates, not evidence of complete regional coverage or a current UK total.

Of the 34 remaining candidates in that group, 33 are approved for informational
map pins. Robert Pocock was approved earlier. The packaged map now contains 37
venues nationwide; one former pub remains excluded and 793 candidates remain
unapproved. Individual Wetherspoons markers now use W; clusters retain counts.

Furze Wren (spoons-osm-w965765986) is held for location reconciliation: the OSM
candidate gives 6 Market Place, DA6 7DY, while the current operator page gives
1 Mayplace Road West, DA7 4JD. The hold does not assert that the pub has closed.
It remains hidden until its OSM-derived position is reconciled. The builder
preserves this distinction from a former-pub exclusion and reports the held ID
under needs_rereview.

The County Hotel's OSM and operator postcode differ (TN24 8TH / TN24 8TD), but
street and house number agree. The issue is retained in the review record.
Names, towns and coordinates remain OSM-derived; operator checks do not copy
directory coordinates or overwrite source data.

Approval remains map_only_partial_beta, coverage_complete=false,
visit_enabled=false and completion_target=null. No visit evidence, achievements,
progress denominator or recorder/matcher/importer behavior changes. Airport and
station pubs still need premises-specific evidence before visits can be awarded.
The previous pins-only drawing crash fix remains in place; the user confirmed
that 0.25.185 worked on their device.
