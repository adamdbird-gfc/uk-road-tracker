# NCN Stage 1.1 — projection consistency and metric-space audit

30 October 2026. Research baseline: **`0f8199651dad6014410274dc1b968e2417efe1b2`**. Android source baseline: **0.25.220 (266)**. Bounded offline research only.

## Findings and recommendation

**Projection can change geometric classifications under otherwise identical rules. It does not establish additional eligible completion.** The exact Deal fixture reproduces five held source edges failing the necessary approved-union whole-edge10 m corridor in spherical azimuthal-equidistant (AEQD) coordinates and passing that same necessary corridor predicate in route-centred equirectangular (EQ) coordinates. Edges7–9 additionally change from no finite target pair to a finite pair when only the projection changes. Edges6 and10 have finite subpieces in both spaces but different limits. Neither clipped subpieces nor a hypothetical corridor pass override the explicit reviewed source sections.

This is a verified scale/provenance inconsistency with threshold effects. The sample does **not** prove a faulty operational admission, unsafe existing credit, a national prevalence, or a reason to change the corridor or direction safeguard. The wider inventory finds multiple deliberately distinct coordinate conventions. No evidence was found documenting an intention that admission and correspondence should make different10 m corridor decisions at Deal.

**Recommend Option B as the next separately authorised increment:** add explicit projection/predicate provenance and common diagnostic measurements alongside existing operational values, preserving all admission and completion outputs. Include origin/latitude, units, source station basis, whole-edge versus clipped-pair predicate, and diagnostic-only status. Keep all operational hashes, source offsets, masks, identity/access and10 m/15-degree policies unchanged. Do not adopt a common operational projection on this evidence.

## Authorised PR #246 integration

| Check | Independently verified result |
| --- | --- |
| Open PR, target, exact head | Open, non-draft, mergeable, main; `65b8eab1450b7065d8969de1247f3c1f57e72089` |
| Baseline main | `da7bf0e0739ed260b15fe332c37b6e160832f594` |
| Scope | Exactly four added files under docs: report, bounded reproduction script, fixture, results; no other changes |
| Rules/checks | Main reports unprotected, required contexts/checks empty; active rulesets, reviews, threads, statuses and check runs empty |
| Administrative visibility | Separate protection endpoint403 integration permission; main's returned protection summary and rulesets read successfully; no bypass |
| Focused validation | 52 diagnostic/offset/finite tests plus15 walking/cycling evidence tests:67 passed |
| Reproduction | Deal receipt, compatibility controls and compatibility results byte-identical; compact Deal fixture SHA-256 matches pinned original |
| Merge | Supported connector merge commit with expected-head lease, `[skip ci] [skip render]` |
| Independently read main | **`0f8199651dad6014410274dc1b968e2417efe1b2`** |
| Merge parents | Starting main above and exact validated head |
| Complete tree equality | Main tree **`f483c88541c4c33e6e4c27fd71ae95f1cde39b24`** equals verified head tree |
| Original branch | `research/ncn1-deal-admitted-spans-20261030` retained at exact validated head |

The four PR files were read at their immutable head. Source sections0–6/11–13, held edges6–10 and private77611523 rejection survive the replay. Fixture extraction from original large archives was **not** repeated; its exact committed compact input was checked and consumed. No national download or assessment was needed. Local environment setup initially lacked test dependencies/helper files; after restoring exact repository dependencies, all required tests passed without skips. A trailing newline introduced during local file restoration was removed; original repository content was never changed. Clone access was unavailable; authenticated GitHub connector operations were used for reads, commits and merge.

## Measurement conventions

See [the complete operation inventory](ncn-measurement-conventions-20261030.md), including coordinate origin, reference latitude, units, distance, interpolation, direction, tolerance, role and compatibility for each inspected stage. Source blob identities are recorded in the audit receipt.

Admission uses spherical AEQD at the official route's WGS84 bounding-box centre. Completion correspondence and full-way source stations use spherical EQ at the same origin, with a fixed cosine of its latitude. Route-local coverage uses those stored stations and affine binding mappings. Canonical unique-network topology is noded in WGS84 degrees; canonical edge lengths use local EQ at each edge's first endpoint. Android source evidence reuses persisted route EQ, while display overlap highlighting has its own local approximation. Diagnostic source-version comparison uses first-source-vertex local EQ. These are distinct conventions with different purposes, not one inconsistent implementation of a single operation.

The origin is part of the contract. At Deal its latitude is54.4699892643778 degrees, about3.28 degrees north of the source. The EQ east-west scale relative to the local spherical scale is about **0.92728**, roughly7.27% compression. Longitude-origin changes alone translate EQ coordinates and do not change distances; latitude-origin changes horizontal scale. Both formulas are spherical approximations, not ellipsoidal geodesic truth. AEQD preserves radial distance from origin, not every local distance or angle.

## Exact Deal reproduction

Original source/target coordinates, metadata and input hashes remain in PR #246's fixture. This audit does not replace them with the earlier rounded research endpoints. Source4471747, target `open-edge:a25d7e26207940a4e9df96e7a4c74582ba53cb3f575a95dd1c8c536813c5d188`, origin **[-1.3437774293553804,54.4699892643778]**.

The audit independently implements both projection formulas and source-parameter finite-pair clipping, compares them to the repository functions within1e-7 m, and evaluates the same original edges, target and approved nearby union in each space. Continuous planar finite-segment minimum distance is computed by Shapely. Maximum distance from a straight source segment to one finite target segment is exactly the maximum of its endpoint distances because the target is convex. The approved-union outside lengths use the same Shapely10 m polygonal-buffer semantics as admission; they are not sampled estimates or maximum deviations.

| Edge | AEQD finite target min / max, m | EQ finite target min / max, m | Outside approved union, AEQD / EQ m | Whole-edge corridor AEQD / EQ |
| --- | ---: | ---: | ---: | --- |
| 6 | 8.544646396 / 10.432832117 | 7.922740161 / 9.673038567 | 18.680803224 / 0.000000000 | fail / pass (necessary condition only) |
| 7 | 10.432832117 / 10.604783506 | 9.673038567 / 9.832135858 | 82.893550561 / 0.000000000 | fail / pass (necessary condition only) |
| 8 | 10.520771714 / 10.604783506 | 9.754143652 / 9.832135858 | 157.641482207 / 0.000000000 | fail / pass (necessary condition only) |
| 9 | 10.376014566 / 10.520771714 | 9.620195985 / 9.754143652 | 89.027279838 / 0.000000000 | fail / pass (necessary condition only) |
| 10 | 9.909225578 / 116.047401908 | 9.189184563 / 116.031409409 | 28.363924962 / 0.000000000 | fail / pass (necessary condition only) |

Values in this table are rounded for reading; the machine-readable Deal receipt retains full Python floating-point precision, exact finite-pair limits, all source vertices, all13 source edges and signed metre/degree differences.

**Threshold outcomes:** AEQD whole-edge approved-corridor failures are exactly6–10; EQ failures are none within the retained approved nearby feature set. The actual reviewed admissible spans remain0–6 and11–13; no hypothetical outcome changes their eligibility. For edges6–9, maximum finite-target distance falls across10 m. Edge10 extends about116 m beyond the investigated finite target: its endpoint maximum is therefore **not** a10 m corridor classification. The whole-edge predicate evaluates the approved union, including the adjoining southern target. Its AEQD outside length28.36392496241386 m becomes0 in EQ. It still has a clipped finite pair in both spaces.

The finite-pair presence flips only on edges7,8,9. At edge6 the AEQD source-local admissible pair ends at62.81228507706751 m, compared with81.4616390930493 m in EQ. At edge10, AEQD retains local[28.363924963720148,35.14562539181701] m, EQ[0,35.00158808292383] m. These are stations in **different spaces** and cannot be substituted into historical source bindings. The independently coded clipping differs from the repository only by sub-1e-7 m floating-point arithmetic; the original #246 receipt itself reproduces byte-for-byte with its original functions.

All held-edge angles remain far inside15 degrees: AEQD0.03053–1.32766 degrees and EQ0.02836–1.23116 degrees. Direction is not the exclusion cause here. Maximum endpoint-distance difference on edges6–9 is about0.772648 m; the angle difference is at most0.096495 degrees on those edges.

Private residential77611523 has finite-target minimum **10.553059470846744 m AEQD** versus **9.78319156257058 m EQ**, a **-0.7698679082761632 m** change. One EQ-only geometric finite pair appears on its edge3. Different feature identity, private access and absent confirmed NCN membership continue to reject it. This demonstrates why even a small change in apparent proximity can generate a false candidate if policy safeguards are lost; it does not demonstrate existing false-positive credit.

### Separating causes

| Variable held or changed | Observation / interpretation |
| --- | --- |
| Projection only; same source vertices, original target, union and whole-edge predicate | Five necessary corridor classifications flip. This isolates a projection effect |
| Algorithm only in one space | Whole-edge admission and clipped finite correspondence have different outcomes even in AEQD; a partial finite pair never proves whole-edge approval |
| Sampling | Same interpolation fractions, <=1 m source spacing in either space, corroborate the effect on edges6–10. Continuous outside lengths and finite-pair classifications do not depend on those samples |
| Target segmentation | Original competition geometry and target identity are preserved.158 edge-pair orientation/midpoint checks show finite-pair source-interval unions invariant within1e-7 m; candidate multiplicity/topology can still depend on segmentation |
| Finite target extent | Especially important on edge10: distance to one target beyond its end differs from distance to the entire approved union |
| Whole-edge policy | Retained. Fractional corridor pieces cannot extend approved sections |
| Identity/access/modes/masks | Retained; no projection can create missing route identity or permission |
| Historical source offsets | Original #246 compilation replay reproduces all10 pre-seam bindings; section11 begins at835.1270332469487 m in the existing EQ full-way station basis; seam/deferred status unchanged |

The local-origin AEQD and local-origin EQ Deal distances nearly agree. Vertex8 is about10.5985 m from the finite target in local AEQD versus10.6048 m in route AEQD; route EQ gives9.8321 m. The major difference is therefore fixed reference-latitude scale in long-route EQ, not small local numerical noise or an OSM node/version change. Local geometry/orientation determines which component of a separation is compressed. Path length alone does not prove distortion: distance from projection origin and latitude/orientation matter. London's largely east-west source length illustrates this: route EQ70.774899 m versus route AEQD74.002746 m, without changing the stored station contract.

## Bounded cross-route comparison

Nine cases cover all eight requested locations plus the separate Deal private control. All use pinned existing evidence; no live mapping refresh or source import was made. The compact case fixture records geometry, identities/access, historical source presence/bindings, exact or inferred origins, and limitations. The results retain projection-specific lengths, continuous distances, planar angles, finite-pair limits, topology decisions and threshold-adjacent pairs.

| Case | Exact comparison and result | Actual eligibility / limitation |
| --- | --- | --- |
| Deal NCN1 | Same union whole-edge predicate flips5 necessary corridor results; finite pairs flip3; route/local metrics isolated | Edges6–10 held;0 additional eligible metres |
| Newry Canal Towpath NCN9,59306268 | Selected pinned source edge has2 finite target pairs in both AEQD/EQ; source/reference Hausdorff0; largest pair angle13.039313 vs13.023231 degrees | Existing traffic-free admitted-span positive control. Does not mean every source edge is admitted |
| Mid-Wales NCN8,31252441 | Selected source edge retains2 pairs; largest angle13.698877 vs13.696535 degrees; source/reference Hausdorff0 | Existing on-road admitted-span positive control |
| West Midlands NCN81,44807950 | Both target arms remain finite candidates; overlap46.69765592461206 m AEQD vs46.684390215867495 m EQ; ordinary-seam partition rejected in all four spaces | Topology/ambiguity negative, despite pair angles<=6.31 degrees; not a simple15-degree rejection |
| London NCN1,1050757473 | Pinned route origin;3 pairs in both spaces across the two retained target edges; exact source/reference geometry unchanged | Only vertices1..2 admitted. Prefix vertex0 cannot be recovered by changing measurement space |
| Bradford NCN66,159311690 | Local AEQD/EQ each yield0 finite pairs. First two source-edge angles16.651742/16.620318 vs16.651418/16.620295 degrees; later directions/finite extent also prevent correspondence | Historical direction rejection retained. Original route origin unavailable; this is local sensitivity, not an exact operational replay |
| Cumbria NCN6/NCN7,23385711 | All8 local geometric source-edge/target pairs present in both spaces, angles<=2.005 degrees; source/reference geometry identical | No admitted NCN6 row; observed NCN7 membership does not establish NCN6 identity. Original route origin unavailable |
| East Sussex RCN90,1289002662 | Exact pinned route-origin source/reference Hausdorff0.610312267277 m AEQD vs0.588966066549 m EQ; local values about0.610216 m in both | Documented shared-node movement remains a geometry-version update;0 existing bindings. No target admission/NCN90 membership inferred |
| Deal private77611523 | Minimum distance crosses10 m;1 EQ-only pair,0 AEQD pairs | Private/distinct identity remains invalid |

For Newry, Mid-Wales and NCN81, the original witness XY source endpoints and WGS84 source chain determine a **unique** EQ origin to<1e-7 m endpoint residual. The audit labels these origins **inferred**, and inversely reconstructs the two target-edge WGS84 endpoints without rounding. It verifies round-trip agreement with pinned target metric coordinates and uses original XY for route-EQ calculations. This is strong local metric consistency, not recovery of the full original route metadata or original unrounded publisher feature. Corresponding target identities are retained; no new target keys are created. London target endpoints and the Bradford/Cumbria preserved target controls are ten-decimal coordinates; the original Deal target is full precision.

No non-Deal finite-pair presence or15-degree classification changes were observed in these bounded comparisons. This is neither a national guarantee nor a claim that all admission gates were replayed. Most source/reference pairs are identical geometry, so their zero distances test reproducibility/identity rather than corridor sensitivity. Newry/Wales use real nonidentical target geometry as positive controls. The full non-approved feature set, exclusion masks and route metadata are not available for every control. No actual admission switch or national recovery estimate is claimed.

### Threshold sensitivity

The machine results list every retained pair whose minimum or endpoint-maximum distance is within1 m of10 m, or angle within1 degree of15 degrees. This descriptive band is not a changed operational tolerance.

Newry's selected source edge versus target1 has endpoint-maximum distance **10.038610 m AEQD /10.019948 m EQ**: both whole finite-target endpoint checks exceed10 m while clipped finite pairs remain valid. Mid-Wales similarly gives **10.625593/10.628068 m**. These demonstrate algorithm/finite-extent differences independent of a classification flip. Bradford's first edge is distance-compatible locally but fails direction at about16.65 degrees. The closest selected positive angle to15 is Mid-Wales13.70 degrees; no retained case lies within1 degree of15, and no angular classification flips occur. A future test requiring an actual near-15 flip needs separately pinned evidence; this audit creates no synthetic geographic observation or false claim of one.

## Materiality, possible defect and safety

| Question | Evidence-backed conclusion |
| --- | --- |
| Merely different diagnostic values? | Also verified: source lengths, distances and angles differ without classification changes across several controls |
| Different compatibility classifications? | Yes, Deal corridor/finite-pair and private finite-pair thresholds flip with otherwise identical geometry/predicate |
| Could source admission differ under identical geometric rules? | Yes, the necessary approved-corridor component flips on all5 held Deal edges. Full admission still requires explicit spans, excluded geometry, provider, route identity/access and modes |
| Could coverage attribution differ after operational reprojection? | Potentially, because target identities/stations, finite intervals and source lengths change. No replacement ledger/catalogue was built and no eligible increment is quantified |
| Parallel-road/branch false-positive risk? | Concrete private candidate and retained NCN81 ambiguity show why geometry cannot replace identity or topology. No new actual false-positive credit demonstrated |
| Wider than Deal? | Differing values and scale effects occur beyond Deal. Classification flips are observed only in Deal/private cases within this sample. National extent remains unresolved |

**Possible correctness issue to investigate separately:** the same numeric10 m threshold is expressed in two spaces with different local ground scale, and legacy records do not carry a sufficiently explicit projection/predicate label for all comparisons. This can make a geometric diagnostic look like an admission contradiction. The evidence supports a diagnostic provenance/interpretation defect risk. It does not establish a bug in reviewed eligibility, offsets, runtime matching or completion accounting. Changing the operational metric, whole-edge granularity, tolerances or source-span policy would be a separate policy/compatibility decision.

All existing10 m and15-degree safeguards, whole-edge admission, reviewed source offsets, mode/access/route restrictions and deferred masks are retained. The historical445.7985839098962 m Deal geometric residual is **not** eligible recovery. Demonstrated additional eligible route-local and unique-network credit remains **0 m**. Existing national assessability figures are neither recomputed nor extrapolated.

## Future options

| Criterion | A — retain projections, document them | B — common diagnostic reporting, preserve decisions | C — standardise operational measurements later |
| --- | --- | --- | --- |
| Accuracy | Existing approximations remain; explicit labels prevent misleading comparisons | Report native values plus a labelled local common metric; no claim a local spherical reference is survey truth | Could improve corridor ground-scale consistency, but requires a justified spherical/ellipsoidal/common-space choice |
| Reproducibility | Lowest change; pin origin/formula and predicate | Strong: versioned diagnostic schema, exact inputs, native and common values | Must version every origin, station, target and migration artifact |
| False positive/negative risk | Existing risk unchanged; discrepancy persists | Operational risk unchanged; diagnostic overinterpretation controlled by explicit non-entitlement fields | Can add or remove candidates, especially parallel roads, branches and threshold edges |
| Existing completion compatibility | Preserved | Preserved byte-for-byte operational outputs | Cannot assume compatible: positions, logical lengths, target coordinate hashes, network mappings and catalogue version can change |
| Historical binding stability | Preserved | Preserved; never rewrite prior station basis | Requires migration or explicit invalidation/reassessment; source identity alone cannot translate all old offsets |
| Complexity | Low documentation maintenance | Low/moderate diagnostic-only schema/tool work | High compiler, catalogue, consumer, migration and regression work |
| Regression requirements | Existing pinned examples and label checks | Deal paired predicates/private/hairpin/extent/version/identity controls; explicit origin and units; unchanged native outputs; tests for absent provenance | Broad national candidate equivalence/delta analysis only when separately authorised; identity/access/mask/branch and legacy journey/Android compatibility; denominator stability and rollback |
| National assessability | Unchanged | Unchanged | Unknown sign/magnitude; no national extrapolation from this sample |

**Smallest next increment:** Option B's projection/predicate provenance in the existing offline diagnostic output, with native admission/Route-EQ values and a clearly labelled first-source-origin common local measurement. This should require zero differences in operational source admission, bindings, catalogue bytes and existing consumer results. Where provenance is missing, emit unknown instead of guessing an origin. Add an explicit distinction between continuous union/finite-segment values and sampled source-version values. No operational reprojection is implemented here.

## Reproduction and validation

Python3.12, Shapely2.2.0. Test-only dependencies include psycopg[binary]3.2.10 and osmium4.3.1 for the existing evidence suites. No database server or external mapping service is contacted by these checks. The receipt records exact installed versions, original input hashes and audited source blobs.

From repository root:

```sh
PYTHONPATH=. python docs/data-trials/audit_ncn_projection_20261030.py \
  --output /tmp/ncn-metric-results.json --cases-output /tmp/ncn-metric-cases.json
cmp /tmp/ncn-metric-results.json docs/data-trials/ncn-projection-results-20261030.json
cmp /tmp/ncn-metric-cases.json docs/data-trials/ncn-projection-cases-20261030.json
PYTHONPATH=. python docs/data-trials/audit_ncn_projection_20261030.py \
  --deal-only --output /tmp/ncn-metric-deal.json
cmp /tmp/ncn-metric-deal.json docs/data-trials/ncn-projection-deal-results-20261030.json
PYTHONPATH=.:tests python docs/data-trials/investigate_ncn1_deal_20261030.py \
  --fixture docs/data-trials/ncn1-deal-fixture-20261030.json --output /tmp/original-deal.json
cmp /tmp/original-deal.json docs/data-trials/ncn1-deal-results-20261030.json
python -m scripts.prepare_ncn_compatibility_controls --output /tmp/ncn-controls.json
cmp /tmp/ncn-controls.json tests/fixtures/ncn-source-compatibility/controls.json
python -m scripts.ncn_source_compatibility --input /tmp/ncn-controls.json --output /tmp/ncn-diagnostics.json
cmp /tmp/ncn-diagnostics.json docs/data-trials/ncn-source-compatibility-20261030.json
PYTHONPATH=.:tests python -m unittest scripts.test_ncn_source_compatibility \
  scripts.test_ncn_binding_attainability scripts.ncn_trial.test_open_completion \
  scripts.ncn_trial.test_ordinary_seams scripts.ncn_trial.test_network_geometry \
  scripts.ncn_trial.test_saved_paths scripts.ncn_trial.test_cycling_saved_paths -q
PYTHONPATH=.:tests python -m unittest discover -s tests -p 'test_*path_evidence.py' -q
sha256sum docs/data-trials/ncn1-deal-fixture-20261030.json \
  tests/fixtures/ncn-source-compatibility/controls.json \
  tests/fixtures/ncn-gap-seams/diagnostic-witnesses.json \
  tests/fixtures/ncn-binding-attainability/source-sections.json
```

**Final results:**95 focused existing tests plus15 existing evidence tests = **110 passed,0 failures/errors/skips**. Independent projection/finite-pair formula assertions, original source/target pin assertions, same-parameter sampling,158 orientation/segmentation invariants and hairpin topology assertions pass. Full cross-route fixture/results and the Deal-only receipt regenerate byte-for-byte.112 existing restored files match immutable baseline Git blobs with zero mismatches. No fresh CI/Android suite, real-device acceptance, surveyed positional accuracy or original import-version recovery is claimed.

Primary input SHA-256 values:

| Input | SHA-256 |
| --- | --- |
| Original Deal fixture | `bb7fa4420151218c053cda1e93b6a592323e2c85a724965985b211d9386c779c` |
| Existing compatibility controls | `113bf75bbe0d89f1fa64b65d732eee4e94e43dbb533b619c86d29c27a445244e` |
| Seam diagnostic witnesses | `9b741fa37ef7f74e29269ed77de2d9d0068ed54744b18775b1a994b49a06612b` |
| Source-section fixture | `5e42cc0058ed71a4bead0ac9636e757e289f3aa1bc8e57b18be95bd31c57a91b` |

The Deal fixture preserves the original archive/source hashes from #246. New compact cases are deterministic extractions/coordinate transforms of these inputs, not newly observed mapping evidence. Full-precision floating-point output is reproducible in the recorded Python/Shapely environment; other GEOS/platform versions may require numeric comparison rather than byte equality.

## Preservation and delivery

Branch **`research/ncn-projection-audit-20261030`** was created directly from newly validated main, without a stacked dependency. Early report checkpoint **`310e4c50498f30aaa88d807ea37b21680e9ee6c1`** was read back exactly. Inventory/Deal checkpoint **`221a8cf937f464211f21d7bdce89ded4e2bb84cf`** preserved three files, each independently verified. Cross-route checkpoint **`e3ee6566ab2778be0f3efda33639d31f8499a00f`** preserved script/cases/results, each read back exactly before final report expansion.

The final new PR targets main and remains **open and unmerged**. Only #246 was merged. All new/changed files are under docs: permanent report, inventory, bounded reproduction code, compact fixtures, full-precision machine results and receipt/checksums. No application, matcher, admission, tolerance, runtime, catalogue or journey changes. No national assessment, commercial data acquisition, Firebase publication, Render deployment, APK build/distribution, branch deletion or unrelated issue/PR closure. Source integration does not establish deployed/installed versions; those were not queried and remain unknown. No live-device acceptance is claimed or required for this research-only result.
