# NCN Completion Register

Audit date: 29 October 2026. Status: **12 owner decisions confirmed; Stage 1 development planned, not implemented**.
Repository: `adamdbird-gfc/uk-road-tracker`.
Audited source: `d9adc5f65065adc82b16996ec8bcc945b81dd311` (latest main at audit).
Android baseline: **0.25.220 (266)**; packaged source `67e74e7d9e9873552ae98b23397cd9e265fc3517`.

Decision consolidation: 29 October 2026, continuing PR #238 at `6bce5a8515f9495e92b525904f6f4fcf9e03fa17`. Main rechecked at `d9adc5f65065adc82b16996ec8bcc945b81dd311`; the original audit receipt is unchanged.

This is the authoritative NCN completion register. The owner decisions below supersede earlier open product questions. It records evidence and uncertainties, not permission to implement, publish, build, deploy or merge. Original issues remain historical evidence; their stale “not implemented”, grey-dashed/red styling and stacked-release instructions must not override later code or decisions. No runtime or reference data changes form part of this audit.

## Confirmed owner decisions — 29 October 2026

These are product requirements, not proposed alternatives or implementation claims. All twelve were supplied in the owner's decision-consolidation brief. Technical details explicitly left open below do not reopen them.

| Decision | Confirmed requirement | Register impact / delivery |
| --- | --- | --- |
| NCN-DEC-001 — Route completion | 100% means all eligible route geometry represented and assessable in Roadprints, not the full real-world NCN route. Published assessable routes must be completable. Resolve denominator geometry that cannot receive qualifying evidence safely: no artificial shortening of valid geometry, invented connectors, tolerance inflation or evidence-free awards. Preserve 25/50/75/100. | CAT-03, JRN-02/04/06, ACH-02/03; S1.1–S1.2. See binding resolution below. |
| NCN-DEC-002 — Milepost proximity | Walking, running or cycling passing within 100 metres of a verified Millennium Milepost qualifies for its assigned personality, whether or not noticed. Historical journeys qualify. Stationary proximity, driving and unsupported transport do not. | MP-04, TIME-04/05; S2. Radius settled; evidence-quality mechanics still technical work. |
| NCN-DEC-003 — Milepost verification | Build a reliable national catalogue using existing official and community-maintained sources; automate reconciliation and retain provenance. Review conflicting records individually. Individual photographs of every post are unnecessary. Unreliable/unresolved coordinates cannot unlock. | MP-01–05; S2. Kent is a useful fixture set, not a mandatory national photographic gate. |
| NCN-DEC-004 — Fixed assignments | Each eligible post has one deterministic assignment to one of six personalities. All six should have opportunities in each of the twelve UK geographies, subject to verified data availability. Assignments remain stable across updates. | MP-03/04, TIME-03; S2. Persist assignments by stable physical-post identity; do not reshuffle on catalogue ordering changes. |
| NCN-DEC-005 — Historical awards | All qualifying historical journeys qualify; one import/assessment may award several or all six personalities and the parent. Reuse existing recognition/celebration without artificial delay. Preserve journey date and recognition date separately where available. | TIME-05/06; S2; preserve existing Stage 1 retrospective mechanism. |
| NCN-DEC-006 — Illustrations | Simple, recognisable 2px-stroke head-and-shoulders illustrations consistent with app icons; no elaborate portraits or photographs. | TIME-01/02, MP-05; S2 content/assets. |
| NCN-DEC-007 — Biographies | Concise factual summaries, approximately 40–70 words. Include original qualifying discovery location and journey date. Link verified Wikipedia biographies where available; never invent links. | TIME-01/02/05; S2. Preserve original discovery metadata. |
| NCN-DEC-008 — Repeats | Award each personality once. Later encounters with posts assigned to that person produce neither further awards nor a separate milepost collection. Preserve original qualifying discovery information. | TIME-03/05; S2. Deduplicate across imports and assessments. |
| NCN-DEC-009 — Mystery visibility | Initially show six “?” achievements. First discovery reveals that personality and changes the other five to anonymous silhouettes. Later discoveries reveal illustration, name and biography. | TIME-02; S2, including accessible spoiler-safe labels. |
| NCN-DEC-010 — No advance clues | Before the first discovery show no descriptions, hints, unlock conditions or milepost explanation. First unlock explains the concept. | TIME-02/04; S2; applies to details, accessibility labels and celebrations too. |
| NCN-DEC-011 — Completion reward | Hide In Honour of Time until first personality discovery; then show progress towards six. Award when all six distinct personalities are discovered. | TIME-02/06; S2. No requirement to visit 72 posts. |
| NCN-DEC-012 — Two stages | Stage 1 completes and safely validates existing NCN routes, progress, milestones and essential Android functionality. Stage 2 adds Millennium Mileposts and personality discoveries. Independent Stage 2 research must not delay Stage 1. | All 46 items assigned below. No milepost release gate in S1. |

## Reading the evidence

Requirement authority: **H** = identifiable historical product requirement; **N** = confirmed owner direction, now including NCN-DEC-001–012; **P** = proposal; **Q** = unresolved. Code proves implementation, not that its exact copy/design was approved.

Implementation classifications:
- **IV** — Implemented and validated: source and identified automated/data evidence; validation scope is stated.
- **ID** — Implemented but awaiting real-device acceptance.
- **PI** — Partially implemented.
- **NI** — Not implemented.
- **U** — Unclear / insufficient evidence.

IV never means real-device visual acceptance. Priorities are scoped to their delivery stage. S1.1–S1.3 refer to the [Stage 1 development brief](ncn-stage-1-development-brief.md); S2 is a separate future feature. Retained functionality needs regression protection, not wholesale redevelopment or a screen-by-screen owner audit.

### Evidence index

All relative source links refer to the audited main tree unless an immutable historical commit is stated.

| Ref | Identifiable evidence and scope |
| --- | --- |
| E01 | [Governance](DEVELOPMENT_GOVERNANCE.md), [integrated baseline](android-025220-integrated-baseline.md), [handover](development-handover.md). Main is authoritative; Android tree matches packaged 220; releases/publication/acceptance remain separate. |
| E02 | [NCN reconciliation](ncn-historical-reconciliation.md) and [per-PR audit](data-trials/ncn-historical-reconciliation-2026-10-29.json), PR [235](https://github.com/adamdbird-gfc/uk-road-tracker/pull/235) including final sign-off. Preserves thirteen historical research PRs and their 333-check gate. |
| E03 | [Checkpoint](ncn-completion-checkpoint.md), [checkpoint summary](data-trials/ncn-completion-checkpoint-2026-10-19.json), [open completion](ncn-open-completion.md), [numbered alternatives](ncn-numbered-alternative-review.md), [remaining 54 dispositions](ncn-remainder-54-review.md), [zero-association review](ncn-zero-association-determination.md), [NCN601](ncn601-open-sections.md). |
| E04 | [Current manifest](../android-prototype/app/src/main/assets/ncn-national/manifest.json); immutable database recovered from publication archive. Fresh read-only comparisons and complete per-route inventory: [audit receipt](data-trials/ncn-completion-audit-2026-10-29.json). |
| E05 | Issues [79](https://github.com/adamdbird-gfc/uk-road-tracker/issues/79), [190](https://github.com/adamdbird-gfc/uk-road-tracker/issues/190), [191](https://github.com/adamdbird-gfc/uk-road-tracker/issues/191), [192](https://github.com/adamdbird-gfc/uk-road-tracker/issues/192), [193](https://github.com/adamdbird-gfc/uk-road-tracker/issues/193) and its [implementation receipt](https://github.com/adamdbird-gfc/uk-road-tracker/issues/193#issuecomment-6287396152), [194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194). Bodies contain successive decisions; later corrections take precedence. |
| E06 | Main Android classes: [catalogue](../android-prototype/app/src/main/java/com/roadprints/capture/NcnCatalogue.java), [installer](../android-prototype/app/src/main/java/com/roadprints/capture/NcnCatalogueFiles.java), [assessor](../android-prototype/app/src/main/java/com/roadprints/capture/NcnMatcher.java), [ledger](../android-prototype/app/src/main/java/com/roadprints/capture/NcnLedger.java), [progress store](../android-prototype/app/src/main/java/com/roadprints/capture/NcnProgressStore.java). |
| E07 | Main Android UI: [NCN Progress](../android-prototype/app/src/main/java/com/roadprints/capture/NcnProgressUi.java), [overlay](../android-prototype/app/src/main/java/com/roadprints/capture/NcnMapOverlay.java), [collection entry](../android-prototype/app/src/main/java/com/roadprints/capture/CollectionsActivity.java), [focused detail](../android-prototype/app/src/main/java/com/roadprints/capture/RoadDetailActivity.java), [map rendering](../android-prototype/app/src/main/java/com/roadprints/capture/RoutePreviewView.java), [collection access](../android-prototype/app/src/main/java/com/roadprints/capture/CollectionAccessStore.java). |
| E08 | [NCN achievements](../android-prototype/app/src/main/java/com/roadprints/capture/NcnAchievements.java), [achievement model](../android-prototype/app/src/main/java/com/roadprints/capture/AchievementStore.java), [achievement screen](../android-prototype/app/src/main/java/com/roadprints/capture/AchievementsActivity.java), [celebrations](../android-prototype/app/src/main/java/com/roadprints/capture/AchievementCelebrations.java). |
| E09 | Android tests in `android-prototype/app/src/test/java/com/roadprints/capture/`: `NcnAndroidTest`, `NcnNationalTest`, `NcnAchievementThresholdTest`, `NcnCatalogueFilesTest`, `NcnGeometryTest`, `NcnUiTest`, `NcnPresentationTest`, `NcnResponsivenessTest`; plus `AchievementCelebrationsTest` and history/navigation regressions. These are JVM/Robolectric tests, not Pixel/Samsung field evidence. |
| E10 | Historical [performance/achievement handover at 6c0deace](https://github.com/adamdbird-gfc/uk-road-tracker/blob/6c0deacedd0e560a73229ab6b289fdff2fccf441/docs/ncn-performance-and-achievements.md); [UI acceptance at 9713b001](https://github.com/adamdbird-gfc/uk-road-tracker/blob/9713b0014eace079a1922b14040ce564e4f13c0d/docs/ncn-ui-private-acceptance.md); [national integration at 98ec39a8](https://github.com/adamdbird-gfc/uk-road-tracker/blob/98ec39a88d621f863f2035657bb4008d59453f30/docs/ncn-national-integration.md). These files are absent from main but recovered from retained branches. Their functionality is inspected on main; old release instructions are superseded. |
| E11 | [Named-route research](ncn-named-route-reconciliation.md), [milepost source assessment](ncn-milepost-source-assessment.md), [saved-path association](ncn-saved-path-association.md), [activation/review agreement](ncn-section-review-proposal.md), [regional trial](ncn-confirmation-regional-trial.md). Research implementation is not Android feature implementation. |
| E12 | Recovered source archive `roadprints-ncn-source-feasibility-2026-10-15.zip`, Library `libfile_4745b6f373948191ba4de3eb3704dcce`: original OSM response, candidate GeoJSON, Overpass query, milepost audit, official ArcGIS item/schema/terms and source feasibility report. |
| E13 | Recovered assessment archive `roadprints-ncn-route-catalogue-assessment-2026-10-16.zip`, Library `libfile_3ea291eb2b3481918b88034ccce30588`, version 15: named-source research and `geograph-local-milepost-examples.json`. Each example explicitly has `identity_confirmed=false`, `automatic_unlock_ready=false`. |
| E14 | Recovered checkpoint `roadprints-ncn-completion-checkpoint-2026-10-19.zip`, Library `libfile_99ba17ccc32c81919dc203e36ac496f5`; publication `roadprints-ncn-firebase-publication-2026-10-19.zip`, Library `libfile_8ab97d8102a88191905627b3c064c7cd`. Downloaded original archives; no new national data extraction or publication. |
| E15 | Connected conversation retrieval: user messages 20 October 06:39:56 UTC (solid orange remaining), 07:20:49 (purple rounded badges/shared controls), 08:11:04 (core achievements and evidence gates); 15 October 14:18:14 (separate mode provenance). Retrieval is a dated excerpt, not a complete transcript/permalink. User's current brief independently confirms six personalities and supersedes poetry. |
| E16 | `roadprints-achievements-specification.md`, Library `libfile_45eb6c82f64481918c4a214f919e85e0`, read in full: 6 October free/default catalogue, explicitly separates collection achievements. Its general distance/landmark badges are not extra agreed NCN awards. |

### Public source references checked on 29 October

- **S1** [Trust: Millennium Mileposts](https://www.walkwheelcycletrust.org.uk/national-cycle-network/millennium-mileposts/): four designs/designers; over 1,000 installed; links official interactive map.
- **S2** [Trust: Bristol–Bath anniversary](https://www.walkwheelcycletrust.org.uk/our-blog/news/celebrating-the-bristol-and-bath-railway-path-s-40th-anniversary/): John Grimshaw's founding/path contribution.
- **S3** [Trust: Northern Ireland milepost restoration](https://www.walkwheelcycletrust.org.uk/our-blog/news/milepost_young_people/): Rowe, Mills and Dudgeon contributions and geographically useful corroboration, not exact verified unlock coordinates.
- **S4** [Time Trail: reproduced 20 March 2001 press release](https://millenniumtimetrail.wordpress.com/the-press-release-from-march-2001/): explicitly attributes the concept to Charlie Harrow. Historically specific reproduced primary text on an independent site; original publisher provenance still needs confirmation.
- **S5** [Time Trail research](https://millenniumtimetrail.wordpress.com/about-the-millennium-time-trail/) and [code discs](https://millenniumtimetrail.wordpress.com/the-code-disks/): secondary research on designs/themes; no rights grant inferred.
- **S6** [OSM historic inventory](https://wiki.openstreetmap.org/wiki/National_Cycle_Network_Millennium_Mileposts) and [OSM terms](https://www.openstreetmap.org/copyright): candidate identities/locations; no current inspection implied.
- **S7** [Geograph article](https://www.geograph.org.uk/article/National-Cycle-Network/15), photos [5455848](https://www.geograph.org.uk/photo/5455848), [3645557](https://www.geograph.org.uk/photo/3645557), [2863732](https://www.geograph.org.uk/photo/2863732); [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0/). Photo 3645557 rechecked; it separates subject/camera positions and dates the observation to 2013.
- **S8** [Official milepost view item](https://www.arcgis.com/home/item.html?id=364c735b130743a9860d3032c76d510c), [metadata endpoint](https://www.arcgis.com/sharing/rest/content/items/364c735b130743a9860d3032c76d510c?f=pjson). Fresh metadata retrieval failed; preserved 15 October terms explicitly require express Sustrans permission. No permission grant recovered. Official route-layer OGL terms do not license this separate audit.
- **S9** [Official route item](https://www.arcgis.com/home/item.html?id=5defd254e78745bfb12d0456abc1bcf1), [OGL 3.0](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/). Captured route provenance preserves OS and Northern Ireland OSM attribution.

## Recovered catalogue baseline and its limits

The fresh audit uses the exact recovered publication database, opened read-only, and the original checkpoint inputs. Live `collections/published/ncn/manifest-v2.json` was read without downloading its large payload and equals the Android pin. See E04's machine-readable receipt for exact hashes, all active identities and each route's target/binding/deferred status.

| Measure | Verified baseline | Meaning |
| --- | ---: | --- |
| Pinned consumed inputs | 1,147 | Files in the checksum-pinned checkpoint, not route count |
| Route receipts / active identities | 219 | 216 National, 3 Regional, 0 Named |
| Measurable captured-open-main targets | 218 | NCN228 is separately preserved, unmeasurable |
| Distinct physical source ways | 62,399 | Not necessarily wholly eligible along every edge |
| Route-way rows | 62,487 | Shared identity projections create repeated physical ways |
| Shared physical ways | 88 | Network union prevents route-overlap inflation |
| Held identities | 34 | Outside supported routes; not declarations of closure |
| Deferred alternative occurrences | 176 across 49 routes | Route-way occurrences, not 176 independent routes/posts |
| Unique physical-network denominator | 17,435,132.494 m | Current supported verified-open reference, not all historical NCN |
| Catalogue revision | 1 | Database SHA below; no publication in this exercise |
| Whole-published-route ready | false | Explicit contract; must remain visible in release claims |

Database SHA-256: `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`.
Compressed SHA-256: `91c8e25f0d58ced6a1e2c91905fe4190b826dc8a469aafc4aab09b83dee1fd91`.
Checkpoint SHA-256: `e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b`.
Input-pin SHA-256: `a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641`.

**Important completion limitation:** all 218 measurable route receipts record more than 1 m of unbound open target. Summed route-local unbound length is **1,334,036.721 m**; this is not a deduplicated network deficit and is not a user's missing travel. For example NCN1's combined maximum bound target is about **92.658%** of its retained target. These are checkpoint binding-capacity diagnostics, not personal completion percentages or proof that remaining travel is impossible forever. Current bindings cannot demonstrate full-route attainment until gaps are reviewed. NCN-DEC-001 requires resolving this limitation for the eligible Roadprints scope. Preserve valid target geometry; do not mask it by shrinking the denominator, inventing connectors, inflating tolerance or rounding to 100%.

The total of route-local targets is 17,430,021.643 m; this is **not** the network denominator above. They use distinct route-local and canonical physical calculations. Their non-equality alone does not prove a bug; reconciliation/attainability must be examined through the piecewise mappings and union, not by substituting one total for the other.

Active Regional keys are **RCN:70, RCN:90, RCN:92**. Other active keys and precise geometries are fixed by the immutable database and the per-route audit inventory. `NCN:228` has display inventory, no measurable completion or awards. Historical numbered/Named trial assets remain in source for fixtures/research; the national SQLite loader is the production consumer.

### All held identity dispositions

| Hold group | Exact keys | Evidence required before a new admission |
| --- | --- | --- |
| Missing confirmed numbered-source membership | NCN:207, 274, 323, 410, 422, 524, 689, 776, 820 | Current UK identity and complete source-node geometry for publisher's open feature; failed queries/nearby numbers are not negative/positive proof |
| Alternative-only official extent | NCN:253 | Reconcile published extent/former regional identity and open alternative scope |
| NCN626/601 conflict | NCN:626 | Independent open NCN626 extent and identity; reviewed NCN601 uses exact source spans retaining NCN626 provenance, no blanket alias |
| Regional N/A category | RCN:12, 15, 16, 21, 30, 31, 32, 40, 41, 50, 51, 55, 61, 67, 80, 81, 83, 89 | Maintained authoritative regional scope/status/access; N/A is not “closed” or “Main” |
| Source/open-main mismatch | RCN:85 | Resolve position/identity mismatch; nearby same-number sources insufficient |
| Earlier regional holds | RCN:11, 17, 71, 82 | Maintained geometry, scope/status/access/branches; RCN82 null open status is not Open |

Mode permission is attached to evidenced source spans and target bindings, with walking/cycling access review; route membership alone is insufficient. Android requires a completed supported matcher result, signed source/hash or unambiguous exact legacy source for walking, or bicycle node evidence for cycling, then clips travelled intervals to eligible bindings. Relabelling a drive does not manufacture provenance. Unknown, bus, train, flight and driving do not qualify. Planned/closed/unknown/ferry target features and unsupported alternatives cannot earn inferred foot/cycle credit. Historical assessment uses the selected reference snapshot; it is not a reconstruction of designation on the travel date.

## Stage 1 attainability resolution — NCN-DEC-001

**Confirmed functional gap:** retained open-target intervals lack eligible source bindings. The receipt gives capacity, not a user result or root-cause classification. For example RCN:90 has only about 9.433% combined binding capacity; NCN1 about 92.658%. Even travelling every currently admitted source span cannot demonstrate the remainder through today's binding contract. A user with incomplete journeys is a different case: no credit is owed without travel evidence.

**Proposed safe resolution (S1.1):** repair the source-to-target correspondence for the existing published eligible scope, with an interval-level capacity gate. Start with reusable causes in pinned inputs: target/source segmentation or alignment, missing supported exact-source associations, ambiguous overlaps and deliberately deferred alternative neighbourhoods. These are candidate causes, not established diagnoses for every gap. Retain geometry, identity, access evidence and provenance. Admit a new binding only where an exact supported source span can evidence that target interval; use deterministic, reviewed mappings where existing automatic correspondence is ambiguous. Do not use nearest-road proximity to invent membership or endpoint travel.

Recompute both route-local capacity and canonical unique-network capacity using the same bindings the Android ledger consumes. For every released measurable route, the union of qualifying foot/cycle bindings must cover its entire eligible denominator; proof must include journey-assessment-to-achievement tests, not merely a changed metadata flag. This does not require each route to be fully traversable separately in both modes. No replacement of route totals by the network total. Keep the public full-real-world-route readiness flag false unless its separate contract is met; Roadprints-scope attainability and real-world completeness are different claims.

Only demonstrably erroneous or ineligible target intervals may be corrected out of the eligible denominator, with specific source evidence, a before/after interval disposition and reference migration checks. Unresolved **valid** geometry stays retained and blocks a claim of Stage 1 attainability; do not silently label it ineligible or redefine the denominator as whatever is already bound. If existing evidence cannot resolve it, return that concrete interval/route and the smallest further source requirement. Any proposal to withhold an otherwise valid published route or narrow its valid scope requires an explicit owner decision then; no such change is authorised here.

**Not independent Stage 1 blockers:** the 34 excluded identities, absent Named routes, NCN228's explicitly unmeasurable inventory, and deferred alternatives outside the eligible target do not prevent completion of the supported assessable scope. Preserve their dispositions. Of the 176 deferred occurrences, only those actually leaving eligible denominator intervals uncreditable belong in S1.1; the number alone does not make all alternatives essential. Endpoint research, extra awards and aggregate chart interaction are separate enhancements. The 1,334,036.721 m sum is route-local and cannot be treated as the network deficit.

## Full recoverable achievement specification

### Current core awards

H: #193 confirms a single combined award family per route and one per network, plus first discovery. No separate on-foot/cycling milestones. RCN keys remain distinct; Named awards depend on future admitted identities. Exact titles below are **current code copy**, not independently recovered approval of every word.

| ID / exact current name | Description / unlock condition | Qualifying evidence and partial progress | Historical and persistence | Implementation / status / source |
| --- | --- | --- | --- | --- |
| `ncn-first` — **First NCN discovery** | “Discover your first verified-open NCN section on foot or by bicycle.” Any positive unique eligible physical-network coverage; no invented minimum distance | Validated walking/running/cycling ledger; binary progress | Activation/import/live assessment; recognition time is evaluation time, not an invented original trip date. Correction/deletion can relock; historical dates retained | E08; `NcnNationalTest.achievementsUseEligiblePhysicalUnionAndDurableCelebrations`. IV calculation, ID celebrations |
| `ncn-network` — **National Cycle Network completion** | “Complete 25%, 50%, 75% and 100% of National Cycle Network on foot or by bicycle.” Levels exactly 25/50/75/100 of canonical unique eligible physical network | Combined mode union, never sum of route totals. Shows percentage/distance/next threshold | Retrospective and live; recompute corrections/deletions/catalogue migration; preserve recognition/seen-level history | E08/E09; IV logic, ID device, PI data attainability |
| `ncn-route:<exact key>` — **<identity> completion** (e.g. NCN 344 completion) | “Complete 25%, 50%, 75% and 100% of <identity> on foot or by bicycle.” Four exact levels per measurable route; denominator retains verified disconnected open sections and unresolved open-target gaps | Combined unique intervals; mode breakdown does not generate extra badges. Shows percentage/distance/next goal | Same retrospective/live/correction rules; exact identity stable across catalogue revisions; no name-based transfer | E08/E09; 218 families in current data. IV logic, ID device, PI complete attainment |
| Full-route completion | Same route family's **100%** level: **<identity> · Completed it!**; no second full-route badge | Requires all eligible target sections, not an imaginary link between them | Same family/history | Confirmed by #193 implementation comment/E10; no missing duplicate award |

Network level titles: **National Cycle Network · 25%**, **· 50%**, **· 75%**, **· Completed it!**.
Route level titles follow the same exact pattern with `NCN n` or `RCN n`; current database has no additional display names.
One first award + four network levels + four × 218 route levels = **877 potential unlock levels**, displayed as **220 families**, not 877 separate cards.

No indiscriminate rounding: exact threshold arithmetic only absorbs numerical noise (max 1e-7 m or 1e-12 of reference length). Existing tests check boundary/display-rounding behaviour, 25% and 100% end-to-end, union/repeats, correction/deletion, dates, cache reconstruction and celebration acknowledgement. A dedicated exhaustive end-to-end 50%/75% and catalogue replacement/identity migration matrix is not demonstrated by those NCN-specific tests; retain as a targeted verification gap, not proof of a runtime defect.

Current levels can fall after correction/deletion; original recognition dates and celebration acknowledgements remain. Bulk assessment queues the highest current family level into the shared carousel. Catalogue replacement changes the denominator and invalidates derived namespaces; exact key/history survives. Removed/held identities disappear from current awards without deleting historical dates. These migration policies are documented in E10 and partly implemented/tested; real update/restart acceptance remains open.

### Recovered historical mystery and other proposals

These are included to prevent lost research or accidental reinstatement. Unless explicitly stated, user-facing description, precise progress rules and exact award IDs were not recovered. Historical positive location evidence was always required; the explicit final correction in #193 excludes driving/unknown from **every** NCN award.

| Historical item / recoverable name | Intended condition, evidence and history | Status now |
| --- | --- | --- |
| 14 original verse-line mysteries | Discover verified physical instances of associated coded discs; no manual decoding; correct disc-to-line/group mapping never established. Intended retrospective eligible foot/cycle recognition; reveal/progress details incomplete | H concept/mechanic in #193; **superseded** by N personality direction; NI |
| 56-fragment / 14 × 4 Roadprints acrostic tribute, In Honour of Time | Supplied historical project context; no shipped fragment dataset/approved full text recovered | **Superseded** by current owner brief. Do not reconstruct or publish poetry |
| 48-fragment / 12 × 4 tribute | Connected retrieval dates an accepted variant to 20 October 09:15:57 UTC, contradicting 56-fragment summaries | Historical transcript gap; both variants retired, so no need to resolve count for implementation |
| Five first-theme discoveries: **The Ages of Man**, **A Time for Every Season**, **Around the World**, **Written in the Stars**, **Through the Ages** | #193 confirmed one first verified disc in each of Ages of Man, Seasons, Continents, Zodiac, Centuries; distinct designs at repeated sites deduplicate. Historic eligible trips intended; partial-progress UI not fixed. Names explicitly **suggested, not user-approved** | H former direction, NI. **Q: retain separately or retire with puzzle?** Do not include automatically in new feature |
| **Master of Time** | First discovery in all five common themes; name a working suggestion; historical eligibility intended | H old condition, proposed title, NI; same retention question |
| Five second-tier theme-completion awards | Every distinct verified design in the theme, expected counts 4/6/8/12/20 respectively, **subject to catalogue verification**. Progress designs discovered / verified total; repeated geographic instances once. Retrospective/corrections required | H direction in #193, NI; no complete verified design-location catalogue. **The Full Circle of Life** suggested only for Ages of Man; other titles TBD; same retention question |
| Four artist/design discoveries | #193 says explore awards for the four milepost designs; not a finite approved rule set. No precise thresholds/names/retrospective specification recovered | P, NI; do not silently equate with the four designer personality awards |
| **First Steps in Time; The Four Artists; Time Traveller; Four Pieces of Time; Verse Collector; Time’s Mystery; Regional Time Traveller** | Assistant suggestions recovered from 20 October 09:16:04 UTC; complete measurable rules and approval not recovered | P only, NI. Poetry-linked suggestions retired; others need explicit scoping |
| First outing, distance, exploration, traffic-free, curated regional sets, named long-distance challenges / route-inspired titles | #79 suggestions; no agreed finite thresholds or exact titles recovered | P/Q, NI; optional, not missing confirmed beta requirements |
| Separate cycling-only / walking-only NCN milestone families | Explicitly rejected by #193 combined-family decision | Not required |
| General default cycling/distance/landmark awards | E16 and default app implementation; scope distinct from NCN collection | Not extra NCN requirements; preserve, do not duplicate |

### Revised personality awards — confirmed direction, not implementation-ready

The owner's current brief is the primary requirement source (N), dated 29 October 2026. Each of **John Grimshaw, Jon Mills, Iain McColl, Andrew Rowe, David Dudgeon, Charlie Harrow** is a distinct discovery target; these are identity labels, not an invented playful award-name catalogue. Descriptions/biographies are **not drafted** in this audit.

| Award | Precise confirmed rule | Evidence / progress / retrospective | Status |
| --- | --- | --- | --- |
| Each of the six personalities | First qualifying discovery of any verified post deterministically assigned to that person; repeats/multiple assigned sites cannot re-award | Walking/running/cycling only with supported location evidence. Binary person state; geography coverage is availability, **not** 72 visits required from the user. Historical journeys and bulk simultaneous awards confirmed by DEC-005; correction/migration mechanics remain technical work | NI, N; biographies/content/IDs and detector need specification |
| **In Honour of Time** | Discover all six different personalities | Six-person set; parent hidden before first discovery, then progress out of six (DEC-011); only qualifying evidence, no driving/unknown or code-disc solving | NI, N; parent reveal and historical policy settled; reuse shared celebrations (DEC-005/011) |

The state transition is confirmed: six “?” mysteries → first discovery reveals that person's identity/biography and exposes five silhouettes → later discoveries reveal their identities/biographies → all six unlock the parent. No particular person is mandated as everyone's first; deterministic post assignments determine which is found.

## Millennium Milepost evidence and coverage

### What was actually recovered

E12 contains **889** OSM candidate mapped objects at snapshot **2026-10-15T17:21:06Z**, from `area[ISO3166-1=GB][admin_level=2]; nwr[ncn_milepost](area); out center tags;`. The GB ISO code in this query denotes the United Kingdom admin area; it is not proof of nation/region completeness. Only **2** records carry both series and disc number, **4** a recorded check date. Missing tags do not prove absence. `out center` can give a way/relation centre, not a surveyed post point.

Disc-tagged leads: OSM node **6421727196** (Ravenscar, T5/disc7) and **7026960015** (Ashbourne, series5/disc5). These are unverified tag assertions, not approved discoveries. No national point set has been newly fabricated or admitted.

The source assessment recovers 182 Geograph table rows / 37 MP references and a 566-image KML search result. **566 images are not 566 distinct posts.** MP385/photo5455848 (2017, 100 m reported precision) is about 10 m from an OSM candidate; MP241/photo3645557 (2013, 10 m precision) about 15.9 m; MP444/photo2863732 (2012, 10 m precision) about 29.5 m. These are corroborating historical candidates, not independently confirmed identity/current survival/access/precise position. MP444 is useful supporting research but **outside the fixed twelve-post pilot**. No coordinates are promoted here.

Fixed Kent pilot (H, #193): **MP241 Milton Gravesend; MP385 Gravesend; MP437 Strood; MP438 Rochester; MP440 Chatham Historic Dockyard; MP441 Gillingham Strand; MP310 Canterbury; MP315 Crab and Winkle Way; MP318 Whitstable; MP626 Sandwich Quay; MP633 St Margaret's at Cliffe; MP833 White Cliffs of Dover**. Historical design labels are candidate assertions. Acceptance originally required at least five physically validated posts and three disc identities plus a repeat-disc test. DEC-003 supersedes mandatory physical/photo counts: automated reconciliation plus reliable source corroboration is the national verification path. Individually inspect conflicts; neither this pilot nor disc gates block Stage 1 or mandate photographs of every post.

**Unlock-ready verified locations in recovered evidence: zero.** This means no accepted location evidence record was recovered, not that no physical posts exist or that the 889 mapped positions are all wrong. No national current-existence/coordinate approval ledger or completed pilot acceptance was found.

| Source | Useful evidence | Quality / reuse limits |
| --- | --- | --- |
| Trust route pages/public route layer | Designation/open scope, geography/context | Route OGL/OS/NI attribution, not a licence to audit point data/art/poetry |
| Official milepost audit/map | Post/type/disc fields, observations/images, site leads | Preserved terms require express permission; public display is not redistribution approval; fresh terms fetch failed |
| OSM/Overpass original response | Candidate positions, object IDs, design/tags, repeatable extraction | ODbL attribution/database obligations; sparse freshness/disc fields; geometry centres and wrong tags require review |
| OSM wiki historic Sustrans inventory | MP references, region labels, historical design/location notes | CC BY-SA text distinct from OSM database licence; moved/missing posts and guessed location warnings present |
| Geograph | Dated photos, subject position/grid precision, design/context | Individual creator + CC BY-SA 2.0; camera ≠ subject; grid square ≠ unlock point; art/verse rights not automatically licensed |
| Time Trail website | Historical scheme, reproduced release, disc/theme research | Secondary hosting/attribution; no blanket reproduction or coordinate licence; original puzzle unnecessary for new experience |
| Claimed “complete KML” / checker CSV | Potential comparison lead | No authenticated complete national checker CSV or verified KML inventory recovered; don't call these datasets collected |

A **repeatable candidate-extraction and local comparison method exists**, with query/raw response/hashes/photo precision. A nationally repeatable **verification/approval** pipeline does not yet exist. Proposed later workflow: stable physical-post identity; separately record each position/identity/current-presence/access assertion with URL/date/precision/licence/confidence; corroborate independently; review relocated/missing/duplicate posts; mark a coordinate unlock-ready only after documented source-quality checks (automated reconciliation first, individual conflict review as needed; no per-post photo requirement); version and retain rejected/held records. Photo/design/disc evidence must remain separate assertions rather than a nearest-point inference.

### All twelve geographies

No accepted six-person assignment matrix exists. “0 accepted” below is an evidence status, not a census of posts. Candidate counts have not been spatially reconciled to an approved twelve-region boundary set.

| Geography | Recovered local verification | Accepted personality opportunities / target |
| --- | --- | --- |
| North East | National candidate source, no approved local verification ledger | 0 / 6 |
| North West | Same | 0 / 6 |
| Yorkshire and the Humber | National source; Ravenscar disc-tag lead, unverified | 0 / 6 |
| East Midlands | National source; Ashbourne disc-tag lead, unverified | 0 / 6 |
| West Midlands | National source, no approved local verification ledger | 0 / 6 |
| East of England | Same | 0 / 6 |
| London | Same | 0 / 6 |
| South East | Fixed twelve Kent candidates; two pilot candidates have Geograph comparison, none approved | 0 / 6 |
| South West | National source, no approved local verification ledger | 0 / 6 |
| Scotland | Same; official national presence statements are not point approval | 0 / 6 |
| Wales | Same | 0 / 6 |
| Northern Ireland | National source + S3 restoration context, no approved point ledger | 0 / 6 |
| Total | Verification and deterministic assignments still required | **0 / minimum target 72** |

The six opportunities in each geography are an availability target, subject to reliable evidence; do not announce universal coverage until the matrix clears. DEC-004 fixes one personality per eligible post, so six local personalities require at least six distinct posts. Implement stable identity, boundary and replacement rules; report evidenced geography gaps rather than inventing coordinates.

## Personality attribution check and architectural fit

This is a fact-check ledger, not publication biographies.

| Person | Supported contribution / source | Publication gate |
| --- | --- | --- |
| John Grimshaw | Trust S2 confirms founding and Bristol–Bath path contribution; Trust public materials identify Sustrans founder | Verify any further dates/claims against primary records before writing |
| Jon Mills | S1 explicitly names Brighton-based Jon Mills, The Fossil Tree | Use **Jon**, not the “John Mills” spelling repeated by some secondary sources; don't conflate sculptors |
| Iain McColl | S1 main designer text names Iain McColl, The Cockerel | Preserve Iain; S1 itself has an inconsistent “Ian” image caption, not grounds to rename him |
| Andrew Rowe | S1 and S3 identify Welsh designer and Swansea nautical/industrial inspiration | His design is not given an invented official title |
| David Dudgeon | S1/S3 identify Belfast designer of Tracks | Check additional biography separately |
| Charlie Harrow | S4 reproduced contemporary release explicitly identifies Time Trail originator; S5 and historical art inventory corroborate | Strong specific attribution lead; original Trust/RBS archived release or direct authoritative confirmation still needed before final publication |

Reuse E08's collection IDs, background refresh, persisted achievement history and shared celebration carousel. **Inference/proposal:** a separate versioned milepost-visit evidence store feeds stable person IDs and one parent award; it should not turn proximity to artwork into route-completion credit. The current `NcnAchievements` input is a route interval ledger, not a verified milepost visit ledger. Its existing rows do not implement “?”/silhouette/biography discovery state. Additive integration is plausible; no major architecture rewrite is justified.

Remaining Stage 2 technical questions: how to validate moving-path proximity within the fixed 100 m radius using raw fixes or supported matched paths; accuracy/continuity requirements for sparse imports; reliable mode transitions; off-current-NCN posts; correction/deletion/reveal retention; deterministic tie-breaking for simultaneous historical discoveries; catalogue relocation/replacement identity; and verified boundary/assignment persistence. These do not reopen historical eligibility, radius, repeat policy, initial visibility, parent reveal, biographies or asset style. They do not block Stage 1. No owner choice is needed to start S1.1; return only a concrete product tradeoff if implementation evidence exposes one.

## Register by workstream

Each row is a tracked work item. “Depends” gives register IDs or evidence gates; “phase” is a recommended package, not authorisation. Status is current implementation, distinct from historical requirement authority.

### 1. Catalogue and provenance

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CAT-01 | H: exact immutable baseline and reproducibility | E02–E04/E14; audit receipt | IV data/provenance | Preserve pins; no repeat capture/build | None | Replay 1,147/219/62,487 and hashes; live pin equality | Retain / S1 regression |
| CAT-02 | H: explicit 34 holds | E03; hold table above | IV exclusion; PI research | Investigate only with new evidence, keep reasons and namespaces | Source evidence | No accidental admissions/aliases; each candidate has reviewed disposition | Later research / not S1 |
| CAT-03 | H: open-target integrity and attainable completion | E03/E04; all 218 unbound targets | PI | Repair eligible denominator bindings; preserve valid geometry; interval dispositions and capacity gate under DEC-001 | CAT-01, mode/source evidence | Every released measurable route and unique network attainable from qualifying evidence, no false credit | P1 / S1.1 blocker |
| CAT-04 | H: alternatives/overlap and NCN228 | E03, E06 | PI | S1 only where deferral blocks eligible target; alternatives outside target and NCN228 remain deferred | CAT-02/03 | No target gaps concealed; excluded/unmeasurable identities gain no awards; no union inflation | S1.1 only intersecting gaps; rest later |
| CAT-05 | H: National/Regional/Named scope | E04/E11; 25-name leads, Heron/Viking research | PI | 216/3/0 active; scope named routes and migrate explicit identities; no blanket NCN relabel | Source/extent/access approval | Verified bounded named entries only, predictable alpha ordering and no duplicated physical total | Later enhancement / not S1 |
| CAT-06 | H: licences/provenance separation | E02/E11–E14/S8/S9 | IV existing route attribution; PI future inputs | Review future reuse before admission; no use of restricted audit without grant | New source selection | Per-source attribution and terms retained, no silent licence crossover | Retain; S1.1 changed sources only |

### 2. Android collection and user experience

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UX-01 | H: collection enable, legend, Progress jump/navigation/removal | E07/E09/E10 | ID | Protect implemented flow; one combined changed-path smoke, no full owner screen audit | Existing app | Single activation; removal confirmation; history/cache retained; core usable | Retain / targeted S1.3 smoke |
| UX-02 | H: grouped numeric lists/search/modes | E07 `NcnProgressUi`; `NcnUiTest` | ID numbered; PI Named | Protect implemented numeric search/modes; Named comparator only when separately admitted | CAT-05 for Named | 1,2,10; normalized NCN/RCN queries; empty state; Combined default; search doesn't erase browsing state | Retain; Named later |
| UX-03 | H later palette: purple badges/completed, solid orange remaining, subdued roads | E07/E09/E15; old #192 styling superseded | ID | Preserve current palette; check only changed rendering and accessibility | Existing app | Shared 78×32 dp badges, white text; main/focused legend agrees; exit restores roads/camera | Retain / S1.3 changed UI only |
| UX-04 | H: focused map/camera/zoom/progress/journeys | E06 detail; E07 shared screen; E09 geometry/presentation/UI tests | ID | Protect existing maps/journeys; test affected disconnected and no-credit fixtures automatically | CAT-03 scope | Correct fit and zoom; selected-mode totals; only actual contributing journeys; no invented joins | Retain / S1.3 integration |
| UX-05 | H: route-tap verified names/endpoints and shared identities | #192; E04/E07 | PI | Ensure honest unknown endpoints and essential selection/navigation; verified endpoint/name enrichment and full panel switch deferred | Verified source metadata | No invented termini or connecting lines; existing shared-identity selection preserved | S1.3 honest unknowns; metadata later |
| UX-06 | H: interactive aggregate summary | #192; E07 | PI | Defer aggregate tap enhancement; existing route rows already open detail | Product interaction choice | Aggregate tap opens meaningful breakdown/selection without implying one route | Optional later / not S1 |
| UX-07 | H: usable loading/empty/failure/retry | E06/E07/E09 | ID | Protect existing install/empty/retry; targeted failure/offline smoke if changed | Existing app | Honest ready state; retained data; bounded work; explicit retry; no endless automatic loop | Retain / S1.3 failure smoke |

### 3. Journey assessment and progress calculations

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| JRN-01 | H: foot/run/cycle provenance; driving/unknown excluded | E06 `NcnMatcher`; `NcnAndroidTest`, `NcnNationalTest` | IV supported contracts; ID real traces | Protect supported contracts; use saved eligible/unsupported fixtures, no requested field trip | Real eligible saved evidence | No relabel/proximity credit; unsupported matcher held; exact partial travel only | Retain / S1.1–2 regressions |
| JRN-02 | H: physical union across repeats/modes/routes | E06 `NcnLedger`; E09 golden/national tests | IV algorithm/data mapping | Preserve interval unions; extend only affected binding/network regressions | CAT-01 | Each route its own credit; combined network once; no sum of route percentages | Retain / S1.1–2 regressions |
| JRN-03 | H: retrospective activation/import, incremental resume, no historical prompts | E06 store; E09 national/responsiveness; E11 | IV cache/resume tests; ID large archive | Protect resumable historical assessment; one large-archive measured replay plus restart test | Existing app/history | Progress and restart safe; no fresh blanket GPS rematch; unchanged data reused; stale epoch never publishes | Retain / S1.2 regression |
| JRN-04 | H: corrections/deletion/reference replacement | E06/E08/E09/E10 | IV tested corrections; PI exhaustive migration acceptance | Add focused replacement/rollback/stable-key matrix before changed bindings ship | CAT-01, data version scenarios | Superseded coverage removed, other support survives, dates preserved, no nearby identity transfer | P1 / S1.2 verification gap |
| JRN-05 | H: bounded section ambiguity review for eligible new native journeys only | #79; E11; Python activation/confirmation fixtures | PI research; NI Android NCN review | Implement bounded eligible-new-native ambiguity review using existing contract; evidence-producing alternatives only | Credible competing-path evidence; approved UX | NCN/road/Not sure; supported uncertain window only; persisted decisions; no historical/import queue or repeat prompt | P1 / S1.3 bounded native review |
| JRN-06 | Q: eligible activity but no supported reference match | E06 supported matcher gates | IV conservative rejection; U user sufficiency | Inspect only specific unbound spans/unsupported associations needed by S1.1; retain conservative rejection elsewhere | Real sample, CAT-03 | No fabricated credit; distinguish no evidence from no route; no broad distance-tolerance relaxation | S1.1 only concrete blocking spans |

### 4. Achievements and milestones

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ACH-01 | H: first discovery | Core table/E08/E09 | IV logic, ID UI | Device first/history/undo/reimport | JRN-01/03 | Positive unique eligible distance only; one family/recognition event | Retain / S1.2 |
| ACH-02 | H: 218 combined route families, 25/50/75/100 | Core table/E08/E09 | IV logic; PI attainability | Preserve existing combined family; prove 50/75 end-to-end and attainable 100 after binding correction | CAT-03, JRN-04 | Exact thresholds, disconnected target, repeats/modes/import/deletion and state reconstruction | P1 / S1.1–2 |
| ACH-03 | H: network 25/50/75/100 | Core table/E06/E08/E09 | IV logic, PI complete evidence | Prove unique-network capacity and all thresholds through changed-reference migration; preserve family | CAT-03, JRN-02/04 | Unique physical target denominator; no sum inflation or rounded false 100 | P1 / S1.1–2 |
| ACH-04 | H: shared carousel/history/persistence | E08/E09/E10 | ID | Reuse shared carousel; focused bulk/dismiss/restart regression and one changed-build smoke | ACH-01/02/03 | No modal storm/reaward on reopening; no fabricated original trip date; retained acknowledgements | Retain / S1.2–3 |
| ACH-05 | Q: historic theme/design awards and proposed names | Historical table/#193/E15 | NI | Keep finite historical inventory; no Stage 1 implementation or disc-retention owner gate. Separate future proposal only | Review only | Complete finite list and explicit evidence gates if retained | Outside S1 and personality scope; future disposition |
| ACH-06 | P: extra distance/geography/mode awards | #79/E16/historical table | NI, optional | No implementation unless separately scoped; don't duplicate free core awards | Owner product scope | Names/conditions supported by new decision, not invented history | Optional later / not S1 |

### 5. Millennium Milepost research

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MP-01 | H: recover datasets, source quality and Kent pilot | E11–E14; evidence section | IV recovery; PI validation | Keep 889 candidates separate from approved posts; no unlock points admitted | None | Raw source/precision/licence provenance recovered; zero false “verified” claims | Retain research / S2 |
| MP-02 | H: fixed twelve Kent candidates | #193/E13 | PI candidate comparisons; no accepted unlocks | Use Kent candidates for S2 pipeline fixtures; automated reconciliation and individual conflict review under DEC-003 | Permitted independent evidence | Reliable coordinates/provenance; hold conflicts; no mandatory photographic census or disc gate | S2 fixtures, not release gate |
| MP-03 | N: six opportunities in every geography | Current brief; twelve-row matrix | NI | All six personalities per twelve geographies subject to verified availability (DEC-004) | MP-02 method, CAT-06 | Stable one-person-per-post mapping; evidenced gaps allowed, no fabricated national coverage | S2 only |
| MP-04 | H/N: repeatable national verification pipeline | E12 extraction, E13 local comparisons | PI | Automate source reconciliation/provenance; individually review conflicts; unresolved coordinates never unlock (DEC-003) | Source terms | Reproducible checks with held/approved distinction and reviewer evidence; extraction alone insufficient | S2 only |
| MP-05 | H: licence and image/art rights | E11–E13/S6–S9 | PI | Official audit permission if used; individual photo/art/silhouette rights review | Chosen sources/assets | No restricted data/verse/art silently bundled; attribution retained | S2 only |
| MP-06 | Superseded: code-disc/poem relationship | #193/S5 | NI | Archive findings; don't continue mapping for personality feature | ACH-05 only if separate discs retained | No puzzle-decoding or verse requirement in revised feature | Archived / not S1 |

### 6. In Honour of Time discovery experience

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-01 | N: six-person selection with reliable attribution | Current brief; fact ledger/S1–S4 | NI feature; partial fact validation | Verify factual 40–70-word summaries/Wikipedia links; simple recognisable 2px head-and-shoulders icons (DEC-006/007) | Reliable historical sources | Six identities/contributions sourced; Jon/Iain spelling; no invented biographical detail | S2 only |
| TIME-02 | N: ? → first identity + five silhouettes → all six + parent | Current brief; E08 lacks mystery state | NI | Implement DEC-009/010/011: six ? with no advance clues; first unlock explains concept, reveals person and five anonymous silhouettes; parent then visible out of six | TIME-01, approved assets | Deterministic persisted transitions, accessible concealed labels and no accidental spoiler leaks | S2 only |
| TIME-03 | N: repeated deterministic site opportunities, target72 | Current brief | NI | Stable deterministic one-person assignment per eligible post; six opportunities per geography subject to verified data (DEC-004/008) | MP-03/04, TIME-01 | Same dataset gives same post/person mapping; repeat visits don't reaward; all six locally available when verified | S2 only |
| TIME-04 | N: foot/run/cycle proximity evidence only | Current brief, #193 correction | NI detector | Fixed 100 m moving foot/run/cycle pass-by; unintentional and historical passes qualify; define reliable path evidence, not a new radius (DEC-002) | MP-02, JRN-01 | Within 100 m verified post + supported moving eligible journey; stationary/drive/unsupported/invalid-coordinate negatives | S2 only |
| TIME-05 | N: retrospective/reimport/original discovery policy; technical correction/update semantics remain | General NCN H; new feature details unresolved | NI | DEC-005/007/008: historical and simultaneous awards, no artificial delay; retain original location/trip date separately from recognition; specify correction/relocation tie mechanics | TIME-04, JRN-04 | Versioned duplicate-safe state, changed/deleted evidence reconciles as approved; no background scan storm | S2 only |
| TIME-06 | N: six discoveries unlock In Honour of Time | Current brief | NI | DEC-011: hidden until first person, then six-person progress; award all six using existing celebrations, including one import | TIME-02–05 | All six distinct people, no need to visit all72; one parent unlock; no original puzzle/poetry dependency | S2 only |

### 7. Performance, accessibility and regression coverage

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| QA-01 | H: no UI wait on install/assessment; useful caching | E06/E09/E10; #211/#219/#221 | IV targeted tests; ID device | Measure only changed assessment/caching against same-archive baseline; protect fast warm navigation | Existing app | Responsive navigation, bounded memory/retries, no stale publication; no invented phone budget | Retain / S1.2–3 measured guard |
| QA-02 | H: overlay frame/zoom/tap performance | E07/E09/E10 | ID | Changed-map checks only; optimise only reproduced regressions, no speculative rewrite | QA-01 | Stable camera, usable gestures/taps, cancellation handles rapid mode/layer changes | Retain / S1.3 changed map smoke |
| QA-03 | H: accessible layout and non-colour interpretation | #192; E07 content descriptions/live region/touch sizes | PI | Close essential labels, large-text clipping and non-colour progress meaning in changed NCN flow | Real devices | All controls labelled/reachable; complete/remaining understandable without hue alone; no clipping | P1 / S1.3 essential usability |
| QA-04 | H: keep evidence/coverage and navigation regressions | E09; E02 automated research checks | IV existing scope | Focus missing-case tests only, preserve Dover/walking/core maps when future changes made | Future changed area | No source/progress inflation, unchanged reference pins; exact affected suites green | Retain / each changed package |
| QA-05 | H: offline/update/resume/rollback and storage | E06 installer; `NcnCatalogueFilesTest` | IV cache/failure tests, ID real transfer/update | Automate replacement/interruption/rollback; one real install/offline/resume smoke when installer or data changes | Controlled cache, approved test data | Existing generation survives failure/offline; 136MiB compressed/232MiB decoded plus generations budget honest | Retain / S1.2–3 migration smoke |

### 8. Release readiness and real-device acceptance

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| REL-01 | H: source integration distinct from release | E01/E02 | IV main integration | No remerge of historic stacks; verify installed provenance when testing | None | Current main branch/PR base recorded; code220 preserved | Retain / each PR |
| REL-02 | H: owner device acceptance | E09/E10/#194; supplied project reports | PI anecdotal exercised beta, no full signed matrix | Short changed-flow Pixel check; Samsung only affected platform/background behaviour or available regression evidence | A, then changed packages | Source/data recorded; changed usability/performance/celebrations verified; no exhaustive screenshot collection | S1.3 targeted checks only |
| REL-03 | H: coordinated purposeful release only | Governance/#208 | NI new release; not requested | One coherent private APK only if runtime changes and separately authorised; publication separately approved | Approved packages + checks | Exact source/signature/artifact/rollback, explicit distribution approval; no automatic testers | Separate approved release |
| REL-04 | H: honest readiness/issue state | E05 current stale issue bodies | PI | Use DEC-001/012 and S1 brief; report supported scope, unresolved blockers and separate S2; no issue closure now | Owner review | Separate bounded supported catalogue, whole-route attainability, optional research and mystery feature | Each package / honest status |

## Focused delivery sequence — replaces former A–G audit-first plan

The [Stage 1 development brief](ncn-stage-1-development-brief.md) is the implementation entrypoint. Existing functionality stays implemented; missing device evidence is not automatically a development defect.

| Package | Purpose | Gate |
| --- | --- | --- |
| S1.1 — Attainable supported geometry | Correct evidence-backed bindings for existing eligible target; implement per-route and canonical-network capacity regression gates. No catalogue expansion. | Exact target preservation or evidenced eligibility correction; real assessment-to-credit proof; unresolved valid intervals remain explicit blockers. |
| S1.2 — Milestones and reference transitions | Close 50/75 and replacement/rollback gaps, retain combined foot/cycle families, historical assessment, corrections and celebrations. Fix only demonstrated defects. | All four thresholds exact, unique union, stable identities/history, bounded resumable work. |
| S1.3 — Essential Android integration | Bounded new-native ambiguity review; honest missing metadata; essential accessibility and focused changed-flow validation. Preserve working maps/search/modes. | No import review queue, no unsupported choice credit, state/performance preserved; short device checks only. |
| S2 — Independent later feature | Reliable milepost catalogue and DEC-002–011 personality discoveries. Research can proceed independently. | Separate specification and release; never a Stage 1 dependency. |

No broad catalogue reinvestigation, manual screen inventory, named-route expansion, extra badges, full endpoint research, mandatory photographic pilot or aggregate-chart redesign is required for Stage 1. Historical disc/theme proposals remain preserved and excluded from both delivery scopes unless separately commissioned; their ultimate retention need not be decided now. No major architecture rewrite. Source merge, data publication, backend deployment and APK distribution retain separate approval gates.

## Explicit evidence gaps and decision queue

1. **Full historical conversations unavailable:** connected retrieval returned dated excerpts, not complete transcripts/permalinks. The 48/56-fragment contradiction cannot be fully reconciled; both are superseded. No missing names/thresholds are invented.
2. **No complete named NCN achievement catalogue beyond recovered families/proposals:** E16 is default/free scope. To add further historical requirements, supply the original approved specification/message or identifiable issue comment.
3. **No accepted milepost point ledger / completed Kent pilot:** candidates, photo correspondences and official schema were recovered; independently approved current position/existence/access records were not. S2 needs source-quality reconciliation with individual conflict review (DEC-003), not an assumed map centre or photographs of every post.
4. **No permission grant for official milepost audit / confirmed complete KML or checker CSV:** provide original permission/data with provenance if available. Do not infer rights from public access or route-layer OGL.
5. **Limited real-device evidence for current NCN slice:** prior beta usage and performance improvements are reported; automated tests do not prove device behaviour. Use only the focused changed-flow checks in the Stage 1 brief; a comprehensive two-phone matrix is not a prerequisite to development.
6. **Charlie Harrow attribution:** reproduced specific historical release supports the selection; original authoritative provenance is still a content publication gate. No further biography facts should be inferred.
7. **Current installed source, user cache generation and live backend version:** not read from phones/Render in this documentation task.220 is the supplied baseline; source provenance is E01. Supported matcher contracts were inspected without assuming all real journeys satisfy them.
8. **Remaining Stage 1 engineering questions:** which retained target intervals have repairable source correspondence versus genuinely missing/ambiguous access evidence; whether exact mapping changes require consumer/schema work; and which credible native ambiguity payload can feed the existing review contract. Resolve these in the focused implementation packages, not a new broad audit. No additional owner input is required to begin S1.1. Ask only if an evidenced valid interval cannot be supported and a product scope change is proposed. Stage 2 correction/deletion, off-network-post and simultaneous-discovery tie policies remain deferred; confirmed NCN-DEC-001–012 are not open questions.

## Audit validation and delivery state

Fresh read-only data receipt and source-test results are in E04. Main's existing validation records include810 Android tests/109 suites and333 NCN/reference checks at the reconciled baseline; these historical Android results were inspected, **not rerun here**, and prove no phone acceptance. Fresh research/reference checks are identified separately in the receipt. The documentation review checks link existence, register IDs and docs-only diff.

PR #238 now contains the updated register, the original unchanged audit receipt and the new Stage 1 brief. This consolidation changes only the two Markdown documents; it does not rerun or overwrite the original audit. No Android/backend runtime, geometry, route identity, production data or release workflow is changed. No publisher, backend deployment, APK build/distribution, branch deletion, historical PR merge or issue closure is performed. Documentation PR targets main and remains open for review. Next task: focused S1.1 development from latest validated main, with the requirements above; no further broad research or acceptance audit. This documentation task does not authorise implementation or release.
