# Saved matched-path identity, schema 1

The preloaded pedestrian graph already knows the source way for each traversed
edge. This additive response field preserves that information for later NCN
assessment. It is routing provenance, not GPS certainty, network membership,
activity access approval or an NCN completion claim.

`matched_path_evidence` has status `available` or `unavailable`. Available records
include `geometry_feature_index: 0`, a logical-numeric SHA-256 of the existing
`geojson.features[0]` LineString coordinates, public reference metadata and spans.
Coordinates are not duplicated. Spans are `[from_vertex, to_vertex_inclusive,
reference_index]`: they identify the actually traversed route edges, in order.
The same way may appear in multiple spans after another way or a return leg.
Never credit the whole reference way from its presence in `road_geojson`.

Each public reference records source key, source version label, original OSM way
ID, kind, public tags, a source-geometry hash and a content hash. Source version
labels such as `latest` are not immutable snapshots; the content hash identifies
what was actually returned. Changing geometry/access tags changes that hash.
Source geometry uses OSM's seven-decimal precision and ignores orientation;
returned-route hashing preserves order and exact numeric values, while treating
JSON `0`, `0.0` and negative zero consistently across Android serialization.

Internal graph feature keys now include source namespace and way ID, avoiding
collisions when regional sources overlap. The graph still returns the same
matched-route/road geometry contract; extra internal metadata is omitted from
the legacy `road_geojson` features and emitted once in the compact reference table.
Invalid edge/source alignment emits unavailable evidence rather than inventing
identity. Existing Android matching persists the full response object, so it can
retain this additive field without an APK solely for metadata preservation.

No schema migration, additional reference ingestion or personal-data storage is
introduced. The existing bounded read query joins source metadata. No deployment
is requested by this PR; coordinate it with the existing backend boundaries work.
Fallback walking routers do not gain pedestrian identity from this change.
Cycling uses the separate graph-node contract described below, rather than
borrowing the pedestrian matcher identity.

The matching NCN development branch uses the identical `matched_path_evidence.py`
helper for contract/hash checks. Preserve that single implementation when the
branches are integrated. Its fixture associations are bounded, not a nationwide
OSM-to-NCN lookup or historical source catalogue.


## Bicycle graph node evidence

The dedicated bicycle router now requests OSM node annotations in its existing
Match calls. Cycling responses identify their matcher as `osrm_bicycle_v1` and
include `cycling_path_evidence`. Returned geometry, cycling geometry attribution,
road-completion exclusions and distances are unchanged. Missing annotations never
turn an otherwise valid match into an error.

Each evidence feature binds the matching geometry checksum to uniquely aligned
leg spans, the annotated OSM node chains and the router data version when supplied.
Node count is not assumed to equal geometry vertex count. Step geometry must align
uniquely and exactly with the route overview; absent, discontinuous or ambiguous
legs are held. Consecutive duplicate graph IDs can be preserved as observations
but cannot establish a travelled edge by themselves. Integral numeric IDs are
normalised without accepting booleans, fractions, zero or unsafe JSON integers.
Public metadata is checksummed for edit detection; this is not authentication.

No extra coordinates are duplicated in the evidence field, no additional router
requests are introduced, and no server-side personal records or database schema
are added. Existing Android code saves the entire response in processing_result.
Historic cycling results without this contract remain usable as journeys but do
not gain source identity; a future rematch may supply it. No bulk review queue is
introduced. This change is prepared for the combined release, not deployed here.

The NCN trial resolves ordered graph node chains against explicitly sourced way
node chains before checking rounded geometry. Unknown chains or multiple source
candidates remain uncredited without a nearby-path fallback. Its source registry
is still bounded and does not certify historic membership or present access.

Validation: 80 backend tests locally, with the five PostgreSQL integration tests
skipped locally and exercised by the existing CI database service. Two API checks
cover bicycle-only annotation requests and response retention. Five collector
checks cover alignment, missing/bad IDs, ambiguous loops and partial responses.

API reference: https://project-osrm.org/docs/v5.24.0/api/#annotation-object
