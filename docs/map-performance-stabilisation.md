# Roadprints — map performance stabilisation

Latest owner evidence is from **0.25.211 (257)**, exact APK source
`290d29c1c506a7b7f2a821abd4452e0e5739dd74`. New Journeys is device-verified
working. NCN incremental assessment is now about 1.5–2 seconds. Map remains
unacceptable at 46–62 seconds and is the scope of private 0.25.212 (258).

| Owner Pixel stage | 211 measurements |
| --- | --- |
| map_evidence | 19,003–37,569 ms |
| map_coverage_add | 21,196–22,035 ms |
| map_coverage_finish | 330–459 ms |
| map_cache | 3,691 ms |
| map_render | 599 ms in the supplied report |
| map_total | 46,737 ms in the supplied report; reported range 46–62 s |
| map_slow_frame | 455 ms, ncn_lines=0, heap_mb=201 |
| ncn_assessment | typically 1.5–2 s after incremental fixes |

## Confirmed costs and corrections

**Evidence:** 211's optional four-entry projection writer drops work under load,
so a first scan does not guarantee warm projections for the full archive. Hits
still inflate large evidence JSON, parse it and serialise it again for checksum
validation. Any global revision invalidates the full map, including unrelated
dwell/status/metadata saves.

212's disposable SQLite map records hold a small filter header, prepared display
lines, and compact per-journey motorway/A-road covered-index contributions.
Checksums cover the encoded bytes, not a fresh serialisation. File replacement
tokens identify changed sources. Unchanged records require no archive/provenance
decode or spatial rematch; filters select compact headers before reading payloads.
Large payloads above 1 MiB retain a compact header but rebuild from original
evidence instead of risking Android CursorWindow overflow; oversized counts are
explicit in diagnostics. This is a derived-cache bound, not discarded coverage.
Each row is atomic, short transactions checkpoint first-use progress, and cancelled
runs retain only complete valid rows. Source changes, malformed rows, explicit
Retry, and actual reference mismatches rebuild from the authoritative original
journey. Deleted files never contribute even if a disposable old row remains.

First-use materialisation still reads older source evidence and computes its
contributions. It is not advertised as a cached 10-second start. Normal process
restarts retain compact records; Android may reclaim disposable cache storage.
The 10-second Pixel target and first-upgrade timings require owner measurements.

**Coverage:** 211 re-sampled each historical motorway/A-road feature every load,
projected samples, allocated neighbour-grid strings, and repeated spatial searches.
212 unions saved canonical indices for selected live journey IDs. This retains
repeated journey mileage, unique coverage and attribution separately. Contribution
reuse validates a SHA-256 fingerprint of the *actual loaded* canonical anchors,
distance and (for A-roads) components, including local motorway references. A
changed reference rebuilds affected journeys; cached indices are range-checked
before any merge. Missing references remain missing. Cold matching uses packed
numeric grid keys, preserving the same sample distances, neighbour order,
Math.hypot radius and tie rules. A-road reference grids are reused within a load.
B/local display geometry and NCN accounting are not replaced with inferred credit.

**Whole-map cache:** 211 parsed an entire stale snapshot before checking its
revision and re-serialised its payload for hashing. The new atomic binary envelope
checks magic/revision before inflation; the checksum covers original payload
bytes. Compatible older gzip snapshots remain readable. There is no new credit
calculation in snapshot restoration.

**NCN:** every save previously advanced the archive token, even for a duplicate
or unrelated dwell update. Exact duplicate saves now do not replace the file or
advance revisions. The NCN evidence token changes only for identity, mode,
processing eligibility, relevant matcher geometry/provenance, corrections,
addition/deletion and existing catalogue/rule invalidation. Metadata-only saves
reuse the published ledger, refresh contributing-journey labels and atomically
refresh restart checkpoints; diagnostics distinguish ncn_metadata_refresh from
ncn_assessment. The first save of an older source conservatively establishes its
fingerprint. Existing per-source assessment reuse, coalescing and cancellation
remain. New Journeys production presentation/acknowledgement code is untouched.

**Foreground:** main-map setters previously fitted the camera repeatedly while
publishing several geometry layers. They now batch fitting once, and skip fitting
when restoring an existing camera. Focused-road fitting is unchanged. This removes
redundant scans; it does not prove a 455 ms device frame is gone. Foreground/frame
diagnostic thresholds and readiness/recovery rules are unchanged.

## Cache/rule maintenance

MapJourneyStore.FORMAT=1 identifies preparation/coverage rules. Bump it if those
rules change in future. Reference changes additionally invalidate automatically
through the actual-anchor fingerprints. The new whole-map binary envelope is
separate from the row cache. All caches remain disposable; raw GPS and saved
journeys are neither migrated nor deleted.

**Delivered private 0.25.212 (258).** Exact signed/tested source
`5d5febdf35f8ac451565b110fbab1e7531fa8116`;
[successful private packaging 39509571145](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509571145);
[APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509571145/artifacts/12305051296);
[verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509571145/artifacts/12305161122).
**711 Android tests passed; zero failures/errors/skips.** Lint, pinned national
reference verification, distribution controls and stable v2 signing passed.
[Independent exact-source checks 39509577110](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39509577110)
also passed. APK downloaded and independently SHA-256 verified:
`b0b40ea84ba636406eab4a2903361aa64bc5a5b98de65667f96b7dadb386d39f`, **36,567,523 bytes**.
Established signing certificate:
`5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`.
One signed APK, no Firebase upload/distribution/notification or Play publication.

### Exact-source automated measurements

Synthetic 1,547 driving journeys using real bundled M2/A2 geometry, **not the
owner's Pixel archive**. These are preparation timings, not Android startup/frame
measurements. Cold means the first compact-record materialisation on the runner;
warm reopens the persisted store; addition reuses those records.

| Runner stage | First materialisation | Warm reopen | One addition |
| --- | --- | --- | --- |
| Total preparation | 896.48 ms | 120.55 ms | 84.33 ms |
| map_evidence | 781 ms | 95 ms | 58 ms |
| map_coverage_add | 82 ms | 8 ms | 6 ms |
| map_coverage_finish | 1 ms | 1 ms | 1 ms |
| map_geometry | 7 ms | <1 ms | <1 ms |
| Original source reads | 1,547 | 0 | 1 |
| Records rebuilt | 1,547 | 0 | 1 |
| Prior records reused | 0 | 1,547 | 1,547 |

Completion percentages, covered canonical indices and displayed sections remain
identical in these tests. New source/row/reference invalidation, deletion,
corruption, interrupted preparation, forced Retry, oversized-row fallback,
metadata-only NCN restoration, duplicate saves and transport defaults passed.

Controlled whole-map snapshot comparison, identical **760,597-byte** payload:

| Runner operation | Legacy gzip-wrapper algorithm | New header/payload algorithm |
| --- | --- | --- |
| Valid snapshot read | 33.46 ms | 22.49 ms |
| Stale snapshot rejection | 12.76 ms | 0.114 ms |

These single-run measurements demonstrate the mechanisms; do not extrapolate
their ratios to a real phone. Full report XML and workflow logs retain raw values.

**Outstanding device measurements:** 212 Pixel map_total/map_render/map_slow_frame,
NCN assessment/metadata-refresh latency, first upgrade compaction, subsequent
process-cold/warm/addition and memory/ANR behaviour. 211 owner baselines remain
46–62 s total, 19–38 s evidence, 21–22 s coverage, 3,691 ms cache, 599 ms render and
455 ms slow frame. The 10-second Pixel target is **unverified**.
First-use or cache-evicted original evidence must still be read. Entries above
1 MiB safely rebuild instead of overflowing CursorWindow; `oversized` diagnostics
make that performance limit visible. No original Pixel dataset or emulator
measurements are claimed. Keep #201/#194/PR #212 open for owner acceptance.


## Verification and remaining device acceptance

Final packaged-source checks passed: 711 tests, zero failures/errors/skips, lint,
reference verification and stable signing. New regressions use real bundled M2/A2 reference
geometry with a 1,547-driving-journey history, comparing cold/warm/addition
preparation and unchanged coverage. They also cover cache reopen, edits/deletes,
corruption, interrupted materialisation, changed-reference rejection, forced
source reload, metadata-only NCN checkpoint restoration, duplicate saves and
train/flight defaults. Existing filter/Retry/Reset/tab/capture/achievement tests
remain. Runner metrics are captured separately from phone measurements.

The owner's 211 counts reconcile **1,547 = 1,355 shown + 192 unprocessed**.
They were excluded at processing status, before transport visibility was tested;
the report therefore does not prove all 192 were train/plane. New diagnostics
break unprocessed counts into train/plane/unknown/other without changing rules.

Before accepting: install over 211 without clearing data; record first upgrade
materialisation, subsequent process-cold start, warm revisit and one-addition
timings; switch Map/Journeys/Progress/Achievements; test combined foot/date and
empty filters, Retry/Reset/Cancel and background/foreground; confirm retained
journey scrolling/camera, motorway/A-road/B/local/NCN completion, attribution and
celebrations. Preserve the device-verified New Journeys flow; only report a new
failure if reproduced. No original Pixel archive, emulator or phone is attached.

One private signed APK after green checks. No Firebase tester upload/distribution,
notification, Google Play publication or new NCN feature work.

