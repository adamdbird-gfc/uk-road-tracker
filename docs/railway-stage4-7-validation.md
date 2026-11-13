# Stage 4.7 railway validation

## Baseline

Build 0.25.237 (283) was signed from `2f96b5c6` in draft PR #313. Draft PR #314 adds the reassessment responsiveness work at `724e69c7`, which already contains #313. Stage 4.7 branches from that commit. Main was `55e9a2a5` at intake. Integrate the reviewed railway changes in dependency order, or consolidate the stack onto current main after owner review and rerun validation on the integrated head. No integration or deployment is part of the development checkpoint.

## Source and eligibility

The pinned Geofabrik UK extract `united-kingdom-261112.osm.pbf` includes England, Scotland, Wales and Northern Ireland. See the bundled catalogue manifest for source and database SHA-256. Geometry and road comparisons are © OpenStreetMap contributors under ODbL 1.0. The complete corresponding database ships as a compressed SQLite asset. The compiler can reproduce it from the dated extract. [Source](https://download.geofabrik.de/europe/united-kingdom.html) · [Licence](https://www.openstreetmap.org/copyright)

Track must be active `railway=rail` geometry. UK passenger train relation membership supplies the primary eligibility evidence. Northern Ireland also uses 1600 mm passenger running tracks and crossovers explicitly attributed to Translink NI Railways in OSM. Cardiff Canton has seven reviewed Network Rail ways that reconnect the South Wales Main Line. Four Northern Ireland station attachments are pinned to existing mapped platform track vertices. The graph joins shared source nodes and attaches stations at represented passenger stop nodes. A nearby station point can supply the label for a stop attachment. Missing route members, stop positions and shared-node connections leave gaps. The snapshot does not establish when tracks or stations opened or changed. Projected coordinates support proximity calculations. The distances are WGS84 ellipsoidal segment sums, with precision limited by source survey, geometry density and attachment placement. Tunnel and bridge track geometry retains its connectivity. There is no synthetic track between terminals or across water.

Independent Northern Ireland checks use the Northern Ireland Railways Network Statement and official OpenDataNI railway network. The latter predates Belfast Grand Central's opening. The current statement identifies Grand Central and the Bangor, Larne, Derry–Londonderry, Portrush and Newry corridors. [Network statement](https://www.translink.co.uk/getmedia/75f785a5-032e-4327-b11b-bbf683fe5e28/Network_Statement_2025.pdf) · [OpenDataNI geometry](https://www.data.gov.uk/dataset/0297b4a0-5cea-4c22-97cc-346c76df33ed/northern-ireland-railways-nir-railway-network)

## Private owner replay

| Case | Evidence and disposition |
| --- | --- |
| 6 November, Gravesend to Charing Cross | Existing accepted Sidcup route still yields 39,125.162 m. Independent engineering chainage is about 39,228 m. The saved change was −4,773.769 m. |
| 28 October, same corridor | Existing accepted Sidcup route still yields 39,125.162 m. The saved change was +90.314 m. |
| 2 November, 07:46–08:41 | The first usable GPS fix is 5 seconds after the saved start, near Greenhithe. Gravesend departure is outside the observed recording. The original corridor matcher declines its endpoint pair. |
| 14 October, 07:43–08:51 BST | The first usable legacy fix is about 1.4 km from Gravesend station. The original corridor matcher declines its endpoint pair. |
| 5 October, Charing Cross to Gravesend | Private source export needed to independently replay the reported 21.8-mile imported journey. |
| 9 November fragments | The two supplied exports share the same GPS fix at 18:24:14 UTC. The second continues beyond Gravesend station. There is no sufficient basis in those two files to restore the earlier missing section, merge archived rows or assert exact boarding and alighting. |

The two available Build 283 commit receipts account for −4,683.455 m (−2.910 miles) combined. Progress rose by a reported 332.6 miles. Per-journey evidence for the rest requires before/after archive rows or commit receipts from the device. The observed totals alone do not identify the source of the change.

## Representative route checks

The host replay uses synthetic observations sampled from the mapped track and omits fixes in tunnels. This verifies the matcher, topology and bidirectional behaviour. The track used to sample observations is separate from the independent distance references below.

| Corridor | Host result, both directions | Independent distance check |
| --- | --- | --- |
| Gravesend–St Pancras via HS1 | 39.539 km | Approx. 39.948 km from the Springhead junction chainage and Gravesend's HDR mileage. Difference −409 m. Platform, terminal and junction track selection remain under review. |
| St Pancras–Sheffield via Derby | 264.841 km | Approx. 264.335 km using the Clay Cross mileage reset and Sheffield platform 4. Difference +506 m. Platform choice and the route through Derby affect this comparison. |
| King's Cross–York, East Coast Main Line | 303.098 km | 303.361 km using York ECM4 chainage from King’s Cross. Difference −263 m. Parallel-platform paths are treated as one corridor. |
| Euston–Birmingham New Street via Northampton | 185.374 km | Approx. 185.577 km using the HNR/Rugby datum offset and RBS1 chainage. Difference −203 m before the Euston platform datum allowance. |
| Paddington–Cardiff via the Severn Tunnel | 233.425 km | 233.516 km via Patchway and Severn Tunnel junction chainages. Difference −91 m. |
| Cardiff–Swansea | 73.556 km | 73.748 km from Network Rail's Cardiff sectional appendix and Swansea engineering mileage. Difference −192 m. |
| Glasgow Queen Street–Edinburgh Waverley | 75.967 km | Approx. 76.102 km using the EGM2/EGM3 change at Haymarket. Difference −134 m. |
| Inverness–Kyle of Lochalsh | 132.565 km | 132.791 km using WCK and KYL at Dingwall. Difference −226 m. |
| Belfast Grand Central–Bangor | 22.997 km | Operator route verified. Independent platform-to-platform distance pending. |
| Belfast Grand Central–Derry~Londonderry | 154.100 km | Operator route verified. Independent platform-to-platform distance pending. |
| Belfast Grand Central–Larne Harbour | 43.672 km | Operator route verified. Independent platform-to-platform distance pending. |
| Belfast Grand Central–Newry | 69.628 km | Operator route verified. Independent platform-to-platform distance pending. |
| Coleraine–Portrush | 9.464 km | Operator route verified. Independent platform-to-platform distance pending. |

Sources: [HDR mileage](https://www.railwaycodes.org.uk/elrs/_mileages/h/hdr.shtm), [TRL engineering references](https://www.railwaycodes.org.uk/elrs/elrt.shtm), [St Pancras station mileage](https://www.railwaycodes.org.uk/stations/stationl.shtm), [SPC9 mileage reset](https://www.railwaycodes.org.uk/elrs/_mileages/s/spc9.shtm), [Sheffield platform reference](https://www.contractsfinder.service.gov.uk/Notice/Attachment/10d77875-3885-4879-8d10-dedd42648cc2), [Network Rail Cardiff sectional appendix](https://sacuksprodnrdigital0001.blob.core.windows.net/sectional-appendix/Periodic%20Operating%20Notice%20and%20Sectional%20Appendix%20December%2025/Western%20%26%20Wales%20PON%20December%202025.pdf), [Swansea station mileage](https://railwaycodes.org.uk/stations/stations.shtm), [EGM references](https://railwaycodes.org.uk/elrs/elre.shtm), [Translink network statement](https://www.translink.co.uk/getmedia/75f785a5-032e-4327-b11b-bbf683fe5e28/Network_Statement_2025.pdf).

The first national replay proposed station-to-station distances for two clipped recordings, 2 November and the earlier 9 November fragment. Their boundary fixes did not establish those stations as trip endpoints. The stricter attachment check now leaves both partial, along with the 14 October and second 9 November cases. The two verified Sidcup journeys retain 39,125.162 m.

## Release gates

| Gate | State |
| --- | --- |
| Preserved Build 283 and PR #314 implementation | Pass. Confirmed ancestry and existing verified Sidcup results. |
| Representative national topology | Pass on the listed corridor sample. A complete audit of every eligible station and passenger route remains open. |
| Independent distance accuracy | Fail for release readiness. Independent checks still needed on multiple corridors, and HS1 terminal/track differences need review. |
| Road and ambiguity safeguards | Pass for synthetic parallel-road, ambiguous branch and clipped-endpoint controls. The genuine Milton Road drive remained partial in a forced-Train host replay and proposed no railway distance. |
| Historical writes, recovery and totals | Focused local archive tests pass. The Android runtime fixture now mixes 12 Sidcup and 12 Belfast–Bangor train rows in the 1,652-row archive. The hosted emulator result and per-journey reconciliation are pending. The complete Build 283 device archive delta remains unavailable. |
| Normal journey presentation and What's New | Pass in focused local Android JVM checks. The final run passed 37 tests, including SQLite catalogue opening, station attachments, the archive worker, mileage safeguards, navigation, Progress and What’s New. Android instrumentation also compiled. |
| Packaged asset, ANR, memory, battery and upgrade | Awaiting Android runtime evidence and signed build. Pixel 10 acceptance remains open. |
| Interchanges and route alternatives | Fail for release readiness. Dedicated transfer validation remains open, for separately recorded train legs. |

No APK packaging or publication should run from this checkpoint. Firebase promotion, production deployment and Train Collection work are outside this stage.
