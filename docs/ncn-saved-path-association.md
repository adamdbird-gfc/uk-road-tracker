# NCN saved-path association trial

16 October 2026. Connects a saved matched path to the activation assessor without
turning its vertices into invented GPS fixes or awarding whole reference ways.
No APK, production progress or deployment occurs in this trial.

## New saved pedestrian matches

The backend companion change preserves exact traversed-edge source identities in
`matched_path_evidence`. The schema uses the existing matched LineString plus
vertex spans, a route hash, namespaced reference IDs and source content hashes.
The shared root `matched_path_evidence.py` helper is identical on both branches.

The reader checks the geometry hash, span alignment, reference metadata and known
source-way geometry. It requires an explicit snapshot-scoped OSM-to-NCN link;
there is no nearest-route fallback. A traversed edge must actually follow the
identified source way (3 cm numeric allowance). The linked NCN geometry must
align continuously and monotonically, with compatible length/direction, before
its measured intervals reach the unique-coverage ledger. The 3 m cross-source
allowance applies only after source identity is established; it is not a radius
for classifying arbitrary journey points.

Removed matched edges remain excluded. Road-ID-only edits whose meaning cannot
be resolved are held out. Changed geometry/reference evidence cannot fall back
to proximity. Explicit access restrictions are held out. Driving is ineligible.

## Existing saved pedestrian matches

Older responses dropped source IDs but retained full public reference-way
geometries in `road_geojson`. The compatibility path identifies an exact full-way
geometry from the bounded verified fixture registry, then assesses only those
travelled route edges that lie on that way. The full way itself earns no credit.
No point times, accuracy or extra observations are fabricated. Sparse Timeline
imports can therefore supply matched-path evidence without creating review tasks.

This compatibility reader is restricted to the known preloaded pedestrian matcher
contract. Relabelling a pedestrian match as cycling cannot supply bicycle routing provenance.
External-router/cycling results, unknown ways, crossings and parallel
routes do not gain credit through it. Existing user edits are respected. Repeats
and multiple journeys are deduplicated by the existing ledger.

## Fixture evidence and limits

The fixture registry uses three independently verified OSM Route 1 member ways
and the checksummed Gravesend NCN reference. Links to source sections are explicit,
local trial associations. They do not establish a complete UK correspondence,
historic route membership, legal access or field accuracy. Stored route identity
is not proof that sparse original locations selected the actual route taken.
The agreed historical-import policy uses supported saved matches automatically;
new live ambiguity review remains a separate evidence decision.

The saved-path assessor is connected to the activation contract in tests: supported
Timeline evidence contributes, remains prompt-free and is reused after unchanged
reactivation. Test controls reject crossings, parallel paths, identity/hash edits,
unsupported provenance, source changes, removed geometry and driving.

96 local NCN tests passed, including 16 saved-path checks. Backend companion tests
exercise the actual graph response and partial-way spans, with no extra coordinate
payload. Local backend checks skip only database-service integrations; CI provides
Postgres for those checks.

Next: expand and validate source-way/NCN associations beyond this bounded fixture,
add an equivalent evidence route for cycling, and integrate the versioned reader,
ledger and resumable assessment into Android activation. The full NCN collection
is not yet implemented or ready for a beta APK.

## Portable association bundle (16 October follow-up)

`python -m scripts.ncn_trial.association_bundle --output <temporary-directory>`
builds a deterministic gzip bundle and release-manifest candidate for the three
reviewed public Route 1 associations. No journey data, Postgres writes or Firebase
publication is involved. This is a development format, not an Android downloader.

The reader binds all rows to the reference version, reference geometry, section
eligibility/routes and projection origin, checks source-way geometry integrity,
rejects duplicate IDs/unknown sections/unsupported modes, and returns no partial
registry on failure. A regional bundle with zero associations is valid. Closed
sections may remain referenced: the existing assessor still excludes them.

The separately supplied canonical content digest detects altered downloads; it is
not a signature or independent verification of NCN membership. Production must
pin manifests through a trusted release channel. A reviewed public membership
source is still required before adding associations: the builder does not infer
links from nearby lines. Changing versions requires reconciliation rather than
silently carrying personal totals forward. OSM attribution and membership/source
checksums travel with the bundle; the separate NCN geometry retains its own
licence obligations.

The three-way candidate measures 2,219 JSON bytes / 975 gzip bytes. This is not a
nationwide size estimate. The format currently repeats source-way coordinates to
support exact legacy geometry identification; compact indexing and regional
partitioning remain implementation work before nationwide/mobile rollout. The
existing metric TrialReference is bounded development infrastructure, not a
national-scale spatial index.

102 NCN tests pass, including six bundle checks covering actual activation through
the serialized reader, deterministic output, corrupt downloads, stale reference
geometry/version/projection, invalid rows and empty/closed reference cases.
Android integration, cycling provenance and nationwide membership expansion remain
outstanding; no APK or production data deployment is part of this step.
