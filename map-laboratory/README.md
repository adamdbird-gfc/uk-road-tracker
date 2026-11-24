# Roadprints hosted Map Laboratory (experimental)

A mobile-first, static GitHub Pages-compatible laboratory for replaying accepted Roadprints journey geometry **without publishing the owner's private location history**.

## Bulk Android diagnostics ZIP

Use **Utilities → Export all journey diagnostics** in Roadprints. In the Lab, choose the ZIP to browse private, chronologically ordered journeys and switch between **Route-first**, **Existing interval engine**, and **Recorded lines**. The interval comparison uses the selected journey’s ZIP references. The cumulative route display can also use the pinned public network. **Next, Back, Play, the slider and direct journey selection** progressively display accumulated journey routes from the ZIP in chronological order. Earlier reconstructed routes remain dark; the selected journey is highlighted. A forward jump reads intervening journeys in sequence, so large jumps may require substantial processing. Existing interval engine and Recorded lines are A/B comparisons for the current journey only; earlier journeys remain route-first geometry. **These are displayed route lines, not automatic road-coverage credits.** The browser retains reconstructed geometry while this ZIP is loaded but does not retain previously decoded diagnostic records. The street basemap is visible by default in both ZIP and legacy modes; OpenStreetMap tile requests reveal the viewed area to the tile provider, but the private ZIP and journey records stay local.

See the [format and acceptance procedure](../docs/bulk-journey-diagnostics-format.md).

## Railway bridge and station access transitions (LAB r456)

The Journey 7 device review accepted Prince of Orange and identified two
remaining transitions. A mapped pavement crossing a bridge can now be
aligned along the road on that same bridge when the journey brackets a
unique, connected road trace. Tagged pedestrian crossings and different
bridge or tunnel levels retain their own geometry. The railway bridge
section of Stone Street now follows the centreline into Clive Road.

A confirmed parallel corridor can continue across a short cluster of dense
pavement vertices to its mapped access turn. The Clive Road transition
retains the exact saved station path and steps after leaving the road.

Relative to r455, the original ZIP replay added 29.4 m near the Stone Street
centreline and 9.1 m near the Clive Road centreline, measured within 0.5 m
of the pinned OSM geometry. The Prince of Orange join and Community Square
were preserved in that replay.

## Pinned public network and corridor recovery (LAB r455)

The normal cumulative ZIP replay reads versioned OpenStreetMap road and
pedestrian reference tiles from the Laboratory host. The public data comes
from checksum-pinned Geofabrik Kent and Greater London extracts dated
23 November 2026. The initial index has nine 0.02-degree cells covering the
previous Gravesend and central London pilot areas. The Lab reads only the cells
near the current journey and shares the public tile cache across the replay.
The static cell requests can reveal the approximate locality to the hosting
service. Journey files, routes, GPS points and identifiers stay in the browser.

The bundled network carries OSM way and node IDs, road classifications and
pedestrian facility tags. The offline builder in
[`scripts/build_lab_reference_tiles.py`](../scripts/build_lab_reference_tiles.py)
accepts one or more checksum-verified local PBFs and area bounds. The
[tile index](reference-network/v1/index.json) records the source URLs,
timestamps, checksums and license. To extend coverage, generate a new immutable
version directory using the same command format below and update the Lab’s
reference version. Tiles derived from OpenStreetMap are distributed under
[ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) with attribution to
[OpenStreetMap contributors](https://www.openstreetmap.org/copyright).

```sh
python scripts/build_lab_reference_tiles.py map-laboratory/reference-network/v1 \
  --region kent.osm.pbf https://download.geofabrik.de/europe/united-kingdom/england/kent-latest.osm.pbf \
  95f7940ab8a873441fd59e0dbc1b46118ab4c78a6dc683e357b8d6bfac3f8a56 0.350 51.425 0.385 51.445 \
  --region london.osm.pbf https://download.geofabrik.de/europe/united-kingdom/england/greater-london-latest.osm.pbf \
  38ac4610926d465a1cdedf70d7887b2d2f4bbd45d8b2d91a4bfcd4863f45885b -0.145 51.500 -0.105 51.515
```

LAB r424 removed short off-centreline components after the legacy interval
engine had credited a continuing road. Later revisions added bounded
source-backed network joins and pedestrian connection safeguards. The ZIP
migration began drawing the ordered saved match, bypassing the interval
post-processing. LAB r454 also required a successful live public query for
missing references, applied it only to explicitly selected journeys, and
indexed accepted interior promotions with an absolute source index into a
run-relative array.

The new replay uses the same reference supply for earlier and selected
journeys. It reconstructs sustained roadside alignments along the actual road
vertices, including unambiguous ways split at shared OSM nodes. Compact
junction alignments require a unique connected road trace with ordered support
from the original journey and anchored approaches. Crossings and tagged
pedestrian infrastructure are protected. A detached short footway duplicate
requires a continuing road already traversed by that same journey and no
nearby raw support. Outside the bundled area or after a tile failure, replay
falls back to the journey’s own references and reports the reference state.
Network geometry is never interpreted as movement by itself.

The public test suite exercises tile selection, deduplication, provider errors,
corridor geometry and progressive replay. The private Journey 7 regression
can be repeated locally using the original owner archive, which must remain
outside the repository and CI:

```sh
ROADPRINTS_DIAGNOSTICS_ZIP=/private/path/to/archive.zip \
  node --test map-laboratory/tests/private-zip-reference-replay.test.cjs
```

In that replay, measured line length within 0.5 m of the public centreline
increased by 35.2 m on Old Road East at the Prince of Orange junction, 47.2 m
on Stone Street and 119.2 m on Clive Road. Community Square and the station
entrance retained their exact ordered source vertices. The test also verifies
ZIP and journey immutability, independence from prior journeys, and fallback
when the reference index is unavailable.

## Explain actual centreline candidate failures (LAB r454)

LAB r453 remained visually unchanged at both Clive Road and Prince of Orange in the owner’s Journey 7 screenshots. Synthetic tests alone do not establish why a real road was rejected. LAB r454 therefore instruments the normal Route-led cumulative display **without creating additional map modes or a new diagnostics button**.

For each selected journey it now reports: independent public OSM lookup success or the actual error string; physical-road reference way count; metres of source geometry promoted onto road centrelines; source-length-weighted individual candidate screening outcomes (missing reference, road >15m distant, direction mismatch, longitudinal overlap mismatch, source already on reference centreline, or individually eligible); and the three nearest scored road options with names, source lengths, maximum lateral offsets and heading agreement. The top visible replay status includes the number of promoted metres and the primary rejection reason when no correction is achieved. A zero is explicitly **not accepted as a geometry fix**.

The per-leg screening data is diagnostic and does not itself approve a corridor: the existing run-level requirements (sustained distance, monotonic progress, competition between road candidates) remain mandatory. The original matched route is not altered and no journey’s travel evidence is imported from other journeys.

A one-time live public-road query from GitHub Actions was attempted to validate the data-supply dependency, without sharing any private journey details: overpass-api.de returned HTTP 406; the documented alternative overpass.private.coffee returned HTTP 429 (rate limiting) on a later request. These are **actual server-side observations**, not evidence that Chrome on the owner's Pixel receives the same responses. The one-time probe was removed rather than hammer rate-limited services. The Lab now prefers the community-hosted service, uses at most one bounded alternative, and displays the actual failure status. No production Android changes, and no claim that Journey 7's two locations have been corrected. The accepted Community Square and Haynes Road shortcut remain protected.

## Independent road centrelines and measurable changes (LAB r452)

LAB r451's ZIP-wide shared road catalogue violated the explicit requirement that a journey's geometry must not depend on other recorded journeys. **That mechanism and its tests are removed.** Historical travel is used only when aggregating independently interpreted journeys.

The route-led Map Laboratory now combines the *selected journey's own* saved movement evidence with a separate **public OpenStreetMap road-network lookup** of its bounded locality. Only a geographic bounding rectangle (not the journey's GPS points, raw or matched route, ID, titles, or ZIP) is sent to an Overpass endpoint. The query is visible in the Map status, time-limited and only performed when the selected walking/running journey has a substantial section without usable physical-road references. No request is made while browsing earlier journeys merely to create cumulative history. If the external network is unavailable, the original match is preserved and the status explains that the reference lookup failed.

The independent road data is **not travel evidence**: each source route still determines where the traveller walked. Sustained physical-road equivalence can promote a road-parallel walking corridor onto a unique public road centreline with strong heading and longitudinal agreement. Broad urban pavements can lie up to 15m from a centreline, but that wider distance requires at least 50m of strongly parallel source support; smaller corridors keep stricter geometry checks. Pub service roads, independent pedestrian shortcuts, diagonals, crossings, unsupported endpoints, and Community Square remain protected.

The Lab now reports **metres of current journey geometry actually aligned**, not just a count of candidate corridors. The new two-corridor regression requires a genuinely shifted geometry result in both corridors and protects the original route. A zero-metre result or public network failure is a non-acceptance, not a claimed fix. This is a Lab-only experiment pending Journey 7 device comparison. Never credit movement on a road from its network presence alone.

## Historical note: Superseded ZIP-wide shared road catalogue (LAB r451)

The owner’s r450 Journey 7 screenshots showed that simply accepting longer roadside legs had not moved Clive Road or the Prince of Orange section onto physical road centrelines. Audit of the **actual private 1,714-journey ZIP** found that Journey 7 exports 24 local network ways, including 13 footways but **no Clive Road centreline reference**. Other journeys in the same ZIP contain multiple physical Clive Road references, as well as road references around the Prince of Orange. This is an evidence-availability defect, not a problem solved by another matching threshold.

LAB r451 introduces a **locally derived, ZIP-wide physical-road reference catalogue**. When a walking/running journey has a substantial (150m+) section without a usable road reference, the Lab scans the owner’s existing ZIP once, with visible progress, collecting only physical-road geometry from exported matcher results. It deduplicates road geometry regardless of direction or road-class label, discards full journey records after each read, and selects only references spatially near the current route. Once available, the Lab rebuilds the provisional cumulative ledger from the beginning of the chronological replay so already-credited sections remain internally consistent. Source-matched geometry, recorded GPS and original ZIP files remain untouched; nothing is uploaded or rematched.

The additional references may only be used for existing **directional, proximity-backed centreline alignment**, not as proof that the owner travelled another road or to invent pedestrian connectors. True pedestrian shortcuts, Community Square and matched endpoints continue to rely on their original evidence. The catalogue is an in-memory operation that can add visible import time for a large ZIP; no new controls or persisted data were introduced.

**Important:** This supplies missing road evidence but does not prove the Prince of Orange correction is complete. Some Journey 7 Windmill Street references already coincide with the saved route, so a remaining offset may instead reflect the quality or identity of that road reference. Real Journey 7 visual acceptance is required. No Android production coverage is changed.

## Sparse long-leg road centreline evidence (LAB r450)

Journey 7 owner screenshots at LAB r449 still showed pedestrian-offset geometry along Clive Road and the Prince of Orange approach, while Community Square remained acceptable. The first r449 implementation unnecessarily demanded at least **two original source legs** to qualify a sustained roadside corridor. Sparse imported journey matches may contain a single long straight segment along one road. LAB r450 permits a single 32–120m matched leg to qualify when the original endpoints **and independent 25%, 50%, and 75% interior samples** follow one matching road reference within the same 8m tolerance with sustained monotone longitudinal progress. This avoids accepting a straight source line merely because it touches both ends of a curving road reference.

The source route and matched evidence remain unchanged. The genuine Haynes Road pedestrian shortcut and Community Square are not targets; short links, perpendicular crossings, divergent curves and competing equally likely road references remain excluded. The change only affects experimental cumulative road attribution, not the Android archive. CI includes additional sparse-leg, competing-reference, short path and curved-road regressions. Whether the real Journey 7 network references support promotion still requires owner visual acceptance.

## Sustained pedestrian-to-physical-road centreline alignment (LAB r449)

The owner identified two instances of the same issue on the December 12 Journey 7 map: roadside pedestrian geometry rather than the physical road centreline. Community Square is accepted and **must not be altered**.

The route-led cumulative model now aligns consecutive, sustained, directionally parallel source-matched walking segments to a matching physical-road reference centreline. The independent promotion rule requires at least two consecutive supported legs covering 32m, mean lateral displacement at least 1.6m, road proximity within 8m, longitudinal progress above 72% of source distance and consistent direction. Tied competing roads are withheld rather than arbitrarily selected. Actual perpendicular crossings, distinct footpaths, independent square shortcuts, endpoint evidence and earlier accepted route geometry remain in the original saved match.

This is an incremental, general-purpose attribution experiment, **not** a city or street-name rule. Existing Roadprints history is unchanged. Regression tests protect real road-to-footway-to-road shortcuts, square crossings, ambiguous road alternatives and absence of network evidence. Device acceptance must confirm both roadside sections on Journey 7 before claiming success. The ZIP stays local.

## Evidence-bounded termini on sparsely recorded journeys (LAB r448)

This corrects the r447 assumption that an unsupported terminal side-road turn should leave coverage ending at the preceding junction. A junction is not independent evidence of the traveller's stopping point. When the r447 safeguards identify an unsupported terminal turn, the route-led accumulator instead locates the final recorded GPS position on the already matched road approach (within 3.5m, and no more than 40m before the saved-match endpoint), projects the terminal position onto that physical road geometry, and withholds **all** later unmatched approach and side-road segments. The traveller may have finished partway along the road. Without a reliable last fix on the approach, no new endpoint is invented and the original match is left unchanged for further diagnosis.

This is a general GPS-evidence rule rather than a school- or town-specific correction. A GPS-backed final destination on the side road, a straight uninterrupted road, and a genuine pedestrian shortcut are protected by tests. The owner's private archive remains in the browser and the original matched history remains unchanged. These changes are to experimental Lab cumulative attribution only, not Android road-completion data.

## Sparse-GPS unsupported terminal side-road turn (LAB r447)

Owner accepted Journey 2 in LAB r446. Journey 3 (10 December 2025, 08:08 UTC) revealed a short end nib where the saved match turns from Haynes Road into Garden Row. The original Timeline-import recording has five GPS positions; its last recorded position lies approximately 0.9m from the matched main road, about 13m before the road junction, while the saved matcher continues another ~14m along Garden Row. This is a separate **terminal matched-route overshoot**, not the out-and-back or entrance nib already handled by r446.

Before route-led cumulative attribution, the LAB can withhold just the side-road tail **only** when there is a last GPS fix close to the main-road approach, a near-right-angle turn to a distinct OSM road reference, an unobserved terminal tail of 6–24m, and no GPS fix near the terminal end. The main-road approach to the junction remains. Genuine GPS-supported destinations, straight-road continuations, and independent pedestrian footpaths are protected by regression tests. The original saved matched geometry is unchanged and remains available via Compare other views.

No private coordinates are published. The Lab remains experimental and must be tested with the owner's cumulative ZIP before any Android release decision.

## Route-led roadside nib consolidation (LAB r446)

The private Journey 2 replay showed two distinct anomalies in the saved match: a very short leading hook from a minor road onto a continuous major-road centreline, and a closed out-and-back excursion that follows a footway approximately parallel to the already used road before returning to its starting point. No private coordinates are committed.

The experimental route-led accumulator now performs **source-backed, non-mutating cleanup** before producing provisional cumulative segments:
- A closed out-and-back is consolidated only when it rejoins the same road position and most of its geometry demonstrably shadows an existing road centreline. Genuine independent pedestrian shortcuts between different roads are unaffected.
- A short leading road hook is withheld only if it is uncorroborated by nearby recorded position evidence, turns sharply onto a long supported road route, and is not supported by a separate pedestrian reference. Confirmed journey starts remain eligible.

The complete original Route-first saved match remains accessible under **Compare other views**. These are provisional display/ledger decisions, not edits to Android history. If reference geometry or position evidence is missing, the rules leave source geometry unchanged instead of inventing coverage. Synthetic cases protect real shortcuts, pedestrian out-and-backs away from roads, recorded starts, and absent references. Real seven-journey owner acceptance remains mandatory.

## One-map mobile Lab controls (LAB r445)

The hosted ZIP Laboratory now opens on the existing **Route-led cumulative** model, labelled **Cumulative map**, rather than displaying five equally weighted engineering buttons. The legacy, recorded, Route-first, and canonical comparison views remain available under the collapsed **Compare other views** disclosure for investigative use. This is a UI choice and does not claim route-led source segment IDs are fully canonical physical-network coverage. The private diagnostic ZIP, history, PR-visible build revision, and Android data remain unchanged. Real-world nib removal and consistent network-section identity remain outstanding.

## ZIP selected-journey state consistency (LAB r444)

The cumulative ZIP replay status now updates after the same selected journey state used by the map, title, counter and controls has committed. The request target and currently selected journey must not be shown as one inconsistent state. A chronological Back/Next/jump test asserts that the status reflects the committed selection, including revisiting earlier cached journeys.

This revision also adds an **opt-in Route-led cumulative** view that uses Sunday's preserved RouteFirst geometry (including corrected segments, pedestrian shortcuts and bounded centreline substitutions), accumulates successive saved source segments in journey order and compares repeats by direction-independent segment identity. Unlike the r438–r443 experimental canonical attribution, the route-led view does not re-filter a saved pedestrian shortcut against its separate reference collection. A/B comparison is available without replacing canonical code. Unlike physical-network interval identity, source segment identity is still sensitive to sampling and geometry variation, so this model is **not yet validated new network credit** and will not change Android completion.

The reference-gap defects identified on the owner's pedestrian shortcut and the Cross Lane East nib remain open for seven-journey A/B field acceptance. Preserving the original matched journey, local ZIP and Android coverage is mandatory.

## Preserve source-backed pedestrian corridors (LAB r443)

The legacy Map Laboratory's tested footway-endcap principle from PR #386 is reused in the ZIP canonical attribution stage: consecutive, directly source-backed pedestrian reference intervals form one corridor, and a multi-interval corridor is protected **before** speculative short-terminal-branch pruning. The owner Journey 2 private audit showed four consecutive footway intervals with direct overlap and zero lateral offset incorrectly withheld by that pruning rule. The new regression uses synthetic geometry only (no private journey coordinates) and verifies a comparable continuous pedestrian corridor survives while a short dangling spur remains withheld.

This is a bounded repair of one demonstrated regression, not a wholesale adoption of the legacy matcher and not a seven-journey field acceptance. The prior diagnostic audit remains available. The original matched journey, cumulative Android credit, private ZIP storage and replay are unchanged.

## Local interval-decision audit (LAB r442)

Under **Canonical coverage decision audit (private)**, run **Diagnose this journey** to inspect the current ZIP journey's actual decision pipeline. The report counts reference roads and paths, supported network intervals, withheld alternatives, and intervals without sufficient overlap. **Save private audit JSON** includes exact coordinates and rejection reasons for each usable reference interval. The file is generated locally in Chrome and contains sensitive journey locations; do not commit it to GitHub or share it publicly. It is calculated on demand for the currently selected journey, never uploaded or stored as a separate long-term report.

This release is deliberately diagnostic-only: no changes to matching, nib removal, road-centreline attribution, ZIP persistence, or the Android app. Missing reference-path evidence cannot be repaired by threshold adjustments. Use the results from the owner's seven-journey replay to guide any subsequent corridor-selection correction.

## Supported road-to-footway-to-road transitions (LAB r441)

Independent pedestrian shortcuts are valid cumulative network coverage, not terminal nibs. Before applying general branch filtering, the experimental canonical accumulator now detects connected pedestrian reference corridors whose endpoints both anchor to road intervals and whose matched route supports the footpath sections. Those reference intervals are protected from the short-branch filter. A footpath dangling from only one road is not automatically protected, and parallel roadside pavements still consolidate onto physical roads. The original matched journey, historical coverage and locally stored diagnostic ZIP remain unchanged.

The Lab reports the count of protected pedestrian transfer intervals for diagnostic review. This is a synthetic-test-backed hypothesis, **not** proof that the owner's specific Haynes Road shortcut is credited: source exports missing usable pedestrian references cannot be reconstructed or credited by this change. Real ZIP acceptance must confirm the path and no spurious branches before promoting the result to Android.

## Continuous source support and terminal-junction correction (LAB r440)

The experimental canonical interval evaluator now combines the longitudinal overlaps of consecutive saved-route legs before deciding whether an ~8m physical-network interval has support. Previously, a journey with dense short matching segments could produce false gaps because no *single* segment overlapped 65% of an interval. Adjacent fragments count toward the same interval; disconnected or perpendicular observations do not.

Terminal-branch filtering now checks the degree of the actual **junction** at the far end of the branch. The previous implementation accidentally examined the opposite endpoint of its last traversed edge. The change is location-independent and retains safeguards for journey endpoints and explicit out-and-back movement. Real seven-journey acceptance is still required; these are provisional visual credits, not authoritative corrections to Roadprints.

## Local diagnostics ZIP retention and regression safeguard (LAB r439)

After a successful import, the compressed diagnostics ZIP is stored as a single private File in the Lab origin's browser IndexedDB. On the same browser/device it automatically reopens on a later visit, without repeating the Android file selection. **Forget stored ZIP** deletes the browser copy; Chrome site-data clearing or storage eviction can also remove it. Quota failures leave the current session working but require manual reimport on the next visit. The ZIP never uploads to the laboratory host. OpenStreetMap base tiles still request the viewed area from the tile provider.

The proven **Route-first** presentation is now the initial ZIP view. **Canonical coverage** is opt-in until owner acceptance because r438 introduced visible gaps and a spur in the early seven-journey replay. A new generic network-slice filter holds short terminal branches only when they connect to a wider network junction and the saved trace neither ends there nor explicitly turns back; it preserves the raw source match, avoids assuming all short branches are errors, and does not rewrite Android coverage. The canonical view shows the original saved match as faint grey dashes beneath the provisional network attribution to distinguish unsupported credit from missing journey data.

This does not prove the real screenshot regression is corrected: the owner's ZIP has not been run as a private automated fixture in this PR. No location-specific exception or automatic rematching has been introduced.

## Experimental canonical attribution (LAB r438 pilot)

The ZIP workflow now defaults to **Canonical coverage**. It projects supported saved-match travel onto reference road centrelines and independent paths, suppresses aligned parallel roadside footways, holds ambiguous candidates, and tracks first/repeated physical intervals in red/black/green. The other modes remain available for the saved journey, recorded lines and interval-engine comparison.

**Important:** This is a *provisional candidate network ledger*, **not** independently verified road credit. A stored matched line (especially a sparse Google Timeline inference) does not prove every interval was traversed. Nearby unused branches are held where the matching geometry does not support them, but spurs already embedded in the matched route may still require stronger source evidence to reject. Legitimate dead ends and out-and-backs must not be removed simply because they are short. Uncertain or conflicting parallel candidates are withheld, and existing Android historical coverage and journey records are never modified.

The first seven private journeys are owner acceptance scenarios, not committed test fixtures. The tests here use synthetic examples and cannot establish real-device seven-journey accuracy without a local, private replay of the owner's ZIP. Do not promote this pilot to Android or claim complete nib elimination until real archive acceptance passes.

## Legacy archive workflow
1. Open a deployed `map-laboratory/` page over HTTPS on the Pixel 10.
2. Download the separate private file `Roadprints-Lab-Private-Journeys.json.gz` provided outside GitHub.
3. Tap **Choose journey file**, select the compressed JSON once. The browser stores it locally in IndexedDB.
4. Use Next / Back / slider and toggle between **Road centrelines** and **Original lines**.
5. Start with journey 1, Cross Lane East / Singlewell Road. Red = first use on reference, black = historical, green = repeated road.

## Privacy
- **NEVER commit** the Roadprints backup or a compressed journey bundle to this repository, GitHub Pages, release assets, diagnostics or CI logs.
- Legacy import is local via File arrayBuffer; the compressed bytes persist in the user's browser IndexedDB for repeat visits.
- The page requests *geographic bounding boxes only* from public OpenStreetMap Overpass services; these requests disclose the approximate area being examined, not the archive, times, or journey IDs.
- OpenStreetMap background tile requests similarly reveal viewed locations. Avoid third-party location lookups if full offline privacy is required.
- Clearing this site's browser storage removes the remembered imported dataset.
- Loading the page requires Leaflet from a CDN and online basemap/Overpass for uncached road-reference regions.
- No backend, authentication, cloud storage, or data upload.

## Engineering status
Experimental. Road intervals are 8-metre stable slices of reference ways; matched if source samples lie within 15 metres of a road interval and agree in road direction within 45 degrees (direction reversed is accepted). Source journey records never change. A road visited from either pavement or direction is credited once on its reference line. Unmatched samples earn no coverage. Distinct OSM way IDs are not yet merged into a canonical physical road identity.

This is **not** production Roadprints road credit. No automatic correction can be accepted until the first five journeys and broader regression corpus have been reviewed. The case of the Cross Lane East spur is deliberately preserved in the source and must be checked against independently retrieved reference geometry.

## Deployment
Files are staged under `map-laboratory/`. This branch does **not** alter the existing Pages configuration or publish a new web root; confirm the Pages publishing source and deploy the folder under the existing site before providing a production Pages URL.
