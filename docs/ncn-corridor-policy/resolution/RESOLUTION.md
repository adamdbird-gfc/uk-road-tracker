# Five NCN corridor cases: finite resolution and publication readiness

5 November 2026. Issue [#265](https://github.com/adamdbird-gfc/uk-road-tracker/issues/265).
Base/main `15ca06d26d045a16583d44038ae6c9fe17778cd7`, including merged #275.

**Bradford and Derry have finite corrections implemented as an opt-in offline candidate. Highland and Wellington Parade have no justified road-to-path correction. Cumbria remains an evidenced designation/edition conflict; dual-route credit is unsafe.** The combined candidate preserves Chapel Lane. No national rule, production matcher, live entitlement or published revision changes.

## Updated resolution matrix

| Case | Resolution and new evidence | Finite outcome |
| --- | --- | --- |
| Bradford NCN66, 159311690 | Current publisher explicitly marks **all three junction arms** Open Main NCN66. Every reviewed revision-4 target vertex is identical at stored precision. Current OSM NCN66 relation 17883 v849 includes the source and its south/northwest neighbours. The cycle.travel GPX selects the northeast itinerary; it is not evidence that another publisher-designated branch is ineligible. | **Correct south/northwest only.** Seven new linear bindings fill the two residuals, **62.114800 route-local m**. Existing three bindings survive. Northeast target `2a8582…` receives nothing from this way. The national 15°/10 m gates remain unchanged. |
| Highland NCN7, 463641622 | Transport Scotland's **November 2021** appraisal, §§2.5.18–24, distinguishes on-road P3 in the village from a separate existing NCN7 path P4 south of Deshar Road, beside the road for about 1.2 km before the A95. June 2024 Kinveachy materials corroborate its off-road continuation towards B9153. Current publisher TrafficFree SegmentID **4725** meets OnRoad **29534** at **[-3.76569304123181,57.2494588637334]**. | **No carriageway admission justified for held edges 0–13.** The current road membership and GPX trace conflict with the separate path evidence. Preserve 14:16 and both existing bindings. A future exact source survey/import of the separate cycleway would be a different correction; its OSM identity is not established in the local capture. |
| Derry NCN92, 30268009 | A dated **14 June 2021 operator account** identifies NCN92 from Craigavon Bridge along the former railway to the Donegal exit beyond Carrigans Point. The current numbered-route page explicitly starts the Derry section at Ballougry Road. This exact way starts on Ballougry Road and continues uninterrupted to admitted 1157895911. Current Open Main TrafficFree SegmentID **40537** matches all reviewed revision-4 target vertices. The withheld indices are extent-buffer failures, not evidence of de-designation. | **Finite correction implemented.** All three admitted screen pieces and nine unadmitted screen pieces are covered separately in the audit. Twelve new linear bindings add **2,058.863720 route-local m**. No reclassified road approach is restored. The 2,054.445413 m screen omitted 4.418307 m at two finite target ends; full verified correspondence recovers these too. |
| Cumbria NCN6/7, 23385711 | Both original GPXs were recovered again and match their preserved SHA-256 values. Reproduced **142 identical undirected rounded segments / 14,261.858200 geodesic m**. Current OSM relation 11017 v1517 labels the disputed way NCN7. The fresh authoritative query confirms Open Main **20314 = NCN6 southbound**; **20313 = NCN7 eastbound**, with `LinkNo=6`, not a second `RouteNo`. The operator's general Route 6/7 descriptions do not establish exact shared membership here. | **Unresolved exact designation conflict; no safe dual grant.** No paths row added to either route and no canonical alias. Needed: an edition-qualified operator map/section record explicitly resolving whether this exact 1.04 km road is NCN6, NCN7 or both, including nodes 244056391–244056344. Existing route-local/shared-network accounting already supports two routes when both are verified. GPX overlap cannot supply the missing designation. |
| Wellington Parade NCN1, 77611523 | Dover's Sustrans-authored audit, revision 1.0 **21 September 2020**, Link L2/L2.5, distinguishes the gravel private road from the adjacent surfaced shared path; proposed widening/surfacing requires landowner consent. The local graph establishes south shared node **913199040** only. North road node **913199043** differs from cycleway **978142881**. Cecil Road joins the landward road, not a second cycleway connector. | **No finite two-ended equivalence justified.** The northern endpoints are near, but neither physical identity nor a second verified connector is established. Legitimate road travel remains independent of NCN attribution. Preserve published 4471747 correction and give no duplicate credit. Private access alone is not the reason for this decision. |

[Research receipt and source links](research.json), [compact local OSM identities/topology](sources/osm-local.json), [fresh publisher queries and request URLs](sources/urls.json), [exact GeoJSON diagnostics](diagnostics.geojson). Original twelve-case evidence, owner decisions and negative controls remain in place. There was no repeat national discovery. The 30 m local OSM selection is a diagnostic export only, never an eligibility threshold.

## Exact correspondence and connectivity

[Immutable review](review.json) pins complete revision-4 source rows, source hashes, ordered nodes, modes, old bindings, target coordinates, lengths and canonical mappings. [Reproducer](build_review.py) rebuilds the review from the preserved exact rows; the compiler pins its SHA-256. These are two named reviews, not a configurable national exception.

**Bradford:** Source stations [5.751157516486935,62.81209959460458] map in order from south target `217a655…` station 54.08486405222375 to 0. Stations [66.36952488511531,74.8482935767978] fill northwest target `c7286ee…` from 24.745119328441977 down to 16.71518299677999. Split at every physical vertex. The old intervening/adjacent bindings retain ownership and provide continuity. The source is a continuous path from Channing Way (160017281) to connected 674543498; its real northeast branch is distinct 200365698. All target graph arms remain untouched. The continuous affine correspondence bound is **6.939286 operational m**. The original ordinary 15° rejection still reproduces.

**Derry:** Historical held indices are 0,10–12,21–24; admitted source sections were 1:10,13:21,25:26. The correction uses the exact existing public shared cycleway, never another road. End connections are Ballougry Road 77637821 at node 851476033 and cycleway 1157895911 at node 4965938235. New finite bounds are [0,15.446494], [512.515775,522.984099], [527.470155,536.734591], [544.681583,550.565734], [684.717151,1817.335441] and [2746.004279,3643.851873] in stored source stations, split at every source vertex and target transition. Existing 40 bindings remain unchanged; the next target's existing ownership is never consumed twice. Target 591a4b7… is clipped at **99.75907687611061**; its remainder beyond this way is not granted. The continuous affine bound is **10.551448 operational m**. This identity-specific review does not enlarge the national 10 m buffer.

For every new segment, the source and target are linear between the pinned anchors. The norm of their affine point difference is bounded by endpoint norms. Splitting at every source bend therefore proves the whole correspondence, rather than extrapolating from sampled nearness. Partial source travel produces proportionate target intervals. Canonical union clips and deduplicates repeats, modes and other routes. Geometry accuracy is not claimed to be survey grade.

## Before/after assessability

These are synthetic catalogue capacities, **not personal completion awards**. The independently audited candidate modifies exactly three `paths` rows; all routes, nodes, targets, canonical network, meta and overview rows are identical. The complete audit considers every source and every numbered route contributing to the affected canonical edges.

| Measure | Published revision 4 | Combined offline candidate |
| --- | ---: | ---: |
| Unique measurable network | 17,435,132.494006 m | unchanged |
| Assessable unique network | 16,146,369.976630 m | 16,148,498.592780 m |
| Unique assessability | 92.608243626372% | **92.620452401678%** |
| Added unique-network capacity | — | **2,128.616151 m** |
| NCN66 route-local capacity | 197,206.679741 m | 197,268.794541 m |
| NCN92 route-local capacity | 13,428.097460 m | 15,486.961180 m |
| NCN1 route-local capacity | 1,805,826.197680 m | 1,805,835.080358 m |
| Newly fully attainable routes | — | **0** |
| Live completion / entitlement change | — | **0** |

Chapel Lane contributes its existing **8.882678 route-local / 9.155570 unique-network m**, once. Newly justified Bradford/Derry corrections contribute **2,119.460581 unique-network m** together. Deal's already-published 561.888737 m is not added again. National totals add the independently measured canonical delta to the retained original seam + published Deal capacity receipts; this is not a fresh national rebuild. [Exact measurements and hashes](impact.json).

## Validation and reproduction

```sh
python -m unittest scripts.test_ncn_five_case_correspondence scripts.test_ncn_chapel_correspondence -v
PYTHONPATH=. python docs/ncn-corridor-policy/resolution/build_review.py
python -m scripts.ncn_five_case_correspondence --database ORIGINAL_REVISION4_DB --output /tmp/five-case.db
PYTHONPATH=. python docs/ncn-corridor-policy/resolution/verify_candidate.py --before ORIGINAL_REVISION4_DB --after /tmp/five-case.db --output /tmp/five-impact.json
```

Source database SHA-256 `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`, recovered from original GitHub artifact 13357563539/run 41900022422. The complete gzip and expanded hashes matched; no CDN catalogue download was needed. A fresh 1,079-byte live manifest read confirms unchanged revision 4. Installed APK, phone catalogue uptake and live backend commit were not inspected; no runtime/deployment changes depend on them. Source Android baseline remains 0.25.223 (269).

The dedicated workflow reconstructs the candidate from the original artifact, reproduces the exact original-row export, performs every-row and canonical-union verification, runs real Android matcher/ledger consumers with walking/running/cycling, driving, partial/repeat/edit/delete and rejected-road controls, and preserves candidate/audit artifacts. It has read-only permissions and no publication credentials. See VALIDATION.md for final-head results.

## Consolidated publication readiness

**Candidate ready for review after green final-head CI; publication is not authorised by this task.** Chapel Lane is included in the single composed candidate. Bradford/Derry require no consumer format change or Android release. Highland, Cumbria and Wellington are excluded, with their exact conflicts retained.

A future publication must assign a **new revision and immutable manifest**, reverify the approved candidate bytes and all existing quota/transfer/rollback gates, preserve revision 4 for rollback, and separately verify on-device uptake. The existing revision-4 manifest must never be overwritten to point to this candidate as revision 4. Source integration alone grants none of this. No historical replay, live entitlement recalculation or retrospective unlock is performed.

Source branch `investigate/ncn-five-cases-20261105` targets the verified main above. No merge, backend deployment, Firebase publication, APK packaging/distribution or device acceptance in this session. Issue #265 remains open for review/integration, the Cumbria designation dependency, and separately authorised publication/acceptance. No reopening of settled owner policy is requested.
