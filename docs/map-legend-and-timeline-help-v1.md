# Map legend and device Timeline help

Included in combined 0.25.196 (#116/#117), alongside the shared venue framework.
No separate APK is distributed for this slice.

The phone export help now uses device Settings, Location, Location services,
Timeline, Export Timeline data, Continue, folder selection and Save. It is shared
by normal, Services and place-only pub import screens through the existing
TimelineImportActivity. Verified against Google's Android export help on
13 October 2026: https://support.google.com/maps/answer/6258979?hl=en&co=GENIE.Platform%3DAndroid.

A bottom-left floating Legend button replaces the persistent collection header
selector and pub-count notice. The Roadprints dialog scrolls its key, switches,
collection selection and visual pin references. Keys match actual paint colours:
black recorded travel, blue covered motorway sections, green covered A-road
sections, red remaining major-road reference sections. A separate town-boundary
key appears on town maps. Black recorded travel may overlap the other overlays;
the underlying OpenStreetMap uses its own colours. Pin samples distinguish the
Services grey dot, Wetherspoons jade W, gold visited venues and numeric clusters.
A gold cluster means at least one venue in the group was visited.

Switches persist as visual preferences. They change drawing and road hit groups
without dropping geometry, changing progress or refitting the map. The remaining-
road summary lookup also skips hidden red overlays. No collection now works even
with a single installed collection. Closing/reopening the panel or reloading data
retains camera state and preferences. Collection removal/install behavior and
visit evidence remain separate.

Regression checks cover persistence, single-collection None, actual line swatch
pixels, camera retention, geometry retention and hidden-overlay hit testing.
