# Achievement memory correction — 4 November 2026

Scope: #256; source baseline main `40f3c0d00e171800a157af4b3db5d4125134203b`, including merged #250. Branch `fix/achievement-memory-20261104`, targeting main independently. Do not merge/release from this task.

## Baseline and delivery state

Independently fetched main and cloned HEAD agree. Main Android metadata is 0.25.220 (266); the reported installed 0.25.221 (267) comes from open packaging #252, source `c5cafa9c5ea9f9200a04d45aead0839b6b5c6625`, which adds version/notes/workflow over merged capture code. Prior packaging reports 821 runnable Android tests passing and four opt-in candidate skips. Open #238/#240/#241/#248/#249/#251/#252/#254 were inspected; none contains either requested correction. Existing driving investigation #255 is retained; memory crash now has focused #256 rather than duplicating #201's older UI symptoms.

Installed version is owner-reported, not connected-device verification. Live backend SHA and active Firebase pins are unknown and are not inferred from main. This work changes neither and requires no production reads or mutation. Source-pinned reference cache checks remain in CI. No APK, integration, deployment, publication or historical rewrite.

## Mechanism and correction

The supplied 1 November OOM failed in a boxed grid lookup in LandmarkEvidence.cover, with less than 1% heap free after GC. That is the last allocation, not proof that landmark samples filled the heap (the packaged catalogue has about 13,400 samples).

AchievementStore already streams archive files, but CoreAchievementEvidence.named and townRoads retain parsed full-road JSON coordinate trees across the entire scan. Each entry also retains geometry.toString() as its identity key. Thus the full parsed coordinate graph remains live with its lossless serialized copy until settlement attribution finishes, even though the archive reader moves on. Two maps separately serialize the same geometry. Other calculators and the app remain resident concurrently.

Keep the existing serialized geometry identity as a LinkedHashSet entry and reconstruct the geometry only for the current road's settlement attribution. Retain the complete JSON, fields, coordinates, insertion order and de-duplication; do not simplify, truncate or drop evidence. No cache format/version, unlock algorithm, persisted history, reference data or journey mutation changes. Only CoreAchievementEvidence and TownAchievementEvidence production files change.

Confidence: high that redundant retained graphs exist and this correction removes them; medium that they were a major contributor to this specific crash. The private archive and ART heap dump are unavailable, so the exact owner/device heap is not reconstructed. This is a reduction in retained memory, not a universal bound for arbitrarily large histories. Unique coverage and serialized geometry still scale with distinct evidence; a single road with huge geometry remains a risk.

## Validation

Four production-Java JUnit tests pass locally: exact serialized round-trip with independently materialized trees, duplicate/repeat identity, MultiLineString/extra-field preservation, correction exclusions and 1,605 repeats without inflated unique coverage. Existing archive/achievement/unlock/history/UI tests run in the separate no-APK Android gate; full CI result pending.

A standalone probe runs three fresh CoreAchievementEvidence scans of 1,605 invented journeys, each with a distinct 500-vertex full road and a short separate matched path: 802,500 full-road vertices per scan. At `java -Xmx256m`, before retained heaps were 166,984,240 / 166,871,792 / 166,896,592 bytes; after initial probe 47,185,696 / 46,727,656 / 46,816,096 bytes. Approximately 72% less retained heap. Totals and 1,605 road identities agree. This isolates retention in actual production CoreAchievementEvidence, with Android/settlement service stubs; it is not full AchievementStore or ART memory. Final test-class probe and full Android CI supply independent follow-up measurements.

Reproduce the probe after Android test compilation with `AchievementGeometryMemoryTest.main` using the test runtime classpath and a 256 MiB heap; the dedicated workflow records it. Before comparison: compile the same fixture against baseline CoreAchievementEvidence and baseline TownAchievementEvidence.addRoadDiscovery. Local Java 17 / ECJ 3.39 / org.json 20240303 / JUnit 4.13.2 were used. Local setup stubs Android Context/AssetManager, settlement lookup and the map-editor linkage; production correction parsing and geometry methods are retained. No private locations or user journeys are in fixtures.

## Device acceptance after separately approved packaging

Install over existing Roadprints, without clearing storage, while no journey is active. With the existing ~1,605-journey history, open Achievements, wait for a completed refresh, switch tabs and reopen ten times, then restart and repeat. Record time, Java heap/PSS, whether progress refresh completes, unlock/celebration/history consistency and capture continuity. Compare to baseline under the same history and device conditions; do not artificially award or rewrite journeys. Collect a heap dump if memory still grows. Do not call the screen stable on Pixel until this acceptance passes.
