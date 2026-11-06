# Stage 8 — isolated Android acceptance and runtime profiling

6 November 2026. Issue #294; PR #295; target `main`, unmerged.

## Delivery receipt and evidence boundary

Starting main: `d3d4188ae66549b046d93ef76c2be9a740a41a03`, independently verified as PR #293's merge. Branch: `feat/milepost-device-acceptance-20261106`. Final PR head, ending main, CI run, exact test counts and JVM probe values are recorded in the final PR receipt (a document cannot name its own eventual commit SHA). No main source Java, production manifest, production migration, gate or release version change is needed.

**Implemented, device acceptance outstanding.** This Work container has Java 17 and networked GitHub CI access, but no Android SDK, `adb`, emulator binary or attached Android surface. No emulator or physical device was run. Installed owner APK/version/source, live backend SHA and device-active Firebase/NCN pins remain unknown. Source remains 0.25.223 (269); this is not an assertion about an installed phone.

| Evidence class | Execution and interpretation |
| --- | --- |
| Host Python | Catalogue validator plus source isolation; exact counts in PR |
| JVM/Robolectric | Normal regression selection plus acceptance fixture checks in CI; not ART |
| Android emulator/instrumented runtime | Instrumentation source compiled in CI; execution **unverified** |
| Physical Android | Model, OS, RAM, ART/native/PSS, GPS continuity, TalkBack and frame measurements **unverified** |

CI status and exact counts must be read with the final-head receipt. Earlier failed configuration attempts do not count as validation. Three unrelated historical branch-gated workflows skip intentionally; that is separate from test skips.

## Harness and isolation

The `acceptance` build type inherits debug compilation but uses application ID **`com.roadprints.capture.milepostacceptance`**, a different Android UID/data directory from `com.roadprints.capture`. Its version name has `-synthetic-acceptance`; backups stay disabled. Firebase automatic initialisation and acceptance-only Google Services/Crashlytics build tasks are disabled. Normal debug/release tasks are unchanged. No APK is built or distributed by the validation workflow.

`src/acceptance` contains the fixture, assertion runner, native host and profiler. `src/androidTest` holds instrumentation; `src/testAcceptance` runs the same processing assertions on Robolectric. None is compiled into normal debug or release. `MilepostReleaseIsolationTest` checks the immutable false gate/private constructor and absence of acceptance classes on the ordinary JVM classpath. `scripts/milepost_acceptance_isolation.py --compiled` additionally checks release Java output and merged manifest, alongside source references and packaged asset paths. This is compiled-source/manifest evidence, not an audit of a distributed APK; no release APK was produced.

The acceptance-only fixture rejects a mismatched generated application ID **or** runtime package before writes. It creates distinct eligible `synthetic:pN` physical posts with canonical aliases, verified synthetic designer attribution and native walking evidence derived from the existing Stage 2/5 fixtures. It installs only this small synthetic envelope in `synthetic-acceptance.db`, publishes a synthetic NCN snapshot in this separate process and reflects a **separate** private integration instance. It never replaces `MilepostIntegration.PRODUCTION`. All 889 real candidates remain unapproved and absent from packaged assets. Synthetic rows, presentation preferences, archives and entitlement state cannot enter the owner's production package through the harness. No importer, backup, Intent extra or preference can activate a release entry point that is absent from that variant; the immutable production gate still fails closed for all such state.

Fixtures deliberately use normal archive/preference filenames **inside the isolated sandbox**, so the real Stage 4–7 components run unchanged. Seed controls replace the sandbox's archive; do not import a private archive there. Seeding is refused during active sandbox capture. Synthetic setup may be interrupted if capture begins during bulk setup. The normal package's data is never opened or cleared. Normal sandbox navigation/capture is available from the host; pressing Back returns to acceptance. The host mounts the existing `MilepostScreenIntegration` and native presentation; the normal Achievements activity still observes the disabled production gate. This validates the same section adapter, not production activation of that screen.

The worker retains the real two-second **cooperative deadline**, slice checkpoints, capture guard, freshness validation and processing/presentation lease. Resume reconciliation explicitly retries incomplete work. No new job scheduler is introduced; gate-disabled production scheduling remains forbidden. The 1,605-journey setup creates 1,605 tiny duplicate-support synthetic journeys and seven posts, not a representative large private geometry archive. It is a reproducible scale fixture; it does not resolve the prior Stage 4 geometry-memory risk.

## Scenario coverage and current results

| Scenario | Runnable implementation | Current acceptance |
| --- | --- | --- |
| One physical post and Mills award | Shared processing assertion on actual evaluator/ledger/deriver | JVM CI; ART unverified |
| Four designers, Harrow seven posts, Grimshaw and collective | Synthetic NCN completion and seven canonical posts; six individuals plus collective assertion | JVM CI; ART unverified |
| Historical consolidated summary and duplicates | Seven new unique posts, unchanged recognition ordinal after repeat processing | JVM CI; ART unverified |
| Correction, withdrawal, fallback, reinstatement | Two support journeys; delete first, fail second, close/reopen ledger and restore | JVM CI; ART unverified |
| Cancellation/resumption and lease cleanup | Cancelled token and simulated active-capture state; converge after resumption | JVM CI; ART unverified |
| Full replay, ten entries, Later and activity recreation | Instrumentation uses native Stage 7 mounting and Stage 6 replay/store | Compiled; runtime unverified |
| Actual process death/restart | Two explicit runner invocations with intervening force-stop | Commands ready; unverified |
| Capture starts during replay | Instrumentation sets sandbox capture preference, asserts paused cursor and refusal to advance | Compiled; simulated state only; not GPS evidence |
| Capture starts during reconciliation | Existing Stage 4–7 JVM race coverage retained; physical manual protocol below | Actual timing/GPS unverified |
| Background/foreground and replay restoration | ActivityScenario lifecycle transitions | Compiled; runtime unverified |
| Disabled release / synthetic persistence isolation | Existing Stage 7 gate-off tests, ordinary-variant class absence, release classes/manifest checks | CI/source evidence; installed release acceptance unverified |

The shared checks reopen SQLite to exercise durable worker state. Activity recreation is **not** labelled process death. Default instrumentation `processRestartReceipt` only prepares a receipt; only its separate verify invocation establishes cross-process persistence. `manualAcceptanceWindow` opens a bounded tester interaction window and passes when that window ends; it does **not** assert manual acceptance. Record observed results separately.

## Reproducible build and test commands

Use Java 17, existing Android SDK 35 and Gradle 8.10. Restore the exact county/NCN cache and pinned Robolectric SDKs as specified in `.github/workflows/millennium-milepost-evidence.yml`; no new reference publication/download is required. Unit-test workers block reference HTTP. CI compiles app/debug/release/acceptance, both test source sets and instrumentation, lints debug/acceptance, runs focused regression tests, checks isolation, and runs catalogue tests. The separate streaming evaluator uses its existing 64MiB JVM.

```bash
# No APK packaging:
gradle --no-daemon -p android-prototype -Pandroid.builder.sdkDownload=false \
  :app:compileDebugJavaWithJavac :app:compileReleaseJavaWithJavac \
  :app:compileAcceptanceJavaWithJavac :app:compileAcceptanceAndroidTestJavaWithJavac \
  :app:lintDebug :app:lintAcceptance
gradle --no-daemon -p android-prototype -Pandroid.builder.sdkDownload=false \
  :app:testAcceptanceUnitTest --tests '*MilepostAcceptanceHarnessTest'
python3 scripts/milepost_acceptance_isolation.py --compiled
python3 -m unittest scripts.test_milepost_catalogue scripts.milepost_acceptance_isolation -q
```

The task authorises private acceptance harness validation; the following local packaging/install commands are for an equipped acceptance workstation, **not public/beta distribution**. They were not run in this session. Use a dedicated test device/profile, preserve the normal package, and confirm the suffixed package before starting. There is no reason to install or clear the production package.

```bash
gradle --no-daemon -p android-prototype -Pandroid.builder.sdkDownload=false \
  :app:assembleAcceptance :app:assembleAcceptanceAndroidTest
adb -s "$TEST_SERIAL" install -r android-prototype/app/build/outputs/apk/acceptance/app-acceptance.apk
adb -s "$TEST_SERIAL" install -r android-prototype/app/build/outputs/apk/androidTest/acceptance/app-acceptance-androidTest.apk
# Install is local only. Record SHA-256 of both APKs, source SHA and signing identity privately.
gradle --no-daemon -p android-prototype :app:connectedAcceptanceAndroidTest
```

Use `ANDROID_SERIAL` when invoking connected tests to select only the intended device. `testBuildType=acceptance` affects instrumentation only; normal debug unit regressions retain their task and test selection.

For a measured individual test, use an explicit device serial and exact source SHA:

```bash
python3 scripts/milepost_acceptance_collect.py --serial "$TEST_SERIAL" \
  --runtime-kind physical --source-sha "$ACCEPTANCE_SOURCE_SHA" \
  --output /tmp/milepost-runtime-01 \
  --method repeatedEntryReplayRecreationAndRuntimeProfile
```

Run each method listed by `--help` separately for clean receipts. Use `--runtime-kind emulator` for an emulator; the classification and source SHA are operator-supplied and must match the build/device receipt. The collector verifies the suffixed installed package, runs only its instrumentation and gathers model/Android/RAM/package dumps, timestamps, one-second `dumpsys meminfo`, in-app samples, frame stats, exit reasons and relevant logs. Missing diagnostics are marked unavailable. It never installs, clears or distributes an APK. Logs append across host sessions; use elapsed timestamps and receipt time to select the current interval. Protect these outputs: manual movement logs can contain locations. Filter logcat against the acceptance PID/time interval when attributing errors; a broad AndroidRuntime error is not automatically this app's failure.

For **actual process restart**, run these separately and retain both receipts:

```bash
python3 scripts/milepost_acceptance_collect.py --serial "$TEST_SERIAL" --runtime-kind physical \
  --source-sha "$ACCEPTANCE_SOURCE_SHA" --output /tmp/milepost-restart-prepare \
  --method processRestartReceipt --restart-phase prepare
adb -s "$TEST_SERIAL" shell am force-stop com.roadprints.capture.milepostacceptance
python3 scripts/milepost_acceptance_collect.py --serial "$TEST_SERIAL" --runtime-kind physical \
  --source-sha "$ACCEPTANCE_SOURCE_SHA" --output /tmp/milepost-restart-verify \
  --method processRestartReceipt --restart-phase verify
```

Do not seed between these runs. Verify restores the committed receipt and paused cursor 2 without replacing the archive or database.

## Runtime profiling and interpretation

`MilepostRuntimeProbe` records device model, Android API/release, RAM, variant and display refresh rate. At 250ms it samples used Java/ART heap, native allocated heap, total PSS, and available ART runtime GC counters/times from `Debug.getRuntimeStats()`. The external collector samples `dumpsys meminfo` at roughly one-second intervals. These are sampled process peaks, not exact allocation maxima. Java/native/PSS overlap and must **not be added**. FrameMetrics records count, max total duration, count above a descriptive 16.67ms marker and callback drops; the marker is not a device acceptance threshold. A 50ms foreground heartbeat records its largest scheduling delay; it is not a hard ANR detector. Existing `PerformanceDiagnostics` also logs slice/setup timings and foreground stalls; `CrashReporter`, logcat and historical exit reasons are retained for OOM/ANR investigation. Missing exit information is unverified, not proof of no crash.

Request GC via the host control, wait for counters to confirm collections and collect a stable post-GC interval. GC is a request, not a guarantee. Compare warm idle baseline, processing peak, ten-cycle replay peak and post-GC retained level under the **same run/device/workload**. A profiler's presence and sampler overhead affect timings. Use Android Studio allocations/heap dumps and Perfetto when retained views/listeners, lock waits or short-lived peaks need attribution. The probe only runs while its host is foreground; external PSS/Perfetto and existing capture/performance diagnostics must cover background capture and navigation. In-app `entry_to_view_ms` measures host resume through fixture restore/processing/view polling (100ms granularity), not pure production screen rendering. Slice timing includes setup when `archive_setup=true`; exclude those records when comparing normal processing slices.

No new numeric ART/PSS/latency results exist in this session. Earlier Stage 4 ~219MiB total sampled JVM peak, Stage 6 ~19.4MiB replay increment and Stage 7 ~48MiB warm integration increment had different workloads; they are not combined or treated as ART budgets. Existing warm JVM guards remain <64MiB incremental peak and <8MiB retained growth, plus the existing 64MiB streaming evaluator process limit.

Proposed Stage 9 screening criteria (not validated device budgets): zero attributable OOM/ANR, zero duplicate awards/journeys, no missing capture fixes attributable to the harness, and no increasing retained-heap trend across warmed repeated cycles. Start retained-growth investigation at the existing 8MiB JVM guard rather than declaring that an ART entitlement. Compare p95 frame duration to the device's actual vsync interval and require no regression against a matched gate-disabled capture/navigation run; establish a numeric tolerance from repeated baseline variance. Peak ART/native/PSS must leave measured headroom for real map/NCN/capture transients on the lowest supported device. The two-second worker deadline remains cooperative, not a demonstrated hard device budget. Final thresholds require physical traces and owner acceptance.

## Capture continuity — manual physical run outstanding

1. Open `manualAcceptanceWindow` with `--manual-seconds 600`; instrumentation holds the non-exported host for inspection. Use `--keep-fixtures` to preserve an earlier 1,605-journey setup. On first use the default seed contains seven posts/two journeys. From the host, enable existing Movement Diagnostics in normal sandbox navigation. Grant location/activity/notification permissions to the **acceptance package**. Record GPS provider, requested interval, signal conditions, battery/thermal state and baseline capture-only fix cadence.
2. Start a genuine walking/cycling capture in normal sandbox navigation, return to the host, and verify paused/suppressed replay while capture evidence/foreground notification continues. Do not seed or replace the archive while recording. Navigate/background/foreground and return without losing capture. Confirm `roadprints_movement_diagnostics.jsonl` timestamps, provider availability and existing capture decisions; the automated preference toggle is not this test.
3. With recording stopped, seed the large synthetic archive and Resume reconciliation. Begin capture from normal navigation while a slice runs; record the request, active-state publication, cancellation and next GPS observation timestamps. Repeat during replay at stages 1–7. Shared serial execution and navigation can affect start latency; measure it rather than assuming cancellation establishes continuity.
4. Stop/interrupt a worker, resume slices and verify eventual convergence/no duplicate recognition. Background/foreground while capturing. Force-stop/restart is a destructive lifecycle event for the sandbox service; verify supported recovery from diagnostics without claiming Android continues GPS collection while a process is dead.
5. Compare capture-only and combined runs: inter-fix gaps, delayed/missing fixes and their causes, capture-start/cancellation latency, journey IDs/counts and new archive rows. Export the existing diagnostics and preserve originals. Any attributable lost evidence, unexpected capture stop or duplicated journey is a fail. Current latency/dropped-fix count: **unverified**.

## Accessibility — manual physical run outstanding

Instrumentation checks native replay focusability, cipher exclusion from accessibility and minimum 48dp button height; existing Stage 6 JVM tests retain spoiler-safe cards, progress announcements and static reveal semantics. These checks do not establish TalkBack usability or actual measured target geometry.

During the manual window test TalkBack on one-post fixtures (locked identities must remain anonymous), the seven-post summary and all static reveal stages. Confirm mystery progress announcements, heading order and no scrambled cipher/hidden names in speech. Test Next/Skip/Later/Resume, keyboard Tab/Shift-Tab/Enter and switch navigation. Recreate/interrupt/reopen at each stage; verify paused restoration. Use actual touch bounds/layout inspection at narrow portrait and landscape, 200% system font scaling and reduced-motion/animator-disabled settings. Record original system settings before changing them and restore those exact values afterward. Verify all text remains readable/reachable and the host's setup controls scroll independently of the presentation. No identity/progress action may require colour, animation or sound. Record screenshots, focus order and manual pass/fail rather than treating an ended interaction window as success.

Current TalkBack speech, 200% clipping, physical target bounds, switch navigation, reduced-motion system integration and nested scrolling: **unverified**.

## Blockers and Stage 9

No production activation is justified. Remaining blockers: actual emulator/instrumentation execution; physical combined capture/memory/frame/large-archive convergence and accessibility acceptance; approved real catalogue with compatible verified installation; final artwork/biography/rights approval; and separately authorised activation/release. #283 historical Timeline recovery and #286 Utilities portability remain deferred.

Recommended Stage 9: execute the checked-in harness/protocol on the owner's Pixel 10 and a lower-memory or Samsung Android device in isolated acceptance sandboxes, record exact installed/source/fixture provenance, run restart/capture/accessibility scenarios, establish measured ART/PSS/frame budgets and fix only demonstrated defects. Keep the production gate disabled. This Stage 8 PR stops validated and unmerged; #294 stays open pending integration/owner acceptance. No backend, Firebase/catalogue publication, production migration, release APK or tester distribution.
