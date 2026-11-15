# Journey Intelligence Stage 1.1

## Source and integration

Build 285 and draft PR #316 started at `853d343267f69754d3d1ab4f7526811eb7336553`.
The same PR and branch carry Stage 1.1. Its base is the Stage 4.7 branch from
PR #315. Integrate the reviewed railway predecessor chain first, then retarget
#316 to the integrated base and rerun CI before owner-approved merge.
The exact backend deployment and active Pixel NCN revision were not measured
in this task. The bundled references and release package identity remain pinned.

## Diagnosed paths

| Area | Source-backed diagnosis | Correction |
|---|---|---|
| Venue | Map resume and display synchronously called the synchronized venue store for pending and completed visits. Pending lookup repeatedly compared one pending event against the full ledger. The owner stack identifies this path even with the railway worker inactive. | Run pending checks and venue overlay reads on a dedicated worker, coalesce by revision, check the active screen before presenting, and use indexed confirmed intervals to preserve the same overlap rule. |
| Archive | The Journeys discovery pass sent an unfiltered request through the function labelled `focused_archive`. It decoded cached geometry to gather road identity labels. Focused selection also issued individual index and payload queries. | Cache per-journey discovery labels separately, bypass stale queued discovery work, batch-read compact exclusion indexes and open known contributor files directly. Stale tokens and incomplete indexes fall back to the existing per-source decoder. |
| Map | Warm archive processing performed per-source SQLite lookups even with no original reads. Map finish built a full set of A-road JSON sections before the display budget was applied. Exact tap lines also retained their JSON coordinate trees. The background path cache owned JSON keys, and returning to the same view cleared its path cache. | Bulk-read bounded headers, process canonical A-road geometry one road at a time, preserve exact tap coordinates in primitive arrays, and retain only viewport-used paths on each overview. The overview prepares paths off-thread within a weak-key budget of 120,000 vertices when headroom allows. Under pressure it prepares bounds only. |
| Concurrent memory | Archive preparation and railway inference can overlap. Owner logs show railway active in some of the near-limit samples, but do not isolate a retained railway object as the cause. | Railway yields while an overview archive scan is active and remaining Java headroom falls below 48 MiB. Existing cancellation and requeue paths resume work. |
| Evidence | The build 285 action ran on a general screen worker, could drain up to 16 earlier queued jobs, and showed a toast at completion. | Prioritize only the selected file on a dedicated worker and render the result in the journey dialog. A current receipt is shown on reopen. The action offers retry after eight seconds and checks cancellation before writing. Byte, interpretation size and archive revision controls still apply. |

## Driving audit and Stage 2 fixtures

The privately reviewed export `roadprints-journey-20261114-203302.json` has
SHA-256 `157946cb4da08b83cbd7d618103bbe9c16c292efe25c872d0bb1972b65b52a7d`.
Journey B contains 1,280 original GPS samples and 32 retained activity changes.
It starts at 17:09:40 UTC and ends at 18:24:49.538 UTC. Its capture evidence
records both initial and saved mode as Unknown. The later manual correction
to Driving is recorded explicitly. The recovered export has matching Pending.
The separate screenshot shows a subsequent partial match with qualifying
sections only.

Android's IN_VEHICLE event includes both road and rail. Capture deliberately
maps that event to Unknown for an unclassified start unless contextual road
or rail evidence promotes it. Existing capture retains activity events.
The saved state and manual correction in Journey B establish upstream
classification uncertainty, rather than a misleading card label. Journey A's
vehicle activity and saved 17:09:40 boundary are owner-reported here.
An independently verified original transition export would be needed to
locate any earlier start of driving. No isolated threshold regression was
established.

Stage 2 fixtures should cover a conventional road departure, slow driving in
congestion, A/B adjacency, parallel rail, and the existing HS1/tunnel cases.
Require time-aligned GPS and activity evidence, defensible boundary candidates,
explicit unresolved spans, and preservation of confirmed modes. Compare
recorded, accepted and collection-qualifying distance separately. Keep the
Colchester pair's saved boundary and mileage unless a later reviewed
interpretation explicitly changes them.

## Acceptance

Repeat Map, Journeys, Progress and Achievements with the owner archive on
Pixel 10 Android 17. Capture cold and warm frames, Java heap, worker activity
and newly separated discovery and focused-road timings. Verify venue offers
while changing screens and reopen a journey after verifying its evidence.
Build 285 owner baselines were 254–255 MiB sampled Java heap, 14–19 s archive
operations, up to 23.6 s to Map display, and 103–132 ms slow road frames.
Controlled emulator and JVM results are separate from those device readings.

## Recovery and validation evidence

The interrupted work was recovered from commits `ab304b0`, `79c48c0` and
`832028a` on PR #316. The previous scratch checkout and its untracked files
were no longer present. Its completed GitHub run 43640712052 retained 1,056
passes, 11 skips, and no JVM failures. The skips were ten optional NCN
candidate-fixture checks and one private owner-export replay. The standard
pinned NCN and railway tests remained enabled.

On the earlier API 35 synthetic replays, the former unfiltered discovery
scan took 861 ms over 1,661 journeys. The new compact-label scan took 264 ms
over 1,663 journeys. The scan was warm in each case, with 67 and 83 sources
read respectively. The JVM warm map replay took 116 ms before and 105 ms
after. The one-added-journey JVM replay increased from 67 to 105 ms.
These are individual controlled samples. They do not establish an
Android 17 warm-frame or heap improvement on the owner's archive.

The resumed acceptance run 43690695343 waited for a populated overview on
Android API 35. With 1,663 journeys and 4,923 displayed sections, the cold map
was ready in 5,406 ms and the warm process snapshot in 68 ms. The concurrent
railway run sampled 67 MiB of Java heap against a 192 MiB limit. The preceding
run of the same fixture sampled 74 MiB and reported ready at 12,524 and 73 ms.
Runner scheduling varied, so these samples do not isolate a causal speedup
for cold preparation. A 118 ms road frame remained in the final run. Pixel
memory headroom, venue-overlay behaviour and frame-time acceptance are open.

## Build 287 cache and memory follow-up

The Pixel 10 (Android 17) reported process-wide Java heap samples of 247–255
MiB against a 256 MiB limit with railway reassessment inactive. The original
sampler reports allocated occupancy and carries its maximum from the
archive scan into geometry or coverage finalisation. Those samples establish
little instantaneous headroom. Live bytes, garbage collected bytes, native
paths and headroom after collection require separate measurements.

- Progress recorded 9,835 ms for 1,663 journeys with zero source decodes.
  Its 8,609 ms cache interval includes SQLite row queries and BLOB copies,
  checksum verification, UTF-8/JSON conversion and road-reference validation.
  The 1,028 ms merge accumulated distances, coverage and discovery evidence.
  A current process snapshot already bypasses this work. After a process
  restart the disk snapshot supplies headline totals only, so full coverage
  and discovery still require aggregation.
- Map recorded 4,237 ms for 1,447 cached payloads with zero rebuilds or source
  reads. That interval also includes bulk header preparation, individual
  payload queries, validation, filtering, coverage merge and geometry append.
  The 10,538 ms navigation figure additionally spans executor and lock wait,
  snapshot restore attempt, reference setup, canonical section construction
  and path preparation. The current view, process snapshot or disk snapshot
  can bypass the archive. A large snapshot can be dropped on pause or skipped
  on disk for its size or available heap headroom.
- Map and Progress archive scans share an exclusive lock. Their models can
  coexist with retained view geometry and with path preparation, optional
  Map snapshot serialisation or Progress settlement enrichment outside the lock.
  Progress keeps aggregate coverage, journey IDs and local-road geometry strings.
  Map keeps route JSON, precise tap coordinates and bounded path buffers.
  Per-row cache BLOBs and parsed records largely create temporary allocation,
  with Map retaining selected lines in its rendered model.
- Successful bundled A-road signature and length metadata now survives
  across calculators in the same process, capped at 512 entries of signatures,
  anchor counts and lengths. File tokens, payload
  digests, per-row signature and index validation stay in place.
- Diagnostics now separate payload access, checksum and decoding, reference
  validation, merge, wait and geometry work. Independent phase samples log
  heap entry and exit and ART collection and reclaimed-byte deltas. If
  occupancy remains high after natural collections, capture one targeted
  heap profile at the implicated screen transition to identify retaining
  owners.
- The 105–111 ms road frames occurred with the path cache working
  (25,745 hits). The slow-frame log is rate-limited to one entry per five
  seconds and has no frame denominator, so frequency remains unknown.

Stage 1.1 physical-device memory and latency acceptance remains open. Build
287 source provenance is `03ef0ee`. The branch head before this follow-up
was `69604f8` and differs only in owner release tooling. Live backend commit
and active Pixel reference revision remain unverified.

### Focused validation

Two matched, sequential Robolectric/JVM runs used the same 1,663-journey
fixtures and C1-only JIT settings after the initial baseline JVM hit a C2
compiler crash. These are individual host timings.

| Warm preparation | Build 287 source | Corrected source | A-road asset opens during validation |
| --- | ---: | ---: | ---: |
| Progress, 56 A roads | 1,058 ms | 210 ms | 56 → 0 |
| Map, one A road | 243 ms | 164 ms | 1 → 0 |

Both warm passes reused 1,663 cached payloads and read zero originals.
The Map finish stage still opens the reference for exact geometry. The
corrected sample spent 114 ms in Map records and continued to produce
identical mapped coverage. Cold Map (3,190 → 3,393 ms) and one-new-journey
Map (157 → 200 ms) were slower in this single comparison. These figures
establish the avoided asset work and provide no physical-device timing
or heap-retention estimate.

All 30 focused JVM tests passed. The Progress fixture changed the distance
of one saved journey and verified a new archive revision, one rebuilt row,
1,662 reused rows and the exact revised total. Map verified changed-source
rebuilds, filter behavior and unchanged rendered coverage. The navigation
suite checked cache invalidation, path reuse and source evidence preservation.
The previous 1,058 JVM passes and four Android API 35 invocations remain
baseline evidence. ART collection deltas require a physical-device test.
