# Pub visits — 0.25.191 (237)

This release adds live check-ins and retrospective Timeline place visits to
the installed beta Spoons collection. There is no arbitrary visited tick and
no automatic GPS stop award. Capture, matching and catalogue update cadence
are unchanged. Achievements and a full-UK completion target remain later work.

## Live visit

Open the pub card on the map, then Check in / View pub visits. Request a current
foreground location and confirm that you are inside the named pub. The fix must
be genuine, no more than 60 seconds old by monotonic time, and have positive
accuracy no worse than 50 m. Distance plus uncertainty must fit within 125 m of
the reviewed display point, or 300 m for an explicitly approximate shared
building point. These radii are evidence checks, not surveyed pub footprints.
Terminal/park presence never awards a visit by itself. The named confirmation
is mandatory and the fix is checked again when the confirmation is saved.
Indoor accuracy can prevent a check-in; it is never bypassed by a manual tick.

The live proof retains stable pub ID, name, date, confirmation and precise fix
evidence locally. Repeated checks in one quarter-hour share a proof key; unique
visited-pub totals count each pub once. This release does not create a journey
or alter an existing journey from a pub check-in.

## Timeline history

The existing local Timeline source ledger already retains explicit place
visits, coordinates, dates and available place names/IDs. Pub reconciliation
uses those visits, never activity routes or matched/drawn lines. A complete,
past visit lasting at least a minute and naming exactly one nearby reviewed
pub can produce a named Timeline proof. Conservative name normalization permits
the operator suffix; coordinates disambiguate identically named pubs. Google
place IDs are retained as evidence, not treated as verified pub mappings.
The thirteen curated/shared-site or converted identities always require
historical confirmation, even if Google's present-day label names the pub.

Nearby unnamed/generic places, shared buildings and ambiguous names are offered
for review. Each confirmation names the pub and refers to the original dated
Timeline visit; an unsupported pub or missing original visit is rejected.
Not a pub visit suppresses that source record on repeat import. Review is paged
in batches of 50. No coordinates alone or airport/holiday-park name automatically
awards a pub. A current membership review cannot certify every historical
operator change; users can inspect the named-source provenance in their visit list.

Collections > View pub visits exposes accepted visits and the review queue.
Import Timeline place visits reads the place fields without importing or
rematching journeys. Normal Timeline imports also reconcile pub visits when
the collection is installed. Services retain their existing source ledger and
visit rules; pub proof records use a separate preference ledger.

## Retention and UI

Map pins use the existing visited styling and the collection shows unique pubs
visited, not a claimed complete national percentage. Accepted records retain
pub IDs/names/dates if a pub disappears from a later map catalogue. Catalogue
updates do not delete or retarget visit proofs. Removing/reinstalling a collection
preserves history and entitlement. Delete all history clears both Timeline and
pub proofs while preserving entitlement; standard Roadprints backup includes
the local pub preference ledger and Timeline source file.

History scanning runs on background workers outside the pub-store lock. Deletes
invalidate in-flight scans so old results cannot reintroduce cleared proofs.
This slice does not enable the catalogue's automatic-visit flag: reviewed map
points are not an automatic detector. The separately implemented app workflow
requires explicit Timeline place evidence or a fresh location plus named consent.

Regression coverage includes duplicate imports, named/generic Timeline visits,
ambiguous names, future/invalid/route data, unsupported confirmations, dismissed
sources, fresh/stale/inaccurate/mock/remote live fixes, mandatory confirmation,
collection reinstall, retained identities and delete-all behaviour. Semantic
and legacy import tests verify source dates/names and preservation of journeys.
Android tests, lint and signed APK verification run in CI.
