# Final travel achievements — 0.25.164

All 12 fixed landmark cards appear immediately. Sightseer upgrades at 3, 6 and
12 distinct landmarks and lists completed/remaining targets. Geometry is loaded
only during the existing background scan; cached progress remains immediate.
Fresh retained-journey evidence determines awards after edits and deletions.
Milestone recognition history persists, so regaining a level does not celebrate
it twice. Existing Angel/A1 and Stonehenge/A303 IDs and rules are preserved.

## Evidence rules

New landmarks use the retained matched `processing_result.geojson` route, not
whole-road shapes, raw nearby GPS or train tracks. Walking/running, driving/bus
and saved cycling routes qualify where appropriate. Failed matches cannot grant
landmarks. Removed edges and removed named roads are excluded. Known underground
features are excluded. Bridge/promenade/M4 awards require a contiguous run,
both reference endpoints and at least 95% of the defined reference stretch;
normal lane/pavement offsets are tolerated, perpendicular crossings are excluded.

| Landmark | Qualifying evidence |
| --- | --- |
| Big Ben | Westminster Bridge, approximately 253 m; 25 m lateral tolerance |
| Blackpool Tower | Promenade between latitudes 53.8147 and 53.8170, approximately 257 m; 55 m allows the seafront pavement |
| Humber Bridge | Full mapped A15 bridge, approximately 2,212 m; 25 m tolerance |
| Trent Bridge | Full A60 bridge, approximately 129 m; 25 m tolerance |
| Bullring Bull | At least 10 m in the 22 m immediate sculpture area, on foot only |
| Ness Point | At least 10 m in the Euroscope polygon plus 15 m, on foot/cycle |
| Windsor Castle | Full M4 stretch between longitudes −0.625 and −0.585, approximately 3,165 m; driving/bus, matched M4 reference, 35 m tolerance |
| Loch Ness | At least 5,000 m of distinct A82/B852/B862 lakeside road in one journey; roads clipped to within 300 m of the mapped shore, 25 m route tolerance |
| Conwy Castle | At least 10 m on foot in the castle footprint plus 25 m, or full adjacent A547 road bridge, approximately 199 m, 25 m tolerance |
| Stormont | At least 10 m within the Parliament Buildings footprint plus 45 m; estate entrance does not count |

Loch Ness uses approximately 5 m reference intervals counted once per journey,
so repeat/out-and-back travel cannot multiply the same qualifying stretch.
Separate short journeys cannot combine into the one-journey requirement. UI
requirements respect the global miles/kilometres preference. The legacy road
crossing set retains its rules; its Humber anchor is corrected from an erroneous
Hull location to the mapped bridge at −0.450, 53.708.

Groundhog Day requires five distinct local Monday–Friday dates (not necessarily
consecutive), the same mode, start/end, ordered broad route and direction. Imports
qualify. A missing timezone uses the timestamp offset; legacy UTC records fall
back to Europe/London. Invalid dates/zones, failed/unfinished captures, routes
shorter than 50 m or with fewer than four distinct vertices, and edited incomplete
routes are excluded. Surface modes use matched routes; completed train journeys
may use a bounded raw route fingerprint. Plane/ferry are excluded. Walking and
running remain separate modes, as do bus and driving. Lengths must agree within
20%; all 65 distance-normalised route anchors must agree within a distance-scaled
GPS tolerance capped at 35 m on foot, 60 m on road/cycle, 120 m for train. Ordered
anchors distinguish opposite directions, loops and same-endpoint detours. Five
representatives per pattern bound memory; raw capture histories are skipped and
raw route input is streamed to at most 258 points.

## Offline references and attribution

`app/src/main/assets/landmarks/reference.json` is the versioned, committed source
of qualifying geometry. © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright),
[ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/). Data fetched 7 October
2026 using the public OSM map API, Overpass and Nominatim. Each reference records
its exact OSM object IDs; the source responses' SHA256s are embedded. The builder
`scripts/build_landmark_reference.py` consumes the named saved responses and
produces the offline asset; it performs no network access. Geometry is packaged
in the APK and never downloaded during achievement evaluation.

Key area sources: Bull node 1101446898 (not Ozzy at New Street); Ness Point
Euroscope way 224261295; Conwy Castle way 52467063; Stormont Parliament Buildings
relation 6010674; Loch Ness water relation 4023212. Humber roads 3996808/4063883,
Trent roads 4440665/29102875 and Conwy bridge roads 151369882/151369883 identify the
actual qualifying road bridges. Supporting official references:
[Humber walking/cycling](https://www.humberbridge.co.uk/plan-your-trip/walking-and-cycling/),
[Bullring](https://www.bullring.co.uk/about-us),
[Stormont grounds](https://www.niassembly.gov.uk/visit-and-learning/planning-your-visit/visit-car-publictransport/).

Coverage is evidence based: unmatched or too sparse routes do not gain awards.
Cycling matcher integration and the absent Northern Ireland settlement inventory
remain separate work; this release does not change their availability.
