# Final achievements: two bounded releases

Decision: 7 October 2026. Build on the current released app, preserving the
0.25.161 pedestrian capture prevention and recovery changes.

## Part 1 — County Collector and navigation (0.25.162)

- One upgrading County Collector badge: 10%, 25%, 50%, 100% of the 92 historic
  UK counties, evaluated before display rounding (10, 23, 46, 92 counties).
- At least one currently unlocked road geographically inside a county qualifies
  it. Matched road modes qualify; train, plane, failed/unmatched GPS and paths
  alone do not. The full completed/remaining list stays available offline.
- Pinned HCT Definition A full-resolution boundaries; all four nations, islands
  and holes retained. County awards remain based on matched road locations;
  settlement reference membership never grants an award. The subsequent
  0.25.163 consolidation aligns Progress to the same historic county IDs.
- Recalculate after journey edits and deletions; retain recognition history and
  existing achievement celebrations without duplicate reopening awards.
- Fixed jump menu like Progress. Motorway/A-road completion accordions belong
  inside Road discovery. Preserve instant catalogue and cached screen behavior.

## Part 2 — Landmarks, Sightseer and Groundhog Day (0.25.164)

Implemented all 12 fixed landmark cards (preserving existing Angel/Stonehenge),
Sightseer levels 3/6/12 with completed/remaining checklist, and Groundhog Day
(five distinct local weekdays on the same mode, direction and broad route).
Offline geometry, per-landmark qualification, route tolerances, edit/deletion
recalculation and recognition history are documented in [LANDMARK-EVIDENCE.md](LANDMARK-EVIDENCE.md).
County/settlement consolidation and pedestrian capture prevention remain intact.
