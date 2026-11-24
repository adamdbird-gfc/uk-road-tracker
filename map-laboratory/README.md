# Roadprints hosted Map Laboratory (experimental)

A mobile-first, static GitHub Pages-compatible laboratory for replaying accepted Roadprints journey geometry **without publishing the owner's private location history**.

## Bulk Android diagnostics ZIP

Use **Utilities → Export all journey diagnostics** in Roadprints. In the Lab, choose the ZIP to browse private, chronologically ordered journeys and switch between **Route-first**, **Existing interval engine**, and **Recorded lines**. References used for ZIP comparison come from the ZIP. **Next, Back, Play, the slider and direct journey selection** progressively display accumulated journey routes from the ZIP in chronological order. Earlier reconstructed routes remain dark; the selected journey is highlighted. A forward jump reads intervening journeys in sequence, so large jumps may require substantial processing. Existing interval engine and Recorded lines are A/B comparisons for the current journey only; earlier journeys remain route-first geometry. **These are displayed route lines, not automatic road-coverage credits.** The browser retains reconstructed geometry while this ZIP is loaded but does not retain previously decoded diagnostic records. The street basemap is visible by default in both ZIP and legacy modes; OpenStreetMap tile requests reveal the viewed area to the tile provider, but the private ZIP and journey records stay local.

See the [format and acceptance procedure](../docs/bulk-journey-diagnostics-format.md).

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
