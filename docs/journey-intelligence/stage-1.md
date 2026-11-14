# Journey Intelligence and Reliability · Stage 1

## Baseline and scope

The owner identified 0.25.238 (284) on a Pixel 10 with Android 17. The signed release
tag resolves to `918af39e2813c7a00820f62fbf50c05b00666e52`. The GitHub APK
was SHA-256 verified against the Stage 4.7 receipt. PR #315 is still open and
its head `3e8a436f` adds only the release receipt to that tag. Stage 1 targets
that exact source lineage. When the upstream railway stack is integrated, retarget
or transplant only the Stage 1 commits to validated main and rerun the gate.
The backend deployment and the exact Firebase revision installed on the Pixel
are unverified here. The Android bundled NCN fallback is pinned revision 1.
The existing national railway asset was recovered using the Stage 4.7 artifact
and its expected database SHA-256 was verified. No live dataset changes.

## Stability and navigation

Build 284's Progress stack exhausted a 256 MiB heap inside A-road reference
initialisation. Progress cached contributions still called `accepts`, which loaded
full JSON, sampled anchors and built a spatial grid for each distinct A road.
Both the template map and aggregate roads could retain those grids until
the entire collection finished. This retention is confirmed in source. The
relative contributions of map, railway, NCN and other simultaneous allocators
to the owner crash require a device memory trace.

A-road references now stream their original assets, retain coverage metadata
for cached contributions, and limit the in-memory matching cache to 16,384 sampled
anchors plus the one road being constructed. Canonical order, sampling and
coverage checks stay the same. Progress no longer retains all A-road map lines.
A focused A-road map recreates its lines from the corresponding canonical
coverage. Local discovery geometry is kept serialized until one road needs
settlement enrichment. Focused B-road details get geometry from their contributors.

At build 284's reported warm focused scan, four source reads coexisted with
1,657 reused rows. Every reused row was still checksummed, parsed, and handed
to the visitor. The new checksummed index screens out unrelated refs/names
before payload decode. Full feature selection, region, correction and geometry
checks still run for candidates. A map point lookup restricts subsequent reads
to known contributing IDs. The first cold index fill still traverses the archive.

The full Map refresh now preserves a valid all-journeys memory snapshot when
changing filters, keeps validated per-source rows on retry, and skips rewriting a
disk snapshot that was just read. Snapshot headroom checks include coverage
and tappable lines. Snapshot decode has a 12 MiB encoded cap and uses one payload
buffer. The renderer reuses its transformation buffer per visible line.
Instrumentation records the navigation path, rows read, rebuilt or reused,
and sampled JVM heap high water with railway activity. Allocations between
samples can exceed the reported peak.

## Evidence retention audit

| Record | Verified retained evidence | Recoverable or missing |
|---|---|---|
| Automatic Android capture | `CaptureService` writes raw capture geometry and timestamped samples with accuracy, speed, provider, elapsed realtime and fix age where available. Prepared samples and display geometry can differ. Newer rows can include bounded activity recognition, capture decisions, contextual/station evidence, stops, places, quality and arrival dwell. | These are observations retained by capture. Fixes never saved and history evicted by bounded capture fields cannot be reconstructed. Older private exports include tuples without provider or elapsed time. |
| Timeline import | `TimelineImportActivity` stores a stable import ID, source type and URI-derived fingerprint, normalised mode, boundaries, distance and available imported route. `TimelineSourceEvidence` carries bounded source activity and aligned point timestamps for supported current semantic imports. | Import path times are not raw GPS. Legacy import timing may be absent. Accuracy, provider and Android motion data are unavailable. The source fingerprint does not recover the file. The import reads the selected source URI and persists derived records. The inspected older Timeline export has no raw capture or timing envelope. |
| Matched and unmatched | `MatchingCoordinator` and the capture preparation classes keep the current match result, metrics, geometry, quality and retry information when present. A rejected source may still have usable raw sections. | An unmatched row may have no match geometry. Retries can replace results or diagnostics. Match history generally has no version ledger. Zero matched distance cannot establish zero movement. |
| User edits | `JourneyStore.saveDetails` versions title and mode changes and marks manual transport confirmation. `JourneyMapEditorActivity` persists exclusions and can splice recorded geometry. Stops and place reviews are part of the saved record. | Modern raw fields survive a route edit. The previous display geometry and match can be overwritten. Older rows without raw fields may retain only the edited route. Removed edge indices apply to the matching geometry that produced them. |
| Split or joined | `FerryJourneyRecovery` journals an explicit split, keeps a private `ferry_original_` backup and gives children source ID, backup key, boundaries and sibling IDs. Capture may save separate legs at supported transitions. | The ferry original is accessible through its existing restoration path if present. There is no general historical split/join lineage for old captures and no general-purpose join workflow. |
| Railway reassessment | `RailwayTrialWorker` reads captured fixes and stores comparisons. Accepted `RailwayMileage` updates retain supported provenance, migration receipt, previous distance and rollback fields. | Legacy before/after data may be absent. Exact historical totals cannot be recovered without old archive or Progress evidence. Track distance and coverage gaps are distinct from raw observations. |
| Collection derivation | NCN ledgers/checkpoints, normalized road contributions, venue visits and achievement state use current collection-specific predicates and saved evidence. | Derived caches and award receipts do not recover missing GPS. Read current qualifying evidence before changing derived credit. |
| Older or partially migrated | `JourneyStore` upgrades shared metadata and migrates legacy preferences into the per-journey archive. The original bytes still present are preserved by the new explicit check. | Synthesized quality or empty stop/place fields do not prove historic capture. Pre-existing pruning and overwrites can leave unrecoverable data. |

## Reconciliation and rollback

Journey JSON remains authoritative. The existing journey detail offers **Check
saved evidence** as an explicit dry run. The worker queues requested IDs,
processes up to 16 per slice, checks for capture and cancellation, and accepts
a committed archive snapshot up to 2 MiB per journey. Oversized and missing
sources receive explicit outcomes. It does not schedule an archive-wide pass,
require charging or idle, or write back an interpretation.

A content-addressed copy preserves the observed bytes independently from the
versioned comparison. The persisted receipt includes the archive token, legacy
revision when present, SHA-256 of source bytes, decision and interpretation
checksums, outcome, and separate decision/proposal payloads. The baseline
records stored boundaries, mode, geometry and stored distance while leaving
accepted distance, confidence and unresolved segments unspecified. A future
rule can compare the same evidence under a new version and propose a safe
candidate or a result requiring review. Existing geometry edits, confirmed modes
and ferry recovery conservatively move otherwise safe candidates to review.

File writes stage outside the archive lock. Receipt insertion and dequeue use
one database transaction after verifying the archive token under the store lock.
A process stop before receipt commit leaves the ID pending. The next slice
reuses the unchanged source/version receipt or the byte-exact copy. Explicit
journey deletion removes its reconciliation copies and receipts. Rollback
material is retained for reviewed future application. Stage 1 has no promotion
path, so there is no changed distance or collection result to roll back.

## Owner regression contracts

Private traces stay out of the repository. Verify the identified exports by
checksum before replaying, and keep the real and synthetic fixtures distinct.

| Case | Evidence reviewed | Subsequent acceptance |
|---|---|---|
| A · Colchester | Export `20261114-155438`, SHA-256 `8b4d82ef610365da6058bf1824c2945b1d8f80172862c86fa7dd9f0f27ef641d`. 1,621 raw and 1,320 route samples over 6,030 s. Failed match, evidence rejection, stored distance 0. Owner reports point 526, gap 165,596 ms and about 1,470 m displacement. | Preserve valid sections on both sides. Label the gap unresolved. Require qualifying evidence for road credit and account for recorded vs matched distance separately. |
| B · Frimley Lodge | Export `20261114-160155`, SHA-256 `ca6b33c832d32f4889d96066f7856f0da22fb9420a49dc80a5bd4e1ff1ea60c8`. 623 raw, 479 validated route points, 2,378 s and 5,467 m stored. Partial result matched 24 of 171 submitted tracepoints. The reported Strava screenshot is visual context. | Keep valid off-network movement. Reject implausible jumps and require independent evidence for qualifying match credit. |
| C · St Albans | Owner report. No verified export identified for replay. | Show continuous credible on-foot recording including unmapped terrain. Use a synthetic equivalent until the original export is available. |
| D · Gravesend / HS1 | Six candidate Android train exports inspected; sample counts 230–1,024, with intermittent fixes and varied capture versions. Existing Stage 4.7 public fixtures pin HS1 and separately verify tunnel/interchange behaviour. | Reuse the national matcher and benchmark. Unresolved topology or boundary ambiguity remains visible. No assumed distance for an unverified owner journey. |
| E · Train to car | 12 November evening export `20261113-224131` contains 444 raw and 266 route samples, two stops and a saved train mode. Owner reports an immediate car pickup. Exact boundary and separate corroborating car trace are unverified. | Recognise a supported transition or surface it for reversible review. Preserve source lineage and avoid committing new mileage from ambiguous evidence. |

## Release and Stage 2 gates

- Use the 1,661-row or larger regression archive to repeat Map, Journeys,
  Progress and Achievements, once while railway checking is active where the
  worker is supported. Record cold and warm timings, JVM heap and source
  reads. Repeat on the owner's Pixel 10 after a verified upgrade.
- Proposed Pixel acceptance budgets: warm navigation first content within
  1 second, unchanged Map zero archive decodes, cold Map and Progress usable
  within 10 seconds, focused warm road within 3 seconds, and sampled Java
  heap below 224 MiB across repeated transitions. Record every frame over
  100 ms during that run. Build 284 showed 27.7 s Progress first content,
  25.8 s Map preparation, 10.3–11.7 s warm focused scan and a 403 ms frame.
  These budgets require device measurement.
- Verify old/new archive counts, hashes for unchanged journeys, titles,
  stops, user decisions, all collection balances and established package
  signing identity before any APK release. A dry-run receipt must leave
  authoritative evidence unchanged.
- Stage 2 needs physical stability evidence, a rule to segment reliable
  evidence around gaps, an explicit recorded/matched/qualifying distance
  contract, and a review/acceptance policy for corrected interpretations.
  First replay A and B privately, then test transition and tunnel contracts.
  A future accepted interpretation must bind to the analyzed source version
  and its user decisions before any collection reconciliation is allowed.
