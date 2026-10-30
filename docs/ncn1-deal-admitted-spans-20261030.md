# NCN Stage 1.1 — Deal NCN1 admitted-span investigation

30 October 2026. Baseline main: `da7bf0e0739ed260b15fe332c37b6e160832f594`. Android source: **0.25.220 (266)**. Research only; no source admissions, bindings, credit, catalogue or runtime changes.

## Decision

**Retain the existing exclusion. Demonstrated additional eligible recovery: 0 m route-local and 0 m unique network.** The missing middle of OSM cycleway **4471747** is explicitly outside its approved source sections. Replaying the necessary whole-edge approved-corridor condition reproduces all five held edges. Several are more than 10 m from the publisher line in source admission's spherical azimuthal-equidistant projection, although they fall within 10 m in completion binding's route-centred equirectangular projection. The same numeric tolerance has different geometric effects here.

This explains the historical known-way classification without identifying a compiler-offset defect or an entitlement to more credit. Whole-way NCN1 membership and walking-reference presence do not extend admitted sections. The existing admission review approved complete source edges, not arbitrary clipped subintervals. Admitting fractional pieces would require a separately reviewed admission-contract change.

Recommend **no Deal credit correction**. A subsequent, separately authorised **research-only projection-consistency/provenance diagnostic** could document the two existing metric spaces and their threshold-sensitive results. This investigation does not choose a replacement projection, change a threshold, or estimate national recovery.

## PR #245 integration

| Verification | Result |
| --- | --- |
| Expected and independently read open head | `f21219ac4e1e89fd87c8789d5911d465befeee60` |
| Base and pre-merge main | `8dea49475e287bfefad7fa93b75e201e94a44230` |
| Scope | Exactly nine added offline diagnostic, fixture, test, result and documentation files; no other changes |
| Required checks / rules | Main unprotected, required contexts/checks empty, active rulesets empty |
| PR conditions | Open, non-draft, mergeable; no submitted reviews or review threads; no commit statuses, check runs or PR workflow runs |
| Validation reproduced | 34 diagnostic + 18 offset/finite + 15 walking/cycling evidence = **67 passed; zero final failures/errors/skips** |
| Reproduction | Fixture and diagnostic results regenerate byte-for-byte |
| Method | Supported GitHub merge commit with expected-head lease and `[skip ci] [skip render]` |
| Resulting independently read main | **`da7bf0e0739ed260b15fe332c37b6e160832f594`** |
| Parents | Pre-merge main and exact validated head above |
| Merged tree | `7069ff338d3ac2bbb52e61f6d4f864a7dc8369f0`; entire recursive tree equals verified head tree |
| Original development branch | `diagnostics/ncn-source-compatibility-20261030`, independently confirmed retained at validated head |

All nine head files were read from GitHub. A recovered local copy was checked against that immutable remote tree: **452 existing files matched, zero mismatches** after replacing two test copies with authoritative originals. The first evidence-test invocation lacked its test helper on the import path; the final full 15-test run passed after restoring the exact helper. No skipped test or CI pass is being substituted for the local results.

The merged nine files are the three `ncn-source-compatibility` report/receipt files, `scripts/ncn_source_compatibility.py`, `scripts/prepare_ncn_compatibility_controls.py`, `scripts/test_ncn_source_compatibility.py`, and `NOTICE.md`, `controls.json`, `deal-private-control.json` under `tests/fixtures/ncn-source-compatibility/`. No application, backend, Android runtime, matcher, tolerance, source admission, target or reference catalogue behaviour changed.

## Evidence lineage and exact identities

The bounded investigation reuses #240 classification (`21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7`), #241 attribution feasibility (`24428cb4ad35e563113d7a86f7edc13652dc2844`), #242 ordinary seams (`7179c7b9a837a2255a85b58693e0b4188d3cc864`), #243 mapping sources (`ef4d229368ad517ddac2ebbe2c7b6621a65e8991`), #244 post-seam reconciliation (`bfe31f6715780908bd8b2a24ad6ea982ba2e79cf`) and verified #245. The retained #239 source-offset correction is replayed as a bounded control. Neither the historical classifier nor a national candidate assessment was rerun.

Existing evidence archives were restored, with only the NCN1 checkpoint inputs/plans and required receipts inspected. No published catalogue or PBF was downloaded, and no candidate database was created. The compact committed fixture removes the need for those archives for normal reproduction.

| Input | Exact SHA-256 |
| --- | --- |
| Completion input pins | `a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641` |
| Completion plan summary | `e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b` |
| Decoded NCN1 plan | `3a78e0553adf20180238d357b0153cd0c35180608ac332fb7b65fed410f5d6dd` |
| Decoded NCN1 association | `1e41883f506438d70c961653657c342b2c27b2451922b8cf8247d0f0ef7d2ec0` |
| Classified interval gzip | `1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb` |
| Validated seam insertion delta | `f98df308ccf4120939e705e498f6cfdbbb36373298060f29a230a88929e6957d` |
| Original approved NCN1 feature array, canonical digest | `cf6c11a075171b2758f15773252ac1a0ab0d8b39e7e24cd6fcf58d8393f48778` |

The extractor verifies the immutable input pins, plan/association decoded hashes, each consumed catalogue input, all seam chunk hashes and concatenated artifact, classified gzip and original bounded OSM capture. Remaining compact capture/provenance hashes are in the fixture. These are integrity checks, not signatures or recovered import dates.

**Published target:** `NCN:1`, `open-edge:a25d7e26207940a4e9df96e7a4c74582ba53cb3f575a95dd1c8c536813c5d188`, length **495.388143301922 m** in the preserved completion metric. It comes from publisher feature **FID 10909**, **SegmentID 28182**, GlobalID **27e00ced-c023-4847-8974-9797eba54dd9**, edge index **2**, `Open / Main Route / TrafficFree / Asphalt`.

Original WGS84 endpoints, recovered from the pinned approved extent rather than rounded controls:

```json
[[1.40372103860818,51.1948382751462],[1.40434206204827,51.1903977901992]]
```

Exact completion-metric endpoints:

```json
[[177539.83136843255,-364180.67702454224],[177579.96110758072,-364674.43710450013]]
```

The script verifies the target digest and exact length from those original metric coordinates. The earlier ten-decimal controls remain descriptive evidence and are not used to reconstruct this target identity.

**Source:** OSM way **4471747**, fourteen ordered vertices/nodes, geometry SHA-256 `d87a1574fc5ac999e24d159cf00937531f9178481705bfa15d25923022196ecb`. Association snapshot `4a6e8e28715d1c87aa256016b30b11c4989bc663a2df24d6e0fb70a3ff21e7fb`; membership-evidence checksum `bb650ac123b9b95236d66a6b0259aa6f8744cd348912766bdca822a4d515212f`.

Observed way v11 is dated **14 October 2023 20:30:22Z**. The pinned bounded OSM capture has direct way membership, empty role, in relation **75880 v747**, dated **5 April 2026 20:40:23Z**, `network=ncn`, `ref=1`, `route=bicycle`, Dover–Canterbury. Superrelation **1992599 v373**, dated **19 May 2026 01:37:26Z**, contains relation75880. The original identity review's roots are1992599 and8438340. The stored association, current observed node chain and walking coordinates agree exactly. Walking's original way/node versions were not retained; the observed versions are not assigned retrospectively to either import.

Access observations: `highway=cycleway`, `bicycle=designated`, `foot=designated`, asphalt. Both modes are retained on the two admitted sections. Static observations do not certify historical access for every possible journey.

## Admitted sections, offsets and current coverage

Stationing below is the existing full-way completion metric, spherical radius6371008.8m and origin **[-1.3437774293553804,54.4699892643778]**. Stations must not be mixed with azimuthal-equidistant lengths. Source direction is vertex0→13, broadly north→south; target direction is the same. All fourteen exact coordinates, nodes and stations are in the fixture/results.

| Status | Vertices / section | Boundary node IDs | Full-way station interval, m |
| --- | --- | --- | --- |
| Admitted | 0–6, `4471747:0:6` | 978142881 →978142885 | [0.0,273.52677777274164] |
| Unadmitted | 6–11; edges6,7,8,9,10 | 978142885 →978142890 | [273.52677777274164,835.1270332469487] |
| Admitted | 11–13, `4471747:11:13` | 978142890 →913199040 | [835.1270332469487,841.4926548476633] |

The unadmitted middle is **561.6002554742071 m** of source stationing, not missing target distance. The audit explicitly holds edges6–10 as `unresolved_official_extent_edges`, partly accepted. The identity review policy is `partial_provider_verified_spans_only` and lists only0–6 and11–13.

`compile_path_bindings` reproduces all ten pre-seam corrected bindings exactly. The second source section translates local0 to full-way **835.1270332469487 m**, as required; no reversed or misplaced section offset is found. The validated seam delta adds precisely two bindings to this source, both around full-way station180.54–180.78m and on other target edges. Thus there are twelve retained bindings after inserting that delta, with **no seam addition on the investigated target**.

Existing target coverage is **[0.0,49.58955939202579]m**, supplied by source edge5 at **[223.91830395603452,273.52677777274164]m**. Historical classification retains:

| Historical region | Exact target interval, m | Current bounded conclusion |
| --- | --- | --- |
| Ambiguous correspondence numerical sliver | [49.58955939202579,49.5895593920258] | Approximately7.1e-15m; retain explicitly |
| Unadmitted known-way span | [49.5895593920258,495.388143301922] | **445.7985839098962m**, unchanged by seams |

The original full-precision targets and corrected plan coverage plus absence of target seam insertions establish this current local status. The historical label is not promoted into a fresh national classification.

## Why the middle remains unadmitted

`confirmed_paths.build_candidate` admits whole node-to-node edges only when they lie entirely within the approved publisher geometry's10m buffer, avoid excluded geometry and pass identity/access/provider checks. Its `metric_projector` is spherical azimuthal-equidistant. The identity-review span list also explicitly restricts edge selection.

`open_completion.edge_interval`, applied only after source admission, uses equirectangular plan coordinates. The route-origin latitude is54.47° while Deal is51.19°; east–west distances are compressed relative to local ground scale. This accounts for borderline parallel linework passing a10m completion screen while failing the necessary admission corridor condition.

| Held source edge | Full-way completion station interval, m | Source length outside approved10m corridor, admission metric m | Geometry-only completion target interval, m |
| --- | --- | ---: | --- |
| 6 | [273.52677777274164,354.98841686579095] | 18.68080322376776 | [49.589559392025805,131.03239271218774] |
| 7 | [354.98841686579095,437.83252947565086] | 82.89355056142496 | [131.03239271218771,213.87635255336855] |
| 8 | [437.83252947565086,595.3751938969185] | 157.64148220749746 | [213.87635255336858,371.41899766943845] |
| 9 | [595.3751938969185,684.3454975808727] | 89.02727983817353 | [371.4189976694384,460.3892005220646] |
| 10 | [684.3454975808727,835.1270332469487] | 28.36392496241386 | [460.3892005220646,495.388143301922] |

All five whole edges fail the necessary approved-corridor test. Their completion angles range **0.02836°–1.23117°**, well within15°; direction is not the cause. Midway source vertices7–10 are about10.38–10.60m from the finite target in the admission metric but9.62–9.83m in the completion metric. No tolerance was widened and no projection was substituted into production.

The source enters the finite target's completion correspondence at station223.91830395603452m, target0; it reaches the admitted-section endpoint at target49.5895593920258m, and geometrically leaves the finite target at source719.3470856637965m, target495.388143301922m. In the admission metric, edge6 leaves the target's10m corridor after **62.81228507706753m** of that edge; edge10 briefly re-enters near the target's southern end. These clipped pieces do not turn either complete held edge into an approved source section.

The compact fixture contains all approved original feature parts within30m of this source; features1079,1820,10909. Its target competition set includes all nearby approved target edges, retaining original noding and exact target keys. The full original non-approved feature set is not reconstructed. This is sufficient to prove rejection: failure of the necessary approved-corridor condition cannot be cured by an excluded-feature check. No positive eligibility claim is made from an incomplete exclusion reconstruction.

## Compatibility, adjacent geometry and negative controls

Both PR #245 Deal comparisons were rerun unchanged:4471747 is an **exact geometry match**, while77611523 is **incompatible/unsupported** because it is a different, private feature. Both continue to emit `not_assessed_no_entitlement` and no credit. Original import versions remain null. The small PR #245 full-way/target extent screen is contextual; the exact interval analysis above explains what it could not decide.

Private residential **77611523**, Wellington Parade, has `access=private`, no confirmed NCN1 membership and no identity transfer from4471747. Its exact finite-target minimum distance is **10.553059470846744m** in the admission projection and **9.78319156257058m** in the completion projection. The latter even produces a geometry-only edge3 candidate over target **[0.0,10.064181640224568]m**, entirely inside existing target coverage. It remains rejected by identity/access/membership safeguards. The old25/101 and0/101 sampled observations are retained historical summaries, not continuous interval measurements.

The held source edge10 also competes briefly with adjacent target `open-edge:e4fa74c3b8c7537c0812c5b97bbcd40f47a09b0572b72b3d58336eda878b2b2b`. Candidate source windows overlap at full-way **[719.3187045766281,719.3470856637965]m**, about0.02838m. This geometric-only ambiguity remains an additional safeguard, not an argument for admitting the edge.

Adjoining admitted NCN1 ways **84079387** and **84079705** share respectively the north and south endpoint nodes978142881 and913199040. Their full lines remain223.54m and121.45m from this target; neither supplies missing target evidence. No other admitted NCN1 path lies within10m of the target in the retained plan. The nearest deferred source section is **96304.36356730695m** away, and4471747 is not deferred. Removing a deferred mask would not resolve this case.

## Hypothesis evaluation

| Hypothesis | Supporting evidence | Contradicting evidence / uncertainty | Existing rules permit correction? |
| --- | --- | --- | --- |
| A: correct exclusion | Explicit reviewed edges0–6/11–13; all five held whole edges fail approved10m corridor condition | Some finite subpieces fit the corridor; positional accuracy of publisher/OSM linework is not independently surveyed | **No additional credit. Supported explanation.** |
| B: incomplete source correspondence / compiler limitation | Whole-way membership, modes and exact nodes; equirectangular finite candidates cover the residual geometrically | Section offsets replay exactly; excluded edges are absent by explicit admission decision; projection/whole-edge gates explain omission | **No. No offset/compiler defect established.** Fractional admission would be a new reviewed contract |
| C: source-version mismatch | Original import versions missing; publisher and OSM geometries differ | NCN, walking and observed source shapes are identical; association node chain matches observed chain; no source geometry update demonstrated | **No.** Missing metadata remains unknown, not a demonstrated version cause |
| D: ambiguous adjacent geometry | Private parallel road; small southern target-seam candidate overlap | Private road has distinct identity/access and no membership; endpoint connectors do not cover the gap | **No.** Preserve private exclusion and target uniqueness |

## Quantification and next increment

**Demonstrated eligible additional source/target interval: none. Demonstrated route-local and unique-network recovery:0m.** Existing completion coverage, both modes and other routes are unchanged. The geometry-only ceiling on this exact historical target residual is **445.7985839098962m**. It ignores failed source admission and the small southern ambiguity; it is not eligible recovery. Its corresponding unadmitted geometric source window starts273.52677777274164m and reaches719.3470856637965m, ending inside held edge10.

No exact nonzero unique-network ceiling is calculated: this bounded fixture does not include the shipped database's target-to-network mappings or all-route coverage unions. Those would be required before any future nonzero attribution claim. They are unnecessary to establish zero demonstrated eligible recovery. No national extrapolation is warranted.

The smallest useful next task is a **research-only metric-space diagnostic**, using the same exact source/target fixtures and explicitly labelled projection provenance. Acceptance controls should include: unchanged admitted source offsets; held whole edges6–10; positive known admitted source/target edge5; private77611523 rejected despite its completion-projection proximity; southern target ambiguity; reversed geometry and source endpoint boundaries; original target digest preserved. Independently reproduce both metric outputs, assert no new bindings, and retain existing masks/10m/15° gates. This would inform a later architectural decision, not authorise a correction. A future credit-changing task would need separate owner authorisation, reviewed source eligibility, exact target/network overlap, independent validation and candidate-specific Android consumer testing.

## Reproduction

Python3.12; research calculation requires Shapely (validated with2.2.0). Existing broader regression imports use the same test-only dependency set recorded in #245. From repository root:

```sh
PYTHONPATH=. python docs/data-trials/investigate_ncn1_deal_20261030.py \
  --fixture docs/data-trials/ncn1-deal-fixture-20261030.json \
  --output /tmp/ncn1-deal-results.json
cmp /tmp/ncn1-deal-results.json docs/data-trials/ncn1-deal-results-20261030.json
python -m scripts.prepare_ncn_compatibility_controls --output /tmp/ncn-controls.json
cmp /tmp/ncn-controls.json tests/fixtures/ncn-source-compatibility/controls.json
python -m scripts.ncn_source_compatibility --input /tmp/ncn-controls.json --output /tmp/ncn-diagnostics.json
cmp /tmp/ncn-diagnostics.json docs/data-trials/ncn-source-compatibility-20261030.json
PYTHONPATH=.:tests python -m unittest scripts.test_ncn_source_compatibility \
  scripts.test_ncn_binding_attainability scripts.ncn_trial.test_open_completion -q
PYTHONPATH=.:tests python -m unittest discover -s tests -p 'test_*path_evidence.py' -q
```

Optional fixture reconstruction from retained archives, without a national build/assessment:

```sh
PYTHONPATH=. python docs/data-trials/investigate_ncn1_deal_20261030.py \
  --checkpoint CHECKPOINT_ROOT \
  --classified CLASSIFICATION_ROOT/classified-intervals.jsonl.gz \
  --osm CAPTURE_ROOT/NCN1_unadmitted_Deal.osm \
  --output /tmp/ncn1-deal-fixture.json
cmp /tmp/ncn1-deal-fixture.json docs/data-trials/ncn1-deal-fixture-20261030.json
```

`CHECKPOINT_ROOT` is the extracted `ncn-completion-checkpoint-2026-10-19` folder from existing archive `libfile_99ba17ccc32c81919dc203e36ac496f5`; classification archive `libfile_5dc7eef825d081918e5e49fe2ee47e36`. The bounded capture is pinned in #243's committed receipt. The committed fixture provides exact relevant original geometry, section/binding excerpts and the compact observed membership; users need no archive download for normal receipt reproduction.

Both fixture and result reconstruction were independently run and compared byte-for-byte. The research script asserts target digest/length, source sections, full-way offsets, held edges, original source/node equality, compatibility controls, private rejection, existing target coverage and seam/deferred status. The67 existing tests passed again at the merged baseline. These are local reproducibility results; no fresh CI, Android/device acceptance or surveyed mapping accuracy is claimed.

## Preservation and delivery

Research branch **`research/ncn1-deal-admitted-spans-20261030`** starts directly at the newly verified main. Initial scope checkpoint **`b51260ff9b1892072f44b35b8ab301b16faf7d8f`** was independently read back before the investigation. Fixture checkpoint **`f5db770db0073d268afb735d0fa8068a39fa9694`**, results checkpoint **`16b23e0028db2fb0c565a54cd0fa38931d571ded`**, and reproduction-script checkpoint **`3e67618aaf26c3f568fa15c3cba4b8ac238d2231`** were each committed and independently read back exactly.

The new PR targets main and must remain **open and unmerged**. Only #245 was authorised and merged. All new files are research documentation or bounded research reproduction/receipt files under `docs/`. No runtime, matcher, admission, source offset, tolerance, target, candidate catalogue, journey or Android changes. No national assessment, Firebase publication, Render deployment, APK build/distribution, commercial acquisition, branch deletion or unrelated issue/PR closure. Installed APK/live backend/active Firebase versions were not queried and remain unknown; source integration does not imply deployment. No further Deal correction is supported or implemented.
