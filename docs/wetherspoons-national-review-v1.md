# National catalogue review — first checkpoint, 11 October 2026

This work starts nationwide reconciliation; it does not certify a complete UK
directory. The pinned OSM snapshot has 831 candidates across 69 represented
historic county groups. Counties use the existing definition A. A county with
all existing candidates reviewed can still have missing venues.

The review ledger now supports 70 informational map approvals, two former-pub
exclusions and two holds. 757 candidates have no decision yet; the map builder's
759 unapproved candidates includes the two holds. The installed 0.25.186 asset
still has 37 pins. These reviews are staged for a later packaged catalogue;
this checkpoint deliberately changes no app assets, version or tracking code.

Completed this checkpoint:

- Furze Wren's address-only hold reconciled using both operator addresses and
  the independent FSA business record. OSM coordinates retained.
- All 27 existing historic Essex candidates reviewed: 26 map approvals and
  Miller's Well excluded as a former Wetherspoon. The January 2024 CAMRA update
  records its December 2023 reopening under another operator.
- Three Northern Ireland candidates approved. Trinity Bar remains held pending
  current operator verification; a renamed former pub is not assumed current.
- Cranborne Hundred, Fletton Brick and Heath Hotel individually confirmed as
  operating new-opening candidates already present in OSM.
- Benjamin Franklin held because its advertised opening is 2 March 2027.
  An advertised date never automatically activates a venue.
- Belle Vue at Manchester Airport recorded as a missing-identity lead; it
  needs its own OSM position, not an airport-wide marker. Three future-opening
  leads remain inactive.

Names are not identities: identically named pubs in other regions receive no
approval from these checks. All review decisions pin the OSM stable ID, aliases,
name and position. Broader OSM towns and postcode discrepancies remain recorded
without silently replacing OSM data with operator directory fields.

collections/wetherspoons-review-progress-v1.json records remaining IDs by
county and distinguishes existing-candidate review from missing-venue search.
collections/wetherspoons-reconciliation-v1.json records missing/future leads.
Both are development records, not app directories. Keep them consistent with
the ledger and source snapshot when adding each batch.

Continue with remaining England county batches, Scotland and Wales; reconcile
Northern Ireland ownership and missing airside venues; search for omitted
operating pubs in each region. Before full release, review every remaining
candidate, resolve missing identities, check UK scope and duplicate aliases,
validate map positions, and run the app with the actual nationwide asset.
Unresolved venues remain hidden and full coverage remains false.

The operator search's 827 headline includes filters beyond UK scope. It is not
a completion target. Individual source checks use review links and original
conclusions; the app's names/positions remain OSM-derived. No operator photos,
descriptions or bulk directory/coordinates are copied into the app.

Map approval remains separate from strict visit evidence. Airports and stations
need pub-specific premises checks; achievements, progress totals and paywall
implementation remain later work. Recorder, matcher and importer are unchanged.
