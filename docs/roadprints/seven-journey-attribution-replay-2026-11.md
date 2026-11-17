# Seven-journey replay: evidence gate results (17 Nov 2026)

This is an aggregate-only report from the owner's seven private football-training diagnostics exports. No private coordinates, raw samples, precise endpoints, or personally identifying journey IDs are committed.

## Owner decision: accepted saved route is the coverage source

**Supersedes the earlier GPS-only completion interpretation in this report.**
The user's agreed product policy is that a successfully matched saved journey is *accepted by default*, regardless of the sparse original Timeline source. The overview uses the effective route displayed in journey detail, after applying the owner's explicit corrections, rather than treating the original handful of positions as the full travelled path.

- The seven files' stored matched geometries are now first-class **candidate coverage** under this policy. Their source-point density and matcher confidence alone must not exclude them.
- The 174 candidate Android observation pairs above are **secondary corroboration/diagnostic evidence**, not a completeness gate and not a cap on the eligible route.
- The existing Gravesend tagged road graph still lacks coverage for these journeys, so the first true canonical section replay remains blocked until wider tiles are available. This is a network-identity blocker, **not** evidence that these saved journeys are invalid.
- A successful saved match may contribute automatically, but known errors, malformed geometry, unrecorded gaps, failed matches, explicitly removed sections, and unresolved reference ambiguity must be handled without fabricated credit.
- Contributions remain reversible per journey and can be inspected from the overview Map. Opening a journey isn't an explicit confirmation, and no retroactive manual approval exercise is required.

## File coverage

Seven files were parsed completely: **four Google Timeline imports** and **three Android activity captures**. All exports were from Roadprints 0.25.244 (290).

| Journey | Source | Stored raw samples | Preliminary qualifying adjacent pairs | Reasons not qualifying |
|---|---|---:|---:|---|
| 24 Sep evening | Timeline | 0 | 0 | 7 reconstructed control points only; do not treat matched geometry as measured fixes |
| 1 Oct outward | Timeline | 0 | 0 | 8 Timeline control points; matched confidence ~0.495 |
| 1 Oct return | Timeline | 0 | 0 | 6 Timeline control points; matched confidence ~0.037 |
| 8 Oct return | Timeline | 0 | 0 | 7 Timeline control points; matched confidence ~0.00047 |
| 15 Oct outward | Android | 229 | **42** | 111 pairs exceeded 12m accuracy; 75 failed movement/time-distance rules |
| 15 Oct return | Android | 2,940 | **91** | 2,591 pairs exceeded 12m accuracy; 229 failed movement rules; 28 failed time rules |
| 5 Nov return | Android | 127 | **41** | 36 accuracy, 39 network-provider, 9 movement, 1 timing rejection |

The Android gate checks each adjacent pair only: reported accuracy 0–12m, monotonic 0–12s timestamps, 4–130m displacement and maximum plausible 45m/s speed. For older Android exports with five-column samples, provider metadata is absent, not assumed to be `gps`. Newer eight-column exports explicitly identify provider; network-only pairs are excluded. In total **174 candidate pairs** advance to map-attribution checks. **These are not credited road intervals and are not required for accepted matched-route coverage.**

### Canonical reference coverage blocker

The tagged `gravesend-tagged-v1` pilot covers approximately longitude 0.3715–0.3845 and latitude 51.4370–51.4445. **None of these seven exports has a source route point or raw capture fix inside those bounds**. Consequently this dataset cannot produce an authentic canonical full-route union against that pilot. Do not generate synthetic road IDs from matched trace geometry to fill the gap.

To complete this real-data task, a genuine wider, versioned road-network reference covering the journeys must be loaded (preferably spatially bounded tiles). It must preserve edge identity, direction, topology, road class and source revision. The adapter intentionally does not fetch or invent OSM data.

### Work actually validated

- `CanonicalRoadAttribution` checks reported raw fix accuracy, timing, plausible movement, same-reference edge projections, heading and unique alignment; it rejects unproven junction transitions and parallel competing edges.
- `CumulativeRoadUnion` stores unique millimetre intervals keyed by canonical section with journey-specific provenance and owner-rejection clipping. Repeated travel must not multiply length.
- Standalone Java synthetic tests cover reverse direction, adjacent alternatives, overlapping contributors, unsupported inferred routes, correction/restoration, parallel sections, and conservative ambiguity rejection.

### Remaining before Android integration

1. Supply/reference real wide-area OSM edge tiles for the journeys (and eventually the complete archive) without loading a nationwide graph at startup.
2. Run **all seven** through an actual canonical graph with section-level attribution and compare to their stored matches and corrections. Report supported/ambiguous/rejected section metres and parent journey IDs in the private test environment.
3. Implement topology-aware continuity across canonical edge boundaries. Current v1 adapter deliberately does not bridge junctions or wide GPS gaps and will undercount genuine travel.
4. Convert confirmed `CanonicalRoadAttribution.Match` results to `CumulativeRoadUnion.Contribution` after applying corrections and excluding motorway/A-road category from the black minor-road layer; ensure no double-credit from other completion ledgers.
5. Run complete Android tests, archive benchmarks and owner upgrade acceptance before changing the Map.

**No source data, archive credits, release builds or visual Map behaviour were altered by this replay.**
