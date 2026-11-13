# Stage 4.7 railway validation

## Baseline

Build 0.25.237 (283) was signed from `2f96b5c6` in draft PR #313. Draft PR #314 adds the reassessment responsiveness work at `724e69c7`, which already contains #313. Stage 4.7 branches from that commit. Main was `55e9a2a5` at intake. Integrate the reviewed railway changes in dependency order, or consolidate the stack onto current main after owner review and rerun validation on the integrated head. No integration or deployment is part of the development checkpoint.

## Source and eligibility

The pinned Geofabrik UK extract `united-kingdom-261112.osm.pbf` includes England, Scotland, Wales and Northern Ireland. See the bundled catalogue manifest for source and database SHA-256. Geometry and road comparisons are © OpenStreetMap contributors under ODbL 1.0. The complete corresponding database ships as a compressed SQLite asset. The compiler can reproduce it from the dated extract. [Source](https://download.geofabrik.de/europe/united-kingdom.html) · [Licence](https://www.openstreetmap.org/copyright)

Track must be active `railway=rail` geometry with passenger `route=train` membership. The graph joins shared source nodes and attaches stations at represented passenger stop nodes. A nearby station point can supply the label for a stop attachment. Missing route members, stop positions and shared-node connections leave gaps. The snapshot does not establish when tracks or stations opened or changed. Projected coordinates support proximity calculations. The distances are WGS84 ellipsoidal segment sums, with precision limited by source survey, geometry density and attachment placement. Tunnel and bridge track geometry retains its connectivity. There is no synthetic track between terminals or across water.

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
