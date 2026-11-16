# NCN Stage 1 development brief

Date: 29 October 2026. Status: **planned; no implementation or release authorised by this documentation change**.
Repository: `adamdbird-gfc/uk-road-tracker`. Planning source: PR #238, originally `6bce5a8515f9495e92b525904f6f4fcf9e03fa17`.
Main checked at `d9adc5f65065adc82b16996ec8bcc945b81dd311`; start implementation from latest validated main at that time.
Android baseline: **0.25.220 (266)**. Installed phone/cache provenance must be recorded when testing; it is not inferred from source.

## 1. Scope and objectives

Complete the existing supported NCN collection: attainable evidence-based route/network progress, the existing combined walking/running/cycling milestone families, and essential Android use. Requirements are [NCN-DEC-001–012 and the 46-item register](ncn-completion-register.md); original [audit receipt](data-trials/ncn-completion-audit-2026-10-29.json) remains immutable historical evidence. Do not repeat its research recovery.

Protect existing catalogue installation, retrospective assessment, numeric groups/search/modes, purple/orange maps, focused route/journey navigation, unique coverage, first discovery, 25/50/75/100 milestones and shared celebrations. These are implemented; a missing device sign-off is not proof they need rebuilding.

Exclude Millennium Mileposts/personality discoveries (Stage 2), additional awards, Named-route expansion, the 34 held identities, NCN228 measurement, alternatives outside eligible targets, aggregate-chart redesign and comprehensive endpoint research. Preserve those findings and dispositions. Stage 2 source research may run independently. No owner photographic survey, repeat field trip or screen-by-screen screenshot exercise.

## 2. Confirmed remaining work and implementation sequence

### S1.1 — Make the published eligible scope attainable (first package)

Register: CAT-03, relevant CAT-04, JRN-06, ACH-02/03; preserve CAT-01/06 and JRN-01/02.

**Known gap:** all 218 measurable routes have over 1 m of unbound open target. The audit's 1,334,036.721 m sum is route-local, not a network deficit. RCN:90 combined binding capacity is about 9.433%; NCN1 about 92.658%. The present bindings cannot demonstrate all their denominator geometry, irrespective of how much a user travels. The capacity receipt does not yet classify each gap's cause.

1. Add a failing interval-capacity regression using the pinned catalogue and retained completion plans, initially a small representative route/gap fixture. Identify the exact unsupported target intervals through existing bindings, not a new national source search. Use RCN:90 and a long/disconnected route to avoid solving only an easy shape.
2. Repair reusable source-to-target correspondence defects with supported exact source spans. Check segmentation/alignment, ambiguity and deferred-alternative blocking only where they intersect eligible target. Retain source nodes/hashes, access modes, identities and valid target geometry. A proximity match, user choice or guessed connector cannot substitute for evidence.
3. Emit a reviewable before/after interval disposition and binding-capacity result for every released measurable route, plus canonical unique-network coverage. Retained research replay verifies provenance; it is not another research recovery task. Do not edit the original audit receipt to look current.
4. Prove all eligible denominator intervals can receive credit through the same supported journey assessment and Android ledger used in production. A full-binding synthetic capacity witness tests mechanics; it is not evidence that any real user travelled those intervals. Pair it with positive/negative saved-evidence fixtures.
5. Prepare a reproducible candidate catalogue only during the subsequently authorised development task. Publication stays separate. If the existing consumer can read corrected bindings, this package may be data/tooling-only; establish that from the actual fix, not assumption.

**Resolution rule:** repair bindings first. Correct a denominator interval only when source evidence demonstrates it was erroneous or ineligible, with explicit disposition and migration tests. Do not redefine eligible geometry as the already-bound subset. Valid unresolved geometry remains retained and blocks Stage 1 attainability; report the exact interval and missing evidence. If resolution would require withholding a valid published route or narrowing valid scope, return that concrete product choice to the owner. No blanket exclusion is authorised.

**Package gate:** 100% combined eligible capacity for each released measurable route and the physical-network union, within existing numerical-noise bounds; exact target retention except evidenced eligibility corrections; no false journey credit. This means the Roadprints eligible scope, not every real-world NCN route. Preserve the separate `whole_published_route_ready=false` contract unless independently satisfied; do not toggle it to claim this fix. The 34 holds and unmeasurable NCN228 do not need admission to pass.

### S1.2 — Verify milestones and reference transitions

Register: JRN-03/04, ACH-01–04, QA-01/04/05. Can prepare tests alongside S1.1; final results depend on its corrected candidate.

Add the missing focused end-to-end 50%/75% cases and changed-reference replacement/rollback matrix. Preserve one combined route family and one network family with 25/50/75/100; full completion is the existing 100% level, not another badge. Test exact threshold boundaries, combined foot/cycle union, repeat imports, disconnected targets, overlapping identities, correction/deletion with other evidence surviving, stable-key updates, removed/held keys, cache reconstruction and recognition/acknowledgement retention. Fix demonstrated failures only.

Verify historical activation/import and incremental resume against a representative large archive. Keep original journey evidence, no blanket rematching, no historical review queue, no false Ready state after failure and no stale assessment publishing over a replacement reference. Measure cold assessment, warm return and one changed journey against the same archive/version baseline; investigate regressions, not unrelated optimisation. No invented phone timings or new performance budget.

### S1.3 — Close essential Android gaps, then targeted acceptance

Register: JRN-05, essential UX-05, QA-03; protect UX-01–04/07, ACH-04 and QA-02.

Implement the already agreed [bounded section review](ncn-section-review-proposal.md) for a new native recording made while NCN is active, and only when evidence supports credible competing paths. Reuse existing journey correction/review mechanisms where suitable. A supported alternative must carry source/access/interval evidence; native review payload production is an unresolved integration detail, not permission to fabricate candidates. Offer supported NCN/road choices and Not sure, show original fixes, change only the uncertain window and persist the decision with evidence/reference versions. A road can itself be NCN. Historical activation, imports and old native journeys must never acquire a review queue. Dismiss/Not sure stays uncredited without repeated prompts.

Preserve current overlapping-route selection and focused maps. Show honest unknown endpoint/name metadata; no invented termini or map connectors. Full endpoint enrichment and an in-panel journey-switch redesign are deferred. Close essential accessibility defects in the affected NCN flow: labelled/reachable controls, usable large text, and complete/remaining meaning conveyed beyond hue alone. Preserve numeric ordering, default Combined mode, colours, search/camera/back state and contributing journeys. Avoid a general UI rewrite.

## 3. Implementation entrypoints and existing tests

All paths below exist on checked main. Read only the changed area and its dependencies.

| Area | Implementation files | Existing regression anchors |
| --- | --- | --- |
| Bindings and target eligibility | `scripts/ncn_trial/open_completion.py`, `alternatives.py`, `extent_reference.py`; `scripts/build_ncn_national.py` | `scripts/ncn_trial/test_open_completion.py` (unsupported middle, near-parallel ambiguity, endpoint non-extrapolation, modes/edits), `test_alternatives.py`, `test_extent_reference.py`, `test_completion_checkpoint.py` |
| Physical union and publication contract | `scripts/ncn_trial/network_geometry.py`, `scripts/repair_ncn_network_mapping.py`, `scripts/ncn_reference.py`; existing manifest at `android-prototype/app/src/main/assets/ncn-national/manifest.json` | `scripts/test_ncn_national.py`, `scripts/test_ncn_reference.py`, `scripts/ncn_trial/test_network_geometry.py`, `test_network_reconciliation.py`; `scripts/verify_ncn_checkpoint_provenance.py` for affected candidate provenance |
| Android assessment, union, cache | `NcnCatalogue.java`, `NcnMatcher.java`, `NcnLedger.java`, `NcnProgressStore.java`, `NcnCatalogueFiles.java` | `NcnAndroidTest`, `NcnNationalTest`, `NcnCatalogueFilesTest`, `NcnResponsivenessTest` |
| Milestones and persistence | `NcnAchievements.java`, `AchievementStore.java`, `AchievementCelebrations.java` | `NcnAchievementThresholdTest`, `NcnNationalTest.achievementsUseEligiblePhysicalUnionAndDurableCelebrations`, `AchievementCelebrationsTest` |
| Bounded review | `scripts/ncn_trial/activation.py`, `review.py`; integrate with existing Android journey evidence/correction path, `NcnMatcher` and `NcnProgressStore` | `scripts/ncn_trial/test_activation.py`, `test_review.py`, `test_alternative_activation.py`; extend Android integration tests for actual chosen path |
| UI and map | `NcnProgressUi.java`, `NcnMapOverlay.java`, `CollectionsActivity.java`, `RoadDetailActivity.java`, `RoutePreviewView.java`, `AchievementsActivity.java` | `NcnUiTest`, `NcnPresentationTest`, `NcnGeometryTest`, `NcnResponsivenessTest`; existing navigation/history coverage |

Java names are under `android-prototype/app/src/main/java/com/roadprints/capture/`; Java test names under `android-prototype/app/src/test/java/com/roadprints/capture/`. Bare Python module names in the first column share `scripts/ncn_trial/`. Test names here describe current anchors, not claims that new cases already pass.

## 4. Regression risks and automated validation

- **False completion:** preserve valid target geometry, exact spans, mode permissions and strict threshold arithmetic. Negative fixtures: unsupported gap, crossing, parallel road, extrapolated endpoint, relabelled drive and unverified alternative. No tolerance increase to clear a gate.
- **Union/migration errors:** route-local and canonical-network calculations remain distinct. Test every changed target-to-network projection, shared ways, repeated modes and catalogue rollback. No nearby-route identity transfer or lost user corrections.
- **Background work:** preserve resumable batches, cancellation/epochs, cached unchanged work and bounded retries. Record source reads/timings for the same archive before/after; keep main-map/navigation responsive.
- **Review side effects:** original fixes retained; decision survives reprocessing; reference changes reconcile it; no import prompts, infinite retry or unsupported-choice credit.

Use the existing `.github/workflows/ncn-trial-checks.yml` commands for changed Python/binding work: `python -m unittest discover -s scripts/ncn_trial -t . -p 'test_*.py' -v`, cycling evidence tests, and `python scripts/test_ncn_reference.py -v`. Run `python scripts/test_ncn_national.py -v` with `NCN_TEST_DATABASE` pointing to the exact candidate reference; retain the shipped-reference baseline comparison. Do not silently validate only the old pinned DB. Existing workflows restore checksum-pinned assets; no production publication is needed to test a candidate.

For changed Java/consumer/UI behaviour, run the affected NCN, achievement and navigation suites, then the existing required Android test/lint/reference checks before a release candidate. Add the focused missing cases above rather than mirror implementation. Preserve existing Dover, Charing Cross walking, cycling and core map/navigation regressions when touched dependencies warrant them; backend matcher edits are not expected. Record commands, versions, counts and failures/skips honestly. The prior 333 research/reference and 810 Android checks are historical baseline evidence, not fresh results for these packages.

## 5. Genuinely necessary device checks

Developer captures short pass/fail notes and diagnostics for changed behaviour; the owner need not inspect every screen or send a screenshot pack. No new field capture is needed to reproduce supported evidence fixtures.

1. On Pixel 10 / Android 17, run one coherent changed flow with recorded APK/source and catalogue hashes: activate/assess saved history, navigate main map → NCN list → affected focused route, switch mode and return. Check responsiveness, totals, disconnected geometry and retained state. Use one eligible native-review fixture plus Not sure/reopen, rather than a new real-world journey, where test tooling supports injection.
2. On the changed achievement/review flow, check large text and TalkBack, one bulk celebration/dismiss/reopen and no repeat awards/prompts. A controlled test profile may exercise thresholds; never alter the owner's real journey evidence to manufacture awards.
3. If catalogue delivery/installer changes, check one offline/interrupted-update/resume path retains the old generation and reaches a correct ready state. Samsung / Android 16 is a targeted second check only for affected background, lifecycle or platform behaviour, or a reproduced device-specific regression. Otherwise record it as untested, without claiming universal acceptance.

Only a failing or uncertain changed behaviour calls for a screenshot or additional check. Missing device access may leave the relevant acceptance pending; it does not justify a new broad audit.

## 6. Release boundaries and definition of done

This task changes documentation only. Future implementation uses short-lived branches from latest validated main, PRs targeting main, with owner approval before merge. S1.1–S1.3 may be separate focused PRs; do not stack historic branches. Keep candidate data generation/review separate from Firebase publication. An existing-schema binding correction may need no APK; S1.3 runtime changes will need one purposeful private build only when separately authorised. Backend deployment is not expected. No automatic tester distribution. Follow [governance](DEVELOPMENT_GOVERNANCE.md), including preventing unintended deployment at source integration.

Stage 1 is done when:

- Every released measurable route's eligible Roadprints denominator and the canonical network are fully creditable by supported journey evidence; unresolved valid gaps block this claim. No need to add held/Named routes or assert full real-world coverage.
- Existing first discovery and combined 25/50/75/100 families pass exact thresholds, unique-union and reference-transition tests, including attainable 100%; original data, corrections and history survive.
- Essential new-native review and accessible NCN use pass their positive/negative integration checks; existing maps/navigation/loading/retry work remains protected and no history/import queue appears.
- Changed-area automation and targeted device acceptance have recorded results, limitations and same-archive performance comparisons. No fabricated validation or unexplained regressions.
- Source integration, candidate/published catalogue, APK provenance/distribution, backend state, rollback and outstanding acceptance are reported separately. No feature is called released merely because its PR exists. Stage 2 remains deferred independently.

**Owner input:** none needed to begin S1.1. Engineering must resolve precise mapping, consumer compatibility and native-review payload details. Escalate only a concrete evidenced need to alter valid published scope; keep Stage 2 policy questions in the register. The next task is focused development, not another recovery or general acceptance audit.
