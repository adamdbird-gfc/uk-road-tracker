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

## Data acquisition boundary

**Available today:** screenshot and owner description identifying this as a problematic location.  
**Still required for automated geographic truth:** one or more owner-approved accepted journey route exports for this specific viewport, plus exact Map camera/zoom and a genuine current-renderer screenshot/geometry export. The screenshot alone cannot supply authoritative sub-metre route coordinates or journey attribution. Build privacy-safe synthetic topology tests separately; never claim they reproduce the owner's London journeys exactly.

## Engineering scope

This is a **high-value regression guardrail** within a general cumulative geometry suite, **not** a special-case Fleet Street renderer or a London-only algorithm. Do not alter road/NCN attribution, archive evidence, journey editing, or the map loading overlay to satisfy this scenario. Keep separate from PR #367's existing fragment-recovery release gate.
