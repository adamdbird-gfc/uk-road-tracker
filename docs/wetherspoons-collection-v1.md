# UK Wetherspoons Collection — catalogue slice

The agreed display name is **UK Wetherspoons Collection**. Its stable internal key
is `wetherspoons`. This slice adds reference tooling and an audit snapshot only;
it does not change the app, recorder, matcher, importer or Services behaviour.

## Source and scope

The snapshot contains UK OSM candidates, fetched once through Geofabrik
Postpass. The query uses the GB administrative boundary, including Northern
Ireland, rather than a rectangular approximation. Republic of Ireland and Isle
of Man are outside this initial scope. Brand Wikidata identity Q6109362 is the
primary selector, supplemented by pub objects whose brand, operator or name
mentions Wetherspoon. Missing or obsolete tags can still cause omissions or
false candidates: this is not a complete, verified current directory.

Only objects tagged `amenity=pub` without explicit lifecycle flags enter the
candidate catalogue. A pub with rooms still qualifies; hotel-only objects are
excluded. Hotel classification is not exposed as a collection feature.

No data was copied from the official app/directory. No official descriptions,
photos or logos are included. Display-name agreement is not trademark clearance
or a claim of partnership. Review the commercial presentation before launch.

## Identity, geometry and counties

Initial IDs derive from public OSM object identity, never name or postcode.
The reviewed identity registry can attach replacement/additional OSM aliases
to an existing Roadprints ID. It rejects rekeying an identity already present
in a previous catalogue. Nearby pubs are flagged, never automatically merged.
Renames keep identity; missing source objects are retained for review and are
not automatically treated as closed. Any merging of previously published
identities requires a deliberate history migration, outside this builder.

All polygon rings, holes and multipolygon components are retained. A mapped
footprint is **unverified**, not proof that it is the pub's exclusive premises.
Points are explicitly marked `point_only`; they cannot become fictitious
boundaries. Airports, stations and shared buildings need review and supported
confirmation when evidence cannot distinguish the pub from adjacent premises.

County assignment uses the exact 92-county Definition A reference packaged in
Roadprints, checked against its SHA-256. Point-on-surface label positions are
assigned using the same even/odd geometry rule as HistoricCountyGeometry. Border
or unmatched points remain unresolved rather than being assigned by proximity.
This is grouping reference, not evidence of a visit or county achievement.

## Build and verification

Python 3.12 and curl are sufficient; no new runtime dependency. Supply a
Roadprints APK containing the pinned county reference. Retrieval time must be
the actual UTC timestamp of the downloaded snapshot.

```sh
python scripts/build_wetherspoons_catalogue.py \
  --source /tmp/wetherspoons-source.json --fetch \
  --county-apk /path/to/roadprints-0.25.182.apk \
  --registry collections/wetherspoons-identities-v1.json \
  --retrieved-at ACTUAL_UTC_TIMESTAMP \
  --output collections/uk-wetherspoons-candidates-v1.json \
  --audit collections/uk-wetherspoons-audit-v1.json
```

For updates, add `--previous collections/uk-wetherspoons-candidates-v1.json`
and write the new result to another file before replacing the previous one.
Omit `--fetch` to rebuild from a downloaded snapshot. Archive its exact bytes as
`collections/wetherspoons-osm-source-v1.json`; the SHA-256 records those bytes.

```sh
python -m unittest discover -s scripts -p 'test_wetherspoons_catalogue.py' -v
python scripts/validate_wetherspoons_catalogue.py
```

The query has a 2,001-object sentinel and an eight-megabyte response limit;
truncated, empty or malformed data fails rather than silently publishing a
partial reference. A single explicit request has a 55-second time limit, with
no automatic retries or fallback fan-out. It does not consume the app's live
map-context cache budget, and contains no personal journey/location data.

## Audit release gate

The initial 11 October extract has 836 public map objects. Two hotel-only
objects are excluded; three point/building duplicate pairs have explicit
reviewed aliases (matching pub names and points inside the pub footprint).
This yields **831 candidate venues**, with 462 mapped footprints and 369
point-only venues. All candidates have a historic county assignment; 47 lack
a postcode and 73 lack a town. The counts describe the snapshot, not a verified
current directory. The two remaining close pairs contain different named pubs
and are deliberately not merged. Snapshot/audit files are the authoritative
counts on future refreshes.

The catalogue deliberately has `ready_for_app=false`, `release_status=audit_only`
and a null completion target. Do not package it into Android or grant visits.
Before activation, reconcile operating status, coverage and aliases, review
footprints/shared premises, and resolve county gaps. The app-reported 821 is a
comparison figure, not a target to reach by adding/removing records arbitrarily.
Keep dates and sources for reviews; retain a separate active denominator and
historical identities when closures occur.

## Existing-code audit and next slice

Reuse Services' journey-backed visit ledger, background historical processing,
revision invalidation and access separate from evidence. Do not copy its
125-metre native stop radius or 350-metre Timeline nearest-venue lookup: pub
premises need stricter geometry/identity evidence. Read existing Timeline and
native stop evidence without modifying capture, matching or import logic.
Entering a boundary alone is not a visit; require a supported stop/dwell and
adequate accuracy. Ambiguous airport/station/shared-building cases can offer
evidence-backed confirmation, never arbitrary retrospective ticking.

Later slices add test installation/access, one-collection map selection with
the selector shown only for multiple installed collections, searchable/lazy
county progress, visit attribution and curated achievements. Purchases remain
deferred. The recorder/matcher/importer baseline stays at 0.25.182.
