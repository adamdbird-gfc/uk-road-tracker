# National mapping sources and network coverage investigation

**Date:** 30 October 2026. **Audited main:** `1030b726c4640e04a9c6051667886812daf39fcc`. **Android source baseline:** 0.25.220 (266). Research only; no application, backend, matcher, reference catalogue or deployment changes.

## Executive summary

**Retain the existing architecture while investigating targeted source and attribution improvements. A replacement national network is not justified by the evidence.** Roadprints already combines OS Open Roads for GB A-road reference geometry, OSM-derived motorway and walking references, separate external routing graphs, official NCN target geometry and official settlement boundaries. These have different purposes; they are not interchangeable versions of one map.

The merged seam correction leaves **1,289.324 km of unbound NCN unique-network capacity**, not 1,289.324 km of proven missing roads or paths. Every one of eight deliberately selected source ways was found in the live walking reference database. Three historical gap examples also have corresponding current OSM geometry, but route identity, alignment and admission remain separate questions. One stored source way differs from the pinned NCN/current OSM geometry by up to approximately 1.51 m at corresponding vertices. Snapshot consistency matters even where a physical path is already present.

The old national classification cannot be presented as an exact post-correction partition. Additive-coverage bounds nevertheless establish that at least **999.509 km** of the remaining deficit lies in regions previously classified as no admitted source correspondence or direction rejection. Neither label proves physical absence. No nationwide physical-network absence measurement was performed.

Continue bounded Stage 1.1 attribution work independently of a broader mapping change. First make source versions and feature correspondence explicit. Then, if a suitable licence and small sample are available, compare OS NGD Transport Network or OS Multi-modal Routing Network against a few established OSM controls. Its potential advantages are detailed topology and restrictions; its incremental UK coverage benefit and commercial redistribution costs are unmeasured. No NCN recovery is claimed for an untested alternative source.

## 1. Scope, method and preservation

The inventory was completed before selecting the architectural recommendation. Evidence comprises pinned repository source/manifests, PR240/241 research already completed, PR242 validation receipts, small public reference manifests, eight exact live walking-reference feature reads, and bounded OSM API samples. No national source download, candidate generation or national assessment rerun was performed.

This report was checkpointed early on `docs/national-mapping-source-investigation-20261030`: initial commit `a4f30c1fab987bdd4b6ce08f099ce09b34040dc1`, then inventory checkpoint `a601299c175c254044d12cc624b3b70445e7b894`. Both received independent GitHub file read-back. Final source captures and calculations accompany this report in [the compact evidence receipt](data-trials/national-mapping-source-evidence-20261030.json) and [historical classification extract](data-trials/national-mapping-historical-classification-20261030.json). Hashes identify captures; large bbox XML and national lists are intentionally omitted from the commit.

**Terminology:** physical geometry describes where a road/path lies; identity says which provider feature or signed route it is; journey evidence records an observation; matching selects plausible physical features; attribution maps admitted evidence onto a progress denominator; published catalogues package selected reference data for consumers. A shared shape does not establish shared identity, access rights or completion credit.

**Confidence labels:** verified = directly inspected source, receipt or bounded read; inference = conclusion conditional on those observations; unknown = no adequate evidence. Source baseline does not prove the currently installed APK or externally hosted router build.

## 2. Existing architecture and source inventory

All repository links below pin the audited main. [R1–R15](#repository-reference-index) provide the exact files; source editions are distinguished from derived asset generation dates.

| System | Physical source and representation | Identity, processing and consumer | Version/provenance and limitations |
|---|---|---|---|
| GB A roads | **OS Open Roads**, link centreline geometry transformed from British National Grid to WGS84; reference anchors sampled at 100 m | `build_os_open_roads_a_cache.py` groups A-road identities/components into per-road GB tiles; Android `ARoadProgressCalculator` uses road identity then anchor proximity | v5 index generated 9 September 2026; 1,781 road identities. Narrow explicit OSM fallback for A1026. Generation date is not an upstream OS edition or source checksum. This is a GB denominator, not a UK multimodal routing graph. [R1,R8] |
| Motorways and motorway-class A(M) roads | OSM ways captured through Overpass; deduplicated 100 m anchor reference | `build_motorway_cache.py`; Android motorway calculator uses reference road identity, approximately 110 m matching radius and its own anchor deduplication | v1 generated 14 September 2026, 63 records; builder explicitly records skipped A601(M) geometry after failed attempts. Separate snapshot and denominator from A roads. [R2,R8] |
| B roads and local-road discovery | Geometry and ref/name carried in saved matched journey features | Android `ProgressActivity.collectRoadDiscovery` at line444; `roadLabel`/category at lines516/528 classify B-number identities or local names; road discovery excludes several non-road feature classes | No inspected national canonical denominator for every B road or local road. Discovery totals are not a census of all physical roads. Stored evidence and router classification influence them. [R8] |
| Driving matching | External OSRM graph; default `https://router.project-osrm.org` | `api.py` match requests, recovery windows and step refs/names produce saved road/motorway/A-road GeoJSON. Driving/bus progress policies remain distinct | Active service graph edition, build hash and profile configuration are not established by the repository default URL. OSRM is an engine, not proof of an OS or OSM source edition. [R3] |
| Walking/running matching | Geofabrik OSM regional PBF ways stored in PostGIS; full original coordinate arrays, selected tags | `pedestrian_reference.py` imports eligible highway classes; `database.py::match_pedestrian_reference` line537 builds a bounded local graph, then `pedestrian_matching.py` matches a trajectory. Foot OSRM fallback is configured separately | Live metadata: 51 load receipts across 50 source keys, loaded 22–23 September 2026; `source_version=latest`. Regional receipts have checksums. Duplicate Kent receipts are not unique feature counts. Retrieving source metadata later does not mean a reload occurred. [R3,R4] |
| Cycling matching | Separate external bicycle OSRM graph; default `https://routing.openstreetmap.de/routed-bike` | Node-annotated matched geometry becomes typed cycling path evidence in `cycling_path_evidence.py`; no automatic road credit from cycling geometry alone | Active graph timestamp/profile/build and persistent feature crosswalk remain unknown. An OSRM URL path alias does not establish travel policy. Bike and walking graphs must not be assumed identical. [R3,R5] |
| NCN published target and source bindings | Official public NCN on-road/traffic-free target geometry; separately admitted OSM route ways/node chains | Target route/logical/network identities plus source-geometry bindings compiled by `scripts/ncn_trial` and `build_ncn_national.py`; Android matches source evidence and unions ledger intervals | Pinned October research inputs, reviewed identity exceptions and deferred masks. The new ordinary-seam policy is opt-in; legacy remains default. **Source-integrated candidate is not the live published catalogue.** [R6,R7,R9] |
| Settlement road coverage | ONS Built-up Areas 2022 for GB; NISRA Settlement Development Limits 2015 for NI; OSM named eligible streets intersecting boundaries | Inventory of distinct stripped names on residential/unclassified/tertiary/living_street features, with official settlement IDs; Android resolves local-road evidence to settlements | Live manifest: 9,072 inventories (8,545 GB +527 NI). Root historical manifest with121 entries is superseded. GB inventory evidence pins OSM timestamp13 October2026 20:21:31Z. NI publication has separate receipts. Not all road/path geometry qualifies. [R10,R11] |
| County geography | Historic county boundaries, full-resolution Definition A source, WGS84 derivative | Offline/cache reference used for geographic attribution, not a routable graph | 92 counties, source/version6 October2026; independent from settlement and road denominators. [R12] |
| Android and Firebase assets | Packaged A-road/motorway assets; downloaded indexed NCN SQLite, county and settlement reference files | `PublicReferenceData`, catalogue file integrity/version checks, local caches and mode-specific progress consumers | Public reference host `https://roadprints-data-822cc.web.app/`. Hash manifests distinguish assets from code. Large NCN geometry is not embedded in every journey. [R8,R9,R12] |

### 2.1 What is already shared, and what is separate

OSM is a common upstream origin for several systems, but not a common frozen snapshot or graph. The walking database, bicycle router, car router, motorway cache, settlement extracts and NCN source capture have separate processing and potentially separate dates. NCN consumes existing walking/cycling evidence; it is not a new GPS router. A-road and motorway progress use relatively broad sampled anchors, while NCN uses finite admitted source-to-target intervals with substantially tighter geometric rules. Sharing their tolerances would change semantics.

The walking importer keeps highway/name/ref/surface/foot/access, but does not preserve original node IDs, bicycle tags or the full bridge/tunnel/layer/conditional restriction set. The bounded matcher constructs coordinate-keyed connectivity. This is not a complete authoritative multimodal topology/access representation; these observations alone do not demonstrate an actual erroneous journey match. Cycling evidence preserves different identifiers. A generic cross-provider spatial join cannot replace either evidence contract.

Settlement road totals count qualifying names, not edge length. An unnamed road or service road can be physically mapped but excluded from the settlement denominator. Existing GB inventory documentation gives such cases; zero qualifying names need not mean no roads. Unified physical geometry would not automatically improve that product definition.

### 2.2 Live/reference distinction

The live Firebase NCN manifest read during this audit still identifies **revision1**, SQLite SHA256 `973ad286cffbec67fa3249b7d00b3a201059ac552aac6b63b577bb5db5637093`. Its database is243,724,288 bytes, compressed142,796,162 bytes. The merged seam candidate has not been published. Consequently the92.6050% below describes validated candidate capacity available in source evidence, not a newly delivered live application result.

The live settlement manifest read is788,222 bytes, SHA256 `8dc2a62941e79f627badfed93077caede2aa8077d982c2f2bf24dc0aff772da9`. Read-only reference SQL returned eight selected public source features and small loader receipts; no user journeys or credentials were exported. Backend health establishes readiness, not routing-graph provenance.

## 3. Remaining NCN deficit: what can actually be classified

### 3.1 Validated merged correction

| Metric | Metres | Kilometres |
|---|---:|---:|
| Published measurable unique target |17,435,132.494006|17,435.132|
| Assessable unique capacity after correction |16,145,808.087893|16,145.808|
| Remaining unique unbound capacity |1,289,324.406113|1,289.324|
| Remaining route-local unbound capacity |1,288,222.023171|1,288.222|
| Seam recovery: unique network |45,834.461832|45.834|
| Seam recovery: route-local |45,814.697795|45.815|

Assessability is **92.6050209%**. 217 routes improved; no newly fully attainable route. The prior44.369/44.388 km estimates were superseded by the validated implementation assessment and geometric audit, including its approximately203 m comparison with the earlier diagnostic ceiling; they are not additive recovery opportunities. [R7]

The audit checked159,711 added bindings with finite geometry: maximum offset9.9966 m, maximum angle14.9993°, all49 deferred-route masks preserved, and all non-binding tables unchanged. Walking/cycling matchers, direction gates and target identity were preserved. This report relies on that completed validation; it does not rerun it.

### 3.2 Historical categories and conservative post-correction bounds

PR240's pinned classification predates the seam correction. Its route-local categories below sum to1,334.036721 km. The unique-network figures are old mutually exclusive cause regions; tiny mixed/residual regions are recorded separately in the JSON extract. A new full causal partition is unavailable.

Because the validated correction only adds eligible coverage, the residual in any old unique cause region C must lie between max(0, C−45.834462 km) and C. These independent bounds **cannot be summed**, and are not a fresh classification.

| Historical cause | Old route-local km | Old unique cause-region km | Possible residual in that old unique region, km | Meaning; physical geometry missing? |
|---|---:|---:|---:|---|
| No admitted source correspondence |618.189|618.866|573.031–618.866| No admitted source record for that target, not proof of absent road/path. Route membership, snapshot and capture/admission need checking. |
| Direction gate |426.082|426.477|380.643–426.477| Source candidate exists but alignment rejected. Could be target generalisation, segmentation, crossing/parallel geometry or truly unsuitable correspondence; not a reason to relax15°. |
| Ambiguous target correspondence |109.459|109.425|63.591–109.425| Competing targets/branches/returns; often physical geometry already exists. Preserve negative controls. |
| Unadmitted span of known way |101.291|101.320|55.486–101.320| Known way does not establish every span's NCN membership. Check route extent and source offsets. |
| Finite endpoint or alignment |69.694|69.767|23.932–69.767| Includes compiler seam/alignment limitations. Some corrected here; not all ordinary seams and not all safe. |
| Deferred binding withheld |9.322|9.304|0–9.304 (conservative arithmetic bound)| Deliberately excluded; unchanged masks prevent treating it as free recovery. |

At least **999.508759 km**, approximately77.52% of the remaining unique deficit, lies in the union of the old no-correspondence and direction regions. This is a mathematical bound on historical regions, not a claim about current cause labels or recoverability. The unchanged exclusion masks further constrain deferred evidence; no new exact deferred kilometre total is asserted here.

The full post-seam partition would require joining the old classified interval archive to the already-generated candidate's compact coverage intervals and projecting through its unchanged network mappings. The full classified archive/candidate database was not re-downloaded for this bounded investigation. A sample cannot substitute for that join. Neither the existing association rows nor the public manifests provide a national road/path/access split or a national comparison against the active walking and bicycle graphs.

**Strongest supported explanation:** the deficit measures conservative source-to-target attribution and correspondence limits, including identity, direction and ambiguity. The proportion caused by genuinely missing physical geometry is **unknown**, not zero and not1,289 km.

## 4. Alternative source comparison

Capabilities below are provider statements, distinct from measured Roadprints benefit. Source pages and licence metadata were checked on30 October2026; quoted commercial prices were not obtained.

| Source | Confirmed capability and role | Limits, licence and Roadprints suitability |
|---|---|---|
| **OSM / Geofabrik** [S1–S3] | UK roads, paths, cycleways, access tags, connectivity and NCN bicycle route relations; full feature history/IDs. Regional extracts are generally updated daily. Already a major Roadprints source. | Community feature freshness and completeness vary locally; daily extract refresh does not mean every feature is recent. Relations may disagree with official target identity. ODbL attribution and database obligations apply to redistributed adapted databases; application code is a separate issue. Pin extracts, node/way versions and transformation policies before considering replacement. |
| **OS Open Roads** [S4] | Structured classified/unclassified GB road centreline links/nodes, April/October publication, open reuse under OGL. Already used for A-road coverage. | Generalised network, not detailed pedestrian/cycle infrastructure or turn-restriction routing. Provider explicitly says unsuitable for in-vehicle navigation without detailed restrictions. GB excludes NI. Retain current role; not a universal substitute for OSM. |
| **OS OpenMap Local** [S5] | Open detailed contextual mapping around1:10,000 including road/building/cartographic context, six-monthly updates. | Not demonstrated to be an improved routable pedestrian/cycle graph. A drawn feature or area is not necessarily a topological centreline with access rules. Useful for checking context, not an evidence-backed replacement. |
| **OS NGD Transport Network / RAMI / detailed highways products** [S6–S8] | Monthly GB detailed road/path network, stable OS feature identifiers, connecting links and richer restriction/asset information. RAMI provides turn/vehicle/hazard detail. | Path capture specification has exclusions: parallel pavements are not simply all Path Links; other feature types/links matter. Coverage rules do not promise every private or isolated path. Paid/contracted access outside applicable open/public-sector arrangements. Consumer Android, derived catalogues and offline/Firebase redistribution require explicit terms and a quote. No sample-level improvement measured. |
| **OS Multi-modal Routing Network (OS MRN)** [S9] | Monthly connected GB road/path/rail/ferry data, restrictions and modal connections; PBF/GeoPackage, OSM schema, parent OSIDs. Strongest detailed-network trial candidate. | OSM schema means compatible structure, not OSM way IDs or ODbL licensing. Mode profiles, feature mapping and attribution still require testing. GB-only; no proved incremental paths or recoverable NCN mileage in this investigation. |
| **Walk Wheel Cycle Trust / Sustrans official NCN** [S10] | Official signed-route target identity and on-road/traffic-free geometry; public service metadata describes regional updates and weekly publication. Existing pinned source remains essential. | An NCN route is not the whole public cycling network or a legal-access guarantee. Official geometry may be generalised or differ from current OSM. Preserve route status/reclassified/proposed distinctions. Exact official public WFS item advertises OGL3.0; verify each service/item's terms, not just its title. |
| **OSNI** [S11] | Separate NI public mapping; OGL50k transport lines are available, including roads by class and railways. Can supplement regional validation. | GB OS products do not close NI gaps. Existing Roadprints review found the50k source insufficient for an authoritative numbered-road catalogue; do not repeat that trial without new attributes. Detailed-path commercial alternatives need a current product/licence check. |
| **Local-authority public rights of way** [S12] | Some councils publish OGL line geometry and legal-status information; useful to validate footpath/bridleway access in a bounded region. | Not uniform UK coverage, not all physical paths, not necessarily a routable graph. Online copies may explicitly be non-definitive. A public footpath does not automatically permit cycling. Check each authority's terms and legal record. |
| **Overture Transportation** [S13] | Open versioned segments/connectors, stable GERS-oriented identity mechanisms and source lineage; OSM supplemented by other contributors. Monthly global releases. | Transportation uses ODbL, not a blanket permissive licence for every theme. Much is OSM-derived; improved UK pedestrian geometry or NCN relations is unproven. GERS is not an automatic OSM node-chain crosswalk. Lower-priority bounded trial after source-version audit. |

The official WWCT WFS item `4265b78e4e9048aab0f36085414a8849`, owner `SustransGIS_Public`, was read directly from ArcGIS metadata. A different similarly named third-party ArcGIS service carries restrictive redistribution wording. That is a warning to pin the actual service/item licence, **not evidence that the pinned official source has lost its open licence**. No third-party restricted geometry was copied into the report.

OS detailed products are not covered merely because OS Open Roads is open. A time-limited Data Exploration Licence permits evaluation subject to its terms, not unrestricted production redistribution. Obtain written scope for server processing, derived progress denominators, cached SQLite, Firebase hosting, attribution and retained historical evidence before adopting commercially licensed geometry. No cost or royalty estimate is defensible without a quote.

## 5. Bounded representative comparisons

This is a purpose-led cause/environment sample, not a national accuracy estimate. Historical classification examples identify the target under examination; their current post-seam binding status was not individually recompiled. Positive/negative regression fixtures are retained controls. All eight named source ways below were independently found in live walking-reference SQL.

For three bbox examples,101 equally spaced points along the pinned target segment were compared to current OSM segments using an equirectangular local projection with Earth radius6,371,008.8 m and latitude-dependent longitude scale; each point's minimum distance to finite way segments was measured. Points include both endpoints at1% increments. “Within10 m” is a diagnostic point count, **not exact overlap length, route correspondence, access eligibility or completion credit**. Exact target coordinates, selected way tags, explicit returned relation memberships, query URLs and capture hashes are in the evidence JSON. Existing graph data and current OSM are related sources, not independent surveys.

| Case / environment | Roadprints physical evidence | Alternative/source identity evidence | Required investigation; confidence |
|---|---|---|---|
| **NCN9 Newry Canal Towpath**, NI traffic-free shared-use ordinary seam; OSM59306268, source section59306268:4:27 | Live walking reference contains all28 vertices; pinned fixture/current OSM geometry identical | Current OSM v12,12 September2023; designated foot/cycle asphalt towpath | Already corrected ordinary seam is a positive control. No new geometry required for this correction. High confidence in geometry presence; no independent OSNI geometry comparison. |
| **NCN8 rural mid-Wales road**, OSM31252441:47:55 near52.48,−3.68 | Live/pinned/current full geometry identical,56 vertices | Current OSM v8,18 October2025, unclassified asphalt road | On-road ordinary-seam positive control; attribution/compiler correction, not missing road. High. |
| **NCN81 hairpin/return**, West Midlands OSM44807950 | Live/pinned/current38-vertex geometry identical | Current OSM v13,18 December2024, unpaved footway with bicycle permission | Preserve hairpin/parallel negative control; more geometry or a wider radius would not justify joining return arms. High presence; safe correction not established. |
| **NCN1 Mudlarks Boulevard**, urban London OSM1050757473 | Live/pinned/current3-vertex geometry identical | Cycleway with designated walking/cycling, current v2,9 September2022 | Source-offset regression control; preserve admitted section1050757473:1:2 without crediting unadmitted prefix. High. |
| **Historical NCN6 no-admitted-source**, Cumbria target f4db91e0… (full key in JSON),1,040.873 m | OSM23385711 is already live with9 vertices | Current tertiary asphalt road;101/101 target samples within10 m. Returned bicycle relation11017 identifies **NCN7**, not6 | Physical road exists. Resolve route identity/date and source correspondence before attributing; do not infer published NCN6 is wrong or that NCN7 cannot overlap. High physical presence, medium/unknown identity. |
| **Historical NCN66 direction rejection**, Bradford Centenary Square target217a655a…,54.085 m | Live way159311690 present,8 vertices | Designated foot/cycle path;101/101 samples within10 m; explicit NCN66 relation17883, whose notes describe local route uncertainty | Inspect source/target segment directions and topology under unchanged15° gate. No automatic credit. High presence, medium alignment hypothesis; exact post-seam status unknown. |
| **Historical NCN1 unadmitted-known-way**, Deal/Wellington Parade targeta25d7e26… | Live OSM4471747 present,14 vertices | Designated cycleway, explicit NCN1 relation75880;25/101 target samples within10 m. Nearby private residential way77611523 has0/101 and minimum sampled distance10.55 m | Requires admitted extent/offset/alignment review, not a blanket445.799 m recovery claim. Private parallel road is a negative control. High presence, medium route-extent evidence. |
| **RCN90 no-binding control**, East Sussex OSM1289002662 | Live3-vertex cycleway exists, but stored geometry differs from current/pinned geometry | Current v2 dated7 October2026, after September walking load; corresponding vertex displacement up to1.51 m. Current note says proposed NCN90 | Version/crosswalk investigation first; keep strict geometry-hash contract. Proposal note does not establish published membership. High mismatch evidence; no recoverable metres claimed. |

All sample quantities refer to full ways or specified target intervals, not a national estimate.

Seven sampled live geometries match the current OSM geometry checked; the eighth,1289002662, matches neither current/pinned geometry and has older loaded coordinates. This supplies a concrete snapshot reconciliation problem. It does not justify loosening source hashes: doing so could transfer evidence across edits, moved crossings or access changes.

No OS NGD/MRN or Overture sample features were acquired for these places. Their physical superiority at these locations is **untested**. The current comparison can establish OSM presence and identity evidence, not claim an independent cartographic survey confirmed each path on the ground.

## 6. Architecture options and practical trade-offs

| Criterion | A — retain and improve current references | B — targeted supplements | C — reconciled national physical network |
|---|---|---|---|
| Coverage/accuracy | Addresses demonstrated admission, snapshot and compiler issues; physical omissions remain possible | Adds geometry/topology only where a bounded comparison proves it helps | Potential common baseline, but no measured national benefit or completeness guarantee |
| Matching / false attribution | Existing mode policies and negative controls retained | Quarantine new features; explicit provider/feature/version crosswalk; no nearest-line identity joins | Must rebuild mode-specific graphs, junction/access rules and evidence mappings; a single graph cannot erase mode differences |
| Identity and history | Smallest migration; preserve current NCN/road/settlement IDs | Provider IDs coexist; changes require versioned many-to-many mappings | Large migration across saved node chains, geometry hashes, road names and denominator versions; risk to historical credit |
| National preprocessing | Current bounded compilers and pinned rebuilds | Regional ingestion, conflict review and new crosswalk validation | Conflation, topology repair, grade separation, access policies and national rebuild infrastructure; cost unmeasured |
| Android storage/performance | Existing compact assets/cache contracts | Prefer server-side supplements and existing delivery schemas; measure changed binding/index bytes | Shipping a complete graph is not required; if chosen, size, RAM, indexing and update latency need measurement |
| Firebase/bandwidth | Existing NCN refresh already approximately142.8 MB compressed | Incremental/sharded catalogues might help, but not implemented or measured | New graph plus catalogues may duplicate bytes; no evidence a unified source reduces delivered size |
| Licence/operations | Existing OSM/OGL/source-specific obligations still require a receipt | Mixed source licences, redistribution scope, attribution and recurring commercial cost | Highest conflation/licence risk; clear separability and rights must be designed, not assumed |
| Updates | Improve exact snapshot receipts and active router provenance | Staggered providers require explicit “compatible evidence” windows | Shared snapshot does not eliminate upstream timing differences or identity changes |
| Reproducibility/rollback | Existing immutable inputs and old catalogues retained | Pin source, conversion, crosswalk, tests and output hashes per release | Must version raw inputs, conflation decisions, mode graphs and identity migration; rollback must preserve old evidence semantics |

**Prefer A now, with a gated B experiment.** Introduce a consistent source provenance/identity registry before a shared replacement geometry: provider, release/content hash, feature/version, original node chain where applicable, geometry hash, mode/access policy, and explicit crosswalk status. This could improve auditability across all features without changing matching or prematurely merging denominators.

If C is reconsidered later, its target should be a reconciled physical layer with separate driving/walking/cycling eligibility graphs and feature-specific attribution policies. NCN route identity, settlement name counts and official road numbers remain separate overlays. A clean diagram alone does not justify the migration.

No processing, memory or hosting price is estimated: those require a representative build and provider quote. As an arithmetic illustration only,100 full142.8 MB catalogue downloads transfer approximately14.28 GB before other traffic; this is not measured current usage or an invoice. Avoid repeated whole-catalogue refreshes merely to change a small binding set.

## 7. Smallest safe next experiment

1. **Reconcile versions first.** Preserve the existing eight-way evidence; confirm regional PBF checksums/OSM timestamps and the NCN source capture dates, explain1289002662's update, and obtain active foot/bicycle/car graph build/profile provenance where the services expose it. A literal “latest” or retrieval date is insufficient. Do not infer absent cycling geometry from the walking database.
2. **Use five bounded cells**, with the Newry seam and Wolverhampton hairpin controls plus the Cumbria NCN6/7 identity discrepancy, Bradford direction case and Deal cycleway/private parallel case. Add the East Sussex version mismatch as a provenance test. The public OSM bbox captures already taken total less than1 MB; no national extract is needed.
3. **Compare one detailed alternative only if rights and sample coverage permit.** Prefer an OS MRN/NGD small GB sample with a clear evaluation licence; retain Newry as the NI control and record “no GB coverage” rather than dropping it. Local PRoW may independently check access for a suitable rural cell. Do not pay for or download a national dataset merely to establish sample coverage.
4. **Produce a sidecar comparison**, not a new catalogue or changed matcher. Record physical feature existence, connectivity/grade separation, legal mode support, signed NCN membership, provider IDs and geometry direction separately. Keep unchanged10 m/15° checks and deferred masks. Require explicit correspondence before counting a candidate interval.
5. **Acceptance evidence:** new eligible physical links absent from existing applicable graphs, or demonstrably corrected correspondence/topology, each independently reviewed; zero extra credit on private/parallel/hairpin/proposed controls; exact source hashes; reproducible transformations; bytes/processing/memory measurements; estimated licence/hosting cost at actual scale. Report “no benefit demonstrated” if that is the result.

Only after that experiment should a development task propose new ingestion or consumer contracts. No Android code change or APK release is required for this research. A future source substitution may require them, especially if identifiers/evidence contracts change; data-only compatibility cannot be assumed.

Further **Stage1.1 attribution corrections should proceed independently**, using pinned route identity and source-supported finite geometry, focused positive/negative tests and GitHub checkpoints. This investigation authorises no tolerance changes, branch/hairpin recovery, deferred evidence admission or Stage1.2 work.

## 8. Outstanding evidence and decisions

- Exact post-seam cause partition: needs interval-level reconciliation using the existing candidate, not another candidate build. Current historical-region bounds are the limit here.
- National missing-physical-geometry fraction: unknown until applicable frozen road/foot/bike source graphs are compared against specifically unbound targets; the eight samples cannot quantify it.
- Active external graph snapshots/access policies and update commitments: unknown. Public service dependence also needs an operational service/capacity decision before production architecture changes.
- OS MRN/NGD incremental geometry, access quality, local correctness and licence cost: untested/unquoted; OSNI detailed coverage needs separate review.
- Current source attribution/legal inventory should pin exact dataset-specific terms and supplied acknowledgement strings; mixed-source derived databases need appropriate licensing review.
- National build cost, Android footprint, cache migration, Firebase download frequency and rollback cost: measure a small prototype, then extrapolate transparently.
- Existing seam candidate remains unpublished. Any future release requires the separate catalogue publication/consumer validation process and explicit authorisation; this report does not change that status.

## Repository reference index

All main links pin `1030b726c4640e04a9c6051667886812daf39fcc`; historical research pins are explicitly different.

- **R1:** [OS A-road builder](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/scripts/build_os_open_roads_a_cache.py), [v5 index](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/canonical-a-roads-v5/index.json).
- **R2:** [motorway builder](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/scripts/build_motorway_cache.py), [reference](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/canonical-motorways-v1.json).
- **R3:** [API router configuration and matching](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/api.py#L34).
- **R4:** [walking import](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/pedestrian_reference.py#L59), [database matcher](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/database.py#L537), [trajectory matching](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/pedestrian_matching.py).
- **R5:** [cycling evidence contract](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/cycling_path_evidence.py).
- **R6:** [NCN pipeline](https://github.com/adamdbird-gfc/uk-road-tracker/tree/1030b726c4640e04a9c6051667886812daf39fcc/scripts/ncn_trial), [national builder](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/scripts/build_ncn_national.py).
- **R7:** [merged validation report](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/ncn-ordinary-seam-validation-20261030.md), [machine receipt](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/data-trials/ncn-ordinary-seam-validation-2026-10-30.json), [PR242](https://github.com/adamdbird-gfc/uk-road-tracker/pull/242).
- **R8:** [Android consumers directory](https://github.com/adamdbird-gfc/uk-road-tracker/tree/1030b726c4640e04a9c6051667886812daf39fcc/android-prototype/app/src/main/java/com/roadprints/capture): ARoadProgressCalculator.java, MotorwayProgressCalculator.java, ProgressActivity.java#L444, PublicReferenceData.java.
- **R9:** Same pinned directory: NcnMatcher.java, NcnLedger.java, NcnCatalogue.java, NcnCatalogueFiles.java; [NCN offset/seam regression fixtures](https://github.com/adamdbird-gfc/uk-road-tracker/tree/1030b726c4640e04a9c6051667886812daf39fcc/tests/fixtures).
- **R10:** [GB inventory candidates](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/gb-road-inventory-candidates.md), [NI publication](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/ni-publication.md).
- **R11:** Android LocalRoadSettlementMatcher.java in R8; [reference catalogue status](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/shared-reference-catalogue-status.md) is historical and must be read with later NI publication.
- **R12:** [reference research documentation](https://github.com/adamdbird-gfc/uk-road-tracker/tree/1030b726c4640e04a9c6051667886812daf39fcc/docs); Android PublicReferenceData.java and county consumers in R8. County source: [Historic County Borders](https://county-borders.co.uk/UKDefinitionA_WG84_Full_Resolution.zip).
- **R13:** [PR240](https://github.com/adamdbird-gfc/uk-road-tracker/pull/240), research pin `21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7`; [classifier](https://github.com/adamdbird-gfc/uk-road-tracker/blob/21ab3fb2951c2d6539c37a0387a7c2887e7e6fe7/scripts/classify_ncn_binding_gaps.py). Original classified inventory hash `1ebb14d7489e5add77dac77dcb25c5beda2ce53c7c3994eccc22696944955dcb`.
- **R14:** [PR241 feasibility](https://github.com/adamdbird-gfc/uk-road-tracker/blob/24428cb4ad35e563113d7a86f7edc13652dc2844/docs/ncn-shared-matching-feasibility.md), pin `24428cb4ad35e563113d7a86f7edc13652dc2844`.
- **R15:** [official NCN source determination](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/ncn-zero-association-determination.md), [named-route reconciliation](https://github.com/adamdbird-gfc/uk-road-tracker/blob/1030b726c4640e04a9c6051667886812daf39fcc/docs/ncn-named-route-reconciliation.md).

## External primary sources

Accessed30 October2026. Product marketing establishes advertised capability, not demonstrated Roadprints accuracy.

- **S1 OSM:** [copyright and ODbL](https://www.openstreetmap.org/copyright), [attribution guidelines](https://osmfoundation.org/wiki/Attribution_Guidelines), [collective database guidance](https://osmfoundation.org/wiki/Licence/Community_Guidelines/Collective_Database_Guideline_Guideline).
- **S2 Geofabrik:** [downloads](https://www.geofabrik.de/data/download.html), [extract update details](https://download.geofabrik.de/technical.html).
- **S3 OSM feature evidence:** [official API](https://api.openstreetmap.org/api/0.6/way/1289002662/full.json); other exact URLs and bbox requests are in the receipt. These reads are evidence, not a bulk data redistribution service.
- **S4 OS Open Roads:** [product](https://www.ordnancesurvey.co.uk/products/os-open-roads), [specification/suitability](https://docs.os.uk/os-downloads/products/transport-network-portfolio/os-open-roads), [open data terms overview](https://www.ordnancesurvey.co.uk/products/open-data).
- **S5 OpenMap Local:** [product](https://www.ordnancesurvey.co.uk/products/os-open-map-local), [technical specification](https://docs.os.uk/os-downloads/products/maps-and-imagery-portfolio/os-openmap-local/os-openmap-local-technical-specification).
- **S6 OS detailed transport:** [product](https://www.ordnancesurvey.co.uk/products/os-transport-network), [NGD transport structure](https://docs.os.uk/osngd/data-structure/transport/transport-network), [Path Link capture rules](https://docs.os.uk/osngd/data-structure/transport/transport-network/path-link).
- **S7 RAMI:** [routing/restriction product](https://www.ordnancesurvey.co.uk/products/os-routing-asset-management-information).
- **S8 OS licensing:** [exploration licence](https://www.ordnancesurvey.co.uk/licensing/data-exploration-licence), [partner licensing](https://www.ordnancesurvey.co.uk/customers/os-channel-partners/partner-licensing-guidance), [public task](https://www.ordnancesurvey.co.uk/governance/public-task).
- **S9 OS MRN:** [product](https://www.ordnancesurvey.co.uk/products/os-multi-modal-routing-network), [GB overview and identities](https://docs.os.uk/os-downloads/products/transport-network-portfolio/os-multi-modal-routing-network/os-mrn-overview), [OSM schema meaning](https://docs.os.uk/os-downloads/products/transport-network-portfolio/os-multi-modal-routing-network/os-mrn-overview/openstreetmap-schema).
- **S10 Official NCN:** [WWCT network](https://www.walkwheelcycletrust.org.uk/national-cycle-network/), [official WFS item and licence metadata](https://www.arcgis.com/sharing/rest/content/items/4265b78e4e9048aab0f36085414a8849?f=json), [actual public target service](https://services5.arcgis.com/1ZHcUS1lwPTg4ms0/arcgis/rest/services/National_Cycle_Network_Public/FeatureServer/0?f=pjson).
- **S11 NI:** [OSNI open product list](https://www.nidirect.gov.uk/articles/osni-open-data-product-list), [50k transport metadata](https://www.data.gov.uk/dataset/828a8d26-b199-4620-9e02-b61557792500/osni-open-data-50k-transport-transport-lines).
- **S12 Rights of way:** [Kent map limitations](https://www.kent.gov.uk/environment-waste-and-planning/public-rights-of-way/rights-of-way-map), [government access categories](https://www.gov.uk/right-of-way-open-access-land/use-public-rights-of-way).
- **S13 Overture:** [transportation](https://docs.overturemaps.org/guides/transportation/), [attribution/licences](https://docs.overturemaps.org/attribution/).

