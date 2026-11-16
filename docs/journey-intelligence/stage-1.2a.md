# Stage 1.2A — Road network identity and attribution

## Finding

The driving match currently supplies no authoritative section identity. On build
288's source lineage, `POST /match` in [api.py](../../api.py) selects the driving
OSRM graph and disables annotations. The response carries the matched route
geometry and route-level confidence, and each road step carries geometry,
name/reference, distance and chunk index. It carries neither a source-section ID
nor a graph version. A chunk index is scoped to one response.

Render reported deployment `dep-dbqe29jrjlhs73foboj0` live at
`55e9a2a58307a9b4899050670b5d0c6e3641961d`. The deployed source has the same
contract. The effective OSRM host override and active graph snapshot were not
independently established. The Firebase reference pins were outside this driving
identity path and were not reverified.

The [OSRM API](https://project-osrm.org/docs/v5.24.0/api/#annotation-object)
offers per-leg OSM node annotations. Its optional `data_version` is an input OSM
timestamp. The standard Match response does not supply a stable, unambiguous
edge and its clipped interval on the canonical centreline for every matched
piece. Turning on node annotations alone leaves gaps at partial edges, can
leave ambiguous edges between the same nodes, and does not identify the precise
processed graph. The existing bicycle evidence binds node chains to geometry
legs. That contract provides no driving-section or per-edge interval guarantee.

Android currently merges section responses in `TimelineRoadPreparation.merge`
and stores their consolidated match in `MatchingCoordinator`. The compact
overview reader in `JourneyStore` selects fields and prepares `map-v1`
projections. The separate `MapActivity` tap path groups nearby matching features
within 120 m by road label. Thus Albion Terrace's shared summary is evidence
about the tap behaviour. The Milton Place screenshot shows why today's basemap
and a plausible visual alignment cannot establish a historical traversal.

## Required upstream capability

The proposed [attribution schema](road-section-attribution-v1.schema.json)
describes one journey's claim for one span. It is a contract and a test fixture
format. There is currently no producer or consumer in the shipped application.

1. The driving matcher must emit, for each supported span, the source namespace
   and immutable graph version (including extraction and profile/build identity),
   a source-assigned section ID unique within that graph, and a shared centreline
   reference resolvable in that same version. A source-proven way and directed
   node span is acceptable only if it uniquely identifies the section including
   parallel, split, and grade-separated edges. The snapshot or a versioned
   centreline service must remain resolvable for supported historical claims.
2. Provide clipped offsets along the centreline in integer millimetres using
   the source's fixed orientation. Require
   `0 <= start_mm < end_mm <= centreline_length_mm` and carry traversal direction
   independently. Identify the match leg and bracketing submitted observations.
   The Android preparation path must preserve the mapping from those observations
   to the original samples after trimming, recovery, or chunk merging. A
   missing or ambiguous interval must be held. Never infer identity from
   geometry alignment, a road label, an opaque OSRM hint, or a local cache key.
3. Verify a span only after validating its source reference and offset bounds,
   original observations and accuracy, monotonic time and gaps, connectivity,
   direction and competing nearby roads. Sparse imports and weak or route-wide
   confidence alone cannot support a whole reconstructed span. Zero route
   confidence cannot produce a verified claim. Supply `unresolved` with a
   reason when a plausible candidate has insufficient support, and
   `unavailable` when the source identity or requisite evidence is absent or
   unusable. Schema validation checks shape and minimum requirements. A future
   evidence policy must make the actual decision.
4. Preserve claims as a versioned optional member of the saved matching result
   (proposed `processing_result.road_section_attribution`). Keep original
   evidence and centreline geometry separate. Missing or unknown schema
   versions fall back to legacy behaviour. Preserve and rebase evidence
   references when merging supported chunks, or hold the affected claims.
   The compact projection should eventually use source references only and
   deduplicate by graph and section, normalized interval, and journey ID.
   Retain each journey's confidence independently of this deduplication.
5. For the future identity projection, invalidate per journey using the saved
   match fingerprint and original observation fingerprint, attribution schema
   and policy version, and any source graph or resolver revision used for the
   claim. Define and share deterministic fingerprint inputs across producer and
   consumer, excluding the attribution envelope from the matching fingerprint.
   Keep archived version claims independent of replacement snapshots. A new
   graph requires independent evidence or a source-proven correspondence.
   Feed this projection through the existing incremental path without new
   full-archive work at Map entry.

## Focused proof

| Driving export | Timeline route points / saved points | Largest timestamp gap | OSRM step distance | Route confidence | Identity |
| --- | ---: | ---: | ---: | ---: | --- |
| 11 February 2026 | 2 / 4 | 180 s | 263 m | 0 | Unavailable |
| 22 February 2026 | 3 / 5 | 180 s | 149 m | 0.5642326653 | Unavailable |

The 22 February step is the exact ordered nine-coordinate suffix of the
17-coordinate 11 February step (longer-step indices 8–16, zero-based).
Both journeys are confirmed Driving. The saved evidence supplies no original
per-fix accuracy for that span. The fixture demonstrates geometric overlap
only, and neither export gains a verified traversal. A separate synthetic source
example tests the representation of a shared interval, a longer prefix,
independent confidence, duplicate contribution IDs, parallel and junction
sections, and a different graph version. The two owner exports remain private.

Replay with Python and `jsonschema` installed.

```sh
ROADPRINTS_ORDNANCE_FIXTURE_DIR=/path/to/owner-exports \
  python scripts/test_road_section_attribution_contract.py -v
```

Three contract tests passed with both exports present. The 23 existing
road-distance and driving-recovery tests also passed, including the collection
exclusion check.

The change adds zero runtime cache bytes, archive passes or retained app objects.
It does not introduce a runtime migration, and Android timing and heap were not
benchmarked. The next gate is a real driving matcher response supplying the
versioned edge, centreline, clipped interval and observation mapping above. Then
validate merge and legacy fallback, cache invalidation and bounds before Stage
1.2B consumes verified claims or changes tap and renderer behaviour.

Baseline is build 288 (application source `620db84`, packaging and PR #316
head `31e6bdd`). This follows the authorised PR #316 lineage. Integrate the
railway predecessor first, PR #316 next, then retarget the follow-on to main
when its base is integrated. No runtime implementation, live deployment,
Firebase publication, APK, or device acceptance is part of this change.
