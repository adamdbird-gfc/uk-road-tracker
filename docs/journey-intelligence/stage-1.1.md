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
| Map | Warm archive processing performed per-source SQLite lookups even with no original reads. Map finish built a full set of A-road JSON sections before the display budget was applied. Exact tap lines also retained their JSON coordinate trees. The background path cache owned JSON keys, and returning to the same view cleared its path cache. | Bulk-read bounded headers, process canonical A-road geometry one road at a time, preserve exact tap coordinates in primitive arrays, and retain only viewport-used paths on each overview. The overview precomputes bounds off-thread without prebuilding every path. |
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
