# Collection installation and map selection — 0.25.183

Beta collection access is free. Installation is distinct from entitlement and
from visit evidence. Removing/reinstalling a collection preserves ownership and
history. Existing Services users keep their installation on upgrade. No billing
API or live purchases are enabled.

The Collections screen offers Motorway Service Stations and UK Wetherspoons
Collection. Show on map selects the collection explicitly. The main map shows
one collection at a time, or None when multiple collections are installed. Its
selector appears only with more than one installed collection; a single installed
collection is selected automatically. Selection is saved; removal falls back to
an installed collection. A collection swap retains the viewport and journey/road
layers and does not rerun recording, matching or import.

Services progress hides when its collection is removed, without deleting any
evidence or affecting Services' existing evidence detector. Its earned/history
records remain available after reinstall. Collection access preferences are
included by the existing backup's shared-preference export/import.

The renderer accepts only the selected collection's pins. Cluster hit testing
uses the same projected groups as drawing, rather than choosing a hidden pin
near the tap. A multi-pin cluster zooms in; coincident pins or pins still grouped
at maximum zoom open a venue chooser. Existing Services pin styles remain intact;
the generic renderer supports a distinct pub marker for the later approved data.

## Catalogue gate and current limitation

The UK Spoons candidate snapshot is still audit-only. **No candidate catalogue is
packaged in Android**, and no pub pins, completion targets, visits or achievements
are activated. Spoons can be installed for beta access and selected; the map
clearly says pub locations are being verified and retains the user's journeys.
The catalogue consumer rejects audit-only data, unapproved release status and
non-active venue rows. Its real approved catalogue supplier is the next data
activation step after #82's operating-status/coverage/footprint checks.

No capture service, mode/arrival logic, raw journey storage, matcher, importer or
Services visit thresholds change in this release. Keep the other test phones on
0.25.182 while using 0.25.183 to test collection installation/selection on Adam's
phone; recording regressions still take priority.

Validation: regression tests cover upgrade defaults, test installation without
visits, removal/reinstall, persisted/fallback map selection, catalogue rejection,
cluster hit centres and coincident choices. Full Android tests, lint and stable
signed APK verification run in CI. Device UI acceptance remains a separate check.
