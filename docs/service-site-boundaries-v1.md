# Service visit footprints

The main building at Warwick Northbound is outside the old universal 125 m circle around its catalogue pin, despite being inside the mapped services grounds. This caused valid check-ins to fail.

The services catalogue now contains offline MultiPolygon footprints for all 112 existing collection identities. Boundaries come from the pinned OpenStreetMap service ways/relations retrieved on 16 October 2026, including mapped sites on separate carriageways and inner holes. Norton Canes retains its existing manual collection ID and uses OSM way 61460820. No collection identity or completion target changes.

Mapped motorway and trunk carriageways are removed from each footprint. Exclusion half-width is the greater of 15 m for motorways (12 m for trunk roads) and 1.8 m per mapped lane plus 6 m. These margins are conservative estimates, not surveyed carriageway edges. Site outlines are OSM data, not independently surveyed or individually certified premises boundaries. This work improves qualification for the existing catalogue; it does not certify that the catalogue contains every UK service area.

Automatic GPS visits and live check-in use the same footprint and require the reported accuracy circle to fit within it. Dwell still requires at least four valid fixes spanning three minutes, no gap exceeding 90 seconds, speed no greater than 2 m/s, and positions remaining within 80 m of the first stop fix. A brief poor-accuracy fix is skipped without inventing dwell; the next usable fix must still satisfy the gap limit. Overlapping candidate sites cannot receive automatic credit by list order. Missing old footprints retain the previous circle policy; malformed supplied footprints fail closed. Existing legacy Timeline evidence remains distinct from raw GPS dwell.

History scans include catalogue revision and evidence-policy version, so saved raw evidence is reconsidered after upgrade even without journey edits. Explicitly dismissed records retain their existing suppression. The bundled services revision is increased to supersede older downloaded point-only catalogues. Live feedback reports integer metres **to the catalogue pin**, separately from GPS accuracy; that pin distance is not distance outside the site.

## Reproduction and verification

`collections/service-site-boundary-source-v1.json.gz` is a minimal OSM source snapshot containing only collection IDs, site outlines and nearby motorway/trunk linework with lane counts. It excludes contributor details and user journey data. Attribution: © OpenStreetMap contributors, ODbL 1.0.

With Python and `shapely==2.2.0` installed:

```sh
python scripts/build_service_site_boundaries.py --check
python -m unittest discover -s scripts -p test_service_site_boundaries.py
python scripts/generate_venue_collections.py --check
python scripts/test_service_collection_metadata.py
python -m unittest discover -s scripts -p test_venue_catalogue.py
```

The audit JSON records source IDs, area, disconnected parts and adjacent road exclusions for every entry. Generation aborts on missing IDs, invalid/unclosed polygons, missing relation outlines or empty resulting grounds. The source snapshot must be refreshed and checked when catalogue membership changes. The existing general catalogue builder preserves attached footprints for matched stable identities; automatic regeneration on script push is removed so a code change cannot overwrite the pinned catalogue with new live data.

`ServiceSiteBoundaryTest` exercises production Java geometry and dwell code, including synthetic dwell at the public OSM Warwick main-building centroid (way 93201761), opposite-side rejection, excluded road holes, disconnected grounds, uncertainty at edges, ambiguous sites, invalid geometry, poor fixes, gaps and passing traffic. The fixture is entirely synthetic, with invented relative timestamps and accuracy values; it contains no user GPS samples or journey data. Framework tests additionally exercise live check-in, automatic visit persistence, duplicate replay and history rescanning. Standard Android checks run unit tests and lint without packaging or distributing a new APK.

An APK/device check remains necessary before this is shipped. In particular, test genuine arrival/check-in at a larger service area and confirm recovery of Warwick from saved evidence after upgrade. No APK release or live catalogue publication is part of this change.
