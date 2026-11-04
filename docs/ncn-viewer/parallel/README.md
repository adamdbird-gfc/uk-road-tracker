# NCN parallel corridor discovery — 4 November 2026

## Decision

**There is a substantial geometry-review opportunity, but this session does not establish a quantified national opportunity for distinct parallel-corridor equivalence.** Do not implement a national tolerance increase or award additional completion from this register. Proceed with a small evidence investigation of the NCN92 and NCN7 examples and human review of Deal's genuinely separate alternatives.

The single national retained-source screen found **47,869 candidate gap pieces / 613.303 km gross route-local**. This includes same-feature representation, direction and source-admission problems. It is neither 47,869 distinct corridors nor 613 km of recoverable unique network. Of the queue, 283.379 km belongs to historical direction-gate regions; 115.846 km has mean sampled separation ≤2 m. Neither proves separate infrastructure. No new high-confidence national equivalent was established.

The bounded search deliberately reuses existing evidence rather than downloading a national OSM graph or rebuilding/downloading the NCN catalogue. It searches full-way coordinate contexts associated with the same route, checks finite admitted-section containment separately, retains one nearest qualifying source edge per gap, and exposes alternatives/counts. It **cannot discover arbitrary nearby roads/footpaths absent from those retained associations**. This is a completed bounded screen with a partial answer to the wider policy question, not an exhaustive national equivalence audit.

| National queue classification | Pieces | Gross route-local km | Meaning |
| --- | ---: | ---: | --- |
| High confidence | 0 | 0 | No distinct new public alternative independently established |
| Requires review | 38,608 | 396.125 | Candidate edge within a pinned admitted section; equivalence still unproved |
| Rejected | 0 | 0 | National source export is prefiltered; negatives are separate controls |
| Unresolved | 9,261 | 217.178 | Candidate edge outside the pinned admitted finite sections |

The review pack additionally includes **three high-confidence existing positive controls**, two rejected controls, and unresolved/review examples. These control labels must not be mixed into the national candidate totals. The positives are existing designated-route/same-feature evidence, not newly approved parallel infrastructure.

## Authoritative baseline

- Main verified through GitHub and local checkout: `8c04b7eb1ac1bc8a6e8700ed19a14ba7ec34149c`, merge of PR #271.
- Issue [#265](https://github.com/adamdbird-gfc/uk-road-tracker/issues/265) remains open: owner policy requires public access, finite corridor mappings, route/mode/direction controls and canonical union accounting.
- Revision 4 publication: successful run [41901168581](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/41901168581); receipt in issue [#194](https://github.com/adamdbird-gfc/uk-road-tracker/issues/194), Hosting version `48588f75e92afd0b`. Only a fresh 1,079-byte manifest was read here; no catalogue bytes fetched. See `baseline-live-check.json`.
- Verified manifest DB SHA-256 `be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f`; canonical `d3486a38fc33a78dd9314720a4677906fa7315e28c15feedb1577a1847caac2d` from the retained receipt.

| Revision 4 measure | Value |
| --- | ---: |
| Measurable unique network | 17,435.132494006 km |
| Assessable unique network | 16,146.369976630 km |
| Assessability | 92.608243626372% |
| Remaining unbound unique network | 1,288.762517376 km |
| Already included Deal increment | 561.888736534 m |

These capacity figures come from the exact-candidate receipts and independently verified live identity, not a new national geometry recalculation or a user's completion. Phone adoption/physical-device acceptance remains unobserved. Android source baseline is 0.25.223 (269); installed version and live backend commit were not inspected because this work is offline diagnostics only.

### Remaining historical gap regions

The classified archive has 442,910 historical pieces. Exact seam and reviewed Deal target additions were subtracted once, using sorted target identities from preserved plans. This leaves **1,287.660429950 km route-local**. Route-local and unique-network metres use different projections/ownership models and must not be subtracted from one another.

| Historical cause region | Revision 4 residual route-local km | Screen queue gross route-local km |
| --- | ---: | ---: |
| No admitted source correspondence | 618.189314 | 147.400359 |
| Direction-gate rejection | 426.015034 | 283.379456 |
| Ambiguous target correspondence | 63.722439 | 45.350559 |
| Unadmitted span of known way | 100.729156 | 97.553072 |
| Endpoint/alignment | 69.682163 | 32.011509 |
| Deferred binding withheld | 9.322324 | 7.607792 |

These are exact residual lengths **within historical cause regions**, not newly determined semantic causes. The post-seam receipt's exclusive unique-network envelopes were respectively 618.865767, 426.410665, 63.668797, 101.320084, 69.755505 and 9.303583 km; those predate Deal's 0.561889 km correction. This session does not silently relabel them as a fresh revision 4 unique cause partition. A new canonical cause partition would require the retained target-to-network mapping export; it was not necessary for this bounded discovery screen.

The full CSV/JSON register and standalone HTML download links are in [REGISTER.md](REGISTER.md). Large generated registers are retained in the evidence artifact; restore them to `results/` before running the viewer generator/tests from a fresh checkout.

## Method and accounting limits

1. Restore the two preserved evidence archives, verify the classified stream SHA-256 `1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb` and record every consumed plan/association hash.
2. Decode and verify all existing seam delta chunks (`f98df308ccf4120939e705e498f6cfdbbb36373298060f29a230a88929e6957d`), translate sorted target indices, and add the exact reviewed Deal bindings. Subtract their union from historical intervals. No compiler or catalogue is executed.
3. Screen all 63,282 surviving pieces ≥5 m. Use a geographic bounding box only for discovery, then WGS84 local azimuthal-equidistant metres, five finite-segment samples, ≤40 m maximum sampled separation and ≤20° undirected heading. These are **queue selection limits, not proposed eligibility thresholds**. Shorter fragments remain in the denominator but are not screened.
4. Retain the deterministic nearest passing edge, tie-break by way ID/edge, count other options, preserve source keys/modes/hash, and classify finite-section containment. Endpoint gaps, ambiguity and private/access requirements remain unresolved unless independently established.
5. Deduplicate exact target coordinate chains and normalized target fractions across route identities. The resulting **614.835 km geodesic candidate proxy** changes the measurement basis from route projections; it is not canonical network recovery. Differently segmented overlapping chains are not fully resolved by this proxy. It may exceed the route-local gross because the metric basis differs.

No candidate kilometres are added together as unique recoverable network. No positive lower/central/upper recovery scenarios are justified. The **validated additional recovery is 0 m**; prospective lower, central and upper recovery estimates are **unknown**, rather than a fabricated fraction of the queue. The already-published Deal gain is excluded from new recovery. Canonical target-to-network interval union remains mandatory before any future estimate or implementation.

## Twelve map examples

Open `Roadprints_NCN_Corridor_Review.html` directly. It embeds Leaflet 1.9.4, source review data and real WGS84 line geometry. It requires no server, install or permission. The online OSM basemap starts enabled; switch it off for an entirely offline geometry map. No tiles or aerial images are bundled. Search, case selection, fit/close comparison, pan/zoom, layer toggles, segment taps, coordinate copy and OSM/aerial links are included. Missing current per-edge assessability in older controls is labelled instead of painting the entire source as eligible. The designated-route and private-control layers are independently selectable; small separations become clear with **Close comparison**.

On Android: download the HTML, open it from Downloads with Chrome or another modern browser. An HTML-preview app may intercept the file; use **Open with → browser**. Offline route lines remain available if map tiles cannot load. Physical Android opening has not been tested; Chromium mobile-size acceptance is recorded separately.

| Stable case | Location / pattern | Review result |
| --- | --- | --- |
| REF-deal | Deal NCN1 designated cycleway | Existing exact correspondence; 561.889 m already in revision 4 |
| NEG-DEAL-PRIVATE | Wellington Parade, Deal | Reject private parallel road despite close alignment |
| REF-44807950 | Wolverhampton NCN81 hairpin | Reject automatic equivalence between nearby return arms |
| REF-59306268 | Newry Canal Towpath NCN9 | Existing same-feature positive control |
| REF-31252441 | Mid-Wales NCN8 road | Existing on-road positive control |
| REF-1050757473 | Mudlarks Boulevard, London NCN1 | Finite prefix admission must not extend by whole-way ID |
| REF-159311690 | Bradford NCN66 | Direction discrepancy; no automatic tolerance increase |
| REF-23385711 | Cumbria NCN6/7 | Route identity unresolved; no alias by geometry |
| REF-1289002662 | East Sussex RCN90 | Historical node movement; no target recovery inferred |
| NAT-01 | NCN92 cycleway near Derry, way 30268009 | Substantial 2,054.445 m gross review group; designated foot/bicycle tags support investigation, not approval |
| NAT-02 | NCN7, Highland C1128, way 463641622 | 820.941 m gross review group; road/target representation and finite connectivity need review |
| NAT-03 | Chapel Lane, near Kingsdown, NCN1 | 8.883 m connector/direction review; small proximity case |

The group lengths can include unadmitted source-edge candidates and multiple target pieces. They are not claims of contiguous or recoverable corridor length. Precise coordinates, evidence and candidate IDs are embedded and in `review-data.json`.

### Deal facts versus interpretation

**Established:** 4471747 is a designated shared public cycleway, bicycle/foot designated, NCN1 relation 75880 in the preserved review. Sections 0–6 and 11–13 were originally admitted; edges 6–10 have the exact reviewed correction now in revision 4. The 561.889 m is already accounted for; the remaining 3.243944 m endpoint was not admitted by that correction. See the existing Deal resolution and review pins.

A bounded fresh OSM context query finds Wellington Parade 77611523, residential, **access=private**, version 8, approximately **841.320 m** long. Local UTM31N gives minimum separation 0 m at the shared node, sampled maximum about **4.567 m** to the cycleway; there is one shared node. Its private tag, different identity and unproved corridor entitlement reject it. This illustrates why a minimum distance or one shared node is inadequate. Measurements use the full observed ways, not a claimed eligible target span.

**Interpretation:** the owner’s experience of adjacent usable paths remains a valid policy motivation, but the queried context does not establish an independently approved public road/footpath alternative alongside this exact reviewed stretch. Some surrounding footways carry `bicycle=no`; one nearby cycleway carries `foot=no`. Access is mode-specific. The context export retains highway ways/required nodes/bicycle relations, not building/address detail. No alternative is admitted from this observation.

## Draft policy and next steps

See [POLICY.md](POLICY.md). Review identity first, then access by mode, ordered endpoint connectivity, finite corridor span, lateral/heading measurements, barriers and false-positive controls. Do not pick a universal lateral threshold from this sample.

1. **Additional source investigation:** validate NAT-01’s 12 pieces and NAT-02’s 35 pieces against original admitted finite sections, public access, target generalisation and exact connectors. They may be exact-route representation cases rather than alternative infrastructure.
2. **Human review:** use the viewer to identify an actual separate public Deal footpath/road worth approving; preserve private Wellington Parade as a negative. Do not convert the owner’s general corridor decision into approval of all nearby geometry.
3. **Compact missing input:** export only candidate target-to-canonical-network mappings plus complete per-section source/access/topology provenance from the retained revision 4 artifact, without republishing/downloading it from CDN. Then compute union across approved finite target intervals and activity modes.
4. **Implementation decision:** defer general corridor equivalence. A later narrow implementation needs explicit reviewed alternative-to-target mappings and positive mode-specific evidence. Retain source-version and direction discrepancies as separate workstreams.

## Validation and delivery

- 13 focused Python tests pass: stable IDs, interval union/subtraction, metric/geodesic checks, retained evidence/classifications, finite section containment, selected source measurements, residual totals, negative controls and viewer/source geometry.
- Offline Chromium desktop/mobile browser acceptance is preserved in `validation.json`; physical Android remains untested.
- Source production files are unchanged: all additions are beneath `docs/ncn-viewer/parallel/`. No runtime imports, matcher/calculation/achievement changes, production catalogue writes, Firebase/Render deployment, APK, journey edits or awards.
- Required evidence is preserved as compressed JSON/CSV, summary, source observations, scripts, policy, viewer and validation receipt. Archives are external preserved inputs, not re-added to Git. Input hashes are in `results/national-summary.json`.
- Issue #265 stays open for evidence review and the unperformed public-alternative/canonical-mapping work. This discovery cannot honestly claim the wider national-equivalence question is fully resolved.

### Reproduce without a catalogue rebuild

Use Python 3.12, Shapely 2.2.0 and pyproj 3.8.0. Restore `roadprints-ncn-remaining-gap-classification.zip` and `roadprints-ncn-completion-checkpoint-2026-10-19.zip` from the preserved evidence. The latter’s full content is not a new national OSM download.

```sh
python docs/ncn-viewer/parallel/discover.py --inputs CHECKPOINT_DIRECTORY --gaps classified-intervals.jsonl.gz --output OUTPUT_DIRECTORY
python docs/ncn-viewer/parallel/build_review.py
python docs/ncn-viewer/parallel/test_discovery.py -v
NCN_CHROMIUM=/path/to/chromium node docs/ncn-viewer/parallel/browser-test.cjs
```

The generator uses committed frozen OSM observations; it only fetches a selected way if its observation is absent. Stable IDs derive from route, target, finite offsets, source way and edge. Output ordering is deterministic. A second full scan was deliberately not performed; selected measurements and all IDs/classification contracts were independently checked. The original single scan received a deterministic finite-section metadata refinement, now included in `discover.py`; this refinement performed no second spatial search. Gzip exports use mtime=0. Decompress JSON/CSV to compare logical output.
