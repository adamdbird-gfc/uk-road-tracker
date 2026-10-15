# NCN confirmation and regional trial

15 October 2026. Development experiment in draft PR #165; no app integration,
APK, deployment, Firebase publication or database writes.

## Results

52 unit tests pass: 20 original coverage checks, 21 section-confirmation checks,
6 regional checks (each exercises the three regional fixtures), and 5 independent
OSM geometry checks. The dedicated PR workflow runs the same suite.

The confirmation experiment preserves captured fixes and provisional core paths.
A supported NCN choice credits only the uncertain window; choosing a parallel
non-NCN road leaves NCN credit at zero. An independently eligible road can earn
credit. Not sure remains pending. Decisions survive JSON replay without repeated
prompts. Changed evidence, candidate geometry or source version is held for
reconciliation. Driving, gaps and poor accuracy cannot be rescued by confirmation.
Other supported windows retain their unique coverage after a correction.

Checksum-verified regional source subsets exercise road cycling and traffic-free
walking, mode separation, repeats, replacement and deletion:

| Area | Route | Features | GeoJSON bytes | Physical edges |
| --- | --- | ---: | ---: | ---: |
| Belfast/Lagan | NCN 9 | 22 | 28,285 | 622 |
| Cardiff | NCN 8 | 19 | 10,387 | 155 |
| Glasgow | NCN 7 | 24 | 14,899 | 247 |

Three independently mapped OSM ways near Gravesend (85488624, 554498838,
4973301) were verified as members of NCN relation 13512082 via their parent
relation responses, including member identities. Synthetic 80 m traces along
those OSM geometries yield approximately 79.16, 79.95 and 79.99 m of source
coverage. These are independent geometry alignment checks, not captured GPS,
production matcher output, legal-access verification or full-route acceptance.

## Sources and limits

Public NCN source and regional fixture checksums, bounds and edit provenance are
in each source.json. Preserve OGL/Ordnance Survey notices and the NI
OpenStreetMap/ODbL notice. The separate OSM context fixture retains ODbL attribution,
query URLs and response hashes; its licence is not replaced by the NCN licence.

The first trial's negative control still stands: proximity alone can award false
credit when competing paths are absent. Do not promote this experiment into a
production matcher. The confirmation factory accepts only exact verified source
associations; cross-source conflation remains a separate gate. Duplicate physical
geometry is coalesced before offering a choice.

Absence from one OSM relation does not establish that an adjacent pavement is
ineligible, particularly when walking an on-road NCN corridor. Corridor semantics,
activity access and source membership need an explicit rule before production.

Next: validate real captured cycling/on-foot traces with competing-path evidence,
reconcile persistent source identities across updates, then discuss map/review UI
with Adam and integrate core/collection progress. Marker permissions and disc-to-
verse verification remain separate. Coordinate #163 and #164 before one combined
ready-slice APK; this trial does not establish either dependency as merged.
