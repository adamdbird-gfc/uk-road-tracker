# Roadprints hosted Map Laboratory (experimental)

A mobile-first, static GitHub Pages-compatible laboratory for replaying accepted Roadprints journey geometry **without publishing the owner's private location history**.

## Bulk Android diagnostics ZIP

Use **Utilities → Export all journey diagnostics** in Roadprints. In the Lab, choose the ZIP to browse private, chronologically ordered journeys and switch between **Route-first**, **Existing interval engine**, and **Recorded lines**. References used for ZIP comparison come from the ZIP. **Next, Back, Play, the slider and direct journey selection** progressively display accumulated journey routes from the ZIP in chronological order. Earlier reconstructed routes remain dark; the selected journey is highlighted. A forward jump reads intervening journeys in sequence, so large jumps may require substantial processing. Existing interval engine and Recorded lines are A/B comparisons for the current journey only; earlier journeys remain route-first geometry. **These are displayed route lines, not automatic road-coverage credits.** The browser retains reconstructed geometry while this ZIP is loaded but does not retain previously decoded diagnostic records. The street basemap is visible by default in both ZIP and legacy modes; OpenStreetMap tile requests reveal the viewed area to the tile provider, but the private ZIP and journey records stay local.

See the [format and acceptance procedure](../docs/bulk-journey-diagnostics-format.md).

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
