# Cycling first slice — 0.25.167

Cycling uses `/match-cycling`, backed by an OSRM bicycle graph. It never falls
back to a driving or pedestrian matcher. Timeline cycle labels, manual capture
and the existing Android ON_BICYCLE classification all retain Cycling mode.

Existing saved cycling journeys with at least two actual Timeline route points
can be matched from their journey card or the global matching queue. Start/end
locations alone are insufficient evidence. Capture recordings retain their
original trace, timestamps and diagnostic data if validation or matching fails.

Cycling has its own matching queue, processing stage, import progress card and
retry/rematch controls. Confirming a different transport mode invalidates the
previous mode's match. Verified matched distance excludes overlap between chunks.

## Device testing

1. Install the same signed build over the existing app, grant location and
   notification permissions, and check the existing journey archive remains.
2. In Utilities > Tracking settings enable manual recording and select Cycling.
3. Start outdoors, ride a familiar short route, pause at a junction, then stop
   recording after arrival. The saved journey should match automatically.
4. Check Cycling filter, route preview, matched distance, map/editor and replay.
5. Repeat using a cycleway or shared path. Compare the saved trace and matched
   geometry, particularly where the path runs beside a road.
6. For failures use Debug Journey / movement diagnostics; retry from the card.
   Check a walk and a drive still behave as before.

Automatic bicycle classification already exists; tuning start/stop thresholds
requires real rides. Manual recording is the reliable first test harness.

## Attribution boundary

Cycling geometry and distance count as cycling activity. Canonical motorway and
A-road completion and named-road discovery are deliberately withheld in this
slice: OSRM step names/refs omit highway/access tags and cannot prove carriageway
identity. Cycle step geometry is retained by the backend for future attribution.
Strava/GPX import, the paid collection and cycle-specific achievements are later work.
