# Global cumulative Map geometry validation

**Purpose:** The cumulative Map is a global union of physically accepted travelled paths. Fleet Street and roundabouts are diagnostic examples, **not special-case algorithms or geographic restrictions**. This document supports the owner-defined contract in `docs/CUMULATIVE_MAP_CONTRACT.md` (draft PR #368).

## Geographic invariants

- Accept valid longitude/latitude anywhere on Earth, in both hemispheres and across the equator.
- Handle the antimeridian as a short longitudinal crossing where accepted geometry supports it; do **not** turn a two-point 179.8°→−179.8° journey into a 359.6° chord across the entire world.
- Preserve valid separate polylines, physical turns, disconnected gaps and nearby but distinct paths irrespective of locale.
- Route/network union is independent of journey order, direction and repeat count.
- Respect private accepted route evidence and saved corrections. Do not invent a road or join segments just because a map tile/reference graph contains a plausible edge.
- Audit complete comparable inputs, never silently subsample over-budget geometry.
- Preserve immutable original journeys, separate canonical road/NCN attribution, and device performance gates.
- Do not treat an unaccepted path in **one** journey as invented cumulative coverage: another accepted journey may independently account for it.

## Test regions / topology (illustrative, not exhaustive)

| Class | Example validation | Guardrail |
|---|---|---|
| Dense city | Fleet Street/Temple, London | Repeated paths consolidate; unrelated side-street strokes need accepted evidence |
| Circular junction | Four-entrance roundabout | Distinct accepted arcs survive; shared approaches are not stacked |
| Antimeridian | Fiji/Pacific 179.8°→−179.8° | Geometry and viewport remain local to ±180° |
| Southern hemisphere | New Zealand −45° | Distances and regional clipping are sign-agnostic |
| Equator | 0° latitude | No northern-hemisphere or UK-only assumptions |
| Parallel corridors | Divided highway, footpath beside road | Distinct physical paths remain distinct |
| Interrupted evidence | Gap between valid route fragments | Do not reconstruct speculative connectors |
| Multi-region archive | Separate city/continent windows | Never make one huge clip from disjoint global journeys |

## Engineering boundary

`MapCorridorGeometry.around()` produces a minimal circular-longitude **regional** bounds; wrapped windows have west > east. `clip()` unrolls each segment using the shortest longitude delta, clips in an unwrapped coordinate interval, and restores conventional longitudes for output. `CumulativeMapShapeAudit` measures longitude displacements without interpreting an antimeridian crossing as a 360-degree detour. Validation must not silently infer a missing transit route: for geographically distant accepted endpoints or a multi-continent journey, select individually supported regional fragments and/or multiple windows rather than one continental shortcut. This helper is not a routing engine.

**Known limitations:** The current shape metric uses a local planar approximation and bounded vertex/edge sample points. It is fit for **regional fidelity diagnostics**, not a global geodesic guarantee, topological identity proof, road-lane attribution or polar projection acceptance. At near-polar latitudes or for very long accepted legs, a separate geodesic method and explicit gap verification are required. The production renderer is **not** modified by this PR.

## Opt-in visible Map geometry evidence export (development branch)

The cumulative Map now has a private, user-initiated diagnostic action: **long-press the Legend button** after the Map reports ready, then confirm **SAVE** and choose a JSON document location. Normal Legend tap behaviour is unchanged; the view, accepted journey files and all source coverage remain untouched.

This diagnostic stores only travelled line segments intersecting the **currently visible** Map camera window, calculated from the actual Mercator camera, zoom, screen dimensions and density-adjusted tile size. It includes compact layer names, map revision, active journey-filter identity, camera, wrapped window bounds, coverage-completeness flags and explicit segment/point counts; it does not include journey IDs or the archive. It neither uploads data nor runs automatically. The file contains **precise historic location coordinates**: the owner should choose whether and where to share it.

The export reads **`displayedRoutes`**, the immutable prepared snapshot already supplied to `RoutePreviewView`. The local-road intervals are quickly screened by coordinate endpoints before JSON is generated. Work is executed on a dedicated background thread after the document destination is selected; no new archive scan or Map renderer change. The current maximum is **512 clipped sections, 8,192 points and 384 KiB**. An over-budget request fails clearly and requires zooming closer, rather than silently discarding travelled geometry. This is a *per-view* export, not a claim to globally serialise every route in one document.

`MapGeometryEvidenceExport.capture()` generates the data; `CumulativeMapSnapshotComparison.compareSavedEvidence()` reads the same schema for private offline comparison with a separately supplied accepted route. The reader validates layer names and section/point counts. Synthetic regression checks exercise real prepared `MapRoutes` collections, persistence round-trip, absent coverage, wrapped Date Line windows, extra road provenance and failure on truncated or oversized captures.

**Owner acceptance procedure (only after signed integrated test APK):** Open the cumulative Map and wait for Map ready; set desired zoom and camera around a problematic area; long-press Legend; confirm Save and choose a private file location. Export the corresponding individual journey diagnostic separately and compare accepted match against Map evidence in a protected analysis environment. Repeat on distinct geographic/network scenarios before any worldwide rendering-correction decision. Do not publish the JSON files in public GitHub issues or PRs.

**Status:** Code and tests committed on PR #369, not yet validated for this increment by Android CI or Pixel 10. An export from an older installed APK (such as Build 313) is not available through this action; it requires a new integrated build after validation and owner release approval.

## Validation before release

1. Compile and execute geometry tests, including the international cases above.
2. Check candidate versus baseline with the **same accepted source data and region window**, including private corrected journeys where authorised.
3. Capture actual cumulative rendered geometry from production `MapRoutes` and genuine Android fixed-camera screenshots for the selected regions.
4. Report missing geometry, wrongly merged physical paths and unsupported connections separately; compare completeness and fidelity before performance.
5. Gate on memory, load readiness and Pixel 10 owner acceptance, without promoting synthetic success to global deployment success.

Never commit owner route exports to this public repository. A global algorithm must be evaluated across varied scenarios rather than tuned to the current London fixture.
