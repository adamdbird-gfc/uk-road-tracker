# Roadprints development handover

Last reviewed: 2026-10-19. This is the **entrypoint**, not a replacement for issue specifications or code review.

## Latest delivery — Final Beta 1 filter recovery and road UI (#201)

**Released 0.25.205 (251)** on 19 October 2026. [Install 205](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/0gmijfgpa0mgo). Based directly on the delivered 0.25.204 app/release ancestry (`f17f2f171b584931e56e5c4a0208b839f15561af`); PR [#204](https://github.com/adamdbird-gfc/uk-road-tracker/pull/204). No NCN implementation, unrelated PR merge, tracking/matching/rematching change, collection change, coverage-calculator change or data migration.

### Filter failure mechanism and repairs

The old in-session refresh submitted map preparation to the unbounded serial archive screen worker used by history, recap and Progress. Applying filters invalidated ready state and hid every recovery control, without cancellation or a finite deadline. Revision observation could supersede pending scans while those scans continued in the queue. Startup restoration used the same date/mode predicates but began with a new queue. The blocked-history regression reproduces this queue-starvation mechanism; the precise backlog and timing on the user's phone remain device evidence to collect, rather than a remotely observed thread trace.

- Main-map preparation now uses one separate cancellable serial worker, preserving the streamed evidence projection and existing geometry budgets/calculators. A pending request is not superseded by background revision observation. Filter changes cancel the old request; generation checks reject stale results. The captured request revision is published, allowing a later relevant-data refresh instead of marking stale results current.
- The last rendered map and camera remain visible and pannable while filtering. Filter-dependent road-summary taps wait for committed results. Normal filter/legend controls stay hidden until ready; a separate **Cancel/Reset** row remains available during an update. Cancel restores the last successfully displayed filter selection and its saved preferences; Reset can immediately reuse the already displayed All view.
- A **30-second deadline** resolves slow/blocked loads to an explicit error with **Retry/Reset**, including initial restored-filter loading. Failure retains the requested selection for Retry and suppresses the automatic observer retry loop. Rendering exceptions use the same recovery path. Pause/resume retains the request; destruction cancels it.
- Retained-map identity includes the filter selection as well as journey/day revision. Queue, preparation, rendering and total success/cancel/failure times are logged under `RoadprintsTiming`, with filter choices but no coordinates. No date-window/mode matching rule or permanent progress value changed.

### Focused UI and preserved welcome overlay

- Motorway/A-road completion cards use a **white untravelled graph track**, blue/green completion, prominent percentage and travelled/total distances. Map strokes remain blue/red and green/red. A1 and A1(M) remain independently attributed and measured.
- Edge-to-edge safe-area handling replaces double top insets; header width and balanced padding keep Close at the content edge. Existing spinners, skeletons, zoom, fit, cancellation/deadline and progressive geometry remain. The counted contributing-journeys action is prominent and retains shared Journey card formatting/navigation/pagination.
- B/local screens omit unsupported statistics cards and show **Recorded sections** with the map, zoom and counted whole-road journey action. **B/local section tapping is deferred and disabled in this release**: instructions/accessibility no longer promise it. Motorway/A-road section selection remains.
- The existing eligible Welcome back overlay, counts/status/actions and prepared-map dismissal remain. During a background refresh it still blocks map touches and hides map controls/recovery controls beneath it; dismissal exposes the current recovery row without reloading geometry.

### Exact provenance and validation

Implementation `8bc3d2b207fbf761fbf0b87cac57b963698c4202`; overlay/evidence refinement `ca3600775fa8c2b0d39cf26543b865b57ed82536`; exact final tested/packaged source **`e85a74ec9c9e6221223986d0a9ae3465a0de4a1c`**. PR #204 merged as `d9a3473175735c1440a037bdeac8335903100ca0`; one immutable pinned release request `a41d253a119c23ec5ccf71d167cf9749dacd142c`. The merge commit skips a redundant push check after successful exact-source PR checks; packaging repeats the required checks before assembly/distribution..

[Android checks 39301868730](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39301868730) passed unit tests and lint. [Signed packaging/Firebase 39302469538](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39302469538) passed **646 tests; zero failures/errors/skips**, lint and stable-key APK signature verification, then uploaded release **`0gmijfgpa0mgo`** and distributed it to existing testers. [Signed APK artifact](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39302469538/artifacts/12211894019); [verification reports](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39302469538/artifacts/12212318780). Only one APK request/build was made..

Sixteen targeted regressions added: eleven filter lifecycle/recovery/summary-gating tests; repeated real route assembly for foot/date/combined/empty/All with unchanged saved-journey evidence; white-track/unchanged-values, A-road colour/selection and B/local map/action tests; and welcome-overlay touch/recovery gating while updating. Existing actual M2 geometry, A1/A1(M) identity, archive projection, navigation, achievement, collection and capture tests remain in the complete suite. One obsolete loading-copy assertion now checks the displayed recovery guidance.

### Device acceptance and limits

Beta 1 is **ready for device acceptance**, not claimed accepted from JVM tests. Install over 204 without clearing data. Verify On foot, Last 7 days, combined On foot + Last 7 days, no results, repeated Reset/Apply, Cancel, tab switches/return, saved-filter restart and failure/timeout Retry/Reset. Capture `RoadprintsTiming` queue/prepare/render/total lines on Pixel and Samsung, especially the original archive. Check M2/A-road card legibility/gestures/section journeys, B/local recorded maps/count/actions, saved-journey navigation, Welcome back dismissal, safe areas/small screens and retained scroll/camera; smoke-test capture, rematching, achievements and collections on the upgrade.

No connected device/emulator was available for this task. For performance comparison, use queue/prepare/total times for uncached filter loads and render/revisit logs for retained views; the cache-hit path has no preparation stage. B/local full reference distances/completion and section tapping remain unavailable. Existing two-road derived cache limits/local-boundary five-minute TTL and Android process-reclamation limits remain. The UI deadline bounds user waiting but cannot forcibly terminate an uninterruptible underlying operation; stale publication is blocked and recovery stays available. This does not erase or rematch any journey.

Code-review follow-up outside the focused road-detail path: a legacy settlement-overview child may recompute on return when the saved main-map selection is non-All, because its retained-selection field is not published by the separate settlement renderer. No journey/coverage loss is involved; verify and align that identity in a later small state-preservation follow-up. Main Map tab retention and the focused RoadDetail cache are covered here.

### NCN resumption handover — implementation remains paused

After this APK passes device acceptance, start app integration from this released source and the latest `fix/onboarding-start-tracking` descendant, preserving the filter recovery and shared UI. Read epic **#79**, implementation/geometry issues **#190–#194** and the existing catalogue/data PRs **#175–#187**; reuse their existing work instead of regenerating/auditing it in this slice. Reconcile the deferred **#172** trial adapter against already-integrated road-detail code; do not blindly restore its older #171 UI base. Next NCN work is the national open-way/source-provenance adapter, shared-way union, alternatives and mode filters described in those issues. Confirm current NCN issue/PR state at that future session; this release has not approved or published NCN data.


## Previous delivery — Beta 1 UI refinement (#201)

**Released 0.25.204 (250)** on 19 October 2026. [Install the UI update](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/6g411l4k07r7o). Built directly above the real-device-tested 0.25.203 stabilisation release. PR [#203](https://github.com/adamdbird-gfc/uk-road-tracker/pull/203) is limited to the six requested UI refinements. Existing map-filter, celebration, road-geometry and retained-tab fixes are preserved. No NCN, tracking, rematching, collection or coverage-calculation change was included; A1 and A1(M) retain independent identities and mileage.

### Exact changes

- **Welcome back:** the eligible discovery recap now overlays the prepared map with part of the map visible. Map controls and touch interactions remain unavailable until both map readiness and recap dismissal. Close, Back and navigation actions acknowledge only the displayed journey IDs, reveal the same prepared map and retain existing eligibility/count/distance/matching/action rules.
- **Close controls:** the shared Close control aligns visible text with the right content inset, retains a minimum 48dp touch height and is used in focused roads and shared dialogs. Focused-road safe-area padding and the achievement Close row are handled explicitly.
- **Progressive focused maps:** the map container appears immediately, with a spinner over the map and skeleton road statistics. Geometry and whole-road statistics appear before final contributing-journey metadata; the map stays visible during section lookups. Existing 30-second deadline, explicit failure/retry, generation checks and cancellation remain. A two-road bounded derived cache reuses unchanged journey revisions without any network I/O during cache lookup.
- **Zoom:** main and focused maps share the existing-style plus/minus controls, retaining initial road fitting, pinch, pan and section selection.
- **Road statistics:** a whole-road completion graph displays travelled/total distance and percentage using existing motorway blue/red and A-road green/red conventions. Selected mapped-section distance, travelled/remaining status and saved-journey frequency are separately labelled. B/local roads without a complete reference explicitly say whole-road completion is unavailable; no inferred coverage is awarded.
- **Contributing journeys:** the main Journeys and focused-road lists share date/time, mode badge and available distance/duration/GPS metadata formatting. Whole-road and section lists open the corresponding saved journey and load 30 visible cards per page without changing attribution.

### Commits and verification

Implementation commits: `086435977337dfd77b3777be484aa37645ebd355`, `ea0600e16a25010459fbdc11d6a7990e221794bd`, and the final cache-freshness correction below. Exact tested/packaged source **`bed7ef24bacb0d57b5caed676fde492563372510`**; PR #203 merged as `1a4dc2ceca87dc607c1a744bd32283bd96f2aa73`. One immutable release request `5804c64d4ad6895c06afa5dc6a08ffeef42357c0` pins that source and requests existing-tester distribution.

[Android checks 39280220062](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39280220062) passed unit tests and lint. [Packaging/distribution 39292861263](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39292861263) passed **630 tests; zero failures/errors/skips**, lint and stable-key APK signature verification, then uploaded and distributed Firebase release **`6g411l4k07r7o`** to existing testers successfully. Signed APK artifact [12206082307](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39292861263/artifacts/12206082307); verification reports [12207465039](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39292861263/artifacts/12207465039). GitHub previously reported the run queued during its Actions/Pages service incident; after recovery it exposes successful execution. The old queued checkpoint is superseded.

Thirteen new regression tests exercise recap dismissal/Back/navigation and captured IDs; map readiness and prepared-map reuse; shared Close positioning/action; immediate loading/skeleton/error-compatible UI; progressive geometry ahead of metadata; completion graph; shared contribution cards and saved-journey navigation; zoom; bounded pagination; separate section details; road/revision cache invalidation, eviction and unchanged reopening. Existing stabilisation regressions remain in the full suite.

### Acceptance and known limits

Keep #201 open for real-device UI acceptance. Install over 0.25.203 without clearing data; check recap dismissal/navigation and readiness, close alignment/safe areas, M2/A-road fit/zoom/section details, contribution navigation/long lists, small-screen layout and Map/Progress revisit state. JVM tests do not substitute for Pixel/Samsung rendering and gesture acceptance.

Numbered-road cache entries depend on exact road identity and saved-journey revision; local-road snapshots expire after five minutes because public boundary responses expose no revision token. At most two derived snapshots are retained; unusually large models are not cached (50,000 map points or 5,000 journeys). Android process reclamation can rebuild derived state. Selected-section distance describes the nearest rendered edge; frequency is explicitly the number of contributing saved journeys near it, not a count of passes within a journey. Existing B/local reference limitations and unavailable-router behaviour remain unchanged. The Work execution/usage timer is not exposed to the agent; reported elapsed time is wall clock.

## Previous delivery — Beta 1 stabilisation (#201)

**Released 0.25.203 (249)** on 19 October 2026. [Install update](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/6odqoda2eki6g). PR [#202](https://github.com/adamdbird-gfc/uk-road-tracker/pull/202) contains only the five reported Beta 1 repairs and targeted tests. Its code starts directly at installed source `5c8deea0a6237f484a865994e7148d51af4f1627`; no NCN or unrelated backlog feature was integrated. Existing journey archives, tracking state, collection ledgers, application ID and signing identity are preserved; no destructive migration or rematch was added.

### Exact changes

- **Map filter/readiness:** filter opening previously queued summary I/O behind archive-heavy screen work with no immediate UI response. It now opens synchronously with the complete set of filter choices, without an archive scan. Filter/reset, legend, zoom and recap controls stay hidden until road geometry is ready; map touch actions are blocked while loading. Failures retain retry and keep dependent controls hidden.
- **Celebrations:** the explicit action was silently gated by unrelated snapshot revision checks. It now reads the durable pending queue even while evidence refresh is queued. Repeated taps reuse the existing dialog; Next/explicit Close acknowledges only the presented milestone. Pause/system cancellation retains unacknowledged events. Automatic presentation retains its freshness gates.
- **Focused roads:** road detail previously shared the long-running background screen queue and had no finite deadline. A separate single interactive lane now loads streamed retained road evidence, with cancellation, generation checks, explicit errors/retry and a **30-second deadline**, including section-to-journey lookup. Revision changes yield retry rather than an endless reload loop. Existing canonical covered/remaining geometry and contributing journeys remain; the actual bundled GB M2 reference is exercised in regression tests.
- **Map loading:** `JourneyStore.forEachMapEvidence` streams displayed matched GeoJSON, motorway/A-road/cycling features, titles, filters and corrections without materialising raw capture arrays or upgrading every archive record. Legacy edge corrections retain their full-evidence fallback. Long summary scans no longer hold the global store monitor during disk I/O. Collection visits refresh pins independently instead of invalidating unchanged road geometry. Existing display budgets are unchanged. Location-free `RoadprintsTiming` logs separate queue, preparation, rendering and reused revisits.
- **Tab persistence:** `RoadprintsNavigation` reorders existing primary activities instead of finishing tabs or clearing those above Map. Child screens still close appropriately. Loaded views, map camera, Progress scroll and expensive process results survive ordinary tab switches; unchanged Progress avoids repeated enrichment setup and resumes its status ticker. Progress jump intents still reach retained screens.

### Commits and verification

- Repair commits: `585651dbceaf81390f3da27aff4b8a3163f4d3ae`, `d6834a76212c1172b526b990d3c9ccda6795fbc0`, and final exact tested/packaged source **`720d49921936c3ba590917ac4e5ae41dae444907`**. The middle commit reconciles only already-released documentation/request ancestry and navigation/deadline tests; no additional app features were imported.
- PR #202 merged to `fix/onboarding-start-tracking` as `200ece081a1df48c16d784bc455dc31cf54d804b`. Immutable release request **`709ecda50d2ed481531f41ca27915013881626e7`** pins the exact tested source and explicitly shares with existing Firebase testers.
- [Android checks 39272662958](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39272662958): complete unit suite and lint passed. [Packaging/distribution 39273170015](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39273170015) repeated **617 tests; zero failures/errors/skips**, lint and stable-key APK signature verification, then uploaded and distributed Firebase release **`6odqoda2eki6g`** successfully. One APK request only.
- Ten new regressions cover actual View clicks, stale-snapshot celebrations, cancellation/duplicate retention, blocked-worker isolation, finite timeout/failure/retry, real M2 covered/remaining geometry and contributing journeys, retained navigation, and exact displayed-geometry/correction fidelity. The synthetic raw-heavy record test verifies more than tenfold smaller projected JSON while retaining the original archive and all displayed geometry. Four inherited UI assertions were updated to the requested readiness/navigation behaviour; no production guard was removed to satisfy them.
- Local release-request tests: 9 passed; existing-tester discovery tests: 3 passed. Workspace has no Android SDK/Gradle/JDK compiler; full Android validation ran in Actions. Signed APK artifact [12196930985](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39273170015/artifacts/12196930985), verification reports [12196780946](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39273170015/artifacts/12196780946).

### Remaining acceptance and limits

Keep **#201 open for device acceptance**, along with #188/#194. No connected Pixel/Samsung was available: actual phone taps and before/after first-load/revisit timings are not claimed from JVM tests. Check filter readiness/apply/reset, both pending celebrations, M2 travelled/remaining strokes and section journeys, then Map → Progress → other tabs → return with camera/scroll retained. Verify upgrade over the existing installation without clearing data. Android may reclaim stopped activities/processes under memory pressure; recreation can rebuild derived results using existing caches/snapshots. B/local full-reference limitations and router availability are unchanged. Exceptionally slow/continually changing archives now offer retry after the bounded road deadline.

The coding and one-APK delivery slice is complete; the next step is testing this build, not another backlog/NCN integration session. The Work app's displayed execution/usage timer is not exposed to the agent; only wall-clock elapsed time can be reported.

## Previous delivery — Beta 1 / Beta 2 split

The user has authorised **Beta 1: core reliability, existing collection fixes and interactive Progress**, followed by **Beta 2: NCN collection and subsequent fixes**. This supersedes the earlier no-build hold, but packaging/distribution must wait for green integrated checks.

Active integration: `integration/beta-1-core`, based on PR #197 at `3dcf99b55925800c4e1417b90f932d3d56c8da11`. See [exact PR disposition, integration work, validation and device checklist](beta-1-integration.md). #163 is already reconciled in #197; no duplicate merge. #164/#168/#173, #169, #166/#171 and #167 are included. #172 and all NCN research/data PRs #165/#175–#187 are deferred. Original branches remain intact.

The combined slice adds independent post-arrival dwell observations for venue collections, replayable interrupted save/leg handoffs, armed checkpoint recovery, null-intent tracking resumption and atomic journey replacement. Existing saved rematching and raw evidence are preserved. Integrated Android unit tests and lint passed at `5c8deea0` ([run 39259997616](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39259997616)); PostgreSQL backend checks passed. Backend-only PR #199 is merged as `b1926f00`, verified live at API 0.12.0 with database ready. Original saved Warwick replay recovers Northbound only. Version 0.25.202 (248) and explicit existing-tester sharing are prepared. **Released Beta 1 0.25.202 (248):** exact source `5c8deea0a6237f484a865994e7148d51af4f1627`, immutable request `7738692e`, app integration merge `6a215c90`. [Packaging and Firebase run 39261229063](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39261229063) passed 607 tests (zero failures/errors/skips), lint and signature verification, then uploaded release `3ln17lg2bp3v8` and shared it with existing testers. [Install Beta 1](https://appdistribution.firebase.google.com/testerapps/1:138967660563:android:d6ff13ee7ab7b7353ba7e6/releases/3ln17lg2bp3v8). No NCN collection or reference-data publication. A private live driving replay was blocked by automatic approval review; offline production preparation and deterministic complete-geometry regressions passed, with device acceptance still outstanding. No NCN data publication is authorised as part of Beta 1.

Next smallest core follow-up: skip linked-archive reads once the bounded arrival-observation window closes, then gather overnight/screen-off Pixel/Samsung evidence. Keep #188/#194 open. For Beta 2, reconcile #172 with the now-integrated road Progress, then implement national open-way/overlap/alternative coverage from #175–#187.

Prior #197 verification: full Android tests/lint passed at `ad3db3e8` in [run 39256415474](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39256415474). That branch-level result does not validate the new combined integration. Keep #188/#194 open for device acceptance.

## Start every ChatGPT / Work development session here

1. Read this document and [release integration issue #194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194) before modifying code or proposing a build.
2. Review current GitHub branch/PR state, CI, merge bases and changes since this document was last updated. Do not assume open PRs are merged, published or deployed.
3. For NCN work, read [epic #79](https://github.com/adamdbird-gfc/uk-road-tracker/issues/79) and child issues [#190](https://github.com/adamdbird-gfc/uk-road-tracker/issues/190), [#191](https://github.com/adamdbird-gfc/uk-road-tracker/issues/191), [#192](https://github.com/adamdbird-gfc/uk-road-tracker/issues/192), [#193](https://github.com/adamdbird-gfc/uk-road-tracker/issues/193) and [#194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194).
4. Check existing components and stacked PRs before implementing. Reuse established Progress, route-detail, journey-list, collection and navigation behaviours. Record decisions, findings, blockers and tests in the appropriate GitHub issue.
5. Classify outstanding PRs **include / defer / superseded** after reviewing their dependencies and overlaps. Do not blindly merge stacked or divergent branches.
6. Complete coherent feature slices and verify integrated tests before preparing **one coordinated beta APK / Firebase release**. No build or deployment merely to demonstrate a small UI decision. Obtain the user's approval for release when appropriate.

## Release scope and dependencies to assess

- **Service-station visit recognition:** [PR #169](https://github.com/adamdbird-gfc/uk-road-tracker/pull/169), mapped service-site footprints, genuine dwell versus passing traffic, saved Warwick Northbound visit recovery, older hosted catalogue compatibility. Real-device verification remains.
- **Place-aware capture:** [#163](https://github.com/adamdbird-gfc/uk-road-tracker/pull/163), [#164](https://github.com/adamdbird-gfc/uk-road-tracker/pull/164), [#170](https://github.com/adamdbird-gfc/uk-road-tracker/pull/170), [#182](https://github.com/adamdbird-gfc/uk-road-tracker/pull/182): stops at schools/businesses/residences versus continuing running/walking, stationary drift and validated arrival duration.
- **Driving geometry and journey quality:** [#173](https://github.com/adamdbird-gfc/uk-road-tracker/pull/173), [#174](https://github.com/adamdbird-gfc/uk-road-tracker/pull/174): correct routing, continuous map strokes and accurate travel mode.
- **Map/history/provenance:** [#166](https://github.com/adamdbird-gfc/uk-road-tracker/pull/166), [#167](https://github.com/adamdbird-gfc/uk-road-tracker/pull/167), [#168](https://github.com/adamdbird-gfc/uk-road-tracker/pull/168).
- **Shared interactive Progress:** [PR #171](https://github.com/adamdbird-gfc/uk-road-tracker/pull/171) contains focused motorway/A-road maps and contributing journey lists. It is open/unmerged as of this review.
- **NCN Android trial:** [PR #172](https://github.com/adamdbird-gfc/uk-road-tracker/pull/172) is an opt-in eight-route trial stacked on #171, **not** the final collection. It already reuses shared route-detail plumbing.
- **NCN national data:** draft stacked [PRs #175–#187](https://github.com/adamdbird-gfc/uk-road-tracker/pulls) culminate in 219 supported route identities, 34 evidence holds and shared physical ways. A production adapter must handle overlaps and alternatives rather than assuming the trial compiler supports them.
- Triage old open PRs #1, #6–#8 as potentially stale; do not auto-merge.

## Agreed NCN product direction (details in #192)

- Only verified **open** route sections count; preserve route identities; hold unsupported or planned sections.
- Walking/running and cycling contribute; driving does not. Combined completion is a **unique physical union**, not the sum of mode totals.
- Collection map: purple completed sections; muted grey dashed remaining eligible sections; temporarily subdue ordinary Roadprints road overlays, restoring prior state on exit.
- Route taps: verified identity, eligible distance, verified endpoints, mode-filtered completion and road-style contributing journeys. For shared geometry, offer every verified numbered route identity.
- Progress: National, Regional and Named route groups, collapsible like existing lists; numerical sort for numbered routes; search, Jump-to entry, route-focused maps and interactive charts.
- **NCN charts use signpost red**, distinct from **purple completed map strokes**. Motorway and A-road charts retain their existing blue/green conventions.
- Retrospective history assessment should be resumable, correction/deletion aware, and not flood users with review prompts. Purchases/paywall remain deferred for beta.

## Release readiness gates

- Review merge dependency graph and overlapping files; explicitly choose include/defer/superseded for each PR.
- For Beta 2 only: integrate and validate the full NCN source-way/provenance adapter, open-only exclusions, shared physical coverage, route alternatives and bounded memory/storage.
- Test service-station genuine visits vs drive-through; place-aware trip end/start; walking duration; continuous motorway geometry; saved journey edits/deletions; road maps and contributing journeys. NCN mode filtering/charts/route maps are Beta 2 gates.
- Check CI, licences/source provenance, versioned catalogue delivery, migrations, rollback and Firebase staging.
- Distribute Beta 1 only after green integrated automated checks, with explicit outstanding device scenarios. Real-device acceptance is a separate gate; do not claim it from automated tests.

## Session handover discipline

At the end of substantial work, update issue #194 with: branch/commit or PR, what changed, test evidence, what remains, blockers, dependencies and the next recommended slice. Update the relevant feature issue as well. Prefer links and verifiable state over claims based only on conversation history. Ordinary Chat and Work can both read GitHub, but new sessions should be explicitly directed to this file.

## Historical reliability checkpoint — superseded by the Beta 1 integration above

PR [#196](https://github.com/adamdbird-gfc/uk-road-tracker/pull/196), branch `fix/capture-mode-transition-evidence`, stacked on #182 (`13084738`). Code checkpoint: `726339649c824d1a69812cbf2f17ca54ffe282a7`. See [the reliability audit](tracking-reliability-audit-2026-10-19.md) for reviewed heads, include/defer decisions, file overlaps and outstanding work.

The new slice fixes early activity callbacks splitting at their timestamp instead of the start of GPS-supported mode movement. Stops/gaps cannot pool stale movement votes; the existing handoff preserves every measured edge with one shared endpoint. It does not resolve all of #188 or repair existing saved geometry.

Validation: 34 local JVM tests passed, including 11 new mode-boundary cases. Full Android unit tests and lint, including the new service-handoff regression, passed for code checkpoint `72633964` in [Actions run 39249993064](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39249993064). No version bump, APK build, Firebase deployment, merge or live-data publication.

Next recommended slice: reconcile #163 place-visit behaviour onto #170 → #174 → #182 plus this mode-boundary fix, with overnight home drift/morning departure and genuine venue-exercise service tests. Integrate #164/#173 backend changes separately and verify full-route geometry; retain #168 source evidence when NCN joins. #169 visits, #167 late saves and the map/Progress/NCN stacks remain release dependencies, not implemented again here. Keep #188/#194 open pending integrated tests and real-device acceptance.

Saved-journey requirement confirmed: completed journeys must remain explicitly rematchable with current preparation/matching, excluding supported drift from derived walking maps/distance while retaining raw evidence. See [saved-journey remapping](saved-journey-remapping.md) and the completed-old-match/failure-retention regressions in WalkingMatchingIntegrationTest. Automatic bulk migration and historical journey re-segmentation are not implemented; original route/user-edit and leg boundaries must be respected.

Saved-remapping follow-up validation: all Android unit tests and lint passed at `e3a752fd` in [Actions run 39252410302](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/39252410302), including three new completed-walk/drift, failure-retention and completed-drive regressions. This verifies existing remapping behaviour; no additional runtime change, APK build or deployment.
