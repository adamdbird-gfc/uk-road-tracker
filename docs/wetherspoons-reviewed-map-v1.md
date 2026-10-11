# Reviewed UK Wetherspoons beta map — 0.25.184 (230)

The OSM extract retrieved on 11 October 2026 still contained the former
Widow Frost. Extraction freshness and venue accuracy are different. Adam reports
its Wetherspoon closure in 2023; a dated local CAMRA report from 1 October 2024
confirms the former pub under another name/operator. No exact closure date is
invented. The stable ID remains in an independent exclusion ledger.

Only four individually checked map venues are packaged: Robert Pocock,
Stag and Pheasant, Benjamin Huntsman and Sheffield Water Works Company.
Names/positions/towns remain OSM-derived; the source links in the ledger document
operator-status checks. Distinct nearby Sheffield pubs remain separate.
The Stag postcode discrepancy remains a review issue; postcode is not displayed
or overwritten from a restricted directory.

One former venue is excluded and 826 candidates are still unreviewed. The
candidate catalogue remains audit-only. A separate asset is approved exclusively
as a partial beta map, with coverage_complete=false, visit_enabled=false and
completion_target=null. The map and Collections screen state that it is incomplete.
No claim of 821/827 current UK pubs or a complete directory is made.

## Repeatable review

The ledger collections/wetherspoons-map-reviews-v1.json is independent of source
refreshes. Unknown/unreviewed venues do not activate. Exclusions persist even if
stale OSM tags remain or the object disappears. Keep exclusions when maintaining
the ledger; deleting an exclusion is a deliberate review action, not a refresh.
Active reviews older than 90 days at rebuild, changed names, replaced aliases or
label positions moved by more than .0005 degrees are withheld for review.
This is a rebuild gate, not a guarantee of live operating status.

Obtain the audit candidate catalogue from the main branch's collections directory
and rebuild from its exact contents (no official directory scraping):

    python3 scripts/build_wetherspoons_map.py --catalogue /path/to/uk-wetherspoons-candidates-v1.json --reviews collections/wetherspoons-map-reviews-v1.json --as-of YYYY-MM-DD --output android-prototype/app/src/main/assets/collections/uk-wetherspoons-map-v1.json
    python3 -m unittest discover -s scripts -p test_wetherspoons_map.py -v

The app loads a bounded bundled asset, never performs pub lookups per GPS point,
and rejects audit/unreviewed data. Missing/malformed assets leave journey maps
available. Pub popups are informational and never use the Services ledger or
offer a visit tick. Premises and strict stop evidence need their own approval.
In particular, Robert Pocock remains a point-only source, not a GPS boundary.

## UI and release scope

The selector now says Showing on map, separates the current selection onto a
second line, has spacing around it and opens a navy/jade/gold Roadprints picker.
The selector still appears only for multiple installed collections. One selected
collection uses the existing clustering renderer; pub cards show name, town,
map-only status and OSM/ODbL attribution. Viewport and journeys remain intact.
Recorder, matcher, importer, Services evidence thresholds, purchases and pub
achievements/progress are unchanged. Device appearance and map pin acceptance
still need testing; automated tests do not establish field accuracy.
