# Stage 1.2C · Gravesend all-road coverage

## Decision

Use an independently versioned road reference and accumulate clipped intervals
against its physical sections. Keep this as a derived Map projection. The saved
journey, its mileage, mode, corrections and collection evidence remain in the
existing systems. In this stage the projection runs offline. It reads the
reference and explicitly supplied exports, and it writes comparison output only
when requested.

| Approach assessed | Decision |
| --- | --- |
| Extend motorway and A-road anchor matching | Reuse the architecture of versioned reference geometry, shared spatial lookup, compact per-journey contributions and unique coverage. The 100 m A-road anchors and 100 m radius are too coarse for residential gaps and parallel streets. The Android calculators and their credit rules are outside this change. |
| Deduplicate saved matched geometry directly | Use exact overlap as a replay baseline. It catches Ordnance Road's shared suffix but cannot identify the same road when the geometry differs or determine whether a route is historically plausible. |
| Use the bounded OSM reference with clipped interval union | Implemented. Keeps section identity separate from road name, uses a tighter local corridor and explicit topology, and works without contacting or changing the matcher. |
| Require Stage 1.2A upstream identity | Retain the contract as the preferred input for source-verified spans. The current baseline exports lack this evidence. Source claims can join the Map projection only where the claimed graph and projection reference have proven correspondence. |

The existing Gravesend OSM extract supplies physical way/node pairs, geometry
and driving eligibility hints. The prototype uses consecutive way-node edges
as atomic sections and resolves endpoint orientation at shared nodes. An edge
can be partly covered. Section IDs are scoped by the fingerprint of the
snapshot. Names are display labels. Same-way adjacent intervals can stitch
into one rendered path at a degree-two node. The path retains constituent
section IDs and offsets for selection. The union does not fill the space
between intervals or combine disconnected carriageways.

Reference bounds are 51.425–51.445° N, 0.350–0.385° E. The snapshot contains
10,630 driving edges. Of those, 6,760 edges covering 77,743.9 m are eligible
under this trial's road-class filter, and the rest remain alignment
competitors. This is a trial network length, computed in a local metric
projection. It is no measure of confirmed public road mileage. The
uncompressed source SHA-256 is
`97c9a14c999b1bf574003c4fd923b06da65a8dcec0eeaddbb910f1f993f85123`.
The compact compiler emits seven-decimal integer coordinates and carries the
source fingerprint. A round-trip test checks every section, coordinate, node,
label and eligibility decision. Data © OpenStreetMap contributors under ODbL.
[OpenStreetMap copyright and licence](https://www.openstreetmap.org/copyright)

## Evidence, selection and incrementality

- **Covered** requires consecutive original Driving fixes with reported
  accuracy at most 5 m, time separation up to 8 s and motion of 2–35 m. The
  pair must align within a 7 m corridor and 20° of the road axis, maintain
  connected topology and lack a competing section within the corridor plus
  fix uncertainty. These are prototype thresholds. The result supports the
  trial display and confers zero verified completion or achievement credit.
- **Unresolved** includes legacy matched geometry with no trusted original-fix
  stream. Its optionally displayed approximation is held in a separate
  ledger. Original observation pairs that fail a confidence gate record
  a hold reason. When a hold lacks a reliable section, the prototype leaves
  it unplaced and keeps inspection on the evidence side.
- **Uncovered** means the selected interval has no supported or located
  unresolved contribution in the selected view. It describes available
  evidence, with gaps preserved at millimetre offset precision.

The in-memory ledger keys projections by dataset and policy and accepts a
fingerprint of each input journey. A replacement validates its spans before
publishing them into the ledger. It updates affected sections only, with
reference counts for repeated identical intervals. Removing a journey
preserves any intervals still supported by others. A new graph or policy
starts a separate ledger. Section and offset selection returns the exact
contributing journey IDs in the selected layer. The offline equivalent of
Map filtering accepts a set of IDs and unions those journeys for display
and selection. All-time coverage is independent of that display filter.

`MapJourneyStore` already provides compact validated projections and avoids
decoding unchanged journey originals. Its schema and stored rows were
untouched. For Android, apply the same per-source invalidation pattern to
a separate derived store, consume the current archive change token and reuse
`JourneyFilters` for its mode/status/date selection. `MapActivity` and
`RoadDetailSelection` currently use geometry and road labels for general-road
selection. A coverage hit should instead pass the displayed section and offset
to a contributor lookup, then use `RoadDetailModel` and the existing journey
entry points for inspection. `LocalRoadSettlementMatcher` provides settlement
context after selection and supplies no section identity. The runtime classes
above were inspected for reuse and were not changed.

## Acceptance results

| Case | Outcome |
| --- | --- |
| Entire nine-mile road | Nine covered miles and one path. A reverse traversal kept the same coverage and the selected offset returned both journeys. |
| First and last mile | Two paths and two covered miles with the seven-mile gap preserved. A repeat added zero. A later overlapping fill added one mile and left the remaining six-mile gap. |
| Same-name parallel roads | The fixture holds all three tested alignments at 3 m reported accuracy where carriageways are 9 m apart. At 1 m reported accuracy, centred samples resolve to their own carriageways. |
| Junction and grade separation | The straight traversal covers both connected through sections without claiming the at-grade branch or the bridge. A sample along the branch near the bridge is held. An apparent continuation with different node IDs is held. |
| Gravesend reference controls | All 57 synthetic pairs generated on Ordnance Road, Albion Terrace and Milton Place at lateral offsets of −2, 0 and +2 m selected their expected edge and zero other edges. This tests the reference and implementation under controlled noise. Real GPS error rates are unknown. |
| Owner's Ordnance Road journeys | The two legacy lines have 26 vertices and 24 subsegments. Eight subsegments are exact repeats. The 149 m step is the ordered suffix of the 263 m step. The optional replay produced 144.258 m of provisional reference alignment. Seven reference sections retain both journey IDs and selection can distinguish one from both with an exact ID filter. Neither journey receives any covered metres. |
| Albion Terrace owner observation | Sixteen reference edges are present. The supplied observation concerns the current app's nearby-name selection. Precise real contributors on each displayed line were not independently established. |
| Milton Place owner screenshot | Twenty-one reference edges are present. The screenshot alone cannot distinguish a matching error from a changed road or another nearby branch. It supplies zero coverage evidence. |

Three legacy Ordnance Road input chords exceed the 35 m pair limit. The
provisional output has three paths and 18 points. Of those, two paths
lie on Ordnance Road and the third is a 39 mm Canal Road seam sliver. This
is a useful false-alignment example in the diagnostic layer and contributes
zero coverage. The historical journeys have only two and three original
Timeline route points, 180 s gaps, route confidence 0 and 0.5642, and lack
an original per-fix accuracy stream. The reference snapshot does not establish
historic road conditions. Both original files passed byte-for-byte preservation
checks and are excluded from the branch.

Eleven Stage 1.2C tests, three Stage 1.2A contract tests, eight road-distance
tests and fifteen driving recovery tests passed locally. Tests cover filtered
selection, unnamed roads, invalid update handling, replacement/removal, version
changes, weak historical evidence, and the compact reference round trip.

## Performance and integration gate

Measured on the development host with Python. Times are medians of five
runs. Catalogue peak is Python allocation tracing in a separate run, not
process RSS or Android Java heap. The repeat-heavy archive uses one synthetic
500 m corridor with 1,664 identical journeys to isolate the best-case effect
of consolidation. The comparison measures geometry preparation only.

| Measure | Before | Prototype |
| --- | ---: | ---: |
| Repeat-heavy rendered paths / points | 1,664 / 34,944 | 1 / 21 |
| Repeat-heavy geometry preparation | 6.98 ms | 0.39 ms |
| Gravesend gzip asset | 621,027 bytes | 192,677 bytes |
| Catalogue load and indexing | 322 ms | 87 ms |
| Catalogue traced peak allocation | 56.8 MB | 12.8 MB |
| Catalogue retained allocation | 7.54 MB | 7.25 MB |

The ledger inserted 1,664 already-prepared contributions in 75.5 ms.
Appending another prepared repeat took 0.065 ms. Those timings exclude journey
matching, storage and actual rendering. The real Ordnance Road provisional
view lowers vertex count but raises path count because it retains two separate
aligned spans and a seam sliver. No physical-device Java heap, slow-frame,
navigation or responsiveness comparison has been run. The [Stage 1.1 Android
baseline](stage-1.1.md) used a different pipeline, including cached Map
preparation of 164 ms in a focused host fixture, so direct comparison with
these Python measurements would be misleading.

Defer the Android layer and owner APK at this gate. Both real Ordnance Road
journeys are weak historical matches. Positive controls use generated fixes,
and the local source lacks complete access, direction and grade tags. The
observations therefore cannot quantify real coverage recall, false attribution
or map responsiveness. The production app, backend, Firebase, railway and
NCN datasets receive no changes. Build 288 owner exports confirm the
installed baseline for this replay. Prior source provenance is `620db84`,
packaged by `31e6bdd` in #316. This stage did not recheck the live matcher or
the owner's active reference pins. There is no merge, production deployment,
APK or physical-device acceptance.

## National path and next stage

The Great Britain public road baseline is about 246,500 miles. Geofabrik's
UK input extract including Northern Ireland is about 2.1 GB. This sets the
scale of the offline import, rather than the size of an app download.
[Department for Transport road lengths](https://www.gov.uk/government/statistics/road-lengths-in-great-britain-2024)
· [Geofabrik UK extract](https://download.geofabrik.de/europe/united-kingdom.html)

As an order-of-magnitude planning estimate, 400,000 km and average atomic
edges of 12–50 m imply roughly 8–33 million edges before including the
different populations of parallel ways and excluded competitors. The compact
Gravesend input averages about 18 gzip bytes per indexed driving edge.
Applying that density mechanically gives roughly 0.15–0.60 GB, with national
tag density, tile seams, access and topology metadata, indexing and
distribution overhead still unmeasured. Plan for hundreds of MB of static
reference data overall and a small on-device working set. Profile two mixed
urban and rural regions before budgeting CI, distribution or cache capacity.

Use an offline reproducible PBF build with fixed tiles, buffered borders,
stable ownership of seam sections, per-tile fingerprints and a manifest.
Use a packed or SQLite spatial index and load only active tiles. Keep sparse
journey intervals on-device. Use side-by-side catalogue revisions with
checksummed promotion and rollback. Regenerate changed tile contributions
from retained evidence or reviewed correspondence. A match across a changed
road requires fresh support. A periodic batch build and static distribution
are sufficient to trial this, with no new always-on routing service. Initial
operational work is reference/tag policy, seam validation, repeatable builds
and rollback. Refresh cost and client download patterns still need
measurement. One-way, explicit access, grade separation, date changes and
equivalent geometry across datasets need a richer source than this extract.

Next, use fresh, labelled native Driving traces through the three Gravesend
areas, including a deliberate turn, repeated route, parallel street and gap.
Compare predicted intervals and held areas against the known route. If the
results support it, add an opt-in, viewport-bounded Android layer that shows
observation-supported trial coverage and leaves the legacy view available for comparison.
Materialise contributions off the main thread, use the current Map filters,
test cancellation, invalidation and section taps, and compare cold/warm
preparation, Java heap, allocations and slow frames with a 1,664-journey
fixture and the Pixel 10. Establish accuracy and device performance before
moving to a larger region or packaging an owner-test release.

The branch stacks on draft #317 at `f23124a`, which stacks on #316 and the
railway predecessors. Review and integrate dependencies in order, then retarget
the follow-on to main when the base lineage has landed. No issue was closed.

## Reproduction

```sh
python -m unittest discover -s scripts -p 'test_all_road_coverage.py' -v
python scripts/benchmark_all_road_coverage.py
python scripts/replay_all_road_coverage.py --compile-reference /tmp/gravesend-compact.json.gz

# Optional private replay, with exports kept local.
python scripts/replay_all_road_coverage.py \
  --reference /tmp/gravesend-compact.json.gz --step-name 'Ordnance Road' \
  --select osm:4973303:32041399:32951605 5000 \
  --geojson /tmp/ordnance-comparison.geojson /path/to/owner-exports/*.json
```

The compiler creates the compact reference from the pinned repository extract.
The comparison GeoJSON uses the `legacy`, `covered` and `unresolved` feature
states for layer styling. To exercise the observed branch, supply
`--original-fixes` with a local JSON object containing `journey_id`, `mode`
(`driving`) and `fixes` entries with `lon`, `lat`, `time_ms`, and
`accuracy_m`. These must be original measured fixes with original accuracy
and timestamps. The reader deliberately has no fallback to matched geometry
or Timeline estimates for that input.
