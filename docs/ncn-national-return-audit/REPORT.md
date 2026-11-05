# National deficit reconciliation and evidence

[Executive decision](DECISION.md) · [machine results](results/results.json) · [validation](results/validation.json)

## Baseline and scope

Authoritative main is `24278f5256bf367039b9a3adf986d619754ccb15`, the merge of #276; its first parent is the #275 merge `15ca06d26d045a16583d44038ae6c9fe17778cd7`. Both merges were independently read through GitHub and the public checkout. Previous PR discussion entries saying “unmerged” are historical.

A fresh 1,079-byte manifest read on 5 November remains revision 4, SHA-256 `f709361dfd5d8f87c0a8eda15ccb971e70f947e4ba7538c80b1922a500fc5d9d`. The retained original GitHub artifact 13357563539 was downloaded once, not from Firebase, and its gzip and decompressed database pins matched that fresh manifest. Only the 1,079-byte manifest was transferred from Firebase. No national dataset rebuild, matcher scan, source download or publication occurred.

| State | Measurable unique km | Assessable unique km | Unassessable unique km | Assessability |
|---|---:|---:|---:|---:|
| Published revision 4 | 17435.132494006 | 16146.369976630 | 1288.762517376 | 92.608243626372% |
| Offline #276 comparison | 17435.132494006 | 16148.498592780 | 1286.633901226 | 92.620452401678% |

The comparison reads the three pinned source rows and unions their already validated additional bindings **in memory**. It does not write a candidate database. Exact input path identity, source modes, immutable target/canonical mappings and all-route coverage union are checked. Recalculated national gain agrees with #276 to much less than 0.0001 m. Published and candidate data remain different delivery states. The #276 candidate itself is not published by this audit.

## Current root-cause classification: evidence boundary

A fully verified **underlying** semantic cause is not assigned nationally: **1,286.633901226 km (100% of the comparison deficit) remains unclassified at that standard**. That is an explicit unknown bucket, not recoverable distance. It includes known observed rejection/withholding mechanisms, whose exclusive residual accounting follows. In particular the deferred-policy mechanism is known, but the physical reason behind every hold has not been re-adjudicated.

Missing source geometry, route-designation/edition conflict, legitimate separate/inaccessible/ineligible geometry and a generic import/projection defect each have **unknown national kilometres, interval counts and route counts**. They are not zero and must not be added to the table below. They remain possible secondary causes within its historical envelopes. Distinguishing source absence from a failed admission requires a current complete source graph and per-section provenance; absent tags or paths are insufficient.

## Exclusive residual partition by historical mechanism

This is a **fresh revision-4 plus #276 subtraction through exact canonical mappings**, not a rerun of semantic classification. Each canonical position has one label consisting of its complete historical cause set. Mixed sets remain explicit, never counted twice. Region counts are partition pieces, not distinct geographical investigations; route counts overlap. Typical length is the median partition piece, including numerical slivers. Every category has **high confidence in accounting/provenance, low confidence as a complete current underlying explanation; recovery uncertain**. Deferred sections remain policy holds and are unjustified for automatic recovery.

| Historical mechanism | Unique km | % deficit | Partition pieces | Routes | Median piece m |
|---|---:|---:|---:|---:|---:|
| No admitted source correspondence | 617.160018619550 | 47.967026054 | 22,488 | 202 | 13.2685382 |
| Direction gate region | 426.307707985313 | 33.133567177 | 92,745 | 217 | 3.34059565 |
| Known way, finite span unadmitted | 100.438283343903 | 7.806282988 | 5,025 | 175 | 11.4758347 |
| Endpoint/alignment region | 69.755504941807 | 5.421550363 | 39,717 | 218 | 6.97800869e-06 |
| Target ambiguity region | 63.668797495129 | 4.948478152 | 116,283 | 217 | 0.03591202 |
| Deferred binding withheld | 9.303583383648 | 0.723094843 | 463 | 49 | 8.28877574 |
| unclassified_projection_residue | 0.000005403851 | 0.000000420 | 137,278 | 217 | 3.55271368e-15 |
| ambiguous_target_correspondence + direction_gate | 0.000000027758 | 0.000000002 | 5 | 4 | 6.57532811e-06 |
| ambiguous_target_correspondence + finite_endpoint_or_alignment | 0.000000024693 | 0.000000002 | 3 | 3 | 6.94247995e-06 |
| direction_gate + finite_endpoint_or_alignment | 0.000000000000 | 0.000000000 | 7 | 5 | 7.20385174e-15 |
| **Total** | **1286.633901225652** | **100** | — | — | — |

The partition sums to 1,286,633.901225652 m within 0.0001 m, solely an arithmetic assertion tolerance. It is not a geometry/admission tolerance. The 5.404 mm unclassified projection residue includes 188 canonical micro-edges with no target mapping (5.394 mm); no road absence is inferred. Tiny positive lengths are retained in national accounting. For size/graph measurements only, fragments at most 1e-7 m are excluded: total omission **0.0000081982 m**. The historical Stage 1.1 route-local totals are not summed as unique-network kilometres.

## Fragment sizes and concentration

Canonical interval = maximal deficit interval within one immutable canonical edge. It is the authoritative unique accounting unit, but publisher segmentation splits continuous physical gaps. A second measure therefore joins gap intervals only at coincident projected edge endpoints (WGS84 rounded to eight decimals; endpoint equality tolerance 1e-7 m). No geographic proximity join is used. Connected components can branch and contain multiple causes; they are not proven single correction opportunities.

| Statistic | Canonical intervals | Connected components |
|---|---:|---:|
| Count | 225,706.000 | 128,636.000 |
| Median m | 0.543 | 1.174 |
| Mean m | 5.700 | 10.002 |
| 90th percentile m | 12.615 | 13.215 |
| 99th percentile m | 77.090 | 132.766 |

Threshold rows are cumulative and must not be summed.

| Threshold | Canonical count | Canonical km | Component count | Component km |
|---|---:|---:|---:|---:|
| below 10 m | 194,338 | 343.888398 | 109,964 | 221.623799 |
| below 50 m | 221,432 | 848.380377 | 125,443 | 500.524630 |
| below 100 m | 224,305 | 1047.907419 | 126,877 | 601.921646 |
| below 500 m | 225,685 | 1272.624866 | 128,393 | 903.946778 |
| below 1,000 m | 225,704 | 1284.559731 | 128,523 | 991.681880 |
| above 1,000 m | 2 | 2.074171 | 113 | 294.952021 |
| above 5,000 m | 0 | 0.000000 | 12 | 105.457297 |

| Largest N | Canonical km | % deficit | Connected km | % deficit |
|---|---:|---:|---:|---:|
| 10 | 8.021285 | 0.623 | 94.291667 | 7.329 |
| 20 | 13.502933 | 1.049 | 137.951676 | 10.722 |
| 50 | 26.527971 | 2.062 | 211.130529 | 16.410 |
| 100 | 43.227416 | 3.360 | 281.435221 | 21.874 |

The 12 components longer than 5 km contain **105.457 km**, a theoretical concentration, not a reusable 100 km correction. The largest single component is **14.872 km**. No component's common technical cause was verified and no geographical case was reopened. Cumbria designation was not investigated.

Endpoint precision sensitivity (7/8/9 decimals) yields 128,521 / 128,636 / 129,626 components; the largest 100 cover 283.267 / 281.435 / 263.018 km. Segmentation affects component counts and ranking, but not exact national kilometres or the absence of proven recovery. This range is material enough that component counts are model-based diagnostics rather than definitive physical road counts.

[Top 100 disjoint components](results/largest-clusters.csv) and [full interval register](https://chatgpt.com/library?file_id=libfile_b87229b9ccfc819180fb4569a9e7f978&account_id=personal) retain their canonical edge identities. Component cumulative totals are theoretical maximum exposure if every included gap were resolved; they are not expected recoverable kilometres.

## Top 20 numbered routes

Canonical gaps are allocated equally across route owners of each canonical edge; the allocated column is additive nationally. The accompanying associated-network column in CSV may overlap and must not be summed. This is an accounting convention, not a route attribution correction. Unmapped numerical residues have a separate row. Route-local geometry remains a different measurement basis.

| Rank | Route | Allocated unique deficit km |
|---|---|---:|
| 1 | NCN:1 | 138.580017 |
| 2 | NCN:4 | 74.538538 |
| 3 | NCN:6 | 65.616571 |
| 4 | NCN:7 | 49.405884 |
| 5 | NCN:5 | 47.480442 |
| 6 | NCN:62 | 38.427035 |
| 7 | NCN:2 | 35.270152 |
| 8 | NCN:8 | 26.627613 |
| 9 | NCN:517 | 23.782721 |
| 10 | NCN:51 | 21.425740 |
| 11 | NCN:10 | 21.230556 |
| 12 | NCN:3 | 20.940205 |
| 13 | NCN:67 | 18.815219 |
| 14 | NCN:45 | 18.635987 |
| 15 | NCN:66 | 18.364852 |
| 16 | NCN:41 | 18.300038 |
| 17 | NCN:55 | 18.279596 |
| 18 | NCN:68 | 17.764226 |
| 19 | NCN:81 | 15.273594 |
| 20 | NCN:82 | 13.311437 |

## Bounded systematic-opportunity experiment

The existing 47,869-row national screen is reused without another spatial search. Its exact finite target intervals are projected through revision-4 target-to-network rows, unioned across routes and intersected with the candidate deficit. This replaces the old geodesic proxy with canonical accounting. Flags overlap and must not be summed.

| Diagnostic subset | Residual unique km | Interpretation |
|---|---:|---|
| all_screened | 611.456959 | Any retained nearby same-route source edge; discovery only |
| admitted_edge | 396.235222 | Screen edge falls inside a pinned admitted source section |
| unadmitted_edge | 215.221737 | Screen edge outside pinned admitted finite section |
| historical_direction_gate | 283.516020 | Screen inside old direction-rejection region |
| admitted_max_separation_2m | 34.879756 | Admitted finite edge; all five samples within 2 m |
| strict_geometry_single_option_admitted | 1.716828 | Previous condition plus <=15 degrees and one screened option |

The final screen is deliberately conservative and is **not a technical acceptance criterion or estimator**. It rejects many potentially valid correspondences and can still contain invalid attribution. Five samples do not prove continuous corridor containment, unique target ownership, endpoint order or adequate source station length. “Single option” means single source edge in the earlier bounded screen, not globally unique topology. The 1.717 km is unverified, not an eligible gain; it also does not cap all possible recoveries. The admitted and unadmitted subsets can overlap on canonical network despite finite source-edge flags.

There are **675.177 km outside the retained screen**. Its cause is unknown; it is not a measured missing-source category. An existing large category cannot serve as evidence for a generic fix. Prior seam work already recovered 45.834 km; Deal's 0.562 km is already published. Neither is a new opportunity. Previous projection investigations established predicate differences but no national recovery prevalence. This audit did not extrapolate those examples.

## Ranked opportunity matrix and expected return

No candidate has enough evidence for a positive central/high recovery estimate or numeric expected km per Work hour. **Low = 0 km demonstrated; central/high = unknown**, for all three. “Unknown” is not zero and is not the diagnostic envelope. A credible ROI ranking is therefore unavailable; the ordering below is by readiness of existing evidence and cheapest possible falsification, not a fabricated economic ranking. All three are parked under the owner's thresholds.

| Rank / proposed intervention | Exact problem/mechanism status | Affected unique km (diagnostic exposure) | Credibly recoverable low / central / high | Confidence | Engineering / validation effort | Expected km/Work h | Risk and recommendation |
|---|---|---:|---|---|---|---|---|
| 1. Finite same-feature representation/binding defect on already admitted geometry | Possible; no common defect proved. Direction rejection alone is insufficient. | 396.235 admitted-screen union; 34.880 within 2 m; strict subset 1.717 | 0 / unknown / unknown | High measurements; low recovery | Implementation not estimable without mechanism. A hypothetical proof cap would be 2–4 active Work h, validation 1–2 h. Not commissioned. | Unknown; no positive demonstrated return | Finite endpoint overreach, wrong branch, hairpin credit. **Park**; no 100 km supported generic rule. |
| 2. Incorrect finite-source admission exclusion | Unadmitted span is observed; systematic erroneous exclusion is not established. Legitimate separate paths and edition holds remain possible. | 215.222 unadmitted-screen union across all historical causes; 100.438 in exclusive known-way historical region | 0 / unknown / unknown | High accounting; low entitlement | Implementation unknown. Hypothetical proof 4–8 h plus 2–4 h validation, depending on retained admission evidence. Not commissioned. | Unknown | Whole-way expansion, access/mode transfer, edition mismatch. **Park**; requires individual evidence unless a reusable predicate defect is proved. |
| 3. Missing-source import / reference edition reconciliation | No national active-graph comparison; cannot distinguish missing geometry from unadmitted/shifted/wrong-edition geometry. | 617.160 exclusive no-correspondence region; not a measured missing-source total | 0 / unknown / unknown | High envelope; low cause/recovery | Import implementation and validation not estimable. Even proof could exceed 8–16 h plus national validation; lowest confidence budget. Not commissioned. | Unknown | False route identity, changed edition/denominator, mode eligibility and broad regressions. **Park**; disproportionate new evidence acquisition. |

Effort ranges are planning judgement for hypothetical proof work, **not measured usage, promised implementation estimates or approved follow-ups**. The app's allowance meter is unavailable. No central recovery percentage is assumed from a convenience sample. The three affected envelopes overlap, so no combined kilometre opportunity is quoted. Missing importable footpaths/cycleways, consistent publisher displacement, ordinary seam direction failure, endpoint projection defects and withheld sections were considered as hypotheses; none has an independently established national safe recovery estimate here.

Any future generic implementation would need exact source/target interval identity, permitted modes, finite source stations, ordered connected endpoints, target ownership, crossing/hairpin/branch negatives, denominator stability, every-row preservation, canonical union delta, Android ledger regression checks and independently pinned artifacts. Reference publication would remain separately authorised with existing integrity, transfer and rollback gates. This audit proposes no implementation.

## Stop decision and limits

**STOP — poor currently evidenced return.** Keep broad NCN assessability work parked. The observations do not support GO's >=100 km credible reusable recovery requirement. CONDITIONAL is not selected merely because a 100+ km review envelope exists: no exact shared bug or supported recovery forecast has been found to justify another proof session. This does not prove a generic defect is absent; it states what the available evidence can support.

The national semantic root-cause deliverable is incomplete by design at the bounded stopping point. Resolving it would require independently established route/access/edition/admission evidence that the stored classifier and screen do not provide. Source geometry can exist while the route correspondence is legitimately absent. The audit reports that limitation instead of relabelling unknown kilometres as safely recoverable, legitimately excluded, or physically missing.

No matching thresholds/algorithms, source admission, published bindings, journey/completion/achievement/entitlement records, Android assets or backend services changed. No national map, external research campaign or geographical correction programme was started. Source Android baseline remains 0.25.223 (269); actual installed version, live backend commit and device uptake were not inspected because this is offline reference analysis. Device acceptance is not claimed.
