# NCN Stage 1.1 — post-seam gap reconciliation and source provenance

**Date:** 30 October 2026. **Validated main:** `668292c306dd937c3a424164ea9c75f934751401`. **Android source:** 0.25.220 (266). Research and planning only; candidate capacity is not a user's travelled distance or newly published application capacity.

## 1. Executive summary and recommended next action

The retained evidence supports a reproducible interval reconciliation of the **1,289,324.406113 m unique-network deficit**. The exact residual lengths within historical cause regions are now measured, replacing PR #243's conservative bounds. They are **not an exact new current-cause classification**, and do not measure missing physical paths.

The seam correction removed almost all its recovered length from historical ambiguity regions. No measurable length was removed from the historical no-correspondence, known-way or deferred regions. Remaining no-correspondence plus direction regions contain **1,045.276431 km unique network**, approximately **81.07%** of the deficit. Neither category proves physical absence or recoverability.

A specific source-version mismatch is explained: OSM way **1289002662** retains the same node chain, but two node coordinates changed on 7 October 2026, after the September walking-reference load. The new coordinates match the pinned NCN source. This does not establish recoverable distance: the retained source row has **zero bindings**, and proposed-route wording is not route membership.

**Recommend the smallest next development increment: an offline source-provenance and compatibility diagnostic sidecar**, with the eight frozen controls in this report. Keep exact source-hash rejection, mode/access rules, finite 10 m/15° gates, route identity and deferred masks unchanged. No next coverage-granting correction or recovery estimate is justified yet. After that diagnostic foundation, a bounded known-way/span admission review is the best next coverage investigation.

No application/backend development, matcher changes, candidate generation, publication, deployment, APK or user-data changes occurred.

## 2. Verified integration of PR #243

Only the explicitly authorised documentation PR was merged, using GitHub's supported merge-commit operation with the exact expected head supplied.

| Check | Result |
|---|---|
| Expected and actual head | `ef4d229368ad517ddac2ebbe2c7b6621a65e8991` |
| Pre-merge main | `1030b726c4640e04a9c6051667886812daf39fcc` |
| Changed files | Exactly three added documentation/evidence files below |
| Unexpected application/matcher/Android/reference-data changes | None |
| Mergeability | Clean and mergeable; not draft |
| Repository-required checks | Main unprotected; no required contexts/checks; rulesets empty |
| Executed head checks/statuses | Zero check runs and zero commit statuses; no fresh runtime CI pass claimed |
| Report/receipts | Totals, sample count, recovery arithmetic and stated bounds consistent within numerical rounding |
| Merge and resulting main | `668292c306dd937c3a424164ea9c75f934751401` |
| Delivery protection | Merge title/message included `[skip ci] [skip render]` |

Independent reads at the resulting main SHA matched all reviewed Git blobs:

| File under `docs/` | Git blob SHA |
|---|---|
| `national-mapping-sources-investigation-20261030.md` | `e4b6f4cf989846269889e5baf6be0f18e259e454` |
| `data-trials/national-mapping-source-evidence-20261030.json` | `e2eb2d38be1c84ed5b756584c2b5aab095221410` |
| `data-trials/national-mapping-historical-classification-20261030.json` | `a1bb81b649c48e5eab93a3423e23d9ac21b4fd98` |

The old route-local category sum differs from its capacity-derived total by **0.0000092953 m**; its unique category-set sum differs by approximately **0.0000000133 m**. These numerical accumulation residues were identified before merging and are below the report's precision. They are not silently assigned to semantic categories here.

## 3. Recovered evidence, pinned inputs and exact coverage

### 3.1 Research lineage

| Evidence | Pin and relevance | Inspected state |
|---|---|---|
| PR #238 Completion Register | `767d81c07c610ea248172825bee784f01550828f`; confirmed decisions, especially DEC-001 and DEC-012 | Register/PR scope inspected; open/unmerged, unchanged |
| PR #239 source-offset correction | Head `22ff0c320b40327c878278aaed1218f383d48e99`; integrated at `cc088b828982a3ff9399e2bd930978ce02ecba4a` | Merged; fixes source offsets without changing target capacity |
| PR #240 national classification | `21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7` | Exact archive/classifier/summary recovered; open/unmerged |
| PR #241 attribution feasibility | `24428cb4ad35e563113d7a86f7edc13652dc2844` | Shared matching retained; route attribution remains distinct; open/unmerged |
| PR #242 ordinary seams | `7179c7b9a837a2255a85b58693e0b4188d3cc864` | Merged through starting main; validation receipt and 96 insertion chunks reused |
| PR #243 mapping sources | Head/merge above | Reviewed, merged and independently read back; eight bounded examples reused |

Confirmed owner decisions are not reopened. Preserve valid published eligible scope, actual qualifying evidence and 25/50/75/100 milestones; do not redefine 100% as the bound subset. Stage 2 mileposts/personality discoveries remain separate.

### 3.2 Exact input evidence

| Input | Immutable identity / location |
|---|---|
| Historical classified interval gzip | SHA-256 `1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb`; 442,910 classified pieces from 385,605 original gaps |
| Completion input pins | SHA-256 `a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641`; all 1,147 consumed files verified |
| Plan summary | SHA-256 `e837b6810d7044d42c2a2eb7db6bce102cebb957032cc18a76487297b6621f0b` |
| Exact shipped SQLite | SHA-256 `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`; read-only baseline coverage and unchanged target/network mappings |
| Integrated pre-seam canonical content | SHA-256 `936c347468a0f14ed6d2a6d3a31765674e059d48a625c1bce96339032456a57a` |
| Seam insertion delta | Reconstructed compressed SHA-256 `f98df308ccf4120939e705e498f6cfdbbb36373298060f29a230a88929e6957d`; all 96 chunk SHA-256s checked |
| Committed delta index | Git blob `674d8e023d8d937e4fa1311cc3c8951c0abc558f`; exact-byte SHA-256 `261aff88eef218a55c977fffc61fd009afa5e0f1b175a10f761f86282bcb9b29` |
| Validated candidate canonical content | SHA-256 `cf25e006e79782c9458895bf4b68179b7829fe3b6e7d5ded96be0ccd07929dc0` |

The retained archives were restored for necessary exact evidence rather than downloading national source data again:

- Classification: Library `libfile_5dc7eef825d081918e5e49fe2ee47e36`, `roadprints-ncn-remaining-gap-classification.zip`.
- Offset evidence: `libfile_a7da8305d85c8191b8470d9c0b590c40`, `roadprints-ncn-stage1-binding-evidence.zip`.
- Checkpoint: `libfile_99ba17ccc32c81919dc203e36ac496f5`, `roadprints-ncn-completion-checkpoint-2026-10-19.zip`.
- Original published catalogue: `libfile_8ab97d8102a88191905627b3c064c7cd`, `roadprints-ncn-firebase-publication-2026-10-19.zip`.

No essential interval/mapping artifact was missing. Original imported OSM feature versions and some capture timestamps were not retained; these are provenance limitations, not grounds to rerun national classification. No national PBF or commercial source was acquired. No candidate database was generated or published.

### 3.3 Validated post-seam totals

| Quantity | Metres |
|---|---:|
| Published measurable unique network | 17,435,132.494005967 |
| Assessable unique-network capacity | 16,145,808.087893184 |
| Remaining unique-network deficit | **1,289,324.406112783** |
| Remaining route-local deficit | **1,288,222.023171362** |
| Unique-network seam recovery | 45,834.461831640 |
| Route-local seam recovery | 45,814.697795170 |

Assessability is **92.60502088781922%**. There are 218 measurable routes; 217 improved and zero became newly attainable. These are PR #242's validated candidate quantities, not fresh publication or real-device results.

## 4. Exact post-seam historical-region reconciliation

### 4.1 Method

For each historical `(route, target, low, high, cause)` interval, subtract the union of baseline target coverage and the validated seam insertion intervals. Resolve delta target indices using the unchanged sorted target table. Compare every route's remaining capacity with the PR #242 all-route receipt.

For the unique network, project baseline coverage, seam additions and historical cause intervals through existing piecewise target-to-network mappings. Union coverage across all routes first. Partition only the surviving network gap by the set of historical causes present there. This prevents shared routes from double-counting and retains mixed-cause fragments explicitly.

The script imports no matcher/compiler and opens SQLite with `mode=ro`. It decodes **159,711** existing insertions in **25,577** source rows. It does not generate, assess or write a candidate catalogue.

This proves exact residual lengths **in the stored interval/mapping model**. It does not prove every residual still has precisely the same semantic cause. Changed seam uniqueness, source versions, membership, target generalisation and access require separate current-policy analysis. Historical cause provenance survives subtraction; it does not become a new current-cause determination.

### 4.2 Measured residuals

| Historical region | Remaining route-local km | Routes with residuals | Remaining exclusive unique-region km | Route-local removed by seams, km |
|---|---:|---:|---:|---:|
| No admitted source correspondence | 618.189314 | 202 | 618.865767 | 0.000000 |
| Direction-gate rejection | 426.015034 | 217 | 426.410665 | 0.066801 |
| Ambiguous target correspondence | 63.722439 | 217 | 63.668797 | 45.736492 |
| Unadmitted span of known source way | 101.290749 | 175 | 101.320084 | 0.000000 |
| Endpoint/alignment limitation | 69.682163 | 217 | 69.755505 | 0.011404 |
| Deferred binding withheld | 9.322324 | 49 | 9.303583 | 0.000000 |

Route counts overlap. These region lengths are review envelopes, not recovery estimates. Exact doubles, per-route results and counts are in [the reconciliation receipt](data-trials/ncn-post-seam-reconciliation-20261030.json). There are **279,501 positive residual pieces within historical classified boundaries**; that is not a count of merged gaps or distinct physical paths.

The unique partition also retains:

| Mixed/unclassified region | Metres |
|---|---:|
| Historical ambiguity + direction | 0.000027758103 |
| Historical ambiguity + endpoint/alignment | 0.000024692651 |
| Historical direction + endpoint numerical overlap | approximately 5.43e-14 |
| **Unclassified numerical projection residual** | **0.005403850651** |

The complete event-sweep sum is **1,289,324.4061127824 m**, versus the validated **1,289,324.4061127827 m**: a difference of approximately 0.0000000003 m.

Route-local classified residuals sum to **1,288,222.0231620774 m**, leaving **0.0000092844 m** against the validated capacity total. This micrometre-scale amount is explicitly unclassified in the receipt, not silently assigned. The prior classifier's 5.395951 mm network residual differs slightly from the present 5.403851 mm because of interval accumulation/projection arithmetic; the present baseline replay agrees with validated network capacity within 0.0001 m. That numerical assertion tolerance is never used as an admission tolerance.

**Confidence:** high for pinned evidence identities, measured historical-region lengths, all-route subtraction and network deduplication. Current semantic causes remain unresolved beyond those historical-region measurements. No national physical-absence or eligibility fraction has been established.

## 5. Source-version, route-identity and direction findings

### 5.1 Eight bounded source ways

Eight current official OSM full-way reads total approximately 34 KB. Seven coordinate arrays equal PR #243's stored walking-reference captures; East Sussex differs. Current feature versions are observed metadata, not proof of the metadata versions originally imported.

| Control | OSM way / observed version | Walking load region/date | Finding |
|---|---|---|---|
| Newry NCN9 seam | 59306268 / v12 | Ireland and Northern Ireland, 22 Sep | Current/live/pinned geometry equal; positive seam and NI control |
| Mid-Wales NCN8 seam | 31252441 / v8 | Wales, 23 Sep | Equal; positive ordinary on-road seam |
| Wolverhampton NCN81 hairpin | 44807950 / v13 | West Midlands, 23 Sep | Equal; preserve hairpin/return exclusion |
| London NCN1 source-offset | 1050757473 / v2 | Greater London, 23 Sep | Equal; preserve admitted section and excluded prefix |
| Cumbria historical NCN6 gap | 23385711 / v12 | Cumbria, 22 Sep | Physical way exists, but no row for this ID in the retained NCN paths table |
| Bradford NCN66 direction case | 159311690 / v5 | West Yorkshire, 23 Sep | Equal geometry and admitted NCN66 row; directional admission remains separate |
| Deal NCN1 known-way case | 4471747 / v11 | Kent, 22–23 Sep receipts | Equal; source extent/membership must still be proved; private parallel road remains negative |
| East Sussex RCN90 mismatch | 1289002662 / v2 | East Sussex, 22 Sep | Older live coordinates versus newer pinned/current coordinates; zero bindings in retained source row |

[The provenance receipt](data-trials/ncn-post-seam-source-provenance-20261030.json) records source keys, regional extract checksums, load timestamps, current way/node versions, capture hashes, coordinates, both geometry-hash conventions and observed NCN rows. Kent has two loader receipts; neither is silently selected as the sole edition. Metadata retrieval time is not import time. Exact PBF replication timestamps were unavailable; no national/regional PBF was redownloaded merely to obtain one.

**Hash conventions:** PR #243's `geometry_sha256` hashes ordered coordinate JSON. Consumer `source_geometry_sha256` rounds to OSM precision, removes adjacent duplicates and normalizes orientation. They differ by orientation for East Sussex and Bradford. That is not evidence of another upstream geometry update. The new receipt records both algorithms and compares like with like.

### 5.2 East Sussex upstream edit explained

Way **1289002662** retains node chain **6925348794 → 6925348793 → 6925348792**.

| State | Feature/node history | Coordinate evidence |
|---|---|---|
| Walking-reference-compatible state | Way v1; node versions 2, 4, 2 following 2 June 2024 edits | Exactly equals September stored walking coordinates |
| 7 October 2026 16:15:07Z update | Way v2, changeset **190157057**; node8794 v2→v3, node8793 v4→v5, node8792 unchanged at v2 | Exactly equals current and pinned NCN geometry |

The way's v2 tag change adds `surface=asphalt`; separate node histories establish the coordinate edits. The preserved maximum corresponding-vertex displacement is **1.5051875383180653 m**. This proves an upstream mapping change, not independent surveyed accuracy.

- Walking extract checksum: `b255032f23946a18d33b87cbd736d46301c5b13a2165074c482e389f17cee11a`.
- Load time: `2026-09-22T21:28:41.801116Z`; source key `osm_geofabrik_east_sussex`.
- Walking ordered-coordinate hash: `c7fee978db7dc57ccd9a4dbd13e43f4e6f4eac5a8c3831ca95811f5e6e92e2d4`.
- Walking consumer-normalized source hash: `78d6e31864bab90f8caf0c3d6fc256fc5696cc80158768dab11b9ec76e8fc02c`.
- NCN/current consumer-normalized source hash: `7b8058924dac3672ef80aa069b0a51b68c98d6e652c09f71b9b299141515a59c`.
- Consumed NCN source snapshot: `847afcc532ab4c680dff85fcdd04d5f8cc9f799443a551f4ed7252939d4b33f5`; membership evidence: `51fe6d5644c0e048b1867e9fdacc89e9a56202cb28439e5f9b00cb73ebe01934`.

Exact NCN capture time is absent from the consumed association. Its coordinates reflect the 7 October update and were consumed by the 19 October checkpoint. A content hash cannot establish a more precise timestamp. Original feature/node metadata versions were not stored; historical equality establishes a compatible state, not recovered original version metadata.

Baseline `NcnMatcher.java::walking()` requires the feature ID and exact consumer-normalized source hash, plus mode/access checks. Incompatible evidence receives no credit. There is no automatic version crosswalk or explanation of upstream edits. `pedestrian_reference.py::WayCollector.way` stores feature ID/coordinates and selected public tags, but not original OSM node IDs or way/node versions. Literal `source_version=latest` is not a snapshot identity.

The RCN90 source row has **no bindings** before or after seams. Its source admission is bounded; a note saying proposed **NCN90** cannot establish new RCN90/NCN90 eligibility. Even accepting geometry compatibility would not demonstrate any credit for this row. Do not weaken the hash contract or transfer evidence based on the approximately 1.5 m displacement.

Reproducible official API histories: [way](https://api.openstreetmap.org/api/0.6/way/1289002662/history.json), [node8794](https://api.openstreetmap.org/api/0.6/node/6925348794/history.json), [node8793](https://api.openstreetmap.org/api/0.6/node/6925348793/history.json), [node8792](https://api.openstreetmap.org/api/0.6/node/6925348792/history.json). The receipt freezes compact versions/coordinates and response hashes; later API history can grow.

### 5.3 Distinct surviving bounded failure modes

Prior exact interval subtraction established that all three PR #243 example gaps survive unchanged:

| Example | Remaining route-local interval | Interpretation |
|---|---|---|
| Cumbria NCN6, target `f4db91e0…` | 0–1,040.872533406 m | Physical way23385711 exists; returned relation11017 says NCN7; no admitted catalogue row. Resolve route identity/date and capture/admission; do not infer NCN6 is wrong or cannot overlap NCN7 |
| Bradford NCN66, target `217a655a…` | 0–54.084864052 m | Same live/pinned source geometry; relation17883 identifies NCN66 but notes local uncertainty. Directional/finite alignment remains a separate issue |
| Deal NCN1, target `a25d7e26…` | 49.589559392–495.388143302 m, length445.798583910 m | Known source4471747 and relation75880 provide a bounded membership lead, not proof of the whole span; only25/101 preserved samples lie within10 m |

Full target IDs/exact intervals are in the provenance receipt. Bradford's source segment headings against its straight target vary; two leading segments are approximately16.6°, above the unchanged15° gate. Some other segments pass angle alone. Neither proximity nor full-way membership proves every target portion passes finite correspondence; no relaxation is warranted. Deal's private parallel way77611523 remains excluded. No sample distance is extrapolated nationally.

## 6. Ranked opportunities and one next increment

Historical region sizes below are **review envelopes**, not measured candidate recovery or guaranteed recoverable upper bounds. Route counts are historical-region reach, not proven consumer impact.

| Rank | Opportunity | Demonstrated coverage / potential reach | Evidence, false-attribution risk and complexity | Consumers and validation |
|---|---|---|---|---|
| 1 | Source-version provenance and compatibility diagnostics | No recovery demonstrated; one explained RCN90 mismatch with zero bindings | Strong node-history/coordinate evidence; low risk if diagnostic only; small offline scope | No matcher/Android contract change; deterministic frozen snapshot and orientation controls |
| 2 | Bounded known-way span admission review | No candidate recovery measured;101.290749 km route-local /101.320084 km unique review region,175 routes | Known feature reduces search; exact span identity, membership and access remain unproved; medium false-credit risk | Preserve offsets/modes/schema; start with Deal and private parallel negative; future candidate needs Android tests |
| 3 | Direction rejection/alignment | No candidate recovery measured;426.015034 km route-local /426.410665 km unique,217 routes | Bradford proves proximity does not solve direction; topology/generalisation analysis required; medium/high risk | Finite-anchor/crossing/turn controls; unchanged15° gate; avoid matcher changes |
| 4 | Missing admitted source correspondence | No candidate recovery measured;618.189314 km route-local /618.865767 km unique,202 routes | Largest envelope, but Cumbria shows identity/admission ambiguity; greater investigation complexity | Explicit route identity and per-span evidence; no nearest-line join or replacement matcher |
| 5 | Residual ambiguity/endpoint cases | No new recovery measured; residual lengths above | Ordinary seams already corrected; branches, hairpins, returns and unsupported endpoints need individual evidence; high false-credit risk | Preserve seam/offset consumer controls and finite source support |
| Excluded | Deferred masks |9.322324 km route-local intentionally retained | Existing review holds, not automatic opportunities | All49 masked routes remain negative controls |

**Recommended development brief: source provenance and compatibility diagnostics.** Implement one offline, read-only sidecar in a separate short-lived PR from latest validated main:

1. Accept pinned walking feature/load records and NCN associations. Record source provider/key, extract checksum and its semantics, actual load/capture timestamp or explicit unknown, original feature/node versions where available, ordered and consumer-normalized geometry hashes, admitted section identities, mode/access provenance and binding presence.
2. Distinguish exact compatibility, orientation-only representation, explained upstream edit, unknown history, missing admitted source, route-identity conflict, no bindings and deferred status. Emit reasons and a compact review queue; award no credit.
3. Freeze the eight controls and the East Sussex before/after node histories. Bradford orientation normalization must not become a false second version mismatch. Preserve RCN90 proposal/no-binding status.
4. Do not change matchers, Android/ledger schema, source admission, target denominator, deferred masks or reference publication. Do not generate a candidate catalogue or rewrite journey evidence.
5. Require a later separately evidenced per-span proposal before accepting changed source geometry. Small displacement or identical way ID alone is insufficient: access, route extent, crossings and node identity may change.

Acceptance: deterministic output from frozen inputs; exact-hash positive; explained before/after mismatch without credit; orientation-only normalization; explicit missing versions; zero extra credit on private/parallel/proposed/deferred/hairpin controls. Existing offset/seam consumers remain untouched. A future credit-granting correction requires candidate-specific route/network receipts and Android consumer regression tests. No APK is needed for the diagnostic-only task.

This is the smallest safe development increment supported now, with **no recovery estimate**. If the next task must grant coverage, evidence is insufficient to select an implementation: first complete the bounded Deal admitted-span/identity analysis and measure eligible intervals. The separate OS detailed-network comparison remains future research; no commercially licensed dataset was acquired.

## 7. Reproduction, validation and GitHub preservation

The [standard-library script](data-trials/reconcile_ncn_post_seam_20261030.py) verifies exact database/classified-gzip/delta hashes, decodes existing anchors and subtracts/project-unions intervals. It writes only a JSON receipt.

From this research branch, extract the original shipped catalogue and classified interval gzip from the retained archives. Use the unchanged committed seam directory:

```sh
python docs/data-trials/reconcile_ncn_post_seam_20261030.py \
  --database RETAINED_SHIPPED_DB \
  --inventory RETAINED_CLASSIFIED_INTERVALS_GZIP \
  --delta-dir .github/ncn-reference/ordinary-seams \
  --validation docs/data-trials/ncn-ordinary-seam-validation-2026-10-30.json \
  --output FRESH_RECONCILIATION_JSON
```

Do not run a national candidate assessment or rebuild merely to reproduce this classification subtraction. Source hash normalization is baseline `matched_path_evidence.source_geometry_digest`; the prior ordered hashes are separately `digest(coordinates)`.

Completed before the allowance interruption:

- Exact shipped DB/classified gzip, all96 delta chunks and reconstructed compressed hash verified;159,711 insertion records decoded.
- All1,147 consumed checkpoint inputs verified. All218 route residuals and national partition/recovery totals asserted against the validated candidate receipt.
- Three exact sample gaps confirmed unchanged by seam insertions; source rows inspected read-only. No new candidate database written.
- GitHub checkpoints independently read back: scope `a4ae0585bc5895c427f58a9e4c122040d9ad5834`, reproduction script `b5e156033a5180dabc808fe8d4cd990db7f0b3cb`, interval receipt `1c04a6cfbad3fac6ffadf18cbae20203ff2f39bf`.

On resumption, GitHub main and branch were intact but the scratch workspace had cleared. The checkpointed script and all218-route receipt were restored directly from GitHub. No national calculation was repeated. The uncommitted narrative/provenance draft was reconstructed from recorded results, with eight small OSM full-way reads and four history reads independently repeated. Source receipt `96bc798da0fc1bdb29f5110a5e74369d9e59b488` was committed and read back before final report completion.

The early receipt used a semantically identical reserialized delta index. Commit `a017b8ab304ad894b37cde0ccff034b520b482cc` replaces only its index hash with the exact committed-byte SHA; measured results are unchanged. Exact index Git blob and five focused interval controls were rechecked: reversed/overlapping intervals, subtraction, full removal, reversed network projection and shared-route deduplication. Route-local and unique partition arithmetic, including explicit residuals, passed.

Final checks cover Python syntax, JSON/links, documentation-only diff, and independent GitHub reads. No new application/Android/real-device validation is claimed; PR #242's43 Android consumer tests remain historical evidence for that candidate. No full810-test rerun or fresh CI pass is implied.

The branch `docs/ncn-post-seam-reconciliation-20261030` starts directly at validated main and targets main. Permanent output consists of this report, two JSON receipts and one documentation reproduction script. Large archives/databases and national delta chunks are not duplicated into the new PR. Leave the new research PR open and unmerged.

## 8. Outstanding unknowns, decisions and delivery status

- **Current causes:** exact semantic classification requires a separate current-policy analysis beyond interval subtraction. Measured historical-region residuals are complete; current causes are not silently assumed.
- **Physical network absence:** national fraction remains unknown. No full active pedestrian eligibility export or bicycle/car graph snapshot comparison was available.
- **Original metadata:** original feature/node versions, PBF replication timestamps and some NCN capture dates were not stored. Coordinate equality cannot recreate missing provenance.
- **Route identity:** Cumbria NCN6/NCN7 discrepancy unresolved; proximity cannot approve an alias or membership. Proposed, private, parallel, ambiguous and deferred spans cannot receive automatic credit.
- **Recovery candidates:** none newly measured. No new product decision is needed for the diagnostic task or bounded existing-scope research; any proposal to change valid published scope/admission needs a concrete separate review.
- **Integration/delivery:** PR #243 integrated at `668292c306dd937c3a424164ea9c75f934751401`. New research PR not merged. Backend/Firebase changes or deployment: none. APK build/distribution/tester release: none. Android source remains0.25.220(266); installed/live versions were not queried or inferred. Issues changed/closed: none. Other research branches/PRs retained.
