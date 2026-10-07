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

## Part 2 — Landmarks, Sightseer and Groundhog Day

Preserve the agreed 6 October specification and existing badge IDs/rules.

| Landmark | Name | Qualification to verify |
| --- | --- | --- |
| Big Ben | Tick Tock | Westminster Bridge crossing |
| Blackpool Tower | Paris is lovely this time of year | Defined promenade stretch |
| Humber Bridge | No longer the longest | Bridge crossing |
| Trent Bridge | Football or Cricket, Sir? | Bridge crossing |
| Bullring bull | Mooooo! | Immediate pedestrian area |
| Ness Point | The Far East | Reach the actual point |
| Windsor Castle | Royal drive-by | Defined M4 stretch |
| Stonehenge | Enjoying the Solstice | Preserve A303 rule and existing identity |
| Angel of the North | I Saw an Angel | Preserve A1 rule and existing identity |
| Loch Ness | Why hello Nessie | Qualifying lakeside road route |
| Conwy Castle | The Iron Ring | Immediate area or qualifying adjacent bridge |
| Stormont | Weather warning, Storms likely | Grounds near Parliament Buildings |

Sightseer upgrades at 3, 6 and 12 distinct landmarks, with a completed/remaining
checklist. Define individual road corridors and pedestrian areas; nearby travel
alone must not qualify. The abandoned branch has no committed implementation or
geography assets to reuse. Earlier screenshots described 5 km of distinct Loch
Ness lakeside road coverage in one journey; verify this and all geographic rules
before packaging. Preserve existing crossing rules and avoid duplicate records.

Groundhog Day: broadly the same trip on five distinct weekday dates (not five
repeats on one date), consecutive or spread out. Same mode, direction, start,
destination and broadly same route, tolerating normal GPS variation. Walking,
cycling, road travel and train journeys qualify; imported history qualifies.
Opposite directions are different trips. Recalculate after edits/deletion.
Test short trips, loops, duplicates, weekends, local dates/timezones and uncertain
or missing route evidence. Route similarity tolerances require verification.

Each part has its own tested, signed APK. Keep private journey history out of
repository fixtures and use synthetic regression data.
