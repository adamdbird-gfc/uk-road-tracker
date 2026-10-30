# NCN Stage 1.1 — offline source provenance and compatibility diagnostics

30 October 2026. Development baseline: `8dea49475e287bfefad7fa93b75e201e94a44230`. Android source baseline remains **0.25.220 (266)**. This diagnostic introduces **no NCN completion credit**, reference-data changes or runtime behaviour changes.

## Outcome and limits

Nine distinct OSM features examined in nine bounded comparisons: **seven exact geometry matches, one same-feature documented geometry update, one incompatible private parallel-road correspondence**. Two unchanged positive controls need no further geometry review; seven comparisons remain in the conservative review queue because of source change, identity, access, topology or extent limitations. These are review leads, not eligible target intervals or recoverable kilometres.

East Sussex way **1289002662** is the one confirmed **geometry snapshot mismatch**: histories explain movement of shared nodes **6925348794** and **6925348793**, with the third node unchanged and the way node chain retained. The maximum corresponding-vertex displacement is **1.505188306 m** in this tool's local projection, agreeing within 0.000001 m with PR #244's 1.505187538 m. History-compatible old node versions are 2/4/2 and new versions 3/5/2, and the relevant edit is 7 October 2026. **No original imported way/node version was recovered.** Way history v1/v2 and current v2 are observed history; import versions remain null. The earlier September reference load and changed node coordinates explain the disagreement without inventing metadata.

This exposes no previously unrecognised eligible correspondence. It makes the already documented update and existing controls reproducible. The RCN90 source has zero existing bindings; a note proposing NCN90 membership does not confirm it. Neither the small displacement nor stable way ID creates entitlement.

## Research integration and baseline

PR [#244](https://github.com/adamdbird-gfc/uk-road-tracker/pull/244) was open, mergeable, targeted main and had exactly expected head `bfe31f6715780908bd8b2a24ad6ea982ba2e79cf`. Its only four changed files were:

- `docs/ncn-post-seam-reconciliation-20261030.md`
- `docs/data-trials/ncn-post-seam-reconciliation-20261030.json`
- `docs/data-trials/ncn-post-seam-source-provenance-20261030.json`
- `docs/data-trials/reconcile_ncn_post_seam_20261030.py`

Independent contents reads verified these files and the read-only reproduction script. The network partition sums to **1,289,324.4061127824 m**, matching **1,289,324.406113 m** at reported precision. The **0.0054038506514915 m** network numerical residue and **0.000009284354746341705 m** route-local residue remain explicitly unclassified. No national assessment was repeated to verify receipt arithmetic.

The authorised GitHub merge used `merge_method=merge`, the expected head lease and `[skip ci] [skip render]`. The merge commit and independently read main both resolved to `8dea49475e287bfefad7fa93b75e201e94a44230`, with parents `668292c306dd937c3a424164ea9c75f934751401` and the expected PR head. All four merged Git blobs were independently read back unchanged. The integration receipt records their exact blob IDs.

A short-lived branch, `diagnostics/ncn-source-compatibility-20261030`, started directly from this main. Scope checkpoint `3fe422c2b145221adbf3fc6c5aea51e0a50f8482` was committed and independently read back before implementation. Implementation/test checkpoint `7cff4e37a3a5db58a0f54178fea6a953484151e2` was also independently read back before final receipts. Final changes and review location are the development PR; it must remain open and unmerged.

## Existing provenance audit

Inspected pinned evidence from #239 `22ff0c320b40327c878278aaed1218f383d48e99`, #240 `21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7`, #241 `24428cb4ad35e563113d7a86f7edc13652dc2844`, #242 `7179c7b9a837a2255a85b58693e0b4188d3cc864`, #243 `ef4d229368ad517ddac2ebbe2c7b6621a65e8991` and #244's expected head. #240/#241 remain evidence, not integrated development dependencies. Their classification witnesses and shared-matching report were read without invoking their national analysis.

| Area | Already recorded | Missing or limited |
|---|---|---|
| Walking import: `pedestrian_reference.py::WayCollector.way` | OSM way ID, compact full-way coordinates, highway/name/ref/surface/foot/access tags; source key and source label | Original way/node versions and ordered node IDs are discarded; bicycle tags are not retained by this importer. Walking presence cannot prove cycling permission. |
| Import receipts: `reference_data_loads`, `reference_sources` | Regional PBF file SHA-256, load/retrieval timestamps, dataset/source keys, row counts | `latest` is a label, not a feature version. PBF replication/extraction timestamp is unavailable. The legacy national `uk` loader uses URL-plus-byte-size SHA-256 as an idempotency marker, **not file-content SHA-256**. Kent has two receipts; the adapter preserves both without selecting an edition. |
| Walking evidence: `matched_path_evidence.py` | Source namespace, feature ID, access tags, consumer-normalized geometry hash and payload checksum; exact returned geometry/spans | Content checksums ensure integrity, not shared route membership or an authenticated signature. No original OSM versions can be inferred from them. |
| Cycling: `cycling_path_evidence.py`, `NcnMatcher.java` | OSRM bicycle provider/profile, ordered annotated OSM nodes, exact overview hash, uniquely aligned leg spans, nullable `data_version` | Node annotations are **not** overview vertex identities. Full original graph PBF/profile/version and way history are absent from these controls; no current bicycle graph comparison is claimed. |
| NCN source associations: `association_bundle.py`, `build_ncn_national.py` | Full source geometry, way/node identities where captured, source hashes, section keys, eligible modes, snapshot/membership checksums, reference digest, projection origin and target bindings | A geometry hash is not a source version. Original capture dates/OSM versions are not consistently recorded. Current API history can explain a compatible state, not recover discarded import metadata. |
| Source offsets and admission | PR #239 verifies section occurrence before translating local anchors to full-way positions; existing section modes, exact geometry, source-to-target 10 m/15° finite gates, ambiguity and deferred holds | Whole-way identity cannot extend section boundaries; geometry similarity cannot establish route admission. |
| Published target geometry | Separate publisher reference/target identities, coordinates, network mappings, reference hashes and fixed denominators | Publisher target and OSM source need not have identical segmentation or geometry. No target rewrite follows from diagnostic similarity. |
| Driving and road inventories | Existing OSRM matching and separately maintained canonical road/reference inventory artefacts; retained geometry and source receipts where present | These nine controls provide no pinned driving graph or complete settlement inventory provenance. The driving request path does not request cycling-style node annotations. Further source-specific exports are needed. |

The audit additionally compared **319 available existing Python/Java files** against the authoritative main Git tree: zero mismatches. The sidecar imports only the Python standard library and has no runtime integration points.

## Classification and output contract

Run `scripts.ncn_source_compatibility` with a specified frozen JSON snapshot. It reads both sides without modifying them and writes a separate JSON result (or stdout). The fixture adapter verifies four committed research inputs by exact byte SHA-256 and reconstructs only bounded controls. Existing admitted source hashes must match reconstructed geometry. Original feature versions remain null; observed October metadata is labelled separately.

Three separate assessments are emitted:

1. **Geometry/feature compatibility:** the classification below, exact/reversed equality, ordered and consumer-normalized hashes, vertex/node differences, displacements, segment headings and extent screening.
2. **Route identity and access:** explicit source membership evidence, requested route, supplied modes/tags and unknowns. `supported_bounded_membership` refers only to the preserved source membership, not the entire nearby target or a journey's right to credit.
3. **Attribution eligibility:** always `not_assessed_no_entitlement`, with `new_completion_credit=false`. No binding or travelled interval is produced.

| Classification | Rule |
|---|---|
| `exact_match` | Same known provider/feature ID, no explicit mode/access incompatibility or changed supplied node membership, and exact coordinate arrays. Missing versions remain reported. |
| `exact_reversed_geometry` | Same conditions, exact reversed arrays. A reversed representation is not a version mismatch. |
| `same_identified_feature_with_documented_geometry_update` | Same identified OSM feature; at least two versioned way-history observations retain one node chain, and per-node versioned history witnesses every coordinate on both sides and at least one moved shared node. Supplied chains must agree. Import versions are never populated from history. All source updates require review. |
| `possible_correspondence_requiring_review` | Supplied node membership changed, or same-feature non-exact geometry passes the descriptive extent screen without a history explanation. This is not a compatible admission. |
| `incompatible_or_unsupported_correspondence` | Different known feature/provider identity, explicit mode/access exclusion, or non-exact same-feature geometry fails the extent screen. Identical shapes with distinct IDs still fail. |
| `insufficient_evidence` | Feature identity missing. Other missing metadata is exposed in its own field; missing route identity always adds a review reason even when the shape is exact. |

The rule order is identity, explicit mode/access conflict, supplied membership change, exact/reversed shape, documented history update, then descriptive extent screening. Unknown original tags or mode evidence remain unknown; private/no tags reject unless a supplied explicit modal permission qualifies them. This tool is not an access-policy adjudicator.

Measurements use local equirectangular metres (radius 6,371,008.8 m; first source vertex origin), bidirectional line sampling at at most 5 m and nearest **finite** segment distances. Outputs explicitly distinguish sampled maximum deviation from an exact continuous Hausdorff bound. Corresponding-vertex displacement is reported only for equal vertex counts in the endpoint-best orientation. Segment undirected angles and directed endpoint orientation are separate. The descriptive compatible-extent screen requires endpoint displacement and sampled bidirectional deviation ≤10 m and length ratio 0.9–1.1; it never evaluates or overrides production admission. A documented historical change can be explained even if its extents fail this screen; that still requires review.

The 15° threshold only labels contextual direction/hairpin evidence. For a target hairpin, a small outward angle between the two preserved target edges flags return/parallel ambiguity. Source-section vertex ranges are preserved separately; an illustrative requested span crossing a London prefix fails containment. Contextual full-way-to-target metrics are conservative and cannot decide which finite subspan might qualify. No exact section-offset values or bindings are recalculated.

History absent: state explicitly that moved nodes, changed membership and independent redraw cannot be distinguished. History present: compatible coordinate/node states are reported; missing original import versions and capture dates stay unknown. Supplied node-chain differences include shared, removed and added IDs. No route identity is ever inferred from shape equality.

Limits: ≤100 comparisons, 2–5,000 vertices per feature, ≤20,000 line samples, bounded vertex/segment work. Excessive inputs fail explicitly; no silent national scan or coarsening. This is a bounded investigative tool, not a national graph comparer.

## Bounded controls

| Control | Diagnostic result | Preserved restriction |
|---|---|---|
| East Sussex 1289002662 | Documented shared-node geometry update; original versions unknown | Zero bindings; proposed NCN90 note cannot confirm NCN90; source admission/access still require review. |
| Newry Canal Towpath 59306268 | Exact unchanged source geometry; bounded NCN9 membership and admitted modes retained | No new seam credit; no journey evidence assessed. |
| Rural mid-Wales NCN8 31252441 | Exact unchanged geometry; bounded NCN8 membership/modes retained | No independent cycling graph or broader extent claim. |
| West Midlands NCN81 44807950 | Exact unchanged source, but target return angle flags hairpin/parallel ambiguity | Negative correspondence control remains a review hold. |
| London NCN1 1050757473 | Exact unchanged full-way shape; admitted vertices [1,2]; illustrative [0,2] request rejected | Prefix vertex0 remains excluded; original section bindings carried unchanged as evidence. |
| Cumbria NCN6/NCN7 23385711 | Exact physical-feature geometry; requested NCN6 unproved; bounded relation11017 says NCN7 | No admitted NCN source row; do not infer NCN6 absence/error or alias route identity. |
| Bradford NCN66 159311690 | Exact unchanged shape; leading target-alignment angles 16.6514° and 16.6203° | Existing 15° gate unchanged; neither whole-way identity nor close target samples proves a valid finite correspondence. |
| Deal NCN1 4471747 | Exact unchanged cycleway geometry; whole-way/target extents differ; retained 25/101 within10 m observation | Known full-way membership cannot approve the remaining target extent. |
| Deal private parallel 77611523 | Different identity and incompatible private-access evidence; rejected | No identity, access, extent or cycling entitlement transferred from4471747. |

Seven cases require manual review; Newry and mid-Wales are the two unchanged positive controls. One confirmed geometry-snapshot discrepancy, zero recovered original import versions, no new attributable correspondence and no quantified additional recoverable kilometres. Source-mode provenance remains incomplete for some rows, deliberately reflected in the review queue.

## Reproduction and validation

From the repository root, Python 3.12 (diagnostic/fixture tests need only the standard library):

```sh
python -m scripts.prepare_ncn_compatibility_controls --output /tmp/ncn-controls.json
cmp /tmp/ncn-controls.json tests/fixtures/ncn-source-compatibility/controls.json
python -m scripts.ncn_source_compatibility --input /tmp/ncn-controls.json --output /tmp/ncn-diagnostics.json
cmp /tmp/ncn-diagnostics.json docs/data-trials/ncn-source-compatibility-20261030.json
python -m unittest scripts.test_ncn_source_compatibility -v
```

**34 diagnostic tests passed**, including all eight original controls, the private parallel negative, exact/reversed geometry, small displacement without history, shared-node history, changed membership, identical/parallel different IDs, crossings without shared topology, missing versions/feature/route identity, private/unsupported modes, cycling unknown from walking, deferred masks, source extent bounds, deterministic reconstruction, tamper rejection, no input mutation and explicit work limits.

Additional existing regressions on unchanged baseline code:

```sh
python -m unittest scripts.test_ncn_binding_attainability scripts.ncn_trial.test_open_completion -q
python -m unittest discover -s tests -p 'test_*path_evidence.py' -q
```

**18 source-offset/finite-correspondence tests and 15 walking/cycling evidence tests passed**: **67 tests total**, zero final failures/errors/skips. These exercise synthetic/pinned local controls; they do not generate a national catalogue or assess national candidates. The sidecar normalizes all eight preserved geometry hashes consistently with their source receipts, including Bradford orientation normalization.

Execution paths for this session: the new diagnostic ran under `/workspace/scratch/e61d5c778001/work`; existing offset/control regressions ran under `/workspace/scratch/edcacb1336ba/mapping-audit` with `PYTHONPATH=/tmp/ncn-diag-deps`; evidence tests were read from main into `regressions/tests` and run with `PYTHONPATH=/tmp/ncn-diag-deps:/workspace/scratch/edcacb1336ba/mapping-audit python -m unittest discover -s regressions/tests -p 'test_*path_evidence.py' -q`. These are test-only copies; all used implementation modules match authoritative main. The first optional regression attempts failed to import missing Shapely/psycopg/osmium or a missing test helper; dependencies/helper were restored and the full specified suites passed. No behavioural failure was observed or suppressed. Test-only packages: Shapely2.2.0, psycopg3.3.6 and osmium4.3.2 (transitive dependencies recorded in the receipt). The sidecar itself has no such dependencies.

The two `cmp` checks verify byte-identical regeneration. The provenance receipt records exact source/main/fixture pins, implementation hashes, test counts and final output checksum. No new CI pass, Android consumer run, real-device acceptance or deployed capacity change is claimed. Existing live backend, installed APK and active Firebase versions were not queried: this offline task uses pinned historical inputs and makes no current live-version assertion.

## Wider applicability and one next increment

The reusable concepts are feature identity, immutable input checksums with their semantics, observed versus original version metadata, ordered node identity, geometry/normalization hashes, explicit transformation policy, bounded source extents, access evidence and a review queue separate from eligibility.

| Area | Potential use | Additional evidence needed |
|---|---|---|
| Walking refresh | Explain changed ways/nodes before replacing reference snapshots | Old/new compact feature exports and load receipts; retain original node/version metadata prospectively. |
| Cycling updates | Distinguish node-chain/profile changes from overview simplification | Pinned graph/PBF/profile and way/node crosswalk; do not align annotations to overview vertices by index. |
| Driving provenance | Identify graph/reference edition and representation changes | Driving provider/profile/build IDs and compact identified features; existing geometry alone is insufficient. |
| NCN maintenance | Make source hash rejections explainable and triage bounded section reviews | Route-specific admission, access, topology, section offsets, deferred status and binding provenance. |
| Settlement inventories | Flag reference edition/road identity changes during inventory refresh | Pinned old/new inventory feature identities, boundary edition and inventory derivation policy. |
| Targeted supplementation | Compare a small proposed supplement with an existing snapshot | Compatible licence/provenance, explicit feature/route identity, access and exact finite extents. |

**One recommended follow-up: a diagnostic-only Deal NCN1 admitted-span review.** Freeze the exact admitted4471747 section coordinates/offsets, target interval and relation75880 membership/date/access evidence; compare finite source sections against the surviving target interval while retaining private77611523 as a negative. Produce a per-span identity/access/extent evidence table and a proposed validation fixture. Do not widen admission or grant credit in that increment. A later separately authorised correction would still need measured eligible intervals, unchanged masks/gates and candidate-specific Android consumer validation. Existing evidence supports this bounded review, not a safe coverage-granting change now.

## Delivery state

Implementation is ready for review in a focused development PR targeting the verified main baseline. The development PR remains **open and unmerged**. Only PR #244 was authorised and merged. No matchers, saved journey evidence, targets, source offsets, admission policy, deferred masks, 10 m/15° tolerances or application runtime files changed. No national assessment, NCN catalogue build/publication, Firebase change, Render deployment, APK build/distribution, commercial-source acquisition, issue closure or branch deletion occurred. Backend/Firebase delivery: none. APK/tester delivery: none. Subsequent correction requires separate authorisation after this PR is reviewed.
