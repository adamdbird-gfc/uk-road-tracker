# NCN Completion Register

Audit date: 29 October 2026. Status: **research recovered; completion plan proposed for owner review**.
Repository: `adamdbird-gfc/uk-road-tracker`.
Audited source: `d9adc5f65065adc82b16996ec8bcc945b81dd311` (latest main at audit).
Android baseline: **0.25.220 (266)**; packaged source `67e74e7d9e9873552ae98b23397cd9e265fc3517`.

This is the authoritative consolidation for the next NCN planning review. It records evidence and uncertainties, not permission to implement, publish, build, deploy or merge. Original issues remain historical evidence; their stale “not implemented”, grey-dashed/red styling and stacked-release instructions must not override later code or decisions. No runtime or reference data changes form part of this audit.

## Reading the evidence

Requirement authority: **H** = identifiable historical product requirement; **N** = new confirmed direction in the owner's 29 October audit brief; **P** = proposal; **Q** = unresolved. Code proves implementation, not that its exact copy/design was approved.

Implementation classifications:
- **IV** — Implemented and validated: source and identified automated/data evidence; validation scope is stated.
- **ID** — Implemented but awaiting real-device acceptance.
- **PI** — Partially implemented.
- **NI** — Not implemented.
- **U** — Unclear / insufficient evidence.

IV never means real-device visual acceptance. P1 = essential reliability/completion or immediate review; P2 = dependent research/feature completion; P3 = optional. Delivery phases refer to the ordered plan below.

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

**Important completion limitation:** all 218 measurable route receipts record more than 1 m of unbound open target. Summed route-local unbound length is **1,334,036.721 m**; this is not a deduplicated network deficit and is not a user's missing travel. For example NCN1's combined maximum bound target is about **92.658%** of its retained target. These are checkpoint binding-capacity diagnostics, not personal completion percentages or proof that remaining travel is impossible forever. Current bindings cannot demonstrate full-route attainment until gaps are reviewed. Never shorten the denominator, invent connectors, inflate tolerance or round to 100% to hide this.

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
| Each of the six personalities | First qualifying discovery of any verified post deterministically assigned to that person; repeats/multiple assigned sites cannot re-award | Walking/running/cycling only with supported location evidence. Binary person state; geography coverage is availability, **not** 72 visits required from the user. Historical qualification/migration details still Q | NI, N; biographies/content/IDs and detector need specification |
| **In Honour of Time** | Discover all six different personalities | Six-person set; progress display before/after reveal to decide; only qualifying evidence, no driving/unknown or code-disc solving | NI, N; exact parent reveal/celebration and retrospective rules Q |

The state transition is confirmed: six “?” mysteries → first discovery reveals that person's identity/biography and exposes five silhouettes → later discoveries reveal their identities/biographies → all six unlock the parent. No particular person is mandated as everyone's first; deterministic post assignments determine which is found.

## Millennium Milepost evidence and coverage

### What was actually recovered

E12 contains **889** OSM candidate mapped objects at snapshot **2026-10-15T17:21:06Z**, from `area[ISO3166-1=GB][admin_level=2]; nwr[ncn_milepost](area); out center tags;`. The GB ISO code in this query denotes the United Kingdom admin area; it is not proof of nation/region completeness. Only **2** records carry both series and disc number, **4** a recorded check date. Missing tags do not prove absence. `out center` can give a way/relation centre, not a surveyed post point.

Disc-tagged leads: OSM node **6421727196** (Ravenscar, T5/disc7) and **7026960015** (Ashbourne, series5/disc5). These are unverified tag assertions, not approved discoveries. No national point set has been newly fabricated or admitted.

The source assessment recovers 182 Geograph table rows / 37 MP references and a 566-image KML search result. **566 images are not 566 distinct posts.** MP385/photo5455848 (2017, 100 m reported precision) is about 10 m from an OSM candidate; MP241/photo3645557 (2013, 10 m precision) about 15.9 m; MP444/photo2863732 (2012, 10 m precision) about 29.5 m. These are corroborating historical candidates, not independently confirmed identity/current survival/access/precise position. MP444 is useful supporting research but **outside the fixed twelve-post pilot**. No coordinates are promoted here.

Fixed Kent pilot (H, #193): **MP241 Milton Gravesend; MP385 Gravesend; MP437 Strood; MP438 Rochester; MP440 Chatham Historic Dockyard; MP441 Gillingham Strand; MP310 Canterbury; MP315 Crab and Winkle Way; MP318 Whitstable; MP626 Sandwich Quay; MP633 St Margaret's at Cliffe; MP833 White Cliffs of Dover**. Historical design labels are candidate assertions. Acceptance originally required at least five physically validated posts and three disc identities plus a repeat-disc test. Physical validation remains useful; disc gates are relevant only if separate disc-theme work survives, not a dependency of the revised personality feature.

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

A **repeatable candidate-extraction and local comparison method exists**, with query/raw response/hashes/photo precision. A nationally repeatable **verification/approval** pipeline does not yet exist. Proposed later workflow: stable physical-post identity; separately record each position/identity/current-presence/access assertion with URL/date/precision/licence/confidence; corroborate independently; review relocated/missing/duplicate posts; approve a bounded unlock coordinate only after an explicit gate; version and retain rejected/held records. Photo/design/disc evidence must remain separate assertions rather than a nearest-point inference.

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

The six opportunities in each geography are an availability target, subject to reliable evidence; do not announce universal coverage until the matrix clears. Decide distinct-post requirements, border treatment, replacement posts and travel/access feasibility before assigning anything.

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

Unresolved technical/product decisions: proximity/accuracy and temporal continuity rules; raw fixes versus matched path support; drive-to-walk transitions and false mode labels; sparse historical imports and retrospective activation; whether artwork off current designated NCN may qualify; unique post-to-person cardinality; simultaneous multi-unlocks/order/reveal; correction/deletion and whether silhouettes revert; catalogue replacement/relocation and stable identity migration; duplicate/reimport handling; silhouette assets/likeness provenance and biography citations; parent card concealment/progress; verified-region boundaries; availability if one geography cannot clear six independent opportunities. None is silently chosen here.

## Register by workstream

Each row is a tracked work item. “Depends” gives register IDs or evidence gates; “phase” is a recommended package, not authorisation. Status is current implementation, distinct from historical requirement authority.

### 1. Catalogue and provenance

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CAT-01 | H: exact immutable baseline and reproducibility | E02–E04/E14; audit receipt | IV data/provenance | Preserve pins; no repeat capture/build | None | Replay 1,147/219/62,487 and hashes; live pin equality | P1 / A, retained |
| CAT-02 | H: explicit 34 holds | E03; hold table above | IV exclusion; PI research | Investigate only with new evidence, keep reasons and namespaces | Source evidence | No accidental admissions/aliases; each candidate has reviewed disposition | P2 / D |
| CAT-03 | H: open-target integrity and attainable completion | E03/E04; all 218 unbound targets | PI | Quantify binding gaps; review achievable thresholds and honest user explanation; do not shrink target | CAT-01, mode/source evidence | Gap inventory reproduced; no inferred travel; approved treatment before “complete routes” claims | P1 / B then D |
| CAT-04 | H: alternatives/overlap and NCN228 | E03, E06 | PI | 176 deferrals/49 routes; NCN228 own completion scope; preserve reviewed Route177 research separately | CAT-02/03 | Exact approved alternative bindings; no untravelled geometry painted or doubled union | P2 / D |
| CAT-05 | H: National/Regional/Named scope | E04/E11; 25-name leads, Heron/Viking research | PI | 216/3/0 active; scope named routes and migrate explicit identities; no blanket NCN relabel | Source/extent/access approval | Verified bounded named entries only, predictable alpha ordering and no duplicated physical total | P2 / D then C |
| CAT-06 | H: licences/provenance separation | E02/E11–E14/S8/S9 | IV existing route attribution; PI future inputs | Review future reuse before admission; no use of restricted audit without grant | New source selection | Per-source attribution and terms retained, no silent licence crossover | P1 / D/E |

### 2. Android collection and user experience

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UX-01 | H: collection enable, legend, Progress jump/navigation/removal | E07/E09/E10 | ID | Pixel/Samsung return-state and disable/re-enable check | Existing app | Single activation; removal confirmation; history/cache retained; core usable | P1 / A |
| UX-02 | H: grouped numeric lists/search/modes | E07 `NcnProgressUi`; `NcnUiTest` | ID numbered; PI Named | Accept current numeric order/search/clear/keyboard/group state; Named alpha comparator needs explicit verification when data admitted (current fallback is key sort) | CAT-05 for Named | 1,2,10; normalized NCN/RCN queries; empty state; Combined default; search doesn't erase browsing state | P1 / A; Named C/D |
| UX-03 | H later palette: purple badges/completed, solid orange remaining, subdued roads | E07/E09/E15; old #192 styling superseded | ID | Device contrast, zoom, narrow layout and legacy issue reconciliation | Existing app | Shared 78×32 dp badges, white text; main/focused legend agrees; exit restores roads/camera | P1 / A |
| UX-04 | H: focused map/camera/zoom/progress/journeys | E06 detail; E07 shared screen; E09 geometry/presentation/UI tests | ID | Full-length and disconnected routes, no-credit/held map, back/scroll, per-section journeys | CAT-03 scope | Correct fit and zoom; selected-mode totals; only actual contributing journeys; no invented joins | P1 / A |
| UX-05 | H: route-tap verified names/endpoints and shared identities | #192; E04/E07 | PI | Current chooser identifies overlapping routes and totals; no verified names/termini data path, no full in-panel journey switch | Verified source metadata | Show known endpoints/multiple sections; explicit unknowns; select all verified identities without losing state | P1 / B/C; data D |
| UX-06 | H: interactive aggregate summary | #192; E07 | PI | Per-route row/bar clickable; overall `CoverageSummary` has no NCN route-breakdown click in this UI | Product interaction choice | Aggregate tap opens meaningful breakdown/selection without implying one route | P2 / C |
| UX-07 | H: usable loading/empty/failure/retry | E06/E07/E09 | ID | Verify first install, interrupted install, assessment failure, mode refresh failure and retry on device | Existing app | Honest ready state; retained data; bounded work; explicit retry; no endless automatic loop | P1 / A |

### 3. Journey assessment and progress calculations

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| JRN-01 | H: foot/run/cycle provenance; driving/unknown excluded | E06 `NcnMatcher`; `NcnAndroidTest`, `NcnNationalTest` | IV supported contracts; ID real traces | Field sample current matcher outputs/import legacy; document unsupported outcomes | Real eligible saved evidence | No relabel/proximity credit; unsupported matcher held; exact partial travel only | P1 / A |
| JRN-02 | H: physical union across repeats/modes/routes | E06 `NcnLedger`; E09 golden/national tests | IV algorithm/data mapping | Preserve tests; device mode switching/drill-down | CAT-01 | Each route its own credit; combined network once; no sum of route percentages | P1 / A |
| JRN-03 | H: retrospective activation/import, incremental resume, no historical prompts | E06 store; E09 national/responsiveness; E11 | IV cache/resume tests; ID large archive | Full history activation, restart, cancellation, changed/deleted journey acceptance | Existing app/history | Progress and restart safe; no fresh blanket GPS rematch; unchanged data reused; stale epoch never publishes | P1 / A |
| JRN-04 | H: corrections/deletion/reference replacement | E06/E08/E09/E10 | IV tested corrections; PI exhaustive migration acceptance | Target live replacement/rollback/stable-key changes; assess coarse road-removal hold explanation | CAT-01, data version scenarios | Superseded coverage removed, other support survives, dates preserved, no nearby identity transfer | P1 / B |
| JRN-05 | H: bounded section ambiguity review for eligible new native journeys only | #79; E11; Python activation/confirmation fixtures | PI research; NI Android NCN review | Specify minimal supported alternatives UI and persisted section/reference decisions | Credible competing-path evidence; approved UX | NCN/road/Not sure; preserve original fixes, rest of journey and saved decisions; no import queue | P1 / B/C |
| JRN-06 | Q: eligible activity but no supported reference match | E06 supported matcher gates | IV conservative rejection; U user sufficiency | Audit reason counts with diagnostics; targeted source association research only | Real sample, CAT-03 | No fabricated credit; distinguish no evidence from no route; no broad distance-tolerance relaxation | P1 / A/B |

### 4. Achievements and milestones

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ACH-01 | H: first discovery | Core table/E08/E09 | IV logic, ID UI | Device first/history/undo/reimport | JRN-01/03 | Positive unique eligible distance only; one family/recognition event | P1 / A |
| ACH-02 | H: 218 combined route families, 25/50/75/100 | Core table/E08/E09 | IV logic; PI attainability | Dedicated 50/75 end-to-end evidence; open binding gaps tracked, no duplicate full-route badge | CAT-03, JRN-04 | Exact thresholds, disconnected target, repeats/modes/import/deletion and state reconstruction | P1 / B/D |
| ACH-03 | H: network 25/50/75/100 | Core table/E06/E08/E09 | IV logic, PI complete evidence | Network bound-capacity study and migration matrix; accept UI | CAT-03, JRN-02/04 | Unique physical target denominator; no sum inflation or rounded false 100 | P1 / B/D |
| ACH-04 | H: shared carousel/history/persistence | E08/E09/E10 | ID | Device bulk import, dismissal, downgrade/requalify, restart; check many route families | ACH-01/02/03 | No modal storm/reaward on reopening; no fabricated original trip date; retained acknowledgements | P1 / A/B |
| ACH-05 | Q: historic theme/design awards and proposed names | Historical table/#193/E15 | NI | Owner disposition: retain as separate future feature or retire; no implicit reapproval | Review only | Complete finite list and explicit evidence gates if retained | P2 / B |
| ACH-06 | P: extra distance/geography/mode awards | #79/E16/historical table | NI, optional | No implementation unless separately scoped; don't duplicate free core awards | Owner product scope | Names/conditions supported by new decision, not invented history | P3 / later |

### 5. Millennium Milepost research

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MP-01 | H: recover datasets, source quality and Kent pilot | E11–E14; evidence section | IV recovery; PI validation | Keep 889 candidates separate from approved posts; no unlock points admitted | None | Raw source/precision/licence provenance recovered; zero false “verified” claims | P1 / audit complete |
| MP-02 | H: fixed twelve Kent candidates | #193/E13 | PI candidate comparisons; no accepted unlocks | Current physical identity/location/access verification; original disc pilot only if retained | Permitted independent evidence | Reviewable confidence/hold per post; several positive/negative detector fixtures; no fabricated coordinates | P2 / E |
| MP-03 | N: six opportunities in every geography | Current brief; twelve-row matrix | NI | Verify boundary set, geographic candidates, accessibility and spread; minimum target72 | MP-02 method, CAT-06 | Six supported opportunities per geography or explicit evidenced gap; no national-complete claim prematurely | P2 / E |
| MP-04 | H/N: repeatable national verification pipeline | E12 extraction, E13 local comparisons | PI | Stable post/assertion IDs, precision/freshness, independent corroboration, moved/missing records | Source terms | Reproducible checks with held/approved distinction and reviewer evidence; extraction alone insufficient | P2 / E |
| MP-05 | H: licence and image/art rights | E11–E13/S6–S9 | PI | Official audit permission if used; individual photo/art/silhouette rights review | Chosen sources/assets | No restricted data/verse/art silently bundled; attribution retained | P1 / E |
| MP-06 | Superseded: code-disc/poem relationship | #193/S5 | NI | Archive findings; don't continue mapping for personality feature | ACH-05 only if separate discs retained | No puzzle-decoding or verse requirement in revised feature | P3 / archived |

### 6. In Honour of Time discovery experience

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TIME-01 | N: six-person selection with reliable attribution | Current brief; fact ledger/S1–S4 | NI feature; partial fact validation | Confirm Charlie primary attribution; verify all proposed facts before future biographies | Reliable historical sources | Six identities/contributions sourced; Jon/Iain spelling; no invented biographical detail | P2 / E |
| TIME-02 | N: ? → first identity + five silhouettes → all six + parent | Current brief; E08 lacks mystery state | NI | Stable reveal state/content model, silhouette UX and simultaneous unlock order | TIME-01, approved assets | Deterministic persisted transitions, accessible concealed labels and no accidental spoiler leaks | P2 / F |
| TIME-03 | N: repeated deterministic site opportunities, target72 | Current brief | NI | Versioned assignments, cardinality, regions/borders/replacement rules; no assignments in this audit | MP-03/04, TIME-01 | Same dataset gives same post/person mapping; repeat visits don't reaward; all six locally available when verified | P2 / E/F |
| TIME-04 | N: foot/run/cycle proximity evidence only | Current brief, #193 correction | NI detector | Define accuracy/continuity/speed/mode source and anti-drive-by; no arbitrary radius chosen | MP-02, JRN-01 | Eligible positives; drive/unknown/parallel road/GPS jump/sparse unsupported track negatives; originals preserved | P1 for feature / F |
| TIME-05 | Q: retrospective/correction/reimport/update policy | General NCN H; new feature details unresolved | NI | Decide visit evidence retention, historical import and downgrade/migration/reveal semantics | TIME-04, JRN-04 | Versioned duplicate-safe state, changed/deleted evidence reconciles as approved; no background scan storm | P2 / F |
| TIME-06 | N: six discoveries unlock In Honour of Time | Current brief | NI | Parent card/progress/celebration details; reuse shared infrastructure | TIME-02–05 | All six distinct people, no need to visit all72; one parent unlock; no original puzzle/poetry dependency | P2 / F |

### 7. Performance, accessibility and regression coverage

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| QA-01 | H: no UI wait on install/assessment; useful caching | E06/E09/E10; #211/#219/#221 | IV targeted tests; ID device | Full-archive cold/warm/download/one edit/one addition/rename profiling; retain source-read counts | Existing app | Responsive navigation, bounded memory/retries, no stale publication; no invented phone budget | P1 / A |
| QA-02 | H: overlay frame/zoom/tap performance | E07/E09/E10 | ID | Measure country→street and overlapping routes. Main overlay still iterates visible JSON vertices during drawing; no speculative rewrite | QA-01 | Stable camera, usable gestures/taps, cancellation handles rapid mode/layer changes | P1 / A |
| QA-03 | H: accessible layout and non-colour interpretation | #192; E07 content descriptions/live region/touch sizes | PI | TalkBack, large fonts/narrow screens, contrast/non-colour map meaning; solid lines currently rely heavily on colour | Real devices | All controls labelled/reachable; complete/remaining understandable without hue alone; no clipping | P1 / A then C |
| QA-04 | H: keep evidence/coverage and navigation regressions | E09; E02 automated research checks | IV existing scope | Focus missing-case tests only, preserve Dover/walking/core maps when future changes made | Future changed area | No source/progress inflation, unchanged reference pins; exact affected suites green | P1 / B/C/F |
| QA-05 | H: offline/update/resume/rollback and storage | E06 installer; `NcnCatalogueFilesTest` | IV cache/failure tests, ID real transfer/update | Real interruption/Range resume/storage exhaustion/higher-revision rollback and stale assessment rejection | Controlled cache, approved test data | Existing generation survives failure/offline; 136MiB compressed/232MiB decoded plus generations budget honest | P1 / A/B |

### 8. Release readiness and real-device acceptance

| ID | Requirement / authority | Evidence | Status | Outstanding work | Depends | Acceptance | Priority / phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| REL-01 | H: source integration distinct from release | E01/E02 | IV main integration | No remerge of historic stacks; verify installed provenance when testing | None | Current main branch/PR base recorded; code220 preserved | P1 / retained |
| REL-02 | H: owner device acceptance | E09/E10/#194; supplied project reports | PI anecdotal exercised beta, no full signed matrix | Record Pixel10/Android17 and Samsung/Android16 where available; source/data version and each scenario | A, then changed packages | UI/maps/modes/history/offline/achievements accepted individually; issue remains open if gaps | P1 / A/G |
| REL-03 | H: coordinated purposeful release only | Governance/#208 | NI new release; not requested | Reuse installed220 for baseline acceptance; later one approved build for coherent code slice; data-only update needs no APK | Approved packages + checks | Exact source/signature/artifact/rollback, explicit distribution approval; no automatic testers | P1 / G |
| REL-04 | H: honest readiness/issue state | E05 current stale issue bodies | PI | Review consolidated register and sequence; later update issues on approved acceptance, no closure now | Owner review | Separate bounded supported catalogue, whole-route attainability, optional research and mystery feature | P1 / B/G |

## Recommended ordered completion plan

These are focused future work packages. No implementation begins through approval of this documentation PR alone.

| Phase | Work package / deliverable | Dependencies | Acceptance gate / release impact |
| --- | --- | --- | --- |
| A | **Existing baseline acceptance first:** UX-01–04/07, JRN-01–03, ACH-01/04, QA-01–03/05, REL-02. Exercise existing220 rather than rebuild it | Installed baseline/current pinned data; existing full history | Record device/source/catalogue and each result; capture cold/warm/one-change diagnostics; label untested cases. No new APK |
| B | **Resolve scope and finite gaps:** CAT-03 attainability review, new-recording review/endpoints/aggregate interaction scope, ACH-05 disposition; targeted achievement/migration test plan | Register owner review; A may proceed independently | Approve bounded collection claims and focused C/D work; identify which historic requirements remain essential. No invented titles/tolerances |
| C | **Close essential Android gaps in one coherent slice:** JRN-05 supported section review, UX-05 metadata/unknown treatment, accessibility defects from A; optional UX-06 only if approved | B; verified alternatives/endpoints when available | Positive/negative review windows, no import prompts, retained camera/search/state, TalkBack; affected suites pass. One later purposeful APK if authorised |
| D | **Catalogue research/corrections in bounded batches:** open binding gaps first; alternatives/NCN228 and34 holds by evidence value; Named route scope separately | Source/licence verification; no dependence on mystery feature | Before/after pins, exact identities/geometry/modes, union and denominator migration tests; unresolved holds remain honest. Publication separately approved; no APK for data-only change |
| E | **Independent milepost/personality evidence package:** Kent physical verification method, historical fact verification, permitted sources, twelve-geography availability matrix; no biographies/assignments until later scope | MP-02–05/TIME-01; can run independently of C/D | Reviewed evidence confidence/precision/current status; Charlie attribution gate; supported opportunity coverage with explicit gaps; no unverified national catalogue |
| F | **Separate In Honour of Time implementation:** approved detector/reveal/persistence specification, stable IDs and deterministic versioned assignments; biographies/art only after evidence and content approval | E plus TIME unresolved decisions | Six “?” initial; first identity+five silhouettes; repeats/reimport/update/corrections consistent; all six parent; anti-drive-by negatives; no code puzzle or poetry |
| G | **Release acceptance/sign-off:** rerun affected regressions, device matrix and coherent private build only when authorised | Accepted C/D or F slice | Evidence-backed source/build/data/deployment states, rollback and distribution approval; leave unrelated research holds visible |

Essential for a reliable bounded NCN collection: A, B and accepted essential C issues, honest CAT-03 limitations and core achievements/migrations. Full published-route completeness additionally depends on D's evidence; cannot be claimed from219 receipts. Named extensions and unresolved route admissions can progress independently in bounded batches. Extra achievement ideas are optional. E/F are a separate feature and do not block fixing the existing collection.

No major architecture rewrite is proposed. Preserve the indexed SQLite catalogue, exact source matcher, physical interval ledger, shared focused map and existing achievement/celebration model. Optimize only measured problems. Do not restart historical stacked PRs, repeat completed Firebase migration or create one APK per task.

## Explicit evidence gaps and decision queue

1. **Full historical conversations unavailable:** connected retrieval returned dated excerpts, not complete transcripts/permalinks. The 48/56-fragment contradiction cannot be fully reconciled; both are superseded. No missing names/thresholds are invented.
2. **No complete named NCN achievement catalogue beyond recovered families/proposals:** E16 is default/free scope. To add further historical requirements, supply the original approved specification/message or identifiable issue comment.
3. **No accepted milepost point ledger / completed Kent pilot:** candidates, photo correspondences and official schema were recovered; independently approved current position/existence/access records were not. Need dated independent evidence and reviewer approval, not an assumed map centre.
4. **No permission grant for official milepost audit / confirmed complete KML or checker CSV:** provide original permission/data with provenance if available. Do not infer rights from public access or route-layer OGL.
5. **No real-device signed acceptance matrix for current NCN slice:** prior beta usage and performance improvements are reported; automated tests/runner timings do not settle cold/warm performance, zoom styling, accessibility, update or celebrations on both devices.
6. **Charlie Harrow attribution:** reproduced specific historical release supports the selection; original authoritative provenance is still a content publication gate. No further biography facts should be inferred.
7. **Current installed source, user cache generation and live backend version:** not read from phones/Render in this documentation task.220 is the supplied baseline; source provenance is E01. Supported matcher contracts were inspected without assuming all real journeys satisfy them.
8. **Product review needed:** historic disc themes/design awards retained separately or retired; named-route scope; section review acceptance; verified endpoint handling; aggregate chart interaction; achievable full-completion claims; TIME detector/history/reveal/migration details. These are a review agenda, not repeated permission prompts.

## Audit validation and delivery state

Fresh read-only data receipt and source-test results are in E04. Main's existing validation records include810 Android tests/109 suites and333 NCN/reference checks at the reconciled baseline; these historical Android results were inspected, **not rerun here**, and prove no phone acceptance. Fresh research/reference checks are identified separately in the receipt. The documentation review checks link existence, register IDs and docs-only diff.

Only this register and its small audit receipt are proposed for Git. No Android/backend runtime, geometry, route identity, production data or release workflow is changed. No publisher, backend deployment, APK build/distribution, branch deletion, historical PR merge or issue closure is performed. Documentation PR targets main and remains open for review. Next step: review this register together and approve the delivery sequence.
