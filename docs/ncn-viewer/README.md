# Offline NCN visual diagnostic viewer

A read-only developer tool for the scope authorised on 30 October 2026 (parent #79; evidence/release tracking #190 and #194). Start at **[demo/index.html](demo/index.html)**. Download the file and open it in a modern browser. It includes all geometry, measurements, styles and scripts; it works under `file://`, without a server, authentication or internet access. GitHub's file page displays source, so use **Download raw file** before opening it.

Development baseline: `main` at `a87d60d4d5b2c4651c571e7f4316e514154734eb`. Main and validated #247 head `efca603f73f6877fb64d3c8bf37796ee31f2635e` share tree `c7e0ba326b82b72c2e4f05cff1d2f2991e5d94c3`. This tool changes no admission, completion, matching, Android or catalogue behaviour. Installed APK, live backend and Firebase reference versions were not queried and remain unknown; source baseline 0.25.220 (266) is not a device/deployment claim.

## First demonstration

1. Open `demo/index.html`. Deal NCN1 and excluded edge 7 are selected initially.
2. Select the red edge or its red timeline interval. The entire timeline represents the pinned source way, with exact historical EQ offsets from vertex 0, not invented NCN1 national chainage.
3. Choose **Locate selection**, then **40 m close-up**. The gold admission corridor and cyan completion corridor differ geographically even though both use a numeric 10 m buffer. The red path lies outside the gold corridor and inside the cyan one. Toggle the corridors independently to inspect their boundaries.
4. Read the adjacent cards: the whole-edge corridor test fails in native admission space and passes hypothetically in completion space. The common local diagnostic also fails. The operational outcome stays **excluded**. A whole-edge corridor comparison and a clipped finite-pair comparison are different predicates; both are labelled and recorded.
5. Select edges 6–10 in turn: these are the five pinned projection-dependent corridor differences. Edge 10 continues beyond the finite investigated target, so its endpoint maximum distance is not the union-corridor test.
6. Select **77611523**, Wellington Parade. Its minimum distance crosses 10 m between projections, but private access, distinct identity and absent confirmed route membership keep it excluded.

**Demonstrated additional eligible recovery: 0 m.** The historical 445.799 m geometric ceiling is not eligible recovery. The tool neither assesses actual journeys nor creates completion credit.

The source selector and timeline support keyboard selection. Map clicks choose the nearest selectable source/control geometry within a screen tolerance; at overview zoom close parallel paths may be difficult to distinguish, so zoom in or use the selector. Selection restores its layer if hidden. Drag to pan; use the wheel or +/− to zoom; **Study extent** fits the bounded source study. A geographic grid replaces background tiles. Colours and line patterns distinguish source statuses, target geometry and controls.

## Additional pinned cases

The selector includes Newry NCN9, Mid-Wales NCN8, West Midlands NCN81, London NCN1, Bradford NCN66, Cumbria NCN6/NCN7 and East Sussex **RCN90**. These use the preserved #247 case fixture and receipt without rerunning research. They show source/reference chains and bounded target pairs where available, with native-origin and local sensitivity measurements from that receipt.

Their whole-way/per-edge operational admission was not replayed: their source distance bars explicitly show **evidence unavailable**, rather than extending a partial admitted-source observation to the whole chain. Newry, Wales and NCN81 route origins are explicitly **inferred** from pinned witness endpoints. Bradford and Cumbria original operational route origins remain **unavailable**; local comparisons do not masquerade as operational replays. East Sussex has no pinned target geometry, which is shown as unavailable. No missing route identity, target, coordinates or status is fabricated.

No deferred source occurs in the Deal study corridor; the nearest pinned deferred section is roughly 96 km away and is not drawn. The excluded Deal edges retain an audit reason of `unresolved_official_extent_edges`; they are shown as operationally excluded/held, not as admitted or operationally deferred.

## Generate and validate

The committed standalone HTML needs no dependencies. Regeneration uses the repository's pinned research functions and Python 3.12 with Shapely 2.2.0. From the repository root:

```sh
python -m pip install shapely==2.2.0
PYTHONPATH=. python docs/ncn-viewer/generate.py
# Optional different output directory:
PYTHONPATH=. python docs/ncn-viewer/generate.py --output /tmp/roadprints-ncn-viewer
# Open /tmp/roadprints-ncn-viewer/index.html in your browser.
PYTHONPATH=.:tests python docs/ncn-viewer/test_viewer.py -q
```

The generator writes only the chosen output directory (`evidence.json`, `index.html`). It verifies immutable SHA-256 pins before consuming evidence or reproduction code. Diagnostic generation is separate from the HTML presentation. The browser consumes only the generated contract; it never calculates eligibility.

Existing relevant regression checks (test-only dependencies `psycopg[binary]==3.2.10`, `osmium==4.3.1` in addition to Shapely):

```sh
PYTHONPATH=.:tests python -m unittest scripts.test_ncn_source_compatibility \
  scripts.test_ncn_binding_attainability scripts.ncn_trial.test_open_completion \
  scripts.ncn_trial.test_ordinary_seams scripts.ncn_trial.test_network_geometry \
  scripts.ncn_trial.test_saved_paths scripts.ncn_trial.test_cycling_saved_paths -q
PYTHONPATH=.:tests python -m unittest discover -s tests -p 'test_*path_evidence.py' -q
```

Browser acceptance, with Node, Playwright and an installed Chromium:

```sh
node docs/ncn-viewer/browser-test.cjs
# Optional installed binary / screenshot output:
NCN_CHROMIUM=/path/to/chromium NCN_SCREENSHOTS=/tmp/ncn-screenshots \
  node docs/ncn-viewer/browser-test.cjs
```

Install Playwright and a compatible browser separately if needed. The test opens the committed HTML with `file://`, puts the browser context offline, blocks HTTP(S), checks case/layer controls, geometric clicks, two-way map/timeline selection, keyboard selection, pan/zoom, missing provenance, private exclusion and narrow-screen overflow, and captures screenshots. `NCN_CHROMIUM_ARGS_JSON` can provide a JSON array of launch arguments for specialised environments. No browser dependency is shipped to users or added to production.

## Version 1 evidence contract and adding cases

`demo/evidence.json` is the compact versioned JSON contract, also embedded into the standalone HTML. Top level: `contract`, `schema_version`, immutable `baseline`, `input_sha256`, `reproduction_code_sha256`, `crs`, `diagnostic_only`, `cases`. Each case defines its identity, geographic features, timeline distance basis/extent, default selection, summary and limitations. Features follow GeoJSON geometry/feature conventions with additional case metadata.

Each feature preserves source/route IDs, coordinate provenance, source reference, geometry checksum (with its basis where different), status, reason code and limitations. Source edges additionally preserve OSM node IDs, original source hash, exact source station interval, operational correspondence receipt, per-space measurements/origins/provenance, thresholds and predicates. Whole-edge corridor results and finite-pair clipping remain separate fields. Case receipts retain full finite-pair distances, angles, source/target offsets and topology results.

To add another case:

1. Preserve bounded immutable evidence in the repository: WGS84 geometry or a documented reversible transform of pinned metric geometry, source/route identities, actual statuses and decision receipt where available. Include provenance/licence limitations. Do not download national references to populate this viewer.
2. Pin its input hash in `generate.py`. Extend `build()` to extract features and intervals from that evidence; name the station basis explicitly. Unknown origins, missing targets and unsupported decisions must remain unavailable. Derive polygon rings only through documented projections/inverses; never approximate missing geography with a schematic.
3. Use existing layer names, or add a presentation legend/style for a new evidenced layer. Add schema-contract and browser coverage for new predicates or interactions. Do not introduce a browser eligibility algorithm.
4. Regenerate, verify the original cases and hashes, run regression/browser checks, inspect rendered geometry, and review the versioned contract change. Bump `schema_version` for an incompatible contract.

## What this enables next

Deal makes the distinction between corridor scale, finite-target extent and actual admission visible. It supplies no evidence to override the five held edges or private control. The extra controls demonstrate positive correspondence, hairpin/topology rejection, source extent, direction rejection, route identity ambiguity and source-version differences; none establishes new eligible recovery.

Use this viewer to triage **one bounded unbound target with an already admitted, identity/access-supported source**, then investigate only the specific missing correspondence the picture reveals. No current displayed case establishes a new correction opportunity. Until such a witness exists, the next concrete task is a **publication-readiness review of the already validated 45.834 km ordinary-seam correction**: confirm source/candidate pins, consumer compatibility, versioning, delivery/rollback and acceptance gates. This recommendation does not authorise publication, deployment, a national assessment or an APK.

Attribution: OpenStreetMap contributors, ODbL 1.0; published extent Walk Wheel Cycle Trust, OGL 3.0 with original source attribution retained in the pinned evidence. The map is a diagnostic display, not surveyed positional truth or a current navigation product.
