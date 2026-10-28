# Pre-integration source assessment — Android 0.25.220

Date: 28 October 2026. Status: **source comparison only; no application code merged**.

## What was checked

Compared packaged Android source `67e74e7d9e9873552ae98b23397cd9e265fc3517` with `main` using GitHub's commit comparison and direct file blob SHA comparisons. Historical divergent PRs #172, #163, #7 and #8 were reviewed separately in `docs/android-025220-divergent-pr-review.md`.

### Critical limitation: truncated comparison

The GitHub comparison yielded **exactly 300 file entries** (277 `android-prototype/**`, 22 `.github/**`, one other), despite 657 Android-side commits and 21 main-side commits. This is the endpoint's file cap, **not the complete changed-file set**. In particular, absence of backend or test files from that returned list is not evidence they are unchanged; earlier direct SHA checks prove `api.py`, `pedestrian_matching.py`, and `database.py` differ. Do not use this partial file list for a blind tree replacement or a completeness claim.

### Direct source evidence

| Path | Packaged Android SHA (blob prefix) | main SHA (blob prefix) | Result |
|---|---|---|---|
| `android-prototype/app/src/main/java/com/roadprints/capture/CaptureService.java` | `7d981e16581b` | `7360b5806d5c` | Different; capture lifecycle risk |
| `android-prototype/app/src/main/java/com/roadprints/capture/JourneyListActivity.java` | `3eae49283f49` | `4e48f654ff9e` | Different; history/performance risk |
| `android-prototype/app/src/main/java/com/roadprints/capture/MapActivity.java` | `962efd85d1b0` | `0af4d3b3d60e` | Different; rendering risk |
| `android-prototype/app/src/main/java/com/roadprints/capture/JourneyStore.java` | `033a7c887447` | `4e50f373f499` | Different; persisted archive risk |
| `android-prototype/app/src/main/java/com/roadprints/capture/NcnProgressStore.java` | `3954301d1618` | absent | New Android component; needs dependencies |
| `android-prototype/app/build.gradle.kts` | `d78fe2808aef` | `c6a8d502e771` | Different; build/release identity |
| `.github/workflows/build-android-prototype.yml` | `d86d27e43893` | `4f0ed0cfe306` | Different; signing/distribution risk |
| `api.py` | `1836196bdaa8` | `936f43b91fb1` | Different; keep main backend |
| `pedestrian_matching.py` | `b44a7eed49ac` | `8c464d09b243` | Different; keep main backend |
| `database.py` | `ffcf43be4f4e` | `fe0d941cf5b1` | Different; protect DB behaviour |

## Proposed safe integration execution

1. Obtain a **complete Git tree/path inventory**, not the capped GitHub compare listing. Pin current `main`, packaged Android and integration branch SHAs. Determine Android-only changed paths and files added/removed/renamed.
2. Build a disposable **candidate integration tree** on a *separate candidate branch* from the documentation branch. Carry forward packaged Android application sources, tests and necessary assets only; retain main's backend, database, governance, CI release controls and reference publication paths. Resolve build configuration and API contracts deliberately. Do not use whole-tree reset.
3. Perform static dependency checks for Android imports/resources/manifest/assets, Gradle and Java/Kotlin build configuration. Check source/API contract compatibility with current main backend.
4. Run Android compile/lint/unit tests, offline pinned-reference and NCN checks, plus backend matching regression tests. Check 429/502/503 work budget, Dover ferry drive, Charing Cross walking and archive/map performance. Tests must be executed and results recorded; historical green results are not substitutes.
5. Present exact changed-file inventory, checks, conflicts and rollback to owner before integrating candidate into `main`. No APK release, tester distribution, Render/Firebase deployment or DB mutation.
6. Only after integration passes, propose evidence-backed historical PR closures and branch cleanup.

## Current disposition

The GitHub connector provides file and commit operations but the comparison's 300-file cap and lack of a complete local source checkout/merge/test in this step prevent an honest claim of safe, complete code integration. **No application code was copied or merged.** This document records the blocking completeness check and the concrete way to resolve it. The previously approved integration branch remains documentation-only.
