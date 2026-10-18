# Interactive progress road details

Progress road rows open a child map containing only the selected road. Motorway
and A-road coverage chart rows (including their labels and metadata) open the same
map. Cumulative chart rows open a list of journeys that contributed the same saved
canonical feature evidence and modes as those charts. Closing the child or using
Back leaves the existing Progress activity beneath it, preserving its section,
expanded groups and saved scroll position.

The child reuses MotorwayProgressCalculator and ARoadProgressCalculator: remaining
sections are red, covered motorway sections blue and covered A-road sections
green. Full reference extent, unique distance and percentage come from those
existing calculators. Missing references, B-roads and local roads show recorded
sections only and explicitly withhold full-road coverage claims. This introduces
no new distance or completion algorithm and makes no matching or archive edits.

Road-line taps show matching journeys near that rendered section, restricted to
the selected identity and respecting removed-road corrections. A separate button
lists all matched journeys for the selected road. Entries open the existing
journey editor. Work streams the archive off the UI thread, retains summary
metadata rather than complete journeys, limits fallback map geometry and pages
journey views in groups of 40. Only road/region/settlement identities cross the
Activity boundary; no route geometry or potentially huge journey-ID arrays do.
Journey archive revisions cause a fresh assessment after an edit or deletion.

GB/NI references remain separate in both discovery rows and the detail model.
Local roads require a settlement and are clipped at actual polygon crossings,
including holes and disconnected components, so same-named roads outside that
settlement cannot contribute. A failed boundary load offers retry instead of
showing unscoped roads. Existing unresolved settlement assignment remains an
upstream limitation; this view does not invent town identities.

This PR is stacked on feat/map-journey-filters (PR166) so it preserves the latest
map filter and shared UI work. Integrate it with the pending app changes before
the combined release. No version bump, signed APK, Firebase distribution or
production data change is requested. Android unit tests/lint run through the
existing checks workflow. The new road-detail tests cover identity navigation,
GB/NI and reference aliases, clipped crossings and holes, same-name roads,
corrections, unfinished journeys, duplicate collections and cumulative evidence.

The NCN consumer still needs its own route selection and verified inventory
adapter. Its orange/purple rendering and activation totals must use the NCN
ledger; they must not inherit motorway proximity-based coverage calculations.
The shared child-map/journey-list interaction is established here; this does not
claim that the NCN Android integration or the collection beta gate is complete.
