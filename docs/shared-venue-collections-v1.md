# Shared venue collections — 0.25.195 (241)

This delivery combines the visit-flow slice of #77 and #82. Motorway Service
Stations and UK Wetherspoons use one collection-scoped ledger, stop detector,
manual/review/history dialogs and searchable lazy progress component.

## Visit qualification

The shared detector requires at least four accurate stationary samples over
three minutes, with no gap above 90 seconds. Moving, inaccurate, missing or
repeated timestamps do not manufacture dwell. Service-site points on either
carriageway retain their previous qualification policy. An individual pub is
automatic only when a supported map-premises arrival names the unique nearby
reviewed pub, has sufficient dwell, and is not a curated/shared-site identity.
Generic nearby stops become review proposals. Airport, station or holiday-park
presence alone does not earn a pub. Manual check-in for either collection needs
a fresh genuine accurate device fix supporting the named venue and explicit
confirmation. A manual visit never manufactures a journey.

Automatic evaluation happens when original captured journey evidence is saved,
with streamed historical reevaluation on an installed collection's first scan
or a changed archive. It does not run a second tracker or request map data for
each collection. Pending visits are offered on the foreground map/progress
screen and remain available through Manual check. Closing defers; rejecting is
persistent. Collection removal retains history while hiding the collection.

## Storage and compatibility

`roadprints_venue_visits_v1` separates each collection's records/revision. Original
pub proofs migrate once, including dates and rejection markers. Legacy Services
journey links and verified Timeline sets migrate without invented visit dates.
Existing store/UI class names are compatibility adapters. Saved backup routes
remain valid and the normal backup includes the new ledger. Delete-all clears
both collections' history while preserving access. Reconciliation is generation
guarded against deletion/restoration and respects JourneyStore-first lock order.

Native recalculation invalidates unsupported derived records while preserving
rejections. Source Timeline changes/removal recalculate qualifying proofs;
catalogue removal alone retains historical evidence. Automatic, Timeline and
manual proofs for the same venue/session share one displayed history entry,
while retaining each original evidence record. Unique venue totals count once.
Removing an incorrect session suppresses its derived sources so rematching or
repeat imports cannot immediately re-award it. Native proofs participate in the
existing sourced pub achievements and durable celebration delivery.

## Repeatable definition

`VenueCollection.Definition` supplies identity, labels, a catalogue source,
name-normalisation and qualification policy. The shared engine accepts a
non-production restaurant fixture without copied screens or capture code.
Progress offers name/town/postcode search and All/Visited/Remaining filters,
with lazy county/town or region/road groups. Both maps/history expose manual
check-in; related journeys are available where evidence actually has one.

The existing silent Wetherspoons updater is preserved unchanged in cadence and
publication contract. Full generic publication/schema support and conversion
of collection-specific achievement definitions/calculators to a declarative
configuration are subsequent #77 slices; this release does not claim those
parts are complete. Adding a third live collection still requires its reviewed
catalogue/definition and achievement adapter. Network/rail/airport semantics
remain separate work.

## Remaining pub content

No held/future venue is activated and no certified national/county completion
target is introduced. Held-status/coverage reconciliation and deferred full-
county, Last orders and Pub crawl content remain in #82. Purchase integration
stays deferred in #75. Field acceptance is outside the development backlog;
this document describes implemented behaviour, not proven real-world reliability.
