# Cumulative Map A/B validation scenario — Fleet Street / Temple, London

**Scenario ID:** `LONDON-FLEET-STREET-TEMPLE-001`  
**Priority:** Owner-designated recurring problem area (20 November 2026).  
**Contract:** `docs/CUMULATIVE_MAP_CONTRACT.md` (draft PR #368 until approved).  
**Evidence:** Owner-supplied screenshot in the Roadprints project conversation dated 20 November 2026. **The image has not been copied into this repository**; use the original screenshot as the visual baseline.  
**Status:** Reference scenario and acceptance plan; no candidate screenshot or real accepted-journey fixture has yet been captured.

## Observed context (not inferred travel history)

The owner's screenshot shows the main cumulative Map, at street-level zoom, around **Fleet Street, Fetter Lane, Chancery Lane, New Fetter Lane and the Inner Temple area** in central London. The screenshot has several black Map strokes across and adjoining Fleet Street, including close parallel/overlapping segments, short transverse connectors, and branches into side streets. The on-screen Map is marked **ready**, with all transport, statuses and dates selected. It also shows the app warning that some international coverage is partially held.

These are **visual observations**, not proof that individual strokes are wrong: the corresponding original accepted journey polylines, journey corrections, Map viewport coordinates and renderer outputs have not yet been exported and compared. **Do not assume** that a line is invented simply because it looks unusual, or that all lines should collapse to one road centreline.

## Fixed comparison recipe (repeat on every visual-renderer change)

1. Select the same owner journey archive / source fingerprints and accepted correction state for baseline and candidate. Preserve originals byte-for-byte; never add real private journeys to a public repository.
2. Open **Main Map → All transport · All statuses · All dates**. Ensure layers, network data revision, device pixel density, viewport dimensions and map-style version are identical.
3. Navigate to this **Fleet Street / Temple street block**, framing the Fleet Street east–west corridor and the streets named above; capture and record the actual camera centre, zoom, viewport pixels, map/data revision and screenshot filename. **Do not invent an exact coordinate/zoom from the supplied cropped image.**
4. Capture three evidence layers at the identical camera: (A) original renderer; (B) proposed renderer; and (C) the union of saved accepted, correction-aware journey geometry for precisely that viewport. A and B should be genuine Android renderer captures, not mock drawings.
5. Save georeferenced line coordinates, accepted segment identifiers/contributors (privacy-safe fixture IDs), and comparison results alongside screenshots. Compute distance/deviation, missing accepted coverage, unsupported extra coverage, near-parallel road merging, path duplication and disconnected gaps.
6. Repeat the same capture at **street close-up and one wider zoom**. Compare after a tab-return and after a warm reopen, to catch cache-dependent results.
7. Do not declare improvement solely because the visual Map looks cleaner. If separate accepted paths really exist, they MUST remain; if multiple journeys share one physical path, their cumulative representation MUST not stack strokes.

## Required acceptance checks

- Every accepted, correction-aware journey path in the selected viewport is geographically represented by the cumulative union, potentially split among multiple Map sections.
- No supported turn, bend, side-street entrance, alley/path or branch disappears as an endpoint-only chord or an overly aggressive simplification.
- No cross-street connector exists solely to bridge unrelated endpoints or a gap.
- Adjacent but distinct corridors are not collapsed; identical physical paths are not drawn N times as journey overlays.
- Legitimate turns from Fleet Street into Fetter Lane / the surrounding street network remain physically faithful to source route shapes.
- A candidate that produces fewer lines is not automatically more correct. Report both missing and unjustified **extra** strokes, with attribution to accepted evidence.
- Reuse a fixed source archive, stable camera and renderer settings for every A/B run; compare coverage and performance against the exact same baseline.

## First executable A/B evidence (synthetic only)

`CumulativeMapSnapshotComparisonTest` generates a repeatable geometry-only report with `cumulative_map_ab=` in its test output. This report includes:

- accepted-evidence SHA-256 fingerprint (must match between comparisons)
- displayed-geometry SHA-256 fingerprint (direction/order independent)
- before/after display line and point counts
- maximum accepted-to-displayed deviation, to expose omitted or distorted travel
- maximum displayed-to-accepted deviation, to expose unsupported extra strokes
- signed before/after deltas for both deviation measurements

The Fleet Street-style fixture is synthetic and does **not** reproduce the owner's real journey locations or screenshot. The snapshot comparison fails closed if the accepted evidence differs between baseline and candidate, even when its record count is unchanged.

**Next adapter now committed:** `CumulativeMapSnapshotComparison.snapshotPreparedOverview` extracts the **actual prepared MapRoutes display collections** (accepted matched corridors, local canonical interval lines, covered major roads and ferries), explicitly excluding untravelled incomplete-road background overlays. `CumulativePreparedOverviewSnapshotTest` validates this integration with production `MapRoutes.addLocalRoad` and `LocalRoadCoverage.Section` against synthetic evidence. It is intentionally bounded to 512 sections / 8,192 display points and rejects oversize inputs rather than silently sampling the owner archive. **These tests are committed but not yet executed.** It does not yet compare the owner's private Fleet Street archive or extract screenshots from Android.

**Not yet implemented:** production renderer screenshots, real archived London geometry exports and pixel-level screenshot diffs. Those require matching actual Android camera/view settings and access to owner-approved accepted journey evidence. Never label synthetic report values as device evidence.

## Owner journey evidence received (20 November 2026)

Owner supplied a journey-map screenshot and the **Build 313** individual-journey diagnostic export for a walking route dated **16 November 2026**, from the Charing Cross side through the Fleet Street / Temple corridor to the east. The export was inspected privately and **must not be committed** to this public repository.

Verified from the owner-provided diagnostic export:

- `processing_status=complete`, matched result `status=ok`, walking; matcher `preloaded_pedestrian_network_v4`
- Accepted `processing_result.geojson` has **one LineString, 276 vertices**; its summed matched geometry is approximately **1.98 km**
- `matched_distance_m` and saved journey `distance_meters` both report **1,980.7 m**
- There is no `journey_corrections` field in the exported journey, and the route-editor screenshot displays **0 route sections removed**
- The raw capture is much noisier (701 raw capture samples). **Do not treat raw GPS as the authoritative accepted line.**
- The real-world individual journey screenshot shows a visually continuous route across this corridor at a wider viewport than the previously supplied close-up cumulative Map screenshot

**Evidence status:** This supplies a real accepted-journey reference, but **not** the cumulative Map's displayed section coordinates or the exact shared camera settings. Thus we cannot yet quantify the candidate-to-real-Map geographic deviation, prove why individual cumulative lines diverge, or claim an owner acceptance result. Do not substitute synthetic fixture values for this journey's measurements.

**Geometry self-check (private local analysis):** The 276 accepted route coordinates sum to **1,980.7 metres** using geodesic segment distances, agreeing with the diagnostic's `matched_distance_m`. This confirms that the file's matched LineString is internally consistent with the reported journey length; it does **not** validate Map coverage or road attribution.

**Read-only reproducibility adapter added:** `CumulativeMapSnapshotComparison.acceptedUncorrectedDiagnostic()` extracts accepted matched LineStrings only from complete, `status=ok` uncorrected diagnostic exports (never raw GPS); it refuses any `journey_corrections` field so edited routes cannot be silently misread. `snapshotSerializedOverview()` reconstructs travelled display geometry from the same serialised Map snapshot collections emitted by `MapActivity.mapRoutesToCache()`, including actual decoded local-road interval lines, bounded to 512 sections / 8,192 points. Both inputs remain private for real journey testing; the committed unit tests use synthetic data only.

**Next reproducible comparison:** In a protected/local validation environment, load this unchanged saved route as the accepted-source side, and capture a cropped `MapRoutes` output assembled from the **same Build 313 archive**, without uploading the owner file or private coordinates publicly. Compare all contributing cumulative segments using the bounded A/B helper on a geographically scoped fixture, and separately capture fixed-camera screenshots. A candidate must preserve the 276-point accepted geographic shape as a **union**, not as 276 identical display points or a duplicate overlay.

## Geographic corridor comparison adapter (new engineering increment)

The new `MapCorridorGeometry` clips line segments against a fixed geographic bounding box. It keeps a segment that crosses the box even if both endpoints lie outside, and keeps genuinely disconnected fragments separate. `CumulativeMapSnapshotComparison.comparePreparedCorridor()` and `compareSerializedCorridor()` use the same clipped union to measure how far **one accepted journey** is from the travelled cumulative geometry.

The comparison draws on the real travelled `MapRoutes` collections or the persisted `MapActivity.mapRoutesToCache()` wire format, including decoded `LocalRoadCoverage.Section` interval lines. It excludes untravelled background roads and fails closed if more than **512 intersecting display fragments or 8,192 displayed points** would be retained. The check is designed for **offline/fixture validation**, not an on-device UI drawing callback.

**Interpretation guardrail:** `max_accepted_to_display_m` measures proximity to the represented union; it is **not** by itself a proof of correct network identity, topology, or completeness. Other legitimate journeys can appear on the main Map, so their additional strokes MUST NOT be called invented geometry merely because the reference file covers only one walk. No reverse extra-geometry verdict is made until a full set of contributing accepted journeys is available.

**Private route bounds (not committed as literal coordinates):** The submitted matched route extends across central London, from the Charing Cross side to the Temple/Fleet Street corridor. The future validation runner should derive exact source bounds with `MapCorridorGeometry.around(accepted, marginMetres)`, preserving the full accepted path in private memory. The owner-provided screenshot is wider than the earlier problematic cumulative zoom, so compare actual routes using geographic bounds first and fixed-camera screenshots second.

**Remaining blocker:** Build 313 uses a revision-keyed `map-routes` persistent snapshot only when resource headroom allows, and may retain the prepared Map only in process memory. No full cumulative geometry export was supplied. This PR therefore supplies the comparison code and synthetic regression tests, but no measured accepted-to-real-cumulative deviation yet. A real scoped export from the current Map assembly or an authorised existing Map cache is needed in a subsequent validation session.

## Device evidence collection — implementation present, release gated

The development branch now contains an **opt-in, bounded, read-only** Map geometry export. It is **not available in the owner's currently installed Build 313** until a signed integrated upgrade containing these changes has passed release gates and been installed.

**Owner procedure after an authorised signed upgrade:**

1. Open the **main cumulative Map** and wait for the existing Map-ready state. Leave the road-fact loading overlay behaviour unchanged.
2. Select **All transport / All statuses / All dates**. Zoom into the Fleet Street / Temple problem area so only the relevant street block is visible. Keep a screenshot of the Map at this camera position.
3. **Long-press the Map's Legend control** (ordinary tap still opens the legend). Confirm the private-location warning, then use the Android document picker to save `roadprints-visible-map-evidence.json`.
4. If the app reports a dense or oversized view, zoom further into a smaller area and repeat; **never silently omit sections**. The exported file is limited to 512 intersecting segments, 8,192 vertices and 384 KiB.
5. Privately supply the JSON file together with the accepted individual-journey diagnostic already provided, and the same-camera screenshot. The export includes **precise travelled location coordinates**; do not post it in a public issue or commit it to GitHub.

The export captures **actual currently prepared travelled Map sections**, not raw GPS, a rebuilt archive, or an inferred reference-road network. It records camera, geographic window, revision, filter selection, counts and incomplete-coverage flags. The existing `compareSavedEvidence()` adapter can measure the accepted journey against the exported cumulative union without labelling unrelated valid journeys as unsupported.

**Release/validation boundaries:** Run the focused JVM test suite including `MapGeometryEvidenceExportTest`; compile the Android application; verify UI gesture and document-picker flow on a test device/emulator; preserve the original archive; then deliver a single signed in-place integrated APK by direct GitHub Release asset URL. A successful focused JVM run alone does **not** establish the device export is operational or that the cumulative Map is accurate.

**Scope:** This is a global-purpose evidence facility: the viewport can be anywhere on Earth, including across the antimeridian. Fleet Street is the first owner's real-world regression, not a location-specific production algorithm.

## Data acquisition boundary

**Available today:** screenshot and owner description identifying this as a problematic location.  
**Still required for automated real-output comparison:** the cumulative Map's actual prepared/displayed coordinates for the same viewport, plus exact Map camera/zoom and a genuine fixed-camera baseline-versus-candidate screenshot pair. One private accepted journey diagnostic export is now available to this conversation but is not copied to GitHub. The screenshot alone cannot supply authoritative sub-metre route coordinates or journey attribution. Build privacy-safe synthetic topology tests separately; never claim they reproduce the owner's London journeys exactly.

## Engineering scope

This is a **high-value regression guardrail** within a general cumulative geometry suite, **not** a special-case Fleet Street renderer or a London-only algorithm. Do not alter road/NCN attribution, archive evidence, journey editing, or the map loading overlay to satisfy this scenario. Keep separate from PR #367's existing fragment-recovery release gate.
