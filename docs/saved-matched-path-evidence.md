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
Fallback walking routers and the independent cycling router do not gain identity
from this change and must not manufacture it from the new pedestrian contract.

The matching NCN development branch uses the identical `matched_path_evidence.py`
helper for contract/hash checks. Preserve that single implementation when the
branches are integrated. Its fixture associations are bounded, not a nationwide
OSM-to-NCN lookup or historical source catalogue.
