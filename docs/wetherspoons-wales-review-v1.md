# Wales catalogue review — 11 October 2026

All 47 Welsh candidates in the pinned 831-row OSM snapshot have individually
checked current operator pub listings. Each approval is attached to its own
stable OSM identity, aliases, source name and label position. Repeated names
such as Picture House and Central Bar do not approve other pubs elsewhere.

The national review ledger now supports 117 map-only approvals, two former-pub
exclusions and two holds. 710 source candidates have no decision yet; the
builder's 712 unapproved candidates includes the two holds. Installed 0.25.186
still contains 37 pins: this is a catalogue checkpoint, not an APK release.

Operator listings and regular schedules distinguish overnight closing from
permanent closure. Source town/postcode omissions are retained. Broader source
towns include Swansea/Morriston, Swansea/Gorseinon and Deeside/Shotton. Name
variants involving articles, hotel suffixes or branding are explicitly reviewed.
Yr Hen Orsaf has SY23 1LH in OSM versus SY23 1LN in the operator listing; Ebbw
Vale Picture House has NP23 6HL versus NP23 6HP. These same-site/locality
discrepancies are recorded without overwriting OSM with directory fields.

An independent researcher list was checked for omitted Welsh names/localities
and matches the 47 candidates. This corroborates the batch; it is not an
authoritative full-coverage guarantee. Missing-venue search remains incomplete.
The Boathouse at Haven Presthaven appeared as a proposal in earlier reports;
later November 2025 reports say the plans were scrapped. It remains an inactive
reconciliation lead, not a missing operating venue or extra Welsh pin.

Hotels that contain operating pubs are included as pubs. There is no new hotel
feature. Yr Hen Orsaf's station location does not approve station-wide visit
evidence; all pub premises remain unverified for visit credit.

## Agreed delivery sequence

1. Establish the UK catalogue: finish remaining regional/country batches,
   resolve closures, duplicates and omitted venues, and validate positions.
2. Publish an app version bundling the approved catalogue. Test the actual UK
   asset size, loading, nationwide clustering and installed-collection behavior.
3. Add silent catalogue updates as the final slice: use a versioned shared
   catalogue, validate identity/scope/schema before activation, replace cached
   data atomically and retain the last good/bundled offline fallback on failure.
   Check updates occasionally, never per GPS sample. Preserve visit identities
   when pubs change status; catalogue updates cannot alter recording logic.

The 117-pin preview is 55,820 bytes and passes current map review gates. The
app loader currently has a 64 KiB bound; the full UK asset will require a
deliberately sized bounded loader before release, not a silently truncated file.
Full-coverage and completion target remain disabled until catalogue approval.
Strict visit recognition, county progress and achievements remain later work.
