# Roadprints · Stage 4 controlled Railway Intelligence trial

**Decision: keep activation off.** The regional evaluator and reassessment worker are wired into the genuine Android app for a controlled comparison. The app retains its single existing displayed distance and totals. The trial writes a separate proposed distance, confidence and provenance. Publishing those values to the authoritative measurement path is a later activation step after device acceptance.

Source base is GitHub main `55e9a2a58307a9b4899050670b5d0c6e3641961d`. Product baseline is [PR #308](https://github.com/adamdbird-gfc/uk-road-tracker/pull/308), with Stage 3's whole-route replacement policy. The switch requires a debuggable build and a private trial marker. The pinned regional SQLite asset is in debug assets. The application ID, build number and signing configuration were preserved.

## What the trial exercises

- The Android implementation reads an indexed, read-only regional catalogue. Installation inflates the asset to a temporary file, verifies its size and SHA-256, then commits the file atomically. The reference pin is `stage1-osm-20261112-r1`, 15,126,528 bytes uncompressed. Source attribution is next to the asset.
- The Java matcher compares connected eligible rail against roads and competing rail corridors, with the prior limits for fix accuracy, gaps, headings, graph search and witness support. Confirmed route distance is measured along selected physical paths. A seam must join the same physical edge position or graph node. Whole-route proposals require connected support between both saved boundaries. Partial or ambiguous results carry the saved fallback, including unresolved distance if absent.
- The worker reads explicitly staged copies of saved Train and Unknown evidence. It preserves the source file bytes. It handles the historic five-field fix format, isolates the GPS stream where available and withholds same-timestamp conflicts. Derived receipts contain source journey identity, input fingerprint, evidence window, matcher/catalogue version, confidence and separate proposed/supported metres. Entitlement is outside this path.
- One background thread processes up to eight inputs per batch. It yields to active capture and when the screen is paused. Per-input atomic receipts are the durable checkpoint, so a restart skips matching the same evidence and versions. A title-only edit does not change the match fingerprint. Cancellation or source replacement before commit discards the pending receipt. Oversized evidence is held with a durable reason.
- Main and Map are the trial entry points. Inputs, receipts and the catalogue cache are confined to the app-private `files/railway-trial` directory. The worker never enumerates the live archive. Stage 4 deliberately stages a selected set for the controlled trial. Live archive indexing, automatic production migration and publishing a new effective distance to aggregate consumers remain activation work.

## Focused validation

| Check | Result |
| --- | --- |
| Independent complete reference segments | Sampled 35 synthetic tracks, each 1,800 m long, along the July 2023 Network Rail ReferenceLines archive on BEX, NKL and HDR. The matcher used the independently sourced OSM graph. **18 complete matches**, all within the Stage 3 tolerance of 50 m for these lengths, with a maximum absolute difference of **1.552 m**. The other **17** were held below full confidence. An example is HDR asset `9003000554`, chain distance 1,800–3,600 m, matched **1,799.934 m**. This validates full-segment distance against an independent reference. A complete owner-recorded journey with independently verified equivalent endpoints remains outstanding. |
| Genuine rail | On the saved 9 November GPS replay, the Java matcher found **9,788.979 m** over 81 supported pairs. On the original 12 November Unknown trace it found **761.034 m** over four pairs. Both are partial and keep the saved fallback. The small change from Stage 3 physical-pair sums reflects preferring candidates that continue at a common physical seam. Canonical corridor unions are a different measure. |
| Genuine road and wrong label | Directly evaluating the real drive, bypassing the stored-mode gate as with a Train label, returned **0 m**. The pinned 299.9 m Milton Road overlap also returned **0 m**. |
| Recovery and data preservation | Host JUnit passed full-route/gap/held/road/cancellation tests plus the worker receipt test. The receipt test interrupted reads and matching, reran, verified one result, reprocessed identical and title-edited inputs, and checked the staged file bytes. It used the Android 15 framework JAR for JSON and atomic-file classes, with host substitutes for logging and app linkage. Physical process death and runtime scheduling still need device verification. |
| Android build and runtime | The four new Android classes compiled with Java 17 language/target settings against the Android 15 framework JAR and host app-linkage substitutes. The focused Android SQLite/Robolectric regression is included. Full Gradle Android app compile and execution, Android SQLite/Robolectric runtime and Pixel profiling remain unverified in this environment. |

Reference archive SHA-256 `c3f40f0b371735a71c0bc53b6c73b670b8d9975a9e586cc3ffe1192b57757599`. Public geometry fixture `src/test/resources/railway/hdr-reference.csv` is derived from the named Network Rail asset under the Open Government Licence. The host replay used equivalent catalogue queries through an in-memory adapter. The Android SQLite test is the next execution check for the packaged asset and query path.

## One Pixel 10 acceptance procedure

1. From this commit, run the genuine app's focused Debug tests. Use Gradle `:app:compileDebugJavaWithJavac :app:testDebugUnitTest --tests '*RailwayStage4Test' --tests '*RailwayTrialWorkerTest' --tests '*RegionalRailwayCatalogueTest'`. Use one separately authorised, upgrade-compatible Debug build signed with the installed identity and a suitable version code. Record the source SHA, APK SHA, installed version and Android version. Use the existing trusted ADB computer. Do not uninstall or clear the app.
2. Choose up to 100 existing eligible journeys for the trial, including the genuine rail/road controls, one oversized input and a complete independently route-labelled example if available. Stage copies only, with one JSON object per file. A diagnostic export's outer `journey` field must be unwrapped. Give files simple unique names such as `001.json`. Example ADB transport from the trusted computer:

   ```sh
   adb shell run-as com.roadprints.capture mkdir -p files/railway-trial/input
   adb shell -T 'run-as com.roadprints.capture sh -c "cat > files/railway-trial/input/001.json"' < 001.json
   adb shell run-as com.roadprints.capture rm -f files/railway-trial/enabled
   ```

3. Record one 30-minute baseline with the marker absent and one comparable 30-minute window with it enabled. Keep screen settings, capture arming, map/overlays and actions comparable. Use Main or Map for processing and measure the active capture preemption separately within the same session. Start Perfetto/System Tracing and record package memory, per-thread CPU, wakeups, thermal state and a resolution-qualified energy or charge measurement. Enable only after the baseline. Reopen Main or Map to start the bounded batches.

   ```sh
   adb shell run-as com.roadprints.capture touch files/railway-trial/enabled
   adb logcat -d -s RoadprintsRailway:I
   adb shell dumpsys meminfo com.roadprints.capture
   adb shell run-as com.roadprints.capture ls files/railway-trial/results
   ```

4. While work is pending, background and reopen the app, then restart the process once between journeys. Verify one receipt per fixture, no new match for identical evidence, and the correct fallback on partial, road and oversized examples. Use a staged copy to exercise an edit or removal while a match is pending. Compare fixture hashes and selected live archive file hashes before and after. Review the same saved journey distances and totals in Home, map and history. Copy back only the needed receipts and diagnostics, keeping them private. Remove the marker at the end.
5. Apply the Stage 3 budgets. Extra managed heap ≤16 MiB, peak RSS growth ≤32 MiB, retained RSS ≤12 MiB at one minute, worker cold open ≤100 ms p95, warm match ≤200 ms p95 per 400–1,000 usable fixes, ≤20 s match wall and ≤25 CPU s per 100 eligible journeys, receipt commit ≤10 ms p95 and cancellation ≤250 ms. Record catalogue installation and archive input parsing separately. Compare paired frame timing and check for matcher-attributable stalls, crashes, ANRs, GPS requests or periodic idle wakeups. Apply ≤1 percentage point additional energy draw only if the measurement can resolve it. Report inconclusive energy as open.

The trial produces an activation decision. General activation also requires publication of the one authoritative measurement across all dependent views and totals, safe archive-index integration, and rollback checks. The controlled implementation has no station, collection, backend or Firebase changes. No APK, release, archive migration or general rollout is part of this delivery.
