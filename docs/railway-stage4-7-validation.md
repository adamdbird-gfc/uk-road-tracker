# Stage 4.7 railway validation

## Baseline

Build 0.25.237 (283) was signed from `2f96b5c6` in draft PR #313. Draft PR #314 adds the reassessment responsiveness work at `724e69c7`, which already contains #313. Stage 4.7 branches from that commit. Main was `55e9a2a5` at intake. Integrate the reviewed railway changes in dependency order, or consolidate the stack onto current main after owner review and rerun validation on the integrated head. No integration or deployment is part of the development checkpoint.

## Source and eligibility

The pinned Geofabrik UK extract `united-kingdom-261112.osm.pbf` includes England, Scotland, Wales and Northern Ireland. See the bundled catalogue manifest for source and database SHA-256. Geometry and road comparisons are © OpenStreetMap contributors under ODbL 1.0. The complete corresponding database ships as a compressed SQLite asset. The compiler can reproduce it from the dated extract. [Source](https://download.geofabrik.de/europe/united-kingdom.html) · [Licence](https://www.openstreetmap.org/copyright)

Track must be active `railway=rail` geometry. UK passenger train relation membership supplies the primary eligibility evidence. Northern Ireland also uses 1600 mm passenger running tracks and crossovers explicitly attributed to Translink NI Railways in OSM. Cardiff Canton has seven reviewed Network Rail ways that reconnect the South Wales Main Line. Four Northern Ireland station attachments are pinned to existing mapped platform track vertices. The graph joins shared source nodes and attaches stations at represented passenger stop nodes. A nearby station point can supply the label for a stop attachment. Missing route members, stop positions and shared-node connections leave gaps. The snapshot does not establish when tracks or stations opened or changed. Projected coordinates support proximity calculations. The distances are WGS84 ellipsoidal segment sums, with precision limited by source survey, geometry density and attachment placement. Tunnel and bridge track geometry retains its connectivity. There is no synthetic track between terminals or across water.

Independent Northern Ireland checks use the Northern Ireland Railways and Iarnród Éireann Network Statements and published engineering chainages. The separately catalogued OpenDataNI railway geometry predates Belfast Grand Central and its download returned HTTP 403 during final validation. The current statement identifies Grand Central and the Bangor, Larne, Derry–Londonderry, Portrush and Newry corridors. [Network statement](https://www.translink.co.uk/getmedia/75f785a5-032e-4327-b11b-bbf683fe5e28/Network_Statement_2025.pdf) · [OpenDataNI geometry](https://www.data.gov.uk/dataset/0297b4a0-5cea-4c22-97cc-346c76df33ed/northern-ireland-railways-nir-railway-network)

## Private owner replay

| Case | Evidence and disposition |
| --- | --- |
| 6 November, Gravesend to Charing Cross | Existing accepted Sidcup route still yields 39,125.162 m. Independent engineering chainage is about 39,228 m. The saved change was −4,773.769 m. |
| 28 October, same corridor | Existing accepted Sidcup route still yields 39,125.162 m. The saved change was +90.314 m. |
| 2 November, 07:46–08:41 | The first usable GPS fix is 5 seconds after the saved start, near Greenhithe. Gravesend departure is outside the observed recording. The original corridor matcher declines its endpoint pair. |
| 14 October, 07:43–08:51 BST | The first usable legacy fix is about 1.4 km from Gravesend station. The original corridor matcher declines its endpoint pair. |
| 5 October, Charing Cross to Gravesend | Private source export needed to independently replay the reported 21.8-mile imported journey. |
| 9 November fragments | The two supplied exports share the same GPS fix at 18:24:14 UTC. The second continues beyond Gravesend station. There is no sufficient basis in those two files to restore the earlier missing section, merge archived rows or assert exact boarding and alighting. |

Build 283 is reconciled with a historical evidence limitation. The supplied 13 November backup contains 1,652 saved journeys, including 197 Train journeys. Their effective distances sum to the saved Train total of approximately 5,104.13 miles. Two earlier Sheffield journeys sum to 332.61 miles. The two verified authoritative adjustments total −2.91 miles. Adding both terms to the earlier approximate 4,774.4-mile display gives 5,104.10 miles, within 0.03 miles of the backed-up total. The earlier complete Progress snapshot is unavailable, so prior inclusion of the Sheffield rows and the exact historical cause remain unproven. This comparison does not affect the release gate. Private exports remain outside the repository.

In later commits, the archive write stores the journey ID, original measurement, superseded and replacement distances, raw distance delta, Train and overall total deltas, route endpoints and path digest, catalogue and inference version, and commit time and outcome together. An initially missing distance contributes zero before migration. Existing receipts keep their original metadata. One persisted receipt describes the current migration, with the initial measurement retained for rollback. The worker's comparison output records reasons for rows that preserve their saved distance.

A fresh host replay of the available November HS1 and Sheffield exports preserved their saved distances. Their specific boundaries lacked a matching platform attachment or usable endpoint observation. Synthetic route recognition supplies coverage evidence for these corridors independently of those owner recordings.

## Representative route checks

The host replay uses synthetic observations sampled from the mapped track and omits fixes in tunnels. This verifies the matcher, topology and bidirectional behaviour. The track used to sample observations is separate from the independent distance references below.

| Corridor and alignment | Inferred geometry | Independent reference | Absolute difference (% of reference) | Disposition |
| --- | ---: | ---: | ---: | --- |
| Gravesend–Charing Cross via Sidcup | 39.125 km | 39.228 km | 0.103 km (0.26%) | Pass. Existing genuine accepted journeys and preserved regional matcher. |
| Gravesend–St Pancras via Springhead Road Junction and HS1 | 39.769 km | 39.773–39.875 km | 0.004–0.106 km (0.01–0.26%) | Pass for the domestic attachment replay. See chainage and terminal assumptions below. |
| St Pancras–Sheffield via Derby, Midland Main Line | 264.841 km | ≈264.335 km | 0.506 km (0.19%) | Pass at corridor scale. Clay Cross datum reset, Derby path and Sheffield platform 4 reference. |
| King's Cross–York, East Coast Main Line | 303.098 km | 303.361 km | 0.263 km (0.09%) | Pass for the bidirectional 200 m sampling replay. Denser synthetic profiles can safely exhaust the diversion budget southbound. York ECM4 chainage uses the King's Cross datum. |
| Euston–Birmingham New Street via Northampton | 185.374 km | ≈185.577 km | 0.203 km (0.11%) | Pass. HNR/Rugby datum offset and RBS1 chainage before the Euston platform datum allowance. |
| Paddington–Cardiff via Severn Tunnel | 233.425 km | 233.516 km | 0.091 km (0.04%) | Pass. Patchway and Severn Tunnel junction chainages. |
| Llandudno Junction–Blaenau Ffestiniog via Conwy Valley | 44.234 km | ≈44.277 km | 0.043 km (0.10%) | Pass. LJT1 27m 41ch station chainage from the Llandudno Junction nominal datum, with a short station approach preceding the branch junction. |
| Cardiff–Swansea via South Wales Main Line | 73.556 km | 73.748 km | 0.192 km (0.26%) | Pass. Cardiff sectional appendix and Swansea station chainage. |
| Paddington–Swansea via Cardiff and Severn Tunnel | 307.009 km | 307.264 km | 0.255 km (0.08%) | Pass on the host, both ways with and without ten-minute Cardiff dwell. Sum of the two references above, with a small difference possible between Cardiff platform attachments. |
| Glasgow Queen Street High Level–Edinburgh Waverley via Falkirk High | 75.966 km | ≈76.102 km | 0.136 km (0.18%) | Pass. EGM2/EGM3 datum change at Haymarket. |
| Inverness–Kyle of Lochalsh via Dingwall | 132.565 km | 132.791 km | 0.226 km (0.17%) | Pass. WCK and KYL chainages at Dingwall. |
| Belfast Grand Central–Bangor via Central and Lagan junctions | 22.997 km | 23.738 km proxy | 0.741 km (3.12%) | Acceptable limitation. Historic Great Victoria Street terminal datum, CGV/CJL/LJB. Saved mileage retained. |
| Belfast Grand Central–Derry~Londonderry via Bleach Green and Coleraine | 154.100 km | 155.463 km proxy | 1.363 km (0.88%) | Acceptable limitation. Historic terminal proxy, CGV/CJL/LBG/BGD and the separate York Road via Greenisland datum. Saved mileage retained. |
| Belfast Grand Central–Larne Harbour via Bleach Green and Whitehead | 43.672 km | 44.639 km proxy | 0.967 km (2.17%) | Acceptable limitation. Historic terminal proxy, CGV/CJL/LBG/BGL with the York Road mileage reset. Saved mileage retained. |
| Belfast Grand Central–Newry via Lisburn and Portadown | 69.628 km | ≈69.735 km | 0.107 km (0.15%) | Pass at corridor scale. Great Victoria Street 112m 44ch as the terminal proxy, border 59m 47ch and Iarnród Éireann's 15.5 km Newry–border distance. Translink rounds the border datum to 59.5 miles. |
| Coleraine–Portrush branch | 9.464 km | 9.374 km | 0.090 km (0.96%) | Pass at corridor scale. CTP 61m 67ch to 67m 53ch engineering limits. Approximate operator limits by quarter-mile give 9.7 km. |

HS1 follows the Gravesend platform attachment through HDR and TRL5, then HS1 through the Thames tunnel and London east and west tunnels via Stratford International into a domestic St Pancras attachment (OSM node 3101677943). The independent engineering calculation uses HDR 23m 75ch at Gravesend and directional Springhead split points of 22m 27ch and 22m 42ch, plus the operator consultation’s 37.3 km up and 37.5 km down references from Springhead to St Pancras. The older 39.539 km replay was about 234–336 m short of this reference band. Its alternative terminal attachment and choice of track are plausible contributors, while the original route chain for that sample is unavailable for exact decomposition. The 39.769 km replay explicitly pins the domestic attachment before sampling. No offset is applied to any inferred distance. [HS1 operator route map](https://stpancras-highspeed.com/wp-content/uploads/2025/02/2026-HS1-Network-Statement-November-2024-update.pdf) · [Springhead reference](https://stpancras-highspeed.com/wp-content/uploads/2025/01/summary-of-draft-5yams-consultation-feedback-non-confidential.pdf)

The three held Northern Ireland pairs include the modern Grand Central attachment (station ID 12248421687). Their connection through Blythefield Curve differs from the historical reference at the Belfast end. The exact route-to-datum differences require current platform and junction chainages or surveyed geometry covering the new terminal. Historical route-mile totals alone cannot isolate the source of those discrepancies. The hold checks station IDs in both directions and leaves Coleraine–Portrush, Newry and other supported endpoints eligible. [Iarnród Éireann Network Statement](https://www.irishrail.ie/Admin/IrishRail/media/Content/About%20Us/IE-2023-Network-Statement.pdf) · [Network Rail LJT1 sectional appendix, module NW3](https://filestore.limitofshunt.org.uk.s3.amazonaws.com/sectional-appendices/nr/lnw-n/module-3-2009.pdf)

Sources: [HDR mileage](https://www.railwaycodes.org.uk/elrs/_mileages/h/hdr.shtm), [TRL engineering references](https://www.railwaycodes.org.uk/elrs/elrt.shtm), [St Pancras station mileage](https://www.railwaycodes.org.uk/stations/stationl.shtm), [SPC9 mileage reset](https://www.railwaycodes.org.uk/elrs/_mileages/s/spc9.shtm), [Sheffield platform reference](https://www.contractsfinder.service.gov.uk/Notice/Attachment/10d77875-3885-4879-8d10-dedd42648cc2), [Network Rail Cardiff sectional appendix](https://sacuksprodnrdigital0001.blob.core.windows.net/sectional-appendix/Periodic%20Operating%20Notice%20and%20Sectional%20Appendix%20December%2025/Western%20%26%20Wales%20PON%20December%202025.pdf), [Swansea station mileage](https://railwaycodes.org.uk/stations/stations.shtm), [EGM references](https://railwaycodes.org.uk/elrs/elre.shtm), [Translink network statement](https://www.translink.co.uk/getmedia/75f785a5-032e-4327-b11b-bbf683fe5e28/Network_Statement_2025.pdf), [Northern Ireland engineering references](https://www.railwaycodes.org.uk/elrs/elr4.shtm), [LJT1](https://www.railwaycodes.org.uk/elrs/_mileages/l/ljt1.shtm).

The first national replay proposed station-to-station distances for two clipped recordings, 2 November and the earlier 9 November fragment. Their boundary fixes did not establish those stations as trip endpoints. The stricter attachment check now leaves both partial, along with the 14 October and second 9 November cases. The two verified Sidcup journeys retain 39,125.162 m.

## Final validation checkpoint

The repo resumed from draft PR #315 on `feat/railway-stage4-7`, based on `fix/railway-reassessment-stability` at `724e69c7`. Validation commit `a3a38b97` was followed by `a05de46b`. That head's API 35 runtime job failed on a missing compressed interchange test asset, after restart and rollback passed. The preserved catalogue has 400,969 rail edges and uncompressed SHA-256 `cea7233a920bdc90130da19a4c2d5ac82e7e7001d4213c996d171c95abd4f48f`. The new workflow consumes the exact artifact from that run and verifies the ZIP and database digests.

At the original ceiling the eastbound Cardiff replay completed both primary searches, then exhausted 60,000 settled nodes testing a diversion over an 872 m observation gap east of Cardiff. The same diversion found a third candidate after 63,320 nodes with the interim higher ceiling. Searching from the destination first supplies conservative lower bounds for the existing diversion search, which found the third candidate after 58,736 nodes. The original discovery, search and journey caps remain in force. Evidence scoring continued to reject the diversion. Dense road cells could also return the query limit from segments beyond witness range. Bounding boxes now filter those before the same 256-row check.

The synthetic Cardiff fixture uses a journey time of approximately 2h 7m to Cardiff and 40m onward to Swansea. The timed case adds ten minutes with sparse jittered observations near the platform. It models an observation pause during a continuous railway itinerary. Real boarding and alighting recognition require real station-event evidence.

| Gate | Result at checkpoint |
| --- | --- |
| Baseline and catalogue | Pass. Build 283 and PR #314 ancestry verified. Exact SQLite artifact regenerated previously and restored by checksum for this validation. |
| National routes, tunnels, interchange | Pass in host replay on the corridors and representative fixtures listed above. Alternative East Coast sampling profiles safely preserve saved mileage when the bounded reverse diversion search is exhausted. |
| Independent physical distances | Pass at the stated corridor scale for eligible representative routes. Acceptable limitation for the three held Belfast pairs. |
| Parallel roads and ambiguous alternatives | Pass in the host controls. Genuine forced-Train Milton Road drive and nearby slices retained zero inferred mileage. Focused JVM tests also cover a parallel road, clipped endpoint and plausible alternate branch. |
| Build 283 mileage | Acceptable limitation. Current archive reconciles, with exact historical inclusion of the older Sheffield rows unknown. |
| Android workload, restart, responsiveness, receipt/totals reconciliation and memory | Pass. 38 focused JVM tests and four API 35 instrumentation invocations passed, covering the Cardiff replay, atomic migration across restart and rollback, and navigation and totals reconciliation across 1,652 journeys. The Cardiff cases met the 10-second per-case CPU ceiling. Repeated screen launch and return met the 5-second limit. API 35 results are emulator measurements. [GitHub Actions](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/43512255293) |
| APK and upgrade compatibility | Pass. Signed 0.25.238 (284) retains package com.roadprints.capture and the extracted Build 283 signing certificate. Release provenance and direct asset appear below. |
| Pixel 10 and Firebase | Pixel 10 owner acceptance follows the GitHub release. Firebase remains on hold until that acceptance and explicit promotion approval. |

## Owner-test release

Build 0.25.238 (284) was packaged from `918af39e2813c7a00820f62fbf50c05b00666e52` in [packaging run 43513362469](https://github.com/adamdbird-gfc/uk-road-tracker/actions/runs/43513362469). Android validation was on `3a0d6870116163d377993b8306b80d3188066415`. The Android source and both runtime checks are identical across those commits.

- [Roadprints-0.25.238-284.apk](https://github.com/adamdbird-gfc/uk-road-tracker/releases/download/roadprints-0.25.238-284-stage4.7/Roadprints-0.25.238-284.apk)
- Size 103,034,384 bytes. SHA-256 `a21e0703ecd68f6fb1862c90a41980f3c5b64b2206090c3ed4386759a1c1c100`.
- Signing certificate SHA-256 `5b7cdba0abd0cb41d570cdc348e3ca9bb8e2312cb7adf568bb536ce8577cc031`. The packaging job verified the signed APK, published a prerelease and downloaded the direct asset to compare its bytes.

Open the APK on the Pixel 10 and choose Update over Build 283, keeping app data. Open Roadprints with recording idle, browse Journeys and Progress while reassessment runs, and check the saved routes, totals and Utilities → What's New. Some older HS1 and Sheffield recordings can keep their existing distances when either end is missing. Monitor for a Wait / Close app dialog. Phone responsiveness and battery acceptance are still pending.

Stage 4.7 passed the controlled owner-test gate. PR #315 remains a draft. Firebase promotion requires successful owner acceptance and explicit approval. No backend or Firebase deployment accompanied the release.
