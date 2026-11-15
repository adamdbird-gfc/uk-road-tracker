# Stage 1.2C — Gravesend all-road coverage prototype

## Architecture

Use a versioned road network and a disposable coverage projection. The
projection stores section IDs and half-open millimetre intervals contributed
by each journey. Union those intervals for drawing and unique coverage.
Repeated traversal leaves the union unchanged and adds a journey to selection.
Any uncovered gap survives the union. A covered interval takes precedence
over unresolved evidence for the same interval. An unresolved journey with
no plausible section stays discoverable by journey ID.

The design carries forward the separate coverage and journey contribution
model of `MotorwayProgressCalculator` and `ARoadProgressCalculator`. Their
canonical anchors and compact cached contributions informed the interval
ledger, spatial grid and incremental invalidation. Their 100–110 m matching
radii and whole reference-anchor decisions suit a different scale. The old
Gravesend browser proof similarly assigns a whole segment at the nearest
sample. This experiment keeps the existing OSM extract workflow, with a
fresh snapshot that retains ordered node identities and access/direction
metadata. It shares no new runtime code with the calculators.

One OSM way-node pair is a section. The source namespace and extract
digest scope each identity. Names and route refs label sections. Shared
nodes connect sections, and coincident coordinates alone establish no
connection. Display geometry can stitch covered intervals across degree-two
nodes on the same way. A stroke still carries its section keys so a tap
can query the actual interval and contributors. Road completion across
multiple OSM ways will need a separate grouping rule.

The alternative of drawing exact unions of legacy matched lines would
immediately remove the eight identical edges in the owner example. It
leaves small shifts, wrong junctions and historical road changes ambiguous.
A routing-engine edge contract such as Stage 1.2A would improve handling of
curves and junctions if observations support the source intervals. Keep
that producer/consumer contract available and validate a correspondence
before projecting its intervals onto a different snapshot. The offline
projection experiment works with original observations and requires no
routing-service deployment.

## Implemented prototype

- The pinned OSM extract covers the Gravesend town-centre area from
  51.433° to 51.447° N and 0.366° to 0.385° E. It contains 663 ways,
  4,245 nodes and 4,361 indexed edges across 725 cells. Of the 663 ways,
  494 qualify under the prototype's driving access policy. Nearby tracks,
  private roads and excluded service roads can act as competitors.
  The data comes from [OpenStreetMap contributors](https://www.openstreetmap.org/copyright)
  and is distributed under the [ODbL](https://opendatacommons.org/licenses/odbl/1-0/).
- The evidence gate uses consecutive original observations, confirmed
  driving, a contemporaneous reference, a positive sample interval up to
  12 s, no more than 80 m between observations, and recorded fix accuracy
  at most 8 m. It rejects implausible speed, wrong direction, and
  candidates outside the accuracy envelope plus 3 m. It searches within
  12 m for competing routes, compares heading and requires a single
  continuous chain of aligned sections. A 12 m boundary margin holds pairs
  where the extract may omit competitors. These parameters are provisional.
- The ledger updates only edges affected by the added, corrected or deleted
  journey. Per-edge interval counts make a repeated claim cheap to add or
  remove. Identical replacements are idempotent. The ledger rejects a
  stale dataset or policy and malformed bounds before changing existing
  rows. It lives in memory and assumes one writer.
- All-time strokes read the precomputed union. Filtered strokes derive their
  union from a supplied set of qualifying journey IDs. The same filter
  governs contributor queries. The offline comparison supports a separate
  unresolved diagnostic overlay. In an app trial, uncoloured basemap roads
  can convey gaps and Journeys can remain the place to inspect source
  tracks. Hold the current journey-line view as the comparison mode.

For an Android trial, reuse `MapJourneyStore`'s source invalidation and
atomic compact records with a separately disposable coverage cache.
Key each contribution by journey evidence token, region/snapshot and policy.
Build an edge/interval contributor index and materialized tile unions on a
worker, process changes by dirty tile, and query visible tiles on screen
entry. Use the existing Map filter headers. A new snapshot is replayed
from original evidence or a validated correspondence before switching the
active tile version. `RoadDetailModel` can load the resulting IDs. The
current 120 m name search in `MapActivity` and the locality inventory in
`LocalRoadSettlementMatcher` answer different questions and cannot identify
the covered section. Exact Android hit testing and persistent cache
invalidation remain future work.

## Acceptance results

| Case | Observed result |
| --- | --- |
| Nine-mile synthetic road, full traverse | Exactly nine miles covered and one stroke. Three journey IDs are selectable after repeats. |
| First and last mile | Exactly two miles covered and two strokes. The middle seven miles remain uncovered. Adding a drive from mile 0.5 to mile 2 increases unique coverage by exactly one mile. |
| Parallel roads, junctions and disconnected geometry | Parallel candidates stay unresolved, including an ineligible private road. Straight-through observations cover the joined main edges. The perpendicular branch and coincident but disconnected endpoints receive no coverage. |
| Unnamed and one-way roads | An unnamed section qualifies by ID. Reverse travel, absent accuracy, reordered timestamps, excessive gaps and implausible speed are held. |
| Real Gravesend snapshot with synthetic fixes | One segment each on Ordnance Road, Albion Terrace and Milton Place aligned to the expected ID. A second Ordnance Road section was held where a parallel candidate exists. Milton Place's tested one-way section held reverse observations. |
| Real Ordnance Road exports | The 149 m legacy step is an exact suffix of the 263 m step. The comparison found 120.661 m of distinct Ordnance Road alignment, of which 68.231 m was shared. The two steps left 80.28 m and 141.41 m of input chord length unaligned, respectively. A 32 mm diagnostic sliver projected to Canal Road at the junction. Both exports earned zero coverage. |
| Albion Terrace and Milton Place owner evidence | The reference identifies real sections in both areas. The owner screenshot has no journey IDs or fix history for those traces. The cause of the divergences and any historical geometry correspondence remain unverified. |

The Ordnance Road matches have five and four saved observations,
180-second maximum gaps and route confidences 0.564 and 0. The source lacks
per-fix accuracy and a verified February road snapshot. The replay emits
the approximated layer as unresolved. Its tiny Canal Road sliver and
unaligned remainder demonstrate why it belongs in diagnostics. The comparison
does not establish a real-world false-positive rate or recall.

### Geometry and preparation cost

| Local CPython 3.12 comparison | Journey lines | Network strokes |
| --- | ---: | ---: |
| Real Ordnance Road step paths | 2 | 4 diagnostic strokes |
| Real Ordnance Road vertices | 26 | 18 diagnostic vertices |
| Identical synthetic routes, paths | 1,664 | 1 |
| Identical synthetic routes, vertices | 54,912 | 17 |
| Warm synthetic preparation median, 11 runs | 10.82 ms | 0.080 ms |
| Warm preparation peak traced allocations | 6.35 MB | 9 KB |

The real diagnostic strokes increase path count because the accepted
approximation is fragmented. The repeat stress case uses 1,664 identical
960 m routes across 16 edges and intentionally isolates geometry
preparation. Its cold assessment and aggregation took 748 ms. A cached
add/delete cycle took 0.121 ms median. The 64,052-byte reference loaded
in 23.2 ms median with 3.36 MB peak Python allocations. The synthetic
in-memory ledger retained 7.60 MB. Warm draw preparation excludes that
ledger, and its memory savings cannot be extrapolated to the total map
heap. The benchmark does not model the actual archive's route diversity,
Android allocation, frame costs, on-disk cache size, tap latency or an
app's first draw.

## Feasibility and next stage

Run a real Gravesend adjudication trial first. Collect confirmed,
accuracy-bearing captures over separate and repeated portions, at least
one nearby-road pair and a Milton Place junction. Record expected
traversal sections independently. Measure covered and missed distance
and wrong-road metres, inspect ambiguous alternatives, and compare the
current map with the offline projection. Calibrate across traces before
choosing a small feature-gated Android comparison. Then measure build
288-equivalent archive latency, Java heap, slow frames and upgrade
integrity on the Pixel. Tight, pairwise evidence can miss curved roads,
sharp turns, stationary drift and frequent short fixes. Test cases with
observed turns, bridges and junction approaches before adding a bounded
multi-observation scorer. Historical replay needs trustworthy time
correspondence, particularly where the street layout changed.

For nationwide use, stream a pinned UK OSM PBF through the disk-backed
node lookup used by [GB inventory generation](../gb-road-inventory-candidates.md).
Emit buffered geographic shards with one owner per edge and copies for
boundary candidate search. Use integer coordinate/offset encoding and
a compact manifest with checksums, seam validation and previous-version
rollback. Serve immutable changed shards and load only visited regions.
Keep authoritative snapshots and their manifest revisions separate from
the active app cache. A single periodic batch job and static shard delivery
are plausible. The [Geofabrik UK extract](https://download.geofabrik.de/europe/united-kingdom.html)
includes Great Britain and Northern Ireland and was about 2.1 GB at review.
The earlier GB inventory pass took 288 s, 4.64 GiB peak process RSS and a
3.76 GB temporary node index. This is a related workload. Start a regional
size trial with a batch worker in the 8–16 GiB RAM and tens-of-GB scratch
range, sized again after measuring geometry emission and seam costs in
urban and rural shards. National output and traffic costs are open and
depend on shard density, update cadence and adoption. A routing service is
unnecessary for this architecture.

## Reproduction and delivery state

```sh
ROADPRINTS_ORDNANCE_FIXTURE_DIR=/path/to/private-exports \
  python -m unittest discover -s tests -p 'test_road_coverage_prototype.py' -v
python -m scripts.road_coverage.replay /path/to/private-exports/roadprints-journey-20261115-142604.json \
  /path/to/private-exports/roadprints-journey-20261115-142731.json
python -m scripts.road_coverage.benchmark
```

The replay accepts an optional `--private-geojson` output for local
inspection. The dated OSM API response from 15 November 2026 is identified
by SHA-256 `7d75581abcf29250b19bdcdb6fccab1921dcd775cfa8d8edeb16d99bd03bf2c8`.
The pinned compressed catalogue SHA-256 is
`eb3ea31a4a36fa51098944f7b1100fdd6ec09a1605e53e7a6e2afe2e8ac765d0`.
The decoded catalogue SHA-256 is
`b648208e870a5292a36845a681d2497959d41442252c9afe3b3fdf085deb527b`.

Baseline is Roadprints 0.25.242 (288), also reported by both build 288
exports, with APK source `620db84` and packaging at `31e6bdd`. The
current-snapshot experiment is based directly on #317 `f23124a`, above
#316 and the railway predecessor. Follow the predecessor integration
order before retargeting to main. The other Stage 1.2C branch is a separate
prototype based on the repo's earlier reference. Private exports and
screenshots are local fixtures. Fourteen prototype tests, 23 road distance
and recovery regression tests, and three Stage 1.2A contract checks passed.
The report and experiment are unmerged. Android and the API have no source
changes in this experiment, so no production cache migration, deployment,
Firebase publication, APK, upgrade run or device acceptance was needed.
Live OSRM graph and Firebase pins play no part in this replay and were
not rechecked. No issue was closed.
